package dev.khanh.feature;

import dev.khanh.KhanhClient;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

/** Snapshot of NameMC's top 120 skin identifiers, displayed without bundling third-party artwork. */
public final class SkinCatalog {
    public static final String[] IDS = {
        "b84ac9a32c374ef1", "12b92a9206470fe2", "b3c20094038702c6", "c178117c21bd0a1c", "069f740e11523d1d", "74a241cff2ecf0fa",
        "015b8af09770d2d7", "b8c61cf07b087b66", "55d873354554d952", "86d7625ab28b17c3", "1351a035a6921001", "64cf8da7dac26f7c",
        "f5e7b3b473ca8846", "56ac4e160f5ac9da", "78fde0361cbbfe8d", "96bc166c0d33900d", "2e19169625723a70", "377c074f11c3773d",
        "4260aaf705c8d417", "1177782fb1167e32", "59e3a240bd150317", "37529af66bcdd70d", "0b28ab23170b1cf7", "69aada765cf09671",
        "6e42536667d82c40", "537836feca2abb7d", "a35033ec2b111c46", "52f3b8fab229a7b8", "48233eabea96fff9", "5173c405cba4b429",
        "fe6e788c348a025f", "3470d4cf2f55852e", "973a1fc841e82a5e", "38fa699c208462f1", "25ae72f29547a766", "5b235a5bf9a07429",
        "8a1a50b8b7d8c1cd", "1e5eb539df4cfdb3", "bc8429d1f2e15539", "eee639b70f9a43b3", "58c66a1b1ddc3eb5", "3bb7b782e4955b00",
        "6454e203bbec5d2b", "cf9d4bc6be2aff68", "354790babeda2bc0", "6b5f942d2f095b64", "c88a8be708bcaa05", "ceceb3234def58e6",
        "ee19018e20c0bfed", "e0a8bfb223df25c8", "9d7ab48a508270f8", "e9ef6447dbe2522a", "7d7f7c82d078eb97", "080d5d087c31841a",
        "fcec3b87bbc4df9e", "e7e73933a9924cb1", "cc464ada76f60e66", "1ca170cfe0e72712", "3afbb9c2e129a814", "b80558ff4b834410",
        "17730d34d3305ce9", "d0ede57259e6e354", "ca511ac942058c79", "6a33badb14ce3d32", "7c15dba0a29d141f", "a5923d905aaad29b",
        "3ce30faa1ac401b2", "91890e835d64c988", "2317780b0855c7b6", "c2f25f8372563a90", "c01009280ddba0e3", "61025d58f5bd6241",
        "9942f13fd3c43d55", "e6829aef112eafa8", "4056dde9cc057115", "5cee519cc6070b5a", "b4d02fcc7485f700", "edd9f0fbfe127e1c",
        "c20c7ad2af0d7fea", "0da38a79982d19d4", "408f4cba2c51a3f7", "5cf56f43f0dfecec", "9770ad44450a63c5", "3523ed3910ae942f",
        "deb7f16a8aa3de74", "da5e92f585e0d7cb", "b1e32d012f962c93", "701f1d7c083ea266", "a34fa13d7d876ae0", "18ff17a56b2ee109",
        "1507b8e973f684f7", "23aa8f1c5180d17a", "ef39b1f96d445f36", "d601f9bf43537d11", "2af8c11db5fe6061", "b8d025746d41fc09",
        "8a605f546ff45a2f", "36b158960bcb3bae", "f8b844fc8ebd6c37", "f0edfefb29f2c463", "e46a73a4564950fa", "b39c4398ffa6988c",
        "f0eef7fed60936e8", "cf10ff1839c1e473", "9cd0e5bf85a56fb4", "94526ba11702f4a5", "4254ed54a9b4116c", "ec82e6323e7c18cf",
        "a862e937cbbfaa49", "e1ca7f628411e455", "1f8d87f0ce0db068", "cbf1f19e150568cf", "57c595c5cf3513e1", "a089414d02940479",
        "e5381b327c119248", "dec8a12db26453a1", "395e83ffd111b574", "aba97be291f29ae0", "f94614494cf980cd", "299b46ae1bdc06ea"
    };
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(6)).build();
    private static final Map<String, Preview> PREVIEWS = new HashMap<>();
    private static final Set<String> PENDING = new HashSet<>();
    private static final Map<String, Long> RETRY_AFTER = new HashMap<>();

    private SkinCatalog() { }

    public static Preview preview(MinecraftClient client, String skinId) {
        Preview existing = PREVIEWS.get(skinId);
        if (existing != null || System.currentTimeMillis() < RETRY_AFTER.getOrDefault(skinId, 0L)
            || !PENDING.add(skinId)) return existing;
        RETRY_AFTER.put(skinId, System.currentTimeMillis() + 60_000L);
        HttpRequest request = HttpRequest.newBuilder(URI.create(
            "https://s.namemc.com/2d/skin/face.png?id=" + skinId + "&scale=4"))
            .timeout(Duration.ofSeconds(10)).GET().build();
        HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray()).whenComplete((response, error) ->
            client.execute(() -> {
                try {
                    if (error != null || response == null || response.statusCode() != 200
                        || response.body().length > 128_000) return;
                    NativeImage image = NativeImage.read(new ByteArrayInputStream(response.body()));
                    Identifier id = Identifier.of(KhanhClient.MOD_ID, "skinpreview/" + skinId);
                    int width = image.getWidth(), height = image.getHeight();
                    client.getTextureManager().registerTexture(id,
                        new NativeImageBackedTexture(() -> "Khanh Client skin preview", image));
                    PREVIEWS.put(skinId, new Preview(id, width, height));
                } catch (Exception exception) {
                    KhanhClient.LOGGER.debug("Could not load skin preview", exception);
                } finally {
                    PENDING.remove(skinId);
                }
            }));
        return null;
    }

    public record Preview(Identifier id, int width, int height) { }
}
