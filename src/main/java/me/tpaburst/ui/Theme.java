package me.tpaburst.ui;

import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.utils.render.color.Color;
import net.minecraft.client.gui.DrawContext;

/**
 * Shared palette and drawing helpers so the history screen, toasts and HUD elements
 * all use the same look: dark panels, a 1px border, softened corners and a red accent.
 */
public final class Theme {
    // ARGB colours for vanilla DrawContext rendering.
    public static final int BACKDROP = 0x88000000;
    public static final int PANEL = 0xF0101116;
    public static final int PANEL_HEADER = 0xF0171920;
    public static final int SURFACE = 0xFF1C1F27;
    public static final int SURFACE_HOVER = 0xFF262A35;
    public static final int ROW_ALT = 0x0FFFFFFF;
    public static final int ROW_HOVER = 0x1FFFFFFF;
    public static final int BORDER = 0xFF2A2E39;
    public static final int ACCENT = 0xFFE04545;
    public static final int ACCENT_DIM = 0x55E04545;
    public static final int TEXT = 0xFFFFFFFF;
    public static final int TEXT_SECONDARY = 0xFFC9CDD6;
    public static final int TEXT_MUTED = 0xFF7D8494;
    public static final int GOOD = 0xFF4ADE80;
    public static final int BAD = 0xFF6B7280;

    private Theme() {
    }

    public static int withAlpha(int argb, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (argb & 0x00FFFFFF);
    }

    public static int rgb(int rgb) {
        return 0xFF000000 | (rgb & 0x00FFFFFF);
    }

    /** Rectangle with the four corner pixels cut out, which reads as a slightly rounded box. */
    public static void softRect(DrawContext ctx, int x, int y, int w, int h, int color) {
        if (w <= 2 || h <= 2) {
            ctx.fill(x, y, x + w, y + h, color);
            return;
        }
        ctx.fill(x + 1, y, x + w - 1, y + 1, color);
        ctx.fill(x, y + 1, x + w, y + h - 1, color);
        ctx.fill(x + 1, y + h - 1, x + w - 1, y + h, color);
    }

    /** Filled panel with a 1px border and softened corners. */
    public static void panel(DrawContext ctx, int x, int y, int w, int h, int fill, int border) {
        softRect(ctx, x, y, w, h, border);
        softRect(ctx, x + 1, y + 1, w - 2, h - 2, fill);
    }

    public static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    // Meteor HUD helpers (HudRenderer works in doubles and Meteor colours).

    public static Color color(int argb) {
        return new Color((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF, (argb >>> 24) & 0xFF);
    }

    public static void softRect(HudRenderer r, double x, double y, double w, double h, double px, Color color) {
        if (w <= px * 2 || h <= px * 2) {
            r.quad(x, y, w, h, color);
            return;
        }
        r.quad(x + px, y, w - px * 2, px, color);
        r.quad(x, y + px, w, h - px * 2, color);
        r.quad(x + px, y + h - px, w - px * 2, px, color);
    }

    public static void panel(HudRenderer r, double x, double y, double w, double h, double px, Color fill, Color border) {
        softRect(r, x, y, w, h, px, border);
        softRect(r, x + px, y + px, w - px * 2, h - px * 2, px, fill);
    }
}
