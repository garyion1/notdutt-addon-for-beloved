package dev.khanh.feature;

import dev.khanh.KhanhClient;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;

/**
 * 3D skin layers for Fun Skins: every visible pixel of the skin's outer layer (hat, jacket,
 * sleeves, trousers) becomes a small lit block standing on that face of the body part, so hair,
 * hats, fins, ties and props have real depth. The flat vanilla outer layer is hidden meanwhile.
 */
public final class FunSkin3DFeature extends FeatureRenderer<PlayerEntityRenderState, PlayerEntityModel> {
    /** One block of the outer layer, in model pixels relative to its part's pivot. */
    private record Voxel(float x0, float y0, float z0, float x1, float y1, float z1, int colour) { }

    /**
     * How far a pixel stands out, in pixels. Fun Skins store it in the outer layer's alpha:
     * 255 = 1, 250 = 2, 245 = 3 ... down to 200 = 12. Lower alpha is ordinary see-through 1px.
     */
    static int depth(int alpha) {
        return alpha >= 200 ? 1 + (255 - alpha + 2) / 5 : 1;
    }
    /** A cuboid of the player model: its pixel origin and size, and the overlay's UV origin. */
    private record Part(String name, float ox, float oy, float oz, int w, int h, int d, int u, int v, float lift) { }

    private static final Part[] PARTS = {
        new Part("head", -4, -8, -4, 8, 8, 8, 32, 0, 0.5F),
        new Part("body", -4, 0, -2, 8, 12, 4, 16, 32, 0.25F),
        new Part("right_arm", -3, -2, -2, 4, 12, 4, 40, 32, 0.25F),
        new Part("left_arm", -1, -2, -2, 4, 12, 4, 48, 48, 0.25F),
        new Part("right_leg", -2, 0, -2, 4, 12, 4, 0, 32, 0.25F),
        new Part("left_leg", -2, 0, -2, 4, 12, 4, 0, 48, 0.25F),
    };
    private static final Map<String, Map<String, List<Voxel>>> CACHE = new HashMap<>();

    public FunSkin3DFeature(FeatureRendererContext<PlayerEntityRenderState, PlayerEntityModel> context) {
        super(context);
    }

    @Override
    public void render(MatrixStack matrices, OrderedRenderCommandQueue queue, int light, PlayerEntityRenderState state,
                       float limbAngle, float limbDistance) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || state.id != client.player.getId() || state.invisible || !FunSkinService.layers3d()) return;
        Map<String, List<Voxel>> voxels = voxels(client, FunSkinService.textureName());
        if (voxels.isEmpty()) return;
        PlayerEntityModel model = getContextModel();
        for (Part part : PARTS) {
            List<Voxel> list = voxels.get(part.name());
            ModelPart modelPart = modelPart(model, part.name());
            if (list == null || list.isEmpty() || modelPart == null || !modelPart.visible) continue;
            matrices.push();
            modelPart.applyTransform(matrices);
            matrices.scale(1.0F / 16.0F, 1.0F / 16.0F, 1.0F / 16.0F);
            queue.submitCustom(matrices, SolidShapes.layer(), (entry, out) -> {
                SolidShapes s = new SolidShapes(entry, out, light);
                for (Voxel v : list) s.box(v.x0(), v.y0(), v.z0(), v.x1(), v.y1(), v.z1(), v.colour());
            });
            matrices.pop();
        }
    }

    private static ModelPart modelPart(PlayerEntityModel model, String name) {
        return switch (name) {
            case "head" -> model.head;
            case "body" -> model.body;
            case "right_arm" -> model.rightArm;
            case "left_arm" -> model.leftArm;
            case "right_leg" -> model.rightLeg;
            default -> model.leftLeg;
        };
    }

    /** The skin's outer-layer pixels turned into blocks, worked out once per skin. */
    private static Map<String, List<Voxel>> voxels(MinecraftClient client, String skin) {
        return CACHE.computeIfAbsent(skin, name -> {
            Map<String, List<Voxel>> result = new HashMap<>();
            var resource = client.getResourceManager().getResource(Identifier.of(KhanhClient.MOD_ID, "textures/skin/" + name + ".png"));
            if (resource.isEmpty()) return result;
            try (var in = resource.get().getInputStream(); NativeImage image = NativeImage.read(in)) {
                for (Part part : PARTS) result.put(part.name(), build(image, part));
            } catch (Exception exception) {
                KhanhClient.LOGGER.warn("Fun Skins could not read {}", name, exception);
            }
            return result;
        });
    }

    /**
     * Blocks for one part. Each overlay face maps onto the cuboid face exactly as Minecraft lays a
     * skin out; a block spans from just above the base surface to one pixel out.
     */
    private static List<Voxel> build(NativeImage image, Part p) {
        List<Voxel> out = new ArrayList<>();
        float gap = p.lift() * 0.4F;
        int w = p.w(), h = p.h(), d = p.d(), u = p.u(), v = p.v();
        float x0 = p.ox(), y0 = p.oy(), z0 = p.oz(), x1 = x0 + w, y1 = y0 + h, z1 = z0 + d;
        // front (-z): u+d.., i -> x0+i
        face(image, out, u + d, v + d, w, h, (i, j, t) -> new float[] {x0 + i, y0 + j, z0 - gap - t, x0 + i + 1, y0 + j + 1, z0 - gap});
        // back (+z): i -> x1-1-i
        face(image, out, u + 2 * d + w, v + d, w, h, (i, j, t) -> new float[] {x1 - 1 - i, y0 + j, z1 + gap, x1 - i, y0 + j + 1, z1 + gap + t});
        // right side (-x): i -> z1-1-i
        face(image, out, u, v + d, d, h, (i, j, t) -> new float[] {x0 - gap - t, y0 + j, z1 - 1 - i, x0 - gap, y0 + j + 1, z1 - i});
        // left side (+x): i -> z0+i
        face(image, out, u + d + w, v + d, d, h, (i, j, t) -> new float[] {x1 + gap, y0 + j, z0 + i, x1 + gap + t, y0 + j + 1, z0 + i + 1});
        // top (-y): i -> x0+i, j -> z1-1-j
        face(image, out, u + d, v, w, d, (i, j, t) -> new float[] {x0 + i, y0 - gap - t, z1 - 1 - j, x0 + i + 1, y0 - gap, z1 - j});
        // bottom (+y): i -> x0+i, j -> z0+j
        face(image, out, u + d + w, v, w, d, (i, j, t) -> new float[] {x0 + i, y1 + gap, z0 + j, x0 + i + 1, y1 + gap + t, z0 + j + 1});
        return out;
    }

    private interface Mapper { float[] at(int i, int j, float depth); }

    private static void face(NativeImage image, List<Voxel> out, int fx, int fy, int fw, int fh, Mapper mapper) {
        for (int j = 0; j < fh; j++) for (int i = 0; i < fw; i++) {
            int argb = image.getColorArgb(fx + i, fy + j);
            int alpha = argb >>> 24;
            if (alpha < 24) continue;
            int depth = depth(alpha);
            float[] b = mapper.at(i, j, depth);
            // Depth-coded pixels are solid; genuinely see-through ones keep their alpha.
            out.add(new Voxel(b[0], b[1], b[2], b[3], b[4], b[5], alpha >= 200 ? argb | 0xFF000000 : argb));
        }
    }

    /** Hides the flat outer layer while the 3D one is drawn (your player only). */
    public static void hideFlatLayers(PlayerEntityRenderState state) {
        state.hatVisible = false;
        state.jacketVisible = false;
        state.leftSleeveVisible = false;
        state.rightSleeveVisible = false;
        state.leftPantsLegVisible = false;
        state.rightPantsLegVisible = false;
    }
}
