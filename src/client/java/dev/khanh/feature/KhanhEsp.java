package dev.khanh.feature;

import net.minecraft.client.render.DrawStyle;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.debug.gizmo.GizmoDrawing;
import net.minecraft.world.debug.gizmo.VisibilityConfigurable;

/**
 * Khanh's ESP look, shared by every box ESP: a soft fill, a thin outline and bright corner
 * brackets that frame the block or player. Each ESP's Style option picks Khanh (all three),
 * Corners (brackets only), Outline or Filled.
 */
public final class KhanhEsp {
    private KhanhEsp() { }

    public static void draw(String module, Box box, int colour, int fillAlpha, boolean throughWalls) {
        String style = KhanhFeatures.choice(module, "esp_style", "Khanh");
        int rgb = colour & 0xFFFFFF;
        float width = (float) KhanhFeatures.decimal(module, "esp_width", 2.0);
        switch (style) {
            case "Outline" -> walls(GizmoDrawing.box(box, DrawStyle.stroked(0xFF000000 | rgb, width), false), throughWalls);
            case "Filled" -> walls(GizmoDrawing.box(box, DrawStyle.filled(Math.max(40, fillAlpha) << 24 | rgb), false), throughWalls);
            case "Corners" -> corners(box, 0xFF000000 | rgb, width + 0.6F, throughWalls);
            default -> {
                walls(GizmoDrawing.box(box, DrawStyle.filledAndStroked(0x70000000 | rgb, Math.max(0.6F, width * 0.5F),
                    Math.min(255, fillAlpha) / 2 << 24 | rgb), false), throughWalls);
                corners(box, 0xFF000000 | rgb, width + 0.6F, throughWalls);
            }
        }
    }

    /** Three short lines at each of the box's eight corners. */
    public static void corners(Box box, int colour, float width, boolean throughWalls) {
        double lx = (box.maxX - box.minX) * 0.28, ly = (box.maxY - box.minY) * 0.22, lz = (box.maxZ - box.minZ) * 0.28;
        for (int i = 0; i < 8; i++) {
            double x = (i & 1) == 0 ? box.minX : box.maxX, y = (i & 2) == 0 ? box.minY : box.maxY, z = (i & 4) == 0 ? box.minZ : box.maxZ;
            double sx = (i & 1) == 0 ? lx : -lx, sy = (i & 2) == 0 ? ly : -ly, sz = (i & 4) == 0 ? lz : -lz;
            Vec3d corner = new Vec3d(x, y, z);
            walls(GizmoDrawing.line(corner, corner.add(sx, 0, 0), colour, width), throughWalls);
            walls(GizmoDrawing.line(corner, corner.add(0, sy, 0), colour, width), throughWalls);
            walls(GizmoDrawing.line(corner, corner.add(0, 0, sz), colour, width), throughWalls);
        }
    }

    private static void walls(VisibilityConfigurable gizmo, boolean throughWalls) {
        if (throughWalls) gizmo.ignoreOcclusion();
    }
}
