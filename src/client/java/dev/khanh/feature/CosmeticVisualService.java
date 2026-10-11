package dev.khanh.feature;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.DrawStyle;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.debug.gizmo.GizmoDrawing;

/** Block Outline's fill. China Hat and Wings are solid shapes in {@link SolidCosmeticsFeature}. */
public final class CosmeticVisualService {
    private static ClientWorld world;

    private CosmeticVisualService() { }

    public static boolean enabled() {
        return KhanhFeatures.enabled("block_outline")
                && KhanhFeatures.bool("block_outline", "fill_enabled", false);
    }

    /** Kept for the existing status-packet hook. */
    public static void totem(Entity entity) { }

    public static void tick(MinecraftClient client) { world = client.world; }

    public static void render(Vec3d camera) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world != world) return;
        if (KhanhFeatures.enabled("block_outline")
            && KhanhFeatures.bool("block_outline", "fill_enabled", false)
            && client.crosshairTarget instanceof BlockHitResult hit
            && !world.getBlockState(hit.getBlockPos()).isAir()) {
            var shape = world.getBlockState(hit.getBlockPos()).getOutlineShape(world, hit.getBlockPos());
            if (!shape.isEmpty()) {
                Box box = shape.getBoundingBox().offset(hit.getBlockPos()).expand(.002);
                int alpha = (int) (KhanhFeatures.decimal("block_outline", "fill_opacity", .18) * 255);
                int fill = VisualTheme.withAlpha(
                    KhanhFeatures.color("block_outline", "fill_color", 0xFF84B7FF), alpha);
                GizmoDrawing.box(box, DrawStyle.filled(fill), false);
            }
        }
    }
}
