package dev.khanh.mixin;

import dev.khanh.feature.KhanhFeatures;
import dev.khanh.gui.KhanhPauseScreen;
import dev.khanh.gui.KhanhServerScreen;
import dev.khanh.gui.KhanhTitleScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Every route to the main menu, server list or pause menu opens Khanh Client's glass version instead
 * of Minecraft's while the Main Menu module (and its per-screen toggle) is on. */
@Mixin(MinecraftClient.class)
public abstract class TitleScreenSwapMixin {
    @ModifyVariable(method = "setScreen", at = @At("HEAD"), argsOnly = true)
    private Screen khanh$zeroScreens(Screen screen) {
        // Khanh Client always uses its own main menu, server list and pause menu.
        if (screen instanceof TitleScreen) return new KhanhTitleScreen();
        if (screen instanceof MultiplayerScreen multiplayer) {
            return new KhanhServerScreen(((MultiplayerScreenAccessor) multiplayer).khanh$parent());
        }
        if (screen instanceof GameMenuScreen pause && pause.shouldShowMenu()) return new KhanhPauseScreen();
        return screen;
    }
}
