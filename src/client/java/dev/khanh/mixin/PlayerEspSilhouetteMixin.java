package dev.khanh.mixin;

import dev.khanh.feature.KhanhFeatures;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Player ESP's glow outline: vanilla's real silhouette outline, locally and only in range. */
@Mixin(Entity.class)
public abstract class PlayerEspSilhouetteMixin {
    @Inject(method = "isGlowing", at = @At("RETURN"), cancellable = true)
    private void khanh$playerEspSilhouette(CallbackInfoReturnable<Boolean> info) {
        MinecraftClient client = MinecraftClient.getInstance();
        Entity entity = (Entity) (Object) this;
        if (!(entity instanceof AbstractClientPlayerEntity player)
            || !KhanhFeatures.enabled("player_esp")
            || !KhanhFeatures.bool("player_esp", "glow_outline", true)) return;
        if (client.player == null || player == client.player || !player.isAlive()) return;
        double range = KhanhFeatures.decimal("player_esp", "range", 128);
        if (player.squaredDistanceTo(client.player) <= range * range) info.setReturnValue(true);
    }

}
