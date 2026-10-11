package dev.khanh.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.khanh.feature.DeepslateOpacityService;
import dev.khanh.feature.SodiumBlockContext;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.BlockRenderView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Deepslate Opacity under Sodium, which meshes chunks with its own code. Each mixin is @Pseudo:
 * without Sodium installed the targets do not exist and they are skipped.
 */
public final class SodiumDeepslateMixins {
    private SodiumDeepslateMixins() { }

    /** Draw the faces of blocks hidden behind deepslate, as the vanilla hook does. */
    @Pseudo
    @Mixin(targets = "net.caffeinemc.mods.sodium.client.render.model.AbstractBlockRenderContext", remap = false)
    public abstract static class Culling implements SodiumBlockContext {
        @Shadow protected BlockRenderView level;
        @Shadow protected BlockState state;
        @Shadow protected BlockPos pos;

        @Override
        public BlockState khanh$state() {
            return state;
        }

        @Inject(method = "shouldDrawSide", at = @At("HEAD"), cancellable = true)
        private void khanh$seeBehind(Direction side, CallbackInfoReturnable<Boolean> info) {
            if (state == null || level == null || pos == null || DeepslateOpacityService.isTarget(state)) return;
            if (DeepslateOpacityService.isTarget(level.getBlockState(pos.offset(side)))) info.setReturnValue(true);
        }
    }

    /** Sodium moves opaque-looking textures out of the translucent pass; keep see-through deepslate in it. */
    @Pseudo
    @Mixin(targets = "net.caffeinemc.mods.sodium.client.render.chunk.compile.pipeline.BlockRenderer", remap = false)
    public abstract static class Downgrade {
        @Inject(method = "attemptPassDowngrade", at = @At("HEAD"), cancellable = true)
        private void khanh$keepTranslucent(CallbackInfoReturnable<Object> info) {
            if (DeepslateOpacityService.isTarget(((SodiumBlockContext) this).khanh$state())) info.setReturnValue(null);
        }
    }

    /** Sodium's lighting treats deepslate as see-through too, so ores behind it are lit, not black. */
    @Pseudo
    @Mixin(targets = "net.caffeinemc.mods.sodium.client.model.light.data.LightDataAccess", remap = false)
    public abstract static class Light {
        @WrapOperation(method = "compute", require = 0,
            at = @At(value = "INVOKE", target = "Lnet/minecraft/block/BlockState;isOpaqueFullCube()Z", remap = true))
        private boolean khanh$notOpaque(BlockState state, Operation<Boolean> original) {
            return !(DeepslateOpacityService.lightThrough() && DeepslateOpacityService.isTarget(state)) && original.call(state);
        }
    }

    /** Deepslate no longer counts as a wall in Sodium's section visibility graph. */
    @Pseudo
    @Mixin(targets = "net.caffeinemc.mods.sodium.client.render.chunk.compile.tasks.ChunkBuilderMeshingTask", remap = false)
    public abstract static class Visibility {
        @WrapOperation(method = "execute", require = 0,
            at = @At(value = "INVOKE", target = "Lnet/minecraft/block/BlockState;isOpaqueFullCube()Z", remap = true))
        private boolean khanh$notAWall(BlockState state, Operation<Boolean> original) {
            return !DeepslateOpacityService.isTarget(state) && original.call(state);
        }
    }
}
