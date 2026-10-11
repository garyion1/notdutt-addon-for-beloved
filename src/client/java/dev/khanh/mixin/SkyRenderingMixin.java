package dev.khanh.mixin;

import dev.khanh.feature.SkyService;
import net.minecraft.client.render.SkyRendering;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.world.MoonPhase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SkyRendering.class)
public abstract class SkyRenderingMixin {
    @Inject(method = "renderTopSky", at = @At("HEAD"), cancellable = true)
    private void khanh$galaxy(int color, CallbackInfo info) {
        if (SkyService.active()) {
            SkyService.render();
            info.cancel();
        }
    }

    @Inject(method = "renderCelestialBodies", at = @At("HEAD"), cancellable = true)
    private void khanh$hideCelestials(MatrixStack matrices, float tick, float rainGradient,
                                          float starBrightness, MoonPhase moonPhase, float moonSize,
                                          float moonOpacity, CallbackInfo info) {
        if (SkyService.active()) info.cancel();
    }

    @Inject(method = "renderGlowingSky", at = @At("HEAD"), cancellable = true)
    private void khanh$hideGlow(MatrixStack matrices, float tick, int color, CallbackInfo info) {
        if (SkyService.active()) info.cancel();
    }

    @Inject(method = "renderSkyDark", at = @At("HEAD"), cancellable = true)
    private void khanh$hideDark(CallbackInfo info) {
        if (SkyService.active()) info.cancel();
    }

    @Inject(method = "close", at = @At("TAIL"))
    private void khanh$release(CallbackInfo info) {
        SkyService.close();
    }
}
