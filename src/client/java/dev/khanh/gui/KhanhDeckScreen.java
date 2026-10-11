package dev.khanh.gui;

import dev.khanh.KhanhClient;
import dev.khanh.config.ConfigManager;
import dev.khanh.feature.KhanhFeatures;
import dev.khanh.feature.VisualTheme;
import dev.khanh.module.KhanhCatalog;
import dev.khanh.module.Module;
import dev.khanh.module.ModuleOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.IntConsumer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.util.InputUtil;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.OrderedText;
import net.minecraft.text.StringVisitable;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.joml.Matrix3x2fStack;
import org.lwjgl.glfw.GLFW;

/**
 * Khanh Client's menu: a cinematic module deck. Letterbox bars slide in, section tabs run across
 * the top, the section's modules sit on a sliding carousel of cards (the focused one glows and
 * floats), and the focused module's settings fill the panel underneath. Type to search, scroll
 * or use the arrow keys to browse, click the focused card to switch it on or off.
 */
public final class KhanhDeckScreen extends Screen implements KhanhClickGui {
    private static final Identifier LOGO = Identifier.of(KhanhClient.MOD_ID, "textures/gui/khanh-logo.png");
    private static final int CARD_W = 118, CARD_H = 150, CARD_GAP = 134, BAR = 30;
    private static final int CONTROL_W = 172, CONTROL_H = 34, CONTROL_GAP = 8;
    private static final Map<String, Boolean> ICONS = new HashMap<>();
    private static int lastTab;
    private static final Map<Integer, Integer> lastFocus = new HashMap<>();

    private record Tab(String title, List<Module> modules) { }

    private record Hit(int x, int y, int w, int h, String key, IntConsumer action) {
        boolean contains(double mx, double my) {
            return mx >= x && my >= y && mx < x + w && my < y + h;
        }
    }

    private final Screen parent;
    private final List<Tab> tabs = new ArrayList<>();
    private final List<Hit> hits = new ArrayList<>();
    private final Spring carousel = new Spring(Spring.GLIDE, 0.0F);
    private final Spring tabX = new Spring(Spring.SMOOTH, 0.0F);
    private final Spring tabW = new Spring(Spring.SMOOTH, 0.0F);
    private final Spring settingsScroll = new Spring(Spring.SMOOTH, 0.0F);
    private final Map<String, Spring> hover = new HashMap<>();
    private int tab = lastTab;
    private int focus;
    private float settingsTarget, settingsMax;
    private long openedAt = System.nanoTime(), tabChangedAt = System.nanoTime();
    private long closingAt = -1L;
    private String search = "";
    private ModuleOption editingText;
    private Module binding;
    private ModuleOption dragging;
    private int dragX, dragW;
    private String hoverKey = "", hint = "";
    private int mouseX, mouseY;
    // Layout worked out each frame so the deck fits any window size.
    private float cardScale = 1.0F;
    private int carouselY, settingsTop, settingsBottom;

    private void layout() {
        int top = BAR + 16, bottom = height - BAR - 6;
        int usable = bottom - top;
        int settings = Math.max(64, Math.round(usable * 0.40F));
        int carousel = usable - settings - 6;
        cardScale = Math.max(0.45F, Math.min(1.0F, carousel / (CARD_H + 8.0F)));
        carouselY = top + carousel / 2;
        settingsTop = top + carousel + 6;
        settingsBottom = bottom;
    }

    public KhanhDeckScreen(Screen parent) {
        super(Text.literal("Khanh Client"));
        this.parent = parent;
        for (KhanhCatalog.Section section : KhanhCatalog.SECTIONS) {
            List<Module> modules = new ArrayList<>();
            for (KhanhCatalog.Entry entry : section.entries()) {
                Module module = KhanhClient.modules().byId(entry.id());
                if (module != null) modules.add(module);
            }
            tabs.add(new Tab(section.title(), modules));
        }
        List<Module> settings = new ArrayList<>();
        for (String id : List.of("hud", "visuals", "intro", "client_sounds")) {
            Module module = KhanhClient.modules().byId(id);
            if (module != null) settings.add(module);
        }
        tabs.add(new Tab("SETTINGS", settings));
        tab = Math.max(0, Math.min(tab, tabs.size() - 1));
        focus = lastFocus.getOrDefault(tab, 0);
        carousel.snap(focus);
    }

    // ---------------------------------------------------------------- state

    private List<Module> shown() {
        if (search.isEmpty()) return tabs.get(tab).modules();
        String query = search.toLowerCase(Locale.ROOT);
        List<Module> found = new ArrayList<>();
        for (Tab each : tabs) for (Module module : each.modules()) {
            if (module.name().toLowerCase(Locale.ROOT).contains(query) || module.description().toLowerCase(Locale.ROOT).contains(query)) {
                if (!found.contains(module)) found.add(module);
            }
        }
        return found;
    }

    private Module focused() {
        List<Module> modules = shown();
        if (modules.isEmpty()) return null;
        focus = Math.max(0, Math.min(focus, modules.size() - 1));
        return modules.get(focus);
    }

    private void setFocus(int index, boolean sound) {
        List<Module> modules = shown();
        if (modules.isEmpty()) return;
        int next = Math.max(0, Math.min(index, modules.size() - 1));
        if (next != focus && sound) play("ui_select", next > focus ? 1.12F : 0.92F, 0.22F);
        focus = next;
        carousel.to(focus);
        settingsTarget = 0;
        editingText = null;
        binding = null;
        if (search.isEmpty()) lastFocus.put(tab, focus);
    }

    private void setTab(int index) {
        if (index == tab && search.isEmpty()) return;
        search = "";
        tab = Math.floorMod(index, tabs.size());
        lastTab = tab;
        focus = lastFocus.getOrDefault(tab, 0);
        carousel.snap(focus + 2.5F);
        carousel.to(focus);
        tabChangedAt = System.nanoTime();
        settingsTarget = 0;
        editingText = null;
        binding = null;
        play("ui_open", 1.0F, 0.22F);
    }

    // ---------------------------------------------------------------- rendering

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) { }

    @Override
    public void render(DrawContext c, int mouseX, int mouseY, float delta) {
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        layout();
        hits.clear();
        hint = "";
        float open = ease(clamp((System.nanoTime() - openedAt) / 4.5e8F));
        float shut = closingAt < 0 ? 0.0F : clamp((System.nanoTime() - closingAt) / 2.2e8F);
        if (shut >= 1.0F) {
            client.setScreen(parent);
            return;
        }
        float show = open * (1.0F - ease(shut));
        int accent = VisualTheme.preset();

        // Darkened world with a vignette, then the film letterbox.
        c.fill(0, 0, width, height, a(0.62F * show) << 24);
        c.fillGradient(0, 0, width, height / 3, a(0.45F * show) << 24, 0);
        c.fillGradient(0, height * 2 / 3, width, height, 0, a(0.55F * show) << 24);
        int bar = Math.round(BAR * show);
        c.fill(0, 0, width, bar, a(0.96F * show) << 24 | 0x05060A);
        c.fill(0, height - bar, width, height, a(0.96F * show) << 24 | 0x05060A);
        c.fill(0, bar, width, bar + 1, a(0.5F * show) << 24 | (accent & 0xFFFFFF));
        c.fill(0, height - bar - 1, width, height - bar, a(0.25F * show) << 24 | (accent & 0xFFFFFF));

        drawTopBar(c, show, bar, accent);
        drawHero(c, show, accent);
        drawCarousel(c, show, accent);
        drawSettings(c, show, accent);
        drawBottomBar(c, show, bar);
        updateHoverSound();
    }

    private void drawTopBar(DrawContext c, float show, int bar, int accent) {
        int y = bar / 2 - 4;
        // Logo, plus the wordmark when there is room for it.
        int logo = 18;
        c.drawTexture(RenderPipelines.GUI_TEXTURED, LOGO, 10, bar / 2 - logo / 2, 0F, 0F, logo, logo, logo, logo, logo, logo, a(show) << 24 | 0xFFFFFF);
        int leftEdge = 34;
        if (width >= 560) {
            text(c, "KHANH", 32, y - 1, 0.9F, a(show) << 24 | 0xFFFFFF);
            text(c, "CLIENT", 32 + width("KHANH", 0.9F) + 3, y - 1, 0.9F, a(show) << 24 | (accent & 0xFFFFFF));
            leftEdge = 32 + width("KHANH CLIENT", 0.9F) + 14;
        }
        // Search box on the right.
        int sw = Math.max(70, Math.min(120, width / 5)), sx = width - sw - 8, sy = bar / 2 - 8;
        SmoothGuiShapes.rect(c, sx, sy, sw, 16, 8, a(show * 0.9F) << 24 | 0x12141B, false);
        SmoothGuiShapes.rect(c, sx, sy, sw, 16, 8, a(show * (search.isEmpty() ? 0.25F : 0.9F)) << 24 | (accent & 0xFFFFFF), true);
        boolean caret = !search.isEmpty() && (System.currentTimeMillis() / 500) % 2 == 0;
        String shownSearch = search.isEmpty() ? "Type to search" : search + (caret ? "_" : "");
        text(c, trimTo(shownSearch, sw - 14, 0.62F), sx + 8, sy + 5, 0.62F, a(show * (search.isEmpty() ? 0.45F : 1.0F)) << 24 | 0xFFFFFF);

        // Section tabs fill the space between, shrinking to fit, with a sliding underline.
        int room = sx - 10 - leftEdge;
        float natural = 0;
        for (Tab each : tabs) natural += width(each.title(), 1.0F) + 16;
        float scale = Math.max(0.42F, Math.min(0.72F, room / Math.max(1.0F, natural)));
        float[] widths = new float[tabs.size()];
        float total = 0;
        for (int i = 0; i < tabs.size(); i++) {
            widths[i] = width(tabs.get(i).title(), scale) + 16 * scale / 0.72F;
            total += widths[i];
        }
        float x = leftEdge + Math.max(0, (room - total) / 2.0F);
        for (int i = 0; i < tabs.size(); i++) {
            boolean active = i == tab && search.isEmpty();
            String key = "tab:" + i;
            float h = hoverValue(key);
            int colour = active ? 0xFFFFFF : lerpColour(0x8A8F9C, 0xE6E8EE, h);
            float pad = (widths[i] - width(tabs.get(i).title(), scale)) / 2.0F;
            text(c, tabs.get(i).title(), Math.round(x + pad), bar / 2 - Math.round(4 * scale / 0.72F), scale, a(show) << 24 | colour);
            final int index = i;
            hits.add(new Hit(Math.round(x), 0, Math.round(widths[i]), bar, key, button -> setTab(index)));
            if (active) {
                tabX.to(x + pad);
                tabW.to(widths[i] - pad * 2);
            }
            x += widths[i];
        }
        if (search.isEmpty()) {
            int lx = Math.round(tabX.value()), lw = Math.round(tabW.value());
            c.fill(lx, bar - 3, lx + lw, bar - 1, a(show) << 24 | (accent & 0xFFFFFF));
        }
    }

    /** The section name, huge and faint behind the cards. */
    private void drawHero(DrawContext c, float show, int accent) {
        String title = search.isEmpty() ? tabs.get(tab).title() : "RESULTS";
        float swap = ease(clamp((System.nanoTime() - tabChangedAt) / 4.0e8F));
        float scale = 4.2F * cardScale;
        int alpha = a(show * 0.08F * swap);
        int w = width(title, scale);
        int y = Math.round(carouselY - 14 * cardScale + (1 - swap) * 10);
        text(c, title, width / 2 - w / 2, y, scale, alpha << 24 | 0xFFFFFF);
        List<Module> modules = shown();
        String count = modules.isEmpty() ? "NOTHING FOUND" : (focus + 1) + " / " + modules.size();
        text(c, count, width / 2 - width(count, 0.55F) / 2, BAR + 5, 0.55F, a(show * 0.55F) << 24 | (accent & 0xFFFFFF));
    }

    private void drawCarousel(DrawContext c, float show, int accent) {
        List<Module> modules = shown();
        if (modules.isEmpty()) return;
        float position = carousel.value();
        int cx = width / 2, cy = carouselY;
        float sinceTab = (System.nanoTime() - tabChangedAt) / 1.0e9F;
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < modules.size(); i++) order.add(i);
        order.sort((p, q) -> Float.compare(Math.abs(q - position), Math.abs(p - position)));
        for (int i : order) {
            float offset = i - position;
            if (Math.abs(offset) > 4.2F) continue;
            float distance = Math.min(Math.abs(offset), 3.0F);
            float scale = (1.0F - distance * 0.13F) * cardScale;
            float fade = clamp(1.0F - distance * 0.28F) * show;
            float enter = ease(clamp((sinceTab - Math.abs(i - focus) * 0.05F) / 0.35F));
            float x = cx + offset * CARD_GAP * cardScale * (1.0F - distance * 0.06F);
            float bob = i == focus ? (float) Math.sin(System.nanoTime() / 4.0e8) * 2.0F : 0.0F;
            float y = cy + (1.0F - enter) * 40.0F + bob;
            drawCard(c, modules.get(i), x, y, scale, fade * enter, i == focus, accent);
            int hw = Math.round(CARD_W * scale / 2), hh = Math.round(CARD_H * scale / 2);
            final int index = i;
            hits.add(new Hit(Math.round(x) - hw, Math.round(y) - hh, hw * 2, hh * 2, "card:" + modules.get(i).id(), button -> {
                if (index == focus) {
                    if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) startBinding(modules.get(index));
                    else toggle(modules.get(index));
                } else {
                    setFocus(index, true);
                }
            }));
        }
    }

    private void drawCard(DrawContext c, Module module, float x, float y, float scale, float alpha, boolean focused, int accent) {
        if (alpha <= 0.02F) return;
        Matrix3x2fStack m = c.getMatrices();
        boolean on = module.enabled();
        float h = hoverValue("card:" + module.id());
        m.pushMatrix();
        m.translate(x, y);
        m.scale(scale * (1.0F + 0.03F * h), scale * (1.0F + 0.03F * h));
        int w = CARD_W, hgt = CARD_H, left = -w / 2, top = -hgt / 2;
        if (focused) {
            for (int g = 3; g >= 1; g--) {
                SmoothGuiShapes.rect(c, left - g * 4, top - g * 4, w + g * 8, hgt + g * 8, 14 + g * 4,
                    a(alpha * (on ? 0.10F : 0.05F)) << 24 | (accent & 0xFFFFFF), false);
            }
        }
        SmoothGuiShapes.rect(c, left, top, w, hgt, 12, a(alpha * 0.96F) << 24 | (focused ? 0x161922 : 0x111318), false);
        c.fillGradient(left + 2, top + 2, left + w - 2, top + 46, a(alpha * (on ? 0.22F : 0.06F)) << 24 | (accent & 0xFFFFFF), 0);
        SmoothGuiShapes.rect(c, left, top, w, hgt, 12, a(alpha * (on ? 0.95F : 0.18F + 0.25F * h)) << 24 | (on ? accent & 0xFFFFFF : 0xFFFFFF), true);

        // Icon (or monogram) in a ring.
        int ring = 34;
        SmoothGuiShapes.circle(c, 0, top + 30, ring / 2, a(alpha) << 24 | (on ? accent & 0xFFFFFF : 0x22252E));
        SmoothGuiShapes.circle(c, 0, top + 30, ring / 2 - 2, a(alpha) << 24 | 0x0D0F14);
        Identifier icon = Identifier.of(KhanhClient.MOD_ID, "textures/gui/module/" + module.id() + ".png");
        boolean hasIcon = ICONS.computeIfAbsent(module.id(), id -> client.getResourceManager().getResource(icon).isPresent());
        int iconColour = a(alpha) << 24 | (on ? 0xFFFFFF : 0xB8BDC8);
        if (hasIcon) {
            c.drawTexture(RenderPipelines.GUI_TEXTURED, icon, -9, top + 21, 0F, 0F, 18, 18, 48, 48, 48, 48, iconColour);
        } else {
            String mono = monogram(module.name());
            text(c, mono, -width(mono, 0.9F) / 2, top + 26, 0.9F, iconColour);
        }

        // Name and description.
        String name = module.name();
        float nameScale = Math.min(0.92F, (w - 14) / (float) Math.max(1, width(name, 1.0F)));
        text(c, name, -width(name, nameScale) / 2, top + 56, nameScale, a(alpha) << 24 | 0xFFFFFF);
        List<OrderedText> lines = textRenderer.wrapLines(StringVisitable.plain(module.description()), Math.round((w - 16) / 0.58F));
        for (int l = 0; l < Math.min(4, lines.size()); l++) {
            m.pushMatrix();
            m.translate(0, top + 72 + l * 8);
            m.scale(0.58F, 0.58F);
            c.drawText(textRenderer, lines.get(l), -textRenderer.getWidth(lines.get(l)) / 2, 0, a(alpha * 0.62F) << 24 | 0xFFFFFF, false);
            m.popMatrix();
        }

        // Status pill and keybind.
        int pw = 44, px = -pw / 2, py = top + hgt - 22;
        SmoothGuiShapes.rect(c, px, py, pw, 14, 7, a(alpha) << 24 | (on ? accent & 0xFFFFFF : 0x1C1F27), false);
        String state = on ? "ON" : "OFF";
        text(c, state, -width(state, 0.62F) / 2, py + 4, 0.62F, a(alpha) << 24 | (on ? 0x06070A : 0x9AA0AC));
        if (module.keybind() >= 0) {
            String key = keyName(module.keybind());
            text(c, key, left + w - 8 - width(key, 0.5F), top + 8, 0.5F, a(alpha * 0.7F) << 24 | 0xFFFFFF);
        }
        m.popMatrix();
    }

    private void drawSettings(DrawContext c, float show, int accent) {
        Module module = focused();
        if (module == null) return;
        int top = settingsTop;
        int bottom = settingsBottom;
        int panelW = Math.min(width - 40, 4 * (CONTROL_W + CONTROL_GAP) - CONTROL_GAP + 24);
        int left = width / 2 - panelW / 2;
        if (bottom - top < 40) return;
        SmoothGuiShapes.rect(c, left, top, panelW, bottom - top, 12, a(show * 0.82F) << 24 | 0x0C0E13, false);
        SmoothGuiShapes.rect(c, left, top, panelW, bottom - top, 12, a(show * 0.14F) << 24 | 0xFFFFFF, true);
        text(c, module.name().toUpperCase(Locale.ROOT), left + 12, top + 9, 0.72F, a(show) << 24 | 0xFFFFFF);
        text(c, "SETTINGS", left + 16 + width(module.name().toUpperCase(Locale.ROOT), 0.72F), top + 9, 0.72F, a(show * 0.45F) << 24 | (accent & 0xFFFFFF));

        // Keybind button in the header.
        String bindText = binding == module ? "Press a key..." : "Key: " + (module.keybind() < 0 ? "None" : keyName(module.keybind()));
        int bw = width(bindText, 0.62F) + 16, bx = left + panelW - 12 - bw, by = top + 5;
        float bh = hoverValue("bind");
        SmoothGuiShapes.rect(c, bx, by, bw, 15, 7, a(show * (0.3F + 0.4F * bh)) << 24 | (binding == module ? accent & 0xFFFFFF : 0x2A2E38), false);
        text(c, bindText, bx + 8, by + 4, 0.62F, a(show) << 24 | 0xFFFFFF);
        hits.add(new Hit(bx, by, bw, 15, "bind", button -> startBinding(module)));

        // Option grid.
        List<ModuleOption> options = module.options();
        int columns = Math.max(1, (panelW - 24 + CONTROL_GAP) / (CONTROL_W + CONTROL_GAP));
        int gridTop = top + 28, gridBottom = bottom - 8;
        int rows = (options.size() + columns - 1) / columns;
        settingsMax = Math.max(0, rows * (CONTROL_H + CONTROL_GAP) - CONTROL_GAP - (gridBottom - gridTop));
        settingsTarget = Math.max(0, Math.min(settingsTarget, settingsMax));
        float scroll = settingsScroll.to(settingsTarget).value();
        if (options.isEmpty()) {
            text(c, "No settings: just switch it on.", left + 12, gridTop + 4, 0.66F, a(show * 0.5F) << 24 | 0xFFFFFF);
            return;
        }
        int gridLeft = left + (panelW - (columns * (CONTROL_W + CONTROL_GAP) - CONTROL_GAP)) / 2;
        c.enableScissor(left + 2, gridTop, left + panelW - 2, gridBottom);
        for (int i = 0; i < options.size(); i++) {
            int x = gridLeft + (i % columns) * (CONTROL_W + CONTROL_GAP);
            int y = Math.round(gridTop + (i / columns) * (CONTROL_H + CONTROL_GAP) - scroll);
            if (y + CONTROL_H < gridTop || y > gridBottom) continue;
            drawControl(c, module, options.get(i), x, y, show, accent, y >= gridTop && y + CONTROL_H <= gridBottom);
        }
        c.disableScissor();
        if (settingsMax > 0) {
            int track = gridBottom - gridTop;
            int thumb = Math.max(16, Math.round(track * track / (float) (track + settingsMax)));
            int ty = gridTop + Math.round((track - thumb) * (scroll / settingsMax));
            c.fill(left + panelW - 5, ty, left + panelW - 3, ty + thumb, a(show * 0.6F) << 24 | (accent & 0xFFFFFF));
        }
    }

    private void drawControl(DrawContext c, Module module, ModuleOption option, int x, int y, float show, int accent, boolean clickable) {
        String key = "opt:" + module.id() + ":" + option.id();
        float h = hoverValue(key);
        SmoothGuiShapes.rect(c, x, y, CONTROL_W, CONTROL_H, 8, a(show * (0.75F + 0.15F * h)) << 24 | 0x151820, false);
        if (h > 0.05F) SmoothGuiShapes.rect(c, x, y, CONTROL_W, CONTROL_H, 8, a(show * 0.35F * h) << 24 | (accent & 0xFFFFFF), true);
        String label = option.label();
        text(c, trimTo(label, CONTROL_W - 70, 0.66F), x + 9, y + 6, 0.66F, a(show) << 24 | 0xFFFFFF);
        int white = a(show) << 24 | 0xFFFFFF;
        switch (option.kind()) {
            case BOOLEAN -> {
                boolean on = option.booleanValue();
                int sx = x + CONTROL_W - 34, sy = y + CONTROL_H / 2 - 6;
                SmoothGuiShapes.rect(c, sx, sy, 24, 12, 6, a(show) << 24 | (on ? accent & 0xFFFFFF : 0x2A2E38), false);
                SmoothGuiShapes.circle(c, on ? sx + 18 : sx + 6, sy + 6, 4, white);
                if (clickable) hits.add(new Hit(x, y, CONTROL_W, CONTROL_H, key, button -> {
                    option.toggle();
                    play("ui_toggle", option.booleanValue() ? 1.18F : 0.84F, 0.24F);
                    save();
                }));
            }
            case NUMBER -> {
                String value = option.displayValue();
                text(c, value, x + CONTROL_W - 9 - width(value, 0.62F), y + 6, 0.62F, a(show) << 24 | (accent & 0xFFFFFF));
                int bx = x + 9, bw = CONTROL_W - 18, by = y + CONTROL_H - 10;
                c.fill(bx, by, bx + bw, by + 2, a(show * 0.6F) << 24 | 0x2A2E38);
                int fill = Math.round(bw * (float) option.ratio());
                c.fill(bx, by, bx + fill, by + 2, a(show) << 24 | (accent & 0xFFFFFF));
                SmoothGuiShapes.circle(c, bx + fill, by + 1, 3 + Math.round(h), white);
                if (clickable) hits.add(new Hit(x, y, CONTROL_W, CONTROL_H, key, button -> {
                    dragging = option;
                    dragX = bx;
                    dragW = bw;
                    dragTo(mouseX);
                    play("ui_select", 1.05F, 0.14F);
                }));
            }
            case CHOICE -> {
                String value = option.choiceValue();
                int vy = y + CONTROL_H - 13;
                text(c, "<", x + 9, vy, 0.9F, a(show * 0.6F) << 24 | 0xFFFFFF);
                text(c, ">", x + CONTROL_W - 14, vy, 0.9F, a(show * 0.6F) << 24 | 0xFFFFFF);
                String shown = trimTo(value, CONTROL_W - 40, 0.64F);
                text(c, shown, x + CONTROL_W / 2 - width(shown, 0.64F) / 2, vy + 1, 0.64F, a(show) << 24 | (accent & 0xFFFFFF));
                if (clickable) hits.add(new Hit(x, y, CONTROL_W, CONTROL_H, key, button -> {
                    boolean back = button == GLFW.GLFW_MOUSE_BUTTON_RIGHT || mouseX < x + CONTROL_W / 2;
                    option.cycle(back ? -1 : 1);
                    play("ui_select", back ? 0.9F : 1.1F, 0.2F);
                    save();
                }));
            }
            case COLOR -> {
                int colour = option.colorValue();
                SmoothGuiShapes.rect(c, x + CONTROL_W - 26, y + 5, 16, 10, 4, a(show) << 24 | (colour & 0xFFFFFF), false);
                int bx = x + 9, bw = CONTROL_W - 18, by = y + CONTROL_H - 11;
                for (int k = 0; k < bw; k += 2) {
                    int hue = java.awt.Color.HSBtoRGB(k / (float) bw, 0.85F, 1.0F) & 0xFFFFFF;
                    c.fill(bx + k, by, bx + k + 2, by + 4, a(show) << 24 | hue);
                }
                float[] hsb = java.awt.Color.RGBtoHSB(colour >> 16 & 255, colour >> 8 & 255, colour & 255, null);
                int knob = bx + Math.round(hsb[0] * bw);
                c.fill(knob - 1, by - 2, knob + 1, by + 6, white);
                if (clickable) hits.add(new Hit(x, y, CONTROL_W, CONTROL_H, key, button -> {
                    if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                        option.setColor(0xFFFFFFFF);   // right click: white
                        save();
                        return;
                    }
                    dragging = option;
                    dragX = bx;
                    dragW = bw;
                    dragTo(mouseX);
                }));
            }
            case TEXT -> {
                boolean editing = editingText == option;
                int fx = x + 9, fy = y + CONTROL_H - 15, fw = CONTROL_W - 18;
                SmoothGuiShapes.rect(c, fx, fy, fw, 11, 4, a(show) << 24 | (editing ? 0x0B0D12 : 0x1C1F27), false);
                if (editing) SmoothGuiShapes.rect(c, fx, fy, fw, 11, 4, a(show) << 24 | (accent & 0xFFFFFF), true);
                String value = option.textValue() + (editing && (System.currentTimeMillis() / 500) % 2 == 0 ? "_" : "");
                text(c, trimTo(value.isEmpty() ? "Click to type" : value, fw - 8, 0.58F), fx + 4, fy + 3, 0.58F,
                    a(show * (value.isEmpty() ? 0.4F : 1.0F)) << 24 | 0xFFFFFF);
                if (clickable) hits.add(new Hit(x, y, CONTROL_W, CONTROL_H, key, button -> {
                    editingText = editing ? null : option;
                    play("ui_select", 1.0F, 0.18F);
                }));
            }
            case ACTION -> {
                SmoothGuiShapes.rect(c, x + CONTROL_W - 54, y + CONTROL_H / 2 - 7, 44, 14, 7, a(show) << 24 | (accent & 0xFFFFFF), false);
                text(c, "RUN", x + CONTROL_W - 32 - width("RUN", 0.6F) / 2, y + CONTROL_H / 2 - 3, 0.6F, a(show) << 24 | 0x06070A);
                if (clickable) hits.add(new Hit(x, y, CONTROL_W, CONTROL_H, key, button -> {
                    play("ui_open", 1.1F, 0.24F);
                    option.trigger();
                }));
            }
        }
        if (h > 0.5F) hint = option.description();
    }

    private void drawBottomBar(DrawContext c, float show, int bar) {
        String text = !hint.isEmpty() ? hint : binding != null ? "Press a key to bind " + binding.name() + "  ·  Esc clears it"
            : "Scroll or arrow keys to browse  ·  Click the middle card to switch it  ·  Right-click it to bind  ·  Type to search";
        text(c, trimTo(text, width - 40, 0.62F), 14, height - bar / 2 - 3, 0.62F, a(show * 0.6F) << 24 | 0xFFFFFF);
        String version = "v" + KhanhClient.VERSION;
        text(c, version, width - 12 - width(version, 0.62F), height - bar / 2 - 3, 0.62F, a(show * 0.35F) << 24 | 0xFFFFFF);
    }

    // ---------------------------------------------------------------- input

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (closingAt >= 0) return true;
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit hit = hits.get(i);
            if (hit.contains(click.x(), click.y())) {
                if (!hit.key().startsWith("opt:") || editingText == null || !hit.key().endsWith(editingText.id())) {
                    if (editingText != null && !hit.key().startsWith("opt:")) editingText = null;
                }
                hit.action().accept(click.button());
                return true;
            }
        }
        editingText = null;
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseDragged(Click click, double deltaX, double deltaY) {
        if (dragging != null) {
            dragTo(click.x());
            return true;
        }
        return super.mouseDragged(click, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(Click click) {
        if (dragging != null) {
            dragging = null;
            save();
            return true;
        }
        return super.mouseReleased(click);
    }

    private void dragTo(double x) {
        if (dragging == null) return;
        double ratio = Math.max(0.0, Math.min(1.0, (x - dragX) / Math.max(1, dragW)));
        if (dragging.kind() == ModuleOption.Kind.COLOR) {
            dragging.setColor(0xFF000000 | (java.awt.Color.HSBtoRGB((float) ratio, 0.85F, 1.0F) & 0xFFFFFF));
        } else {
            dragging.setFromRatio(ratio);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (mouseY > settingsTop && settingsMax > 0) {
            settingsTarget = Math.max(0, Math.min(settingsMax, settingsTarget - (float) vertical * 22));
            return true;
        }
        setFocus(focus + (vertical < 0 ? 1 : -1), true);
        return true;
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        int key = input.key();
        if (binding != null) {
            binding.setKeybind(key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_BACKSPACE ? -1 : key);
            play("ui_toggle", 1.2F, 0.22F);
            binding = null;
            save();
            return true;
        }
        if (editingText != null) {
            if (key == GLFW.GLFW_KEY_BACKSPACE) editingText.deleteLastTextCharacter();
            else if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER || key == GLFW.GLFW_KEY_ESCAPE) {
                editingText = null;
                save();
            } else if (key == GLFW.GLFW_KEY_V && (input.modifiers() & GLFW.GLFW_MOD_CONTROL | input.modifiers() & GLFW.GLFW_MOD_SUPER) != 0) {
                editingText.appendText(client.keyboard.getClipboard());
            }
            return true;
        }
        switch (key) {
            case GLFW.GLFW_KEY_ESCAPE -> {
                if (!search.isEmpty()) {
                    search = "";
                    setFocus(0, false);
                } else close();
                return true;
            }
            case GLFW.GLFW_KEY_BACKSPACE -> {
                if (!search.isEmpty()) {
                    search = search.substring(0, search.length() - 1);
                    setFocus(0, false);
                }
                return true;
            }
            case GLFW.GLFW_KEY_LEFT -> { setFocus(focus - 1, true); return true; }
            case GLFW.GLFW_KEY_RIGHT -> { setFocus(focus + 1, true); return true; }
            case GLFW.GLFW_KEY_TAB -> { setTab(tab + ((input.modifiers() & GLFW.GLFW_MOD_SHIFT) != 0 ? -1 : 1)); return true; }
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                Module module = focused();
                if (module != null) toggle(module);
                return true;
            }
            default -> { }
        }
        return super.keyPressed(input);
    }

    @Override
    public boolean charTyped(CharInput input) {
        String typed = input.asString();
        if (editingText != null) {
            editingText.appendText(typed);
            return true;
        }
        if (binding != null || typed.isBlank() && search.isEmpty()) return true;
        if (search.length() < 24) {
            search += typed;
            focus = 0;
            carousel.snap(1.5F);
            carousel.to(0);
            tabChangedAt = System.nanoTime();
            play("ui_hover", 1.2F, 0.12F);
        }
        return true;
    }

    // ---------------------------------------------------------------- actions

    private void toggle(Module module) {
        module.toggle();
        dev.khanh.feature.KhanhSounds.toggle(client, module.enabled());
        save();
    }

    private void startBinding(Module module) {
        binding = binding == module ? null : module;
        editingText = null;
        play("ui_select", 1.25F, 0.22F);
    }

    private void save() {
        ConfigManager.save(KhanhClient.modules());
    }

    @Override
    public void close() {
        if (closingAt < 0) {
            closingAt = System.nanoTime();
            play("ui_back", 1.0F, 0.24F);
            save();
        }
    }

    @Override
    protected void init() {
        if (openedAt == 0L) openedAt = System.nanoTime();
        play("ui_open", 1.0F, 0.26F);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    // ---------------------------------------------------------------- helpers

    /** Hover springs for each control, plus a soft tick when the pointer moves onto a new one. */
    private float hoverValue(String key) {
        boolean over = false;
        for (Hit hit : hits) if (hit.key().equals(key) && hit.contains(mouseX, mouseY)) over = true;
        return hover.computeIfAbsent(key, k -> new Spring(Spring.SMOOTH, 0.0F)).to(over || key.equals(hoverKey) ? 1.0F : 0.0F).value();
    }

    private void updateHoverSound() {
        String now = "";
        for (int i = hits.size() - 1; i >= 0; i--) {
            if (hits.get(i).contains(mouseX, mouseY)) {
                now = hits.get(i).key();
                break;
            }
        }
        if (!now.isEmpty() && !now.equals(hoverKey)) play("ui_hover", 1.0F + (now.hashCode() & 7) * 0.02F, 0.10F);
        hoverKey = now;
    }

    private void play(String sound, float pitch, float volume) {
        if (KhanhFeatures.enabled("client_sounds") && KhanhFeatures.bool("client_sounds", "muted", false)) return;
        MinecraftClient.getInstance().getSoundManager().play(PositionedSoundInstance.ui(
            SoundEvent.of(Identifier.of(KhanhClient.MOD_ID, sound)), pitch, volume));
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

    private String trimTo(String value, int max, float scale) {
        if (width(value, scale) <= max) return value;
        String cut = value;
        while (cut.length() > 1 && width(cut + "…", scale) > max) cut = cut.substring(0, cut.length() - 1);
        return cut + "…";
    }

    private static String monogram(String name) {
        StringBuilder out = new StringBuilder();
        for (String word : name.split(" ")) if (!word.isEmpty() && out.length() < 2) out.append(Character.toUpperCase(word.charAt(0)));
        return out.toString();
    }

    private static String keyName(int key) {
        return InputUtil.Type.KEYSYM.createFromCode(key).getLocalizedText().getString().toUpperCase(Locale.ROOT);
    }

    private static int lerpColour(int a, int b, float t) {
        int r = Math.round((a >> 16 & 255) + ((b >> 16 & 255) - (a >> 16 & 255)) * t);
        int g = Math.round((a >> 8 & 255) + ((b >> 8 & 255) - (a >> 8 & 255)) * t);
        int bl = Math.round((a & 255) + ((b & 255) - (a & 255)) * t);
        return r << 16 | g << 8 | bl;
    }

    private static int a(float alpha) {
        return Math.round(clamp(alpha) * 255.0F);
    }

    private static float clamp(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }

    private static float ease(float x) {
        return 1.0F - (float) Math.pow(1.0F - x, 3.0);
    }
}
