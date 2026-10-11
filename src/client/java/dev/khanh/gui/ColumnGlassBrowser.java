package dev.khanh.gui;

import dev.khanh.KhanhClient;
import dev.khanh.config.ConfigManager;
import dev.khanh.feature.KhanhSounds;
import dev.khanh.feature.GlintPattern;
import dev.khanh.feature.VisualTheme;
import dev.khanh.feature.WorkAutomationService;
import dev.khanh.module.Category;
import dev.khanh.module.Module;
import dev.khanh.module.ModuleOption;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.input.KeyInput;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

/** Compact glass columns with options expanding inside the module list.
 * Hit regions come from the rendered animated layout, not a second approximation.
 */
final class ColumnGlassBrowser {
    private static int X = 28, Y = 86, W = 160, GAP = 10, BOTTOM = 526, CONTENT = 128;
    private static final int ZERO_ROW_HEIGHT = 22;
    private static final int ZERO_CARD_HEIGHT = 20;
    private static final Category[] ARENA_ORDER = {
        Category.COMBAT, Category.MISC, Category.PLAYER, Category.RENDER,
        Category.CLIENT, Category.VISUAL, Category.CONFIG
    };
    private final Map<Category, UiAnimation> scroll = new EnumMap<>(Category.class);
    private final Map<Category, Float> targetScroll = new EnumMap<>(Category.class);
    private final Map<Category, List<Module>> filtered = new EnumMap<>(Category.class);
    private final Map<String, UiAnimation> expansion = new HashMap<>();
    private final Map<String, UiAnimation> hoverMotion = new HashMap<>();
    private final Map<String, Integer> automationTabs = new HashMap<>();
    private final List<Hit> hits = new ArrayList<>();
    private String lastQuery = "\u0000";
    private Module open, binding;
    private ModuleOption dragging, editing, bindingAction, wheel;
    private Hit draggedHit;
    private Category scrollDragging;
    private int clipBottom = BOTTOM - 9;
    private boolean wheelDragging;
    private boolean brightnessDragging;
    private final Map<String, String> trimmed = new HashMap<>();

    private record Hit(int x, int y, int w, int h, String kind, Module module, ModuleOption option) {
        boolean contains(double px, double py) { return px >= x && px < x + w && py >= y && py < y + h && py >= CONTENT && py < BOTTOM - 9; }
    }

    ColumnGlassBrowser() {
        for (Category category : Category.values()) { scroll.put(category, new UiAnimation(0)); targetScroll.put(category, 0F); }
    }
    void select(Module module) { open = module; wheel = null; }
    void blur() { editing = null; binding = null; bindingAction = null; }
    Module hoveredAt(double x, double y) {
        for (Hit hit : hits) if (hit.kind.equals("module") && hit.contains(x, y)) return hit.module;
        return null;
    }
    boolean containsColumn(double x, double y) {
        if (y < Y || y >= BOTTOM) return false;
        for (int column = 0; column < Category.values().length; column++) {
            int left = X + column * (W + GAP);
            if (x >= left && x < left + W) return true;
        }
        return false;
    }
    private void addHit(Hit hit) {
        int visibleHeight = Math.min(hit.h, clipBottom - hit.y);
        if (visibleHeight > 0) hits.add(new Hit(hit.x, hit.y, hit.w, visibleHeight, hit.kind, hit.module, hit.option));
    }
    void resetScroll() {
        for (Category category : Category.values()) { targetScroll.put(category, 0F); scroll.get(category).snapTo(0); }
    }
    void render(DrawContext c, int mx, int my, String query, String view) {
        String layout = style();
        boolean glassDropdown = layout.equals("Glass Dropdown");
        boolean arena = layout.equals("Arena Columns");
        if (glassDropdown) {
            W = 115; GAP = 10; X = (1250 - Category.values().length * W - (Category.values().length - 1) * GAP) / 2;
            Y = 160; BOTTOM = 400; CONTENT = Y + 28;
        } else if (arena) {
            X = 28; Y = 84; W = 160; GAP = 10; BOTTOM = 526; CONTENT = Y + 43;
        } else if (layout.equals("Monolith")) {
            // Preserve the familiar seven-column layout while giving it tighter,
            // calmer proportions and more visible modules per column.
            X = 104; Y = 116; W = 168; GAP = 8; BOTTOM = 626; CONTENT = 159;
        } else {
            X = 28; Y = 86; W = 160; GAP = 10; BOTTOM = 526; CONTENT = 128;
        }
        String filterKey = view + ":" + query;
        if (!filterKey.equals(lastQuery)) {
            lastQuery = filterKey;
            String q = query.toLowerCase(Locale.ROOT).strip();
            for (Category category : Category.values()) filtered.put(category, KhanhClient.modules().all().stream()
                .filter(m -> m.category() == category)
                .filter(m -> !view.equals("ACTIVE") || m.enabled())
                .filter(m -> !view.equals("BOUND") || m.keybind() >= 0)
                .filter(m -> q.isEmpty() || GuiState.searchRank(m, q) >= 0)
                .sorted(q.isEmpty() ? (a, b) -> 0 : java.util.Comparator.<Module>comparingInt(m -> GuiState.searchRank(m, q)))
                .toList());
            resetScroll();
        }
        hits.clear();
        if (layout.equals("Monolith")) {
            renderZeroConcept(c, mx, my);
            return;
        }
        Category[] order = arena ? ARENA_ORDER : Category.values();
        for (int column = 0; column < order.length; column++) {
            Category category = order[column];
            int x = X + column * (W + GAP);
            List<Module> modules = filtered.getOrDefault(category, List.of());
            if (glassDropdown) {
                panel(c, x, Y, W, BOTTOM - Y, 10, 0xAE191E27);
                text(c, category.displayName(), x + 10, Y + 9, 0xFFF3F5FA);
                c.fill(x + 1, Y + 24, x + W - 1, Y + 28, VisualTheme.withAlpha(GuiState.interfaceAccent(), 75));
            } else if (arena) {
                int estimatedHeight = Math.min(BOTTOM - Y, 44 + modules.size() * 25
                    + (open != null && open.category() == category ? expandedHeight(open) : 0));
                panel(c, x, Y, W, Math.max(44, estimatedHeight), 5, 0xF0171A24);
                icon(c, category, x + 10, Y + 9, 16, 0xFF5FD9DC);
                String title = category.displayName();
                int titleWidth = KhanhClickGuiScreen.textRenderer().getWidth(KhanhClickGuiScreen.fontText(title));
                text(c, title, x + (W - titleWidth) / 2, Y + 11, 0xFF9AF8F3);
                c.fill(x + 8, Y + 35, x + W - 8, Y + 37, 0xFF5FD9DC);
            } else if (!layout.equals("Minimal")) panel(c, x, Y, W, 38, layout.equals("Tactical") ? 2 : 5, 0xF0020305);
            if (layout.equals("Minimal")) c.fill(x + 3, Y + 34, x + W - 3, Y + 35,
                VisualTheme.withAlpha(GuiState.interfaceAccent(), 128));
            if (layout.equals("Monolith")) KhanhClickGuiScreen.roundedRect(c, x + 5, Y + 5,
                W - 10, 28, 3, 0xFF181B20);
            if (!glassDropdown && !arena) {
                icon(c, category, x + 13, Y + 12, 18, GuiState.interfaceAccent());
                text(c, category.displayName().toUpperCase(Locale.ROOT), x + 39, Y + 12, 0xFFF1F4F8);
                c.fill(x + 12, Y + 35, x + W - 12, Y + 37,
                    VisualTheme.withAlpha(GuiState.interfaceAccent(), 210));
            }
            float total = 0;
            int rowHeight = glassDropdown ? 20 : 25;
            for (Module m : modules) {
                UiAnimation a = expansion.computeIfAbsent(m.id(), id -> new UiAnimation(0));
                a.animateTo(m == open ? expandedHeight(m) : 0, 180);
                total += rowHeight + a.value();
            }
            float max = Math.max(0, total - (BOTTOM - CONTENT - 9));
            float target = Math.min(max, targetScroll.get(category));
            targetScroll.put(category, target); scroll.get(category).animateTo(target, 145);
            float y = CONTENT - scroll.get(category).value();
            c.enableScissor(x + 5, CONTENT, x + W - 5, BOTTOM - 9);
            for (Module m : modules) {
                int top = Math.round(y), extra = Math.round(expansion.get(m.id()).value());
                if (top + 22 + extra >= CONTENT && top < BOTTOM - 9) {
                    boolean hover = mx >= x + 7 && mx < x + W - 7 && my >= top && my < top + 22 && my >= CONTENT && my < BOTTOM - 9;
                    int rowColour = switch (layout) {
                        case "Arena Columns" -> m.enabled() ? 0x9B263D49 : hover ? 0xBE252A34 : 0x701A1E27;
                        case "Glass Dropdown" -> m.enabled() ? 0x463C4550 : hover ? 0x453F4854 : 0x00191E27;
                        case "Tactical" -> m.enabled() ? 0xF03C4149 : hover ? 0xE02C3037 : 0xEE1B1D22;
                        case "Monolith" -> m.enabled() ? 0xEF30363F : hover ? 0xED22272F : 0xEF11151B;
                        default -> m.enabled() ? VisualTheme.withAlpha(GuiState.interfaceAccent(), hover ? 36 : 23)
                            : hover ? 0xE00D1117 : 0xD907090C;
                    };
                    if (!layout.equals("Minimal") || extra > 3) {
                        KhanhClickGuiScreen.roundedRect(c, x + 7, top, W - 14, 22 + extra,
                            layout.equals("Tactical") || arena ? 1 : glassDropdown ? 2 : layout.equals("Monolith") ? 5 : 3, rowColour);
                    }
                    if (layout.equals("Columns") || layout.equals("Monolith") || extra > 3) {
                        KhanhClickGuiScreen.roundedOutline(c, x + 7, top, W - 14, 22 + extra, 3,
                            VisualTheme.withAlpha(GuiState.interfaceAccent(), m == open ? 115 : m.enabled() ? 52 : 24));
                    }
                    if (layout.equals("Tactical") || arena) c.fill(x + 11, top + 21, x + W - 11, top + 22, arena ? 0x4D4A5964 : 0x553E4149);
                    if (m.enabled()) c.fill(x + 8, top + 5, x + 11, top + 17, styleAccent());
                    text(c, trim(glassDropdown ? m.name() : m.name().toUpperCase(Locale.ROOT), W - 34), x + 16, top + 7,
                        m.enabled() ? (arena ? 0xFF8FF3F2 : 0xFFF4F7FB) : layout.equals("Tactical") || arena ? 0xFFD1D3D8 : 0xFFA8B1BE);
                    if (glassDropdown) {
                        c.fill(x + 8, top + 20, x + W - 8, top + 21, 0x22F4F5FA);
                    }
                    clipBottom = BOTTOM - 9;
                    addHit(new Hit(x + 7, top, W - 14, 22, "module", m, null));
                    if (extra > 3 && Math.max(CONTENT, top + 25) < Math.min(BOTTOM - 9, top + 22 + extra)) {
                        clipBottom = Math.min(BOTTOM - 9, top + 22 + extra);
                        c.enableScissor(x + 7, Math.max(CONTENT, top + 25), x + W - 7, Math.min(BOTTOM - 9, top + 22 + extra));
                        drawOptions(c, m, x, top + 25, mx, my);
                        c.disableScissor();
                    }
                }
                y += rowHeight + extra;
            }
            c.disableScissor();
            if (modules.isEmpty()) text(c, "No matches", x + 14, CONTENT + 12, 0xFF8295AE);
            if (max > 0) {
                int viewport = BOTTOM - CONTENT - 9, thumb = Math.max(24, Math.round(viewport * viewport / total));
                int thumbY = CONTENT + Math.round((viewport - thumb) * Math.min(1, scroll.get(category).value() / max));
                KhanhClickGuiScreen.roundedRect(c, x + W - 4, thumbY, 2, thumb, 1, 0x8CE5E9F0);
            }
        }
    }

    /** The approved Zero concept: seven quiet glass columns, live module states,
     * and settings that expand in the same column without moving the others. */
    private void renderZeroConcept(DrawContext c, int mx, int my) {
        for (int column = 0; column < Category.values().length; column++) {
            Category category = Category.values()[column];
            int x = X + column * (W + GAP);
            List<Module> modules = filtered.getOrDefault(category, List.of());
            float total = 0;
            for (Module module : modules) {
                UiAnimation animation = expansion.computeIfAbsent(module.id(), id -> new UiAnimation(0));
                animation.animateTo(module == open ? expandedHeight(module) + 10 : 0, 180);
                total += ZERO_ROW_HEIGHT + animation.value();
            }

            int panelHeight = Math.min(BOTTOM - Y, Math.max(47, Math.round(47 + total)));
            LiquidGlass.card(c, x, Y, W, panelHeight, 12, 1.0F);
            icon(c, category, x + 14, Y + 14, 14, 0xFFD5DEE7);
            text(c, category.displayName(), x + 35, Y + 18, 0xFFF0F4F8);
            String countLabel = modules.stream().filter(Module::enabled).count() + "/" + modules.size();
            int countWidth = KhanhClickGuiScreen.textRenderer().getWidth(KhanhClickGuiScreen.fontText(countLabel));
            text(c, countLabel, x + W - 13 - countWidth, Y + 18, 0xFF7F8B98);
            c.fill(x + 11, Y + 40, x + W - 11, Y + 41, 0x1AFFFFFF);
            c.fill(x + 14, Y + 40, x + 42, Y + 41,
                VisualTheme.withAlpha(GuiState.interfaceAccent(), 145));

            float viewport = BOTTOM - CONTENT - 9;
            float max = Math.max(0, total - viewport);
            float target = Math.min(max, targetScroll.get(category));
            targetScroll.put(category, target);
            scroll.get(category).animateTo(target, 145);
            float y = CONTENT - scroll.get(category).value();
            c.enableScissor(x + 6, CONTENT, x + W - 6, BOTTOM - 9);
            for (Module module : modules) {
                int top = (int) Math.floor(y);
                int extra = Math.round(expansion.get(module.id()).value());
                if (top + ZERO_CARD_HEIGHT + extra >= CONTENT && top < BOTTOM - 9) {
                    // Draw at whole pixels and carry the remainder in the transform so
                    // scrolling glides instead of stepping a pixel at a time.
                    c.getMatrices().pushMatrix();
                    c.getMatrices().translate(0.0F, y - top);
                    boolean hover = mx >= x + 8 && mx < x + W - 8 && my >= top
                        && my < top + ZERO_CARD_HEIGHT && my >= CONTENT && my < BOTTOM - 9;
                    boolean selected = module == open;
                    UiAnimation rowHover = hoverMotion.computeIfAbsent(module.id(), id -> new UiAnimation(0));
                    rowHover.animateTo(hover ? 1.0F : 0.0F, 125);
                    float hoverAmount = rowHover.value();
                    int rowColor = selected ? 0x30FFFFFF : module.enabled()
                        ? blendArgb(0x1CFFFFFF, 0x2AFFFFFF, hoverAmount)
                        : blendArgb(0x07FFFFFF, 0x15FFFFFF, hoverAmount);
                    KhanhClickGuiScreen.roundedRect(c, x + 7, top, W - 14, ZERO_CARD_HEIGHT, 5, rowColor);
                    if (selected) KhanhClickGuiScreen.roundedOutline(c, x + 7, top, W - 14,
                        ZERO_CARD_HEIGHT, 5, VisualTheme.withAlpha(GuiState.interfaceAccent(), 112));
                    if (module.enabled()) KhanhClickGuiScreen.roundedRect(c, x + 7, top + 4, 2,
                        ZERO_CARD_HEIGHT - 8, 1, VisualTheme.withAlpha(GuiState.interfaceAccent(), 220));
                    if (!module.id().equals("config")) toggle(c, x + W - 32, top + 5, module.enabled());
                    text(c, trim(module.name(), module.id().equals("config") ? W - 67 : W - 50),
                        x + 14 + Math.round(hoverAmount), top + 6,
                        module.enabled() || selected ? 0xFFF1F5F8
                            : blendArgb(0xFFA8B2BF, 0xFFD4DEE7, hoverAmount));
                    if (module.id().equals("config")) {
                        text(c, "OPEN", x + W - 45, top + 6, GuiState.interfaceAccent());
                    }
                    clipBottom = BOTTOM - 9;
                    addHit(new Hit(x + 7, top, W - 14, ZERO_CARD_HEIGHT, "module", module, null));
                    if (extra > 3) {
                        int optionsY = top + ZERO_ROW_HEIGHT;
                        int optionsBottom = Math.min(BOTTOM - 9, optionsY + extra - 4);
                        KhanhClickGuiScreen.roundedRect(c, x + 7, optionsY, W - 14,
                            extra - 4, 5, 0x12FFFFFF);
                        if (optionsBottom > Math.max(CONTENT, optionsY + 9)) {
                            clipBottom = optionsBottom;
                            c.enableScissor(x + 8, Math.max(CONTENT, optionsY + 9),
                                x + W - 8, optionsBottom);
                            drawOptions(c, module, x, optionsY + 9, mx, my);
                            // Popping returns to the column's clip; pushing it again left one clip
                            // on the stack that hid every column to the right.
                            c.disableScissor();
                        }
                    }
                    c.getMatrices().popMatrix();
                }
                y += ZERO_ROW_HEIGHT + extra;
            }
            c.disableScissor();
            if (modules.isEmpty()) text(c, "No matches", x + 17, CONTENT + 10, 0xFFA8B2BF);
            if (max > 0) {
                int thumb = Math.max(24, Math.round(viewport * viewport / total));
                int thumbY = CONTENT + Math.round((viewport - thumb)
                    * Math.min(1, scroll.get(category).value() / max));
                KhanhClickGuiScreen.roundedRect(c, x + W - 5, CONTENT, 2,
                    Math.round(viewport), 1, 0x344A5562);
                KhanhClickGuiScreen.roundedRect(c, x + W - 5, thumbY, 2, thumb, 1,
                    VisualTheme.withAlpha(GuiState.interfaceAccent(), 188));
            }
        }
    }

    private static int blendRgb(int from, int to, float ratio) {
        float t = Math.max(0.0F, Math.min(1.0F, ratio));
        int r = Math.round(((from >> 16) & 255) * (1.0F - t) + ((to >> 16) & 255) * t);
        int g = Math.round(((from >> 8) & 255) * (1.0F - t) + ((to >> 8) & 255) * t);
        int b = Math.round((from & 255) * (1.0F - t) + (to & 255) * t);
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    private static int blendArgb(int from, int to, float ratio) {
        float t = Math.max(0.0F, Math.min(1.0F, ratio));
        int a = Math.round(((from >>> 24) & 255) * (1.0F - t) + ((to >>> 24) & 255) * t);
        return a << 24 | (blendRgb(from, to, t) & 0x00FFFFFF);
    }

    private int expandedHeight(Module m) {
        int height = 27;
        if (automation(m)) height += 66;
        for (ModuleOption o : visibleOptions(m)) height += optionHeight(o);
        return height + 5;
    }
    private static boolean automation(Module module) {
        return module.id().equals("auto_schematic_builder") || module.id().equals("auto_mineout");
    }
    private List<ModuleOption> visibleOptions(Module module) {
        if (!automation(module)) return module.options();
        int tab = automationTabs.getOrDefault(module.id(), 0);
        return module.options().stream().filter(option -> automationGroup(option.id()) == tab).toList();
    }
    private static int automationGroup(String id) {
        return switch (id) {
            case "start_build", "check_build", "pause_build", "resume_build", "stop_build", "corner_a", "corner_b", "clear_corners", "private_server", "reach", "delay", "auto_walk", "layer_mode", "build_order", "mine_order" -> 0;
            case "walk_radius", "route_nodes", "scan_budget", "vertical_scan", "path_height", "route_replan", "stuck_timeout", "walk_turn_speed", "walking_ease", "retry_route", "step_up", "step_down" -> 1;
            case "min_health", "min_hunger", "pause_players", "player_radius", "skip_falling", "skip_block_entities", "skip_waterlogged", "only_full_blocks", "missing_material", "skip_ores", "skip_containers", "skip_spawners", "skip_valuable", "preserve_floor", "floor_margin", "liquid_buffer" -> 2;
            default -> 3;
        };
    }
    private void automationHeader(DrawContext c, Module m, int x, int y) {
        String status = m.id().equals("auto_schematic_builder")
            ? WorkAutomationService.builderUiStatus()
            : m.enabled() ? "Mining begins when GUI closes" : "Set corners, then turn on";
        text(c, "STATUS", x + 16, y + 2, 0xFF9EACBA);
        int available = W - 32;
        String first = trim(status, available);
        text(c, first, x + 16, y + 14, 0xFFD8E3EC);
        if (first.length() < status.length()) {
            String rest = status.substring(Math.max(0, first.length() - 1)).strip();
            text(c, trim(rest, available), x + 16, y + 25, 0xFFD8E3EC);
        }
        String[] labels = {"RUN", "PATH", "SAFE", "MORE"};
        int selected = automationTabs.getOrDefault(m.id(), 0);
        int tabWidth = (W - 20) / labels.length;
        for (int tab = 0; tab < labels.length; tab++) {
            int tx = x + 10 + tab * tabWidth;
            KhanhClickGuiScreen.roundedRect(c, tx, y + 42, tabWidth - 2, 19, 3,
                tab == selected ? 0xB74A5B6A : 0x5525323E);
            text(c, labels[tab], tx + 5, y + 47,
                tab == selected ? 0xFFF5F9FC : 0xFFA5B4C1);
            addHit(new Hit(tx, y + 42, tabWidth - 2, 19, "auto-tab-" + tab, m, null));
        }
    }
    private int optionHeight(ModuleOption o) {
        return o == wheel ? 167 : o.id().equals("theme") ? 91
            : o.kind() == ModuleOption.Kind.NUMBER ? 36 : 29;
    }
    /** A small pill switch: accent and knob right when on, dim and knob left when off. */
    private static void toggle(DrawContext c, int x, int y, boolean on) {
        int accent = GuiState.interfaceAccent();
        KhanhClickGuiScreen.roundedRect(c, x, y, 18, 10, 5, on ? VisualTheme.withAlpha(accent, 225) : 0xFF3A424D);
        if (!on) KhanhClickGuiScreen.roundedOutline(c, x, y, 18, 10, 5, 0x40FFFFFF);
        KhanhClickGuiScreen.drawCircle(c, on ? x + 13 : x + 5, y + 5, 3, on ? 0xFF0B0C0F : 0xFFB4BDC8);
    }

    private void drawOptions(DrawContext c, Module m, int x, int y, int mx, int my) {
        text(c, "Keybind", x + 16, y + 5, 0xFFE6EBF2);
        String bind = binding == m ? "Press key…" : KhanhClickGuiScreen.keyName(m.keybind());
        String shownBind = trim(bind.equalsIgnoreCase("unbound") ? "None" : bind, 65);
        text(c, shownBind, x + W - 16 - KhanhClickGuiScreen.textRenderer().getWidth(
            KhanhClickGuiScreen.fontText(shownBind)), y + 5, GuiState.interfaceAccent());
        addHit(new Hit(x + 10, y, W - 20, 22, "bind", m, null));
        c.fill(x + 15, y + 24, x + W - 15, y + 25, 0x394A5561);
        y += 27;
        if (automation(m)) {
            automationHeader(c, m, x, y);
            y += 66;
        }
        for (ModuleOption o : visibleOptions(m)) {
            int height = optionHeight(o);
            if (y + height >= CONTENT && y < BOTTOM - 9) {
                String value = o == bindingAction ? "Press key…" : o == editing ? o.displayValue() + "|" : o.displayValue();
                int valueWidth = KhanhClickGuiScreen.textRenderer().getWidth(KhanhClickGuiScreen.fontText(value));
                int labelWidth = o.kind() == ModuleOption.Kind.BOOLEAN
                    ? W - 45 : o.kind() == ModuleOption.Kind.NUMBER
                        ? Math.max(48, W - valueWidth - 44) : W - 58;
                text(c, trim(o.label(), labelWidth), x + 16, y + 3, o.kind() == ModuleOption.Kind.BOOLEAN && !o.booleanValue()
                    ? 0xFFA8B2BF : 0xFFE6EBF2);
                if (o.kind() == ModuleOption.Kind.BOOLEAN) {
                    toggle(c, x + W - 34, y + 3, o.booleanValue());
                } else if (o.kind() == ModuleOption.Kind.NUMBER) {
                    int sx = x + 17, sy = y + 25, sw = W - 34;
                    text(c, value, x + W - 16 - valueWidth, y + 3, GuiState.interfaceAccent());
                    KhanhClickGuiScreen.roundedRect(c, sx, sy, sw, 3, 1, 0xFF27303B);
                    int filled = Math.round((float) o.ratio() * sw);
                    KhanhClickGuiScreen.roundedRect(c, sx, sy, filled, 3, 1, GuiState.interfaceAccent());
                    KhanhClickGuiScreen.drawCircle(c, sx + filled, sy + 1, 3, 0xFFF0F6FF);
                } else {
                    text(c, trim(value, W - 36), x + 16, y + 15,
                        o.kind() == ModuleOption.Kind.COLOR ? o.colorValue() : GuiState.interfaceAccent());
                }
                if (m.id().equals("custom_glint") && o.id().equals("theme")) {
                    Identifier preview = Identifier.of(KhanhClient.MOD_ID,
                        GlintPattern.texturePath(o.choiceValue()));
                    KhanhClickGuiScreen.roundedRect(c, x + 15, y + 29, W - 30, 55, 5, 0xFF0C1018);
                    c.drawTexture(RenderPipelines.GUI_TEXTURED, preview,
                        x + 18, y + 32, 0F, 0F, W - 36, 49, 128, 128, 128, 128);
                    KhanhClickGuiScreen.roundedOutline(c, x + 15, y + 29, W - 30, 55, 5,
                        VisualTheme.withAlpha(GuiState.interfaceAccent(), 105));
                }
                addHit(new Hit(x + 10, y, W - 20, optionHeight(o) - 2, "option", m, o));
                if (o == wheel) {
                    int wx = x + 30, wy = y + 31;
                    c.drawTexture(RenderPipelines.GUI_TEXTURED, Identifier.of(KhanhClient.MOD_ID, "textures/gui/colour-wheel.png"), wx, wy, 0F, 0F, 100, 100, 256, 256, 256, 256);
                    float[] hsv = java.awt.Color.RGBtoHSB(o.colorValue() >> 16 & 255, o.colorValue() >> 8 & 255, o.colorValue() & 255, null);
                    int px = wx + 50 + Math.round((float) Math.cos(hsv[0] * Math.PI * 2) * hsv[1] * 47);
                    int py = wy + 50 + Math.round((float) Math.sin(hsv[0] * Math.PI * 2) * hsv[1] * 47);
                    KhanhClickGuiScreen.drawCircle(c, px, py, 4, 0xFF101A29); KhanhClickGuiScreen.drawCircle(c, px, py, 2, 0xFFFFFFFF);
                    addHit(new Hit(wx, wy, 100, 100, "wheel", m, o));
                    int by = wy + 108;
                    for (int bx = 0; bx < 100; bx++) {
                        c.fill(wx + bx, by, wx + bx + 1, by + 8,
                            java.awt.Color.HSBtoRGB(hsv[0], hsv[1], bx / 99F));
                    }
                    int marker = wx + Math.round(hsv[2] * 99F);
                    KhanhClickGuiScreen.roundedRect(c, marker - 2, by - 2, 4, 12, 2, 0xFFFFFFFF);
                    KhanhClickGuiScreen.roundedRect(c, marker - 1, by - 1, 2, 10, 1, 0xFF151311);
                    addHit(new Hit(wx, by - 2, 100, 12, "brightness", m, o));
                }
            }
            y += height;
        }
    }

    boolean mouseClicked(double mx, double my, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && style().equals("Monolith")
            && my >= CONTENT && my < BOTTOM - 9) {
            for (Category category : Category.values()) {
                int x = X + category.ordinal() * (W + GAP);
                if (mx >= x + W - 12 && mx < x + W - 1 && scrollTo(category, my)) {
                    scrollDragging = category;
                    return true;
                }
            }
        }
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit h = hits.get(i); if (!h.contains(mx, my)) continue;
            editing = null; dragging = null; binding = null; bindingAction = null;
            if (h.kind.equals("module")) {
                if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && h.module.id().equals("config")) {
                    MinecraftClient client = MinecraftClient.getInstance();
                    client.setScreen(new KhanhConfigScreen(client.currentScreen));
                    sound(1.14F);
                    return true;
                } else if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) h.module.toggle();
                else if (button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE) binding = h.module;
                else if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                    open = open == h.module ? null : h.module;
                    wheel = null;
                    if (open != null && style().equals("Monolith")) reveal(open);
                }
            } else if (h.kind.equals("bind")) {
                if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) h.module.setKeybind(-1); else binding = h.module;
            } else if (h.kind.startsWith("auto-tab-")) {
                automationTabs.put(h.module.id(), Integer.parseInt(h.kind.substring(9)));
                wheel = null;
                if (style().equals("Monolith")) reveal(h.module);
            } else if (h.kind.equals("wheel")) {
                wheelDragging = true; brightnessDragging = false; draggedHit = h; updateWheel(h, mx, my);
            } else if (h.kind.equals("brightness")) {
                brightnessDragging = true; wheelDragging = false; draggedHit = h; updateBrightness(h, mx);
            } else {
                ModuleOption o = h.option;
                switch (o.kind()) {
                    case BOOLEAN -> o.toggle();
                    case CHOICE -> o.cycle(button == GLFW.GLFW_MOUSE_BUTTON_RIGHT ? -1 : 1);
                    case NUMBER -> { dragging = o; draggedHit = h; updateSlider(h, mx); }
                    case COLOR -> wheel = wheel == o ? null : o;
                    case TEXT -> editing = o;
                    case ACTION -> { if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) bindingAction = o; else o.trigger(); }
                }
            }
            sound(button == GLFW.GLFW_MOUSE_BUTTON_RIGHT ? 0.92F : 1.14F); save(); return true;
        }
        editing = null; binding = null; bindingAction = null; return false;
    }
    boolean mouseDragged(double mx, double my) {
        if (scrollDragging != null) { scrollTo(scrollDragging, my); return true; }
        if (wheelDragging && draggedHit != null) { updateWheel(draggedHit, mx, my); return true; }
        if (brightnessDragging && draggedHit != null) { updateBrightness(draggedHit, mx); return true; }
        if (dragging != null && draggedHit != null) { updateSlider(draggedHit, mx); return true; }
        return false;
    }
    private void reveal(Module module) {
        List<Module> modules = filtered.getOrDefault(module.category(), List.of());
        int before = 0;
        for (Module candidate : modules) {
            if (candidate == module) break;
            before += ZERO_ROW_HEIGHT;
        }
        int viewport = BOTTOM - CONTENT - 9;
        int total = modules.size() * ZERO_ROW_HEIGHT + expandedHeight(module) + 10;
        targetScroll.put(module.category(), (float) Math.min(before, Math.max(0, total - viewport)));
    }
    boolean mouseReleased() {
        boolean changedOption = wheelDragging || brightnessDragging || dragging != null;
        boolean active = changedOption || scrollDragging != null;
        wheelDragging = false; brightnessDragging = false; dragging = null; draggedHit = null;
        scrollDragging = null;
        if (changedOption) save(); return active;
    }
    private boolean scrollTo(Category category, double mouseY) {
        float total = 0;
        for (Module module : filtered.getOrDefault(category, List.of())) {
            UiAnimation animation = expansion.get(module.id());
            total += ZERO_ROW_HEIGHT + (animation == null ? 0 : animation.value());
        }
        float viewport = BOTTOM - CONTENT - 9;
        float maximum = Math.max(0, total - viewport);
        if (maximum <= 0) return false;
        float thumb = Math.max(24, viewport * viewport / total);
        float travel = Math.max(1, viewport - thumb);
        float fraction = (float) Math.max(0, Math.min(1, (mouseY - CONTENT - thumb / 2) / travel));
        targetScroll.put(category, fraction * maximum);
        return true;
    }
    boolean mouseScrolled(double mx, double my, double amount) {
        if (my < CONTENT || my >= BOTTOM) return false;
        Category[] order = style().equals("Arena Columns") ? ARENA_ORDER : Category.values();
        for (int column = 0; column < order.length; column++) {
            Category category = order[column];
            int x = X + column * (W + GAP);
            if (mx >= x && mx < x + W) {
                targetScroll.put(category, Math.max(0, targetScroll.get(category) - (float) amount * 76)); return true;
            }
        }
        return false;
    }
    boolean keyPressed(KeyInput input) {
        swallowChar = false;
        if (binding != null || bindingAction != null) {
            if (!input.isEscape()) {
                int key = input.key() == GLFW.GLFW_KEY_DELETE || input.key() == GLFW.GLFW_KEY_BACKSPACE ? -1 : input.key();
                if (key != GLFW.GLFW_KEY_RIGHT_SHIFT) { if (binding != null) binding.setKeybind(key); else bindingAction.setActionKeybind(key); }
            }
            binding = null; bindingAction = null; swallowChar = true; save(); return true;
        }
        if (editing != null) {
            if (input.key() == GLFW.GLFW_KEY_BACKSPACE) editing.deleteLastTextCharacter();
            else if (input.isEscape() || input.key() == GLFW.GLFW_KEY_ENTER) { editing = null; save(); }
            else if (input.key() == GLFW.GLFW_KEY_V && (input.modifiers() & GLFW.GLFW_MOD_CONTROL) != 0) editing.appendText(MinecraftClient.getInstance().keyboard.getClipboard());
            return true;
        }
        if (input.isEscape() && open != null) { open = null; wheel = null; return true; }
        return false;
    }
    private boolean swallowChar;
    boolean charTyped(String value) {
        // The key just bound also arrives as typed text; drop it.
        if (swallowChar) { swallowChar = false; return true; }
        if (editing == null) return false; editing.appendText(value); return true;
    }
    private void updateSlider(Hit h, double mx) { h.option.setFromRatio((mx - h.x - 7) / (h.w - 14.0)); }
    private void updateWheel(Hit h, double mx, double my) {
        double dx = mx - h.x - 50, dy = my - h.y - 50;
        float hue = (float) (Math.atan2(dy, dx) / (Math.PI * 2)); if (hue < 0) hue++;
        float[] hsv = java.awt.Color.RGBtoHSB(h.option.colorValue() >> 16 & 255,
            h.option.colorValue() >> 8 & 255, h.option.colorValue() & 255, null);
        if (hsv[2] < .01F) hsv[2] = 1F;
        h.option.setColor(java.awt.Color.HSBtoRGB(hue,
            Math.min(1F, (float) Math.hypot(dx, dy) / 47), hsv[2]));
    }
    private void updateBrightness(Hit h, double mx) {
        float[] hsv = java.awt.Color.RGBtoHSB(h.option.colorValue() >> 16 & 255,
            h.option.colorValue() >> 8 & 255, h.option.colorValue() & 255, null);
        float value = Math.max(0F, Math.min(1F, (float) ((mx - h.x) / Math.max(1.0, h.w - 1.0))));
        h.option.setColor(java.awt.Color.HSBtoRGB(hsv[0], hsv[1], value));
    }
    static void icon(DrawContext c, Category category, int x, int y, int size, int tint) {
        Identifier id = Identifier.of(KhanhClient.MOD_ID, "textures/gui/" + category.name().toLowerCase(Locale.ROOT) + ".png");
        c.drawTexture(RenderPipelines.GUI_TEXTURED, id, x, y, 0F, 0F, size, size, 256, 256, 256, 256, tint);
    }
    private static void panel(DrawContext c, int x, int y, int w, int h, int r, int colour) {
        if (style().equals("Arena Columns")) {
            KhanhClickGuiScreen.roundedRect(c, x + 2, y + 3, w, h, 5, 0x65000000);
            KhanhClickGuiScreen.roundedRect(c, x, y, w, h, 5, colour);
            KhanhClickGuiScreen.roundedOutline(c, x, y, w, h, 5, 0x503A4659);
            return;
        }
        if (style().equals("Glass Dropdown")) {
            KhanhClickGuiScreen.roundedRect(c, x + 2, y + 4, w, h, 10, 0x4A000000);
            if (!GlassGuiRenderer.liquidSurface(c, x, y, w, h, 10, 0xA6262E3A)) {
                KhanhClickGuiScreen.roundedRect(c, x, y, w, h, 10, 0xA6262E3A);
            }
            KhanhClickGuiScreen.roundedOutline(c, x, y, w, h, 10, 0x50E5ECFA);
            return;
        }
        if (style().equals("Tactical")) {
            KhanhClickGuiScreen.roundedRect(c, x, y, w, h, 3, 0xF11A1C20);
            return;
        }
        if (dev.khanh.feature.KhanhFeatures.choice("visuals", "gui_theme", "Khanh Client Black").equals("Khanh Client Black")) {
            int radius = Math.min(6, Math.max(2, r));
            KhanhClickGuiScreen.roundedRect(c, x + 2, y + 3, w, h, radius, 0x78000000);
            KhanhClickGuiScreen.roundedRect(c, x, y, w, h, radius, 0xF2020407);
            KhanhClickGuiScreen.roundedOutline(c, x, y, w, h, radius,
                VisualTheme.withAlpha(GuiState.interfaceAccent(), 58));
            c.fill(x + 8, y + 2, x + w - 8, y + 3,
                VisualTheme.withAlpha(GuiState.interfaceAccent(), 48));
            return;
        }
        int themedColour = VisualTheme.guiGlass(colour);
        KhanhClickGuiScreen.roundedRect(c, x + 2, y + 5, w, h, r, 0x50000000);
        if (!GlassGuiRenderer.liquidSurface(c, x, y, w, h, r, themedColour)) {
            KhanhClickGuiScreen.roundedRect(c, x, y, w, h, r, themedColour);
        }
        KhanhClickGuiScreen.roundedOutline(c, x, y, w, h, r,
            VisualTheme.withAlpha(GuiState.interfaceAccent(), 76));
    }
    private static void text(DrawContext c, String label, int x, int y, int colour) {
        c.drawText(KhanhClickGuiScreen.textRenderer(), KhanhClickGuiScreen.fontText(label), x, y, colour, false);
    }
    private String trim(String label, int width) {
        String key = width + ":" + label;
        String cached = trimmed.get(key); if (cached != null) return cached;
        String result = label;
        if (KhanhClickGuiScreen.textRenderer().getWidth(KhanhClickGuiScreen.fontText(result)) > width) {
            while (!result.isEmpty() && KhanhClickGuiScreen.textRenderer().getWidth(
                KhanhClickGuiScreen.fontText(result + "…")) > width)
                result = result.substring(0, result.offsetByCodePoints(result.length(), -1));
            result = result.isEmpty() ? "" : result + "…";
        }
        if (trimmed.size() >= 512) trimmed.clear();
        trimmed.put(key, result); return result;
    }
    private static void sound(float pitch) { KhanhSounds.playInterface(MinecraftClient.getInstance(), pitch); }
    private static void save() { ConfigManager.save(KhanhClient.modules()); }
    private static String style() {
        return dev.khanh.feature.KhanhFeatures.choice("visuals", "gui_layout", "Columns");
    }
    private static int styleAccent() { return style().equals("Arena Columns") ? 0xFF5FD9DC : GuiState.interfaceAccent(); }
}
