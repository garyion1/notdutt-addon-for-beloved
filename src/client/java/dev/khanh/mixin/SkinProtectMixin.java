package dev.khanh.mixin;

import dev.khanh.feature.SkinProtectService;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.player.SkinTextures;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractClientPlayerEntity.class)
public abstract class SkinProtectMixin {
    @Inject(method = "getSkin", at = @At("RETURN"), cancellable = true)
    private void khanh$replacePublicSkin(CallbackInfoReturnable<SkinTextures> info) {
        AbstractClientPlayerEntity player = (AbstractClientPlayerEntity) (Object) this;
        SkinTextures fun = dev.khanh.feature.FunSkinService.replace(player, info.getReturnValue());
        if (fun != null) {
            info.setReturnValue(fun);
            return;
        }
        SkinTextures replacement = SkinProtectService.replacement(player.getUuid());
        if (replacement != null) info.setReturnValue(replacement);
    }
}
