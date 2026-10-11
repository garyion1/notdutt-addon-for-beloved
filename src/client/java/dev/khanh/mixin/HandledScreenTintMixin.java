package dev.khanh.mixin;

import dev.khanh.feature.InventoryTint;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Inventory Colour: tints every container background while it is being drawn. */
@Mixin(HandledScreen.class)
public abstract class HandledScreenTintMixin {
    @Inject(method = "renderBackground", at = @At("HEAD"))
    private void khanh$beginTint(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo info) {
        InventoryTint.begin();
    }

    @Inject(method = "renderBackground", at = @At("RETURN"))
    private void khanh$endTint(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo info) {
        InventoryTint.end();
    }
}
