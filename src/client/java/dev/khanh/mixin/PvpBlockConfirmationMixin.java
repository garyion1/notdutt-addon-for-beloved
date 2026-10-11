package dev.khanh.mixin;

import dev.khanh.feature.CombatPvpService;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.ChunkDeltaUpdateS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** These handlers run after Minecraft applies a packet on the client thread. */
@Mixin(ClientPlayNetworkHandler.class)
public abstract class PvpBlockConfirmationMixin {
    @Inject(method="onBlockUpdate",at=@At("TAIL"))
    private void khanh$confirmAnchor(BlockUpdateS2CPacket packet,CallbackInfo info) {
        CombatPvpService.serverAnchorUpdate((ClientPlayNetworkHandler)(Object)this,packet.getPos(),packet.getState());
    }
    @Inject(method="onChunkDeltaUpdate",at=@At("TAIL"))
    private void khanh$confirmAnchorDelta(ChunkDeltaUpdateS2CPacket packet,CallbackInfo info) {
        packet.visitUpdates((pos,state)->CombatPvpService.serverAnchorUpdate((ClientPlayNetworkHandler)(Object)this,pos,state));
    }
}
