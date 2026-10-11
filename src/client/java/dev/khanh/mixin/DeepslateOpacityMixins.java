package dev.khanh.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.khanh.feature.DeepslateOpacityService;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.BlockRenderLayer;
import net.minecraft.client.render.BlockRenderLayers;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.chunk.SectionBuilder;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.BlockRenderView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Deepslate Opacity's hooks into chunk meshing. All are no-ops while the module is off. */
public final class DeepslateOpacityMixins {
    private DeepslateOpacityMixins() { }

    /** See-through deepslate must go in the translucent layer, or its lowered alpha is ignored. */
    @Mixin(BlockRenderLayers.class)
    public abstract static class Layers {
        @Inject(method = "getBlockLayer", at = @At("HEAD"), cancellable = true)
        private static void khanh$translucent(BlockState state, CallbackInfoReturnable<BlockRenderLayer> info) {
            if (DeepslateOpacityService.isTarget(state)) info.setReturnValue(BlockRenderLayer.TRANSLUCENT);
        }
    }

    /** Draw the faces hidden behind deepslate (ores, cave walls), but not deepslate-to-deepslate faces. */
    @Mixin(Block.class)
    public abstract static class Culling {
        @Inject(method = "shouldDrawSide", at = @At("HEAD"), cancellable = true)
        private static void khanh$seeBehind(BlockState state, BlockState otherState, Direction side,
                                                 CallbackInfoReturnable<Boolean> info) {
            if (DeepslateOpacityService.isTarget(otherState) && !DeepslateOpacityService.isTarget(state)) info.setReturnValue(true);
        }
    }

    /** Deepslate no longer counts as a wall when deciding which chunk sections are visible. */
    @Mixin(SectionBuilder.class)
    public abstract static class Visibility {
        @WrapOperation(method = "build", at = @At(value = "INVOKE", target = "Lnet/minecraft/block/BlockState;isOpaqueFullCube()Z"))
        private boolean khanh$notAWall(BlockState state, Operation<Boolean> original) {
            return !DeepslateOpacityService.isTarget(state) && original.call(state);
        }
    }

    /** Faces seen through deepslate sample its light; give them daylight instead of pitch black. */
    @Mixin(WorldRenderer.class)
    public abstract static class Light {
        @Inject(method = "getLightmapCoordinates(Lnet/minecraft/client/render/WorldRenderer$BrightnessGetter;Lnet/minecraft/world/BlockRenderView;Lnet/minecraft/block/BlockState;Lnet/minecraft/util/math/BlockPos;)I",
            at = @At("HEAD"), cancellable = true)
        private static void khanh$lit(WorldRenderer.BrightnessGetter getter, BlockRenderView world, BlockState state, BlockPos pos,
                                           CallbackInfoReturnable<Integer> info) {
            // The position asked about is the neighbour a face looks into; light it if that is deepslate.
            if (DeepslateOpacityService.lightThrough()
                && (DeepslateOpacityService.isTarget(state) || DeepslateOpacityService.isTarget(world.getBlockState(pos)))) {
                info.setReturnValue(LightmapTextureManager.MAX_LIGHT_COORDINATE);
            }
        }
    }
}
