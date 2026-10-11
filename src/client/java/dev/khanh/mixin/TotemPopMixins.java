package dev.khanh.mixin;

import dev.khanh.KhanhClient;
import dev.khanh.feature.TotemService;
import dev.khanh.feature.KhanhFeatures;
import net.minecraft.client.gui.hud.InGameOverlayRenderer;
import net.minecraft.client.particle.TotemParticle;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.random.Random;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Totem Styles' pop options: the big pop animation, the particles, and a random style per pop. */
public final class TotemPopMixins {
    private TotemPopMixins() { }

    @Mixin(InGameOverlayRenderer.class)
    public abstract static class Animation {
        @Shadow private ItemStack floatingItem;
        @Shadow private int floatingItemTimer;

        @Inject(method = "setFloatingItem", at = @At("TAIL"))
        private void khanh$popAnimation(ItemStack stack, Random random, CallbackInfo info) {
            if (!KhanhFeatures.enabled("totem_styles") || stack == null || !stack.isOf(Items.TOTEM_OF_UNDYING)) return;
            if (KhanhFeatures.bool("totem_styles", "random_each_pop", false)) {
                var option = KhanhClient.modules().byId("totem_styles").option("style");
                option.setChoice(TotemService.STYLES[random.nextInt(TotemService.STYLES.length)]);
            }
            switch (KhanhFeatures.choice("totem_styles", "pop_animation", "Normal")) {
                case "Off" -> { floatingItem = null; floatingItemTimer = 0; }
                case "Quick" -> floatingItemTimer = Math.min(floatingItemTimer, 20);
                default -> { }
            }
        }
    }

    @Mixin(TotemParticle.class)
    public abstract static class Particles {
        @Inject(method = "<init>", at = @At("TAIL"))
        private void khanh$popParticles(ClientWorld world, double x, double y, double z, double vx, double vy, double vz,
                                             net.minecraft.client.particle.SpriteProvider sprites, CallbackInfo info) {
            if (!KhanhFeatures.enabled("totem_styles")) return;
            TotemParticle self = (TotemParticle) (Object) this;
            switch (KhanhFeatures.choice("totem_styles", "pop_particles", "Normal")) {
                case "Off" -> self.markDead();
                case "Coloured" -> {
                    int colour = KhanhFeatures.color("totem_styles", "particle_color", 0xFFB76CFF);
                    float shade = 0.8F + world.random.nextFloat() * 0.35F;
                    self.setColor(Math.min(1.0F, ((colour >> 16) & 255) / 255.0F * shade),
                        Math.min(1.0F, ((colour >> 8) & 255) / 255.0F * shade), Math.min(1.0F, (colour & 255) / 255.0F * shade));
                }
                default -> { }
            }
        }
    }
}
