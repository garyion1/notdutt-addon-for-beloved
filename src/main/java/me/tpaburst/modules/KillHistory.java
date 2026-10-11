package me.tpaburst.modules;

import me.tpaburst.TPABurstAddon;
import me.tpaburst.history.HistoryScreen;
import meteordevelopment.meteorclient.systems.modules.Module;

public final class KillHistory extends Module {
    public KillHistory() {
        super(TPABurstAddon.CATEGORY, "kill-history", "Opens the session-only kill history GUI.");
    }

    @Override
    public void onActivate() {
        if (mc != null) mc.setScreen(new HistoryScreen(true));
        if (isActive()) disable();
    }
}
