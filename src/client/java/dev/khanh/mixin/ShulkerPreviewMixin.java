package dev.khanh.mixin;

import dev.khanh.feature.KhanhFeatures;
import dev.khanh.gui.HudSurfaceRenderer;
import dev.khanh.gui.SmoothGuiShapes;
import java.util.List;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ContainerComponent;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Renders only the container contents already present in the hovered item data. */
@Mixin(HandledScreen.class)
public abstract class ShulkerPreviewMixin {
    @Shadow protected Slot focusedSlot;

    @Inject(method = "drawMouseoverTooltip", at = @At("TAIL"))
    private void khanh$previewShulker(DrawContext context, int mouseX, int mouseY, CallbackInfo info) {
        if (!KhanhFeatures.enabled("shulker_preview") || focusedSlot == null) return;
        ItemStack stack = focusedSlot.getStack();
        if (!(stack.getItem() instanceof BlockItem block) || !(block.getBlock() instanceof ShulkerBoxBlock)) return;
        ContainerComponent container = stack.get(DataComponentTypes.CONTAINER);
        if (container == null) return;

        List<ItemStack> items = container.stream().limit(27).toList();
        int width = 174, height = 81;
        int x = Math.min(mouseX + 18, context.getScaledWindowWidth() - width - 4);
        int y = Math.min(mouseY + 12, context.getScaledWindowHeight() - height - 4);
        x = Math.max(4, x); y = Math.max(4, y);
        HudSurfaceRenderer.card(context, x, y, width, height);
        var textRenderer = MinecraftClient.getInstance().textRenderer;
        context.drawText(textRenderer, stack.getName(), x + 7, y + 6, HudSurfaceRenderer.TEXT, false);
        for (int i = 0; i < 27; i++) {
            int cellX = x + 6 + i % 9 * 18;
            int cellY = y + 21 + i / 9 * 18;
            SmoothGuiShapes.rect(context, cellX, cellY, 17, 17, 3, 0x66272A32, false);
            if (i >= items.size() || items.get(i).isEmpty()) continue;
            ItemStack item = items.get(i);
            context.drawItemWithoutEntity(item, cellX, cellY);
            if (KhanhFeatures.bool("shulker_preview", "counts", true) && item.getCount() > 1) {
                String count = Integer.toString(item.getCount());
                context.drawText(textRenderer, count, cellX + 17 - textRenderer.getWidth(count), cellY + 9,
                    0xFFF2F2F5, true);
            }
        }
    }
}
