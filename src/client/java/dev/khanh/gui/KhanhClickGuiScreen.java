package dev.khanh.gui;

import dev.khanh.KhanhClient;
import dev.khanh.config.ConfigManager;
import dev.khanh.feature.KhanhSounds;
import dev.khanh.feature.KhanhFeatures;
import dev.khanh.feature.PrivacyService;
import dev.khanh.feature.GlintPattern;
import dev.khanh.feature.VisualTheme;
import dev.khanh.module.Category;
import dev.khanh.module.Module;
import dev.khanh.module.ModuleOption;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Style;
import net.minecraft.text.StyleSpriteSource;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

/** Khanh Client's liquid-glass module browser. */
public final class KhanhClickGuiScreen extends Screen implements KhanhClickGui {
    private enum ModuleView { ALL, ACTIVE, BOUND }
    private static final Identifier RUSSO_ONE = Identifier.of(KhanhClient.MOD_ID, "russo_one");
    private static final Identifier ZERO_LOGO = Identifier.of(KhanhClient.MOD_ID, "textures/gui/khanh-logo.png");

    private static final int VIRTUAL_WIDTH = 1440;
    private static final int VIRTUAL_HEIGHT = 660;
    private static final int COLUMN_X = 28;
    private static final int COLUMN_Y = 86;
    private static final int COLUMN_WIDTH = 160;
    private static final int COLUMN_GAP = 10;
    private static final int COLUMN_BOTTOM = 526;
    private static final int MODULE_TOP = 142;
    private static final int INLINE_POWER_Y = MODULE_TOP;
    private static final int INLINE_BIND_Y = MODULE_TOP + 34;
    private static final int INLINE_OPTIONS_TOP = MODULE_TOP + 82;
    private static final int INSPECTOR_X = 382;
    private static final int INSPECTOR_Y = 78;
    private static final int INSPECTOR_WIDTH = 316;
    private static final int INSPECTOR_HEIGHT = 438;
    private static final int OPTION_ROW_HEIGHT = 30;
    private static final int COLOR_PICKER_X = INSPECTOR_X + INSPECTOR_WIDTH + 10;
    private static final int COLOR_PICKER_Y = INSPECTOR_Y + 150;
    private static final int COLOR_PICKER_WIDTH = 154;
    private static final int COLOR_PICKER_HEIGHT = 176;
    private static final int COLOR_WHEEL_RADIUS = 40;

    private static final int SEARCH_X = 380;
    private static final int SEARCH_Y = 44;
    private static final int SEARCH_WIDTH = 680;
    private static final int SEARCH_HEIGHT = 40;
    private static final int LAYOUT_X = 252;
    private static final int LAYOUT_Y = 50;
    private static final int LAYOUT_WIDTH = 112;
    private static final int LAYOUT_HEIGHT = 28;
    private static final int PROFILE_X = 1090;
    private static final int PROFILE_Y = 44;
    private static final int PROFILE_WIDTH = 260;
    private static final int PROFILE_HEIGHT = 40;
    private static final int DROPDOWN_X = 360;
    private static final int DROPDOWN_WIDTH = 360;
    private static final int DROPDOWN_HEADER_HEIGHT = 42;
    private static final int WATER_X = 282;
    private static final int WATER_Y = 104;
    private static final int WATER_WIDTH = 516;
    private static final int WATER_HEIGHT = 348;
    private static final int WATER_SIDEBAR_WIDTH = 110;
    private static final int WATER_CONTENT_X = WATER_X + 117;
    private static final int WATER_CONTENT_Y = WATER_Y + 35;
    private static final int WATER_CONTENT_WIDTH = WATER_WIDTH - 122;
    private static final int WATER_CARD_GAP = 5;
    private static final int WATER_CARD_HEIGHT = 42;

    private static final int GLASS = 0xB0020304;
    private static final int GLASS_RAISED = 0xC10B0D10;
    private static final int GLASS_SOFT = 0xB516191D;
    private static final int GLASS_ROW = 0x7A050607;
    private static final int GLASS_SHADOW = 0x52000000;
    private static final int COLUMN_GLASS = 0xA8020304;

    private final Screen parent;
    private final ColumnGlassBrowser columnBrowser = new ColumnGlassBrowser();
    private final Map<Category, Integer> columnScroll = new EnumMap<>(Category.class);
    private final Map<Category, List<Module>> filteredCache = new EnumMap<>(Category.class);
    private final Map<String, Float> rowMotion = new java.util.HashMap<>();
    private Module selectedModule;
    private Module bindingModule;
    private ModuleOption bindingActionOption;
    private ModuleOption draggingOption;
    private ModuleOption colorOption;
    private ModuleOption draggingColorOption;
    private boolean draggingColorBrightness;
    private ModuleOption editingOption;
    private int optionScroll;
    private String searchText = "";
    private String filteredQuery = "\u0000";
    private ModuleView moduleView = ModuleView.ALL;
    private boolean searchFocused;
    private boolean profileOpen;
    private boolean closing;
    private boolean closeSaved;
    private boolean openSoundPlayed;
    private String lastHoverSoundId = "";
    private final UiAnimation openMotion = new UiAnimation(0.0F);
    private Category expandedCategory = Category.RENDER;
    private final GuiFrameBudget frameBudget = new GuiFrameBudget();
    private static final Map<String, Text> FONT_TEXT_CACHE = new java.util.LinkedHashMap<>(512, 0.75F, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, Text> entry) { return size() > 2048; }
    };

    public KhanhClickGuiScreen(Screen parent) {
        this(parent, null);
    }

    public KhanhClickGuiScreen(Screen parent, Module initialModule) {
        super(Text.literal("Khanh Client"));
        this.parent = parent;
        this.selectedModule = initialModule;
        columnBrowser.select(initialModule);
        for (Category category : Category.values()) {
            columnScroll.put(category, 0);
        }
    }

    @Override
    protected void init() {
        if (KhanhFeatures.enabled("animations")) {
            openMotion.animateTo(1.0F, animationMillis(205));
        } else {
            openMotion.snapTo(1.0F);
        }
        if (!openSoundPlayed) {
            openSoundPlayed = true;
            playSound(1.22F);
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        frameBudget.frame(System.nanoTime());
        if (client != null && client.world == null) {
            // Keep the menu-side browser in the same 2D visual system as the
            // title screen instead of drawing Minecraft's panorama behind it.
            context.fill(0, 0, width, height, 0xFF090A0D);
        }
        // Glass samples one shared, locally frosted scene. Do not also run
        // Minecraft's full-screen menu blur behind it.
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        GlassGuiRenderer.begin(KhanhFeatures.choice("visuals", "gui_finish", "Glass").equals("Glass"),
            !frameBudget.reduced());
        if (closing) {
            openMotion.animateTo(0.0F, animationMillis(145));
        } else if (KhanhFeatures.enabled("animations")) {
            openMotion.animateTo(1.0F, animationMillis(205));
        } else {
            openMotion.snapTo(1.0F);
        }

        float entrance = openMotion.value();
        // A light dim keeps the game readable behind the frosted columns.
        context.fill(0, 0, width, height,
            (Math.round(56.0F * entrance) << 24));

        // Keep geometry on one stable scale while fading in. Fractional scale
        // animation made rounded borders crawl across pixels as the GUI opened.
        float scale = uiScale();
        int uiMouseX = Math.round((mouseX - width / 2.0F) / scale + VIRTUAL_WIDTH / 2.0F);
        int uiMouseY = Math.round((mouseY - height / 2.0F) / scale + VIRTUAL_HEIGHT / 2.0F);

        context.getMatrices().pushMatrix();
        context.getMatrices().translate(width / 2.0F, height / 2.0F);
        context.getMatrices().scale(scale, scale);
        context.getMatrices().translate(-VIRTUAL_WIDTH / 2.0F, -VIRTUAL_HEIGHT / 2.0F);

        drawTopBar(context, uiMouseX, uiMouseY);
        if (compactDropdownLayout()) {
            drawCompactDropdownLayout(context, uiMouseX, uiMouseY);
            if (selectedModule != null) {
                drawInspector(context, uiMouseX, uiMouseY);
            }
        } else {
            drawColumns(context, uiMouseX, uiMouseY);
        }
        if (profileOpen) drawPlayerProfileDetails(context);
        Module hovered = compactDropdownLayout() ? moduleAt(uiMouseX, uiMouseY) : columnBrowser.hoveredAt(uiMouseX, uiMouseY);
        String hoverId = hovered == null ? "" : hovered.id();
        if (!hoverId.equals(lastHoverSoundId)) {
            lastHoverSoundId = hoverId;
            if (!hoverId.isEmpty()) {
                playSound(0.70F);
            }
        }

        context.getMatrices().popMatrix();
        super.render(context, mouseX, mouseY, delta);
        if (closing && openMotion.finished()) {
            finishClose();
        }
        GlassGuiRenderer.end();
    }

    private void drawTopBar(DrawContext context, int mouseX, int mouseY) {
        int accent = GuiState.interfaceAccent();
        context.drawTexture(RenderPipelines.GUI_TEXTURED, ZERO_LOGO,
            91, 42, 0F, 0F, 42, 42, 256, 256, 256, 256);
        context.getMatrices().pushMatrix();
        context.getMatrices().translate(140, 58);
        context.getMatrices().scale(1.35F, 1.35F);
        context.drawText(textRenderer(), fontText("Khanh Client"), 0, 0, 0xFFF1F5F8, false);
        context.getMatrices().popMatrix();
        boolean searchHover = contains(mouseX, mouseY, SEARCH_X, SEARCH_Y, SEARCH_WIDTH, SEARCH_HEIGHT);
        glassPill(context, SEARCH_X, SEARCH_Y, SEARCH_WIDTH, SEARCH_HEIGHT,
            searchFocused ? accent : 0xFFFFFF, searchHover || searchFocused);
        boolean layoutHover = contains(mouseX, mouseY, LAYOUT_X, LAYOUT_Y, LAYOUT_WIDTH, LAYOUT_HEIGHT);
        glassPill(context, LAYOUT_X, LAYOUT_Y, LAYOUT_WIDTH, LAYOUT_HEIGHT, 0xFFFFFF, layoutHover);
        Text layoutLabel = fontText("Glass layout");
        context.drawText(textRenderer(), layoutLabel,
            LAYOUT_X + (LAYOUT_WIDTH - textRenderer().getWidth(layoutLabel)) / 2, LAYOUT_Y + 10,
            layoutHover ? 0xFFF1F5F8 : 0xFFA8B2BF, false);
        HudSurfaceRenderer.icon(context, HudSurfaceRenderer.HudIcon.SEARCH,
            SEARCH_X + 16, SEARCH_Y + 12, 16, 0xFF9CAAB8);
        String shown = searchText.isEmpty() && !searchFocused ? "Search modules and settings" : searchText;
        Text search = fontText(textRenderer().trimToWidth(shown, SEARCH_WIDTH - 78));
        context.drawText(textRenderer(), search, SEARCH_X + 44, SEARCH_Y + 16,
            searchText.isEmpty() ? 0xFFA8B2BF : 0xFFF1F5F8, false);
        if (searchFocused && (System.currentTimeMillis() / 430L) % 2 == 0) {
            int cursorX = SEARCH_X + 44 + textRenderer().getWidth(fontText(
                textRenderer().trimToWidth(searchText, SEARCH_WIDTH - 78)));
            context.fill(cursorX, SEARCH_Y + 12, cursorX + 1, SEARCH_Y + 28, accent);
        }
        drawPlayerProfileCard(context, mouseX, mouseY);
    }

    private void drawPlayerProfileCard(DrawContext context, int mouseX, int mouseY) {
        glassPill(context, PROFILE_X, PROFILE_Y, PROFILE_WIDTH, PROFILE_HEIGHT,
            profileOpen ? GuiState.interfaceAccent() : 0xFFFFFF,
            profileOpen || contains(mouseX, mouseY, PROFILE_X, PROFILE_Y, PROFILE_WIDTH, PROFILE_HEIGHT));
        if (client != null && client.player != null) {
            PlayerSkinDrawer.draw(context, client.player.getSkin(), PROFILE_X + 9, PROFILE_Y + 6, 28);
        } else {
            context.drawTexture(RenderPipelines.GUI_TEXTURED, ZERO_LOGO,
                PROFILE_X + 9, PROFILE_Y + 6, 0F, 0F, 28, 28, 256, 256, 256, 256);
        }
        String name = client == null ? "Player" : PrivacyService.protectedName(client.getGameProfile().name());
        context.drawText(textRenderer(), fontText(textRenderer().trimToWidth(name, 172)),
            PROFILE_X + 47, PROFILE_Y + 8, 0xFFF1F5F8, false);
        context.drawText(textRenderer(), fontText(client != null && client.player != null ? "In game" : "Main menu"),
            PROFILE_X + 47, PROFILE_Y + 23, 0xFFA8B2BF, false);
        context.drawText(textRenderer(), fontText(profileOpen ? "−" : "+"),
            PROFILE_X + PROFILE_WIDTH - 20, PROFILE_Y + 16, 0xFFD3DEE8, false);
    }

    /** Black frosted pill shared by the top bar controls. */
    private static void glassPill(DrawContext context, int x, int y, int w, int h, int tint, boolean active) {
        LiquidGlass.shadow(context, x, y, w, h, h / 2, 0.8F);
        LiquidGlass.frost(context, x, y, w, h, h / 2, 1.0F);
        LiquidGlass.glass(context, x, y, w, h, h / 2, tint, active ? 1.0F : 0.8F);
    }

    private void drawPlayerProfileDetails(DrawContext context) {
        int x = PROFILE_X, y = PROFILE_Y + PROFILE_HEIGHT + 7;
        int height = client != null && client.player != null ? 111 : 55;
        LiquidGlass.card(context, x, y, PROFILE_WIDTH, height, 12, 1.0F);
        context.drawText(textRenderer(), fontText("PLAYER PROFILE"), x + 15, y + 13,
            GuiState.interfaceAccent(), false);
        if (client == null || client.player == null) {
            context.drawText(textRenderer(), fontText("Join a world to see live details"),
                x + 15, y + 34, 0xFFA8B2BF, false);
            return;
        }
        int health = Math.round(client.player.getHealth());
        var pos = client.player.getBlockPos();
        context.drawText(textRenderer(), fontText("HEALTH  " + health + " / "
            + Math.round(client.player.getMaxHealth())), x + 15, y + 39, 0xFFEAF0F5, false);
        context.drawText(textRenderer(), fontText("LEVEL  " + client.player.experienceLevel),
            x + 15, y + 60, 0xFFEAF0F5, false);
        context.drawText(textRenderer(), fontText("POSITION  " + pos.getX() + "  " + pos.getY()
            + "  " + pos.getZ()), x + 15, y + 81, 0xFFEAF0F5, false);
    }

    private void drawColumns(DrawContext context, int mouseX, int mouseY) {
        columnBrowser.render(context, mouseX, mouseY, searchText, moduleView.name());
    }

    private void drawCompactDropdownLayout(DrawContext context, int mouseX, int mouseY) {
        int accent = GuiState.interfaceAccent();
        glassPanel(context, WATER_X, WATER_Y, WATER_WIDTH, WATER_HEIGHT, 12, 0x89020304);
        roundedRect(context, WATER_X + 5, WATER_Y + 5, WATER_SIDEBAR_WIDTH,
            WATER_HEIGHT - 10, 12, 0x7A090B0E);
        roundedRect(context, WATER_CONTENT_X, WATER_Y + 5, WATER_CONTENT_WIDTH,
            WATER_HEIGHT - 10, 12, 0x64040506);

        context.drawText(textRenderer(), fontText("KHANH CLIENT"), WATER_X + 16, WATER_Y + 17,
            pinkText(), false);
        context.drawText(textRenderer(), fontText("MODULES"), WATER_X + 16, WATER_Y + 39,
            pinkMuted(), false);

        int categoryY = WATER_Y + 58;
        for (Category category : Category.values()) {
            boolean active = category == expandedCategory;
            boolean hovered = contains(mouseX, mouseY, WATER_X + 10, categoryY,
                WATER_SIDEBAR_WIDTH - 10, 34);
            if (active || hovered) {
                roundedRect(context, WATER_X + 10, categoryY, WATER_SIDEBAR_WIDTH - 10, 34,
                    6, VisualTheme.withAlpha(accent, active ? 42 : 20));
            }
            ColumnGlassBrowser.icon(context, category, WATER_X + 14, categoryY + 9, 16, accent);
            context.drawText(textRenderer(),
                fontText(textRenderer().trimToWidth(category.displayName(), 62)),
                WATER_X + 36, categoryY + 13,
                active || hovered ? pinkText() : pinkMuted(), false);
            categoryY += 40;
        }

        Category category = expandedCategory == null ? Category.RENDER : expandedCategory;
        List<Module> modules = filteredModules(category);
        int visibleRows = 6;
        int totalRows = (modules.size() + 1) / 2;
        int maximumScroll = Math.max(0, totalRows - visibleRows);
        int scroll = Math.max(0, Math.min(columnScroll.getOrDefault(category, 0), maximumScroll));
        columnScroll.put(category, scroll);

        ColumnGlassBrowser.icon(context, category, WATER_CONTENT_X + 8, WATER_Y + 11, 16, accent);
        Text heading = fontText(category.displayName().toUpperCase(Locale.ROOT));
        context.drawText(textRenderer(), heading, WATER_CONTENT_X + 30, WATER_Y + 18,
            pinkText(), false);
        Text count = fontText(modules.stream().filter(Module::enabled).count() + " / " + modules.size());
        context.drawText(textRenderer(), count,
            WATER_CONTENT_X + WATER_CONTENT_WIDTH - 10 - textRenderer().getWidth(count),
            WATER_Y + 18, pinkMuted(), false);

        int cardWidth = (WATER_CONTENT_WIDTH - 15) / 2;
        context.enableScissor(WATER_CONTENT_X + 5, WATER_CONTENT_Y,
            WATER_CONTENT_X + WATER_CONTENT_WIDTH - 5, WATER_Y + WATER_HEIGHT - 8);
        for (int index = scroll * 2; index < Math.min(modules.size(), (scroll + visibleRows) * 2); index++) {
            Module module = modules.get(index);
            int local = index - scroll * 2;
            int col = local & 1;
            int row = local / 2;
            int cardX = WATER_CONTENT_X + 5 + col * (cardWidth + WATER_CARD_GAP);
            int cardY = WATER_CONTENT_Y + row * (WATER_CARD_HEIGHT + WATER_CARD_GAP);
            boolean hovered = contains(mouseX, mouseY, cardX, cardY, cardWidth, WATER_CARD_HEIGHT);
            float hover = rowMotion(module.id(), hovered);
            int hoverAlpha = Math.round(18.0F + hover * 34.0F);
            int cardColour = module.enabled() ? VisualTheme.withAlpha(accent, 34)
                : VisualTheme.withAlpha(0xFF0B0D10, 55 + Math.round(hover * 34.0F));
            if (!GlassGuiRenderer.surface(context, cardX, cardY, cardWidth, WATER_CARD_HEIGHT, 8, cardColour))
                roundedRect(context, cardX, cardY, cardWidth, WATER_CARD_HEIGHT, 8, cardColour);
            roundedOutline(context, cardX, cardY, cardWidth, WATER_CARD_HEIGHT, 8,
                VisualTheme.withAlpha(accent, module.enabled() ? 72 : hoverAlpha));
            context.drawText(textRenderer(),
                fontText(textRenderer().trimToWidth(module.name(), cardWidth - 38)),
                cardX + 8, cardY + 9, module.enabled() || hovered ? pinkText() : pinkMuted(), false);
            if (module.enabled() || module.keybind() >= 0) {
                String subtitle = module.enabled() ? "ACTIVE" : keyName(module.keybind());
                context.drawText(textRenderer(),
                    fontText(textRenderer().trimToWidth(subtitle, cardWidth - 38)),
                    cardX + 8, cardY + 25, pinkMuted(), false);
            }
            drawCircle(context, cardX + cardWidth - 16, cardY + 21, 5,
                module.enabled() ? accent : VisualTheme.withAlpha(accent, 30));
            drawCircle(context, cardX + cardWidth - 16, cardY + 21, 2,
                module.enabled() ? 0xFF241D25 : VisualTheme.withAlpha(accent, 70));
        }
        context.disableScissor();
    }

    private void drawDropdown(DrawContext context, int mouseX, int mouseY) {
        if (selectedModule != null) {
            drawOptionsColumn(context, mouseX, mouseY);
            return;
        }
        int accent = GuiState.interfaceAccent();
        int y = COLUMN_Y;
        int rowHeight = rowHeight();
        int visible = dropdownVisibleRows();
        glassPanel(context, DROPDOWN_X, COLUMN_Y, DROPDOWN_WIDTH,
            COLUMN_BOTTOM - COLUMN_Y, 22, COLUMN_GLASS);

        for (Category category : Category.values()) {
            List<Module> modules = filteredModules(category);
            boolean expanded = category == expandedCategory;
            boolean hovered = contains(mouseX, mouseY, DROPDOWN_X + 8, y + 4,
                DROPDOWN_WIDTH - 16, DROPDOWN_HEADER_HEIGHT - 8);
            if (hovered || expanded) {
                roundedRect(context, DROPDOWN_X + 8, y + 4, DROPDOWN_WIDTH - 16,
                    DROPDOWN_HEADER_HEIGHT - 8, 8,
                    VisualTheme.withAlpha(accent, expanded ? 34 : 18));
            }
            ColumnGlassBrowser.icon(context, category, DROPDOWN_X + 12, y + 12, 16, accent);
            context.drawText(textRenderer(), fontText(category.displayName()), DROPDOWN_X + 36,
                y + 17, expanded || hovered ? pinkText() : pinkMuted(), false);
            long active = modules.stream().filter(Module::enabled).count();
            Text count = fontText(active + " / " + modules.size() + (expanded ? "  -" : "  +"));
            context.drawText(textRenderer(), count,
                DROPDOWN_X + DROPDOWN_WIDTH - 18 - textRenderer().getWidth(count), y + 17,
                expanded ? pinkText() : pinkMuted(), false);
            y += DROPDOWN_HEADER_HEIGHT;

            if (!expanded) {
                continue;
            }
            int maximumScroll = Math.max(0, modules.size() - visible);
            int scroll = Math.max(0, Math.min(columnScroll.getOrDefault(category, 0), maximumScroll));
            columnScroll.put(category, scroll);
            int listTop = y;
            int listBottom = Math.min(COLUMN_BOTTOM, listTop + visible * rowHeight);
            context.enableScissor(DROPDOWN_X + 4, listTop,
                DROPDOWN_X + DROPDOWN_WIDTH - 4, listBottom);
            for (int index = scroll; index < Math.min(modules.size(), scroll + visible); index++) {
                Module module = modules.get(index);
                int rowY = listTop + (index - scroll) * rowHeight;
                boolean moduleHover = contains(mouseX, mouseY, DROPDOWN_X + 12, rowY,
                    DROPDOWN_WIDTH - 24, rowHeight - 3);
                if (moduleHover) {
                    roundedRect(context, DROPDOWN_X + 12, rowY, DROPDOWN_WIDTH - 24,
                        rowHeight - 3, 5, VisualTheme.withAlpha(accent, 25));
                }
                roundedRect(context, DROPDOWN_X + 16, rowY + 5, 2, rowHeight - 13, 1,
                    module.enabled() ? accent : VisualTheme.withAlpha(accent, 35));
                context.drawText(textRenderer(),
                    fontText(textRenderer().trimToWidth(module.name(), 250)),
                    DROPDOWN_X + 26, rowY + (rowHeight - 9) / 2,
                    module.enabled() || moduleHover ? pinkText() : pinkMuted(), false);
                if (module.keybind() >= 0) {
                    Text key = fontText(textRenderer().trimToWidth(keyName(module.keybind()), 42));
                    context.drawText(textRenderer(), key,
                        DROPDOWN_X + DROPDOWN_WIDTH - 18 - textRenderer().getWidth(key),
                        rowY + (rowHeight - 9) / 2,
                        module.enabled() ? pinkText() : pinkMuted(), false);
                }
            }
            context.disableScissor();
            y = listBottom;
        }
    }

    private void drawColumn(DrawContext context, Category category, int mouseX, int mouseY) {
        if (selectedModule != null && selectedModule.category() == category) {
            drawOptionsColumn(context, mouseX, mouseY);
            return;
        }
        int x = columnX(category);
        int accent = GuiState.interfaceAccent();
        List<Module> modules = filteredModules(category);
        int rowHeight = rowHeight();
        int visibleRows = visibleRows();
        int maximumScroll = Math.max(0, modules.size() - visibleRows);
        int scroll = Math.max(0, Math.min(columnScroll.getOrDefault(category, 0), maximumScroll));
        columnScroll.put(category, scroll);
        int shownRows = Math.min(modules.size(), visibleRows);
        int panelBottom = maximumScroll > 0
            ? COLUMN_BOTTOM
            : Math.min(COLUMN_BOTTOM, MODULE_TOP + shownRows * rowHeight + 8);

        glassPanel(context, x, COLUMN_Y, COLUMN_WIDTH, panelBottom - COLUMN_Y, 22, COLUMN_GLASS);
        roundedRect(context, x + 10, MODULE_TOP - 10, COLUMN_WIDTH - 20, 1, 1,
            VisualTheme.withAlpha(accent, 42));

        ColumnGlassBrowser.icon(context, category, x + 10, COLUMN_Y + 11, 16, accent);
        context.drawText(textRenderer(), fontText(category.displayName()), x + 32, COLUMN_Y + 18,
            pinkText(), false);
        long active = modules.stream().filter(Module::enabled).count();
        Text countText = fontText(active + " / " + modules.size());
        context.drawText(textRenderer(), countText, x + COLUMN_WIDTH - 10 - textRenderer().getWidth(countText),
            COLUMN_Y + 18,
            pinkMuted(), false);

        context.enableScissor(x + 3, MODULE_TOP, x + COLUMN_WIDTH - 3, panelBottom - 8);
        for (int index = scroll; index < Math.min(modules.size(), scroll + visibleRows); index++) {
            Module module = modules.get(index);
            int row = index - scroll;
            int rowY = MODULE_TOP + row * rowHeight;
            boolean hovered = contains(mouseX, mouseY, x + 7, rowY, COLUMN_WIDTH - 14, rowHeight - 3);
            float hover = rowMotion(module.id(), hovered);
            roundedRect(context, x + 7, rowY, COLUMN_WIDTH - 14, rowHeight - 3, 8,
                hover > 0.01F ? VisualTheme.withAlpha(accent, Math.round(12.0F + hover * 34.0F)) : GLASS_ROW);
            if (hover > 0.01F || module.enabled()) {
                roundedOutline(context, x + 7, rowY, COLUMN_WIDTH - 14, rowHeight - 3, 8,
                    VisualTheme.withAlpha(accent, module.enabled() ? 58 : Math.round(hover * 42.0F)));
            }
            roundedRect(context, x + 9, rowY + 5, 2, rowHeight - 13, 1,
                module.enabled() ? accent : VisualTheme.withAlpha(accent, 35));

            int keyReserve = module.keybind() < 0 ? 10 : 34;
            String label = textRenderer().trimToWidth(module.name(), COLUMN_WIDTH - 31 - keyReserve);
            context.drawText(textRenderer(), fontText(label), x + 17, rowY + (rowHeight - 9) / 2,
                module.enabled() || hovered ? pinkText() : pinkMuted(), false);
            if (module.keybind() >= 0) {
                String key = textRenderer().trimToWidth(keyName(module.keybind()), 22);
                Text keyText = fontText(key);
                int keyX = x + COLUMN_WIDTH - textRenderer().getWidth(keyText) - 10;
                context.drawText(textRenderer(), keyText, keyX,
                    rowY + (rowHeight - 9) / 2, module.enabled() ? pinkText() : pinkMuted(), false);
            }
        }
        context.disableScissor();

        if (maximumScroll > 0) {
            int trackX = x + COLUMN_WIDTH - 5;
            int trackHeight = COLUMN_BOTTOM - MODULE_TOP - 12;
            int thumbHeight = Math.max(22, Math.round(trackHeight * (visibleRows / (float) modules.size())));
            int thumbY = MODULE_TOP + Math.round((trackHeight - thumbHeight) * (scroll / (float) maximumScroll));
            roundedRect(context, trackX, thumbY, 2, thumbHeight, 1, VisualTheme.withAlpha(accent, 194));
        }
    }

    private void drawOptionsColumn(DrawContext context, int mouseX, int mouseY) {
        int x = activePanelX(selectedModule.category());
        int accent = GuiState.interfaceAccent();
        int visible = visibleOptionRows();
        int maximum = Math.max(0, selectedModule.options().size() - visible);
        optionScroll = Math.max(0, Math.min(optionScroll, maximum));
        int shown = Math.min(selectedModule.options().size(), visible);
        int panelBottom = maximum > 0 || colorOption != null
            ? COLUMN_BOTTOM
            : Math.min(COLUMN_BOTTOM, INLINE_OPTIONS_TOP + shown * OPTION_ROW_HEIGHT + 8);

        glassPanel(context, x, COLUMN_Y, COLUMN_WIDTH, panelBottom - COLUMN_Y, 22, COLUMN_GLASS);
        boolean backHover = contains(mouseX, mouseY, x + 7, COLUMN_Y + 9, COLUMN_WIDTH - 14, 42);
        context.drawText(textRenderer(), fontText("<"), x + 12, COLUMN_Y + 21,
            backHover ? pinkText() : pinkMuted(), false);
        String title = textRenderer().trimToWidth(selectedModule.name(), COLUMN_WIDTH - 48);
        context.drawText(textRenderer(), fontText(title), x + 27, COLUMN_Y + 17, pinkText(), false);
        context.drawText(textRenderer(), fontText("OPTIONS"), x + 27, COLUMN_Y + 34, pinkMuted(), false);
        roundedRect(context, x + 10, MODULE_TOP - 10, COLUMN_WIDTH - 20, 1, 1,
            VisualTheme.withAlpha(accent, 42));

        boolean powerHover = contains(mouseX, mouseY, x + 7, INLINE_POWER_Y, COLUMN_WIDTH - 14, 28);
        inspectorRow(context, x + 7, INLINE_POWER_Y, COLUMN_WIDTH - 14, 28,
            powerHover, selectedModule.enabled());
        context.drawText(textRenderer(), fontText("POWER"), x + 14, INLINE_POWER_Y + 10, pinkText(), false);
        drawStateBadge(context, x + COLUMN_WIDTH - 51, INLINE_POWER_Y + 5, selectedModule.enabled());

        boolean binding = bindingModule == selectedModule;
        boolean bindHover = contains(mouseX, mouseY, x + 7, INLINE_BIND_Y, COLUMN_WIDTH - 14, 28);
        inspectorRow(context, x + 7, INLINE_BIND_Y, COLUMN_WIDTH - 14, 28, bindHover, binding);
        context.drawText(textRenderer(), fontText("KEYBIND"), x + 14, INLINE_BIND_Y + 10, pinkText(), false);
        String bindValue = binding ? "PRESS" : keyName(selectedModule.keybind());
        Text bindText = fontText(textRenderer().trimToWidth(bindValue, 53));
        context.drawText(textRenderer(), bindText,
            x + COLUMN_WIDTH - 14 - textRenderer().getWidth(bindText), INLINE_BIND_Y + 10,
            binding ? pinkText() : pinkMuted(), false);

        context.drawText(textRenderer(), fontText("SETTINGS"), x + 10, INLINE_OPTIONS_TOP - 15,
            pinkMuted(), false);
        context.enableScissor(x + 3, INLINE_OPTIONS_TOP, x + COLUMN_WIDTH - 3, panelBottom - 8);
        int rowY = INLINE_OPTIONS_TOP;
        for (int index = optionScroll;
             index < Math.min(selectedModule.options().size(), optionScroll + visible);
             index++) {
            ModuleOption option = selectedModule.options().get(index);
            boolean hovered = contains(mouseX, mouseY, x + 7, rowY, COLUMN_WIDTH - 14,
                OPTION_ROW_HEIGHT - 4);
            boolean editing = editingOption == option || colorOption == option;
            inspectorRow(context, x + 7, rowY, COLUMN_WIDTH - 14, OPTION_ROW_HEIGHT - 4,
                hovered, editing);
            context.drawText(textRenderer(), fontText(textRenderer().trimToWidth(option.label(), 76)),
                x + 14, rowY + 10, pinkText(), false);

            if (option.kind() == ModuleOption.Kind.BOOLEAN) {
                drawStateBadge(context, x + COLUMN_WIDTH - 51, rowY + 5, option.booleanValue());
            } else if (option.kind() == ModuleOption.Kind.COLOR) {
                roundedRect(context, x + COLUMN_WIDTH - 43, rowY + 5, 30, 17, 7,
                    option.colorValue());
            } else {
                String value;
                if (option.kind() == ModuleOption.Kind.ACTION) {
                    value = bindingActionOption == option
                        ? "PRESS"
                        : "RUN " + keyName(option.actionKeybind());
                } else {
                    value = option.displayValue();
                    if (editing && (System.currentTimeMillis() / 420L) % 2 == 0) {
                        value += "|";
                    }
                }
                Text valueText = fontText(textRenderer().trimToWidth(value, 54));
                context.drawText(textRenderer(), valueText,
                    x + COLUMN_WIDTH - 14 - textRenderer().getWidth(valueText), rowY + 10,
                    editing || bindingActionOption == option ? pinkText() : pinkMuted(), false);
            }
            if (option.kind() == ModuleOption.Kind.NUMBER) {
                int sliderX = x + 14;
                int sliderWidth = COLUMN_WIDTH - 28;
                int sliderY = rowY + 24;
                roundedRect(context, sliderX, sliderY, sliderWidth, 3, 2,
                    VisualTheme.withAlpha(accent, 42));
                roundedRect(context, sliderX, sliderY,
                    Math.max(4, (int) Math.round(sliderWidth * option.ratio())), 3, 2, accent);
            }
            rowY += OPTION_ROW_HEIGHT;
        }
        context.disableScissor();

        if (maximum > 0) {
            int trackHeight = COLUMN_BOTTOM - INLINE_OPTIONS_TOP - 12;
            int thumbHeight = Math.max(18,
                Math.round(trackHeight * (visible / (float) selectedModule.options().size())));
            int thumbY = INLINE_OPTIONS_TOP
                + Math.round((trackHeight - thumbHeight) * (optionScroll / (float) maximum));
            roundedRect(context, x + COLUMN_WIDTH - 5, INLINE_OPTIONS_TOP, 2, trackHeight, 1,
                VisualTheme.withAlpha(accent, 36));
            roundedRect(context, x + COLUMN_WIDTH - 5, thumbY, 2, thumbHeight, 1,
                VisualTheme.withAlpha(accent, 205));
        }

        if (colorOption != null) {
            drawColorPicker(context, mouseX, mouseY);
        }
        if (selectedModule.id().equals("custom_glint")) {
            drawGlintPreview(context, Math.min(VIRTUAL_WIDTH - 166, x + COLUMN_WIDTH + 10),
                INLINE_OPTIONS_TOP + 12);
        }
    }

    private void drawGlintPreview(DrawContext context, int x, int y) {
        ModuleOption theme = selectedModule.option("theme");
        if (theme == null) return;
        glassPanel(context, x, y, 156, 142, 13, GLASS_RAISED);
        context.drawText(textRenderer(), fontText("GLINT PREVIEW"), x + 12, y + 12, pinkText(), false);
        Identifier image = Identifier.of(KhanhClient.MOD_ID,
            GlintPattern.texturePath(theme.choiceValue()));
        context.drawTexture(RenderPipelines.GUI_TEXTURED, image,
            x + 12, y + 31, 0F, 0F, 132, 82, 128, 128, 128, 128);
        context.drawText(textRenderer(), fontText(theme.choiceValue().toUpperCase(Locale.ROOT)),
            x + 12, y + 121, pinkMuted(), false);
    }

    private void drawInspector(DrawContext context, int mouseX, int mouseY) {
        float progress = 1.0F;
        int x = INSPECTOR_X;
        int y = INSPECTOR_Y + Math.round((1.0F - progress) * 18.0F);
        int accent = GuiState.interfaceAccent();

        glassPanel(context, x, y, INSPECTOR_WIDTH, INSPECTOR_HEIGHT, 24,
            VisualTheme.withAlpha(GLASS_RAISED, Math.round(232 * progress)));
        GlassGuiRenderer.surface(context,x,y,INSPECTOR_WIDTH,INSPECTOR_HEIGHT,24,
            VisualTheme.withAlpha(GLASS_RAISED,Math.round(90*progress)));

        roundedRect(context, x + 12, y + 12, INSPECTOR_WIDTH - 24, 58, 24,
            VisualTheme.withAlpha(accent, 35));
        drawCircle(context, x + 40, y + 41, 15, accent);
        String mark = selectedModule.name().substring(0, 1).toUpperCase(Locale.ROOT);
        Text markText = fontText(mark);
        context.drawText(textRenderer(), markText, x + 40 - textRenderer().getWidth(markText) / 2,
            y + 38, 0xFF241D25, false);
        context.drawText(textRenderer(), fontText(textRenderer().trimToWidth(selectedModule.name(), 184)),
            x + 64, y + 29, pinkText(), false);
        context.drawText(textRenderer(), fontText(selectedModule.category().displayName()), x + 64, y + 46,
            pinkMuted(), false);
        boolean closeHover = contains(mouseX, mouseY, x + INSPECTOR_WIDTH - 48, y + 23, 28, 28);
        drawCircle(context, x + INSPECTOR_WIDTH - 34, y + 37, 12,
            closeHover ? VisualTheme.withAlpha(accent, 92) : VisualTheme.withAlpha(accent, 25));
        context.drawText(textRenderer(), fontText("×"), x + INSPECTOR_WIDTH - 37, y + 33,
            closeHover ? pinkText() : pinkMuted(), false);

        String description = textRenderer().trimToWidth(selectedModule.description(), INSPECTOR_WIDTH - 40);
        context.drawText(textRenderer(), fontText(description), x + 20, y + 82, pinkMuted(), false);

        int powerY = y + 102;
        boolean powerHover = contains(mouseX, mouseY, x + 16, powerY, INSPECTOR_WIDTH - 32, 34);
        inspectorRow(context, x + 16, powerY, INSPECTOR_WIDTH - 32, 34, powerHover, selectedModule.enabled());
        context.drawText(textRenderer(), fontText("MODULE POWER"), x + 30, powerY + 13, pinkText(), false);
        drawStateBadge(context, x + INSPECTOR_WIDTH - 68, powerY + 8, selectedModule.enabled());

        int bindY = powerY + 42;
        boolean binding = bindingModule == selectedModule;
        boolean bindHover = contains(mouseX, mouseY, x + 16, bindY, INSPECTOR_WIDTH - 32, 30);
        inspectorRow(context, x + 16, bindY, INSPECTOR_WIDTH - 32, 30, bindHover, binding);
        context.drawText(textRenderer(), fontText("KEYBIND"), x + 30, bindY + 11, pinkText(), false);
        String bindValue = binding ? "PRESS KEY" : keyName(selectedModule.keybind());
        Text bindText = fontText(bindValue);
        context.drawText(textRenderer(), bindText,
            x + INSPECTOR_WIDTH - 30 - textRenderer().getWidth(bindText), bindY + 11,
            binding ? pinkText() : pinkMuted(), false);

        context.drawText(textRenderer(), fontText("OPTIONS"), x + 22, bindY + 43, pinkMuted(), false);
        int contentTop = bindY + 56;
        int visible = visibleOptionRows();
        int maximum = Math.max(0, selectedModule.options().size() - visible);
        optionScroll = Math.max(0, Math.min(optionScroll, maximum));
        context.enableScissor(x + 12, contentTop, x + INSPECTOR_WIDTH - 12, y + INSPECTOR_HEIGHT - 25);
        int rowY = contentTop;
        for (int index = optionScroll;
             index < Math.min(selectedModule.options().size(), optionScroll + visible);
             index++) {
            ModuleOption option = selectedModule.options().get(index);
            boolean hovered = contains(mouseX, mouseY, x + 16, rowY, INSPECTOR_WIDTH - 32, OPTION_ROW_HEIGHT - 4);
            boolean editing = editingOption == option || colorOption == option;
            inspectorRow(context, x + 16, rowY, INSPECTOR_WIDTH - 32, OPTION_ROW_HEIGHT - 4,
                hovered, editing);
            context.drawText(textRenderer(), fontText(textRenderer().trimToWidth(option.label(), 142)),
                x + 28, rowY + 9, pinkText(), false);
            if (option.kind() == ModuleOption.Kind.BOOLEAN) {
                drawStateBadge(context, x + INSPECTOR_WIDTH - 66, rowY + 5, option.booleanValue());
            } else if (option.kind() == ModuleOption.Kind.COLOR) {
                Text valueText = fontText(option.displayValue());
                context.drawText(textRenderer(), valueText,
                    x + INSPECTOR_WIDTH - 68 - textRenderer().getWidth(valueText), rowY + 9,
                    pinkText(), false);
                roundedRect(context, x + INSPECTOR_WIDTH - 58, rowY + 5, 30, 17, 7,
                    option.colorValue());
            } else if (option.kind() == ModuleOption.Kind.ACTION) {
                String value = bindingActionOption == option
                    ? "PRESS KEY"
                    : "RUN • " + keyName(option.actionKeybind());
                Text valueText = fontText(textRenderer().trimToWidth(value, 104));
                context.drawText(textRenderer(), valueText,
                    x + INSPECTOR_WIDTH - 28 - textRenderer().getWidth(valueText), rowY + 9,
                    bindingActionOption == option ? pinkText() : pinkMuted(), false);
            } else {
                String value = option.kind() == ModuleOption.Kind.TEXT
                    ? textRenderer().trimToWidth(option.displayValue(), 92)
                    : option.displayValue();
                if (editing && (System.currentTimeMillis() / 420L) % 2 == 0) {
                    value += "|";
                }
                Text valueText = fontText(value);
                context.drawText(textRenderer(), valueText,
                    x + INSPECTOR_WIDTH - 28 - textRenderer().getWidth(valueText), rowY + 9,
                    pinkText(), false);
            }
            if (option.kind() == ModuleOption.Kind.NUMBER) {
                int sliderX = x + 28;
                int sliderWidth = INSPECTOR_WIDTH - 56;
                int sliderY = rowY + 23;
                roundedRect(context, sliderX, sliderY, sliderWidth, 3, 2, VisualTheme.withAlpha(accent, 42));
                roundedRect(context, sliderX, sliderY,
                    Math.max(4, (int) Math.round(sliderWidth * option.ratio())), 3, 2, accent);
            }
            rowY += OPTION_ROW_HEIGHT;
        }
        context.disableScissor();

        if (maximum > 0) {
            int trackHeight = y + INSPECTOR_HEIGHT - 29 - contentTop;
            int thumbHeight = Math.max(18,
                Math.round(trackHeight * (visible / (float) selectedModule.options().size())));
            int thumbY = contentTop + Math.round((trackHeight - thumbHeight) * (optionScroll / (float) maximum));
            roundedRect(context, x + INSPECTOR_WIDTH - 9, contentTop, 2, trackHeight, 1,
                VisualTheme.withAlpha(accent, 36));
            roundedRect(context, x + INSPECTOR_WIDTH - 9, thumbY, 2, thumbHeight, 1,
                VisualTheme.withAlpha(accent, 205));
        }

        if (colorOption != null) {
            drawColorPicker(context, mouseX, mouseY);
        }
        if (selectedModule.id().equals("custom_glint")) {
            drawGlintPreview(context, x + INSPECTOR_WIDTH + 10, y + 82);
        }
    }

    private void drawColorPicker(DrawContext context, int mouseX, int mouseY) {
        int x = colorPickerX();
        int y = colorPickerY();
        int accent = GuiState.interfaceAccent();
        glassPanel(context, x, y, COLOR_PICKER_WIDTH, COLOR_PICKER_HEIGHT, 20, GLASS_RAISED);
        String pickerTitle = textRenderer().trimToWidth(
            colorOption.label().toUpperCase(Locale.ROOT), COLOR_PICKER_WIDTH - 36);
        context.drawText(textRenderer(), fontText(pickerTitle), x + 18, y + 15, pinkText(), false);

        int cx = x + COLOR_PICKER_WIDTH / 2;
        int cy = y + 72;
        int radiusSquared = COLOR_WHEEL_RADIUS * COLOR_WHEEL_RADIUS;
        // Two-pixel cells retain a smooth wheel at Minecraft GUI scale while
        // reducing draw calls by roughly 75 percent during colour editing.
        for (int py = -COLOR_WHEEL_RADIUS; py <= COLOR_WHEEL_RADIUS; py += 2) {
            for (int px = -COLOR_WHEEL_RADIUS; px <= COLOR_WHEEL_RADIUS; px += 2) {
                int distanceSquared = px * px + py * py;
                if (distanceSquared > radiusSquared) {
                    continue;
                }
                float hue = (float) (Math.atan2(py, px) / (Math.PI * 2.0));
                if (hue < 0.0F) {
                    hue += 1.0F;
                }
                float saturation = Math.min(1.0F,
                    (float) Math.sqrt(distanceSquared) / COLOR_WHEEL_RADIUS);
                context.fill(cx + px, cy + py, cx + px + 2, cy + py + 2,
                    hsvColor(hue, saturation));
            }
        }

        float hue = colorHue(colorOption.colorValue());
        float saturation = colorSaturation(colorOption.colorValue());
        double angle = hue * Math.PI * 2.0;
        int markerX = cx + (int) Math.round(Math.cos(angle) * saturation * COLOR_WHEEL_RADIUS);
        int markerY = cy + (int) Math.round(Math.sin(angle) * saturation * COLOR_WHEEL_RADIUS);
        drawCircle(context, markerX, markerY, 7, 0xA0221B23);
        drawCircle(context, markerX, markerY, 5, 0xFFFFFFFF);
        drawCircle(context, markerX, markerY, 3, colorOption.colorValue());

        int brightnessX = x + 17;
        int brightnessY = y + 119;
        int brightnessWidth = COLOR_PICKER_WIDTH - 34;
        float brightness = colorBrightness(colorOption.colorValue());
        for (int index = 0; index < brightnessWidth; index++) {
            float value = index / (float) Math.max(1, brightnessWidth - 1);
            context.fill(brightnessX + index, brightnessY, brightnessX + index + 1, brightnessY + 8,
                hsvColor(hue, saturation, value));
        }
        int brightnessMarker = brightnessX + Math.round(brightness * (brightnessWidth - 1));
        roundedRect(context, brightnessMarker - 2, brightnessY - 2, 4, 12, 2, 0xFFFFFFFF);
        roundedRect(context, brightnessMarker - 1, brightnessY - 1, 2, 10, 1, 0xFF151311);

        roundedRect(context, x + 17, y + 143, 22, 16, 7, colorOption.colorValue());
        context.drawText(textRenderer(), fontText(colorOption.displayValue()), x + 47, y + 147,
            pinkText(), false);
    }

    private void inspectorRow(DrawContext context, int x, int y, int rowWidth, int rowHeight,
                              boolean hovered, boolean active) {
        int accent = GuiState.interfaceAccent();
        roundedRect(context, x, y, rowWidth, rowHeight, 10,
            active ? VisualTheme.withAlpha(accent, 44) : hovered ? GLASS_SOFT : GLASS_ROW);
        if (active) {
            roundedRect(context, x, y + 5, 2, rowHeight - 10, 1, accent);
        }
    }

    private void drawStateBadge(DrawContext context, int x, int y, boolean enabled) {
        int accent = GuiState.interfaceAccent();
        roundedRect(context, x, y, 38, 18, 9,
            enabled ? VisualTheme.withAlpha(accent, 205) : VisualTheme.withAlpha(accent, 20));
        Text state = fontText(enabled ? "ON" : "OFF");
        context.drawText(textRenderer(), state, x + 19 - textRenderer().getWidth(state) / 2, y + 6,
            enabled ? 0xFF241D25 : pinkMuted(), false);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        double mouseX = toUiX(click.x());
        double mouseY = toUiY(click.y());

        if (contains(mouseX, mouseY, LAYOUT_X, LAYOUT_Y, LAYOUT_WIDTH, LAYOUT_HEIGHT) && client != null) {
            ConfigManager.save(KhanhClient.modules());
            KhanhClickGui.switchLayout(client, parent, true);
            return true;
        }

        if (contains(mouseX, mouseY, PROFILE_X, PROFILE_Y, PROFILE_WIDTH, PROFILE_HEIGHT)) {
            profileOpen = !profileOpen;
            searchFocused = false;
            playSound(profileOpen ? 1.08F : 0.88F);
            return true;
        }
        if (profileOpen && contains(mouseX, mouseY, PROFILE_X, PROFILE_Y + PROFILE_HEIGHT + 7,
            PROFILE_WIDTH, client != null && client.player != null ? 111 : 55)) return true;

        boolean columnArea = !compactDropdownLayout() && columnBrowser.containsColumn(mouseX, mouseY);
        if (!compactDropdownLayout() && columnArea) {
            searchFocused = false;
            return columnBrowser.mouseClicked(mouseX, mouseY, click.button());
        }
        if (!compactDropdownLayout()) columnBrowser.blur();

        if (colorOption != null) {
            if (handleColorPickerClick(mouseX, mouseY, click.button())) {
                return true;
            }
            colorOption = null;
            draggingColorOption = null;
        }

        if (selectedModule != null && compactDropdownLayout()
            && handleInspectorClick(mouseX, mouseY, click.button())) {
            return true;
        }
        if (selectedModule != null && !compactDropdownLayout()
            && handleOptionsColumnClick(mouseX, mouseY, click.button())) {
            return true;
        }

        if (contains(mouseX, mouseY, SEARCH_X, SEARCH_Y, SEARCH_WIDTH, SEARCH_HEIGHT)) {
            searchFocused = true;
            editingOption = null;
            bindingModule = null;
            bindingActionOption = null;
            playSound(1.02F);
            return true;
        }
        searchFocused = false;

        if (compactDropdownLayout() && selectedModule == null) {
            Category header = dropdownHeaderAt(mouseX, mouseY);
            if (header != null) {
                expandedCategory = header;
                resetColumnScroll();
                playSound(1.06F);
                return true;
            }
        }

        Module module = moduleAt(mouseX, mouseY);
        if (module != null) {
            if (module.id().equals("config") && click.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                client.setScreen(new KhanhConfigScreen(this));
                playSound(1.08F);
                return true;
            }
            if (click.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                module.toggle();
                playSound(module.enabled() ? 1.30F : 0.82F);
                ConfigManager.save(KhanhClient.modules());
            } else if (click.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                selectModule(module);
            }
            return true;
        }
        return super.mouseClicked(click, doubled);
    }

    private boolean handleInspectorClick(double mouseX, double mouseY, int button) {
        int x = INSPECTOR_X;
        int y = INSPECTOR_Y;
        if (!contains(mouseX, mouseY, x, y, INSPECTOR_WIDTH, INSPECTOR_HEIGHT)) {
            return false;
        }
        if (contains(mouseX, mouseY, x + INSPECTOR_WIDTH - 48, y + 23, 28, 28)) {
            selectedModule = null;
            bindingModule = null;
            bindingActionOption = null;
            draggingOption = null;
            colorOption = null;
            draggingColorOption = null;
            editingOption = null;
            optionScroll = 0;
            playSound(0.86F);
            return true;
        }

        int powerY = y + 102;
        if (contains(mouseX, mouseY, x + 16, powerY, INSPECTOR_WIDTH - 32, 34)) {
            selectedModule.toggle();
            editingOption = null;
            playSound(selectedModule.enabled() ? 1.30F : 0.82F);
            ConfigManager.save(KhanhClient.modules());
            return true;
        }

        int bindY = powerY + 42;
        if (contains(mouseX, mouseY, x + 16, bindY, INSPECTOR_WIDTH - 32, 30)) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                selectedModule.setKeybind(-1);
                bindingModule = null;
                playSound(0.80F);
                ConfigManager.save(KhanhClient.modules());
            } else if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                bindingModule = selectedModule;
                bindingActionOption = null;
                editingOption = null;
                playSound(1.16F);
            }
            return true;
        }

        int rowY = bindY + 56;
        int visible = visibleOptionRows();
        for (int index = optionScroll;
             index < Math.min(selectedModule.options().size(), optionScroll + visible);
             index++) {
            ModuleOption option = selectedModule.options().get(index);
            if (contains(mouseX, mouseY, x + 16, rowY, INSPECTOR_WIDTH - 32, OPTION_ROW_HEIGHT - 4)) {
                if (option.kind() != ModuleOption.Kind.COLOR) {
                    colorOption = null;
                    draggingColorOption = null;
                }
                if (option.kind() != ModuleOption.Kind.TEXT) {
                    editingOption = null;
                }
                if (option.kind() == ModuleOption.Kind.BOOLEAN) {
                    option.toggle();
                } else if (option.kind() == ModuleOption.Kind.CHOICE) {
                    option.cycle(button == GLFW.GLFW_MOUSE_BUTTON_RIGHT ? -1 : 1);
                } else if (option.kind() == ModuleOption.Kind.NUMBER) {
                    draggingOption = option;
                    updateSlider(option, mouseX);
                } else if (option.kind() == ModuleOption.Kind.COLOR && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                    colorOption = option;
                } else if (option.kind() == ModuleOption.Kind.TEXT && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                    editingOption = option;
                } else if (option.kind() == ModuleOption.Kind.ACTION) {
                    if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                        bindingActionOption = option;
                        bindingModule = null;
                    } else if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                        bindingActionOption = null;
                        option.trigger();
                    }
                }
                playSound(button == GLFW.GLFW_MOUSE_BUTTON_RIGHT ? 0.90F : 1.14F);
                ConfigManager.save(KhanhClient.modules());
                return true;
            }
            rowY += OPTION_ROW_HEIGHT;
        }
        return true;
    }

    private boolean handleOptionsColumnClick(double mouseX, double mouseY, int button) {
        int x = activePanelX(selectedModule.category());
        if (!contains(mouseX, mouseY, x, COLUMN_Y, COLUMN_WIDTH, COLUMN_BOTTOM - COLUMN_Y)) {
            return false;
        }
        if (contains(mouseX, mouseY, x + 7, COLUMN_Y + 9, COLUMN_WIDTH - 14, 42)) {
            selectedModule = null;
            bindingModule = null;
            bindingActionOption = null;
            draggingOption = null;
            colorOption = null;
            draggingColorOption = null;
            editingOption = null;
            optionScroll = 0;
            playSound(0.86F);
            return true;
        }
        if (contains(mouseX, mouseY, x + 7, INLINE_POWER_Y, COLUMN_WIDTH - 14, 28)) {
            selectedModule.toggle();
            editingOption = null;
            playSound(selectedModule.enabled() ? 1.30F : 0.82F);
            ConfigManager.save(KhanhClient.modules());
            return true;
        }
        if (contains(mouseX, mouseY, x + 7, INLINE_BIND_Y, COLUMN_WIDTH - 14, 28)) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                selectedModule.setKeybind(-1);
                bindingModule = null;
                playSound(0.80F);
                ConfigManager.save(KhanhClient.modules());
            } else if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                bindingModule = selectedModule;
                bindingActionOption = null;
                editingOption = null;
                playSound(1.16F);
            }
            return true;
        }

        int rowY = INLINE_OPTIONS_TOP;
        int visible = visibleOptionRows();
        for (int index = optionScroll;
             index < Math.min(selectedModule.options().size(), optionScroll + visible);
             index++) {
            ModuleOption option = selectedModule.options().get(index);
            if (contains(mouseX, mouseY, x + 7, rowY, COLUMN_WIDTH - 14, OPTION_ROW_HEIGHT - 4)) {
                if (option.kind() != ModuleOption.Kind.COLOR) {
                    colorOption = null;
                    draggingColorOption = null;
                }
                if (option.kind() != ModuleOption.Kind.TEXT) {
                    editingOption = null;
                }
                if (option.kind() == ModuleOption.Kind.BOOLEAN) {
                    option.toggle();
                } else if (option.kind() == ModuleOption.Kind.CHOICE) {
                    option.cycle(button == GLFW.GLFW_MOUSE_BUTTON_RIGHT ? -1 : 1);
                } else if (option.kind() == ModuleOption.Kind.NUMBER) {
                    draggingOption = option;
                    updateSlider(option, mouseX);
                } else if (option.kind() == ModuleOption.Kind.COLOR
                    && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                    colorOption = option;
                } else if (option.kind() == ModuleOption.Kind.TEXT
                    && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                    editingOption = option;
                } else if (option.kind() == ModuleOption.Kind.ACTION) {
                    if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                        bindingActionOption = option;
                        bindingModule = null;
                    } else if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                        bindingActionOption = null;
                        option.trigger();
                    }
                }
                playSound(button == GLFW.GLFW_MOUSE_BUTTON_RIGHT ? 0.90F : 1.14F);
                ConfigManager.save(KhanhClient.modules());
                return true;
            }
            rowY += OPTION_ROW_HEIGHT;
        }
        return true;
    }

    private boolean handleColorPickerClick(double mouseX, double mouseY, int button) {
        int pickerX = colorPickerX();
        int pickerY = colorPickerY();
        if (!contains(mouseX, mouseY, pickerX, pickerY,
            COLOR_PICKER_WIDTH, COLOR_PICKER_HEIGHT)) {
            return false;
        }
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return true;
        }
        int cx = pickerX + COLOR_PICKER_WIDTH / 2;
        int cy = pickerY + 72;
        double dx = mouseX - cx;
        double dy = mouseY - cy;
        if (dx * dx + dy * dy <= COLOR_WHEEL_RADIUS * COLOR_WHEEL_RADIUS) {
            draggingColorOption = colorOption;
            draggingColorBrightness = false;
            updateColorFromWheel(mouseX, mouseY);
            playSound(1.18F);
            ConfigManager.save(KhanhClient.modules());
        } else if (contains(mouseX, mouseY, pickerX + 17, pickerY + 117,
            COLOR_PICKER_WIDTH - 34, 12)) {
            draggingColorOption = colorOption;
            draggingColorBrightness = true;
            updateColorBrightness(mouseX);
            playSound(1.12F);
            ConfigManager.save(KhanhClient.modules());
        }
        return true;
    }

    private void selectModule(Module module) {
        if (module == null) {
            return;
        }
        selectedModule = module;
        bindingModule = null;
        bindingActionOption = null;
        colorOption = null;
        draggingColorOption = null;
        draggingColorBrightness = false;
        editingOption = null;
        optionScroll = 0;
        playSound(1.10F);
    }

    @Override
    public boolean mouseDragged(Click click, double deltaX, double deltaY) {
        if (!compactDropdownLayout() && columnBrowser.mouseDragged(toUiX(click.x()), toUiY(click.y()))) return true;
        if (draggingColorOption != null && colorOption == draggingColorOption) {
            if (draggingColorBrightness) updateColorBrightness(toUiX(click.x()));
            else updateColorFromWheel(toUiX(click.x()), toUiY(click.y()));
            return true;
        }
        if (draggingOption != null && selectedModule != null) {
            updateSlider(draggingOption, toUiX(click.x()));
            return true;
        }
        return super.mouseDragged(click, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(Click click) {
        if (!compactDropdownLayout() && columnBrowser.mouseReleased()) return true;
        if (draggingColorOption != null) {
            draggingColorOption = null;
            draggingColorBrightness = false;
            ConfigManager.save(KhanhClient.modules());
            return true;
        }
        if (draggingOption != null) {
            draggingOption = null;
            ConfigManager.save(KhanhClient.modules());
            return true;
        }
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        mouseX = toUiX(mouseX);
        mouseY = toUiY(mouseY);
        if (!compactDropdownLayout()) return columnBrowser.mouseScrolled(mouseX, mouseY, verticalAmount);
        if (selectedModule != null && compactDropdownLayout()
            && contains(mouseX, mouseY, INSPECTOR_X, INSPECTOR_Y, INSPECTOR_WIDTH, INSPECTOR_HEIGHT)) {
            int maximum = Math.max(0, selectedModule.options().size() - visibleOptionRows());
            if (maximum > 0) {
                optionScroll = Math.max(0, Math.min(maximum,
                    optionScroll - (int) Math.signum(verticalAmount)));
                playSound(0.68F);
            }
            return true;
        }
        if (selectedModule != null && !compactDropdownLayout()
            && contains(mouseX, mouseY, activePanelX(selectedModule.category()), COLUMN_Y,
                COLUMN_WIDTH, COLUMN_BOTTOM - COLUMN_Y)) {
            int maximum = Math.max(0, selectedModule.options().size() - visibleOptionRows());
            if (maximum > 0) {
                optionScroll = Math.max(0, Math.min(maximum,
                    optionScroll - (int) Math.signum(verticalAmount)));
                playSound(0.68F);
            }
            return true;
        }
        Category category = categoryAt(mouseX, mouseY);
        if (category != null) {
            int maximum = compactDropdownLayout()
                ? Math.max(0, (filteredModules(category).size() + 1) / 2 - 6)
                : Math.max(0, filteredModules(category).size() - visibleRows());
            if (maximum > 0) {
                int current = columnScroll.getOrDefault(category, 0);
                columnScroll.put(category, Math.max(0, Math.min(maximum,
                    current - (int) Math.signum(verticalAmount))));
                playSound(0.66F);
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (!compactDropdownLayout() && !searchFocused && columnBrowser.keyPressed(input)) return true;
        if (editingOption != null) {
            if (input.key() == GLFW.GLFW_KEY_BACKSPACE) {
                editingOption.deleteLastTextCharacter();
                return true;
            }
            if (input.key() == GLFW.GLFW_KEY_ENTER || input.key() == GLFW.GLFW_KEY_KP_ENTER
                || input.key() == GLFW.GLFW_KEY_TAB) {
                editingOption = null;
                ConfigManager.save(KhanhClient.modules());
                playSound(1.18F);
                return true;
            }
            if (input.isEscape()) {
                editingOption = null;
                ConfigManager.save(KhanhClient.modules());
                playSound(0.84F);
                return true;
            }
        }
        if (bindingActionOption != null) {
            if (input.isEscape()) {
                bindingActionOption = null;
                playSound(0.82F);
                return true;
            }
            if (input.key() == GLFW.GLFW_KEY_BACKSPACE || input.key() == GLFW.GLFW_KEY_DELETE) {
                bindingActionOption.setActionKeybind(-1);
            } else if (input.key() != GLFW.GLFW_KEY_RIGHT_SHIFT) {
                bindingActionOption.setActionKeybind(input.key());
            }
            bindingActionOption = null;
            ConfigManager.save(KhanhClient.modules());
            playSound(1.32F);
            return true;
        }
        if (bindingModule != null) {
            if (input.isEscape()) {
                bindingModule = null;
                playSound(0.82F);
                return true;
            }
            if (input.key() == GLFW.GLFW_KEY_BACKSPACE || input.key() == GLFW.GLFW_KEY_DELETE) {
                bindingModule.setKeybind(-1);
            } else if (input.key() != GLFW.GLFW_KEY_RIGHT_SHIFT) {
                bindingModule.setKeybind(input.key());
            }
            bindingModule = null;
            ConfigManager.save(KhanhClient.modules());
            playSound(1.32F);
            return true;
        }
        if (searchFocused) {
            if (input.key() == GLFW.GLFW_KEY_BACKSPACE) {
                if (!searchText.isEmpty()) {
                    int end = searchText.offsetByCodePoints(searchText.length(), -1);
                    searchText = searchText.substring(0, end);
                    resetColumnScroll();
                }
                return true;
            }
            if (input.key() == GLFW.GLFW_KEY_ENTER || input.key() == GLFW.GLFW_KEY_KP_ENTER
                || input.key() == GLFW.GLFW_KEY_TAB) {
                searchFocused = false;
                return true;
            }
            if (input.isEscape()) {
                if (!searchText.isEmpty()) {
                    searchText = "";
                    resetColumnScroll();
                } else {
                    searchFocused = false;
                }
                return true;
            }
        }
        if (input.isEscape() && selectedModule != null) {
            selectedModule = null;
            bindingModule = null;
            bindingActionOption = null;
            draggingOption = null;
            colorOption = null;
            draggingColorOption = null;
            editingOption = null;
            optionScroll = 0;
            playSound(0.86F);
            return true;
        }
        if (input.isEscape() || input.key() == GLFW.GLFW_KEY_RIGHT_SHIFT) {
            close();
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public boolean charTyped(CharInput input) {
        if (!compactDropdownLayout() && !searchFocused && input.isValidChar() && columnBrowser.charTyped(input.asString())) return true;
        if (editingOption != null && input.isValidChar()) {
            editingOption.appendText(input.asString());
            return true;
        }
        if (searchFocused && input.isValidChar() && searchText.codePointCount(0, searchText.length()) < 32) {
            searchText += input.asString();
            resetColumnScroll();
            return true;
        }
        return super.charTyped(input);
    }

    @Override
    public void close() {
        if (closing) {
            return;
        }
        if (!closeSaved) {
            closeSaved = true;
            ConfigManager.save(KhanhClient.modules());
            playSound(0.76F);
        }
        if (KhanhFeatures.enabled("animations")) {
            closing = true;
            openMotion.animateTo(0.0F, animationMillis(145));
        } else {
            finishClose();
        }
    }

    private void finishClose() {
        if (client != null && client.currentScreen == this) {
            client.setScreen(parent);
        }
    }

    private List<Module> filteredModules(Category category) {
        String query = searchText.trim().toLowerCase(Locale.ROOT);
        String filterKey = moduleView.name() + ":" + query;
        if (!filterKey.equals(filteredQuery)) {
            filteredQuery = filterKey;
            filteredCache.clear();
        }
        List<Module> cached = filteredCache.get(category);
        if (cached != null) {
            return cached;
        }
        List<Module> result = new ArrayList<>();
        for (Module module : KhanhClient.modules().inCategory(category)) {
            if (query.isEmpty() && GuiState.SETTINGS_ONLY.contains(module.id())) continue;
            if (moduleView == ModuleView.ACTIVE && !module.enabled()) continue;
            if (moduleView == ModuleView.BOUND && module.keybind() < 0) continue;
            if (query.isEmpty()) {
                result.add(module);
                continue;
            }
            String haystack = (module.name() + " " + module.description() + " " + module.id()
                + " " + category.displayName()).toLowerCase(Locale.ROOT);
            if (haystack.contains(query)) {
                result.add(module);
            }
        }
        List<Module> frozen = List.copyOf(result);
        filteredCache.put(category, frozen);
        return frozen;
    }

    private ModuleView commandViewAt(double mouseX, double mouseY) {
        if (mouseY < 536 || mouseY >= 554) return null;
        int x = 738;
        for (ModuleView view : ModuleView.values()) {
            int width = view == ModuleView.ACTIVE ? 96 : 82;
            if (contains(mouseX, mouseY, x, 536, width, 18)) return view;
            x += width + 8;
        }
        return null;
    }

    private Module moduleAt(double mouseX, double mouseY) {
        if (compactDropdownLayout()) {
            if (selectedModule != null || !contains(mouseX, mouseY, WATER_CONTENT_X + 5,
                WATER_CONTENT_Y, WATER_CONTENT_WIDTH - 10, WATER_HEIGHT - 43)) {
                return null;
            }
            Category category = expandedCategory == null ? Category.RENDER : expandedCategory;
            int cardWidth = (WATER_CONTENT_WIDTH - 15) / 2;
            int localX = (int) mouseX - (WATER_CONTENT_X + 5);
            int column = localX >= cardWidth + WATER_CARD_GAP ? 1 : 0;
            int row = ((int) mouseY - WATER_CONTENT_Y) / (WATER_CARD_HEIGHT + WATER_CARD_GAP);
            int withinY = ((int) mouseY - WATER_CONTENT_Y) % (WATER_CARD_HEIGHT + WATER_CARD_GAP);
            int withinX = localX - column * (cardWidth + WATER_CARD_GAP);
            if (row < 0 || row >= 6 || withinY >= WATER_CARD_HEIGHT || withinX < 0 || withinX >= cardWidth) {
                return null;
            }
            int index = (columnScroll.getOrDefault(category, 0) + row) * 2 + column;
            List<Module> modules = filteredModules(category);
            return index >= 0 && index < modules.size() ? modules.get(index) : null;
        }
        Category category = categoryAt(mouseX, mouseY);
        if (category == null || mouseY < MODULE_TOP || mouseY >= COLUMN_BOTTOM - 8) {
            return null;
        }
        if (selectedModule != null && selectedModule.category() == category) {
            return null;
        }
        int x = columnX(category);
        if (!contains(mouseX, mouseY, x + 7, MODULE_TOP, COLUMN_WIDTH - 14, COLUMN_BOTTOM - MODULE_TOP - 8)) {
            return null;
        }
        int index = columnScroll.getOrDefault(category, 0) + ((int) mouseY - MODULE_TOP) / rowHeight();
        List<Module> modules = filteredModules(category);
        return index >= 0 && index < modules.size() ? modules.get(index) : null;
    }

    private Category categoryAt(double mouseX, double mouseY) {
        if (compactDropdownLayout()) {
            return selectedModule == null && contains(mouseX, mouseY, WATER_CONTENT_X,
                WATER_CONTENT_Y, WATER_CONTENT_WIDTH, WATER_HEIGHT - 43)
                ? (expandedCategory == null ? Category.RENDER : expandedCategory) : null;
        }
        if (mouseY < COLUMN_Y || mouseY > COLUMN_BOTTOM) {
            return null;
        }
        for (Category category : Category.values()) {
            if (contains(mouseX, mouseY, columnX(category), COLUMN_Y, COLUMN_WIDTH, COLUMN_BOTTOM - COLUMN_Y)) {
                return category;
            }
        }
        return null;
    }

    private int columnX(Category category) {
        return COLUMN_X + category.ordinal() * (COLUMN_WIDTH + COLUMN_GAP);
    }

    private int activePanelX(Category category) {
        return columnX(category);
    }

    private boolean compactDropdownLayout() {
        return KhanhFeatures.choice("visuals", "gui_layout", "Columns").equals("Cards");
    }

    private int dropdownVisibleRows() {
        int space = COLUMN_BOTTOM - COLUMN_Y - Category.values().length * DROPDOWN_HEADER_HEIGHT;
        return Math.max(1, space / rowHeight());
    }

    private int dropdownListTop(Category target) {
        int y = COLUMN_Y;
        for (Category category : Category.values()) {
            y += DROPDOWN_HEADER_HEIGHT;
            if (category == target) {
                return y;
            }
        }
        return COLUMN_Y;
    }

    private Category dropdownHeaderAt(double mouseX, double mouseY) {
        if (compactDropdownLayout()) {
            int y = WATER_Y + 58;
            for (Category category : Category.values()) {
                if (contains(mouseX, mouseY, WATER_X + 10, y, WATER_SIDEBAR_WIDTH - 10, 34)) {
                    return category;
                }
                y += 40;
            }
            return null;
        }
        if (!contains(mouseX, mouseY, DROPDOWN_X, COLUMN_Y,
            DROPDOWN_WIDTH, COLUMN_BOTTOM - COLUMN_Y)) {
            return null;
        }
        int y = COLUMN_Y;
        for (Category category : Category.values()) {
            if (contains(mouseX, mouseY, DROPDOWN_X, y,
                DROPDOWN_WIDTH, DROPDOWN_HEADER_HEIGHT)) {
                return category;
            }
            y += DROPDOWN_HEADER_HEIGHT;
            if (category == expandedCategory) {
                y += dropdownVisibleRows() * rowHeight();
            }
        }
        return null;
    }

    private int rowHeight() {
        return 24;
    }

    private int visibleRows() {
        return Math.max(1, (COLUMN_BOTTOM - MODULE_TOP - 8) / rowHeight());
    }

    private int visibleOptionRows() {
        return Math.max(1, (COLUMN_BOTTOM - INLINE_OPTIONS_TOP - 8) / OPTION_ROW_HEIGHT);
    }

    private String currentDensity() {
        return KhanhFeatures.choice("visuals", "gui_layout", "Columns");
    }

    private void resetColumnScroll() {
        columnBrowser.resetScroll();
        for (Category category : Category.values()) {
            columnScroll.put(category, 0);
        }
    }

    private void updateSlider(ModuleOption option, double mouseX) {
        int sliderX = compactDropdownLayout() ? INSPECTOR_X + 28 : activePanelX(selectedModule.category()) + 14;
        int sliderWidth = compactDropdownLayout() ? INSPECTOR_WIDTH - 56 : COLUMN_WIDTH - 28;
        option.setFromRatio((mouseX - sliderX) / sliderWidth);
    }

    private void updateColorFromWheel(double mouseX, double mouseY) {
        if (draggingColorOption == null) {
            return;
        }
        double cx = colorPickerX() + COLOR_PICKER_WIDTH / 2.0;
        double cy = colorPickerY() + 72.0;
        double dx = mouseX - cx;
        double dy = mouseY - cy;
        double distance = Math.sqrt(dx * dx + dy * dy);
        if (distance > COLOR_WHEEL_RADIUS) {
            double scale = COLOR_WHEEL_RADIUS / distance;
            dx *= scale;
            dy *= scale;
            distance = COLOR_WHEEL_RADIUS;
        }
        float hue = (float) (Math.atan2(dy, dx) / (Math.PI * 2.0));
        if (hue < 0.0F) {
            hue += 1.0F;
        }
        float saturation = (float) Math.min(1.0, distance / COLOR_WHEEL_RADIUS);
        float brightness = colorBrightness(draggingColorOption.colorValue());
        if (brightness < 0.01F) brightness = 1.0F;
        draggingColorOption.setColor(hsvColor(hue, saturation, brightness));
    }

    private void updateColorBrightness(double mouseX) {
        if (draggingColorOption == null) return;
        int sliderX = colorPickerX() + 17;
        int sliderWidth = COLOR_PICKER_WIDTH - 34;
        float value = (float) Math.max(0.0, Math.min(1.0,
            (mouseX - sliderX) / Math.max(1.0, sliderWidth - 1.0)));
        int color = draggingColorOption.colorValue();
        draggingColorOption.setColor(hsvColor(colorHue(color), colorSaturation(color), value));
    }

    private int colorPickerX() {
        return selectedModule == null ? COLUMN_X : activePanelX(selectedModule.category()) + 3;
    }

    private int colorPickerY() {
        return COLUMN_BOTTOM - COLOR_PICKER_HEIGHT - 4;
    }

    private static int hsvColor(float hue, float saturation) {
        return hsvColor(hue, saturation, 1.0F);
    }

    private static int hsvColor(float hue, float saturation, float brightness) {
        float h = (hue - (float) Math.floor(hue)) * 6.0F;
        int sector = (int) Math.floor(h);
        float fraction = h - sector;
        float p = brightness * (1.0F - saturation);
        float q = brightness * (1.0F - saturation * fraction);
        float t = brightness * (1.0F - saturation * (1.0F - fraction));
        float red;
        float green;
        float blue;
        switch (sector) {
            case 0 -> { red = brightness; green = t; blue = p; }
            case 1 -> { red = q; green = brightness; blue = p; }
            case 2 -> { red = p; green = brightness; blue = t; }
            case 3 -> { red = p; green = q; blue = brightness; }
            case 4 -> { red = t; green = p; blue = brightness; }
            default -> { red = brightness; green = p; blue = q; }
        }
        return 0xFF000000
            | Math.round(red * 255.0F) << 16
            | Math.round(green * 255.0F) << 8
            | Math.round(blue * 255.0F);
    }

    private static float colorSaturation(int color) {
        float red = ((color >> 16) & 0xFF) / 255.0F;
        float green = ((color >> 8) & 0xFF) / 255.0F;
        float blue = (color & 0xFF) / 255.0F;
        float maximum = Math.max(red, Math.max(green, blue));
        float minimum = Math.min(red, Math.min(green, blue));
        return maximum <= 0.0F ? 0.0F : (maximum - minimum) / maximum;
    }

    private static float colorBrightness(int color) {
        float red = ((color >> 16) & 0xFF) / 255.0F;
        float green = ((color >> 8) & 0xFF) / 255.0F;
        float blue = (color & 0xFF) / 255.0F;
        return Math.max(red, Math.max(green, blue));
    }

    private static float colorHue(int color) {
        float red = ((color >> 16) & 0xFF) / 255.0F;
        float green = ((color >> 8) & 0xFF) / 255.0F;
        float blue = (color & 0xFF) / 255.0F;
        float maximum = Math.max(red, Math.max(green, blue));
        float minimum = Math.min(red, Math.min(green, blue));
        float difference = maximum - minimum;
        if (difference <= 0.0001F) {
            return 0.0F;
        }
        float hue;
        if (maximum == red) {
            hue = ((green - blue) / difference) % 6.0F;
        } else if (maximum == green) {
            hue = (blue - red) / difference + 2.0F;
        } else {
            hue = (red - green) / difference + 4.0F;
        }
        hue /= 6.0F;
        return hue < 0.0F ? hue + 1.0F : hue;
    }

    private float uiScale() {
        float requested = (float) KhanhFeatures.decimal("visuals", "gui_zoom", 0.55);
        float fitted = Math.min((float) width / VIRTUAL_WIDTH, (float) height / VIRTUAL_HEIGHT);
        return Math.max(0.32F, Math.min(requested, Math.min(1.0F, fitted)));
    }

    private double toUiX(double mouseX) {
        return (mouseX - width / 2.0) / uiScale() + VIRTUAL_WIDTH / 2.0;
    }

    private double toUiY(double mouseY) {
        return (mouseY - height / 2.0) / uiScale() + VIRTUAL_HEIGHT / 2.0;
    }

    private int pinkText() {
        if (KhanhFeatures.choice("visuals", "gui_theme", "Khanh Client Black").equals("Khanh Client Black")) {
            return 0xFFF1F4F8;
        }
        return VisualTheme.guiSoftAccent();
    }

    private int pinkMuted() {
        if (KhanhFeatures.choice("visuals", "gui_theme", "Khanh Client Black").equals("Khanh Client Black")) {
            return 0xFF8693A5;
        }
        return VisualTheme.withAlpha(GuiState.interfaceAccent(), 162);
    }

    private float rowMotion(String id, boolean hovered) {
        float current = rowMotion.getOrDefault(id, hovered ? 1.0F : 0.0F);
        float target = hovered ? 1.0F : 0.0F;
        float response = KhanhFeatures.enabled("animations") ? 0.22F : 1.0F;
        current += (target - current) * response;
        if (Math.abs(target - current) < 0.004F) current = target;
        rowMotion.put(id, current);
        return current;
    }

    private void playSound(float pitchMultiplier) {
        playUiSound(client, pitchMultiplier);
    }

    static void playUiSound(MinecraftClient client, float pitchMultiplier) {
        KhanhSounds.playInterface(client, pitchMultiplier);
    }

    static long animationMillis(int baseMillis) {
        if (!KhanhFeatures.enabled("animations")) {
            return 1L;
        }
        double speed = KhanhFeatures.decimal("animations", "speed", 0.22);
        return Math.round(Math.max(65.0, Math.min(420.0,
            baseMillis * 0.22 / Math.max(0.01, speed))));
    }

    private static String fontChoice = "";

    /** Text in Khanh's font (the Fonts module picks it: Russo One, or Minecraft's own). */
    public static Text fontText(String value) {
        String choice = dev.khanh.feature.KhanhFeatures.enabled("fonts")
            ? dev.khanh.feature.KhanhFeatures.choice("fonts", "font", "Xuong") : "Xuong";
        if (!choice.equals(fontChoice)) {
            fontChoice = choice;
            FONT_TEXT_CACHE.clear();
        }
        Text cached = FONT_TEXT_CACHE.get(value); if (cached != null) return cached;
        Identifier font = switch (choice) {
            case "Minecraft" -> Identifier.ofVanilla("default");
            case "Minecraft Uniform" -> Identifier.ofVanilla("uniform");
            case "Russo One" -> RUSSO_ONE;
            case "Inter" -> Identifier.of(KhanhClient.MOD_ID, "inter");
            default -> Identifier.of(KhanhClient.MOD_ID, "xuong");
        };
        Text result = Text.literal(value).setStyle(Style.EMPTY.withFont(new StyleSpriteSource.Font(font)));
        FONT_TEXT_CACHE.put(value, result); return result;
    }

    public static TextRenderer textRenderer() {
        return MinecraftClient.getInstance().textRenderer;
    }

    public static String keyName(int key) {
        if (key < 0) {
            return "UNBOUND";
        }
        return switch (key) {
            case GLFW.GLFW_KEY_SPACE -> "SPACE";
            case GLFW.GLFW_KEY_LEFT_SHIFT -> "L-SHIFT";
            case GLFW.GLFW_KEY_LEFT_CONTROL -> "L-CTRL";
            case GLFW.GLFW_KEY_LEFT_ALT -> "L-ALT";
            case GLFW.GLFW_KEY_TAB -> "TAB";
            case GLFW.GLFW_KEY_ENTER -> "ENTER";
            case GLFW.GLFW_KEY_BACKSPACE -> "BACKSPACE";
            case GLFW.GLFW_KEY_DELETE -> "DELETE";
            default -> {
                if (key >= GLFW.GLFW_KEY_F1 && key <= GLFW.GLFW_KEY_F25) {
                    yield "F" + (key - GLFW.GLFW_KEY_F1 + 1);
                }
                String name = GLFW.glfwGetKeyName(key, 0);
                yield name == null ? "KEY " + key : name.toUpperCase(Locale.ROOT);
            }
        };
    }

    public static void liquidGlow(DrawContext context, int x, int y, int width, int height,
                                  int radius, int color) {
        if (!KhanhFeatures.bool("visuals", "glow", false)) {
            return;
        }
        roundedRect(context, x - 2, y - 2, width + 4, height + 4, radius + 2,
            VisualTheme.withAlpha(color, Math.min(30, (color >>> 24) & 0xFF)));
    }

    private static void glassPanel(DrawContext context, int x, int y, int width, int height,
                                   int radius, int color) {
        if (KhanhFeatures.choice("visuals", "gui_theme", "Khanh Client Black").equals("Khanh Client Black")) {
            int compactRadius = Math.max(3, Math.min(6, Math.min(width, height) / 2));
            roundedRect(context, x + 2, y + 3, width, height, compactRadius, 0x85000000);
            roundedRect(context, x, y, width, height, compactRadius, 0xF2020407);
            roundedOutline(context, x, y, width, height, compactRadius,
                VisualTheme.withAlpha(GuiState.interfaceAccent(), 55));
            roundedRect(context, x + 7, y + 2, Math.max(1, width - 14), 1, 1,
                VisualTheme.withAlpha(GuiState.interfaceAccent(), 44));
            return;
        }
        int cleanRadius = Math.max(8, Math.min(radius, Math.min(width, height) / 2));
        int themedColor = VisualTheme.guiGlass(color);
        roundedRect(context, x + 3, y + 5, width, height, cleanRadius, GLASS_SHADOW);
        roundedRect(context, x - 1, y - 1, width + 2, height + 2, cleanRadius + 1,
            VisualTheme.withAlpha(GuiState.interfaceAccent(), 18));
        if (!GlassGuiRenderer.liquidSurface(context, x, y, width, height, cleanRadius, themedColor)) {
            roundedRect(context, x, y, width, height, cleanRadius, themedColor);
        }
        roundedOutline(context, x, y, width, height, cleanRadius,
            VisualTheme.withAlpha(GuiState.interfaceAccent(), 72));
        roundedOutline(context, x + 1, y + 1, width - 2, height - 2,
            Math.max(1, cleanRadius - 1), 0x24FFFFFF);
        roundedRect(context, x + cleanRadius, y + 2, width - cleanRadius * 2, 1, 1,
            VisualTheme.withAlpha(VisualTheme.guiSoftAccent(), 62));
    }

    public static void roundedOutline(DrawContext context, int x, int y, int width, int height,
                                      int radius, int color) {
        SmoothGuiShapes.rect(context, x, y, width, height, radius, color, true);
    }

    private static void rasterRoundedOutline(DrawContext context, int x, int y, int width, int height,
                                      int radius, int color) {
        if (width <= 0 || height <= 0) {
            return;
        }
        int safeRadius = Math.max(0, Math.min(radius, Math.min(width, height) / 2));
        int thickness = Math.min(2, Math.max(1, Math.min(width, height) / 2));
        if (safeRadius == 0) {
            context.fill(x, y, x + width, y + thickness, color);
            context.fill(x, y + height - thickness, x + width, y + height, color);
            context.fill(x, y + thickness, x + thickness, y + height - thickness, color);
            context.fill(x + width - thickness, y + thickness, x + width, y + height - thickness, color);
            return;
        }
        int edgeRows = Math.min((height + 1) / 2, Math.max(safeRadius, thickness));
        context.fill(x, y + edgeRows, x + thickness, y + height - edgeRows, color);
        context.fill(x + width - thickness, y + edgeRows, x + width, y + height - edgeRows, color);
        for (int row = 0; row < edgeRows; row++) {
            double outer = roundedInsetExact(row, height, safeRadius);
            int top = y + row;
            int bottom = y + height - row - 1;
            if (row < thickness) {
                smoothSpan(context, top, x + outer, x + width - outer, color);
                if (bottom != top) smoothSpan(context, bottom, x + outer, x + width - outer, color);
            } else {
                double inner = thickness + roundedInsetExact(row - thickness,
                    height - thickness * 2, Math.max(0, safeRadius - thickness));
                smoothSpan(context, top, x + outer, x + inner, color);
                smoothSpan(context, top, x + width - inner, x + width - outer, color);
                if (bottom != top) {
                    smoothSpan(context, bottom, x + outer, x + inner, color);
                    smoothSpan(context, bottom, x + width - inner, x + width - outer, color);
                }
            }
        }
    }

    private static void smoothSpan(DrawContext context, int y, double left, double right, int color) {
        if (right <= left) return;
        int first = (int) Math.floor(left);
        int last = (int) Math.ceil(right) - 1;
        if (first == last) {
            context.fill(first, y, first + 1, y + 1, scaledAlpha(color, (float) (right - left)));
            return;
        }
        context.fill(first + 1, y, last, y + 1, color);
        context.fill(first, y, first + 1, y + 1, scaledAlpha(color, (float) (first + 1 - left)));
        context.fill(last, y, last + 1, y + 1, scaledAlpha(color, (float) (right - last)));
    }

    public static void roundedRect(DrawContext context, int x, int y, int width, int height,
                                   int radius, int color) {
        SmoothGuiShapes.rect(context, x, y, width, height, radius, color, false);
    }

    private static void rasterRoundedRect(DrawContext context, int x, int y, int width, int height,
                                   int radius, int color) {
        if (width <= 0 || height <= 0) {
            return;
        }
        int safeRadius = Math.max(0, Math.min(radius, Math.min(width, height) / 2));
        if (safeRadius == 0) {
            context.fill(x, y, x + width, y + height, color);
            return;
        }
        if (height <= safeRadius * 4 + 3) {
            for (int row = 0; row < height; row++) {
                double exactInset = roundedInsetExact(row, height, safeRadius);
                int inset = (int) Math.ceil(exactInset);
                context.fill(x + inset, y + row, x + width - inset, y + row + 1, color);
                float coverage = (float) Math.max(0.0, Math.min(1.0, inset - exactInset));
                if (inset > 0 && coverage > 0.02F) {
                    int fringe = scaledAlpha(color, coverage);
                    context.fill(x + inset - 1, y + row, x + inset, y + row + 1, fringe);
                    context.fill(x + width - inset, y + row, x + width - inset + 1, y + row + 1, fringe);
                }
            }
            return;
        }
        context.fill(x + safeRadius, y, x + width - safeRadius, y + height, color);
        context.fill(x, y + safeRadius, x + safeRadius, y + height - safeRadius, color);
        context.fill(x + width - safeRadius, y + safeRadius, x + width, y + height - safeRadius, color);
        for (int row = 0; row < safeRadius; row++) {
            double exactInset = roundedInsetExact(row, height, safeRadius);
            int inset = (int) Math.ceil(exactInset);
            context.fill(x + inset, y + row, x + safeRadius, y + row + 1, color);
            context.fill(x + width - safeRadius, y + row, x + width - inset, y + row + 1, color);
            context.fill(x + inset, y + height - row - 1, x + safeRadius, y + height - row, color);
            context.fill(x + width - safeRadius, y + height - row - 1, x + width - inset, y + height - row, color);
            float coverage = (float) Math.max(0.0, Math.min(1.0, inset - exactInset));
            if (inset > 0 && coverage > 0.02F) {
                int fringe = scaledAlpha(color, coverage);
                context.fill(x + inset - 1, y + row, x + inset, y + row + 1, fringe);
                context.fill(x + width - inset, y + row, x + width - inset + 1, y + row + 1, fringe);
                context.fill(x + inset - 1, y + height - row - 1, x + inset, y + height - row, fringe);
                context.fill(x + width - inset, y + height - row - 1,
                    x + width - inset + 1, y + height - row, fringe);
            }
        }
    }

    public static void drawCircle(DrawContext context, int cx, int cy, int radius, int color) {
        SmoothGuiShapes.circle(context, cx, cy, radius, color);
    }

    private static void rasterDrawCircle(DrawContext context, int cx, int cy, int radius, int color) {
        if (radius <= 0) {
            context.fill(cx, cy, cx + 1, cy + 1, color);
            return;
        }
        for (int py = -radius; py < radius; py++) {
            double dy = py + 0.5;
            double extent = Math.sqrt(Math.max(0.0, (double) radius * radius - dy * dy));
            int left = (int) Math.ceil(-extent);
            int right = (int) Math.floor(extent);
            context.fill(cx + left, cy + py, cx + right, cy + py + 1, color);
            float coverage = (float) (extent - Math.floor(extent));
            if (coverage > 0.02F) {
                int fringe = scaledAlpha(color, coverage);
                context.fill(cx + left - 1, cy + py, cx + left, cy + py + 1, fringe);
                context.fill(cx + right, cy + py, cx + right + 1, cy + py + 1, fringe);
            }
        }
    }

    private static int roundedInset(int row, int height, int radius) {
        return (int) Math.ceil(roundedInsetExact(row, height, radius));
    }

    private static double roundedInsetExact(int row, int height, int radius) {
        if (radius <= 0 || (row >= radius && row < height - radius)) {
            return 0.0;
        }
        double centerY = row < radius ? radius : height - radius;
        double dy = Math.abs(row + 0.5 - centerY);
        double extent = Math.sqrt(Math.max(0.0, (double) radius * radius - dy * dy));
        return Math.max(0.0, Math.min(radius, radius - extent));
    }

    private static int scaledAlpha(int color, float coverage) {
        int alpha = (color >>> 24) & 0xFF;
        return (Math.max(0, Math.min(255, Math.round(alpha * coverage))) << 24) | (color & 0x00FFFFFF);
    }

    private static boolean contains(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }
}
