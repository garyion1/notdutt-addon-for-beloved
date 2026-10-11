package dev.khanh.feature;

/**
 * Zoom: glides in and out instead of snapping, and the scroll wheel changes how far you are
 * zoomed while it is on. With Hold to Zoom, the keybind only zooms while you hold it.
 */
public final class ZoomService {
    private static double zoomFov = -1.0;
    private static float progress;
    private static long lastNanos;
    private static boolean wasOn;

    private ZoomService() { }

    /** The FOV to use this frame, given what it would be without zoom. */
    public static float apply(float normalFov) {
        boolean on = KhanhFeatures.enabled("zoom");
        if (on && !wasOn && (zoomFov < 0.0 || KhanhFeatures.bool("zoom", "reset", true))) {
            zoomFov = KhanhFeatures.decimal("zoom", "fov", 24.0);
        }
        wasOn = on;
        long now = System.nanoTime();
        float seconds = lastNanos == 0L ? 0.0F : Math.min(0.1F, (now - lastNanos) / 1.0E9F);
        lastNanos = now;
        if (KhanhFeatures.bool("zoom", "smooth", true)) {
            float speed = (float) KhanhFeatures.decimal("zoom", "smooth_speed", 10.0);
            float target = on ? 1.0F : 0.0F;
            progress += (target - progress) * (1.0F - (float) Math.exp(-speed * seconds));
            if (Math.abs(target - progress) < 0.002F) progress = target;
        } else {
            progress = on ? 1.0F : 0.0F;
        }
        if (progress <= 0.0F) return normalFov;
        double fov = zoomFov < 0.0 ? KhanhFeatures.decimal("zoom", "fov", 24.0) : zoomFov;
        float eased = progress * progress * (3.0F - 2.0F * progress);
        return (float) (normalFov + (fov - normalFov) * eased);
    }

    /** Scroll while zoomed: up zooms further in, down zooms out. Returns true when it used the scroll. */
    public static boolean scroll(double amount) {
        if (!KhanhFeatures.enabled("zoom") || !KhanhFeatures.bool("zoom", "scroll", true) || amount == 0.0) return false;
        if (zoomFov < 0.0) zoomFov = KhanhFeatures.decimal("zoom", "fov", 24.0);
        zoomFov = Math.max(1.0, Math.min(90.0, zoomFov * (amount > 0 ? 0.85 : 1.0 / 0.85)));
        return true;
    }

    /** Lower mouse sensitivity while zoomed, so aiming far away is not twitchy. */
    public static double sensitivityScale() {
        if (progress <= 0.0F || !KhanhFeatures.bool("zoom", "lower_sensitivity", true)) return 1.0;
        double fov = zoomFov < 0.0 ? KhanhFeatures.decimal("zoom", "fov", 24.0) : zoomFov;
        double scale = Math.max(0.1, Math.min(1.0, fov / 70.0));
        return 1.0 + (scale - 1.0) * progress;
    }
}
