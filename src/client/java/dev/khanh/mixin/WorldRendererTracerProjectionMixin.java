package dev.khanh.mixin;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import dev.khanh.feature.StorageTracerRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.memory.ObjectAllocator;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldRenderer.class)
public abstract class WorldRendererTracerProjectionMixin {
    @Inject(method = "render", at = @At("HEAD"))
    private void khanh$captureTracerProjection(ObjectAllocator allocator, RenderTickCounter tickCounter,
            boolean blockOutline, Camera camera, Matrix4f view, Matrix4f drawnProjection,
            Matrix4f cullProjection, GpuBufferSlice fogBuffer, Vector4f fogColor, boolean renderSky,
            CallbackInfo info) {
        // Fabric's extraction context exposes the culling projection (which can
        // have a different FOV), not the bobbed projection used to draw the world.
        StorageTracerRenderer.capture(view, drawnProjection, camera.getCameraPos());
    }
}
