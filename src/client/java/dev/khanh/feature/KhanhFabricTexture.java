package dev.khanh.feature;

import dev.khanh.KhanhClient;
import java.io.IOException;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.util.Identifier;

/** Uses the Zero mark on the visible UV faces of local cape and elytra textures. */
final class KhanhFabricTexture {
    static final int SCALE = 8;
    private static final Identifier LOGO = Identifier.of(KhanhClient.MOD_ID, "textures/gui/khanh-logo.png");
    private static final Identifier FABRIC = Identifier.of(KhanhClient.MOD_ID, "textures/cosmetic/zero-fabric.png");

    private KhanhFabricTexture() { }

    static NativeImage logo(MinecraftClient client) { return read(client, LOGO); }
    static NativeImage fabric(MinecraftClient client) { return read(client, FABRIC); }

    private static NativeImage read(MinecraftClient client, Identifier id) {
        var resource = client.getResourceManager().getResource(id);
        if (resource.isEmpty()) return null;
        try (var stream = resource.get().getInputStream()) {
            return NativeImage.read(stream);
        } catch (IOException exception) {
            KhanhClient.LOGGER.warn("Could not load Khanh Client cosmetic artwork: {}", id, exception);
            return null;
        }
    }

    static int fabricLight(NativeImage fabric, int x, int y) {
        if (fabric == null) return 0;
        int sampleWidth = Math.max(1, fabric.getWidth() / 8);
        int sampleHeight = Math.max(1, fabric.getHeight() / 8);
        int pixel = fabric.getColorArgb(Math.floorMod(x, sampleWidth), Math.floorMod(y, sampleHeight));
        return ((pixel >> 16 & 255) + (pixel >> 8 & 255) + (pixel & 255)) / 3;
    }

    static void paintLogo(NativeImage atlas, NativeImage logo, int uvX, int uvY, int uvWidth, int uvHeight) {
        if (logo == null) return;
        int left = uvX * SCALE;
        int top = uvY * SCALE;
        int width = uvWidth * SCALE;
        int height = uvHeight * SCALE;
        int square = Math.min(width, height);
        int logoLeft = left + (width - square) / 2;
        int logoTop = top + (height - square) / 2;
        for (int dy = 0; dy < square; dy++) {
            int y = logoTop + dy;
            if (y < 0 || y >= atlas.getHeight()) continue;
            for (int dx = 0; dx < square; dx++) {
                int x = logoLeft + dx;
                if (x < 0 || x >= atlas.getWidth()) continue;
                int source = atlas.getColorArgb(x, y);
                int sourceAlpha = source >>> 24;
                if (sourceAlpha == 0) continue;
                int logoPixel = logo.getColorArgb(dx * logo.getWidth() / square,
                    dy * logo.getHeight() / square);
                int logoAlpha = logoPixel >>> 24;
                if (logoAlpha == 0) continue;
                int red = blend(source >> 16 & 255, logoPixel >> 16 & 255, logoAlpha);
                int green = blend(source >> 8 & 255, logoPixel >> 8 & 255, logoAlpha);
                int blue = blend(source & 255, logoPixel & 255, logoAlpha);
                atlas.setColorArgb(x, y, sourceAlpha << 24 | red << 16 | green << 8 | blue);
            }
        }
    }

    private static int blend(int underneath, int above, int alpha) {
        return (underneath * (255 - alpha) + above * alpha + 127) / 255;
    }
}
