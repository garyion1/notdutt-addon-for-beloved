package dev.khanh.mixin;

import dev.khanh.feature.ClientDiagnosticsService;
import dev.khanh.feature.KhanhFeatures;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.ChunkDeltaUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.LightUpdateS2CPacket;
import net.minecraft.util.math.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayNetworkHandler.class)
public abstract class ClientPlayNetworkHandlerMixin {
    @Inject(method = "onBlockUpdate", at = @At("TAIL"))
    private void khanh$recordBlockUpdate(BlockUpdateS2CPacket packet, CallbackInfo callbackInfo) {
        ClientDiagnosticsService.recordBlockUpdate(packet.getPos(), packet.getState());
        KhanhFeatures.scanner().onBlockUpdate(MinecraftClient.getInstance().world, packet.getPos(), packet.getState());
    }

    @Inject(method = "onChunkDeltaUpdate", at = @At("TAIL"))
    private void khanh$recordChunkDelta(ChunkDeltaUpdateS2CPacket packet, CallbackInfo callbackInfo) {
        packet.visitUpdates((pos, state) -> {
            ClientDiagnosticsService.recordBlockUpdate(pos, state);
            KhanhFeatures.scanner().onBlockUpdate(MinecraftClient.getInstance().world, pos, state);
        });
    }

    @Inject(method = "onLightUpdate", at = @At("TAIL"))
    private void khanh$recordPrimeLightActivity(LightUpdateS2CPacket packet, CallbackInfo callbackInfo) {
        KhanhFeatures.scanner().onLightUpdate(MinecraftClient.getInstance().world,
            new ChunkPos(packet.getChunkX(), packet.getChunkZ()));
    }
}
