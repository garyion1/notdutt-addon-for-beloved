package dev.khanh.feature;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import java.io.IOException;

/** A local Khanh Client elytra texture; no server packets or skin changes. */
public final class ElytraColorService {
    private static final Identifier TEXTURE = Identifier.of("khanh", "elytra/coloured");
    private static int applied = -1;
    private static boolean installed;

    private ElytraColorService() { }

    public static Identifier texture(Identifier original) {
        return installed && KhanhFeatures.enabled("elytra_colors") ? TEXTURE : original;
    }

    public static void tick(MinecraftClient client) {
        if (client.getTextureManager() == null) return;
        if (!KhanhFeatures.enabled("elytra_colors")) {
            restore(client);
            return;
        }
        int color = KhanhFeatures.color("elytra_colors", "color", 0xFF8B7BFF);
        if (installed && color == applied) return;
        NativeImage base = null;
        var resource = client.getResourceManager().getResource(
            Identifier.ofVanilla("textures/entity/equipment/wings/elytra.png"));
        if (resource.isPresent()) {
            try (var stream = resource.get().getInputStream()) {
                base = NativeImage.read(stream);
            } catch (IOException ignored) { }
        }
        int scale = KhanhFabricTexture.SCALE;
        int width = (base == null ? 64 : base.getWidth()) * scale;
        int height = (base == null ? 32 : base.getHeight()) * scale;
        NativeImage image = new NativeImage(width, height, false);
        NativeImage fabric = KhanhFabricTexture.fabric(client);
        NativeImage logo = KhanhFabricTexture.logo(client);
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
            int source = base == null ? 0xFFFFFFFF : base.getColorArgb(x / scale, y / scale);
            double sourceLight = ((source >> 16 & 255) * 0.22 + (source >> 8 & 255) * 0.68
                + (source & 255) * 0.10) / 255.0;
            double light = base == null ? 0.75 + 0.19 * (1.0 - y / (double) height)
                + 0.06 * Math.sin((x + y * 0.5) * 0.45 / scale) : 0.25 + sourceLight * 0.65;
            int grain = KhanhFabricTexture.fabricLight(fabric, x, y) / 4;
            int r = Math.min(255, (int) ((color >> 16 & 255) * light) + grain);
            int g = Math.min(255, (int) ((color >> 8 & 255) * light) + grain);
            int b = Math.min(255, (int) ((color & 255) * light) + grain);
            image.setColorArgb(x, y, source & 0xFF000000 | r << 16 | g << 8 | b);
        }
        // Both broad wing faces use a 10x20 UV panel; keep the vanilla alpha silhouette.
        KhanhFabricTexture.paintLogo(image, logo, 24, 2, 10, 20);
        KhanhFabricTexture.paintLogo(image, logo, 36, 2, 10, 20);
        if (base != null) base.close();
        if (fabric != null) fabric.close();
        if (logo != null) logo.close();
        client.getTextureManager().registerTexture(TEXTURE,
            new NativeImageBackedTexture(() -> "Khanh Client Elytra Colour", image));
        installed = true;
        applied = color;
    }

    public static void restore(MinecraftClient client) {
        if (!installed || client == null || client.getTextureManager() == null) return;
        client.getTextureManager().destroyTexture(TEXTURE);
        installed = false;
        applied = -1;
    }
}
