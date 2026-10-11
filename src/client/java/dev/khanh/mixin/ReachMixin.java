package dev.khanh.mixin;

import dev.khanh.feature.KhanhFeatures;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Reach: your own player (never others, never the singleplayer server) can hit and build further. */
@Mixin(PlayerEntity.class)
public abstract class ReachMixin {
    @Inject(method = "getEntityInteractionRange", at = @At("RETURN"), cancellable = true)
    private void khanh$entityReach(CallbackInfoReturnable<Double> info) {
        if ((Object) this instanceof ClientPlayerEntity && KhanhFeatures.enabled("reach")) {
            info.setReturnValue(Math.max(info.getReturnValue(), KhanhFeatures.decimal("reach", "entity", 3.5)));
        }
    }

    @Inject(method = "getBlockInteractionRange", at = @At("RETURN"), cancellable = true)
    private void khanh$blockReach(CallbackInfoReturnable<Double> info) {
        if ((Object) this instanceof ClientPlayerEntity && KhanhFeatures.enabled("reach")) {
            info.setReturnValue(Math.max(info.getReturnValue(), KhanhFeatures.decimal("reach", "block", 4.5)));
        }
    }
}
