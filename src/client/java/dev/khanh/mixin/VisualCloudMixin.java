package dev.khanh.mixin;

import dev.khanh.feature.KhanhFeatures;
import net.minecraft.client.render.WorldRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldRenderer.class)
public abstract class VisualCloudMixin {
    @Inject(method = "renderClouds", at = @At("HEAD"), cancellable = true)
    private void khanh$clouds(CallbackInfo info) { if (KhanhFeatures.enabled("visual_cleanup") && KhanhFeatures.bool("visual_cleanup", "clouds", false)) info.cancel(); }
}
