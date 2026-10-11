package dev.khanh.feature;

/** One reading from the Spotify app; {@code artKey} is an image URL or "file:path#version", or empty. */
public record SpotifyState(
    boolean available,
    boolean active,
    boolean playing,
    String title,
    String artist,
    long positionMs,
    long durationMs,
    long receivedNanos,
    String artKey
) {
    public static final SpotifyState IDLE = new SpotifyState(true, false, false, "Nothing playing", "Open Spotify to start listening", 0L, 0L, 0L, "");
    public static final SpotifyState OPEN = new SpotifyState(true, false, false, "Spotify is open", "Press play to start listening", 0L, 0L, 0L, "");
    public static final SpotifyState NO_PLAYERCTL = new SpotifyState(false, false, false, "Spotify not linked", "Install playerctl to link Spotify", 0L, 0L, 0L, "");
    public static final SpotifyState UNAVAILABLE = new SpotifyState(false, false, false, "Spotify not linked", "Open the Spotify desktop app", 0L, 0L, 0L, "");

    public long livePositionMs() {
        if (!playing || durationMs <= 0L) {
            return Math.max(0L, positionMs);
        }
        long elapsed = Math.max(0L, (System.nanoTime() - receivedNanos) / 1_000_000L);
        return Math.min(durationMs, Math.max(0L, positionMs + elapsed));
    }
}
