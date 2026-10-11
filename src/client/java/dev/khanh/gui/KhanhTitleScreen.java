package dev.khanh.gui;

import dev.khanh.KhanhClient;
import dev.khanh.feature.IntroOverlay;
import dev.khanh.feature.KhanhFeatures;
import dev.khanh.feature.VisualTheme;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerWarningScreen;
import net.minecraft.client.gui.screen.option.OptionsScreen;
import net.minecraft.client.gui.screen.world.SelectWorldScreen;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.option.ServerList;
import net.minecraft.client.realms.gui.screen.RealmsMainScreen;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.joml.Matrix3x2fStack;

/**
 * Khanh Client's main menu, shot like a film title: deep space with drifting dust in two depths,
 * light beams sweeping behind the Kh logo, a horizon glow and letterbox bars. The menu is a column
 * of big numbered lines that fly in one after another and slide out with an accent bar on hover;
 * your first servers sit underneath for one-click joining. The launch intro plays on top.
 */
public final class KhanhTitleScreen extends Screen {
    private record Item(String id, String label, int x, int y, int w, int h, boolean small, Runnable action) {
        boolean contains(double px, double py) { return px >= x && px < x + w && py >= y && py < y + h; }
    }

    private static final Identifier LOGO = Identifier.of(KhanhClient.MOD_ID, "textures/gui/khanh-logo.png");
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm");
    private static final String[] TAGLINES = {"Locked in.", "No lag. All aura.", "Built different.", "Main character energy.", "Clean frames, no cap."};
    private static final float[][] DUST = new float[140][5];

    static {
        Random random = new Random(7);
        for (float[] dust : DUST) {
            dust[0] = random.nextFloat(); dust[1] = random.nextFloat();
            dust[2] = random.nextFloat();              // depth: 0 far, 1 near
            dust[3] = random.nextFloat() * 6.28F;      // twinkle phase
            dust[4] = random.nextFloat();
        }
    }

    private final List<Item> items = new ArrayList<>();
    private final Map<String, Spring> hover = new HashMap<>();
    private long openedAt;
    private String hovered = "";
    private long flashAt;
    private final String tagline = TAGLINES[new Random().nextInt(TAGLINES.length)];

    public KhanhTitleScreen() {
        super(Text.literal("Khanh Client"));
    }

    @Override
    protected void init() {
        if (openedAt == 0L) openedAt = System.nanoTime();
        IntroOverlay.wantsTitleScreen();
        items.clear();
        int left = Math.max(28, width / 10);
        int top = Math.max(118, height / 2 - 30);
        int lineH = 22;
        String[][] menu = {{"single", "SINGLEPLAYER"}, {"multi", "MULTIPLAYER"}, {"realms", "REALMS"}, {"options", "OPTIONS"}, {"quit", "QUIT"}};
        Runnable[] actions = {
            () -> client.setScreen(new SelectWorldScreen(this)),
            () -> client.setScreen(client.options.skipMultiplayerWarning ? new MultiplayerScreen(this) : new MultiplayerWarningScreen(this)),
            () -> client.setScreen(new RealmsMainScreen(this)),
            () -> client.setScreen(new OptionsScreen(this, client.options)),
            () -> client.scheduleStop()
        };
        for (int i = 0; i < menu.length; i++) {
            items.add(new Item(menu[i][0], menu[i][1], left, top + i * lineH, 220, lineH - 2, false, actions[i]));
        }
        // Quick join: your first saved servers.
        ServerList list = new ServerList(client);
        list.loadFile();
        int quickY = top + menu.length * lineH + 22;
        for (int i = 0; i < Math.min(3, list.size()) && quickY + 14 + i * 14 < height - 30; i++) {
            ServerInfo info = list.get(i);
            String name = info.name == null || info.name.isBlank() ? info.address : info.name;
            items.add(new Item("server:" + i, name, left, quickY + 12 + i * 14, 200, 12, true,
                () -> ConnectScreen.connect(this, client, ServerAddress.parse(info.address), info, false, null)));
        }
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) { }

    @Override
    public void render(DrawContext c, int mouseX, int mouseY, float delta) {
        float t = (System.nanoTime() - openedAt) / 1.0e9F;
        int accent = VisualTheme.preset() & 0xFFFFFF;
        Matrix3x2fStack m = c.getMatrices();

        // Deep space with a horizon glow.
        c.fillGradient(0, 0, width, height, 0xFF04050A, 0xFF0A0D16);
        c.fillGradient(0, height * 3 / 5, width, height, 0, 0x30000000 | accent);
        int logoX = Math.round(width * 0.68F), logoY = Math.round(height * 0.45F);
        drawBeams(c, logoX, logoY, t, accent);
        drawDust(c, t, accent, mouseX, mouseY);

        // The big Kh logo, floating, with a soft halo.
        float logoSize = Math.min(width, height) * 0.34F;
        float float_ = (float) Math.sin(t * 0.9) * 4.0F;
        for (int g = 5; g >= 1; g--) {
            SmoothGuiShapes.circle(c, logoX, Math.round(logoY + float_), Math.round(logoSize * (0.32F + g * 0.09F)), (6 + g) << 24 | accent);
        }
        for (int p = 0; p < 14; p++) {
            double angle = t * (0.5 + (p % 3) * 0.15) + p * (Math.PI * 2 / 14);
            float orbit = logoSize * (0.55F + (p % 4) * 0.05F);
            int ox = Math.round(logoX + (float) Math.cos(angle) * orbit), oy = Math.round(logoY + float_ + (float) Math.sin(angle) * orbit * 0.38F);
            SmoothGuiShapes.circle(c, ox, oy, p % 4 == 0 ? 2 : 1, alpha(0.35F + 0.35F * (float) Math.sin(t * 2 + p)) << 24 | (p % 2 == 0 ? accent : 0xFFFFFF));
        }
        float breathe = 1.0F + 0.025F * (float) Math.sin(t * 1.6);
        m.pushMatrix();
        m.translate(logoX, logoY + float_);
        m.rotate((float) Math.toRadians(Math.sin(t * 0.7) * 2.5));
        m.scale(logoSize * breathe / 64.0F, logoSize * breathe / 64.0F);
        c.drawTexture(RenderPipelines.GUI_TEXTURED, LOGO, -32, -32, 0F, 0F, 64, 64, 64, 64, 64, 64, alpha(ease(clamp(t / 1.2F))) << 24 | 0xFFFFFF);
        m.popMatrix();

        // Wordmark and tagline over the menu.
        int left = items.isEmpty() ? 28 : items.get(0).x();
        int titleY = (items.isEmpty() ? height / 2 : items.get(0).y()) - 70;
        float titleIn = ease(clamp((t - 0.1F) / 0.8F));
        text(c, "KHANH", left - Math.round((1 - titleIn) * 30), titleY, 3.0F, alpha(titleIn) << 24 | 0xFFFFFF);
        text(c, "CLIENT", left + width("KHANH", 3.0F) + 8 - Math.round((1 - titleIn) * 30), titleY, 3.0F, alpha(titleIn) << 24 | accent);
        text(c, tagline.toUpperCase(), left, titleY + 34, 0.72F, alpha(titleIn * 0.55F) << 24 | 0xFFFFFF);
        c.fill(left, titleY + 48, left + Math.round(160 * titleIn), titleY + 49, alpha(titleIn * 0.7F) << 24 | accent);

        // Menu lines and quick join.
        String now = "";
        boolean quickHeader = false;
        for (int i = 0; i < items.size(); i++) {
            Item item = items.get(i);
            boolean over = item.contains(mouseX, mouseY);
            if (over) now = item.id();
            float h = hover.computeIfAbsent(item.id(), k -> new Spring(Spring.SMOOTH, 0.0F)).to(over ? 1.0F : 0.0F).value();
            float in = ease(clamp((t - 0.35F - i * 0.07F) / 0.5F));
            int x = item.x() - Math.round((1 - in) * 40) + Math.round(h * 10);
            if (item.small()) {
                if (!quickHeader) {
                    quickHeader = true;
                    text(c, "QUICK JOIN", item.x(), item.y() - 13, 0.6F, alpha(in * 0.45F) << 24 | accent);
                }
                c.fill(x - 6, item.y() + 3, x - 4, item.y() + 9, alpha(in * (0.3F + 0.7F * h)) << 24 | accent);
                text(c, item.label(), x, item.y() + 2, 0.72F, alpha(in * (0.55F + 0.45F * h)) << 24 | 0xFFFFFF);
            } else {
                if (h > 0.02F) {
                    int bw = width(item.label(), 1.35F) + 40;
                    SmoothGuiShapes.rect(c, x + 8, item.y() - 1, bw, item.h() + 2, 9, alpha(0.14F * h * in) << 24 | accent, false);
                    float sweep = (float) ((t * 1.3) % 1.0);
                    int sx = x + 8 + Math.round(bw * sweep);
                    c.fill(sx, item.y() + 1, sx + 2, item.y() + item.h() - 1, alpha(0.35F * h * in) << 24 | 0xFFFFFF);
                }
                float shimmer = (float) (((t * 0.35) - i * 0.12) % 1.6);
                if (shimmer > 0 && shimmer < 0.25F) {
                    float k = shimmer / 0.25F;
                    c.fill(x + 18, item.y() + item.h() - 2, x + 18 + Math.round(width(item.label(), 1.35F) * k), item.y() + item.h() - 1,
                        alpha(0.5F * (1 - k) * in) << 24 | accent);
                }
                String number = String.format("%02d", i + 1);
                text(c, number, x, item.y() + 6, 0.62F, alpha(in * 0.6F) << 24 | accent);
                int bar = Math.round(h * 24);
                if (bar > 0) c.fill(x + 16, item.y() + item.h() - 4, x + 16 + bar, item.y() + item.h() - 3, alpha(in) << 24 | accent);
                int colour = item.id().equals("quit") ? 0xFF8A8A : 0xFFFFFF;
                text(c, item.label(), x + 18, item.y() + 2, 1.35F, alpha(in * (0.62F + 0.38F * h)) << 24 | (h > 0.5F ? accent : colour));
            }
        }
        if (!now.isEmpty() && !now.equals(hovered)) play("ui_hover", 1.0F, 0.10F);
        hovered = now;

        // Letterbox bars and the footer line.
        int bar = 18;
        c.fill(0, 0, width, bar, 0xF0000000);
        c.fill(0, height - bar, width, height, 0xF0000000);
        String user = client.getSession() == null ? "" : client.getSession().getUsername();
        text(c, "KHANH CLIENT " + KhanhClient.VERSION + "  ·  MINECRAFT 1.21.11", 10, height - bar / 2 - 3, 0.6F, 0x66FFFFFF);
        String right = LocalTime.now().format(CLOCK) + "  ·  " + user;
        text(c, right, width - 10 - width(right, 0.6F), bar / 2 - 3, 0.6F, 0x88FFFFFF);
        String legal = "Copyright Mojang AB. Do not distribute!";
        text(c, legal, width - 10 - width(legal, 0.55F), height - bar / 2 - 3, 0.55F, 0x55FFFFFF);
        // Click flash.
        float flash = 1.0F - clamp((System.nanoTime() - flashAt) / 2.5e8F);
        if (flashAt != 0 && flash > 0) c.fill(0, 0, width, height, alpha(0.18F * flash) << 24 | accent);
        // Opening fade from black.
        float fadeIn = 1.0F - clamp(t / 0.8F);
        if (fadeIn > 0) c.fill(0, 0, width, height, alpha(fadeIn) << 24);

        IntroOverlay.render(c);
    }

    /** Slow light beams fanning out from behind the logo. */
    private void drawBeams(DrawContext c, int x, int y, float t, int accent) {
        Matrix3x2fStack m = c.getMatrices();
        for (int i = 0; i < 9; i++) {
            m.pushMatrix();
            m.translate(x, y);
            m.rotate((float) Math.toRadians(i * 40.0 + t * 4.0 + Math.sin(t * 0.3 + i) * 6.0));
            int length = Math.round(Math.max(width, height) * 1.1F);
            int w = 14 + (i % 3) * 12;
            c.fillGradient(-w / 2, -length, w / 2, 0, 0, (10 + (i % 2) * 6) << 24 | accent);
            m.popMatrix();
        }
    }

    /** Dust drifting in two depths; the near layer leans a little toward the mouse (parallax). */
    private void drawDust(DrawContext c, float t, int accent, int mouseX, int mouseY) {
        float px = (mouseX - width / 2.0F) / width, py = (mouseY - height / 2.0F) / height;
        for (float[] dust : DUST) {
            float depth = dust[2];
            float x = dust[0] * width + (float) Math.sin(t * 0.25 + dust[3]) * 10 - px * 18 * depth;
            float y = (((dust[1] - t * (0.006F + 0.02F * depth)) % 1.0F) + 1.0F) % 1.0F * height - py * 12 * depth;
            float twinkle = 0.35F + 0.65F * (float) Math.abs(Math.sin(t * (0.8 + dust[4]) + dust[3]));
            int size = depth > 0.8F ? 2 : 1;
            int colour = dust[4] > 0.75F ? accent : 0xFFFFFF;
            SmoothGuiShapes.circle(c, Math.round(x), Math.round(y), size, alpha(twinkle * (0.25F + 0.5F * depth)) << 24 | colour);
        }
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (IntroOverlay.wantsTitleScreen() && IntroPlaying.active()) return true;
        for (Item item : items) {
            if (item.contains(click.x(), click.y())) {
                play(item.id().equals("quit") ? "ui_back" : "ui_select", 1.0F, 0.24F);
                flashAt = System.nanoTime();
                item.action().run();
                return true;
            }
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    private void play(String sound, float pitch, float volume) {
        if (KhanhFeatures.enabled("client_sounds") && KhanhFeatures.bool("client_sounds", "muted", false)) return;
        client.getSoundManager().play(PositionedSoundInstance.ui(SoundEvent.of(Identifier.of(KhanhClient.MOD_ID, sound)), pitch, volume));
    }

    private void text(DrawContext c, String value, int x, int y, float scale, int colour) {
        Matrix3x2fStack m = c.getMatrices();
        m.pushMatrix();
        m.translate(x, y);
        m.scale(scale, scale);
        c.drawText(textRenderer, KhanhClickGuiScreen.fontText(value), 0, 0, colour, false);
        m.popMatrix();
    }

    private int width(String value, float scale) {
        return Math.round(textRenderer.getWidth(KhanhClickGuiScreen.fontText(value)) * scale);
    }

    private static int alpha(float value) {
        return Math.round(clamp(value) * 255.0F);
    }

    private static float clamp(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }

    private static float ease(float x) {
        return 1.0F - (float) Math.pow(1.0F - x, 3.0);
    }

    /** Whether the intro is still covering the menu (clicks then belong to the intro's skip). */
    private static final class IntroPlaying {
        static boolean active() {
            return IntroOverlay.playing();
        }
    }
}
