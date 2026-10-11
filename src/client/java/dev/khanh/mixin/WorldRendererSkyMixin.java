package dev.khanh.mixin;

import dev.khanh.feature.SkyService;
import net.minecraft.client.option.CloudRenderMode;
import net.minecraft.client.render.FrameGraphBuilder;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldRenderer.class)
public abstract class WorldRendererSkyMixin {
    @Inject(method = "renderClouds", at = @At("HEAD"), cancellable = true)
    private void khanh$clearSky(FrameGraphBuilder frameGraph, CloudRenderMode mode,
                                   Vec3d camera, long time, float tick, int color,
                                   float cloudHeight, CallbackInfo info) {
        if (SkyService.active()) info.cancel();
    }
}
