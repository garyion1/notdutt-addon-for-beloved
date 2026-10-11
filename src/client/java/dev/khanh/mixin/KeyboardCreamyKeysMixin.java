package dev.khanh.mixin;

import dev.khanh.feature.KhanhFeatures;
import dev.khanh.feature.KhanhSounds;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Keyboard;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.input.KeyInput;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Keyboard.class)
public abstract class KeyboardCreamyKeysMixin {
    @Inject(method = "onKey", at = @At("TAIL"))
    private void khanh$softKey(long window, int action, KeyInput input, CallbackInfo info) {
        if (action != GLFW.GLFW_PRESS || input.key() == GLFW.GLFW_KEY_UNKNOWN
            || !KhanhFeatures.enabled("creamy_keys")) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.getWindow().getHandle() != window) return;
        if (client.currentScreen != null && !(client.currentScreen instanceof ChatScreen
            && KhanhFeatures.bool("creamy_keys", "in_chat", false))) return;
        KhanhSounds.creamyKey(client);
    }
}
