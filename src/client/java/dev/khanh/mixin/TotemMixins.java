package dev.khanh.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.khanh.feature.TotemService;
import net.minecraft.client.item.ItemModelManager;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Totem Styles: swap the totem's item model for the chosen style. */
public final class TotemMixins {
    private TotemMixins() { }

    @Mixin(ItemModelManager.class)
    public abstract static class Model {
        @ModifyExpressionValue(method = "update", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/item/ItemStack;get(Lnet/minecraft/component/ComponentType;)Ljava/lang/Object;"))
        private Object khanh$totemModel(Object model, @Local(argsOnly = true) ItemStack stack) {
            Identifier swapped = TotemService.model(stack);
            return swapped != null ? swapped : model;
        }
    }
}
