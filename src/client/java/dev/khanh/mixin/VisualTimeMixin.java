package dev.khanh.mixin;

import dev.khanh.feature.KhanhFeatures;
import net.minecraft.client.MinecraftClient;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(World.class)
public abstract class VisualTimeMixin {
    @Inject(method = "getTimeOfDay", at = @At("RETURN"), cancellable = true)
    private void khanh$localTime(CallbackInfoReturnable<Long> info) {
        if ((Object)this == MinecraftClient.getInstance().world && KhanhFeatures.enabled("shader_fog") && KhanhFeatures.bool("shader_fog", "time_enabled", false))
            info.setReturnValue((long) KhanhFeatures.number("shader_fog", "time", 18000));
    }
}
