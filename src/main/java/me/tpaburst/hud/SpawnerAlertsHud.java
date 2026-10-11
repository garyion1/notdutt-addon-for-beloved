package me.tpaburst.hud;

import java.util.List;
import me.tpaburst.TPABurstAddon;
import me.tpaburst.ui.SpawnerAlerts;
import me.tpaburst.ui.Theme;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.utils.render.color.Color;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

/**
 * Movable replacement for the spawner toast. Set Spawner Noti's "alert-style" to Hud or Both,
 * then place this element anywhere from the HUD editor.
 */
public class SpawnerAlertsHud extends HudElement {
    public static final HudElementInfo<SpawnerAlertsHud> INFO = new HudElementInfo<>(TPABurstAddon.HUD_GROUP, "spawner-alerts",
        "Movable spawner alert popups (set Spawner Noti alert-style to Hud or Both).", SpawnerAlertsHud::new);

    private static final ItemStack ICON = Items.SPAWNER.getDefaultStack();
    private static final double CARD_W = 170;
    private static final double CARD_H = 34;
    private static final double GAP = 4;

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgStyle = settings.createGroup("Style");

    private final Setting<Integer> maxCards = sgGeneral.add(new IntSetting.Builder()
        .name("max-alerts")
        .description("How many alerts can be stacked at once.")
        .defaultValue(3)
        .range(1, 5)
        .sliderRange(1, 5)
        .build()
    );

    private final Setting<Boolean> stackUp = sgGeneral.add(new BoolSetting.Builder()
        .name("stack-upwards")
        .description("New alerts push older ones upwards instead of downwards.")
        .defaultValue(false)
        .build()
    );

    private final HudStyle style = new HudStyle(sgStyle);
    private final Color accent = new Color();

    public SpawnerAlertsHud() {
        super(INFO);
    }

    @Override
    public void render(HudRenderer r) {
        double u = style.unit();
        List<SpawnerAlerts.Alert> alerts = SpawnerAlerts.active();
        boolean preview = alerts.isEmpty() && isInEditor();
        if (preview) {
            long now = System.currentTimeMillis();
            alerts = List.of(new SpawnerAlerts.Alert("Zombie", 128, -24, -512, "below", now - (now % 6000), 6000, style.accent.get().getPacked()));
        }

        int count = Math.min(maxCards.get(), alerts.size());
        double cw = CARD_W * u;
        double ch = CARD_H * u;
        double gap = GAP * u;
        double totalH = Math.max(ch, count * ch + Math.max(0, count - 1) * gap);
        setSize(cw, totalH);
        if (count == 0) return;

        long now = System.currentTimeMillis();
        for (int i = 0; i < count; i++) {
            SpawnerAlerts.Alert a = alerts.get(i);
            double cy = stackUp.get() ? y + totalH - ch - i * (ch + gap) : y + i * (ch + gap);
            drawCard(r, a, x, cy, u, preview ? 0.6f : a.remaining(now));
        }
    }

    private void drawCard(HudRenderer r, SpawnerAlerts.Alert a, double cx, double cy, double u, float left) {
        double px = Math.max(1, Math.round(u / 2));
        double w = CARD_W * u;
        double h = CARD_H * u;
        accent.set((a.accentRgb() >> 16) & 0xFF, (a.accentRgb() >> 8) & 0xFF, a.accentRgb() & 0xFF, 255);

        Theme.panel(r, cx, cy, w, h, px, style.background.get(), style.border.get());
        r.quad(cx + px, cy + 2 * u, 2 * u, h - 4 * u, accent);

        Theme.softRect(r, cx + 7 * u, cy + 7 * u, 20 * u, 20 * u, px, style.surface);
        r.item(ICON, (int) (cx + 9 * u), (int) (cy + 9 * u), (float) u, false);

        double tx = cx + 30 * u;
        style.text(r, a.mobType() + " Spawner", tx, cy + 7 * u, style.text);
        String sub = "Spawner " + a.depth() + "  ";
        style.text(r, sub, tx, cy + 19 * u, style.muted);
        style.text(r, a.x() + ", " + a.y() + ", " + a.z(), tx + style.textWidth(r, sub), cy + 19 * u, style.secondary);

        double barX = cx + 4 * u;
        double barW = w - 6 * u;
        r.quad(barX, cy + h - 3 * u, barW, u, style.track);
        if (left > 0) r.quad(barX, cy + h - 3 * u, barW * left, u, accent);
    }
}
