package dev.khanh.feature;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.InputUtil;
import net.minecraft.item.Items;
import org.lwjgl.glfw.GLFW;

/** Releases a fully charged trident once per held-use cycle. */
final class AutoTridentService {
    private boolean released;

    void tick(MinecraftClient client) {
        if (released) {
            client.options.useKey.setPressed(physicalUseKeyDown(client));
            released = false;
            return;
        }
        if (!KhanhFeatures.enabled("auto_trident") || client.player == null || client.currentScreen != null
            || FreecamService.active() || !client.options.useKey.isPressed()
            || !client.player.isUsingItem() || !client.player.getActiveItem().isOf(Items.TRIDENT)) {
            return;
        }
        if (client.player.getItemUseTime() >= KhanhFeatures.number("auto_trident", "charge_ticks", 10)) {
            // Vanilla releases the item when the use key goes up on its next input pass.
            client.options.useKey.setPressed(false);
            released = true;
        }
    }

    private static boolean physicalUseKeyDown(MinecraftClient client) {
        InputUtil.Key bound = InputUtil.fromTranslationKey(client.options.useKey.getBoundKeyTranslationKey());
        long window = client.getWindow().getHandle();
        return switch (bound.getCategory()) {
            case MOUSE -> GLFW.glfwGetMouseButton(window, bound.getCode()) == GLFW.GLFW_PRESS;
            case KEYSYM -> GLFW.glfwGetKey(window, bound.getCode()) == GLFW.GLFW_PRESS;
            case SCANCODE -> false;
        };
    }
}
