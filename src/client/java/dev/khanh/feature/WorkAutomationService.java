package dev.khanh.feature;

import dev.khanh.KhanhClient;
import dev.khanh.config.ConfigManager;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.FallingBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.MathHelper;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.chunk.ChunkStatus;

/** Client-side survival builder and bounded excavation, paced through normal interactions. */
public final class WorkAutomationService {
    private static final SafeWorkNavigator NAVIGATOR = new SafeWorkNavigator();
    private static BlockPos mineCornerA, mineCornerB, miningTarget, remoteTarget;
    private static Direction miningFace;
    private static ClientWorld session;
    private static int tick, nextAction, lastStatus = -1000, originalSlot = -1, placements;
    private static long remoteCursor;
    private static final Map<BlockPos, Integer> DEFERRED = new HashMap<>();
    private static String activeMode = "";
    private static boolean builderPaused;
    private static String builderPauseReason = "";
    private static BlockPos pendingPlacement;
    private static BlockState pendingWanted;
    private static int pendingDeadline;
    private static String placementKey = "";
    private static LitematicaPlacementBridge.Placement activePlacement;
    private static Vec3d aimGoal;
    private static int aimStableSince = -1;
    private static boolean idleLooking;
    private static int idleGlanceStarted, lastIdleGlanceTick, idleGlanceIndex;
    private static float idleBaseYaw, idleBasePitch;
    private static String builderActivity = "Ready";
    private static String cachedBuilderUiStatus = "Checking setup";
    private static long builderUiCheckedAt;

    private WorkAutomationService() { }

    public static void startBuilder() {
        MinecraftClient client = MinecraftClient.getInstance();
        var module = KhanhClient.modules().byId("auto_schematic_builder");
        if (module == null) return;
        module.setEnabled(true);
        builderPaused = false;
        builderPauseReason = "";
        builderActivity = "Ready - close GUI to build";
        builderUiCheckedAt = 0;
        ConfigManager.save(KhanhClient.modules());
        checkBuilderSetup();
    }

    public static void checkBuilderSetup() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;
        String blocker = builderBlocker(client);
        client.player.sendMessage(Text.literal("[Khanh Client] Builder: "
            + (blocker == null ? "ready; close the GUI to begin" : blocker)), false);
    }

    /** A short, throttled status for the module inspector; never scans schematic blocks while drawing. */
    public static String builderUiStatus() {
        long now = System.currentTimeMillis();
        if (now - builderUiCheckedAt > 600) {
            MinecraftClient client = MinecraftClient.getInstance();
            String blocker = builderBlocker(client);
            cachedBuilderUiStatus = blocker == null ? builderActivity : blocker;
            builderUiCheckedAt = now;
        }
        return cachedBuilderUiStatus;
    }

    private static String builderBlocker(MinecraftClient client) {
        if (!KhanhFeatures.enabled("auto_schematic_builder")) return "OFF — press Start Build";
        if (client.player == null || client.world == null || client.interactionManager == null)
            return "enter a world first";
        if (!allowedWorld(client, "auto_schematic_builder"))
            return "singleplayer only unless your permitted private server address is entered exactly";
        if (KhanhFeatures.enabled("auto_mineout")) return "turn off Auto Mineout";
        if (builderPaused) return builderPauseReason.isEmpty()
            ? "Paused - press Resume Build"
            : builderPauseReason + " - press Resume Build";
        if (client.player.isSpectator()) return "spectator mode cannot place blocks";
        if (FreecamService.active()) return "turn off Freecam";
        if (client.player.getHealth() <= KhanhFeatures.number("auto_schematic_builder", "min_health", 6))
            return "health is below the safety limit";
        if (client.player.getHungerManager().getFoodLevel()
            < KhanhFeatures.number("auto_schematic_builder", "min_hunger", 6))
            return "hunger is below the safety limit";
        if (client.player.isTouchingWater() || client.player.isOnFire() || client.player.isGliding())
            return "leave water, fire, or Elytra flight before building";
        if (!LitematicaPlacementBridge.installed()) return "Litematica is not installed";
        LitematicaPlacementBridge.Placement placement = LitematicaPlacementBridge.selected();
        if (placement == null) return LitematicaPlacementBridge.diagnostic();
        if (KhanhFeatures.choice("auto_schematic_builder", "layer_mode", "All").equals("Player Level")
            && (client.player.getBlockY() < placement.min().getY()
                || client.player.getBlockY() > placement.max().getY()))
            return "Player Level excludes this schematic height — choose Build Layers: All";
        int radius = KhanhFeatures.number("auto_schematic_builder", "walk_radius", 32);
        BlockPos feet = client.player.getBlockPos();
        if (feet.getX() < placement.min().getX() - radius
            || feet.getX() > placement.max().getX() + radius
            || feet.getZ() < placement.min().getZ() - radius
            || feet.getZ() > placement.max().getZ() + radius)
            return "move within " + radius + " blocks of the schematic";
        if (!client.player.isOnGround()) return "stand on solid ground before building";
        return null;
    }

    public static void pauseBuilder() {
        builderPaused = true;
        builderPauseReason = "";
        builderActivity = "Paused";
        MinecraftClient client = MinecraftClient.getInstance();
        release(client);
        status(client, "Schematic build paused");
    }

    public static void resumeBuilder() {
        builderPaused = false;
        builderPauseReason = "";
        builderActivity = "Ready - close GUI to build";
        status(MinecraftClient.getInstance(), "Schematic build resumed");
    }

    public static void stopBuilder() {
        var module = KhanhClient.modules().byId("auto_schematic_builder");
        if (module != null) module.setEnabled(false);
        ConfigManager.save(KhanhClient.modules());
        reset(MinecraftClient.getInstance());
        builderPaused = false;
        builderPauseReason = "";
        builderActivity = "Stopped";
    }

    private static boolean allowedWorld(MinecraftClient client, String moduleId) {
        if (client.isInSingleplayer()) return true;
        ServerInfo server = client.getCurrentServerEntry();
        if (server == null || server.isRealm()) return false;
        String actual = server.address.trim().toLowerCase(Locale.ROOT);
        String allowed = KhanhFeatures.text(moduleId, "private_server", "")
            .trim().toLowerCase(Locale.ROOT);
        // DonutSMP's published rules prohibit auto/easy-place and scripts.
        String host = actual.split(":", 2)[0];
        if (host.equals("donutsmp.net") || host.endsWith(".donutsmp.net")) return false;
        return !allowed.isEmpty() && actual.equals(allowed);
    }

    public static void setMineCorner(boolean first) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) return;
        BlockPos selected = client.player.getBlockPos().toImmutable();
        if (first) mineCornerA = selected;
        else mineCornerB = selected;
        remoteTarget = null;
        remoteCursor = 0;
        client.player.sendMessage(Text.literal("Mineout corner " + (first ? "A" : "B") + ": "
            + selected.toShortString()), true);
    }

    public static void clearMineCorners() {
        mineCornerA = mineCornerB = null;
        remoteTarget = null;
        remoteCursor = 0;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) client.player.sendMessage(Text.literal("Mineout selection cleared"), true);
    }

    public static Box mineoutBox() {
        if (mineCornerA == null || mineCornerB == null) return null;
        Bounds area = Bounds.of(mineCornerA, mineCornerB);
        return new Box(area.min().getX(), area.min().getY(), area.min().getZ(),
            area.max().getX() + 1, area.max().getY() + 1, area.max().getZ() + 1);
    }

    private static boolean playersNearby(MinecraftClient client, String moduleId) {
        if (!KhanhFeatures.bool(moduleId, "pause_players", false)) return false;
        int radius = KhanhFeatures.number(moduleId, "player_radius", 8);
        for (PlayerEntity other : client.world.getPlayers()) {
            if (other != client.player && other.squaredDistanceTo(client.player) <= radius * radius)
                return true;
        }
        return false;
    }

    private static boolean buildLayerAllowed(MinecraftClient client, BlockPos pos) {
        return switch (KhanhFeatures.choice("auto_schematic_builder", "layer_mode", "All")) {
            case "Player Level" -> pos.getY() == client.player.getBlockY();
            case "Below Player" -> pos.getY() <= client.player.getBlockY();
            default -> true;
        };
    }

    private static boolean buildStateAllowed(MinecraftClient client, BlockState state, BlockPos pos) {
        String id = "auto_schematic_builder";
        if (KhanhFeatures.bool(id, "skip_falling", true) && state.getBlock() instanceof FallingBlock)
            return false;
        if (KhanhFeatures.bool(id, "skip_block_entities", true) && state.hasBlockEntity()) return false;
        if (KhanhFeatures.bool(id, "skip_waterlogged", true) && !state.getFluidState().isEmpty())
            return false;
        return !KhanhFeatures.bool(id, "only_full_blocks", false)
            || state.isFullCube(client.world, pos);
    }

    private static double workPriority(String order, Bounds area, BlockPos pos, double distance) {
        return switch (order) {
            case "Bottom First", "Bottom Up" -> (pos.getY() - area.min().getY()) * 1000.0 + distance;
            case "Top First", "Top Down" -> (area.max().getY() - pos.getY()) * 1000.0 + distance;
            default -> distance;
        };
    }

    public static void tick(MinecraftClient client) {
        tick++;
        if (client.world != session) {
            if (!activeMode.isEmpty()) {
                originalSlot = -1;
                reset(client);
            }
            session = client.world;
            mineCornerA = mineCornerB = null;
        }
        boolean builder = KhanhFeatures.enabled("auto_schematic_builder");
        boolean mineout = KhanhFeatures.enabled("auto_mineout");
        if (!builder) builderPaused = false;
        if (!builder && !mineout) {
            if (!activeMode.isEmpty()) reset(client);
            return;
        }
        if (builder && mineout) {
            reset(client);
            status(client, "Turn off one automation module before starting the other");
            return;
        }
        String moduleId = builder ? "auto_schematic_builder" : "auto_mineout";
        if (!allowedWorld(client, moduleId)) {
            release(client);
            status(client, "Automation: singleplayer or an allowed private server only");
            return;
        }
        if (builder && builderPaused) {
            release(client);
            return;
        }
        if (client.player == null || client.world == null || client.interactionManager == null
            || client.currentScreen != null || client.player.isSpectator() || FreecamService.active()
            || CombatPvpService.busy()
            || client.player.getHealth() <= KhanhFeatures.number(moduleId, "min_health", 6)
            || client.player.getHungerManager().getFoodLevel() < KhanhFeatures.number(moduleId, "min_hunger", 6)
            || playersNearby(client, moduleId) || client.player.isTouchingWater()
            || client.player.isOnFire() || client.player.isGliding()) {
            release(client);
            return;
        }
        String mode = builder ? "builder" : "mineout";
        if (!mode.equals(activeMode)) {
            reset(client);
            activeMode = mode;
        }
        double reach = KhanhFeatures.decimal(moduleId, "reach", 4.5);
        if (!client.player.isOnGround()) {
            if (remoteTarget != null && NAVIGATOR.isDriving())
                NAVIGATOR.moveTo(client, remoteTarget, tick, reach, builder, moduleId);
            else release(client);
            return;
        }
        if (builder) {
            if (!LitematicaPlacementBridge.installed()) {
                release(client);
                status(client, "Install Litematica and MaLiLib for Auto Schematic Builder");
                return;
            }
            LitematicaPlacementBridge.Placement placement = LitematicaPlacementBridge.selected();
            activePlacement = placement;
            if (placement == null) {
                release(client);
                status(client, "Select and enable a Litematica placement first");
                return;
            }
            Bounds area = new Bounds(placement.min(), placement.max());
            String key = placement.name() + "@" + placement.min() + ":" + placement.max()
                + ":" + placement.regions().hashCode();
            if (!key.equals(placementKey)) {
                remoteCursor = 0;
                remoteTarget = null;
                pendingPlacement = null;
                DEFERRED.clear();
                placementKey = key;
            }
            if (checkPendingPlacement(client)) return;
            int limit = KhanhFeatures.number(moduleId, "max_placements", 0);
            if (limit > 0 && placements >= limit) {
                release(client);
                status(client, "Placement limit reached");
                return;
            }
            Predicate<BlockPos> needed = pos -> placement.contains(pos)
                && !placement.schematicWorld().getBlockState(pos).isAir()
                && client.world.getBlockState(pos).isAir()
                && DEFERRED.getOrDefault(pos, 0) <= tick
                && buildLayerAllowed(client, pos)
                && buildStateAllowed(client, placement.schematicWorld().getBlockState(pos), pos);
            runWork(client, area, needed, reach, true);
        } else {
            if (mineCornerA == null || mineCornerB == null) {
                release(client);
                status(client, "Set both Mineout corners in its options first");
                return;
            }
            Bounds area = Bounds.of(mineCornerA, mineCornerB);
            if (area.volume() > 262_144) {
                release(client);
                status(client, "Mineout selection is too large (maximum 262,144 blocks)");
                return;
            }
            Predicate<BlockPos> needed = pos -> mineable(client, pos);
            runWork(client, area, needed, reach, false);
        }
    }

    private static void runWork(MinecraftClient client, Bounds area, Predicate<BlockPos> needed,
                                double reach, boolean builder) {
        BlockPos local = closestReachable(client, area, needed, reach, builder);
        if (local != null) {
            if (builder) builderActivity = "Building - " + placements + " placed";
            endIdleLook();
            NAVIGATOR.stop(client);
            remoteTarget = null;
            if (builder) place(client, local, reach);
            else mine(client, local);
            return;
        }
        cancelMining(client);
        if (remoteTarget != null && (!area.contains(remoteTarget) || !needed.test(remoteTarget)))
            remoteTarget = null;
        if (remoteTarget == null) remoteTarget = nextRemote(client, area, needed, builder);
        if (remoteTarget != null) {
            if (builder) builderActivity = "Walking to next build area";
            endIdleLook();
            String moduleId = builder ? "auto_schematic_builder" : "auto_mineout";
            if (!KhanhFeatures.bool(moduleId, "auto_walk", true)) {
                if (builder) builderActivity = "Target is out of reach - enable Auto Walk";
                NAVIGATOR.stop(client);
                return;
            }
            if (!NAVIGATOR.moveTo(client, remoteTarget, tick, reach, builder, moduleId)) {
                DEFERRED.put(remoteTarget, tick + KhanhFeatures.number(moduleId, "retry_route", 200));
                remoteTarget = null;
                status(client, "No safe walking route to the next work block");
            }
        } else {
            NAVIGATOR.stop(client);
            if (builder) status(client,
                "No buildable block nearby - check layer, support, and materials");
            idleLook(client, builder ? "auto_schematic_builder" : "auto_mineout");
        }
    }

    private static void endIdleLook() {
        idleLooking = false;
        lastIdleGlanceTick = tick;
    }

    private static void idleLook(MinecraftClient client, String moduleId) {
        if (!KhanhFeatures.bool(moduleId, "idle_glances", true)
            || client.player.getVelocity().horizontalLengthSquared() > 0.01) {
            endIdleLook();
            return;
        }
        int interval = KhanhFeatures.number(moduleId, "glance_interval", 80);
        if (!idleLooking) {
            if (tick - lastIdleGlanceTick < interval) return;
            idleLooking = true;
            idleGlanceStarted = tick;
            idleBaseYaw = client.player.getYaw();
            idleBasePitch = client.player.getPitch();
            idleGlanceIndex++;
        }
        int duration = KhanhFeatures.number(moduleId, "glance_duration", 12);
        int elapsed = tick - idleGlanceStarted;
        if (elapsed >= duration * 2) {
            endIdleLook();
            return;
        }
        float angle = KhanhFeatures.number(moduleId, "glance_angle", 18);
        float[] offsets = {1.0F, -0.75F, 0.55F, -1.0F};
        float offset = offsets[idleGlanceIndex % offsets.length];
        float targetYaw = elapsed < duration ? idleBaseYaw + offset * angle : idleBaseYaw;
        float targetPitch = elapsed < duration
            ? MathHelper.clamp(idleBasePitch + offset * 3.0F, -75.0F, 75.0F)
            : idleBasePitch;
        float yawStep = MathHelper.clamp(MathHelper.wrapDegrees(targetYaw - client.player.getYaw()) * 0.4F,
            -6.0F, 6.0F);
        float pitchStep = MathHelper.clamp((targetPitch - client.player.getPitch()) * 0.4F,
            -4.0F, 4.0F);
        client.player.setYaw(client.player.getYaw() + yawStep);
        client.player.setPitch(client.player.getPitch() + pitchStep);
    }

    private static BlockPos closestReachable(MinecraftClient client, Bounds area,
                                             Predicate<BlockPos> needed, double reach, boolean builder) {
        BlockPos origin = client.player.getBlockPos();
        int radius = (int) Math.ceil(reach);
        double best = Double.MAX_VALUE;
        String order = KhanhFeatures.choice(builder ? "auto_schematic_builder" : "auto_mineout",
            builder ? "build_order" : "mine_order", "Nearest");
        BlockPos result = null;
        for (int y = Math.max(area.min().getY(), origin.getY() - radius);
             y <= Math.min(area.max().getY(), origin.getY() + radius); y++) {
            for (int x = Math.max(area.min().getX(), origin.getX() - radius);
                 x <= Math.min(area.max().getX(), origin.getX() + radius); x++) {
                for (int z = Math.max(area.min().getZ(), origin.getZ() - radius);
                     z <= Math.min(area.max().getZ(), origin.getZ() + radius); z++) {
                    BlockPos candidate = new BlockPos(x, y, z);
                    if (!needed.test(candidate)) continue;
                    double distance = client.player.getEyePos().squaredDistanceTo(Vec3d.ofCenter(candidate));
                    if (distance > reach * reach) continue;
                    double priority = workPriority(order, area, candidate, distance);
                    if (priority >= best) continue;
                    if (builder ? placementFace(client, candidate, reach) == null
                        : visibleMiningFace(client, candidate, reach) == null) continue;
                    best = priority;
                    result = candidate;
                }
            }
        }
        return result;
    }

    private static BlockPos nextRemote(MinecraftClient client, Bounds area,
                                       Predicate<BlockPos> needed, boolean builder) {
        String moduleId = builder ? "auto_schematic_builder" : "auto_mineout";
        int radius = KhanhFeatures.number(moduleId, "walk_radius", 32);
        int vertical = KhanhFeatures.number(moduleId, "vertical_scan", 16);
        BlockPos center = client.player.getBlockPos();
        Bounds nearby = new Bounds(
            new BlockPos(Math.max(area.min().getX(), center.getX() - radius),
                Math.max(area.min().getY(), center.getY() - vertical),
                Math.max(area.min().getZ(), center.getZ() - radius)),
            new BlockPos(Math.min(area.max().getX(), center.getX() + radius),
                Math.min(area.max().getY(), center.getY() + vertical),
                Math.min(area.max().getZ(), center.getZ() + radius)));
        if (nearby.min().getX() > nearby.max().getX()
            || nearby.min().getY() > nearby.max().getY()
            || nearby.min().getZ() > nearby.max().getZ()) return null;
        long volume = nearby.volume();
        if (volume <= 0) return null;
        if (DEFERRED.size() > 512) DEFERRED.entrySet().removeIf(entry -> entry.getValue() <= tick);
        String order = KhanhFeatures.choice(moduleId, builder ? "build_order" : "mine_order", "Nearest");
        boolean reverse = order.equals("Top First") || order.equals("Top Down");
        BlockPos nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        int budget = KhanhFeatures.number(moduleId, "scan_budget", 2048);
        for (int checked = 0; checked < Math.min(budget, volume); checked++) {
            long index = remoteCursor++ % volume;
            BlockPos pos = nearby.at(reverse ? volume - 1 - index : index);
            double distance = pos.getSquaredDistance(center);
            if (distance > radius * radius) continue;
            if (DEFERRED.getOrDefault(pos, 0) > tick) continue;
            if (client.world.getChunkManager().getChunk(pos.getX() >> 4, pos.getZ() >> 4,
                ChunkStatus.FULL, false) == null) continue;
            if (!needed.test(pos) || !exposedWorkFace(client.world, pos, builder)) continue;
            if (!order.equals("Nearest")) return pos;
            if (distance < nearestDistance) {
                nearest = pos;
                nearestDistance = distance;
            }
        }
        return nearest;
    }

    private static boolean exposedWorkFace(ClientWorld world, BlockPos pos, boolean builder) {
        for (Direction direction : Direction.values()) {
            BlockPos adjacent = pos.offset(direction);
            BlockState state = world.getBlockState(adjacent);
            if (builder ? !state.getCollisionShape(world, adjacent).isEmpty() : state.isAir())
                return true;
        }
        return false;
    }

    private static void mine(MinecraftClient client, BlockPos target) {
        if (tick < nextAction) return;
        Direction face = visibleMiningFace(client, target,
            KhanhFeatures.decimal("auto_mineout", "reach", 4.5));
        if (face == null) { cancelMining(client); return; }
        if (!aimAt(client, Vec3d.ofCenter(target))) return;
        if (!target.equals(miningTarget)) {
            cancelMining(client);
            miningTarget = target.toImmutable();
            miningFace = face;
            client.interactionManager.attackBlock(miningTarget, miningFace);
        } else {
            client.interactionManager.updateBlockBreakingProgress(miningTarget, miningFace);
        }
        client.player.swingHand(Hand.MAIN_HAND);
        if (client.world.getBlockState(target).isAir()) {
            miningTarget = null;
            nextAction = tick + KhanhFeatures.number("auto_mineout", "delay", 2);
        }
    }

    private static void place(MinecraftClient client, BlockPos target, double reach) {
        if (tick < nextAction) return;
        BlockHitResult hit = placementFace(client, target, reach);
        if (hit == null) return;
        if (!aimAt(client, hit.getPos())) return;
        LitematicaPlacementBridge.Placement placement = activePlacement;
        if (placement == null) return;
        BlockState wanted = placement.schematicWorld().getBlockState(target);
        ItemStack required = LitematicaPlacementBridge.requiredItem(wanted);
        if (required.isEmpty()) {
            builderPauseReason = "No obtainable item for " + wanted.getBlock().getName().getString();
            status(client, builderPauseReason);
            builderPaused = true;
            return;
        }
        int slot = -1;
        boolean last = KhanhFeatures.choice("auto_schematic_builder", "hotbar_order", "First").equals("Last");
        for (int offset = 0; offset < 9; offset++) {
            int index = last ? 8 - offset : offset;
            if (!required.isEmpty() && client.player.getInventory().getStack(index).isOf(required.getItem())) {
                slot = index;
                break;
            }
        }
        if (slot < 0) {
            if (KhanhFeatures.bool("auto_schematic_builder", "inventory_refill", true)
                && refillHotbar(client, required)) {
                nextAction = tick + 3;
                return;
            }
            String missing = "Missing material: " + wanted.getBlock().getName().getString();
            status(client, missing);
            if (KhanhFeatures.choice("auto_schematic_builder", "missing_material", "Pause").equals("Skip"))
                DEFERRED.put(target.toImmutable(), tick
                    + KhanhFeatures.number("auto_schematic_builder", "retry_missing", 1200));
            else {
                builderPauseReason = missing;
                builderPaused = true;
            }
            nextAction = tick + 10;
            return;
        }
        if (originalSlot < 0) originalSlot = client.player.getInventory().getSelectedSlot();
        if (client.player.getInventory().getSelectedSlot() != slot) {
            client.player.getInventory().setSelectedSlot(slot);
            client.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(slot));
        }
        if (client.interactionManager.interactBlock(client.player, Hand.MAIN_HAND, hit).isAccepted()) {
            client.player.swingHand(Hand.MAIN_HAND);
            pendingPlacement = target.toImmutable();
            pendingWanted = wanted;
            pendingDeadline = tick + KhanhFeatures.number("auto_schematic_builder", "verify_delay", 8);
        }
        nextAction = tick + KhanhFeatures.number("auto_schematic_builder", "delay", 3);
    }

    private static boolean checkPendingPlacement(MinecraftClient client) {
        if (pendingPlacement == null) return false;
        BlockState observed = client.world.getBlockState(pendingPlacement);
        if (observed.equals(pendingWanted)) {
            placements++;
            pendingPlacement = null;
            pendingWanted = null;
            return false;
        }
        if (tick < pendingDeadline) {
            release(client);
            return true;
        }
        if (!observed.isAir()) {
            status(client, "Wrong block state at " + pendingPlacement.toShortString()
                + "; adjust placement manually");
            if (KhanhFeatures.bool("auto_schematic_builder", "pause_wrong_state", true)) {
                builderPauseReason = "Wrong block state at " + pendingPlacement.toShortString();
                builderPaused = true;
            }
        } else {
            DEFERRED.put(pendingPlacement, tick
                + KhanhFeatures.number("auto_schematic_builder", "retry_failed_place", 40));
        }
        pendingPlacement = null;
        pendingWanted = null;
        return true;
    }

    private static boolean refillHotbar(MinecraftClient client, ItemStack required) {
        if (required.isEmpty() || client.player.currentScreenHandler != client.player.playerScreenHandler)
            return false;
        int emptyHotbar = -1;
        for (int i = 0; i < 9; i++) {
            if (client.player.getInventory().getStack(i).isEmpty()) {
                emptyHotbar = i;
                break;
            }
        }
        if (emptyHotbar < 0) return false;
        for (int i = 9; i < 36; i++) {
            if (client.player.getInventory().getStack(i).isOf(required.getItem())) {
                client.interactionManager.clickSlot(client.player.playerScreenHandler.syncId,
                    i, emptyHotbar, SlotActionType.SWAP, client.player);
                status(client, "Refilled hotbar from inventory");
                return true;
            }
        }
        return false;
    }

    private static BlockHitResult placementFace(MinecraftClient client, BlockPos target, double reach) {
        for (Direction towardSupport : new Direction[] {Direction.DOWN, Direction.NORTH,
            Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.UP}) {
            BlockPos support = target.offset(towardSupport);
            BlockState state = client.world.getBlockState(support);
            if (state.isAir() || state.getCollisionShape(client.world, support).isEmpty()
                || !state.getFluidState().isEmpty()) continue;
            Direction face = towardSupport.getOpposite();
            Vec3d hitPoint = Vec3d.ofCenter(support).add(face.getOffsetX() * 0.5,
                face.getOffsetY() * 0.5, face.getOffsetZ() * 0.5);
            if (client.player.getEyePos().squaredDistanceTo(hitPoint) > reach * reach) continue;
            HitResult visible = client.world.raycast(new RaycastContext(client.player.getEyePos(),
                hitPoint, RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, client.player));
            if (visible instanceof BlockHitResult block && block.getBlockPos().equals(support))
                return new BlockHitResult(hitPoint, face, support, false);
        }
        return null;
    }

    private static Direction visibleMiningFace(MinecraftClient client, BlockPos target, double reach) {
        Vec3d eye = client.player.getEyePos();
        Vec3d center = Vec3d.ofCenter(target);
        if (eye.squaredDistanceTo(center) > reach * reach) return null;
        HitResult hit = client.world.raycast(new RaycastContext(eye, center,
            RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, client.player));
        return hit instanceof BlockHitResult block && block.getBlockPos().equals(target)
            ? block.getSide() : null;
    }

    private static boolean mineable(MinecraftClient client, BlockPos pos) {
        BlockState state = client.world.getBlockState(pos);
        if (state.isAir() || !state.getFluidState().isEmpty() || state.isOf(Blocks.BEDROCK)
            || state.getHardness(client.world, pos) < 0) return false;
        String path = Registries.BLOCK.getId(state.getBlock()).getPath();
        if (KhanhFeatures.bool("auto_mineout", "skip_ores", true) && path.endsWith("_ore")) return false;
        if (KhanhFeatures.bool("auto_mineout", "skip_spawners", true) && state.isOf(Blocks.SPAWNER)) return false;
        if (KhanhFeatures.bool("auto_mineout", "skip_containers", true)
            && (path.contains("chest") || path.contains("shulker_box") || path.equals("barrel")
                || path.equals("hopper") || path.contains("furnace") || path.equals("dispenser")
                || path.equals("dropper") || path.equals("brewing_stand"))) return false;
        if (KhanhFeatures.bool("auto_mineout", "skip_valuable", true)
            && (state.isOf(Blocks.ANCIENT_DEBRIS) || state.isOf(Blocks.DIAMOND_BLOCK)
                || state.isOf(Blocks.EMERALD_BLOCK) || state.isOf(Blocks.NETHERITE_BLOCK)
                || state.isOf(Blocks.GOLD_BLOCK) || state.isOf(Blocks.IRON_BLOCK))) return false;
        BlockPos feet = client.player.getBlockPos();
        // Keep the player's footing and avoid opening a hole directly below it.
        int floorRadius = KhanhFeatures.bool("auto_mineout", "preserve_floor", true)
            ? KhanhFeatures.number("auto_mineout", "floor_margin", 2) : 1;
        if (pos.getY() < feet.getY()
            && Math.abs(pos.getX() - feet.getX()) <= floorRadius
            && Math.abs(pos.getZ() - feet.getZ()) <= floorRadius) return false;
        int liquidBuffer = KhanhFeatures.number("auto_mineout", "liquid_buffer", 1);
        for (Direction side : Direction.values()) {
            for (int distance = 1; distance <= liquidBuffer; distance++) {
                if (!client.world.getBlockState(pos.offset(side, distance)).getFluidState().isEmpty())
                    return false;
            }
        }
        return true;
    }

    private static boolean aimAt(MinecraftClient client, Vec3d target) {
        if (aimGoal == null || aimGoal.squaredDistanceTo(target) > 0.01) {
            aimGoal = target;
            aimStableSince = -1;
        }
        Vec3d difference = target.subtract(client.player.getEyePos());
        double horizontal = Math.hypot(difference.x, difference.z);
        float yaw = (float) Math.toDegrees(Math.atan2(-difference.x, difference.z));
        float pitch = (float) -Math.toDegrees(Math.atan2(difference.y, horizontal));
        float yawDelta = MathHelper.wrapDegrees(yaw - client.player.getYaw());
        float pitchDelta = pitch - client.player.getPitch();
        String moduleId = activeMode.equals("builder") ? "auto_schematic_builder" : "auto_mineout";
        float speed = KhanhFeatures.number(moduleId, "aim_speed", 35);
        float smoothness = (float) KhanhFeatures.decimal(moduleId, "look_smoothness", 0.45);
        client.player.setYaw(client.player.getYaw()
            + MathHelper.clamp(yawDelta * smoothness, -speed, speed));
        client.player.setPitch(client.player.getPitch()
            + MathHelper.clamp(pitchDelta * smoothness, -speed * 0.72F, speed * 0.72F));
        float remainingYaw = MathHelper.wrapDegrees(yaw - client.player.getYaw());
        float remainingPitch = pitch - client.player.getPitch();
        if (Math.abs(remainingYaw) >= 4.0F || Math.abs(remainingPitch) >= 4.0F) {
            aimStableSince = -1;
            return false;
        }
        if (aimStableSince < 0) aimStableSince = tick;
        return tick - aimStableSince >= KhanhFeatures.number(moduleId, "look_settle", 2);
    }

    private static void cancelMining(MinecraftClient client) {
        if (miningTarget != null && client.interactionManager != null)
            client.interactionManager.cancelBlockBreaking();
        miningTarget = null;
        miningFace = null;
    }

    private static void release(MinecraftClient client) {
        NAVIGATOR.stop(client);
        cancelMining(client);
        aimGoal = null;
        aimStableSince = -1;
        idleLooking = false;
    }

    private static void reset(MinecraftClient client) {
        release(client);
        if (originalSlot >= 0 && client.player != null
            && KhanhFeatures.bool("auto_schematic_builder", "restore_slot", true)) {
            client.player.getInventory().setSelectedSlot(originalSlot);
            client.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(originalSlot));
        }
        originalSlot = -1;
        remoteTarget = null;
        remoteCursor = 0;
        DEFERRED.clear();
        nextAction = 0;
        placements = 0;
        pendingPlacement = null;
        pendingWanted = null;
        placementKey = "";
        activePlacement = null;
        activeMode = "";
        lastIdleGlanceTick = tick;
    }

    private static void status(MinecraftClient client, String message) {
        if (activeMode.equals("builder")) builderActivity = message;
        if (activeMode.equals("builder")
            && !KhanhFeatures.bool("auto_schematic_builder", "status_messages", true)) return;
        if (client.player != null && tick - lastStatus >= 80) {
            client.player.sendMessage(Text.literal("[Khanh Client] " + message), true);
            lastStatus = tick;
        }
    }

    private record Bounds(BlockPos min, BlockPos max) {
        static Bounds of(BlockPos a, BlockPos b) {
            return new Bounds(new BlockPos(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()),
                Math.min(a.getZ(), b.getZ())), new BlockPos(Math.max(a.getX(), b.getX()),
                Math.max(a.getY(), b.getY()), Math.max(a.getZ(), b.getZ())));
        }
        long volume() {
            return (long) (max.getX() - min.getX() + 1) * (max.getY() - min.getY() + 1)
                * (max.getZ() - min.getZ() + 1);
        }
        boolean contains(BlockPos pos) {
            return pos.getX() >= min.getX() && pos.getX() <= max.getX()
                && pos.getY() >= min.getY() && pos.getY() <= max.getY()
                && pos.getZ() >= min.getZ() && pos.getZ() <= max.getZ();
        }
        BlockPos at(long index) {
            long width = max.getX() - min.getX() + 1L;
            long depth = max.getZ() - min.getZ() + 1L;
            int x = min.getX() + (int) (index % width);
            int z = min.getZ() + (int) ((index / width) % depth);
            int y = min.getY() + (int) (index / (width * depth));
            return new BlockPos(x, y, z);
        }
    }
}
