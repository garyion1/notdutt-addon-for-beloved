package dev.khanh.mixin;

import dev.khanh.feature.FreecamMiningService;
import dev.khanh.feature.FreecamService;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * In Freecam the player's own crosshair target (where the body still looks) is what vanilla mines,
 * like Free Look. Attacking is only blocked when Freecam's Mining option is off.
 */
@Mixin(MinecraftClient.class)
public abstract class FreecamMiningMixin {
    @Inject(method = "doAttack", at = @At("HEAD"), cancellable = true)
    private void khanh$blockCameraAttack(CallbackInfoReturnable<Boolean> info) {
        if (FreecamService.active() && !FreecamMiningService.enabled()) info.setReturnValue(false);
    }

    @Inject(method = "handleBlockBreaking", at = @At("HEAD"), cancellable = true)
    private void khanh$blockCameraMining(boolean breaking, CallbackInfo info) {
        if (FreecamService.active() && !FreecamMiningService.enabled()) info.cancel();
    }
}
