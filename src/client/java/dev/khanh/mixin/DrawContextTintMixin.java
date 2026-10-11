package dev.khanh.mixin;

import dev.khanh.feature.InventoryTint;
import net.minecraft.client.gui.DrawContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Every textured GUI quad funnels through here; while a container background draws, tint it. */
@Mixin(DrawContext.class)
public abstract class DrawContextTintMixin {
    @ModifyVariable(method = "drawTexturedQuad(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lcom/mojang/blaze3d/textures/GpuTextureView;Lnet/minecraft/client/gl/GpuSampler;IIIIFFFFI)V",
        at = @At("HEAD"), argsOnly = true, ordinal = 4)
    private int khanh$tint(int color) {
        return InventoryTint.apply(color);
    }
}
