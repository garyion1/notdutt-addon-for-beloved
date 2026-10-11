package me.tpaburst.ui;

import meteordevelopment.meteorclient.MeteorClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.toast.Toast;
import net.minecraft.client.toast.ToastManager;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.MathHelper;

public final class SpawnerNotifierToast implements Toast {
    private static final ItemStack ICON = Items.SPAWNER.getDefaultStack();
    private static final int WIDTH = 170;
    private static final int HEIGHT = 34;
    private static final int TEXT_X = 30;

    private final String mobType;
    private final String subtitle;
    private final String coords;
    private final SoundInstance sound;
    private final long duration;
    private final int accentColor;
    private long start = -1L;
    private boolean playedSound;
    private Toast.Visibility visibility = Toast.Visibility.HIDE;

    public SpawnerNotifierToast(String mobType, int x, int y, int z, long durationMs, boolean playSound, int progressBarRgb) {
        this.duration = Math.max(2500L, durationMs);
        this.mobType = mobType;
        this.accentColor = Theme.rgb(progressBarRgb);
        this.subtitle = "Spawner " + SpawnerAlerts.depthLabel(y);
        this.coords = x + ", " + y + ", " + z;
        this.sound = playSound ? PositionedSoundInstance.master(SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), 1.0F, 1.0F) : null;
    }

    @Override
    public int getWidth() {
        return WIDTH;
    }

    @Override
    public int getHeight() {
        return HEIGHT;
    }

    @Override
    public Toast.Visibility getVisibility() {
        return visibility;
    }

    @Override
    public void update(ToastManager manager, long time) {
        if (start == -1L) start = time;
        visibility = time - start >= duration ? Toast.Visibility.HIDE : Toast.Visibility.SHOW;
        if (!playedSound && sound != null && MeteorClient.mc.getSoundManager() != null) {
            MeteorClient.mc.getSoundManager().play(sound);
            playedSound = true;
        }
    }

    @Override
    public void draw(DrawContext context, TextRenderer textRenderer, long time) {
        if (start < 0L) return;
        float left = 1.0F - MathHelper.clamp((float) (time - start) / (float) duration, 0.0F, 1.0F);

        Theme.panel(context, 0, 0, WIDTH, HEIGHT, Theme.PANEL, Theme.BORDER);
        context.fill(1, 2, 3, HEIGHT - 2, accentColor);

        Theme.softRect(context, 7, 7, 20, 20, Theme.SURFACE);
        context.drawItem(ICON, 9, 9);

        int textMax = WIDTH - TEXT_X - 6;
        context.drawText(textRenderer, textRenderer.trimToWidth(mobType + " Spawner", textMax), TEXT_X, 7, Theme.TEXT, false);

        String sub = subtitle + "  ";
        context.drawText(textRenderer, sub, TEXT_X, 19, Theme.TEXT_MUTED, false);
        int cx = TEXT_X + textRenderer.getWidth(sub);
        context.drawText(textRenderer, textRenderer.trimToWidth(coords, Math.max(0, WIDTH - 6 - cx)), cx, 19, Theme.TEXT_SECONDARY, false);

        int barLeft = 4;
        int barRight = WIDTH - 2;
        int fill = Math.max(0, (int) ((barRight - barLeft) * left));
        context.fill(barLeft, HEIGHT - 3, barRight, HEIGHT - 2, 0xFF22252D);
        if (fill > 0) context.fill(barLeft, HEIGHT - 3, barLeft + fill, HEIGHT - 2, accentColor);
    }
}
