package dev.khanh.feature;

public final class VisualTheme {
    private VisualTheme() {
    }

    /** Khanh's GUI Colors preset: the one colour the whole client uses. */
    public static int preset() {
        return switch (KhanhFeatures.choice("client_colors", "preset", "Blue")) {
            case "Red" -> 0xFFFF3B4E;
            case "Orange" -> 0xFFFF8A1F;
            case "Yellow" -> 0xFFFFD43B;
            case "Purple" -> 0xFFA855F7;
            case "Green" -> 0xFF22C55E;
            case "Lime" -> 0xFFA3E635;
            case "Rainbow" -> 0xFF000000 | (java.awt.Color.HSBtoRGB((System.currentTimeMillis() % 6000L) / 6000.0F, 0.72F, 1.0F) & 0xFFFFFF);
            default -> 0xFF3B9BFF;
        };
    }

    public static int accent() {
        if (KhanhFeatures.choice("visuals", "accent", "Theme").equals("Theme")) return preset();
        int base = switch (KhanhFeatures.choice("visuals", "accent", "Theme")) {
            case "Theme" -> themeAccent();
            case "Custom" -> rgb(
                KhanhFeatures.number("visuals", "custom_red", 157),
                KhanhFeatures.number("visuals", "custom_green", 78),
                KhanhFeatures.number("visuals", "custom_blue", 255));
            case "Rose" -> 0xFFFF477E;
            case "Violet" -> 0xFFB76CFF;
            case "Cyan" -> 0xFF42D9FF;
            case "Crimson" -> 0xFFFF344F;
            case "White" -> 0xFFF7F4F6;
            case "Khanh Client Purple", "Khanh Client Pink", "Khanh Client Blue" -> 0xFF9D4EFF;
            default -> 0xFF9D4EFF;
        };
        if (!KhanhFeatures.bool("visuals", "animated_accent", false)) {
            return base;
        }
        float strength = (float) KhanhFeatures.decimal("visuals", "accent_motion", 0.20);
        float wave = (float) ((Math.sin(System.nanoTime() / 1_250_000_000.0) + 1.0) * 0.5);
        return mix(base, 0xFFFFFFFF, wave * strength);
    }

    public static int guiAccent() {
        return KhanhFeatures.enabled("client_colors") && KhanhFeatures.bool("client_colors", "gui_override", false)
            ? KhanhFeatures.color("client_colors", "gui_color", 0xFF9D4EFF) : accent();
    }

    public static int hudAccent() {
        return KhanhFeatures.enabled("client_colors") && KhanhFeatures.bool("client_colors", "hud_override", false)
            ? KhanhFeatures.color("client_colors", "hud_color", 0xFF9D4EFF) : accent();
    }

    public static int hudSoftAccent() {
        return mix(hudAccent(), 0xFFF7F8FA, 0.42F);
    }

    public static int guiSoftAccent() {
        return mix(guiAccent(), 0xFFF7F8FA, 0.42F);
    }

    public static int guiGlass(int original) {
        int alpha = original >>> 24;
        int red = original >> 16 & 0xFF;
        int green = original >> 8 & 0xFF;
        int blue = original & 0xFF;
        int luminance = (red * 3 + green * 6 + blue) / 10;
        int palette = luminance >= 42 ? themeRaised() : themeBase();
        return withAlpha(mix(palette, guiAccent(), luminance >= 42 ? 0.09F : 0.05F), alpha);
    }

    private static int themeAccent() {
        return switch (theme()) {
            case "Khanh Client Black" -> 0xFFB7BCC4;
            case "Black Glass" -> 0xFFE5E9F0;
            case "Khanh Client Violet" -> 0xFF9D4EFF;
            case "Black Ice" -> 0xFFCBB8FF;
            case "Ultraviolet" -> 0xFFC13CFF;
            case "Obsidian" -> 0xFFE9E4F4;
            case "Arctic" -> 0xFF76D9FF;
            case "Rose Quartz" -> 0xFFFF6FA8;
            case "Emerald Glass" -> 0xFF54E6A8;
            case "Crimson Glass" -> 0xFFFF596D;
            case "Sunset" -> 0xFFFF825D;
            case "Sakura" -> 0xFFFFA4B8;
            case "Cyberpunk" -> 0xFF34DCE8;
            case "Gold" -> 0xFFFFD268;
            case "Ocean" -> 0xFF60D8D0;
            case "Midnight" -> 0xFF9299E8;
            case "Toxic" -> 0xFFAEED52;
            case "Mocha" -> 0xFFD4A47C;
            case "Sapphire" -> 0xFF6999F0;
            case "Lavender" -> 0xFFC99BEE;
            default -> 0xFF9D4EFF;
        };
    }

    private static int themeBase() {
        return switch (theme()) {
            case "Khanh Client Black" -> 0xFF010204;
            case "Black Glass" -> 0xFF020304;
            case "Khanh Client Violet" -> 0xFF08060D;
            case "Black Ice" -> 0xFF07080C;
            case "Ultraviolet" -> 0xFF10051A;
            case "Obsidian" -> 0xFF0B0B0F;
            case "Arctic" -> 0xFF11242D;
            case "Rose Quartz" -> 0xFF27151E;
            case "Emerald Glass" -> 0xFF10251F;
            case "Crimson Glass" -> 0xFF281318;
            default -> 0xFF08060D;
        };
    }

    private static int themeRaised() {
        return switch (theme()) {
            case "Khanh Client Black" -> 0xFF0B0F15;
            case "Black Glass" -> 0xFF0B0D10;
            case "Khanh Client Violet" -> 0xFF21172F;
            case "Black Ice" -> 0xFF191A21;
            case "Ultraviolet" -> 0xFF35124B;
            case "Obsidian" -> 0xFF232329;
            case "Arctic" -> 0xFF245061;
            case "Rose Quartz" -> 0xFF513040;
            case "Emerald Glass" -> 0xFF214A3D;
            case "Crimson Glass" -> 0xFF512630;
            default -> mix(0xFF17191E, themeAccent(), 0.16F);
        };
    }

    private static String theme() {
        return KhanhFeatures.choice("visuals", "gui_theme", "Khanh Client Black");
    }

    public static int rgb(int red, int green, int blue) {
        return 0xFF000000
            | Math.max(0, Math.min(255, red)) << 16
            | Math.max(0, Math.min(255, green)) << 8
            | Math.max(0, Math.min(255, blue));
    }

    public static int softAccent() {
        return mix(accent(), 0xFFF7F8FA, 0.42F);
    }

    public static int glass() {
        int alpha = (int) Math.round(KhanhFeatures.decimal("visuals", "glass_opacity", 0.86) * 255.0);
        float tint = (float) KhanhFeatures.decimal("visuals", "glass_tint", 0.20);
        return withAlpha(mix(0xFF020304, guiAccent(), Math.max(0.0F, Math.min(0.40F, tint)) * 0.08F), alpha);
    }

    public static int glassRaised() {
        int alpha = Math.min(248, ((glass() >>> 24) & 0xFF) + 18);
        float tint = (float) KhanhFeatures.decimal("visuals", "glass_tint", 0.20);
        return withAlpha(mix(0xFF0B0D10, guiAccent(), Math.max(0.0F, Math.min(0.40F, tint)) * 0.10F), alpha);
    }

    public static int border() {
        return withAlpha(mix(0xFF4F435F, guiAccent(), 0.44F), 190);
    }

    public static int surface() {
        return 0xFF0B0910;
    }

    public static int surfaceRaised() {
        return 0xFF18121F;
    }

    public static int text() {
        return 0xFFF6F0FF;
    }

    public static int mutedText() {
        return 0xFFAA9CB8;
    }

    public static int chrome() {
        return withAlpha(mix(0xFF08060C, accent(), 0.10F), 242);
    }

    public static int chromeRaised() {
        return withAlpha(mix(0xFF17111F, accent(), 0.18F), 246);
    }

    public static int chromeText() {
        return 0xFFF9F5FF;
    }

    public static int chromeMuted() {
        return 0xFFB8A9C8;
    }

    public static int withAlpha(int color, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (color & 0x00FFFFFF);
    }

    private static int mix(int first, int second, float amount) {
        float t = Math.max(0.0F, Math.min(1.0F, amount));
        int red = Math.round(((first >> 16) & 0xFF) * (1.0F - t) + ((second >> 16) & 0xFF) * t);
        int green = Math.round(((first >> 8) & 0xFF) * (1.0F - t) + ((second >> 8) & 0xFF) * t);
        int blue = Math.round((first & 0xFF) * (1.0F - t) + (second & 0xFF) * t);
        return 0xFF000000 | red << 16 | green << 8 | blue;
    }
}
