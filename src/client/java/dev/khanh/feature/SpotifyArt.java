package dev.khanh.feature;

import dev.khanh.KhanhClient;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import javax.imageio.ImageIO;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

/**
 * Album art for the Spotify HUD: fetched off the render thread (an https image URL or a file the
 * Windows helper saved), scaled to 96x96, uploaded as a texture, with its average colour so the
 * card can glow in the album's colour.
 */
public final class SpotifyArt {
    public record Art(Identifier texture, int colour) { }

    private static final int SIZE = 96;
    private static final Identifier TEXTURE = Identifier.of(KhanhClient.MOD_ID, "dynamic/spotify_art");
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4))
        .followRedirects(HttpClient.Redirect.NORMAL).build();
    private static volatile String loadedKey = "";
    private static volatile String loadingKey = "";
    private static volatile Art art;

    private SpotifyArt() { }

    /** Art for {@code key}, starting a load if it changed; null while loading or when there is none. */
    public static Art current(String key) {
        if (key == null || key.isBlank()) return null;
        if (!key.equals(loadedKey) && !key.equals(loadingKey)) load(key);
        return key.equals(loadedKey) ? art : null;
    }

    private static void load(String key) {
        loadingKey = key;
        Thread.ofVirtual().name("khanh-spotify-art").start(() -> {
            try {
                byte[] bytes = fetch(key);
                BufferedImage source = bytes == null ? null : ImageIO.read(new ByteArrayInputStream(bytes));
                if (source == null) throw new IllegalStateException("unreadable image");
                NativeImage image = new NativeImage(SIZE, SIZE, false);
                long r = 0, gr = 0, b = 0;
                for (int y = 0; y < SIZE; y++) for (int x = 0; x < SIZE; x++) {
                    int argb = average(source, x, y) | 0xFF000000;
                    image.setColorArgb(x, y, argb);
                    r += (argb >> 16) & 255; gr += (argb >> 8) & 255; b += argb & 255;
                }
                int n = SIZE * SIZE;
                int colour = 0xFF000000 | (int) (r / n) << 16 | (int) (gr / n) << 8 | (int) (b / n);
                MinecraftClient.getInstance().execute(() -> {
                    if (!key.equals(loadingKey)) { image.close(); return; }
                    MinecraftClient.getInstance().getTextureManager().registerTexture(TEXTURE,
                        new NativeImageBackedTexture(() -> "khanh spotify art", image));
                    art = new Art(TEXTURE, colour);
                    loadedKey = key;
                });
            } catch (Exception exception) {
                KhanhClient.LOGGER.debug("Spotify art skipped: {}", exception.toString());
                MinecraftClient.getInstance().execute(() -> {
                    if (!key.equals(loadingKey)) return;
                    art = null;
                    loadedKey = key;
                });
            }
        });
    }

    /** Box-filtered sample of the source pixels that fall in target pixel (x, y); no AWT drawing needed. */
    private static int average(BufferedImage source, int x, int y) {
        int w = source.getWidth(), h = source.getHeight();
        int x0 = x * w / SIZE, x1 = Math.max(x0 + 1, (x + 1) * w / SIZE);
        int y0 = y * h / SIZE, y1 = Math.max(y0 + 1, (y + 1) * h / SIZE);
        int stepX = Math.max(1, (x1 - x0) / 4), stepY = Math.max(1, (y1 - y0) / 4);
        long r = 0, g = 0, b = 0, n = 0;
        for (int sy = y0; sy < Math.min(h, y1); sy += stepY) for (int sx = x0; sx < Math.min(w, x1); sx += stepX) {
            int c = source.getRGB(sx, sy);
            r += (c >> 16) & 255; g += (c >> 8) & 255; b += c & 255; n++;
        }
        if (n == 0) return 0xFF000000;
        return (int) (r / n) << 16 | (int) (g / n) << 8 | (int) (b / n);
    }

    private static byte[] fetch(String key) throws Exception {
        if (key.startsWith("resource:")) {
            var resource = MinecraftClient.getInstance().getResourceManager().getResource(Identifier.of(key.substring(9)));
            if (resource.isEmpty()) return null;
            try (var in = resource.get().getInputStream()) { return in.readAllBytes(); }
        }
        if (key.startsWith("file:")) {
            String path = key.substring(5);
            int hash = path.lastIndexOf('#');
            if (hash > 0) path = path.substring(0, hash);
            return Files.readAllBytes(Path.of(path.startsWith("//") ? URI.create("file:" + path).getPath() : path));
        }
        if (key.startsWith("https://") || key.startsWith("http://")) {
            HttpRequest request = HttpRequest.newBuilder(URI.create(key)).timeout(Duration.ofSeconds(6)).GET().build();
            HttpResponse<byte[]> response = HTTP.send(request, HttpResponse.BodyHandlers.ofByteArray());
            return response.statusCode() == 200 ? response.body() : null;
        }
        return null;
    }
}
