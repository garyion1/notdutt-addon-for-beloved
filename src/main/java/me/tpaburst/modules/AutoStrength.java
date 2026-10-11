package me.tpaburst.modules;

import me.tpaburst.TPABurstAddon;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;

public class AutoStrength extends Module {
    private int cooldown;
    private boolean drinking;
    private int drinkTicks;
    private int previousSlot;

    public AutoStrength() {
        super(TPABurstAddon.CATEGORY, "auto-strength", "Drinks a normal Strength potion from your hotbar when Strength expires, then switches back.");
    }

    @Override
    public void onActivate() {
        cooldown = 0;
        drinking = false;
        drinkTicks = 0;
    }

    @Override
    public void onDeactivate() {
        if (drinking) finishDrink();
    }

    @Override
    public String getInfoString() {
        return drinking ? "drinking" : null;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc == null || mc.player == null || mc.interactionManager == null) return;
        if (cooldown > 0) cooldown--;

        if (drinking) {
            drinkTicks++;
            mc.options.useKey.setPressed(true);
            if (drinkTicks >= 40 || mc.player.hasStatusEffect(StatusEffects.STRENGTH)) finishDrink();
            return;
        }

        if (cooldown > 0 || mc.player.hasStatusEffect(StatusEffects.STRENGTH)) return;

        PlayerInventory inv = mc.player.getInventory();
        int slot = -1;
        for (int i = 0; i < 9; i++) {
            if (isDrinkableStrength(inv.getStack(i))) {
                slot = i;
                break;
            }
        }
        if (slot < 0) return;

        previousSlot = inv.getSelectedSlot();
        inv.setSelectedSlot(slot);
        mc.options.useKey.setPressed(true);
        mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
        drinking = true;
        drinkTicks = 0;
    }

    private void finishDrink() {
        mc.options.useKey.setPressed(false);
        if (mc.player != null) mc.player.getInventory().setSelectedSlot(previousSlot);
        drinking = false;
        drinkTicks = 0;
        cooldown = 20;
    }

    private boolean isDrinkableStrength(ItemStack stack) {
        if (stack == null || !stack.isOf(Items.POTION)) return false;
        if (!(stack.get(DataComponentTypes.POTION_CONTENTS) instanceof PotionContentsComponent contents)) return false;
        Iterable<StatusEffectInstance> effects = contents.getEffects();
        if (effects == null) return false;
        for (StatusEffectInstance effect : effects) {
            if (effect != null && effect.getEffectType() == StatusEffects.STRENGTH) return true;
        }
        return false;
    }
}
