package dev.khanh.mixin;

import dev.khanh.feature.KhanhFeatures;
import net.minecraft.client.render.WeatherRendering;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WeatherRendering.class)
public abstract class VisualWeatherMixin {
    @Inject(method = "renderPrecipitation", at = @At("HEAD"), cancellable = true)
    private void khanh$weather(CallbackInfo info) { if (KhanhFeatures.enabled("visual_cleanup") && KhanhFeatures.bool("visual_cleanup", "weather", false)) info.cancel(); }
}
