package dev.khanh.feature;

import dev.khanh.KhanhClient;
import dev.khanh.config.ConfigManager;
import dev.khanh.gui.HudLayout;
import dev.khanh.gui.KhanhColumnsScreen;
import dev.khanh.gui.KhanhHudEditorScreen;
import dev.khanh.gui.Spring;
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
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import org.joml.Matrix3x2fStack;

/**
 * Khanh Client's HUD, in the same blocky style as the menu: a tag in the top-left (logo, name,
 * FPS, ping, time), the active modules down the top-right with accent edges that slide in and
 * out, coordinates in the bottom-left (with the other dimension's), and armor next to the hotbar.
 * Every piece can be dragged (and scrolled to resize) in the HUD editor or with chat open; the
 * positions live in {@link HudLayout} and are saved with the config.
 */
public final class KhanhHud {
    private static final Identifier LOGO = Identifier.of(KhanhClient.MOD_ID, "textures/gui/khanh-logo.png");
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm");
    private static final Map<String, Spring> SLIDE = new HashMap<>();
    private static final float S = 0.85F;

    private KhanhHud() { }

    /** Where each piece was drawn this frame (x, y, w, h in GUI pixels), for dragging. */
    private static final Map<HudLayout.Widget, int[]> BOUNDS = new EnumMap<>(HudLayout.Widget.class);
    private static final HudLayout.Widget[] ORDER = {
        HudLayout.Widget.WATERMARK, HudLayout.Widget.COMPASS, HudLayout.Widget.MODULES,
        HudLayout.Widget.COORDS, HudLayout.Widget.ARMOR};
    private static final int RADAR_R = 38;
    private static HudLayout.Widget dragging;
    private static int dragOffsetX, dragOffsetY, lastWidth, lastHeight;

    public static void render(DrawContext c, RenderTickCounter counter) {
        MinecraftClient client = MinecraftClient.getInstance();
        BOUNDS.clear();
        if (client.player == null || client.options.hudHidden || !KhanhFeatures.enabled("hud")) return;
        if (client.currentScreen instanceof dev.khanh.gui.KhanhClickGui) return;
        int width = c.getScaledWindowWidth(), height = c.getScaledWindowHeight();
        lastWidth = width;
        lastHeight = height;
        boolean editing = KhanhHudEditorScreen.editing(client);

        if (KhanhFeatures.bool("hud", "watermark", true)) {
            int[] p = place(HudLayout.Widget.WATERMARK, 4, 4, tagWidth(client), 14);
            scaled(c, HudLayout.Widget.WATERMARK, p, () -> tag(c, client, p[0], p[1]));
        }
        if (KhanhFeatures.bool("hud", "compass", true)) {
            int size = RADAR_R * 2 + 6;
            int[] p = place(HudLayout.Widget.COMPASS, 6, 24, size, size);
            scaled(c, HudLayout.Widget.COMPASS, p, () -> radar(c, client, p[0], p[1], RADAR_R));
        }
        if (KhanhFeatures.bool("hud", "modules", true)) {
            List<Module> active = activeModules(client);
            int listW = 0;
            float listH = 0;
            for (Module module : active) {
                listW = Math.max(listW, width(client, module.name().toUpperCase(Locale.ROOT)) + 12);
                listH += 12 * SLIDE.get(module.id()).value();
            }
            listW = Math.max(listW, 60);
            int[] p = place(HudLayout.Widget.MODULES, width - 4 - listW, 4, listW, Math.max(12, Math.round(listH)));
            boolean right = p[0] + p[2] / 2 >= width / 2;
            final int boxW = listW;
            scaled(c, HudLayout.Widget.MODULES, p, () -> modules(c, client, active, p[0], p[1], boxW, right));
            if (editing && active.isEmpty()) text(c, client, "MODULES", p[0] + 4, p[1] + 2, 0x80FFFFFF);
        }
        if (KhanhFeatures.bool("hud", "coords", true)) {
            String line = coordsLine(client);
            int chatOpen = client.currentScreen instanceof net.minecraft.client.gui.screen.ChatScreen ? 14 : 0;
            int[] p = place(HudLayout.Widget.COORDS, 4, height - 15 - chatOpen, width(client, line) + 10, 13);
            scaled(c, HudLayout.Widget.COORDS, p, () -> coords(c, client, line, p[0], p[1]));
        }
        if (KhanhFeatures.bool("hud", "armor", true)) {
            int[] p = place(HudLayout.Widget.ARMOR, width / 2 + 98, height - 26, 72, 23);
            scaled(c, HudLayout.Widget.ARMOR, p, () -> armor(c, client, p[0], p[1] + 7, editing));
        }
        if (editing) outlines(c, client);
    }

    /** Resolves a piece's spot: its saved position, or the default; records its bounds. */
    private static int[] place(HudLayout.Widget widget, int defaultX, int defaultY, int w, int h) {
        HudLayout.State state = HudLayout.widget(widget);
        int sw = Math.round(w * state.scale), sh = Math.round(h * state.scale);
        int x = state.positioned ? state.resolveX(lastWidth, sw) : defaultX;
        int y = state.positioned ? state.resolveY(lastHeight, sh) : defaultY;
        int[] bounds = {x, y, sw, sh};
        BOUNDS.put(widget, bounds);
        return bounds;
    }

    private static void scaled(DrawContext c, HudLayout.Widget widget, int[] at, Runnable draw) {
        float scale = HudLayout.widget(widget).scale;
        Matrix3x2fStack m = c.getMatrices();
        m.pushMatrix();
        m.translate(at[0], at[1]);
        m.scale(scale, scale);
        m.translate(-at[0], -at[1]);
        draw.run();
        m.popMatrix();
    }

    /** Dashed-look frames and names around every piece while editing; the hovered one lights up. */
    private static void outlines(DrawContext c, MinecraftClient client) {
        int accent = VisualTheme.preset() & 0xFFFFFF;
        double mx = client.mouse.getX() * lastWidth / Math.max(1, client.getWindow().getWidth());
        double my = client.mouse.getY() * lastHeight / Math.max(1, client.getWindow().getHeight());
        for (HudLayout.Widget widget : ORDER) {
            int[] b = BOUNDS.get(widget);
            if (b == null) continue;
            boolean hot = widget == dragging || contains(b, mx, my);
            int x = b[0] - 2, y = b[1] - 2, w = b[2] + 4, h = b[3] + 4;
            if (hot) c.fill(x, y, x + w, y + h, 0x22000000 | accent);
            int edge = (hot ? 0xFF000000 : 0x90000000) | accent;
            c.fill(x, y, x + w, y + 1, edge);
            c.fill(x, y + h - 1, x + w, y + h, edge);
            c.fill(x, y + 1, x + 1, y + h - 1, edge);
            c.fill(x + w - 1, y + 1, x + w, y + h - 1, edge);
            if (hot) {
                HudLayout.State state = HudLayout.widget(widget);
                String label = widget.name() + "  " + Math.round(state.scale * 100) + "%";
                Text small = KhanhColumnsScreen.small(label);
                int ly = y > 10 ? y - 9 : y + h + 2;
                c.fill(x, ly - 1, x + client.textRenderer.getWidth(small) + 4, ly + 8, 0xD0101014);
                c.drawText(client.textRenderer, small, x + 2, ly, 0xFF000000 | accent, false);
            }
        }
    }

    private static boolean contains(int[] b, double x, double y) {
        return x >= b[0] && y >= b[1] && x < b[0] + b[2] && y < b[1] + b[3];
    }

    // ---------------------------------------------------------------- dragging

    public static boolean mouseClicked(double mx, double my, int button) {
        for (int i = ORDER.length - 1; i >= 0; i--) {
            HudLayout.Widget widget = ORDER[i];
            int[] b = BOUNDS.get(widget);
            if (b == null || !contains(b, mx, my)) continue;
            HudLayout.State state = HudLayout.widget(widget);
            if (button == 1) {
                state.positioned = false;
                state.scale = 1.0F;
                ConfigManager.save(KhanhClient.modules());
                KhanhSounds.playInterface(MinecraftClient.getInstance(), 0.85F);
                return true;
            }
            if (button != 0) return false;
            state.x = b[0];
            state.y = b[1];
            state.refWidth = lastWidth;
            state.refHeight = lastHeight;
            state.positioned = true;
            dragging = widget;
            dragOffsetX = (int) Math.round(mx) - b[0];
            dragOffsetY = (int) Math.round(my) - b[1];
            return true;
        }
        return false;
    }

    public static boolean mouseDragged(double mx, double my) {
        if (dragging == null) return false;
        int[] b = BOUNDS.get(dragging);
        int w = b == null ? 40 : b[2], h = b == null ? 14 : b[3];
        HudLayout.State state = HudLayout.widget(dragging);
        int x = (int) Math.round(mx) - dragOffsetX, y = (int) Math.round(my) - dragOffsetY;
        // Snap to the screen edges and centre lines when close to them.
        x = snap(x, w, lastWidth);
        y = snap(y, h, lastHeight);
        state.x = Math.max(0, Math.min(x, Math.max(0, lastWidth - w)));
        state.y = Math.max(0, Math.min(y, Math.max(0, lastHeight - h)));
        state.refWidth = lastWidth;
        state.refHeight = lastHeight;
        return true;
    }

    private static int snap(int pos, int size, int total) {
        int margin = 4, pull = 4;
        if (Math.abs(pos - margin) <= pull) return margin;
        if (Math.abs(pos + size - (total - margin)) <= pull) return total - margin - size;
        if (Math.abs(pos + size / 2 - total / 2) <= pull) return total / 2 - size / 2;
        return pos;
    }

    public static boolean mouseReleased() {
        if (dragging == null) return false;
        dragging = null;
        ConfigManager.save(KhanhClient.modules());
        return true;
    }

    public static boolean mouseScrolled(double mx, double my, double amount) {
        if (amount == 0) return false;
        for (int i = ORDER.length - 1; i >= 0; i--) {
            int[] b = BOUNDS.get(ORDER[i]);
            if (b == null || !contains(b, mx, my)) continue;
            HudLayout.State state = HudLayout.widget(ORDER[i]);
            float next = state.scale + (amount > 0 ? 0.05F : -0.05F);
            state.scale = Math.max(0.5F, Math.min(1.75F, Math.round(next * 20.0F) / 20.0F));
            ConfigManager.save(KhanhClient.modules());
            return true;
        }
        return false;
    }

    /**
     * The radar: a glowing accent rim with tick marks, faint range rings, rotating N/E/S/W, a
     * sweeping scan line, red dots for players (clamped to the rim when out of range), optional
     * orange dots for hostile mobs, and your arrow in the middle. Up is the way you face.
     */
    private static void radar(DrawContext c, MinecraftClient client, int left, int top, int r) {
        int accent = VisualTheme.preset() & 0xFFFFFF;
        int cx = left + r + 3, cy = top + r + 3;
        dev.khanh.gui.SmoothGuiShapes.circle(c, cx, cy, r + 3, 0x22000000 | accent);
        dev.khanh.gui.SmoothGuiShapes.circle(c, cx, cy, r + 1, 0xE0000000 | accent);
        dev.khanh.gui.SmoothGuiShapes.circle(c, cx, cy, r, 0xE00B0C11);
        dev.khanh.gui.SmoothGuiShapes.rect(c, cx - r * 2 / 3, cy - r * 2 / 3, r * 4 / 3, r * 4 / 3, r * 2 / 3, 0x30000000 | accent, true);
        dev.khanh.gui.SmoothGuiShapes.rect(c, cx - r / 3, cy - r / 3, r * 2 / 3, r * 2 / 3, r / 3, 0x22000000 | accent, true);
        c.fill(cx - r + 3, cy, cx + r - 3, cy + 1, 0x18FFFFFF);
        c.fill(cx, cy - r + 3, cx + 1, cy + r - 3, 0x18FFFFFF);
        float yaw = client.player.getYaw(client.getRenderTickCounter().getTickProgress(true));
        double rad = Math.toRadians(yaw);
        double sin = Math.sin(rad), cos = Math.cos(rad);
        Matrix3x2fStack m = c.getMatrices();
        // Tick marks every 30 degrees, rotating with you.
        for (int i = 0; i < 12; i++) {
            m.pushMatrix();
            m.translate(cx + 0.5F, cy + 0.5F);
            m.rotate((float) Math.toRadians(i * 30 - yaw + 180));
            c.fill(0, -r + 1, 1, -r + (i % 3 == 0 ? 5 : 3), i % 3 == 0 ? 0xC0FFFFFF : 0x60FFFFFF);
            m.popMatrix();
        }
        // Scan line with a fading trail.
        double sweep = (System.currentTimeMillis() % 2400L) / 2400.0 * 360.0;
        for (int k = 0; k < 10; k++) {
            m.pushMatrix();
            m.translate(cx + 0.5F, cy + 0.5F);
            m.rotate((float) Math.toRadians(sweep - k * 3.0));
            c.fill(0, -r + 2, 1, 0, Math.round(110 * (1 - k / 10.0F)) << 24 | accent);
            m.popMatrix();
        }
        double range = KhanhFeatures.decimal("hud", "radar_range", 64);
        // Cardinal letters on the rim.
        String[] letters = {"N", "E", "S", "W"};
        double[][] dirs = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};
        for (int i = 0; i < 4; i++) {
            double[] p = toRadar(dirs[i][0], dirs[i][1], sin, cos);
            int lx = (int) Math.round(cx + p[0] * (r - 8)), ly = (int) Math.round(cy + p[1] * (r - 8));
            Text letter = KhanhColumnsScreen.small(letters[i]);
            c.drawText(client.textRenderer, letter, lx - client.textRenderer.getWidth(letter) / 2, ly - 3, i == 0 ? 0xFFFF5555 : 0xC0FFFFFF, false);
        }
        // Players and (optionally) mobs.
        boolean mobs = KhanhFeatures.bool("hud", "radar_mobs", false);
        for (var entity : client.world.getEntities()) {
            if (entity == client.player) continue;
            boolean player = entity instanceof net.minecraft.entity.player.PlayerEntity;
            if (!player && !(mobs && entity instanceof net.minecraft.entity.mob.HostileEntity)) continue;
            double dx = entity.getX() - client.player.getX(), dz = entity.getZ() - client.player.getZ();
            double[] p = toRadar(dx, dz, sin, cos);
            double dist = Math.sqrt(dx * dx + dz * dz) / range;
            double scale = Math.min(dist, 1.0) * (r - 4) / Math.max(1e-6, Math.sqrt(p[0] * p[0] + p[1] * p[1]));
            int px = (int) Math.round(cx + p[0] * scale), py = (int) Math.round(cy + p[1] * scale);
            if (player) {
                dev.khanh.gui.SmoothGuiShapes.circle(c, px, py, 3, 0x60FF3B4E);
                dev.khanh.gui.SmoothGuiShapes.circle(c, px, py, 2, dist > 1 ? 0xA0FF3B4E : 0xFFFF3B4E);
            } else {
                dev.khanh.gui.SmoothGuiShapes.circle(c, px, py, 1, 0xD0FF9A3B);
            }
        }
        // You: an accent arrow pointing up.
        Text arrow = Text.literal("\u25B2");   // Minecraft's font has the arrow glyph
        c.drawText(client.textRenderer, arrow, cx - client.textRenderer.getWidth(arrow) / 2 + 1, cy - 4, 0xFF000000 | accent, false);
    }

    /** World offset to radar space: x right, y up = the way you face. */
    private static double[] toRadar(double dx, double dz, double sin, double cos) {
        double forward = dx * -sin + dz * cos;
        double right = dx * -cos + dz * -sin;
        return new double[] {right, -forward};
    }

    private static String tagInfo(MinecraftClient client) {
        List<String> parts = new ArrayList<>();
        if (KhanhFeatures.bool("hud", "fps", true)) {
            parts.add(client.getCurrentFps() + " FPS");
            if (!client.isInSingleplayer() && client.getNetworkHandler() != null && client.player != null) {
                var entry = client.getNetworkHandler().getPlayerListEntry(client.player.getUuid());
                if (entry != null) parts.add(entry.getLatency() + "MS");
            }
        }
        parts.add(LocalTime.now().format(CLOCK));
        return String.join("  ", parts);
    }

    private static int tagWidth(MinecraftClient client) {
        return 14 + 4 + width(client, "KHANH CLIENT") + 8 + width(client, tagInfo(client)) + 8;
    }

    private static void tag(DrawContext c, MinecraftClient client, int x, int y) {
        int accent = VisualTheme.preset() & 0xFFFFFF;
        String info = tagInfo(client);
        int h = 14;
        int w = tagWidth(client);
        dev.khanh.gui.SmoothGuiShapes.rect(c, x, y, w, h, 7, 0xC00B0C11, false);
        dev.khanh.gui.SmoothGuiShapes.rect(c, x, y, w, h, 7, 0xB0000000 | accent, true);
        c.drawTexture(RenderPipelines.GUI_TEXTURED, LOGO, x + 4, y + 1, 0F, 0F, 12, 12, 12, 12, 12, 12, 0xFFFFFFFF);
        int tx = x + 19;
        tx = text(c, client, "KHANH", tx, y + 4, 0xFFFFFFFF);
        tx = text(c, client, " CLIENT", tx, y + 4, 0xFF000000 | accent);
        text(c, client, "  " + info, tx, y + 4, 0xFFB8BCC6);
    }

    /** Modules shown in the list, longest first, including ones still sliding out. */
    private static List<Module> activeModules(MinecraftClient client) {
        List<Module> all = new ArrayList<>();
        for (Module module : KhanhClient.modules().all()) {
            if (module.id().equals("hud") || module.id().equals("visuals") || module.id().equals("intro")
                || module.id().equals("client_sounds") || module.id().equals("config")) continue;
            float target = module.enabled() ? 1.0F : 0.0F;
            Spring spring = SLIDE.computeIfAbsent(module.id(), k -> new Spring(Spring.SMOOTH, target));
            if (spring.to(target).value() > 0.01F) all.add(module);
        }
        all.sort(Comparator.comparingInt((Module m) -> width(client, m.name().toUpperCase(Locale.ROOT))).reversed());
        return all;
    }

    /**
     * The module list inside its box. On the right half of the screen rows hug the right edge
     * and slide in from it; on the left half they hug the left edge instead.
     */
    private static void modules(DrawContext c, MinecraftClient client, List<Module> active, int boxX, int boxY, int boxW, boolean right) {
        boolean rainbow = KhanhFeatures.choice("client_colors", "preset", "Blue").equals("Rainbow");
        int accent = VisualTheme.preset() & 0xFFFFFF;
        int left = boxX, edgeR = boxX + boxW;
        float y = boxY;
        int row = 0;
        for (Module module : active) {
            float k = SLIDE.get(module.id()).value();
            String name = module.name().toUpperCase(Locale.ROOT);
            int w = width(client, name) + 8;
            int h = 12;
            int edge = rainbow ? java.awt.Color.HSBtoRGB(((System.currentTimeMillis() % 4000L) / 4000.0F + row * 0.06F) % 1.0F, 0.7F, 1.0F) & 0xFFFFFF : accent;
            int alpha = Math.round(255 * Math.min(1.0F, k * 1.4F));
            int top = Math.round(y), bottom = Math.round(y + h * k) - 1;
            int x = right ? Math.round(edgeR - 4 - w * k) : left;
            int bgW = right ? edgeR - (x - 2) : Math.round((w + 4) * k);
            int bgX = right ? x - 2 : left;
            if (bottom - top > 2) {
                dev.khanh.gui.SmoothGuiShapes.rect(c, bgX, top, bgW, bottom - top, 4, Math.round(185 * k) << 24 | 0x0B0C11, false);
                dev.khanh.gui.SmoothGuiShapes.rect(c, bgX, top, bgW, bottom - top, 4, Math.round(120 * k) << 24 | edge, true);
                int barX = right ? edgeR - 3 : left + 1;
                dev.khanh.gui.SmoothGuiShapes.rect(c, barX, top + 2, 2, bottom - top - 4, 1, alpha << 24 | edge, false);
            }
            if (k > 0.5F) text(c, client, name, right ? x + 2 : left + 5, Math.round(y) + 2, alpha << 24 | 0xFFFFFF);
            y += h * k;
            row++;
        }
    }

    private static String coordsLine(MinecraftClient client) {
        var p = client.player.getBlockPos();
        boolean nether = client.world.getRegistryKey() == World.NETHER;
        boolean overworld = client.world.getRegistryKey() == World.OVERWORLD;
        String line = "XYZ " + p.getX() + " " + p.getY() + " " + p.getZ();
        if (nether) line += "   OVERWORLD " + p.getX() * 8 + " " + p.getZ() * 8;
        else if (overworld) line += "   NETHER " + p.getX() / 8 + " " + p.getZ() / 8;
        return line + "   " + client.player.getHorizontalFacing().asString().toUpperCase(Locale.ROOT);
    }

    private static void coords(DrawContext c, MinecraftClient client, String line, int x, int y) {
        int w = width(client, line) + 10;
        dev.khanh.gui.SmoothGuiShapes.rect(c, x, y, w, 13, 6, 0xC00B0C11, false);
        dev.khanh.gui.SmoothGuiShapes.rect(c, x, y, w, 13, 6, 0xB0000000 | (VisualTheme.preset() & 0xFFFFFF), true);
        text(c, client, line, x + 5, y + 3, 0xFFFFFFFF);
    }

    /** Armor pieces to the right of the hotbar, each with its durability. */
    private static void armor(DrawContext c, MinecraftClient client, int x, int y, boolean editing) {
        EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
        for (EquipmentSlot slot : slots) {
            ItemStack stack = client.player.getEquippedStack(slot);
            if (stack.isEmpty()) {
                // Empty slots keep their spot in the editor so the piece is easy to grab.
                if (editing) c.fill(x + 2, y + 2, x + 14, y + 14, 0x30FFFFFF);
                x += 18;
                continue;
            }
            c.drawItem(stack, x, y);
            if (stack.isDamageable()) {
                int left = stack.getMaxDamage() - stack.getDamage();
                int percent = Math.round(left * 100.0F / Math.max(1, stack.getMaxDamage()));
                int colour = percent > 50 ? 0xFF5DF27A : percent > 20 ? 0xFFFFD84A : 0xFFFF5555;
                Matrix3x2fStack m = c.getMatrices();
                String value = percent + "%";
                Text label = KhanhColumnsScreen.small(value);
                c.drawText(client.textRenderer, label, x + 8 - client.textRenderer.getWidth(label) / 2, y - 7, colour, false);
            }
            x += 18;
        }
    }

    private static int text(DrawContext c, MinecraftClient client, String value, int x, int y, int colour) {
        Matrix3x2fStack m = c.getMatrices();
        Text text = KhanhColumnsScreen.blocky(value);
        c.drawText(client.textRenderer, text, x, y, colour, false);
        return x + client.textRenderer.getWidth(text);
    }

    private static int width(MinecraftClient client, String value) {
        TextRenderer tr = client.textRenderer;
        return tr.getWidth(KhanhColumnsScreen.blocky(value));
    }
}
