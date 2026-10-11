/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.fabricmc.api.EnvType
 *  net.fabricmc.api.Environment
 *  org.lwjgl.glfw.GLFW
 */
package dev.khanh.feature;

import dev.khanh.feature.FreecamService;
import dev.khanh.feature.FriendService;
import dev.khanh.feature.KhanhFeatures;
import dev.khanh.mixin.HandledScreenAccessor;
import java.util.Locale;
import java.util.function.Predicate;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.vehicle.ChestMinecartEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

@Environment(value=EnvType.CLIENT)
public final class CombatUtilityService {
    private boolean heldTotemLastTick;
    private int autoTotemTicks;
    private int antiTrapTicks;
    private boolean pearlKeyWasDown;
    private int pearlStage;
    private int pearlTicks;
    private int pearlPreviousSlot = -1;
    private boolean lungeKeyWasDown;
    private int lungeStage;
    private int lungeTicks;
    private int lungePreviousSlot = -1;

    public void tick(MinecraftClient client) {
        if (client.player == null || client.world == null || client.interactionManager == null) {
            this.resetTransient();
            return;
        }
        this.tickAutoDoubleHand(client);
        this.tickAutoInventoryTotem(client);
        this.tickHoverTotem(client);
        if (client.currentScreen != null || FreecamService.active()) {
            this.resetCombatKeys();
            return;
        }
        this.tickAimAssist(client);
        this.tickAntiTrap(client);
        this.tickKeyPearl(client);
        this.tickLunge(client);
    }

    private static volatile PlayerEntity aimTarget;
    private static long aimFrameNanos;

    /** Picks the target each tick; the turning itself happens every frame in {@link #aimFrame}. */
    private void tickAimAssist(MinecraftClient client) {
        aimTarget = null;
        if (!KhanhFeatures.enabled("aim_assist")) {
            return;
        }
        if (KhanhFeatures.bool("aim_assist", "attack_only", true) && !client.options.attackKey.isPressed()) {
            return;
        }
        double range = KhanhFeatures.decimal("aim_assist", "range", 6.0);
        PlayerEntity target = this.nearestTarget(client, range, KhanhFeatures.decimal("aim_assist", "fov", 100.0));
        if (target == null) {
            return;
        }
        KhanhFeatures.setTarget(target);
        aimTarget = target;
    }

    /**
     * Turns the view toward the target once per rendered frame, eased by real time, so it glides
     * at your frame rate instead of stepping 20 times a second.
     */
    public static void aimFrame(MinecraftClient client, float tickProgress) {
        long now = System.nanoTime();
        float seconds = aimFrameNanos == 0L ? 0.0F : Math.min(0.1F, (now - aimFrameNanos) / 1.0E9F);
        aimFrameNanos = now;
        PlayerEntity target = aimTarget;
        if (target == null || client.player == null || client.currentScreen != null || FreecamService.active()
            || !target.isAlive() || !KhanhFeatures.enabled("aim_assist")
            || KhanhFeatures.bool("aim_assist", "attack_only", true) && !client.options.attackKey.isPressed()) {
            return;
        }
        Vec3d from = client.player.getCameraPosVec(tickProgress);
        Vec3d base = target.getLerpedPos(tickProgress);
        double height = target.getHeight();
        Vec3d to = switch (KhanhFeatures.choice("aim_assist", "aim_point", "Chest")) {
            case "Head" -> base.add(0.0, height * 0.88, 0.0);
            case "Feet" -> base.add(0.0, height * 0.15, 0.0);
            case "Closest" -> new Vec3d(base.x, MathHelper.clamp(from.y, base.y + 0.1, base.y + height - 0.1), base.z);
            default -> base.add(0.0, height * 0.62, 0.0);
        };
        double dx = to.x - from.x, dy = to.y - from.y, dz = to.z - from.z;
        float wantedYaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0F;
        float wantedPitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        float yawGap = MathHelper.wrapDegrees(wantedYaw - client.player.getYaw());
        float pitchGap = wantedPitch - client.player.getPitch();
        if (Math.abs(yawGap) < 0.15F && Math.abs(pitchGap) < 0.15F) return;   // on target: no jitter
        float k;
        if (KhanhFeatures.bool("aim_assist", "instant", false)) {
            k = 1.0F;
        } else {
            // "speed" is the share of the gap closed per game tick; spread evenly over the frames.
            double speed = MathHelper.clamp(KhanhFeatures.decimal("aim_assist", "speed", 0.18), 0.01, 1.0);
            k = (float) (1.0 - Math.pow(1.0 - Math.min(0.999, speed), seconds * 20.0));
        }
        float maxTurn = (float) KhanhFeatures.decimal("aim_assist", "max_turn", 540.0) * Math.max(seconds, 1.0F / 240.0F);
        float yawStep = MathHelper.clamp(yawGap * k, -maxTurn, maxTurn);
        float pitchStep = MathHelper.clamp(pitchGap * k, -maxTurn, maxTurn);
        if (!KhanhFeatures.bool("aim_assist", "vertical", true)) pitchStep = 0.0F;
        client.player.setYaw(client.player.getYaw() + yawStep);
        client.player.lastYaw += yawStep;
        float pitch = MathHelper.clamp(client.player.getPitch() + pitchStep, -90.0F, 90.0F);
        client.player.lastPitch += pitch - client.player.getPitch();
        client.player.setPitch(pitch);
    }

    private PlayerEntity nearestTarget(MinecraftClient client, double range, double fov) {
        PlayerEntity best = null;
        double bestScore = Double.MAX_VALUE;
        for (PlayerEntity playerEntity : client.world.getPlayers()) {
            double score;
            double distance;
            if (playerEntity == client.player || !playerEntity.isAlive() || playerEntity.isSpectator() || FriendService.protectsTargets() && FriendService.isFriend(playerEntity.getGameProfile().name()) || (distance = (double)client.player.distanceTo(playerEntity)) > range || !KhanhFeatures.bool("aim_assist", "through_walls", false) && !client.player.canSee(playerEntity)) continue;
            Vec3d delta = playerEntity.getBoundingBox().getCenter().subtract(client.player.getEyePos());
            float yaw = (float)Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90.0f;
            double yawDifference = Math.abs(MathHelper.wrapDegrees(yaw - client.player.getYaw()));
            if (yawDifference > fov * 0.5 || !((score = yawDifference * 2.0 + distance) < bestScore)) continue;
            bestScore = score;
            best = playerEntity;
        }
        return best;
    }

    private void tickAntiTrap(MinecraftClient client) {
        if (!KhanhFeatures.enabled("anti_trap")) {
            this.antiTrapTicks = 0;
            return;
        }
        if (this.antiTrapTicks++ < KhanhFeatures.number("anti_trap", "interval", 10)) {
            return;
        }
        this.antiTrapTicks = 0;
        double range = KhanhFeatures.decimal("anti_trap", "range", 4.0);
        double rangeSquared = range * range;
        for (Entity entity : client.world.getEntities()) {
            if (entity == client.player || entity.squaredDistanceTo(client.player) > rangeSquared || !this.trapEntity(entity)) continue;
            entity.discard();
        }
    }

    private boolean trapEntity(Entity entity) {
        return KhanhFeatures.bool("anti_trap", "armor_stands", true) && entity instanceof ArmorStandEntity || KhanhFeatures.bool("anti_trap", "chest_minecarts", true) && entity instanceof ChestMinecartEntity;
    }

    private void tickAutoDoubleHand(MinecraftClient client) {
        int slot;
        boolean holding = client.player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING);
        if (KhanhFeatures.enabled("auto_double_hand") && this.heldTotemLastTick && !holding && (slot = CombatUtilityService.findHotbar(client, stack -> stack.isOf(Items.TOTEM_OF_UNDYING))) >= 0) {
            CombatUtilityService.selectSlot(client, slot);
        }
        this.heldTotemLastTick = holding;
    }

    private void tickAutoInventoryTotem(MinecraftClient client) {
        if (!KhanhFeatures.enabled("auto_inv_totem") || client.player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING)) {
            this.autoTotemTicks = 0;
            return;
        }
        // This module is deliberately inventory-only. Auto Totem owns closed-screen combat refills.
        if (!(client.currentScreen instanceof InventoryScreen)
            || client.player.currentScreenHandler != client.player.playerScreenHandler) {
            this.autoTotemTicks = 0;
            return;
        }
        this.autoTotemTicks = 0;
        boolean hotbar = KhanhFeatures.bool("auto_inv_totem", "from_hotbar", true);
        int slot = CombatUtilityService.findInventory(client, stack -> stack.isOf(Items.TOTEM_OF_UNDYING), hotbar);
        if (slot >= 0) {
            CombatUtilityService.swapIntoOffhand(client, CombatUtilityService.inventoryScreenSlot(slot));
        }
    }

    private void tickHoverTotem(MinecraftClient client) {
        Screen screen;
        if (!KhanhFeatures.enabled("hover_totem") || !((screen = client.currentScreen) instanceof HandledScreen)) {
            return;
        }
        HandledScreen handled = (HandledScreen)screen;
        Slot focused = ((HandledScreenAccessor)((Object)handled)).khanh$getFocusedSlot();
        if (focused == null || !focused.getStack().isOf(Items.TOTEM_OF_UNDYING)) {
            return;
        }
        if (!client.player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING)) {
            CombatUtilityService.swapIntoOffhand(client, focused.id);
            return;
        }
        if (!KhanhFeatures.bool("hover_totem", "hotbar_totem", true)) {
            return;
        }
        int hotbar = KhanhFeatures.number("hover_totem", "hotbar_slot", 1) - 1;
        if (!client.player.getInventory().getStack(hotbar).isOf(Items.TOTEM_OF_UNDYING)) {
            client.interactionManager.clickSlot(((ScreenHandler)handled.getScreenHandler()).syncId, focused.id, hotbar, SlotActionType.SWAP, client.player);
        }
        if (KhanhFeatures.bool("hover_totem", "select_hotbar", false)) {
            CombatUtilityService.selectSlot(client, hotbar);
        }
    }

    private void tickKeyPearl(MinecraftClient client) {
        if (!KhanhFeatures.enabled("key_pearl")) {
            this.pearlStage = 0;
            this.pearlKeyWasDown = false;
            return;
        }
        boolean down = CombatUtilityService.keyDown(client, KhanhFeatures.choice("key_pearl", "trigger", "V"));
        if (down && !this.pearlKeyWasDown && this.pearlStage == 0) {
            int slot = CombatUtilityService.findHotbar(client, stack -> stack.isOf(Items.ENDER_PEARL));
            if (slot >= 0) {
                this.pearlPreviousSlot = client.player.getInventory().getSelectedSlot();
                CombatUtilityService.selectSlot(client, slot);
                this.pearlTicks = 0;
                this.pearlStage = 1;
            } else if (KhanhFeatures.bool("key_pearl", "missing_message", true)) {
                client.player.sendMessage(Text.literal("[Khanh Client] No ender pearl in your hotbar"), true);
            }
        }
        this.pearlKeyWasDown = down;
        if (this.pearlStage == 1 && this.pearlTicks++ >= KhanhFeatures.number("key_pearl", "delay", 0)) {
            if (client.player.getMainHandStack().isOf(Items.ENDER_PEARL)) {
                client.interactionManager.interactItem(client.player, Hand.MAIN_HAND);
                client.player.swingHand(Hand.MAIN_HAND);
            }
            this.pearlTicks = 0;
            this.pearlStage = KhanhFeatures.bool("key_pearl", "switch_back", true) ? 2 : 0;
        } else if (this.pearlStage == 2 && this.pearlTicks++ >= KhanhFeatures.number("key_pearl", "switch_back_delay", 2)) {
            CombatUtilityService.selectSlot(client, this.pearlPreviousSlot);
            this.pearlStage = 0;
        }
    }

    private void tickLunge(MinecraftClient client) {
        if (!KhanhFeatures.enabled("lunge_macro")) {
            this.lungeStage = 0;
            this.lungeKeyWasDown = false;
            return;
        }
        boolean down = CombatUtilityService.keyDown(client, KhanhFeatures.choice("lunge_macro", "trigger", "R"));
        boolean continuous = KhanhFeatures.bool("lunge_macro", "continuous", false);
        if (!(!down || this.lungeStage != 0 || this.lungeKeyWasDown && !continuous || this.lungeKeyWasDown && this.lungeTicks++ < KhanhFeatures.number("lunge_macro", "continuous_delay", 10))) {
            this.startLunge(client);
        }
        if (!down) {
            this.lungeTicks = 0;
        }
        this.lungeKeyWasDown = down;
        if (this.lungeStage == 1 && ++this.lungeTicks >= KhanhFeatures.number("lunge_macro", "hold_ticks", 2)) {
            EntityHitResult hit;
            HitResult hitResult = client.crosshairTarget;
            if (hitResult instanceof EntityHitResult && (hit = (EntityHitResult)hitResult).getEntity().isAlive()) {
                client.interactionManager.attackEntity(client.player, hit.getEntity());
                client.player.swingHand(Hand.MAIN_HAND);
            }
            this.lungeTicks = 0;
            this.lungeStage = KhanhFeatures.bool("lunge_macro", "switch_back", true) ? 2 : 0;
        } else if (this.lungeStage == 2 && ++this.lungeTicks >= KhanhFeatures.number("lunge_macro", "switch_back_delay", 3)) {
            CombatUtilityService.selectSlot(client, this.lungePreviousSlot);
            this.lungeStage = 0;
            this.lungeTicks = 0;
        }
    }

    private void startLunge(MinecraftClient client) {
        int slot = CombatUtilityService.findHotbar(client, this::isLungeWeapon);
        if (slot < 0) {
            return;
        }
        this.lungePreviousSlot = client.player.getInventory().getSelectedSlot();
        CombatUtilityService.selectSlot(client, slot);
        this.lungeStage = 1;
        this.lungeTicks = 0;
    }

    private boolean isLungeWeapon(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        String name = stack.getName().getString().toLowerCase(Locale.ROOT);
        return stack.isOf(Items.TRIDENT) || name.contains("spear") || name.contains("lunge");
    }

    private static int findHotbar(MinecraftClient client, Predicate<ItemStack> predicate) {
        for (int slot = 0; slot < 9; ++slot) {
            if (!predicate.test(client.player.getInventory().getStack(slot))) continue;
            return slot;
        }
        return -1;
    }

    private static int findInventory(MinecraftClient client, Predicate<ItemStack> predicate, boolean includeHotbar) {
        int slot;
        int n = slot = includeHotbar ? 0 : 9;
        while (slot < 36) {
            if (predicate.test(client.player.getInventory().getStack(slot))) {
                return slot;
            }
            ++slot;
        }
        return -1;
    }

    private static int inventoryScreenSlot(int inventorySlot) {
        return inventorySlot < 9 ? 36 + inventorySlot : inventorySlot;
    }

    private static void swapIntoOffhand(MinecraftClient client, int screenSlot) {
        ScreenHandler screenHandler = client.player.currentScreenHandler;
        if (!(screenHandler instanceof PlayerScreenHandler)) {
            return;
        }
        PlayerScreenHandler handler = (PlayerScreenHandler)screenHandler;
        client.interactionManager.clickSlot(handler.syncId, screenSlot, 40, SlotActionType.SWAP, client.player);
    }

    private static void selectSlot(MinecraftClient client, int slot) {
        if (slot < 0 || slot > 8 || client.player.getInventory().getSelectedSlot() == slot) {
            return;
        }
        client.player.getInventory().setSelectedSlot(slot);
        client.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(slot));
    }

    private static boolean keyDown(MinecraftClient client, String key) {
        int code = switch (key) {
            case "G" -> 71;
            case "B" -> 66;
            case "C" -> 67;
            case "X" -> 88;
            case "F" -> 70;
            case "F7" -> 296;
            case "R" -> 82;
            default -> 86;
        };
        return GLFW.glfwGetKey((long)client.getWindow().getHandle(), (int)code) == 1;
    }

    private void resetCombatKeys() {
        this.pearlKeyWasDown = false;
        this.lungeKeyWasDown = false;
    }

    private void resetTransient() {
        this.heldTotemLastTick = false;
        this.autoTotemTicks = 0;
        this.antiTrapTicks = 0;
        this.pearlStage = 0;
        this.lungeStage = 0;
        this.resetCombatKeys();
    }
}
