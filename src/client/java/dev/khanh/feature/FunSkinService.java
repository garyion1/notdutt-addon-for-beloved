package dev.khanh.feature;

import dev.khanh.KhanhClient;
import java.util.Locale;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.player.PlayerSkinType;
import net.minecraft.entity.player.SkinTextures;
import net.minecraft.util.AssetInfo;
import net.minecraft.util.Identifier;

/**
 * Fun Skins: wear Homer Simpson, SpongeBob, Tung Tung Tung Sahur or one of Khanh Client's own
 * brainrot and TV-show skins. Local only: your player, first-person arms and previews change;
 * nobody else sees it. The outer layer can be built as real 3D blocks (see {@link FunSkin3DFeature}).
 */
public final class FunSkinService {
    public static final String[] SKINS = {
        "Homer Simpson", "SpongeBob", "Tung Tung Tung Sahur", "Toilet Head", "Shark Sneaks", "Croco Bomber", "Cappuccino Cup",
        "Sigma Grindset", "Banana Cat", "Sitcom Dad", "Game Show Host", "Angry Chef", "News Anchor", "Sitcom Alien"};

    private FunSkinService() { }

    /** The skin to show for this player, or null to keep their own. */
    public static SkinTextures replace(AbstractClientPlayerEntity player, SkinTextures original) {
        if (!KhanhFeatures.enabled("fun_skins") || original == null) return null;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || !player.getUuid().equals(client.player.getUuid())) return null;
        return apply(original);
    }

    /** File name of the chosen skin, e.g. "homer_simpson". */
    public static String textureName() {
        return KhanhFeatures.choice("fun_skins", "skin", SKINS[0]).toLowerCase(Locale.ROOT).replace(' ', '_');
    }

    /** True while your player wears a fun skin with its outer layer built as 3D blocks. */
    public static boolean layers3d() {
        return KhanhFeatures.enabled("fun_skins") && KhanhFeatures.bool("fun_skins", "layers_3d", true);
    }

    /** {@code original} wearing the chosen fun skin (its cape and elytra are kept). */
    public static SkinTextures apply(SkinTextures original) {
        String name = textureName();
        Identifier id = Identifier.of(KhanhClient.MOD_ID, "fun_skin/" + name);
        Identifier path = Identifier.of(KhanhClient.MOD_ID, "textures/skin/" + name + ".png");
        return new SkinTextures(new AssetInfo.TextureAssetInfo(id, path), original.cape(), original.elytra(),
            PlayerSkinType.WIDE, original.secure());
    }
}
