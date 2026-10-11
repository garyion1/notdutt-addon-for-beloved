package dev.khanh.mixin;

import dev.khanh.feature.KhanhFeatures;
import dev.khanh.feature.SwingStyle;
import dev.khanh.feature.HeldItemSize;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import net.minecraft.util.math.RotationAxis;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HeldItemRenderer.class)
public abstract class HeldItemVisualMixin {
    @Inject(method = "renderFirstPersonItem", at = @At("HEAD"))
    private void khanh$viewModel(AbstractClientPlayerEntity player, float tick, float pitch, Hand hand, float swing,
                                ItemStack item, float equip, MatrixStack matrices, OrderedRenderCommandQueue queue, int light, CallbackInfo info) {
        matrices.push();
        if (!KhanhFeatures.enabled("view_model")) return;
        String id = hand == Hand.MAIN_HAND ? "main" : "off";
        matrices.translate(KhanhFeatures.decimal("view_model", id + "_x", 0), KhanhFeatures.decimal("view_model", id + "_y", 0), KhanhFeatures.decimal("view_model", id + "_z", 0));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees((float) KhanhFeatures.decimal("view_model", id + "_rx", 0)));
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((float) KhanhFeatures.decimal("view_model", id + "_ry", 0)));
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees((float) KhanhFeatures.decimal("view_model", id + "_rz", 0)));
        float size = (float) KhanhFeatures.decimal("view_model", id + "_scale", 1); matrices.scale(size, size, size);
    }
    @Inject(method = "renderFirstPersonItem", at = @At("RETURN"))
    private void khanh$restoreViewModel(AbstractClientPlayerEntity player, float tick, float pitch, Hand hand, float swing,
                                ItemStack item, float equip, MatrixStack matrices, OrderedRenderCommandQueue queue, int light, CallbackInfo info) { matrices.pop(); }
    private static final String ITEM_DRAW = "renderItem(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/item/ItemStack;Lnet/minecraft/item/ItemDisplayContext;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;I)V";
    @Inject(method = ITEM_DRAW, at = @At("HEAD"))
    private void khanh$itemSize(LivingEntity entity, ItemStack item, ItemDisplayContext display,
                               MatrixStack matrices, OrderedRenderCommandQueue queue, int light, CallbackInfo info) {
        matrices.push();
        if (!display.isFirstPerson() || !KhanhFeatures.enabled("item_size")) return;
        float size = HeldItemSize.scale(item);
        matrices.scale(size, size, size);
    }
    @Inject(method = ITEM_DRAW, at = @At("RETURN"))
    private void khanh$restoreItemSize(LivingEntity entity, ItemStack item, ItemDisplayContext display,
                                      MatrixStack matrices, OrderedRenderCommandQueue queue, int light, CallbackInfo info) {
        matrices.pop();
    }
    @Inject(method = "applySwingOffset", at = @At("HEAD"), cancellable = true)
    private void khanh$swing(MatrixStack matrices, Arm arm, float progress, CallbackInfo info) {
        if (!KhanhFeatures.enabled("swing_animations")) return;
        float phase = Math.max(0, Math.min(1, progress));
        progress = switch (KhanhFeatures.choice("swing_animations", "easing", "Natural")) {
            case "Smooth" -> phase * phase * (3 - 2 * phase);
            case "Sharp" -> 1 - (1 - phase) * (1 - phase);
            default -> phase;
        };
        int side = (arm == Arm.RIGHT ? 1 : -1) * (KhanhFeatures.bool("swing_animations", "mirror", false) ? -1 : 1);
        var t = SwingStyle.transform(KhanhFeatures.choice("swing_animations", "style", "Smooth"), progress,
            (float) KhanhFeatures.decimal("swing_animations", "strength", .8), side);
        matrices.translate(t.x(), t.y(), t.z());
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(t.rx()));
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(t.ry()));
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(t.rz())); info.cancel();
    }
}
