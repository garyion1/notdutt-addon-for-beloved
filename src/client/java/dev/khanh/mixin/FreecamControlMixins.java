package dev.khanh.mixin;

import dev.khanh.feature.FreecamMiningService;
import dev.khanh.feature.FreecamService;
import dev.khanh.feature.KhanhFeatures;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Mouse;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Freecam controls: scroll for speed. */
public final class FreecamControlMixins {
    private FreecamControlMixins() { }

    @Mixin(Mouse.class)
    public abstract static class Scroll {
        @Inject(method = "onMouseScroll", at = @At("HEAD"), cancellable = true)
        private void khanh$freecamSpeed(long window, double horizontal, double vertical, CallbackInfo info) {
            MinecraftClient client = MinecraftClient.getInstance();
            if (FreecamService.active() && client.currentScreen == null && KhanhFeatures.bool("freecam", "scroll_speed", true)) {
                FreecamService.adjustSpeed(vertical);
                info.cancel();
            } else if (client.currentScreen == null && dev.khanh.feature.ZoomService.scroll(vertical)) {
                info.cancel();   // scroll changes the zoom instead of the hotbar slot
            }
        }
    }
}
