package dev.khanh.gui;

import dev.khanh.module.Category;

/** Khanh Client's colour language: one hue per category, and a three-step scale for live stats. */
public final class KhanhPalette {
    public static final int GOOD = 0xFF34D399;
    public static final int OKAY = 0xFFFBBF24;
    public static final int BAD = 0xFFFB7185;
    public static final int VIOLET = 0xFFA78BFA;
    public static final int CYAN = 0xFF38BDF8;
    public static final int PINK = 0xFFF472B6;

    private KhanhPalette() {
    }

    public static int category(Category category) {
        return switch (category) {
            case COMBAT -> 0xFFFF5C7A;
            case RENDER -> CYAN;
            case PLAYER -> GOOD;
            case MISC -> OKAY;
            case CLIENT -> VIOLET;
            case VISUAL -> PINK;
            case CONFIG -> 0xFF60A5FA;
        };
    }

    /** Green, amber or red depending on where a value sits against two thresholds. */
    public static int tier(double value, double good, double okay, boolean higherIsBetter) {
        if (higherIsBetter) return value >= good ? GOOD : value >= okay ? OKAY : BAD;
        return value <= good ? GOOD : value <= okay ? OKAY : BAD;
    }

    public static int mix(int from, int to, float t) {
        float k = Math.max(0.0F, Math.min(1.0F, t));
        int a = Math.round((from >>> 24) * (1 - k) + (to >>> 24) * k);
        int r = Math.round((from >> 16 & 255) * (1 - k) + (to >> 16 & 255) * k);
        int g = Math.round((from >> 8 & 255) * (1 - k) + (to >> 8 & 255) * k);
        int b = Math.round((from & 255) * (1 - k) + (to & 255) * k);
        return a << 24 | r << 16 | g << 8 | b;
    }
}
