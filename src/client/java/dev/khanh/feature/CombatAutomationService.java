package dev.khanh.feature;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.RespawnAnchorBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.lwjgl.glfw.GLFW;

/** Hotbar-only crystal and anchor macros with explicit, server-safe switching. */
public final class CombatAutomationService {
    private int crystalBreakCooldown;
    private int crystalPlaceCooldown;
    private int crystalObsidianCooldown;
    private int crystalOriginalSlot = -1;
    private int crystalRestoreTicks = -1;
    private int lastCrystalId = -1;

    private AnchorStage anchorStage = AnchorStage.IDLE;
    private BlockPos anchorPos;
    private int anchorStageTicks;
    private int anchorCooldown;
    private int anchorOriginalSlot = -1;
    private int anchorConfirmTicks;
    private int anchorMacroSwitchClock;
    private int anchorMacroChargeClock;
    private int anchorMacroExplodeClock;
    private BlockPos anchorMacroPos;
    private static final int SERVER_CONFIRM_TICKS = 12;

    public void tick(MinecraftClient client) {
        if (client.player == null || client.world == null || client.interactionManager == null
            || client.currentScreen != null || FreecamService.active()) {
            restoreAll(client);
            return;
        }

        tickCooldowns();
        boolean crystalEnabled = KhanhFeatures.enabled("auto_crystal");
        boolean anchorEnabled = KhanhFeatures.enabled("auto_anchor");
        boolean useHeld = rightMouseHeld(client);

        if (!crystalEnabled) {
            restoreCrystal(client);
            resetCrystalCounters();
        } else {
            maintainCrystalSwitchback(client, useHeld);
        }
        if (!anchorEnabled) {
            resetAnchor(client, true);
        }

        boolean anchorHoldMode = anchorEnabled && isHoldMode("auto_anchor");
        if (anchorHoldMode) {
            resetEnemyAnchorState(client);
            if (tickAnchorMacro(client, useHeld)) {
                return;
            }
            // Let vanilla place the anchor/glowstone while held; do not allow
            // Auto Crystal to steal that right click.
            if (useHeld && holdsAnchorPart(client)) {
                return;
            }
        } else {
            resetAnchorMacro(client, true);
        }

        if (anchorEnabled && anchorStage != AnchorStage.IDLE) {
            continueAnchor(client);
            return;
        }

        if (crystalEnabled && tickCrystal(client, useHeld)) {
            return;
        }
        if (anchorEnabled && !anchorHoldMode && anchorCooldown <= 0) {
            startAnchor(client, false);
        }
    }

    /**
     * Anchor macro: read the aimed block every tick instead of
     * predicting a multi-step transaction. That lets the server-confirmed
     * charge state decide when it is safe to switch and detonate.
     */
    private boolean tickAnchorMacro(MinecraftClient client, boolean useHeld) {
        boolean onLook = KhanhFeatures.bool("auto_anchor", "on_look", true);
        boolean instant = KhanhFeatures.bool("auto_anchor", "instant", true);
        if ((!useHeld && !onLook) || client.world.getRegistryKey() == World.NETHER
            || !healthyEnough(client, "auto_anchor")) {
            resetAnchorMacro(client, true);
            return false;
        }
        if (!(client.crosshairTarget instanceof BlockHitResult blockHit)
            || blockHit.getType() != HitResult.Type.BLOCK
            || !client.world.getBlockState(blockHit.getBlockPos()).isOf(Blocks.RESPAWN_ANCHOR)) {
            resetAnchorMacro(client, true);
            return false;
        }

        BlockPos pos = blockHit.getBlockPos().toImmutable();
        if (!canReach(client, pos, KhanhFeatures.decimal("auto_anchor", "reach", 5.0))) {
            resetAnchorMacro(client, true);
            return false;
        }
        if (!pos.equals(anchorMacroPos)) {
            anchorMacroPos = pos;
            resetAnchorMacroClocks();
        }
        suppressVanillaUse(client);
        if (anchorCooldown > 0) return true;

        int wantedCharges = KhanhFeatures.bool("auto_anchor", "one_charge", true) ? 1 : 4;
        int charges = anchorCharges(client, pos);
        // Instant is immediate on the first attempt, not a stream of duplicate
        // explosions while the server's removal/update is still in flight.
        if (anchorMacroExplodeClock < 0 && charges > 0) {
            anchorMacroExplodeClock++;
            return true;
        }
        if (charges < wantedCharges && KhanhFeatures.bool("auto_anchor", "charge", true)) {
            anchorMacroExplodeClock = 0;
            Hand glowstoneHand = heldHand(client, Items.GLOWSTONE);
            if (glowstoneHand == null) {
                if (!instant && !delayElapsed(KhanhFeatures.number("auto_anchor", "switch_delay", 1), AnchorClock.SWITCH)) {
                    return true;
                }
                glowstoneHand = equipAnchorItem(client, Items.GLOWSTONE);
                anchorMacroSwitchClock = 0;
                if (glowstoneHand == null || !instant) return true;
            }
            anchorMacroSwitchClock = 0;
            if (!instant && !delayElapsed(KhanhFeatures.number("auto_anchor", "charge_delay", 0), AnchorClock.CHARGE)) {
                return true;
            }
            anchorMacroChargeClock = 0;
            interact(client, glowstoneHand, blockHit);
            return true;
        }

        anchorMacroChargeClock = 0;
        if (charges > 0 && KhanhFeatures.bool("auto_anchor", "detonate", true)) {
            int detonationSlot = detonationSlot(client);
            if (detonationSlot < 0) {
                return true;
            }
            if (client.player.getInventory().getSelectedSlot() != detonationSlot
                || client.player.getMainHandStack().isOf(Items.GLOWSTONE)) {
                if (!instant && !delayElapsed(KhanhFeatures.number("auto_anchor", "switch_delay", 1), AnchorClock.SWITCH)) {
                    return true;
                }
                rememberAnchorSlot(client);
                selectSlot(client, detonationSlot);
                anchorMacroSwitchClock = 0;
                if (!instant) return true;
            }
            anchorMacroSwitchClock = 0;
            if (!instant && !delayElapsed(KhanhFeatures.number("auto_anchor", "explode_delay", 1), AnchorClock.EXPLODE)) {
                return true;
            }
            anchorMacroExplodeClock = 0;
            boolean accepted = interact(client, Hand.MAIN_HAND, blockHit);
            if (accepted) {
                anchorCooldown = instant ? 1 : Math.max(1, KhanhFeatures.number("auto_anchor", "cycle_delay", 6));
                restoreAnchorSlot(client);
                if (instant) anchorMacroExplodeClock = -SERVER_CONFIRM_TICKS;
                else anchorMacroPos = null;
            }
            return true;
        }
        return true;
    }

    private boolean delayElapsed(int delay, AnchorClock clock) {
        int value = switch (clock) {
            case SWITCH -> anchorMacroSwitchClock;
            case CHARGE -> anchorMacroChargeClock;
            case EXPLODE -> anchorMacroExplodeClock;
        };
        if (value >= Math.max(0, delay)) {
            return true;
        }
        switch (clock) {
            case SWITCH -> anchorMacroSwitchClock++;
            case CHARGE -> anchorMacroChargeClock++;
            case EXPLODE -> anchorMacroExplodeClock++;
        }
        return false;
    }

    private boolean tickCrystal(MinecraftClient client, boolean useHeld) {
        boolean holdMode = isHoldMode("auto_crystal");
        if (holdMode && !useHeld) {
            lastCrystalId = -1;
            return false;
        }
        if (holdMode) {
            return tickAimedCrystal(client);
        }
        if (!healthyEnough(client, "auto_crystal")) {
            return false;
        }

        PlayerEntity target = nearestEnemy(client, KhanhFeatures.decimal("auto_crystal", "target_range", 7.0));
        if (target == null) {
            return false;
        }
        KhanhFeatures.setTarget(target);
        double reach = KhanhFeatures.decimal("auto_crystal", "reach", 5.0);
        double selfDistance = KhanhFeatures.decimal("auto_crystal", "self_distance", 3.0);

        if (KhanhFeatures.bool("auto_crystal", "break", true) && crystalBreakCooldown <= 0) {
            EndCrystalEntity crystal = nearestCrystal(client, target, reach, selfDistance);
            if (crystal != null && crystal.getId() != lastCrystalId) {
                breakCrystal(client, crystal);
                return true;
            }
        }
        if (!KhanhFeatures.bool("auto_crystal", "place", true) || crystalPlaceCooldown > 0) {
            return false;
        }

        BlockPos base = findCrystalBase(client, target, reach, selfDistance);
        if (base != null) {
            Hand crystalHand = equipCrystalItem(client, Items.END_CRYSTAL);
            if (crystalHand == null) {
                return false;
            }
            boolean accepted = interact(client, crystalHand, hit(base, Direction.UP));
            if (accepted) {
                crystalPlaceCooldown = KhanhFeatures.number("auto_crystal", "place_delay", 2);
                scheduleCrystalRestore();
            }
            return accepted;
        }

        if (KhanhFeatures.bool("auto_crystal", "auto_obsidian", true) && crystalObsidianCooldown <= 0) {
            BlockPos support = findObsidianSupport(client, target, reach, selfDistance);
            if (support != null) {
                Hand obsidianHand = equipCrystalItem(client, Items.OBSIDIAN);
                if (obsidianHand == null) {
                    return false;
                }
                boolean accepted = interact(client, obsidianHand, hit(support, Direction.UP));
                if (accepted) {
                    crystalObsidianCooldown = KhanhFeatures.number("auto_crystal", "obsidian_delay", 4);
                    scheduleCrystalRestore();
                }
                return accepted;
            }
        }
        return false;
    }

    private boolean tickAimedCrystal(MinecraftClient client) {
        suppressVanillaUse(client);
        HitResult target = client.crosshairTarget;
        if (target instanceof EntityHitResult entityHit && entityHit.getEntity() instanceof EndCrystalEntity crystal) {
            if (KhanhFeatures.bool("auto_crystal", "break", true) && crystalBreakCooldown <= 0
                && crystal.getId() != lastCrystalId
                && client.player.getEyePos().distanceTo(crystal.getEntityPos())
                    <= KhanhFeatures.decimal("auto_crystal", "reach", 5.0)) {
                breakCrystal(client, crystal);
                return true;
            }
            return false;
        }
        if (!(target instanceof BlockHitResult blockHit) || blockHit.getType() != HitResult.Type.BLOCK) {
            return false;
        }

        BlockPos pos = blockHit.getBlockPos();
        BlockState state = client.world.getBlockState(pos);
        if (isCrystalBase(state)) {
            EndCrystalEntity crystal = crystalOn(client, pos);
            if (crystal != null) {
                if (KhanhFeatures.bool("auto_crystal", "break", true) && crystalBreakCooldown <= 0
                    && crystal.getId() != lastCrystalId
                    && client.player.getEyePos().distanceTo(crystal.getEntityPos())
                        <= KhanhFeatures.decimal("auto_crystal", "reach", 3.0)) {
                    breakCrystal(client, crystal);
                    return true;
                }
                return false;
            }
            lastCrystalId = -1;
            if (!KhanhFeatures.bool("auto_crystal", "place", true) || crystalPlaceCooldown > 0
                || !canFitCrystal(client, pos)) {
                return false;
            }
            Hand hand = equipCrystalItem(client, Items.END_CRYSTAL);
            if (hand == null) {
                return false;
            }
            boolean accepted = interact(client, hand, blockHit);
            if (accepted) {
                crystalPlaceCooldown = KhanhFeatures.number("auto_crystal", "place_delay", 2);
            }
            return accepted;
        }

        if (!state.isOf(Blocks.RESPAWN_ANCHOR)
            && KhanhFeatures.bool("auto_crystal", "auto_obsidian", true) && crystalObsidianCooldown <= 0) {
            BlockPos placed = state.isReplaceable() ? pos : pos.offset(blockHit.getSide());
            if (!client.world.getBlockState(placed).isReplaceable() || !canFitCrystal(client, placed)) {
                return false;
            }
            Hand hand = equipCrystalItem(client, Items.OBSIDIAN);
            if (hand == null) {
                return false;
            }
            boolean accepted = interact(client, hand, blockHit);
            if (accepted) {
                crystalObsidianCooldown = KhanhFeatures.number("auto_crystal", "obsidian_delay", 4);
            }
            return accepted;
        }
        return false;
    }

    private void breakCrystal(MinecraftClient client, EndCrystalEntity crystal) {
        client.interactionManager.attackEntity(client.player, crystal);
        client.player.swingHand(Hand.MAIN_HAND);
        lastCrystalId = crystal.getId();
        crystalBreakCooldown = KhanhFeatures.number("auto_crystal", "break_delay", 2);
    }

    private boolean startAnchor(MinecraftClient client, boolean useHeld) {
        boolean holdMode = isHoldMode("auto_anchor");
        if (holdMode && !useHeld) {
            return false;
        }
        if (client.world.getRegistryKey() == World.NETHER || !healthyEnough(client, "auto_anchor")) {
            return false;
        }

        BlockHitResult blockHit;
        if (holdMode) {
            if (!(client.crosshairTarget instanceof BlockHitResult aimed) || aimed.getType() != HitResult.Type.BLOCK) {
                return false;
            }
            blockHit = aimed;
        } else {
            PlayerEntity target = nearestEnemy(client, KhanhFeatures.decimal("auto_anchor", "target_range", 6.0));
            if (target == null) {
                return false;
            }
            KhanhFeatures.setTarget(target);
            double reach = KhanhFeatures.decimal("auto_anchor", "reach", 5.0);
            double selfDistance = KhanhFeatures.decimal("auto_anchor", "self_distance", 3.0);
            BlockPos existing = findAnchor(client, target, reach, selfDistance);
            if (existing != null) {
                blockHit = hit(existing, Direction.UP);
            } else {
                BlockPos support = findAnchorSupport(client, target, reach, selfDistance);
                if (support == null) {
                    return false;
                }
                blockHit = hit(support, Direction.UP);
            }
        }

        BlockPos hitPos = blockHit.getBlockPos();
        BlockState state = client.world.getBlockState(hitPos);
        if (state.isOf(Blocks.RESPAWN_ANCHOR)) {
            anchorPos = hitPos.toImmutable();
            suppressVanillaUse(client);
            return beginAnchorChargeOrDetonate(client);
        }
        if (!KhanhFeatures.bool("auto_anchor", "place", true)) {
            return false;
        }

        BlockPos placed = state.isReplaceable() ? hitPos : hitPos.offset(blockHit.getSide());
        if (!client.world.getBlockState(placed).isReplaceable() || !spaceIsClear(client, placed, 1)) {
            return false;
        }
        Hand hand = equipAnchorItem(client, Items.RESPAWN_ANCHOR);
        if (hand == null) {
            return false;
        }
        suppressVanillaUse(client);
        if (!interact(client, hand, blockHit)) {
            resetAnchor(client, true);
            return false;
        }
        anchorPos = placed.toImmutable();
        anchorStage = AnchorStage.WAIT_PLACE;
        anchorStageTicks = Math.max(1, KhanhFeatures.number("auto_anchor", "place_delay", 2));
        anchorConfirmTicks = 0;
        return true;
    }

    private boolean beginAnchorChargeOrDetonate(MinecraftClient client) {
        int charges = anchorCharges(client, anchorPos);
        if (charges <= 0 && KhanhFeatures.bool("auto_anchor", "charge", true)) {
            Hand hand = equipAnchorItem(client, Items.GLOWSTONE);
            if (hand == null) {
                resetAnchor(client, true);
                return false;
            }
            anchorStage = AnchorStage.WAIT_CHARGE;
            anchorStageTicks = Math.max(0, KhanhFeatures.number("auto_anchor", "switch_delay", 1));
            anchorConfirmTicks = 0;
            return true;
        }
        if (charges > 0 && KhanhFeatures.bool("auto_anchor", "detonate", true)) {
            return prepareDetonation(client);
        }
        resetAnchor(client, true);
        return false;
    }

    private void continueAnchor(MinecraftClient client) {
        if (anchorPos == null || !canReach(client, anchorPos, KhanhFeatures.decimal("auto_anchor", "reach", 5.0))) {
            resetAnchor(client, true);
            return;
        }
        if (anchorStageTicks > 0) {
            anchorStageTicks--;
            return;
        }

        switch (anchorStage) {
            case WAIT_PLACE -> {
                if (!client.world.getBlockState(anchorPos).isOf(Blocks.RESPAWN_ANCHOR)) {
                    if (anchorConfirmTicks++ < SERVER_CONFIRM_TICKS) {
                        anchorStageTicks = 1;
                        return;
                    }
                    resetAnchor(client, true);
                    return;
                }
                anchorConfirmTicks = 0;
                beginAnchorChargeOrDetonate(client);
            }
            case WAIT_CHARGE -> {
                if (!client.world.getBlockState(anchorPos).isOf(Blocks.RESPAWN_ANCHOR)) {
                    resetAnchor(client, true);
                    return;
                }
                suppressVanillaUse(client);
                if (!interact(client, equippedAnchorHand(client, Items.GLOWSTONE), hit(anchorPos, Direction.UP))) {
                    resetAnchor(client, true);
                    return;
                }
                anchorStageTicks = Math.max(1, KhanhFeatures.number("auto_anchor", "charge_delay", 2));
                anchorStage = AnchorStage.SWITCH_DETONATOR;
                anchorConfirmTicks = 0;
            }
            case SWITCH_DETONATOR -> {
                if (anchorCharges(client, anchorPos) <= 0) {
                    if (anchorConfirmTicks++ < SERVER_CONFIRM_TICKS) {
                        anchorStageTicks = 1;
                        return;
                    }
                    resetAnchor(client, true);
                    return;
                }
                if (!prepareDetonation(client)) {
                    resetAnchor(client, true);
                }
            }
            case WAIT_DETONATE -> {
                if (!client.world.getBlockState(anchorPos).isOf(Blocks.RESPAWN_ANCHOR)) {
                    resetAnchor(client, true);
                    return;
                }
                if (anchorCharges(client, anchorPos) <= 0) {
                    if (anchorConfirmTicks++ < SERVER_CONFIRM_TICKS) {
                        anchorStageTicks = 1;
                        return;
                    }
                    resetAnchor(client, true);
                    return;
                }
                Hand hand = equippedDetonationHand(client);
                if (hand == null) {
                    resetAnchor(client, true);
                    return;
                }
                suppressVanillaUse(client);
                if (!interact(client, hand, hit(anchorPos, Direction.UP))) {
                    if (anchorConfirmTicks++ < 4) {
                        anchorStageTicks = 1;
                        return;
                    }
                    resetAnchor(client, true);
                    return;
                }
                anchorCooldown = Math.max(1, KhanhFeatures.number("auto_anchor", "cycle_delay", 6));
                anchorStage = AnchorStage.WAIT_RESTORE;
                anchorStageTicks = Math.max(1, KhanhFeatures.number("auto_anchor", "switch_back_delay", 2));
                anchorConfirmTicks = 0;
            }
            case WAIT_RESTORE -> resetAnchor(client, true);
            default -> resetAnchor(client, true);
        }
    }

    private boolean prepareDetonation(MinecraftClient client) {
        if (!KhanhFeatures.bool("auto_anchor", "detonate", true)) {
            resetAnchor(client, true);
            return false;
        }
        Hand hand = equipDetonator(client);
        if (hand == null) {
            return false;
        }
        anchorStage = AnchorStage.WAIT_DETONATE;
        anchorStageTicks = Math.max(1, KhanhFeatures.number("auto_anchor", "explode_delay", 2));
        anchorConfirmTicks = 0;
        return true;
    }

    private Hand equipCrystalItem(MinecraftClient client, Item item) {
        if (client.player.getMainHandStack().isOf(item)) {
            return Hand.MAIN_HAND;
        }
        if (client.player.getOffHandStack().isOf(item)) {
            return Hand.OFF_HAND;
        }
        int slot = findHotbarItem(client, item);
        if (slot < 0) {
            return null;
        }
        if (crystalOriginalSlot < 0) {
            crystalOriginalSlot = client.player.getInventory().getSelectedSlot();
        }
        selectSlot(client, slot);
        return Hand.MAIN_HAND;
    }

    private Hand equipAnchorItem(MinecraftClient client, Item item) {
        if (client.player.getMainHandStack().isOf(item)) {
            return Hand.MAIN_HAND;
        }
        if (client.player.getOffHandStack().isOf(item)) {
            return Hand.OFF_HAND;
        }
        int slot = findHotbarItem(client, item);
        if (slot < 0) {
            return null;
        }
        rememberAnchorSlot(client);
        selectSlot(client, slot);
        return Hand.MAIN_HAND;
    }

    private static Hand heldHand(MinecraftClient client, Item item) {
        if (client.player.getMainHandStack().isOf(item)) {
            return Hand.MAIN_HAND;
        }
        if (client.player.getOffHandStack().isOf(item)) {
            return Hand.OFF_HAND;
        }
        return null;
    }

    private Hand equippedAnchorHand(MinecraftClient client, Item item) {
        if (client.player.getMainHandStack().isOf(item)) {
            return Hand.MAIN_HAND;
        }
        if (client.player.getOffHandStack().isOf(item)) {
            return Hand.OFF_HAND;
        }
        return equipAnchorItem(client, item);
    }

    private Hand equipDetonator(MinecraftClient client) {
        if (!client.player.getMainHandStack().isOf(Items.GLOWSTONE)) {
            return Hand.MAIN_HAND;
        }
        int configured = KhanhFeatures.number("auto_anchor", "detonate_slot", 0);
        int slot = configured >= 1 && configured <= 9 ? configured - 1 : findHotbarItemExcept(client, Items.GLOWSTONE);
        if (slot >= 0 && !client.player.getInventory().getStack(slot).isOf(Items.GLOWSTONE)) {
            rememberAnchorSlot(client);
            selectSlot(client, slot);
            return Hand.MAIN_HAND;
        }
        return client.player.getOffHandStack().isOf(Items.GLOWSTONE) ? null : Hand.OFF_HAND;
    }

    private int detonationSlot(MinecraftClient client) {
        int configured = KhanhFeatures.number("auto_anchor", "detonate_slot", 0);
        if (configured >= 1 && configured <= 9) {
            int slot = configured - 1;
            return client.player.getInventory().getStack(slot).isOf(Items.GLOWSTONE) ? -1 : slot;
        }
        return findHotbarItemExcept(client, Items.GLOWSTONE);
    }

    private Hand equippedDetonationHand(MinecraftClient client) {
        return client.player.getMainHandStack().isOf(Items.GLOWSTONE) ? equipDetonator(client) : Hand.MAIN_HAND;
    }

    private void rememberAnchorSlot(MinecraftClient client) {
        if (anchorOriginalSlot < 0) {
            anchorOriginalSlot = client.player.getInventory().getSelectedSlot();
        }
    }

    private void restoreAnchorSlot(MinecraftClient client) {
        if (client.player != null && anchorOriginalSlot >= 0
            && KhanhFeatures.bool("auto_anchor", "switch_back", true)) {
            selectSlot(client, anchorOriginalSlot);
        }
        anchorOriginalSlot = -1;
    }

    private void scheduleCrystalRestore() {
        if (!isHoldMode("auto_crystal") && KhanhFeatures.bool("auto_crystal", "switch_back", true)) {
            crystalRestoreTicks = Math.max(1, KhanhFeatures.number("auto_crystal", "switch_back_delay", 2));
        }
    }

    private void maintainCrystalSwitchback(MinecraftClient client, boolean useHeld) {
        if (isHoldMode("auto_crystal")) {
            if (!useHeld) {
                restoreCrystal(client);
            }
            return;
        }
        if (crystalRestoreTicks > 0) {
            crystalRestoreTicks--;
        }
        if (crystalRestoreTicks == 0) {
            restoreCrystal(client);
        }
    }

    private void restoreCrystal(MinecraftClient client) {
        if (client.player != null && crystalOriginalSlot >= 0
            && KhanhFeatures.bool("auto_crystal", "switch_back", true)) {
            selectSlot(client, crystalOriginalSlot);
        }
        crystalOriginalSlot = -1;
        crystalRestoreTicks = -1;
    }

    private void resetAnchor(MinecraftClient client, boolean restore) {
        if (restore) {
            restoreAnchorSlot(client);
        } else {
            anchorOriginalSlot = -1;
        }
        resetEnemyAnchorState(client);
        resetAnchorMacro(client, false);
    }

    private void resetEnemyAnchorState(MinecraftClient client) {
        anchorStage = AnchorStage.IDLE;
        anchorPos = null;
        anchorStageTicks = 0;
        anchorConfirmTicks = 0;
    }

    private void resetAnchorMacro(MinecraftClient client, boolean restore) {
        if (restore) {
            restoreAnchorSlot(client);
        }
        anchorMacroPos = null;
        resetAnchorMacroClocks();
    }

    private void resetAnchorMacroClocks() {
        anchorMacroSwitchClock = 0;
        anchorMacroChargeClock = 0;
        anchorMacroExplodeClock = 0;
    }

    private void restoreAll(MinecraftClient client) {
        restoreCrystal(client);
        resetAnchor(client, true);
    }

    private void tickCooldowns() {
        if (crystalBreakCooldown > 0 && --crystalBreakCooldown == 0) lastCrystalId = -1;
        if (crystalPlaceCooldown > 0) crystalPlaceCooldown--;
        if (crystalObsidianCooldown > 0) crystalObsidianCooldown--;
        if (anchorCooldown > 0) anchorCooldown--;
    }

    private void resetCrystalCounters() {
        crystalBreakCooldown = 0;
        crystalPlaceCooldown = 0;
        crystalObsidianCooldown = 0;
        lastCrystalId = -1;
    }

    private static boolean isHoldMode(String module) {
        return KhanhFeatures.choice(module, "activation", "Hold Use").equals("Hold Use");
    }

    private static boolean healthyEnough(MinecraftClient client, String module) {
        double health = client.player.getHealth() + client.player.getAbsorptionAmount();
        return health >= KhanhFeatures.decimal(module, "min_health", 12.0);
    }

    private static PlayerEntity nearestEnemy(MinecraftClient client, double range) {
        PlayerEntity nearest = null;
        double nearestDistance = range * range;
        for (PlayerEntity player : client.world.getPlayers()) {
            if (player == client.player || !player.isAlive() || player.isSpectator()
                || FriendService.isFriend(player.getGameProfile().name())) {
                continue;
            }
            double distance = client.player.squaredDistanceTo(player);
            if (distance <= nearestDistance) {
                nearestDistance = distance;
                nearest = player;
            }
        }
        return nearest;
    }

    private static EndCrystalEntity nearestCrystal(MinecraftClient client, PlayerEntity target,
                                                    double reach, double selfDistance) {
        EndCrystalEntity nearest = null;
        double targetDistance = Double.MAX_VALUE;
        double reachSquared = reach * reach;
        double safeSquared = selfDistance * selfDistance;
        for (Entity entity : client.world.getEntities()) {
            if (!(entity instanceof EndCrystalEntity crystal) || !crystal.isAlive()) {
                continue;
            }
            double fromSelf = client.player.squaredDistanceTo(crystal);
            double fromTarget = target.squaredDistanceTo(crystal);
            if (fromSelf > reachSquared || fromSelf < safeSquared || fromTarget > 36.0) {
                continue;
            }
            if (fromTarget < targetDistance) {
                targetDistance = fromTarget;
                nearest = crystal;
            }
        }
        return nearest;
    }

    private static BlockPos findCrystalBase(MinecraftClient client, PlayerEntity target,
                                            double reach, double selfDistance) {
        BlockPos center = target.getBlockPos();
        BlockPos best = null;
        double bestTargetDistance = Double.MAX_VALUE;
        for (int dy = -2; dy <= 0; dy++) {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    BlockPos base = center.add(dx, dy, dz);
                    if (!isCrystalBase(client.world.getBlockState(base)) || !canFitCrystal(client, base)) {
                        continue;
                    }
                    double fromSelf = squaredDistance(client.player, base.up());
                    double fromTarget = squaredDistance(target, base.up());
                    if (fromSelf > reach * reach || fromSelf < selfDistance * selfDistance || fromTarget > 16.0) {
                        continue;
                    }
                    if (fromTarget < bestTargetDistance) {
                        bestTargetDistance = fromTarget;
                        best = base.toImmutable();
                    }
                }
            }
        }
        return best;
    }

    private static BlockPos findObsidianSupport(MinecraftClient client, PlayerEntity target,
                                                double reach, double selfDistance) {
        BlockPos center = target.getBlockPos();
        BlockPos best = null;
        double bestTargetDistance = Double.MAX_VALUE;
        for (int dy = -2; dy <= 0; dy++) {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    BlockPos support = center.add(dx, dy, dz);
                    BlockPos base = support.up();
                    if (!client.world.getBlockState(support).isSolidBlock(client.world, support)
                        || !client.world.getBlockState(base).isReplaceable() || !canFitCrystal(client, base)) {
                        continue;
                    }
                    double fromSelf = squaredDistance(client.player, base.up());
                    double fromTarget = squaredDistance(target, base.up());
                    if (fromSelf > reach * reach || fromSelf < selfDistance * selfDistance || fromTarget > 16.0) {
                        continue;
                    }
                    if (fromTarget < bestTargetDistance) {
                        bestTargetDistance = fromTarget;
                        best = support.toImmutable();
                    }
                }
            }
        }
        return best;
    }

    private static BlockPos findAnchor(MinecraftClient client, PlayerEntity target,
                                       double reach, double selfDistance) {
        BlockPos center = target.getBlockPos();
        BlockPos best = null;
        double bestTargetDistance = Double.MAX_VALUE;
        for (int dy = -2; dy <= 2; dy++) {
            for (int dx = -3; dx <= 3; dx++) {
                for (int dz = -3; dz <= 3; dz++) {
                    BlockPos pos = center.add(dx, dy, dz);
                    if (!client.world.getBlockState(pos).isOf(Blocks.RESPAWN_ANCHOR)) {
                        continue;
                    }
                    double fromSelf = squaredDistance(client.player, pos);
                    double fromTarget = squaredDistance(target, pos);
                    if (fromSelf > reach * reach || fromSelf < selfDistance * selfDistance) {
                        continue;
                    }
                    if (fromTarget < bestTargetDistance) {
                        bestTargetDistance = fromTarget;
                        best = pos.toImmutable();
                    }
                }
            }
        }
        return best;
    }

    private static BlockPos findAnchorSupport(MinecraftClient client, PlayerEntity target,
                                              double reach, double selfDistance) {
        BlockPos center = target.getBlockPos();
        BlockPos best = null;
        double bestTargetDistance = Double.MAX_VALUE;
        for (int dy = -2; dy <= 0; dy++) {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    BlockPos support = center.add(dx, dy, dz);
                    BlockPos placed = support.up();
                    if (!client.world.getBlockState(support).isSolidBlock(client.world, support)
                        || !client.world.getBlockState(placed).isReplaceable() || !spaceIsClear(client, placed, 1)) {
                        continue;
                    }
                    double fromSelf = squaredDistance(client.player, placed);
                    double fromTarget = squaredDistance(target, placed);
                    if (fromSelf > reach * reach || fromSelf < selfDistance * selfDistance || fromTarget > 12.25) {
                        continue;
                    }
                    if (fromTarget < bestTargetDistance) {
                        bestTargetDistance = fromTarget;
                        best = support.toImmutable();
                    }
                }
            }
        }
        return best;
    }

    private static boolean canFitCrystal(MinecraftClient client, BlockPos base) {
        BlockPos lower = base.up();
        // Minecraft 1.21.11 requires only the block directly above the base to
        // be air; entity collision is still checked across the full 1x2 volume.
        if (!client.world.getBlockState(lower).isAir()) {
            return false;
        }
        return spaceIsClear(client, lower, 2);
    }

    private static EndCrystalEntity crystalOn(MinecraftClient client, BlockPos base) {
        BlockPos up = base.up();
        Box region = new Box(up.getX() + 0.125, up.getY() - 0.1, up.getZ() + 0.125,
            up.getX() + 0.875, up.getY() + 2.5, up.getZ() + 0.875);
        java.util.List<EndCrystalEntity> found = client.world.getEntitiesByClass(
            EndCrystalEntity.class, region, entity -> entity.isAlive());
        return found.isEmpty() ? null : found.getFirst();
    }

    private static boolean isCrystalBase(BlockState state) {
        return state.isOf(Blocks.OBSIDIAN) || state.isOf(Blocks.BEDROCK);
    }

    private static boolean spaceIsClear(MinecraftClient client, BlockPos pos, int height) {
        Box box = new Box(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1.0, pos.getY() + height, pos.getZ() + 1.0);
        return client.world.getOtherEntities(null, box, entity -> entity.isAlive() && !entity.isSpectator()).isEmpty();
    }

    private static double squaredDistance(PlayerEntity player, BlockPos pos) {
        return player.squaredDistanceTo(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
    }

    private static boolean canReach(MinecraftClient client, BlockPos pos, double reach) {
        return client.player.getEyePos().squaredDistanceTo(Vec3d.ofCenter(pos)) <= reach * reach;
    }

    private static int anchorCharges(MinecraftClient client, BlockPos pos) {
        BlockState state = client.world.getBlockState(pos);
        return state.isOf(Blocks.RESPAWN_ANCHOR) ? state.get(RespawnAnchorBlock.CHARGES) : -1;
    }

    private static int findHotbarItem(MinecraftClient client, Item item) {
        for (int slot = 0; slot < 9; slot++) {
            if (client.player.getInventory().getStack(slot).isOf(item)) {
                return slot;
            }
        }
        return -1;
    }

    private static int findHotbarItemExcept(MinecraftClient client, Item excluded) {
        for (int slot = 0; slot < 9; slot++) {
            if (!client.player.getInventory().getStack(slot).isOf(excluded)) {
                return slot;
            }
        }
        return -1;
    }

    private static void selectSlot(MinecraftClient client, int slot) {
        if (slot < 0 || slot > 8 || client.player.getInventory().getSelectedSlot() == slot) {
            return;
        }
        client.player.getInventory().setSelectedSlot(slot);
        client.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(slot));
    }

    private static boolean interact(MinecraftClient client, Hand hand, BlockHitResult hit) {
        if (hand == null) {
            return false;
        }
        boolean accepted = client.interactionManager.interactBlock(client.player, hand, hit).isAccepted();
        if (accepted) {
            client.player.swingHand(hand);
        }
        return accepted;
    }

    private static BlockHitResult hit(BlockPos pos, Direction side) {
        Vec3d hitPosition = Vec3d.ofCenter(pos).add(0.0, side == Direction.UP ? 0.5 : 0.0, 0.0);
        return new BlockHitResult(hitPosition, side, pos, false);
    }

    private static boolean rightMouseHeld(MinecraftClient client) {
        return GLFW.glfwGetMouseButton(client.getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS;
    }

    private static void suppressVanillaUse(MinecraftClient client) {
        client.options.useKey.setPressed(false);
    }

    private static boolean aimedAnchor(MinecraftClient client) {
        return client.crosshairTarget instanceof BlockHitResult hit
            && hit.getType() == HitResult.Type.BLOCK
            && client.world.getBlockState(hit.getBlockPos()).isOf(Blocks.RESPAWN_ANCHOR);
    }

    private static boolean prefersAnchor(MinecraftClient client) {
        if (aimedAnchor(client)) {
            return true;
        }
        return client.player.getMainHandStack().isOf(Items.RESPAWN_ANCHOR)
            || client.player.getMainHandStack().isOf(Items.GLOWSTONE)
            || client.player.getOffHandStack().isOf(Items.RESPAWN_ANCHOR)
            || client.player.getOffHandStack().isOf(Items.GLOWSTONE);
    }

    private static boolean holdsAnchorPart(MinecraftClient client) {
        return client.player.getMainHandStack().isOf(Items.RESPAWN_ANCHOR)
            || client.player.getMainHandStack().isOf(Items.GLOWSTONE)
            || client.player.getOffHandStack().isOf(Items.RESPAWN_ANCHOR)
            || client.player.getOffHandStack().isOf(Items.GLOWSTONE);
    }

    private enum AnchorClock {
        SWITCH,
        CHARGE,
        EXPLODE
    }

    private enum AnchorStage {
        IDLE,
        WAIT_PLACE,
        WAIT_CHARGE,
        SWITCH_DETONATOR,
        WAIT_DETONATE,
        WAIT_RESTORE
    }
}
