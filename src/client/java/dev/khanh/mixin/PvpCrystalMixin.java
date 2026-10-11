package dev.khanh.mixin;

import dev.khanh.feature.CombatPvpService;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayerInteractionManager.class)
public abstract class PvpCrystalMixin {
    @Inject(method="attackEntity",at=@At("HEAD"),cancellable=true)
    private void khanh$optimiseCrystal(PlayerEntity player,Entity target,CallbackInfo info){
        if(!CombatPvpService.permitCrystalAttack(target))info.cancel();
    }
}
