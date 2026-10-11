package dev.khanh.mixin;

import dev.khanh.feature.FreecamService;
import dev.khanh.feature.InputMetrics;
import net.minecraft.client.Mouse;
import net.minecraft.client.input.MouseInput;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.lwjgl.glfw.GLFW;

@Mixin(Mouse.class)
public abstract class MouseFreeLookMixin {
    @Inject(method = "onMouseButton", at = @At("HEAD"))
    private void khanh$countClicks(long window, MouseInput input, int action, CallbackInfo info) {
        if (action == GLFW.GLFW_PRESS) InputMetrics.press(window, input.button());
    }
    // WrapOperation (not Redirect) so other mods hooking the same call keep working.
    @WrapOperation(
        method = "updateMouse",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/network/ClientPlayerEntity;changeLookDirection(DD)V"
        )
    )
    private void khanh$moveFreeLookCamera(ClientPlayerEntity player, double horizontal, double vertical, Operation<Void> original) {
        if (!FreecamService.changeFreeLookDirection(horizontal, vertical)) {
            double scale = dev.khanh.feature.ZoomService.sensitivityScale();
            original.call(player, horizontal * scale, vertical * scale);
        }
    }
}
