package dev.khanh.feature;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Groups cluster-bearing chunks into real edge-connected regions. A region is
 * promoted only when its combined mature-cluster count reaches the configured
 * threshold; every occupied member chunk then receives its own stable marker.
 */
public final class DenseClusterRegionIndex {
    private DenseClusterRegionIndex() { }

    public static long key(int chunkX, int chunkZ) {
        return (chunkX & 0xFFFFFFFFL) | ((chunkZ & 0xFFFFFFFFL) << 32);
    }

    public static Map<Long, Integer> qualifyingChunks(Map<Long, Integer> counts, int threshold) {
        return qualifyingChunks(counts, threshold, 1);
    }

    public static Map<Long, Integer> qualifyingChunks(Map<Long, Integer> counts, int threshold, int minimumChunks) {
        Map<Long, Integer> occupied = new HashMap<>();
        for (Map.Entry<Long, Integer> entry : counts.entrySet()) {
            int count = entry.getValue() == null ? 0 : entry.getValue();
            if (count > 0) {
                occupied.put(entry.getKey(), count);
            }
        }

        Set<Long> remaining = new HashSet<>(occupied.keySet());
        Map<Long, Integer> result = new HashMap<>();
        ArrayDeque<Long> pending = new ArrayDeque<>();
        while (!remaining.isEmpty()) {
            long first = remaining.iterator().next();
            remaining.remove(first);
            pending.add(first);

            List<Long> region = new ArrayList<>();
            int total = 0;
            while (!pending.isEmpty()) {
                long current = pending.removeFirst();
                region.add(current);
                total += occupied.get(current);

                int x = (int) current;
                int z = (int) (current >>> 32);
                addIfPresent(key(x - 1, z), remaining, pending);
                addIfPresent(key(x + 1, z), remaining, pending);
                addIfPresent(key(x, z - 1), remaining, pending);
                addIfPresent(key(x, z + 1), remaining, pending);
            }

            if (region.size() >= Math.max(1, minimumChunks) && AmethystDensity.qualifies(total, threshold)) {
                for (long member : region) {
                    result.put(member, total);
                }
            }
        }
        return Map.copyOf(result);
    }

    private static void addIfPresent(long candidate, Set<Long> remaining, ArrayDeque<Long> pending) {
        if (remaining.remove(candidate)) {
            pending.addLast(candidate);
        }
    }
}
