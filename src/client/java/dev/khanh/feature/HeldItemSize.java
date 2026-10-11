package dev.khanh.feature;

import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.registry.tag.ItemTags;

/** Changes only the submitted first-person item transform, never the hand or inventory. */
public final class HeldItemSize {
    private HeldItemSize() { }
    public static float scale(ItemStack item) {
        if (item.isEmpty()) return 1;
        String group = item.isOf(Items.TOTEM_OF_UNDYING) ? "totems"
            : item.contains(DataComponentTypes.TOOL) || item.isIn(ItemTags.SWORDS)
                || item.isOf(Items.BOW) || item.isOf(Items.CROSSBOW)
                || item.isOf(Items.TRIDENT) || item.isOf(Items.MACE) ? "tools" : "other";
        return bounded(KhanhFeatures.decimal("item_size", group, group.equals("totems") ? .6 : group.equals("tools") ? .7 : .8));
    }
    public static float bounded(double value) {
        return Double.isFinite(value) ? (float)Math.max(.25, Math.min(1, value)) : 1;
    }
}
