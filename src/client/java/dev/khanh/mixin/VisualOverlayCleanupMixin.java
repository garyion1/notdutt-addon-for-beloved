package dev.khanh.mixin;

import dev.khanh.feature.KhanhFeatures;
import net.minecraft.client.gui.hud.InGameOverlayRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameOverlayRenderer.class)
public abstract class VisualOverlayCleanupMixin {
    private static boolean hide(String id) { return KhanhFeatures.enabled("visual_cleanup") && KhanhFeatures.bool("visual_cleanup", id, false); }
    @Inject(method = "renderFireOverlay", at = @At("HEAD"), cancellable = true)
    private static void khanh$fire(CallbackInfo info) {
        if (hide("fire")) info.cancel();
    }
    @Inject(method = "renderInWallOverlay", at = @At("HEAD"), cancellable = true)
    private static void khanh$block(CallbackInfo info) { if (hide("block")) info.cancel(); }
    @Inject(method = "renderUnderwaterOverlay", at = @At("HEAD"), cancellable = true)
    private static void khanh$water(CallbackInfo info) {
        if (hide("water") || KhanhFeatures.enabled("clear_water")
            && KhanhFeatures.bool("clear_water", "hide_overlay", true)) info.cancel();
    }
    @Inject(method = "renderFloatingItem", at = @At("HEAD"), cancellable = true)
    private void khanh$totem(CallbackInfo info) { if (hide("totem")) info.cancel(); }
}
