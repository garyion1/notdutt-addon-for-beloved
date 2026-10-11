package dev.khanh.feature;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import dev.khanh.KhanhClient;
import dev.khanh.mixin.SpriteContentsAccessor;
import java.util.List;
import java.util.Set;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.util.Identifier;

/**
 * Deepslate Opacity: deepslate stays visible but turns see-through, so ores, caves and bases
 * behind it show. Its texture alpha is lowered in the block atlas, it renders in the translucent
 * layer, faces behind it are no longer culled, and it stops hiding whole chunk sections.
 */
public final class DeepslateOpacityService {
    private static final Set<Block> NATURAL = Set.of(Blocks.DEEPSLATE, Blocks.INFESTED_DEEPSLATE);
    private static final Set<Block> ALL = Set.of(Blocks.DEEPSLATE, Blocks.INFESTED_DEEPSLATE, Blocks.COBBLED_DEEPSLATE,
        Blocks.POLISHED_DEEPSLATE, Blocks.DEEPSLATE_BRICKS, Blocks.CRACKED_DEEPSLATE_BRICKS, Blocks.DEEPSLATE_TILES,
        Blocks.CRACKED_DEEPSLATE_TILES, Blocks.CHISELED_DEEPSLATE, Blocks.REINFORCED_DEEPSLATE);
    private static final List<String> NATURAL_SPRITES = List.of("block/deepslate", "block/deepslate_top");
    private static final List<String> ALL_SPRITES = List.of("block/deepslate", "block/deepslate_top", "block/cobbled_deepslate",
        "block/polished_deepslate", "block/deepslate_bricks", "block/cracked_deepslate_bricks", "block/deepslate_tiles",
        "block/cracked_deepslate_tiles", "block/chiseled_deepslate", "block/reinforced_deepslate_side",
        "block/reinforced_deepslate_top", "block/reinforced_deepslate_bottom");

    private static volatile boolean active;
    private static volatile boolean allVariants;
    private static String appliedKey = "";
    private static Object appliedAtlas;

    private DeepslateOpacityService() { }

    /** Thread-safe: chunk builders ask this while meshing. */
    public static boolean isTarget(BlockState state) {
        return active && state != null && (allVariants ? ALL : NATURAL).contains(state.getBlock());
    }

    public static boolean lightThrough() {
        return active && KhanhFeatures.bool("deepslate_opacity", "light", true);
    }

    public static void tick(MinecraftClient client) {
        // The block atlas only exists once loading has finished.
        if (client == null || client.getTextureManager() == null || client.getOverlay() != null) return;
        try {
            apply(client);
        } catch (IllegalStateException notReady) {
            appliedKey = "";
        }
    }

    private static void apply(MinecraftClient client) {
        boolean enabled = KhanhFeatures.enabled("deepslate_opacity");
        boolean variants = KhanhFeatures.bool("deepslate_opacity", "all_variants", false);
        double opacity = KhanhFeatures.decimal("deepslate_opacity", "opacity", 0.35);
        boolean light = KhanhFeatures.bool("deepslate_opacity", "light", true);
        boolean layoutChanged = enabled != active || variants != allVariants;
        AbstractTexture texture = client.getTextureManager().getTexture(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE);
        if (!(texture instanceof SpriteAtlasTexture atlas)) return;

        String key = enabled + ":" + variants + ":" + opacity + ":" + light;
        if (key.equals(appliedKey) && atlas == appliedAtlas) return;
        // Put every sprite back first, then lower the ones the current setting covers.
        writeSprites(atlas, ALL_SPRITES, 1.0);
        if (enabled) writeSprites(atlas, variants ? ALL_SPRITES : NATURAL_SPRITES, opacity);
        active = enabled;
        allVariants = variants;
        boolean rebuild = layoutChanged || !key.equals(appliedKey) && light != lightFromKey(appliedKey);
        appliedKey = key;
        appliedAtlas = atlas;
        if (rebuild && client.worldRenderer != null && client.world != null) client.worldRenderer.reload();
    }

    private static boolean lightFromKey(String key) {
        return key.endsWith(":true");
    }

    private static void writeSprites(SpriteAtlasTexture atlas, List<String> ids, double opacity) {
        GpuTexture gpu = atlas.getGlTexture();
        if (gpu == null) return;
        for (String id : ids) {
            Sprite sprite = atlas.getSprite(Identifier.ofVanilla(id));
            if (sprite == null || sprite == atlas.getMissingSprite()) continue;
            NativeImage[] levels = ((SpriteContentsAccessor) sprite.getContents()).khanh$mipmapLevels();
            if (levels == null) continue;
            for (int level = 0; level < levels.length && level < gpu.getMipLevels(); level++) {
                NativeImage source = levels[level];
                if (source == null) continue;
                try (NativeImage copy = new NativeImage(source.getWidth(), source.getHeight(), false)) {
                    for (int y = 0; y < source.getHeight(); y++) for (int x = 0; x < source.getWidth(); x++) {
                        int argb = source.getColorArgb(x, y);
                        int alpha = (int) Math.round((argb >>> 24) * opacity);
                        copy.setColorArgb(x, y, alpha << 24 | (argb & 0xFFFFFF));
                    }
                    // Sprites sit inside a mipmap padding border: content starts at x + padding.
                    int padding = ((dev.khanh.mixin.SpriteAccessor) sprite).khanh$padding();
                    RenderSystem.getDevice().createCommandEncoder().writeToTexture(gpu, copy, level, 0,
                        (sprite.getX() + padding) >> level, (sprite.getY() + padding) >> level, source.getWidth(), source.getHeight(), 0, 0);
                } catch (RuntimeException exception) {
                    KhanhClient.LOGGER.warn("Deepslate Opacity could not update {}", id, exception);
                    return;
                }
            }
        }
    }
}
