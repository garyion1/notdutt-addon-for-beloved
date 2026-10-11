package me.tpaburst.hud;

import me.tpaburst.ui.Theme;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;

/** Appearance settings shared by every TPA Tools HUD element. */
final class HudStyle {
    final Setting<Double> scale;
    final Setting<Boolean> shadow;
    final Setting<SettingColor> accent;
    final Setting<SettingColor> background;
    final Setting<SettingColor> border;

    final Color text = Theme.color(Theme.TEXT);
    final Color secondary = Theme.color(Theme.TEXT_SECONDARY);
    final Color muted = Theme.color(Theme.TEXT_MUTED);
    final Color good = Theme.color(Theme.GOOD);
    final Color off = Theme.color(Theme.BAD);
    final Color surface = Theme.color(Theme.SURFACE);
    final Color track = Theme.color(0xFF22252D);
    final Color divider = Theme.color(0x22FFFFFF);

    HudStyle(SettingGroup group) {
        scale = group.add(new DoubleSetting.Builder()
            .name("scale")
            .description("Size of this element.")
            .defaultValue(1.0)
            .min(0.5)
            .sliderRange(0.5, 3.0)
            .build()
        );
        shadow = group.add(new BoolSetting.Builder()
            .name("text-shadow")
            .defaultValue(false)
            .build()
        );
        accent = group.add(new ColorSetting.Builder()
            .name("accent")
            .defaultValue(new SettingColor(224, 69, 69, 255))
            .build()
        );
        background = group.add(new ColorSetting.Builder()
            .name("background")
            .defaultValue(new SettingColor(16, 17, 22, 220))
            .build()
        );
        border = group.add(new ColorSetting.Builder()
            .name("border")
            .defaultValue(new SettingColor(42, 46, 57, 255))
            .build()
        );
    }

    /** Pixels per GUI pixel: HUD space is unscaled, vanilla text is 2px per GUI px at scale 1. */
    double unit() {
        return 2 * scale.get();
    }

    double textScale() {
        return scale.get();
    }

    double textWidth(HudRenderer r, String s) {
        return r.textWidth(s, shadow.get(), textScale());
    }

    double textHeight(HudRenderer r) {
        return r.textHeight(shadow.get(), textScale());
    }

    void text(HudRenderer r, String s, double x, double y, Color c) {
        r.text(s, x, y, c, shadow.get(), textScale());
    }
}
