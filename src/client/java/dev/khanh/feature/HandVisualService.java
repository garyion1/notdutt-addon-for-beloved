package dev.khanh.feature;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.khanh.KhanhClient;
import java.nio.ByteBuffer;
import java.util.OptionalInt;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.gl.UniformType;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;
import org.lwjgl.system.MemoryUtil;

/** Capture only the flushed first-person hand draw, not the world or HUD.
 * Output overrides are restored before any overlays run. All work stays on GPU.
 */
public final class HandVisualService {
    private static final RenderPipeline COMPOSITE = pipeline("hands", "hand_composite")
        .withSampler("SceneSampler").withSampler("HandSampler").withSampler("TrailSampler").build();
    private static final RenderPipeline TRAIL = pipeline("hand_trail", "hand_trail")
        .withSampler("HandSampler").withSampler("TrailSampler").build();
    private static Framebuffer hands, scene, historyRead, historyWrite;
    private static GpuBuffer parameters;
    private static GpuTextureView previousColor, previousDepth;
    private static boolean capturing, registered, failed, historyValid;
    private static long lastFrame;
    private HandVisualService() { }

    private static RenderPipeline.Builder pipeline(String name, String shader) {
        return RenderPipeline.builder().withLocation(Identifier.of("khanh", "pipeline/" + name))
            .withVertexShader("core/screenquad")
            .withFragmentShader(Identifier.of("khanh", "core/" + shader))
            .withUniform("HandEffects", UniformType.UNIFORM_BUFFER)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST).withDepthWrite(false)
            .withCull(false).withoutBlend().withVertexFormat(VertexFormats.EMPTY, VertexFormat.DrawMode.TRIANGLES);
    }
    public static void begin() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!registered) {
            registered = true;
            ClientLifecycleEvents.CLIENT_STOPPING.register(c -> close());
        }
        if (!KhanhFeatures.enabled("shader_hands") || failed || client.player == null || client.currentScreen instanceof dev.khanh.gui.KhanhClickGui
            || !(client.options.getPerspective().isFirstPerson() || FreecamService.active())) { close(); return; }
        if (RenderSystem.outputColorTextureOverride != null || RenderSystem.outputDepthTextureOverride != null) return;
        try {
            Framebuffer main = client.getFramebuffer();
            ensure(main.textureWidth, main.textureHeight);
            var encoder = RenderSystem.getDevice().createCommandEncoder();
            encoder.copyTextureToTexture(main.getColorAttachment(), scene.getColorAttachment(), 0, 0, 0, 0, 0, main.textureWidth, main.textureHeight);
            encoder.clearColorAndDepthTextures(hands.getColorAttachment(), 0, hands.getDepthAttachment(), 1.0);
            previousColor = RenderSystem.outputColorTextureOverride;
            previousDepth = RenderSystem.outputDepthTextureOverride;
            RenderSystem.outputColorTextureOverride = hands.getColorAttachmentView();
            RenderSystem.outputDepthTextureOverride = hands.getDepthAttachmentView();
            capturing = true;
        } catch (RuntimeException error) { fail(error); }
    }
    public static void end() {
        if (!capturing) return;
        restore();
        MinecraftClient client = MinecraftClient.getInstance();
        try {
            updateParameters();
            boolean trail = KhanhFeatures.bool("shader_hands", "trail", false);
            if (trail) {
                if (historyRead == null) {
                    historyRead = new SimpleFramebuffer("Khanh Client hand history A", hands.textureWidth, hands.textureHeight, false);
                    historyWrite = new SimpleFramebuffer("Khanh Client hand history B", hands.textureWidth, hands.textureHeight, false);
                    RenderSystem.getDevice().createCommandEncoder().clearColorTexture(historyRead.getColorAttachment(), 0);
                    historyValid = false;
                }
                try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                    () -> "Khanh Client hand trails", historyWrite.getColorAttachmentView(), OptionalInt.empty())) {
                    pass.setPipeline(TRAIL); pass.setUniform("HandEffects", parameters);
                    bind(pass, "HandSampler", hands.getColorAttachmentView());
                    bind(pass, "TrailSampler", historyRead.getColorAttachmentView()); pass.draw(0, 3);
                }
                Framebuffer swap = historyRead; historyRead = historyWrite; historyWrite = swap; historyValid = true;
            } else { deleteHistory(); }
            try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                () -> "Khanh Client held-item outline", client.getFramebuffer().getColorAttachmentView(), OptionalInt.empty())) {
                pass.setPipeline(COMPOSITE); pass.setUniform("HandEffects", parameters);
                bind(pass, "SceneSampler", scene.getColorAttachmentView());
                bind(pass, "HandSampler", hands.getColorAttachmentView());
                bind(pass, "TrailSampler", trail ? historyRead.getColorAttachmentView() : hands.getColorAttachmentView());
                pass.draw(0, 3);
            }
        } catch (RuntimeException error) {
            // Preserve visible hands if the optional shader cannot compile on a GPU.
            try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                () -> "Khanh Client normal hands fallback", client.getFramebuffer().getColorAttachmentView(), OptionalInt.empty())) {
                pass.setPipeline(RenderPipelines.ENTITY_OUTLINE_BLIT);
                bind(pass, "InSampler", hands.getColorAttachmentView()); pass.draw(0, 3);
            }
            fail(error);
        }
    }
    private static void bind(RenderPass pass, String name, GpuTextureView view) {
        pass.bindTexture(name, view, RenderSystem.getSamplerCache().get(FilterMode.LINEAR));
    }
    private static void ensure(int width, int height) {
        if (hands != null && hands.textureWidth == width && hands.textureHeight == height) return;
        close();
        hands = new SimpleFramebuffer("Khanh Client first-person mask", width, height, true);
        scene = new SimpleFramebuffer("Khanh Client scene copy", width, height, false);
        parameters = RenderSystem.getDevice().createBuffer(() -> "Khanh Client hand parameters", GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, 96);
    }
    private static void updateParameters() {
        ByteBuffer data = MemoryUtil.memAlloc(96);
        try {
            long now = System.nanoTime();
            float dt = Math.min(.1F, Math.max(.001F, lastFrame == 0 ? .016F : (now - lastFrame) / 1_000_000_000F)); lastFrame = now;
            rgba(data, KhanhFeatures.color("shader_hands", "outline_color", 0xFF69BCFF));
            rgba(data, KhanhFeatures.color("shader_hands", "glow_color", 0xFF409FFF));
            rgba(data, KhanhFeatures.color("shader_hands", "fill_color", 0xFF69BCFF));
            data.putFloat(1F / hands.textureWidth).putFloat(1F / hands.textureHeight)
                .putFloat(KhanhFeatures.bool("shader_hands", "outline", true) ? (float) KhanhFeatures.decimal("shader_hands", "width", 1.1) : 0)
                .putFloat(KhanhFeatures.bool("shader_hands", "glow", true) ? (float) KhanhFeatures.decimal("shader_hands", "glow_radius", 5) : 0);
            String mode = KhanhFeatures.choice("shader_hands", "mode", "Outline");
            data.putFloat(mode.equals("Outline") ? 0 : (float) KhanhFeatures.decimal("shader_hands", "fill_strength", .35))
                .putFloat((float) KhanhFeatures.decimal("shader_hands", "glow_strength", .35))
                .putFloat(mode.equals("Rainbow") ? 1 : mode.equals("Glass") ? 2 : mode.equals("Mirror") ? 3 : mode.equals("Contour") ? 4 : 0).putFloat((now % 120_000_000_000L) / 1_000_000_000F);
            float persistence = (float) KhanhFeatures.decimal("shader_hands", "trail_duration", .3);
            data.putFloat(KhanhFeatures.bool("shader_hands", "trail", false) ? .55F : 0)
                .putFloat(historyValid ? (float) Math.exp(-dt * 4 / persistence) : 0)
                .putFloat(dt * .015F).putFloat(KhanhFeatures.bool("shader_hands", "keep_shading", true) ? 1 : 0);
            data.flip(); RenderSystem.getDevice().createCommandEncoder().writeToBuffer(parameters.slice(), data);
        } finally { MemoryUtil.memFree(data); }
    }
    private static void rgba(ByteBuffer data, int c) {
        data.putFloat((c >> 16 & 255) / 255F).putFloat((c >> 8 & 255) / 255F).putFloat((c & 255) / 255F).putFloat(1);
    }
    private static void restore() {
        RenderSystem.outputColorTextureOverride = previousColor;
        RenderSystem.outputDepthTextureOverride = previousDepth;
        previousColor = previousDepth = null; capturing = false;
    }
    private static void deleteHistory() {
        if (historyRead != null) historyRead.delete(); if (historyWrite != null) historyWrite.delete();
        historyRead = historyWrite = null; historyValid = false;
    }
    private static void fail(RuntimeException error) {
        if (capturing) restore(); failed = true;
        KhanhClient.LOGGER.warn("Khanh Client optional hand shader disabled; normal hand rendering retained", error);
        close();
    }
    public static void close() {
        if (capturing) restore(); deleteHistory();
        if (hands != null) hands.delete(); if (scene != null) scene.delete(); if (parameters != null) parameters.close();
        hands = scene = null; parameters = null; lastFrame = 0;
    }
}
