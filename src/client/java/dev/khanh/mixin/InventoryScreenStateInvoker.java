package dev.khanh.mixin;

import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Vanilla's render-state builder for GUI entities, so the wardrobe can spin the player all the way round. */
@Mixin(InventoryScreen.class)
public interface InventoryScreenStateInvoker {
    @Invoker("drawEntity")
    static EntityRenderState khanh$renderState(LivingEntity entity) {
        throw new AssertionError();
    }
}
