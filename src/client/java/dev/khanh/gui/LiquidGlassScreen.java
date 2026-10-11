package dev.khanh.gui;

import dev.khanh.KhanhClient;
import dev.khanh.config.ConfigManager;
import dev.khanh.feature.PrivacyService;
import dev.khanh.feature.VisualTheme;
import dev.khanh.feature.KhanhFeatures;
import dev.khanh.feature.KhanhSounds;
import dev.khanh.feature.KhanhHudRenderer;
import dev.khanh.module.Category;
import dev.khanh.module.Module;
import dev.khanh.module.ModuleOption;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.IntConsumer;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.gui.widget.PlayerSkinWidget;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.joml.Matrix3x2fStack;
import org.lwjgl.glfw.GLFW;

/** Khanh Client's glass console in three panes: a sidebar with search, favourites and
 * categories; the category's modules as a list; and the selected module's details and
 * settings. A Configs page replaces the two right-hand panes. All motion is springs;
 * with Animations off everything settles instantly. */
public final class LiquidGlassScreen extends Screen implements KhanhClickGui {
    private enum View {
        ALL("All"), ACTIVE("Active"), BOUND("Bound");
        final String label;
        View(String label) { this.label = label; }
    }

    private enum Page { MODULES, SETTINGS, THEMES, COSMETICS, CONFIGS }

    private record Hit(String id, float x, float y, float w, float h, IntConsumer action) {
        boolean contains(double px, double py) { return px >= x && px < x + w && py >= y && py < y + h; }
    }

    private record MenuItem(String label, String meta, boolean selected, Runnable pick) { }

    private record Slot(boolean saved, String name) { }

    private final class Menu {
        final String id;
        final String heading;
        final List<MenuItem> items;
        final float anchorX, anchorY, anchorW, anchorH, width;
        final Spring progress = new Spring(Spring.PANEL, 0.0F).to(1.0F);
        final List<Spring> rows = new ArrayList<>();
        final long opened = System.nanoTime();
        boolean closing;
        int scroll;

        Menu(String id, String heading, List<MenuItem> items, float x, float y, float w, float h, float width) {
            this.id = id;
            this.heading = heading;
            this.items = items;
            anchorX = x; anchorY = y; anchorW = w; anchorH = h;
            this.width = Math.max(w, width);
            for (int i = 0; i < items.size(); i++) rows.add(new Spring(Spring.PANEL, 0.0F));
            int selectedIndex = 0;
            for (int i = 0; i < items.size(); i++) if (items.get(i).selected()) selectedIndex = i;
            scroll = Math.max(0, Math.min(selectedIndex - visibleRows() / 2, items.size() - visibleRows()));
        }

        boolean hasMeta() {
            for (MenuItem item : items) if (item.meta() != null) return true;
            return false;
        }
        int rowHeight() { return hasMeta() ? 29 : 22; }
        float left() {
            float x = anchorX + anchorW > VW - 10 - width ? anchorX + anchorW - width : anchorX;
            return Math.max(10, Math.min(x, VW - 10 - width));
        }
        float top() { return anchorY + anchorH + 6; }
        int visibleRows() {
            int room = (int) ((VH - 8 - (anchorY + anchorH + 6) - 30) / rowHeight());
            return Math.min(items.size(), Math.max(3, room));
        }
        float height() { return 28 + visibleRows() * rowHeight(); }
        boolean contains(double x, double y) {
            return x >= left() && x < left() + width && y >= top() && y < top() + height();
        }
        void close() {
            closing = true;
            progress.to(0.0F);
            for (Spring row : rows) row.to(0.0F);
        }
    }

    private static final Identifier ZERO_LOGO = Identifier.of(KhanhClient.MOD_ID, "textures/gui/khanh-logo.png");
    private static final int VW = 780, VH = 500, WIN_R = 18;
    /** Live status strip across the top of the content columns. */
    private static final int BAR_Y = 10, BAR_H = 26, TOP = BAR_Y + BAR_H + 8;
    private static final int SX = 10, SY = 10, SW = 178, SH = VH - 20;
    private static final int LX = SX + SW + 12, LY = TOP, LW = 262;
    private static final int DX = LX + LW + 10, DY = TOP, DW = VW - DX - 10, DH = VH - TOP - 10;
    private static final int CX = LX, CW = VW - LX - 10;
    private static final int LIST_TOP = LY + 56, LIST_BOTTOM = VH - 10, ROW = 40;
    private static final int DETAIL_TOP = DY + 112, DETAIL_BOTTOM = DY + DH - 8;
    private static final int SEARCH_Y = SY + 42;
    private static final float[] WINDOW = {420.0F, 24.0F, 0.8F};
    private static final int CAT_ROW = 23, FAV_Y = SEARCH_Y + 32, RECENT_Y = FAV_Y + CAT_ROW, CAT_TOP = RECENT_Y + CAT_ROW + 19;
    private static final int OPTION_H = 34;
    private static final int ACCENT_CARD_H = 58;
    private static final int CROSSHAIR_CARD_H = 78;
    /** Zero blue (0), violet, cyan, mint, amber, rose, pink and white. */
    private static final int[] ACCENTS = {0, 0xA78BFA, 0x38BDF8, 0x34D399, 0xFBBF24, 0xFB7185, 0xF472B6, 0x8A8F98};
    private static final Map<Integer, Integer> ACCENT_INDEX = new HashMap<>();
    static {
        for (int i = 0; i < ACCENTS.length; i++) ACCENT_INDEX.put(ACCENTS[i], i);
    }
    private static final int PROFILE_SLOTS = 5;
    private static final int[] SLOT_HUES = {0xFFF2F2F6, 0xFFC9CCD2, 0xFFA2A6AE, 0xFF7E828A, 0xFF5C6068};
    private static final int MONO = 0xFFF2F2F6;
    private static final int FG = 0xFFF2F4F8, SOFT = 0xD9F2F4F8, DIM = 0x94F2F4F8, FAINT = 0x57F2F4F8;
    private static final long STAGGER_NANOS = 22_000_000L;
    private static Category lastCategory = Category.COMBAT;
    private static View lastView = View.ALL;

    private final Screen parent;
    private Page page = Page.MODULES;
    private Category category;
    private boolean favourites;
    private boolean recentView;
    private View view;
    private String search = "";
    private boolean searchFocused;
    private Module selected;
    private Module shownModule;
    private Module bindingModule;
    /** The letter of a key just used for a keybind still arrives as typed text: drop it. */
    private boolean swallowChar;
    private ModuleOption bindingAction;
    private ModuleOption editing;
    private ModuleOption sliding;
    private ModuleOption colorOpen;
    private ModuleOption colorSliding;
    private int colorChannel;
    private float dragLeft, dragWidth;
    private boolean dirty;

    private final Slot[] slots = new Slot[PROFILE_SLOTS];
    private String field;
    private String profileName = "";
    private String importCode = "";
    private String status = "";
    private int statusColor = FAINT;
    private long statusAt;
    private int confirmDelete = -1;
    private Module confirmReset;
    private long confirmResetAt;
    private long confirmAt;

    private final Spring windowIn = new Spring(WINDOW, 0.0F);
    private PlayerSkinWidget skin;
    /** Live preview on the Cosmetics page, wearing your custom cape when it is on. */
    private PlayerSkinWidget cosmeticSkin;
    private final Map<Page, Spring> pageIn = new EnumMap<>(Page.class);
    private final Spring sidebarPill = new Spring(Spring.PANEL, 0.0F);
    private final Spring detailIn = new Spring(Spring.PANEL, 1.0F);
    private final Spring listScroll = new Spring(Spring.SMOOTH, 0.0F);
    private final Spring detailScroll = new Spring(Spring.SMOOTH, 0.0F);
    private float listTarget, detailTarget, listMax, detailMax, settingsTarget, settingsMax;
    private final Spring settingsScroll = new Spring(Spring.SMOOTH, 0.0F);
    private final Map<String, Spring> rowIn = new HashMap<>();
    private String listKey = "";
    private long listEpoch;
    private long openedAt;
    private boolean initialised, closing, saved;

    private final Map<String, Spring> springs = new HashMap<>();
    private final Map<String, Boolean> toggleStates = new HashMap<>();
    private final Map<ModuleOption, List<String>> choiceCache = new IdentityHashMap<>();
    private final List<Hit> hits = new ArrayList<>();
    private final List<Hit> menuHits = new ArrayList<>();
    private Menu menu;
    private Menu closingMenu;
    private String pressedId;
    private double mx, my;
    private float opacity = 1.0F;
    private float clipTop = Float.NEGATIVE_INFINITY, clipBottom = Float.POSITIVE_INFINITY;
    private boolean drawingMenu;
    private boolean inert;

    public LiquidGlassScreen(Screen parent) {
        this(parent, null);
    }

    public LiquidGlassScreen(Screen parent, Module initialModule) {
        super(Text.literal("Khanh Client"));
        this.parent = parent;
        // Development screenshots only: KHANH_DEVSHOTS_PAGE=themes opens straight onto that page.
        String devPage = System.getenv("KHANH_DEVSHOTS_PAGE");
        if ("themes".equals(devPage)) this.page = Page.THEMES;
        if ("cosmetics".equals(devPage)) this.page = Page.COSMETICS;
        if (devPage != null && devPage.startsWith("module:")) initialModule = KhanhClient.modules().byId(devPage.substring(7));
        this.category = initialModule != null ? initialModule.category() : lastCategory;
        this.view = lastView;
        this.selected = initialModule;
        this.shownModule = initialModule;
        for (Page option : Page.values()) pageIn.put(option, new Spring(Spring.PANEL, option == page ? 1.0F : 0.0F));
        sidebarPill.snap(pillTarget());
    }

    // ---------------------------------------------------------------- lifecycle

    @Override
    protected void init() {
        skin = null;
        if (client != null) {
            // Your skin stands in the top-right corner of the screen, outside the window.
            float scale = uiScale();
            int windowRight = Math.round(width / 2.0F + VW / 2.0F * scale);
            int room = width - windowRight - 16;
            int skinW = Math.min(84, room - 16);
            try {
                if (skinW >= 40) {
                    int skinH = Math.min(Math.round(skinW * 1.75F), height / 2);
                    var own = client.getSkinProvider().supplySkinTextures(client.getGameProfile(), true);
                    skin = new PlayerSkinWidget(skinW, skinH, client.getLoadedEntityModels(), () -> withFunSkin(own.get()));
                    // Below the potion icons Minecraft keeps drawing in the top-right corner.
                    skin.setPosition(width - 16 - skinW, client.player != null && !client.player.getStatusEffects().isEmpty() ? 64 : 18);
                    addDrawableChild(skin);
                }
                var textures = client.getSkinProvider().supplySkinTextures(client.getGameProfile(), true);
                cosmeticSkin = new PlayerSkinWidget(100, 175, client.getLoadedEntityModels(), () -> {
                    var base = withFunSkin(textures.get());
                    Identifier cape = dev.khanh.feature.CustomCapeService.texture();
                    return cape == null ? base : new net.minecraft.entity.player.SkinTextures(base.body(),
                        new net.minecraft.util.AssetInfo.TextureAssetInfo(cape, cape), base.elytra(), base.model(), base.secure());
                });
                cosmeticSkin.visible = false;
                addDrawableChild(cosmeticSkin);
            } catch (IllegalArgumentException notLoadedYet) {
                // Player model not loaded yet (game still starting): open without the skin previews.
                skin = null;
                cosmeticSkin = null;
            }
        }
        if (!initialised) {
            initialised = true;
            openedAt = System.nanoTime();
            listEpoch = openedAt;
            sound(1.22F);
        }
    }

    @Override
    public void removed() {
        KhanhFeatures.previewModule = null;
        super.removed();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        // The game stays visible under a soft dark wash that fades with the window, for focus.
        float wash = clamp01(windowIn.value());
        context.fillGradient(0, 0, width, height, LiquidGlass.alpha(0.28F * wash) << 24, LiquidGlass.alpha(0.46F * wash) << 24);
    }

    @Override
    public void close() {
        KhanhFeatures.previewModule = null;
        if (closing) return;
        if (!saved) {
            saved = true;
            save();
            sound(0.76F);
        }
        closeMenu();
        closing = true;
        windowIn.to(0.0F);
        if (!KhanhFeatures.enabled("animations")) finishClose();
    }

    private void finishClose() {
        if (client != null && client.currentScreen == this) client.setScreen(parent);
    }

    // ---------------------------------------------------------------- render

    @Override
    public void render(DrawContext c, int mouseX, int mouseY, float delta) {
        GlassGuiRenderer.begin(KhanhFeatures.choice("visuals", "gui_finish", "Glass").equals("Glass"), true);
        float scale = uiScale();
        mx = toVirtualX(mouseX);
        my = toVirtualY(mouseY);
        hits.clear();
        menuHits.clear();
        if (!closing) windowIn.to(1.0F);

        List<Module> modules = modules();
        if (page == Page.MODULES && (selected == null || !modules.contains(selected)) && !modules.isEmpty()) {
            showModule(modules.get(0), false);
        }

        Matrix3x2fStack matrices = c.getMatrices();
        matrices.pushMatrix();
        matrices.translate(width / 2.0F, height / 2.0F);
        matrices.scale(scale, scale);
        matrices.translate(-VW / 2.0F, -VH / 2.0F);

        float p = windowIn.value();
        if (p > 0.003F || windowIn.target() > 0.0F) {
            opacity = clamp01(p);
            matrices.pushMatrix();
            matrices.translate(0.0F, (1.0F - p) * 22.0F);
            matrices.translate(VW / 2.0F, VH / 2.0F);
            // Pops in from slightly smaller and lower, overshooting a touch before it settles.
            float s = 0.9F + 0.1F * p;
            matrices.scale(s, s);
            matrices.translate(-VW / 2.0F, -VH / 2.0F);
            LiquidGlass.card(c, 0, 0, VW, VH, WIN_R, opacity);
            drawSidebar(c);
            drawStatusBar(c);
            drawPages(c, modules);
            drawWhatsNew(c);
            matrices.popMatrix();
            opacity = 1.0F;
        }

        matrices.popMatrix();
        if (skin != null) {
            float shown = clamp01((windowIn.value() - 0.85F) / 0.15F);
            skin.visible = !closing && shown > 0.5F;
            if (shown > 0.01F) {
                int sx = skin.getX() - 8, sy = skin.getY() - 8, sw = skin.getWidth() + 16, sh = skin.getHeight() + 16;
                LiquidGlass.shadow(c, sx, sy, sw, sh, 14, shown);
                LiquidGlass.frost(c, sx, sy, sw, sh, 14, shown);
                LiquidGlass.glass(c, sx, sy, sw, sh, 14, 0xFFFFFF, shown);
            }
        }
        positionCosmeticSkin();
        if ((skin != null && skin.visible) || (cosmeticSkin != null && cosmeticSkin.visible)) {
            c.createNewRootLayer();
            super.render(c, mouseX, mouseY, delta);
        }
        drawLivePlayer(c);
        matrices.pushMatrix();
        matrices.translate(width / 2.0F, height / 2.0F);
        matrices.scale(scale, scale);
        matrices.translate(-VW / 2.0F, -VH / 2.0F);

        if (menu != null || closingMenu != null) {
            c.createNewRootLayer();
            drawingMenu = true;
            if (closingMenu != null && !drawMenu(c, closingMenu)) closingMenu = null;
            if (menu != null) drawMenu(c, menu);
            drawingMenu = false;
        }
        matrices.popMatrix();

        if (closing && (windowIn.value() < 0.2F || windowIn.settled())) finishClose();
        GlassGuiRenderer.end();
    }

    /** Who, where and how the game is running: name, server, ping, FPS, position, clock, active modules. */
    private void drawStatusBar(DrawContext c) {
        int x = LX, y = BAR_Y, w = VW - LX - 10;
        LiquidGlass.glass(c, x, y, w, BAR_H, BAR_H / 2, 0xFFFFFF, opacity * 0.9F);
        String name = client != null && client.getSession() != null ? client.getSession().getUsername() : "Player";
        float cursor = x + 12;
        HudSurfaceRenderer.icon(c, HudSurfaceRenderer.HudIcon.PLAYER, Math.round(cursor), y + 8, 10, LiquidGlass.alpha(opacity) << 24 | 0xFFFFFF);
        cursor += 15;
        text(c, trim(name, 110, 0.74F), cursor, y + 9, FG, 0.74F);
        cursor += Math.min(110, width(name, 0.74F)) + 12;

        long active = KhanhClient.modules().all().stream().filter(Module::enabled).count();
        String[][] stats = {
            {"world", serverLabel()},
            {"speed", pingLabel()},
            {"radar", (client == null ? 0 : client.getCurrentFps()) + " FPS"},
            {"compass", positionLabel()},
            {"modules", active + " active"},
            {"clock", java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))},
        };
        float right = x + w - 10;
        // Lay out right-to-left so the clock pins to the edge and long server names trim first.
        java.util.List<float[]> spans = new java.util.ArrayList<>();
        float edge = right;
        for (int i = stats.length - 1; i >= 1; i--) {
            float cw = width(stats[i][1], 0.62F) + 26;
            edge -= cw;
            spans.add(0, new float[] {edge, cw});
            edge -= 6;
        }
        float serverW = Math.max(40, edge - cursor);
        spans.add(0, new float[] {cursor, serverW});
        for (int i = 0; i < stats.length; i++) {
            float sx = spans.get(i)[0], sw = spans.get(i)[1];
            String value = i == 0 ? trim(stats[i][1], sw - 26, 0.62F) : stats[i][1];
            SmoothGuiShapes.rect(c, Math.round(sx), y + 5, Math.round(sw), BAR_H - 10, (BAR_H - 10) / 2,
                LiquidGlass.alpha(0.06F * opacity) << 24 | 0xFFFFFF, false);
            HudSurfaceRenderer.icon(c, statIcon(stats[i][0]), Math.round(sx) + 7, y + 9, 8, LiquidGlass.alpha(0.75F * opacity) << 24 | 0xFFFFFF);
            text(c, value, sx + 19, y + 10, i == 2 ? fpsTint() : SOFT, 0.62F);
        }
    }

    private static HudSurfaceRenderer.HudIcon statIcon(String id) {
        return switch (id) {
            case "world" -> HudSurfaceRenderer.HudIcon.WORLD;
            case "speed" -> HudSurfaceRenderer.HudIcon.SPEED;
            case "radar" -> HudSurfaceRenderer.HudIcon.RADAR;
            case "compass" -> HudSurfaceRenderer.HudIcon.COMPASS;
            case "modules" -> HudSurfaceRenderer.HudIcon.MODULES;
            default -> HudSurfaceRenderer.HudIcon.CLOCK;
        };
    }

    private String serverLabel() {
        if (client == null || client.world == null) return "Main menu";
        if (client.isInSingleplayer()) return "Singleplayer";
        return client.getCurrentServerEntry() != null ? client.getCurrentServerEntry().address : "Multiplayer";
    }

    private String pingLabel() {
        if (client == null || client.player == null || client.getNetworkHandler() == null || client.isInSingleplayer()) return "0 ms";
        var entry = client.getNetworkHandler().getPlayerListEntry(client.player.getUuid());
        return (entry == null ? 0 : entry.getLatency()) + " ms";
    }

    private String positionLabel() {
        if (client == null || client.player == null) return "-";
        return client.player.getBlockX() + ", " + client.player.getBlockY() + ", " + client.player.getBlockZ();
    }

    /** FPS keeps meaning: white when smooth, amber when dipping, red when struggling. */
    private int fpsTint() {
        int fps = client == null ? 0 : client.getCurrentFps();
        return fps >= 60 ? SOFT : fps >= 30 ? KhanhPalette.OKAY : KhanhPalette.BAD;
    }

    private void drawPages(DrawContext c, List<Module> modules) {
        float previous = opacity;
        Matrix3x2fStack m = c.getMatrices();
        for (Page option : Page.values()) {
            float v = pageIn.get(option).to(page == option ? 1.0F : 0.0F).value();
            if (v <= 0.005F && page != option) continue;
            opacity = previous * clamp01(v);
            inert = page != option;
            m.pushMatrix();
            m.translate((1.0F - v) * 26.0F * (page == option ? 1.0F : -1.0F), 0.0F);
            if (option == Page.MODULES) {
                drawList(c, modules);
                drawDetail(c);
            } else if (option == Page.SETTINGS) {
                drawSettingsPage(c);
            } else if (option == Page.THEMES) {
                drawThemes(c);
            } else if (option == Page.COSMETICS) {
                drawCosmetics(c);
            } else {
                drawConfigs(c);
            }
            m.popMatrix();
            inert = false;
        }
        opacity = previous;
    }

    // ---------------------------------------------------------------- sidebar

    private float pillTarget() {
        if (page == Page.SETTINGS) return generalRowY();
        if (page == Page.THEMES) return generalRowY() + CAT_ROW;
        if (page == Page.COSMETICS) return generalRowY() + CAT_ROW * 2;
        if (page == Page.CONFIGS) return generalRowY() + CAT_ROW * 3;
        if (recentView) return RECENT_Y;
        if (favourites) return FAV_Y;
        return categoryRowY(category);
    }

    private void drawSidebar(DrawContext c) {
        int x = SX, y = SY;
        LiquidGlass.glass(c, x, y, SW, SH, 14, 0xFFFFFF, opacity);
        LiquidGlass.glass(c, x, y, SW, SH, 14, 0xFFFFFF, opacity * 0.5F);
        int logoAlpha = LiquidGlass.alpha(opacity);
        if (logoAlpha > 4) {
            c.drawTexture(RenderPipelines.GUI_TEXTURED, ZERO_LOGO, x + 12, y + 11, 0.0F, 0.0F,
                22, 22, 256, 256, 256, 256, logoAlpha << 24 | 0xFFFFFF);
        }
        text(c, "Khanh Client", x + 40, y + 17, FG, 0.98F);
        textField(c, "search", search, "Search modules and settings", x + 10, SEARCH_Y, SW - 20, 24, accent(), true);

        boolean searching = !search.isEmpty() && page == Page.MODULES;
        float pillY = sidebarPill.to(pillTarget()).value();
        float pillAlpha = clamp01(spring("sidebar-pill-alpha", Spring.SMOOTH, 1.0F).to(searching ? 0.0F : 1.0F).value());
        if (pillAlpha > 0.01F) {
            float previous = opacity;
            opacity *= pillAlpha;
            int py = (int) Math.floor(pillY);
            c.getMatrices().pushMatrix();
            c.getMatrices().translate(0.0F, pillY - py);
            LiquidGlass.glass(c, x + 8, py, SW - 16, 22, 9, accent(), opacity);
            c.getMatrices().popMatrix();
            opacity = previous;
        }

        sidebarRow(c, "favourites", "Favorites", null, HudSurfaceRenderer.HudIcon.FOCUS, FAV_Y, MONO,
            favourites && page == Page.MODULES && !searching, GuiState.favouriteCount(), () -> {
                favourites = true;
                recentView = false;
                page = Page.MODULES;
                search = "";
                closeMenu();
                sound(1.1F);
            });

        sidebarRow(c, "recent", "Recent", null, HudSurfaceRenderer.HudIcon.CLOCK, RECENT_Y, MONO,
            recentView && page == Page.MODULES && !searching, GuiState.recent().size(), () -> {
                recentView = true;
                favourites = false;
                page = Page.MODULES;
                search = "";
                closeMenu();
                sound(1.1F);
            });

        tracked(c, "CATEGORIES", x + 14, CAT_TOP - 12, FAINT, 0.52F, 1.3F);
        Map<Category, long[]> counts = counts();
        for (Category option : Category.values()) {
            boolean active = option == category && !favourites && !recentView && !searching && page == Page.MODULES;
            sidebarRow(c, "cat:" + option.name(), title(option.displayName()), option, null, Math.round(categoryRowY(option)),
                MONO, active, (int) counts.get(option)[0], () -> setCategory(option));
        }

        int generalY = generalRowY();
        tracked(c, "GENERAL", x + 14, generalY - 12, FAINT, 0.52F, 1.3F);
        sidebarRow(c, "settings", "Settings", null, HudSurfaceRenderer.HudIcon.WAND, generalY, MONO,
            page == Page.SETTINGS, 0, this::openSettings);
        sidebarRow(c, "themes", "Themes", null, HudSurfaceRenderer.HudIcon.FOCUS, generalY + CAT_ROW, MONO,
            page == Page.THEMES, 0, this::openThemes);
        sidebarRow(c, "cosmetics", "Cosmetics", null, HudSurfaceRenderer.HudIcon.PLAYER, generalY + CAT_ROW * 2, MONO,
            page == Page.COSMETICS, 0, this::openCosmetics);
        sidebarRow(c, "configs", "Configs", null, HudSurfaceRenderer.HudIcon.DOCUMENT, generalY + CAT_ROW * 3, MONO,
            page == Page.CONFIGS, 0, this::openConfigs);
        sidebarRow(c, "classic", "Classic layout", null, HudSurfaceRenderer.HudIcon.MODULES, generalY + CAT_ROW * 4, MONO,
            false, 0, () -> {
                if (client != null) KhanhClickGui.switchLayout(client, parent, false);
            });
        drawProfileCard(c, x, generalY + CAT_ROW * 5 + 10);
    }

    /** Fills the bottom of the sidebar: who you are, how many modules are on, how to close. */
    private void drawProfileCard(DrawContext c, int x, int minY) {
        int h = 44, y = SY + SH - h - 8;
        if (y < minY || client == null) return;
        int cx = x + 8, w = SW - 16;
        LiquidGlass.glass(c, cx, y, w, h, 12, 0xFFFFFF, opacity * 0.8F);
        if (opacity > 0.6F) {
            PlayerSkinDrawer.draw(c, client.getSkinProvider().supplySkinTextures(client.getGameProfile(), true).get(), cx + 9, y + 10, 24);
        }
        String name = PrivacyService.protectedName(client.getGameProfile().name());
        text(c, trim(name, w - 52, 0.8F), cx + 42, y + 11, FG, 0.8F);
        long on = KhanhClient.modules().all().stream().filter(Module::enabled).count();
        text(c, on + " on  ·  Right Shift closes", cx + 42, y + 25, FAINT, 0.54F);
    }

    private int generalRowY() {
        return Math.round(categoryRowY(Category.values()[Category.values().length - 1])) + CAT_ROW + 18;
    }

    private float categoryRowY(Category option) {
        return CAT_TOP + option.ordinal() * CAT_ROW;
    }

    private void sidebarRow(DrawContext c, String id, String label, Category category, HudSurfaceRenderer.HudIcon icon,
                            int rowY, int color, boolean active, int count, Runnable action) {
        int x = SX;
        boolean hot = hover(x + 8, rowY, SW - 16, 22);
        float glow = spring("side:" + id, Spring.SMOOTH, 0.0F).to(hot && !active ? 1.0F : 0.0F).value();
        if (glow > 0.01F) SmoothGuiShapes.rect(c, x + 8, rowY, SW - 16, 22, 9,
            LiquidGlass.alpha(0.05F * clamp01(glow) * opacity) << 24 | 0xFFFFFF, false);
        float nudge = spring("side-nudge:" + id, Spring.BOUNCE, 0.0F).to(hot || active ? 3.0F : 0.0F).value();
        int iconAlpha = LiquidGlass.alpha(opacity * (active ? 1.0F : hot ? 0.95F : 0.7F));
        int tint = iconAlpha << 24 | 0xFFFFFF;
        if (iconAlpha > 4) {
            if (category != null) ColumnGlassBrowser.icon(c, category, Math.round(x + 16 + nudge), rowY + 5, 12, tint);
            else HudSurfaceRenderer.icon(c, icon, Math.round(x + 16 + nudge), rowY + 5, 12, tint);
        }
        text(c, label, x + 36 + nudge, rowY + 7, active ? FG : hot ? SOFT : DIM, 0.82F);
        if (count > 0) {
            String badge = Integer.toString(count);
            float bw = Math.max(16, width(badge, 0.58F) + 10);
            LiquidGlass.glass(c, Math.round(x + SW - 16 - bw), rowY + 4, Math.round(bw), 14, 7, 0xFFFFFF, opacity * (active ? 1.0F : 0.7F));
            text(c, badge, x + SW - 16 - bw + (bw - width(badge, 0.58F)) / 2.0F, rowY + 8, active ? FG : DIM, 0.58F);
        }
        hit("side:" + id, x + 8, rowY, SW - 16, 22, button -> action.run());
    }

    // ---------------------------------------------------------------- module list

    private void drawList(DrawContext c, List<Module> modules) {
        int x = LX, y = LY;
        String heading = !search.isEmpty() ? "Search" : favourites ? "Favorites" : recentView ? "Recent" : title(category.displayName());
        int color = accent();
        float pulse = spring("head-pop", Spring.BOUNCE, 1.0F).to(1.0F).value();
        push(c, x + 14, y + 22, pulse);
        LiquidGlass.glass(c, x, y + 9, 28, 28, 8, color, opacity);
        if (search.isEmpty() && !favourites && !recentView) {
            int iconAlpha = LiquidGlass.alpha(opacity);
            if (iconAlpha > 4) ColumnGlassBrowser.icon(c, category, x + 7, y + 16, 14, iconAlpha << 24 | 0xFFFFFF);
        } else {
            HudSurfaceRenderer.icon(c, !search.isEmpty() ? HudSurfaceRenderer.HudIcon.SEARCH : favourites ? HudSurfaceRenderer.HudIcon.FOCUS : HudSurfaceRenderer.HudIcon.CLOCK,
                x + 7, y + 16, 14, LiquidGlass.alpha(opacity) << 24 | 0xFFFFFF);
        }
        c.getMatrices().popMatrix();
        text(c, trim(heading, LW - 110, 1.18F), x + 36, y + 11, FG, 1.18F);
        long on = modules.stream().filter(Module::enabled).count();
        tracked(c, modules.size() + " MODULES  ·  " + on + " ENABLED", x + 36, y + 27, FAINT, 0.5F, 1.1F);
        String viewValue = view.label;
        int viewW = Math.round(width(viewValue, 0.72F) + 30);
        int viewX = x + LW - viewW, viewY = y + 12;
        dropdownTrigger(c, "view", viewX, viewY, viewW, 22, viewValue, () -> openViewMenu(viewX, viewY, viewW, 22));
        fillF(c, x, y + 47, LW, 1, 0x14FFFFFF);

        String key = category.name() + "|" + favourites + "|" + recentView + "|" + view.name() + "|" + search;
        if (!key.equals(listKey)) {
            listKey = key;
            listEpoch = System.nanoTime();
            rowIn.clear();
            listTarget = 0;
            listScroll.snap(0.0F);
        }
        float viewport = LIST_BOTTOM - LIST_TOP;
        listMax = Math.max(0, modules.size() * ROW - viewport);
        listTarget = Math.max(0, Math.min(listTarget, listMax));
        float scroll = listScroll.to(listTarget).value();
        long since = System.nanoTime() - listEpoch;

        c.enableScissor(x - 4, LIST_TOP, x + LW + 4, LIST_BOTTOM);
        clipTop = LIST_TOP;
        clipBottom = LIST_BOTTOM;
        for (int i = 0; i < modules.size(); i++) {
            Module module = modules.get(i);
            float rowY = LIST_TOP + i * ROW - scroll;
            Spring in = rowIn.computeIfAbsent(module.id(), id -> new Spring(Spring.PANEL, 0.0F));
            if (since >= Math.min(i, 16) * STAGGER_NANOS) in.to(1.0F);
            if (rowY + ROW < LIST_TOP || rowY > LIST_BOTTOM) continue;
            float q = in.value();
            float previous = opacity;
            opacity = previous * clamp01(q);
            int iy = (int) Math.floor(rowY);
            c.getMatrices().pushMatrix();
            c.getMatrices().translate((1.0F - q) * -14.0F, rowY - iy);
            drawRow(c, module, x, iy);
            c.getMatrices().popMatrix();
            opacity = previous;
        }
        clipTop = Float.NEGATIVE_INFINITY;
        clipBottom = Float.POSITIVE_INFINITY;
        c.disableScissor();

        if (modules.isEmpty()) {
            String line = !search.isEmpty() ? "Nothing matches \"" + trim(search, 120, 0.8F) + "\""
                : favourites ? "No favourites yet" : recentView ? "Nothing used yet" : "Nothing to show in this view";
            String hint = !search.isEmpty() ? "Try a module or setting name." : favourites
                ? "Open a module and press Favorite to pin it here."
                : recentView ? "Modules you open or switch show up here." : "Switch the view back to All.";
            text(c, line, x + (LW - width(line, 0.8F)) / 2.0F, LIST_TOP + 40, DIM, 0.8F);
            text(c, hint, x + (LW - width(hint, 0.62F)) / 2.0F, LIST_TOP + 54, FAINT, 0.62F);
        }
        scrollbar(c, x + LW + 4, LIST_TOP, LIST_BOTTOM, scroll, listMax, modules.size() * ROW);
    }

    /** Other modules or actions bound to the same key as this module. */
    private static List<String> clashes(Module module) {
        List<String> others = new ArrayList<>();
        int key = module.keybind();
        if (key < 0) return others;
        for (Module other : KhanhClient.modules().all()) {
            if (other != module && other.keybind() == key) others.add(other.name());
            for (ModuleOption option : other.options()) {
                if (option.kind() == ModuleOption.Kind.ACTION && option.actionKeybind() == key) {
                    others.add(other.name() + " › " + option.label());
                }
            }
        }
        return others;
    }

    private void drawRow(DrawContext c, Module module, int x, int y) {
        boolean isSelected = module == selected;
        boolean hot = hover(x, y + 2, LW, ROW - 4);
        int color = accent();
        float glow = spring("row:" + module.id(), Spring.SMOOTH, 0.0F).to(hot || isSelected ? 1.0F : 0.0F).value();
        float sel = spring("row-sel:" + module.id(), Spring.PANEL, 0.0F).to(isSelected ? 1.0F : 0.0F).value();
        // Rows lift a little under the mouse and drop back with a soft bounce.
        float lift = spring("lift:" + module.id(), Spring.BOUNCE, 0.0F).to(hot ? -1.5F : 0.0F).value();
        c.getMatrices().pushMatrix();
        c.getMatrices().translate(0.0F, lift);
        if (sel > 0.01F) {
            float previous = opacity;
            opacity *= clamp01(sel);
            push(c, x + LW / 2.0F, y + ROW / 2.0F, 0.96F + 0.04F * sel);
            LiquidGlass.glass(c, x, y + 2, LW, ROW - 4, 11, 0xFFFFFF, opacity);
            SmoothGuiShapes.rect(c, x, y + 2, LW, ROW - 4, 11, LiquidGlass.alpha(0.28F * opacity) << 24 | (accent() & 0xFFFFFF), true);
            c.getMatrices().popMatrix();
            opacity = previous;
        } else if (glow > 0.01F) {
            SmoothGuiShapes.rect(c, x, y + 2, LW, ROW - 4, 11, LiquidGlass.alpha(0.045F * clamp01(glow) * opacity) << 24 | 0xFFFFFF, false);
        }
        // Enabled modules: a soft accent bar on the left edge and a lit monogram tile.
        float on = clamp01(spring("on:" + module.id(), Spring.SMOOTH, module.enabled() ? 1.0F : 0.0F)
            .to(module.enabled() ? 1.0F : 0.0F).value());
        if (on > 0.02F) {
            SmoothGuiShapes.rect(c, x + 1, y + 12, 2, ROW - 24, 1, LiquidGlass.alpha(0.9F * on * opacity) << 24 | (color & 0xFFFFFF), false);
        }
        float tileBounce = spring("tile:" + module.id(), Spring.BOUNCE, 1.0F).to(hot ? 1.12F : 1.0F).value();
        push(c, x + 18, y + ROW / 2.0F, tileBounce);
        LiquidGlass.glass(c, x + 6, y + 8, 24, 24, 7, 0xFFFFFF, opacity);
        if (on > 0.02F) {
            SmoothGuiShapes.rect(c, x + 7, y + 9, 22, 22, 6, LiquidGlass.alpha(0.92F * on * opacity) << 24 | (color & 0xFFFFFF), false);
        }
        // Each module has its own icon; it turns dark on the lit tile when the module is on.
        float wob = spring("icon-wob:" + module.id(), Spring.WOBBLE, 0.0F).to(hot ? 9.0F : 0.0F).value();
        c.getMatrices().pushMatrix();
        c.getMatrices().translate(x + 18.0F, y + 20.0F);
        c.getMatrices().rotate((float) Math.toRadians(wob));
        c.getMatrices().translate(-(x + 18.0F), -(y + 20.0F));
        moduleIcon(c, module, x + 11, y + 13, 14, on > 0.5F ? 0x141418 : 0xFFFFFF, module.enabled() || on > 0.5F ? 1.0F : 0.75F);
        c.getMatrices().popMatrix();
        c.getMatrices().popMatrix();

        boolean fav = GuiState.favourite(module.id());
        float nameRoom = LW - 38 - 54 - (module.keybind() >= 0 ? 40 : 0);
        String name = trim(module.name(), nameRoom, 0.84F);
        text(c, name, x + 38, y + 10, module.enabled() || isSelected ? FG : SOFT, 0.84F);
        text(c, trim(module.description(), nameRoom, 0.56F), x + 38, y + 22, FAINT, 0.56F);
        if (fav) SmoothGuiShapes.rect(c, Math.round(x + 38 + width(name, 0.84F) + 4), y + 12, 4, 4, 2,
            LiquidGlass.alpha(opacity) << 24 | (KhanhPalette.OKAY & 0xFFFFFF), false);

        hit("row:" + module.id(), x, y + 2, LW, ROW - 4, button -> {
            if (button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE) {
                bindingModule = module;
                showModule(module, true);
            } else if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                toggleModule(module);
            } else {
                showModule(module, true);
            }
        });
        int right = x + LW - 8;
        if (module.id().equals("config")) {
            chevron(c, right - 6, y + ROW / 2.0F, -90.0F, DIM);
        } else {
            toggle(c, "module:" + module.id(), right - 32, y + 11, module.enabled(), accent(), () -> toggleModule(module));
            right -= 40;
        }
        if (module.keybind() >= 0 || bindingModule == module) {
            String key = bindingModule == module ? "..." : keyLabel(module.keybind());
            float kw = width(key, 0.56F) + 10;
            keyChip(c, key, right - kw, y + 14, bindingModule == module ? accent() : !clashes(module).isEmpty() ? KhanhPalette.BAD : 0);
        }
        c.getMatrices().popMatrix();
    }

    private static final Map<String, Boolean> ICON_EXISTS = new HashMap<>();

    /** Draws a module's own icon, falling back to its monogram if the icon file is missing. */
    private void moduleIcon(DrawContext c, Module module, int x, int y, int size, int rgb, float alphaScale) {
        Identifier id = Identifier.of(KhanhClient.MOD_ID, "textures/gui/module/" + module.id() + ".png");
        boolean exists = ICON_EXISTS.computeIfAbsent(module.id(),
            key -> client != null && client.getResourceManager().getResource(id).isPresent());
        int a = LiquidGlass.alpha(opacity * alphaScale);
        if (a <= 4) return;
        if (exists) {
            c.drawTexture(RenderPipelines.GUI_TEXTURED, id, x, y, 0.0F, 0.0F, size, size, 48, 48, 48, 48, a << 24 | rgb);
        } else {
            String mono = monogram(module.name());
            float scale = size / 21.0F;
            text(c, mono, x + size / 2.0F - width(mono, scale) / 2.0F, y + size / 2.0F - 3.5F * scale, a << 24 | rgb, scale);
        }
    }

    /** "Aim Assist" -> "AA", "Zoom" -> "Zo": a short, stable label for a module's tile. */
    private static String monogram(String name) {
        String[] words = name.replaceAll("[^A-Za-z0-9 ]", " ").trim().split("\\s+");
        if (words.length >= 2) return (words[0].substring(0, 1) + words[1].substring(0, 1)).toUpperCase(Locale.ROOT);
        String word = words.length == 1 ? words[0] : "?";
        return word.length() >= 2 ? word.substring(0, 1).toUpperCase(Locale.ROOT) + word.substring(1, 2).toLowerCase(Locale.ROOT)
            : word.toUpperCase(Locale.ROOT);
    }

    // ---------------------------------------------------------------- detail pane

    private void drawDetail(DrawContext c) {
        int x = DX, y = DY;
        LiquidGlass.glass(c, x, y, DW, DH, 14, 0xFFFFFF, opacity);
        Module module = shownModule;
        if (module == null) {
            String line = "Pick a module to see its settings";
            text(c, line, x + (DW - width(line, 0.76F)) / 2.0F, y + DH / 2.0F - 4, FAINT, 0.76F);
            return;
        }
        float d = detailIn.to(1.0F).value();
        float previous = opacity;
        opacity *= clamp01(d);
        c.getMatrices().pushMatrix();
        c.getMatrices().translate((1.0F - d) * 18.0F, 0.0F);
        int color = accent();

        float pop = spring("detail-tile", Spring.BOUNCE, 1.0F).to(1.0F).value();
        float lit = clamp01(spring("detail-on:" + module.id(), Spring.SMOOTH, module.enabled() ? 1.0F : 0.0F)
            .to(module.enabled() ? 1.0F : 0.0F).value());
        push(c, x + 31, y + 31, pop);
        LiquidGlass.glass(c, x + 14, y + 14, 34, 34, 10, 0xFFFFFF, opacity);
        if (lit > 0.02F) {
            SmoothGuiShapes.rect(c, x + 15, y + 15, 32, 32, 9, LiquidGlass.alpha(0.92F * lit * opacity) << 24 | (color & 0xFFFFFF), false);
        }
        moduleIcon(c, module, x + 21, y + 21, 20, lit > 0.5F ? 0x141418 : 0xFFFFFF, 1.0F);
        c.getMatrices().popMatrix();
        text(c, trim(module.name(), DW - 120, 1.12F), x + 56, y + 16, FG, 1.12F);
        List<String> lines = wrap(module.description(), DW - 112, 0.58F, 2);
        for (int i = 0; i < lines.size(); i++) text(c, lines.get(i), x + 56, y + 31 + i * 8, DIM, 0.58F);
        if (!module.id().equals("config")) {
            toggle(c, "detail:" + module.id(), x + DW - 14 - 32, y + 20, module.enabled(), accent(), () -> toggleModule(module));
        }

        int chipY = y + 60;
        boolean capturing = bindingModule == module;
        String bind = capturing ? "Press a key" : module.keybind() < 0 ? "No key" : keyLabel(module.keybind());
        int bindW = Math.round(width(bind, 0.66F) + 30);
        List<String> clash = clashes(module);
        chip(c, "bind:" + module.id(), HudSurfaceRenderer.HudIcon.KEYBINDS, bind, x + 14, chipY, bindW,
            capturing ? accent() : !clash.isEmpty() ? KhanhPalette.BAD : 0, button -> {
            if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                module.setKeybind(-1);
                save();
            } else {
                bindingModule = module;
            }
            sound(1.0F);
        });
        boolean fav = GuiState.favourite(module.id());
        String favLabel = fav ? "Favorited" : "Favorite";
        int favW = Math.round(width(favLabel, 0.66F) + 30);
        chip(c, "fav:" + module.id(), HudSurfaceRenderer.HudIcon.FOCUS, favLabel, x + 14 + bindW + 6, chipY, favW,
            fav ? KhanhPalette.OKAY : 0, button -> {
                boolean now = GuiState.toggleFavourite(module.id());
                spring("chip-pop:fav:" + module.id(), Spring.BOUNCE, 1.0F).kick(1.25F);
                sound(now ? 1.3F : 0.9F);
            });
        if (module.hasChangedOptions()) {
            boolean confirming = confirmReset == module && System.nanoTime() - confirmResetAt < 3_000_000_000L;
            String resetLabel = confirming ? "Sure?" : "Reset";
            int resetW = Math.round(width(resetLabel, 0.66F) + 30);
            chip(c, "reset:" + module.id(), HudSurfaceRenderer.HudIcon.WAND, resetLabel, x + 14 + bindW + 6 + favW + 6, chipY, resetW,
                confirming ? KhanhPalette.BAD : 0, button -> {
                    if (confirmReset == module && System.nanoTime() - confirmResetAt < 3_000_000_000L) {
                        module.resetOptions();
                        save();
                        confirmReset = null;
                        choiceCache.clear();
                        spring("detail-tile", Spring.BOUNCE, 1.0F).kick(1.3F);
                        sound(1.28F);
                    } else {
                        confirmReset = module;
                        confirmResetAt = System.nanoTime();
                        sound(0.95F);
                    }
                });
        } else {
            String cat = title(module.category().displayName());
            int catW = Math.round(width(cat, 0.62F) + 16);
            LiquidGlass.glass(c, x + DW - 14 - catW, chipY + 2, catW, 16, 8, color, opacity);
            text(c, cat, x + DW - 14 - catW + 8, chipY + 6.5F, FG, 0.62F);
        }
        if (!clash.isEmpty()) {
            String warning = keyLabel(module.keybind()) + " is also used by " + String.join(", ", clash) + ". Click the key to change it.";
            text(c, trim(warning, DW - 28, 0.54F), x + 14, y + 84, KhanhPalette.BAD, 0.54F);
        }
        fillF(c, x + 14, y + 94, DW - 28, 1, 0x14FFFFFF);
        tracked(c, "SETTINGS", x + 14, y + 100, FAINT, 0.52F, 1.3F);

        boolean picker = module.id().equals("visuals");
        boolean crosshair = module.id().equals("custom_crosshair");
        float total = picker ? ACCENT_CARD_H + 4 : crosshair ? CROSSHAIR_CARD_H + 4 : 0;
        for (ModuleOption option : module.options()) total += optionHeight(option) + 4;
        float viewport = DETAIL_BOTTOM - DETAIL_TOP;
        detailMax = Math.max(0, total - viewport);
        detailTarget = Math.max(0, Math.min(detailTarget, detailMax));
        float scroll = detailScroll.to(detailTarget).value();
        c.enableScissor(x + 4, DETAIL_TOP, x + DW - 4, DETAIL_BOTTOM);
        clipTop = DETAIL_TOP;
        clipBottom = DETAIL_BOTTOM;
        float listY = DETAIL_TOP - scroll;
        int rowY = (int) Math.floor(listY);
        c.getMatrices().pushMatrix();
        c.getMatrices().translate(0.0F, listY - rowY);
        if (picker) {
            drawAccentPicker(c, x, rowY, DW);
            rowY += ACCENT_CARD_H + 4;
        }
        if (crosshair) {
            drawCrosshairPreview(c, x, rowY, DW);
            rowY += CROSSHAIR_CARD_H + 4;
        }
        for (ModuleOption option : module.options()) {
            int height = optionHeight(option);
            if (rowY + height >= DETAIL_TOP && rowY <= DETAIL_BOTTOM) drawOption(c, module, option, x, rowY, DW, accent());
            rowY += height + 4;
        }
        c.getMatrices().popMatrix();
        clipTop = Float.NEGATIVE_INFINITY;
        clipBottom = Float.POSITIVE_INFINITY;
        c.disableScissor();
        if (module.options().isEmpty()) {
            text(c, module.id().equals("config") ? "Open Configs in the sidebar to manage profiles." : "This module has no settings.",
                x + 14, DETAIL_TOP + 6, FAINT, 0.66F);
        }
        scrollbar(c, x + DW - 6, DETAIL_TOP, DETAIL_BOTTOM, scroll, detailMax, total);
        c.getMatrices().popMatrix();
        opacity = previous;
    }

    /** Interface accent swatches; the whole menu recolours live as you pick. */
    private void drawAccentPicker(DrawContext c, int x, int y, int w) {
        tile(c, "accent-picker", x + 10, y, w - 20, ACCENT_CARD_H);
        text(c, "Interface accent", x + 20, y + 7, FG, 0.78F);
        text(c, "Colours the menu. The HUD keeps the logo colour.", x + 20, y + 19, DIM, 0.56F);
        int current = GuiState.accent();
        int size = 16, gap = 8;
        int sx = x + 20;
        for (int i = 0; i < ACCENTS.length; i++) {
            int value = ACCENTS[i];
            int swatch = value == 0 ? MONO : 0xFF000000 | value;
            int cx = sx + i * (size + gap);
            boolean active = current == value;
            boolean hot = hover(cx - 2, y + 32, size + 4, size + 4);
            float pop = spring("swatch:" + i, Spring.BOUNCE, 1.0F).to(active ? 1.15F : hot ? 1.1F : 1.0F).value();
            push(c, cx + size / 2.0F, y + 34 + size / 2.0F, pop);
            if (active) SmoothGuiShapes.rect(c, cx - 3, y + 31, size + 6, size + 6, (size + 6) / 2,
                LiquidGlass.alpha(0.9F * opacity) << 24 | 0xFFFFFF, true);
            SmoothGuiShapes.rect(c, cx, y + 34, size, size, size / 2, LiquidGlass.alpha(opacity) << 24 | (swatch & 0xFFFFFF), false);
            c.getMatrices().popMatrix();
            hit("swatch:" + i, cx - 2, y + 32, size + 4, size + 4, button -> {
                GuiState.setAccent(value);
                spring("swatch:" + ACCENT_INDEX.getOrDefault(value, 0), Spring.BOUNCE, 1.0F).kick(1.4F);
                sound(1.2F);
            });
        }
    }

    private void chip(DrawContext c, String id, HudSurfaceRenderer.HudIcon icon, String label, int x, int y, int w, int tint,
                      IntConsumer action) {
        float s = pressScale(id, x, y, w, 20, 1.06F) * spring("chip-pop:" + id, Spring.BOUNCE, 1.0F).to(1.0F).value();
        push(c, x + w / 2.0F, y + 10, s);
        LiquidGlass.glass(c, x, y, w, 20, 10, tint != 0 ? tint : 0xFFFFFF, opacity);
        int iconAlpha = LiquidGlass.alpha(opacity * (tint != 0 ? 1.0F : 0.7F));
        if (iconAlpha > 4) HudSurfaceRenderer.icon(c, icon, x + 8, y + 5, 10, iconAlpha << 24 | 0xFFFFFF);
        text(c, label, x + 22, y + 7, tint != 0 ? FG : SOFT, 0.66F);
        c.getMatrices().popMatrix();
        hit(id, x, y, w, 20, action);
    }

    private int optionHeight(ModuleOption option) {
        Gallery gallery = gallery(option);
        if (gallery != null) {
            int rows = (choices(option).size() + GALLERY_COLS - 1) / GALLERY_COLS;
            return 30 + rows * (GALLERY_CELL + GALLERY_GAP) + 4;
        }
        if (option.kind() == ModuleOption.Kind.NUMBER) return 44;
        if (option.kind() == ModuleOption.Kind.COLOR && option == colorOpen) return OPTION_H + 58;
        return OPTION_H;
    }

    private void tile(DrawContext c, String id, int x, int y, int w, int h) {
        boolean hot = hover(x, y, w, h);
        float glow = clamp01(spring("tile-row:" + id, Spring.SMOOTH, 0.0F).to(hot ? 1.0F : 0.0F).value());
        SmoothGuiShapes.rect(c, x, y, w, h, 10, LiquidGlass.alpha((0.035F + 0.035F * glow) * opacity) << 24 | 0xFFFFFF, false);
        SmoothGuiShapes.rect(c, x, y, w, h, 10, LiquidGlass.alpha((0.05F + 0.06F * glow) * opacity) << 24 | 0xFFFFFF, true);
    }

    private void drawOption(DrawContext c, Module module, ModuleOption option, int x, int rowY, int w, int color) {
        String key = module.id() + ":" + option.id();
        tile(c, key, x + 10, rowY, w - 20, optionHeight(option));
        Gallery gallery = gallery(option);
        if (gallery != null) {
            drawGallery(c, key, option, gallery, x, rowY, w, color);
            return;
        }
        int left = x + 20;
        int right = x + w - 20;
        List<String> choices = option.kind() == ModuleOption.Kind.CHOICE ? choices(option) : List.of();
        boolean segmented = option.kind() == ModuleOption.Kind.CHOICE && segmentedWidth(choices) > 0;
        int controlW = switch (option.kind()) {
            case BOOLEAN -> 32;
            case CHOICE -> segmented ? segmentedWidth(choices) : Math.min(118, Math.round(width(option.choiceValue(), 0.72F) + 30));
            case TEXT -> 110;
            case ACTION -> 44;
            case COLOR -> Math.round(width(option.displayValue(), 0.62F) + 32);
            case NUMBER -> Math.round(width(option.displayValue(), 0.78F));
        };
        boolean wrapTexture = false;
        float textW = right - left - controlW - 10 - (wrapTexture ? 28 : 0);
        boolean changed = !option.isDefault();
        float changedIn = clamp01(spring("changed:" + key, Spring.BOUNCE, changed ? 1.0F : 0.0F).to(changed ? 1.0F : 0.0F).value());
        float labelX = left + 7.0F * changedIn;
        if (changedIn > 0.02F) {
            push(c, left + 2.5F, rowY + 10.5F, changedIn);
            SmoothGuiShapes.rect(c, left, rowY + 8, 5, 5, 2, LiquidGlass.alpha(opacity) << 24 | (color & 0xFFFFFF), false);
            c.getMatrices().popMatrix();
        }
        text(c, trim(option.label(), textW - 7.0F * changedIn, 0.78F), labelX, rowY + 7, FG, 0.78F);
        if (changed) {
            String reset = "Reset to default";
            float resetW = width(reset, 0.56F);
            boolean hot = hover(left, rowY + 17, resetW + 4, 11);
            text(c, reset, left, rowY + 19, hot ? FG : color, 0.56F);
            if (hot) fillF(c, left, rowY + 26, resetW, 0.6F, color);
            hit("reset-option:" + key, left, rowY + 17, resetW + 4, 11, button -> {
                option.reset();
                save();
                spring("changed:" + key, Spring.BOUNCE, 1.0F).kick(1.6F);
                sound(1.18F);
            });
        } else {
            text(c, trim(option.description(), textW, 0.56F), left, rowY + 19, DIM, 0.56F);
        }

        switch (option.kind()) {
            case BOOLEAN -> toggle(c, "option:" + key, right - 32, rowY + 8, option.booleanValue(), color, () -> {
                option.toggle();
                save();
                sound(option.booleanValue() ? 1.24F : 0.9F);
            });
            case CHOICE -> {
                if (wrapTexture) {
                    // Live swatch of the PNG the items are wrapped in.
                    int tx = right - controlW - 28, ty = rowY + 7;
                    Identifier wrap = Identifier.of(KhanhClient.MOD_ID,
                        dev.khanh.feature.GlintPattern.texturePath(option.choiceValue()));
                    c.drawTexture(RenderPipelines.GUI_TEXTURED, wrap, tx, ty, 0.0F, 0.0F, 20, 20, 64, 64, 128, 128,
                        LiquidGlass.alpha(opacity) << 24 | 0xFFFFFF);
                    SmoothGuiShapes.rect(c, tx, ty, 20, 20, 6, LiquidGlass.alpha(0.35F * opacity) << 24 | 0xFFFFFF, true);
                }
                if (segmented) drawSegmented(c, key, option, choices, right - controlW, rowY + 7, controlW, color);
                else drawChoiceDropdown(c, key, option, right - controlW, rowY + 7, controlW, color);
            }
            case NUMBER -> drawSlider(c, key, option, left, right, rowY, color);
            case COLOR -> {
                int px = right - controlW, py = rowY + 7;
                boolean open = colorOpen == option;
                pillButton(c, "color:" + key, "", px, py, controlW, 20, open ? color : 0, button -> {
                    colorOpen = open ? null : option;
                    sound(1.06F);
                });
                SmoothGuiShapes.rect(c, px + 7, py + 5, 10, 10, 5, LiquidGlass.alpha(opacity) << 24 | (option.colorValue() & 0xFFFFFF), false);
                text(c, option.displayValue(), px + 21, py + 7, SOFT, 0.62F);
                if (open) drawColorEditor(c, option, left, rowY + OPTION_H + 2, right - left);
            }
            case TEXT -> {
                int px = right - controlW, py = rowY + 7;
                boolean active = editing == option;
                LiquidGlass.glass(c, px, py, controlW, 20, 10, active ? color : 0xFFFFFF, opacity);
                String value = option.textValue();
                String shown = value.isEmpty() && !active ? "Empty" : tail(value, controlW - 20, 0.7F);
                text(c, shown, px + 10, py + 7, value.isEmpty() && !active ? FAINT : FG, 0.7F);
                if (active && caretOn()) fillF(c, px + 10 + width(shown, 0.7F) + 1, py + 5, 1.0F, 10, color);
                hit("text:" + key, px, py, controlW, 20, button -> {
                    editing = option;
                    sound(1.1F);
                });
            }
            case ACTION -> {
                boolean capturing = bindingAction == option;
                String label = capturing ? "Key…" : "Run";
                String id = "action:" + key;
                pillButton(c, id, label, right - controlW, rowY + 7, controlW, 20, color, button -> {
                    if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                        bindingAction = option;
                        sound(1.0F);
                    } else {
                        option.trigger();
                        spring("action-pop:" + id, Spring.BOUNCE, 1.0F).kick(1.2F);
                        sound(1.3F);
                    }
                });
                if (option.actionKeybind() >= 0) {
                    String bound = keyLabel(option.actionKeybind());
                    keyChip(c, bound, right - controlW - 8 - width(bound, 0.56F) - 10, rowY + 11, 0);
                }
            }
        }
    }

    /** Width of a segmented control for these choices, or 0 when they will not fit. */
    private int segmentedWidth(List<String> choices) {
        if (choices.size() < 2 || choices.size() > 4) return 0;
        float widest = 0;
        for (String choice : choices) widest = Math.max(widest, width(choice, 0.66F));
        int total = Math.round((widest + 14) * choices.size()) + 4;
        return total <= 160 ? total : 0;
    }

    private void drawSegmented(DrawContext c, String key, ModuleOption option, List<String> choices, int x, int y, int w, int color) {
        int h = 20;
        LiquidGlass.track(c, x, y, w, h, h / 2, opacity);
        float segment = (w - 4) / (float) choices.size();
        int index = Math.max(0, choices.indexOf(option.choiceValue()));
        float slide = spring("segment:" + key, Spring.PANEL, index).to(index).value();
        float sx = x + 2 + slide * segment;
        int ix = (int) Math.floor(sx);
        c.getMatrices().pushMatrix();
        c.getMatrices().translate(sx - ix, 0.0F);
        LiquidGlass.glass(c, ix, y + 2, Math.round(segment), h - 4, (h - 4) / 2, color, opacity);
        c.getMatrices().popMatrix();
        for (int i = 0; i < choices.size(); i++) {
            String choice = choices.get(i);
            float cx = x + 2 + i * segment;
            boolean active = i == index;
            boolean hot = hover(cx, y, segment, h);
            text(c, choice, cx + (segment - width(choice, 0.66F)) / 2.0F, y + 7, active ? FG : hot ? SOFT : DIM, 0.66F);
            final String value = choice;
            hit("segment:" + key + ":" + i, cx, y, segment, h, button -> {
                if (!option.choiceValue().equals(value)) {
                    option.setChoice(value);
                    save();
                    sound(1.14F);
                }
            });
        }
    }

    private void drawChoiceDropdown(DrawContext c, String key, ModuleOption option, int px, int py, int controlW, int color) {
        String id = "choice:" + key;
        boolean isOpen = menu != null && menu.id.equals(id);
        float s = pressScale(id, px, py, controlW, 20, 1.05F);
        push(c, px + controlW / 2.0F, py + 10, s);
        LiquidGlass.glass(c, px, py, controlW, 20, 10, isOpen ? color : 0xFFFFFF, opacity);
        text(c, trim(option.choiceValue(), controlW - 28, 0.72F), px + 10, py + 7, FG, 0.72F);
        float angle = spring(id + ":chevron", Spring.PANEL, 0.0F).to(isOpen ? 180.0F : 0.0F).value();
        chevron(c, px + controlW - 11, py + 10, angle, DIM);
        c.getMatrices().popMatrix();
        hit(id, px, py, controlW, 20, button -> openChoiceMenu(id, option, px, py, controlW, 20));
    }

    private void drawSlider(DrawContext c, String key, ModuleOption option, int left, int right, int rowY, int color) {
        String value = option.displayValue();
        text(c, value, right - width(value, 0.78F), rowY + 7, color, 0.78F);
        int tx = left, ty = rowY + 31, tw = right - left;
        LiquidGlass.track(c, tx, ty, tw, 6, 3, opacity);
        float ratio = spring("slider:" + key, Spring.SMOOTH, (float) option.ratio()).to((float) option.ratio()).value();
        float knobX = tx + tw * clamp01(ratio);
        int filled = Math.max(6, Math.round(tw * clamp01(ratio)));
        LiquidGlass.accent(c, tx, ty, filled, 6, 3, color, opacity);
        boolean active = sliding == option;
        float knobScale = spring("slider-knob:" + key, Spring.BOUNCE, 1.0F)
            .to(active ? 1.3F : hover(tx - 4, ty - 8, tw + 8, 22) ? 1.12F : 1.0F).value();
        push(c, knobX, ty + 3, knobScale);
        LiquidGlass.knob(c, Math.round(knobX - 6), ty - 3, 12, opacity);
        c.getMatrices().popMatrix();
        float bubble = clamp01(spring("slider-bubble:" + key, Spring.PANEL, 0.0F).to(active ? 1.0F : 0.0F).value());
        if (bubble > 0.02F) {
            float previous = opacity;
            opacity *= bubble;
            float bw = width(value, 0.68F) + 12;
            float bx = Math.max(left, Math.min(right - bw, knobX - bw / 2.0F));
            push(c, knobX, ty - 6, 0.7F + 0.3F * bubble);
            LiquidGlass.glass(c, Math.round(bx), ty - 22, Math.round(bw), 14, 7, color, opacity);
            text(c, value, bx + 6, ty - 18.5F, FG, 0.68F);
            c.getMatrices().popMatrix();
            opacity = previous;
        }
        hit("slider:" + key, tx - 4, rowY + 22, tw + 8, 20, button -> {
            sliding = option;
            dragLeft = tx;
            dragWidth = tw;
            option.setFromRatio((mx - tx) / tw);
            dirty = true;
        });
    }

    private void drawColorEditor(DrawContext c, ModuleOption option, int x, int y, int w) {
        float[] hsv = hsv(option.colorValue());
        String[] labels = {"HUE", "SAT", "BRIGHT"};
        for (int channel = 0; channel < 3; channel++) {
            int by = y + channel * 18;
            tracked(c, labels[channel], x, by + 1, FAINT, 0.5F, 0.8F);
            int bx = x + 40, bw = w - 40;
            int a = LiquidGlass.alpha(opacity);
            for (int i = 0; i < bw; i++) {
                float t = i / (float) Math.max(1, bw - 1);
                int rgb = switch (channel) {
                    case 0 -> java.awt.Color.HSBtoRGB(t, 1.0F, 1.0F);
                    case 1 -> java.awt.Color.HSBtoRGB(hsv[0], t, Math.max(0.2F, hsv[2]));
                    default -> java.awt.Color.HSBtoRGB(hsv[0], hsv[1], t);
                };
                c.fill(bx + i, by, bx + i + 1, by + 7, a << 24 | (rgb & 0xFFFFFF));
            }
            SmoothGuiShapes.rect(c, bx, by, bw, 7, 3, LiquidGlass.alpha(0.3F * opacity) << 24 | 0xFFFFFF, true);
            LiquidGlass.knob(c, Math.round(bx + hsv[channel] * (bw - 1) - 5), by - 2, 11, opacity);
            final int ch = channel;
            hit("color-bar:" + option.id() + ch, bx - 4, by - 4, bw + 8, 15, button -> {
                colorSliding = option;
                colorChannel = ch;
                dragLeft = bx;
                dragWidth = bw;
                updateColor(mx);
            });
        }
    }

    // ---------------------------------------------------------------- settings page

    /** One row on the Settings page: a section header, the accent picker, a module's
     * on/off switch, or one of a module's options. */
    private record SettingRow(String header, Module module, ModuleOption option, boolean picker) { }

    private void openSettings() {
        page = Page.SETTINGS;
        closeMenu();
        sound(1.12F);
    }

    private List<SettingRow> settingRows() {
        List<SettingRow> rows = new ArrayList<>();
        Module visuals = KhanhClient.modules().byId("visuals");
        Module animations = KhanhClient.modules().byId("animations");
        Module hud = KhanhClient.modules().byId("hud");
        Module mainMenu = KhanhClient.modules().byId("main_menu");
        Module intro = KhanhClient.modules().byId("intro");
        Module sounds = KhanhClient.modules().byId("client_sounds");
        Module colours = KhanhClient.modules().byId("client_colors");
        rows.add(new SettingRow("INTERFACE", null, null, false));
        rows.add(new SettingRow(null, null, null, true));
        if (visuals != null) {
            for (String id : List.of("gui_layout", "gui_zoom", "blur", "gui_finish", "glass_opacity")) {
                ModuleOption option = visuals.option(id);
                if (option != null) rows.add(new SettingRow(null, visuals, option, false));
            }
        }
        if (colours != null) {
            rows.add(new SettingRow("COLOURS", null, null, false));
            rows.add(new SettingRow(null, colours, null, false));
            for (ModuleOption option : colours.options()) rows.add(new SettingRow(null, colours, option, false));
        }
        if (animations != null) {
            rows.add(new SettingRow(null, animations, null, false));
            for (ModuleOption option : animations.options()) rows.add(new SettingRow(null, animations, option, false));
        }
        if (hud != null) {
            rows.add(new SettingRow("HUD", null, null, false));
            rows.add(new SettingRow(null, hud, null, false));
            for (ModuleOption option : hud.options()) rows.add(new SettingRow(null, hud, option, false));
        }
        if (mainMenu != null || intro != null) {
            rows.add(new SettingRow("KHANH CLIENT MENUS", null, null, false));
            if (mainMenu != null) {
                rows.add(new SettingRow(null, mainMenu, null, false));
                for (ModuleOption option : mainMenu.options()) rows.add(new SettingRow(null, mainMenu, option, false));
            }
            if (intro != null) {
                rows.add(new SettingRow(null, intro, null, false));
                for (ModuleOption option : intro.options()) rows.add(new SettingRow(null, intro, option, false));
            }
        }
        if (sounds != null) {
            rows.add(new SettingRow("SOUNDS", null, null, false));
            for (ModuleOption option : sounds.options()) rows.add(new SettingRow(null, sounds, option, false));
        }
        return rows;
    }

    private int settingHeight(SettingRow row) {
        if (row.header() != null) return 24;
        if (row.picker()) return ACCENT_CARD_H;
        if (row.option() == null) return OPTION_H;
        return optionHeight(row.option());
    }

    private void drawSettingsPage(DrawContext c) {
        int x = CX, y = LY, w = CW, h = VH - LY - 10;
        LiquidGlass.glass(c, x, y, w, h, 14, 0xFFFFFF, opacity);
        LiquidGlass.glass(c, x + 14, y + 14, 28, 28, 8, accent(), opacity);
        HudSurfaceRenderer.icon(c, HudSurfaceRenderer.HudIcon.WAND, x + 21, y + 21, 14, LiquidGlass.alpha(opacity) << 24 | 0xFFFFFF);
        text(c, "Settings", x + 50, y + 15, FG, 1.18F);
        tracked(c, "INTERFACE  ·  COLOURS  ·  HUD  ·  MAIN MENU  ·  SOUNDS", x + 50, y + 31, FAINT, 0.5F, 1.1F);
        pillButton(c, "all-visuals", "All visual options", x + w - 16 - 112, y + 18, 112, 20, 0, button -> {
            Module visuals = KhanhClient.modules().byId("visuals");
            if (visuals == null) return;
            view = View.ALL;
            lastView = View.ALL;
            setCategory(visuals.category());
            showModule(visuals, true);
        });
        fillF(c, x + 14, y + 52, w - 28, 1, 0x14FFFFFF);

        int colW = Math.min(500, w - 40);
        int colX = x + (w - colW) / 2;
        int top = y + 58, bottom = y + h - 8;
        List<SettingRow> rows = settingRows();
        float total = 0;
        for (SettingRow row : rows) total += settingHeight(row) + 4;
        settingsMax = Math.max(0, total - (bottom - top));
        settingsTarget = Math.max(0, Math.min(settingsTarget, settingsMax));
        float scroll = settingsScroll.to(settingsTarget).value();

        c.enableScissor(x + 4, top, x + w - 4, bottom);
        clipTop = top;
        clipBottom = bottom;
        float listY = top - scroll;
        int rowY = (int) Math.floor(listY);
        c.getMatrices().pushMatrix();
        c.getMatrices().translate(0.0F, listY - rowY);
        for (SettingRow row : rows) {
            int height = settingHeight(row);
            if (rowY + height >= top && rowY <= bottom) {
                if (row.header() != null) {
                    tracked(c, row.header(), colX + 12, rowY + 11, FAINT, 0.52F, 1.3F);
                } else if (row.picker()) {
                    drawAccentPicker(c, colX, rowY, colW);
                } else if (row.option() == null) {
                    drawModuleSwitch(c, row.module(), colX, rowY, colW);
                } else {
                    drawOption(c, row.module(), row.option(), colX, rowY, colW, accent());
                }
            }
            rowY += height + 4;
        }
        c.getMatrices().popMatrix();
        clipTop = Float.NEGATIVE_INFINITY;
        clipBottom = Float.POSITIVE_INFINITY;
        c.disableScissor();
        scrollbar(c, x + w - 8, top, bottom, scroll, settingsMax, total);
    }

    private void drawModuleSwitch(DrawContext c, Module module, int x, int y, int w) {
        tile(c, "switch:" + module.id(), x + 10, y, w - 20, OPTION_H);
        text(c, module.name(), x + 20, y + 7, FG, 0.78F);
        text(c, trim(module.description(), w - 90, 0.56F), x + 20, y + 19, DIM, 0.56F);
        toggle(c, "settings-switch:" + module.id(), x + w - 20 - 32, y + 8, module.enabled(), accent(), () -> {
            module.toggle();
            save();
            sound(module.enabled() ? 1.34F : 0.82F);
        });
    }

    /** Previews wear the chosen Fun Skin while that module is on. */
    private static net.minecraft.entity.player.SkinTextures withFunSkin(net.minecraft.entity.player.SkinTextures base) {
        return dev.khanh.feature.KhanhFeatures.enabled("fun_skins") ? dev.khanh.feature.FunSkinService.apply(base) : base;
    }

    // ---------------------------------------------------------------- image galleries

    /** Options whose choices are pictures: totem textures, glint wraps and skies. */
    private record Gallery(String folder, String prefix, int textureSize, boolean pixelArt) { }
    private static final int GALLERY_COLS = 5, GALLERY_CELL = 42, GALLERY_GAP = 6;
    private final Map<ModuleOption, Gallery> galleries = new IdentityHashMap<>();
    private boolean galleriesBuilt;

    private Gallery gallery(ModuleOption option) {
        if (!galleriesBuilt) {
            galleriesBuilt = true;
            link("totem_styles", "style", new Gallery("textures/item/", "totem_style_", 16, true));
            link("custom_glint", "theme", new Gallery("textures/glint/", "", 128, false));
            link("fun_skins", "skin", new Gallery("textures/skin_preview/", "", 64, true));
            link("shader_fog", "sky", new Gallery("textures/sky_preview/", "", 192, false));
        }
        return galleries.get(option);
    }

    private void link(String moduleId, String optionId, Gallery gallery) {
        Module module = KhanhClient.modules().byId(moduleId);
        ModuleOption option = module == null ? null : module.option(optionId);
        if (option != null) galleries.put(option, gallery);
    }

    private static final Map<String, int[]> IMAGE_SIZES = new HashMap<>();

    /** Width and height of a texture, read once; null when the picture does not exist. */
    private int[] imageSize(Identifier id) {
        return IMAGE_SIZES.computeIfAbsent(id.toString(), key -> {
            if (client == null) return new int[0];
            var resource = client.getResourceManager().getResource(id);
            if (resource.isEmpty()) return new int[0];
            try (var stream = resource.get().getInputStream(); var image = net.minecraft.client.texture.NativeImage.read(stream)) {
                return new int[] {image.getWidth(), image.getHeight()};
            } catch (Exception exception) {
                return new int[0];
            }
        });
    }

    /** A grid of picture tiles; click one to choose it. The header names the hovered or chosen one. */
    private void drawGallery(DrawContext c, String key, ModuleOption option, Gallery gallery, int x, int rowY, int w, int color) {
        List<String> choices = choices(option);
        int left = x + 20, gridW = GALLERY_COLS * GALLERY_CELL + (GALLERY_COLS - 1) * GALLERY_GAP;
        int gx = x + (w - gridW) / 2, gy = rowY + 28;
        String hovered = null;
        for (int i = 0; i < choices.size(); i++) {
            String choice = choices.get(i);
            int cx = gx + (i % GALLERY_COLS) * (GALLERY_CELL + GALLERY_GAP), cy = gy + (i / GALLERY_COLS) * (GALLERY_CELL + GALLERY_GAP);
            boolean chosen = choice.equals(option.choiceValue());
            boolean hot = hover(cx, cy, GALLERY_CELL, GALLERY_CELL);
            if (hot) hovered = choice;
            float pop = spring("gal:" + key + ":" + i, Spring.BOUNCE, 1.0F).to(hot ? 1.08F : 1.0F).value();
            push(c, cx + GALLERY_CELL / 2.0F, cy + GALLERY_CELL / 2.0F, pop);
            SmoothGuiShapes.rect(c, cx, cy, GALLERY_CELL, GALLERY_CELL, 8, LiquidGlass.alpha(0.6F * opacity) << 24 | 0x0E0E12, false);
            Identifier id = Identifier.of(KhanhClient.MOD_ID, gallery.folder() + gallery.prefix()
                + choice.toLowerCase(Locale.ROOT).replace(' ', '_') + ".png");
            int[] size = imageSize(id);
            int a = LiquidGlass.alpha(opacity);
            if (size.length == 2 && a > 4) {
                int inset = gallery.pixelArt() ? 5 : 3, draw = GALLERY_CELL - inset * 2;
                // Skies are wide panoramas: show a centred square slice of them.
                int u = size[0] > size[1] ? (size[0] - size[1]) / 2 : 0, region = Math.min(size[0], size[1]);
                c.drawTexture(RenderPipelines.GUI_TEXTURED, id, cx + inset, cy + inset, u, 0.0F, draw, draw,
                    region, region, size[0], size[1], a << 24 | 0xFFFFFF);
            } else {
                String initial = choice.substring(0, Math.min(2, choice.length()));
                text(c, initial, cx + GALLERY_CELL / 2.0F - width(initial, 0.8F) / 2.0F, cy + GALLERY_CELL / 2.0F - 3, DIM, 0.8F);
            }
            SmoothGuiShapes.rect(c, cx, cy, GALLERY_CELL, GALLERY_CELL, 8,
                LiquidGlass.alpha((chosen ? 0.95F : hot ? 0.45F : 0.12F) * opacity) << 24 | ((chosen ? color : 0xFFFFFF) & 0xFFFFFF), true);
            c.getMatrices().popMatrix();
            int index = i;
            hit("gal:" + key + ":" + i, cx, cy, GALLERY_CELL, GALLERY_CELL, button -> {
                option.setChoice(choice);
                save();
                spring("gal:" + key + ":" + index, Spring.BOUNCE, 1.0F).kick(1.2F);
                sound(1.18F);
            });
        }
        text(c, option.label(), left, rowY + 9, FG, 0.78F);
        String shown = hovered != null ? hovered : option.choiceValue();
        text(c, shown, x + w - 20 - width(shown, 0.66F), rowY + 10, hovered != null ? SOFT : color, 0.66F);
    }

    // ---------------------------------------------------------------- crosshair preview

    /** Two live previews of the Custom Crosshair: open air, and over a player in the target colour. */
    private void drawCrosshairPreview(DrawContext c, int x, int y, int w) {
        tile(c, "crosshair-preview", x + 10, y, w - 20, CROSSHAIR_CARD_H);
        text(c, "Preview", x + 20, y + 7, FG, 0.78F);
        int panelW = (w - 50) / 2, panelH = CROSSHAIR_CARD_H - 30, py = y + 22;
        for (int i = 0; i < 2; i++) {
            int px = x + 20 + i * (panelW + 10);
            SmoothGuiShapes.rect(c, px, py, panelW, panelH, 8, LiquidGlass.alpha(0.6F * opacity) << 24 | 0x101418, false);
            if (i == 1) {
                // A simple player silhouette behind the crosshair.
                int sx = px + panelW / 2, sy = py + panelH / 2;
                int body = LiquidGlass.alpha(0.55F * opacity) << 24 | 0x6B7280;
                SmoothGuiShapes.rect(c, sx - 5, sy - 18, 10, 10, 2, body, false);
                SmoothGuiShapes.rect(c, sx - 7, sy - 7, 14, 16, 2, body, false);
                SmoothGuiShapes.rect(c, sx - 6, sy + 9, 5, 11, 1, body, false);
                SmoothGuiShapes.rect(c, sx + 1, sy + 9, 5, 11, 1, body, false);
            }
            if (opacity > 0.5F) KhanhHudRenderer.drawCrosshair(c, px + panelW / 2, py + panelH / 2, i == 1);
            String label = i == 0 ? "Normal" : "On a player";
            text(c, label, px + 6, py + panelH - 10, FAINT, 0.5F);
        }
    }

    // ---------------------------------------------------------------- cosmetics page

    private static final String[] COSMETICS = {"fun_skins", "custom_cape", "elytra_colors", "wings", "crown", "cat_ears", "devil_horns",
        "orbit_orbs", "saturn_ring", "energy_shield", "rune_circle", "motion_trail", "footprints", "spirit_pet", "star_aura",
        "rain_cloud", "china_hat", "player_halo", "body_aura", "custom_glint", "totem_styles", "jump_circles", "fireflies"};
    private float cosmeticPreviewX, cosmeticPreviewY, cosmeticPreviewW, cosmeticPreviewH;
    private final Spring cosmeticsScroll = new Spring(Spring.SMOOTH, 0.0F);
    private float cosmeticsTarget, cosmeticsMax;
    private boolean cosmeticPreviewShown;

    private void openCosmetics() {
        page = Page.COSMETICS;
        closeMenu();
        sound(1.12F);
    }

    /** A wardrobe: your skin on the left (wearing your cape), every cosmetic as a card on the right. */
    private void drawCosmetics(DrawContext c) {
        int x = CX, y = LY, w = CW, h = VH - LY - 10;
        LiquidGlass.glass(c, x, y, w, h, 14, 0xFFFFFF, opacity);
        text(c, "Cosmetics", x + 18, y + 16, FG, 1.18F);
        tracked(c, "LOCAL ONLY  \u00b7  ONLY YOU SEE THEM", x + 18, y + 33, FAINT, 0.5F, 1.1F);
        int previewW = 196, previewX = x + 16, previewY = y + 54, previewH = h - 70;
        tile(c, "cosmetic-preview", previewX, previewY, previewW, previewH);
        String hint = hoveredCosmetic != null ? "Trying on: " + hoveredCosmetic.name() : "Drag to spin  \u00b7  hover a cosmetic to try it";
        text(c, trim(hint, previewW - 16, 0.58F), previewX + (previewW - Math.min(previewW - 16, width(hint, 0.58F))) / 2.0F,
            previewY + previewH - 15, hoveredCosmetic != null ? FG : FAINT, 0.58F);
        cosmeticPreviewX = previewX + 8;
        cosmeticPreviewY = previewY + 8;
        cosmeticPreviewW = previewW - 16;
        cosmeticPreviewH = previewH - 28;
        Module hoveredNow = null;
        cosmeticPreviewShown = page == Page.COSMETICS && !inert && opacity > 0.9F;

        int gx = previewX + previewW + 12, gw = x + w - 16 - gx, gap = 8, cols = 2;
        int cardW = (gw - gap) / cols, cardH = 50;
        int gridTop = previewY, gridBottom = y + h - 10;
        int rowsTotal = (COSMETICS.length + cols - 1) / cols;
        cosmeticsMax = Math.max(0, rowsTotal * (cardH + gap) - gap - (gridBottom - gridTop));
        cosmeticsTarget = Math.max(0, Math.min(cosmeticsTarget, cosmeticsMax));
        float scrolled = cosmeticsScroll.to(cosmeticsTarget).value();
        c.enableScissor(gx - 6, gridTop - 2, gx + gw + 6, gridBottom);
        clipTop = gridTop - 2;
        clipBottom = gridBottom;
        for (int i = 0; i < COSMETICS.length; i++) {
            Module module = KhanhClient.modules().byId(COSMETICS[i]);
            if (module == null) continue;
            int cx = gx + (i % cols) * (cardW + gap), cy = Math.round(previewY + (i / cols) * (cardH + gap) - scrolled);
            if (cy + cardH < gridTop || cy > gridBottom) continue;
            boolean hot = hover(cx, cy, cardW, cardH);
            if (hot && hover(gx - 6, gridTop - 2, gw + 12, gridBottom - gridTop + 2)) hoveredNow = module;
            float pop = spring("cos-pop:" + module.id(), Spring.BOUNCE, 1.0F).to(hot ? 1.03F : 1.0F).value();
            float on = clamp01(spring("cos-on:" + module.id(), Spring.SMOOTH, module.enabled() ? 1.0F : 0.0F)
                .to(module.enabled() ? 1.0F : 0.0F).value());
            push(c, cx + cardW / 2.0F, cy + cardH / 2.0F, pop);
            LiquidGlass.glass(c, cx, cy, cardW, cardH, 12, 0xFFFFFF, opacity);
            if (on > 0.02F) SmoothGuiShapes.rect(c, cx, cy, cardW, cardH, 12,
                LiquidGlass.alpha(0.5F * on * opacity) << 24 | (accent() & 0xFFFFFF), true);
            LiquidGlass.glass(c, cx + 10, cy + 12, 26, 26, 8, 0xFFFFFF, opacity);
            if (on > 0.02F) SmoothGuiShapes.rect(c, cx + 11, cy + 13, 24, 24, 7,
                LiquidGlass.alpha(0.92F * on * opacity) << 24 | (accent() & 0xFFFFFF), false);
            float wob = spring("icon-wob:" + module.id(), Spring.WOBBLE, 0.0F).to(hot ? 9.0F : 0.0F).value();
            c.getMatrices().pushMatrix();
            c.getMatrices().translate(cx + 23.0F, cy + 25.0F);
            c.getMatrices().rotate((float) Math.toRadians(wob));
            c.getMatrices().translate(-(cx + 23.0F), -(cy + 25.0F));
            moduleIcon(c, module, cx + 15, cy + 17, 16, on > 0.5F ? 0x141418 : 0xFFFFFF, 1.0F);
            c.getMatrices().popMatrix();
            ModuleOption colour = module.option("color");
            float nameRoom = cardW - 44 - 46;
            text(c, trim(module.name(), nameRoom, 0.78F), cx + 44, cy + 14, FG, 0.78F);
            if (colour != null) {
                SmoothGuiShapes.rect(c, cx + 44, cy + 29, 8, 8, 4, LiquidGlass.alpha(opacity) << 24 | (colour.colorValue() & 0xFFFFFF), false);
                text(c, "Customise", cx + 56, cy + 30, FAINT, 0.52F);
            } else {
                text(c, "Customise", cx + 44, cy + 30, FAINT, 0.52F);
            }
            c.getMatrices().popMatrix();
            hit("cos:" + module.id(), cx, cy, cardW - 46, cardH, button -> {
                showModule(module, true);
                page = Page.MODULES;
                sound(1.12F);
            });
            toggle(c, "cos-switch:" + module.id(), cx + cardW - 42, cy + 16, module.enabled(), accent(), () -> toggleModule(module));
        }
        c.disableScissor();
        clipTop = Float.NEGATIVE_INFINITY;
        clipBottom = Float.POSITIVE_INFINITY;
        if (cosmeticsMax > 0) scrollbar(c, gx + gw + 3, gridTop, gridBottom, scrolled, cosmeticsMax, rowsTotal * (cardH + gap));
        hoveredCosmetic = hoveredNow != null && !hoveredNow.enabled() ? hoveredNow : null;
        KhanhFeatures.previewModule = hoveredCosmetic == null ? null : hoveredCosmetic.id();
    }

    private Module hoveredCosmetic;

    /** Keeps the real skin widget lined up with its card, in screen pixels, only on the Cosmetics page. */
    private void positionCosmeticSkin() {
        if (cosmeticSkin == null) return;
        boolean show = cosmeticPreviewShown && page == Page.COSMETICS && !closing && windowIn.value() > 0.95F;
        cosmeticPreviewShown = false;
        livePreview = show && client != null && client.player != null;
        if (!show) {
            KhanhFeatures.previewModule = null;
            hoveredCosmetic = null;
        }
        // In a world your real player is drawn instead (see drawLivePlayer), cosmetics and all.
        cosmeticSkin.visible = show && !livePreview;
        if (!show) return;
        float scale = uiScale();
        int sx = Math.round((cosmeticPreviewX - VW / 2.0F) * scale + width / 2.0F);
        int sy = Math.round((cosmeticPreviewY - VH / 2.0F) * scale + height / 2.0F);
        cosmeticSkin.setPosition(sx, sy);
        cosmeticSkin.setWidth(Math.max(20, Math.round(cosmeticPreviewW * scale)));
        cosmeticSkin.setHeight(Math.max(30, Math.round(cosmeticPreviewH * scale)));
    }

    private boolean livePreview;

    private float wardrobeYaw, wardrobeSpin;
    private boolean wardrobeDragging, wardrobeMouseWasDown;
    private double wardrobeLastX;
    private long wardrobeFrameNanos, wardrobeIdleSince;

    /**
     * Your actual player in the wardrobe, wearing every cosmetic (and the one you hover). Drag it
     * with the mouse to spin it all the way round; let go and it glides to a stop.
     */
    private void drawLivePlayer(DrawContext c) {
        if (!livePreview || client == null || client.player == null) return;
        float scale = uiScale();
        int x1 = Math.round((cosmeticPreviewX - VW / 2.0F) * scale + width / 2.0F);
        int y1 = Math.round((cosmeticPreviewY - VH / 2.0F) * scale + height / 2.0F);
        int x2 = x1 + Math.round(cosmeticPreviewW * scale), y2 = y1 + Math.round(cosmeticPreviewH * scale);

        long now = System.nanoTime();
        float seconds = wardrobeFrameNanos == 0L ? 0.0F : Math.min(0.1F, (now - wardrobeFrameNanos) / 1.0E9F);
        wardrobeFrameNanos = now;
        double mx = client.mouse.getX() * width / Math.max(1, client.getWindow().getWidth());
        double my = client.mouse.getY() * height / Math.max(1, client.getWindow().getHeight());
        boolean down = org.lwjgl.glfw.GLFW.glfwGetMouseButton(client.getWindow().getHandle(), org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT)
            == org.lwjgl.glfw.GLFW.GLFW_PRESS;
        if (down && !wardrobeMouseWasDown && mx >= x1 && mx <= x2 && my >= y1 && my <= y2) {
            wardrobeDragging = true;
            wardrobeLastX = mx;
            wardrobeSpin = 0.0F;
        }
        if (!down) wardrobeDragging = false;
        wardrobeMouseWasDown = down;
        if (wardrobeDragging) {
            float step = (float) (mx - wardrobeLastX) * 1.6F;
            wardrobeLastX = mx;
            wardrobeYaw += step;
            // Remember how fast you were spinning it, for the glide after you let go.
            if (seconds > 0.0F) wardrobeSpin = wardrobeSpin * 0.6F + (step / seconds) * 0.4F;
            wardrobeIdleSince = now;
        } else {
            wardrobeYaw += wardrobeSpin * seconds;
            wardrobeSpin *= (float) Math.exp(-3.2 * seconds);
            if (Math.abs(wardrobeSpin) < 2.0F) wardrobeSpin = 0.0F;
            // Left alone for a few seconds, it slowly turns on its own.
            if (wardrobeSpin == 0.0F && (now - wardrobeIdleSince) / 1.0E9 > 3.0) wardrobeYaw += 18.0F * seconds;
        }
        wardrobeYaw %= 360.0F;

        var state = dev.khanh.mixin.InventoryScreenStateInvoker.khanh$renderState(client.player);
        if (state instanceof net.minecraft.client.render.entity.state.LivingEntityRenderState living) {
            living.bodyYaw = 180.0F + wardrobeYaw;
            living.relativeHeadYaw = 0.0F;
            living.pitch = 0.0F;
            living.width /= living.baseScale;
            living.height /= living.baseScale;
            living.baseScale = 1.0F;
        }
        float size = Math.max(16.0F, (y2 - y1) * 0.36F);
        org.joml.Quaternionf rotation = new org.joml.Quaternionf().rotateZ((float) Math.PI);
        org.joml.Quaternionf camera = new org.joml.Quaternionf().rotateX((float) Math.toRadians(-6.0));
        rotation.mul(camera);
        org.joml.Vector3f offset = new org.joml.Vector3f(0.0F, state.height / 2.0F + 0.0625F, 0.0F);
        c.createNewRootLayer();
        c.addEntity(state, size, offset, rotation, camera, x1, y1, x2, y2);
    }

    // ---------------------------------------------------------------- what's new

    private static final String[] WHATS_NEW = {
        "Discord Presence works out of the box: no Application ID needed",
        "Finder notifications: sus chunks, spawners, ores... pop up with coords",
        "Server list no longer stuck on Pinging; dead servers say so",
        "Zoom: hold to zoom, scroll to zoom further, smooth glide",
        "Wardrobe: drag your character to spin it, it glides to a stop",
        "Name Tags: health bar on/off + names on every dropped item",
        "Anchor Macro: place an anchor and it charges + blows it up",
        "Skin Protect: type any username to wear their skin",
        "Auto Sprint walks for you; Import configs straight from the clipboard",
        "Client Colours moved to Settings; Fake Pay shows a green $ only",
        "New: Pop Counter, Kill Effects, Armor Alert",
        "Fixed crashes with other mods that hook mouse look, FOV or brightness",
    };

    /** A one-time card the first time this version's GUI opens. */
    private void drawWhatsNew(DrawContext c) {
        String version = KhanhClient.VERSION;
        if (version.equals("dev") || version.equals(GuiState.seenVersion())) return;
        float in = clamp01(spring("whats-new", Spring.PANEL, 0.0F).to(windowIn.value() > 0.8F ? 1.0F : 0.0F).value());
        // The live skin is a real widget drawn above the window; keep it hidden under this card.
        cosmeticPreviewShown = false;
        if (in <= 0.01F) return;
        float previous = opacity;
        opacity *= in;
        SmoothGuiShapes.rect(c, 0, 0, VW, VH, WIN_R, LiquidGlass.alpha(0.55F * opacity) << 24, false);
        int w = 330, h = 74 + WHATS_NEW.length * 16, x = (VW - w) / 2, y = (VH - h) / 2;
        push(c, x + w / 2.0F, y + h / 2.0F, 0.92F + 0.08F * in);
        LiquidGlass.card(c, x, y, w, h, 16, opacity);
        text(c, "What's new", x + 18, y + 16, FG, 1.15F);
        tracked(c, "KHANH CLIENT " + version, x + 18, y + 33, FAINT, 0.5F, 1.1F);
        for (int i = 0; i < WHATS_NEW.length; i++) {
            SmoothGuiShapes.rect(c, x + 20, y + 54 + i * 16, 4, 4, 2, LiquidGlass.alpha(opacity) << 24 | (accent() & 0xFFFFFF), false);
            text(c, trim(WHATS_NEW[i], w - 50, 0.64F), x + 30, y + 52 + i * 16, SOFT, 0.64F);
        }
        String ok = "Click anywhere to continue";
        text(c, ok, x + (w - width(ok, 0.56F)) / 2.0F, y + h - 16, FAINT, 0.56F);
        c.getMatrices().popMatrix();
        opacity = previous;
        hit("whats-new", 0, 0, VW, VH, button -> {
            GuiState.setSeenVersion(version);
            sound(1.2F);
        });
    }

    // ---------------------------------------------------------------- themes page

    private record Theme(String name, String mood, int accent) { }

    /** accent 0 is Khanh Client's own black-and-white. */
    private static final Theme[] THEMES = {
        new Theme("Midnight", "Black glass, white light", 0),
        new Theme("Graphite", "Soft steel greys", 0xA8AAB2),
        new Theme("Violet", "Deep purple glow", 0xA78BFA),
        new Theme("Ocean", "Clear sky blue", 0x38BDF8),
        new Theme("Mint", "Fresh green", 0x34D399),
        new Theme("Amber", "Warm gold", 0xFBBF24),
        new Theme("Rose", "Soft red", 0xFB7185),
        new Theme("Sakura", "Cherry pink", 0xF472B6),
        new Theme("Crimson", "Bold red", 0xEF4444),
        new Theme("Ice", "Frosted pale blue", 0xBAE6FD),
    };

    private void openThemes() {
        page = Page.THEMES;
        closeMenu();
        sound(1.12F);
    }

    /** Big preview cards: each shows a tiny Khanh Client window in its colour; click to apply. */
    private void drawThemes(DrawContext c) {
        int x = CX, y = LY, w = CW, h = VH - LY - 10;
        LiquidGlass.glass(c, x, y, w, h, 14, 0xFFFFFF, opacity);
        text(c, "Themes", x + 18, y + 16, FG, 1.18F);
        tracked(c, "PICK A LOOK  \u00b7  IT APPLIES INSTANTLY", x + 18, y + 33, FAINT, 0.5F, 1.1F);
        int cols = 3, gap = 12, top = y + 56;
        int rows = (THEMES.length + cols - 1) / cols;
        int cardW = (w - 36 - gap * (cols - 1)) / cols, cardH = Math.min(118, (h - 72 - gap * (rows - 1)) / rows);
        int current = GuiState.accent();
        for (int i = 0; i < THEMES.length; i++) {
            Theme theme = THEMES[i];
            int cx = x + 18 + (i % cols) * (cardW + gap), cy = top + (i / cols) * (cardH + gap);
            int accent = theme.accent() == 0 ? MONO : 0xFF000000 | theme.accent();
            boolean active = current == theme.accent();
            boolean hot = hover(cx, cy, cardW, cardH);
            float in = clamp01(spring("theme-in:" + i, Spring.PANEL, 0.0F).to(1.0F).value());
            float pop = spring("theme-pop:" + i, Spring.BOUNCE, 1.0F).to(hot ? 1.04F : 1.0F).value();
            float previous = opacity;
            opacity *= in;
            push(c, cx + cardW / 2.0F, cy + cardH / 2.0F, pop);
            LiquidGlass.glass(c, cx, cy, cardW, cardH, 12, active ? accent : 0xFFFFFF, opacity);
            if (active) SmoothGuiShapes.rect(c, cx, cy, cardW, cardH, 12, LiquidGlass.alpha(0.55F * opacity) << 24 | (accent & 0xFFFFFF), true);
            // A tiny Khanh Client window: sidebar with a lit pill, two rows, a lit toggle.
            int px = cx + 10, py = cy + 10, pw = cardW - 20, ph = Math.max(24, cardH - 42);
            SmoothGuiShapes.rect(c, px, py, pw, ph, 7, LiquidGlass.alpha(0.55F * opacity) << 24 | 0x0A0A0C, false);
            int sw = Math.max(14, pw / 4);
            SmoothGuiShapes.rect(c, px + 5, py + 5, sw, ph - 10, 4, LiquidGlass.alpha(0.10F * opacity) << 24 | 0xFFFFFF, false);
            SmoothGuiShapes.rect(c, px + 8, py + 9, sw - 6, 5, 2, LiquidGlass.alpha(0.9F * opacity) << 24 | (accent & 0xFFFFFF), false);
            int rx = px + sw + 10, rw = pw - sw - 15;
            for (int r = 0; r < 2; r++) {
                int ry = py + 7 + r * 12;
                if (ry + 10 > py + ph - 3) break;
                SmoothGuiShapes.rect(c, rx, ry, rw, 10, 4, LiquidGlass.alpha(0.08F * opacity) << 24 | 0xFFFFFF, false);
                SmoothGuiShapes.rect(c, rx + 3, ry + 3, 4, 4, 2, LiquidGlass.alpha((r == 0 ? 0.95F : 0.35F) * opacity) << 24 | (accent & 0xFFFFFF), false);
                SmoothGuiShapes.rect(c, rx + rw - 14, ry + 2, 11, 6, 3,
                    LiquidGlass.alpha((r == 0 ? 0.95F : 0.2F) * opacity) << 24 | (r == 0 ? accent & 0xFFFFFF : 0xFFFFFF), false);
            }
            text(c, theme.name(), cx + 12, cy + cardH - 26, FG, 0.82F);
            text(c, trim(theme.mood(), cardW - 70, 0.54F), cx + 12, cy + cardH - 14, FAINT, 0.54F);
            if (active) {
                String badge = "Active";
                float bw = width(badge, 0.54F) + 12;
                SmoothGuiShapes.rect(c, Math.round(cx + cardW - 10 - bw), cy + cardH - 25, Math.round(bw), 13, 6,
                    LiquidGlass.alpha(0.9F * opacity) << 24 | (accent & 0xFFFFFF), false);
                text(c, badge, cx + cardW - 10 - bw + 6, cy + cardH - 21.5F, 0xF0141418, 0.54F);
            }
            c.getMatrices().popMatrix();
            opacity = previous;
            int index = i;
            hit("theme:" + i, cx, cy, cardW, cardH, button -> {
                GuiState.setAccent(theme.accent());
                spring("theme-pop:" + index, Spring.BOUNCE, 1.0F).kick(1.12F);
                sound(1.2F);
            });
        }
    }

    // ---------------------------------------------------------------- configs page

    private void openConfigs() {
        refreshSlots();
        page = Page.CONFIGS;
        closeMenu();
        sound(1.12F);
    }

    private void refreshSlots() {
        for (int i = 0; i < PROFILE_SLOTS; i++) {
            int slot = i + 1;
            boolean exists = false;
            String name = "Profile " + slot;
            try {
                exists = ConfigManager.profileExists(slot);
                if (exists) name = ConfigManager.profileName(slot);
            } catch (RuntimeException ignored) {
                // A damaged profile file still shows as a slot; Load reports the problem.
            }
            slots[i] = new Slot(exists, name);
        }
    }

    private void setStatus(String message) {
        status = message == null ? "" : message;
        String lower = status.toLowerCase(Locale.ROOT);
        statusColor = lower.contains("fail") || lower.contains("could not") || lower.contains("invalid") ? KhanhPalette.BAD
            : lower.startsWith("saved") || lower.startsWith("loaded") || lower.startsWith("copied") || lower.startsWith("imported")
                || lower.contains("removed") ? KhanhPalette.GOOD : KhanhPalette.OKAY;
        statusAt = System.nanoTime();
        spring("status-pop", Spring.BOUNCE, 1.0F).kick(1.4F);
    }

    private void drawConfigs(DrawContext c) {
        int x = CX, y = LY, w = CW, h = VH - LY - 10;
        if (slots[0] == null) refreshSlots();
        LiquidGlass.glass(c, x, y, w, h, 14, 0xFFFFFF, opacity);
        LiquidGlass.glass(c, x + 14, y + 14, 28, 28, 8, MONO, opacity);
        HudSurfaceRenderer.icon(c, HudSurfaceRenderer.HudIcon.DOCUMENT, x + 21, y + 21, 14, LiquidGlass.alpha(opacity) << 24 | 0xFFFFFF);
        text(c, "Configs", x + 50, y + 15, FG, 1.18F);
        tracked(c, PROFILE_SLOTS + " SLOTS  ·  SHARE CODES", x + 50, y + 31, FAINT, 0.5F, 1.1F);
        pillButton(c, "advanced-configs", "Advanced", x + w - 16 - 78, y + 18, 78, 20, 0, button -> {
            if (client != null) {
                save();
                client.setScreen(new KhanhConfigScreen(this));
                sound(1.1F);
            }
        });
        fillF(c, x + 14, y + 52, w - 28, 1, 0x14FFFFFF);

        tracked(c, "NAME FOR NEXT SAVE", x + 16, y + 62, FAINT, 0.52F, 1.2F);
        textField(c, "profile-name", profileName, "Optional, defaults to the slot name", x + 16, y + 72, 280, 22, accent(), false);

        int slotTop = y + 106;
        for (int i = 0; i < PROFILE_SLOTS; i++) drawSlot(c, i, x + 16, slotTop + i * 46, w - 32, 40);

        int shareY = slotTop + PROFILE_SLOTS * 46 + 6;
        tracked(c, "SHARE", x + 16, shareY + 7, FAINT, 0.52F, 1.2F);
        pillButton(c, "copy-code", "Copy my config", x + 60, shareY, 118, 22, accent(), button -> {
            setStatus(ConfigManager.copySixDigitCode(KhanhClient.modules(), this::asyncStatus));
            sound(1.2F);
        });
        textField(c, "import-code", importCode, "Press Import to use your clipboard, or paste a code", x + 188, shareY, w - 188 - 16 - 70, 22, accent(), false);
        pillButton(c, "import", "Import", x + w - 16 - 62, shareY, 62, 22, accent(), button -> importNow());

        if (!status.isEmpty()) {
            float age = (System.nanoTime() - statusAt) / 1_000_000_000.0F;
            float fade = clamp01(1.0F - (age - 6.0F) / 1.5F);
            if (fade > 0.01F) {
                float previous = opacity;
                opacity *= fade;
                float pop = spring("status-pop", Spring.BOUNCE, 1.0F).to(1.0F).value();
                float sy = y + h - 22;
                push(c, x + 20, sy + 4, pop);
                SmoothGuiShapes.rect(c, x + 16, Math.round(sy), 8, 8, 4, LiquidGlass.alpha(opacity) << 24 | (statusColor & 0xFFFFFF), false);
                c.getMatrices().popMatrix();
                text(c, trim(status, w - 50, 0.7F), x + 30, sy + 0.5F, SOFT, 0.7F);
                opacity = previous;
            }
        }
    }

    private void drawSlot(DrawContext c, int index, int x, int y, int w, int h) {
        int slot = index + 1;
        Slot info = slots[index];
        int color = SLOT_HUES[index];
        tile(c, "slot:" + slot, x, y, w, h);
        float pop = spring("slot-pop:" + slot, Spring.BOUNCE, 1.0F).to(1.0F).value();
        push(c, x + 20, y + h / 2.0F, pop);
        LiquidGlass.glass(c, x + 9, y + 9, 22, 22, 11, info.saved() ? color : 0xFFFFFF, opacity);
        String number = Integer.toString(slot);
        text(c, number, x + 20 - width(number, 0.78F) / 2.0F, y + 16.5F, info.saved() ? FG : DIM, 0.78F);
        c.getMatrices().popMatrix();
        text(c, trim(info.saved() ? info.name() : "Empty slot", w - 300, 0.86F), x + 40, y + 10, info.saved() ? FG : DIM, 0.86F);
        text(c, info.saved() ? "Saved" : "Nothing saved here yet", x + 40, y + 23, info.saved() ? color : FAINT, 0.6F);

        int bx = x + w - 10;
        boolean confirming = confirmDelete == slot && System.nanoTime() - confirmAt < 3_000_000_000L;
        if (info.saved()) {
            String deleteLabel = confirming ? "Sure?" : "Delete";
            int dw = Math.round(width(deleteLabel, 0.74F) + 22);
            bx -= dw;
            pillButton(c, "slot-delete:" + slot, deleteLabel, bx, y + 10, dw, 20, confirming ? KhanhPalette.BAD : 0, button -> {
                if (confirmDelete == slot && System.nanoTime() - confirmAt < 3_000_000_000L) {
                    setStatus(ConfigManager.deleteProfile(slot));
                    confirmDelete = -1;
                    refreshSlots();
                    sound(0.8F);
                } else {
                    confirmDelete = slot;
                    confirmAt = System.nanoTime();
                    sound(0.95F);
                }
            });
            bx -= 6;
        }
        int sw = 48;
        bx -= sw;
        pillButton(c, "slot-save:" + slot, "Save", bx, y + 10, sw, 20, 0, button -> {
            String name = !profileName.isBlank() ? profileName : info.saved() ? info.name() : "";
            setStatus(ConfigManager.saveProfile(KhanhClient.modules(), slot, name));
            profileName = "";
            refreshSlots();
            spring("slot-pop:" + slot, Spring.BOUNCE, 1.0F).kick(1.35F);
            sound(1.25F);
        });
        if (info.saved()) {
            int lw = 48;
            bx -= 6 + lw;
            pillButton(c, "slot-load:" + slot, "Load", bx, y + 10, lw, 20, accent(), button -> {
                setStatus(ConfigManager.loadProfile(KhanhClient.modules(), slot));
                choiceCache.clear();
                spring("slot-pop:" + slot, Spring.BOUNCE, 1.0F).kick(1.35F);
                sound(1.32F);
            });
        }
    }

    private void textField(DrawContext c, String id, String value, String placeholder, int x, int y, int w, int h, int color,
                           boolean withIcon) {
        boolean active = id.equals("search") ? searchFocused : id.equals(field);
        float s = pressScale("field:" + id, x, y, w, h, 1.02F);
        push(c, x + w / 2.0F, y + h / 2.0F, s);
        LiquidGlass.glass(c, x, y, w, h, h / 2, active ? color : 0xFFFFFF, opacity);
        int textX = x + 11;
        if (withIcon) {
            int iconAlpha = LiquidGlass.alpha(opacity * (active ? 0.95F : 0.55F));
            if (iconAlpha > 4) HudSurfaceRenderer.icon(c, HudSurfaceRenderer.HudIcon.SEARCH, x + 9, y + (h - 11) / 2, 11,
                iconAlpha << 24 | 0xF2F4F8);
            textX = x + 25;
        }
        boolean empty = value.isEmpty();
        float room = x + w - textX - 8;
        String shown = empty ? trim(placeholder, room, 0.7F) : tail(value, room, 0.7F);
        text(c, shown, textX, y + (h - 9 * 0.7F) / 2.0F + 0.5F, empty ? FAINT : FG, 0.7F);
        if (active && caretOn()) fillF(c, textX + (empty ? 0 : width(shown, 0.7F)) + 1, y + 6, 1.0F, h - 12, color);
        c.getMatrices().popMatrix();
        hit("field:" + id, x, y, w, h, button -> {
            if (id.equals("search")) {
                if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) setSearch("");
                searchFocused = true;
                if (page != Page.MODULES) page = Page.MODULES;
            } else {
                if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                    if (id.equals("profile-name")) profileName = "";
                    else importCode = "";
                }
                field = id;
            }
            sound(1.08F);
        });
    }

    private void importNow() {
        String code = importCode.strip();
        // Nothing typed: take the code straight from the clipboard (what a friend's Copy put there).
        if (code.isEmpty() && client != null) code = client.keyboard.getClipboard().replace('\n', ' ').replace('\r', ' ').strip();
        if (code.isEmpty()) {
            setStatus("Copy a friend's config code first, then press Import");
            return;
        }
        setStatus(ConfigManager.importShareCodeAsync(KhanhClient.modules(), code, this::asyncStatus));
        importCode = "";
        field = null;
        choiceCache.clear();
        sound(1.2F);
    }

    /** Share-code results can arrive on a network thread; show them on the render thread. */
    private void asyncStatus(String message) {
        if (client != null) client.execute(() -> setStatus(message));
    }

    // ---------------------------------------------------------------- dropdown menus

    private void openViewMenu(float x, float y, float w, float h) {
        List<MenuItem> items = new ArrayList<>();
        String[] meta = {"Every module in the category", "Only modules that are on", "Only modules with a key"};
        for (View option : View.values()) {
            items.add(new MenuItem(option.label, meta[option.ordinal()], option == view, () -> {
                view = option;
                lastView = option;
            }));
        }
        openMenu("view", "Show", items, x, y, w, h, 186);
    }

    private void openChoiceMenu(String id, ModuleOption option, float x, float y, float w, float h) {
        List<MenuItem> items = new ArrayList<>();
        String current = option.choiceValue();
        for (String choice : choices(option)) {
            items.add(new MenuItem(choice, null, choice.equals(current), () -> {
                option.setChoice(choice);
                save();
            }));
        }
        openMenu(id, option.label(), items, x, y, w, h, Math.max(w, 150));
    }

    /** ModuleOption exposes only the current choice, so walk the list once with cycle() and cache it. */
    private List<String> choices(ModuleOption option) {
        List<String> cached = choiceCache.get(option);
        if (cached != null) return cached;
        List<String> values = new ArrayList<>();
        String start = option.choiceValue();
        values.add(start);
        for (int guard = 0; guard < 256; guard++) {
            option.cycle(1);
            String next = option.choiceValue();
            if (next.equals(start)) break;
            values.add(next);
        }
        option.setChoice(start);
        List<String> result = List.copyOf(values);
        choiceCache.put(option, result);
        return result;
    }

    private void openMenu(String id, String heading, List<MenuItem> items, float x, float y, float w, float h, float width) {
        if (menu != null && menu.id.equals(id)) {
            closeMenu();
            sound(0.88F);
            return;
        }
        closeMenu();
        menu = new Menu(id, heading, items, x, y, w, h, width);
        sound(1.08F);
    }

    private void closeMenu() {
        if (menu == null) return;
        menu.close();
        closingMenu = menu;
        menu = null;
    }

    /** Returns false once a closing menu has finished its exit spring. */
    private boolean drawMenu(DrawContext c, Menu m) {
        float p = m.progress.value();
        if (m.closing && p < 0.02F) return false;
        float left = m.left(), top = m.top(), w = m.width, h = m.height();
        int rowH = m.rowHeight();
        int visible = m.visibleRows();
        m.scroll = Math.max(0, Math.min(m.scroll, m.items.size() - visible));
        float previous = opacity;
        opacity = clamp01(p) * clamp01(windowIn.value());
        Matrix3x2fStack matrices = c.getMatrices();
        matrices.pushMatrix();
        float originX = Math.max(left + 12, Math.min(m.anchorX + m.anchorW / 2.0F, left + w - 12));
        matrices.translate(0.0F, (1.0F - p) * -7.0F);
        matrices.translate(originX, top);
        float s = 0.92F + 0.08F * p;
        matrices.scale(s, s);
        matrices.translate(-originX, -top);

        int ix = Math.round(left), iy = Math.round(top), iw = Math.round(w), ih = Math.round(h);
        LiquidGlass.shadow(c, ix, iy, iw, ih, 12, opacity);
        LiquidGlass.frost(c, ix, iy, iw, ih, 12, opacity);
        LiquidGlass.glass(c, ix, iy, iw, ih, 12, 0xFFFFFF, opacity);
        tracked(c, m.heading.toUpperCase(Locale.ROOT), left + 12, top + 10, FAINT, 0.56F, 1.2F);
        String total = m.items.size() + " total";
        text(c, total, left + w - 12 - width(total, 0.56F), top + 10.5F, FAINT, 0.56F);

        long since = System.nanoTime() - m.opened;
        for (int i = 0; i < visible && i + m.scroll < m.items.size(); i++) {
            int index = i + m.scroll;
            MenuItem item = m.items.get(index);
            Spring row = m.rows.get(index);
            if (!m.closing && since >= i * 30_000_000L) row.to(1.0F);
            float q = row.value();
            float rowY = top + 24 + i * rowH + (1.0F - q) * -8.0F;
            float rowPrevious = opacity;
            opacity = rowPrevious * clamp01(q);
            boolean hot = !m.closing && hover(left + 5, top + 24 + i * rowH, w - 10, rowH);
            float glow = spring("menu-row:" + m.id + ":" + index, Spring.SMOOTH, 0.0F).to(hot ? 1.0F : 0.0F).value();
            if (glow > 0.01F) SmoothGuiShapes.rect(c, Math.round(left + 5), Math.round(rowY), Math.round(w - 10), rowH - 2, 9,
                LiquidGlass.alpha(0.07F * clamp01(glow) * opacity) << 24 | 0xFFFFFF, false);
            boolean hasMeta = item.meta() != null;
            text(c, trim(item.label(), w - 50, 0.84F), left + 13, rowY + (hasMeta ? 6 : 7), item.selected() ? accent() : FG, 0.84F);
            if (hasMeta) text(c, trim(item.meta(), w - 50, 0.58F), left + 13, rowY + 17, FAINT, 0.58F);
            if (item.selected()) {
                float chip = spring("menu-check:" + m.id, Spring.BOUNCE, 0.6F).to(1.0F).value();
                float cx = left + w - 21, cy = rowY + (rowH - 2) / 2.0F;
                push(c, cx, cy, chip);
                LiquidGlass.glass(c, Math.round(cx - 8), Math.round(cy - 8), 16, 16, 5, accent(), opacity);
                check(c, cx, cy, FG);
                c.getMatrices().popMatrix();
            }
            opacity = rowPrevious;
            if (!m.closing) {
                hit("menu-item:" + index, left + 5, top + 24 + i * rowH, w - 10, rowH, button -> {
                    item.pick().run();
                    closeMenu();
                    sound(1.16F);
                });
            }
        }
        if (m.items.size() > visible) {
            float trackH = visible * rowH - 4;
            float thumb = Math.max(14, trackH * visible / m.items.size());
            float thumbY = top + 26 + (trackH - thumb) * m.scroll / Math.max(1, m.items.size() - visible);
            SmoothGuiShapes.rect(c, Math.round(left + w - 5), Math.round(thumbY), 2, Math.round(thumb), 1,
                LiquidGlass.alpha(0.35F * opacity) << 24 | 0xFFFFFF, false);
        }
        matrices.popMatrix();
        opacity = previous;
        return true;
    }

    // ---------------------------------------------------------------- widgets

    private void toggle(DrawContext c, String id, int x, int y, boolean on, int color, Runnable flip) {
        Boolean last = toggleStates.put(id, on);
        Spring wobble = spring(id + ":wobble", Spring.WOBBLE, 0.0F);
        Spring squash = spring(id + ":squash", Spring.BOUNCE, 1.0F);
        if (last != null && last != on) {
            wobble.kick(on ? 7.0F : -7.0F);
            squash.kick(1.18F);
        }
        float knob = spring(id + ":knob", Spring.PANEL, on ? 1.0F : 0.0F).to(on ? 1.0F : 0.0F).value();
        float fill = spring(id + ":fill", Spring.SMOOTH, on ? 1.0F : 0.0F).to(on ? 1.0F : 0.0F).value();
        float s = pressScale(id, x - 4, y - 4, 40, 26, 1.06F) * squash.to(1.0F).value();
        Matrix3x2fStack m = c.getMatrices();
        m.pushMatrix();
        m.translate(x + 16.0F, y + 9.0F);
        m.scale(s, s);
        m.rotate((float) Math.toRadians(wobble.value()));
        m.translate(-x - 16.0F, -y - 9.0F);
        LiquidGlass.track(c, x, y, 32, 18, 9, opacity);
        LiquidGlass.accent(c, x, y, 32, 18, 9, color, opacity * clamp01(fill));
        m.pushMatrix();
        m.translate(knob * 14.0F, 0.0F);
        int knobColor = light(color) ? KhanhPalette.mix(0xFFFFFFFF, 0xFF141418, clamp01(fill)) & 0xFFFFFF : 0xFFFFFF;
        LiquidGlass.knob(c, x + 2, y + 2, 14, knobColor, opacity);
        m.popMatrix();
        m.popMatrix();
        hit(id, x - 4, y - 4, 40, 26, button -> flip.run());
    }

    /** A glass pill button; tint 0 means plain glass. */
    private void pillButton(DrawContext c, String id, String label, int x, int y, int w, int h, int tint,
                            IntConsumer action) {
        float s = pressScale(id, x, y, w, h, 1.06F) * spring("action-pop:" + id, Spring.BOUNCE, 1.0F).to(1.0F).value();
        push(c, x + w / 2.0F, y + h / 2.0F, s);
        LiquidGlass.glass(c, x, y, w, h, h / 2, tint != 0 ? tint : 0xFFFFFF, opacity);
        if (!label.isEmpty()) {
            float scale = h <= 16 ? 0.7F : 0.74F;
            text(c, label, x + (w - width(label, scale)) / 2.0F, y + (h - 9 * scale) / 2.0F + 0.5F,
                tint != 0 ? FG : SOFT, scale);
        }
        c.getMatrices().popMatrix();
        hit(id, x, y, w, h, action);
    }

    private void dropdownTrigger(DrawContext c, String id, int x, int y, int w, int h, String value, Runnable open) {
        boolean isOpen = menu != null && menu.id.equals(id);
        float s = pressScale(id, x, y, w, h, 1.05F);
        push(c, x + w / 2.0F, y + h / 2.0F, s);
        LiquidGlass.glass(c, x, y, w, h, h / 2, isOpen ? accent() : 0xFFFFFF, opacity);
        text(c, value, x + 10, y + (h - 9 * 0.72F) / 2.0F + 0.5F, FG, 0.72F);
        float angle = spring(id + ":chevron", Spring.PANEL, 0.0F).to(isOpen ? 180.0F : 0.0F).value();
        chevron(c, x + w - 11, y + h / 2.0F, angle, DIM);
        c.getMatrices().popMatrix();
        hit(id, x, y, w, h, button -> open.run());
    }

    private void keyChip(DrawContext c, String key, float x, float y, int tint) {
        float w = width(key, 0.56F) + 10;
        LiquidGlass.glass(c, Math.round(x), Math.round(y), Math.round(w), 12, 5, tint != 0 ? tint : 0xFFFFFF, opacity * 0.85F);
        text(c, key, x + 5, y + 3.4F, tint != 0 ? FG : DIM, 0.56F);
    }

    private void scrollbar(DrawContext c, int x, int top, int bottom, float scroll, float max, float total) {
        if (max <= 0) return;
        float viewport = bottom - top;
        float thumb = Math.max(18, viewport * viewport / total);
        float y = top + (viewport - thumb) * clamp01(scroll / max);
        SmoothGuiShapes.rect(c, x, Math.round(y), 2, Math.round(thumb), 1,
            LiquidGlass.alpha(0.3F * opacity) << 24 | 0xFFFFFF, false);
    }

    private void chevron(DrawContext c, float cx, float cy, float angle, int color) {
        Matrix3x2fStack m = c.getMatrices();
        m.pushMatrix();
        m.translate(cx, cy);
        m.rotate((float) Math.toRadians(angle));
        line(c, -3.2F, -1.4F, 0.0F, 1.7F, 1.2F, color);
        line(c, 0.0F, 1.7F, 3.2F, -1.4F, 1.2F, color);
        m.popMatrix();
    }

    private void check(DrawContext c, float cx, float cy, int color) {
        line(c, cx - 3.4F, cy + 0.2F, cx - 1.0F, cy + 2.6F, 1.5F, color);
        line(c, cx - 1.0F, cy + 2.6F, cx + 3.6F, cy - 2.6F, 1.5F, color);
    }

    private void line(DrawContext c, float x1, float y1, float x2, float y2, float thickness, int color) {
        int argb = alphaColor(color);
        if ((argb >>> 24) < 3) return;
        Matrix3x2fStack m = c.getMatrices();
        m.pushMatrix();
        m.translate(x1, y1);
        m.rotate((float) Math.atan2(y2 - y1, x2 - x1));
        m.scale(0.125F, 0.125F);
        int length = Math.round((float) Math.hypot(x2 - x1, y2 - y1) * 8.0F);
        int half = Math.max(1, Math.round(thickness * 4.0F));
        c.fill(-half / 2, -half, length + half / 2, half, argb);
        m.popMatrix();
    }

    // ---------------------------------------------------------------- text

    private static TextRenderer tr() {
        return KhanhClickGuiScreen.textRenderer();
    }

    private void text(DrawContext c, String value, float x, float y, int color, float scale) {
        int argb = alphaColor(color);
        if ((argb >>> 24) < 5 || value.isEmpty()) return;
        Matrix3x2fStack m = c.getMatrices();
        m.pushMatrix();
        m.translate(x, y);
        m.scale(scale, scale);
        c.drawText(tr(), KhanhClickGuiScreen.fontText(value), 0, 0, argb, false);
        m.popMatrix();
    }

    private void tracked(DrawContext c, String value, float x, float y, int color, float scale, float tracking) {
        float cursor = x;
        for (int i = 0; i < value.length(); ) {
            int codePoint = value.codePointAt(i);
            String glyph = new String(Character.toChars(codePoint));
            text(c, glyph, cursor, y, color, scale);
            cursor += width(glyph, scale) + tracking;
            i += Character.charCount(codePoint);
        }
    }

    private static float width(String value, float scale) {
        return tr().getWidth(KhanhClickGuiScreen.fontText(value)) * scale;
    }

    private static final Map<String, String> TRIM_CACHE = new java.util.LinkedHashMap<>(256, 0.75F, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, String> eldest) { return size() > 1024; }
    };

    private static String trim(String value, float max, float scale) {
        String key = Math.round(max) + "|" + scale + "|" + value;
        String cached = TRIM_CACHE.get(key);
        if (cached != null) return cached;
        String result = trimUncached(value, max, scale);
        TRIM_CACHE.put(key, result);
        return result;
    }

    private static String trimUncached(String value, float max, float scale) {
        if (width(value, scale) <= max) return value;
        String result = value;
        while (!result.isEmpty() && width(result + "…", scale) > max) {
            result = result.substring(0, result.offsetByCodePoints(result.length(), -1));
        }
        return result.isEmpty() ? "" : result.stripTrailing() + "…";
    }

    /** Keeps the end of a value visible while typing. */
    private static String tail(String value, float max, float scale) {
        if (width(value, scale) <= max) return value;
        String result = value;
        while (!result.isEmpty() && width("…" + result, scale) > max) {
            result = result.substring(result.offsetByCodePoints(0, 1));
        }
        return "…" + result;
    }

    private static List<String> wrap(String value, float max, float scale, int maxLines) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : value.split(" ")) {
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (width(candidate, scale) <= max || line.isEmpty()) {
                line.setLength(0);
                line.append(candidate);
            } else {
                lines.add(line.toString());
                line.setLength(0);
                line.append(word);
            }
        }
        if (!line.isEmpty()) lines.add(line.toString());
        if (lines.size() > maxLines) {
            List<String> kept = new ArrayList<>(lines.subList(0, maxLines));
            String rest = String.join(" ", lines.subList(maxLines - 1, lines.size()));
            kept.set(maxLines - 1, trim(rest, max, scale));
            return kept;
        }
        return lines;
    }

    private static String title(String value) {
        String lower = value.toLowerCase(Locale.ROOT);
        return lower.isEmpty() ? lower : Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    private static String keyLabel(int key) {
        return KhanhClickGuiScreen.keyName(key).toUpperCase(Locale.ROOT);
    }

    // ---------------------------------------------------------------- state helpers

    /** Modules for the current view: a category, favourites, or a search across names,
     * descriptions and setting labels. */
    private List<Module> modules() {
        String query = search.toLowerCase(Locale.ROOT).strip();
        java.util.stream.Stream<Module> source;
        if (query.isEmpty() && recentView) {
            source = GuiState.recent().stream().map(id -> KhanhClient.modules().byId(id)).filter(java.util.Objects::nonNull);
        } else {
            source = KhanhClient.modules().all().stream()
                .filter(m -> !query.isEmpty() || (favourites ? GuiState.favourite(m.id())
                    : m.category() == category && !GuiState.SETTINGS_ONLY.contains(m.id())));
        }
        var filtered = source
            .filter(m -> view != View.ACTIVE || m.enabled())
            .filter(m -> view != View.BOUND || m.keybind() >= 0)
            .filter(m -> query.isEmpty() || GuiState.searchRank(m, query) >= 0);
        if (query.isEmpty()) return filtered.toList();
        return filtered.sorted(java.util.Comparator.<Module>comparingInt(m -> GuiState.searchRank(m, query))
            .thenComparing(m -> m.name().toLowerCase(Locale.ROOT))).toList();
    }

    private Map<Category, long[]> counts() {
        Map<Category, long[]> counts = new EnumMap<>(Category.class);
        for (Category option : Category.values()) counts.put(option, new long[2]);
        for (Module module : KhanhClient.modules().all()) {
            if (GuiState.SETTINGS_ONLY.contains(module.id())) continue;
            long[] count = counts.get(module.category());
            count[0]++;
            if (module.enabled()) count[1]++;
        }
        return counts;
    }

    private void setCategory(Category option) {
        boolean changed = option != category || favourites || recentView || !search.isEmpty() || page != Page.MODULES;
        category = option;
        lastCategory = option;
        favourites = false;
        recentView = false;
        search = "";
        searchFocused = false;
        page = Page.MODULES;
        selected = null;
        closeMenu();
        if (changed) {
            spring("head-pop", Spring.BOUNCE, 1.0F).kick(1.3F);
            sound(1.1F);
        }
    }

    private void setSearch(String value) {
        search = value;
        page = Page.MODULES;
    }

    private void showModule(Module module, boolean user) {
        if (module == selected) return;
        selected = module;
        shownModule = module;
        colorOpen = null;
        editing = null;
        detailTarget = 0;
        detailScroll.snap(0.0F);
        detailIn.snap(0.0F);
        detailIn.to(1.0F);
        spring("detail-tile", Spring.BOUNCE, 1.0F).kick(0.8F);
        if (user) {
            GuiState.used(module.id());
            sound(1.12F);
        }
    }

    private void toggleModule(Module module) {
        if (module.id().equals("config")) {
            openConfigs();
            return;
        }
        module.toggle();
        save();
        GuiState.used(module.id());
        spring("tile:" + module.id(), Spring.BOUNCE, 1.0F).kick(1.3F);
        spring("icon-wob:" + module.id(), Spring.WOBBLE, 0.0F).kick(module.enabled() ? 14.0F : -10.0F);
        KhanhSounds.toggle(client, module.enabled());
    }

    private void updateColor(double mouseX) {
        if (colorSliding == null) return;
        float t = clamp01((float) ((mouseX - dragLeft) / Math.max(1.0F, dragWidth - 1)));
        float[] hsv = hsv(colorSliding.colorValue());
        hsv[colorChannel] = t;
        if (colorChannel != 2 && hsv[2] < 0.05F) hsv[2] = 1.0F;
        colorSliding.setColor(java.awt.Color.HSBtoRGB(hsv[0], hsv[1], hsv[2]));
        dirty = true;
    }

    private static float[] hsv(int color) {
        return java.awt.Color.RGBtoHSB(color >> 16 & 255, color >> 8 & 255, color & 255, null);
    }

    private Spring spring(String id, float[] config, float initial) {
        return springs.computeIfAbsent(id, key -> new Spring(config, initial));
    }

    private float pressScale(String id, float x, float y, float w, float h, float hoverScale) {
        boolean hot = hover(x, y, w, h);
        float target = hot && id.equals(pressedId) ? 0.92F : hot ? hoverScale : 1.0F;
        return spring(id + ":press", Spring.BOUNCE, 1.0F).to(target).value();
    }

    private boolean hover(float x, float y, float w, float h) {
        if (closing || inert) return false;
        if (!drawingMenu && menu != null && !menu.closing) return false;
        if (my < clipTop || my >= clipBottom) return false;
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private void hit(String id, float x, float y, float w, float h, IntConsumer action) {
        float top = Math.max(y, clipTop), bottom = Math.min(y + h, clipBottom);
        if (bottom <= top || inert) return;
        (drawingMenu ? menuHits : hits).add(new Hit(id, x, top, w, bottom - top, action));
    }

    private void push(DrawContext c, float cx, float cy, float scale) {
        Matrix3x2fStack m = c.getMatrices();
        m.pushMatrix();
        m.translate(cx, cy);
        m.scale(scale, scale);
        m.translate(-cx, -cy);
    }

    private void fillF(DrawContext c, float x, float y, float w, float h, int color) {
        int argb = alphaColor(color);
        if ((argb >>> 24) < 2) return;
        Matrix3x2fStack m = c.getMatrices();
        m.pushMatrix();
        m.translate(x, y);
        m.scale(0.25F, 0.25F);
        c.fill(0, 0, Math.max(1, Math.round(w * 4)), Math.max(1, Math.round(h * 4)), argb);
        m.popMatrix();
    }

    private int alphaColor(int color) {
        int alpha = Math.round((color >>> 24) * clamp01(opacity));
        return alpha << 24 | (color & 0xFFFFFF);
    }

    private static float clamp01(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }

    private static boolean caretOn() {
        return System.currentTimeMillis() / 480L % 2L == 0L;
    }

    /** The interface accent picked in Settings, or Khanh Client's black-and-white default. */
    private static int accent() {
        if (dev.khanh.feature.KhanhFeatures.enabled("client_colors")
            && dev.khanh.feature.KhanhFeatures.bool("client_colors", "gui_override", false))
            return 0xFF000000 | dev.khanh.feature.KhanhFeatures.color("client_colors", "gui_color", 0xFF9D4EFF);
        int custom = GuiState.accent();
        return custom != 0 ? 0xFF000000 | custom : MONO;
    }

    private static boolean light(int color) {
        return ((color >> 16 & 255) * 3 + (color >> 8 & 255) * 6 + (color & 255)) / 10 > 190;
    }

    private void save() {
        ConfigManager.save(KhanhClient.modules());
        dirty = false;
    }

    private void sound(float pitch) {
        KhanhSounds.playInterface(client, pitch);
    }

    private float uiScale() {
        float fitted = Math.min(width / (float) VW, height / (float) VH);
        float zoom = (float) KhanhFeatures.decimal("visuals", "gui_zoom", 0.55);
        float factor = Math.max(0.5F, Math.min(1.0F, 0.86F * zoom / 0.55F));
        return Math.max(0.3F, fitted * factor);
    }

    private double toVirtualX(double x) {
        return (x - width / 2.0) / uiScale() + VW / 2.0;
    }

    private double toVirtualY(double y) {
        return (y - height / 2.0) / uiScale() + VH / 2.0;
    }

    // ---------------------------------------------------------------- input

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (closing) return true;
        double x = toVirtualX(click.x()), y = toVirtualY(click.y());
        mx = x;
        my = y;
        int button = click.button();
        bindingModule = null;
        bindingAction = null;
        if (menu != null && !menu.closing) {
            for (int i = menuHits.size() - 1; i >= 0; i--) {
                Hit hit = menuHits.get(i);
                if (hit.contains(x, y)) {
                    pressedId = hit.id();
                    hit.action().accept(button);
                    return true;
                }
            }
            if (!menu.contains(x, y)) {
                closeMenu();
                sound(0.88F);
            }
            return true;
        }
        boolean wasEditing = editing != null;
        searchFocused = false;
        editing = null;
        field = null;
        if (wasEditing) save();
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit hit = hits.get(i);
            if (hit.contains(x, y)) {
                pressedId = hit.id();
                hit.action().accept(button);
                return true;
            }
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseDragged(Click click, double deltaX, double deltaY) {
        double x = toVirtualX(click.x());
        if (sliding != null) {
            sliding.setFromRatio((x - dragLeft) / Math.max(1.0F, dragWidth));
            dirty = true;
            return true;
        }
        if (colorSliding != null) {
            updateColor(x);
            return true;
        }
        return super.mouseDragged(click, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(Click click) {
        pressedId = null;
        boolean wasDragging = sliding != null || colorSliding != null;
        sliding = null;
        colorSliding = null;
        if (wasDragging || dirty) save();
        return wasDragging || super.mouseReleased(click);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        double x = toVirtualX(mouseX), y = toVirtualY(mouseY);
        if (menu != null && !menu.closing) {
            if (menu.contains(x, y)) {
                menu.scroll = Math.max(0, Math.min(menu.scroll - (int) Math.signum(verticalAmount),
                    menu.items.size() - menu.visibleRows()));
            }
            return true;
        }
        float amount = (float) verticalAmount * 44.0F;
        if (page == Page.MODULES && x >= LX && x < LX + LW && y >= LIST_TOP && y < LIST_BOTTOM) {
            listTarget = Math.max(0, Math.min(listMax, listTarget - amount));
            return true;
        }
        if (page == Page.SETTINGS && x >= CX && x < CX + CW) {
            settingsTarget = Math.max(0, Math.min(settingsMax, settingsTarget - amount));
            return true;
        }
        if (page == Page.COSMETICS && x >= CX && x < CX + CW) {
            cosmeticsTarget = Math.max(0, Math.min(cosmeticsMax, cosmeticsTarget - amount));
            return true;
        }
        if (page == Page.MODULES && x >= DX && x < DX + DW && y >= DETAIL_TOP && y < DETAIL_BOTTOM) {
            detailTarget = Math.max(0, Math.min(detailMax, detailTarget - amount));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        int key = input.key();
        boolean paste = key == GLFW.GLFW_KEY_V && (input.modifiers() & (GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_SUPER)) != 0;
        swallowChar = false;
        if (bindingModule != null || bindingAction != null) {
            if (!input.isEscape() && key != GLFW.GLFW_KEY_RIGHT_SHIFT) {
                int bound = key == GLFW.GLFW_KEY_DELETE || key == GLFW.GLFW_KEY_BACKSPACE ? -1 : key;
                if (bindingModule != null) bindingModule.setKeybind(bound);
                else bindingAction.setActionKeybind(bound);
                save();
                sound(1.2F);
            }
            bindingModule = null;
            bindingAction = null;
            // Otherwise binding "R" would also type "r" into search and jump the list away.
            swallowChar = true;
            return true;
        }
        if (field != null) {
            String value = field.equals("profile-name") ? profileName : importCode;
            if (key == GLFW.GLFW_KEY_BACKSPACE && !value.isEmpty()) {
                value = value.substring(0, value.offsetByCodePoints(value.length(), -1));
            } else if (paste && client != null) {
                value = value + client.keyboard.getClipboard().replace('\n', ' ').replace('\r', ' ').strip();
            } else if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                if (field.equals("import-code")) {
                    importNow();
                    return true;
                }
                field = null;
                return true;
            } else if (input.isEscape()) {
                field = null;
                return true;
            }
            int limit = field.equals("profile-name") ? 24 : 8192;
            if (value.length() > limit) value = value.substring(0, limit);
            if (field.equals("profile-name")) profileName = value; else importCode = value;
            return true;
        }
        if (editing != null) {
            if (key == GLFW.GLFW_KEY_BACKSPACE) {
                editing.deleteLastTextCharacter();
            } else if (input.isEscape() || key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                editing = null;
                save();
                sound(1.1F);
            } else if (paste && client != null) {
                editing.appendText(client.keyboard.getClipboard());
            }
            return true;
        }
        if (searchFocused) {
            if (key == GLFW.GLFW_KEY_BACKSPACE && !search.isEmpty()) {
                setSearch(search.substring(0, search.offsetByCodePoints(search.length(), -1)));
                return true;
            }
            if (input.isEscape() || key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                if (input.isEscape() && !search.isEmpty()) setSearch("");
                searchFocused = false;
                return true;
            }
        }
        if (input.isEscape()) {
            if (menu != null) {
                closeMenu();
                sound(0.88F);
            } else if (colorOpen != null) {
                colorOpen = null;
            } else if (page != Page.MODULES) {
                page = Page.MODULES;
                sound(0.9F);
            } else {
                close();
            }
            return true;
        }
        if (key == GLFW.GLFW_KEY_F && (input.modifiers() & (GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_SUPER)) != 0) {
            searchFocused = true;
            page = Page.MODULES;
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public boolean charTyped(CharInput input) {
        if (!input.isValidChar()) return super.charTyped(input);
        if (field != null) {
            if (field.equals("profile-name")) {
                if (profileName.length() < 24) profileName += input.asString();
            } else if (importCode.length() < 8192) {
                importCode += input.asString();
            }
            return true;
        }
        if (editing != null) {
            editing.appendText(input.asString());
            return true;
        }
        if (swallowChar) {
            swallowChar = false;
            return true;
        }
        if (bindingModule != null || bindingAction != null || menu != null) return true;
        if (page == Page.CONFIGS && !searchFocused) return true;
        if (!searchFocused && input.asString().isBlank()) return super.charTyped(input);
        // Typing anywhere starts a search, the way a launcher would.
        searchFocused = true;
        if (search.codePointCount(0, search.length()) < 32) setSearch(search + input.asString());
        return true;
    }
}
