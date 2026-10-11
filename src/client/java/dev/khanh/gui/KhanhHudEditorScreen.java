package dev.khanh.gui;

import dev.khanh.KhanhClient;
import dev.khanh.config.ConfigManager;
import dev.khanh.feature.KhanhFeatures;
import dev.khanh.feature.KhanhHud;
import dev.khanh.feature.KhanhHudRenderer;
import dev.khanh.feature.KhanhSounds;
import dev.khanh.feature.VisualTheme;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/**
 * HUD editor: the world stays visible and every HUD piece can be dragged to a new spot, scrolled
 * to resize and right clicked to reset. Opened from the menu's CLIENT panel; Esc goes back.
 */
public final class KhanhHudEditorScreen extends Screen {
    private final Screen parent;

    public KhanhHudEditorScreen(Screen parent) {
        super(Text.literal("HUD Editor"));
        this.parent = parent;
    }

    /** True while HUD pieces should be movable: in this editor or with chat open. */
    public static boolean editing(MinecraftClient client) {
        return client.currentScreen instanceof KhanhHudEditorScreen || client.currentScreen instanceof ChatScreen;
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) { }

    @Override
    public void render(DrawContext c, int mouseX, int mouseY, float delta) {
        super.render(c, mouseX, mouseY, delta);
        // The move / resize / reset hints and the Done bar come from the HUD itself while editing.
        if (KhanhFeatures.enabled("hud")) return;
        Text text = KhanhColumnsScreen.small("TURN ON HUD IN THE CLIENT PANEL TO MOVE THE WATERMARK, MODULES, COORDS AND ARMOR");
        int w = textRenderer.getWidth(text) + 12;
        int x = width / 2 - w / 2, y = height / 2 - 40;
        c.fill(x, y, x + w, y + 13, 0xE0101014);
        c.fill(x, y, x + 2, y + 13, VisualTheme.preset());
        c.drawText(textRenderer, text, x + 7, y + 3, 0xFFE6E6EA, false);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (KhanhHud.mouseClicked(click.x(), click.y(), click.button())) return true;
        if (KhanhHudRenderer.mouseClicked(click.x(), click.y(), click.button())) return true;
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseDragged(Click click, double deltaX, double deltaY) {
        return KhanhHud.mouseDragged(click.x(), click.y())
            || KhanhHudRenderer.mouseDragged(click.x(), click.y())
            || super.mouseDragged(click, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(Click click) {
        boolean hud = KhanhHud.mouseReleased();
        boolean widgets = KhanhHudRenderer.mouseReleased();
        return hud || widgets || super.mouseReleased(click);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        return KhanhHud.mouseScrolled(mouseX, mouseY, vertical)
            || KhanhHudRenderer.mouseScrolled(mouseX, mouseY, vertical)
            || super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }

    @Override
    protected void init() {
        KhanhSounds.playInterface(client, 1.2F);
    }

    @Override
    public void close() {
        ConfigManager.save(KhanhClient.modules());
        KhanhSounds.playInterface(client, 0.85F);
        client.setScreen(parent);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
