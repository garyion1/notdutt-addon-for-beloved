package dev.khanh.feature;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.client.render.DrawStyle;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.debug.gizmo.GizmoDrawing;

/** Bounded local effects: jump rings, crystal bursts, totem rings, and fireflies. */
public final class VisualEffectService {
    private static final List<Burst> bursts = new ArrayList<>();
    private static final List<Firefly> fireflies = new ArrayList<>();
    private static final Random random = new Random();
    private static ClientWorld world;
    private static boolean grounded;

    private record Burst(Vec3d pos, long born, String module) { }
    private record Firefly(Vec3d pos, long born, double phase) { }

    private VisualEffectService() { }

    public static boolean enabled() {
        return CosmeticVisualService.enabled() || CosmeticsPlusService.enabled() || KhanhFeatures.enabled("jump_circles")
            || KhanhFeatures.enabled("fireflies") || KhanhFeatures.enabled("player_halo")
            || KhanhFeatures.enabled("body_aura");
    }

    public static void tick(MinecraftClient client) {
        if (client.world != world) {
            bursts.clear(); fireflies.clear();
            world = client.world;
            grounded = client.player != null && client.player.isOnGround();
        }
        if (client.player == null || world == null) return;
        CosmeticVisualService.tick(client);
        CosmeticsPlusService.tick(client);
        long now = System.nanoTime();
        boolean onGround = client.player.isOnGround();
        if (KhanhFeatures.enabled("jump_circles") && grounded && !onGround
            && client.player.getVelocity().y > .05 && !client.player.isGliding()
            && !client.player.isTouchingWater())
            add(new Burst(client.player.getEntityPos().add(0, .025, 0), now, "jump_circles"));
        grounded = onGround;

        bursts.removeIf(burst -> !KhanhFeatures.enabled(burst.module)
            || now - burst.born > lifetime(burst.module));
        if (KhanhFeatures.enabled("fireflies")) {
            int density = KhanhFeatures.number("fireflies", "count", 18);
            int interval = Math.max(2, Math.round(90.0F / density));
            fireflies.removeIf(fly -> now - fly.born > 11_000_000_000L
                || fly.pos.squaredDistanceTo(client.player.getEntityPos()) > 20 * 20);
            if (world.getTime() % interval == 0 && fireflies.size() < density * 2) {
                Vec3d origin = client.player.getEntityPos();
                Vec3d pos = origin.add((random.nextDouble() - .5) * 12,
                    .35 + random.nextDouble() * 2.5, (random.nextDouble() - .5) * 12);
                if (world.isAir(BlockPos.ofFloored(pos)))
                    fireflies.add(new Firefly(pos, now, random.nextDouble() * Math.PI * 2));
            }
        } else fireflies.clear();
    }

    private static void add(Burst burst) {
        if (bursts.size() >= 20) bursts.remove(0);
        bursts.add(burst);
    }

    private static long lifetime(String module) {
        return KhanhFeatures.number(module, "lifetime", 1200) * 1_000_000L;
    }

    private static int colour(String module, float strength) {
        return VisualTheme.withAlpha(KhanhFeatures.color(module, "color", 0xFF69BCFF),
            Math.round(Math.max(0, strength) * 230));
    }

    public static void render(Vec3d camera) {
        if (MinecraftClient.getInstance().currentScreen instanceof dev.khanh.gui.KhanhClickGui) return;
        CosmeticVisualService.render(camera);
        CosmeticsPlusService.render(camera);
        long now = System.nanoTime();
        renderFireflies(camera, now);
        renderPlayerDecorations(camera, now);
        for (Burst burst : bursts) {
            if (!KhanhFeatures.enabled(burst.module) || burst.pos.squaredDistanceTo(camera) > 64 * 64) continue;
            float t = Math.min(1, (now - burst.born) / (float) lifetime(burst.module));
            float eased = 1 - (float) Math.pow(1 - t, 3);
            float radius = (float) KhanhFeatures.decimal(burst.module, "radius", 1.6) * eased;
            float width = (float) KhanhFeatures.decimal(burst.module, "width", 1.7);
            if (burst.module.equals("jump_circles") && !KhanhFeatures.choice("jump_circles", "style", "Khanh").equals("Classic")) {
                // Khanh: a bright ring, a softer inner ring, and sparks riding round the edge as they rise.
                ring(burst.pos, radius, colour(burst.module, 1 - t), width, 0);
                ring(burst.pos.add(0, .02, 0), radius * .58F, colour(burst.module, (1 - t) * .6F), width * .7F, 0);
                for (int i = 0; i < 12; i++) {
                    double angle = Math.toRadians(i * 30.0 + t * 110.0);
                    Vec3d spark = burst.pos.add(Math.cos(angle) * radius * 1.08, t * .45, Math.sin(angle) * radius * 1.08);
                    net.minecraft.world.debug.gizmo.GizmoDrawing.point(spark, colour(burst.module, 1 - t), 2.0F + 3.0F * (1 - t));
                }
                continue;
            }
            ring(burst.pos, radius, colour(burst.module, 1 - t), width, 0);
            ring(burst.pos.add(0, .025, 0), radius * .82F, colour(burst.module, (1 - t) * .5F), width, 0);
        }
    }

    private static final String RING_TEXT = "KHANH CLIENT \u00B7 ";

    /**
     * "KHANH CLIENT" lettering laid flat on the ground between each jump circle's two rings, letters
     * standing outward and the words slowly turning. Drawn with the entities as real world text.
     */
    public static void renderJumpText(net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (bursts.isEmpty() || !KhanhFeatures.enabled("jump_circles") || !KhanhFeatures.bool("jump_circles", "text", true)) return;
        if (client.currentScreen instanceof dev.khanh.gui.KhanhClickGui || context.worldState().cameraRenderState == null) return;
        Vec3d camera = context.worldState().cameraRenderState.pos;
        net.minecraft.client.font.TextRenderer font = client.textRenderer;
        var matrices = context.matrices();
        long now = System.nanoTime();
        int base = KhanhFeatures.color("jump_circles", "color", 0xFF69BCFF);
        float widthPx = 0;
        for (char ch : RING_TEXT.toCharArray()) widthPx += font.getWidth(letter(ch));
        for (Burst burst : bursts) {
            if (!burst.module.equals("jump_circles") || burst.pos.squaredDistanceTo(camera) > 48 * 48) continue;
            float t = Math.min(1, (now - burst.born) / (float) lifetime(burst.module));
            float eased = 1 - (float) Math.pow(1 - t, 3);
            float radius = (float) KhanhFeatures.decimal(burst.module, "radius", 1.6) * eased * 0.91F;
            int alpha = Math.round((float) Math.pow(1 - t, 0.7) * 255);
            if (radius < 0.15F || alpha < 10) continue;
            int colour = alpha << 24 | (SolidShapes.shade(base | 0xFF000000, 0.35F) & 0xFFFFFF);
            // Three repeats around the circle; letter size follows the ring as it grows.
            float scale = (float) (Math.PI * 2 * radius / (3 * widthPx));
            double angle = (now - burst.born) / 1.0e9 * 0.6;
            Vec3d centre = burst.pos.add(0, 0.02, 0).subtract(camera);
            for (int repeat = 0; repeat < 3; repeat++) {
                for (char ch : RING_TEXT.toCharArray()) {
                    net.minecraft.text.Text glyph = letter(ch);
                    int w = font.getWidth(glyph);
                    double a = angle + w * 0.5 * scale / radius;
                    angle += w * scale / radius;
                    if (ch == ' ') continue;
                    matrices.push();
                    matrices.translate(centre.x + Math.cos(a) * radius, centre.y, centre.z + Math.sin(a) * radius);
                    matrices.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Y.rotation((float) (-Math.PI / 2 - a)));
                    matrices.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_X.rotationDegrees(90.0F));
                    matrices.scale(scale, scale, -scale);
                    context.commandQueue().submitText(matrices, -w / 2.0F, -4.0F, glyph.asOrderedText(), false,
                        net.minecraft.client.font.TextRenderer.TextLayerType.POLYGON_OFFSET, SolidShapes.FULL_BRIGHT, colour, 0, 0);
                    matrices.pop();
                }
            }
        }
    }

    private static net.minecraft.text.Text letter(char ch) {
        return net.minecraft.text.Text.literal(String.valueOf(ch)).formatted(net.minecraft.util.Formatting.BOLD);
    }

    private static void renderFireflies(Vec3d camera, long now) {
        if (!KhanhFeatures.enabled("fireflies")) return;
        for (Firefly fly : fireflies) {
            double seconds = (now - fly.born) / 1_000_000_000.0;
            double fade = Math.min(1.0, Math.min(seconds * 2.0, (11.0 - seconds) * .7));
            if (fade <= 0) continue;
            Vec3d pos = fly.pos.add(Math.sin(seconds * 1.1 + fly.phase) * .17,
                Math.sin(seconds * 1.8 + fly.phase) * .12,
                Math.cos(seconds * .9 + fly.phase) * .17);
            if (pos.squaredDistanceTo(camera) > 18 * 18) continue;
            Vec3d toward = camera.subtract(pos).normalize();
            Vec3d right = new Vec3d(-toward.z, 0, toward.x);
            if (right.lengthSquared() < .0001) right = new Vec3d(1, 0, 0);
            right = right.normalize();
            Vec3d up = toward.crossProduct(right).normalize();
            int glow = VisualTheme.withAlpha(0xFFFFE89B, (int) (43 * fade));
            int core = VisualTheme.withAlpha(0xFFFFF8D0, (int) (205 * fade));
            GizmoDrawing.line(pos.subtract(right.multiply(.085)), pos.add(right.multiply(.085)), glow, 4.5F);
            GizmoDrawing.line(pos.subtract(up.multiply(.085)), pos.add(up.multiply(.085)), glow, 4.5F);
            GizmoDrawing.line(pos.subtract(right.multiply(.019)), pos.add(right.multiply(.019)), core, 2.0F);
            GizmoDrawing.line(pos.subtract(up.multiply(.019)), pos.add(up.multiply(.019)), core, 2.0F);
        }
    }

    private static void renderPlayerDecorations(Vec3d camera, long now) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null) return;
        boolean haloEnabled = KhanhFeatures.enabled("player_halo");
        boolean auraEnabled = KhanhFeatures.enabled("body_aura");
        if (!haloEnabled && !auraEnabled) return;
        float tickDelta = client.getRenderTickCounter().getTickProgress(true);
        boolean firstPerson = client.options.getPerspective().isFirstPerson();
        int rendered = 0;
        for (AbstractClientPlayerEntity player : client.world.getPlayers()) {
            if (!player.isAlive() || player.isInvisible() || rendered >= 40) continue;
            boolean self = player == client.player;
            Vec3d pos = player.getLerpedPos(tickDelta);
            double distanceSquared = pos.squaredDistanceTo(camera);
            if (haloEnabled && (!self || !firstPerson && KhanhFeatures.bool("player_halo", "self", true))) {
                double range = KhanhFeatures.number("player_halo", "range", 96);
                if (distanceSquared <= range * range) renderHalo(player, pos, now);
            }
            if (auraEnabled && (!self || !firstPerson && KhanhFeatures.bool("body_aura", "self", false))) {
                double range = KhanhFeatures.number("body_aura", "range", 72);
                if (distanceSquared <= range * range) renderAura(player, pos, now);
            }
            rendered++;
        }
    }

    private static void renderHalo(AbstractClientPlayerEntity player, Vec3d pos, long now) {
        int base = KhanhFeatures.color("player_halo", "color", 0xFFB58CFF);
        float radius = (float) KhanhFeatures.decimal("player_halo", "radius", .38);
        float width = (float) KhanhFeatures.decimal("player_halo", "width", 1.6);
        double height = KhanhFeatures.decimal("player_halo", "height", .22);
        double pulse = Math.sin(now / 520_000_000.0 + player.getId() * .7) * .018;
        Vec3d center = pos.add(0, player.getEyeHeight(player.getPose()) + height, 0);
        boolean throughWalls = KhanhFeatures.bool("player_halo", "through_walls", false);
        var glow = GizmoDrawing.circle(center, radius + .055F + (float) pulse,
            DrawStyle.stroked(VisualTheme.withAlpha(base, 42), width + 4.0F));
        var middle = GizmoDrawing.circle(center.add(0, .006, 0), radius + .018F + (float) pulse,
            DrawStyle.stroked(VisualTheme.withAlpha(base, 112), width + 1.5F));
        var core = GizmoDrawing.circle(center.add(0, .012, 0), radius + (float) pulse,
            DrawStyle.stroked(VisualTheme.withAlpha(base, 235), width));
        if (throughWalls) {
            glow.ignoreOcclusion(); middle.ignoreOcclusion(); core.ignoreOcclusion();
        }
    }

    private static void renderAura(AbstractClientPlayerEntity player, Vec3d pos, long now) {
        int base = KhanhFeatures.color("body_aura", "color", 0xFF74D8FF);
        float size = (float) KhanhFeatures.decimal("body_aura", "size", .72);
        float opacity = (float) KhanhFeatures.decimal("body_aura", "opacity", .55);
        boolean throughWalls = KhanhFeatures.bool("body_aura", "through_walls", false);
        float pulse = 1.0F + (float) Math.sin(now / 620_000_000.0 + player.getId()) * .035F;
        Vec3d center = pos.add(0, player.getHeight() * .50, 0);
        int outer = VisualTheme.withAlpha(base, Math.round(34 * opacity));
        int middle = VisualTheme.withAlpha(base, Math.round(72 * opacity));
        int core = VisualTheme.withAlpha(base, Math.round(132 * opacity));
        ellipse(center, size * 1.10F * pulse, player.getHeight() * .57F, outer, 5.0F, 1, throughWalls);
        ellipse(center, size * .92F * pulse, player.getHeight() * .53F, middle, 2.8F, 2, throughWalls);
        ellipse(center, size * .76F * pulse, player.getHeight() * .49F, core, 1.2F, 1, throughWalls);
    }

    private static void ring(Vec3d center, float radius, int colour, float width, int plane) {
        ellipse(center, radius, radius, colour, width, plane, false);
    }

    private static void ellipse(Vec3d center, float radiusX, float radiusY, int colour,
                                float width, int plane, boolean ignoreOcclusion) {
        Vec3d previous = null;
        for (int i = 0; i <= 56; i++) {
            double angle = i * Math.PI * 2 / 56;
            double x = Math.cos(angle) * radiusX, y = Math.sin(angle) * radiusY;
            Vec3d point = center.add(plane == 2 ? 0 : x,
                plane == 0 ? 0 : y, plane == 0 ? y : plane == 2 ? x : 0);
            if (previous != null) {
                var segment = GizmoDrawing.line(previous, point, colour, width);
                if (ignoreOcclusion) segment.ignoreOcclusion();
            }
            previous = point;
        }
    }
}
