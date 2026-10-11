package dev.khanh.feature;

import dev.khanh.KhanhClient;
import dev.khanh.config.ConfigManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.world.ClientChunkManager;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public final class FreecamService {
    private static boolean active;
    private static boolean freeLookActive;
    private static ClientPlayerEntity owner;
    private static Vec3d position;
    private static float bodyYaw;
    private static float bodyPitch;
    private static float yaw;
    private static float pitch;
    private static Perspective previousPerspective;
    private static ChunkPos lastChunkCenter;
    private static int lastLoadDistance = Integer.MIN_VALUE;
    private static boolean savedChunkCulling;
    private static boolean chunkCullingCaptured;
    private static Vec3d velocity = Vec3d.ZERO;
    private static long lastFrame;
    private static float viewYaw;
    private static float viewPitch;

    private FreecamService() {
    }

    public static void tick(MinecraftClient client) {
        boolean requested = KhanhFeatures.enabled("freecam");
        boolean freeLookRequested = KhanhFeatures.enabled("freelook") && !requested;
        if (client.player == null || client.world == null) {
            if (active) {
                stop(true);
            }
            if (freeLookActive) {
                stopFreeLook();
            }
            return;
        }
        if (!requested) {
            if (active) {
                stop(true);
            }
            tickFreeLook(client, freeLookRequested);
            return;
        }
        if (freeLookActive) {
            stopFreeLook();
        }
        if (!active || owner != client.player) {
            start(client.player);
        }

        ClientPlayerEntity player = client.player;

        yaw += MathHelper.wrapDegrees(player.getYaw() - bodyYaw);
        pitch = MathHelper.clamp(pitch + player.getPitch() - bodyPitch, -90.0F, 90.0F);
        // Looking up and down with the camera also tilts your player's aim, so you can mine the
        // block above or below while turning left and right stays free.
        if (KhanhFeatures.bool("freecam", "aim_follows_pitch", true)) bodyPitch = pitch;
        player.setYaw(bodyYaw);
        player.setPitch(bodyPitch);

        syncChunkRendering(client);
    }

    /**
     * Per-frame camera motion, called from the camera every frame: velocity eases toward where the
     * keys point (time-constant smoothing, so it is the same at any FPS), and the view eases toward
     * where the mouse points. Nothing moves in whole-tick steps, so it glides.
     */
    public static void frame(MinecraftClient client) {
        if (!active || position == null) return;
        long now = System.nanoTime();
        double dt = lastFrame == 0L ? 0.0 : Math.min(0.1, (now - lastFrame) / 1.0e9);
        lastFrame = now;
        if (dt <= 0.0) return;

        Vec3d wish = Vec3d.ZERO;
        if (client.currentScreen == null) {
            double forward = key(client.options.forwardKey) - key(client.options.backKey);
            double strafe = key(client.options.leftKey) - key(client.options.rightKey);
            double vertical = key(client.options.jumpKey) - key(client.options.sneakKey);
            double horizontalLength = Math.hypot(forward, strafe);
            if (horizontalLength > 1.0) {
                forward /= horizontalLength;
                strafe /= horizontalLength;
            }
            // Speeds are blocks per tick in the menu; the camera moves in blocks per second.
            double speed = KhanhFeatures.decimal("freecam", "speed", 0.65) * 20.0;
            if (client.options.sprintKey.isPressed()) speed *= 2.0;
            double radians = Math.toRadians(viewYaw);
            double sin = Math.sin(radians), cos = Math.cos(radians);
            wish = new Vec3d((-sin * forward + cos * strafe) * speed,
                vertical * KhanhFeatures.decimal("freecam", "vertical_speed", 0.55) * 20.0,
                (cos * forward + sin * strafe) * speed);
        }
        double smooth = KhanhFeatures.decimal("freecam", "smoothness", 0.55);
        double moveTau = 0.015 + smooth * 0.3;
        velocity = velocity.lerp(wish, 1.0 - Math.exp(-dt / moveTau));
        if (wish.lengthSquared() == 0.0 && velocity.lengthSquared() < 1.0e-6) velocity = Vec3d.ZERO;
        position = position.add(velocity.multiply(dt));

        if (KhanhFeatures.bool("freecam", "smooth_look", true)) {
            double k = 1.0 - Math.exp(-dt / (0.012 + smooth * 0.06));
            viewYaw += (float) (MathHelper.wrapDegrees(yaw - viewYaw) * k);
            viewPitch += (float) ((pitch - viewPitch) * k);
        } else {
            viewYaw = yaw;
            viewPitch = pitch;
        }
    }


    private static double key(net.minecraft.client.option.KeyBinding binding) {
        return binding.isPressed() ? 1.0 : 0.0;
    }

    private static void start(ClientPlayerEntity player) {
        owner = player;
        bodyYaw = player.getYaw();
        bodyPitch = player.getPitch();
        yaw = bodyYaw;
        pitch = bodyPitch;
        position = player.getEyePos();
        velocity = Vec3d.ZERO;
        lastFrame = 0L;
        viewYaw = yaw;
        viewPitch = pitch;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.options.getPerspective().isFirstPerson()) {
            previousPerspective = client.options.getPerspective();
            client.options.setPerspective(Perspective.THIRD_PERSON_BACK);
        } else {
            previousPerspective = null;
        }
        savedChunkCulling = client.chunkCullingEnabled;
        chunkCullingCaptured = true;
        client.chunkCullingEnabled = false;
        lastChunkCenter = null;
        lastLoadDistance = Integer.MIN_VALUE;
        active = true;
        syncChunkRendering(client);
    }

    public static void stop(boolean restoreBody) {
        if (owner != null && restoreBody) {
            owner.setYaw(bodyYaw);
            owner.setPitch(bodyPitch);
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (previousPerspective != null) {
            client.options.setPerspective(previousPerspective);
            previousPerspective = null;
        }
        restoreChunkRendering(client);
        active = false;
        owner = null;
    }

    private static void tickFreeLook(MinecraftClient client, boolean requested) {
        if (!requested) {
            if (freeLookActive) {
                stopFreeLook();
            }
            return;
        }
        ClientPlayerEntity player = client.player;
        if (!freeLookActive || owner != player) {
            owner = player;
            bodyYaw = player.getYaw();
            bodyPitch = player.getPitch();
            yaw = bodyYaw;
            pitch = bodyPitch;
            previousPerspective = client.options.getPerspective();
            client.options.setPerspective(Perspective.THIRD_PERSON_BACK);
            freeLookActive = true;
        }
        player.setYaw(bodyYaw);
        player.setPitch(bodyPitch);
    }

    public static boolean changeFreeLookDirection(double horizontal, double vertical) {
        if (!freeLookActive && !active) {
            return false;
        }
        yaw += (float) (horizontal * 0.15);
        pitch = MathHelper.clamp(pitch + (float) (vertical * 0.15), -90.0F, 90.0F);
        return true;
    }

    private static void stopFreeLook() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (owner != null) {
            owner.setYaw(bodyYaw);
            owner.setPitch(bodyPitch);
        }
        if (previousPerspective != null) {
            client.options.setPerspective(previousPerspective);
            previousPerspective = null;
        }
        freeLookActive = false;
        owner = null;
    }

    public static void toggle() {
        var module = KhanhClient.modules().byId("freecam");
        if (module == null) {
            return;
        }
        module.toggle();
        if (!module.enabled() && active) {
            stop(true);
        }
        ConfigManager.save(KhanhClient.modules());
    }

    /** Scroll wheel in Freecam: each notch speeds the camera up or down by about a fifth, and it sticks. */
    public static void adjustSpeed(double notches) {
        dev.khanh.module.Module freecam = dev.khanh.KhanhClient.modules().byId("freecam");
        dev.khanh.module.ModuleOption option = freecam == null ? null : freecam.option("speed");
        if (option == null || notches == 0.0) return;
        double next = Math.max(0.05, Math.min(5.0, option.numberValue() * Math.pow(1.2, Math.signum(notches))));
        option.setNumber(next);
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            client.player.sendMessage(net.minecraft.text.Text.literal(String.format(java.util.Locale.ROOT,
                "Freecam speed %.2f", option.numberValue())), true);
        }
    }

    /** Turn the real body to face what the camera is mining, so the server sees a normal swing. */
    public static void faceBody(float yaw, float pitch) {
        bodyYaw = yaw;
        bodyPitch = MathHelper.clamp(pitch, -90.0F, 90.0F);
        if (owner != null) {
            owner.setYaw(bodyYaw);
            owner.setPitch(bodyPitch);
        }
    }

    public static boolean active() {
        return active;
    }

    public static boolean freeLookActive() {
        return freeLookActive;
    }

    public static boolean bodyFollowsKeys() {
        return KhanhFeatures.bool("freecam", "body_follows_keys", false);
    }

    /** Freecam moves per frame (see {@link #frame}); free look follows the mouse every frame. */
    public static Vec3d position(float tickProgress) {
        return position == null ? Vec3d.ZERO : position;
    }

    public static float yaw(float tickProgress) {
        return active ? viewYaw : yaw;
    }

    public static float pitch(float tickProgress) {
        return active ? viewPitch : pitch;
    }

    private static void syncChunkRendering(MinecraftClient client) {
        if (!active || position == null || client.world == null || client.player == null) {
            return;
        }

        ClientChunkManager chunks = client.world.getChunkManager();
        ChunkPos cameraChunk = new ChunkPos(
            MathHelper.floor(position.x) >> 4,
            MathHelper.floor(position.z) >> 4
        );
        ChunkPos playerChunk = client.player.getChunkPos();

        int separation = Math.max(
            Math.abs(cameraChunk.x - playerChunk.x),
            Math.abs(cameraChunk.z - playerChunk.z)
        );
        ChunkPos renderCenter = cameraChunk;
        if (separation + 4 > 32) {
            renderCenter = new ChunkPos(
                Math.floorDiv(cameraChunk.x + playerChunk.x, 2),
                Math.floorDiv(cameraChunk.z + playerChunk.z, 2)
            );
        }

        int furthestNeeded = Math.max(
            Math.max(Math.abs(renderCenter.x - cameraChunk.x), Math.abs(renderCenter.z - cameraChunk.z)),
            Math.max(Math.abs(renderCenter.x - playerChunk.x), Math.abs(renderCenter.z - playerChunk.z))
        );
        int preferred = MathHelper.clamp(KhanhFeatures.number("freecam", "chunk_distance", 12), 2, 32);
        int loadDistance = Math.min(32, Math.max(preferred, furthestNeeded + 4));

        if (loadDistance != lastLoadDistance) {
            chunks.updateLoadDistance(loadDistance);
            lastLoadDistance = loadDistance;
        }
        if (lastChunkCenter == null || renderCenter.x != lastChunkCenter.x || renderCenter.z != lastChunkCenter.z) {
            chunks.setChunkMapCenter(renderCenter.x, renderCenter.z);
            lastChunkCenter = renderCenter;
            client.worldRenderer.scheduleTerrainUpdate();
        }
    }

    private static void restoreChunkRendering(MinecraftClient client) {
        lastChunkCenter = null;
        lastLoadDistance = Integer.MIN_VALUE;
        if (chunkCullingCaptured) {
            client.chunkCullingEnabled = savedChunkCulling;
            chunkCullingCaptured = false;
        }
        if (client.world == null) {
            return;
        }
        ClientChunkManager chunks = client.world.getChunkManager();
        if (client.player != null) {
            ChunkPos playerChunk = client.player.getChunkPos();
            chunks.setChunkMapCenter(playerChunk.x, playerChunk.z);
        }
        chunks.updateLoadDistance(client.options.getClampedViewDistance());
        client.worldRenderer.scheduleTerrainUpdate();
    }
}
