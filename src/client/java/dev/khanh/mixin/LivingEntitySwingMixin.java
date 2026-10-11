package dev.khanh.mixin;

import dev.khanh.feature.KhanhFeatures;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class LivingEntitySwingMixin {
    @Inject(method = "getHandSwingDuration", at = @At("RETURN"), cancellable = true)
    private void khanh$changeLocalSwingDuration(CallbackInfoReturnable<Integer> info) {
        if ((Object) this == MinecraftClient.getInstance().player && KhanhFeatures.enabled("swing_speed")) {
            double speed = KhanhFeatures.decimal("swing_speed", "speed", 1.15);
            info.setReturnValue(Math.max(1, (int) Math.round(info.getReturnValue() / speed)));
        }
    }
}
