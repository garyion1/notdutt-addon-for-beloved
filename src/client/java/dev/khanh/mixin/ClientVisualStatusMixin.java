package dev.khanh.mixin;

import dev.khanh.feature.CosmeticVisualService;
import dev.khanh.feature.CombatPvpService;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayNetworkHandler.class)
public abstract class ClientVisualStatusMixin {
    @Inject(method = "onEntityStatus", at = @At("TAIL"))
    private void khanh$totemEffect(EntityStatusS2CPacket packet, CallbackInfo info) {
        var world = MinecraftClient.getInstance().world;
        if (world != null) {
            var entity=packet.getEntity(world);
            if (packet.getStatus() == 35) {
                CombatPvpService.onTotemPop(entity);
                CosmeticVisualService.totem(entity);
                dev.khanh.feature.PvpEventsService.onTotemPop(entity);
            } else if (packet.getStatus() == 3) {
                dev.khanh.feature.PvpEventsService.onDeath(entity);
            }
        }
    }
}
