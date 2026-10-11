package me.tpaburst.modules;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.Suggestions;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import me.tpaburst.SharedState;
import me.tpaburst.TPABurstAddon;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.packet.c2s.play.RequestCommandCompletionsC2SPacket;
import net.minecraft.network.packet.s2c.play.CommandSuggestionsS2CPacket;

public class TPABurst extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> requestDelay = sgGeneral.add(new IntSetting.Builder()
        .name("request-delay")
        .description("Delay between /tpahere requests in ticks. 20 ticks = 1 second.")
        .defaultValue(7)
        .min(1)
        .sliderRange(1, 100)
        .build()
    );

    private final Setting<Integer> batchSize = sgGeneral.add(new IntSetting.Builder()
        .name("batch-size")
        .description("How many random players to queue per server suggestion response.")
        .defaultValue(10)
        .min(1)
        .sliderRange(1, 30)
        .build()
    );

    private final List<String> targets = new ArrayList<>();
    private final Set<String> used = new HashSet<>();
    private int index;
    private int delayTicks;
    private int retryTicks;
    private int completionId = 730000;
    private boolean refreshing;
    private int sent;

    public TPABurst() {
        super(TPABurstAddon.CATEGORY, "tpa-burst", "Continuously sends /tpahere to random non-bot players until disabled.");
    }

    public int getSent() {
        return sent;
    }

    @Override
    public void onActivate() {
        synchronized (targets) {
            targets.clear();
            index = 0;
        }
        used.clear();
        delayTicks = 0;
        retryTicks = 0;
        refreshing = false;
        sent = 0;
        requestTargets();
    }

    @Override
    public void onDeactivate() {
        refreshing = false;
        synchronized (targets) {
            targets.clear();
            index = 0;
        }
        used.clear();
    }

    @Override
    public String getInfoString() {
        return SharedState.pauseTPA ? "paused" : Integer.toString(sent);
    }

    private Set<String> visibleTabNames() {
        Set<String> names = new HashSet<>();
        if (mc == null || mc.getNetworkHandler() == null) return names;
        Collection<PlayerListEntry> entries = mc.getNetworkHandler().getPlayerList();
        if (entries == null) return names;
        for (PlayerListEntry entry : entries) {
            if (entry == null) continue;
            GameProfile profile = entry.getProfile();
            if (profile != null && profile.name() != null) names.add(profile.name().toLowerCase(Locale.ROOT));
        }
        return names;
    }

    private void requestTargets() {
        if (refreshing || mc == null || mc.getNetworkHandler() == null || mc.player == null) return;
        refreshing = true;
        retryTicks = 40;
        char letter = (char) ('a' + ThreadLocalRandom.current().nextInt(26));
        ClientConnection connection = mc.getNetworkHandler().getConnection();
        if (connection == null) {
            refreshing = false;
            return;
        }
        connection.send(new RequestCommandCompletionsC2SPacket(completionId++, "tpahere " + letter));
    }

    private void readSuggestions(CommandSuggestionsS2CPacket packet) {
        Suggestions suggestions = packet.getSuggestions();
        if (suggestions == null) return;

        Set<String> visible = visibleTabNames();
        List<String> found = new ArrayList<>();
        for (Suggestion suggestion : suggestions.getList()) {
            if (suggestion == null || suggestion.getText() == null) continue;
            String name = suggestion.getText().trim();
            if (!eligible(name)) continue;
            String lower = name.toLowerCase(Locale.ROOT);
            if (!visible.contains(lower) && !used.contains(lower)) found.add(name);
        }

        Collections.shuffle(found);
        int count = Math.min(batchSize.get(), found.size());
        synchronized (targets) {
            targets.clear();
            targets.addAll(found.subList(0, count));
            index = 0;
        }
        refreshing = false;
        retryTicks = 0;
        if (count == 0) retryTicks = 2;
    }

    private static boolean eligible(String name) {
        if (name == null || name.isEmpty() || name.length() > 16) return false;
        if (!asciiLetter(name.charAt(0))) return false;
        int letters = 0;
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (asciiLetter(c)) letters++;
            else if ((c < '0' || c > '9') && c != '_') return false;
        }
        return letters * 100 >= name.length() * 60;
    }

    private static boolean asciiLetter(char c) {
        return c >= 'A' && c <= 'Z' || c >= 'a' && c <= 'z';
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (refreshing && event != null && event.packet instanceof CommandSuggestionsS2CPacket packet) readSuggestions(packet);
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (SharedState.pauseTPA) return;
        if (mc == null || mc.getNetworkHandler() == null || mc.player == null) return;

        if (refreshing && retryTicks > 0) {
            retryTicks--;
            if (retryTicks == 0) {
                refreshing = false;
                requestTargets();
            }
        }

        if (delayTicks > 0) {
            delayTicks--;
            return;
        }

        String target = null;
        synchronized (targets) {
            if (index < targets.size()) target = targets.get(index++);
        }

        if (target == null) {
            if (!refreshing) requestTargets();
            delayTicks = 2;
            return;
        }

        String lower = target.toLowerCase(Locale.ROOT);
        if (!used.contains(lower)) {
            used.add(lower);
            mc.getNetworkHandler().sendChatCommand("tpahere " + target);
            sent++;
            delayTicks = Math.max(1, requestDelay.get());
        }
    }
}
