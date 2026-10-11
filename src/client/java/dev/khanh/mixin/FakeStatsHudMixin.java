package dev.khanh.mixin;

import dev.khanh.feature.FakeScoreboardService;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.scoreboard.ScoreboardObjective;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Fake Stats: draw the private fake board in the sidebar instead of the server's. */
@Mixin(InGameHud.class)
public abstract class FakeStatsHudMixin {
    @Shadow
    private void renderScoreboardSidebar(DrawContext context, ScoreboardObjective objective) {
        throw new AssertionError();
    }

    @Inject(method = "renderScoreboardSidebar(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/client/render/RenderTickCounter;)V",
        at = @At("HEAD"), cancellable = true)
    private void khanh$fakeStats(DrawContext context, RenderTickCounter counter, CallbackInfo info) {
        ScoreboardObjective fake = FakeScoreboardService.objective();
        if (fake == null) return;
        context.createNewRootLayer();
        renderScoreboardSidebar(context, fake);
        info.cancel();
    }
}
