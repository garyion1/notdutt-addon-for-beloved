package me.tpaburst.modules;

import me.tpaburst.TPABurstAddon;
import me.tpaburst.history.HistoryScreen;
import meteordevelopment.meteorclient.systems.modules.Module;

public final class LootHistory extends Module {
    public LootHistory() {
        super(TPABurstAddon.CATEGORY, "loot-history", "Opens the session-only loot history GUI.");
    }

    @Override
    public void onActivate() {
        if (mc != null) mc.setScreen(new HistoryScreen(false));
        if (isActive()) disable();
    }
}
