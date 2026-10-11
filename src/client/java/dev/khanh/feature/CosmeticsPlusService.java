package dev.khanh.feature;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.debug.gizmo.GizmoDrawing;

/**
 * Khanh Client's cosmetics. Things worn on you (crown, ears, horns, orbs, rings, shield, cloud) are
 * solid 3D shapes in {@link SolidCosmeticsFeature}; the spirit pet is a solid crystal drawn here;
 * effects left on the world (runes, trail, footprints, stars, rain) are glowing lines, drawn in
 * three passes so they read as light. Local only: only you see them.
 */
public final class CosmeticsPlusService {
    public static final String[] IDS = {"orbit_orbs", "rune_circle", "motion_trail", "crown", "cat_ears", "devil_horns",
        "spirit_pet", "saturn_ring", "footprints", "star_aura", "rain_cloud", "energy_shield"};

    private record Stamp(Vec3d pos, long born, float yaw, boolean left) { }
    private record Mote(Vec3d pos, Vec3d velocity, long born, float size) { }

    private static final ArrayDeque<Vec3d> TRAIL = new ArrayDeque<>();
    private static final List<Stamp> PRINTS = new ArrayList<>();
    private static final List<Mote> SPARKS = new ArrayList<>();
    private static final List<Mote> DROPS = new ArrayList<>();
    private static final Random RANDOM = new Random();
    private static Vec3d lastPrint;
    private static boolean leftFoot;
    private static Vec3d pet, petPrevious;

    private CosmeticsPlusService() { }

    public static boolean enabled() {
        for (String id : IDS) if (KhanhFeatures.enabled(id)) return true;
        return false;
    }

    // ---------------------------------------------------------------- tick: things that leave history

    public static void tick(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        if (player == null || client.world == null) {
            TRAIL.clear(); PRINTS.clear(); SPARKS.clear(); DROPS.clear(); pet = null;
            return;
        }
        long now = System.nanoTime();
        Vec3d feet = player.getEntityPos();

        if (KhanhFeatures.enabled("motion_trail")) {
            Vec3d waist = feet.add(0, player.getHeight() * 0.5, 0);
            if (TRAIL.isEmpty() || TRAIL.peekLast().squaredDistanceTo(waist) > 0.0025) TRAIL.addLast(waist);
            int length = KhanhFeatures.number("motion_trail", "length", 24);
            while (TRAIL.size() > length) TRAIL.removeFirst();
            if (player.getVelocity().horizontalLengthSquared() < 1.0e-4 && !TRAIL.isEmpty()) TRAIL.removeFirst();
        } else TRAIL.clear();

        if (KhanhFeatures.enabled("footprints")) {
            if (player.isOnGround() && (lastPrint == null || lastPrint.squaredDistanceTo(feet) > 0.55)) {
                PRINTS.add(new Stamp(feet.add(0, 0.02, 0), now, player.getBodyYaw(), leftFoot = !leftFoot));
                lastPrint = feet;
                if (PRINTS.size() > 40) PRINTS.remove(0);
            }
            PRINTS.removeIf(p -> now - p.born() > 4_000_000_000L);
        } else PRINTS.clear();

        if (KhanhFeatures.enabled("star_aura")) {
            double density = KhanhFeatures.decimal("star_aura", "density", 1.0);
            if (RANDOM.nextDouble() < 0.55 * density) {
                double angle = RANDOM.nextDouble() * Math.PI * 2, r = 0.45 + RANDOM.nextDouble() * 0.35;
                SPARKS.add(new Mote(feet.add(Math.cos(angle) * r, RANDOM.nextDouble() * player.getHeight(), Math.sin(angle) * r),
                    new Vec3d(0, 0.02 + RANDOM.nextDouble() * 0.03, 0), now, 0.06F + RANDOM.nextFloat() * 0.07F));
            }
            SPARKS.removeIf(m -> now - m.born() > 1_600_000_000L);
        } else SPARKS.clear();

        if (KhanhFeatures.enabled("rain_cloud")) {
            for (int i = 0; i < 2; i++) {
                DROPS.add(new Mote(feet.add((RANDOM.nextDouble() - 0.5) * 0.7, player.getHeight() + 0.55, (RANDOM.nextDouble() - 0.5) * 0.5),
                    new Vec3d(0, -0.22, 0), now, 0.12F));
            }
            DROPS.removeIf(m -> now - m.born() > 450_000_000L);
        } else DROPS.clear();

        if (KhanhFeatures.enabled("spirit_pet")) {
            double yaw = Math.toRadians(player.getBodyYaw());
            Vec3d target = feet.add(-Math.sin(yaw + 2.3) * 0.9, player.getHeight() * 0.85, Math.cos(yaw + 2.3) * 0.9);
            petPrevious = pet == null ? target : pet;
            pet = pet == null ? target : pet.lerp(target, 0.18);
        } else pet = null;
    }

    // ---------------------------------------------------------------- render

    public static void render(Vec3d camera) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        if (player == null || client.world == null) return;
        float tick = client.getRenderTickCounter().getTickProgress(true);
        boolean thirdPerson = !client.options.getPerspective().isFirstPerson();
        double t = (System.nanoTime() % 3_600_000_000_000L) / 1.0e9;
        Vec3d feet = player.getLerpedPos(tick);

        if (on("rune_circle")) runeCircle(feet.add(0, 0.03, 0), size("rune_circle"), t, colour("rune_circle", t));
        if (on("motion_trail")) trail(colour("motion_trail", t));
        if (on("footprints")) footprints(colour("footprints", t));
        if (!thirdPerson) return;
        if (on("star_aura")) sparks(colour("star_aura", t));
        if (on("rain_cloud")) rain(colour("rain_cloud", t));
    }

    /** The spirit pet is a solid crystal drawn with the entities, so it is lit and depth-sorted like one. */
    public static void renderSolid(net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!on("spirit_pet") || pet == null || client.player == null || context.worldState().cameraRenderState == null) return;
        if (client.currentScreen instanceof dev.khanh.gui.KhanhClickGui) return;
        float tick = client.getRenderTickCounter().getTickProgress(true);
        float t = (float) ((System.nanoTime() % 3_600_000_000_000L) / 1.0e9);
        Vec3d camera = context.worldState().cameraRenderState.pos;
        Vec3d pos = petPrevious.lerp(pet, tick).add(0, Math.sin(t * 2.4) * 0.08, 0).subtract(camera);
        int colour = colour("spirit_pet", t);
        float size = (float) size("spirit_pet");
        var matrices = context.matrices();
        matrices.push();
        matrices.translate(pos.x, pos.y, pos.z);
        context.commandQueue().submitCustom(matrices, SolidShapes.layer(), (entry, out) -> {
            SolidShapes s = new SolidShapes(entry, out, SolidShapes.FULL_BRIGHT);
            int dark = SolidShapes.shade(colour, -0.3F), light = SolidShapes.shade(colour, 0.45F);
            s.push().scale(size, size, size).rotateY(t * 1.8F);
            s.octahedron(0, 0, 0, 0.13F, 0.21F, colour, dark);
            s.push().rotateX(0.55F).rotateY(t * 2.2F).torus(0.25F, 0.016F, light, 32, 6);
            s.pop();
            for (int i = 0; i < 3; i++) {
                float a = t * 3.0F + i * (float) Math.PI * 2 / 3;
                s.push().translate((float) Math.cos(a) * 0.32F, (float) Math.sin(t * 2 + i) * 0.08F, (float) Math.sin(a) * 0.32F)
                    .rotateY(t * 4).rotateX(t * 3);
                s.cube(0, 0, 0, 0.025F, light);
                s.pop();
            }
            s.pop();
        });
        matrices.pop();
    }

    // ---------------------------------------------------------------- the cosmetics

    private static void runeCircle(Vec3d centre, double size, double t, int colour) {
        double r = 0.95 * size;
        glowRing(centre, r, colour, 0.0, t * 0.4);
        glowRing(centre, r * 0.78, colour, 0.0, -t * 0.6);
        // An eight-point star and rune ticks between the rings, turning slowly.
        for (int i = 0; i < 8; i++) {
            double a = t * 0.4 + i * Math.PI / 4;
            Vec3d tickA = centre.add(Math.cos(a) * r * 0.8, 0, Math.sin(a) * r * 0.8);
            Vec3d tickB = centre.add(Math.cos(a) * r * 0.97, 0, Math.sin(a) * r * 0.97);
            glowLine(tickA, tickB, colour);
            double b = -t * 0.6 + i * Math.PI / 4, c = b + Math.PI * 3 / 4;
            glowLine(centre.add(Math.cos(b) * r * 0.76, 0, Math.sin(b) * r * 0.76),
                centre.add(Math.cos(c) * r * 0.76, 0, Math.sin(c) * r * 0.76), fade(colour, 0.7F));
        }
    }

    private static void trail(int colour) {
        Vec3d previous = null;
        int i = 0, n = TRAIL.size();
        for (Vec3d point : TRAIL) {
            if (previous != null) {
                float k = i / (float) n;
                GizmoDrawing.line(previous, point, fade(colour, 0.15F * k), 9.0F * k);
                GizmoDrawing.line(previous, point, fade(colour, 0.85F * k), 2.0F + 2.0F * k);
            }
            previous = point;
            i++;
        }
    }

    private static void footprints(int colour) {
        long now = System.nanoTime();
        for (Stamp print : PRINTS) {
            float life = 1.0F - (now - print.born()) / 4.0e9F;
            double yaw = Math.toRadians(print.yaw());
            double side = print.left() ? 0.13 : -0.13;
            Vec3d centre = print.pos().add(Math.cos(yaw) * side, 0, Math.sin(yaw) * side);
            Vec3d forward = new Vec3d(-Math.sin(yaw), 0, Math.cos(yaw)), right = new Vec3d(Math.cos(yaw), 0, Math.sin(yaw));
            Vec3d a = centre.add(forward.multiply(0.13)).add(right.multiply(0.05)), b = centre.add(forward.multiply(0.13)).add(right.multiply(-0.05));
            Vec3d c = centre.add(forward.multiply(-0.12)).add(right.multiply(-0.05)), d = centre.add(forward.multiply(-0.12)).add(right.multiply(0.05));
            int col = fade(colour, life);
            GizmoDrawing.line(a, b, col, 2.0F); GizmoDrawing.line(b, c, col, 2.0F);
            GizmoDrawing.line(c, d, col, 2.0F); GizmoDrawing.line(d, a, col, 2.0F);
        }
    }

    private static void rain(int colour) {
        long now = System.nanoTime();
        for (Mote drop : DROPS) {
            float age = (now - drop.born()) / 0.45e9F;
            Vec3d p = drop.pos().add(drop.velocity().multiply(age * 9));
            GizmoDrawing.line(p, p.add(0, -drop.size(), 0), fade(colour, 1.0F - age * 0.6F), 1.6F);
        }
    }

    private static void sparks(int colour) {
        long now = System.nanoTime();
        for (Mote spark : SPARKS) {
            float age = (now - spark.born()) / 1.6e9F;
            Vec3d p = spark.pos().add(spark.velocity().multiply(age * 32));
            double s = spark.size() * (1.0 - age);
            int col = fade(colour, 1.0F - age);
            GizmoDrawing.line(p.add(-s, 0, 0), p.add(s, 0, 0), col, 1.6F);
            GizmoDrawing.line(p.add(0, -s, 0), p.add(0, s, 0), col, 1.6F);
            GizmoDrawing.line(p.add(0, 0, -s), p.add(0, 0, s), col, 1.6F);
        }
    }

    // ---------------------------------------------------------------- drawing helpers

    private static void glowLine(Vec3d a, Vec3d b, int colour) {
        GizmoDrawing.line(a, b, fade(colour, 0.12F), 7.0F);
        GizmoDrawing.line(a, b, fade(colour, 0.35F), 3.5F);
        GizmoDrawing.line(a, b, colour, 1.6F);
    }

    /** A ring tilted about the X axis by {@code tilt} radians and spun by {@code spin}. */
    private static void glowRing(Vec3d centre, double r, int colour, double tilt, double spin) {
        Vec3d previous = null;
        for (int i = 0; i <= 48; i++) {
            double a = spin + i * Math.PI * 2 / 48;
            Vec3d p = tilted(centre, Math.cos(a) * r, Math.sin(a) * r, tilt);
            if (previous != null) {
                GizmoDrawing.line(previous, p, fade(colour, 0.12F), 7.0F);
                GizmoDrawing.line(previous, p, fade(colour, 0.35F), 3.5F);
                GizmoDrawing.line(previous, p, colour, 1.6F);
            }
            previous = p;
        }
    }

    private static Vec3d tilted(Vec3d centre, double x, double z, double tilt) {
        return centre.add(x, z * Math.sin(tilt), z * Math.cos(tilt));
    }

    // ---------------------------------------------------------------- options

    private static boolean on(String id) {
        return KhanhFeatures.enabled(id);
    }

    private static double size(String id) {
        return KhanhFeatures.decimal(id, "size", 1.0);
    }

    public static int colour(String id, double t) {
        if (KhanhFeatures.bool(id, "rainbow", false)) {
            return 0xFF000000 | java.awt.Color.HSBtoRGB((float) ((t * 0.12) % 1.0), 0.65F, 1.0F) & 0xFFFFFF;
        }
        return KhanhFeatures.color(id, "color", 0xFFC9B6FF);
    }

    private static int fade(int colour, float k) {
        int a = Math.round(((colour >>> 24) & 255) * Math.max(0.0F, Math.min(1.0F, k)));
        return a << 24 | (colour & 0xFFFFFF);
    }
}
