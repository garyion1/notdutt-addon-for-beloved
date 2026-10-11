package dev.khanh.mixin;

import dev.khanh.feature.CombatPvpService;
import dev.khanh.feature.FreecamService;
import dev.khanh.feature.KhanhFeatures;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MinecraftClient.class)
public abstract class MinecraftCombatMixin {
    @Shadow protected int attackCooldown;
    @Inject(method="doAttack",at=@At("HEAD"),cancellable=true)
    private void khanh$combatAttack(CallbackInfoReturnable<Boolean> info) {
        MinecraftClient client=(MinecraftClient)(Object)this;
        if(client.player==null||client.currentScreen!=null||FreecamService.active())return;
        if(KhanhFeatures.enabled("no_hit_delay"))attackCooldown=0;
        if(CombatPvpService.beforeAttack(client))info.setReturnValue(true);
    }
    @Inject(method="doItemUse",at=@At("HEAD"),cancellable=true)
    private void khanh$anchorOwnsUse(CallbackInfo info){if(CombatPvpService.busy())info.cancel();}
}
