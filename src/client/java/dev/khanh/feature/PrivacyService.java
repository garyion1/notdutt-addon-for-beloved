package dev.khanh.feature;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.MutableText;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;

public final class PrivacyService {
    private static final Map<String, String> ALIASES = new LinkedHashMap<>();
    private static Pattern namesPattern;
    private static int aliasRefreshTick = Integer.MIN_VALUE;
    private static Object lastWorld;
    private static String lastProtectedName = "";
    private static boolean lastHideOthers;
    private static double fakePayments;

    private PrivacyService() {
    }

    public static String transform(String original) {
        if (original == null || original.isEmpty()) {
            return original;
        }

        String result = original;
        if (KhanhFeatures.enabled("name_protect")) {
            result = replaceNames(result);
        }
        if (KhanhFeatures.enabled("fake_rank")) {
            result = replaceOwnRank(result);
        }
        return result;
    }

    public static Text transform(Text original) {
        if (original == null) {
            return null;
        }
        String fullyReplaced = transform(original.getString());
        if (fullyReplaced.equals(original.getString())) return original;
        // Preserve chat, scoreboard and tab-list formatting rather than
        // flattening every styled component into a plain literal.
        MutableText styled = Text.empty();
        original.visit((style, content) -> {
            styled.append(Text.literal(transform(content)).setStyle(style));
            return Optional.empty();
        }, Style.EMPTY);
        return styled.getString().equals(fullyReplaced)
            ? styled : Text.literal(fullyReplaced).setStyle(original.getStyle());
    }

    public static OrderedText transform(OrderedText original) {
        if (original == null) {
            return null;
        }

        StringBuilder value = new StringBuilder();
        List<StringBuilder> runs = new ArrayList<>();
        List<Style> styles = new ArrayList<>();
        Style[] firstStyle = {Style.EMPTY};
        boolean[] capturedStyle = {false};
        original.accept((index, style, codePoint) -> {
            if (!capturedStyle[0]) {
                firstStyle[0] = style;
                capturedStyle[0] = true;
            }
            value.appendCodePoint(codePoint);
            if (styles.isEmpty() || !styles.get(styles.size() - 1).equals(style)) {
                styles.add(style);
                runs.add(new StringBuilder());
            }
            runs.get(runs.size() - 1).appendCodePoint(codePoint);
            return true;
        });
        String replaced = transform(value.toString());
        if (replaced.equals(value.toString())) return original;
        List<String> changed = new ArrayList<>(runs.size());
        StringBuilder combined = new StringBuilder();
        for (StringBuilder run : runs) {
            String part = transform(run.toString());
            changed.add(part);
            combined.append(part);
        }
        if (!combined.toString().equals(replaced))
            return OrderedText.styledForwardsVisitedString(replaced, firstStyle[0]);
        return visitor -> {
            int index = 0;
            for (int run = 0; run < changed.size(); run++) {
                String part = changed.get(run);
                for (int offset = 0; offset < part.length();) {
                    int codePoint = part.codePointAt(offset);
                    if (!visitor.accept(index++, styles.get(run), codePoint)) return false;
                    offset += Character.charCount(codePoint);
                }
            }
            return true;
        };
    }

    public static String protectedName(String actualName) {
        if (actualName == null) return null;
        if (!KhanhFeatures.enabled("name_protect")) {
            return actualName;
        }
        refreshAliases();
        for (Map.Entry<String, String> entry : ALIASES.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(actualName)) {
                return entry.getValue();
            }
        }
        return KhanhFeatures.bool("name_protect", "hide_others", true) ? "Player" : actualName;
    }

    public static String fakeBalance() {
        String configured = KhanhFeatures.text("fake_stats", "money", "$1.25M");
        if (!KhanhFeatures.enabled("fake_pay") || !KhanhFeatures.bool("fake_pay", "remove_from_scoreboard", false) || fakePayments <= 0.0) {
            return configured;
        }
        return formatMoney(Math.max(0.0, parseMoney(configured) - fakePayments), true);
    }

    public static String fakeShards() {
        return KhanhFeatures.text("fake_stats", "shards", "9,999");
    }

    public static String fakeKills() {
        return KhanhFeatures.text("fake_stats", "kills", "250");
    }

    public static String fakeDeaths() {
        return KhanhFeatures.text("fake_stats", "deaths", "0");
    }

    public static String fakePlaytime() {
        return KhanhFeatures.text("fake_stats", "playtime", "365d 12h");
    }

    public static void tick(MinecraftClient client) {
        if (client.world != lastWorld) {
            lastWorld = client.world;
            fakePayments = 0.0;
        }
    }

    public static boolean handleFakePay(String command) {
        String normalizedCommand = command == null ? "" : command.trim();
        if (normalizedCommand.startsWith("/")) {
            normalizedCommand = normalizedCommand.substring(1);
        }
        String[] parts = normalizedCommand.split("\\s+");
        if (parts.length == 0 || !parts[0].equalsIgnoreCase("pay") || !KhanhFeatures.enabled("fake_pay")) {
            return false;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) {
            return true;
        }
        if (parts.length < 3) {
            return false;
        }

        double amount = parseMoney(parts[2]);
        if (!Double.isFinite(amount) || amount <= 0.0) {
            return false;
        }
        String recipient = parts[1];
        if (KhanhFeatures.bool("fake_pay", "remove_from_scoreboard", false)) {
            fakePayments += amount;
        }
        String style = KhanhFeatures.choice("fake_pay", "amount_style", "Short");
        String shown = switch (style) {
            case "As Typed" -> parts[2].replace("$", "");
            case "Full" -> formatAmount(amount, false);
            default -> formatAmount(amount, true);
        };
        MutableText message = Text.literal("You paid ").withColor(0xFFFFFF)
            .append(Text.literal(recipient).withColor(0xFFFFFF))
            .append(Text.literal(" $").withColor(0x55FF55))
            .append(Text.literal(" " + shown).withColor(0xFFFFFF))
            .append(Text.literal(KhanhFeatures.bool("fake_pay", "full_stop", true) ? "." : "").withColor(0xFFFFFF));
        showFakePay(client, message);
        playFakePaySound(client);
        return true;
    }

    private static void showFakePay(MinecraftClient client, Text message) {
        client.player.sendMessage(message, false);
        if (KhanhFeatures.bool("fake_pay", "action_bar", true)) {
            client.player.sendMessage(message, true);
        }
    }

    private static void playFakePaySound(MinecraftClient client) {
        if (!KhanhFeatures.bool("fake_pay", "sound", true)) {
            return;
        }
        KhanhSounds.notify(client);
    }

    private static double parseMoney(String raw) {
        String normalized = raw.trim().replace(",", "").replace("$", "").replace("€", "").replace("£", "");
        if (normalized.isEmpty()) {
            return Double.NaN;
        }
        double multiplier = 1.0;
        char suffix = Character.toLowerCase(normalized.charAt(normalized.length() - 1));
        if (suffix == 'k' || suffix == 'm' || suffix == 'b') {
            multiplier = suffix == 'k' ? 1_000.0 : suffix == 'm' ? 1_000_000.0 : 1_000_000_000.0;
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        try {
            return Double.parseDouble(normalized) * multiplier;
        } catch (NumberFormatException ignored) {
            return Double.NaN;
        }
    }

    private static String formatMoney(double amount, boolean compact) {
        return "$" + formatAmount(amount, compact);
    }

    private static String formatAmount(double amount, boolean compact) {
        if (compact) {
            if (amount >= 1_000_000_000.0) return trimCompact(amount / 1_000_000_000.0) + "B";
            if (amount >= 1_000_000.0) return trimCompact(amount / 1_000_000.0) + "M";
            if (amount >= 1_000.0) return trimCompact(amount / 1_000.0) + "K";
        }
        return Math.abs(amount - Math.rint(amount)) < 0.005
            ? String.format(Locale.ROOT, "%,.0f", amount)
            : String.format(Locale.ROOT, "%,.2f", amount);
    }

    private static String trimCompact(double amount) {
        String formatted = amount >= 100.0
            ? String.format(Locale.ROOT, "%.0f", amount)
            : amount >= 10.0 ? String.format(Locale.ROOT, "%.1f", amount) : String.format(Locale.ROOT, "%.2f", amount);
        return formatted.contains(".") ? formatted.replaceAll("0+$", "").replaceAll("\\.$", "") : formatted;
    }

    private static String replaceNames(String text) {
        refreshAliases();
        if (namesPattern == null) {
            return text;
        }

        Matcher matcher = namesPattern.matcher(text);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            String alias = aliasFor(matcher.group());
            matcher.appendReplacement(result, Matcher.quoteReplacement(alias));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private static String replaceOwnRank(String text) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) {
            return text;
        }
        String name = client.player.getGameProfile().name();
        if (name == null || name.isBlank() || !text.contains(name) || text.contains("[" + KhanhFeatures.choice("fake_rank", "rank", "OWNER") + "] " + name)) {
            return text;
        }
        return text.replace(name, "[" + KhanhFeatures.choice("fake_rank", "rank", "OWNER") + "] " + name);
    }

    private static String aliasFor(String name) {
        for (Map.Entry<String, String> entry : ALIASES.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(name)) {
                return entry.getValue();
            }
        }
        return "Player";
    }

    private static void refreshAliases() {
        MinecraftClient client = MinecraftClient.getInstance();
        int tick = client.player == null ? 0 : client.player.age;
        String replacement = KhanhFeatures.text("name_protect", "replacement_name", "Khanh Client").trim();
        boolean hideOthers = KhanhFeatures.bool("name_protect", "hide_others", true);
        String custom = KhanhFeatures.text("name_protect", "custom_names", "").strip();
        String prefix = KhanhFeatures.text("name_protect", "others_name", "Player").strip();
        String key = replacement + "\u0000" + custom + "\u0000" + prefix;
        if (tick - aliasRefreshTick < 20 && tick >= aliasRefreshTick
            && key.equals(lastProtectedName) && hideOthers == lastHideOthers) {
            return;
        }
        aliasRefreshTick = tick;
        lastProtectedName = key;
        lastHideOthers = hideOthers;
        ALIASES.clear();

        // Your account name is known from launch, so menus and server lists are covered too.
        String own = client.getGameProfile() != null ? client.getGameProfile().name() : null;
        if (own != null && !own.isBlank()) ALIASES.put(own, replacement.isEmpty() ? "Khanh Client" : replacement);
        // Your own picks for other players: "RealName=Fake Name, Other=Another".
        for (String pair : custom.split("[,;\\n]")) {
            int eq = pair.indexOf('=');
            if (eq <= 0) continue;
            String real = pair.substring(0, eq).strip(), fake = pair.substring(eq + 1).strip();
            if (real.matches("[A-Za-z0-9_]{1,16}") && !fake.isEmpty() && ALIASES.keySet().stream().noneMatch(real::equalsIgnoreCase)) {
                ALIASES.put(real, fake);
            }
        }
        if (hideOthers && client.getNetworkHandler() != null) {
            int number = 1;
            for (PlayerListEntry entry : client.getNetworkHandler().getPlayerList()) {
                String name = entry.getProfile().name();
                if (name != null && !name.isBlank() && ALIASES.keySet().stream().noneMatch(name::equalsIgnoreCase)) {
                    ALIASES.put(name, (prefix.isEmpty() ? "Player" : prefix) + " " + number++);
                }
            }
        }

        List<String> names = new ArrayList<>(ALIASES.keySet());
        names.sort(Comparator.comparingInt(String::length).reversed());
        if (names.isEmpty()) {
            namesPattern = null;
            return;
        }
        String joined = names.stream().map(Pattern::quote).reduce((a, b) -> a + "|" + b).orElse("");
        namesPattern = Pattern.compile("(?i)(?<![A-Za-z0-9_])(?:" + joined + ")(?![A-Za-z0-9_])");
    }
}
