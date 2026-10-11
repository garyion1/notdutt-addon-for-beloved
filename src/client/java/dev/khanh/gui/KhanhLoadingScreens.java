package dev.khanh.gui;

import dev.khanh.KhanhClient;
import dev.khanh.feature.KhanhFeatures;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.joml.Matrix3x2fStack;

/** Khanh Client's look for every loading and waiting screen: black glass, logo, status and a moving bar. */
public final class KhanhLoadingScreens {
    private static final Identifier LOGO = Identifier.of(KhanhClient.MOD_ID, "textures/gui/khanh-logo.png");
    private static final long START = System.nanoTime();

    private static final String[] TIPS = {
        "Right Shift opens the Khanh Client menu",
        "Themes change the whole menu's colour in one click",
        "Scroll while in Freecam to change its speed",
        "Ctrl + F searches every module and setting",
        "Right-click a module to toggle it without opening it",
        "Middle-click a module to set its keybind",
        "Cosmetics shows your cape and wings in one place",
        "Totem Styles restyles every totem you see",
    };

    private KhanhLoadingScreens() { }

    /** One rotating tip under the bar, cross-fading every five seconds. */
    private static void tip(DrawContext c, int width, int height) {
        float t = seconds();
        int index = (int) (t / 5.0F) % TIPS.length;
        float phase = (t / 5.0F) % 1.0F;
        float alpha = Math.min(1.0F, Math.min(phase / 0.1F, (1.0F - phase) / 0.1F));
        String tip = "Tip: " + TIPS[index];
        int a = Math.round(140 * Math.max(0.0F, alpha));
        if (a > 4) text(c, tip, width / 2.0F - width(tip, 0.6F) / 2.0F, height - 28, a << 24 | 0xF2F4F8, 0.6F);
    }

    public static boolean enabled() {
        return true;   // Khanh always uses its own loading screens
    }

    /** Backdrop only: black liquid glass with drifting motes and the logo up top. */
    public static void backdrop(DrawContext c, int width, int height) {
        float t = seconds();
        try {
            LiquidGlass.backdrop(c, -2, -2, width + 4, height + 4, 1, 1.0F);
        } catch (RuntimeException notReady) {
            c.fill(0, 0, width, height, 0xFF050506);
        }
        for (int i = 0; i < 36; i++) {
            float seed = i * 12.9898F;
            float sx = fract((float) Math.sin(seed) * 43758.547F), sy = fract((float) Math.sin(seed * 1.7F) * 24634.634F);
            float x = (sx * width + (float) Math.sin(t * 0.3F + i) * 12.0F) % width;
            float y = ((sy * (height + 20) - t * (4.0F + 9.0F * fract(sx * 7.31F))) % (height + 20) + height + 20) % (height + 20) - 10;
            float twinkle = 0.35F + 0.65F * (0.5F + 0.5F * (float) Math.sin(t * (0.8F + sx) + i));
            c.fill(Math.round(x), Math.round(y), Math.round(x) + 1, Math.round(y) + 1, Math.round(80 * twinkle) << 24 | 0xFFFFFF);
        }
        int logo = 28;
        c.drawTexture(RenderPipelines.GUI_TEXTURED, LOGO, width / 2 - logo / 2, Math.max(16, height / 2 - 78), 0F, 0F, logo, logo,
            256, 256, 256, 256, 0xFFFFFFFF);
        String brand = "Khanh Client";
        text(c, brand, width / 2.0F - width(brand, 1.2F) / 2.0F, Math.max(16, height / 2 - 78) + logo + 6, 0xFFF2F4F8, 1.2F);
    }

    /** Full loading view: backdrop, a status line and a progress bar (indeterminate when progress < 0). */
    public static void full(DrawContext c, int width, int height, Text status, float progress) {
        backdrop(c, width, height);
        String line = status == null ? "Loading" : status.getString();
        float dots = seconds() * 2.5F % 3.0F;
        String shown = line.endsWith("...") || line.endsWith("…") ? line : line + ".".repeat(1 + (int) dots);
        text(c, shown, width / 2.0F - width(line, 0.85F) / 2.0F, height / 2.0F + 4, 0xD9F2F4F8, 0.85F);
        tip(c, width, height);
        int barW = Math.min(220, width - 60), barX = (width - barW) / 2, barY = Math.round(height / 2.0F + 22);
        SmoothGuiShapes.rect(c, barX, barY, barW, 4, 2, 0x22FFFFFF, false);
        if (progress >= 0.0F) {
            int fill = Math.max(4, Math.round(barW * Math.min(1.0F, progress)));
            SmoothGuiShapes.rect(c, barX, barY, fill, 4, 2, 0xF0F2F2F6, false);
            String pct = Math.round(Math.min(1.0F, progress) * 100) + "%";
            text(c, pct, width / 2.0F - width(pct, 0.6F) / 2.0F, barY + 10, 0x80F2F4F8, 0.6F);
        } else {
            float phase = seconds() * 0.9F % 1.0F;
            int seg = barW / 3;
            int x = barX + Math.round((barW + seg) * phase) - seg;
            int from = Math.max(barX, x), to = Math.min(barX + barW, x + seg);
            if (to > from) SmoothGuiShapes.rect(c, from, barY, to - from, 4, 2, 0xF0F2F2F6, false);
        }
    }

    private static float seconds() {
        return (System.nanoTime() - START) / 1_000_000_000.0F;
    }

    private static void text(DrawContext c, String value, float x, float y, int color, float scale) {
        Matrix3x2fStack m = c.getMatrices();
        m.pushMatrix();
        m.translate(x, y);
        m.scale(scale, scale);
        c.drawText(KhanhClickGuiScreen.textRenderer(), KhanhClickGuiScreen.fontText(value), 0, 0, color, false);
        m.popMatrix();
    }

    private static float width(String value, float scale) {
        return KhanhClickGuiScreen.textRenderer().getWidth(KhanhClickGuiScreen.fontText(value)) * scale;
    }

    private static float fract(float value) {
        return value - (float) Math.floor(value);
    }
}
