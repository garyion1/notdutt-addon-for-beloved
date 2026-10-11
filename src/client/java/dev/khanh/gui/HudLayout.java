package dev.khanh.gui;

import java.util.EnumMap;
import java.util.Map;

public final class HudLayout {
    private static final Map<Widget, State> WIDGETS = new EnumMap<>(Widget.class);

    static {
        for (Widget widget : Widget.values()) {
            WIDGETS.put(widget, new State());
        }
    }

    private HudLayout() {
    }

    public static State widget(Widget widget) {
        return WIDGETS.get(widget);
    }

    public enum Widget {
        WATERMARK,
        POTIONS,
        HOTKEYS,
        MODULES,
        INFO,
        COORDS,
        PING,
        TPS,
        BPS,
        ARMOR,
        INVENTORY,
        TOTEMS,
        COMPASS,
        STAFF,
        FRIENDS,
        SPOTIFY,
        PLAYER_RADAR,
        TARGET_HUD,
        RTP_MAP,
        KEYSTROKES,
        CPS,
        FPS
    }

    public static final class State {
        public int x;
        public int y;
        public boolean positioned;
        public float scale = 1.0F;
        /** HUD size when x/y were saved; 0 means a legacy absolute position. */
        public int refWidth;
        public int refHeight;

        /** Saved X kept the same distance from whichever edge it was nearest to. */
        public int resolveX(int hudWidth, int widgetWidth) {
            int value = refWidth <= 0 || x + widgetWidth / 2 < refWidth / 2 ? x : hudWidth - (refWidth - x);
            return Math.max(0, Math.min(value, Math.max(0, hudWidth - widgetWidth)));
        }

        public int resolveY(int hudHeight, int widgetHeight) {
            int value = refHeight <= 0 || y + widgetHeight / 2 < refHeight / 2 ? y : hudHeight - (refHeight - y);
            return Math.max(0, Math.min(value, Math.max(0, hudHeight - widgetHeight)));
        }
    }
}
