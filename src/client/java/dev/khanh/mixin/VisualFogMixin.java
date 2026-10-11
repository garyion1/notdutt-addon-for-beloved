package dev.khanh.mixin;

import dev.khanh.feature.KhanhFeatures;
import net.minecraft.block.enums.CameraSubmersionType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.fog.FogRenderer;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FogRenderer.class)
public abstract class VisualFogMixin {
    private static boolean active() {
        var client = MinecraftClient.getInstance();
        var sub = client.gameRenderer.getCamera().getSubmersionType();
        return client.world != null && (sub == CameraSubmersionType.NONE || sub == CameraSubmersionType.ATMOSPHERIC)
            && KhanhFeatures.enabled("shader_fog") && KhanhFeatures.bool("shader_fog", "fog_enabled", false);
    }
    private static boolean clearWater() {
        var client = MinecraftClient.getInstance();
        return client.world != null && client.gameRenderer.getCamera().getSubmersionType() == CameraSubmersionType.WATER
            && KhanhFeatures.enabled("clear_water");
    }
    /** Clear Water: under water the fog, colour and view distance are exactly the surface ones. */
    @Inject(method = "getCameraSubmersionType", at = @At("RETURN"), cancellable = true)
    private void khanh$surfaceFog(CallbackInfoReturnable<CameraSubmersionType> info) {
        if (info.getReturnValue() == CameraSubmersionType.WATER && clearWater()) info.setReturnValue(CameraSubmersionType.ATMOSPHERIC);
    }
    @Inject(method = "getFogColor", at = @At("RETURN"), cancellable = true)
    private void khanh$fogColour(CallbackInfoReturnable<Vector4f> info) {
        if (!active()) return;
        int c = KhanhFeatures.color("shader_fog", "fog_color", 0xFF183047);
        info.setReturnValue(new Vector4f((c >> 16 & 255) / 255F, (c >> 8 & 255) / 255F, (c & 255) / 255F, 1));
    }
    @ModifyArgs(method = "applyFog(Lnet/minecraft/client/render/Camera;ILnet/minecraft/client/render/RenderTickCounter;FLnet/minecraft/client/world/ClientWorld;)Lorg/joml/Vector4f;",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/fog/FogRenderer;applyFog(Ljava/nio/ByteBuffer;ILorg/joml/Vector4f;FFFFFF)V"))
    private void khanh$fogDistance(Args args) {
        if (!active()) return;
        float far = (float)Math.min(KhanhFeatures.decimal("shader_fog", "fog", 192), MinecraftClient.getInstance().options.getClampedViewDistance() * 16);
        args.set(3, far * .6F); args.set(4, far); args.set(5, far * .8F); args.set(6, far); args.set(7, far); args.set(8, far);
    }
}
