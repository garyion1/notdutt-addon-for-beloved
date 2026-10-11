package dev.khanh.mixin;

import dev.khanh.feature.GalaxyGlintService;
import dev.khanh.feature.ItemWrapState;
import java.util.List;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.TexturedRenderLayers;
import net.minecraft.client.render.command.BatchingRenderCommandQueue;
import net.minecraft.client.render.command.RenderCommandQueue;
import net.minecraft.client.render.command.OrderedRenderCommandQueueImpl;
import net.minecraft.client.render.item.ItemRenderState;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemDisplayContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Swaps a wrapped held item's atlas layer for the wrap layer (no vanilla glint on top). Hooked on
 * the queue itself so it coexists with Fabric Indigo's redirect of the submit call.
 */
@Mixin({OrderedRenderCommandQueueImpl.class, BatchingRenderCommandQueue.class})
public abstract class RenderQueueWrapMixin {
    @Inject(method = "submitItem", at = @At("HEAD"), cancellable = true)
    private void khanh$wrapItem(MatrixStack matrices, ItemDisplayContext context, int light, int overlay, int outline,
                                     int[] tints, List<BakedQuad> quads, RenderLayer layer, ItemRenderState.Glint glint,
                                     CallbackInfo info) {
        if (!ItemWrapState.ACTIVE.get() || layer != TexturedRenderLayers.getItemTranslucentCull()) return;
        info.cancel();
        ((RenderCommandQueue) this).submitItem(matrices, context, light, overlay, outline, tints, quads,
            GalaxyGlintService.layer(), ItemRenderState.Glint.NONE);
    }
}
