package dev.khanh.feature;

import java.util.Locale;

/** The glint wrap themes: each is a tileable PNG in assets/khanh/textures/glint. */
public final class GlintPattern {
    public static final String[] THEMES = {
        "Galaxy", "Nebula", "Andromeda", "Aurora", "Black Hole", "Starfield", "Void", "Solar Flare", "Ice Field",
        "Crimson", "Emerald", "Ocean", "Amethyst", "Gold", "Rose", "Toxic", "Silver", "Obsidian", "Rainbow"
    };

    private GlintPattern() { }

    public static String fileName(String theme) {
        return theme.toLowerCase(Locale.ROOT).replace(' ', '_') + ".png";
    }

    /** Texture path inside the khanh namespace. */
    public static String texturePath(String theme) {
        return "textures/glint/" + fileName(theme);
    }
}
