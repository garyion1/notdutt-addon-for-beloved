package dev.khanh.gui;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.khanh.KhanhClient;
import dev.khanh.mixin.DrawContextGlassInvoker;
import net.minecraft.client.gl.UniformType;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;

/** The liquid-glass material: panels that frost the game behind them (with a faint
 * drifting colour glow), a 6% white film, a top-lit 1px border, an inset
 * highlight, grain and a soft drop shadow. Every shape is one antialiased quad; size, radius and a per-shape
 * parameter travel in the UVs exactly like {@link SmoothGuiShapes}. */
public final class LiquidGlass {
    static final int MODE_GLASS = 1;
    static final int MODE_ACCENT = 2;
    static final int MODE_KNOB = 3;
    static final int MODE_TRACK = 4;
    private static final Identifier FILL = Identifier.of(KhanhClient.MOD_ID, "textures/gui/rounded-fill.png");
    private static final RenderPipeline BACKDROP = pipeline("liquid_backdrop", true);
    private static final RenderPipeline PANEL = pipeline("liquid_panel", false);
    private static final RenderPipeline SHADOW = pipeline("liquid_shadow", false);
    private static final long EPOCH = System.nanoTime();
    private static final long LOOP_NANOS = 600_000_000_000L;
    private static final int SHADOW_BLUR = 15;
    private static final int SHADOW_DROP = 14;
    private static int state;

    private LiquidGlass() {
    }

    private static RenderPipeline pipeline(String name, boolean globals) {
        RenderPipeline.Builder builder = RenderPipeline.builder()
            .withLocation(Identifier.of(KhanhClient.MOD_ID, "pipeline/" + name))
            .withVertexShader(Identifier.of(KhanhClient.MOD_ID, "core/liquid_shape"))
            .withFragmentShader(Identifier.of(KhanhClient.MOD_ID, "core/" + name))
            .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
            .withUniform("Projection", UniformType.UNIFORM_BUFFER);
        if (globals) builder = builder.withUniform("Globals", UniformType.UNIFORM_BUFFER);
        return builder.withSampler("Sampler0").withBlend(BlendFunction.TRANSLUCENT)
            .withDepthWrite(false).withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST).withCull(false)
            .withVertexFormat(VertexFormats.POSITION_TEXTURE_COLOR, VertexFormat.DrawMode.QUADS).build();
    }

    /** Compiles once; any driver failure falls back to flat rounded panels for the session. */
    public static boolean ready() {
        if (state == 0) {
            try {
                for (RenderPipeline pipeline : new RenderPipeline[] {BACKDROP, PANEL, SHADOW}) {
                    if (!RenderSystem.getDevice().precompilePipeline(pipeline).isValid())
                        throw new IllegalStateException("Liquid glass shader unavailable");
                }
                state = 1;
            } catch (RuntimeException error) {
                state = -1;
                KhanhClient.LOGGER.warn("Khanh Client liquid glass unavailable; using flat panels", error);
            }
        }
        return state == 1;
    }

    public static int alpha(float opacity) {
        return Math.max(0, Math.min(255, Math.round(opacity * 255.0F)));
    }

    private static int radius(int r, int w, int h) {
        return Math.max(1, Math.min(r, Math.min(w, h) / 2));
    }

    private static void quad(DrawContext c, RenderPipeline pipeline, int x, int y, int w, int h,
                             int r, float param, int color) {
        if (w <= 1 || h <= 1) return;
        c.drawTexture(pipeline, FILL, x, y, -w - radius(r, w, h) / 1024.0F, -h - param / 1024.0F,
            w, h, w * 2 + 1, h * 2 + 1, 1, 1, color);
    }

    /** 24-bit phase of a ten minute loop, packed into RGB for the orb drift. */
    private static int timePhase() {
        long t = Math.floorMod(System.nanoTime() - EPOCH, LOOP_NANOS);
        return (int) Math.round(t / (double) LOOP_NANOS * 16_777_215.0) & 0xFFFFFF;
    }

    /** Frosted panel body: the game behind the panel, heavily blurred and pulled
     * towards black. Opaque, so a dropdown hides whatever card it
     * covers exactly like heavy frosted glass would. Without a world it falls back
     * to translucent ink. */
    /**
     * Client Colours "Colour Whole GUI": blends a surface colour toward the GUI colour wheel by the
     * chosen strength (scaled by {@code weight}); otherwise returns it unchanged.
     */
    static int tinted(int rgb, float weight) {
        if (!dev.khanh.feature.KhanhFeatures.enabled("client_colors")
            || !dev.khanh.feature.KhanhFeatures.bool("client_colors", "gui_override", false)) return rgb & 0xFFFFFF;
        int wheel = dev.khanh.feature.KhanhFeatures.color("client_colors", "gui_color", 0xFF9D4EFF);
        float k = Math.max(0.0F, Math.min(1.0F, (float) dev.khanh.feature.KhanhFeatures.decimal("client_colors", "tint_strength", 0.45) * weight));
        int r = Math.round(((rgb >> 16) & 255) + (((wheel >> 16) & 255) - ((rgb >> 16) & 255)) * k);
        int g = Math.round(((rgb >> 8) & 255) + (((wheel >> 8) & 255) - ((rgb >> 8) & 255)) * k);
        int b = Math.round((rgb & 255) + ((wheel & 255) - (rgb & 255)) * k);
        return r << 16 | g << 8 | b;
    }

    public static void frost(DrawContext c, int x, int y, int w, int h, int r, float opacity) {
        int a = alpha(opacity);
        if (a == 0 || w <= 1 || h <= 1) return;
        if (!ready()) {
            SmoothGuiShapes.rect(c, x, y, w, h, r, alpha(0.72F * opacity) << 24 | tinted(0x050506, 0.6F), false);
            return;
        }
        // GUI Blur off (the default): Khanh Client's menus use clean dark glass and the world behind
        // stays sharp. The HUD (drawn with no screen open) keeps its frosted glass.
        if (net.minecraft.client.MinecraftClient.getInstance().currentScreen != null
            && !dev.khanh.feature.KhanhFeatures.bool("visuals", "blur", false)) {
            SmoothGuiShapes.rect(c, x, y, w, h, r, alpha(0.92F * opacity) << 24 | tinted(0x0A0A0C, 0.6F), false);
            return;
        }
        GpuTextureView scene = GlassGuiRenderer.sceneView();
        if (scene == null) {
            quad(c, BACKDROP, x, y, w, h, r, 1.0F, alpha(0.78F * opacity) << 24 | timePhase());
            return;
        }
        float u1 = -w - radius(r, w, h) / 1024.0F;
        float v1 = -h - 3.0F / 1024.0F;
        ((DrawContextGlassInvoker) (Object) c).khanh$glassQuad(BACKDROP, scene,
            RenderSystem.getSamplerCache().get(FilterMode.LINEAR), x, y, x + w, y + h,
            u1, u1 + w * 2 + 1, v1, v1 + h * 2 + 1, a << 24 | timePhase());
    }

    /** Opaque ink backdrop with the three slow colour orbs: the main menu's background. */
    public static void backdrop(DrawContext c, int x, int y, int w, int h, int r, float opacity) {
        int a = alpha(opacity);
        if (a == 0 || w <= 1 || h <= 1) return;
        if (!ready()) {
            SmoothGuiShapes.rect(c, x, y, w, h, Math.max(1, r), a << 24 | 0x050506, false);
            return;
        }
        quad(c, BACKDROP, x, y, w, h, Math.max(1, r), 1.0F, a << 24 | timePhase());
    }

    /** 0 20px 40px rgba(0,0,0,.35), in console units. */
    public static void shadow(DrawContext c, int x, int y, int w, int h, int r, float opacity) {
        int a = alpha(0.35F * opacity);
        if (a == 0 || !ready()) return;
        quad(c, SHADOW, x - SHADOW_BLUR, y - SHADOW_BLUR + SHADOW_DROP, w + SHADOW_BLUR * 2,
            h + SHADOW_BLUR * 2, r, SHADOW_BLUR, a << 24);
    }

    public static void glass(DrawContext c, int x, int y, int w, int h, int r, int tint, float opacity) {
        int a = alpha(opacity);
        if (a == 0) return;
        // Plain white glass picks up the GUI colour too when the whole GUI is coloured.
        if ((tint & 0xFFFFFF) == 0xFFFFFF && net.minecraft.client.MinecraftClient.getInstance().currentScreen != null) tint = tinted(0xFFFFFF, 0.8F);
        if (!ready()) {
            SmoothGuiShapes.rect(c, x, y, w, h, r, alpha(0.10F * opacity) << 24 | (tint & 0xFFFFFF), false);
            SmoothGuiShapes.rect(c, x, y, w, h, r, alpha(0.22F * opacity) << 24 | 0xFFFFFF, true);
            return;
        }
        quad(c, PANEL, x, y, w, h, r, MODE_GLASS, a << 24 | (tint & 0xFFFFFF));
    }

    public static void accent(DrawContext c, int x, int y, int w, int h, int r, int color, float opacity) {
        int a = alpha(opacity);
        if (a == 0) return;
        if (!ready()) {
            SmoothGuiShapes.rect(c, x, y, w, h, r, a << 24 | (color & 0xFFFFFF), false);
            return;
        }
        quad(c, PANEL, x, y, w, h, r, MODE_ACCENT, a << 24 | (color & 0xFFFFFF));
    }

    public static void knob(DrawContext c, int x, int y, int size, float opacity) {
        knob(c, x, y, size, 0xFFFFFF, opacity);
    }

    public static void knob(DrawContext c, int x, int y, int size, int color, float opacity) {
        int a = alpha(opacity);
        if (a == 0) return;
        if (!ready()) {
            SmoothGuiShapes.rect(c, x, y, size, size, size / 2, a << 24 | (color & 0xFFFFFF), false);
            return;
        }
        quad(c, PANEL, x, y, size, size, size / 2, MODE_KNOB, a << 24 | (color & 0xFFFFFF));
    }

    public static void track(DrawContext c, int x, int y, int w, int h, int r, float opacity) {
        int a = alpha(opacity);
        if (a == 0) return;
        if (!ready()) {
            SmoothGuiShapes.rect(c, x, y, w, h, r, alpha(0.08F * opacity) << 24 | 0xFFFFFF, false);
            return;
        }
        quad(c, PANEL, x, y, w, h, r, MODE_TRACK, a << 24 | 0xFFFFFF);
    }

    /** Glass shadow, film and border in one call: the full card recipe. */
    public static void card(DrawContext c, int x, int y, int w, int h, int r, float opacity) {
        shadow(c, x, y, w, h, r, opacity);
        frost(c, x, y, w, h, r, opacity);
        glass(c, x, y, w, h, r, 0xFFFFFF, opacity);
    }
}
