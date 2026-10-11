package dev.khanh.feature;

import dev.khanh.KhanhClient;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.scoreboard.ScoreAccess;
import net.minecraft.scoreboard.ScoreHolder;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardCriterion;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.number.BlankNumberFormat;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;

/**
 * Fake Stats: a sidebar of your own made-up stats. It lives on a private scoreboard the server
 * never sees or touches, and is swapped in only when the HUD draws the sidebar, so servers that
 * keep resending their board can no longer overwrite it.
 */
public final class FakeScoreboardService {
    private static final String OBJECTIVE = "khanh_fake_stats";
    private static final Scoreboard BOARD = new Scoreboard();
    private static volatile ScoreboardObjective ours;
    private int refreshTicks;

    /** The sidebar to draw instead of the server's, or null when Fake Stats is off. */
    public static ScoreboardObjective objective() {
        return KhanhFeatures.enabled("fake_stats") ? ours : null;
    }

    public void tick(MinecraftClient client) {
        if (!KhanhFeatures.enabled("fake_stats")) {
            refreshTicks = 0;
            return;
        }
        if (--refreshTicks <= 0) {
            refreshTicks = 10;
            rebuild();
        }
    }

    private void rebuild() {
        try {
            ScoreboardObjective old = BOARD.getNullableObjective(OBJECTIVE);
            if (old != null) BOARD.removeObjective(old);
            MutableText title = colored(title(), KhanhFeatures.color("fake_stats", "title_color", 0xFFFFFFFF))
                .styled(style -> style.withBold(KhanhFeatures.bool("fake_stats", "title_bold", false)));
            ScoreboardObjective objective = BOARD.addObjective(OBJECTIVE, ScoreboardCriterion.DUMMY, title,
                ScoreboardCriterion.RenderType.INTEGER, false, BlankNumberFormat.INSTANCE);
            List<Text> lines = buildLines();
            for (int index = 0; index < lines.size(); index++) {
                ScoreAccess score = BOARD.getOrCreateScore(ScoreHolder.fromName("khanh_line_" + index), objective);
                score.setScore(lines.size() - index);
                score.setDisplayText(lines.get(index));
                score.setNumberFormat(BlankNumberFormat.INSTANCE);
            }
            ours = objective;
        } catch (Exception exception) {
            KhanhClient.LOGGER.debug("Could not refresh the Khanh Client fake scoreboard", exception);
        }
    }

    /** DonutSMP's sidebar: your name on top, then a coloured icon and a white value per line. */
    private List<Text> buildLines() {
        List<Text> lines = new ArrayList<>();
        boolean labels = KhanhFeatures.bool("fake_stats", "labels", false);
        boolean spacers = KhanhFeatures.bool("fake_stats", "spacers", false);
        if (spacers) lines.add(Text.literal(" "));
        if (KhanhFeatures.bool("fake_stats", "show_money", true))
            lines.add(line("$", "Money", PrivacyService.fakeBalance().replace("$", ""), 0x00FC00, labels));
        if (KhanhFeatures.bool("fake_stats", "show_shards", true))
            lines.add(line("\u2605", "Shards", PrivacyService.fakeShards(), 0xA503FC, labels));
        if (KhanhFeatures.bool("fake_stats", "show_kills", true))
            lines.add(line("\uD83D\uDDE1", "Kills", PrivacyService.fakeKills(), 0xFC0000, labels));
        if (KhanhFeatures.bool("fake_stats", "show_deaths", true))
            lines.add(line("\u2620", "Deaths", PrivacyService.fakeDeaths(), 0xFC7703, labels));
        if (KhanhFeatures.bool("fake_stats", "show_keyall", false))
            lines.add(line("\u231B", "Keyall", keyall(), 0x00A4FC, labels));
        if (KhanhFeatures.bool("fake_stats", "show_playtime", true))
            lines.add(line("\u231A", "Playtime", PrivacyService.fakePlaytime(), 0xFCE300, labels));
        String team = KhanhFeatures.text("fake_stats", "team", "").strip();
        if (KhanhFeatures.bool("fake_stats", "show_team", true) && !team.isEmpty())
            lines.add(line("\u2691", "Team", team, 0x00FCFC, labels));
        String region = KhanhFeatures.text("fake_stats", "region", "EU West").strip();
        if (KhanhFeatures.bool("fake_stats", "show_region", false) && !region.isEmpty()) {
            if (spacers) lines.add(Text.literal("  "));
            lines.add(colored(region + " ", 0xA0A0A0).append(colored("(" + ping() + "ms)", 0x707070)));
        }
        if (lines.isEmpty()) lines.add(Text.literal(" "));
        return lines;
    }

    /** The title: {name} becomes your (protected) name, like Donut puts your username on top. */
    private static String title() {
        String title = KhanhFeatures.text("fake_stats", "title", "{name}");
        if (!title.contains("{name}")) return title;
        MinecraftClient client = MinecraftClient.getInstance();
        String name = client.getGameProfile() != null ? client.getGameProfile().name() : "Player";
        return title.replace("{name}", name == null ? "Player" : name);
    }

    private static Text line(String icon, String label, String value, int colour, boolean labels) {
        MutableText text = colored(icon + " ", colour);
        if (labels) text.append(colored(label + " ", 0xFFFFFF));
        boolean coloured = KhanhFeatures.bool("fake_stats", "coloured_values", false);
        return text.append(colored(value, coloured ? colour : 0xFFFFFF));
    }

    /** Donut's hourly keyall: counts down to the next hour, or shows your own text. */
    private static String keyall() {
        String fixed = KhanhFeatures.text("fake_stats", "keyall", "").strip();
        if (!fixed.isEmpty()) return fixed;
        java.time.LocalTime now = java.time.LocalTime.now();
        int left = 3600 - (now.getMinute() * 60 + now.getSecond());
        return (left / 60) + "m " + (left % 60) + "s";
    }

    private static int ping() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!KhanhFeatures.bool("fake_stats", "real_ping", true) || client.getNetworkHandler() == null || client.player == null) {
            return KhanhFeatures.number("fake_stats", "ping", 23);
        }
        var entry = client.getNetworkHandler().getPlayerListEntry(client.player.getUuid());
        return entry == null ? KhanhFeatures.number("fake_stats", "ping", 23) : entry.getLatency();
    }

    private static MutableText colored(String text, int rgb) {
        return Text.literal(text).withColor(rgb & 0x00FFFFFF);
    }
}
