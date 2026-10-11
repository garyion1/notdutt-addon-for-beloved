package dev.khanh.mixin;

import dev.khanh.feature.GalaxyGlintService;
import dev.khanh.feature.ItemWrapState;
import net.minecraft.client.item.ItemModelManager;
import net.minecraft.client.render.item.ItemRenderState;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.item.ItemStack;
import net.minecraft.util.HeldItemContext;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Decides per update whether this render state is a held, non-block item to wrap. */
@Mixin(ItemModelManager.class)
public abstract class ItemModelManagerWrapMixin {
    @Inject(method = "update", at = @At("HEAD"))
    private void khanh$markWrap(ItemRenderState state, ItemStack stack, ItemDisplayContext context, World world,
                                     HeldItemContext holder, int seed, CallbackInfo info) {
        ((ItemWrapState) state).khanh$setWrap(GalaxyGlintService.wraps(stack, context));
    }

    /** Inventory icons are cached unless animated; wrapped ones flow and sparkle, so redraw them. */
    @Inject(method = "update", at = @At("RETURN"))
    private void khanh$animateWrap(ItemRenderState state, ItemStack stack, ItemDisplayContext context, World world,
                                        HeldItemContext holder, int seed, CallbackInfo info) {
        if (GalaxyGlintService.wraps(stack, context)) state.markAnimated();
    }
}
