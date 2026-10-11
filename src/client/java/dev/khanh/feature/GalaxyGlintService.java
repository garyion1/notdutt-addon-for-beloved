package dev.khanh.feature;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.khanh.KhanhClient;
import java.io.InputStream;
import java.util.Optional;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.OutputTarget;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.client.render.TextureTransform;
import net.minecraft.client.render.LayeringTransform;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.item.ItemStack;
import net.minecraft.resource.Resource;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import org.joml.Matrix4f;
import java.util.HashMap;
import java.util.Map;

/**
 * Custom Glint as a wrap: held items, inventory items (never blocks) and worn armour are drawn
 * with the chosen PNG covering their pixels, cut to their own silhouette, slowly flowing.
 */
public final class GalaxyGlintService {
    private static final Identifier WRAP_TEXTURE = Identifier.of(KhanhClient.MOD_ID, "dynamic/item_wrap");
    /**
     * Places, flows and colours the wrap. The 2D part (scale, spin, drift direction) moves the PNG;
     * slots a 2D lookup never reads carry shader data (see wrap_sparkle.glsl): time [3].z, sparkle
     * strength [2].z and density [1].w, tint colour [0].z [1].z [2].x, tint amount [2].y and
     * brightness [2].w.
     */
    private static final TextureTransform FLOW = new TextureTransform("khanh_wrap_flow", () -> {
        float seconds = (Util.getMeasuringTimeMs() % 600_000L) / 1000.0F;
        float t = (float) (seconds * KhanhFeatures.decimal("custom_glint", "speed", 0.35));
        double direction = Math.toRadians(KhanhFeatures.decimal("custom_glint", "direction", 30.0));
        float scale = 1.0F / (float) KhanhFeatures.decimal("custom_glint", "scale", 1.0);
        float spin = (float) (seconds * KhanhFeatures.decimal("custom_glint", "spin", 0.0) * 0.5);
        float cos = (float) Math.cos(spin) * scale, sin = (float) Math.sin(spin) * scale;
        int tint = KhanhFeatures.color("custom_glint", "tint_color", 0xFFFF4FD8);
        float amount = KhanhFeatures.bool("custom_glint", "tint", false) ? (float) KhanhFeatures.decimal("custom_glint", "tint_amount", 0.6) : 0.0F;
        if (KhanhFeatures.bool("custom_glint", "rainbow", false)) {
            tint = java.awt.Color.HSBtoRGB((seconds * 0.15F) % 1.0F, 0.75F, 1.0F);
            amount = Math.max(amount, 0.65F);
        }
        float pulse = (float) KhanhFeatures.decimal("custom_glint", "pulse", 0.0);
        float brightness = (float) KhanhFeatures.decimal("custom_glint", "brightness", 1.0) * (1.0F + pulse * 0.45F * (float) Math.sin(seconds * 3.0));
        float sparkleSize = (float) KhanhFeatures.decimal("custom_glint", "sparkle_size", 1.0);
        return new Matrix4f()
            .m00(cos).m01(sin).m10(-sin).m11(cos)
            .m30((float) Math.cos(direction) * t * 0.36F).m31((float) Math.sin(direction) * t * 0.36F)
            .m32(seconds)
            .m22((float) KhanhFeatures.decimal("custom_glint", "sparkle", 3.0))
            .m13(1.0F / Math.max(0.25F, sparkleSize))
            .m02(((tint >> 16) & 255) / 255.0F).m12(((tint >> 8) & 255) / 255.0F).m20((tint & 255) / 255.0F)
            .m21(amount).m23(brightness);
    });
    private static final RenderPipeline ARMOR_PIPELINE = RenderPipeline.builder(RenderPipelines.ENTITY_SNIPPET)
        .withLocation(Identifier.of(KhanhClient.MOD_ID, "pipeline/armor_wrap"))
        .withFragmentShader(Identifier.of(KhanhClient.MOD_ID, "core/armor_wrap"))
        .withSampler("Sampler3")
        .withShaderDefine("ALPHA_CUTOUT", 0.1F).withShaderDefine("NO_OVERLAY").withShaderDefine("PER_FACE_LIGHTING")
        .withCull(false)
        .build();
    private static final Map<Identifier, RenderLayer> ARMOR_LAYERS = new HashMap<>();
    private static final RenderPipeline PIPELINE = RenderPipeline.builder(RenderPipelines.TRANSFORMS_PROJECTION_FOG_LIGHTING_SNIPPET)
        .withLocation(Identifier.of(KhanhClient.MOD_ID, "pipeline/item_wrap"))
        .withVertexShader(Identifier.of(KhanhClient.MOD_ID, "core/item_wrap"))
        .withFragmentShader(Identifier.of(KhanhClient.MOD_ID, "core/item_wrap"))
        .withSampler("Sampler0").withSampler("Sampler2").withSampler("Sampler3")
        .withBlend(BlendFunction.TRANSLUCENT)
        .withVertexFormat(VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL, VertexFormat.DrawMode.QUADS)
        .build();
    private static RenderLayer layer;
    private static String applied = "";
    private static boolean installed;

    private GalaxyGlintService() { }

    /** Set while the equipment renderer draws one piece: does that piece glint? (render thread only) */
    public static final ThreadLocal<Boolean> ARMOR_GLINTS = ThreadLocal.withInitial(() -> false);

    /**
     * True when this stack, shown in this context, should be wrapped: only items that would glint
     * anyway (enchanted gear and the like), never blocks, in hand, inventory, on the ground or in frames.
     */
    public static boolean wraps(ItemStack stack, ItemDisplayContext context) {
        if (!installed || stack == null || stack.isEmpty() || stack.getItem() instanceof BlockItem || !stack.hasGlint()) return false;
        return switch (context) {
            case GUI -> KhanhFeatures.bool("custom_glint", "inventory", true);
            case GROUND, FIXED -> KhanhFeatures.bool("custom_glint", "ground", true);
            case FIRST_PERSON_RIGHT_HAND, FIRST_PERSON_LEFT_HAND, THIRD_PERSON_RIGHT_HAND, THIRD_PERSON_LEFT_HAND -> true;
            default -> false;
        };
    }

    /** True when the armour piece being drawn should be wrapped: it glints, and Armour is on. */
    public static boolean wrapsArmor() {
        return installed && ARMOR_GLINTS.get() && KhanhFeatures.bool("custom_glint", "armor", true);
    }

    /** The armour wrap layer for one armour texture (each armour material has its own). */
    public static RenderLayer armorLayer(Identifier texture) {
        return ARMOR_LAYERS.computeIfAbsent(texture, id -> RenderLayer.of("khanh_armor_wrap", RenderSetup.builder(ARMOR_PIPELINE)
            .texture("Sampler0", id)
            .texture("Sampler3", WRAP_TEXTURE, () -> RenderSystem.getSamplerCache().getRepeated(FilterMode.NEAREST))
            .textureTransform(FLOW)
            .useLightmap().useOverlay()
            .layeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
            .crumbling()
            .outlineMode(RenderSetup.OutlineMode.AFFECTS_OUTLINE)
            .build()));
    }

    /** The wrap layer for items drawn from the item atlas. */
    public static RenderLayer layer() {
        if (layer == null) {
            layer = RenderLayer.of("khanh_item_wrap", RenderSetup.builder(PIPELINE)
                .texture("Sampler0", SpriteAtlasTexture.ITEMS_ATLAS_TEXTURE)
                .texture("Sampler3", WRAP_TEXTURE, () -> RenderSystem.getSamplerCache().getRepeated(FilterMode.NEAREST))
                .textureTransform(FLOW)
                .outputTarget(OutputTarget.ITEM_ENTITY_TARGET)
                .useLightmap().useOverlay().translucent()
                .outlineMode(RenderSetup.OutlineMode.AFFECTS_OUTLINE)
                .build());
        }
        return layer;
    }

    public static void tick(MinecraftClient client) {
        if (client == null || client.getTextureManager() == null || client.getResourceManager() == null) return;
        if (!KhanhFeatures.enabled("custom_glint")) {
            installed = false;
            return;
        }
        String theme = KhanhFeatures.choice("custom_glint", "theme", "Galaxy");
        double strength = KhanhFeatures.decimal("custom_glint", "strength", 1.0);
        String key = theme + ":" + strength;
        if (key.equals(applied)) {
            installed = true;
            return;
        }
        if (install(client, theme, strength)) {
            applied = key;
            installed = true;
        }
    }

    public static void restore(MinecraftClient client) {
        installed = false;
    }

    private static boolean install(MinecraftClient client, String theme, double strength) {
        Identifier source = Identifier.of(KhanhClient.MOD_ID, GlintPattern.texturePath(theme));
        Optional<Resource> resource = client.getResourceManager().getResource(source);
        if (resource.isEmpty()) {
            KhanhClient.LOGGER.warn("Missing item wrap texture {}", source);
            return false;
        }
        try (InputStream stream = resource.get().getInputStream()) {
            NativeImage image = NativeImage.read(stream);
            // Alpha carries Strength: the shader blends the item toward the PNG by this much.
            int alpha = (int) Math.round(255 * Math.max(0.0, Math.min(1.0, strength)));
            for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) {
                image.setColorArgb(x, y, alpha << 24 | (image.getColorArgb(x, y) & 0xFFFFFF));
            }
            client.getTextureManager().registerTexture(WRAP_TEXTURE, new NativeImageBackedTexture(() -> "Khanh Client Item Wrap", image));
            return true;
        } catch (Exception exception) {
            KhanhClient.LOGGER.warn("Could not load item wrap texture {}", source, exception);
            return false;
        }
    }
}
