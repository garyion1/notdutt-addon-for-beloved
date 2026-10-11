package dev.khanh.gui;

import dev.khanh.KhanhClient;
import dev.khanh.config.ConfigManager;
import dev.khanh.feature.KhanhFeatures;
import dev.khanh.module.Module;
import dev.khanh.module.ModuleOption;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;

/** Marks Khanh Client's module browser screens, whichever layout is active, so world
 * overlays and the shared glass scene can treat them the same way. */
public interface KhanhClickGui {
    String GLASS = "Liquid Glass";
    String CLASSIC = "Monolith";

    /** Opens whichever layout the player picked in Visuals → GUI Layout. */
    static Screen create(Screen parent) {
        return new KhanhColumnsScreen(parent);   // Khanh Client's own menu
    }

    /** Remembers the choice and swaps straight to the other layout. */
    static void switchLayout(MinecraftClient client, Screen parent, boolean glass) {
        Module visuals = KhanhClient.modules().byId("visuals");
        ModuleOption layout = visuals == null ? null : visuals.option("gui_layout");
        if (layout != null) layout.setChoice(glass ? GLASS : CLASSIC);
        ConfigManager.save(KhanhClient.modules());
        KhanhClickGuiScreen.playUiSound(client, 1.12F);
        client.setScreen(glass ? new LiquidGlassScreen(parent) : new KhanhClickGuiScreen(parent));
    }
}
