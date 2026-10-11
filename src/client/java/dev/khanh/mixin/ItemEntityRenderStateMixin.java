package dev.khanh.mixin;

import dev.khanh.feature.ItemPhysicsState;
import net.minecraft.client.render.entity.state.ItemEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(ItemEntityRenderState.class)
public abstract class ItemEntityRenderStateMixin implements ItemPhysicsState {
    @Unique private boolean khanh$grounded;

    @Override public boolean khanh$isGrounded() { return khanh$grounded; }
    @Override public void khanh$setGrounded(boolean grounded) { khanh$grounded = grounded; }
}
