package dev.khanh.gui;

import dev.khanh.KhanhClient;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ConfirmScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.AddServerScreen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.gui.screen.multiplayer.DirectConnectScreen;
import net.minecraft.client.gui.screen.world.WorldIcon;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.network.MultiplayerServerListPinger;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.option.ServerList;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.network.NetworkingBackend;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

/**
 * Khanh Client's server list: glass rows with favicon, MOTD, players and live ping bars, smooth
 * scrolling, double-click to join, and the vanilla add / edit / direct-connect dialogs.
 */
public final class KhanhServerScreen extends KhanhGlassScreen {
    private static final Identifier LOGO = Identifier.of(KhanhClient.MOD_ID, "textures/gui/khanh-logo.png");
    private static final ExecutorService PINGS = Executors.newFixedThreadPool(5, runnable -> {
        Thread thread = new Thread(runnable, "khanh-server-ping");
        thread.setDaemon(true);
        return thread;
    });
    private static final int ROW_H = 46, ROW_GAP = 6;

    private final Screen parent;
    private final MultiplayerServerListPinger pinger = new MultiplayerServerListPinger();
    private final ServerList servers;
    private final Map<String, WorldIcon> icons = new HashMap<>();
    private final Map<String, byte[]> iconBytes = new HashMap<>();
    private final Spring scroll = new Spring(Spring.SMOOTH, 0.0F);
    private float scrollTarget;
    private int selected = -1;
    private long lastClickAt;
    private int lastClickRow = -1;
    private int listX, listY, listW, listH;

    public KhanhServerScreen(Screen parent) {
        super("Servers");
        this.parent = parent;
        this.servers = new ServerList(net.minecraft.client.MinecraftClient.getInstance());
        this.servers.loadFile();
    }

    @Override
    protected void init() {
        super.init();
        listW = Math.min(560, width - 40);
        listX = (width - listW) / 2;
        listY = 52;
        int barY = height - 42;
        listH = barY - 10 - listY;
        int gap = 6, h = 26;
        String[][] actions = {
            {"join", "Join"}, {"direct", "Direct"}, {"add", "Add"}, {"edit", "Edit"}, {"delete", "Delete"}, {"refresh", "Refresh"}
        };
        HudSurfaceRenderer.HudIcon[] icons = {
            HudSurfaceRenderer.HudIcon.PLAYER, HudSurfaceRenderer.HudIcon.SPEED, HudSurfaceRenderer.HudIcon.FOCUS,
            HudSurfaceRenderer.HudIcon.WAND, HudSurfaceRenderer.HudIcon.COMBAT, HudSurfaceRenderer.HudIcon.CLOCK
        };
        int w = (listW - gap * (actions.length - 1)) / actions.length;
        for (int i = 0; i < actions.length; i++) {
            String id = actions[i][0];
            buttons.add(new Button(id, actions[i][1], null, icons[i], listX + i * (w + gap), barY, w, h, false, () -> act(id)));
        }
        buttons.add(new Button("back", "Back", null, null, 12, 12, 64, 24, false, this::close));
        for (int i = 0; i < servers.size(); i++) ping(servers.get(i), false);
    }

    private void ping(ServerInfo info, boolean force) {
        if (!force && info.getStatus() != ServerInfo.Status.INITIAL) return;
        info.setStatus(ServerInfo.Status.PINGING);
        info.label = Text.empty();
        info.playerCountLabel = Text.empty();
        PINGS.submit(() -> {
            try {
                // The last-but-one callback is where vanilla marks the server online (or a version mismatch).
                pinger.add(info, () -> client.execute(servers::saveFile), () -> info.setStatus(
                        info.protocolVersion == net.minecraft.SharedConstants.getProtocolVersion()
                            ? ServerInfo.Status.SUCCESSFUL : ServerInfo.Status.INCOMPATIBLE),
                    NetworkingBackend.remote(client.options.shouldUseNativeTransport()));
            } catch (java.net.UnknownHostException exception) {
                info.setStatus(ServerInfo.Status.UNREACHABLE);
                info.label = Text.literal("Can't resolve the address").withColor(0xFB7185);
            } catch (Exception exception) {
                info.setStatus(ServerInfo.Status.UNREACHABLE);
                info.label = Text.literal("Can't connect to this server").withColor(0xFB7185);
            }
        });
    }

    private void act(String id) {
        ServerInfo current = selected >= 0 && selected < servers.size() ? servers.get(selected) : null;
        switch (id) {
            case "join" -> { if (current != null) connect(current); }
            case "direct" -> {
                ServerInfo direct = new ServerInfo(I18n.translate("selectServer.defaultName"), "", ServerInfo.ServerType.OTHER);
                client.setScreen(new DirectConnectScreen(this, ok -> {
                    if (ok) connect(direct); else client.setScreen(this);
                }, direct));
            }
            case "add" -> {
                ServerInfo fresh = new ServerInfo(I18n.translate("selectServer.defaultName"), "", ServerInfo.ServerType.OTHER);
                client.setScreen(new AddServerScreen(this, Text.translatable("manageServer.add.title"), ok -> {
                    if (ok) {
                        servers.add(fresh, false);
                        servers.saveFile();
                        selected = servers.size() - 1;
                    }
                    client.setScreen(this);
                }, fresh));
            }
            case "edit" -> {
                if (current == null) return;
                ServerInfo edited = new ServerInfo(current.name, current.address, ServerInfo.ServerType.OTHER);
                edited.copyWithSettingsFrom(current);
                client.setScreen(new AddServerScreen(this, Text.translatable("manageServer.edit.title"), ok -> {
                    if (ok) {
                        current.copyWithSettingsFrom(edited);
                        servers.saveFile();
                        ping(current, true);
                    }
                    client.setScreen(this);
                }, edited));
            }
            case "delete" -> {
                if (current == null) return;
                client.setScreen(new ConfirmScreen(ok -> {
                    if (ok) {
                        servers.remove(current);
                        servers.saveFile();
                        selected = -1;
                    }
                    client.setScreen(this);
                }, Text.translatable("selectServer.deleteQuestion"), Text.translatable("selectServer.deleteWarning", current.name),
                    Text.translatable("selectServer.deleteButton"), Text.translatable("gui.cancel")));
            }
            case "refresh" -> {
                for (int i = 0; i < servers.size(); i++) ping(servers.get(i), true);
            }
            default -> { }
        }
    }

    private void connect(ServerInfo info) {
        ConnectScreen.connect(this, client, ServerAddress.parse(info.address), info, false, null);
    }

    @Override
    boolean enabled(Button b) {
        boolean has = selected >= 0 && selected < servers.size();
        return switch (b.id()) {
            case "join", "edit", "delete" -> has;
            default -> true;
        };
    }

    @Override
    public void tick() {
        pinger.tick();
    }

    @Override
    public void removed() {
        pinger.cancel();
        for (WorldIcon icon : icons.values()) icon.close();
        icons.clear();
        iconBytes.clear();
    }

    @Override
    public void close() {
        client.setScreen(parent);
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        // Drawn in render().
    }

    @Override
    public void render(DrawContext c, int mouseX, int mouseY, float delta) {
        LiquidGlass.backdrop(c, -2, -2, width + 4, height + 4, 1, 1.0F);
        drawMotes(c, seconds());
        float fade = clamp(spring("fade", Spring.PANEL, 0.0F).to(1.0F).value());
        c.drawTexture(RenderPipelines.GUI_TEXTURED, LOGO, 86, 14, 0F, 0F, 20, 20, 256, 256, 256, 256, alpha(fade) << 24 | 0xFFFFFF);
        text(c, "Servers", 112, 15, FG, 1.0F, fade);
        long online = 0;
        for (int i = 0; i < servers.size(); i++) if (servers.get(i).getStatus() == ServerInfo.Status.SUCCESSFUL) online++;
        text(c, servers.size() + " SAVED  ·  " + online + " ONLINE", 112, 26, FAINT, 0.58F, fade);

        LiquidGlass.card(c, listX - 8, listY - 8, listW + 16, listH + 16, 18, fade);
        drawRows(c, mouseX, mouseY, fade);
        for (int i = 0; i < buttons.size(); i++) drawButton(c, buttons.get(i), i, mouseX, mouseY, enabled(buttons.get(i)));
        c.createNewRootLayer();
        super.render(c, mouseX, mouseY, delta);
    }

    private void drawRows(DrawContext c, int mouseX, int mouseY, float fade) {
        float max = Math.max(0, servers.size() * (ROW_H + ROW_GAP) - ROW_GAP - listH);
        scrollTarget = Math.max(0, Math.min(scrollTarget, max));
        float offset = scroll.to(scrollTarget).value();
        if (servers.size() == 0) {
            String empty = "No servers yet. Add one, or use Direct to join once.";
            text(c, empty, listX + (listW - width(empty, 0.8F)) / 2.0F, listY + listH / 2.0F - 4, DIM, 0.8F, fade);
            return;
        }
        c.enableScissor(listX - 4, listY, listX + listW + 4, listY + listH);
        TextRenderer tr = client.textRenderer;
        for (int i = 0; i < servers.size(); i++) {
            float rowY = listY + i * (ROW_H + ROW_GAP) - offset;
            if (rowY + ROW_H < listY || rowY > listY + listH) continue;
            ServerInfo info = servers.get(i);
            int y = Math.round(rowY);
            boolean hot = mouseX >= listX && mouseX < listX + listW && mouseY >= y && mouseY < y + ROW_H
                && mouseY >= listY && mouseY < listY + listH;
            float pick = clamp(spring("pick:" + i, Spring.SMOOTH, 0.0F).to(i == selected ? 1.0F : hot ? 0.45F : 0.0F).value());
            float in = clamp(spring("row:" + i, Spring.PANEL, 0.0F).to(seconds() > 0.05F + i * 0.03F ? 1.0F : 0.0F).value());
            float a = fade * in;
            SmoothGuiShapes.rect(c, listX, y, listW, ROW_H, 12, alpha((0.05F + 0.08F * pick) * a) << 24 | 0xFFFFFF, false);
            if (pick > 0.02F) SmoothGuiShapes.rect(c, listX, y, listW, ROW_H, 12, alpha(0.35F * pick * a) << 24 | 0xFFFFFF, true);

            Identifier icon = icon(info);
            if (icon != null) {
                c.drawTexture(RenderPipelines.GUI_TEXTURED, icon, listX + 7, y + 7, 0F, 0F, 32, 32, 32, 32, alpha(a) << 24 | 0xFFFFFF);
            } else {
                SmoothGuiShapes.rect(c, listX + 7, y + 7, 32, 32, 8, alpha(0.08F * a) << 24 | 0xFFFFFF, false);
                HudSurfaceRenderer.icon(c, HudSurfaceRenderer.HudIcon.WORLD, listX + 16, y + 16, 14, alpha(0.7F * a) << 24 | 0xFFFFFF);
            }
            int textX = listX + 48;
            String name = info.name == null || info.name.isBlank() ? info.address : info.name;
            text(c, trim(name, listW - 200, 0.9F), textX, y + 7, FG, 0.9F, a);
            List<OrderedText> motd = tr.wrapLines(status(info), listW - 48 - 110);
            for (int line = 0; line < Math.min(2, motd.size()); line++) {
                c.drawText(tr, motd.get(line), textX, y + 19 + line * 10, alpha(0.85F * a) << 24 | 0xFFFFFF, false);
            }
            drawSignal(c, info, listX + listW - 12, y + 8, a);
            if (info.getStatus() == ServerInfo.Status.SUCCESSFUL && info.playerCountLabel != null) {
                int pw = tr.getWidth(info.playerCountLabel);
                c.drawText(tr, info.playerCountLabel, listX + listW - 12 - pw, y + 28, alpha(0.75F * a) << 24 | 0xFFFFFF, false);
            }
        }
        c.disableScissor();
        if (max > 0) {
            float track = listH, thumb = Math.max(24, track * listH / (listH + max));
            float thumbY = listY + (track - thumb) * (offset / max);
            SmoothGuiShapes.rect(c, listX + listW + 3, Math.round(thumbY), 2, Math.round(thumb), 1, alpha(0.35F * fade) << 24 | 0xFFFFFF, false);
        }
    }

    /** The main menu's slow drifting motes, so the two screens feel like one place. */
    private void drawMotes(DrawContext c, float t) {
        for (int i = 0; i < 40; i++) {
            float seed = i * 12.9898F;
            float sx = fract((float) Math.sin(seed) * 43758.547F), sy = fract((float) Math.sin(seed * 1.7F) * 24634.634F);
            float speed = 4.0F + 10.0F * fract(sx * 7.31F);
            float x = (sx * width + (float) Math.sin(t * 0.3F + i) * 12.0F) % width;
            float y = ((sy * (height + 20) - t * speed) % (height + 20) + height + 20) % (height + 20) - 10;
            float twinkle = 0.35F + 0.65F * (0.5F + 0.5F * (float) Math.sin(t * (0.8F + sx) + i));
            int size = i % 7 == 0 ? 2 : 1;
            SmoothGuiShapes.rect(c, Math.round(x), Math.round(y), size + 1, size + 1, 1, alpha(0.28F * twinkle) << 24 | 0xFFFFFF, false);
        }
    }

    private static float fract(float value) {
        return value - (float) Math.floor(value);
    }

    /** True when the pinger has written its "can't connect / can't resolve" message into the label. */
    private static boolean failed(ServerInfo info) {
        return info.label != null && info.label.getContent() instanceof net.minecraft.text.TranslatableTextContent key
            && key.getKey().startsWith("multiplayer.status.cannot");
    }

    private Text status(ServerInfo info) {
        return switch (info.getStatus()) {
            // The pinger reports failures (bad address, offline) through the label, not the status.
            case PINGING, INITIAL -> failed(info) ? Text.literal(info.label.getString()).withColor(0xFB7185)
                : Text.literal("Pinging" + ".".repeat(1 + (int) (seconds() * 3) % 3)).withColor(0x8C8F96);
            case UNREACHABLE -> info.label != null && !info.label.getString().isBlank()
                ? info.label : Text.literal("Can't reach this server").withColor(0xFB7185);
            case INCOMPATIBLE -> Text.literal("Needs " + (info.version == null ? "another version" : info.version.getString())).withColor(0xFBBF24);
            default -> info.label == null ? Text.empty() : info.label;
        };
    }

    /** Five ping bars, white when good, amber when slow, red when bad or unreachable. */
    private void drawSignal(DrawContext c, ServerInfo info, int right, int y, float a) {
        ServerInfo.Status status = info.getStatus();
        int bars;
        int color;
        if (status == ServerInfo.Status.SUCCESSFUL) {
            long ms = info.ping;
            bars = ms < 0 ? 0 : ms < 80 ? 5 : ms < 150 ? 4 : ms < 300 ? 3 : ms < 600 ? 2 : 1;
            color = ms < 150 ? 0xF2F2F6 : ms < 300 ? 0xFBBF24 : 0xFB7185;
            String label = ms + " ms";
            text(c, label, right - 30 - width(label, 0.6F), y + 1, DIM, 0.6F, a);
        } else if ((status == ServerInfo.Status.PINGING || status == ServerInfo.Status.INITIAL) && !failed(info)) {
            bars = 1 + (int) (seconds() * 6) % 5;
            color = 0x8C8F96;
        } else {
            bars = 0;
            color = 0xFB7185;
        }
        for (int i = 0; i < 5; i++) {
            int h = 3 + i * 2;
            boolean on = i < bars;
            SmoothGuiShapes.rect(c, right - 24 + i * 5, y + 11 - h, 3, h, 1,
                alpha((on ? 0.95F : 0.18F) * a) << 24 | (on ? color : 0xFFFFFF), false);
        }
    }

    private Identifier icon(ServerInfo info) {
        byte[] bytes = info.getFavicon();
        if (bytes == null) return null;
        String key = info.address;
        WorldIcon icon = icons.get(key);
        if (icon == null || !Arrays.equals(bytes, iconBytes.get(key))) {
            try {
                NativeImage image = NativeImage.read(ServerInfo.validateFavicon(bytes));
                if (icon == null) icons.put(key, icon = WorldIcon.forServer(client.getTextureManager(), key));
                icon.load(image);
                iconBytes.put(key, bytes);
            } catch (Exception exception) {
                info.setFavicon(null);
                return null;
            }
        }
        return icon.getTextureId();
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (super.mouseClicked(click, doubled)) return true;
        double mx = click.x(), my = click.y();
        if (click.button() != 0 || mx < listX || mx >= listX + listW || my < listY || my >= listY + listH) return false;
        int row = (int) ((my - listY + scroll.value()) / (ROW_H + ROW_GAP));
        if (row < 0 || row >= servers.size()) return false;
        long now = System.currentTimeMillis();
        boolean twice = row == lastClickRow && now - lastClickAt < 300;
        selected = row;
        lastClickRow = row;
        lastClickAt = now;
        if (twice) connect(servers.get(row));
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        scrollTarget -= (float) vertical * (ROW_H + ROW_GAP) * 0.8F;
        return true;
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        int key = input.key();
        if (key == GLFW.GLFW_KEY_ENTER && selected >= 0 && selected < servers.size()) {
            connect(servers.get(selected));
            return true;
        }
        if (key == GLFW.GLFW_KEY_DOWN || key == GLFW.GLFW_KEY_UP) {
            if (servers.size() > 0) selected = Math.floorMod(selected + (key == GLFW.GLFW_KEY_DOWN ? 1 : -1), servers.size());
            return true;
        }
        if (key == GLFW.GLFW_KEY_F5) {
            act("refresh");
            return true;
        }
        return super.keyPressed(input);
    }
}
