package dev.khanh.mixin;

import dev.khanh.feature.KhanhFeatures;
import net.minecraft.client.option.SimpleOption;
import net.minecraft.client.render.LightmapTextureManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

@Mixin(LightmapTextureManager.class)
public abstract class LightmapTextureManagerMixin {
    @WrapOperation(
        method = "update",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/option/SimpleOption;getValue()Ljava/lang/Object;",
            ordinal = 2
        )
    )
    private Object khanh$fullBrightGamma(SimpleOption<?> option, Operation<Object> original) {
        if (KhanhFeatures.enabled("full_bright")) {
            return KhanhFeatures.decimal("full_bright", "brightness", 12.0);
        }
        return original.call(option);
    }
}
