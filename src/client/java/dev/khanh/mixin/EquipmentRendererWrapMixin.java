package dev.khanh.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.khanh.feature.GalaxyGlintService;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.entity.equipment.EquipmentRenderer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.model.Model;
import net.minecraft.client.render.entity.equipment.EquipmentModel;
import net.minecraft.item.equipment.EquipmentAsset;
import net.minecraft.registry.RegistryKey;

/** Worn armour: the armour layer becomes the wrap layer, and vanilla's glint is left off on top. */
@Mixin(EquipmentRenderer.class)
public abstract class EquipmentRendererWrapMixin {
    /** Only pieces that glint (enchanted armour) get the wrap. */
    @Inject(method = "render(Lnet/minecraft/client/render/entity/equipment/EquipmentModel$LayerType;Lnet/minecraft/registry/RegistryKey;Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;ILnet/minecraft/util/Identifier;II)V", at = @At("HEAD"))
    private void khanh$noteGlint(EquipmentModel.LayerType layer, RegistryKey<EquipmentAsset> asset, Model<?> model, Object state,
                                      ItemStack stack, MatrixStack matrices, OrderedRenderCommandQueue queue, int light,
                                      Identifier texture, int outline, int seed, CallbackInfo info) {
        GalaxyGlintService.ARMOR_GLINTS.set(stack != null && stack.hasGlint());
    }

    @WrapOperation(method = "render(Lnet/minecraft/client/render/entity/equipment/EquipmentModel$LayerType;Lnet/minecraft/registry/RegistryKey;Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;ILnet/minecraft/util/Identifier;II)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/RenderLayers;armorCutoutNoCull(Lnet/minecraft/util/Identifier;)Lnet/minecraft/client/render/RenderLayer;"))
    private RenderLayer khanh$armorWrap(Identifier texture, Operation<RenderLayer> original) {
        return GalaxyGlintService.wrapsArmor() ? GalaxyGlintService.armorLayer(texture) : original.call(texture);
    }

    @ModifyExpressionValue(method = "render(Lnet/minecraft/client/render/entity/equipment/EquipmentModel$LayerType;Lnet/minecraft/registry/RegistryKey;Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;ILnet/minecraft/util/Identifier;II)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/item/ItemStack;hasGlint()Z"))
    private boolean khanh$noGlintUnderWrap(boolean glint) {
        return glint && !GalaxyGlintService.wrapsArmor();
    }
}
