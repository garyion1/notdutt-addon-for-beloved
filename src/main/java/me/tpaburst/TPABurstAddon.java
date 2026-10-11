package me.tpaburst;

import me.tpaburst.history.HistoryStore;
import me.tpaburst.hud.SpawnerAlertsHud;
import me.tpaburst.hud.TpaToolsHud;
import me.tpaburst.modules.AutoLootSell;
import me.tpaburst.modules.AutoStrength;
import me.tpaburst.modules.KillHistory;
import me.tpaburst.modules.LootHistory;
import me.tpaburst.modules.SpawnerNotifier;
import me.tpaburst.modules.TPABurst;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.systems.hud.Hud;
import meteordevelopment.meteorclient.systems.hud.HudGroup;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.orbit.EventHandler;

public class TPABurstAddon extends MeteorAddon {
    public static final Category CATEGORY = new Category("TPA Tools");
    public static final HudGroup HUD_GROUP = new HudGroup("TPA Tools");

    @Override
    public void onInitialize() {
        Modules.get().add(new TPABurst());
        Modules.get().add(new AutoStrength());
        Modules.get().add(new AutoLootSell());
        Modules.get().add(new KillHistory());
        Modules.get().add(new LootHistory());
        Modules.get().add(new SpawnerNotifier());

        Hud.get().register(TpaToolsHud.INFO);
        Hud.get().register(SpawnerAlertsHud.INFO);

        // Record kills from chat all session, not only while Auto Loot Sell is on.
        MeteorClient.EVENT_BUS.subscribe(this);
    }

    @EventHandler
    private void onReceiveMessage(ReceiveMessageEvent event) {
        HistoryStore.captureKillMessage(event.getMessage().getString());
    }

    @Override
    public void onRegisterCategories() {
        Modules.registerCategory(CATEGORY);
    }

    @Override
    public String getPackage() {
        return "me.tpaburst";
    }
}
