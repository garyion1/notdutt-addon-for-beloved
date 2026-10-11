package dev.khanh.feature;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.block.AbstractFurnaceBlock;
import net.minecraft.block.BarrelBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.BrewingStandBlock;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.DispenserBlock;
import net.minecraft.block.EnderChestBlock;
import net.minecraft.block.HopperBlock;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.block.SpawnerBlock;
import net.minecraft.block.TrappedChestBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.enums.ChestType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.registry.Registries;
import net.minecraft.state.property.Properties;
import net.minecraft.world.LightType;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;

public final class WorldScanner {
    public record HoleShaft(BlockPos start, int width, int length, int depth) { }
    public enum StorageType {
        CHEST,
        TRAPPED_CHEST,
        ENDER_CHEST,
        SHULKER,
        BARREL,
        SPAWNER,
        TRIAL_SPAWNER,
        HOPPER,
        FURNACE,
        ENCHANTING_TABLE,
        MOVING_PISTON
    }

    public enum SusReason {
        KELP("SUS KELP"),
        SUGAR_CANE("SUS SUGAR CANE"),
        BAMBOO("SUS BAMBOO"),
        VINES("SUS VINES"),
        CAVE_VINES("SUS CAVE VINES"),
        BEE_NEST("SUS BEE NEST"),
        MIXED_GROWTH("SUS MIXED GROWTH"),
        ROTATED_DEEPSLATE("SUS ROTATED DEEPSLATE"),
        AMETHYST("SUS AMETHYST"),
        EXTRA("SUS BLOCK SIGNAL");

        private final String label;

        SusReason(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    // Storage and spawners use their own high-throughput section scanner. This
    // budget remains only for the fallback configurable block notifier.
    private static final int BLOCKS_PER_TICK = 256;
    private static final int STORAGE_CHUNKS_PER_TICK = 12;
    private static final int STORAGE_BLOCKS_PER_TICK = 16_384;
    private static final long STORAGE_SCAN_BUDGET_NANOS = 3_000_000L;
    private static final int STORAGE_RESCAN_TICKS = 60;
    private static final int STORAGE_RESULT_LIMIT = 6_000;
    private static final int STORAGE_MAX_CHUNK_RADIUS = 32;
    private static final int ULTIMATE_CHUNK_RADIUS = 40;
    private static final int AMETHYST_CHUNKS_PER_TICK = 160;
    private static final int AMETHYST_RESCAN_TICKS = 10;
    private static final int DETAIL_RESCAN_TICKS = 20;
    private static final int SUS_SETTLE_TICKS = 10;
    private static final long AMETHYST_SCAN_BUDGET_NANOS = 4_000_000L;
    private static final long SUS_SCAN_BUDGET_NANOS = 3_000_000L;
    private static final int PRIME_LIGHT_HITS = 5;
    private static final long PRIME_CONFIRM_DELAY_MS = 1_000L;
    private static final long PRIME_LIGHT_WINDOW_MS = 10_000L;
    private final Deque<ChunkPos> susQueue = new ArrayDeque<>();
    private final Deque<ChunkPos> amethystQueue = new ArrayDeque<>();
    private final Deque<ChunkPos> storageQueue = new ArrayDeque<>();
    private final Set<Long> scannedSusChunks = new HashSet<>();
    private final Set<ChunkPos> suspiciousChunks = new HashSet<>();
    private final Map<ChunkPos, SusReason> suspiciousReasons = new java.util.HashMap<>();
    private final Map<ChunkPos, Integer> chunkFirstSeenTick = new HashMap<>();
    private final Map<ChunkPos, Long> suspiciousSeenAt = new java.util.HashMap<>();
    private final Set<BlockPos> suspiciousCells = new HashSet<>();
    private final Map<ChunkPos, Set<BlockPos>> fullAmethystByChunk = new HashMap<>();
    private final Map<ChunkPos, Set<BlockPos>> amethystSignaturesByChunk = new HashMap<>();
    private final Set<ChunkPos> amethystChunks = new HashSet<>();
    private final Map<ChunkPos, Integer> denseClusterChunks = new HashMap<>();
    private final Map<ChunkPos, Integer> primeGrowthChunks = new HashMap<>();
    private final Map<ChunkPos, PrimeLightEvidence> primeLightEvidence = new HashMap<>();
    private final Map<ChunkPos, Integer> primeLightMarkers = new HashMap<>();
    private final Map<ChunkPos, Integer> undergroundLightChunks = new HashMap<>();
    private record PrimeLightEvidence(int hits, long thresholdAt, long lastAt) { }
    private boolean clusterSnapshotDirty = true;
    private boolean suspiciousSnapshotDirty = true;
    private int lastDenseSettings = Integer.MIN_VALUE;
    private int lastSusDetectorSettings = Integer.MIN_VALUE;
    private Set<ChunkPos> suspiciousChunksSnapshot = Set.of();
    private Set<BlockPos> suspiciousCellsSnapshot = Set.of();
    private Set<BlockPos> amethystSignatureCellsSnapshot = Set.of();
    private Set<ChunkPos> amethystChunksSnapshot = Set.of();
    private Set<BlockPos> geodeCentersSnapshot = Set.of();
    private boolean amethystDetailDirty = true;
    private int lastAmethystDetailSettings = Integer.MIN_VALUE;
    private final Map<BlockPos, StorageType> storageBlocks = new HashMap<>();
    private Map<BlockPos, StorageType> storageSnapshot = Map.of();
    private boolean storageSnapshotDirty = true;
    private final Set<BlockPos> interactedStorage = new HashSet<>();
    private final Map<ChunkPos, BlockPos> geodeCentersByChunk = new HashMap<>();
    private final Set<ChunkPos> baseChunks = new HashSet<>();
    private final Set<ChunkPos> tuffChunks = new HashSet<>();
    private final Set<HoleShaft> holeShafts = new HashSet<>();
    private Set<BlockPos> bedrockVoidCellsSnapshot = Set.of();
    private final Set<Long> notifiedBlocks = new HashSet<>();
    private final Set<Long> notifiedSpawners = new HashSet<>();
    private ClientWorld lastWorld;
    private ChunkPos lastSusCenter;
    private int lastSusRadius = -1;
    private int lastSusSweepTick = Integer.MIN_VALUE;
    private ChunkPos lastAmethystCenter;
    private int lastAmethystRadius = -1;
    private int lastAmethystSweepTick = Integer.MIN_VALUE;
    private ChunkPos lastStorageCenter;
    private int lastStorageRadius = -1;
    private int lastStorageSweepTick = Integer.MIN_VALUE;
    private boolean storageWasEnabled;
    private boolean storageScannerWasEnabled;
    private BlockPos notifierCenter;
    private int notifierRadius = -1;
    private int notifierIndex;
    private int tick;

    private static boolean susDetects(String detector) {
        String selected = KhanhFeatures.choice("sus_esp", "detector", "Amethyst Clusters");
        return selected.equals(detector) || selected.equals("All Purzo Signals");
    }

    private static int susSensitivity() {
        return Math.max(1, Math.min(10, KhanhFeatures.number("sus_esp", "sensitivity", 7)));
    }

    private static int susThreshold(int purzoThreshold) {
        return Math.max(1, purzoThreshold + 7 - susSensitivity());
    }

    public void tick(MinecraftClient client) {
        if (client.world == null || client.player == null) {
            if (lastWorld != null) {
                clear();
            }
            return;
        }
        if (client.world != lastWorld) {
            clear();
            lastWorld = client.world;
        }

        tick++;
        VisualEffectService.tick(client);
        int susDetectorSettings = java.util.Objects.hash(
            KhanhFeatures.enabled("sus_esp"),
            KhanhFeatures.number("sus_esp", "simulation_distance", 8),
            KhanhFeatures.number("sus_esp", "sensitivity", 7),
            KhanhFeatures.choice("sus_esp", "detector", "Amethyst Clusters"));
        if (susDetectorSettings != lastSusDetectorSettings) {
            suspiciousChunks.clear();
            suspiciousReasons.clear();
            suspiciousSeenAt.clear();
            suspiciousSnapshotDirty = true;
            lastSusCenter = null;
            lastSusDetectorSettings = susDetectorSettings;
        }
        boolean storageEnabled = KhanhFeatures.enabled("storage_esp");
        boolean storageScannerEnabled = storageEnabled || KhanhFeatures.enabled("spawner_chunk");
        if (storageEnabled && !storageWasEnabled) {
            interactedStorage.clear();
            lastStorageCenter = null;
        }
        if (storageScannerEnabled && !storageScannerWasEnabled) lastStorageCenter = null;
        storageWasEnabled = storageEnabled;
        storageScannerWasEnabled = storageScannerEnabled;
        boolean priorityScanner = KhanhFeatures.enabled("sus_esp") || amethystScan()
            || KhanhFeatures.enabled("prime_chunk_finder") || KhanhFeatures.enabled("light_finder");
        int detailSettings = java.util.Objects.hash(amethystScan(),
            KhanhFeatures.bool("amethyst_esp", "signature_blocks", true),
            KhanhFeatures.bool("amethyst_esp", "geode_centres", true),
            KhanhFeatures.number("amethyst_esp", "minimum_hits", 3));
        if (detailSettings != lastAmethystDetailSettings) {
            clearAmethystDetails();
            susQueue.clear();
            lastSusCenter = null;
            lastAmethystDetailSettings = detailSettings;
        }
        boolean exactAmethystScanner = KhanhFeatures.enabled("sus_esp") && susDetects("Amethyst Clusters")
            || amethystScan()
            || KhanhFeatures.enabled("tuff_chunk_finder");
        if (exactAmethystScanner) {
            tickFastAmethystClusters(client.world, new ChunkPos(client.player.getBlockPos()), activeAmethystRadius());
        } else {
            amethystQueue.clear();
            lastAmethystCenter = null;
            lastAmethystRadius = -1;
            if (!fullAmethystByChunk.isEmpty()) {
                fullAmethystByChunk.clear();
                suspiciousCells.clear();
                clusterSnapshotDirty = true;
            }
        }
        if ((priorityScanner
            || KhanhFeatures.enabled("base_esp")
            || KhanhFeatures.enabled("tuff_chunk_finder"))
            && (priorityScanner || tick % 8 == 0)) {
            int radius = activeChunkRadius();
            tickSuspiciousChunks(client.world, new ChunkPos(client.player.getBlockPos()), radius,
                priorityScanner ? suspiciousScanBudget() : 1);
        }
        if (KhanhFeatures.enabled("hole_esp") && tick % 20 == 0) {
            scanHoles(client.world, client.player.getBlockPos());
        } else if (!KhanhFeatures.enabled("hole_esp") && !holeShafts.isEmpty()) {
            holeShafts.clear();
        }
        if (storageScannerEnabled) {
            tickStorageChunks(client);
            if (tick % 10 == 0) refreshLoadedStorageBlockEntities(client.world, client.player.getBlockPos());
        }
        if (KhanhFeatures.enabled("spawner_chunk")) {
            tickSpawnerNotifier(client);
        }
        if (KhanhFeatures.enabled("block_notifier")) {
            tickBlockNotifier(client);
        }
        if (KhanhFeatures.enabled("bedrock_void_esp") && tick % 12 == 0) {
            scanBedrockVoids(client.world, client.player.getBlockPos());
        } else if (!KhanhFeatures.enabled("bedrock_void_esp") && !bedrockVoidCellsSnapshot.isEmpty()) {
            bedrockVoidCellsSnapshot = Set.of();
        }
        if (!KhanhFeatures.enabled("prime_chunk_finder")) {
            primeGrowthChunks.clear();
            primeLightEvidence.clear();
            primeLightMarkers.clear();
        } else if (tick % 20 == 0) {
            long now = System.currentTimeMillis();
            for (Map.Entry<ChunkPos, PrimeLightEvidence> entry : primeLightEvidence.entrySet()) {
                if (primeLightConfirmed(entry.getKey(), now)) {
                    primeLightMarkers.putIfAbsent(entry.getKey(), entry.getValue().hits());
                }
            }
            primeLightEvidence.entrySet().removeIf(entry -> now - entry.getValue().lastAt() > PRIME_LIGHT_WINDOW_MS);
        }
        if (!KhanhFeatures.enabled("light_finder")) undergroundLightChunks.clear();
        refreshRenderSnapshots();
    }

    public Set<ChunkPos> suspiciousChunks() {
        return suspiciousChunksSnapshot;
    }

    public Set<BlockPos> suspiciousCells() {
        return suspiciousCellsSnapshot;
    }

    public Set<BlockPos> amethystSignatureCells() { return amethystSignatureCellsSnapshot; }
    public Set<ChunkPos> amethystChunks() { return amethystChunksSnapshot; }

    /** Cluster Counter: full amethyst clusters seen in loaded chunks. */
    public int amethystClusterCount() {
        int count = 0;
        for (Set<BlockPos> cells : fullAmethystByChunk.values()) count += cells.size();
        return count;
    }

    public SusReason suspiciousReason(ChunkPos pos) {
        return suspiciousReasons.getOrDefault(pos, SusReason.EXTRA);
    }

    public int denseClusterCount(ChunkPos pos) { return denseClusterChunks.getOrDefault(pos, 0); }
    public Map<ChunkPos, Integer> primeGrowthChunks() { return Map.copyOf(primeGrowthChunks); }
    public Map<ChunkPos, Integer> undergroundLightChunks() { return Map.copyOf(undergroundLightChunks); }

    public Map<ChunkPos, Integer> primeLightChunks() {
        if (!KhanhFeatures.enabled("prime_chunk_finder")) return Map.of();
        Map<ChunkPos, Integer> result = new HashMap<>(primeLightMarkers);
        result.keySet().removeAll(primeGrowthChunks.keySet());
        return result;
    }

    /** Repeated light updates are displayed separately from confirmed cultivation. */
    public void onLightUpdate(ClientWorld world, ChunkPos pos) {
        if (world == null || world != lastWorld || pos == null || !KhanhFeatures.enabled("prime_chunk_finder")) return;
        var player = MinecraftClient.getInstance().player;
        if (player == null || pos.getChebyshevDistance(new ChunkPos(player.getBlockPos()))
            > KhanhFeatures.number("prime_chunk_finder", "radius", 10) + 2) return;
        long now = System.currentTimeMillis();
        PrimeLightEvidence previous = primeLightEvidence.get(pos);
        if (previous == null || now - previous.lastAt() > PRIME_LIGHT_WINDOW_MS) {
            primeLightEvidence.put(pos, new PrimeLightEvidence(1, 0, now));
        } else {
            int hits = Math.min(32, previous.hits() + 1);
            long thresholdAt = hits == PRIME_LIGHT_HITS ? now : previous.thresholdAt();
            primeLightEvidence.put(pos, new PrimeLightEvidence(hits, thresholdAt, now));
        }
    }

    private boolean primeLightConfirmed(ChunkPos pos, long now) {
        PrimeLightEvidence evidence = primeLightEvidence.get(pos);
        return evidence != null && evidence.hits() >= PRIME_LIGHT_HITS
            && evidence.thresholdAt() > 0
            && now - evidence.thresholdAt() >= PRIME_CONFIRM_DELAY_MS
            && now - evidence.lastAt() <= PRIME_LIGHT_WINDOW_MS;
    }

    public Set<BlockPos> storageBlocks() {
        return storageSnapshot.keySet();
    }

    public Map<BlockPos, StorageType> storageEntries() {
        return storageSnapshot;
    }

    /** Keep local block changes responsive without repeating a full scan every frame. */
    public void onBlockUpdate(ClientWorld world, BlockPos pos, BlockState state) {
        if (world == null || world != lastWorld || pos == null || state == null) return;
        if (!KhanhFeatures.enabled("storage_esp") && !KhanhFeatures.enabled("spawner_chunk")) return;
        StorageType type = storageType(state);
        if (type != null && (!KhanhFeatures.enabled("storage_esp") && !isSpawner(type))) type = null;
        var player = MinecraftClient.getInstance().player;
        if (type != null && (player == null || pos.getSquaredDistance(player.getBlockPos())
            > (double) maximumStorageRange() * maximumStorageRange())) {
            type = null;
        }
        StorageType previous = type == null ? storageBlocks.remove(pos) : storageBlocks.put(pos.toImmutable(), type);
        if (previous != type) storageSnapshotDirty = true;
        if (type == null) interactedStorage.remove(pos);
    }

    public StorageType storageType(BlockPos pos) {
        return storageBlocks.get(pos);
    }

    public boolean storageInteracted(BlockPos pos) {
        return interactedStorage.contains(pos);
    }

    public void trackStorageInteraction(ClientWorld world, BlockPos pos) {
        if (world == null || pos == null || storageType(world.getBlockState(pos)) == null) {
            return;
        }
        BlockPos immutable = pos.toImmutable();
        interactedStorage.add(immutable);
        BlockState state = world.getBlockState(immutable);
        if (state.getBlock() instanceof ChestBlock && state.get(ChestBlock.CHEST_TYPE) != ChestType.SINGLE) {
            Direction facing = state.get(ChestBlock.FACING);
            ChestType type = state.get(ChestBlock.CHEST_TYPE);
            interactedStorage.add(immutable.offset(type == ChestType.LEFT
                ? facing.rotateYClockwise() : facing.rotateYCounterclockwise()));
        }
    }

    public Set<BlockPos> geodeCenters() { return geodeCentersSnapshot; }
    public Set<ChunkPos> baseChunks() { return Set.copyOf(baseChunks); }
    public Set<ChunkPos> tuffChunks() { return Set.copyOf(tuffChunks); }

    public Set<HoleShaft> holeShafts() {
        return Set.copyOf(holeShafts);
    }

    public Set<BlockPos> bedrockVoidCells() {
        return bedrockVoidCellsSnapshot;
    }

    public Set<BlockPos> notifiedBlocks() {
        return notifiedBlocks.stream().map(BlockPos::fromLong).collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    public Set<BlockPos> notifiedSpawners() {
        return notifiedSpawners.stream().map(BlockPos::fromLong).collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    public void clear() {
        susQueue.clear();
        amethystQueue.clear();
        storageQueue.clear();
        scannedSusChunks.clear();
        suspiciousChunks.clear();
        suspiciousReasons.clear();
        chunkFirstSeenTick.clear();
        suspiciousSeenAt.clear();
        suspiciousCells.clear();
        fullAmethystByChunk.clear();
        amethystSignaturesByChunk.clear();
        amethystChunks.clear();
        denseClusterChunks.clear();
        primeGrowthChunks.clear();
        primeLightEvidence.clear();
        primeLightMarkers.clear();
        undergroundLightChunks.clear();
        clusterSnapshotDirty = true;
        suspiciousSnapshotDirty = true;
        lastDenseSettings = Integer.MIN_VALUE;
        lastSusDetectorSettings = Integer.MIN_VALUE;
        suspiciousChunksSnapshot = Set.of();
        suspiciousCellsSnapshot = Set.of();
        amethystSignatureCellsSnapshot = Set.of();
        amethystChunksSnapshot = Set.of();
        geodeCentersSnapshot = Set.of();
        amethystDetailDirty = true;
        lastAmethystDetailSettings = Integer.MIN_VALUE;
        storageBlocks.clear();
        storageSnapshot = Map.of();
        storageSnapshotDirty = true;
        interactedStorage.clear();
        geodeCentersByChunk.clear();
        baseChunks.clear();
        tuffChunks.clear();
        holeShafts.clear();
        bedrockVoidCellsSnapshot = Set.of();
        notifiedBlocks.clear();
        notifiedSpawners.clear();
        lastWorld = null;
        lastSusCenter = null;
        lastSusRadius = -1;
        lastSusSweepTick = Integer.MIN_VALUE;
        lastAmethystCenter = null;
        lastAmethystRadius = -1;
        lastAmethystSweepTick = Integer.MIN_VALUE;
        lastStorageCenter = null;
        lastStorageRadius = -1;
        lastStorageSweepTick = Integer.MIN_VALUE;
        storageWasEnabled = false;
        storageScannerWasEnabled = false;
        notifierCenter = null;
        notifierRadius = -1;
        notifierIndex = 0;
        tick = 0;
    }

    private void scanBedrockVoids(ClientWorld world, BlockPos center) {
        int range = KhanhFeatures.number("bedrock_void_esp", "range", 48);
        int bottomY = world.getBottomY();
        int chunkRadius = Math.max(1, (range + 15) / 16);
        int centerChunkX = center.getX() >> 4;
        int centerChunkZ = center.getZ() >> 4;
        long rangeSquared = (long) range * range;
        Set<BlockPos> found = new HashSet<>();
        for (int chunkX = centerChunkX - chunkRadius; chunkX <= centerChunkX + chunkRadius; chunkX++) {
            for (int chunkZ = centerChunkZ - chunkRadius; chunkZ <= centerChunkZ + chunkRadius; chunkZ++) {
                WorldChunk chunk = world.getChunkManager().getChunk(chunkX, chunkZ, ChunkStatus.FULL, false);
                if (chunk == null) continue;
                int startX = chunkX << 4;
                int startZ = chunkZ << 4;
                for (int localX = 0; localX < 16; localX++) {
                    for (int localZ = 0; localZ < 16; localZ++) {
                        int x = startX + localX;
                        int z = startZ + localZ;
                        long dx = x - center.getX();
                        long dz = z - center.getZ();
                        if (dx * dx + dz * dz > rangeSquared) continue;
                        BlockPos pos = new BlockPos(x, bottomY, z);
                        BlockState state = chunk.getBlockState(pos);
                        if (state.isAir() || state.getCollisionShape(world, pos).isEmpty()) {
                            found.add(pos);
                        }
                    }
                }
            }
        }
        bedrockVoidCellsSnapshot = Set.copyOf(found);
    }

    private void tickSuspiciousChunks(ClientWorld world, ChunkPos center, int radius, int budget) {
        if (lastSusCenter == null || center.getChebyshevDistance(lastSusCenter) > 1
            || lastSusRadius != radius
            || (susQueue.isEmpty() && tick - lastSusSweepTick >= DETAIL_RESCAN_TICKS)) {
            refillSusQueue(center, radius);
        }

        long deadline = System.nanoTime() + SUS_SCAN_BUDGET_NANOS;
        boolean sweepWasActive = !susQueue.isEmpty();
        for (int checked = 0; checked < budget && System.nanoTime() < deadline; checked++) {
            ChunkPos next = susQueue.pollFirst();
            if (next == null) {
                break;
            }
            WorldChunk chunk = world.getChunkManager().getChunk(next.x, next.z, ChunkStatus.FULL, false);
            if (chunk == null) {
                chunkFirstSeenTick.remove(next);
                if (suspiciousChunks.remove(next)) suspiciousSnapshotDirty = true;
                suspiciousReasons.remove(next);
                suspiciousSeenAt.remove(next);
                undergroundLightChunks.remove(next);
                replaceFullAmethystClusters(next, Set.of());
                clearAmethystDetails(next);
                continue;
            }
            scannedSusChunks.add(next.toLong());
            int firstSeen = chunkFirstSeenTick.computeIfAbsent(next, ignored -> tick);
            scanSuspiciousChunk(world, chunk, tick - firstSeen >= SUS_SETTLE_TICKS);
        }
        if (sweepWasActive && susQueue.isEmpty()) lastSusSweepTick = tick;
        pruneSus(center, radius);
    }

    private void refillSusQueue(ChunkPos center, int radius) {
        lastSusCenter = center;
        lastSusRadius = radius;
        susQueue.clear();
        List<ChunkPos> ordered = new ArrayList<>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                ChunkPos pos = new ChunkPos(center.x + dx, center.z + dz);
                ordered.add(pos);
            }
        }
        ordered.sort(Comparator.comparingInt(pos -> pos.getSquaredDistance(center)));
        susQueue.addAll(ordered);
    }

    private void tickFastAmethystClusters(ClientWorld world, ChunkPos center, int radius) {
        boolean moved = lastAmethystCenter == null || !lastAmethystCenter.equals(center);
        boolean radiusChanged = lastAmethystRadius != radius;
        boolean rescanDue = amethystQueue.isEmpty() && tick - lastAmethystSweepTick >= AMETHYST_RESCAN_TICKS;
        if (moved || radiusChanged || rescanDue) {
            refillAmethystQueue(center, radius);
        }
        long deadline = System.nanoTime() + AMETHYST_SCAN_BUDGET_NANOS;
        boolean sweepWasActive = !amethystQueue.isEmpty();
        for (int checked = 0; checked < AMETHYST_CHUNKS_PER_TICK && System.nanoTime() < deadline; checked++) {
            ChunkPos next = amethystQueue.pollFirst();
            if (next == null) {
                break;
            }
            WorldChunk chunk = world.getChunkManager().getChunk(next.x, next.z, ChunkStatus.FULL, false);
            if (chunk == null) {
                replaceFullAmethystClusters(next, Set.of());
                clearAmethystDetails(next);
                continue;
            }
            Set<BlockPos> clusters = new HashSet<>();
            collectFullAmethystClusters(chunk, clusters);
            replaceFullAmethystClusters(next, clusters);
        }
        if (sweepWasActive && amethystQueue.isEmpty()) {
            lastAmethystSweepTick = tick;
        }
    }

    private void refillAmethystQueue(ChunkPos center, int radius) {
        lastAmethystCenter = center;
        lastAmethystRadius = radius;
        amethystQueue.clear();
        List<ChunkPos> ordered = new ArrayList<>((radius * 2 + 1) * (radius * 2 + 1));
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                ordered.add(new ChunkPos(center.x + dx, center.z + dz));
            }
        }
        ordered.sort(Comparator.comparingInt(pos -> pos.getSquaredDistance(center)));
        amethystQueue.addAll(ordered);
    }

    private void replaceFullAmethystClusters(ChunkPos chunk, Set<BlockPos> clusters) {
        Set<BlockPos> current = fullAmethystByChunk.get(chunk);
        if (current == null ? clusters.isEmpty() : current.equals(clusters)) return;
        Set<BlockPos> previous = fullAmethystByChunk.remove(chunk);
        if (previous != null) {
            suspiciousCells.removeAll(previous);
        }
        if (!clusters.isEmpty()) {
            Set<BlockPos> immutable = Set.copyOf(clusters);
            fullAmethystByChunk.put(chunk, immutable);
            suspiciousCells.addAll(immutable);
        }
        clusterSnapshotDirty = true;
    }

    private void clearAmethystDetails() {
        if (amethystSignaturesByChunk.isEmpty() && geodeCentersByChunk.isEmpty() && amethystChunks.isEmpty()) return;
        amethystSignaturesByChunk.clear();
        geodeCentersByChunk.clear();
        amethystChunks.clear();
        amethystDetailDirty = true;
    }

    private void clearAmethystDetails(ChunkPos chunk) {
        boolean changed = amethystSignaturesByChunk.remove(chunk) != null;
        changed |= geodeCentersByChunk.remove(chunk) != null;
        changed |= amethystChunks.remove(chunk);
        if (changed) amethystDetailDirty = true;
    }

    private void markSuspicious(ChunkPos chunk, SusReason reason) {
        if (suspiciousChunks.add(chunk)) suspiciousSnapshotDirty = true;
        suspiciousReasons.put(chunk, reason);
        suspiciousSeenAt.put(chunk, System.currentTimeMillis());
    }

    private void scanSuspiciousChunk(ClientWorld world, WorldChunk chunk, boolean susReady) {
        ChunkPos chunkPos = chunk.getPos();
        if (KhanhFeatures.enabled("light_finder") && lastSusCenter != null
            && chunkPos.getChebyshevDistance(lastSusCenter)
                <= KhanhFeatures.number("light_finder", "radius", 8)) {
            int litSamples = countUndergroundLight(world, chunkPos,
                KhanhFeatures.number("light_finder", "min_level", 10));
            if (litSamples >= KhanhFeatures.number("light_finder", "min_samples", 4)) {
                undergroundLightChunks.put(chunkPos, litSamples);
            } else {
                undergroundLightChunks.remove(chunkPos);
            }
        } else {
            undergroundLightChunks.remove(chunkPos);
        }
        int fullClusters = fullAmethystByChunk.getOrDefault(chunkPos, Set.of()).size();
        boolean amethystEnabled = amethystScan();
        boolean susUsesAmethyst = KhanhFeatures.enabled("sus_esp") && susDetects("Amethyst Clusters");
        boolean needsGeode = amethystScan()
            && KhanhFeatures.bool("amethyst_esp", "geode_centres", true);
        boolean wantsSignature = susUsesAmethyst || amethystEnabled
            && KhanhFeatures.bool("amethyst_esp", "signature_blocks", true);
        BlockPos geodeCenter = needsGeode || wantsSignature ? findGeodeCenter(chunk) : null;
        if (needsGeode && geodeCenter != null) {
            if (!geodeCenter.equals(geodeCentersByChunk.put(chunkPos, geodeCenter))) amethystDetailDirty = true;
        } else if (geodeCentersByChunk.remove(chunkPos) != null) {
            amethystDetailDirty = true;
        }
        Set<BlockPos> signatureCells = new HashSet<>();
        if (wantsSignature) collectAmethystSignatureCells(world, chunk, geodeCenter, signatureCells);
        Set<BlockPos> previousSignatures = amethystSignaturesByChunk.get(chunkPos);
        if (signatureCells.isEmpty()) {
            if (amethystSignaturesByChunk.remove(chunkPos) != null) amethystDetailDirty = true;
        } else if (previousSignatures == null || !previousSignatures.equals(signatureCells)) {
            amethystSignaturesByChunk.put(chunkPos, Set.copyOf(signatureCells));
            amethystDetailDirty = true;
        }
        int amethystMinimum = KhanhFeatures.number("amethyst_esp", "minimum_hits", 3);
        boolean markedBefore = amethystChunks.contains(chunkPos);
        setFlag(amethystChunks, chunkPos, amethystEnabled && signatureCells.size() >= amethystMinimum);
        if (markedBefore != amethystChunks.contains(chunkPos)) amethystDetailDirty = true;

        if (KhanhFeatures.enabled("sus_esp") && susReady) {
            SusReason reason = null;
            int amethystThreshold = susThreshold(3);
            if (susDetects("Amethyst Clusters")
                && (signatureCells.size() >= amethystThreshold || fullClusters >= amethystThreshold)) {
                reason = SusReason.AMETHYST;
            } else {
                reason = scanPurzoSignals(world, chunk);
            }
            if (reason != null) markSuspicious(chunkPos, reason);
            else {
                if (suspiciousChunks.remove(chunkPos)) suspiciousSnapshotDirty = true;
                suspiciousReasons.remove(chunkPos);
                suspiciousSeenAt.remove(chunkPos);
            }
        } else if (!KhanhFeatures.enabled("sus_esp")) {
            if (suspiciousChunks.remove(chunkPos)) suspiciousSnapshotDirty = true;
            suspiciousReasons.remove(chunkPos);
            suspiciousSeenAt.remove(chunkPos);
        }

        setFlag(tuffChunks, chunkPos, KhanhFeatures.enabled("tuff_chunk_finder")
            && fullClusters >= KhanhFeatures.number("tuff_chunk_finder", "clusters", 4));
        if (KhanhFeatures.enabled("base_esp")) {
            String baseDetection = KhanhFeatures.choice("base_esp", "detection", "Both");
            boolean storageBase = !baseDetection.equals("Inhabited") && storageCount(world, chunkPos)
                >= KhanhFeatures.number("base_esp", "min_blocks", 8);
            boolean timeBase = !baseDetection.equals("Storage")
                && chunk.getInhabitedTime() >= KhanhFeatures.number("base_esp", "min_hours", 12) * 20L * 60L * 60L;
            boolean nearbyRequirement = !KhanhFeatures.bool("base_esp", "require_player", false)
                || hasNearbyPlayer(world, chunkPos, KhanhFeatures.number("base_esp", "player_radius", 6));
            setFlag(baseChunks, chunkPos, nearbyRequirement && (storageBase || timeBase));
        } else {
            baseChunks.remove(chunkPos);
        }
        if (KhanhFeatures.enabled("prime_chunk_finder")) {
            int growth = cultivatedGrowthScore(chunk);
            if (growth > 0) primeGrowthChunks.put(chunkPos, growth);
            else primeGrowthChunks.remove(chunkPos);
        }
    }

    private int cultivatedGrowthScore(WorldChunk chunk) {
        int crops = 0;
        int mature = 0;
        int farmland = 0;
        for (ChunkSection section : chunk.getSectionArray()) {
            if (section.isEmpty() || !section.hasAny(WorldScanner::isFarmSignal)) continue;
            for (int y = 0; y < 16; y++) for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
                BlockState state = section.getBlockState(x, y, z);
                if (state.isOf(Blocks.FARMLAND)) {
                    farmland++;
                } else if (isPlantedCrop(state)) {
                    crops++;
                    if ((state.contains(Properties.AGE_7) && state.get(Properties.AGE_7) >= 7)
                        || (state.contains(Properties.AGE_3) && state.get(Properties.AGE_3) >= 3)) mature++;
                }
            }
        }
        int minCrops = KhanhFeatures.number("prime_chunk_finder", "min_crops", 40);
        int minMature = KhanhFeatures.number("prime_chunk_finder", "min_mature", 24);
        int minFarmland = KhanhFeatures.number("prime_chunk_finder", "min_farmland", 24);
        boolean strongGrowth = crops >= minCrops && mature >= minMature && farmland >= minFarmland;
        boolean activityAssisted = primeLightConfirmed(chunk.getPos(), System.currentTimeMillis())
            && crops >= Math.max(12, (minCrops * 2 + 2) / 3)
            && mature >= Math.max(6, (minMature * 2 + 2) / 3)
            && farmland >= Math.max(6, (minFarmland * 2 + 2) / 3);
        return strongGrowth || activityAssisted ? mature : 0;
    }

    private static boolean isFarmSignal(BlockState state) {
        return state.isOf(Blocks.FARMLAND) || isPlantedCrop(state);
    }

    private static boolean isPlantedCrop(BlockState state) {
        return state.isOf(Blocks.WHEAT) || state.isOf(Blocks.CARROTS)
            || state.isOf(Blocks.POTATOES) || state.isOf(Blocks.BEETROOTS);
    }

    private static void collectAmethystSignatureCells(ClientWorld world, WorldChunk chunk, BlockPos center,
                                                      Set<BlockPos> result) {
        if (center == null) {
            return;
        }
        ChunkPos chunkPos = chunk.getPos();
        int minimumX = Math.max(chunkPos.getStartX(), center.getX() - 8);
        int maximumX = Math.min(chunkPos.getEndX(), center.getX() + 8);
        int minimumY = Math.max(Math.max(world.getBottomY(), -58), center.getY() - 8);
        int maximumY = Math.min(Math.min(world.getTopYInclusive(), 30), center.getY() + 8);
        int minimumZ = Math.max(chunkPos.getStartZ(), center.getZ() - 8);
        int maximumZ = Math.min(chunkPos.getEndZ(), center.getZ() + 8);
        BlockPos.Mutable candidate = new BlockPos.Mutable();
        BlockPos.Mutable neighbour = new BlockPos.Mutable();

        for (int y = minimumY; y <= maximumY; y++) {
            for (int x = minimumX; x <= maximumX; x++) {
                for (int z = minimumZ; z <= maximumZ; z++) {
                    candidate.set(x, y, z);
                    BlockState state = world.getBlockState(candidate);
                    if (!state.isAir() && !state.isOf(Blocks.AMETHYST_CLUSTER)) {
                        continue;
                    }
                    if (world.getLightLevel(LightType.BLOCK, candidate) != 0) {
                        continue;
                    }

                    int maximumNeighbourLight = 0;
                    boolean lightFourBoundary = false;
                    for (Direction direction : Direction.values()) {
                        neighbour.set(candidate).move(direction);
                        int neighbourLight = world.getLightLevel(LightType.BLOCK, neighbour);
                        maximumNeighbourLight = Math.max(maximumNeighbourLight, neighbourLight);
                        BlockState neighbourState = world.getBlockState(neighbour);
                        if (neighbourLight == 4
                            && (neighbourState.isAir() || neighbourState.isOf(Blocks.AMETHYST_CLUSTER))) {
                            lightFourBoundary = true;
                        }
                    }
                    if (maximumNeighbourLight == 4 && lightFourBoundary) {
                        result.add(candidate.toImmutable());
                    }
                }
            }
        }
    }

    private static boolean isAmethystFamily(BlockState state) {
        return state.isOf(Blocks.AMETHYST_BLOCK) || state.isOf(Blocks.BUDDING_AMETHYST)
            || state.isOf(Blocks.AMETHYST_CLUSTER) || state.isOf(Blocks.SMALL_AMETHYST_BUD)
            || state.isOf(Blocks.MEDIUM_AMETHYST_BUD) || state.isOf(Blocks.LARGE_AMETHYST_BUD);
    }

    private static SusReason scanPurzoSignals(ClientWorld world, WorldChunk chunk) {
        boolean detectKelp = susDetects("Kelp");
        boolean detectSugar = susDetects("Sugar Cane");
        boolean detectBamboo = susDetects("Bamboo");
        boolean detectVines = susDetects("Vines");
        boolean detectCaveVines = susDetects("Cave Vines");
        boolean detectBeeNests = susDetects("Bee Nests");
        boolean detectRotated = susDetects("Rotated Deepslate");
        if (!detectKelp && !detectSugar && !detectBamboo && !detectVines
            && !detectCaveVines && !detectBeeNests && !detectRotated) return null;

        int kelp = 0;
        int sugar = 0;
        int bamboo = 0;
        int vines = 0;
        int caveVines = 0;
        int beeNests = 0;
        int rotatedDeepslate = 0;
        ChunkSection[] sections = chunk.getSectionArray();
        int baseSection = chunk.getBottomSectionCoord();
        ChunkPos chunkPos = chunk.getPos();
        BlockPos.Mutable cursor = new BlockPos.Mutable();
        for (int sectionIndex = 0; sectionIndex < sections.length; sectionIndex++) {
            ChunkSection section = sections[sectionIndex];
            int startY = (baseSection + sectionIndex) << 4;
            if (section.isEmpty() || !section.hasAny(state ->
                detectSugar && state.isOf(Blocks.SUGAR_CANE)
                    || detectKelp && state.isOf(Blocks.KELP)
                    || detectCaveVines && state.isOf(Blocks.CAVE_VINES)
                    || detectBamboo && state.isOf(Blocks.BAMBOO)
                    || detectVines && state.isOf(Blocks.VINE)
                    || detectBeeNests && state.isOf(Blocks.BEE_NEST)
                    || detectRotated && startY <= 60 && startY + 15 >= 0
                        && state.isOf(Blocks.DEEPSLATE))) continue;

            for (int localY = 0; localY < 16; localY++) {
                int worldY = startY + localY;
                for (int localZ = 0; localZ < 16; localZ++) {
                    for (int localX = 0; localX < 16; localX++) {
                        BlockState state = section.getBlockState(localX, localY, localZ);
                        Block block = state.getBlock();
                        if (detectSugar && block == Blocks.SUGAR_CANE && worldY <= 0) sugar++;
                        if (detectKelp && block == Blocks.KELP && state.contains(Properties.AGE_25)
                            && state.get(Properties.AGE_25) >= 25) kelp++;
                        if (detectCaveVines && block == Blocks.CAVE_VINES && state.contains(Properties.AGE_25)
                            && state.get(Properties.AGE_25) >= 25) caveVines++;
                        if (detectBamboo && block == Blocks.BAMBOO && state.contains(Properties.STAGE)
                            && state.get(Properties.STAGE) == 1) {
                            cursor.set(chunkPos.getStartX() + localX, worldY + 1, chunkPos.getStartZ() + localZ);
                            if (!chunk.getBlockState(cursor).isOf(Blocks.BAMBOO)) bamboo++;
                        }
                        if (detectVines && block == Blocks.VINE) {
                            cursor.set(chunkPos.getStartX() + localX, worldY - 1, chunkPos.getStartZ() + localZ);
                            if (!chunk.getBlockState(cursor).isOf(Blocks.VINE)
                                && !chunk.getBlockState(cursor).isAir()) vines++;
                        }
                        if (detectBeeNests && block == Blocks.BEE_NEST && state.contains(Properties.HONEY_LEVEL)
                            && state.get(Properties.HONEY_LEVEL) == 5) beeNests++;
                        if (detectRotated && block == Blocks.DEEPSLATE && worldY >= 0 && worldY <= 60
                            && state.contains(Properties.AXIS)
                            && state.get(Properties.AXIS) != Direction.Axis.Y) {
                            cursor.set(chunkPos.getStartX() + localX, worldY, chunkPos.getStartZ() + localZ);
                            boolean buried = true;
                            for (Direction direction : Direction.values()) {
                                if (world.getBlockState(cursor.offset(direction)).isAir()) {
                                    buried = false;
                                    break;
                                }
                            }
                            if (buried) rotatedDeepslate++;
                        }
                    }
                }
            }
        }

        if (rotatedDeepslate >= susThreshold(1)) return SusReason.ROTATED_DEEPSLATE;
        if (beeNests >= susThreshold(1)) return SusReason.BEE_NEST;
        if (caveVines >= susThreshold(3)) return SusReason.CAVE_VINES;
        if (kelp >= susThreshold(3)) return SusReason.KELP;
        if (sugar >= susThreshold(4)) return SusReason.SUGAR_CANE;
        if (bamboo >= susThreshold(3)) return SusReason.BAMBOO;
        if (vines >= susThreshold(3)) return SusReason.VINES;
        int growthKinds = (kelp > 0 ? 1 : 0) + (caveVines > 0 ? 1 : 0)
            + (bamboo > 0 ? 1 : 0) + (vines > 0 ? 1 : 0);
        return KhanhFeatures.choice("sus_esp", "detector", "Amethyst Clusters").equals("All Purzo Signals")
            && growthKinds >= 2 && kelp + caveVines + bamboo + vines >= susThreshold(4)
            ? SusReason.MIXED_GROWTH : null;
    }

    private static int countUndergroundLight(ClientWorld world, ChunkPos chunk, int minimumLevel) {
        int highestY = Math.min(-1, world.getTopYInclusive());
        int lowestY = Math.max(-64, world.getBottomY());
        if (lowestY > highestY) return 0;
        BlockPos.Mutable sample = new BlockPos.Mutable();
        int found = 0;
        // A fixed 4-block grid samples loaded block light on the client thread.
        // The bounded scan avoids the source module's per-voxel render cost.
        for (int y = lowestY + 1; y <= highestY; y += 4) {
            for (int x = 1; x < 16; x += 4) {
                for (int z = 1; z < 16; z += 4) {
                    sample.set(chunk.getStartX() + x, y, chunk.getStartZ() + z);
                    if (world.getLightLevel(LightType.BLOCK, sample) >= minimumLevel && ++found >= 64) {
                        return found;
                    }
                }
            }
        }
        return found;
    }

    private static void collectFullAmethystClusters(WorldChunk chunk, Set<BlockPos> result) {
        ChunkSection[] sections = chunk.getSectionArray();
        int baseSection = chunk.getBottomSectionCoord();
        int startX = chunk.getPos().getStartX();
        int startZ = chunk.getPos().getStartZ();
        for (int sectionIndex = 0; sectionIndex < sections.length; sectionIndex++) {
            ChunkSection section = sections[sectionIndex];
            int startY = (baseSection + sectionIndex) << 4;
            // Mature clusters can also be grown above geode height. Palette-skip
            // unrelated sections, but scan every loaded height and dimension.
            if (section.isEmpty() || !section.hasAny(state -> state.isOf(Blocks.AMETHYST_CLUSTER))) {
                continue;
            }
            for (int localY = 0; localY < 16; localY++) {
                for (int localZ = 0; localZ < 16; localZ++) {
                    for (int localX = 0; localX < 16; localX++) {
                        if (section.getBlockState(localX, localY, localZ).isOf(Blocks.AMETHYST_CLUSTER)) {
                            result.add(new BlockPos(startX + localX, startY + localY, startZ + localZ));
                        }
                    }
                }
            }
        }
    }

    private void tickStorageChunks(MinecraftClient client) {
        ChunkPos center = new ChunkPos(client.player.getBlockPos());
        int radius = activeStorageChunkRadius(client);
        if (lastStorageCenter == null || center.getChebyshevDistance(lastStorageCenter) > 0
            || lastStorageRadius != radius || (storageQueue.isEmpty() && tick - lastStorageSweepTick >= STORAGE_RESCAN_TICKS)) {
            refillStorageQueue(center, radius);
        }

        int scannedBlocks = 0;
        long deadline = System.nanoTime() + STORAGE_SCAN_BUDGET_NANOS;
        for (int checked = 0; checked < STORAGE_CHUNKS_PER_TICK && scannedBlocks < STORAGE_BLOCKS_PER_TICK; checked++) {
            if (checked > 0 && System.nanoTime() >= deadline) break;
            ChunkPos next = storageQueue.pollFirst();
            if (next == null) {
                break;
            }
            WorldChunk chunk = client.world.getChunkManager().getChunk(next.x, next.z, ChunkStatus.FULL, false);
            if (chunk != null) {
                scannedBlocks += scanStorageChunk(chunk, client.player.getBlockPos());
            }
        }

        int pruneRange = maximumStorageRange() + 32;
        BlockPos playerPos = client.player.getBlockPos();
        if (storageBlocks.keySet().removeIf(pos -> Math.abs(pos.getX() - playerPos.getX()) > pruneRange
            || Math.abs(pos.getZ() - playerPos.getZ()) > pruneRange)) storageSnapshotDirty = true;
    }

    private void refillStorageQueue(ChunkPos center, int radius) {
        lastStorageCenter = center;
        lastStorageRadius = radius;
        lastStorageSweepTick = tick;
        storageQueue.clear();
        List<ChunkPos> ordered = new ArrayList<>((radius * 2 + 1) * (radius * 2 + 1));
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                ordered.add(new ChunkPos(center.x + dx, center.z + dz));
            }
        }
        ordered.sort(Comparator.comparingInt(pos -> pos.getSquaredDistance(center)));
        storageQueue.addAll(ordered);
    }

    private int scanStorageChunk(WorldChunk chunk, BlockPos playerPos) {
        ChunkPos chunkPos = chunk.getPos();
        if (storageBlocks.keySet().removeIf(pos -> (pos.getX() >> 4) == chunkPos.x
            && (pos.getZ() >> 4) == chunkPos.z)) storageSnapshotDirty = true;
        ChunkSection[] sections = chunk.getSectionArray();
        int baseSection = chunk.getBottomSectionCoord();
        int startX = chunkPos.getStartX();
        int startZ = chunkPos.getStartZ();
        int visited = 0;
        int range = maximumStorageRange();
        double rangeSquared = (double) range * range;

        for (int sectionIndex = 0; sectionIndex < sections.length; sectionIndex++) {
            ChunkSection section = sections[sectionIndex];
            if (section.isEmpty() || !section.hasAny(state -> storageType(state) != null)) {
                continue;
            }
            visited += 4096;
            int startY = (baseSection + sectionIndex) << 4;
            for (int localY = 0; localY < 16; localY++) {
                for (int localZ = 0; localZ < 16; localZ++) {
                    for (int localX = 0; localX < 16; localX++) {
                        BlockState state = section.getBlockState(localX, localY, localZ);
                        StorageType type = storageType(state);
                        if (type == null || (!KhanhFeatures.enabled("storage_esp") && !isSpawner(type))) {
                            continue;
                        }
                        BlockPos pos = new BlockPos(startX + localX, startY + localY, startZ + localZ);
                        if (pos.getSquaredDistance(playerPos) <= rangeSquared && storageBlocks.size() < STORAGE_RESULT_LIMIT) {
                            storageBlocks.put(pos, type);
                            storageSnapshotDirty = true;
                        }
                    }
                }
            }
        }
        return visited;
    }

    private void refreshLoadedStorageBlockEntities(ClientWorld world, BlockPos playerPos) {
        double rangeSquared = (double) maximumStorageRange() * maximumStorageRange();
        boolean storageEnabled = KhanhFeatures.enabled("storage_esp");
        for (BlockEntity entity : world.getBlockEntities()) {
            BlockPos pos = entity.getPos();
            if (pos.getSquaredDistance(playerPos) > rangeSquared) continue;
            StorageType type = storageType(world.getBlockState(pos));
            if (type == null || (!storageEnabled && !isSpawner(type))) continue;
            if (storageBlocks.size() >= STORAGE_RESULT_LIMIT && !storageBlocks.containsKey(pos)) continue;
            if (storageBlocks.put(pos.toImmutable(), type) != type) storageSnapshotDirty = true;
        }
    }

    private static StorageType storageType(BlockState state) {
        Block block = state.getBlock();
        if (block instanceof TrappedChestBlock) return StorageType.TRAPPED_CHEST;
        if (block instanceof ChestBlock) return StorageType.CHEST;
        if (block instanceof EnderChestBlock) return StorageType.ENDER_CHEST;
        if (block instanceof ShulkerBoxBlock) return StorageType.SHULKER;
        if (block instanceof BarrelBlock) return StorageType.BARREL;
        if (state.isOf(Blocks.TRIAL_SPAWNER)) return StorageType.TRIAL_SPAWNER;
        if (block instanceof SpawnerBlock) return StorageType.SPAWNER;
        if (block instanceof HopperBlock || block instanceof DispenserBlock) return StorageType.HOPPER;
        if (block instanceof AbstractFurnaceBlock || block instanceof BrewingStandBlock) return StorageType.FURNACE;
        if (state.isOf(Blocks.ENCHANTING_TABLE)) return StorageType.ENCHANTING_TABLE;
        if (state.isOf(Blocks.MOVING_PISTON)) return StorageType.MOVING_PISTON;
        return null;
    }

    private static boolean isSpawner(StorageType type) {
        return type == StorageType.SPAWNER || type == StorageType.TRIAL_SPAWNER;
    }

    /** Ultimate Range: every chunk the client has loaded, with no distance or render cap. */
    private static boolean ultimateStorage() {
        return KhanhFeatures.enabled("storage_esp") && KhanhFeatures.bool("storage_esp", "ultimate_range", false);
    }

    private static int maximumStorageRange() {
        int range = 0;
        if (KhanhFeatures.enabled("storage_esp")) {
            if (ultimateStorage()) {
                range = (ULTIMATE_CHUNK_RADIUS + 1) * 16 * 2;
            } else if (KhanhFeatures.bool("storage_esp", "all_loaded", true)) {
                int viewDistance = MinecraftClient.getInstance().options.getClampedViewDistance();
                range = Math.max(range, Math.min(STORAGE_MAX_CHUNK_RADIUS, viewDistance) * 16 + 32);
            } else {
                range = Math.max(range, KhanhFeatures.number("storage_esp", "range", 128));
            }
        }
        if (KhanhFeatures.enabled("spawner_chunk")) {
            range = Math.max(range, KhanhFeatures.number("spawner_chunk", "range", 128));
        }
        return Math.max(32, range);
    }

    private static int activeStorageChunkRadius(MinecraftClient client) {
        // The client keeps chunks up to view distance + 3 around you (more in Freecam); cover them all.
        if (ultimateStorage()) return Math.min(ULTIMATE_CHUNK_RADIUS, client.options.getViewDistance().getValue() + 3);
        int wanted = (int) Math.ceil(maximumStorageRange() / 16.0) + 1;
        int viewDistance = client.options.getViewDistance().getValue();
        return Math.max(2, Math.min(Math.min(STORAGE_MAX_CHUNK_RADIUS, wanted), viewDistance));
    }

    private static int storageCount(ClientWorld world, ChunkPos chunk) {
        int count = 0;
        for (BlockEntity blockEntity : world.getBlockEntities()) {
            if (new ChunkPos(blockEntity.getPos()).equals(chunk)
                && blockEntity instanceof net.minecraft.inventory.Inventory) count++;
        }
        return count;
    }

    private static boolean hasNearbyPlayer(ClientWorld world, ChunkPos chunk, int radius) {
        MinecraftClient client = MinecraftClient.getInstance();
        for (net.minecraft.client.network.AbstractClientPlayerEntity player : world.getPlayers()) {
            if (player == client.player) continue;
            if (new ChunkPos(player.getBlockPos()).getChebyshevDistance(chunk) <= radius) return true;
        }
        return false;
    }

    private static void setFlag(Set<ChunkPos> set, ChunkPos pos, boolean value) {
        if (value) set.add(pos); else set.remove(pos);
    }

    private static BlockPos findGeodeCenter(WorldChunk chunk) {
        ChunkSection[] sections = chunk.getSectionArray();
        int baseSection = chunk.getBottomSectionCoord();
        int startX = chunk.getPos().getStartX();
        int startZ = chunk.getPos().getStartZ();
        for (int sectionIndex = 0; sectionIndex < sections.length; sectionIndex++) {
            ChunkSection section = sections[sectionIndex];
            int startY = (baseSection + sectionIndex) << 4;
            if (startY > 30 || startY + 15 < -58 || section.isEmpty()
                || !section.hasAny(WorldScanner::isGeodeCore)) {
                continue;
            }
            for (int localY = 0; localY < 16; localY++) {
                int worldY = startY + localY;
                if (worldY < -58 || worldY > 30) {
                    continue;
                }
                for (int localZ = 0; localZ < 16; localZ++) {
                    for (int localX = 0; localX < 16; localX++) {
                        if (isGeodeCore(section.getBlockState(localX, localY, localZ))) {
                            return new BlockPos(startX + localX, worldY, startZ + localZ);
                        }
                    }
                }
            }
        }
        return null;
    }

    private static boolean isGeodeCore(BlockState state) {
        return state.isOf(Blocks.BUDDING_AMETHYST) || state.isOf(Blocks.AMETHYST_BLOCK);
    }

    private void refreshRenderSnapshots() {
        if (storageSnapshotDirty) {
            storageSnapshot = Map.copyOf(storageBlocks);
            storageSnapshotDirty = false;
        }
        int threshold = Math.max(4, susThreshold(3) * 4);
        int localMinimum = 1;
        int minimumChunks = 1;
        boolean amethystDetection = susDetects("Amethyst Clusters");
        int settings = java.util.Objects.hash(KhanhFeatures.enabled("sus_esp"), amethystDetection, threshold,
            localMinimum, minimumChunks);
        if (clusterSnapshotDirty || suspiciousSnapshotDirty || settings != lastDenseSettings) {
            // Adjacent occupied chunks form one region, so a large field
            // produces one stable marker per real occupied member chunk.
            denseClusterChunks.clear();
            if (KhanhFeatures.enabled("sus_esp") && amethystDetection) {
                Map<Long, Integer> countsByChunk = new HashMap<>();
                for (Map.Entry<ChunkPos, Set<BlockPos>> entry : fullAmethystByChunk.entrySet()) {
                    int count = entry.getValue().size();
                    if (count >= localMinimum) {
                        ChunkPos chunk = entry.getKey();
                        countsByChunk.put(DenseClusterRegionIndex.key(chunk.x, chunk.z), count);
                    }
                }
                for (Map.Entry<Long, Integer> entry : DenseClusterRegionIndex.qualifyingChunks(countsByChunk, threshold, minimumChunks).entrySet()) {
                    denseClusterChunks.put(new ChunkPos(entry.getKey()), entry.getValue());
                }
            }
            Set<ChunkPos> allSuspicious = new HashSet<>(suspiciousChunks);
            allSuspicious.addAll(denseClusterChunks.keySet());
            suspiciousChunksSnapshot = Set.copyOf(allSuspicious);
            suspiciousCellsSnapshot = Set.copyOf(suspiciousCells);
            clusterSnapshotDirty = false;
            suspiciousSnapshotDirty = false;
            lastDenseSettings = settings;
        }
        if (amethystDetailDirty) {
            Set<BlockPos> signatures = new HashSet<>();
            for (Set<BlockPos> cells : amethystSignaturesByChunk.values()) signatures.addAll(cells);
            amethystSignatureCellsSnapshot = Set.copyOf(signatures);
            amethystChunksSnapshot = Set.copyOf(amethystChunks);
            geodeCentersSnapshot = Set.copyOf(geodeCentersByChunk.values());
            amethystDetailDirty = false;
        }
    }

    private void pruneSus(ChunkPos center, int radius) {
        scannedSusChunks.removeIf(value -> new ChunkPos(value).getChebyshevDistance(center) > radius + 2);
        long now = System.currentTimeMillis();
        long susCache = 60_000L;
        if (suspiciousChunks.removeIf(value -> value.getChebyshevDistance(center) > radius + 2
            && now - suspiciousSeenAt.getOrDefault(value, 0L) > susCache)) suspiciousSnapshotDirty = true;
        suspiciousSeenAt.keySet().retainAll(suspiciousChunks);
        suspiciousReasons.keySet().retainAll(suspiciousChunks);
        chunkFirstSeenTick.keySet().removeIf(value -> value.getChebyshevDistance(center) > radius + 2);
        if (suspiciousCells.removeIf(value -> new ChunkPos(value).getChebyshevDistance(center) > radius + 2))
            clusterSnapshotDirty = true;
        if (fullAmethystByChunk.keySet().removeIf(value -> value.getChebyshevDistance(center) > radius + 2))
            clusterSnapshotDirty = true;
        if (amethystSignaturesByChunk.keySet().removeIf(value -> value.getChebyshevDistance(center) > radius + 2))
            amethystDetailDirty = true;
        if (amethystChunks.removeIf(value -> value.getChebyshevDistance(center) > radius + 2))
            amethystDetailDirty = true;
        denseClusterChunks.keySet().removeIf(value -> value.getChebyshevDistance(center) > radius + 2);
        primeGrowthChunks.keySet().removeIf(value -> value.getChebyshevDistance(center) > radius + 2);
        primeLightEvidence.keySet().removeIf(value -> value.getChebyshevDistance(center) > radius + 2);
        primeLightMarkers.keySet().removeIf(value -> value.getChebyshevDistance(center) > radius + 2);
        undergroundLightChunks.keySet().removeIf(value -> value.getChebyshevDistance(center)
            > KhanhFeatures.number("light_finder", "radius", 8) + 2);
        if (geodeCentersByChunk.keySet().removeIf(value -> value.getChebyshevDistance(center) > radius + 2))
            amethystDetailDirty = true;
        baseChunks.removeIf(value -> value.getChebyshevDistance(center) > radius + 2);
        tuffChunks.removeIf(value -> value.getChebyshevDistance(center) > radius + 2);
    }

    private static int activeChunkRadius() {
        int radius = 2;
        if (KhanhFeatures.enabled("sus_esp")) radius = Math.max(radius, KhanhFeatures.number("sus_esp", "simulation_distance", 8));
        if (KhanhFeatures.enabled("prime_chunk_finder")) radius = Math.max(radius,
            KhanhFeatures.number("prime_chunk_finder", "radius", 10));
        if (KhanhFeatures.enabled("light_finder")) radius = Math.max(radius,
            KhanhFeatures.number("light_finder", "radius", 8));
        if (amethystScan()) radius = Math.max(radius, KhanhFeatures.number("amethyst_esp", "range", 160) / 16);
        if (KhanhFeatures.enabled("base_esp")) radius = Math.max(radius, KhanhFeatures.number("base_esp", "range", 160) / 16);
        for (String module : List.of("base_esp", "tuff_chunk_finder")) {
            if (KhanhFeatures.enabled(module)) radius = Math.max(radius, KhanhFeatures.number(module, "radius", 5));
        }
        return Math.max(2, Math.min(16, radius));
    }

    private static int activeAmethystRadius() {
        int radius = 2;
        if (KhanhFeatures.enabled("sus_esp")) {
            radius = Math.max(radius, KhanhFeatures.number("sus_esp", "simulation_distance", 8));
        }
        if (amethystScan()) {
            radius = Math.max(radius, KhanhFeatures.number("amethyst_esp", "range", 160) / 16);
        }
        if (KhanhFeatures.enabled("tuff_chunk_finder")) {
            radius = Math.max(radius, KhanhFeatures.number("tuff_chunk_finder", "radius", 5));
        }
        return Math.max(2, Math.min(16, radius));
    }

    private void scanHoles(ClientWorld world, BlockPos center) {
        holeShafts.clear();
        int radius = KhanhFeatures.number("hole_esp", "radius", 12);
        int vertical = KhanhFeatures.number("hole_esp", "vertical_range", 12);
        int bottom = Math.max(world.getBottomY(), center.getY() - vertical);
        int top = Math.min(world.getTopYInclusive(), center.getY() + vertical);
        int minDepth = KhanhFeatures.number("hole_esp", "min_depth", 4);
        Map<Long, Boolean> passable = new HashMap<>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int x = center.getX() + dx;
                int z = center.getZ() + dz;
                if (KhanhFeatures.bool("hole_esp", "one_by_one", true))
                    scanShaft(world, passable, x, z, bottom, top, 1, 1, minDepth);
                if (KhanhFeatures.bool("hole_esp", "three_by_one", true)) {
                    scanShaft(world, passable, x, z, bottom, top, 3, 1, minDepth);
                    scanShaft(world, passable, x, z, bottom, top, 1, 3, minDepth);
                }
            }
        }
    }

    private void scanShaft(ClientWorld world, Map<Long, Boolean> passable, int x, int z,
                           int bottom, int top, int width, int length, int minDepth) {
        int start = Integer.MIN_VALUE;
        for (int y = bottom; y <= top + 1; y++) {
            boolean matches = y <= top && enclosedShaftLayer(world, passable, x, y, z, width, length);
            if (matches && start == Integer.MIN_VALUE) start = y;
            if (!matches && start != Integer.MIN_VALUE) {
                if (y - start >= minDepth)
                    holeShafts.add(new HoleShaft(new BlockPos(x, start, z), width, length, y - start));
                start = Integer.MIN_VALUE;
            }
        }
    }

    private static boolean enclosedShaftLayer(ClientWorld world, Map<Long, Boolean> cache,
                                               int x, int y, int z, int width, int length) {
        for (int dx = 0; dx < width; dx++)
            for (int dz = 0; dz < length; dz++)
                if (!passable(world, cache, x + dx, y, z + dz)) return false;
        for (int dx = 0; dx < width; dx++)
            if (passable(world, cache, x + dx, y, z - 1)
                || passable(world, cache, x + dx, y, z + length)) return false;
        for (int dz = 0; dz < length; dz++)
            if (passable(world, cache, x - 1, y, z + dz)
                || passable(world, cache, x + width, y, z + dz)) return false;
        return true;
    }

    private static boolean passable(ClientWorld world, Map<Long, Boolean> cache, int x, int y, int z) {
        BlockPos pos = new BlockPos(x, y, z);
        return cache.computeIfAbsent(pos.asLong(), ignored ->
            world.getBlockState(pos).getCollisionShape(world, pos).isEmpty());
    }

    private void tickBlockNotifier(MinecraftClient client) {
        BlockPos playerPos = client.player.getBlockPos();
        int radius = KhanhFeatures.number("block_notifier", "radius", 16);
        if (notifierCenter == null || notifierRadius != radius || squaredHorizontalDistance(notifierCenter, playerPos) > 16) {
            notifierCenter = playerPos.toImmutable();
            notifierRadius = radius;
            notifierIndex = 0;
        }

        int diameter = radius * 2 + 1;
        int total = diameter * diameter * diameter;
        BlockPos.Mutable cursor = new BlockPos.Mutable();
        for (int count = 0; count < BLOCKS_PER_TICK; count++) {
            int index = notifierIndex++;
            if (notifierIndex >= total) {
                notifierIndex = 0;
            }
            // Scan outward from the player rather than spending the first half
            // of every pass below them. Nearby spawners now arrive first.
            int dx = centeredOffset(index % diameter);
            int dz = centeredOffset(index / diameter % diameter);
            int dy = centeredOffset(index / (diameter * diameter));
            cursor.set(notifierCenter.getX() + dx, notifierCenter.getY() + dy, notifierCenter.getZ() + dz);
            BlockState state = client.world.getBlockState(cursor);
                    boolean withinNotifier = Math.abs(dx) <= radius && Math.abs(dy) <= radius && Math.abs(dz) <= radius;
            boolean notifierMatch = withinNotifier && isSelectedNotifierBlock(state.getBlock())
                && (!antiXrayFilter() || exposed(client.world, cursor));
            if (notifierMatch && notifiedBlocks.add(cursor.asLong())) {
                double distance = Math.sqrt(client.player.squaredDistanceTo(cursor.getX() + 0.5, cursor.getY() + 0.5, cursor.getZ() + 0.5));
                String name = state.getBlock().getName().getString();
                Text message = Text.literal(String.format(Locale.ROOT, "[Khanh Client] Found %s • %.0fm",
                    name, distance));
                int color = blockNotifierColor() & 0x00FFFFFF;
                // With notification cards on, the card says it instead of the action bar.
                if (!FinderNotifications.wanted("block_notifier")) {
                    client.player.sendMessage(message.copy().styled(style -> style.withColor(color)), true);
                }
                if (KhanhFeatures.enabled("block_notifier") && KhanhFeatures.bool("block_notifier", "sound", true)
                    && !FinderNotifications.wanted("block_notifier")) {
                    KhanhSounds.notify(client);
                }
            }
        }

        if (notifierIndex == 0) {
            notifiedBlocks.removeIf(value -> {
                BlockPos pos = BlockPos.fromLong(value);
                return Math.abs(pos.getX() - playerPos.getX()) > 48 || Math.abs(pos.getZ() - playerPos.getZ()) > 48;
            });
        }
    }

    /** Cluster ESP and Cluster Counter both need the amethyst scan. */
    private static boolean amethystScan() {
        return KhanhFeatures.enabled("amethyst_esp") || KhanhFeatures.enabled("cluster_counter");
    }

    /** Bypasses: anti-xray fills hidden stone with fake ores, but real ores you can mine to always touch something open. */
    private static boolean antiXrayFilter() {
        return KhanhFeatures.enabled("bypasses") && KhanhFeatures.bool("bypasses", "anti_xray", true);
    }

    private static boolean exposed(net.minecraft.world.World world, BlockPos pos) {
        for (net.minecraft.util.math.Direction direction : net.minecraft.util.math.Direction.values()) {
            BlockState neighbour = world.getBlockState(pos.offset(direction));
            if (neighbour.isAir() || !neighbour.getFluidState().isEmpty() || !neighbour.isOpaqueFullCube()) return true;
        }
        return false;
    }

    public static boolean isSelectedNotifierBlock(Block block) {
        if (!KhanhFeatures.enabled("block_notifier")) {
            return false;
        }
        if (block == Blocks.DIAMOND_ORE || block == Blocks.DEEPSLATE_DIAMOND_ORE) {
            return selected("diamond", true);
        }
        if (block == Blocks.EMERALD_ORE || block == Blocks.DEEPSLATE_EMERALD_ORE) {
            return selected("emerald", true);
        }
        if (block == Blocks.ANCIENT_DEBRIS) {
            return selected("ancient_debris", true);
        }
        if (block == Blocks.GOLD_ORE || block == Blocks.DEEPSLATE_GOLD_ORE || block == Blocks.NETHER_GOLD_ORE) {
            return KhanhFeatures.bool("block_notifier", "gold", false);
        }
        if (block == Blocks.SPAWNER) {
            return KhanhFeatures.bool("block_notifier", "mob_spawner", true);
        }
        if (block == Blocks.TRIAL_SPAWNER) {
            return KhanhFeatures.bool("block_notifier", "trial_spawner", true);
        }
        if (block == Blocks.VAULT) {
            return KhanhFeatures.bool("block_notifier", "vault", false);
        }
        return block == Blocks.END_PORTAL_FRAME && KhanhFeatures.bool("block_notifier", "end_portal", false);
    }

    private static boolean selected(String option, boolean fallback) {
        return KhanhFeatures.bool("block_notifier", option, fallback);
    }

    public static int blockNotifierColor() {
        return switch (KhanhFeatures.choice("block_notifier", "color", "Khanh Client Pink")) {
            case "Custom" -> VisualTheme.rgb(
                KhanhFeatures.number("block_notifier", "custom_red", 255),
                KhanhFeatures.number("block_notifier", "custom_green", 22),
                KhanhFeatures.number("block_notifier", "custom_blue", 159));
            case "Red" -> 0xFFFF263D;
            case "Gold" -> 0xFFFFB21A;
            case "Cyan" -> 0xFF42D9FF;
            case "Lime" -> 0xFF64F58D;
            case "Violet" -> 0xFFB76CFF;
            case "White" -> 0xFFF5F3F4;
            default -> 0xFFFF169F;
        };
    }

    private void tickSpawnerNotifier(MinecraftClient client) {
        int range = KhanhFeatures.number("spawner_chunk", "range", 128);
        for (Map.Entry<BlockPos, StorageType> entry : storageBlocks.entrySet()) {
            StorageType storageType = entry.getValue();
            if (!isSpawner(storageType)) {
                continue;
            }
            BlockPos pos = entry.getKey();
            double squaredDistance = client.player.squaredDistanceTo(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
            if (squaredDistance > range * range || !notifiedSpawners.add(pos.asLong())) {
                continue;
            }
            if (KhanhFeatures.bool("spawner_chunk", "notification", true)) {
                String type = storageType == StorageType.TRIAL_SPAWNER ? "Trial Spawner" : "Mob Spawner";
                client.player.sendMessage(Text.literal(String.format(Locale.ROOT,
                    "[Khanh Client] %s nearby at %d %d %d (%.0fm)", type, pos.getX(), pos.getY(), pos.getZ(), Math.sqrt(squaredDistance))), false);
                if (KhanhFeatures.bool("spawner_chunk", "sound", true)) {
                    KhanhSounds.notify(client);
                }
            }
        }
        BlockPos playerPos = client.player.getBlockPos();
        int pruneRange = range + 32;
        notifiedSpawners.removeIf(value -> {
            BlockPos pos = BlockPos.fromLong(value);
            return Math.abs(pos.getX() - playerPos.getX()) > pruneRange || Math.abs(pos.getZ() - playerPos.getZ()) > pruneRange;
        });
    }

    private static int squaredHorizontalDistance(BlockPos first, BlockPos second) {
        int dx = first.getX() - second.getX();
        int dz = first.getZ() - second.getZ();
        return dx * dx + dz * dz;
    }

    private static int centeredOffset(int index) {
        if (index == 0) {
            return 0;
        }
        int distance = (index + 1) / 2;
        return (index & 1) == 1 ? distance : -distance;
    }

    private static int suspiciousScanBudget() {
        if (!KhanhFeatures.enabled("sus_esp")) {
            return 2;
        }
        return 10;
    }
}
