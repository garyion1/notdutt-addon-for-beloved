package dev.khanh.feature;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Vector3f;

/**
 * Khanh Client's solid cosmetics, built from filled, lit 3D shapes and attached to your player
 * model: head pieces follow your head, wings follow your body, and orbiting pieces circle you.
 * Only drawn on your own player.
 */
public final class SolidCosmeticsFeature extends FeatureRenderer<PlayerEntityRenderState, PlayerEntityModel> {
    private static final int RUBY = 0xFFE8344E, SAPPHIRE = 0xFF3E7BFF;

    public SolidCosmeticsFeature(FeatureRendererContext<PlayerEntityRenderState, PlayerEntityModel> context) {
        super(context);
    }

    @Override
    public void render(MatrixStack matrices, OrderedRenderCommandQueue queue, int light, PlayerEntityRenderState state,
                       float limbAngle, float limbDistance) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || state.id != client.player.getId() || state.invisible) return;
        float t = (float) ((System.nanoTime() % 3_600_000_000_000L) / 1.0e9);
        PlayerEntityModel model = getContextModel();

        boolean crown = on("crown"), ears = on("cat_ears"), horns = on("devil_horns"), hat = on("china_hat");
        if (crown || ears || horns || hat) {
            int crownColour = CosmeticsPlusService.colour("crown", t), earColour = CosmeticsPlusService.colour("cat_ears", t);
            int hornColour = CosmeticsPlusService.colour("devil_horns", t), hatColour = opaque(KhanhFeatures.color("china_hat", "color", 0xFF9C9CFF));
            float crownSize = size("crown"), earSize = size("cat_ears"), hornSize = size("devil_horns"), hatSize = size("china_hat");
            boolean showHat = hat && KhanhFeatures.bool("china_hat", "show_hat", true), halo = hat && KhanhFeatures.bool("china_hat", "halo", false);
            matrices.push();
            model.head.applyTransform(matrices);
            matrices.scale(-1.0F, -1.0F, 1.0F);
            queue.submitCustom(matrices, SolidShapes.layer(), (entry, out) -> {
                SolidShapes s = new SolidShapes(entry, out, SolidShapes.FULL_BRIGHT);
                if (crown) crown(s, crownSize, t, crownColour, showHat);
                if (ears) ears(s, earSize, earColour);
                if (horns) horns(s, hornSize, hornColour);
                if (showHat) chinaHat(s, hatSize, hatColour);
                if (halo) halo(s, hatSize, hatColour, t, showHat);
            });
            matrices.pop();
        }

        if (on("wings")) {
            int colour = opaque(KhanhFeatures.color("wings", "color", 0xFF9C9CFF));
            float size = size("wings");
            boolean feather = KhanhFeatures.choice("wings", "style", "Butterfly").equals("Feather");
            matrices.push();
            model.body.applyTransform(matrices);
            matrices.scale(-1.0F, -1.0F, 1.0F);
            queue.submitCustom(matrices, SolidShapes.layer(), (entry, out) ->
                wings(new SolidShapes(entry, out, SolidShapes.FULL_BRIGHT), size, colour, t, feather));
            matrices.pop();
        }

        boolean orbs = on("orbit_orbs"), saturn = on("saturn_ring"), shield = on("energy_shield"), cloud = on("rain_cloud");
        if (orbs || saturn || shield || cloud) {
            int orbColour = CosmeticsPlusService.colour("orbit_orbs", t), ringColour = CosmeticsPlusService.colour("saturn_ring", t);
            int shieldColour = CosmeticsPlusService.colour("energy_shield", t), rainColour = CosmeticsPlusService.colour("rain_cloud", t);
            float orbSize = size("orbit_orbs"), ringSize = size("saturn_ring"), shieldSize = size("energy_shield"), cloudSize = size("rain_cloud");
            int count = KhanhFeatures.number("orbit_orbs", "count", 3);
            matrices.push();
            matrices.scale(-1.0F, -1.0F, 1.0F);
            queue.submitCustom(matrices, SolidShapes.layer(), (entry, out) -> {
                SolidShapes s = new SolidShapes(entry, out, SolidShapes.FULL_BRIGHT);
                if (cloud) rainCloud(s, cloudSize, t, rainColour);
                if (orbs) orbitOrbs(s, orbSize, count, t, orbColour);
                if (saturn) saturnRing(s, ringSize, t, ringColour);
                if (shield) shield(s, shieldSize, t, shieldColour);
            });
            matrices.pop();
        }
    }

    // ---------------------------------------------------------------- head (head top is y = 0.5)

    private static void crown(SolidShapes s, float size, float t, int colour, boolean onHat) {
        int dark = SolidShapes.shade(colour, -0.28F), light = SolidShapes.shade(colour, 0.35F);
        s.push().translate(0, (onHat ? 0.86F : 0.53F) + (float) Math.sin(t * 2.0) * 0.015F, 0).scale(size, size, size).rotateY(t * 0.35F);
        s.annulus(0.255F, 0.31F, 0.1F, 24, dark, colour);
        s.push().translate(0, -0.05F, 0).annulus(0.25F, 0.325F, 0.025F, 24, dark, light);
        s.pop();
        s.push().translate(0, 0.05F, 0).annulus(0.25F, 0.325F, 0.022F, 24, dark, light);
        s.pop();
        int spikes = 8;
        for (int i = 0; i < spikes; i++) {
            float a = (float) (i * Math.PI * 2 / spikes);
            float x = (float) Math.cos(a) * 0.285F, z = (float) Math.sin(a) * 0.285F;
            float h = (i & 1) == 0 ? 0.17F : 0.11F;
            s.push().translate(x, 0.05F, z).rotateY(-a);
            s.spike(0, 0, 0, 0.042F, h, colour, dark);
            s.octahedron(0, h + 0.03F, 0, 0.026F, 0.034F, (i & 1) == 0 ? RUBY : SAPPHIRE, light);
            s.pop();
            // Gems set into the band between the spikes.
            float g = a + (float) Math.PI / spikes;
            s.push().translate((float) Math.cos(g) * 0.315F, 0, (float) Math.sin(g) * 0.315F).rotateY(-g).rotateZ((float) Math.PI / 2);
            s.octahedron(0, 0, 0, 0.022F, 0.016F, (i & 1) == 0 ? SAPPHIRE : RUBY, light);
            s.pop();
        }
        s.pop();
    }

    private static void ears(SolidShapes s, float size, int colour) {
        int dark = SolidShapes.shade(colour, -0.25F), inner = SolidShapes.shade(colour, 0.5F);
        for (int side = -1; side <= 1; side += 2) {
            s.push().translate(side * 0.155F, 0.49F, 0.03F).scale(size, size, size).rotateZ(-side * 0.22F);
            s.slab(new Vector3f(0, 0.06F, 0), List.of(new Vector3f(-0.1F, 0, 0), new Vector3f(0.1F, 0, 0), new Vector3f(side * 0.015F, 0.22F, 0)),
                0.06F, colour, dark, true);
            s.push().translate(0, 0.02F, -0.034F);
            s.slab(new Vector3f(0, 0.05F, 0), List.of(new Vector3f(-0.06F, 0, 0), new Vector3f(0.06F, 0, 0), new Vector3f(side * 0.01F, 0.15F, 0)),
                0.012F, inner, SolidShapes.shade(inner, -0.1F), true);
            s.pop();
            s.pop();
        }
    }

    private static void horns(SolidShapes s, float size, int colour) {
        int root = SolidShapes.shade(colour, -0.45F), tip = SolidShapes.shade(colour, 0.3F);
        for (int side = -1; side <= 1; side += 2) {
            List<Vector3f> path = new ArrayList<>();
            int n = 9;
            float[] radii = new float[n];
            for (int k = 0; k < n; k++) {
                float f = k / (float) (n - 1);
                path.add(new Vector3f(side * (0.16F + 0.15F * (float) Math.sin(f * Math.PI * 0.55)) * size,
                    0.44F + 0.3F * (float) Math.pow(f, 1.25) * size, (-0.07F - 0.07F * f * f) * size));
                radii[k] = (0.062F * (1.0F - f) + 0.003F) * size;
            }
            s.tube(path, radii, 10, root, tip);
        }
    }

    private static void chinaHat(SolidShapes s, float size, int colour) {
        int dark = SolidShapes.shade(colour, -0.25F);
        s.push().translate(0, 0.5F, 0).scale(size, size, size);
        s.frustum(0.02F, 0.52F, 0.0F, 0.3F, 24, 0, colour, dark, true);
        s.push().translate(0, 0.02F, 0).torus(0.52F, 0.022F, SolidShapes.shade(colour, -0.4F), 40, 6);
        s.pop();
        s.sphere(0, 0.33F, 0, 0.035F, SolidShapes.shade(colour, 0.4F), 6, 10);
        s.pop();
    }

    private static void halo(SolidShapes s, float size, int colour, float t, boolean overHat) {
        s.push().translate(0, (overHat ? 0.9F : 0.72F) + (float) Math.sin(t * 2.2) * 0.02F, 0).scale(size, size, size);
        s.torus(0.26F, 0.028F, SolidShapes.shade(colour, 0.55F), 40, 8);
        s.pop();
    }

    // ---------------------------------------------------------------- body (neck is y = 0, back is z = 0.125)

    private static void wings(SolidShapes s, float size, int colour, float t, boolean feather) {
        int inner = SolidShapes.shade(colour, -0.35F), outer = SolidShapes.shade(colour, 0.3F), bone = SolidShapes.shade(colour, -0.55F);
        float open = 0.55F + 0.3F * (float) Math.sin(t * 4.0);
        for (int side = -1; side <= 1; side += 2) {
            List<Vector3f> outline = new ArrayList<>();
            int n = 30;
            for (int i = 0; i <= n; i++) {
                double phi = -1.25 + 2.7 * i / n, rho;
                if (feather) rho = 0.36 + 0.5 * Math.cos((phi - 0.45) * 1.05) + 0.045 * Math.abs(Math.sin(phi * 11));
                else rho = 0.24 + 0.52 * Math.pow(Math.abs(Math.sin(2 * phi)), 0.8) * (phi > 0 ? 1.0 : 0.72);
                rho *= size;
                outline.add(new Vector3f((float) (side * rho * Math.cos(phi)), (float) (rho * Math.sin(phi)), 0));
            }
            s.push().translate(side * 0.07F, -0.2F, 0.16F).rotateY(-side * open);
            s.slab(new Vector3f(0, 0, 0), outline, 0.025F, inner, outer, false);
            // A curved bone along the top edge.
            List<Vector3f> spine = new ArrayList<>();
            float[] radii = new float[7];
            for (int k = 0; k < 7; k++) {
                float f = k / 6.0F;
                double phi = 0.75 - 0.35 * f, rho = (feather ? 0.82 : 0.7) * f * size;
                spine.add(new Vector3f((float) (side * rho * Math.cos(phi)), (float) (rho * Math.sin(phi)), 0));
                radii[k] = (0.03F * (1.0F - f) + 0.008F) * size;
            }
            s.tube(spine, radii, 6, bone, inner);
            s.pop();
        }
    }

    // ---------------------------------------------------------------- around you (root space; feet at y = -1.5)

    private static void orbitOrbs(SolidShapes s, float size, int count, float t, int colour) {
        int light = SolidShapes.shade(colour, 0.45F);
        for (int i = 0; i < count; i++) {
            for (int k = 0; k <= 4; k++) {
                double a = t * 1.6 + i * Math.PI * 2 / count - k * 0.2;
                float x = (float) Math.cos(a) * 0.78F * size, z = (float) Math.sin(a) * 0.78F * size;
                float y = -0.45F + (float) Math.sin(t * 2.2 + i - k * 0.12) * 0.25F;
                float r = 0.08F * size * (1.0F - k * 0.17F);
                int c = k == 0 ? colour : SolidShapes.alpha(colour, 0.75F - k * 0.15F);
                s.sphere(x, y, z, r, c, k == 0 ? light : c, 8, 12);
            }
        }
    }

    private static void saturnRing(SolidShapes s, float size, float t, int colour) {
        s.push().translate(0, -0.45F, 0).rotateX(0.38F).rotateZ(0.12F).rotateY(t * 0.5F);
        s.annulus(0.78F * size, 0.98F * size, 0.035F, 56, SolidShapes.shade(colour, 0.35F), SolidShapes.shade(colour, -0.2F));
        s.annulus(1.03F * size, 1.1F * size, 0.02F, 56, SolidShapes.alpha(colour, 0.8F), SolidShapes.alpha(SolidShapes.shade(colour, -0.35F), 0.8F));
        for (int i = 0; i < 6; i++) {
            float a = (float) (i * Math.PI / 3 + t * 0.4), r = (0.88F + 0.05F * (float) Math.sin(i * 7.0)) * size;
            s.push().translate((float) Math.cos(a) * r, 0.05F, (float) Math.sin(a) * r).rotateY(t * 2 + i).rotateX(t * 1.5F + i);
            s.cube(0, 0, 0, 0.03F * size, SolidShapes.shade(colour, i % 2 == 0 ? 0.5F : -0.1F));
            s.pop();
        }
        s.pop();
    }

    private static void shield(SolidShapes s, float size, float t, int colour) {
        float pulse = 0.5F + 0.5F * (float) Math.sin(t * 2.0);
        int a = SolidShapes.alpha(colour, 0.16F + 0.08F * pulse), b = SolidShapes.alpha(SolidShapes.shade(colour, 0.4F), 0.26F + 0.1F * pulse);
        s.push().translate(0, -0.45F, 0).rotateY(t * 0.3F);
        s.sphere(0, 0, 0, 1.08F * size, a, b, 10, 18);
        s.pop();
    }

    private static void rainCloud(SolidShapes s, float size, float t, int colour) {
        int cloud = SolidShapes.mix(0xFFDDE3EC, colour, 0.12F), shadow = SolidShapes.mix(0xFFB4BECC, colour, 0.18F);
        float drift = (float) Math.sin(t * 0.8) * 0.03F;
        s.push().translate(drift, 1.05F, 0).scale(size, size, size);
        float[][] puffs = {{-0.2F, 0, 0, 0.17F}, {0, 0.07F, 0, 0.22F}, {0.21F, 0, 0, 0.16F}, {0.05F, -0.04F, 0.1F, 0.17F},
            {-0.07F, -0.02F, -0.1F, 0.16F}, {0.12F, 0.04F, -0.06F, 0.15F}};
        for (float[] p : puffs) s.sphere(p[0], p[1], p[2], p[3], p[1] < 0 ? shadow : cloud, 8, 12);
        s.pop();
    }

    // ---------------------------------------------------------------- options

    private static boolean on(String id) {
        return KhanhFeatures.enabled(id);
    }

    private static float size(String id) {
        return (float) KhanhFeatures.decimal(id, "size", 1.0);
    }

    private static int opaque(int colour) {
        return colour | 0xFF000000;
    }
}
