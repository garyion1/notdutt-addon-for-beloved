package dev.khanh.feature;

import dev.khanh.KhanhClient;
import java.util.Objects;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

/** A local Zero-branded cape. No remote skins, accounts or downloads are involved. */
public final class CustomCapeService {
    private static final Identifier TEXTURE = Identifier.of(KhanhClient.MOD_ID, "cape/custom");
    private static int appliedColor;
    private static String appliedStyle;
    private static boolean installed;

    private CustomCapeService() { }

    public static Identifier texture() {
        return installed && KhanhFeatures.enabled("custom_cape") ? TEXTURE : null;
    }

    public static void tick(MinecraftClient client) {
        if (client.getTextureManager() == null) return;
        if (!KhanhFeatures.enabled("custom_cape")) {
            restore(client);
            return;
        }
        int color = KhanhFeatures.color("custom_cape", "color", 0xFF746CBF);
        String style = KhanhFeatures.choice("custom_cape", "style", "Gradient");
        if (installed && color == appliedColor && Objects.equals(style, appliedStyle)) return;
        int scale = KhanhFabricTexture.SCALE;
        NativeImage image = new NativeImage(64 * scale, 32 * scale, false);
        NativeImage fabric = KhanhFabricTexture.fabric(client);
        NativeImage logo = KhanhFabricTexture.logo(client);
        for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) {
            double vertical = 1.0 - y / (45.0 * scale);
            double detail = switch (style) {
                case "Stripes" -> (x / (6 * scale) % 2 == 0 ? .55 : .35) * vertical;
                case "Night" -> (.28 + .12 * Math.sin(x * .38 / scale + y * .19 / scale)) * vertical;
                default -> (.40 + .10 * Math.sin(x * .11 / scale)) * vertical;
            };
            int grain = KhanhFabricTexture.fabricLight(fabric, x, y) / 4;
            int r = Math.max(0, Math.min(255, (int) ((color >> 16 & 255) * detail) + grain));
            int g = Math.max(0, Math.min(255, (int) ((color >> 8 & 255) * detail) + grain));
            int b = Math.max(0, Math.min(255, (int) ((color & 255) * detail) + grain));
            image.setColorArgb(x, y, 0xFF000000 | r << 16 | g << 8 | b);
        }
        // The cape cuboid's two broad visible faces in the vanilla 64x32 UV layout.
        KhanhFabricTexture.paintLogo(image, logo, 1, 1, 10, 16);
        KhanhFabricTexture.paintLogo(image, logo, 12, 1, 10, 16);
        if (fabric != null) fabric.close();
        if (logo != null) logo.close();
        client.getTextureManager().registerTexture(TEXTURE,
            new NativeImageBackedTexture(() -> "Khanh Client local cape", image));
        installed = true;
        appliedColor = color;
        appliedStyle = style;
    }

    public static void restore(MinecraftClient client) {
        if (!installed || client == null || client.getTextureManager() == null) return;
        client.getTextureManager().destroyTexture(TEXTURE);
        installed = false;
        appliedStyle = null;
    }
}
