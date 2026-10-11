package dev.khanh.feature;

import java.util.List;

/** Deterministic radar maths and collision-free label placement, independent of Minecraft. */
public final class RadarLayout {
    public record Point(double x, double y) { }
    public record Rect(int x, int y, int width, int height) {
        public boolean intersects(Rect other, int gap) {
            return x < other.x + other.width + gap && x + width + gap > other.x
                && y < other.y + other.height + gap && y + height + gap > other.y;
        }
    }
    private RadarLayout() { }

    public static Point project(double dx, double dz, double yawDegrees, boolean viewUp, double scale) {
        if (!Double.isFinite(dx) || !Double.isFinite(dz) || !Double.isFinite(yawDegrees) || !Double.isFinite(scale))
            return new Point(0, 0);
        if (!viewUp) return new Point(dx * scale, dz * scale); // north is -Z
        double yaw = Math.toRadians(yawDegrees), c = Math.cos(yaw), s = Math.sin(yaw);
        // Minecraft yaw 0 faces +Z; forward must be UP and right is -X.
        return new Point((-dx * c - dz * s) * scale, (dx * s - dz * c) * scale);
    }

    public static boolean inside(Rect rect, double radius) {
        for (double x : new double[]{rect.x, rect.x + rect.width})
            for (double y : new double[]{rect.y, rect.y + rect.height})
                if (x * x + y * y > radius * radius) return false;
        return true;
    }

    public static Rect label(double x, double y, int headSize, int width, int height,
                             double radius, List<Rect> occupied) {
        int hx = (int) Math.round(x), hy = (int) Math.round(y), offset = headSize / 2 + 5;
        Rect[] candidates = {
            new Rect(hx - width / 2, hy - offset - height, width, height),
            new Rect(hx - width / 2, hy + offset, width, height),
            new Rect(hx + offset, hy - height / 2, width, height),
            new Rect(hx - offset - width, hy - height / 2, width, height)
        };
        for (Rect rect : candidates) {
            if (!inside(rect, radius)) continue;
            boolean free = true;
            for (Rect other : occupied) if (rect.intersects(other, 2)) { free = false; break; }
            if (free) return rect;
        }
        return null; // keep the portrait visible instead of overlapping text
    }
}
