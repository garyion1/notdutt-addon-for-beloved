package dev.khanh.feature;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.scoreboard.Team;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.world.GameMode;

/** Detects staff from server-visible tab names, rank text, styled icons and hidden entries. */
public final class StaffOnlineService {
    private static final List<String> DEFAULT_RANKS = List.of(
        "coowner", "owner", "manager", "administrator", "admin", "developer", "dev",
        "srmod", "seniormod", "moderator", "mod", "srhelper", "seniorhelper",
        "helper", "trialmod", "trial", "support", "staff"
    );

    private final Set<String> online = new LinkedHashSet<>();
    private List<StaffEntry> entries = List.of();
    private ClientWorld lastWorld;
    private int tick;
    private boolean initialized;

    public record StaffEntry(UUID uuid, String rawName, String role, int latency) {
        public String name() { return PrivacyService.protectedName(rawName); }
    }

    public void tick(MinecraftClient client) {
        if ((!KhanhFeatures.enabled("staff_online") && !KhanhFeatures.enabled("admin_detector"))
            || client.world == null || client.getNetworkHandler() == null) {
            reset();
            return;
        }
        if (lastWorld != client.world) {
            reset();
            lastWorld = client.world;
        }
        if (initialized && ++tick % 10 != 0) return;

        Set<String> watchNames = watchNames();
        Set<UUID> listed = new HashSet<>();
        for (PlayerListEntry entry : client.getNetworkHandler().getListedPlayerListEntries()) {
            listed.add(entry.getProfile().id());
        }

        String mode = KhanhFeatures.choice("staff_online", "detect_by", "Icons + Ranks");
        boolean allowIcons = mode.equals("Icons + Ranks") || mode.equals("Icons");
        boolean allowRanks = mode.equals("Icons + Ranks") || mode.equals("Ranks");
        boolean showVanished = KhanhFeatures.bool("staff_online", "show_vanished", true);
        boolean fontIcons = KhanhFeatures.bool("staff_online", "font_icons", true);
        String symbols = KhanhFeatures.text("staff_online", "marker_symbols",
            "★,✦,✧,✪,✯,✰,✵,✶,✷,✸,✹,✺,✻,✼,✽,✾,✿,❂,❈");
        List<String> ranks = rankKeywords();

        Set<String> detected = new LinkedHashSet<>();
        List<StaffEntry> snapshot = new ArrayList<>();
        UUID self = client.player == null ? null : client.player.getUuid();
        // Read the same names the Tab list shows; unlisted entries are only checked
        // when Hidden Staff is on, since many servers pad the list with fake entries.
        Iterable<PlayerListEntry> source = showVanished
            ? client.getNetworkHandler().getPlayerList()
            : client.getNetworkHandler().getListedPlayerListEntries();
        for (PlayerListEntry entry : source) {
            UUID uuid = entry.getProfile().id();
            if (uuid.equals(self)) continue;
            String rawName = entry.getProfile().name();
            if (rawName == null || rawName.isBlank()) continue;

            boolean vanished = entry.getGameMode() == GameMode.SPECTATOR || !listed.contains(uuid);
            if (vanished && !showVanished) continue;
            Text tabName = client.inGameHud.getPlayerListHud().getPlayerName(entry);

            boolean exact = watchNames.contains(rawName.toLowerCase(Locale.ROOT));
            boolean icon = hasStaffIcon(tabName, symbols, fontIcons);
            String rank = findRank(plain(tabName), rawName, ranks);
            if (!(exact || allowIcons && icon || allowRanks && rank != null)) continue;

            String role = rank == null ? "Staff" : rankLabel(rank);
            if (vanished) role += " · hidden";
            detected.add(rawName);
            snapshot.add(new StaffEntry(uuid, rawName, role, Math.max(0, entry.getLatency())));
        }

        boolean alerts = KhanhFeatures.enabled("admin_detector")
            || KhanhFeatures.enabled("staff_online") && KhanhFeatures.bool("staff_online", "notification", true);
        if (alerts && initialized && client.player != null) {
            for (String name : detected) {
                if (!containsIgnoreCase(online, name)) {
                    boolean sound = KhanhFeatures.enabled("admin_detector")
                        ? KhanhFeatures.bool("admin_detector", "sound", true)
                        : KhanhFeatures.bool("staff_online", "sound", true);
                    if (sound) KhanhSounds.notify(client);
                }
            }
        }
        online.clear();
        online.addAll(detected);
        snapshot.sort(Comparator.comparing(StaffEntry::role).thenComparing(
            StaffEntry::rawName, String.CASE_INSENSITIVE_ORDER));
        entries = List.copyOf(snapshot);
        initialized = true;
    }

    public List<StaffEntry> entries() { return entries; }

    public List<String> online() {
        List<String> names = new ArrayList<>();
        for (String name : online) names.add(PrivacyService.protectedName(name));
        return List.copyOf(names);
    }

    public boolean containsRawName(String name) {
        return name != null && containsIgnoreCase(online, name);
    }

    public void reset() {
        online.clear();
        lastWorld = null;
        tick = 0;
        entries = List.of();
        initialized = false;
    }

    private static Set<String> watchNames() {
        Set<String> names = new HashSet<>();
        for (String module : new String[] {"staff_online", "admin_detector"}) {
            if (!KhanhFeatures.enabled(module)) continue;
            for (String name : KhanhFeatures.text(module, "watch_names", "").split(",")) {
                String normalized = name.strip().toLowerCase(Locale.ROOT);
                if (!normalized.isEmpty()) names.add(normalized);
            }
        }
        return names;
    }

    private static List<String> rankKeywords() {
        String configured = KhanhFeatures.text("staff_online", "rank_keywords",
            String.join(",", DEFAULT_RANKS));
        List<String> result = new ArrayList<>();
        for (String value : configured.split(",")) {
            String normalized = lettersOnly(value.toLowerCase(Locale.ROOT));
            if (!normalized.isEmpty()) result.add(normalized);
        }
        return result.isEmpty() ? DEFAULT_RANKS : List.copyOf(result);
    }

    private static boolean hasStaffIcon(Text text, String symbols, boolean fontIcons) {
        if (text == null) return false;
        return text.visit((Style style, String value) -> {
            for (int offset = 0; offset < value.length();) {
                int codePoint = value.codePointAt(offset);
                offset += Character.charCount(codePoint);
                if (symbols.indexOf(codePoint) >= 0 || fontIcons && isPrivateUse(codePoint)) {
                    return Optional.of(true);
                }
            }
            return Optional.empty();
        }, Style.EMPTY).orElse(false);
    }

    private static boolean isPrivateUse(int codePoint) {
        return codePoint >= 0xE000 && codePoint <= 0xF8FF
            || codePoint >= 0xF0000 && codePoint <= 0xFFFFD
            || codePoint >= 0x100000 && codePoint <= 0x10FFFD;
    }

    /** Matches rank keywords as whole words in the Tab text (so "Mod" counts but "Modern"
     * or "Commodore" do not); two-word ranks such as "Sr Mod" are joined and checked too. */
    private static String findRank(String evidence, String rawName, List<String> ranks) {
        String stripped = evidence.replaceAll("(?i)" + Pattern.quote(rawName), " ").toLowerCase(Locale.ROOT);
        List<String> words = new ArrayList<>();
        for (String word : stripped.split("[^a-z]+")) if (!word.isEmpty()) words.add(word);
        for (String rank : ranks) {
            for (int i = 0; i < words.size(); i++) {
                if (words.get(i).equals(rank)) return rank;
                if (i + 1 < words.size() && (words.get(i) + words.get(i + 1)).equals(rank)) return rank;
            }
        }
        return null;
    }

    private static String rankLabel(String rank) {
        return switch (rank) {
            case "coowner" -> "Co-Owner";
            case "administrator", "admin" -> "Admin";
            case "developer", "dev" -> "Developer";
            case "srmod", "seniormod" -> "Sr. Mod";
            case "moderator", "mod" -> "Mod";
            case "srhelper", "seniorhelper" -> "Sr. Helper";
            case "trialmod", "trial" -> "Trial";
            default -> Character.toUpperCase(rank.charAt(0)) + rank.substring(1);
        };
    }

    private static String plain(Text text) { return text == null ? "" : text.getString(); }

    private static String lettersOnly(String value) {
        StringBuilder result = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character >= 'a' && character <= 'z') result.append(character);
        }
        return result.toString();
    }

    private static boolean containsIgnoreCase(Set<String> values, String wanted) {
        return values.stream().anyMatch(value -> value.equalsIgnoreCase(wanted));
    }
}
