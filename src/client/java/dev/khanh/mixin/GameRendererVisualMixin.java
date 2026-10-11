package dev.khanh.mixin;

import dev.khanh.feature.FreecamService;
import dev.khanh.feature.HandVisualService;
import dev.khanh.feature.KhanhFeatures;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.client.render.Camera;

@Mixin(GameRenderer.class)
public abstract class GameRendererVisualMixin {
    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void khanh$unboundFov(Camera camera, float tickProgress, boolean changingFov,
                                    CallbackInfoReturnable<Float> info) {
        if (camera == null) return;
        float fov = info.getReturnValue();
        if (KhanhFeatures.enabled("fov_unbound")) fov = (float) KhanhFeatures.decimal("fov_unbound", "fov", 110);
        float zoomed = dev.khanh.feature.ZoomService.apply(fov);
        if (zoomed != info.getReturnValue()) info.setReturnValue(zoomed);
    }
    /** Clear Water: no underwater zoom, so the view matches the surface. */
    @WrapOperation(method = "getFov", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/Camera;getSubmersionType()Lnet/minecraft/block/enums/CameraSubmersionType;"))
    private net.minecraft.block.enums.CameraSubmersionType khanh$surfaceFov(Camera camera,
                                                                               Operation<net.minecraft.block.enums.CameraSubmersionType> original) {
        var type = original.call(camera);
        return type == net.minecraft.block.enums.CameraSubmersionType.WATER && KhanhFeatures.enabled("clear_water")
            && KhanhFeatures.bool("clear_water", "normal_fov", true) ? net.minecraft.block.enums.CameraSubmersionType.NONE : type;
    }
    private static final String HAND_DRAW = "Lnet/minecraft/client/render/item/HeldItemRenderer;renderItem(FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/network/ClientPlayerEntity;I)V";
    @Inject(method = "renderHand", at = @At(value = "INVOKE", target = HAND_DRAW))
    private void khanh$captureHands(CallbackInfo info) { HandVisualService.begin(); }
    @Inject(method = "renderHand", at = @At(value = "INVOKE", target = HAND_DRAW, shift = At.Shift.AFTER))
    private void khanh$outlineHands(CallbackInfo info) { HandVisualService.end(); }
    @WrapOperation(method = "renderHand", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/option/Perspective;isFirstPerson()Z"))
    private boolean khanh$showHandsInFreecam(Perspective perspective, Operation<Boolean> original) {
        return original.call(perspective) || FreecamService.active();
    }
    @Inject(method = "tiltViewWhenHurt", at = @At("HEAD"), cancellable = true)
    private void khanh$hurtTilt(CallbackInfo info) { if (KhanhFeatures.enabled("visual_cleanup") && KhanhFeatures.bool("visual_cleanup", "hurt", false)) info.cancel(); }
}
