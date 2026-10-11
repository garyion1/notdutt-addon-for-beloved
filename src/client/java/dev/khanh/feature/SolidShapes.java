package dev.khanh.feature;

import dev.khanh.KhanhClient;
import java.util.ArrayDeque;
import java.util.List;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Filled, lit 3D shapes for cosmetics: boxes, cones, spheres, tori, tubes and extruded slabs,
 * drawn with a plain white texture so vertex colours and the game's entity lighting do the shading.
 * Coordinates are blocks, +y up. Keeps its own transform stack so one submit can hold many parts.
 */
public final class SolidShapes {
    public static final Identifier WHITE = Identifier.of(KhanhClient.MOD_ID, "textures/misc/white.png");
    public static final int FULL_BRIGHT = 0xF000F0;

    private final VertexConsumer out;
    private final int light;
    private Matrix4f pose;
    private Matrix3f normal;
    private final ArrayDeque<Matrix4f> poses = new ArrayDeque<>();
    private final ArrayDeque<Matrix3f> normals = new ArrayDeque<>();

    public SolidShapes(MatrixStack.Entry entry, VertexConsumer out, int light) {
        this.out = out;
        this.light = light;
        this.pose = new Matrix4f(entry.getPositionMatrix());
        this.normal = new Matrix3f(entry.getNormalMatrix());
    }

    public static RenderLayer layer() {
        return RenderLayers.entityTranslucent(WHITE);
    }

    // ---------------------------------------------------------------- transforms

    public SolidShapes push() {
        poses.push(new Matrix4f(pose));
        normals.push(new Matrix3f(normal));
        return this;
    }

    public SolidShapes pop() {
        pose = poses.pop();
        normal = normals.pop();
        return this;
    }

    public SolidShapes translate(float x, float y, float z) {
        pose.translate(x, y, z);
        return this;
    }

    public SolidShapes rotateX(float radians) { return rotate(new Quaternionf().rotationX(radians)); }
    public SolidShapes rotateY(float radians) { return rotate(new Quaternionf().rotationY(radians)); }
    public SolidShapes rotateZ(float radians) { return rotate(new Quaternionf().rotationZ(radians)); }

    private SolidShapes rotate(Quaternionf q) {
        pose.rotate(q);
        normal.rotate(q);
        return this;
    }

    /** Scaling; normals are re-normalised per vertex, so any scale is safe. */
    public SolidShapes scale(float x, float y, float z) {
        pose.scale(x, y, z);
        normal.scale(1.0F / x, 1.0F / y, 1.0F / z);
        return this;
    }

    // ---------------------------------------------------------------- primitives

    /** A quad with its normal pointing away from {@code inside}. */
    public void quad(Vector3f a, Vector3f b, Vector3f c, Vector3f d, int ca, int cb, int cc, int cd, Vector3f inside) {
        Vector3f n = new Vector3f(c).sub(a).cross(new Vector3f(d).sub(b));
        if (n.lengthSquared() < 1.0e-14F) n = new Vector3f(b).sub(a).cross(new Vector3f(c).sub(a));
        if (n.lengthSquared() < 1.0e-14F) return;
        Vector3f mid = new Vector3f(a).add(b).add(c).add(d).mul(0.25F);
        if (inside != null && n.dot(mid.sub(inside)) < 0) n.negate();
        emit(a, b, c, d, ca, cb, cc, cd, n);
    }

    public void quad(Vector3f a, Vector3f b, Vector3f c, Vector3f d, int colour, Vector3f inside) {
        quad(a, b, c, d, colour, colour, colour, colour, inside);
    }

    public void tri(Vector3f a, Vector3f b, Vector3f c, int colour, Vector3f inside) {
        quad(a, b, c, c, colour, colour, colour, colour, inside);
    }

    private void emit(Vector3f a, Vector3f b, Vector3f c, Vector3f d, int ca, int cb, int cc, int cd, Vector3f n) {
        Vector3f tn = normal.transform(new Vector3f(n));
        if (tn.lengthSquared() > 0) tn.normalize();
        vertex(a, ca, 0, 0, tn);
        vertex(b, cb, 1, 0, tn);
        vertex(c, cc, 1, 1, tn);
        vertex(d, cd, 0, 1, tn);
    }

    private void vertex(Vector3f p, int colour, float u, float v, Vector3f n) {
        Vector3f t = pose.transformPosition(p, new Vector3f());
        out.vertex(t.x, t.y, t.z).color(colour).texture(u, v).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(n.x, n.y, n.z);
    }

    // ---------------------------------------------------------------- shapes

    public void box(float x0, float y0, float z0, float x1, float y1, float z1, int colour) {
        Vector3f[] p = {
            new Vector3f(x0, y0, z0), new Vector3f(x1, y0, z0), new Vector3f(x1, y0, z1), new Vector3f(x0, y0, z1),
            new Vector3f(x0, y1, z0), new Vector3f(x1, y1, z0), new Vector3f(x1, y1, z1), new Vector3f(x0, y1, z1)};
        Vector3f centre = new Vector3f((x0 + x1) / 2, (y0 + y1) / 2, (z0 + z1) / 2);
        quad(p[0], p[1], p[2], p[3], colour, centre);
        quad(p[4], p[5], p[6], p[7], colour, centre);
        quad(p[0], p[1], p[5], p[4], colour, centre);
        quad(p[3], p[2], p[6], p[7], colour, centre);
        quad(p[0], p[3], p[7], p[4], colour, centre);
        quad(p[1], p[2], p[6], p[5], colour, centre);
    }

    /** Box centred on a point. */
    public void cube(float x, float y, float z, float half, int colour) {
        box(x - half, y - half, z - half, x + half, y + half, z + half, colour);
    }

    /**
     * A vertical cone or cylinder from radius {@code r0} at {@code y} to {@code r1} at {@code y + h}.
     * Side panels alternate between the two colours, which reads as facets or stripes.
     */
    public void frustum(float y, float r0, float r1, float h, int sides, float spin, int colour, int alternate, boolean caps) {
        Vector3f bottom = new Vector3f(0, y, 0), top = new Vector3f(0, y + h, 0);
        for (int i = 0; i < sides; i++) {
            float a = spin + i * (float) Math.PI * 2 / sides, b = spin + (i + 1) * (float) Math.PI * 2 / sides;
            Vector3f p0 = new Vector3f(cos(a) * r0, y, sin(a) * r0), p1 = new Vector3f(cos(b) * r0, y, sin(b) * r0);
            Vector3f q0 = new Vector3f(cos(a) * r1, y + h, sin(a) * r1), q1 = new Vector3f(cos(b) * r1, y + h, sin(b) * r1);
            int c = (i & 1) == 0 ? colour : alternate;
            quad(p0, p1, q1, q0, c, new Vector3f(0, y + h / 2, 0));
            if (caps && r0 > 0) emit(bottom, p1, p0, p0, colour, colour, colour, colour, new Vector3f(0, -1, 0));
            if (caps && r1 > 0) emit(top, q0, q1, q1, colour, colour, colour, colour, new Vector3f(0, 1, 0));
        }
    }

    public void sphere(float x, float y, float z, float r, int colour, int rings, int segments) {
        sphere(x, y, z, r, colour, colour, rings, segments);
    }

    /** A sphere whose panels alternate colours like a checkerboard. */
    public void sphere(float x, float y, float z, float r, int colour, int alternate, int rings, int segments) {
        Vector3f centre = new Vector3f(x, y, z);
        for (int i = 0; i < rings; i++) {
            float la = (float) (-Math.PI / 2 + Math.PI * i / rings), lb = (float) (-Math.PI / 2 + Math.PI * (i + 1) / rings);
            for (int j = 0; j < segments; j++) {
                float a = (float) (Math.PI * 2 * j / segments), b = (float) (Math.PI * 2 * (j + 1) / segments);
                quad(onSphere(centre, r, la, a), onSphere(centre, r, la, b), onSphere(centre, r, lb, b), onSphere(centre, r, lb, a),
                    ((i + j) & 1) == 0 ? colour : alternate, centre);
            }
        }
    }

    private static Vector3f onSphere(Vector3f c, float r, float lat, float lon) {
        return new Vector3f(c.x + r * cos(lat) * cos(lon), c.y + r * sin(lat), c.z + r * cos(lat) * sin(lon));
    }

    /** A doughnut lying in the XZ plane. */
    public void torus(float major, float minor, int colour, int segments, int tube) {
        for (int i = 0; i < segments; i++) {
            float a = (float) (Math.PI * 2 * i / segments), b = (float) (Math.PI * 2 * (i + 1) / segments);
            float mid = (a + b) / 2;
            Vector3f core = new Vector3f(cos(mid) * major, 0, sin(mid) * major);
            for (int j = 0; j < tube; j++) {
                float u = (float) (Math.PI * 2 * j / tube), v = (float) (Math.PI * 2 * (j + 1) / tube);
                quad(onTorus(major, minor, a, u), onTorus(major, minor, b, u), onTorus(major, minor, b, v), onTorus(major, minor, a, v),
                    colour, core);
            }
        }
    }

    private static Vector3f onTorus(float major, float minor, float around, float tube) {
        float r = major + minor * cos(tube);
        return new Vector3f(cos(around) * r, minor * sin(tube), sin(around) * r);
    }

    /** A flat ring band (like Saturn's) in the XZ plane, with real thickness. */
    public void annulus(float inner, float outer, float thickness, int segments, int colourInner, int colourOuter) {
        float h = thickness / 2;
        for (int i = 0; i < segments; i++) {
            float a = (float) (Math.PI * 2 * i / segments), b = (float) (Math.PI * 2 * (i + 1) / segments);
            Vector3f ia = new Vector3f(cos(a) * inner, 0, sin(a) * inner), ib = new Vector3f(cos(b) * inner, 0, sin(b) * inner);
            Vector3f oa = new Vector3f(cos(a) * outer, 0, sin(a) * outer), ob = new Vector3f(cos(b) * outer, 0, sin(b) * outer);
            emit(up(ia, h), up(ib, h), up(ob, h), up(oa, h), colourInner, colourInner, colourOuter, colourOuter, new Vector3f(0, 1, 0));
            emit(up(ia, -h), up(oa, -h), up(ob, -h), up(ib, -h), colourInner, colourOuter, colourOuter, colourInner, new Vector3f(0, -1, 0));
            float mid = (a + b) / 2;
            emit(up(oa, -h), up(oa, h), up(ob, h), up(ob, -h), colourOuter, colourOuter, colourOuter, colourOuter,
                new Vector3f(cos(mid), 0, sin(mid)));
            emit(up(ia, -h), up(ib, -h), up(ib, h), up(ia, h), colourInner, colourInner, colourInner, colourInner,
                new Vector3f(-cos(mid), 0, -sin(mid)));
        }
    }

    private static Vector3f up(Vector3f p, float h) {
        return new Vector3f(p.x, p.y + h, p.z);
    }

    /** A four-sided pyramid standing on {@code y}, pointing up. */
    public void spike(float x, float y, float z, float half, float height, int colour, int alternate) {
        Vector3f apex = new Vector3f(x, y + height, z);
        Vector3f[] base = {new Vector3f(x - half, y, z - half), new Vector3f(x + half, y, z - half),
            new Vector3f(x + half, y, z + half), new Vector3f(x - half, y, z + half)};
        Vector3f inside = new Vector3f(x, y + height * 0.3F, z);
        for (int i = 0; i < 4; i++) tri(base[i], base[(i + 1) % 4], apex, (i & 1) == 0 ? colour : alternate, inside);
        quad(base[0], base[1], base[2], base[3], alternate, inside);
    }

    /** A gem: two pyramids base to base. */
    public void octahedron(float x, float y, float z, float radius, float height, int colour, int alternate) {
        Vector3f top = new Vector3f(x, y + height, z), bottom = new Vector3f(x, y - height, z), centre = new Vector3f(x, y, z);
        Vector3f[] ring = {new Vector3f(x + radius, y, z), new Vector3f(x, y, z + radius), new Vector3f(x - radius, y, z), new Vector3f(x, y, z - radius)};
        for (int i = 0; i < 4; i++) {
            Vector3f a = ring[i], b = ring[(i + 1) % 4];
            tri(a, b, top, (i & 1) == 0 ? colour : alternate, centre);
            tri(b, a, bottom, (i & 1) == 0 ? alternate : colour, centre);
        }
    }

    /**
     * A round tube following a path, with a radius per point (0 makes a point). Frames are carried
     * along the path so the tube never twists.
     */
    public void tube(List<Vector3f> path, float[] radii, int sides, int colourRoot, int colourTip) {
        int n = path.size();
        if (n < 2) return;
        Vector3f[][] rings = new Vector3f[n][sides];
        Vector3f u = null;
        for (int i = 0; i < n; i++) {
            Vector3f prev = path.get(Math.max(0, i - 1)), next = path.get(Math.min(n - 1, i + 1));
            Vector3f d = new Vector3f(next).sub(prev).normalize();
            if (u == null) {
                Vector3f ref = Math.abs(d.y) < 0.9F ? new Vector3f(0, 1, 0) : new Vector3f(1, 0, 0);
                u = new Vector3f(d).cross(ref).normalize();
            } else {
                u = new Vector3f(u).sub(new Vector3f(d).mul(u.dot(d))).normalize();
            }
            Vector3f v = new Vector3f(d).cross(u).normalize();
            for (int s = 0; s < sides; s++) {
                float a = (float) (Math.PI * 2 * s / sides);
                rings[i][s] = new Vector3f(path.get(i)).add(new Vector3f(u).mul(cos(a) * radii[i])).add(new Vector3f(v).mul(sin(a) * radii[i]));
            }
        }
        for (int i = 0; i < n - 1; i++) {
            int c0 = mix(colourRoot, colourTip, i / (float) (n - 1)), c1 = mix(colourRoot, colourTip, (i + 1) / (float) (n - 1));
            Vector3f axis = new Vector3f(path.get(i)).add(path.get(i + 1)).mul(0.5F);
            for (int s = 0; s < sides; s++) {
                int t = (s + 1) % sides;
                quad(rings[i][s], rings[i][t], rings[i + 1][t], rings[i + 1][s], c0, c0, c1, c1, axis);
            }
        }
        Vector3f root = path.get(0), inside = path.get(1);
        for (int s = 0; s < sides; s++) tri(root, rings[0][s], rings[0][(s + 1) % sides], colourRoot, inside);
    }

    /**
     * An outline in the XY plane, filled from {@code centre} and given {@code thickness} along Z.
     * The centre takes {@code colourIn} and the rim {@code colourOut}, so faces get a soft gradient.
     */
    public void slab(Vector3f centre, List<Vector3f> outline, float thickness, int colourIn, int colourOut, boolean closed) {
        float h = thickness / 2;
        Vector3f cf = new Vector3f(centre.x, centre.y, h), cb = new Vector3f(centre.x, centre.y, -h);
        int n = outline.size(), last = closed ? n : n - 1;
        for (int i = 0; i < last; i++) {
            Vector3f a = outline.get(i), b = outline.get((i + 1) % n);
            Vector3f af = new Vector3f(a.x, a.y, h), bf = new Vector3f(b.x, b.y, h);
            Vector3f ab = new Vector3f(a.x, a.y, -h), bb = new Vector3f(b.x, b.y, -h);
            emit(cf, af, bf, bf, colourIn, colourOut, colourOut, colourOut, new Vector3f(0, 0, 1));
            emit(cb, bb, ab, ab, colourIn, colourOut, colourOut, colourOut, new Vector3f(0, 0, -1));
            quad(af, bf, bb, ab, colourOut, new Vector3f(centre.x, centre.y, 0));
        }
    }

    // ---------------------------------------------------------------- colour helpers

    public static int mix(int from, int to, float k) {
        k = Math.max(0, Math.min(1, k));
        int a = Math.round(((from >>> 24) & 255) + (((to >>> 24) & 255) - ((from >>> 24) & 255)) * k);
        int r = Math.round(((from >> 16) & 255) + (((to >> 16) & 255) - ((from >> 16) & 255)) * k);
        int g = Math.round(((from >> 8) & 255) + (((to >> 8) & 255) - ((from >> 8) & 255)) * k);
        int b = Math.round((from & 255) + ((to & 255) - (from & 255)) * k);
        return a << 24 | r << 16 | g << 8 | b;
    }

    /** Darker (k &lt; 0, toward black) or lighter (k &gt; 0, toward white) with the same alpha. */
    public static int shade(int colour, float k) {
        int target = k < 0 ? colour & 0xFF000000 : colour | 0x00FFFFFF;
        return mix(colour, target, Math.abs(k));
    }

    public static int alpha(int colour, float k) {
        int a = Math.round(((colour >>> 24) & 255) * Math.max(0, Math.min(1, k)));
        return a << 24 | (colour & 0xFFFFFF);
    }

    private static float cos(float a) { return (float) Math.cos(a); }
    private static float sin(float a) { return (float) Math.sin(a); }
}
