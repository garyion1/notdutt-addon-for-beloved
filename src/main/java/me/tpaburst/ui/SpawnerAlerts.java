package me.tpaburst.ui;

import java.util.ArrayList;
import java.util.List;
import meteordevelopment.meteorclient.MeteorClient;

/** Active spawner alerts shown by the movable {@link me.tpaburst.hud.SpawnerAlertsHud}. */
public final class SpawnerAlerts {
    public record Alert(String mobType, int x, int y, int z, String depth, long start, long duration, int accentRgb) {
        public float remaining(long now) {
            return 1f - Math.max(0f, Math.min(1f, (float) (now - start) / duration));
        }
    }

    private static final int MAX = 5;
    private static final List<Alert> ALERTS = new ArrayList<>();

    private SpawnerAlerts() {
    }

    public static String depthLabel(int y) {
        int playerY = MeteorClient.mc.player != null ? MeteorClient.mc.player.getBlockY() : y;
        return y < playerY ? "below" : (y > playerY ? "above" : "nearby");
    }

    public static synchronized void push(String mobType, int x, int y, int z, long durationMs, int accentRgb) {
        ALERTS.add(0, new Alert(mobType, x, y, z, depthLabel(y), System.currentTimeMillis(), Math.max(2500L, durationMs), accentRgb));
        while (ALERTS.size() > MAX) ALERTS.remove(ALERTS.size() - 1);
    }

    public static synchronized List<Alert> active() {
        long now = System.currentTimeMillis();
        ALERTS.removeIf(a -> now - a.start() >= a.duration());
        return new ArrayList<>(ALERTS);
    }

    public static synchronized void clear() {
        ALERTS.clear();
    }
}
