package dev.khanh.feature;

import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector4f;

/** Stateless geometry: no clock, interpolation, player position or animated endpoint. */
public final class StableTracerGeometry {
    private StableTracerGeometry() { }

    public record Point(double x, double y, double z) { }
    public record Frame(double cameraX, double cameraY, double cameraZ,
                        Matrix4fc clipMatrix, Point start) { }

    public static Frame frame(Matrix4fc view, Matrix4fc projection,
                              double cameraX, double cameraY, double cameraZ) {
        Matrix4f clip = new Matrix4f(projection).mul(view);
        if (!clip.isFinite() || Math.abs(clip.determinant()) < 1.0e-9F) return null;
        // Invert the actual drawn projection, including view bob/hurt distortion.
        // A raw camera-forward vector does not stay on the crosshair in that frame.
        Vector4f centre = new Matrix4f(clip).invert().transform(new Vector4f(0, 0, 0, 1));
        if (!Float.isFinite(centre.w) || Math.abs(centre.w) < 1.0e-6F) return null;
        double x = cameraX + centre.x / centre.w;
        double y = cameraY + centre.y / centre.w;
        double z = cameraZ + centre.z / centre.w;
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) return null;
        return new Frame(cameraX, cameraY, cameraZ, clip, new Point(x, y, z));
    }

    public static boolean visibleEndpoint(Frame frame, double targetX, double targetY, double targetZ) {
        if (frame == null) return false;
        // Subtract the double-precision camera position before projection. This
        // avoids float-coordinate jitter far from spawn without moving the block.
        double x = targetX - frame.cameraX(), y = targetY - frame.cameraY(), z = targetZ - frame.cameraZ();
        Matrix4fc m = frame.clipMatrix();
        double w = m.m03() * x + m.m13() * y + m.m23() * z + m.m33();
        double depth = m.m02() * x + m.m12() * y + m.m22() * z + m.m32();
        // Do not invent a floating replacement endpoint for containers behind the
        // camera. Let the GPU clip off-screen lines whose real endpoint is ahead.
        return Double.isFinite(w) && Double.isFinite(depth) && w > 1.0e-6 && depth >= -w;
    }

    /** Bearing for a rear container; absolute W avoids mirroring its direction. */
    public static Point bearing(Frame frame, double targetX, double targetY, double targetZ) {
        if (frame == null) return null;
        double x=targetX-frame.cameraX(), y=targetY-frame.cameraY(), z=targetZ-frame.cameraZ();
        if (!Double.isFinite(x+y+z) || x*x+y*y+z*z < 1.0e-8) return null;
        Matrix4fc m=frame.clipMatrix();
        double bx=m.m00()*x+m.m10()*y+m.m20()*z+m.m30();
        double by=m.m01()*x+m.m11()*y+m.m21()*z+m.m31();
        double length=Math.hypot(bx,by);
        // Exactly behind has no left/right bearing. Use a stable lower edge.
        return length < 1.0e-7 ? new Point(0,-1,0) : new Point(bx/length,by/length,0);
    }
}
