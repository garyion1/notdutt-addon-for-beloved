package dev.khanh.feature;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.DrawStyle;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.debug.gizmo.GizmoDrawing;

/**
 * Motion Scanner: adds up how much everything moves in each loaded chunk (mobs in a farm, items on
 * a belt, minecarts, players...). A chunk with a lot of motion gets a small glowing green pillar,
 * and a notification card the first time it lights up. Busy chunks far from you are a base sign.
 */
public final class MotionScanner {
    /** Movement score for one chunk: decays over time, so only sustained motion stays lit. */
    private static final class Activity {
        double score;
        double y;
        int samples;
    }

    private static final Map<Integer, Vec3d> LAST = new HashMap<>();
    private static final Map<Long, Activity> CHUNKS = new HashMap<>();
    private static final Set<Long> ANNOUNCED = new HashSet<>();
    private static Object lastWorld;
    private static int ticks;

    private MotionScanner() { }

    private static double threshold() {
        return switch (KhanhFeatures.choice("motion_scanner", "sensitivity", "Medium")) {
            case "Low" -> 60.0;
            case "High" -> 12.0;
            default -> 28.0;
        };
    }

    public static void tick(MinecraftClient client) {
        boolean on = KhanhFeatures.enabled("motion_scanner");
        if (client.world != lastWorld || !on) {
            lastWorld = client.world;
            LAST.clear();
            CHUNKS.clear();
            ANNOUNCED.clear();
            if (!on) return;
        }
        if (client.world == null || client.player == null || ++ticks % 10 != 0) return;
        boolean players = KhanhFeatures.bool("motion_scanner", "players", true);
        Map<Integer, Vec3d> seen = new HashMap<>();
        for (Activity activity : CHUNKS.values()) activity.score *= 0.82;   // half-life of about two seconds
        for (Entity entity : client.world.getEntities()) {
            if (entity == client.player || entity.hasVehicle() && entity.getVehicle() == client.player) continue;
            if (!players && entity instanceof PlayerEntity) continue;
            Vec3d pos = entity.getEntityPos();
            seen.put(entity.getId(), pos);
            Vec3d last = LAST.get(entity.getId());
            if (last == null) continue;
            double moved = Math.min(4.0, last.distanceTo(pos));   // teleports don't count as motion
            if (moved < 0.05) continue;
            long key = ChunkPos.toLong(entity.getBlockX() >> 4, entity.getBlockZ() >> 4);
            Activity activity = CHUNKS.computeIfAbsent(key, k -> new Activity());
            activity.score += moved;
            activity.y = (activity.y * activity.samples + pos.y) / (activity.samples + 1);
            activity.samples = Math.min(200, activity.samples + 1);
        }
        LAST.clear();
        LAST.putAll(seen);
        CHUNKS.values().removeIf(activity -> activity.score < 0.5);

        double threshold = threshold();
        double minDistance = KhanhFeatures.number("motion_scanner", "min_distance", 24);
        for (Map.Entry<Long, Activity> entry : CHUNKS.entrySet()) {
            if (entry.getValue().score < threshold || ANNOUNCED.contains(entry.getKey())) continue;
            ChunkPos chunk = new ChunkPos(entry.getKey());
            BlockPos at = new BlockPos(chunk.getCenterX(), (int) Math.round(entry.getValue().y), chunk.getCenterZ());
            double distance = Math.sqrt(client.player.squaredDistanceTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5));
            if (distance < minDistance) continue;
            ANNOUNCED.add(entry.getKey());
            if (false) {   // no pop-ups in Khanh Client
                FinderNotifications.show("Lots of motion in a chunk", "X " + at.getX() + "  Y " + at.getY() + "  Z " + at.getZ()
                    + "  ·  " + Math.round(distance) + "m", KhanhFeatures.color("motion_scanner", "color", 0xFF39FF6A),
                    KhanhFeatures.bool("visuals", "finder_sound", true));
            }
        }
    }

    /** A small green pillar standing in every busy chunk, glowing brighter the busier it is. */
    public static void render() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null) return;
        double threshold = threshold();
        double minDistance = KhanhFeatures.number("motion_scanner", "min_distance", 24);
        int colour = KhanhFeatures.color("motion_scanner", "color", 0xFF39FF6A) & 0xFFFFFF;
        double height = KhanhFeatures.decimal("motion_scanner", "height", 6.0);
        double seconds = (System.nanoTime() % 3_600_000_000_000L) / 1.0e9;
        for (Map.Entry<Long, Activity> entry : CHUNKS.entrySet()) {
            Activity activity = entry.getValue();
            if (activity.score < threshold) continue;
            ChunkPos chunk = new ChunkPos(entry.getKey());
            double x = chunk.getCenterX() + 0.5, z = chunk.getCenterZ() + 0.5, y = Math.floor(activity.y);
            if (client.player.squaredDistanceTo(x, y, z) < minDistance * minDistance) continue;
            float busy = (float) Math.min(1.0, activity.score / (threshold * 3.0));
            float pulse = 0.75F + 0.25F * (float) Math.sin(seconds * 3.0 + entry.getKey() * 0.001);
            int fill = Math.round((60 + 120 * busy) * pulse) << 24 | colour;
            Box pillar = new Box(x - 0.3, y, z - 0.3, x + 0.3, y + height, z + 0.3);
            GizmoDrawing.box(pillar, DrawStyle.filledAndStroked(0xFF000000 | colour, 1.5F, fill), false).ignoreOcclusion();
            GizmoDrawing.box(pillar.expand(0.25, 0.0, 0.25), DrawStyle.filled(Math.round(40 * pulse) << 24 | colour), false).ignoreOcclusion();
        }
    }
}
