package dev.khanh.feature;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.io.ByteArrayInputStream;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.entity.player.SkinTextures;
import net.minecraft.entity.player.PlayerSkinType;
import net.minecraft.util.AssetInfo;
import net.minecraft.util.Identifier;
import dev.khanh.KhanhClient;

/** Skin replacement based on public Mojang profile data; no account credentials are read. */
public final class SkinProtectService {
    private static final HttpClient HTTP = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(8)).build();
    private static volatile SkinTextures replacement;
    private static volatile String requested = "";
    private static volatile boolean fetching;
    private static volatile long retryAfter;
    private static String installedId = "";

    private SkinProtectService() { }

    public static void tick(MinecraftClient client) {
        if (!KhanhFeatures.enabled("skin_protect")) return;
        String username = KhanhFeatures.text("skin_protect", "skin_ign", "").trim();
        boolean useName = username.matches("[A-Za-z0-9_]{3,16}") && !username.equalsIgnoreCase("Steve");
        String skinId = KhanhFeatures.text("skin_protect", "skin_id", "").trim().toLowerCase(java.util.Locale.ROOT);
        if (!useName && skinId.matches("[0-9a-f]{16}")) {
            if (!skinId.equals(requested)) {
                requested = skinId;
                replacement = null;
                retryAfter = 0;
            }
            if (replacement == null && !fetching && System.currentTimeMillis() >= retryAfter) fetchNameMc(client, skinId);
            PlayerSkinType model = KhanhFeatures.bool("skin_protect", "slim_arms", false)
                ? PlayerSkinType.SLIM : PlayerSkinType.WIDE;
            if (replacement != null && replacement.model() != model)
                replacement = new SkinTextures(replacement.body(), null, null, model, false);
            return;
        }
        if (!useName) {
            requested = "Steve";
            replacement = DefaultSkinHelper.getSteve();
            return;
        }
        if (!username.equalsIgnoreCase(requested)) {
            requested = username;
            replacement = null;
            retryAfter = 0;
        }
        if (replacement != null || fetching || System.currentTimeMillis() < retryAfter) return;
        fetching = true;
        retryAfter = System.currentTimeMillis() + 60_000L;
        // Name -> UUID, then the full profile by UUID (the by-name answer has no skin attached).
        CompletableFuture.supplyAsync(() -> client.getApiServices().profileResolver().getProfileByName(username)
                .flatMap(found -> client.getApiServices().profileResolver().getProfileById(found.id()).or(() -> java.util.Optional.of(found))))
            .thenCompose(profile -> profile
                .map(client.getSkinProvider()::fetchSkinTextures)
                .orElseGet(() -> CompletableFuture.completedFuture(java.util.Optional.empty())))
            .whenComplete((result, error) -> {
                if (username.equalsIgnoreCase(requested) && error == null && result != null) {
                    result.ifPresent(skin -> replacement = skin);
                }
                fetching = false;
            });
    }

    private static void fetchNameMc(MinecraftClient client, String skinId) {
        fetching = true;
        retryAfter = System.currentTimeMillis() + 60_000L;
        HttpRequest request = HttpRequest.newBuilder(URI.create("https://s.namemc.com/i/" + skinId + ".png"))
            .timeout(Duration.ofSeconds(12)).GET().build();
        HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray()).whenComplete((response, error) -> {
            client.execute(() -> {
                try {
                    if (error != null || response == null || response.statusCode() != 200
                        || response.body().length > 256_000 || !skinId.equals(requested)) return;
                    NativeImage image = NativeImage.read(new ByteArrayInputStream(response.body()));
                    if (image.getWidth() != 64 || (image.getHeight() != 64 && image.getHeight() != 32)) {
                        image.close();
                        return;
                    }
                    Identifier id = Identifier.of(KhanhClient.MOD_ID, "skinprotect/" + skinId);
                    if (!installedId.isEmpty() && !installedId.equals(skinId))
                        client.getTextureManager().destroyTexture(Identifier.of(KhanhClient.MOD_ID, "skinprotect/" + installedId));
                    client.getTextureManager().registerTexture(id, new NativeImageBackedTexture(() -> "Khanh Client selected skin", image));
                    installedId = skinId;
                    replacement = new SkinTextures(new AssetInfo.TextureAssetInfo(id, id), null, null,
                        KhanhFeatures.bool("skin_protect", "slim_arms", false) ? PlayerSkinType.SLIM : PlayerSkinType.WIDE,
                        false);
                } catch (Exception exception) {
                    KhanhClient.LOGGER.debug("Could not load selected skin", exception);
                } finally {
                    fetching = false;
                }
            });
        });
    }

    public static SkinTextures replacement(UUID playerId) {
        SkinTextures skin = replacement;
        if (skin == null || playerId == null || !KhanhFeatures.enabled("skin_protect")) return null;
        MinecraftClient client = MinecraftClient.getInstance();
        UUID ownId = client.player == null ? client.getGameProfile().id() : client.player.getUuid();
        String scope = KhanhFeatures.choice("skin_protect", "apply_to", "Everyone");
        if ("Self".equals(scope) && !playerId.equals(ownId)) return null;
        if ("Others".equals(scope) && playerId.equals(ownId)) return null;
        return skin;
    }

}
