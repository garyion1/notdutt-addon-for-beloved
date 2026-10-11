package dev.khanh.feature;

/** Density is a configurable heuristic, not evidence of player activity. */
public final class AmethystDensity {
    private AmethystDensity() { }
    public static boolean qualifies(int matureClusters, int threshold) {
        return matureClusters >= Math.max(6, threshold);
    }
}
