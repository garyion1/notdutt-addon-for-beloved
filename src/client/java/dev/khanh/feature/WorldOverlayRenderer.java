package dev.khanh.feature;

import dev.khanh.KhanhClient;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldExtractionContext;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.enums.ChestType;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.MobSpawnerBlockEntity;
import net.minecraft.block.entity.TrialSpawnerBlockEntity;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.DrawStyle;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.thrown.EnderPearlEntity;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.entity.projectile.TridentEntity;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.debug.gizmo.GizmoDrawing;
import org.joml.Vector3fc;

public final class WorldOverlayRenderer {
    private static final int CHUNK_LAYER_Y = 55;
    private static final int PINK = 0xFFFF20AA;
    private static final int PINK_FILL = 0x35FF009D;
    private static final int RED = 0xFFFF263D;
    private static final int RED_FILL = 0x35FF1028;
    private static final int CYAN = 0xFF42D9FF;
    private static final int CYAN_FILL = 0x3042D9FF;
    private static final int GREEN = 0xFF52FF9A;
    private static final int GREEN_FILL = 0x3052FF9A;
    private static final int GOLD = 0xFFFFB21A;
    private static BlockPos outlineTarget;
    private static Vec3d outlineStart;
    private static long outlineStartedAt;

    private record StorageCandidate(BlockPos pos, WorldScanner.StorageType type, double distanceSquared) {}

    private WorldOverlayRenderer() {
    }

    public static void extract(WorldExtractionContext context) {
        // The menu is interactive: don't spend its frame budget drawing thousands
        // of cosmetic/ESP gizmos behind the blurred panels. Scanning stays live.
        if (net.minecraft.client.MinecraftClient.getInstance().currentScreen instanceof dev.khanh.gui.KhanhClickGui) return;
        if (!anyWorldOverlayEnabled()) {
            return;
        }

        ClientWorld world = context.world();
        try (GizmoDrawing.CollectorScope ignored = context.worldRenderer().startDrawingGizmos()) {
            if (FreecamService.freeLookActive() && KhanhFeatures.bool("freelook", "see_nametag", true)) {
                guard("renderFreelookName", () -> renderFreelookName());
            }
            if (VisualEffectService.enabled()) guard("visualEffects", () -> VisualEffectService.render(context.camera().getCameraPos()));
            if (KhanhFeatures.enabled("player_esp")) {
                guard("renderPlayers", () -> renderPlayers(world, context.camera().getCameraPos()));
            }
            if (KhanhFeatures.enabled("name_tags")) {
                guard("renderNameTags", () -> renderNameTags(world, context.camera().getCameraPos()));
            }
            if (KhanhFeatures.enabled("hitbox_outline")) {
                guard("renderHitboxes", () -> renderHitboxes(world, context.camera().getCameraPos(), context.tickCounter().getTickProgress(false)));
            }
            if (KhanhFeatures.enabled("motion_scanner")) {
                guard("renderMotion", () -> MotionScanner.render());
            }
            if (KhanhFeatures.enabled("see_invisible") && !KhanhFeatures.enabled("player_esp")) {
                guard("renderInvisiblePlayers", () -> renderInvisiblePlayers(world, context.camera().getCameraPos()));
            }
            if (KhanhFeatures.enabled("block_entity_debug") || KhanhFeatures.enabled("spawner_chunk")) {
                guard("renderBlockEntities", () -> renderBlockEntities(world, context.camera().getCameraPos()));
            }
            if (KhanhFeatures.enabled("storage_esp")) {
                guard("renderStorage", () -> renderStorage(world, context.camera().getCameraPos()));
            } else {
                StorageGlowRenderer.clear();
            }
            if (KhanhFeatures.enabled("hole_esp")) {
                guard("renderHoles", () -> renderHoles());
            }
            if (KhanhFeatures.enabled("sus_esp")) {
                guard("renderSuspiciousChunks", () -> renderSuspiciousChunks(context));
            } else {
                StorageGlowRenderer.clearPlates();
            }
            if (KhanhFeatures.enabled("prime_chunk_finder")) {
                guard("renderPrimeChunks", () -> renderPrimeChunks(context));
            }
            if (KhanhFeatures.enabled("light_finder")) {
                guard("renderUndergroundLight", () -> renderUndergroundLight(context));
            }
            if (KhanhFeatures.enabled("amethyst_esp")) {
                guard("renderAmethyst", () -> renderAmethyst(context.camera().getCameraPos()));
            }
            if (KhanhFeatures.enabled("block_outline")) {
                guard("renderTargetBlock", () -> renderTargetBlock(world));
            }
            if (KhanhFeatures.enabled("block_notifier")) {
                guard("renderNotifiedBlocks", () -> renderNotifiedBlocks(world, context.camera().getCameraPos()));
            }
            if (KhanhFeatures.enabled("bedrock_void_esp")) {
                guard("renderBedrockVoids", () -> renderBedrockVoids());
            }
            if (KhanhFeatures.enabled("projectile_pearl")) {
                guard("renderPearlPredictions", () -> renderPearlPredictions(world, context.camera().getCameraPos()));
            }
            if (KhanhFeatures.enabled("base_esp") || KhanhFeatures.enabled("tuff_chunk_finder")) {
                guard("renderChunkSignals", () -> renderChunkSignals(context));
            }
            if (KhanhFeatures.enabled("entity_debug")) {
                guard("renderEntities", () -> renderEntities(world, context.camera().getCameraPos()));
            }
            if (KhanhFeatures.enabled("redstone_activity")) {
                guard("renderRedstoneActivity", () -> renderRedstoneActivity(context));
            }
            if (KhanhFeatures.enabled("auto_mineout")
                && KhanhFeatures.bool("auto_mineout", "show_boundary", true)
                && WorkAutomationService.mineoutBox() != null) {
                GizmoDrawing.box(WorkAutomationService.mineoutBox(),
                    DrawStyle.stroked(KhanhFeatures.color("auto_mineout", "boundary_color", 0xFF83D7F5),
                        1.5F)).ignoreOcclusion();
            }
        } catch (Exception exception) {
            KhanhClient.LOGGER.debug("Skipped a Khanh Client world overlay frame", exception);
        }
    }

    private static final java.util.Set<String> FAILED_SECTIONS = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /**
     * One overlay throwing must not take every other ESP down with it: each module renders in its
     * own guard, and the first failure of each is logged loudly instead of hidden at debug level.
     */
    private static void guard(String section, Runnable render) {
        try {
            render.run();
        } catch (RuntimeException exception) {
            if (FAILED_SECTIONS.add(section)) KhanhClient.LOGGER.warn("World overlay {} failed; other overlays keep rendering", section, exception);
        }
    }

    private static boolean anyWorldOverlayEnabled() {
        return FreecamService.freeLookActive() || VisualEffectService.enabled() || KhanhFeatures.enabled("player_esp")
            || KhanhFeatures.enabled("name_tags")
            || KhanhFeatures.enabled("see_invisible")
            || KhanhFeatures.enabled("block_entity_debug")
            || KhanhFeatures.enabled("storage_esp")
            || KhanhFeatures.enabled("spawner_chunk")
            || KhanhFeatures.enabled("hole_esp")
            || KhanhFeatures.enabled("sus_esp")
            || KhanhFeatures.enabled("prime_chunk_finder")
            || KhanhFeatures.enabled("light_finder")
            || KhanhFeatures.enabled("amethyst_esp")
            || KhanhFeatures.enabled("block_outline")
            || KhanhFeatures.enabled("block_notifier")
            || KhanhFeatures.enabled("bedrock_void_esp")
            || KhanhFeatures.enabled("projectile_pearl")
            || KhanhFeatures.enabled("base_esp")
            || KhanhFeatures.enabled("tuff_chunk_finder")
            || KhanhFeatures.enabled("entity_debug")
            || KhanhFeatures.enabled("auto_mineout")
            || KhanhFeatures.enabled("redstone_activity");
    }

    private static void renderBedrockVoids() {
        int color = KhanhFeatures.color("bedrock_void_esp", "color", 0xFF8B5CFF);
        boolean filled = KhanhFeatures.bool("bedrock_void_esp", "filled", true);
        int alpha = KhanhFeatures.number("bedrock_void_esp", "fill_alpha", 72);
        boolean labels = KhanhFeatures.bool("bedrock_void_esp", "labels", true);
        for (BlockPos pos : KhanhFeatures.scanner().bedrockVoidCells()) {
            DrawStyle style = filled
                ? DrawStyle.filledAndStroked(color, 2.0F, translucent(color, alpha))
                : DrawStyle.stroked(color, 2.0F);
            GizmoDrawing.box(new Box(pos).expand(0.025), style, false).ignoreOcclusion();
            if (labels) {
                GizmoDrawing.blockLabel("VOID", pos.up(), color, 0, 0.021F);
            }
        }
    }

    private static void renderPearlPredictions(ClientWorld world, Vec3d camera) {
        double range = KhanhFeatures.number("projectile_pearl", "range", 160);
        double rangeSquared = range * range;
        int color = KhanhFeatures.color("projectile_pearl", "color", 0xFF66D9FF);
        float width = (float) KhanhFeatures.decimal("projectile_pearl", "line_width", 1.5);
        boolean impactMarker = KhanhFeatures.bool("projectile_pearl", "impact", true);
        boolean labels = KhanhFeatures.bool("projectile_pearl", "labels", true);
        boolean pearls = KhanhFeatures.bool("projectile_pearl", "pearls", true);
        boolean arrows = KhanhFeatures.bool("projectile_pearl", "arrows", true);
        boolean tridents = KhanhFeatures.bool("projectile_pearl", "tridents", true);
        int rendered = 0;
        for (Entity entity : world.getEntities()) {
            boolean pearl = pearls && entity instanceof EnderPearlEntity;
            boolean arrow = arrows && entity instanceof ArrowEntity;
            boolean trident = tridents && entity instanceof TridentEntity;
            if ((!pearl && !arrow && !trident) || !entity.isAlive()
                || entity.getVelocity().lengthSquared() < 0.0004
                || entity.squaredDistanceTo(camera) > rangeSquared) {
                continue;
            }
            if (++rendered > 24) break;
            Vec3d position = entity.getEntityPos();
            Vec3d velocity = entity.getVelocity();
            Vec3d predicted = position;
            boolean impacted = false;
            for (int step = 0; step < 100; step++) {
                Vec3d next = position.add(velocity);
                HitResult hit = world.raycast(new RaycastContext(position, next,
                    RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, entity));
                if (hit.getType() != HitResult.Type.MISS) {
                    next = hit.getPos();
                    impacted = true;
                }
                GizmoDrawing.line(position, next, translucent(color, 220), width).ignoreOcclusion();
                predicted = next;
                if (impacted || next.y < world.getBottomY() - 2) break;
                position = next;
                velocity = velocity.multiply(0.99).add(0.0, pearl ? -0.03 : -0.05, 0.0);
            }
            BlockPos landing = BlockPos.ofFloored(predicted);
            if (impactMarker) {
                GizmoDrawing.box(new Box(predicted.x - 0.16, predicted.y - 0.16, predicted.z - 0.16,
                    predicted.x + 0.16, predicted.y + 0.16, predicted.z + 0.16),
                    DrawStyle.filledAndStroked(color, 2.0F, translucent(color, 54)), false).ignoreOcclusion();
            }
            if (labels) {
                int distance = (int) Math.round(camera.distanceTo(predicted));
                GizmoDrawing.blockLabel((pearl ? "PEARL" : trident ? "TRIDENT" : "ARROW")
                    + " • " + distance + "m", landing.up(), color, 0, 0.021F);
            }
        }
    }

    private static void renderFreelookName() {
        var player = net.minecraft.client.MinecraftClient.getInstance().player;
        if (player == null) return;
        GizmoDrawing.entityLabel(player, VisualTheme.accent(),
            PrivacyService.protectedName(player.getGameProfile().name()), 0, 0.028F);
    }

    private static void renderInvisiblePlayers(ClientWorld world, Vec3d camera) {
        double range = KhanhFeatures.number("see_invisible", "range", 96);
        int color = KhanhFeatures.color("see_invisible", "color", 0xFFB5D9FF);
        int opacity = KhanhFeatures.number("see_invisible", "opacity", 58);
        for (AbstractClientPlayerEntity player : world.getPlayers()) {
            if (player == net.minecraft.client.MinecraftClient.getInstance().player || !player.isAlive()
                || !player.isInvisible() || player.squaredDistanceTo(camera) > range * range) continue;
            GizmoDrawing.box(player.getBoundingBox().expand(.025),
                DrawStyle.filledAndStroked(color, 1.4F, translucent(color, opacity)), false).ignoreOcclusion();
            GizmoDrawing.entityLabel(player, color,
                PrivacyService.protectedName(player.getName().getString()) + " • INVISIBLE", 0, .023F);
        }
    }

    private static void renderPlayers(ClientWorld world, Vec3d camera) {
        double range = KhanhFeatures.number("player_esp", "range", 128);
        int rendered = 0;
        for (AbstractClientPlayerEntity player : world.getPlayers()) {
            if (rendered >= 48) break;
            if (player == net.minecraft.client.MinecraftClient.getInstance().player || !player.isAlive()
                || player.squaredDistanceTo(camera) > range * range) {
                continue;
            }
            rendered++;
            String playerName = player.getGameProfile().name();
            boolean friend = KhanhFeatures.enabled("friends") && FriendService.isFriend(playerName);
            int accent = friend && KhanhFeatures.bool("friends", "highlight_esp", true)
                ? FriendService.color() : KhanhFeatures.color("player_esp", "color", 0xFFD2A8FF);
            if (KhanhFeatures.bool("player_esp", "glow_outline", false)) {
                GizmoDrawing.box(player.getBoundingBox().expand(0.09),
                    DrawStyle.stroked(translucent(accent, 42), 3.5F), false).ignoreOcclusion();
                GizmoDrawing.box(player.getBoundingBox().expand(0.045),
                    DrawStyle.stroked(translucent(accent, 105), 1.8F), false).ignoreOcclusion();
            }
            int fill = KhanhFeatures.bool("player_esp", "soft_fill", false)
                ? translucent(accent, 28) : translucent(accent, 0);
            KhanhEsp.draw("player_esp", player.getBoundingBox().expand(0.05), accent, fill >>> 24 == 0 ? 40 : fill >>> 24, true);
            if (KhanhFeatures.bool("player_esp", "glow_ring", true)) {
                double base = KhanhFeatures.decimal("player_esp", "ring_size", .72);
                double pulse = Math.sin(System.nanoTime() / 480_000_000.0 + player.getId()) * .045;
                Vec3d feet = player.getLerpedPos(
                    net.minecraft.client.MinecraftClient.getInstance().getRenderTickCounter().getTickProgress(true))
                    .add(0, .045, 0);
                GizmoDrawing.circle(feet, (float) (base + .11 + pulse),
                    DrawStyle.stroked(translucent(accent, 35), 5.0F)).ignoreOcclusion();
                GizmoDrawing.circle(feet.add(0, .005, 0), (float) (base + .035 + pulse),
                    DrawStyle.stroked(translucent(accent, 95), 2.5F)).ignoreOcclusion();
                GizmoDrawing.circle(feet.add(0, .01, 0), (float) (base + pulse),
                    DrawStyle.stroked(translucent(accent, 225), 1.15F)).ignoreOcclusion();
            }
            if (KhanhFeatures.bool("player_esp", "tracers", false)) {
                // Interpolated centre: the raw tick box steps 20 times a second and shakes the line.
                float tracerDelta = net.minecraft.client.MinecraftClient.getInstance().getRenderTickCounter().getTickProgress(true);
                StorageTracerRenderer.draw(camera, player.getLerpedPos(tracerDelta).add(0, player.getHeight() / 2.0, 0),
                    translucent(accent, 205), (float) KhanhFeatures.decimal("player_esp", "tracer_width", 0.65));
            }
            if (KhanhFeatures.bool("player_esp", "labels", true)) {
                String label = PrivacyService.protectedName(player.getName().getString()) + (friend ? " • FRIEND" : "");
                if (KhanhFeatures.bool("player_esp", "label_health", true)) {
                    label += " • " + Math.round(player.getHealth() + player.getAbsorptionAmount()) + " HP";
                }
                if (KhanhFeatures.bool("player_esp", "label_armor", false)) {
                    label += " • " + player.getArmor() + " ARM";
                }
                if (KhanhFeatures.bool("player_esp", "label_item", false)
                    && !player.getMainHandStack().isEmpty()) {
                    String item = player.getMainHandStack().getName().getString();
                    label += " • " + (item.length() > 18 ? item.substring(0, 18) + "…" : item);
                }
                GizmoDrawing.entityLabel(player, accent, label, 0, 0.025F);
            }
        }
    }

    private static void renderNameTags(ClientWorld world, Vec3d camera) {
        double range = KhanhFeatures.number("name_tags", "range", 48);
        double rangeSquared = range * range;
        int color = KhanhFeatures.color("name_tags", "color", 0xFFEDEDF4);
        boolean players = KhanhFeatures.bool("name_tags", "players", true);
        boolean mobs = KhanhFeatures.bool("name_tags", "mobs", false);
        boolean items = KhanhFeatures.bool("name_tags", "items", false);
        boolean playerEspLabels = KhanhFeatures.enabled("player_esp")
            && KhanhFeatures.bool("player_esp", "labels", true);
        String health = KhanhFeatures.choice("name_tags", "health", "Both");
        boolean healthColours = KhanhFeatures.bool("name_tags", "health_colours", true);
        double itemRange = Math.min(range, KhanhFeatures.number("name_tags", "item_range", 24));
        int rendered = 0, itemLabels = 0;
        for (Entity entity : world.getEntities()) {
            if (rendered >= 64) break;
            if (entity == net.minecraft.client.MinecraftClient.getInstance().player || !entity.isAlive()
                || entity.squaredDistanceTo(camera) > rangeSquared) continue;
            String label;
            int labelColour = color;
            if (entity instanceof LivingEntity living && (entity instanceof AbstractClientPlayerEntity ? players : mobs)) {
                if (entity instanceof AbstractClientPlayerEntity && playerEspLabels) continue;
                String name = entity instanceof AbstractClientPlayerEntity player
                    ? PrivacyService.protectedName(player.getName().getString()) : living.getName().getString();
                float hp = living.getHealth() + living.getAbsorptionAmount();
                float ratio = Math.max(0.0F, Math.min(1.0F, living.getHealth() / Math.max(1.0F, living.getMaxHealth())));
                label = name + healthText(health, hp, ratio);
                if (healthColours && !health.equals("Off")) {
                    labelColour = 0xFF000000 | (ratio > 0.6F ? 0x5DF27A : ratio > 0.3F ? 0xFFD84A : 0xFF5555);
                }
            } else if (entity instanceof ItemEntity item) {
                if (!items || item.getStack().isEmpty() || itemLabels >= 48
                    || entity.squaredDistanceTo(camera) > itemRange * itemRange) continue;
                label = item.getStack().getName().getString();
                if (item.getStack().getCount() > 1) label += " \u00d7" + item.getStack().getCount();
                itemLabels++;
            } else continue;
            GizmoDrawing.entityLabel(entity, labelColour, label, 0, 0.025F);
            rendered++;
        }
    }

    /** " • 18 HP", " ▮▮▮▮▮▮▯▯▯▯" or both, depending on the Health option. */
    private static String healthText(String mode, float hp, float ratio) {
        int filled = Math.round(ratio * 10.0F);
        String bar = "\u25AE".repeat(filled) + "\u25AF".repeat(10 - filled);
        return switch (mode) {
            case "Off" -> "";
            case "Number" -> " \u2022 " + Math.round(hp) + " HP";
            case "Bar" -> " " + bar;
            default -> " \u2022 " + Math.round(hp) + " " + bar;
        };
    }

    /** Hitbox Outline: the exact box the game uses for hits, plus an eye line showing where they look. */
    private static void renderHitboxes(ClientWorld world, Vec3d camera, float tickProgress) {
        boolean players = KhanhFeatures.bool("hitbox_outline", "players", true);
        boolean mobs = KhanhFeatures.bool("hitbox_outline", "mobs", false);
        boolean eyes = KhanhFeatures.bool("hitbox_outline", "eyes", true);
        boolean walls = KhanhFeatures.bool("hitbox_outline", "through_walls", false);
        int color = KhanhFeatures.color("hitbox_outline", "color", 0xFF38BDF8);
        float width = (float) KhanhFeatures.decimal("hitbox_outline", "width", 1.5);
        double range = KhanhFeatures.number("hitbox_outline", "range", 48);
        var self = net.minecraft.client.MinecraftClient.getInstance().player;
        for (Entity entity : world.getEntities()) {
            if (entity == self || !entity.isAlive() || entity.isInvisible() || entity.squaredDistanceTo(camera) > range * range) continue;
            boolean isPlayer = entity instanceof net.minecraft.entity.player.PlayerEntity;
            if (isPlayer ? !players : !(mobs && entity instanceof net.minecraft.entity.mob.MobEntity)) continue;
            Vec3d lerped = entity.getLerpedPos(tickProgress);
            Box box = entity.getBoundingBox().offset(lerped.subtract(entity.getEntityPos()));
            KhanhEsp.draw("hitbox_outline", box, color, 30, walls);
            if (eyes && entity instanceof LivingEntity living) {
                double eye = lerped.y + living.getStandingEyeHeight();
                Box eyeBox = new Box(box.minX, eye - 0.01, box.minZ, box.maxX, eye + 0.01, box.maxZ);
                var line = GizmoDrawing.box(eyeBox, DrawStyle.stroked(0xFFFF5555, width), false);
                if (walls) line.ignoreOcclusion();
                Vec3d look = living.getRotationVec(tickProgress).multiply(1.2);
                Vec3d from = new Vec3d(lerped.x, eye, lerped.z);
                var arrow = GizmoDrawing.line(from, from.add(look), 0xFFFFFFFF, width);
                if (walls) arrow.ignoreOcclusion();
            }
        }
    }

    private static void renderBlockEntities(ClientWorld world, Vec3d camera) {
        double debugRange = KhanhFeatures.number("block_entity_debug", "range", 128);
        double spawnerRange = KhanhFeatures.number("spawner_chunk", "range", 128);
        double maximumRange = Math.max(KhanhFeatures.enabled("block_entity_debug") ? debugRange : 0.0,
            KhanhFeatures.enabled("spawner_chunk") ? spawnerRange : 0.0);
        Set<BlockPos> renderedSpawners = new HashSet<>();
        for (BlockEntity blockEntity : new ArrayList<>(world.getBlockEntities())) {
            BlockPos pos = blockEntity.getPos();
            double squaredDistance = pos.getSquaredDistance(camera.x, camera.y, camera.z);
            if (squaredDistance > maximumRange * maximumRange) {
                continue;
            }
            boolean spawner = blockEntity instanceof MobSpawnerBlockEntity || blockEntity instanceof TrialSpawnerBlockEntity;
            if (KhanhFeatures.enabled("block_entity_debug") && squaredDistance <= debugRange * debugRange) {
                Identifier id = Registries.BLOCK_ENTITY_TYPE.getId(blockEntity.getType());
                String label = id == null ? "block entity" : id.getPath().replace('_', ' ');
                KhanhEsp.draw("block_entity_debug", new Box(pos).expand(0.012), KhanhFeatures.color("block_entity_debug", "color", CYAN), 60, true);
                if (KhanhFeatures.bool("block_entity_debug", "labels", true)) {
                    GizmoDrawing.blockLabel(label, pos.up(), CYAN, 0, 0.02F);
                }
            }
            if (spawner && KhanhFeatures.enabled("spawner_chunk") && squaredDistance <= spawnerRange * spawnerRange) {
                renderedSpawners.add(pos);
                renderSpawner(world, pos, blockEntity instanceof TrialSpawnerBlockEntity, Math.sqrt(squaredDistance), camera);
            }
        }
        if (KhanhFeatures.enabled("spawner_chunk")) {
            for (BlockPos pos : KhanhFeatures.scanner().notifiedSpawners()) {
                double squaredDistance = pos.getSquaredDistance(camera.x, camera.y, camera.z);
                if (squaredDistance > spawnerRange * spawnerRange || !renderedSpawners.add(pos)) {
                    continue;
                }
                net.minecraft.block.BlockState state = world.getBlockState(pos);
                if (state.isOf(net.minecraft.block.Blocks.SPAWNER) || state.isOf(net.minecraft.block.Blocks.TRIAL_SPAWNER)) {
                    renderSpawner(world, pos, state.isOf(net.minecraft.block.Blocks.TRIAL_SPAWNER), Math.sqrt(squaredDistance), camera);
                }
            }
        }
    }

    private static void renderStorage(ClientWorld world, Vec3d camera) {
        boolean ultimate = KhanhFeatures.bool("storage_esp", "ultimate_range", false);
        double range = ultimate ? Double.MAX_VALUE / 4 : KhanhFeatures.bool("storage_esp", "all_loaded", true)
            ? Math.min(32, net.minecraft.client.MinecraftClient.getInstance().options.getClampedViewDistance()) * 16.0 + 32.0
            : KhanhFeatures.number("storage_esp", "range", 128);
        double rangeSquared = range * range;
        int maximumBoxes = KhanhFeatures.number("storage_esp", "max_render", 500);
        if (ultimate) maximumBoxes = Math.max(maximumBoxes, 3000);
        boolean mergeDoubleChests = KhanhFeatures.bool("storage_esp", "merge_double_chests", true);
        boolean labels = KhanhFeatures.bool("storage_esp", "labels", false);
        boolean labelDistance = KhanhFeatures.bool("storage_esp", "label_distance", true);
        boolean tracers = KhanhFeatures.bool("storage_esp", "tracers", true);
        float tracerWidth = (float) KhanhFeatures.decimal("storage_esp", "tracer_width", 1.0);
        boolean hideOpened = KhanhFeatures.bool("storage_esp", "hide_opened", false);

        if (maximumBoxes <= 0) { StorageGlowRenderer.clear(); return; }
        // Keep only the nearest visible candidates. Sorting the entire scan result
        // each frame caused large FPS drops around dense storage rooms.
        PriorityQueue<StorageCandidate> nearest = new PriorityQueue<>(
            Comparator.comparingDouble(StorageCandidate::distanceSquared).reversed());
        for (Map.Entry<BlockPos, WorldScanner.StorageType> entry : KhanhFeatures.scanner().storageEntries().entrySet()) {
            BlockPos pos = storageRenderPos(world, entry.getKey(), entry.getValue(), mergeDoubleChests);
            WorldScanner.StorageType type = entry.getValue();
            if (pos == null || !storageEnabled(type)
                || (hideOpened && storageInteracted(world, pos, type, mergeDoubleChests))) continue;
            double distanceSquared = pos.getSquaredDistance(camera.x, camera.y, camera.z);
            if (distanceSquared > rangeSquared) continue;
            if (nearest.size() < maximumBoxes) {
                nearest.add(new StorageCandidate(pos, type, distanceSquared));
            } else if (distanceSquared < nearest.peek().distanceSquared()) {
                nearest.poll();
                nearest.add(new StorageCandidate(pos, type, distanceSquared));
            }
        }

        List<StorageGlowRenderer.Glow> glows = new ArrayList<>(nearest.size());
        for (StorageCandidate entry : nearest) {
            BlockPos pos = entry.pos();
            WorldScanner.StorageType type = entry.type();
            boolean interacted = storageInteracted(world, pos, type, mergeDoubleChests);
            int color = interacted ? 0xFF646464 : vibrant(storageColor(world, pos, type));
            Box box = storageBox(world, pos, type, mergeDoubleChests);
            // Solid see-through blocks with crisp edges, drawn by StorageGlowRenderer with the entities.
            float pulse = interacted ? 0.7F : 1.0F;
            glows.add(new StorageGlowRenderer.Glow(box, color, pulse));
            if (!KhanhFeatures.choice("storage_esp", "esp_style", "Khanh").equals("Classic")) KhanhEsp.corners(box, 0xFF000000 | color, 2.4F, true);
            if (tracers) {
                StorageTracerRenderer.draw(camera, box.getCenter(), translucent(color, 255), tracerWidth);
            }
            if (labels) {
                String label = storageLabel(type);
                if (labelDistance) label += String.format(Locale.ROOT, " • %.0fm", Math.sqrt(entry.distanceSquared()));
                GizmoDrawing.blockLabel(label, BlockPos.ofFloored(box.getCenter()).up(), color, 0, 0.02F);
            }
        }
        StorageGlowRenderer.set(glows);
    }

    private static BlockPos storageRenderPos(ClientWorld world, BlockPos pos,
                                              WorldScanner.StorageType type, boolean mergeDoubleChests) {
        if (!mergeDoubleChests || (type != WorldScanner.StorageType.CHEST
            && type != WorldScanner.StorageType.TRAPPED_CHEST)) return pos;
        BlockState state = world.getBlockState(pos);
        if (!(state.getBlock() instanceof ChestBlock)) return pos;
        return state.get(ChestBlock.CHEST_TYPE) == ChestType.RIGHT ? null : pos;
    }

    private static boolean storageInteracted(ClientWorld world, BlockPos pos,
                                              WorldScanner.StorageType type, boolean mergeDoubleChests) {
        if (KhanhFeatures.scanner().storageInteracted(pos)) return true;
        if (!mergeDoubleChests || (type != WorldScanner.StorageType.CHEST
            && type != WorldScanner.StorageType.TRAPPED_CHEST)) return false;
        BlockPos neighbour = chestNeighbour(world, pos);
        return neighbour != null && KhanhFeatures.scanner().storageInteracted(neighbour);
    }

    private static BlockPos chestNeighbour(ClientWorld world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        if (!(state.getBlock() instanceof ChestBlock) || state.get(ChestBlock.CHEST_TYPE) == ChestType.SINGLE) return null;
        Direction facing = state.get(ChestBlock.FACING);
        Direction neighbour = state.get(ChestBlock.CHEST_TYPE) == ChestType.LEFT
            ? facing.rotateYClockwise() : facing.rotateYCounterclockwise();
        return pos.offset(neighbour);
    }

    private static String storageLabel(WorldScanner.StorageType type) {
        return switch (type) {
            case CHEST -> "Chest";
            case TRAPPED_CHEST -> "Trapped Chest";
            case ENDER_CHEST -> "Ender Chest";
            case SHULKER -> "Shulker";
            case BARREL -> "Barrel";
            case SPAWNER -> "Spawner";
            case TRIAL_SPAWNER -> "Trial Spawner";
            case HOPPER -> "Hopper / Utility";
            case FURNACE -> "Furnace / Brewing";
            case ENCHANTING_TABLE -> "Enchanting Table";
            case MOVING_PISTON -> "Moving Piston";
        };
    }

    private static Box storageBox(ClientWorld world, BlockPos pos, WorldScanner.StorageType type,
                                  boolean mergeDoubleChests) {
        double x1 = pos.getX();
        double y1 = pos.getY();
        double z1 = pos.getZ();
        double x2 = x1 + 1.0;
        double y2 = y1 + 1.0;
        double z2 = z1 + 1.0;
        if (type == WorldScanner.StorageType.CHEST || type == WorldScanner.StorageType.TRAPPED_CHEST
            || type == WorldScanner.StorageType.ENDER_CHEST) {
            x1 += 0.0625;
            z1 += 0.0625;
            x2 -= 0.0625;
            y2 -= 0.125;
            z2 -= 0.0625;
            BlockState state = world.getBlockState(pos);
            if (mergeDoubleChests
                && (type == WorldScanner.StorageType.CHEST || type == WorldScanner.StorageType.TRAPPED_CHEST)
                && state.getBlock() instanceof ChestBlock && state.get(ChestBlock.CHEST_TYPE) != ChestType.SINGLE) {
                Direction facing = state.get(ChestBlock.FACING);
                ChestType chestType = state.get(ChestBlock.CHEST_TYPE);
                Direction neighbour = chestType == ChestType.LEFT ? facing.rotateYClockwise() : facing.rotateYCounterclockwise();
                if (neighbour == Direction.WEST) x1 = pos.getX() - 1.0 + 0.0625;
                if (neighbour == Direction.EAST) x2 = pos.getX() + 2.0 - 0.0625;
                if (neighbour == Direction.NORTH) z1 = pos.getZ() - 1.0 + 0.0625;
                if (neighbour == Direction.SOUTH) z2 = pos.getZ() + 2.0 - 0.0625;
            }
        }
        return new Box(x1, y1, z1, x2, y2, z2);
    }

    private static void renderSpawner(ClientWorld world, BlockPos pos, boolean trial, double distance, Vec3d camera) {
        String type = trial ? "Trial Spawner" : "Mob Spawner";
        String label = KhanhFeatures.bool("spawner_chunk", "distance", true)
            ? String.format(Locale.ROOT, "%s • %.0fm", type, distance) : type;
        if (KhanhFeatures.bool("spawner_chunk", "box", true)) {
            GizmoDrawing.box(new Box(pos).expand(0.02),
                DrawStyle.filledAndStroked(RED, 2.5F, RED_FILL), false).ignoreOcclusion();
        }
        if (KhanhFeatures.bool("spawner_chunk", "beam", true)) {
            Vec3d bottom = Vec3d.ofCenter(pos).add(0.0, -0.35, 0.0);
            Vec3d top = new Vec3d(bottom.x, Math.max(bottom.y + 12.0, world.getTopYInclusive() + 16.0), bottom.z);
            double topY = top.y;
            Box pillar = new Box(pos.getX() - 1.0, pos.getY(), pos.getZ() - 1.0,
                pos.getX() + 2.0, topY, pos.getZ() + 2.0);
            GizmoDrawing.box(pillar, DrawStyle.filledAndStroked(0xDFFF263D, 2.0F,
                translucent(RED, pulseAlpha(38))), false).ignoreOcclusion();
            // A bright fixed centre line keeps the three-by-three pillar readable
            // from long range without bobbing with the camera.
            GizmoDrawing.line(bottom, top, 0x68FF1028, 14.0F).ignoreOcclusion();
            GizmoDrawing.line(bottom, top, 0xFFFF263D, 7.0F).ignoreOcclusion();
            GizmoDrawing.line(bottom, top, 0xFFFFB8C0, 2.5F).ignoreOcclusion();
            GizmoDrawing.circle(bottom.add(0.0, 0.04, 0.0), 0.78F,
                DrawStyle.filledAndStroked(RED, 2.0F, 0x30FF1028)).ignoreOcclusion();
        }
        if (KhanhFeatures.bool("spawner_chunk", "range_ring", true)) {
            GizmoDrawing.circle(Vec3d.ofBottomCenter(pos).add(0.0, 0.06, 0.0), 16.0F,
                DrawStyle.stroked(0xDFFF263D, 1.5F)).ignoreOcclusion();
        }
        if (KhanhFeatures.bool("spawner_chunk", "tracers", false)) {
            drawStableTracer(camera, Vec3d.ofCenter(pos), 0xB4FF263D, 1.15F);
        }
        if (KhanhFeatures.bool("spawner_chunk", "nametag", true)) {
            GizmoDrawing.blockLabel(label, pos.up(2), RED, 0, 0.03F);
        }
    }

    private static void renderHoles() {
        for (WorldScanner.HoleShaft shaft : KhanhFeatures.scanner().holeShafts()) {
            BlockPos pos = shaft.start();
            int alpha = KhanhFeatures.number("hole_esp", "fill_alpha", 120);
            float lineWidth = (float) KhanhFeatures.decimal("hole_esp", "line_width", 2.0);
            Box shaftBox = new Box(pos.getX(), pos.getY(), pos.getZ(),
                pos.getX() + shaft.width(), pos.getY() + shaft.depth(), pos.getZ() + shaft.length());
            KhanhEsp.draw("hole_esp", shaftBox, KhanhFeatures.color("hole_esp", "color", RED), alpha, true);
            if (KhanhFeatures.bool("hole_esp", "labels", true)) {
                GizmoDrawing.blockLabel(shaft.width() == 1 && shaft.length() == 1 ? "1×1 HOLE" : "3×1 HOLE",
                    pos.up(shaft.depth()), RED, 0, 0.02F);
            }
        }
    }

    private static void renderSuspiciousChunks(WorldExtractionContext context) {
        int color = RED;
        int simulationDistance = KhanhFeatures.number("sus_esp", "simulation_distance", 8);
        Vec3d camera = context.camera().getCameraPos();
        double range = simulationDistance * 16.0 + 24.0;
        Set<ChunkPos> detected = KhanhFeatures.scanner().suspiciousChunks();
        List<StorageGlowRenderer.Plate> plates = new ArrayList<>();
        for (ChunkPos chunk : detected) {
            if (!chunkWithinHorizontalRange(chunk, camera, range)) continue;
            plates.add(new StorageGlowRenderer.Plate(chunk.getStartX(), chunk.getStartZ(), CHUNK_LAYER_Y + 0.02, color));
        }
        if (System.getenv("KHANH_DEVSHOTS_PLATES") != null && net.minecraft.client.MinecraftClient.getInstance().player != null) {
            // Dev check only: show plates around the player so they can be photographed.
            var player = net.minecraft.client.MinecraftClient.getInstance().player;
            ChunkPos here = player.getChunkPos();
            for (int dx = -1; dx <= 1; dx++) plates.add(new StorageGlowRenderer.Plate((here.x + dx) * 16, here.z * 16 + 16 * dx, Math.floor(player.getY()) + 0.02, color));
        }
        StorageGlowRenderer.setPlates(plates);
        int labels = 0;
        for (ChunkPos chunk : detected) {
            if (labels >= 64 || !chunkWithinHorizontalRange(chunk, camera, Math.min(range, 96.0))) continue;
            int clusterCount = KhanhFeatures.scanner().denseClusterCount(chunk);
            WorldScanner.SusReason reason = KhanhFeatures.scanner().suspiciousReason(chunk);
            BlockPos center = chunk.getCenterAtY(CHUNK_LAYER_Y + 1);
            GizmoDrawing.blockLabel(clusterCount > 0 ? "SUS REGION • " + clusterCount + " CLUSTERS" : reason.label(),
                center, color, 0, clusterCount > 0 ? 0.035F : 0.025F);
            labels++;
        }
    }

    private static void renderPrimeChunks(WorldExtractionContext context) {
        int color = KhanhFeatures.color("prime_chunk_finder", "color", 0xFFB8A6FF);
        double range = KhanhFeatures.number("prime_chunk_finder", "radius", 10) * 16.0 + 24.0;
        Vec3d camera = context.camera().getCameraPos();
        int lightLabels = 0;
        for (Map.Entry<ChunkPos, Integer> entry : KhanhFeatures.scanner().primeLightChunks().entrySet()) {
            ChunkPos chunk = entry.getKey();
            if (!chunkWithinHorizontalRange(chunk, camera, range)) continue;
            Box marker = new Box(chunk.getStartX(), CHUNK_LAYER_Y - 0.04, chunk.getStartZ(),
                chunk.getEndX() + 1, CHUNK_LAYER_Y + 0.08, chunk.getEndZ() + 1);
            int leadColor = 0xFFFFC36C;
            GizmoDrawing.box(marker, DrawStyle.filled(translucent(leadColor, 48)), false).ignoreOcclusion();
            if (lightLabels < 64 && KhanhFeatures.bool("prime_chunk_finder", "labels", true)) {
                GizmoDrawing.blockLabel("LIGHT ACTIVITY • " + entry.getValue() + " UPDATES",
                    chunk.getCenterAtY(CHUNK_LAYER_Y + 1), leadColor, 0, 0.024F);
                lightLabels++;
            }
        }
        for (Map.Entry<ChunkPos, Integer> entry : KhanhFeatures.scanner().primeGrowthChunks().entrySet()) {
            ChunkPos chunk = entry.getKey();
            if (!chunkWithinHorizontalRange(chunk, camera, range)) continue;
            Box marker = new Box(chunk.getStartX(), CHUNK_LAYER_Y - 0.04, chunk.getStartZ(),
                chunk.getEndX() + 1, CHUNK_LAYER_Y + 0.08, chunk.getEndZ() + 1);
            GizmoDrawing.box(marker, DrawStyle.filled(translucent(color, 56)), false).ignoreOcclusion();
            if (KhanhFeatures.bool("prime_chunk_finder", "tracers", false)) {
                drawStableTracer(camera, Vec3d.ofCenter(chunk.getCenterAtY(CHUNK_LAYER_Y + 1)), color, 1.0F);
            }
            if (KhanhFeatures.bool("prime_chunk_finder", "labels", true)) {
                GizmoDrawing.blockLabel("PRIME GROWTH • " + entry.getValue(),
                    chunk.getCenterAtY(CHUNK_LAYER_Y + 1), color, 0, 0.025F);
            }
        }
    }

    private static void renderUndergroundLight(WorldExtractionContext context) {
        int color = KhanhFeatures.color("light_finder", "color", 0xFFFFC36C);
        double range = KhanhFeatures.number("light_finder", "radius", 8) * 16.0 + 24.0;
        Vec3d camera = context.camera().getCameraPos();
        int labels = 0;
        for (Map.Entry<ChunkPos, Integer> entry : KhanhFeatures.scanner().undergroundLightChunks().entrySet()) {
            ChunkPos chunk = entry.getKey();
            if (!chunkWithinHorizontalRange(chunk, camera, range)) continue;
            Box marker = new Box(chunk.getStartX(), CHUNK_LAYER_Y + 0.12, chunk.getStartZ(),
                chunk.getEndX() + 1, CHUNK_LAYER_Y + 0.16, chunk.getEndZ() + 1);
            GizmoDrawing.box(marker, DrawStyle.filled(translucent(color, 52)), false).ignoreOcclusion();
            if (labels < 64 && KhanhFeatures.bool("light_finder", "labels", true)) {
                GizmoDrawing.blockLabel("UNDERGROUND LIGHT • " + entry.getValue() + " SAMPLES",
                    chunk.getCenterAtY(CHUNK_LAYER_Y + 1), color, 0, 0.024F);
                labels++;
            }
        }
    }

    private static void renderRedstoneActivity(WorldExtractionContext context) {
        int height = KhanhFeatures.number("redstone_activity", "box_height", 4);
        int color = 0xFFFF405D;
        for (ChunkPos chunk : ClientDiagnosticsService.activeRedstoneChunks()) {
            Box box = new Box(chunk.getStartX(), CHUNK_LAYER_Y - 0.04, chunk.getStartZ(),
                chunk.getEndX() + 1, CHUNK_LAYER_Y + height, chunk.getEndZ() + 1);
            GizmoDrawing.box(box, DrawStyle.filled(translucent(color, pulseAlpha(36))), false).ignoreOcclusion();
            BlockPos center = chunk.getCenterAtY(CHUNK_LAYER_Y + Math.max(1, height / 2));
            GizmoDrawing.blockLabel("REDSTONE ACTIVITY", center, color, 0, 0.024F);
            if (KhanhFeatures.bool("redstone_activity", "tracers", false)) {
                drawStableTracer(context.camera().getCameraPos(), Vec3d.ofCenter(center), color, 1.5F);
            }
        }
    }

    private static void renderAmethyst(Vec3d camera) {
        double range = KhanhFeatures.number("amethyst_esp", "range", 160);
        int color = KhanhFeatures.color("amethyst_esp", "color", 0xFFFFFFFF);
        int alpha = KhanhFeatures.number("amethyst_esp", "fill_alpha", 255);
        boolean labels = KhanhFeatures.bool("amethyst_esp", "labels", false);

        if (KhanhFeatures.bool("amethyst_esp", "clusters", true)) {
            for (BlockPos cell : KhanhFeatures.scanner().suspiciousCells()) {
                double distance = cell.getSquaredDistance(camera.x, camera.y, camera.z);
                if (distance > range * range) continue;
                KhanhEsp.draw("amethyst_esp", new Box(cell).expand(0.018), color, alpha, true);
                if (labels) GizmoDrawing.blockLabel("AMETHYST CLUSTER", cell.up(), color, 0, 0.020F);
            }
        }

        if (KhanhFeatures.bool("amethyst_esp", "signature_blocks", true)) {
            for (BlockPos cell : KhanhFeatures.scanner().amethystSignatureCells()) {
                if (cell.getSquaredDistance(camera.x, camera.y, camera.z) > range * range) continue;
                KhanhEsp.draw("amethyst_esp", new Box(cell).expand(0.012), color, alpha, true);
            }
        }

        if (KhanhFeatures.bool("amethyst_esp", "geode_centres", true)) {
            for (BlockPos pos : KhanhFeatures.scanner().geodeCenters()) {
                if (pos.getSquaredDistance(camera.x, camera.y, camera.z) > range * range) continue;
                KhanhEsp.draw("amethyst_esp", new Box(pos).expand(0.025), color, alpha, true);
                if (labels) GizmoDrawing.blockLabel("GEODE", pos.up(), color, 0, 0.024F);
            }
        }
    }


    private static void renderNotifiedBlocks(ClientWorld world, Vec3d camera) {
        double range = KhanhFeatures.number("block_notifier", "radius", 16);
        int color = WorldScanner.blockNotifierColor();
        for (BlockPos pos : KhanhFeatures.scanner().notifiedBlocks()) {
            if (pos.getSquaredDistance(camera.x, camera.y, camera.z) > range * range) {
                continue;
            }
            net.minecraft.block.BlockState state = world.getBlockState(pos);
            if (!WorldScanner.isSelectedNotifierBlock(state.getBlock())) {
                continue;
            }
            KhanhEsp.draw("block_notifier", new Box(pos).expand(0.022), color, 0x50, true);
            GizmoDrawing.blockLabel(state.getBlock().getName().getString().toUpperCase(Locale.ROOT),
                pos.up(), color, 0, 0.022F);
        }
    }

    private static void renderChunkSignals(WorldExtractionContext context) {
        renderChunkSet(KhanhFeatures.scanner().baseChunks(), 0xFFFF477E, "BASE", KhanhFeatures.bool("base_esp", "chunk", true));
        renderChunkSet(KhanhFeatures.scanner().tuffChunks(), 0xFFB76CFF, "TUFF / CLUSTERS", true);
    }

    private static void renderChunkSet(Set<ChunkPos> chunks, int color, String label, boolean enabled) {
        if (!enabled) return;
        for (ChunkPos chunk : chunks) {
            Box box = new Box(chunk.getStartX(), CHUNK_LAYER_Y - 0.04, chunk.getStartZ(),
                chunk.getEndX() + 1, CHUNK_LAYER_Y + 0.17, chunk.getEndZ() + 1);
            GizmoDrawing.box(box, DrawStyle.filled(translucent(color, pulseAlpha(32))), false).ignoreOcclusion();
            GizmoDrawing.blockLabel(label, chunk.getCenterAtY(CHUNK_LAYER_Y + 1), color, 0, 0.023F);
        }
    }

    private static void renderEntities(ClientWorld world, Vec3d camera) {
        double range = KhanhFeatures.number("entity_debug", "range", 64);
        Box area = new Box(camera, camera).expand(range);
        for (Entity entity : world.getEntitiesByClass(Entity.class, area, Entity::isAlive)) {
            if (entity == net.minecraft.client.MinecraftClient.getInstance().player) continue;
            boolean player = entity instanceof AbstractClientPlayerEntity;
            boolean item = entity instanceof ItemEntity;
            boolean mob = entity instanceof LivingEntity && !player;
            if ((player && !KhanhFeatures.bool("entity_debug", "players", true))
                || (mob && !KhanhFeatures.bool("entity_debug", "mobs", true))
                || (item && !KhanhFeatures.bool("entity_debug", "items", false))
                || (!player && !mob && !item)) continue;
            int color = player ? CYAN : item ? GOLD : PINK;
            GizmoDrawing.box(entity.getBoundingBox().expand(0.025),
                DrawStyle.filledAndStroked(color, 1.5F,
                    translucent(color, 48)), false).ignoreOcclusion();
            if (KhanhFeatures.bool("entity_debug", "labels", true)) {
                String label = entity.getType().getName().getString().toUpperCase(Locale.ROOT)
                    + " • " + Math.round(Math.sqrt(entity.squaredDistanceTo(camera))) + "m";
                GizmoDrawing.entityLabel(entity, color, label, 0, 0.020F);
            }
        }
    }

    private static int pulseAlpha(int base) {
        if (!KhanhFeatures.bool("visuals", "world_pulse", true)) return base;
        return Math.max(18, Math.min(92, base + (int) Math.round(Math.sin(System.nanoTime() / 500_000_000.0) * 14.0)));
    }

    private static void drawStableTracer(Vec3d camera, Vec3d target, int color, float width) {
        Vector3fc forward = net.minecraft.client.MinecraftClient.getInstance()
            .gameRenderer.getCamera().getHorizontalPlane();
        double forwardX = forward.x();
        double forwardY = forward.y();
        double forwardZ = forward.z();
        double length = Math.sqrt(forwardX * forwardX + forwardY * forwardY + forwardZ * forwardZ);
        if (length <= 1.0E-6) {
            return;
        }
        forwardX /= length;
        forwardY /= length;
        forwardZ /= length;

        Vec3d start = camera.add(forwardX * 0.35, forwardY * 0.35, forwardZ * 0.35);
        double relativeX = target.x - camera.x;
        double relativeY = target.y - camera.y;
        double relativeZ = target.z - camera.z;
        double endDot = relativeX * forwardX + relativeY * forwardY + relativeZ * forwardZ;
        Vec3d end = target;
        if (endDot < 0.1) {
            double amount = -0.25 / (endDot - 0.35);
            end = new Vec3d(
                start.x + (target.x - start.x) * amount,
                start.y + (target.y - start.y) * amount,
                start.z + (target.z - start.z) * amount);
        }
        GizmoDrawing.line(start, end, color, Math.max(1.15F, width)).ignoreOcclusion();
    }

    private static boolean storageEnabled(WorldScanner.StorageType type) {
        return switch (type) {
            case CHEST -> KhanhFeatures.bool("storage_esp", "chests", true);
            case TRAPPED_CHEST -> KhanhFeatures.bool("storage_esp", "trapped_chests", true);
            case ENDER_CHEST -> KhanhFeatures.bool("storage_esp", "ender_chests", true);
            case SHULKER -> KhanhFeatures.bool("storage_esp", "shulkers", true);
            case BARREL -> KhanhFeatures.bool("storage_esp", "barrels", true);
            case SPAWNER -> KhanhFeatures.bool("storage_esp", "spawners", true);
            case TRIAL_SPAWNER -> false;
            case HOPPER -> KhanhFeatures.bool("storage_esp", "hoppers", false);
            case FURNACE -> KhanhFeatures.bool("storage_esp", "furnaces", false);
            case ENCHANTING_TABLE -> KhanhFeatures.bool("storage_esp", "enchanting_tables", true);
            case MOVING_PISTON -> KhanhFeatures.bool("storage_esp", "moving_pistons", true);
        };
    }

    private static int storageColor(ClientWorld world, BlockPos pos, WorldScanner.StorageType type) {
        return switch (type) {
            case CHEST -> KhanhFeatures.color("storage_esp", "chest_color", 0xFFFFB21A);
            case TRAPPED_CHEST -> KhanhFeatures.color("storage_esp", "trapped_chest_color", 0xFFFF405D);
            case ENDER_CHEST -> KhanhFeatures.color("storage_esp", "ender_chest_color", 0xFFB76CFF);
            case SHULKER -> KhanhFeatures.bool("storage_esp", "shulker_dyes", true)
                ? dyedShulkerColor(world.getBlockState(pos),
                    KhanhFeatures.color("storage_esp", "shulker_color", 0xFFFF44FF))
                : KhanhFeatures.color("storage_esp", "shulker_color", 0xFFFF44FF);
            case BARREL -> KhanhFeatures.color("storage_esp", "barrel_color", 0xFF8B5A2B);
            case SPAWNER, TRIAL_SPAWNER -> KhanhFeatures.color("storage_esp", "spawner_color", 0xFFFF263D);
            case HOPPER -> KhanhFeatures.color("storage_esp", "hopper_color", 0xFFF7F4F6);
            case FURNACE -> KhanhFeatures.color("storage_esp", "furnace_color", 0xFFB0B0B0);
            case ENCHANTING_TABLE -> KhanhFeatures.color("storage_esp", "enchanting_color", 0xFF5050FF);
            case MOVING_PISTON -> KhanhFeatures.color("storage_esp", "piston_color", 0xFF23E200);
        };
    }

    private static int dyedShulkerColor(BlockState state, int fallback) {
        if (state.isOf(net.minecraft.block.Blocks.WHITE_SHULKER_BOX)) return 0xFFF4F0EB;
        if (state.isOf(net.minecraft.block.Blocks.ORANGE_SHULKER_BOX)) return 0xFFF5A623;
        if (state.isOf(net.minecraft.block.Blocks.MAGENTA_SHULKER_BOX)) return 0xFFC74EBD;
        if (state.isOf(net.minecraft.block.Blocks.LIGHT_BLUE_SHULKER_BOX)) return 0xFF3DB2D1;
        if (state.isOf(net.minecraft.block.Blocks.YELLOW_SHULKER_BOX)) return 0xFFF9C619;
        if (state.isOf(net.minecraft.block.Blocks.LIME_SHULKER_BOX)) return 0xFF74B62B;
        if (state.isOf(net.minecraft.block.Blocks.PINK_SHULKER_BOX)) return 0xFFF07CB8;
        if (state.isOf(net.minecraft.block.Blocks.GRAY_SHULKER_BOX)) return 0xFF4A4F52;
        if (state.isOf(net.minecraft.block.Blocks.LIGHT_GRAY_SHULKER_BOX)) return 0xFF9EA3A3;
        if (state.isOf(net.minecraft.block.Blocks.CYAN_SHULKER_BOX)) return 0xFF258E8E;
        if (state.isOf(net.minecraft.block.Blocks.PURPLE_SHULKER_BOX)) return 0xFF7F3FB4;
        if (state.isOf(net.minecraft.block.Blocks.BLUE_SHULKER_BOX)) return 0xFF2D3DAB;
        if (state.isOf(net.minecraft.block.Blocks.BROWN_SHULKER_BOX)) return 0xFF7F5131;
        if (state.isOf(net.minecraft.block.Blocks.GREEN_SHULKER_BOX)) return 0xFF506523;
        if (state.isOf(net.minecraft.block.Blocks.RED_SHULKER_BOX)) return 0xFFA02722;
        if (state.isOf(net.minecraft.block.Blocks.BLACK_SHULKER_BOX)) return 0xFF1C1C24;
        return fallback;
    }

    private static int resolveColor(String name) {
        return switch (name) {
            case "Classic Orange" -> 0xFFFFAA00;
            case "Classic Red" -> 0xFFFF0000;
            case "Classic Purple" -> 0xFF7800FF;
            case "Classic Pink" -> 0xFFFF44FF;
            case "Classic Brown" -> 0xFF885500;
            case "Classic Green" -> 0xFF00FF00;
            case "Classic Gray" -> 0xFF888888;
            case "Pink", "Khanh Client Pink" -> PINK;
            case "Purple", "Violet" -> 0xFFB76CFF;
            case "Cyan" -> CYAN;
            case "Lime", "Green", "Mint" -> 0xFF64F58D;
            case "Red" -> RED;
            case "White" -> 0xFFF5F3F4;
            default -> GOLD;
        };
    }


    private static int customColor(String module) {
        return VisualTheme.rgb(
            KhanhFeatures.number(module, "custom_red", 255),
            KhanhFeatures.number(module, "custom_green", 22),
            KhanhFeatures.number(module, "custom_blue", 159));
    }

    /** Pushes a colour toward a saturated, bright version of itself; greys and white stay neutral. */
    private static int vibrant(int color) {
        float[] hsb = java.awt.Color.RGBtoHSB((color >> 16) & 255, (color >> 8) & 255, color & 255, null);
        float saturation = hsb[1] < 0.15F ? hsb[1] : Math.min(1.0F, hsb[1] * 1.2F + 0.1F);
        float brightness = Math.min(1.0F, hsb[2] * 0.45F + 0.58F);
        return 0xFF000000 | java.awt.Color.HSBtoRGB(hsb[0], saturation, brightness) & 0xFFFFFF;
    }

    private static int translucent(int color, int alpha) {
        return (alpha << 24) | (color & 0x00FFFFFF);
    }

    private static boolean chunkWithinHorizontalRange(ChunkPos chunk, Vec3d camera, double range) {
        double centerX = chunk.getStartX() + 8.0;
        double centerZ = chunk.getStartZ() + 8.0;
        double dx = centerX - camera.x;
        double dz = centerZ - camera.z;
        double paddedRange = range + 12.0;
        return dx * dx + dz * dz <= paddedRange * paddedRange;
    }

    private static void renderTargetBlock(ClientWorld world) {
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        Entity camera = client.getCameraEntity();
        double range = KhanhFeatures.decimal("block_outline", "range", 6.0);
        float tickDelta = client.getRenderTickCounter().getTickProgress(false);
        if (camera == null || !(camera.raycast(range, tickDelta, false) instanceof BlockHitResult hit)
            || hit.getType() != net.minecraft.util.hit.HitResult.Type.BLOCK) {
            outlineTarget = null;
            outlineStart = null;
            return;
        }
        BlockPos pos = hit.getBlockPos();
        if (world.getBlockState(pos).isAir()) {
            outlineTarget = null;
            outlineStart = null;
            return;
        }
        int color = KhanhFeatures.color("block_outline", "color", 0xFFFFFFFF);
        float lineWidth = (float) KhanhFeatures.decimal("block_outline", "line_width", 2.5);
        long now = System.nanoTime();
        double strength = KhanhFeatures.decimal("block_outline", "bounce_strength", 0.10);
        double speed = KhanhFeatures.decimal("block_outline", "bounce_speed", 1.8);
        Vec3d center = outlineCenter(pos, now, strength, speed,
            KhanhFeatures.bool("block_outline", "bounce", true));
        DrawStyle style = DrawStyle.stroked(color, lineWidth);
        GizmoDrawing.box(new Box(center.x - 0.5, center.y - 0.5, center.z - 0.5,
            center.x + 0.5, center.y + 0.5, center.z + 0.5).expand(0.006), style, false);
    }

    private static Vec3d outlineCenter(BlockPos target, long now, double strength, double speed, boolean bounce) {
        Vec3d destination = Vec3d.ofCenter(target);
        if (!bounce) {
            outlineTarget = target.toImmutable();
            outlineStart = destination;
            outlineStartedAt = now;
            return destination;
        }
        if (outlineTarget == null || outlineStart == null) {
            outlineTarget = target.toImmutable();
            outlineStart = destination;
            outlineStartedAt = now;
            return destination;
        }
        if (!outlineTarget.equals(target)) {
            outlineStart = animatedOutlineCenter(outlineTarget, now, strength, speed);
            outlineTarget = target.toImmutable();
            outlineStartedAt = now;
        }
        return animatedOutlineCenter(outlineTarget, now, strength, speed);
    }

    private static Vec3d animatedOutlineCenter(BlockPos target, long now, double strength, double speed) {
        Vec3d destination = Vec3d.ofCenter(target);
        double duration = 320_000_000.0 / Math.max(0.5, speed);
        double progress = Math.max(0.0, Math.min(1.0, (now - outlineStartedAt) / duration));
        double smooth = progress * progress * (3.0 - 2.0 * progress);
        Vec3d base = outlineStart.lerp(destination, smooth);
        double hop = Math.sin(Math.PI * progress) * strength;
        return base.add(0.0, hop, 0.0);
    }

}
