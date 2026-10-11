package dev.khanh.feature;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.khanh.KhanhClient;
import java.nio.ByteBuffer;
import java.util.OptionalInt;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.gl.UniformType;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;
import org.lwjgl.system.MemoryUtil;

/** Lightweight temporal world blur. It runs before hands and HUD and never reads pixels on the CPU. */
public final class MotionBlurService {
    private static final RenderPipeline PIPELINE = RenderPipeline.builder()
        .withLocation(Identifier.of(KhanhClient.MOD_ID, "pipeline/motion_blur"))
        .withVertexShader("core/screenquad")
        .withFragmentShader(Identifier.of(KhanhClient.MOD_ID, "core/motion_blur"))
        .withUniform("MotionBlur", UniformType.UNIFORM_BUFFER)
        .withSampler("CurrentSampler").withSampler("HistorySampler")
        .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST).withDepthWrite(false)
        .withCull(false).withoutBlend()
        .withVertexFormat(VertexFormats.EMPTY, VertexFormat.DrawMode.TRIANGLES).build();
    private static Framebuffer historyRead, historyWrite;
    private static GpuBuffer parameters;
    private static boolean valid, failed;

    private MotionBlurService() { }

    public static void render() {
        MinecraftClient client = MinecraftClient.getInstance();
        int minimumFps = KhanhFeatures.number("motion_blur", "minimum_fps", 30);
        if (!KhanhFeatures.enabled("motion_blur") || failed || client.player == null
            || client.currentScreen != null || client.getCurrentFps() < minimumFps) {
            valid = false;
            return;
        }
        try {
            Framebuffer main = client.getFramebuffer();
            ensure(main.textureWidth, main.textureHeight);
            var encoder = RenderSystem.getDevice().createCommandEncoder();
            if (!valid) {
                encoder.copyTextureToTexture(main.getColorAttachment(), historyRead.getColorAttachment(),
                    0, 0, 0, 0, 0, main.textureWidth, main.textureHeight);
                valid = true;
                return;
            }
            updateParameters();
            try (RenderPass pass = encoder.createRenderPass(
                    () -> "Khanh Client temporal motion blur", historyWrite.getColorAttachmentView(), OptionalInt.empty())) {
                pass.setPipeline(PIPELINE);
                pass.setUniform("MotionBlur", parameters);
                pass.bindTexture("CurrentSampler", main.getColorAttachmentView(),
                    RenderSystem.getSamplerCache().get(FilterMode.LINEAR));
                pass.bindTexture("HistorySampler", historyRead.getColorAttachmentView(),
                    RenderSystem.getSamplerCache().get(FilterMode.LINEAR));
                pass.draw(0, 3);
            }
            Framebuffer swap = historyRead;
            historyRead = historyWrite;
            historyWrite = swap;
            encoder.copyTextureToTexture(historyRead.getColorAttachment(), main.getColorAttachment(),
                0, 0, 0, 0, 0, main.textureWidth, main.textureHeight);
        } catch (RuntimeException error) {
            failed = true;
            close();
            KhanhClient.LOGGER.warn("Optional motion blur disabled; normal rendering retained", error);
        }
    }

    private static void ensure(int width, int height) {
        if (historyRead != null && historyRead.textureWidth == width && historyRead.textureHeight == height) return;
        close();
        historyRead = new SimpleFramebuffer("Khanh Client motion history A", width, height, false);
        historyWrite = new SimpleFramebuffer("Khanh Client motion history B", width, height, false);
        parameters = RenderSystem.getDevice().createBuffer(() -> "Khanh Client motion parameters",
            GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, 16);
    }

    private static void updateParameters() {
        ByteBuffer data = MemoryUtil.memAlloc(16);
        try {
            data.putFloat((float) KhanhFeatures.decimal("motion_blur", "strength", .22));
            data.putFloat(0).putFloat(0).putFloat(0).flip();
            RenderSystem.getDevice().createCommandEncoder().writeToBuffer(parameters.slice(), data);
        } finally {
            MemoryUtil.memFree(data);
        }
    }

    public static void close() {
        valid = false;
        if (historyRead != null) historyRead.delete();
        if (historyWrite != null) historyWrite.delete();
        if (parameters != null) parameters.close();
        historyRead = historyWrite = null;
        parameters = null;
    }
}
