package dev.khanh.feature;

import java.util.ArrayList;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4fc;

/**
 * Pixel-locked tracers. The world pass only records endpoints; the HUD pass projects them with
 * the exact matrices that drew this frame's world, so every line starts on the crosshair pixel
 * and ends on its block with no near-plane clipping, gizmo latency or view-bob drift.
 */
public final class StorageTracerRenderer {
    private record Target(double x, double y, double z, int colour, float width) { }

    private static final int MAX_TARGETS = 2000;
    private static final ArrayList<Target> targets = new ArrayList<>();
    private static StableTracerGeometry.Frame frame;
    private static boolean registered;

    private StorageTracerRenderer() { }

    public static void capture(Matrix4fc view, Matrix4fc projection, Vec3d camera) {
        targets.clear();
        if (!registered) {
            registered = true;
            HudElementRegistry.attachElementBefore(VanillaHudElements.CROSSHAIR,
                Identifier.of("khanh", "storage_rear_tracers"), StorageTracerRenderer::render);
        }
        frame = tracersEnabled() ? StableTracerGeometry.frame(view, projection, camera.x, camera.y, camera.z) : null;
    }

    public static void draw(Vec3d camera, Vec3d target, int color, float width) {
        if (frame == null || camera.x != frame.cameraX() || camera.y != frame.cameraY()
            || camera.z != frame.cameraZ() || targets.size() >= MAX_TARGETS) return;
        targets.add(new Target(target.x, target.y, target.z, color, Math.max(0.25F, Math.min(4.0F, width))));
    }

    private static void render(DrawContext context, RenderTickCounter counter) {
        MinecraftClient client = MinecraftClient.getInstance();
        StableTracerGeometry.Frame current = frame;
        if (client.world == null || current == null || targets.isEmpty() || !tracersEnabled()
            || (client.currentScreen != null && !(client.currentScreen instanceof ChatScreen))) return;
        float w = context.getScaledWindowWidth(), h = context.getScaledWindowHeight();
        float cx = w / 2.0F, cy = h / 2.0F;
        float pixel = 1.0F / (float) Math.max(1.0, client.getWindow().getScaleFactor());
        Matrix4fc m = current.clipMatrix();
        for (Target target : targets) {
            // Camera-relative doubles first: no float jitter far from spawn.
            double x = target.x() - current.cameraX(), y = target.y() - current.cameraY(), z = target.z() - current.cameraZ();
            double clipX = m.m00() * x + m.m10() * y + m.m20() * z + m.m30();
            double clipY = m.m01() * x + m.m11() * y + m.m21() * z + m.m31();
            double clipW = m.m03() * x + m.m13() * y + m.m23() * z + m.m33();
            if (!Double.isFinite(clipX + clipY + clipW)) continue;
            // Storage behind you still gets a tracer: a line from the crosshair to the screen edge
            // on the side you would turn toward.
            if (clipW <= 1.0e-4) {
                double ax = clipX, ay = -clipY, length = Math.hypot(ax, ay);
                if (length < 1.0e-6) { ax = 0; ay = 1; length = 1; }
                ax /= length; ay /= length;
                double edge = Math.min((cx - 2) / Math.max(Math.abs(ax), 1.0e-6), (cy - 2) / Math.max(Math.abs(ay), 1.0e-6));
                line(context, cx, cy, (float) edge, (float) Math.atan2(ay, ax), pixel * target.width() * 2.0F, target.colour());
                continue;
            }
            boolean ahead = true;
            double dx = clipX / clipW * cx;
            double dy = -clipY / clipW * cy;
            double reach = Math.hypot(dx, dy);
            if (reach < 0.5) continue;
            boolean onScreen = ahead && Math.abs(dx) <= cx && Math.abs(dy) <= cy;
            if (!onScreen) {
                double edge = Math.min((cx - 2) / Math.max(Math.abs(dx), 1.0e-6), (cy - 2) / Math.max(Math.abs(dy), 1.0e-6));
                dx *= edge;
                dy *= edge;
                reach = Math.hypot(dx, dy);
            }
            line(context, cx, cy, (float) reach, (float) Math.atan2(dy, dx), pixel * target.width() * 2.0F, target.colour());
        }
    }

    /** A bright core line wrapped in a soft glow, so tracers read as light rather than thin wire. */
    private static void line(DrawContext context, float x, float y, float length, float angle, float thickness, int colour) {
        int alpha = colour >>> 24, rgb = colour & 0xFFFFFF;
        var matrices = context.getMatrices();
        matrices.pushMatrix();
        matrices.translate(x, y);
        matrices.rotate(angle);
        centred(context, length, thickness * 4.0F, Math.round(alpha * 0.10F) << 24 | rgb);
        centred(context, length, thickness * 2.2F, Math.round(alpha * 0.30F) << 24 | rgb);
        centred(context, length, thickness, colour);
        matrices.popMatrix();
    }

    private static void centred(DrawContext context, float length, float height, int colour) {
        strip(context, length, -height / 2.0F, height, colour);
    }

    private static void strip(DrawContext context, float length, float offset, float height, int colour) {
        var matrices = context.getMatrices();
        matrices.pushMatrix();
        matrices.translate(0, offset);
        matrices.scale(length / 64.0F, height);
        context.fill(0, 0, 64, 1, colour);
        matrices.popMatrix();
    }

    private static boolean tracersEnabled() {
        return KhanhFeatures.enabled("storage_esp") && KhanhFeatures.bool("storage_esp", "tracers", true)
            || KhanhFeatures.enabled("player_esp") && KhanhFeatures.bool("player_esp", "tracers", false);
    }
}
