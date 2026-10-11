package dev.khanh.mixin;

import dev.khanh.feature.KhanhHud;
import dev.khanh.feature.KhanhHudRenderer;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChatScreen.class)
public abstract class ChatScreenMixin extends Screen {
    protected ChatScreenMixin(Text title) {
        super(title);
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void khanh$startHudDrag(Click click, boolean doubled, CallbackInfoReturnable<Boolean> info) {
        if (KhanhHud.mouseClicked(click.x(), click.y(), click.button())
            || KhanhHudRenderer.mouseClicked(click.x(), click.y(), click.button())) {
            info.setReturnValue(true);
        }
    }

    @Override
    public boolean mouseDragged(Click click, double deltaX, double deltaY) {
        return KhanhHud.mouseDragged(click.x(), click.y()) || KhanhHudRenderer.mouseDragged(click.x(), click.y()) || super.mouseDragged(click, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(Click click) {
        boolean hud = KhanhHud.mouseReleased();
        return KhanhHudRenderer.mouseReleased() || hud || super.mouseReleased(click);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        return KhanhHud.mouseScrolled(mouseX, mouseY, verticalAmount)
            || KhanhHudRenderer.mouseScrolled(mouseX, mouseY, verticalAmount)
            || super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }
}
