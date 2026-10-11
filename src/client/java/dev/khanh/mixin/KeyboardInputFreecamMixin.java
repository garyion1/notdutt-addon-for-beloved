package dev.khanh.mixin;

import dev.khanh.feature.FreecamService;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.input.KeyboardInput;
import net.minecraft.util.PlayerInput;
import net.minecraft.util.math.Vec2f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardInput.class)
public abstract class KeyboardInputFreecamMixin {
    @Inject(method = "tick", at = @At("TAIL"))
    private void khanh$keepFreecamKeysOutOfBodyMovement(CallbackInfo info) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!FreecamService.active() || FreecamService.bodyFollowsKeys()
            || client.player == null || client.player.input != (Object) this) {
            return;
        }
        ClientInputAccessor input = (ClientInputAccessor) client.player.input;
        input.khanh$setPlayerInput(PlayerInput.DEFAULT);
        input.khanh$setMovementVector(Vec2f.ZERO);
        client.player.setSneaking(false);
    }
}
