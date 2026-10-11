package dev.khanh.feature;

import dev.khanh.gui.KhanhClickGuiScreen;
import dev.khanh.gui.SmoothGuiShapes;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.Text;

/** Cluster Counter's on-screen pill: amethyst clusters and geodes in loaded chunks. */
public final class ClusterCounterHud {
    private ClusterCounterHud() { }

    public static void render(DrawContext context, RenderTickCounter counter) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!KhanhFeatures.enabled("cluster_counter") || !KhanhFeatures.bool("cluster_counter", "hud", true)
            || client.player == null || client.options.hudHidden || client.currentScreen != null) return;
        WorldScanner scanner = KhanhFeatures.scanner();
        String line = "Clusters " + scanner.amethystClusterCount();
        if (KhanhFeatures.bool("cluster_counter", "geodes", true)) line += "   Geodes " + scanner.geodeCenters().size();
        Text text = KhanhClickGuiScreen.fontText(line);
        int w = client.textRenderer.getWidth(text) + 22, h = 16;
        int x = 6, y = context.getScaledWindowHeight() / 2 - h / 2;
        SmoothGuiShapes.rect(context, x, y, w, h, 6, 0xC00B0B0F, false);
        SmoothGuiShapes.rect(context, x + 6, y + 5, 6, 6, 3, 0xFFC084FC, false);
        context.drawText(client.textRenderer, text, x + 16, y + 4, 0xFFFFFFFF, false);
    }
}
