package dev.khanh.feature;

/** Inventory Colour: multiplies container backgrounds by the chosen colour (or a slow rainbow). */
public final class InventoryTint {
    private static int depth;

    private InventoryTint() { }

    public static void begin() {
        depth++;
    }

    public static void end() {
        depth = Math.max(0, depth - 1);
    }

    public static int apply(int color) {
        if (depth == 0 || !KhanhFeatures.enabled("inventory_color")) return color;
        int tint = KhanhFeatures.bool("inventory_color", "rainbow", false)
            ? java.awt.Color.HSBtoRGB((System.currentTimeMillis() % 6000L) / 6000.0F, 0.55F, 1.0F)
            : KhanhFeatures.color("inventory_color", "color", 0xFF3A3A44);
        int r = (color >> 16 & 255) * (tint >> 16 & 255) / 255;
        int g = (color >> 8 & 255) * (tint >> 8 & 255) / 255;
        int b = (color & 255) * (tint & 255) / 255;
        return color & 0xFF000000 | r << 16 | g << 8 | b;
    }
}
