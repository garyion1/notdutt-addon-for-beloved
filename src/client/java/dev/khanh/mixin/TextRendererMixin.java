package dev.khanh.mixin;

import dev.khanh.feature.PrivacyService;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.text.OrderedText;
import net.minecraft.text.StringVisitable;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(TextRenderer.class)
public abstract class TextRendererMixin {
    @ModifyVariable(
        method = "draw(Ljava/lang/String;FFIZLorg/joml/Matrix4f;Lnet/minecraft/client/render/VertexConsumerProvider;Lnet/minecraft/client/font/TextRenderer$TextLayerType;II)V",
        at = @At("HEAD"), argsOnly = true, ordinal = 0, require = 0
    )
    private String khanh$protectDrawnString(String text) {
        return PrivacyService.transform(text);
    }

    @ModifyVariable(
        method = "draw(Lnet/minecraft/text/Text;FFIZLorg/joml/Matrix4f;Lnet/minecraft/client/render/VertexConsumerProvider;Lnet/minecraft/client/font/TextRenderer$TextLayerType;II)V",
        at = @At("HEAD"), argsOnly = true, ordinal = 0, require = 0
    )
    private Text khanh$protectDrawnText(Text text) {
        return PrivacyService.transform(text);
    }

    @ModifyVariable(
        method = "draw(Lnet/minecraft/text/OrderedText;FFIZLorg/joml/Matrix4f;Lnet/minecraft/client/render/VertexConsumerProvider;Lnet/minecraft/client/font/TextRenderer$TextLayerType;II)V",
        at = @At("HEAD"), argsOnly = true, ordinal = 0, require = 0
    )
    private OrderedText khanh$protectDrawnOrderedText(OrderedText text) {
        return PrivacyService.transform(text);
    }

    // 1.21.11 draws all GUI and world text (scoreboard, chat, tab list, name tags, HUD) through
    // prepare(), not draw(): without these, Name Protect missed nearly everything on screen.
    @ModifyVariable(method = "prepare(Ljava/lang/String;FFIZI)Lnet/minecraft/client/font/TextRenderer$GlyphDrawable;",
        at = @At("HEAD"), argsOnly = true, ordinal = 0, require = 0)
    private String khanh$protectPreparedString(String text) {
        return PrivacyService.transform(text);
    }

    @ModifyVariable(method = "prepare(Lnet/minecraft/text/OrderedText;FFIZZI)Lnet/minecraft/client/font/TextRenderer$GlyphDrawable;",
        at = @At("HEAD"), argsOnly = true, ordinal = 0, require = 0)
    private OrderedText khanh$protectPreparedText(OrderedText text) {
        return PrivacyService.transform(text);
    }

    @ModifyVariable(method = "getWidth(Ljava/lang/String;)I", at = @At("HEAD"), argsOnly = true, ordinal = 0, require = 0)
    private String khanh$protectMeasuredString(String text) {
        return PrivacyService.transform(text);
    }

    @ModifyVariable(method = "getWidth(Lnet/minecraft/text/StringVisitable;)I", at = @At("HEAD"), argsOnly = true, ordinal = 0, require = 0)
    private StringVisitable khanh$protectMeasuredText(StringVisitable text) {
        return text instanceof Text component ? PrivacyService.transform(component) : text;
    }

    @ModifyVariable(method = "getWidth(Lnet/minecraft/text/OrderedText;)I", at = @At("HEAD"), argsOnly = true, ordinal = 0, require = 0)
    private OrderedText khanh$protectMeasuredOrderedText(OrderedText text) {
        return PrivacyService.transform(text);
    }
}
