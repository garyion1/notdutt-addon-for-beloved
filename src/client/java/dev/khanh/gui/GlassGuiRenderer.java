package dev.khanh.gui;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.khanh.KhanhClient;
import dev.khanh.mixin.DrawContextGlassInvoker;
import java.util.OptionalInt;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.gl.UniformType;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import dev.khanh.feature.KhanhFeatures;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;

/** Original refractive glass: one quarter-resolution scene shared by every panel.
 * No CPU framebuffer reads, per-panel blur passes, animated noise or native library.
 */
public final class GlassGuiRenderer {
    private static final RenderPipeline SOFT = background("glass_soft", "gui_glass_blur");
    private static final RenderPipeline CLEAR = background("glass_clear", "gui_glass_copy");
    private static final RenderPipeline GLASS = RenderPipeline.builder()
        .withLocation(Identifier.of("khanh", "pipeline/gui_glass"))
        .withVertexShader(Identifier.of("khanh", "core/gui_shape"))
        .withFragmentShader(Identifier.of("khanh", "core/gui_glass"))
        .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
        .withUniform("Projection", UniformType.UNIFORM_BUFFER).withUniform("Globals", UniformType.UNIFORM_BUFFER)
        .withSampler("Sampler0").withBlend(BlendFunction.TRANSLUCENT)
        .withDepthWrite(false).withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST).withCull(false)
        .withVertexFormat(VertexFormats.POSITION_TEXTURE_COLOR, VertexFormat.DrawMode.QUADS).build();
    private static Framebuffer scene;
    private static boolean ready, registered, failed;
    // Shader creation is synchronous on the render thread. Spread the cold
    // compile across frames so opening the GUI never waits for all effects at once.
    private static int compiledStages;
    private static boolean hudCompiled, hudFailed;
    private static final RenderPipeline HUD_GLASS = RenderPipeline.builder()
        .withLocation(Identifier.of("khanh", "pipeline/hud_liquid_glass"))
        .withVertexShader(Identifier.of("khanh", "core/gui_shape"))
        .withFragmentShader(Identifier.of("khanh", "core/hud_liquid_glass"))
        .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
        .withUniform("Projection", UniformType.UNIFORM_BUFFER).withUniform("Globals", UniformType.UNIFORM_BUFFER)
        .withSampler("Sampler0").withBlend(BlendFunction.TRANSLUCENT)
        .withDepthWrite(false).withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST).withCull(false)
        .withVertexFormat(VertexFormats.POSITION_TEXTURE_COLOR, VertexFormat.DrawMode.QUADS).build();

    private GlassGuiRenderer() { }

    private static RenderPipeline background(String name, String shader) {
        return RenderPipeline.builder().withLocation(Identifier.of("khanh", "pipeline/" + name))
            .withVertexShader("core/screenquad").withFragmentShader(Identifier.of("khanh", "core/" + shader))
            .withSampler("SceneSampler").withoutBlend().withCull(false).withDepthWrite(false)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withVertexFormat(VertexFormats.EMPTY, VertexFormat.DrawMode.TRIANGLES).build();
    }

    /** Compiles the glass shaders ahead of time (on the title screen) so the first menu
     * open or HUD frame never stalls on shader compilation. */
    public static void warmUp() {
        if (failed || compiledStages >= 3) return;
        try {
            for (RenderPipeline pipeline : new RenderPipeline[] {GLASS, SOFT, CLEAR}) {
                if (!RenderSystem.getDevice().precompilePipeline(pipeline).isValid())
                    throw new IllegalStateException("Glass shader unavailable");
            }
            compiledStages = 3;
            if (!hudCompiled && !hudFailed) {
                if (RenderSystem.getDevice().precompilePipeline(HUD_GLASS).isValid()) hudCompiled = true;
                else hudFailed = true;
            }
        } catch (RuntimeException error) {
            failed = true;
            KhanhClient.LOGGER.warn("Khanh Client optional glass unavailable; smooth translucent panels retained", error);
        }
    }

    public static void begin(boolean enabled, boolean soften) {
        ready = false;
        if (!registered) {
            registered = true;
            ClientLifecycleEvents.CLIENT_STOPPING.register(client -> close());
            // Do not delete a texture from Screen.close(): its quads can still be
            // queued in that closing frame. The next tick is after their draw.
            ClientTickEvents.END_CLIENT_TICK.register(client -> {
                if (!(client.currentScreen instanceof KhanhClickGui) && !hudGlassEnabled(client)) close();
            });
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (!enabled || failed || client.world == null) { close(); return; }
        try {
            if (compiledStages < 3) {
                RenderPipeline next = switch (compiledStages) {
                    case 0 -> GLASS;
                    case 1 -> SOFT;
                    default -> CLEAR;
                };
                if (!RenderSystem.getDevice().precompilePipeline(next).isValid())
                    throw new IllegalStateException("Glass shader unavailable");
                compiledStages++;
                return;
            }
            if (!hudCompiled && !hudFailed) {
                try {
                    if (!RenderSystem.getDevice().precompilePipeline(HUD_GLASS).isValid())
                        throw new IllegalStateException("Liquid glass shader unavailable");
                    hudCompiled = true;
                } catch (RuntimeException error) {
                    hudFailed = true;
                    KhanhClient.LOGGER.warn("Khanh Client optional liquid surface unavailable", error);
                }
            }
            Framebuffer main = client.getFramebuffer();
            int w = Math.max(1, (main.textureWidth + 3) / 4), h = Math.max(1, (main.textureHeight + 3) / 4);
            if (scene == null || scene.textureWidth != w || scene.textureHeight != h) {
                close(); scene = new SimpleFramebuffer("Khanh Client glass background", w, h, false);
            }
            try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                    () -> "Khanh Client shared glass background", scene.getColorAttachmentView(), OptionalInt.empty())) {
                pass.setPipeline(soften ? SOFT : CLEAR);
                pass.bindTexture("SceneSampler", main.getColorAttachmentView(), RenderSystem.getSamplerCache().get(FilterMode.LINEAR));
                pass.draw(0, 3);
            }
            ready = true;
        } catch (RuntimeException error) {
            failed = true; close();
            KhanhClient.LOGGER.warn("Khanh Client optional glass unavailable; smooth translucent panels retained", error);
        }
    }

    private static boolean hudGlassEnabled(MinecraftClient client) {
        return client.player != null && client.world != null && !client.options.hudHidden
            && (client.currentScreen == null || KhanhHudEditorScreen.editing(client))
            && KhanhFeatures.enabled("hud");
    }

    public static void beginHud() {
        boolean enabled = hudGlassEnabled(MinecraftClient.getInstance());
        if (enabled && !hudCompiled && !hudFailed) {
            try {
                if (!RenderSystem.getDevice().precompilePipeline(HUD_GLASS).isValid())
                    throw new IllegalStateException("HUD glass shader unavailable");
                hudCompiled = true;
                // Keep this frame light; the backdrop pipelines warm on later frames.
                return;
            } catch (RuntimeException error) {
                hudFailed = true;
                KhanhClient.LOGGER.warn("Khanh Client optional HUD glass unavailable; smooth translucent panels retained", error);
            }
        }
        begin(enabled && !hudFailed, true);
    }

    public static boolean hudSurface(DrawContext context, int x, int y, int w, int h, int radius, int color, boolean border) {
        if (!ready || !hudCompiled || hudFailed || scene == null || w <= 0 || h <= 0 || color >>> 24 == 0) return false;
        int r = Math.max(0, Math.min(radius, Math.min(w, h) / 2));
        float u1 = -w - r / 1024F, v1 = -h - (border ? .25F : .125F) / 1024F;
        ((DrawContextGlassInvoker)(Object)context).khanh$glassQuad(HUD_GLASS, scene.getColorAttachmentView(),
            RenderSystem.getSamplerCache().get(FilterMode.LINEAR), x, y, x + w, y + h,
            u1, u1 + w * 2 + 1, v1, v1 + h * 2 + 1, color);
        return true;
    }

    /** Uses the same restrained refraction as the HUD without enabling a second scene blur. */
    public static boolean liquidSurface(DrawContext context, int x, int y, int w, int h, int radius, int color) {
        return hudSurface(context, x, y, w, h, radius, color, true);
    }

    public static boolean surface(DrawContext context, int x, int y, int w, int h, int radius, int color) {
        if (!ready || scene == null || w <= 0 || h <= 0 || color >>> 24 == 0) return false;
        int r = Math.max(0, Math.min(radius, Math.min(w, h) / 2));
        float u1 = -w - r / 1024F, v1 = -h - .125F / 1024F;
        ((DrawContextGlassInvoker)(Object)context).khanh$glassQuad(GLASS, scene.getColorAttachmentView(),
            RenderSystem.getSamplerCache().get(FilterMode.LINEAR), x, y, x + w, y + h,
            u1, u1 + w * 2 + 1, v1, v1 + h * 2 + 1, color);
        return true;
    }

    /** The frosted world for this frame, or null while shaders warm up or without a world. */
    static GpuTextureView sceneView() {
        return ready && scene != null ? scene.getColorAttachmentView() : null;
    }

    public static void end() { ready = false; }
    public static void close() {
        ready = false;
        if (scene != null) scene.delete();
        scene = null;
    }
}
