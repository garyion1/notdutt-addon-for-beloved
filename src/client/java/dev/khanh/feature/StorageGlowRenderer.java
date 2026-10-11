package dev.khanh.feature;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.khanh.KhanhClient;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/**
 * Storage ESP's blocks, drawn with their own see-through layer instead of debug boxes (those dim
 * and grey out wherever a block hides them): a rich translucent fill and crisp bright edges.
 */
public final class StorageGlowRenderer {
    public record Glow(Box box, int colour, float pulse) { }
    /** A Sus Chunk Finder plate: one chunk at height {@code y}. */
    public record Plate(int x, int z, double y, int colour) { }

    private static final RenderPipeline SOLID_PIPELINE = RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
        .withLocation(Identifier.of(KhanhClient.MOD_ID, "pipeline/storage_glow_core"))
        .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.QUADS)
        .withBlend(BlendFunction.TRANSLUCENT)
        .withDepthWrite(false).withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST).withCull(false)
        .build();
    private static RenderLayer solidLayer;
    private static List<Glow> frame = List.of();
    private static List<Plate> plates = List.of();

    private StorageGlowRenderer() { }

    /** Called from the extraction pass with this frame's boxes. */
    public static void set(List<Glow> glows) {
        frame = glows;
    }

    public static void clear() {
        frame = List.of();
    }

    public static void setPlates(List<Plate> list) {
        plates = list;
    }

    public static void clearPlates() {
        plates = List.of();
    }

    public static void render(WorldRenderContext context) {
        if (context.worldState().cameraRenderState == null) return;
        Vec3d camera = context.worldState().cameraRenderState.pos;
        if (!plates.isEmpty()) {
            if (KhanhFeatures.enabled("sus_esp")) renderPlates(context, camera); else plates = List.of();
        }
        List<Glow> glows = frame;
        if (glows.isEmpty()) return;
        if (!KhanhFeatures.enabled("storage_esp")) { frame = List.of(); return; }
        List<Glow> local = new ArrayList<>(glows.size());
        for (Glow glow : glows) local.add(new Glow(glow.box().offset(-camera.x, -camera.y, -camera.z), glow.colour(), glow.pulse()));
        // Far to near in a fixed order: overlapping boxes always blend the same way, so colours
        // stay steady while you move instead of shifting as a per-frame sort reorders them.
        local.sort((a, b) -> Double.compare(centreDistance(b.box()), centreDistance(a.box())));
        MatrixStack matrices = context.matrices();
        // The world's end-of-frame buffer is drawn after every entity and chest model, so the
        // tint sits on top of chests and shulkers too, not only on plain blocks.
        MatrixStack.Entry entry = matrices.peek();
        VertexConsumer out = context.consumers().getBuffer(solidLayer());
        for (Glow glow : local) {
            int c = glow.colour();
            facing(entry, out, glow.box().expand(0.003), alpha(c, 0.45F * glow.pulse()));
            edges(entry, out, glow.box(), 0.018F, alpha(neon(c), 1.0F));
        }
    }

    /**
     * Sus chunk tiles with smooth ends: a rounded tile inset from the chunk edges, a soft glow
     * that is brightest along the rim and fades to the middle, a feathered halo that melts away
     * outside the edge, and a thin clean outline, all breathing gently.
     */
    private static void renderPlates(WorldRenderContext context, Vec3d camera) {
        MatrixStack.Entry e = context.matrices().peek();
        VertexConsumer out = context.consumers().getBuffer(solidLayer());
        double seconds = (System.nanoTime() % 3_600_000_000_000L) / 1.0e9;
        for (Plate plate : plates) {
            float x0 = (float) (plate.x() - camera.x), z0 = (float) (plate.z() - camera.z), y = (float) (plate.y() - camera.y);
            float cx = x0 + 8.0F, cz = z0 + 8.0F;
            int c = plate.colour();
            float pulse = 0.82F + 0.18F * (float) Math.sin(seconds * 1.8 + (plate.x() * 31 + plate.z()) * 0.01);
            float[][] ring = roundedTile(cx, cz, 7.3F, 3.2F);
            int n = ring.length;
            int rim = alpha(c, 0.50F * pulse), core = alpha(c, 0.10F * pulse), clear = alpha(c, 0.0F);
            int line = alpha(neon(c), 0.95F * pulse);
            for (int k = 0; k < n; k++) {
                float[] a = ring[k], b = ring[(k + 1) % n];
                // Inner glow: rim colour fading toward the middle (3 blocks in).
                float[] ai = toward(a, cx, cz, 3.0F), bi = toward(b, cx, cz, 3.0F);
                quad4(e, out, a[0], y, a[1], rim, b[0], y, b[1], rim, bi[0], y, bi[1], core, ai[0], y, ai[1], core);
                // Calm centre.
                quad4(e, out, ai[0], y, ai[1], core, bi[0], y, bi[1], core, cx, y, cz, core, cx, y, cz, core);
                // Feathered halo melting away outside the edge: the smooth ends.
                float[] ao = toward(a, cx, cz, -1.1F), bo = toward(b, cx, cz, -1.1F);
                quad4(e, out, ao[0], y, ao[1], clear, bo[0], y, bo[1], clear, b[0], y, b[1], rim, a[0], y, a[1], rim);
                // Thin outline.
                float[] al = toward(a, cx, cz, 0.06F), bl = toward(b, cx, cz, 0.06F);
                float[] am = toward(a, cx, cz, -0.06F), bm = toward(b, cx, cz, -0.06F);
                quad4(e, out, am[0], y + 0.02F, am[1], line, bm[0], y + 0.02F, bm[1], line, bl[0], y + 0.02F, bl[1], line, al[0], y + 0.02F, al[1], line);
            }
        }
    }

    /** Points around a rounded square (centre cx/cz, half size h, corner radius r), counter-clockwise. */
    private static float[][] roundedTile(float cx, float cz, float h, float r) {
        int arc = 8;
        float[][] points = new float[arc * 4][];
        float[][] centres = {{cx + h - r, cz + h - r}, {cx - h + r, cz + h - r}, {cx - h + r, cz - h + r}, {cx + h - r, cz - h + r}};
        int index = 0;
        for (int corner = 0; corner < 4; corner++) {
            for (int k = 0; k < arc; k++) {
                double angle = Math.toRadians(corner * 90.0 + k * 90.0 / (arc - 1));
                points[index++] = new float[] {centres[corner][0] + r * (float) Math.cos(angle), centres[corner][1] + r * (float) Math.sin(angle)};
            }
        }
        return points;
    }

    /** A point moved {@code distance} toward the centre (negative: away from it). */
    private static float[] toward(float[] p, float cx, float cz, float distance) {
        float dx = cx - p[0], dz = cz - p[1];
        float length = (float) Math.sqrt(dx * dx + dz * dz);
        if (length < 1.0e-4F) return p;
        float k = Math.min(distance / length, 0.999F);
        return new float[] {p[0] + dx * k, p[1] + dz * k};
    }

    /** A flat rectangle on the XZ plane at height {@code y}. */
    private static void slab(MatrixStack.Entry e, VertexConsumer out, float xa, float za, float xb, float zb, float y, int c) {
        quad(e, out, c, xa, y, za, xb, y, za, xb, y, zb, xa, y, zb);
    }

    private static void quad4(MatrixStack.Entry e, VertexConsumer out, float ax, float ay, float az, int ca, float bx, float by, float bz, int cb,
                              float cx, float cy, float cz, int cc, float dx, float dy, float dz, int cd) {
        out.vertex(e, ax, ay, az).color(ca);
        out.vertex(e, bx, by, bz).color(cb);
        out.vertex(e, cx, cy, cz).color(cc);
        out.vertex(e, dx, dy, dz).color(cd);
    }

    private static RenderLayer solidLayer() {
        if (solidLayer == null) solidLayer = RenderLayer.of("khanh_storage_glow_core", RenderSetup.builder(SOLID_PIPELINE).build());
        return solidLayer;
    }

    private static double centreDistance(Box b) {
        double x = (b.minX + b.maxX) / 2, y = (b.minY + b.maxY) / 2, z = (b.minZ + b.maxZ) / 2;
        return x * x + y * y + z * z;
    }

    /**
     * Only the faces turned toward the camera (at the origin), so a box is one even layer of its
     * colour from every angle instead of front and back faces stacking up.
     */
    private static void facing(MatrixStack.Entry e, VertexConsumer out, Box b, int c) {
        float x0 = (float) b.minX, y0 = (float) b.minY, z0 = (float) b.minZ, x1 = (float) b.maxX, y1 = (float) b.maxY, z1 = (float) b.maxZ;
        if (x0 < 0 && x1 > 0 && y0 < 0 && y1 > 0 && z0 < 0 && z1 > 0) return; // inside the box
        if (y0 > 0) quad(e, out, c, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1);
        if (y1 < 0) quad(e, out, c, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0);
        if (z0 > 0) quad(e, out, c, x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0);
        if (z1 < 0) quad(e, out, c, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1);
        if (x0 > 0) quad(e, out, c, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0);
        if (x1 < 0) quad(e, out, c, x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1);
    }

    /** The six faces of a box. */
    private static void shell(MatrixStack.Entry e, VertexConsumer out, Box b, int c) {
        float x0 = (float) b.minX, y0 = (float) b.minY, z0 = (float) b.minZ, x1 = (float) b.maxX, y1 = (float) b.maxY, z1 = (float) b.maxZ;
        quad(e, out, c, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1);
        quad(e, out, c, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0);
        quad(e, out, c, x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0);
        quad(e, out, c, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1);
        quad(e, out, c, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0);
        quad(e, out, c, x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1);
    }

    /** The twelve edges of a box as thin square beams. */
    private static void edges(MatrixStack.Entry e, VertexConsumer out, Box b, float t, int c) {
        double[] xs = {b.minX, b.maxX}, ys = {b.minY, b.maxY}, zs = {b.minZ, b.maxZ};
        for (double y : ys) for (double z : zs) shell(e, out, new Box(b.minX - t, y - t, z - t, b.maxX + t, y + t, z + t), c);
        for (double x : xs) for (double z : zs) shell(e, out, new Box(x - t, b.minY - t, z - t, x + t, b.maxY + t, z + t), c);
        for (double x : xs) for (double y : ys) shell(e, out, new Box(x - t, y - t, b.minZ - t, x + t, y + t, b.maxZ + t), c);
    }

    private static void quad(MatrixStack.Entry e, VertexConsumer out, int c, float ax, float ay, float az, float bx, float by, float bz,
                             float cx, float cy, float cz, float dx, float dy, float dz) {
        out.vertex(e, ax, ay, az).color(c);
        out.vertex(e, bx, by, bz).color(c);
        out.vertex(e, cx, cy, cz).color(c);
        out.vertex(e, dx, dy, dz).color(c);
    }

    private static int alpha(int colour, float k) {
        return Math.round(255 * Math.max(0.0F, Math.min(1.0F, k))) << 24 | (colour & 0xFFFFFF);
    }

    private static int neon(int colour) {
        int r = (colour >> 16) & 255, g = (colour >> 8) & 255, b = colour & 255;
        return 0xFF000000 | (r + (255 - r) * 2 / 5) << 16 | (g + (255 - g) * 2 / 5) << 8 | (b + (255 - b) * 2 / 5);
    }
}
