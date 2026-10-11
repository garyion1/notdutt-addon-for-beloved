package dev.khanh.feature;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.khanh.KhanhClient;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;

/**
 * Discord Rich Presence over Discord's local IPC (no extra library): "Playing Khanh Client" with
 * your server line and a button to the Khanh Client Discord. Works with the Discord desktop app on
 * Windows (named pipe) and macOS / Linux (unix socket). Uses Khanh Client's own Discord application,
 * whose name is what Discord shows after "Playing".
 */
public final class DiscordPresenceService {
    /** Khanh Client's own Discord application: Discord shows its name after "Playing". */
    public static final String APP_ID = "";   // Khanh Client needs its own Discord app; Zero Client's ID must not be used here
    private static final long START = System.currentTimeMillis() / 1000L;
    private static final Object LOCK = new Object();
    private static volatile Pipe pipe;
    private static volatile String connectedId = "";
    private static volatile String lastActivity = "";
    private static volatile long retryAt;
    private static volatile long backoff = 5_000L;
    private static volatile long lastSent;
    private static volatile boolean busy;
    private static int ticks;

    private DiscordPresenceService() { }

    public static void tick(MinecraftClient client) {
        if (++ticks % 40 != 0 || busy) return;   // every 2 seconds
        String appId = APP_ID;
        boolean wanted = KhanhFeatures.enabled("discord_rpc") && appId.matches("\\d{15,22}");
        String activity = wanted ? activity(client).toString() : "";
        busy = true;
        Thread.ofVirtual().name("khanh-discord").start(() -> {
            try {
                update(appId, wanted, activity);
            } finally {
                busy = false;
            }
        });
    }

    private static void update(String appId, boolean wanted, String activity) {
        synchronized (LOCK) {
            if (!wanted || !appId.equals(connectedId)) {
                close();
                if (!wanted) return;
            }
            long now = System.currentTimeMillis();
            if (pipe == null) {
                if (now < retryAt) return;
                loadAssets();
                try {
                    pipe = Pipe.open();
                    JsonObject hello = new JsonObject();
                    hello.addProperty("v", 1);
                    hello.addProperty("client_id", appId);
                    pipe.write(0, hello.toString());
                    String ready = reply();                       // READY, or an error for a bad ID
                    if (!ready.contains("\"READY\"")) throw new IOException("Discord refused the app ID: " + ready);
                    connectedId = appId;
                    lastActivity = "";
                    backoff = 5_000L;
                    KhanhClient.LOGGER.info("Discord presence connected");
                } catch (IOException exception) {
                    KhanhClient.LOGGER.debug("Discord presence: not connected ({})", exception.toString());
                    close();
                    retryAt = now + backoff;
                    backoff = Math.min(30_000L, backoff * 2);   // 5s, 10s, 20s, 30s ...
                    return;
                }
            }
            // Re-send now and then even if nothing changed, so Discord never drops a stale card.
            if (activity.equals(lastActivity) && now - lastSent < 15_000L) return;
            try {
                JsonObject args = new JsonObject();
                args.addProperty("pid", ProcessHandle.current().pid());
                args.add("activity", JsonParser.parseString(activity));
                JsonObject frame = new JsonObject();
                frame.addProperty("cmd", "SET_ACTIVITY");
                frame.add("args", args);
                frame.addProperty("nonce", UUID.randomUUID().toString());
                pipe.write(1, frame.toString());
                String answer = reply();
                if (answer.contains("\"evt\":\"ERROR\"")) KhanhClient.LOGGER.warn("Discord presence rejected: {}", answer);
                lastActivity = activity;
                lastSent = now;
            } catch (IOException exception) {
                KhanhClient.LOGGER.debug("Discord presence lost: {}", exception.toString());
                close();
                retryAt = now + backoff;
            }
        }
    }

    /**
     * Waits for Discord's answer: pings are answered with a pong, a close frame ends the connection,
     * and a Discord that stops answering is dropped after five seconds instead of hanging.
     */
    private static String reply() throws IOException {
        Pipe current = pipe;
        Thread watchdog = Thread.ofVirtual().start(() -> {
            try {
                Thread.sleep(5_000L);
                current.close();
            } catch (InterruptedException ignored) { }
        });
        try {
            for (int i = 0; i < 8; i++) {
                Pipe.Frame frame = current.read();
                switch (frame.opcode()) {
                    case 3 -> current.write(4, frame.json());          // ping -> pong
                    case 2 -> throw new IOException("Discord closed the connection: " + frame.json());
                    default -> { return frame.json(); }
                }
            }
            throw new IOException("no answer from Discord");
        } finally {
            watchdog.interrupt();
        }
    }

    /** Where you are, the way Discord shows it. */
    /** The line under the name: just the server, like "donutsmp.net". */
    private static String place(MinecraftClient client) {
        String fixed = KhanhFeatures.text("discord_rpc", "server", "donutsmp.net").strip();
        if (!KhanhFeatures.bool("discord_rpc", "real_server", false)) {
            return fixed.isEmpty() ? "Minecraft" : fixed;
        }
        if (client.world == null) return "In the menus";
        if (client.isInSingleplayer()) return "Singleplayer";
        ServerInfo server = client.getCurrentServerEntry();
        if (server == null) return "Multiplayer";
        if (server.isRealm()) return "Realms";
        if (server.isLocal()) return "LAN world";
        return KhanhFeatures.bool("discord_rpc", "show_ip", true) ? server.address.toLowerCase(Locale.ROOT) : "Multiplayer";
    }

    /** The card Discord shows: details, state, elapsed time, logo and the Discord button. */
    private static JsonObject activity(MinecraftClient client) {
        JsonObject activity = new JsonObject();
        activity.addProperty("details", place(client));
        if (KhanhFeatures.bool("discord_rpc", "show_version", false)) activity.addProperty("state", "Khanh Client " + KhanhClient.VERSION);
        JsonObject timestamps = new JsonObject();
        timestamps.addProperty("start", START);
        activity.add("timestamps", timestamps);
        // Your uploaded art if it exists; with none, Discord shows the app's own icon instead.
        String image = KhanhFeatures.text("discord_rpc", "image", "logo").strip().toLowerCase(Locale.ROOT);
        if (!image.isEmpty() && ASSETS.contains(image)) {
            JsonObject assets = new JsonObject();
            assets.addProperty("large_image", image);
            assets.addProperty("large_text", "Khanh Client");
            activity.add("assets", assets);
        }
        JsonArray buttons = new JsonArray();
        JsonObject button = new JsonObject();
        button.addProperty("label", "Discord");
        button.addProperty("url", "https://discord.gg/khanh");
        buttons.add(button);
        activity.add("buttons", buttons);
        return activity;
    }

    /** Names of the images uploaded to the Khanh Client Discord app (read once per launch). */
    private static final java.util.Set<String> ASSETS = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static volatile boolean assetsLoaded;

    private static void loadAssets() {
        if (assetsLoaded) return;
        assetsLoaded = true;
        try {
            var request = java.net.http.HttpRequest.newBuilder(java.net.URI.create(
                "https://discord.com/api/v10/oauth2/applications/" + APP_ID + "/assets"))
                .timeout(java.time.Duration.ofSeconds(8)).GET().build();
            String body = java.net.http.HttpClient.newHttpClient().send(request, java.net.http.HttpResponse.BodyHandlers.ofString()).body();
            for (var element : JsonParser.parseString(body).getAsJsonArray()) {
                ASSETS.add(element.getAsJsonObject().get("name").getAsString().toLowerCase(Locale.ROOT));
            }
        } catch (Exception exception) {
            assetsLoaded = false;   // try again next connection
            KhanhClient.LOGGER.debug("Discord presence: could not list art assets ({})", exception.toString());
        }
    }

    private static void close() {
        Pipe current = pipe;
        pipe = null;
        connectedId = "";
        lastActivity = "";
        if (current != null) current.close();
    }

    /** One connection to Discord: frames are [opcode int32 LE][length int32 LE][JSON]. */
    private interface Pipe {
        record Frame(int opcode, String json) { }

        void write(int opcode, String json) throws IOException;
        Frame read() throws IOException;
        void close();

        static Pipe open() throws IOException {
            if (System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")) {
                IOException last = null;
                for (int i = 0; i < 10; i++) {
                    try {
                        return new WindowsPipe(new RandomAccessFile("\\\\.\\pipe\\discord-ipc-" + i, "rw"));
                    } catch (IOException exception) {
                        last = exception;
                    }
                }
                throw last;
            }
            for (String env : new String[] {"XDG_RUNTIME_DIR", "TMPDIR", "TMP", "TEMP"}) {
                String dir = System.getenv(env);
                if (dir == null) continue;
                for (String sub : new String[] {"", "app/com.discordapp.Discord/", "snap.discord/"}) {
                    for (int i = 0; i < 10; i++) {
                        Path path = Path.of(dir, sub + "discord-ipc-" + i);
                        if (!Files.exists(path)) continue;
                        SocketChannel channel = SocketChannel.open(StandardProtocolFamily.UNIX);
                        channel.connect(UnixDomainSocketAddress.of(path));
                        return new UnixPipe(channel);
                    }
                }
            }
            Path fallback = Path.of("/tmp/discord-ipc-0");
            if (!Files.exists(fallback)) throw new IOException("no Discord IPC socket");
            SocketChannel channel = SocketChannel.open(StandardProtocolFamily.UNIX);
            channel.connect(UnixDomainSocketAddress.of(fallback));
            return new UnixPipe(channel);
        }

        static ByteBuffer frame(int opcode, String json) {
            byte[] body = json.getBytes(StandardCharsets.UTF_8);
            ByteBuffer buffer = ByteBuffer.allocate(8 + body.length).order(ByteOrder.LITTLE_ENDIAN);
            buffer.putInt(opcode).putInt(body.length).put(body).flip();
            return buffer;
        }
    }

    private record UnixPipe(SocketChannel channel) implements Pipe {
        public void write(int opcode, String json) throws IOException {
            ByteBuffer buffer = Pipe.frame(opcode, json);
            while (buffer.hasRemaining()) channel.write(buffer);
        }

        public Frame read() throws IOException {
            ByteBuffer header = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
            while (header.hasRemaining()) if (channel.read(header) < 0) throw new IOException("Discord closed the connection");
            header.flip();
            int opcode = header.getInt();
            ByteBuffer body = ByteBuffer.allocate(Math.max(0, Math.min(header.getInt(), 1 << 20)));
            while (body.hasRemaining()) if (channel.read(body) < 0) throw new IOException("Discord closed the connection");
            return new Frame(opcode, new String(body.array(), StandardCharsets.UTF_8));
        }

        public void close() {
            try { channel.close(); } catch (IOException ignored) { }
        }
    }

    private record WindowsPipe(RandomAccessFile file) implements Pipe {
        public void write(int opcode, String json) throws IOException {
            file.write(Pipe.frame(opcode, json).array());
        }

        public Frame read() throws IOException {
            byte[] header = new byte[8];
            file.readFully(header);
            ByteBuffer buffer = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN);
            int opcode = buffer.getInt(0);
            byte[] body = new byte[Math.max(0, Math.min(buffer.getInt(4), 1 << 20))];
            file.readFully(body);
            return new Frame(opcode, new String(body, StandardCharsets.UTF_8));
        }

        public void close() {
            try { file.close(); } catch (IOException ignored) { }
        }
    }
}
