package dev.khanh.mixin;

import dev.khanh.feature.FreecamService;
import dev.khanh.feature.KhanhFeatures;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow
    private boolean thirdPerson;

    @Shadow
    protected abstract void setPos(Vec3d pos);

    @Shadow
    protected abstract void setRotation(float yaw, float pitch);

    @Shadow
    protected abstract void moveBy(float x, float y, float z);

    @Shadow
    private float clipToSpace(float desiredCameraDistance) {
        throw new AssertionError();
    }

    @Inject(method = "update", at = @At("HEAD"))
    private void khanh$aimAssistFrame(World world, Entity focusedEntity, boolean thirdPerson,
                                        boolean inverseView, float tickProgress, CallbackInfo info) {
        dev.khanh.feature.CombatUtilityService.aimFrame(net.minecraft.client.MinecraftClient.getInstance(), tickProgress);
    }

    @Inject(method = "update", at = @At("TAIL"))
    private void khanh$applyFreecam(World world, Entity focusedEntity, boolean thirdPerson,
                                    boolean inverseView, float tickProgress, CallbackInfo info) {
        if (FreecamService.active()) {
            this.thirdPerson = true;
            FreecamService.frame(net.minecraft.client.MinecraftClient.getInstance());
            setPos(FreecamService.position(tickProgress));
            setRotation(FreecamService.yaw(tickProgress), FreecamService.pitch(tickProgress));
        } else if (FreecamService.freeLookActive()) {
            this.thirdPerson = true;
            setRotation(FreecamService.yaw(tickProgress), FreecamService.pitch(tickProgress));
            setPos(focusedEntity.getCameraPosVec(tickProgress));
            moveBy(KhanhFeatures.bool("freelook", "through_walls", true) ? -4.0F : -clipToSpace(4.0F), 0.0F, 0.0F);
        } else if (thirdPerson && KhanhFeatures.enabled("camera_clip")) {
            this.thirdPerson = true;
            setPos(focusedEntity.getCameraPosVec(tickProgress));
            float distance = (float) KhanhFeatures.decimal("camera_clip", "distance", 4.0);
            moveBy(inverseView ? distance : -distance, 0.0F, 0.0F);
        }
    }
}
