package dev.khanh.gui;

/** Adaptive blur never oscillates: a slow opening disables the extra blur for
 * that opening only. A single hitch or a paused/tabbed-out frame does not count.
 */
public final class GuiFrameBudget {
    private long previous;
    private int samples, slowFrames;
    private boolean reduced;
    public void frame(long now) {
        long elapsed = previous == 0 ? 0 : now - previous; previous = now;
        if (elapsed < 1_000_000 || elapsed > 250_000_000) return;
        if (++samples <= 8) return;
        if (elapsed > 30_000_000) slowFrames++; else slowFrames = Math.max(0, slowFrames - 1);
        if (slowFrames >= 6) reduced = true;
    }
    public boolean allowBlur(String quality) { return !quality.equals("Off") && (quality.equals("Always") || !reduced); }
    public boolean reduced() { return reduced; }
}
