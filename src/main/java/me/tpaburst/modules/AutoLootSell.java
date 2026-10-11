package me.tpaburst.modules;

import java.util.Locale;
import me.tpaburst.SharedState;
import me.tpaburst.TPABurstAddon;
import me.tpaburst.history.HistoryStore;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.math.Box;

public class AutoLootSell extends Module {
    public enum State {
        WAITING("waiting"),
        OPEN_SELL("opening"),
        SELLING("selling"),
        FINISH("finishing");

        public final String label;

        State(String label) {
            this.label = label;
        }
    }

    private static final int CENTER_SLOT = 22;

    private State state = State.WAITING;
    private int timer;
    private int quietTicks;

    public AutoLootSell() {
        super(TPABurstAddon.CATEGORY, "auto-loot-sell",
            "Stays still, opens /sell when drops are nearby, and continuously shift-clicks only the center main-inventory slot as floor loot refills it.");
    }

    public State getState() {
        return state;
    }

    @Override
    public void onActivate() {
        state = State.WAITING;
        timer = 0;
        quietTicks = 0;
        SharedState.pauseTPA = false;
    }

    @Override
    public void onDeactivate() {
        SharedState.pauseTPA = false;
    }

    @Override
    public String getInfoString() {
        return state.label;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc == null || mc.player == null || mc.world == null) return;

        switch (state) {
            case WAITING -> {
                if (!hasNearbyDrops(7.0)) return;
                SharedState.pauseTPA = true;
                state = State.OPEN_SELL;
                timer = 0;
                quietTicks = 0;
            }
            case OPEN_SELL -> {
                timer++;
                if (sellContainerReady()) {
                    state = State.SELLING;
                    timer = 0;
                    quietTicks = 0;
                    return;
                }
                if (timer == 1 || timer % 40 == 0) {
                    ClientPlayNetworkHandler handler = mc.getNetworkHandler();
                    if (handler != null) handler.sendChatCommand("sell");
                }
            }
            case SELLING -> {
                if (!sellContainerReady()) {
                    state = State.OPEN_SELL;
                    timer = 0;
                    return;
                }
                boolean moved = false;
                timer++;
                if ((timer & 1) == 0) moved = shiftNewLoot();

                if (!moved && !hasNearbyDrops(7.0)) quietTicks++;
                else quietTicks = 0;

                if (quietTicks >= 30) {
                    clickSellButtonIfPresent();
                    state = State.FINISH;
                    timer = 0;
                }
            }
            case FINISH -> {
                if (++timer >= 8) {
                    try {
                        mc.player.closeHandledScreen();
                    } catch (Throwable ignored) {
                    }
                    SharedState.pauseTPA = false;
                    state = State.WAITING;
                    timer = 0;
                    quietTicks = 0;
                }
            }
        }
    }

    private boolean sellContainerReady() {
        ScreenHandler handler = mc.player.currentScreenHandler;
        return handler != null && !(handler instanceof PlayerScreenHandler) && handler.slots != null && handler.slots.size() > 46;
    }

    private boolean shiftNewLoot() {
        ScreenHandler handler = mc.player.currentScreenHandler;
        if (!sellContainerReady() || mc.interactionManager == null) return false;

        PlayerInventory inv = mc.player.getInventory();
        if (inv.getStack(CENTER_SLOT).isEmpty()) return false;
        if (handleProtectedCenter()) return true;

        int slot = handler.slots.size() - 23;
        if (slot < 0 || slot >= handler.slots.size()) return false;

        HistoryStore.addLoot(inv.getStack(CENTER_SLOT));
        mc.interactionManager.clickSlot(handler.syncId, slot, 0, SlotActionType.QUICK_MOVE, mc.player);
        return true;
    }

    private void clickSellButtonIfPresent() {
        ScreenHandler handler = mc.player.currentScreenHandler;
        if (!sellContainerReady() || mc.interactionManager == null) return;
        if (handler.slots.size() > 53) {
            mc.interactionManager.clickSlot(handler.syncId, 53, 0, SlotActionType.PICKUP, mc.player);
        }
    }

    private boolean hasNearbyDrops(double range) {
        Box box = mc.player.getBoundingBox().expand(range);
        return !mc.world.getEntitiesByClass(ItemEntity.class, box, e -> true).isEmpty();
    }

    private boolean handleProtectedCenter() {
        PlayerInventory inv = mc.player.getInventory();
        String name = inv.getStack(CENTER_SLOT).toString().toLowerCase(Locale.ROOT);
        if (!name.contains("shard") && !name.contains("drill") && !name.contains("sell axe") && !name.contains("potion of haste")) {
            return false;
        }

        ScreenHandler handler = mc.player.currentScreenHandler;
        int size = handler.slots.size();
        int center = size - 23;

        for (int i = 9; i <= 35; i++) {
            if (i != CENTER_SLOT && inv.getStack(i).isEmpty()) {
                int target = size - 36 + (i - 9);
                mc.interactionManager.clickSlot(handler.syncId, center, 0, SlotActionType.PICKUP, mc.player);
                mc.interactionManager.clickSlot(handler.syncId, target, 0, SlotActionType.PICKUP, mc.player);
                return true;
            }
        }

        mc.interactionManager.clickSlot(handler.syncId, center, 0, SlotActionType.PICKUP, mc.player);
        mc.interactionManager.clickSlot(handler.syncId, -999, 0, SlotActionType.PICKUP, mc.player);
        return true;
    }
}
