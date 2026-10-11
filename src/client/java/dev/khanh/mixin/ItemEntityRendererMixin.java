package dev.khanh.mixin;

import dev.khanh.feature.KhanhFeatures;
import dev.khanh.feature.ItemPhysicsState;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.ItemEntityRenderer;
import net.minecraft.client.render.entity.state.ItemEntityRenderState;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.ItemEntity;
import net.minecraft.util.math.RotationAxis;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemEntityRenderer.class)
public abstract class ItemEntityRendererMixin {
    @Inject(method = "updateRenderState(Lnet/minecraft/entity/ItemEntity;Lnet/minecraft/client/render/entity/state/ItemEntityRenderState;F)V", at = @At("TAIL"))
    private void khanh$trackGround(ItemEntity entity, ItemEntityRenderState state, float tick, CallbackInfo info) {
        ((ItemPhysicsState) state).khanh$setGrounded(entity.isOnGround());
    }

    @Inject(method = "render(Lnet/minecraft/client/render/entity/state/ItemEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/render/state/CameraRenderState;)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/entity/ItemEntityRenderer;render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;ILnet/minecraft/client/render/entity/state/ItemStackEntityRenderState;Lnet/minecraft/util/math/random/Random;Lnet/minecraft/util/math/Box;)V", shift = At.Shift.BEFORE))
    private void khanh$physics(ItemEntityRenderState state, MatrixStack matrices,
                                   OrderedRenderCommandQueue queue, CameraRenderState camera, CallbackInfo info) {
        if (!KhanhFeatures.enabled("item_physics")) return;
        if (KhanhFeatures.bool("item_physics", "small", false)) matrices.scale(.5F, .5F, .5F);
        if (((ItemPhysicsState) state).khanh$isGrounded())
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(90));
        else matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(
            ItemEntity.getRotation(state.age, state.uniqueOffset) * 300));
    }
}
