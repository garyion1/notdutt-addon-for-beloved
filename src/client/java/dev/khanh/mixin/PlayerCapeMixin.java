package dev.khanh.mixin;

import dev.khanh.feature.CustomCapeService;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.entity.PlayerLikeEntity;
import net.minecraft.entity.player.SkinTextures;
import net.minecraft.util.AssetInfo;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntityRenderer.class)
public abstract class PlayerCapeMixin {
    @Inject(method = "updateRenderState(Lnet/minecraft/entity/PlayerLikeEntity;Lnet/minecraft/client/render/entity/state/PlayerEntityRenderState;F)V", at = @At("TAIL"))
    private void khanh$localCape(PlayerLikeEntity player, PlayerEntityRenderState state, float tick, CallbackInfo info) {
        if (player == MinecraftClient.getInstance().player && dev.khanh.feature.FunSkinService.layers3d()) {
            dev.khanh.feature.FunSkin3DFeature.hideFlatLayers(state);
        }
        Identifier texture = CustomCapeService.texture();
        if (texture == null || player != MinecraftClient.getInstance().player || state.skinTextures == null) return;
        SkinTextures original = state.skinTextures;
        state.skinTextures = new SkinTextures(original.body(),
            new AssetInfo.TextureAssetInfo(texture, texture), original.elytra(), original.model(), original.secure());
        state.capeVisible = true;
    }
}
