package dev.khanh.feature;

import dev.khanh.KhanhClient;
import dev.khanh.config.ConfigManager;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.hit.EntityHitResult;

public final class FriendService {
    private static final Pattern USERNAME = Pattern.compile("[A-Za-z0-9_]{1,16}");
    private static final Map<String, String> FRIENDS = new LinkedHashMap<>();

    private FriendService() {
    }

    public static boolean handleCommand(String command) {
        String trimmed = command == null ? "" : command.trim();
        String[] parts = trimmed.split("\\s+");
        if (parts.length == 0 || (!parts[0].equalsIgnoreCase("zerofriend")
            && !parts[0].equalsIgnoreCase("zerofirefriend"))) {
            return false;
        }
        if (parts.length == 1 || parts[1].equalsIgnoreCase("list")) {
            listInChat();
            return true;
        }
        switch (parts[1].toLowerCase(Locale.ROOT)) {
            case "add" -> {
                if (parts.length < 3) {
                    notifyPlayer("Use /zerofriend add <name>");
                } else {
                    add(parts[2]);
                }
            }
            case "remove", "del" -> {
                if (parts.length < 3) {
                    notifyPlayer("Use /zerofriend remove <name>");
                } else {
                    remove(parts[2]);
                }
            }
            case "clear" -> clear();
            default -> notifyPlayer("Friends: add <name>, remove <name>, list, or clear");
        }
        return true;
    }

    public static void addTarget() {
        PlayerEntity target = selectedPlayer();
        if (target == null) {
            notifyPlayer("Aim at a player first");
            return;
        }
        add(target.getGameProfile().name());
    }

    public static void removeTarget() {
        PlayerEntity target = selectedPlayer();
        if (target == null) {
            notifyPlayer("Aim at a player first");
            return;
        }
        remove(target.getGameProfile().name());
    }

    public static void add(String name) {
        String clean = name == null ? "" : name.trim();
        if (!USERNAME.matcher(clean).matches()) {
            notifyPlayer("Friend names must be valid Minecraft usernames");
            return;
        }
        String previous = FRIENDS.putIfAbsent(key(clean), clean);
        if (previous == null) {
            ConfigManager.save(KhanhClient.modules());
            notifyPlayer("Added friend " + clean);
        } else {
            notifyPlayer(previous + " is already a friend");
        }
    }

    public static void remove(String name) {
        String removed = FRIENDS.remove(key(name));
        if (removed == null) {
            notifyPlayer(name + " is not in your friends list");
            return;
        }
        ConfigManager.save(KhanhClient.modules());
        notifyPlayer("Removed friend " + removed);
    }

    public static void clear() {
        if (FRIENDS.isEmpty()) {
            notifyPlayer("Friends list is already empty");
            return;
        }
        FRIENDS.clear();
        ConfigManager.save(KhanhClient.modules());
        notifyPlayer("Cleared friends list");
    }

    public static boolean isFriend(String name) {
        return name != null && FRIENDS.containsKey(key(name));
    }

    public static boolean protectsTargets() {
        return KhanhFeatures.enabled("friends") && KhanhFeatures.bool("friends", "protect_targets", true);
    }

    public static List<String> names() {
        return List.copyOf(FRIENDS.values());
    }

    public static void replaceAll(Collection<String> names) {
        FRIENDS.clear();
        if (names == null) {
            return;
        }
        for (String name : names) {
            String clean = name == null ? "" : name.trim();
            if (USERNAME.matcher(clean).matches()) {
                FRIENDS.putIfAbsent(key(clean), clean);
            }
        }
    }

    public static List<FriendEntry> entries() {
        MinecraftClient client = MinecraftClient.getInstance();
        Map<String, Boolean> online = new LinkedHashMap<>();
        if (client.getNetworkHandler() != null) {
            for (PlayerListEntry entry : client.getNetworkHandler().getPlayerList()) {
                online.put(key(entry.getProfile().name()), true);
            }
        }
        List<FriendEntry> entries = new ArrayList<>();
        for (String name : FRIENDS.values()) {
            entries.add(new FriendEntry(name, online.containsKey(key(name))));
        }
        return List.copyOf(entries);
    }

    public static int color() {
        String selected = KhanhFeatures.choice("friends", "color", "Mint");
        return switch (selected) {
            case "Khanh Client Pink" -> 0xFFFF169F;
            case "Cyan" -> 0xFF42D9FF;
            case "Violet" -> 0xFFB76CFF;
            case "Gold" -> 0xFFFFB21A;
            case "Custom" -> VisualTheme.rgb(
                KhanhFeatures.number("friends", "custom_red", 80),
                KhanhFeatures.number("friends", "custom_green", 255),
                KhanhFeatures.number("friends", "custom_blue", 166));
            default -> 0xFF50FFA6;
        };
    }

    public static void listInChat() {
        if (FRIENDS.isEmpty()) {
            notifyPlayer("No friends saved. Use /zerofriend add <name>");
            return;
        }
        notifyPlayer("Friends: " + String.join(", ", FRIENDS.values()));
    }

    private static PlayerEntity selectedPlayer() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.crosshairTarget instanceof EntityHitResult hit && hit.getEntity() instanceof PlayerEntity player) {
            return player;
        }
        return KhanhFeatures.target() instanceof PlayerEntity player ? player : null;
    }

    private static String key(String name) {
        return name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
    }

    private static void notifyPlayer(String message) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            client.player.sendMessage(Text.literal("[Khanh Client] " + message), false);
        }
    }

    public record FriendEntry(String name, boolean online) {
    }
}
