package dev.khanh.feature;

/** Pure cosmetic transform: one complete swing always returns to its rest pose. */
public final class SwingStyle {
    private static final String[] STYLES = {"Smooth", "Static", "Down", "Drop Down", "Poke", "Back", "Spin", "Slant",
        "Block", "Swipe", "Orbit", "Flick", "Heavy", "Reverse", "Cross", "Fan", "Self Back", "To Back",
        "Feast", "Akrien", "Break", "Pander", "Uppercut", "Saber", "Whip", "Figure Eight", "Helix", "Recoil",
        "Pendulum", "Double Cut", "Snap", "Float"};
    public record Transform(float x, float y, float z, float rx, float ry, float rz) { }
    private SwingStyle() { }
    public static String[] styles() { return STYLES.clone(); }
    public static Transform transform(String style, float progress, float strength, int side) {
        float t = Math.max(0, Math.min(1, progress));
        if (t == 0 || t == 1) return new Transform(0, 0, 0, 0, 0, 0);
        float wave = (float) Math.sin(t * Math.PI), ease = (float) Math.sin(Math.sqrt(t) * Math.PI);
        float s = Math.max(0, Math.min(2, strength));
        float sweep = (float) Math.sin(t * Math.PI * 2) * wave;
        return switch (style) {
            case "Static" -> new Transform(0, 0, 0, 0, 0, 0);
            case "Down" -> new Transform(0, -wave * .15F * s, 0, -wave * 65 * s, 0, side * wave * 8 * s);
            case "Drop Down" -> new Transform(side * wave * .12F * s, -wave * .2F * s, 0, -wave * 80 * s, side * wave * 15 * s, side * wave * 28 * s);
            case "Poke" -> new Transform(0, 0, -wave * .45F * s, -wave * 12 * s, 0, 0);
            case "Back" -> new Transform(0, wave * .08F * s, wave * .2F * s, wave * 48 * s, side * wave * 30 * s, 0);
            case "Spin" -> new Transform(0, 0, 0, 0, 0, side * (float) Math.sin(t * Math.PI * 2) * 160 * s);
            case "Slant" -> new Transform(side * wave * .12F * s, 0, 0, -wave * 40 * s, side * wave * 38 * s, side * wave * 65 * s);
            case "Block" -> new Transform(-side * wave * .13F * s, wave * .09F * s, 0, -wave * 20 * s, side * wave * 35 * s, side * wave * 50 * s);
            case "Swipe" -> new Transform(side * wave * .20F * s, 0, -wave * .08F * s, -wave * 14 * s, side * wave * 72 * s, side * wave * 18 * s);
            case "Orbit" -> new Transform(side * (float) Math.sin(t * Math.PI * 2) * wave * .12F * s,
                wave * .07F * s, -wave * .10F * s, -wave * 32 * s,
                side * (float) Math.sin(t * Math.PI * 2) * wave * 42 * s, side * wave * 26 * s);
            case "Flick" -> new Transform(side * wave * .05F * s, wave * .04F * s, -wave * .12F * s,
                -wave * 34 * s, side * wave * 12 * s, side * wave * 86 * s);
            case "Heavy" -> new Transform(0, -wave * .16F * s, wave * .06F * s, -wave * 105 * s,
                side * wave * 10 * s, side * wave * 12 * s);
            case "Reverse" -> new Transform(0, wave * .10F * s, wave * .14F * s, wave * 64 * s,
                -side * wave * 30 * s, -side * wave * 20 * s);
            case "Cross" -> new Transform(-side * wave * .16F * s, wave * .05F * s, -wave * .08F * s,
                -wave * 38 * s, -side * wave * 48 * s, side * wave * 72 * s);
            case "Fan" -> new Transform(side * wave * .10F * s, -wave * .07F * s, 0,
                -wave * 28 * s, side * wave * 28 * s,
                side * (float) Math.sin(t * Math.PI * 2) * wave * 95 * s);
            case "Self Back" -> new Transform(-side * wave * .12F * s, wave * .12F * s, wave * .23F * s,
                wave * 55 * s, -side * wave * 48 * s, side * wave * 20 * s);
            case "To Back" -> new Transform(side * wave * .08F * s, -wave * .04F * s, wave * .3F * s,
                wave * 75 * s, side * wave * 65 * s, -side * wave * 32 * s);
            case "Feast" -> new Transform(-side * wave * .2F * s, wave * .08F * s, -wave * .12F * s,
                -wave * 60 * s, side * sweep * 70 * s, side * wave * 90 * s);
            case "Akrien" -> new Transform(-side * wave * .1F * s, -wave * .08F * s, -wave * .15F * s,
                -ease * 78 * s, side * wave * 24 * s, side * ease * 48 * s);
            case "Break" -> new Transform(side * sweep * .08F * s, -wave * .18F * s, -wave * .08F * s,
                -ease * 95 * s, side * sweep * 20 * s, side * wave * 22 * s);
            case "Pander" -> new Transform(side * sweep * .16F * s, wave * .04F * s, -wave * .12F * s,
                -wave * 45 * s, side * sweep * 90 * s, -side * wave * 55 * s);
            case "Uppercut" -> new Transform(side * wave * .08F * s, wave * .25F * s, -wave * .2F * s,
                wave * 85 * s, side * wave * 10 * s, -side * wave * 22 * s);
            case "Saber" -> new Transform(-side * sweep * .23F * s, wave * .08F * s, -wave * .1F * s,
                -wave * 25 * s, side * sweep * 95 * s, side * wave * 48 * s);
            case "Whip" -> new Transform(side * sweep * .14F * s, -wave * .08F * s, -ease * .25F * s,
                -ease * 72 * s, side * sweep * 80 * s, side * sweep * 110 * s);
            case "Figure Eight" -> new Transform(side * sweep * .18F * s,
                (float) Math.sin(t * Math.PI * 4) * wave * .08F * s, -wave * .1F * s,
                -wave * 35 * s, side * sweep * 60 * s, side * sweep * 55 * s);
            case "Helix" -> new Transform(side * sweep * .13F * s, wave * .12F * s, -wave * .24F * s,
                sweep * 60 * s, side * sweep * 75 * s, side * wave * 130 * s);
            case "Recoil" -> new Transform(0, wave * .06F * s, wave * .32F * s,
                wave * 38 * s, side * sweep * 8 * s, side * wave * 9 * s);
            case "Pendulum" -> new Transform(side * sweep * .23F * s, -wave * .14F * s, 0,
                -wave * 18 * s, 0, side * sweep * 100 * s);
            case "Double Cut" -> new Transform(side * sweep * .15F * s, -wave * .05F * s, -wave * .12F * s,
                -wave * 40 * s, side * sweep * 85 * s, -side * sweep * 65 * s);
            case "Snap" -> new Transform(0, -ease * .07F * s, -ease * .2F * s,
                -ease * 90 * s, side * wave * 15 * s, side * ease * 35 * s);
            case "Float" -> new Transform(side * sweep * .04F * s, wave * .1F * s, -wave * .06F * s,
                -wave * 22 * s, side * wave * 14 * s, side * sweep * 18 * s);
            default -> new Transform(0, -ease * .04F * s, 0, -ease * 52 * s, side * wave * -12 * s, side * ease * -12 * s);
        };
    }
}
