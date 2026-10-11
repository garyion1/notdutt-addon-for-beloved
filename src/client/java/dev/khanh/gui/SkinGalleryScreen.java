package dev.khanh.gui;

import dev.khanh.KhanhClient;
import dev.khanh.config.ConfigManager;
import dev.khanh.feature.SkinCatalog;
import dev.khanh.module.Module;
import dev.khanh.module.ModuleOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

/** Searchable, paged chooser for publicly listed NameMC skins. */
public final class SkinGalleryScreen extends Screen {
    private static final int PAGE_SIZE = 20;
    private final Screen parent;
    private String query = "";
    private int page;

    public SkinGalleryScreen(Screen parent) {
        super(Text.literal("Skin Protect Gallery"));
        this.parent = parent;
    }

    @Override public boolean shouldPause() { return false; }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        if (client != null && client.world != null) context.fill(0, 0, width, height, 0xB9000000);
        else context.fill(0, 0, width, height, 0xFF090B10);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);
        int left = Math.max(12, (width - 700) / 2), top = Math.max(15, (height - 470) / 2);
        int panelW = Math.min(width - 24, 700), panelH = Math.min(height - 30, 470);
        KhanhClickGuiScreen.roundedRect(context, left, top, panelW, panelH, 13, 0xE511141A);
        context.fill(left + 16, top + 51, left + panelW - 16, top + 52, 0xFF414851);
        context.drawText(textRenderer, KhanhClickGuiScreen.fontText("SKIN PROTECT  /  NAMEMC TOP 120"),
            left + 20, top + 21, 0xFFF4F5F9, false);
        context.drawText(textRenderer, KhanhClickGuiScreen.fontText("BACK"), left + panelW - 62, top + 21, 0xFFB9C2CE, false);
        context.drawText(textRenderer, KhanhClickGuiScreen.fontText("SEARCH RANK  " + (query.isEmpty() ? "#1 TO #120" : query)),
            left + 20, top + 66, 0xFFBFC8D2, false);
        int columns = 5;
        int gap = 8;
        int tileW = (panelW - 40 - gap * (columns - 1)) / columns;
        int tileH = Math.max(42, Math.min(69, (panelH - 150) / 4 - 6));
        List<Integer> matches = matches();
        int pages = Math.max(1, (matches.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        page = Math.min(page, pages - 1);
        for (int slot = 0; slot < PAGE_SIZE; slot++) {
            int at = page * PAGE_SIZE + slot;
            if (at >= matches.size()) break;
            int rank = matches.get(at), col = slot % columns, row = slot / columns;
            int x = left + 20 + col * (tileW + gap), y = top + 91 + row * (tileH + gap);
            boolean hover = inside(mouseX, mouseY, x, y, tileW, tileH);
            KhanhClickGuiScreen.roundedRect(context, x, y, tileW, tileH, 7,
                hover ? 0xFF343A45 : 0xD8242932);
            SkinCatalog.Preview preview = SkinCatalog.preview(client, SkinCatalog.IDS[rank]);
            int faceSize = Math.min(tileH - 12, 38);
            if (preview != null)
                context.drawTexture(RenderPipelines.GUI_TEXTURED, preview.id(),
                    x + 8, y + (tileH - faceSize) / 2, 0, 0, faceSize, faceSize,
                    preview.width(), preview.height(), preview.width(), preview.height());
            else context.fill(x + 8, y + (tileH - faceSize) / 2, x + 8 + faceSize,
                y + (tileH + faceSize) / 2, 0xFF363D48);
            context.drawText(textRenderer, KhanhClickGuiScreen.fontText("#" + (rank + 1)),
                x + faceSize + 15, y + (tileH - 8) / 2, hover ? 0xFFFFFFFF : 0xFFBFC7D1, false);
        }
        context.drawText(textRenderer, KhanhClickGuiScreen.fontText("PREV"),
            left + 20, top + panelH - 28, 0xFFCAD2DC, false);
        context.drawText(textRenderer, KhanhClickGuiScreen.fontText("PAGE " + (page + 1) + " / " + pages),
            left + panelW / 2 - 42, top + panelH - 28, 0xFFB9C2CC, false);
        context.drawText(textRenderer, KhanhClickGuiScreen.fontText("NEXT"),
            left + panelW - 60, top + panelH - 28, 0xFFCAD2DC, false);
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (click.button() != 0) return super.mouseClicked(click, doubled);
        int left = Math.max(12, (width - 700) / 2), top = Math.max(15, (height - 470) / 2);
        int panelW = Math.min(width - 24, 700), panelH = Math.min(height - 30, 470);
        int mx = (int) click.x(), my = (int) click.y();
        if (inside(mx, my, left + panelW - 76, top + 8, 70, 35)) { close(); return true; }
        if (inside(mx, my, left + 10, top + panelH - 40, 80, 35)) { page = Math.max(0, page - 1); return true; }
        if (inside(mx, my, left + panelW - 85, top + panelH - 40, 80, 35)) { page++; return true; }
        int columns = 5, gap = 8;
        int tileW = (panelW - 40 - gap * (columns - 1)) / columns;
        int tileH = Math.max(42, Math.min(69, (panelH - 150) / 4 - 6));
        List<Integer> matches = matches();
        for (int slot = 0; slot < PAGE_SIZE; slot++) {
            int at = page * PAGE_SIZE + slot;
            if (at >= matches.size()) break;
            int x = left + 20 + (slot % columns) * (tileW + gap);
            int y = top + 91 + (slot / columns) * (tileH + gap);
            if (!inside(mx, my, x, y, tileW, tileH)) continue;
            Module module = KhanhClient.modules().byId("skin_protect");
            if (module != null) {
                ModuleOption selected = module.option("skin_id");
                if (selected != null) selected.setText(SkinCatalog.IDS[matches.get(at)]);
                module.setEnabled(true);
                ConfigManager.save(KhanhClient.modules());
            }
            close();
            return true;
        }
        return super.mouseClicked(click, doubled);
    }

    @Override public boolean charTyped(CharInput input) {
        if (!input.asString().isEmpty() && query.length() < 10) {
            query += input.asString().toLowerCase(Locale.ROOT);
            page = 0;
            return true;
        }
        return super.charTyped(input);
    }

    @Override public boolean keyPressed(KeyInput input) {
        if (input.key() == GLFW.GLFW_KEY_BACKSPACE && !query.isEmpty()) {
            query = query.substring(0, query.length() - 1);
            page = 0;
            return true;
        }
        return super.keyPressed(input);
    }

    @Override public void close() { client.setScreen(parent); }

    private List<Integer> matches() {
        List<Integer> result = new ArrayList<>();
        for (int i = 0; i < SkinCatalog.IDS.length; i++) {
            if (query.isEmpty() || ("#" + (i + 1)).contains(query) || Integer.toString(i + 1).contains(query)
                || SkinCatalog.IDS[i].contains(query)) result.add(i);
        }
        return result;
    }

    private static boolean inside(int px, int py, int x, int y, int w, int h) {
        return px >= x && py >= y && px < x + w && py < y + h;
    }
}
