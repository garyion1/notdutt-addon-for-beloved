package dev.khanh.gui;

import dev.khanh.KhanhClient;
import dev.khanh.config.ConfigManager;
import dev.khanh.feature.KhanhSounds;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

/** Five-profile config library styled as a compact card workspace. */
public final class KhanhConfigScreen extends Screen {
    private static final int W = 920;
    private static final int H = 570;
    private static final int ACCENT = 0xFF54BFFF;
    private static final int WHITE = 0xFFF2F4F8;
    private static final int MUTED = 0xFF858A98;
    private static final int RED = 0xFFFF6578;
    private static final Identifier LOGO = Identifier.of(KhanhClient.MOD_ID, "textures/gui/khanh-logo.png");

    private final Screen parent;
    private int selected = 1;
    private int loaded;
    private int pendingDelete;
    private String name = "Profile 1";
    private String code = "";
    private String status = "Select a config or import a six-digit sharing code.";
    private boolean editingName;
    private boolean editingCode;
    private boolean importOpen;

    public KhanhConfigScreen(Screen parent) {
        super(Text.literal("Khanh Client Configs"));
        this.parent = parent;
    }

    @Override
    public void init() {
        super.init();
        name = ConfigManager.profileName(selected);
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, 0xE8080A10);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);
        context.getMatrices().pushMatrix();
        context.getMatrices().translate(width / 2.0F, height / 2.0F);
        context.getMatrices().scale(scale(), scale());
        context.getMatrices().translate(-W / 2.0F, -H / 2.0F);

        panel(context, 8, 8, 904, 554, 14, 0xFA11131D, 0x59414A5A);
        context.drawTexture(RenderPipelines.GUI_TEXTURED, LOGO,
            26, 22, 0F, 0F, 24, 24, 256, 256, 256, 256);
        label(context, "KHANH CLIENT CONFIGS", 58, 29, WHITE);
        label(context, "ESC", 868, 29, WHITE);
        context.fill(8, 59, 912, 60, 0xFF313544);
        label(context, "MY CONFIGS", 28, 75, WHITE);
        label(context, "5 LOCAL PROFILES", 788, 75, MUTED);

        for (int slot = 1; slot <= 5; slot++) renderCard(context, slot, mouseX, mouseY);

        context.fill(24, 468, 896, 469, 0xFF2B2F3C);
        int buttonY = 493;
        button(context, "SAVE CURRENT", 258, buttonY, 122, 36, mouseX, mouseY, true, false);
        button(context, "SAVE NEW", 390, buttonY, 104, 36, mouseX, mouseY, false, false);
        button(context, "RESET", 504, buttonY, 86, 36, mouseX, mouseY, false, true);
        button(context, "IMPORT", 600, buttonY, 96, 36, mouseX, mouseY, false, false);
        label(context, shorten(status, 112), 28, 543, statusColor());

        if (importOpen) renderImport(context, mouseX, mouseY);
        context.getMatrices().popMatrix();
        super.render(context, mouseX, mouseY, delta);
    }

    private void renderCard(DrawContext context, int slot, int mouseX, int mouseY) {
        int column = (slot - 1) % 3;
        int row = (slot - 1) / 3;
        int x = 24 + column * 292;
        int y = 93 + row * 151;
        int w = 270;
        int h = 126;
        boolean exists = ConfigManager.profileExists(slot);
        boolean active = slot == selected;
        panel(context, x, y, w, h, 10,
            active ? 0xF7191D29 : 0xF3161822,
            active ? ACCENT : 0xFF343948);
        if (active) context.fill(x + 1, y + 1, x + w - 1, y + 3, ACCENT);

        String profileName = slot == selected ? name : ConfigManager.profileName(slot);
        label(context, shorten(profileName.toUpperCase(), 25), x + 14, y + 16, WHITE);
        label(context, "SLOT " + String.format("%02d", slot), x + w - 58, y + 16, MUTED);
        String cardStatus = loaded == slot ? "CURRENTLY LOADED" : exists ? "SAVED" : "EMPTY";
        label(context, cardStatus, x + 14, y + 42,
            loaded == slot ? ACCENT : exists ? 0xFFA7B8C8 : MUTED);
        context.fill(x + 14, y + 65, x + w - 14, y + 66, 0xFF2A2F3D);

        actionButton(context, "↗", x + 159, y + 80, mouseX, mouseY, exists, false);
        actionButton(context, "−", x + 195, y + 80, mouseX, mouseY, exists, true);
        actionButton(context, "↓", x + 231, y + 80, mouseX, mouseY, exists, false);
        if (active && editingName && blink()) label(context, "|", x + 15 + textRenderer.getWidth(
            KhanhClickGuiScreen.fontText(shorten(profileName.toUpperCase(), 25))), y + 16, ACCENT);
    }

    private void renderImport(DrawContext context, int mouseX, int mouseY) {
        context.fill(8, 8, 912, 562, 0xB0000000);
        panel(context, 281, 205, 358, 160, 12, 0xFF171A24, 0xFF424A5A);
        label(context, "IMPORT CONFIG", 301, 226, WHITE);
        label(context, "ENTER A SIX-DIGIT CODE OR PASTE A FULL CODE", 301, 248, MUTED);
        panel(context, 301, 273, 318, 38, 7, 0xFF0C0F16, editingCode ? ACCENT : 0xFF3A4150);
        String shown = code.isBlank() ? "000000" : compactCode(code);
        label(context, shown + (editingCode && blink() ? "|" : ""), 314, 287,
            code.isBlank() ? MUTED : WHITE);
        button(context, "CANCEL", 381, 324, 88, 29, mouseX, mouseY, false, false);
        button(context, "IMPORT", 479, 324, 90, 29, mouseX, mouseY, true, false);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (click.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) return super.mouseClicked(click, doubled);
        double x = localX(click.x());
        double y = localY(click.y());
        if (importOpen) return clickImport(x, y);
        if (hit(x, y, 855, 16, 48, 34)) { close(); return true; }

        for (int slot = 1; slot <= 5; slot++) {
            int cardX = 24 + (slot - 1) % 3 * 292;
            int cardY = 93 + (slot - 1) / 3 * 151;
            if (hit(x, y, cardX + 159, cardY + 80, 30, 28)) {
                selected = slot;
                name = ConfigManager.profileName(slot);
                if (ConfigManager.profileExists(slot))
                    status = ConfigManager.copySixDigitCode(KhanhClient.modules(), result -> status = result);
                sound();
                return true;
            }
            if (hit(x, y, cardX + 195, cardY + 80, 30, 28)) {
                selected = slot;
                name = ConfigManager.profileName(slot);
                deleteSelected();
                sound();
                return true;
            }
            if (hit(x, y, cardX + 231, cardY + 80, 30, 28)) {
                selected = slot;
                name = ConfigManager.profileName(slot);
                status = ConfigManager.loadProfile(KhanhClient.modules(), slot);
                if (ConfigManager.profileExists(slot)) loaded = slot;
                pendingDelete = 0;
                sound();
                return true;
            }
            if (hit(x, y, cardX, cardY, 270, 126)) {
                selected = slot;
                name = ConfigManager.profileName(slot);
                editingName = hit(x, y, cardX + 10, cardY + 6, 180, 34);
                pendingDelete = 0;
                status = ConfigManager.profileExists(slot) ? "Selected " + name : "Profile " + slot + " is empty.";
                sound();
                return true;
            }
        }

        if (hit(x, y, 258, 493, 122, 36)) {
            status = ConfigManager.saveProfile(KhanhClient.modules(), selected, name);
            name = ConfigManager.profileName(selected);
            loaded = selected;
        } else if (hit(x, y, 390, 493, 104, 36)) {
            int empty = firstEmptySlot();
            if (empty == 0) status = "All five profiles are used — select one and use Save Current.";
            else {
                selected = empty;
                name = "Profile " + empty;
                status = ConfigManager.saveProfile(KhanhClient.modules(), selected, name);
                loaded = selected;
            }
        } else if (hit(x, y, 504, 493, 86, 36)) {
            status = ConfigManager.profileExists(selected)
                ? ConfigManager.loadProfile(KhanhClient.modules(), selected)
                : "This profile is empty.";
            if (ConfigManager.profileExists(selected)) loaded = selected;
        } else if (hit(x, y, 600, 493, 96, 36)) {
            importOpen = true;
            editingCode = true;
        } else return super.mouseClicked(click, doubled);
        editingName = false;
        sound();
        return true;
    }

    private boolean clickImport(double x, double y) {
        if (hit(x, y, 301, 273, 318, 38)) {
            editingCode = true;
        } else if (hit(x, y, 381, 324, 88, 29)) {
            importOpen = false;
            editingCode = false;
        } else if (hit(x, y, 479, 324, 90, 29)) {
            String entered = code.isBlank() ? client.keyboard.getClipboard().strip() : code;   // empty: use the clipboard
            if (entered.isBlank()) status = "Copy a friend's config code first.";
            else status = ConfigManager.importShareCodeAsync(KhanhClient.modules(), entered, result -> status = result);
            importOpen = false;
            editingCode = false;
        } else return true;
        sound();
        return true;
    }

    private void deleteSelected() {
        if (!ConfigManager.profileExists(selected)) {
            status = "Profile " + selected + " is already empty.";
            return;
        }
        if (pendingDelete != selected) {
            pendingDelete = selected;
            status = "Click the minus button again to delete " + name + ".";
        } else {
            status = ConfigManager.deleteProfile(selected);
            pendingDelete = 0;
            if (loaded == selected) loaded = 0;
            name = ConfigManager.profileName(selected);
        }
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (editingName || editingCode) {
            if (input.key() == GLFW.GLFW_KEY_V && (input.modifiers() & GLFW.GLFW_MOD_CONTROL) != 0) {
                if (editingCode) code = client.keyboard.getClipboard().trim();
                else name = truncate(name + client.keyboard.getClipboard(), 24);
                return true;
            }
            if (input.key() == GLFW.GLFW_KEY_A && (input.modifiers() & GLFW.GLFW_MOD_CONTROL) != 0) {
                if (editingCode) code = ""; else name = "";
                return true;
            }
            if (input.key() == GLFW.GLFW_KEY_BACKSPACE) {
                if (editingCode && !code.isEmpty()) code = code.substring(0, code.length() - 1);
                if (editingName && !name.isEmpty()) name = name.substring(0, name.length() - 1);
                return true;
            }
            if (input.key() == GLFW.GLFW_KEY_ENTER || input.key() == GLFW.GLFW_KEY_KP_ENTER) {
                if (editingCode && !code.isBlank()) {
                    status = ConfigManager.importShareCodeAsync(KhanhClient.modules(), code, result -> status = result);
                    importOpen = false;
                }
                editingCode = false;
                editingName = false;
                return true;
            }
        }
        if (input.isEscape()) {
            if (importOpen) {
                importOpen = false;
                editingCode = false;
            } else close();
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public boolean charTyped(CharInput input) {
        if (input.isValidChar()) {
            for (char character : input.asString().toCharArray()) {
                if (character < ' ' || character == '\u007f') continue;
                if (editingName && name.length() < 24) name += character;
                else if (editingCode && code.length() < 131072) code += character;
            }
            if (editingName || editingCode) return true;
        }
        return super.charTyped(input);
    }

    @Override
    public void close() { client.setScreen(parent); }

    private int firstEmptySlot() {
        for (int slot = 1; slot <= 5; slot++) if (!ConfigManager.profileExists(slot)) return slot;
        return 0;
    }

    private int statusColor() {
        return status.contains("failed") || status.startsWith("Could not") ? RED : 0xFFA9BAC8;
    }

    private float scale() { return Math.max(.25F, Math.min(width / (float) W, height / (float) H)); }
    private double localX(double x) { return (x - width / 2.0) / scale() + W / 2.0; }
    private double localY(double y) { return (y - height / 2.0) / scale() + H / 2.0; }
    private static boolean hit(double x, double y, int bx, int by, int w, int h) {
        return x >= bx && y >= by && x < bx + w && y < by + h;
    }
    private boolean in(int mx, int my, int x, int y, int w, int h) {
        return hit(localX(mx), localY(my), x, y, w, h);
    }
    private static boolean blink() { return System.currentTimeMillis() / 430 % 2 == 0; }
    private static String truncate(String value, int limit) {
        return value.length() <= limit ? value : value.substring(0, limit);
    }
    private static String compactCode(String value) {
        return value.length() <= 32 ? value : value.substring(0, 12) + "…" + value.substring(value.length() - 16);
    }
    private static String shorten(String value, int limit) {
        return value.length() <= limit ? value : value.substring(0, limit - 1) + "…";
    }
    private void sound() { KhanhSounds.playInterface(client, 1.07F); }
    private void label(DrawContext context, String value, int x, int y, int color) {
        context.drawText(textRenderer, KhanhClickGuiScreen.fontText(value), x, y, color, false);
    }
    private static void panel(DrawContext context, int x, int y, int w, int h, int radius, int fill, int border) {
        SmoothGuiShapes.rect(context, x, y, w, h, radius, fill, false);
        SmoothGuiShapes.rect(context, x, y, w, h, radius, border, true);
    }
    private void actionButton(DrawContext context, String value, int x, int y,
                              int mouseX, int mouseY, boolean available, boolean danger) {
        boolean hover = available && in(mouseX, mouseY, x, y, 30, 28);
        int fill = !available ? 0xFF1B1E28 : danger && hover ? 0xFF42232D : hover ? 0xFF263447 : 0xFF202430;
        panel(context, x, y, 30, 28, 6, fill, !available ? 0xFF292D39 : danger ? 0xFF55313B : 0xFF323846);
        Text text = Text.literal(value);
        context.drawText(textRenderer, text, x + (30 - textRenderer.getWidth(text)) / 2, y + 10,
            !available ? 0xFF555A68 : danger ? RED : ACCENT, false);
    }
    private void button(DrawContext context, String value, int x, int y, int w, int h,
                        int mouseX, int mouseY, boolean primary, boolean danger) {
        boolean hover = in(mouseX, mouseY, x, y, w, h);
        int fill = danger ? (hover ? 0xFF32202A : 0xFF211A23)
            : primary ? (hover ? 0xFF24618A : 0xFF1C4B6A)
            : hover ? 0xFF252A36 : 0xFF1C1F29;
        panel(context, x, y, w, h, 7, fill,
            danger ? 0xFF4B2A35 : primary ? 0xFF347FB0 : 0xFF303541);
        Text styled = KhanhClickGuiScreen.fontText(value);
        context.drawText(textRenderer, styled, x + (w - textRenderer.getWidth(styled)) / 2,
            y + (h - 9) / 2, danger ? RED : WHITE, false);
    }
}
