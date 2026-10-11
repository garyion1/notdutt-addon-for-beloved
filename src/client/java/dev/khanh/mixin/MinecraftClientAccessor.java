package dev.khanh.mixin;

import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(MinecraftClient.class)
public interface MinecraftClientAccessor {
    @Accessor("itemUseCooldown")
    int khanh$getItemUseCooldown();

    @Accessor("itemUseCooldown")
    void khanh$setItemUseCooldown(int value);

    @Invoker("doItemUse")
    void khanh$doItemUse();
}
