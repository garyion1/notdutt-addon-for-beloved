package dev.khanh.gui;

import dev.khanh.feature.KhanhSounds;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.joml.Matrix3x2fStack;

/** Shared look for Khanh Client's full-screen menus: springy glass buttons, Zero type and fades. */
abstract class KhanhGlassScreen extends Screen {
    record Button(String id, String label, String hint, HudSurfaceRenderer.HudIcon icon, int x, int y, int w, int h,
                  boolean big, Runnable action) {
        boolean contains(double px, double py) { return px >= x && px < x + w && py >= y && py < y + h; }
    }

    static final int FG = 0xFFF2F4F8, SOFT = 0xD9F2F4F8, DIM = 0x94F2F4F8, FAINT = 0x57F2F4F8;
    static final int ACCENT = 0xFFF2F2F6;

    final List<Button> buttons = new ArrayList<>();
    private final Map<String, Spring> springs = new HashMap<>();
    long openedAt;
    private String pressed;

    KhanhGlassScreen(String title) {
        super(Text.literal(title));
    }

    @Override
    protected void init() {
        if (openedAt == 0L) openedAt = System.nanoTime();
        buttons.clear();
    }

    float seconds() {
        return (System.nanoTime() - openedAt) / 1_000_000_000.0F;
    }

    Spring spring(String id, float[] config, float initial) {
        return springs.computeIfAbsent(id, key -> new Spring(config, initial));
    }

    /** One glass button. Big buttons carry an icon tile and a hint line; small ones are pills. */
    void drawButton(DrawContext c, Button b, int index, int mouseX, int mouseY, boolean enabled) {
        Spring in = spring("in:" + b.id(), Spring.PANEL, 0.0F);
        if (seconds() > 0.08F + index * 0.035F) in.to(1.0F);
        float q = in.value();
        float a = clamp(q) * (enabled ? 1.0F : 0.4F);
        if (a <= 0.01F) return;
        boolean hot = enabled && b.contains(mouseX, mouseY);
        float scale = spring("press:" + b.id(), Spring.BOUNCE, 1.0F)
            .to(hot && b.id().equals(pressed) ? 0.95F : hot ? 1.035F : 1.0F).value();
        float lift = spring("lift:" + b.id(), Spring.BOUNCE, 0.0F).to(hot ? -1.5F : 0.0F).value();
        float glow = clamp(spring("glow:" + b.id(), Spring.SMOOTH, 0.0F).to(hot ? 1.0F : 0.0F).value());
        Matrix3x2fStack m = c.getMatrices();
        m.pushMatrix();
        m.translate(0.0F, (1.0F - q) * 12.0F + lift);
        m.translate(b.x() + b.w() / 2.0F, b.y() + b.h() / 2.0F);
        m.scale(scale, scale);
        m.translate(-(b.x() + b.w() / 2.0F), -(b.y() + b.h() / 2.0F));
        int radius = b.big() ? 14 : Math.min(12, b.h() / 2);
        LiquidGlass.shadow(c, b.x(), b.y(), b.w(), b.h(), radius, a * (0.45F + 0.4F * glow));
        LiquidGlass.glass(c, b.x(), b.y(), b.w(), b.h(), radius, hot ? ACCENT : 0xFFFFFF, a);
        if (b.big()) {
            LiquidGlass.glass(c, b.x() + 10, b.y() + (b.h() - 26) / 2, 26, 26, 9, hot ? ACCENT : 0xFFFFFF, a);
            HudSurfaceRenderer.icon(c, b.icon(), b.x() + 16, b.y() + (b.h() - 14) / 2, 14, alpha(a) << 24 | 0xFFFFFF);
            boolean hint = b.hint() != null && !b.hint().isEmpty();
            text(c, b.label(), b.x() + 44, b.y() + b.h() / 2.0F - (hint ? 8.0F : 3.5F), FG, 1.0F, a);
            if (hint) text(c, b.hint(), b.x() + 44, b.y() + b.h() / 2.0F + 4.0F, FAINT, 0.62F, a * (0.75F + 0.25F * glow));
            int underline = Math.round((b.w() - 24) * glow);
            // Only once it has length: a zero-width rounded rect still paints a small stub.
            if (underline >= 3) SmoothGuiShapes.rect(c, b.x() + 12, b.y() + b.h() - 7, underline, 2, 1,
                alpha(a * glow) << 24 | 0xFFFFFF, false);
        } else {
            int iconSize = 11;
            float textW = width(b.label(), 0.82F);
            float content = (b.icon() == null ? 0 : iconSize + 6) + textW;
            float start = b.x() + (b.w() - content) / 2.0F;
            if (b.icon() != null) {
                HudSurfaceRenderer.icon(c, b.icon(), Math.round(start), b.y() + (b.h() - iconSize) / 2, iconSize, alpha(a) << 24 | 0xFFFFFF);
                start += iconSize + 6;
            }
            text(c, b.label(), start, b.y() + (b.h() - 7) / 2.0F, FG, 0.82F, a);
        }
        m.popMatrix();
    }

    boolean enabled(Button b) {
        return true;
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (click.button() == 0) {
            for (Button b : List.copyOf(buttons)) {
                if (b.contains(click.x(), click.y()) && enabled(b)) {
                    pressed = b.id();
                    spring("press:" + b.id(), Spring.BOUNCE, 1.0F).kick(0.9F);
                    KhanhSounds.playInterface(client, 1.15F);
                    b.action().run();
                    return true;
                }
            }
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseReleased(Click click) {
        pressed = null;
        return super.mouseReleased(click);
    }

    void text(DrawContext c, String value, float x, float y, int color, float scale, float opacity) {
        int a = Math.round((color >>> 24) * clamp(opacity));
        if (a < 5 || value == null || value.isEmpty()) return;
        Matrix3x2fStack m = c.getMatrices();
        m.pushMatrix();
        m.translate(x, y);
        m.scale(scale, scale);
        c.drawText(KhanhClickGuiScreen.textRenderer(), KhanhClickGuiScreen.fontText(value), 0, 0, a << 24 | (color & 0xFFFFFF), false);
        m.popMatrix();
    }

    static float width(String value, float scale) {
        return KhanhClickGuiScreen.textRenderer().getWidth(KhanhClickGuiScreen.fontText(value)) * scale;
    }

    String trim(String value, float max, float scale) {
        if (value == null) return "";
        if (width(value, scale) <= max) return value;
        String cut = value;
        while (!cut.isEmpty() && width(cut + "…", scale) > max) cut = cut.substring(0, cut.length() - 1);
        return cut + "…";
    }

    static int alpha(float value) {
        return Math.max(0, Math.min(255, Math.round(value * 255.0F)));
    }

    static float clamp(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }
}
