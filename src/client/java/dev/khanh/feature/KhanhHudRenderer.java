package dev.khanh.feature;

import dev.khanh.KhanhClient;
import dev.khanh.config.ConfigManager;
import dev.khanh.gui.HudLayout;
import dev.khanh.gui.HudSurfaceRenderer;
import dev.khanh.gui.HudSurfaceRenderer.HudIcon;
import dev.khanh.gui.GlassGuiRenderer;
import dev.khanh.gui.LiquidGlass;
import dev.khanh.gui.SmoothGuiShapes;
import dev.khanh.gui.KhanhClickGuiScreen;
import dev.khanh.gui.Spring;
import dev.khanh.gui.KhanhPalette;
import org.lwjgl.glfw.GLFW;
import dev.khanh.module.Category;
import dev.khanh.module.Module;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.IntSupplier;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

public final class KhanhHudRenderer {
    private static final Identifier ZERO_LOGO = Identifier.of(KhanhClient.MOD_ID, "textures/gui/khanh-logo.png");
    private static final int RTP_MAP_SIZE = 9;
    private static final double RTP_CELL_WORLD_SIZE = 50_000.0;
    private static final double RTP_MAP_OFFSET = 225_000.0;
    private static final int[] RTP_REGION_LAYOUT = {
        82, 100, 101, 102, 103, 104, 105, 106, 91,
        83, 44, 75, 42, 41, 40, 39, 38, 92,
        84, 45, 14, 13, 12, 11, 10, 37, 93,
        85, 46, 74, 3, 2, 1, 25, 36, 94,
        86, 47, 72, 71, 5, 4, 24, 35, 95,
        87, 51, 17, 9, 8, 7, 23, 34, 96,
        88, 54, 18, 61, 62, 21, 22, 33, 97,
        89, 26, 27, 28, 29, 30, 59, 32, 98,
        90, 107, 108, 109, 110, 111, 112, 113, 99
    };
    // col, row, type: 0 glitched, 1 media/baltop, 2 media/bases.
    private static final int[][] RTP_SPOT_CELLS = {
        {0, 8, 0}, {0, 7, 0}, {2, 0, 0}, {4, 0, 0}, {5, 0, 0}, {8, 6, 0},
        {4, 4, 1}, {5, 4, 1}, {4, 5, 1}, {5, 7, 1},
        {0, 4, 2}, {0, 6, 2}, {4, 3, 2}, {5, 5, 2}, {7, 5, 2}, {3, 7, 2},
        {4, 7, 2}, {6, 7, 2}, {7, 7, 2}, {8, 7, 2}, {3, 8, 2}, {6, 8, 2},
        {7, 8, 2}, {8, 8, 2}
    };
    private static final int[] RTP_SPOT_COLORS = {0xFFBA6EFF, 0xFF408CFF, 0xFFB0BCD0};
    private static final String[] RTP_SPOT_LABELS = {
        "Glitched (no rtp spots)", "Good for media / baltop", "Good for media / other bases"
    };
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter SHORT_CLOCK = DateTimeFormatter.ofPattern("HH:mm");
    private static final int GRID = 8;
    private static boolean snapToGrid = true;
    private static final Rect[] TOOLBAR = new Rect[3];
    private static final Map<HudLayout.Widget, Rect> BOUNDS = new EnumMap<>(HudLayout.Widget.class);
    /** Last frame's widget sizes, for anchoring saved positions before this frame measures them. */
    private static final Map<HudLayout.Widget, Rect> LAST_BOUNDS = new EnumMap<>(HudLayout.Widget.class);
    private static HudLayout.Widget dragging;
    private static double dragOffsetX;
    private static double dragOffsetY;
    private static int lastHudWidth;
    private static int lastHudHeight;
    private static float smoothYaw;
    private static List<Module> cachedActiveModules = List.of();
    private static long activeModuleCacheAt;
    private static final Map<String, Spring> MODULE_ROWS = new HashMap<>();
    private static final Map<String, Spring> POPS = new HashMap<>();
    private static final Map<String, Integer> POP_COLORS = new HashMap<>();
    private static final Spring HEALTH = new Spring(Spring.PANEL, 1.0F);

    private KhanhHudRenderer() {
    }

    public static void renderStandaloneCrosshair(DrawContext context, RenderTickCounter tickCounter) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!KhanhFeatures.enabled("custom_crosshair")
            || client.world == null || client.player == null || client.currentScreen != null) return;
        renderCrosshair(context, context.getScaledWindowWidth() / 2, context.getScaledWindowHeight() / 2);
    }

    private static boolean healthWasLow;
    private static long healthSoundAt;

    /**
     * Low health warning: a glowing red frame that blinks (or pulses) faster the lower you are,
     * a big LOW HP readout, a ping you can hear and a one-off message when you first drop low.
     */
    public static void renderHealthAlert(DrawContext context, RenderTickCounter tickCounter) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!KhanhFeatures.enabled("health_alert") || client.player == null || client.world == null
            || client.player.isDead() || client.player.isCreative() || client.player.isSpectator()) {
            healthWasLow = false;
            return;
        }
        float health = client.player.getHealth()
            + (KhanhFeatures.bool("health_alert", "count_absorption", false) ? client.player.getAbsorptionAmount() : 0.0F);
        float threshold = (float) KhanhFeatures.decimal("health_alert", "threshold", 8.0);
        boolean low = health <= threshold;
        long now = System.currentTimeMillis();
        if (!low) {
            healthWasLow = false;
            return;
        }
        float danger = MathHelper.clamp(1.0F - health / Math.max(1.0F, threshold), 0.0F, 1.0F);
        if (!healthWasLow) {
            healthWasLow = true;
            healthSoundAt = 0L;
            if (KhanhFeatures.bool("health_alert", "message", true)) {
                client.inGameHud.setOverlayMessage(Text.literal("\u26A0 Low HP: " + hearts(health) + " \u2764").withColor(0xFF5555), false);
            }
        }
        if (KhanhFeatures.bool("health_alert", "sound", true)) {
            long every = Math.round(KhanhFeatures.decimal("health_alert", "sound_every", 1.5) * 1000.0 * (1.0 - 0.5 * danger));
            if (now - healthSoundAt >= every) {
                healthSoundAt = now;
                KhanhSounds.alert(client, 0.8F + 0.6F * danger);
            }
        }
        double speed = KhanhFeatures.decimal("health_alert", "blink_speed", 1.0) * (1.0 + 1.5 * danger);
        double phase = now / 1000.0 * speed * Math.PI * 2.0;
        String style = KhanhFeatures.choice("health_alert", "style", "Blink");
        double wave = switch (style) {
            case "Solid" -> 1.0;
            case "Pulse" -> 0.55 + 0.45 * Math.sin(phase);
            default -> Math.sin(phase) >= -0.2 ? 1.0 : 0.12;       // Blink: on / nearly off
        };
        int rgb = KhanhFeatures.color("health_alert", "color", 0xFF2A3D) & 0xFFFFFF;
        int opacity = KhanhFeatures.number("health_alert", "opacity", 65);
        int peak = MathHelper.clamp((int) Math.round(opacity * 2.6 * wave * (0.7 + 0.3 * danger)), 0, 235);
        int width = context.getScaledWindowWidth();
        int height = context.getScaledWindowHeight();
        int size = Math.max(4, Math.min(Math.min(width, height) / 3, KhanhFeatures.number("health_alert", "size", 48)));
        for (int k = 0; k < size; k++) {
            float t = 1.0F - (float) k / size;
            int color = (Math.max(0, Math.round(peak * (float) Math.pow(t, 1.4))) << 24) | rgb;
            if ((color >>> 24) == 0) continue;
            context.fill(k, k, width - k, k + 1, color);
            context.fill(k, height - k - 1, width - k, height - k, color);
            context.fill(k, k + 1, k + 1, height - k - 1, color);
            context.fill(width - k - 1, k + 1, width - k, height - k - 1, color);
        }
        if (KhanhFeatures.bool("health_alert", "show_text", true) && client.currentScreen == null) {
            Text label = Text.literal("LOW HP  " + hearts(health) + " \u2764");
            float scale = (float) KhanhFeatures.decimal("health_alert", "text_size", 1.6);
            int textAlpha = MathHelper.clamp((int) Math.round(255 * (0.45 + 0.55 * wave)), 40, 255);
            int textWidth = renderer().getWidth(label);
            context.getMatrices().pushMatrix();
            context.getMatrices().translate(width / 2.0F, height * 0.30F);
            context.getMatrices().scale(scale, scale);
            context.drawText(renderer(), label, -textWidth / 2, -4, (textAlpha << 24) | rgb, true);
            context.getMatrices().popMatrix();
        }
    }

    private static String hearts(float health) {
        float hearts = health / 2.0F;
        return Math.abs(hearts - Math.round(hearts)) < 0.05F ? String.valueOf(Math.round(hearts)) : String.format(java.util.Locale.ROOT, "%.1f", hearts);
    }

    /** Watermark, FPS, coords, module list and armor are drawn by Khanh's own HUD ({@link KhanhHud}). */
    private static final boolean LEGACY_HUD = false;

    public static void render(DrawContext context, RenderTickCounter tickCounter) {
        MinecraftClient client = MinecraftClient.getInstance();
        boolean editing = dev.khanh.gui.KhanhHudEditorScreen.editing(client);
        if (client.player == null || client.world == null || (client.currentScreen != null && !editing)) {
            return;
        }
        // The HUD module owns the watermark, stats, coordinates, module list and armour. Widgets
        // with their own module (radar, Spotify, target, staff, friends, RTP map) show whenever
        // that module is on, so turning the HUD off never makes them vanish.
        boolean hud = KhanhFeatures.enabled("hud");

        GlassGuiRenderer.beginHud();
        float scale = hudScale();
        int hudWidth = Math.round(context.getScaledWindowWidth() / scale);
        int hudHeight = Math.round(context.getScaledWindowHeight() / scale);
        lastHudWidth = hudWidth;
        lastHudHeight = hudHeight;
        LAST_BOUNDS.putAll(BOUNDS);
        BOUNDS.clear();
        java.util.Arrays.fill(SPOTIFY_BUTTONS, null);
        context.getMatrices().pushMatrix();
        context.getMatrices().scale(scale, scale);
        if (editing) renderEditorGrid(context, hudWidth, hudHeight);

        // The HUD itself is the watermark, player radar, active modules and armor; the
        // staff list and the widgets below belong to their own modules.
        if (LEGACY_HUD && hud && KhanhFeatures.bool("hud", "watermark", true)) {
            Point point = point(HudLayout.Widget.WATERMARK, 9, 9);
            renderScaled(context, HudLayout.Widget.WATERMARK, point.x(), point.y(),
                () -> renderWatermark(context, point.x(), point.y(), editing));
        }
        int leftY = 38;
        if (LEGACY_HUD && hud && KhanhFeatures.bool("hud", "fps", true)) {
            Point point = point(HudLayout.Widget.FPS, 9, leftY);
            renderScaled(context, HudLayout.Widget.FPS, point.x(), point.y(),
                () -> renderMetricPill(context, HudLayout.Widget.FPS, HudIcon.SPEED,
                    client.getCurrentFps() + " FPS" + (client.isInSingleplayer() || client.getNetworkHandler() == null
                        ? "" : "  \u00B7  " + playerPing(client) + " ms"), point.x(), point.y(), editing, accent()));
            if (!HudLayout.widget(HudLayout.Widget.FPS).positioned) leftY += 29;
        }
        if (LEGACY_HUD && hud && KhanhFeatures.bool("hud", "coords", true)) {
            Point point = point(HudLayout.Widget.COORDS, 9, leftY);
            renderScaled(context, HudLayout.Widget.COORDS, point.x(), point.y(),
                () -> renderCoords(context, client, point.x(), point.y(), editing));
            if (!HudLayout.widget(HudLayout.Widget.COORDS).positioned) leftY += 29;
        }
        boolean dockedRadar = false;   // Khanh's own HUD draws the radar
        if (dockedRadar) {
            Point point = point(HudLayout.Widget.PLAYER_RADAR, 9, leftY);
            renderScaled(context, HudLayout.Widget.PLAYER_RADAR, point.x(), point.y(),
                () -> renderPlayerRadar(context, client, point.x(), point.y(), editing));
            Rect radar = BOUNDS.get(HudLayout.Widget.PLAYER_RADAR);
            if (radar != null && !HudLayout.widget(HudLayout.Widget.PLAYER_RADAR).positioned) {
                leftY = radar.y() + radar.height() + 8;
            }
        }
        if (LEGACY_HUD && hud && KhanhFeatures.bool("hud", "modules", true)) {
            final int modulesY = leftY;
            Point point = point(HudLayout.Widget.MODULES, 9, modulesY);
            renderScaled(context, HudLayout.Widget.MODULES, point.x(), point.y(),
                () -> renderActiveModules(context, point.x(), point.y(), hudHeight, editing));
        }
        if (LEGACY_HUD && hud && KhanhFeatures.bool("hud", "armor", true)) {
            Point point = point(HudLayout.Widget.ARMOR, Math.max(9, hudWidth / 2 - 52), Math.max(9, hudHeight - 72));
            renderScaled(context, HudLayout.Widget.ARMOR, point.x(), point.y(),
                () -> renderArmorHud(context, client, point.x(), point.y(), editing));
        }
        if (hud && KhanhFeatures.bool("hud", "keystrokes", true)) {
            // Bottom-right by default, clear of chat on the left.
            Point point = point(HudLayout.Widget.KEYSTROKES, Math.max(9, hudWidth - 96), Math.max(9, hudHeight - 150));
            renderScaled(context, HudLayout.Widget.KEYSTROKES, point.x(), point.y(),
                () -> renderKeystrokes(context, client, point.x(), point.y(), editing));
        }
        int rightColumnY = 82;
        if (KhanhFeatures.enabled("rtp_map")) {
            Point point = point(HudLayout.Widget.RTP_MAP, Math.max(9,hudWidth - 258), 82);
            renderScaled(context, HudLayout.Widget.RTP_MAP, point.x(), point.y(),
                () -> renderRtpMap(context, client, point.x(), point.y(), editing));
            Rect map = BOUNDS.get(HudLayout.Widget.RTP_MAP);
            if (map != null && !HudLayout.widget(HudLayout.Widget.RTP_MAP).positioned) {
                rightColumnY = map.y() + map.height() + 8;
            }
        }
        if ((KhanhFeatures.enabled("staff_online") && KhanhFeatures.bool("staff_online", "hud", true))
            || (KhanhFeatures.enabled("admin_detector") && KhanhFeatures.bool("admin_detector", "hud", true))) {
            Point point = point(HudLayout.Widget.STAFF, hudWidth - 202, rightColumnY);
            renderScaled(context, HudLayout.Widget.STAFF, point.x(), point.y(),
                () -> renderStaffOnline(context, point.x(), point.y(), editing));
            Rect staff = BOUNDS.get(HudLayout.Widget.STAFF);
            if (staff != null && !HudLayout.widget(HudLayout.Widget.STAFF).positioned) {
                rightColumnY = staff.y() + staff.height() + 8;
            }
        }
        if (KhanhFeatures.enabled("friends") && KhanhFeatures.bool("friends", "hud", true)) {
            Point point = point(HudLayout.Widget.FRIENDS, hudWidth - 166, rightColumnY);
            renderScaled(context, HudLayout.Widget.FRIENDS, point.x(), point.y(),
                () -> renderFriends(context, point.x(), point.y(), editing));
        }
        if (KhanhFeatures.enabled("target_hud")) {
            Point point = point(HudLayout.Widget.TARGET_HUD, Math.max(9, hudWidth / 2 + 84), Math.max(90, hudHeight / 2 + 30));
            renderScaled(context, HudLayout.Widget.TARGET_HUD, point.x(), point.y(),
                () -> renderTargetHud(context, client, point.x(), point.y(), editing));
        }
        if (SpotifyService.cardWanted() && KhanhFeatures.bool("spotify_hud", "hud", true)) {
            // Default spot sits under Minecraft's potion icons in the top-right, not behind them.
            // Default spot: top-left under Khanh's tag (the module list owns the top-right).
            Point point = point(HudLayout.Widget.SPOTIFY, 6, Math.round((KhanhFeatures.bool("hud", "compass", true) ? 112 : 24) / scale));
            renderScaled(context, HudLayout.Widget.SPOTIFY, point.x(), point.y(),
                () -> renderSpotify(context, point.x(), point.y(), editing));
        }
        if (FreecamService.active() && !editing) renderFreecamPill(context, hudWidth);
        boolean detailedHud = KhanhFeatures.choice("visuals", "hud_finish", "Clean").equals("Detailed");
        if (KhanhFeatures.enabled("visuals") && detailedHud && !editing) {
            renderCinematicVisuals(context, client, hudWidth, hudHeight);
        }
        if (KhanhFeatures.enabled("visuals") && detailedHud
            && KhanhFeatures.bool("visuals", "screen_corners", false) && !editing) {
            renderCornerAccents(context, hudWidth, hudHeight);
        }
        if (editing) {
            Text hint = text("DRAG TO MOVE  •  SCROLL TO RESIZE  •  RIGHT CLICK TO RESET");
            int width = renderer().getWidth(hint) + 16;
            flatCard(context, (hudWidth - width) / 2, 7, width, 19);
            context.drawText(renderer(), hint, (hudWidth - renderer().getWidth(hint)) / 2, 13, HudSurfaceRenderer.MUTED, false);
            renderEditorToolbar(context, hudWidth, hudHeight);
        }

        context.getMatrices().popMatrix();
        GlassGuiRenderer.end();
    }

    public static boolean mouseClicked(double mouseX, double mouseY, int button) {
        double x = mouseX / hudScale();
        double y = mouseY / hudScale();
        if (button == 0 && SpotifyService.cardWanted() && KhanhFeatures.bool("spotify_hud", "hud", true)) {
            Runnable[] actions = {() -> KhanhFeatures.spotify().previous(), () -> KhanhFeatures.spotify().playPause(),
                () -> KhanhFeatures.spotify().next()};
            for (int i = 0; i < SPOTIFY_BUTTONS.length; i++) {
                if (SPOTIFY_BUTTONS[i] != null && SPOTIFY_BUTTONS[i].contains(x, y)) {
                    actions[i].run();
                    KhanhSounds.playInterface(MinecraftClient.getInstance(), i == 1 ? 1.15F : 1.0F);
                    return true;
                }
            }
        }
        if (button == 0) {
            if (TOOLBAR[0] != null && TOOLBAR[0].contains(x, y)) {
                snapToGrid = !snapToGrid;
                KhanhSounds.playInterface(MinecraftClient.getInstance(), snapToGrid ? 1.2F : 0.9F);
                return true;
            }
            if (TOOLBAR[1] != null && TOOLBAR[1].contains(x, y)) {
                for (HudLayout.Widget widget : HudLayout.Widget.values()) {
                    HudLayout.State state = HudLayout.widget(widget);
                    state.positioned = false;
                    state.scale = 1.0F;
                }
                ConfigManager.save(KhanhClient.modules());
                KhanhSounds.playInterface(MinecraftClient.getInstance(), 0.85F);
                return true;
            }
            if (TOOLBAR[2] != null && TOOLBAR[2].contains(x, y)) {
                ConfigManager.save(KhanhClient.modules());
                KhanhSounds.playInterface(MinecraftClient.getInstance(), 1.3F);
                MinecraftClient done = MinecraftClient.getInstance();
                if (done.currentScreen != null) done.currentScreen.close();
                return true;
            }
        }
        HudLayout.Widget[] widgets = HudLayout.Widget.values();
        for (int index = widgets.length - 1; index >= 0; index--) {
            HudLayout.Widget widget = widgets[index];
            Rect rect = BOUNDS.get(widget);
            if (rect == null || !rect.contains(x, y)) {
                continue;
            }
            HudLayout.State state = HudLayout.widget(widget);
            if (button == 1) {
                state.positioned = false;
                state.scale = 1.0F;
                ConfigManager.save(KhanhClient.modules());
                return true;
            }
            if (button != 0) {
                return false;
            }
            state.x = rect.x();
            state.y = rect.y();
            state.refWidth = lastHudWidth;
            state.refHeight = lastHudHeight;
            state.positioned = true;
            dragging = widget;
            dragOffsetX = x - rect.x();
            dragOffsetY = y - rect.y();
            return true;
        }
        return false;
    }

    public static boolean mouseDragged(double mouseX, double mouseY) {
        if (dragging == null) {
            return false;
        }
        Rect bounds = BOUNDS.get(dragging);
        int width = bounds == null ? 120 : bounds.width();
        int height = bounds == null ? 30 : bounds.height();
        HudLayout.State state = HudLayout.widget(dragging);
        int nextX = (int) Math.round(mouseX / hudScale() - dragOffsetX);
        int nextY = (int) Math.round(mouseY / hudScale() - dragOffsetY);
        if (snapToGrid) {
            nextX = Math.round(nextX / (float) GRID) * GRID;
            nextY = Math.round(nextY / (float) GRID) * GRID;
        }
        state.x = MathHelper.clamp(nextX, 0, Math.max(0, lastHudWidth - width));
        state.y = MathHelper.clamp(nextY, 0, Math.max(0, lastHudHeight - height));
        state.refWidth = lastHudWidth;
        state.refHeight = lastHudHeight;
        return true;
    }

    public static boolean mouseReleased() {
        if (dragging == null) {
            return false;
        }
        dragging = null;
        ConfigManager.save(KhanhClient.modules());
        return true;
    }

    public static boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (amount == 0.0) return false;
        double x = mouseX / hudScale();
        double y = mouseY / hudScale();
        HudLayout.Widget[] widgets = HudLayout.Widget.values();
        for (int index = widgets.length - 1; index >= 0; index--) {
            HudLayout.Widget widget = widgets[index];
            Rect rect = BOUNDS.get(widget);
            if (rect == null || !rect.contains(x, y)) continue;
            HudLayout.State state = HudLayout.widget(widget);
            float next = state.scale + (amount > 0.0 ? 0.05F : -0.05F);
            state.scale = MathHelper.clamp(Math.round(next * 20.0F) / 20.0F, 0.5F, 1.75F);
            ConfigManager.save(KhanhClient.modules());
            return true;
        }
        return false;
    }

    private static float hudScale() {
        return (float) KhanhFeatures.decimal("hud", "hud_scale", 0.65);
    }

    private static void renderScaled(DrawContext context, HudLayout.Widget widget, int x, int y, Runnable render) {
        float scale = HudLayout.widget(widget).scale;
        context.getMatrices().pushMatrix();
        context.getMatrices().translate(x, y);
        context.getMatrices().scale(scale, scale);
        context.getMatrices().translate(-x, -y);
        render.run();
        context.getMatrices().popMatrix();
    }

    private static int renderScaledValue(DrawContext context, HudLayout.Widget widget, int x, int y,
                                         IntSupplier render) {
        float scale = HudLayout.widget(widget).scale;
        context.getMatrices().pushMatrix();
        context.getMatrices().translate(x, y);
        context.getMatrices().scale(scale, scale);
        context.getMatrices().translate(-x, -y);
        int value = render.getAsInt();
        context.getMatrices().popMatrix();
        return Math.round(value * scale);
    }

    /** Khanh Client | 270 FPS | 20:08 | Singleplayer, with hairline dividers. */
    private static void renderWatermark(DrawContext context, int x, int y, boolean editing) {
        MinecraftClient client = MinecraftClient.getInstance();
        String server = client.isInSingleplayer() ? "Singleplayer"
            : client.getCurrentServerEntry() != null ? client.getCurrentServerEntry().address : "Multiplayer";
        Text brand = text("Khanh Client");
        // FPS and ping live in their own pill when it is on, so they are not shown twice.
        boolean statsPill = KhanhFeatures.bool("hud", "fps", true);
        String[] segments = statsPill ? new String[] {server, LocalTime.now().format(SHORT_CLOCK)}
            : client.isInSingleplayer() || client.getNetworkHandler() == null
            ? new String[] {client.getCurrentFps() + " FPS", server, LocalTime.now().format(SHORT_CLOCK)}
            : new String[] {client.getCurrentFps() + " FPS", playerPing(client) + " ms", server, LocalTime.now().format(SHORT_CLOCK)};
        int width = 24 + renderer().getWidth(brand) + 8;
        for (String segment : segments) width += 9 + renderer().getWidth(text(segment)) + 8;
        width += 2;
        flatCard(context, x, y, width, 22);
        context.drawTexture(RenderPipelines.GUI_TEXTURED, ZERO_LOGO, x + 6, y + 4, 0F, 0F, 14, 14, 256, 256, 256, 256);
        context.drawText(renderer(), brand, x + 24, y + 7, accent(), false);
        int cursor = x + 24 + renderer().getWidth(brand) + 8;
        for (String segment : segments) {
            context.fill(cursor, y + 6, cursor + 1, y + 16, 0x30FFFFFF);
            cursor += 9;
            Text value = text(segment);
            // Numbers read bright, their units muted: "144 FPS", "38 ms".
            int split = segment.indexOf(' ');
            if (split > 0 && Character.isDigit(segment.charAt(0))) {
                Text head = text(segment.substring(0, split));
                context.drawText(renderer(), head, cursor, y + 7, HudSurfaceRenderer.TEXT, false);
                context.drawText(renderer(), text(segment.substring(split)), cursor + renderer().getWidth(head), y + 7,
                    HudSurfaceRenderer.MUTED, false);
            } else {
                context.drawText(renderer(), value, cursor, y + 7, HudSurfaceRenderer.MUTED, false);
            }
            cursor += renderer().getWidth(value) + 8;
        }
        track(context, HudLayout.Widget.WATERMARK, x, y, width, 22, editing);
    }

    private static void renderEditorGrid(DrawContext context, int hudWidth, int hudHeight) {
        context.fill(0, 0, hudWidth, hudHeight, 0x2A1A3A8C);
        for (int gx = 0; gx < hudWidth; gx += GRID * 2) {
            context.fill(gx, 0, gx + 1, hudHeight, gx % (GRID * 8) == 0 ? 0x24FFFFFF : 0x10FFFFFF);
        }
        for (int gy = 0; gy < hudHeight; gy += GRID * 2) {
            context.fill(0, gy, hudWidth, gy + 1, gy % (GRID * 8) == 0 ? 0x24FFFFFF : 0x10FFFFFF);
        }
        context.fill(hudWidth / 2, 0, hudWidth / 2 + 1, hudHeight, 0x40FFFFFF);
        context.fill(0, hudHeight / 2, hudWidth, hudHeight / 2 + 1, 0x40FFFFFF);
    }

    /** Snap, Reset all and Done, docked at the bottom like a dock. */
    private static void renderEditorToolbar(DrawContext context, int hudWidth, int hudHeight) {
        String[] labels = {snapToGrid ? "Snap: on" : "Snap: off", "Reset all", "Done"};
        int[] tints = {snapToGrid ? accent() : 0, 0, accent()};
        int gap = 6, total = -gap;
        int[] widths = new int[labels.length];
        for (int i = 0; i < labels.length; i++) {
            widths[i] = renderer().getWidth(text(labels[i])) + 22;
            total += widths[i] + gap;
        }
        int barX = (hudWidth - total) / 2 - 8, barY = hudHeight - 64;
        flatCard(context, barX, barY, total + 16, 28);
        int cursor = barX + 8;
        for (int i = 0; i < labels.length; i++) {
            int bx = cursor, by = barY + 5;
            if (tints[i] != 0) LiquidGlass.glass(context, bx, by, widths[i], 18, 9, tints[i], 1.0F);
            else LiquidGlass.glass(context, bx, by, widths[i], 18, 9, 0xFFFFFF, 0.8F);
            Text label = text(labels[i]);
            context.drawText(renderer(), label, bx + (widths[i] - renderer().getWidth(label)) / 2, by + 5,
                HudSurfaceRenderer.TEXT, false);
            TOOLBAR[i] = new Rect(bx, by, widths[i], 18);
            cursor += widths[i] + gap;
        }
    }

    private static Point point(HudLayout.Widget widget, int defaultX, int defaultY) {
        HudLayout.State state = HudLayout.widget(widget);
        if (!state.positioned) return new Point(defaultX, defaultY);
        Rect bounds = LAST_BOUNDS.get(widget);
        int width = bounds == null ? 40 : bounds.width(), height = bounds == null ? 20 : bounds.height();
        return new Point(state.resolveX(lastHudWidth, width), state.resolveY(lastHudHeight, height));
    }

    /** Four armor slots in one slim glass bar; durability in the HUD colour, pulsing when low. */
    private static void renderArmorHud(DrawContext context, MinecraftClient client, int x, int y, boolean editing) {
        EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
        int width = 8 + slots.length * 24 - 4 + 8;
        int height = 30;
        flatCard(context, x, y, width, height);
        double pulse = 0.55 + 0.45 * Math.sin(System.nanoTime() / 160_000_000.0);
        for (int index = 0; index < slots.length; index++) {
            ItemStack stack = client.player.getEquippedStack(slots[index]);
            int itemX = x + 8 + index * 24;
            if (stack.isEmpty()) {
                SmoothGuiShapes.rect(context, itemX, y + 5, 16, 16, 5, 0x14FFFFFF, false);
                continue;
            }
            context.drawItemWithoutEntity(stack, itemX, y + 5);
            if (stack.isDamageable()) {
                float left = Math.max(0, stack.getMaxDamage() - stack.getDamage()) / (float) Math.max(1, stack.getMaxDamage());
                int bar = Math.max(2, Math.round(16.0F * left));
                int alpha = left < 0.2F ? (int) Math.round(255 * pulse) : 255;
                // White while healthy; amber, then pulsing red as the piece wears out.
                int wear = left < 0.2F ? 0xFFFB7185 : left < 0.4F ? 0xFFFBBF24 : accent();
                SmoothGuiShapes.rect(context, itemX, y + 24, 16, 2, 1, 0x26FFFFFF, false);
                SmoothGuiShapes.rect(context, itemX, y + 24, bar, 2, 1, VisualTheme.withAlpha(wear, alpha), false);
            }
        }
        track(context, HudLayout.Widget.ARMOR, x, y, width, height, editing);
    }

    private static void renderActiveModules(DrawContext context, int x, int y, int hudHeight, boolean editing) {
        int maximumRows = Math.max(3, Math.min(16, (hudHeight - y - 12 - 23) / 15));
        long now = System.currentTimeMillis();
        if (now - activeModuleCacheAt >= 250L) {
            cachedActiveModules = KhanhClient.modules().all().stream()
                .filter(Module::enabled)
                .filter(module -> module.category() != Category.CLIENT)
                .filter(module -> !module.id().equals("visuals"))
                .toList();
            activeModuleCacheAt = now;
        }
        // Rows spring in when a module turns on and slide away when it turns off,
        // longest first, each marked with its category colour.
        List<Module> shown = new ArrayList<>();
        for (Module module : KhanhClient.modules().all()) {
            if (module.category() == Category.CLIENT || module.id().equals("visuals")) continue;
            Spring row = MODULE_ROWS.get(module.id());
            if (row == null && !module.enabled()) continue;
            if (row == null) MODULE_ROWS.put(module.id(), row = new Spring(Spring.PANEL, 0.0F));
            row.to(module.enabled() ? 1.0F : 0.0F);
            if (module.enabled() || row.value() > 0.02F) shown.add(module);
        }
        shown.sort(Comparator.comparingInt((Module module) -> renderer().getWidth(text(module.name()))).reversed());
        List<Module> active = shown.subList(0, Math.min(maximumRows, shown.size()));
        if (active.isEmpty() && !editing) {
            return;
        }
        int width = 120;
        for (Module module : active) width = Math.max(width, renderer().getWidth(text(module.name())) + 14);
        if (active.isEmpty()) {
            flatCard(context, x, y, width, 15);
            context.drawText(renderer(), text("No modules enabled"), x + 8, y + 3, 0xFF8C828A, false);
        }
        // Rows hug the screen edge the list sits nearest to.
        boolean leftSide = x + width / 2 < Math.max(1, lastHudWidth) / 2;
        float cursor = y;
        int rank = 0;
        for (Module module : active) {
            float p = MODULE_ROWS.get(module.id()).value();
            // Longest names sit on top at full strength; each row below fades a touch.
            float fade = Math.max(0.55F, 1.0F - rank++ * 0.035F);
            float shownAmount = Math.max(0.0F, Math.min(1.0F, p));
            float textAmount = shownAmount * fade;
            Text label = text(module.name());
            int rowWidth = renderer().getWidth(label) + 14;
            int rowX = leftSide ? x : x + width - rowWidth;
            int rowY = (int) Math.floor(cursor);
            int hue = accent();
            context.getMatrices().pushMatrix();
            context.getMatrices().translate((1.0F - p) * (leftSide ? -(width + 16.0F) : width + 16.0F), cursor - rowY);
            HudSurfaceRenderer.chip(context, rowX, rowY, rowWidth, 13);
            // A slim accent along the chip's outer edge (a thick bar read like a capital "I").
            int barX = leftSide ? rowX + 1 : rowX + rowWidth - 2;
            SmoothGuiShapes.rect(context, barX, rowY + 3, 1, 7, 0, VisualTheme.withAlpha(hue, Math.round(150 * fade)), false);
            if (shownAmount > 0.05F) {
                int textX = leftSide ? rowX + 7 : rowX + rowWidth - 7 - renderer().getWidth(label);
                context.drawText(renderer(), label, textX, rowY + 2,
                    VisualTheme.withAlpha(HudSurfaceRenderer.TEXT, Math.max(8, Math.round(255 * textAmount))), false);
            }
            context.getMatrices().popMatrix();
            cursor += 15.0F * shownAmount;
        }
        int height = Math.max(15, Math.round(cursor - y));
        track(context, HudLayout.Widget.MODULES, x, y, width, height, editing);
    }

    private static void renderTargetHud(DrawContext context, MinecraftClient client, int x, int y, boolean editing) {
        LivingEntity target = KhanhFeatures.target();
        if (target == null && !editing) return;
        int width = 214, height = 83;
        flatCard(context, x, y, width, height);
        String name = target == null ? "TARGET PREVIEW" : PrivacyService.protectedName(target.getName().getString());
        if (target instanceof AbstractClientPlayerEntity player) {
            PlayerSkinDrawer.draw(context, player.getSkin(), x + 9, y + 9, 30);
        } else {
            HudSurfaceRenderer.icon(context, HudIcon.FOCUS, x + 14, y + 14, 20, softAccent());
        }
        context.drawText(renderer(), text(renderer().trimToWidth(name, 154)), x + 47, y + 11,
            HudSurfaceRenderer.TEXT, false);
        if (target != null && KhanhFeatures.bool("target_hud", "distance", true)) {
            int distance = (int) Math.round(Math.sqrt(client.player.squaredDistanceTo(target)));
            context.drawText(renderer(), text(distance + "m away"), x + 47, y + 24,
                HudSurfaceRenderer.MUTED, false);
        }
        if (target != null && KhanhFeatures.bool("target_hud", "health", true)) {
            float max = Math.max(1, target.getMaxHealth());
            float ratio = MathHelper.clamp((target.getHealth() + target.getAbsorptionAmount()) / max, 0, 1);
            SmoothGuiShapes.rect(context, x + 9, y + 45, width - 18, 5, 3, 0x76343841, false);
            if (ratio > 0) SmoothGuiShapes.rect(context, x + 9, y + 45,
                Math.max(3, Math.round((width - 18) * ratio)), 5, 3,
                ratio < .3F ? 0xFFFF6D83 : softAccent(), false);
            context.drawText(renderer(), text(Math.round(target.getHealth() + target.getAbsorptionAmount()) + " HP"),
                x + 9, y + 55, HudSurfaceRenderer.MUTED, false);
        }
        if (target != null && KhanhFeatures.bool("target_hud", "equipment", true)) {
            EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS,
                EquipmentSlot.FEET, EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND};
            for (int i = 0; i < slots.length; i++) {
                ItemStack item = target.getEquippedStack(slots[i]);
                int itemX = x + 91 + i * 19;
                if (item.isEmpty()) HudSurfaceRenderer.chip(context, itemX, y + 58, 16, 16);
                else context.drawItemWithoutEntity(item, itemX, y + 58);
            }
        }
        track(context, HudLayout.Widget.TARGET_HUD, x, y, width, height, editing);
    }

    private static int renderMetricPill(DrawContext context, HudLayout.Widget widget, HudIcon icon,
                                        String value, int x, int y, boolean editing, int color) {
        Text label = text(value);
        int width = renderer().getWidth(label) + 37;
        float pop = pop(widget.name(), color);
        context.getMatrices().pushMatrix();
        context.getMatrices().translate(x + width / 2.0F, y + 11.5F);
        context.getMatrices().scale(pop, pop);
        context.getMatrices().translate(-(x + width / 2.0F), -(y + 11.5F));
        flatCard(context, x, y, width, 23);
        HudSurfaceRenderer.icon(context, icon, x + 6, y + 5, 13, color);
        context.fill(x + 25, y + 5, x + 26, y + 18, VisualTheme.withAlpha(color, 90));
        int split = value.indexOf(' ');
        String number = split < 0 ? value : value.substring(0, split);
        Text head = text(number);
        context.drawText(renderer(), head, x + 32, y + 7, color, false);
        if (split >= 0) context.drawText(renderer(), text(value.substring(split)), x + 32 + renderer().getWidth(head), y + 7,
            HudSurfaceRenderer.MUTED, false);
        context.getMatrices().popMatrix();
        track(context, widget, x, y, width, 23, editing);
        return width;
    }

    /** A quick bounce whenever a stat changes colour band. */
    private static float pop(String key, int color) {
        Spring spring = POPS.computeIfAbsent(key, k -> new Spring(Spring.BOUNCE, 1.0F));
        Integer last = POP_COLORS.put(key, color);
        if (last != null && last != color) spring.kick(1.14F);
        return spring.to(1.0F).value();
    }


    private static void pillSeparator(DrawContext context, int x, int y) {
        context.fill(x, y + 5, x + 1, y + 18, VisualTheme.withAlpha(accent(), 70));
    }

    private static int playerPing(MinecraftClient client) {
        if (client.getNetworkHandler() == null || client.player == null) return 0;
        PlayerListEntry entry = client.getNetworkHandler().getPlayerListEntry(client.player.getUuid());
        return entry == null ? 0 : entry.getLatency();
    }

    private static void renderCompass(DrawContext context, MinecraftClient client, int x, int y, boolean editing) {
        int size = 104;
        int centerX = x + size / 2;
        int centerY = y + size / 2;
        int radius = 49;
        HudSurfaceRenderer.dial(context,x,y,size);
        float targetYaw = MathHelper.wrapDegrees(client.player.getYaw());
        drawCardinal(context, centerX, centerY, radius - 12, "N", 180.0F, targetYaw, accent());
        drawCardinal(context, centerX, centerY, radius - 12, "E", -90.0F, targetYaw, HudSurfaceRenderer.MUTED);
        drawCardinal(context, centerX, centerY, radius - 12, "S", 0.0F, targetYaw, HudSurfaceRenderer.MUTED);
        drawCardinal(context, centerX, centerY, radius - 12, "W", 90.0F, targetYaw, HudSurfaceRenderer.MUTED);
        track(context, HudLayout.Widget.COMPASS, x, y, size, size, editing);
    }

    private static void renderPlayerRadar(DrawContext context, MinecraftClient client, int x, int y, boolean editing) {
        int size = PlayerRadarRenderer.size();
        HudSurfaceRenderer.radar(context,x,y,size);
        int height = PlayerRadarRenderer.content(context,client,x,y,size);
        track(context, HudLayout.Widget.PLAYER_RADAR, x, y, size, height, editing);
    }

    private static void renderRtpMap(DrawContext context, MinecraftClient client, int x, int y, boolean editing) {
        int cell = KhanhFeatures.number("rtp_map", "cell_size", 26);
        int mapPixels = RTP_MAP_SIZE * cell;
        boolean coordinates = KhanhFeatures.bool("rtp_map", "coordinates", true);
        boolean legend = KhanhFeatures.bool("rtp_map", "legend", true);
        int width = Math.max(mapPixels, 218);
        int height = mapPixels + (coordinates ? 22 : 6) + (legend ? 50 : 0);
        double panelOpacity = KhanhFeatures.decimal("rtp_map", "panel_opacity", 0.68);
        int panelAlpha = (int) Math.round(165.0 * panelOpacity);
        context.fill(x, y, x + mapPixels, y + mapPixels, VisualTheme.withAlpha(0xFF0A0C12, panelAlpha));

        if (KhanhFeatures.bool("rtp_map", "distance_rings", false)) {
            int center = x + mapPixels / 2;
            int middle = y + mapPixels / 2;
            for (int ring = 1; ring <= 9; ring++) {
                int radius = Math.max(1, (int) Math.round(cell * ring * 0.5));
                drawCircleOutline(context, center, middle, radius, 0x3746464B);
            }
        }

        if (KhanhFeatures.bool("rtp_map", "spot_zones", true)) {
            double opacity = KhanhFeatures.decimal("rtp_map", "spot_opacity", 0.86);
            int[] sourceAlpha = {205, 215, 195};
            for (int[] spot : RTP_SPOT_CELLS) {
                int color = VisualTheme.withAlpha(RTP_SPOT_COLORS[spot[2]],
                    (int) Math.round(sourceAlpha[spot[2]] * opacity));
                int cellX = x + spot[0] * cell;
                int cellY = y + spot[1] * cell;
                context.fill(cellX + 1, cellY + 1, cellX + cell - 1, cellY + cell - 1, color);
            }
        }

        if (KhanhFeatures.bool("rtp_map", "grid", true)) {
            for (int index = 0; index <= RTP_MAP_SIZE; index++) {
                int lineX = x + index * cell;
                int lineY = y + index * cell;
                context.fill(lineX, y, lineX + 1, y + mapPixels, 0xD23A4252);
                context.fill(x, lineY, x + mapPixels, lineY + 1, 0xD23A4252);
            }
        }

        if (KhanhFeatures.bool("rtp_map", "region_numbers", true)) {
            float numberScale = (float) KhanhFeatures.decimal("rtp_map", "number_scale", 0.78);
            for (int row = 0; row < RTP_MAP_SIZE; row++) {
                for (int col = 0; col < RTP_MAP_SIZE; col++) {
                    Text number = text(Integer.toString(RTP_REGION_LAYOUT[row * RTP_MAP_SIZE + col]));
                    float centerX = x + col * cell + cell / 2.0F;
                    float centerY = y + row * cell + cell / 2.0F;
                    context.getMatrices().pushMatrix();
                    context.getMatrices().translate(centerX, centerY);
                    context.getMatrices().scale(numberScale, numberScale);
                    context.drawText(renderer(), number, -renderer().getWidth(number) / 2, -renderer().fontHeight / 2,
                        0xEBECF0F8, false);
                    context.getMatrices().popMatrix();
                }
            }
        }

        if (KhanhFeatures.bool("rtp_map", "player_indicator", true)) {
            double mapX = (client.player.getX() + RTP_MAP_OFFSET) / RTP_CELL_WORLD_SIZE;
            double mapZ = (client.player.getZ() + RTP_MAP_OFFSET) / RTP_CELL_WORLD_SIZE;
            int gridX = (int) Math.floor(mapX);
            int gridZ = (int) Math.floor(mapZ);
            if (gridX >= 0 && gridX < RTP_MAP_SIZE && gridZ >= 0 && gridZ < RTP_MAP_SIZE) {
                int markerX = x + gridX * cell + (int) Math.round((mapX - Math.floor(mapX)) * cell);
                int markerY = y + gridZ * cell + (int) Math.round((mapZ - Math.floor(mapZ)) * cell);
                double angle = Math.toRadians(-client.player.getYaw() - 90.0);
                int size = 9;
                int tipX = markerX + (int) Math.round(Math.cos(angle) * size);
                int tipY = markerY - (int) Math.round(Math.sin(angle) * size);
                int leftX = markerX + (int) Math.round(Math.cos(angle + Math.toRadians(135.0)) * size);
                int leftY = markerY - (int) Math.round(Math.sin(angle + Math.toRadians(135.0)) * size);
                int rightX = markerX + (int) Math.round(Math.cos(angle - Math.toRadians(135.0)) * size);
                int rightY = markerY - (int) Math.round(Math.sin(angle - Math.toRadians(135.0)) * size);
                drawFilledTriangle(context, tipX, tipY, leftX, leftY, rightX, rightY, 0xFFFF4048);
            }
        }

        int detailsY = y + mapPixels + 8;
        if (coordinates) {
            context.drawText(renderer(), text(String.format(Locale.ROOT, "Position: X: %d, Z: %d",
                (int) client.player.getX(), (int) client.player.getZ())), x, detailsY, 0xFFFFFFFF, false);
            detailsY += 18;
        }
        if (legend) {
            for (int index = 0; index < RTP_SPOT_LABELS.length; index++) {
                context.fill(x, detailsY + index * 16, x + 14, detailsY + index * 16 + 12, RTP_SPOT_COLORS[index]);
                context.drawText(renderer(), text(RTP_SPOT_LABELS[index]), x + 18, detailsY + index * 16 + 1,
                    0xF0DCE2EE, false);
            }
        }
        if (editing) {
            KhanhClickGuiScreen.roundedOutline(context, x - 2, y - 2, width + 4, height + 4, 3,
                VisualTheme.withAlpha(accent(), 150));
        }
        track(context, HudLayout.Widget.RTP_MAP, x, y, width, height, editing);
    }

    private static void drawFilledTriangle(DrawContext context, int x1, int y1, int x2, int y2,
                                           int x3, int y3, int color) {
        int minimumY = Math.min(y1, Math.min(y2, y3));
        int maximumY = Math.max(y1, Math.max(y2, y3));
        for (int scanY = minimumY; scanY <= maximumY; scanY++) {
            int left = Integer.MAX_VALUE;
            int right = Integer.MIN_VALUE;
            int[] intersections = {
                edgeIntersection(x1, y1, x2, y2, scanY),
                edgeIntersection(x2, y2, x3, y3, scanY),
                edgeIntersection(x3, y3, x1, y1, scanY)
            };
            for (int intersection : intersections) {
                if (intersection != Integer.MAX_VALUE) {
                    left = Math.min(left, intersection);
                    right = Math.max(right, intersection);
                }
            }
            if (left <= right && left != Integer.MAX_VALUE) {
                context.fill(left, scanY, right + 1, scanY + 1, color);
            }
        }
    }

    private static int edgeIntersection(int x1, int y1, int x2, int y2, int scanY) {
        if (scanY < Math.min(y1, y2) || scanY > Math.max(y1, y2)) {
            return Integer.MAX_VALUE;
        }
        return y1 == y2 ? (x1 + x2) / 2 : x1 + (x2 - x1) * (scanY - y1) / (y2 - y1);
    }

    private static void drawThinLine(DrawContext context, int x0, int y0, int x1, int y1, int color) {
        int dx = Math.abs(x1 - x0);
        int dy = -Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1;
        int sy = y0 < y1 ? 1 : -1;
        int error = dx + dy;
        while (true) {
            context.fill(x0, y0, x0 + 1, y0 + 1, color);
            if (x0 == x1 && y0 == y1) break;
            int twice = error * 2;
            if (twice >= dy) { error += dy; x0 += sx; }
            if (twice <= dx) { error += dx; y0 += sy; }
        }
    }

    private static void drawCircleOutline(DrawContext context, int cx, int cy, int radius, int color) {
        int steps = Math.max(24, radius * 4);
        int previousX = cx + radius;
        int previousY = cy;
        for (int step = 1; step <= steps; step++) {
            double angle = Math.PI * 2.0 * step / steps;
            int nextX = cx + (int) Math.round(Math.cos(angle) * radius);
            int nextY = cy + (int) Math.round(Math.sin(angle) * radius);
            drawThinLine(context, previousX, previousY, nextX, nextY, color);
            previousX = nextX;
            previousY = nextY;
        }
    }

    private static void renderCornerAccents(DrawContext context, int width, int height) {
        int color = VisualTheme.withAlpha(accent(), 150);
        int margin = 12;
        int length = 18;
        context.fill(margin, margin, margin + length, margin + 1, color);
        context.fill(margin, margin, margin + 1, margin + length, color);
        context.fill(width - margin - length, margin, width - margin, margin + 1, color);
        context.fill(width - margin - 1, margin, width - margin, margin + length, color);
        context.fill(margin, height - margin - 1, margin + length, height - margin, color);
        context.fill(margin, height - margin - length, margin + 1, height - margin, color);
        context.fill(width - margin - length, height - margin - 1, width - margin, height - margin, color);
        context.fill(width - margin - 1, height - margin - length, width - margin, height - margin, color);
    }

    private static void renderCinematicVisuals(DrawContext context, MinecraftClient client, int width, int height) {
        int centerX = width / 2;
        int centerY = height / 2;
        int accent = accent();

        if (KhanhFeatures.bool("visuals", "horizon_ticks", false)) {
            int color = VisualTheme.withAlpha(accent, 82);
            for (int index = -4; index <= 4; index++) {
                if (index == 0) continue;
                int tickX = centerX + index * 25;
                int tickHeight = Math.abs(index) % 2 == 0 ? 5 : 3;
                context.fill(tickX, centerY - tickHeight / 2, tickX + 1, centerY + (tickHeight + 1) / 2, color);
            }
        }

        if (KhanhFeatures.bool("visuals", "velocity_rails", true)) {
            double speed = client.player == null ? 0.0 : client.player.getVelocity().horizontalLength();
            if (speed > 0.055) {
                int alpha = MathHelper.clamp((int) Math.round(38.0 + speed * 240.0), 38, 132);
                int travel = (int) ((System.currentTimeMillis() / 32L) % 24L);
                int color = VisualTheme.withAlpha(accent, alpha);
                int span = Math.max(80, height - 72);
                for (int index = 0; index < 6; index++) {
                    int railY = 36 + Math.floorMod(index * span / 6 + travel, span);
                    int length = 8 + (index % 3) * 4;
                    context.fill(12, railY, 12 + length, railY + 1, color);
                    context.fill(width - 12 - length, railY, width - 12, railY + 1, color);
                }
            }
        }

        if (KhanhFeatures.bool("visuals", "edge_scanlines", false)) {
            int color = VisualTheme.withAlpha(accent, 44);
            for (int lineY = 26; lineY < height - 20; lineY += 30) {
                int length = lineY % 60 == 26 ? 18 : 11;
                context.fill(5, lineY, 5 + length, lineY + 1, color);
                context.fill(width - 5 - length, lineY, width - 5, lineY + 1, color);
            }
        }

        if (!KhanhFeatures.enabled("health_alert") && KhanhFeatures.bool("visuals", "health_edges", true) && client.player != null) {
            float healthRatio = client.player.getHealth() / Math.max(1.0F, client.player.getMaxHealth());
            if (healthRatio < 0.40F) {
                int alpha = MathHelper.clamp(Math.round((0.40F - healthRatio) * 300.0F), 18, 86);
                int warning = VisualTheme.withAlpha(0xFFFF304D, alpha);
                context.fill(0, 0, width, 3, warning);
                context.fill(0, height - 3, width, height, warning);
                context.fill(0, 3, 3, height - 3, warning);
                context.fill(width - 3, 3, width, height - 3, warning);
            }
        }
    }

    private static void drawCardinal(DrawContext context, int cx, int cy, int radius, String label, float worldYaw, float playerYaw, int color) {
        double radians = Math.toRadians(worldYaw - playerYaw);
        int x = cx + (int) Math.round(Math.sin(radians) * radius);
        int y = cy - (int) Math.round(Math.cos(radians) * radius);
        Text value = text(label);
        context.drawText(renderer(), value, x - renderer().getWidth(value) / 2, y - 4, color, false);
    }

    private static void renderFriends(DrawContext context, int x, int y, boolean editing) {
        List<FriendService.FriendEntry> friends = FriendService.entries();
        if (friends.isEmpty() && !editing) {
            return;
        }
        int visible = Math.min(6, friends.size());
        int width = 156;
        int height = 25 + Math.max(1, visible) * 23;
        int friendColor = FriendService.color();
        flatCard(context, x, y, width, height);
        header(context, "FRIENDS", x, y, width);
        int onlineCount = (int) friends.stream().filter(FriendService.FriendEntry::online).count();
        statusBadge(context, Integer.toString(onlineCount), x + width - 25, y + 6,
            onlineCount > 0 ? friendColor : HudSurfaceRenderer.MUTED);
        if (friends.isEmpty()) {
            context.drawText(renderer(), text("No friends saved"), x + 9, y + 27, 0xFF8C828A, false);
        } else {
            for (int index = 0; index < visible; index++) {
                FriendService.FriendEntry friend = friends.get(index);
                int rowY = y + 24 + index * 23;
                int dotColor = friend.online() ? friendColor : 0xFF51464E;
                PlayerListEntry profile = MinecraftClient.getInstance().getNetworkHandler() == null ? null
                    : MinecraftClient.getInstance().getNetworkHandler().getPlayerListEntry(friend.name());
                if (profile != null) PlayerSkinDrawer.draw(context, profile.getSkinTextures(), x + 8, rowY + 3, 16);
                else { drawCircle(context, x + 16, rowY + 11, 6, VisualTheme.withAlpha(dotColor, 72)); drawCircle(context, x + 16, rowY + 11, 3, dotColor); }
                context.drawText(renderer(), text(renderer().trimToWidth(friend.name(), width - 62)), x + 29, rowY + 4,
                    friend.online() ? 0xFFF7EFF4 : 0xFF8D8289, false);
                Text status = text(friend.online() ? "ON" : "OFF");
                context.drawText(renderer(), status, x + width - 9 - renderer().getWidth(status), rowY + 4,
                    friend.online() ? friendColor : 0xFF6E626A, false);
                context.drawText(renderer(), text(friend.online() ? "ONLINE" : "OFFLINE"), x + 29, rowY + 13,
                    friend.online() ? VisualTheme.withAlpha(friendColor, 190) : 0xFF675E65, false);
            }
        }
        track(context, HudLayout.Widget.FRIENDS, x, y, width, height, editing);
    }

    private static final Rect[] SPOTIFY_BUTTONS = new Rect[3];

    private static final Map<String, Spring> KEY_PRESS = new HashMap<>();

    /**
     * Keystrokes: your movement keys (as bound), left/right click with clicks per second, and
     * jump. Pressed keys fill with the HUD accent and give a small pop.
     */
    private static void renderKeystrokes(DrawContext context, MinecraftClient client, int x, int y, boolean editing) {
        int key = 26, gap = 3, width = key * 3 + gap * 2;
        var o = client.options;
        keyCap(context, "forward", o.forwardKey, x + key + gap, y, key, key, null);
        keyCap(context, "left", o.leftKey, x, y + key + gap, key, key, null);
        keyCap(context, "back", o.backKey, x + key + gap, y + key + gap, key, key, null);
        keyCap(context, "right", o.rightKey, x + (key + gap) * 2, y + key + gap, key, key, null);
        int mouseY = y + (key + gap) * 2, mouseW = (width - gap) / 2;
        keyCap(context, "attack", o.attackKey, x, mouseY, mouseW, 24, InputMetrics.cps(false) + " CPS");
        keyCap(context, "use", o.useKey, x + mouseW + gap, mouseY, mouseW, 24, InputMetrics.cps(true) + " CPS");
        keyCap(context, "jump", o.jumpKey, x, mouseY + 24 + gap, width, 13, null);
        track(context, HudLayout.Widget.KEYSTROKES, x, y, width, (key + gap) * 2 + 24 + gap + 13, editing);
    }

    private static void keyCap(DrawContext context, String id, net.minecraft.client.option.KeyBinding binding,
                               int x, int y, int w, int h, String sub) {
        boolean down = binding.isPressed();
        Spring press = KEY_PRESS.computeIfAbsent(id, k -> new Spring(Spring.SMOOTH, 0.0F));
        float p = MathHelper.clamp(press.to(down ? 1.0F : 0.0F).value(), 0.0F, 1.0F);
        float pop = 1.0F - 0.06F * p;
        context.getMatrices().pushMatrix();
        context.getMatrices().translate(x + w / 2.0F, y + h / 2.0F);
        context.getMatrices().scale(pop, pop);
        context.getMatrices().translate(-(x + w / 2.0F), -(y + h / 2.0F));
        HudSurfaceRenderer.chip(context, x, y, w, h);
        if (p > 0.01F) SmoothGuiShapes.rect(context, x, y, w, h, Math.min(5, h / 2), VisualTheme.withAlpha(accent(), Math.round(225 * p)), false);
        int ink = p > 0.5F ? 0xFF0B0C0F : HudSurfaceRenderer.TEXT;
        String label = id.equals("jump") ? "" : id.equals("attack") ? "LMB" : id.equals("use") ? "RMB"
            : binding.getBoundKeyLocalizedText().getString().toUpperCase(Locale.ROOT);
        if (id.equals("jump")) {
            SmoothGuiShapes.rect(context, x + w / 2 - 12, y + h / 2 - 1, 24, 2, 1, p > 0.5F ? 0xFF0B0C0F : HudSurfaceRenderer.MUTED, false);
        } else {
            Text text = text(label.length() > 4 ? label.substring(0, 4) : label);
            int textY = sub == null ? y + (h - 8) / 2 : y + 4;
            context.drawText(renderer(), text, x + (w - renderer().getWidth(text)) / 2, textY, ink, false);
            if (sub != null) {
                float scale = 0.7F;
                Text small = text(sub);
                context.getMatrices().pushMatrix();
                context.getMatrices().translate(x + (w - renderer().getWidth(small) * scale) / 2.0F, y + 14);
                context.getMatrices().scale(scale, scale);
                context.drawText(renderer(), small, 0, 0, p > 0.5F ? 0xFF2A2C30 : HudSurfaceRenderer.MUTED, false);
                context.getMatrices().popMatrix();
            }
        }
        context.getMatrices().popMatrix();
    }

    /** A small top-centre pill while Freecam is on: its speed, and that scrolling changes it. */
    private static void renderFreecamPill(DrawContext context, int hudWidth) {
        String speed = String.format(Locale.ROOT, "%.2f", KhanhFeatures.decimal("freecam", "speed", 0.65));
        Text title = text("FREECAM"), value = text(speed), hint = text("scroll to change speed");
        int width = 30 + renderer().getWidth(title) + 10 + renderer().getWidth(value) + 10 + Math.round(renderer().getWidth(hint) * 0.75F) + 10;
        int x = (hudWidth - width) / 2, y = 9;
        flatCard(context, x, y, width, 22);
        HudSurfaceRenderer.icon(context, HudIcon.FOCUS, x + 7, y + 5, 12, accent());
        int cursor = x + 25;
        context.drawText(renderer(), title, cursor, y + 7, accent(), false);
        cursor += renderer().getWidth(title) + 5;
        context.fill(cursor, y + 6, cursor + 1, y + 16, 0x30FFFFFF);
        cursor += 5;
        context.drawText(renderer(), value, cursor, y + 7, HudSurfaceRenderer.TEXT, false);
        cursor += renderer().getWidth(value) + 8;
        drawSmall(context, "scroll to change speed", cursor, y + 8, HudSurfaceRenderer.MUTED, false);
    }

    /** Bottom of the vanilla status-effect icons in HUD units (0 when none are shown). */
    private static int effectIconsBottom(MinecraftClient client) {
        boolean beneficial = false, harmful = false;
        for (StatusEffectInstance effect : client.player.getStatusEffects()) {
            if (!effect.shouldShowIcon()) continue;
            if (effect.getEffectType().value().isBeneficial()) beneficial = true; else harmful = true;
        }
        int bottom = harmful ? 51 : beneficial ? 26 : 0;
        return bottom == 0 ? 0 : Math.round(bottom / hudScale());
    }

    /**
     * Now-playing card: album art, a scrolling title, the artist, a live progress bar with times,
     * and previous / play-pause / next buttons you can click while chat is open. The card takes a
     * soft glow from the album's own colour.
     */
    private static void renderSpotify(DrawContext context, int x, int y, boolean editing) {
        SpotifyState state = KhanhFeatures.spotify().state();
        if (!state.active() && !editing && !KhanhFeatures.bool("spotify_hud", "show_idle", true)) {
            return;
        }
        SpotifyArt.Art art = state.active() ? KhanhFeatures.spotify().art() : null;
        int glow = art != null ? vividColour(art.colour()) : 0xFF1ED760;
        int width = 236, height = 60;
        double time = System.nanoTime() / 1_000_000_000.0;
        flatCard(context, x, y, width, height);
        SmoothGuiShapes.rect(context, x + 1, y + 1, width - 2, height - 2, 7, VisualTheme.withAlpha(glow, 22), false);

        // Album art (or the Spotify mark), with a glow of its colour behind it.
        int artX = x + 8, artY = y + 8, artSize = 44;
        SmoothGuiShapes.rect(context, artX - 3, artY - 3, artSize + 6, artSize + 6, 9, VisualTheme.withAlpha(glow, 54), false);
        if (art != null) {
            context.drawTexture(RenderPipelines.GUI_TEXTURED, art.texture(), artX, artY, 0F, 0F, artSize, artSize, 96, 96, 96, 96);
            context.fill(artX, artY, artX + artSize, artY + 1, 0x30FFFFFF);
        } else {
            LiquidGlass.glass(context, artX, artY, artSize, artSize, 8, 0xFFFFFF, 1.0F);
            HudSurfaceRenderer.icon(context, HudIcon.SPOTIFY, artX + 12, artY + 12, 20, state.active() ? glow : HudSurfaceRenderer.MUTED);
        }

        int textX = artX + artSize + 9, textRight = x + width - 10, textWidth = textRight - textX;
        // Title, scrolling when it is too long; an equaliser dances beside it while music plays.
        int titleRoom = textWidth - 18;
        Text title = text(state.title());
        int titleWidth = renderer().getWidth(title);
        int titleColour = state.active() ? HudSurfaceRenderer.TEXT : HudSurfaceRenderer.MUTED;
        if (titleWidth <= titleRoom) {
            context.drawText(renderer(), title, textX, y + 9, titleColour, false);
        } else {
            int loop = titleWidth + 28;
            int offset = (int) ((Math.max(0.0, time * 22.0 % (loop + 60) - 60)) % loop);
            context.enableScissor(textX, y + 4, textX + titleRoom, y + 21);
            context.drawText(renderer(), title, textX - offset, y + 9, titleColour, false);
            context.drawText(renderer(), title, textX - offset + loop, y + 9, titleColour, false);
            context.disableScissor();
        }
        for (int bar = 0; bar < 4; bar++) {
            double wave = state.playing() ? 0.5 + 0.5 * Math.sin(time * (5.2 + bar * 1.7) + bar * 1.3) : 0.15;
            int barHeight = 2 + (int) Math.round(wave * 8);
            SmoothGuiShapes.rect(context, textRight - 14 + bar * 4, y + 18 - barHeight, 2, barHeight, 1,
                state.playing() ? glow : HudSurfaceRenderer.MUTED, false);
        }
        context.drawText(renderer(), text(renderer().trimToWidth(state.artist(), textWidth)), textX, y + 20,
            HudSurfaceRenderer.MUTED, false);

        // Progress bar with a knob, and the elapsed / total time under it.
        long duration = Math.max(0L, state.durationMs());
        float progress = duration > 0L ? MathHelper.clamp(state.livePositionMs() / (float) duration, 0.0F, 1.0F) : 0.0F;
        int barY = y + 34;
        SmoothGuiShapes.rect(context, textX, barY, textWidth, 3, 1, 0x30FFFFFF, false);
        if (duration > 0L) {
            int fill = Math.max(3, Math.round(textWidth * progress));
            SmoothGuiShapes.rect(context, textX, barY, fill, 3, 1, glow, false);
            SmoothGuiShapes.circle(context, textX + fill, barY + 1, 3, 0xFFFFFFFF);
            drawSmall(context, formatMillis(state.livePositionMs()), textX, y + 45, HudSurfaceRenderer.MUTED, false);
            drawSmall(context, formatMillis(duration), textRight, y + 45, HudSurfaceRenderer.MUTED, true);
        }

        // Previous / play-pause / next.
        if (KhanhFeatures.bool("spotify_hud", "controls", true)) {
            float scale = HudLayout.widget(HudLayout.Widget.SPOTIFY).scale;
            int centre = textX + textWidth / 2, buttonY = y + 47;
            int controls = state.available() ? HudSurfaceRenderer.TEXT : HudSurfaceRenderer.MUTED;
            SmoothGuiShapes.circle(context, centre, buttonY, 7, VisualTheme.withAlpha(glow, state.active() ? 235 : 90));
            if (state.playing()) {
                context.fill(centre - 3, buttonY - 3, centre - 1, buttonY + 4, 0xFF0B0B0D);
                context.fill(centre + 1, buttonY - 3, centre + 3, buttonY + 4, 0xFF0B0B0D);
            } else {
                triangle(context, centre - 2, buttonY, 4, true, 0xFF0B0B0D);
            }
            triangle(context, centre - 22, buttonY, 3, false, controls);
            context.fill(centre - 26, buttonY - 3, centre - 25, buttonY + 4, controls);
            triangle(context, centre + 19, buttonY, 3, true, controls);
            context.fill(centre + 24, buttonY - 3, centre + 25, buttonY + 4, controls);
            int[] buttonX = {centre - 30, centre - 9, centre + 15};
            int[] buttonW = {14, 18, 14};
            for (int i = 0; i < 3; i++) {
                SPOTIFY_BUTTONS[i] = new Rect(Math.round(x + (buttonX[i] - x) * scale), Math.round(y + (buttonY - 8 - y) * scale),
                    Math.round(buttonW[i] * scale), Math.round(16 * scale));
            }
        }
        track(context, HudLayout.Widget.SPOTIFY, x, y, width, height, editing);
    }

    /** A small solid triangle pointing right (or left), centred on its tip's row. */
    private static void triangle(DrawContext context, int x, int cy, int half, boolean right, int colour) {
        for (int row = -half; row <= half; row++) {
            int length = half + 1 - Math.abs(row);
            int length2 = Math.max(1, Math.round(length * 1.6F));
            if (right) context.fill(x, cy + row, x + length2, cy + row + 1, colour);
            else context.fill(x + Math.round((half + 1) * 1.6F) - length2, cy + row, x + Math.round((half + 1) * 1.6F), cy + row + 1, colour);
        }
    }

    private static void drawSmall(DrawContext context, String value, int x, int y, int colour, boolean alignRight) {
        Text label = text(value);
        float scale = 0.75F;
        float drawX = alignRight ? x - renderer().getWidth(label) * scale : x;
        context.getMatrices().pushMatrix();
        context.getMatrices().translate(drawX, y);
        context.getMatrices().scale(scale, scale);
        context.drawText(renderer(), label, 0, 0, colour, false);
        context.getMatrices().popMatrix();
    }

    /** The album colour pushed bright and saturated enough to glow on black glass. */
    private static int vividColour(int colour) {
        float[] hsb = java.awt.Color.RGBtoHSB((colour >> 16) & 255, (colour >> 8) & 255, colour & 255, null);
        float saturation = hsb[1] < 0.08F ? 0.0F : Math.min(1.0F, hsb[1] * 1.35F + 0.1F);
        return 0xFF000000 | java.awt.Color.HSBtoRGB(hsb[0], saturation, Math.max(0.78F, hsb[2])) & 0xFFFFFF;
    }

    /** X, Y, Z and the direction you face, numbers bright and labels muted. */
    private static void renderCoords(DrawContext context, MinecraftClient client, int x, int y, boolean editing) {
        String[][] parts = {
            {"X", Integer.toString(client.player.getBlockX())},
            {"Y", Integer.toString(client.player.getBlockY())},
            {"Z", Integer.toString(client.player.getBlockZ())}};
        String facing = switch (client.player.getHorizontalFacing()) {
            case NORTH -> "N"; case SOUTH -> "S"; case EAST -> "E"; default -> "W";
        };
        int width = 32;
        for (String[] part : parts) width += renderer().getWidth(text(part[0])) + 3 + renderer().getWidth(text(part[1])) + 8;
        width += renderer().getWidth(text(facing)) + 12;
        flatCard(context, x, y, width, 23);
        HudSurfaceRenderer.icon(context, HudIcon.COMPASS, x + 6, y + 5, 13, accent());
        context.fill(x + 25, y + 5, x + 26, y + 18, VisualTheme.withAlpha(accent(), 90));
        int cursor = x + 32;
        for (String[] part : parts) {
            context.drawText(renderer(), text(part[0]), cursor, y + 7, HudSurfaceRenderer.MUTED, false);
            cursor += renderer().getWidth(text(part[0])) + 3;
            context.drawText(renderer(), text(part[1]), cursor, y + 7, HudSurfaceRenderer.TEXT, false);
            cursor += renderer().getWidth(text(part[1])) + 8;
        }
        HudSurfaceRenderer.chip(context, cursor - 2, y + 5, renderer().getWidth(text(facing)) + 8, 13);
        context.drawText(renderer(), text(facing), cursor + 2, y + 8, accent(), false);
        track(context, HudLayout.Widget.COORDS, x, y, width, 23, editing);
    }

    private static void renderStaffOnline(DrawContext context, int x, int y, boolean editing) {
        List<StaffOnlineService.StaffEntry> staff = KhanhFeatures.staffOnline().entries();
        if (staff.isEmpty() && !editing) {
            return;
        }
        int visible = Math.min(6, staff.size());
        int width = 192;
        int height = 25 + Math.max(1, visible) * 26 + (staff.size() > visible ? 14 : 0);
        flatCard(context, x, y, width, height);
        header(context, "STAFF ONLINE", x, y, width);
        statusBadge(context, Integer.toString(staff.size()), x + width - 25, y + 6,
            staff.isEmpty() ? HudSurfaceRenderer.MUTED : accent());
        if (staff.isEmpty()) {
            context.drawText(renderer(), text("None detected"), x + 9, y + 27, 0xFF8C828A, false);
        } else {
            for (int index = 0; index < visible; index++) {
                StaffOnlineService.StaffEntry person = staff.get(index);
                int rowY = y + 24 + index * 26;
                PlayerListEntry profile = MinecraftClient.getInstance().getNetworkHandler() == null ? null
                    : MinecraftClient.getInstance().getNetworkHandler().getPlayerListEntry(person.uuid());
                SmoothGuiShapes.rect(context, x + 7, rowY + 2, 21, 21, 6, 0x14FFFFFF, false);
                if (profile != null) PlayerSkinDrawer.draw(context, profile.getSkinTextures(), x + 9, rowY + 4, 17);
                context.drawText(renderer(), text(renderer().trimToWidth(person.name(), width - 81)), x + 34, rowY + 4, 0xFFF2F5F8, false);
                context.drawText(renderer(), text(person.role().toUpperCase(Locale.ROOT)), x + 34, rowY + 14, accent(), false);
                Text ping = text(person.latency() + "ms");
                context.drawText(renderer(), ping, x + width - 9 - renderer().getWidth(ping), rowY + 9,
                    HudSurfaceRenderer.MUTED, false);
            }
            if (staff.size() > visible) context.drawText(renderer(), text("+" + (staff.size() - visible) + " more"),
                x + 9, y + 25 + visible * 26, HudSurfaceRenderer.MUTED, false);
        }
        track(context, HudLayout.Widget.STAFF, x, y, width, height, editing);
    }

    private static void header(DrawContext context, String title, int x, int y, int width) {
        HudIcon icon = switch(title) {
            case "ACTIVE POTIONS" -> HudIcon.POTIONS;
            case "Keybinds" -> HudIcon.KEYBINDS;
            case "FRIENDS" -> HudIcon.PLAYER;
            case "STAFF ONLINE" -> HudIcon.SEARCH;
            default -> HudIcon.MODULES;
        };
        int iconSize = title.equals("ACTIVE POTIONS") ? 16 : 13;
        HudSurfaceRenderer.icon(context,icon,x+7,y+(21-iconSize)/2,iconSize,softAccent());
        context.drawText(renderer(), text(title), x + 28, y + 7, HudSurfaceRenderer.TEXT, false);
        context.fill(x + 9, y + 20, x + width - 9, y + 21, HudSurfaceRenderer.RULE);
        LiquidGlass.accent(context, x + 9, y + 19, Math.min(42, width - 18), 2, 1, accent(), 1.0F);
    }

    private static void statusBadge(DrawContext context, String value, int x, int y, int color) {
        Text label = text(value);
        int width = Math.max(17, renderer().getWidth(label) + 8);
        HudSurfaceRenderer.chip(context, x - width + 17, y, width, 13);
        context.drawText(renderer(), label, x - width + 21 + (width - 8 - renderer().getWidth(label)) / 2,
            y + 2, color, false);
    }

    private static void flatCard(DrawContext context, int x, int y, int width, int height) {
        boolean detailed = KhanhFeatures.choice("visuals", "hud_finish", "Clean").equals("Detailed");
        if (detailed && KhanhFeatures.bool("visuals", "hud_shadow", false)) {
            KhanhClickGuiScreen.roundedRect(context, x + 2, y + 3, width, height, 8, 0x30000000);
        }
        HudSurfaceRenderer.card(context, x, y, width, height);
    }

    private static int hudGlass() {
        double opacity = KhanhFeatures.decimal("visuals", "glass_opacity", 0.86);
        float tint = (float) KhanhFeatures.decimal("visuals", "glass_tint", 0.12);
        int alpha = MathHelper.clamp((int) Math.round(72.0 + opacity * 102.0), 104, 176);
        return VisualTheme.withAlpha(mixColor(0xFF0A0A0B, accent(), 0.02F + tint * 0.04F), alpha);
    }

    private static int hudGlassRaised() {
        int alpha = Math.min(202, ((hudGlass() >>> 24) & 0xFF) + 18);
        return VisualTheme.withAlpha(mixColor(0xFF121213, accent(), 0.05F), alpha);
    }

    private static int hudRowGlass() {
        return VisualTheme.withAlpha(mixColor(0xFF0F0F10, accent(), 0.04F), 132);
    }

    private static int hudSheen() {
        return VisualTheme.withAlpha(mixColor(0xFFEEEEF0, accent(), 0.16F), 18);
    }

    private static int hudBorder() {
        return VisualTheme.withAlpha(mixColor(0xFF808084, accent(), 0.24F), 48);
    }

    private static int mixColor(int first, int second, float amount) {
        float t = MathHelper.clamp(amount, 0.0F, 1.0F);
        int red = Math.round(((first >> 16) & 0xFF) * (1.0F - t) + ((second >> 16) & 0xFF) * t);
        int green = Math.round(((first >> 8) & 0xFF) * (1.0F - t) + ((second >> 8) & 0xFF) * t);
        int blue = Math.round((first & 0xFF) * (1.0F - t) + (second & 0xFF) * t);
        return 0xFF000000 | red << 16 | green << 8 | blue;
    }

    private static void track(DrawContext context, HudLayout.Widget widget, int x, int y, int width, int height, boolean editing) {
        float scale = HudLayout.widget(widget).scale;
        BOUNDS.put(widget, new Rect(x, y, Math.round(width * scale), Math.round(height * scale)));
        if (editing) {
            int color = dragging == widget ? softAccent() : VisualTheme.withAlpha(accent(), 152);
            KhanhClickGuiScreen.roundedOutline(context, x - 1, y - 1, width + 2, height + 2, 5, color);
        }
    }

    private static void renderCrosshair(DrawContext context, int x, int y) {
        MinecraftClient client = MinecraftClient.getInstance();
        boolean onPlayer = client.targetedEntity instanceof net.minecraft.entity.player.PlayerEntity target && target != client.player;
        drawCrosshair(context, x, y, onPlayer);
    }

    /**
     * The Custom Crosshair, also used by the live preview in the GUI. {@code onPlayer} switches to the
     * target colour when that option is on.
     */
    public static void drawCrosshair(DrawContext context, int x, int y, boolean onPlayer) {
        int base = KhanhFeatures.color("custom_crosshair", "color", 0xFFF2F2F6);
        int color = onPlayer && KhanhFeatures.bool("custom_crosshair", "target_change", true)
            ? KhanhFeatures.color("custom_crosshair", "target_color", 0xFFFF4D6A) : base;
        color = VisualTheme.withAlpha(color, 235);
        int size = KhanhFeatures.number("custom_crosshair", "size", 7);
        int gap = KhanhFeatures.number("custom_crosshair", "gap", 3);
        int t = Math.max(1, KhanhFeatures.number("custom_crosshair", "thickness", 1));
        boolean outline = KhanhFeatures.bool("custom_crosshair", "outline", true);
        String style = KhanhFeatures.choice("custom_crosshair", "style", "Cross");
        if (outline) drawCrosshairShape(context, x, y, style, size, gap, t, 0xB0000000, 1);
        drawCrosshairShape(context, x, y, style, size, gap, t, color, 0);
    }

    /** One pass of a crosshair shape; {@code grow} > 0 draws a slightly bigger copy as the outline. */
    private static void drawCrosshairShape(DrawContext c, int x, int y, String style, int size, int gap, int t, int color, int grow) {
        int lo = -(t / 2) - grow, hi = (t - t / 2) + grow;
        switch (style) {
            case "Dot" -> SmoothGuiShapes.rect(c, x - Math.max(1, size / 3) - grow, y - Math.max(1, size / 3) - grow,
                Math.max(2, size / 3) * 2 + 1 + grow * 2, Math.max(2, size / 3) * 2 + 1 + grow * 2, Math.max(1, size / 3) + grow, color, false);
            case "Circle", "Circle + Dot" -> {
                int r = size + grow;
                for (int i = 0; i < t + grow * 2; i++) SmoothGuiShapes.rect(c, x - r + i, y - r + i, (r - i) * 2 + 1, (r - i) * 2 + 1, r - i, color, true);
                if (style.equals("Circle + Dot")) SmoothGuiShapes.rect(c, x - 1 - grow, y - 1 - grow, 3 + grow * 2, 3 + grow * 2, 1 + grow, color, false);
            }
            case "Bracket" -> {
                c.fill(x - size - grow, y - size - grow, x - size + t + grow, y + size + 1 + grow, color);
                c.fill(x + size - t + 1 - grow, y - size - grow, x + size + 1 + grow, y + size + 1 + grow, color);
                c.fill(x - size - grow, y - size - grow, x - gap + grow, y - size + t + grow, color);
                c.fill(x + gap - grow, y - size - grow, x + size + 1 + grow, y - size + t + grow, color);
                c.fill(x - size - grow, y + size - t + 1 - grow, x - gap + grow, y + size + 1 + grow, color);
                c.fill(x + gap - grow, y + size - t + 1 - grow, x + size + 1 + grow, y + size + 1 + grow, color);
            }
            case "T-Shape" -> {
                c.fill(x - size - grow, y + lo, x - gap + grow, y + hi, color);
                c.fill(x + gap + 1 - grow, y + lo, x + size + 1 + grow, y + hi, color);
                c.fill(x + lo, y + gap + 1 - grow, x + hi, y + size + 1 + grow, color);
            }
            case "Chevron" -> {
                for (int i = -grow; i <= size / 2 + grow; i++) {
                    c.fill(x - i - t + 1 - grow, y + gap + i, x - i + 1 + grow, y + gap + i + 1, color);
                    c.fill(x + i - grow, y + gap + i, x + i + t + grow, y + gap + i + 1, color);
                }
            }
            default -> {
                c.fill(x - size - grow, y + lo, x - gap + grow, y + hi, color);
                c.fill(x + gap + 1 - grow, y + lo, x + size + 1 + grow, y + hi, color);
                c.fill(x + lo, y - size - grow, x + hi, y - gap + grow, color);
                c.fill(x + lo, y + gap + 1 - grow, x + hi, y + size + 1 + grow, color);
                if (style.equals("Cross + Dot")) c.fill(x - grow, y - grow, x + 1 + grow, y + 1 + grow, color);
            }
        }
    }

    /** Black theme: the HUD always uses the logo's soft white. */
    private static int accent() {
        return 0xFFE6E8EE;
    }

    private static int softAccent() {
        return 0xB8E6E8EE;
    }

    private static void drawCircle(DrawContext context, int cx, int cy, int radius, int color) {
        int radiusSquared = radius * radius;
        for (int dy = -radius; dy <= radius; dy++) {
            int half = (int) Math.floor(Math.sqrt(Math.max(0, radiusSquared - dy * dy)));
            context.fill(cx - half, cy + dy, cx + half + 1, cy + dy + 1, color);
        }
    }

    private static String formatDuration(StatusEffectInstance effect) {
        if (effect.isInfinite()) {
            return "∞";
        }
        int seconds = Math.max(0, effect.getDuration() / 20);
        return String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60);
    }

    private static String formatMillis(long milliseconds) {
        long seconds = Math.max(0L, milliseconds / 1000L);
        return String.format(Locale.ROOT, "%d:%02d", seconds / 60L, seconds % 60L);
    }

    private static TextRenderer renderer() {
        return KhanhClickGuiScreen.textRenderer();
    }

    private static Text text(String value) {
        return KhanhClickGuiScreen.fontText(value);
    }

    private record Point(int x, int y) {
    }

    private record Rect(int x, int y, int width, int height) {
        boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
        }
    }
}
