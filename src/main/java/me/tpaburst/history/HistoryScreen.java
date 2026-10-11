package me.tpaburst.history;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import me.tpaburst.ui.Theme;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Session kill / loot history. The window can be dragged by its header, and its position
 * is remembered for the rest of the session.
 */
public final class HistoryScreen extends Screen {
    private static final int WIDTH = 360;
    private static final int MAX_HEIGHT = 300;
    private static final int HEADER_H = 26;
    private static final int TOOLBAR_H = 26;
    private static final int FOOTER_H = 18;
    private static final int ROW_H = 18;
    private static final int PAD = 8;
    private static final int SCROLLBAR_W = 4;

    // Remembered between openings; MIN_VALUE means "centre on first open".
    private static int savedX = Integer.MIN_VALUE;
    private static int savedY = Integer.MIN_VALUE;
    private static String savedQuery = "";

    private boolean killsMode;
    private double scroll;
    private TextFieldWidget search;

    private int winX;
    private int winY;
    private int winH;

    private boolean draggingWindow;
    private double dragOffX;
    private double dragOffY;
    private boolean draggingThumb;
    private double thumbGrabY;

    public HistoryScreen(boolean killsMode) {
        super(Text.literal("TPA Tools History"));
        this.killsMode = killsMode;
    }

    @Override
    protected void init() {
        winH = Math.max(140, Math.min(MAX_HEIGHT, height - 40));
        if (savedX == Integer.MIN_VALUE) {
            winX = (width - WIDTH) / 2;
            winY = (height - winH) / 2;
        } else {
            winX = savedX;
            winY = savedY;
        }
        clampWindow();

        search = new TextFieldWidget(textRenderer, 0, 0, 108, 12, Text.literal("Search"));
        search.setDrawsBackground(false);
        search.setMaxLength(32);
        search.setEditableColor(Theme.TEXT);
        search.setPlaceholder(Text.literal("Search...").formatted(Formatting.DARK_GRAY));
        search.setText(savedQuery);
        search.setChangedListener(q -> {
            savedQuery = q;
            scroll = 0;
        });
        addDrawableChild(search);
        layoutWidgets();
    }

    private void clampWindow() {
        winX = Math.max(0, Math.min(width - WIDTH, winX));
        winY = Math.max(0, Math.min(height - winH, winY));
        savedX = winX;
        savedY = winY;
    }

    private void layoutWidgets() {
        if (search == null) return;
        search.setX(searchX() + 5);
        search.setY(toolbarY() + 7);
    }

    // Layout helpers

    private int toolbarY() {
        return winY + HEADER_H;
    }

    private int listX() {
        return winX + PAD;
    }

    private int listY() {
        return toolbarY() + TOOLBAR_H;
    }

    private int listW() {
        return WIDTH - PAD * 2;
    }

    private int listH() {
        return winH - HEADER_H - TOOLBAR_H - FOOTER_H;
    }

    private int visibleRows() {
        return Math.max(1, listH() / ROW_H);
    }

    private int closeX() {
        return winX + WIDTH - PAD - 14;
    }

    private int clearX() {
        return winX + WIDTH - PAD - 40;
    }

    private int searchX() {
        return clearX() - 6 - 120;
    }

    private int tabX(boolean kills) {
        return kills ? winX + PAD : winX + PAD + tabW(true) + 4;
    }

    private int tabW(boolean kills) {
        return textRenderer.getWidth(tabLabel(kills)) + 14;
    }

    private String tabLabel(boolean kills) {
        return kills ? "Kills  " + HistoryStore.killCount() : "Loot  " + HistoryStore.lootCount();
    }

    private List<HistoryStore.Entry> filtered() {
        List<HistoryStore.Entry> all = killsMode ? HistoryStore.kills() : HistoryStore.loot();
        String q = savedQuery.trim().toLowerCase(Locale.ROOT);
        List<HistoryStore.Entry> out = new ArrayList<>(all.size());
        for (int i = all.size() - 1; i >= 0; i--) {
            HistoryStore.Entry e = all.get(i);
            if (q.isEmpty() || e.text().toLowerCase(Locale.ROOT).contains(q)) out.add(e);
        }
        return out;
    }

    private int maxScroll(int size) {
        return Math.max(0, size - visibleRows());
    }

    // Rendering

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        List<HistoryStore.Entry> rows = filtered();
        scroll = Math.max(0, Math.min(maxScroll(rows.size()), scroll));

        // Window body and header.
        Theme.panel(ctx, winX - 1, winY - 1, WIDTH + 2, winH + 2, Theme.PANEL, Theme.BORDER);
        ctx.fill(winX, winY, winX + WIDTH, winY + HEADER_H, Theme.PANEL_HEADER);
        ctx.fill(winX, winY + HEADER_H - 1, winX + WIDTH, winY + HEADER_H, Theme.BORDER);
        ctx.fill(winX, winY, winX + WIDTH, winY + 1, Theme.ACCENT);

        Theme.softRect(ctx, winX + PAD, winY + 9, 8, 8, Theme.ACCENT);
        ctx.drawText(textRenderer, "TPA Tools", winX + PAD + 14, winY + 9, Theme.TEXT, false);
        ctx.drawText(textRenderer, "Session history", winX + PAD + 14 + textRenderer.getWidth("TPA Tools") + 6, winY + 9, Theme.TEXT_MUTED, false);

        boolean closeHover = Theme.inside(mouseX, mouseY, closeX(), winY + 6, 14, 14);
        Theme.softRect(ctx, closeX(), winY + 6, 14, 14, closeHover ? Theme.ACCENT : Theme.SURFACE);
        ctx.drawText(textRenderer, "x", closeX() + 5, winY + 9, Theme.TEXT, false);

        // Toolbar: tabs, search, clear.
        int ty = toolbarY() + 5;
        drawTab(ctx, true, ty, mouseX, mouseY);
        drawTab(ctx, false, ty, mouseX, mouseY);

        boolean searchFocused = search != null && search.isFocused();
        Theme.panel(ctx, searchX(), ty, 120, 16, Theme.SURFACE, searchFocused ? Theme.ACCENT : Theme.BORDER);

        boolean clearHover = Theme.inside(mouseX, mouseY, clearX(), ty, 40 - 2, 16);
        Theme.panel(ctx, clearX(), ty, 38, 16, clearHover ? Theme.ACCENT_DIM : Theme.SURFACE, clearHover ? Theme.ACCENT : Theme.BORDER);
        ctx.drawText(textRenderer, "Clear", clearX() + (38 - textRenderer.getWidth("Clear")) / 2, ty + 4, clearHover ? Theme.TEXT : Theme.TEXT_SECONDARY, false);

        // Widgets (search field).
        super.render(ctx, mouseX, mouseY, delta);

        // List.
        int lx = listX();
        int ly = listY();
        int lw = listW();
        int lh = listH();
        Theme.panel(ctx, lx, ly, lw, lh, 0xFF0C0D11, Theme.BORDER);

        if (rows.isEmpty()) {
            String msg = savedQuery.isBlank() ? (killsMode ? "No kills recorded yet." : "No loot recorded yet.") : "Nothing matches \"" + savedQuery + "\".";
            ctx.drawText(textRenderer, msg, lx + (lw - textRenderer.getWidth(msg)) / 2, ly + lh / 2 - 4, Theme.TEXT_MUTED, false);
        } else {
            ctx.enableScissor(lx + 1, ly + 1, lx + lw - 1, ly + lh - 1);
            int first = (int) scroll;
            int rowW = lw - 2 - SCROLLBAR_W - 4;
            for (int i = 0; i < visibleRows() + 1 && first + i < rows.size(); i++) {
                HistoryStore.Entry e = rows.get(first + i);
                int ry = ly + 1 + i * ROW_H;
                boolean hover = Theme.inside(mouseX, mouseY, lx + 1, ry, rowW, ROW_H) && ry + ROW_H <= ly + lh;
                if (hover) ctx.fill(lx + 1, ry, lx + 1 + rowW, ry + ROW_H, Theme.ROW_HOVER);
                else if (((first + i) & 1) == 1) ctx.fill(lx + 1, ry, lx + 1 + rowW, ry + ROW_H, Theme.ROW_ALT);
                if (hover) ctx.fill(lx + 1, ry, lx + 2, ry + ROW_H, Theme.ACCENT);

                int tx = lx + 8;
                ctx.drawText(textRenderer, e.time(), tx, ry + 5, Theme.TEXT_MUTED, false);
                tx += textRenderer.getWidth("00:00:00") + 8;
                if (!e.icon().isEmpty()) {
                    ctx.drawItem(e.icon(), tx, ry + 1);
                    tx += 20;
                } else {
                    Theme.softRect(ctx, tx + 2, ry + 6, 5, 5, Theme.ACCENT);
                    tx += 12;
                }
                String text = textRenderer.trimToWidth(e.text(), lx + rowW - tx - 4);
                ctx.drawText(textRenderer, text, tx, ry + 5, Theme.TEXT, false);
            }
            ctx.disableScissor();

            drawScrollbar(ctx, rows.size(), mouseX, mouseY);
        }

        // Footer.
        int fy = winY + winH - FOOTER_H + 5;
        int total = killsMode ? HistoryStore.killCount() : HistoryStore.lootCount();
        String count = rows.size() == total ? total + (total == 1 ? " entry" : " entries") : rows.size() + " of " + total + " entries";
        ctx.drawText(textRenderer, count, winX + PAD, fy, Theme.TEXT_MUTED, false);
        String hint = "Drag header to move  •  Scroll to browse";
        ctx.drawText(textRenderer, hint, winX + WIDTH - PAD - textRenderer.getWidth(hint), fy, 0xFF565C6A, false);
    }

    private void drawTab(DrawContext ctx, boolean kills, int y, int mouseX, int mouseY) {
        int x = tabX(kills);
        int w = tabW(kills);
        boolean selected = killsMode == kills;
        boolean hover = Theme.inside(mouseX, mouseY, x, y, w, 16);
        int fill = selected ? Theme.ACCENT : (hover ? Theme.SURFACE_HOVER : Theme.SURFACE);
        Theme.softRect(ctx, x, y, w, 16, fill);
        String name = kills ? "Kills" : "Loot";
        String num = Integer.toString(kills ? HistoryStore.killCount() : HistoryStore.lootCount());
        ctx.drawText(textRenderer, name, x + 7, y + 4, selected ? Theme.TEXT : Theme.TEXT_SECONDARY, false);
        ctx.drawText(textRenderer, num, x + 7 + textRenderer.getWidth(name + "  "), y + 4, selected ? 0xFFFFD6D6 : Theme.TEXT_MUTED, false);
    }

    private int[] thumbBounds(int size) {
        int trackY = listY() + 2;
        int trackH = listH() - 4;
        int vis = visibleRows();
        int thumbH = Math.max(12, (int) ((double) trackH * vis / Math.max(vis, size)));
        int max = maxScroll(size);
        int thumbY = trackY + (max == 0 ? 0 : (int) ((trackH - thumbH) * (scroll / max)));
        return new int[]{trackY, trackH, thumbY, thumbH};
    }

    private int scrollbarX() {
        return listX() + listW() - SCROLLBAR_W - 3;
    }

    private void drawScrollbar(DrawContext ctx, int size, int mouseX, int mouseY) {
        if (maxScroll(size) == 0) return;
        int[] b = thumbBounds(size);
        int sx = scrollbarX();
        ctx.fill(sx, b[0], sx + SCROLLBAR_W, b[0] + b[1], 0x14FFFFFF);
        boolean hover = draggingThumb || Theme.inside(mouseX, mouseY, sx - 2, b[2], SCROLLBAR_W + 4, b[3]);
        Theme.softRect(ctx, sx, b[2], SCROLLBAR_W, b[3], hover ? Theme.ACCENT : 0xFF3A3F4C);
    }

    // Input

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        double mx = click.x();
        double my = click.y();
        if (click.button() == 0) {
            int ty = toolbarY() + 5;
            if (Theme.inside(mx, my, closeX(), winY + 6, 14, 14)) {
                close();
                return true;
            }
            if (Theme.inside(mx, my, clearX(), ty, 38, 16)) {
                if (killsMode) HistoryStore.clearKills();
                else HistoryStore.clearLoot();
                scroll = 0;
                return true;
            }
            for (boolean kills : new boolean[]{true, false}) {
                if (Theme.inside(mx, my, tabX(kills), ty, tabW(kills), 16)) {
                    if (killsMode != kills) {
                        killsMode = kills;
                        scroll = 0;
                    }
                    return true;
                }
            }
            int size = filtered().size();
            if (maxScroll(size) > 0 && Theme.inside(mx, my, scrollbarX() - 2, listY(), SCROLLBAR_W + 4, listH())) {
                int[] b = thumbBounds(size);
                draggingThumb = true;
                thumbGrabY = (my >= b[2] && my < b[2] + b[3]) ? my - b[2] : b[3] / 2.0;
                dragThumbTo(my, size);
                return true;
            }
            if (Theme.inside(mx, my, winX, winY, WIDTH, HEADER_H)) {
                draggingWindow = true;
                dragOffX = mx - winX;
                dragOffY = my - winY;
                return true;
            }
        }
        boolean handled = super.mouseClicked(click, doubled);
        if (!handled && search != null && !Theme.inside(mx, my, searchX(), toolbarY() + 5, 120, 16)) search.setFocused(false);
        return handled;
    }

    @Override
    public boolean mouseDragged(Click click, double offsetX, double offsetY) {
        if (draggingWindow) {
            winX = (int) (click.x() - dragOffX);
            winY = (int) (click.y() - dragOffY);
            clampWindow();
            layoutWidgets();
            return true;
        }
        if (draggingThumb) {
            dragThumbTo(click.y(), filtered().size());
            return true;
        }
        return super.mouseDragged(click, offsetX, offsetY);
    }

    private void dragThumbTo(double my, int size) {
        int[] b = thumbBounds(size);
        int range = b[1] - b[3];
        if (range <= 0) return;
        double t = (my - thumbGrabY - b[0]) / range;
        scroll = Math.round(Math.max(0, Math.min(1, t)) * maxScroll(size));
    }

    @Override
    public boolean mouseReleased(Click click) {
        draggingWindow = false;
        draggingThumb = false;
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int size = filtered().size();
        scroll = Math.max(0, Math.min(maxScroll(size), scroll - verticalAmount * 3));
        return true;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
