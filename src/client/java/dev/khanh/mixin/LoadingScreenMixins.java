package dev.khanh.mixin;

import dev.khanh.gui.KhanhLoadingScreens;
import java.util.List;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Drawable;
import net.minecraft.client.gui.screen.DisconnectedScreen;
import net.minecraft.client.gui.screen.MessageScreen;
import net.minecraft.client.gui.screen.ProgressScreen;
import net.minecraft.client.gui.screen.ReconfiguringScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.SplashOverlay;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.gui.screen.world.LevelLoadingScreen;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Khanh Client loading screens: world loading, connecting, saving and every message in between. */
public final class LoadingScreenMixins {
    private LoadingScreenMixins() { }

    @Mixin(Screen.class)
    public interface Drawables {
        @Accessor("drawables")
        List<Drawable> khanh$drawables();
    }

    /** Saving, disconnected, progress and reconfiguring screens keep their text on Zero's backdrop. */
    @Mixin(Screen.class)
    public abstract static class Backdrop {
        @Inject(method = "renderBackground", at = @At("HEAD"), cancellable = true)
        private void khanh$loadingBackdrop(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo info) {
            Screen self = (Screen) (Object) this;
            if (!KhanhLoadingScreens.enabled()) return;
            if (self instanceof MessageScreen || self instanceof ProgressScreen || self instanceof ReconfiguringScreen
                || self instanceof DisconnectedScreen) {
                KhanhLoadingScreens.backdrop(context, self.width, self.height);
                info.cancel();
            }
        }
    }

    @Mixin(LevelLoadingScreen.class)
    public abstract static class WorldLoading {
        @Shadow private float loadProgress;

        @Inject(method = "render", at = @At("HEAD"), cancellable = true)
        private void khanh$worldLoading(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo info) {
            if (!KhanhLoadingScreens.enabled()) return;
            Screen self = (Screen) (Object) this;
            KhanhLoadingScreens.full(context, self.width, self.height, Text.literal("Loading world"), loadProgress);
            info.cancel();
        }

        @Inject(method = "renderBackground", at = @At("HEAD"), cancellable = true)
        private void khanh$noVanillaBackground(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo info) {
            if (KhanhLoadingScreens.enabled()) info.cancel();
        }
    }

    @Mixin(ConnectScreen.class)
    public abstract static class Connecting {
        @Shadow private Text status;

        @Inject(method = "render", at = @At("HEAD"), cancellable = true)
        private void khanh$connecting(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo info) {
            if (!KhanhLoadingScreens.enabled()) return;
            Screen self = (Screen) (Object) this;
            KhanhLoadingScreens.full(context, self.width, self.height, status, -1.0F);
            for (Drawable drawable : ((Drawables) self).khanh$drawables()) drawable.render(context, mouseX, mouseY, delta);
            info.cancel();
        }
    }

    /** Mojang's red startup splash becomes black. */
    @Mixin(SplashOverlay.class)
    public abstract static class Splash {
        @Inject(method = "method_35733", at = @At("HEAD"), cancellable = true)
        private static void khanh$blackSplash(CallbackInfoReturnable<Integer> info) {
            if (KhanhLoadingScreens.enabled()) info.setReturnValue(0xFF000000);
        }
    }
}
