package dev.khanh.feature;

import net.minecraft.client.MinecraftClient;

/** Sprint control retained from the optional movement set. */
public final class MovementAssistService {
    private MovementAssistService() { }

    private static boolean flyWasOn;
    private static boolean walking;

    /** Auto Sprint's auto walk: holds W for you; let go of it by pressing S or turning it off. */
    private static void tickAutoWalk(MinecraftClient client) {
        var forward = client.options.forwardKey;
        boolean want = KhanhFeatures.enabled("auto_sprint") && KhanhFeatures.bool("auto_sprint", "auto_walk", true)
            && client.currentScreen == null && client.player.isAlive() && !FreecamService.active()
            && !client.options.backKey.isPressed()
            && !(KhanhFeatures.bool("auto_sprint", "stop_in_water", false) && (client.player.isTouchingWater() || client.player.isInLava()));
        if (want) {
            forward.setPressed(true);
            walking = true;
        } else if (walking) {
            walking = false;
            // Give W back to the real key: only stay pressed if you are actually holding it.
            forward.setPressed(net.minecraft.client.util.InputUtil.isKeyPressed(client.getWindow(),
                net.minecraft.client.util.InputUtil.fromTranslationKey(forward.getBoundKeyTranslationKey()).getCode()));
        }
    }

    public static void tick(MinecraftClient client) {
        if (client.player == null || client.world == null) {
            flyWasOn = false;
            return;
        }
        tickFly(client);
        tickAutoWalk(client);
        if (client.currentScreen != null || !client.player.isAlive() || FreecamService.active()) return;
        if (KhanhFeatures.enabled("auto_sprint") && !CombatPvpService.sprintResetActive()
            && client.options.forwardKey.isPressed()
            && !client.player.isSneaking() && !client.player.horizontalCollision
            && client.player.getHungerManager().getFoodLevel() > 6) client.player.setSprinting(true);
    }

    /**
     * Fly: Creative mode gives you creative-style flight (double-tap jump, space up, shift down);
     * Velocity mode moves you straight from your keys with no gliding. Servers with an anticheat
     * (DonutSMP included) will kick or ban for flying, so this is for singleplayer and your own worlds.
     */
    private static void tickFly(MinecraftClient client) {
        var player = client.player;
        var abilities = player.getAbilities();
        if (!KhanhFeatures.enabled("fly") || !player.isAlive()) {
            if (flyWasOn) {
                boolean allowed = player.isCreative() || player.isSpectator();
                abilities.allowFlying = allowed;
                if (!allowed) abilities.flying = false;
                abilities.setFlySpeed(0.05F);
                flyWasOn = false;
            }
            return;
        }
        float speed = (float) KhanhFeatures.decimal("fly", "speed", 1.0);
        if (KhanhFeatures.choice("fly", "mode", "Creative").equals("Velocity")) {
            abilities.flying = false;
            var input = player.input.getMovementInput();
            double yaw = Math.toRadians(player.getYaw());
            double forward = input.y, strafe = input.x;
            double horizontal = 0.55 * speed;
            double vx = (strafe * Math.cos(yaw) - forward * Math.sin(yaw)) * horizontal;
            double vz = (forward * Math.cos(yaw) + strafe * Math.sin(yaw)) * horizontal;
            boolean up = client.options.jumpKey.isPressed(), down = client.options.sneakKey.isPressed();
            double vy = up && !down ? 0.45 * speed : down && !up ? -0.45 * speed : 0.0;
            if (vy == 0.0 && KhanhFeatures.bool("fly", "anti_kick", true) && player.age % 40 == 0) vy = -0.04;
            player.setVelocity(vx, vy, vz);
            player.fallDistance = 0.0;
        } else {
            abilities.allowFlying = true;
            abilities.setFlySpeed(0.05F * speed);
            if (!flyWasOn && KhanhFeatures.bool("fly", "start_flying", true) && !player.isOnGround()) abilities.flying = true;
        }
        flyWasOn = true;
    }
}
