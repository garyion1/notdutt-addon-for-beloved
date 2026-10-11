package dev.khanh.mixin;

import dev.khanh.feature.ItemWrapState;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.item.ItemRenderState;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Carries "wrap this item" from the model update to the moment its layers are submitted. */
@Mixin(ItemRenderState.class)
public abstract class ItemRenderStateWrapMixin implements ItemWrapState {
    @Unique private boolean khanh$wrap;

    @Override
    public void khanh$setWrap(boolean wrap) {
        khanh$wrap = wrap;
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void khanh$beginWrap(MatrixStack matrices, OrderedRenderCommandQueue queue, int light, int overlay,
                                      int outline, CallbackInfo info) {
        ItemWrapState.ACTIVE.set(khanh$wrap);
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void khanh$endWrap(MatrixStack matrices, OrderedRenderCommandQueue queue, int light, int overlay,
                                    int outline, CallbackInfo info) {
        ItemWrapState.ACTIVE.set(false);
    }
}
