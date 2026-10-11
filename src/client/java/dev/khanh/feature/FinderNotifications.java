package dev.khanh.feature;

import dev.khanh.gui.SmoothGuiShapes;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;

/**
 * Finder notifications: when a finder (sus chunks, prime chunks, light, amethyst, holes, bedrock
 * voids, spawners, Block Notifier) turns up something new, a card slides down at the top of the
 * screen saying what it found and where, then fades out after a few seconds. A finder that turns up
 * lots at once (joining a server) gets one grouped card instead of a flood.
 */
public final class FinderNotifications {
    /** One finder: its module, what to call a find, and where its finds are. */
    private record Finder(String module, String what, int colour) { }

    private record Toast(String title, String detail, int colour, long born) { }

    private static final Finder[] FINDERS = {
        new Finder("sus_esp", "sus chunk", 0xFF5A8C),
        new Finder("prime_chunk_finder", "prime chunk", 0x5DF27A),
        new Finder("light_finder", "underground light", 0xFFD84A),
        new Finder("amethyst_esp", "amethyst", 0xC084FC),
        new Finder("hole_esp", "hole", 0xFF5555),
        new Finder("bedrock_void_esp", "bedrock hole", 0x8B5CFF),
        new Finder("spawner_chunk", "spawner", 0xFF4D4D),
        new Finder("block_notifier", "block", 0x66D9FF),
    };
    private static final Map<String, Set<Long>> SEEN = new HashMap<>();
    private static final Deque<Toast> TOASTS = new ArrayDeque<>();
    private static Object lastWorld;
    private static int ticks;

    private FinderNotifications() { }

    /** Khanh Client has no finder pop-ups: finders only draw in the world. */
    public static boolean wanted(String module) {
        return false;
    }

    public static void tick(MinecraftClient client) {
        if (client.world != lastWorld) {
            lastWorld = client.world;
            SEEN.clear();
            TOASTS.clear();
        }
        if (client.world == null || client.player == null || ++ticks % 10 != 0) return;
        WorldScanner scanner = KhanhFeatures.scanner();
        for (Finder finder : FINDERS) {
            if (!wanted(finder.module())) {
                SEEN.remove(finder.module());
                continue;
            }
            List<BlockPos> found = finds(client, scanner, finder.module());
            Set<Long> seen = SEEN.computeIfAbsent(finder.module(), key -> new HashSet<>());
            List<BlockPos> fresh = new ArrayList<>();
            for (BlockPos pos : found) if (seen.add(pos.asLong())) fresh.add(pos);
            if (fresh.isEmpty()) continue;
            BlockPos nearest = fresh.stream().min(java.util.Comparator.comparingDouble(
                pos -> client.player.squaredDistanceTo(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5))).orElseThrow();
            String what = finder.module().equals("block_notifier")
                ? client.world.getBlockState(nearest).getBlock().getName().getString().toLowerCase(java.util.Locale.ROOT)
                : finder.what();
            String title = fresh.size() == 1 ? "Found " + article(what) + " " + what
                : "Found " + fresh.size() + " " + plural(what);
            int distance = (int) Math.round(Math.sqrt(client.player.squaredDistanceTo(nearest.getX() + 0.5, nearest.getY() + 0.5, nearest.getZ() + 0.5)));
            String detail = (fresh.size() == 1 ? "" : "Nearest  ") + "X " + nearest.getX() + "  Y " + nearest.getY()
                + "  Z " + nearest.getZ() + "  ·  " + distance + "m";
            push(client, new Toast(title, detail, finder.colour(), System.currentTimeMillis()));
        }
    }

    /** A card from any other feature (Armor Alert, ...). */
    public static void show(String title, String detail, int colour, boolean sound) {
        MinecraftClient client = MinecraftClient.getInstance();
        TOASTS.addLast(new Toast(title, detail, colour & 0xFFFFFF, System.currentTimeMillis()));
        while (TOASTS.size() > 4) TOASTS.removeFirst();
        if (sound) KhanhSounds.warning(client);
    }

    private static void push(MinecraftClient client, Toast toast) {
        TOASTS.addLast(toast);
        while (TOASTS.size() > 4) TOASTS.removeFirst();
        if (KhanhFeatures.bool("visuals", "finder_sound", true)) KhanhSounds.notify(client);
    }

    /** Where a finder's current results are, as block positions (chunks become their middle). */
    private static List<BlockPos> finds(MinecraftClient client, WorldScanner scanner, String module) {
        int y = client.player.getBlockY();
        List<BlockPos> out = new ArrayList<>();
        switch (module) {
            case "sus_esp" -> chunks(out, scanner.suspiciousChunks(), y);
            case "prime_chunk_finder" -> {
                chunks(out, scanner.primeGrowthChunks().keySet(), y);
                chunks(out, scanner.primeLightChunks().keySet(), y);
            }
            case "light_finder" -> chunks(out, scanner.undergroundLightChunks().keySet(), y);
            case "amethyst_esp" -> out.addAll(scanner.geodeCenters());
            case "hole_esp" -> scanner.holeShafts().forEach(hole -> out.add(hole.start()));
            case "bedrock_void_esp" -> out.addAll(scanner.bedrockVoidCells());
            case "spawner_chunk" -> out.addAll(scanner.notifiedSpawners());
            case "block_notifier" -> out.addAll(scanner.notifiedBlocks());
            default -> { }
        }
        return out;
    }

    private static void chunks(List<BlockPos> out, Collection<ChunkPos> chunks, int y) {
        for (ChunkPos chunk : chunks) out.add(new BlockPos(chunk.getCenterX(), y, chunk.getCenterZ()));
    }

    private static String article(String word) {
        return "aeiou".indexOf(word.charAt(0)) >= 0 ? "an" : "a";
    }

    private static String plural(String word) {
        if (word.endsWith("light")) return word + " spots";
        if (word.endsWith("amethyst")) return "amethyst geodes";
        return word.endsWith("s") ? word : word + "s";
    }

    /** The cards: slide down and fade in, wait, then fade out smoothly. */
    public static void render(DrawContext context, RenderTickCounter counter) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (TOASTS.isEmpty() || client.player == null || client.options.hudHidden) return;
        long now = System.currentTimeMillis();
        long life = Math.round(KhanhFeatures.decimal("visuals", "finder_seconds", 4.0) * 1000.0);
        TOASTS.removeIf(toast -> now - toast.born() > life + 700L);
        var tr = client.textRenderer;
        int y = 8;
        for (Toast toast : TOASTS) {
            long age = now - toast.born();
            float in = Math.min(1.0F, age / 260.0F);
            float out = age > life ? 1.0F - Math.min(1.0F, (age - life) / 700.0F) : 1.0F;
            float ease = 1.0F - (1.0F - in) * (1.0F - in) * (1.0F - in);
            float alpha = ease * out * out * (3.0F - 2.0F * out);
            if (alpha <= 0.02F) continue;
            int w = Math.max(tr.getWidth(toast.title()), Math.round(tr.getWidth(toast.detail()) * 0.85F)) + 34;
            int h = 30;
            int x = (context.getScaledWindowWidth() - w) / 2;
            int top = Math.round(y - (1.0F - ease) * 14.0F);
            int a = Math.round(alpha * 255.0F);
            SmoothGuiShapes.rect(context, x, top, w, h, 9, Math.round(alpha * 225.0F) << 24 | 0x0B0B0F, false);
            SmoothGuiShapes.rect(context, x, top, w, h, 9, Math.round(alpha * 60.0F) << 24 | 0xFFFFFF, true);
            SmoothGuiShapes.rect(context, x + 9, top + 11, 8, 8, 4, a << 24 | toast.colour(), false);
            context.drawText(tr, Text.literal(toast.title()), x + 24, top + 5, a << 24 | 0xFFFFFF, false);
            context.getMatrices().pushMatrix();
            context.getMatrices().translate(x + 24, top + 17);
            context.getMatrices().scale(0.85F, 0.85F);
            context.drawText(tr, Text.literal(toast.detail()), 0, 0, Math.round(alpha * 200.0F) << 24 | 0xC8C8D0, false);
            context.getMatrices().popMatrix();
            y += Math.round((h + 5) * Math.max(ease, out));
        }
    }
}
