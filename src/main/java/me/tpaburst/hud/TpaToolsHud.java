package me.tpaburst.hud;

import java.util.ArrayList;
import java.util.List;
import me.tpaburst.TPABurstAddon;
import me.tpaburst.history.HistoryStore;
import me.tpaburst.modules.SpawnerNotifier;
import me.tpaburst.ui.Theme;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;

/** Movable status panel: which TPA Tools modules are on, plus session stats. */
public class TpaToolsHud extends HudElement {
    public static final HudElementInfo<TpaToolsHud> INFO = new HudElementInfo<>(TPABurstAddon.HUD_GROUP, "tpa-tools-status",
        "Shows TPA Tools module status and session stats. Drag it anywhere in the HUD editor.", TpaToolsHud::new);

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgStyle = settings.createGroup("Style");

    private final Setting<Boolean> showHeader = sgGeneral.add(new BoolSetting.Builder()
        .name("show-header")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> showInactive = sgGeneral.add(new BoolSetting.Builder()
        .name("show-inactive")
        .description("Also list modules that are turned off.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> showStats = sgGeneral.add(new BoolSetting.Builder()
        .name("show-stats")
        .description("Shows session kills, loot and nearby spawners.")
        .defaultValue(true)
        .build()
    );

    private final HudStyle style = new HudStyle(sgStyle);

    public TpaToolsHud() {
        super(INFO);
    }

    private List<Module> modules() {
        List<Module> out = new ArrayList<>();
        for (Module m : Modules.get().getGroup(TPABurstAddon.CATEGORY)) {
            if (m.isActive() || showInactive.get()) out.add(m);
        }
        return out;
    }

    @Override
    public void render(HudRenderer r) {
        double u = style.unit();
        double lineH = style.textHeight(r);
        double pad = 6 * u;
        double rowH = lineH + 4 * u;
        double dot = 4 * u;

        List<Module> mods = modules();
        int active = 0;
        for (Module m : mods) if (m.isActive()) active++;

        // Measure.
        String title = "TPA Tools";
        String badge = active + " on";
        double width = 120 * u;
        if (showHeader.get()) width = Math.max(width, pad * 2 + 8 * u + style.textWidth(r, title) + 12 * u + style.textWidth(r, badge));
        for (Module m : mods) {
            String info = m.isActive() ? m.getInfoString() : null;
            double w = pad * 2 + dot + 6 * u + style.textWidth(r, m.title) + (info != null ? 10 * u + style.textWidth(r, info) : 0);
            width = Math.max(width, w);
        }
        String stats = statsLine();
        if (showStats.get()) width = Math.max(width, pad * 2 + style.textWidth(r, stats));

        double height = pad;
        if (showHeader.get()) height += rowH + 3 * u;
        height += Math.max(1, mods.size()) * rowH;
        if (showStats.get()) height += 5 * u + rowH;
        height += pad - 2 * u;

        setSize(width, height);

        // Draw.
        double px = Math.max(1, Math.round(u / 2));
        Theme.panel(r, x, y, width, height, px, style.background.get(), style.border.get());
        r.quad(x + px, y + px, 2 * u, height - px * 2, style.accent.get());

        double cy = y + pad;
        if (showHeader.get()) {
            Theme.softRect(r, x + pad, cy + (lineH - 5 * u) / 2, 5 * u, 5 * u, px, style.accent.get());
            style.text(r, title, x + pad + 9 * u, cy + 2 * u, style.text);
            double bw = style.textWidth(r, badge) + 8 * u;
            Theme.softRect(r, x + width - pad - bw, cy, bw, lineH + 4 * u, px, style.surface);
            style.text(r, badge, x + width - pad - bw + 4 * u, cy + 2 * u, active > 0 ? style.good : style.muted);
            cy += rowH + 3 * u;
            r.quad(x + pad, cy - 2 * u, width - pad * 2, px, style.divider);
        }

        if (mods.isEmpty()) {
            style.text(r, "All modules off", x + pad, cy + 2 * u, style.muted);
            cy += rowH;
        }
        for (Module m : mods) {
            boolean on = m.isActive();
            Theme.softRect(r, x + pad, cy + 2 * u + (lineH - dot) / 2, dot, dot, px, on ? style.good : style.off);
            style.text(r, m.title, x + pad + dot + 6 * u, cy + 2 * u, on ? style.text : style.muted);
            String info = on ? m.getInfoString() : null;
            if (info != null) {
                style.text(r, info, x + width - pad - style.textWidth(r, info), cy + 2 * u, style.secondary);
            }
            cy += rowH;
        }

        if (showStats.get()) {
            cy += 2 * u;
            r.quad(x + pad, cy, width - pad * 2, px, style.divider);
            cy += 3 * u;
            style.text(r, stats, x + pad, cy + 2 * u, style.muted);
        }
    }

    private String statsLine() {
        SpawnerNotifier noti = Modules.get().get(SpawnerNotifier.class);
        int spawners = noti != null && noti.isActive() ? noti.getTrackedCount() : 0;
        return "Kills " + HistoryStore.killCount() + "   Loot " + HistoryStore.lootCount() + "   Spawners " + spawners;
    }
}
