package dev.khanh.feature;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.chunk.ChunkStatus;

/** Bounded, loaded-chunk-only walking. Never bridges, digs, swims or jumps over gaps. */
final class SafeWorkNavigator {
    private static final Direction[] HORIZONTAL = {
        Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST
    };
    private List<BlockPos> route = List.of();
    private BlockPos goal;
    private int routeIndex;
    private int lastPlanTick = Integer.MIN_VALUE;
    private int lastProgressTick;
    private Vec3d lastProgressPos;
    private boolean driving;

    boolean moveTo(MinecraftClient client, BlockPos target, int tick, double reach,
                   boolean building, String moduleId) {
        if (client.player == null || client.world == null || (!client.player.isOnGround() && !driving)
            || client.player.getHealth() <= 6.0F || client.player.isTouchingWater()
            || client.player.isOnFire() || client.player.isGliding()) {
            stop(client);
            return false;
        }
        BlockPos feet = client.player.getBlockPos();
        if (!target.equals(goal) || routeIndex >= route.size()
            || tick - lastPlanTick > KhanhFeatures.number(moduleId, "route_replan", 40)) {
            route = plan(client.world, client.player, feet, target, reach, building,
                KhanhFeatures.number(moduleId, "walk_radius", 32),
                KhanhFeatures.number(moduleId, "route_nodes", 2400),
                KhanhFeatures.number(moduleId, "path_height", 8),
                KhanhFeatures.bool(moduleId, "step_up", true),
                KhanhFeatures.bool(moduleId, "step_down", true));
            goal = target;
            routeIndex = 0;
            lastPlanTick = tick;
            lastProgressPos = null;
        }
        if (route.isEmpty()) {
            release(client);
            return false;
        }
        Vec3d position = client.player.getEntityPos();
        if (lastProgressPos == null || position.squaredDistanceTo(lastProgressPos) > 0.10) {
            lastProgressPos = position;
            lastProgressTick = tick;
        } else if (tick - lastProgressTick > KhanhFeatures.number(moduleId, "stuck_timeout", 40)) {
            stop(client);
            return false;
        }
        while (client.player.isOnGround() && routeIndex < route.size()) {
            BlockPos step = route.get(routeIndex);
            double dx = step.getX() + 0.5 - client.player.getX();
            double dz = step.getZ() + 0.5 - client.player.getZ();
            if (dx * dx + dz * dz >= 0.18 || Math.abs(step.getY() - client.player.getY()) > 0.65) break;
            routeIndex++;
        }
        if (routeIndex >= route.size()) {
            release(client);
            return true;
        }
        BlockPos step = route.get(routeIndex);
        if (!safeStand(client.world, step)) {
            route = List.of();
            release(client);
            return false;
        }
        double dx = step.getX() + 0.5 - client.player.getX();
        double dz = step.getZ() + 0.5 - client.player.getZ();
        float wanted = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float delta = net.minecraft.util.math.MathHelper.wrapDegrees(wanted - client.player.getYaw());
        float turn = KhanhFeatures.number(moduleId, "walk_turn_speed", 22);
        float ease = (float) KhanhFeatures.decimal(moduleId, "walking_ease", 0.65);
        client.player.setYaw(client.player.getYaw()
            + Math.max(-turn, Math.min(turn, delta * ease)));
        driving = true;
        client.options.forwardKey.setPressed(Math.abs(delta) < 32.0F);
        client.options.jumpKey.setPressed(step.getY() > feet.getY() && Math.abs(delta) < 25.0F);
        return true;
    }

    void stop(MinecraftClient client) {
        route = List.of();
        goal = null;
        routeIndex = 0;
        lastProgressPos = null;
        release(client);
    }

    boolean isDriving() {
        return driving;
    }

    private void release(MinecraftClient client) {
        if (client != null && driving) {
            client.options.forwardKey.setPressed(false);
            client.options.jumpKey.setPressed(false);
        }
        driving = false;
    }

    private static List<BlockPos> plan(ClientWorld world, net.minecraft.entity.player.PlayerEntity player,
                                       BlockPos start, BlockPos work, double reach, boolean building,
                                       int walkRadius, int routeNodes, int pathHeight,
                                       boolean stepUp, boolean stepDown) {
        if (!safeStand(world, start)) return List.of();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        Map<BlockPos, BlockPos> parent = new HashMap<>();
        queue.add(start);
        parent.put(start, start);
        while (!queue.isEmpty() && parent.size() < routeNodes) {
            BlockPos current = queue.removeFirst();
            if (!current.equals(start) && canWorkFrom(world, player, current, work, reach, building)) {
                List<BlockPos> reversed = new ArrayList<>();
                for (BlockPos step = current; !step.equals(start); step = parent.get(step)) reversed.add(step);
                Collections.reverse(reversed);
                return reversed;
            }
            for (Direction direction : HORIZONTAL) {
                BlockPos adjacent = current.offset(direction);
                for (int dy : new int[] {0, 1, -1}) {
                    if (dy > 0 && !stepUp || dy < 0 && !stepDown) continue;
                    BlockPos next = adjacent.up(dy);
                    if (Math.abs(next.getX() - start.getX()) > walkRadius
                        || Math.abs(next.getZ() - start.getZ()) > walkRadius
                        || Math.abs(next.getY() - start.getY()) > pathHeight
                        || (dy > 0 && !world.getBlockState(current.up(2)).getCollisionShape(
                            world, current.up(2)).isEmpty())
                        || parent.containsKey(next) || !safeStand(world, next)) continue;
                    parent.put(next, current);
                    queue.addLast(next);
                }
            }
        }
        return List.of();
    }

    private static boolean canWorkFrom(ClientWorld world, net.minecraft.entity.player.PlayerEntity player,
                                       BlockPos feet, BlockPos work, double reach, boolean building) {
        Vec3d eye = Vec3d.ofCenter(feet).add(0, 1.12, 0);
        Vec3d target = Vec3d.ofCenter(work);
        if (eye.squaredDistanceTo(target) > reach * reach) return false;
        if (building) {
            for (Direction side : Direction.values()) {
                BlockPos support = work.offset(side);
                BlockState state = world.getBlockState(support);
                if (state.isAir() || state.getCollisionShape(world, support).isEmpty()
                    || !state.getFluidState().isEmpty()) continue;
                Direction face = side.getOpposite();
                Vec3d hitPoint = Vec3d.ofCenter(support).add(face.getOffsetX() * 0.5,
                    face.getOffsetY() * 0.5, face.getOffsetZ() * 0.5);
                if (eye.squaredDistanceTo(hitPoint) > reach * reach) continue;
                HitResult hit = world.raycast(new RaycastContext(eye, hitPoint,
                    RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, player));
                if (hit instanceof BlockHitResult block && block.getBlockPos().equals(support)) return true;
            }
            return false;
        }
        HitResult hit = world.raycast(new RaycastContext(eye, target, RaycastContext.ShapeType.OUTLINE,
            RaycastContext.FluidHandling.NONE, player));
        return hit instanceof BlockHitResult block && block.getBlockPos().equals(work);
    }

    private static boolean safeStand(ClientWorld world, BlockPos feet) {
        if (world.getChunkManager().getChunk(feet.getX() >> 4, feet.getZ() >> 4,
            ChunkStatus.FULL, false) == null) return false;
        BlockState floor = world.getBlockState(feet.down());
        if (floor.isAir() || !floor.getFluidState().isEmpty() || floor.isOf(Blocks.MAGMA_BLOCK)
            || floor.isOf(Blocks.CAMPFIRE) || floor.isOf(Blocks.SOUL_CAMPFIRE)
            || floor.isOf(Blocks.CACTUS) || floor.isOf(Blocks.POWDER_SNOW)
            || floor.isOf(Blocks.POINTED_DRIPSTONE)) return false;
        if (floor.getCollisionShape(world, feet.down()).isEmpty()) return false;
        for (BlockPos space : new BlockPos[] {feet, feet.up()}) {
            BlockState state = world.getBlockState(space);
            if (!state.getCollisionShape(world, space).isEmpty() || !state.getFluidState().isEmpty()
                || state.isOf(Blocks.FIRE) || state.isOf(Blocks.SOUL_FIRE)
                || state.isOf(Blocks.SWEET_BERRY_BUSH) || state.isOf(Blocks.WITHER_ROSE)
                || state.isOf(Blocks.COBWEB)) return false;
        }
        return true;
    }
}
