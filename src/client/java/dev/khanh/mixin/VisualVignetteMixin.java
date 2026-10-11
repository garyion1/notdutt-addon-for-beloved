package dev.khanh.mixin;

import dev.khanh.feature.KhanhFeatures;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Hides this one overlay without touching the player's video settings. */
@Mixin(InGameHud.class)
public abstract class VisualVignetteMixin {
    @Inject(method = "renderVignetteOverlay", at = @At("HEAD"), cancellable = true)
    private void khanh$noVignette(DrawContext context, Entity entity, CallbackInfo info) {
        if (KhanhFeatures.enabled("visual_cleanup") && KhanhFeatures.bool("visual_cleanup", "vignette", false)) info.cancel();
    }
}
