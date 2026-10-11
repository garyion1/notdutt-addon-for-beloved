package dev.khanh.mixin;

import dev.khanh.feature.KhanhFeatures;
import net.minecraft.client.option.GameOptions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** With GUI Blur off, Minecraft's own menus (options, inventory, chat...) keep the world sharp too. */
@Mixin(GameOptions.class)
public abstract class MenuBlurMixin {
    @Inject(method = "getMenuBackgroundBlurrinessValue", at = @At("HEAD"), cancellable = true)
    private void khanh$noMenuBlur(CallbackInfoReturnable<Integer> info) {
        if (!KhanhFeatures.bool("visuals", "blur", false)) info.setReturnValue(0);
    }
}
