package dev.khanh.feature;

import java.util.HashMap;
import java.util.Map;

/** Pure presentation cache: never owns or mutates inventory stacks. */
public final class SlotMotion {
    public record Sprite(String item, float x, float y) { }
    public record Offset(float x, float y) { }
    private record Move(float dx, float dy, long start) { }
    private final Map<Integer, Move> moves = new HashMap<>();
    private Map<Integer, Sprite> previous = Map.of();
    public void clear() { previous = Map.of(); moves.clear(); }
    public void frame(Map<Integer, Sprite> current, long now, long duration) {
        for (var e : current.entrySet()) {
            int key = e.getKey(); Sprite sprite = e.getValue(), old = previous.get(key);
            if (sprite.item.isEmpty()) { moves.remove(key); continue; }
            if (old != null && old.item.equals(sprite.item)) continue;
            Sprite source = null; float nearest = Float.MAX_VALUE;
            for (var candidate : previous.entrySet()) {
                Sprite s = candidate.getValue(); Sprite still = current.get(candidate.getKey());
                if (!s.item.equals(sprite.item) || still != null && still.item.equals(s.item)) continue;
                float d = (s.x - sprite.x) * (s.x - sprite.x) + (s.y - sprite.y) * (s.y - sprite.y);
                if (d < nearest) { nearest = d; source = s; }
            }
            if (source != null && nearest <= 320 * 320) moves.put(key, new Move(source.x - sprite.x, source.y - sprite.y, now));
            else moves.remove(key);
        }
        moves.keySet().retainAll(current.keySet());
        moves.values().removeIf(m -> now - m.start >= duration);
        previous = Map.copyOf(current);
    }
    public Offset offset(int slot, long now, long duration) {
        Move move = moves.get(slot); if (move == null || duration <= 0) return new Offset(0, 0);
        float t = Math.max(0, Math.min(1, (now - move.start) / (float) duration));
        float left = (1 - t) * (1 - t) * (1 - t);
        return new Offset(move.dx * left, move.dy * left);
    }
}
