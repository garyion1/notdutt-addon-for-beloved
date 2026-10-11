package me.tpaburst.history;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.item.ItemStack;

public final class HistoryStore {
    /** One history row. {@code icon} is empty for kills. */
    public record Entry(String time, String text, ItemStack icon) {
    }

    private static final int MAX_KILLS = 500;
    private static final int MAX_LOOT = 1000;
    private static final List<Entry> KILLS = new ArrayList<>();
    private static final List<Entry> LOOT = new ArrayList<>();
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    private static final String[] DEATH_PHRASES = {
        " was slain by ",
        " was shot by ",
        " was killed by ",
        " was blown up by ",
        " was fireballed by ",
        " was impaled by ",
        " was pummeled by ",
        " was doomed to fall by ",
        " was knocked into the void by ",
        " was thrown into the void by "
    };
    private static final String[] KILL_PHRASES = {"you killed", "you have killed", "killed player", "you eliminated"};

    private HistoryStore() {
    }

    private static String now() {
        return LocalTime.now().format(TIME);
    }

    public static synchronized void addKill(String name) {
        if (name == null || name.isBlank()) return;
        KILLS.add(new Entry(now(), name.trim(), ItemStack.EMPTY));
        if (KILLS.size() > MAX_KILLS) KILLS.remove(0);
    }

    public static synchronized void addLoot(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return;
        String text = stack.getCount() + "x " + stack.getName().getString();
        LOOT.add(new Entry(now(), text, stack.copy()));
        if (LOOT.size() > MAX_LOOT) LOOT.remove(0);
    }

    public static synchronized List<Entry> kills() {
        return new ArrayList<>(KILLS);
    }

    public static synchronized List<Entry> loot() {
        return new ArrayList<>(LOOT);
    }

    public static synchronized int killCount() {
        return KILLS.size();
    }

    public static synchronized int lootCount() {
        return LOOT.size();
    }

    public static synchronized void clearKills() {
        KILLS.clear();
    }

    public static synchronized void clearLoot() {
        LOOT.clear();
    }

    public static void captureKillMessage(String message) {
        if (message == null) return;
        String raw = message.trim();
        String lower = raw.toLowerCase(Locale.ROOT);

        for (String phrase : DEATH_PHRASES) {
            int idx = lower.indexOf(phrase);
            if (idx > 0) {
                String victim = raw.substring(0, idx).trim()
                    .replaceAll("(?i)§[0-9A-FK-OR]", "")
                    .replaceAll("[^A-Za-z0-9_]", "");
                if (!victim.isBlank()) addKill(victim);
                return;
            }
        }

        for (String phrase : KILL_PHRASES) {
            int idx = lower.indexOf(phrase);
            if (idx >= 0) {
                String victim = raw.substring(idx + phrase.length()).trim();
                int space = victim.indexOf(' ');
                if (space > 0) victim = victim.substring(0, space);
                victim = victim.replaceAll("[^A-Za-z0-9_]", "");
                if (!victim.isBlank()) addKill(victim);
                return;
            }
        }
    }
}
