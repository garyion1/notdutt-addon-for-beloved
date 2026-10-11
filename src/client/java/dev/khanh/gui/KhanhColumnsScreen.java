package dev.khanh.gui;

import dev.khanh.KhanhClient;
import dev.khanh.config.ConfigManager;
import dev.khanh.feature.KhanhFeatures;
import dev.khanh.feature.KhanhSounds;
import dev.khanh.feature.VisualTheme;
import dev.khanh.module.KhanhCatalog;
import dev.khanh.module.Module;
import dev.khanh.module.ModuleOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.IntConsumer;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.util.InputUtil;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.Style;
import net.minecraft.text.StyleSpriteSource;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

/**
 * Khanh Client's menu, Krypton style: a row of flat dark columns across the top of the screen, one
 * per section, a centred light-blue title and the modules listed in a small blocky font with a
 * tiny switch on the right. Enabled modules get a blue edge and blue name. Left click switches a
 * module, right click drops its settings down under it, middle click binds it. Drag a column by
 * its title; scroll a long column; type to search. The CLIENT column opens the HUD editor.
 */
public final class KhanhColumnsScreen extends Screen implements KhanhClickGui {
    private static final int PANEL_W = 104, GAP = 12, MARGIN_X = 10, MARGIN_Y = 8;
    private static final int HEADER_H = 14, ROW_H = 13, SETTING_H = 12;
    private static final float TEXT = 1.0F;
    /** Default GUI blue, used where the theme colour is not wanted (the editor's hint bar). */
    public static final int ACCENT = 0x3B9BFF;
    private static final int PANEL_BG = 0x141418, HEADER_BG = 0x0F0F12, ROW_TEXT = 0xE4E4E8, TRACK_OFF = 0x3A3A42,
        KNOB_OFF = 0x8C8C96;
    private static final String HUD_EDITOR = "hud_editor";
    private static final Map<String, int[]> POSITIONS = new HashMap<>();
    private final Map<String, Long> toggledAt = new HashMap<>();
    private static final Set<String> EXPANDED = new HashSet<>();

    private record Panel(String title, List<Module> modules) { }

    private record Hit(int x, int y, int w, int h, String key, IntConsumer action) {
        boolean contains(double mx, double my) { return mx >= x && my >= y && mx < x + w && my < y + h; }
    }

    private final Screen parent;
    private final List<Panel> panels = new ArrayList<>();
    private final List<Hit> hits = new ArrayList<>();
    private final Map<String, Spring> springs = new HashMap<>();
    private final Map<String, Float> scroll = new HashMap<>();
    private final long openedAt = System.nanoTime();
    private long closingAt = -1L;
    private String search = "";
    private String draggingPanel;
    private int dragOffsetX, dragOffsetY;
    private ModuleOption sliding, editing;
    private int slideX, slideW;
    private Module binding;
    private String hoverKey = "";
    private int mouseX, mouseY;

    public KhanhColumnsScreen(Screen parent) {
        super(Text.literal("Khanh Client"));
        this.parent = parent;
        for (KhanhCatalog.Section section : KhanhCatalog.SECTIONS) {
            List<Module> modules = new ArrayList<>();
            for (KhanhCatalog.Entry entry : section.entries()) {
                Module module = KhanhClient.modules().byId(entry.id());
                if (module != null) modules.add(module);
            }
            panels.add(new Panel(section.title(), modules));
        }
        List<Module> client = new ArrayList<>();
        for (String id : List.of("hud", "intro", "client_sounds")) {
            Module module = KhanhClient.modules().byId(id);
            if (module != null) client.add(module);
        }
        panels.add(new Panel("CLIENT", client));
    }

    // ---------------------------------------------------------------- layout

    /** Columns sit side by side from the top-left, wrapping onto a second row on narrow screens. */
    private int[] position(int index) {
        Panel panel = panels.get(index);
        return POSITIONS.computeIfAbsent(panel.title(), key -> {
            int perRow = Math.max(1, (width - MARGIN_X * 2 + GAP) / (PANEL_W + GAP));
            int column = index % perRow, row = index / perRow;
            return new int[] {MARGIN_X + column * (PANEL_W + GAP), MARGIN_Y + row * 190};
        });
    }

    private List<Module> visible(Panel panel) {
        if (search.isEmpty()) return panel.modules();
        String query = search.toLowerCase(Locale.ROOT);
        return panel.modules().stream().filter(m -> m.name().toLowerCase(Locale.ROOT).contains(query)).toList();
    }

    private static boolean isClientPanel(Panel panel) {
        return panel.title().equals("CLIENT");
    }

    // ---------------------------------------------------------------- rendering

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) { }

    @Override
    public void render(DrawContext c, int mouseX, int mouseY, float delta) {
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        hits.clear();
        float open = ease(clamp((System.nanoTime() - openedAt) / 2.2e8F));
        float shut = closingAt < 0 ? 0 : clamp((System.nanoTime() - closingAt) / 1.4e8F);
        if (shut >= 1.0F) {
            client.setScreen(parent);
            return;
        }
        float show = open * (1 - shut);
        int accent = VisualTheme.preset() & 0xFFFFFF;

        float since = (System.nanoTime() - openedAt) / 1.0e9F;
        for (int i = 0; i < panels.size(); i++) {
            float stagger = ease(clamp((since - i * 0.035F) / 0.25F));
            drawPanel(c, i, show * stagger, (1 - stagger) * -8 + ease(shut) * -8, accent);
        }

        // Only shown while binding a key or searching, so the screen stays as clean as the panels.
        String line = binding != null ? "PRESS A KEY FOR " + binding.name().toUpperCase(Locale.ROOT) + "  (ESC CLEARS)"
            : !search.isEmpty() ? "SEARCH: " + search.toUpperCase(Locale.ROOT) + "_" : null;
        if (line != null) {
            int lw = width(line, 0.62F) + 14;
            int lx = width / 2 - lw / 2, ly = height - 22;
            c.fill(lx, ly, lx + lw, ly + 13, a(0.88F * show) << 24 | PANEL_BG);
            c.fill(lx, ly, lx + 2, ly + 13, a(show) << 24 | accent);
            text(c, line, lx + 8, ly + 3, 0.62F, a(show) << 24 | ROW_TEXT);
        }

        String now = "";
        for (int i = hits.size() - 1; i >= 0; i--) if (hits.get(i).contains(mouseX, mouseY)) { now = hits.get(i).key(); break; }
        if (!now.isEmpty() && !now.equals(hoverKey) && !now.startsWith("drag")) play("ui_hover", 1.0F + (now.hashCode() & 7) * 0.02F, 0.06F);
        hoverKey = now;
    }

    /** The light blue of the column titles: the GUI colour washed towards white. */
    private static int titleColour(int accent) {
        return lerp(accent, 0xFFFFFF, 0.55F);
    }

    private void drawPanel(DrawContext c, int index, float alpha, float drop, int accent) {
        if (alpha <= 0.01F) return;
        Panel panel = panels.get(index);
        int[] pos = position(index);
        int x = pos[0], y = Math.round(pos[1] + drop);
        List<Module> modules = visible(panel);
        boolean editorRow = isClientPanel(panel) && search.isEmpty();

        // Body height: rows plus any open settings, clipped to the screen with scrolling.
        int content = editorRow ? ROW_H : 0;
        for (Module module : modules) content += ROW_H + Math.round(settingsHeight(module) * springValue("open:" + module.id()));
        int maxBody = Math.max(40, height - y - HEADER_H - 8);
        int body = Math.min(content, maxBody) + 4;
        float offset = scroll.getOrDefault(panel.title(), 0.0F);
        offset = Math.max(0, Math.min(offset, Math.max(0, content - maxBody)));
        scroll.put(panel.title(), offset);

        // Flat dark column with softly rounded corners; the title bar is a shade darker.
        int total = HEADER_H + body;
        SmoothGuiShapes.rect(c, x, y, PANEL_W, total, 3, a(0.86F * alpha) << 24 | PANEL_BG, false);
        SmoothGuiShapes.rect(c, x, y, PANEL_W, HEADER_H, 3, a(0.55F * alpha) << 24 | HEADER_BG, false);
        String title = panel.title();
        int tw = width(title, TEXT);
        text(c, title, x + PANEL_W / 2 - tw / 2, y + 3, TEXT, a(alpha) << 24 | titleColour(accent));
        hits.add(new Hit(x, y, PANEL_W, HEADER_H, "drag:" + title, button -> {
            draggingPanel = title;
            dragOffsetX = mouseX - pos[0];
            dragOffsetY = mouseY - pos[1];
        }));

        c.enableScissor(x, y + HEADER_H, x + PANEL_W, y + HEADER_H + body);
        clipTop = y + HEADER_H;
        clipBottom = y + HEADER_H + body;
        int rowY = Math.round(y + HEADER_H + 1 - offset);
        for (Module module : modules) {
            drawRow(c, module, x, rowY, alpha, accent);
            rowY += ROW_H;
            float open = spring("open:" + module.id(), EXPANDED.contains(module.id()) ? 1 : 0);
            if (open > 0.01F) {
                int full = settingsHeight(module);
                int shown = Math.round(full * open);
                c.enableScissor(x, rowY, x + PANEL_W, rowY + shown);
                drawSettings(c, module, x, rowY - Math.round(full * (1 - open) * 0.35F), alpha * open, accent);
                c.disableScissor();
                rowY += shown;
            }
        }
        if (editorRow) drawEditorRow(c, x, rowY, alpha, accent);
        c.disableScissor();
    }

    private void drawRow(DrawContext c, Module module, int x, int y, float alpha, int accent) {
        String key = "row:" + module.id();
        boolean over = hoverKey.equals(key);
        float h = spring(key, over ? 1 : 0);
        boolean on = module.enabled();
        float lit = spring("on:" + module.id(), on ? 1 : 0);
        if (h > 0.01F) c.fill(x, y, x + PANEL_W, y + ROW_H, a(0.06F * h * alpha) << 24 | 0xFFFFFF);
        // Enabled: a blue edge on the left and a blue-tinted name.
        if (lit > 0.01F) c.fill(x, y + 1, x + 2, y + ROW_H - 1, a(lit * alpha) << 24 | accent);
        Long at = toggledAt.get(module.id());
        if (at != null) {
            float k = (System.nanoTime() - at) / 3.5e8F;
            if (k < 1) c.fill(x, y, x + PANEL_W, y + ROW_H, a((1 - k) * 0.16F * alpha) << 24 | accent);
            else toggledAt.remove(module.id());
        }
        String name = module.name().toUpperCase(Locale.ROOT);
        int colour = lerp(lerp(ROW_TEXT, 0xFFFFFF, h), titleColour(accent), lit);
        int nameX = x + 6;
        text(c, trim(name, x + PANEL_W - 22 - nameX, TEXT), nameX, y + 3, TEXT, a(alpha) << 24 | colour);
        drawSwitch(c, x + PANEL_W - 17, y + 4, lit, alpha, accent);
        if (inClip(y)) hits.add(new Hit(x, y, PANEL_W, ROW_H, key, button -> {
            if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                if (!EXPANDED.remove(module.id())) EXPANDED.add(module.id());
                play("ui_select", EXPANDED.contains(module.id()) ? 1.15F : 0.9F, 0.2F);
            } else if (button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE) {
                binding = module;
                play("ui_select", 1.25F, 0.2F);
            } else {
                module.toggle();
                toggledAt.put(module.id(), System.nanoTime());
                KhanhSounds.toggle(client, module.enabled());
                save();
            }
        }));
    }

    /** The little switch at the end of each row: a grey pill when off, a blue one with its knob right when on. */
    private static void drawSwitch(DrawContext c, int sx, int sy, float lit, float alpha, int accent) {
        int track = lerp(TRACK_OFF, lerp(accent, 0x101018, 0.45F), lit);
        SmoothGuiShapes.rect(c, sx, sy, 11, 5, 2, a(alpha) << 24 | track, false);
        int knob = Math.round(sx + 2.5F + lit * 6);
        SmoothGuiShapes.circle(c, knob, sy + 2, 2, a(alpha) << 24 | lerp(KNOB_OFF, lerp(accent, 0xFFFFFF, 0.2F), lit));
    }

    /** Last row of the CLIENT column: opens the HUD editor. */
    private void drawEditorRow(DrawContext c, int x, int y, float alpha, int accent) {
        boolean over = hoverKey.equals(HUD_EDITOR);
        float h = spring(HUD_EDITOR, over ? 1 : 0);
        if (h > 0.01F) c.fill(x, y, x + PANEL_W, y + ROW_H, a(0.06F * h * alpha) << 24 | 0xFFFFFF);
        text(c, "HUD EDITOR", x + 6, y + 3, TEXT, a(alpha) << 24 | lerp(ROW_TEXT, 0xFFFFFF, h));
        text(c, ">", x + PANEL_W - 10 + Math.round(h * 2), y + 3, TEXT, a(alpha) << 24 | lerp(KNOB_OFF, titleColour(accent), h));
        if (inClip(y)) hits.add(new Hit(x, y, PANEL_W, ROW_H, HUD_EDITOR, button -> {
            save();
            play("ui_open", 1.1F, 0.22F);
            client.setScreen(new KhanhHudEditorScreen(new KhanhColumnsScreen(parent)));
        }));
    }

    /** Settings shown per module: the client modules only show what Khanh actually uses. */
    private static final Map<String, Set<String>> ONLY = Map.of(
        "visuals", Set.of("finder_seconds", "finder_sound", "blur"),
        "intro", Set.of("sound"),
        "hud", Set.of("watermark", "modules", "fps", "coords", "armor", "keystrokes", "compass", "radar_range", "radar_mobs", "hud_scale"));

    private static List<ModuleOption> options(Module module) {
        Set<String> only = ONLY.get(module.id());
        return only == null ? module.options() : module.options().stream().filter(o -> only.contains(o.id())).toList();
    }

    private int settingsHeight(Module module) {
        int h = SETTING_H + 2;   // keybind row
        for (ModuleOption option : options(module)) h += option.kind() == ModuleOption.Kind.NUMBER || option.kind() == ModuleOption.Kind.COLOR ? SETTING_H + 4 : SETTING_H;
        return h + 3;
    }

    /** The settings drawn under an expanded module; returns the y below them. */
    private int drawSettings(DrawContext c, Module module, int x, int y, float alpha, int accent) {
        int height = settingsHeight(module);
        c.fill(x, y, x + PANEL_W, y + height, a(0.5F * alpha) << 24 | 0x08080A);
        c.fill(x + 3, y + 1, x + 4, y + height - 1, a(0.45F * alpha) << 24 | accent);
        int left = x + 8, right = x + PANEL_W - 6;
        float s = 0.62F;
        int white = a(alpha) << 24 | ROW_TEXT, dim = a(alpha * 0.55F) << 24 | 0xFFFFFF, acc = a(alpha) << 24 | titleColour(accent);

        // Keybind.
        String bind = binding == module ? "..." : module.keybind() < 0 ? "NONE" : keyName(module.keybind());
        text(c, "BIND", left, y + 4, s, dim);
        text(c, bind, right - width(bind, s), y + 4, s, binding == module ? acc : white);
        if (inClip(y)) hits.add(new Hit(x, y, PANEL_W, SETTING_H, "bind:" + module.id(), button -> {
            binding = binding == module ? null : module;
            play("ui_select", 1.25F, 0.2F);
        }));
        y += SETTING_H + 2;

        for (ModuleOption option : options(module)) {
            String key = "opt:" + module.id() + ":" + option.id();
            String label = trim(option.label().toUpperCase(Locale.ROOT), PANEL_W - 52, s);
            text(c, label, left, y + 3, s, white);
            switch (option.kind()) {
                case BOOLEAN -> {
                    boolean on = option.booleanValue();
                    drawSwitch(c, right - 11, y + 3, on ? 1 : 0, alpha, accent);
                    if (inClip(y)) hits.add(new Hit(x, y, PANEL_W, SETTING_H, key, button -> {
                        option.toggle();
                        play("ui_toggle", option.booleanValue() ? 1.18F : 0.84F, 0.2F);
                        save();
                    }));
                    y += SETTING_H;
                }
                case NUMBER -> {
                    String value = option.displayValue();
                    text(c, value, right - width(value, s), y + 3, s, acc);
                    int by = y + SETTING_H, bw = right - left;
                    c.fill(left, by, right, by + 2, a(alpha) << 24 | TRACK_OFF);
                    c.fill(left, by, left + Math.round(bw * (float) option.ratio()), by + 2, a(alpha) << 24 | accent);
                    final int barLeft = left;
                    if (inClip(y)) hits.add(new Hit(x, y, PANEL_W, SETTING_H + 4, key, button -> {
                        sliding = option;
                        slideX = barLeft;
                        slideW = bw;
                        slide(mouseX);
                    }));
                    y += SETTING_H + 4;
                }
                case CHOICE -> {
                    String value = trim(option.choiceValue().toUpperCase(Locale.ROOT), 52, s);
                    text(c, value, right - width(value, s), y + 3, s, acc);
                    if (inClip(y)) hits.add(new Hit(x, y, PANEL_W, SETTING_H, key, button -> {
                        option.cycle(button == GLFW.GLFW_MOUSE_BUTTON_RIGHT ? -1 : 1);
                        play("ui_select", 1.05F, 0.18F);
                        save();
                    }));
                    y += SETTING_H;
                }
                case COLOR -> {
                    int colour = option.colorValue();
                    c.fill(right - 10, y + 3, right, y + 9, a(alpha) << 24 | (colour & 0xFFFFFF));
                    int by = y + SETTING_H, bw = right - left;
                    for (int k = 0; k < bw; k++) {
                        c.fill(left + k, by, left + k + 1, by + 3, a(alpha) << 24 | (java.awt.Color.HSBtoRGB(k / (float) bw, 0.85F, 1.0F) & 0xFFFFFF));
                    }
                    final int barLeft = left;
                    if (inClip(y)) hits.add(new Hit(x, y, PANEL_W, SETTING_H + 4, key, button -> {
                        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) { option.setColor(0xFFFFFFFF); save(); return; }
                        sliding = option;
                        slideX = barLeft;
                        slideW = bw;
                        slide(mouseX);
                    }));
                    y += SETTING_H + 4;
                }
                case TEXT -> {
                    boolean typing = editing == option;
                    String value = option.textValue() + (typing && (System.currentTimeMillis() / 500) % 2 == 0 ? "_" : "");
                    value = trim(value.isEmpty() ? "-" : value, 50, s);
                    text(c, value, right - width(value, s), y + 3, s, typing ? acc : dim);
                    if (inClip(y)) hits.add(new Hit(x, y, PANEL_W, SETTING_H, key, button -> {
                        editing = typing ? null : option;
                        play("ui_select", 1.0F, 0.18F);
                    }));
                    y += SETTING_H;
                }
                case ACTION -> {
                    text(c, "RUN", right - width("RUN", s), y + 3, s, acc);
                    if (inClip(y)) hits.add(new Hit(x, y, PANEL_W, SETTING_H, key, button -> {
                        play("ui_open", 1.1F, 0.22F);
                        option.trigger();
                    }));
                    y += SETTING_H;
                }
            }
        }
        return y + 3;
    }

    // ---------------------------------------------------------------- input

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (closingAt >= 0) return true;
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit hit = hits.get(i);
            if (hit.contains(click.x(), click.y())) {
                if (editing != null && !hit.key().startsWith("opt:")) editing = null;
                hit.action().accept(click.button());
                return true;
            }
        }
        editing = null;
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseDragged(Click click, double deltaX, double deltaY) {
        if (draggingPanel != null) {
            POSITIONS.put(draggingPanel, new int[] {(int) click.x() - dragOffsetX, Math.max(0, (int) click.y() - dragOffsetY)});
            return true;
        }
        if (sliding != null) {
            slide(click.x());
            return true;
        }
        return super.mouseDragged(click, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(Click click) {
        if (draggingPanel != null || sliding != null) {
            draggingPanel = null;
            if (sliding != null) save();
            sliding = null;
            return true;
        }
        return super.mouseReleased(click);
    }

    private void slide(double x) {
        if (sliding == null) return;
        double ratio = Math.max(0, Math.min(1, (x - slideX) / Math.max(1.0, slideW)));
        if (sliding.kind() == ModuleOption.Kind.COLOR) sliding.setColor(0xFF000000 | (java.awt.Color.HSBtoRGB((float) ratio, 0.85F, 1.0F) & 0xFFFFFF));
        else sliding.setFromRatio(ratio);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
        for (int i = 0; i < panels.size(); i++) {
            int[] pos = position(i);
            if (mx >= pos[0] && mx < pos[0] + PANEL_W && my >= pos[1]) {
                String title = panels.get(i).title();
                scroll.put(title, scroll.getOrDefault(title, 0.0F) - (float) vertical * 16);
                return true;
            }
        }
        return super.mouseScrolled(mx, my, horizontal, vertical);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        int key = input.key();
        if (binding != null) {
            binding.setKeybind(key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_BACKSPACE ? -1 : key);
            binding = null;
            play("ui_toggle", 1.2F, 0.2F);
            save();
            return true;
        }
        if (editing != null) {
            if (key == GLFW.GLFW_KEY_BACKSPACE) editing.deleteLastTextCharacter();
            else if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER || key == GLFW.GLFW_KEY_ESCAPE) { editing = null; save(); }
            else if (key == GLFW.GLFW_KEY_V && (input.modifiers() & (GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_SUPER)) != 0) editing.appendText(client.keyboard.getClipboard());
            return true;
        }
        if (key == GLFW.GLFW_KEY_BACKSPACE && !search.isEmpty()) {
            search = search.substring(0, search.length() - 1);
            return true;
        }
        if (key == GLFW.GLFW_KEY_ESCAPE && !search.isEmpty()) {
            search = "";
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public boolean charTyped(CharInput input) {
        String typed = input.asString();
        if (editing != null) {
            editing.appendText(typed);
            return true;
        }
        if (binding == null && !(typed.isBlank() && search.isEmpty()) && search.length() < 20) {
            search += typed;
            play("ui_hover", 1.2F, 0.1F);
        }
        return true;
    }

    @Override
    protected void init() {
        play("ui_open", 1.0F, 0.25F);
    }

    @Override
    public void close() {
        if (closingAt < 0) {
            closingAt = System.nanoTime();
            play("ui_back", 1.0F, 0.22F);
            save();
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    // ---------------------------------------------------------------- helpers

    private int clipTop, clipBottom;

    /** Only rows inside the visible part of their panel can be clicked. */
    private boolean inClip(int y) {
        return y + 4 >= clipTop && y + 4 <= clipBottom;
    }

    private float springValue(String key) {
        Spring spring = springs.get(key);
        return spring == null ? 0.0F : spring.value();
    }

    private float spring(String key, float target) {
        return springs.computeIfAbsent(key, k -> new Spring(Spring.SMOOTH, target)).to(target).value();
    }

    private void save() {
        ConfigManager.save(KhanhClient.modules());
    }

    private void play(String sound, float pitch, float volume) {
        if (KhanhFeatures.enabled("client_sounds") && KhanhFeatures.bool("client_sounds", "muted", false)) return;
        client.getSoundManager().play(PositionedSoundInstance.ui(SoundEvent.of(Identifier.of(KhanhClient.MOD_ID, sound)), pitch, volume));
    }

    private static final StyleSpriteSource.Font INTER = new StyleSpriteSource.Font(Identifier.of(KhanhClient.MOD_ID, "xuong"));
    private static final StyleSpriteSource.Font INTER_SMALL = new StyleSpriteSource.Font(Identifier.of(KhanhClient.MOD_ID, "xuong_small"));

    /** Menu and HUD text: Xuong (Krypton's look), crisp at its real size. */
    public static Text blocky(String value) {
        return Text.literal(value).setStyle(Style.EMPTY.withFont(INTER));
    }

    /** The smaller Inter used for settings rows and the HUD. */
    public static Text small(String value) {
        return Text.literal(value).setStyle(Style.EMPTY.withFont(INTER_SMALL));
    }

    /** Big text (scale 1) uses Inter 8, anything smaller Inter 6.5, both drawn unscaled so they stay sharp. */
    private Text font(String value, float scale) {
        return scale >= 0.7F ? blocky(value) : small(value);
    }

    private void text(DrawContext c, String value, int x, int y, float scale, int colour) {
        c.drawText(textRenderer, font(value, scale), x, y, colour, false);
    }

    private int width(String value, float scale) {
        return textRenderer.getWidth(font(value, scale));
    }

    private String trim(String value, int max, float scale) {
        if (width(value, scale) <= max) return value;
        String cut = value;
        while (cut.length() > 1 && width(cut + "..", scale) > max) cut = cut.substring(0, cut.length() - 1);
        return cut + "..";
    }

    private static String keyName(int key) {
        return InputUtil.Type.KEYSYM.createFromCode(key).getLocalizedText().getString().toUpperCase(Locale.ROOT);
    }

    private static int lerp(int a, int b, float t) {
        int r = Math.round((a >> 16 & 255) + ((b >> 16 & 255) - (a >> 16 & 255)) * t);
        int g = Math.round((a >> 8 & 255) + ((b >> 8 & 255) - (a >> 8 & 255)) * t);
        int bl = Math.round((a & 255) + ((b & 255) - (a & 255)) * t);
        return r << 16 | g << 8 | bl;
    }

    private static int a(float alpha) {
        return Math.round(Math.max(0, Math.min(1, alpha)) * 255);
    }

    private static float clamp(float value) {
        return Math.max(0, Math.min(1, value));
    }

    private static float ease(float x) {
        return 1 - (float) Math.pow(1 - x, 3);
    }
}
