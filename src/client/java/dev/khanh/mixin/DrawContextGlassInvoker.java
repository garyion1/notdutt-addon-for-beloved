package dev.khanh.mixin;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.gl.GpuSampler;
import net.minecraft.client.gui.DrawContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Use vanilla's queued GUI quad path so transforms, clipping and text ordering remain intact. */
@Mixin(DrawContext.class)
public interface DrawContextGlassInvoker {
    @Invoker("drawTexturedQuad")
    void khanh$glassQuad(RenderPipeline pipeline, GpuTextureView texture, GpuSampler sampler,
                         int x1, int y1, int x2, int y2,
                         float u1, float u2, float v1, float v2, int color);
}
