package dev.khanh.feature;

import dev.khanh.gui.SmoothGuiShapes;
import java.util.Comparator;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

/** Local-only HUD markers for loaded players outside the camera's central view. */
public final class OffscreenPlayerPointers {
    private OffscreenPlayerPointers() { }

    public static void render(DrawContext context, RenderTickCounter ignored) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!KhanhFeatures.enabled("offscreen_players") || client.world == null || client.player == null
            || client.options.hudHidden || client.currentScreen != null && !(client.currentScreen instanceof ChatScreen)) return;

        double range = KhanhFeatures.decimal("offscreen_players", "range", 96);
        double maxDistanceSquared = range * range;
        int maxPlayers = KhanhFeatures.number("offscreen_players", "limit", 12);
        int centerX = context.getScaledWindowWidth() / 2;
        int centerY = context.getScaledWindowHeight() / 2;
        int radius = Math.max(18, (int) (Math.min(centerX, centerY)
            * KhanhFeatures.decimal("offscreen_players", "radius", .33) * 2));
        int color = KhanhFeatures.color("offscreen_players", "color", 0xFFCBB9FF);

        client.world.getPlayers().stream()
            .filter(player -> player != client.player && !player.isRemoved()
                && player.squaredDistanceTo(client.player) <= maxDistanceSquared)
            .sorted(Comparator.comparingDouble(player -> player.squaredDistanceTo(client.player)))
            .limit(maxPlayers)
            .forEach(player -> draw(context, client, player, centerX, centerY, radius, color));
    }

    private static void draw(DrawContext context, MinecraftClient client, AbstractClientPlayerEntity player,
                             int centerX, int centerY, int radius, int color) {
        double dx = player.getX() - client.player.getX();
        double dz = player.getZ() - client.player.getZ();
        double bearing = Math.atan2(-dx, dz);
        double relative = MathHelper.wrapDegrees(Math.toDegrees(bearing) - client.player.getYaw());
        if (Math.abs(relative) < 55) return;

        double radians = Math.toRadians(relative);
        int x = centerX + (int) Math.round(Math.sin(radians) * radius);
        int y = centerY - (int) Math.round(Math.cos(radians) * radius);
        context.getMatrices().pushMatrix();
        try {
            context.getMatrices().translate(x, y);
            context.getMatrices().rotate((float) radians);
            SmoothGuiShapes.rect(context, -2, -9, 4, 12, 2, 0xD5080A0D, false);
            SmoothGuiShapes.rect(context, -1, -8, 2, 9, 1, color, false);
            context.getMatrices().pushMatrix();
            context.getMatrices().translate(-1, -6);
            context.getMatrices().rotate(-.66F);
            SmoothGuiShapes.rect(context, -1, -1, 2, 7, 1, color, false);
            context.getMatrices().popMatrix();
            context.getMatrices().pushMatrix();
            context.getMatrices().translate(1, -6);
            context.getMatrices().rotate(.66F);
            SmoothGuiShapes.rect(context, -1, -1, 2, 7, 1, color, false);
            context.getMatrices().popMatrix();
        } finally {
            context.getMatrices().popMatrix();
        }
        if (KhanhFeatures.bool("offscreen_players", "distance", true)) {
            int distance = (int) Math.round(Math.hypot(dx, dz));
            String label = distance + "m";
            int labelX = x + (x < centerX ? 8 : -client.textRenderer.getWidth(label) - 8);
            context.drawText(client.textRenderer, Text.literal(label), labelX, y - 4, 0xFFE8E7ED, true);
        }
    }
}
