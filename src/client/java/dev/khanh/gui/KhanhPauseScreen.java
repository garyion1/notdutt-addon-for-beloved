package dev.khanh.gui;

import dev.khanh.KhanhClient;
import dev.khanh.feature.PrivacyService;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.option.OptionsScreen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

/** Khanh Client's pause menu: one glass panel with the game's destinations, live session stats and your skin. */
public final class KhanhPauseScreen extends KhanhGlassScreen {
    private static final Identifier LOGO = Identifier.of(KhanhClient.MOD_ID, "textures/gui/khanh-logo.png");
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm");
    private int panelX, panelY, panelW, panelH, infoX, infoY, infoW, infoH;
    private boolean exiting;

    public KhanhPauseScreen() {
        super("Game Menu");
    }

    @Override
    protected void init() {
        super.init();
        panelW = 248;
        int rowH = 24, gap = 6;
        panelH = 44 + 46 + gap + (rowH + gap) + 6 + 30 + 14;
        infoH = 12 + 16 + 5 * 18 + 8;
        boolean side = width >= 520;
        infoW = side ? 168 : 0;
        int total = panelW + (side ? infoW + 14 : 0);
        panelX = (width - total) / 2;
        int tallest = side ? Math.max(panelH, infoH) : panelH;
        panelY = Math.max(10, (height - tallest) / 2 + (tallest - panelH) / 2);
        infoX = panelX + panelW + 14;
        infoY = Math.max(10, (height - tallest) / 2);

        int x = panelX + 14, w = panelW - 28, y = panelY + 44;
        buttons.add(new Button("back", "Back to Game", "Esc also closes this", HudSurfaceRenderer.HudIcon.PLAYER,
            x, y, w, 46, true, this::close));
        y += 46 + gap;
        int half = (w - gap) / 2;
        buttons.add(new Button("options", "Options", null, HudSurfaceRenderer.HudIcon.WAND, x, y, half, rowH, false,
            () -> client.setScreen(new OptionsScreen(this, client.options))));
        buttons.add(new Button("zero", "Khanh Client", null, HudSurfaceRenderer.HudIcon.MODULES, x + half + gap, y, half, rowH, false,
            () -> client.setScreen(KhanhClickGui.create(this))));
        y += rowH + gap + 6;
        String exit = client.isInSingleplayer() ? "Save and Quit" : "Disconnect";
        buttons.add(new Button("exit", exit, null, HudSurfaceRenderer.HudIcon.COMBAT, x, y, w, 30, false, this::exit));

    }

    @Override
    boolean enabled(Button b) {
        if (b.id().equals("exit")) return !exiting;
        return true;
    }

    private void exit() {
        exiting = true;
        client.disconnect(ClientWorld.QUITTING_MULTIPLAYER_TEXT);
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        // Drawn in render(): the world stays visible under a soft wash, not Minecraft's heavy blur.
    }

    @Override
    public void render(DrawContext c, int mouseX, int mouseY, float delta) {
        float wash = clamp(seconds() / 0.18F);
        c.fillGradient(0, 0, width, height, alpha(0.42F * wash) << 24, alpha(0.62F * wash) << 24);
        float fade = clamp(spring("panel", Spring.PANEL, 0.0F).to(1.0F).value());
        c.getMatrices().pushMatrix();
        c.getMatrices().translate(0.0F, (1.0F - fade) * 10.0F);
        LiquidGlass.card(c, panelX, panelY, panelW, panelH, 18, fade);
        c.drawTexture(RenderPipelines.GUI_TEXTURED, LOGO, panelX + 14, panelY + 12, 0F, 0F, 20, 20, 256, 256, 256, 256,
            alpha(fade) << 24 | 0xFFFFFF);
        text(c, "Khanh Client", panelX + 40, panelY + 13, FG, 1.0F, fade);
        text(c, "PAUSED", panelX + 40, panelY + 24, FAINT, 0.6F, fade);
        String clock = LocalTime.now().format(CLOCK);
        text(c, clock, panelX + panelW - 14 - width(clock, 0.85F), panelY + 17, SOFT, 0.85F, fade);
        for (int i = 0; i < buttons.size(); i++) drawButton(c, buttons.get(i), i, mouseX, mouseY, enabled(buttons.get(i)));
        if (infoW > 0) drawInfo(c, fade);
        c.getMatrices().popMatrix();
        c.createNewRootLayer();
        super.render(c, mouseX, mouseY, delta);
    }

    /** Live session card: who, where, how well it's running. */
    private void drawInfo(DrawContext c, float fade) {
        LiquidGlass.card(c, infoX, infoY, infoW, infoH, 18, fade);
        String name = PrivacyService.protectedName(client.getGameProfile().name());
        int rowY = infoY + 14;
        text(c, name, infoX + (infoW - width(name, 0.9F)) / 2.0F, rowY, FG, 0.9F, fade);
        rowY += 16;
        String[][] rows = {
            {"World", world()},
            {"Ping", ping()},
            {"FPS", Integer.toString(client.getCurrentFps())},
            {"Position", client.player == null ? "-" : client.player.getBlockX() + ", " + client.player.getBlockY() + ", " + client.player.getBlockZ()},
            {"Players", client.getNetworkHandler() == null ? "-" : Integer.toString(client.getNetworkHandler().getPlayerList().size())},
        };
        for (String[] row : rows) {
            SmoothGuiShapes.rect(c, infoX + 10, rowY - 3, infoW - 20, 15, 7, alpha(0.05F * fade) << 24 | 0xFFFFFF, false);
            text(c, row[0], infoX + 16, rowY + 1, FAINT, 0.62F, fade);
            String value = trim(row[1], infoW - 80, 0.66F);
            text(c, value, infoX + infoW - 16 - width(value, 0.66F), rowY + 1, SOFT, 0.66F, fade);
            rowY += 18;
        }
    }

    private String world() {
        if (client.isInSingleplayer()) {
            return client.getServer() != null ? client.getServer().getSaveProperties().getLevelName() : "Singleplayer";
        }
        return client.getCurrentServerEntry() != null ? client.getCurrentServerEntry().address : "Multiplayer";
    }

    private String ping() {
        if (client.isInSingleplayer() || client.getNetworkHandler() == null || client.player == null) return "Local";
        PlayerListEntry entry = client.getNetworkHandler().getPlayerListEntry(client.player.getUuid());
        return (entry == null ? 0 : entry.getLatency()) + " ms";
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (input.key() == GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }
        return super.keyPressed(input);
    }
}
