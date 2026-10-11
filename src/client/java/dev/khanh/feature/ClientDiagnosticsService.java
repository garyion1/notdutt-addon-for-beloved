package dev.khanh.feature;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;

/** Native Khanh Client diagnostics and player utility features. */
public final class ClientDiagnosticsService {
    private static final EquipmentSlot[] EQUIPMENT = {
        EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND, EquipmentSlot.HEAD,
        EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };
    private static final Set<String> durabilityWarnings = new HashSet<>();
    private static final Map<ChunkPos, Deque<Long>> redstoneUpdates = new HashMap<>();
    private static final Map<ChunkPos, Long> activeRedstone = new HashMap<>();
    private static Object lastWorld;
    private static int durabilityTimer;
    private static VanishStage vanishStage = VanishStage.IDLE;
    private static boolean forceVanishProbe;
    private static boolean sawOfflineReply;
    private static long nextVanishAt;
    private static String vanishTarget = "";

    private ClientDiagnosticsService() {
    }

    public static void tick(MinecraftClient client) {
        if (client.world != lastWorld) {
            lastWorld = client.world;
            durabilityWarnings.clear();
            redstoneUpdates.clear();
            activeRedstone.clear();
            durabilityTimer = 0;
            vanishStage = VanishStage.IDLE;
            forceVanishProbe = false;
            nextVanishAt = System.currentTimeMillis() + 2_000L;
        }
        if (client.player == null || client.world == null) {
            return;
        }
        tickDurability(client);
        tickRedstoneActivity(client);
        tickVanish(client);
    }

    private static void tickDurability(MinecraftClient client) {
        if (!KhanhFeatures.enabled("durability_alert")) {
            durabilityWarnings.clear();
            durabilityTimer = 0;
            return;
        }
        if (--durabilityTimer > 0) {
            return;
        }
        durabilityTimer = KhanhFeatures.number("durability_alert", "interval", 20);
        Set<String> lowNow = new HashSet<>();
        if (KhanhFeatures.bool("durability_alert", "equipment", true)) {
            for (EquipmentSlot slot : EQUIPMENT) {
                checkDurability(client, client.player.getEquippedStack(slot), "equip:" + slot.getName(), slot.getName(), lowNow);
            }
        }
        if (KhanhFeatures.bool("durability_alert", "inventory", false)) {
            for (int slot = 0; slot < client.player.getInventory().size(); slot++) {
                checkDurability(client, client.player.getInventory().getStack(slot), "inv:" + slot, "slot " + slot, lowNow);
            }
        }
        durabilityWarnings.retainAll(lowNow);
    }

    private static void checkDurability(MinecraftClient client, ItemStack stack, String key, String where, Set<String> lowNow) {
        if (stack.isEmpty() || !stack.isDamageable() || !watchedDurabilityItem(stack)) {
            return;
        }
        int maximum = stack.getMaxDamage();
        int remaining = maximum - stack.getDamage();
        int percent = maximum <= 0 ? 100 : remaining * 100 / maximum;
        int value = KhanhFeatures.choice("durability_alert", "mode", "Percent").equals("Durability") ? remaining : percent;
        if (value > KhanhFeatures.number("durability_alert", "threshold", 10)) {
            return;
        }
        lowNow.add(key);
        if (!durabilityWarnings.add(key)) {
            return;
        }
        if (KhanhFeatures.bool("durability_alert", "card", true)) {
            FinderNotifications.show(stack.getName().getString() + " is about to break",
                String.format(Locale.ROOT, "%d/%d durability left (%d%%)", remaining, maximum, percent),
                0xFF5555, KhanhFeatures.bool("durability_alert", "sound", true));
            return;
        }
        GameplayUtilityService.actionBar(client, String.format(Locale.ROOT,
            "%s low in %s • %d/%d (%d%%)", stack.getName().getString(), where, remaining, maximum, percent));
        if (KhanhFeatures.bool("durability_alert", "sound", true)) {
            KhanhSounds.warning(client);
        }
    }

    private static boolean watchedDurabilityItem(ItemStack stack) {
        String mode = KhanhFeatures.choice("durability_alert", "items", "All Damageable");
        if (mode.equals("All Damageable")) {
            return true;
        }
        String path = Registries.ITEM.getId(stack.getItem()).getPath();
        boolean armor = path.equals("elytra") || containsAny(path, "helmet", "chestplate", "leggings", "boots", "shield");
        if (mode.equals("Armor + Elytra")) {
            return armor;
        }
        return !armor && containsAny(path, "sword", "axe", "pickaxe", "shovel", "hoe", "bow", "trident", "mace", "shears", "rod");
    }

    public static void recordBlockUpdate(BlockPos pos, BlockState state) {
        if (!KhanhFeatures.enabled("redstone_activity") || pos == null || state == null) {
            return;
        }
        if (KhanhFeatures.bool("redstone_activity", "only_below", false)
            && pos.getY() > KhanhFeatures.number("redstone_activity", "y_level", 0)) {
            return;
        }
        if (KhanhFeatures.bool("redstone_activity", "only_redstone", true) && !isRedstoneComponent(state)) {
            return;
        }
        redstoneUpdates.computeIfAbsent(new ChunkPos(pos), ignored -> new ArrayDeque<>()).addLast(System.currentTimeMillis());
    }

    private static boolean isRedstoneComponent(BlockState state) {
        String path = Registries.BLOCK.getId(state.getBlock()).getPath();
        return containsAny(path, "redstone", "repeater", "comparator", "observer", "piston", "lever", "button",
            "pressure_plate", "tripwire", "target", "note_block", "dispenser", "dropper", "hopper");
    }

    private static void tickRedstoneActivity(MinecraftClient client) {
        long now = System.currentTimeMillis();
        if (!KhanhFeatures.enabled("redstone_activity")) {
            redstoneUpdates.clear();
            activeRedstone.clear();
            return;
        }
        long window = KhanhFeatures.number("redstone_activity", "window_ms", 3000);
        int threshold = KhanhFeatures.number("redstone_activity", "threshold", 20);
        long linger = KhanhFeatures.number("redstone_activity", "linger_ms", 8000);
        Iterator<Map.Entry<ChunkPos, Deque<Long>>> iterator = redstoneUpdates.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<ChunkPos, Deque<Long>> entry = iterator.next();
            Deque<Long> events = entry.getValue();
            while (!events.isEmpty() && now - events.peekFirst() > window) {
                events.removeFirst();
            }
            if (events.size() >= threshold) {
                boolean fresh = !activeRedstone.containsKey(entry.getKey());
                activeRedstone.put(entry.getKey(), now + linger);
                events.clear();
                if (fresh && KhanhFeatures.bool("redstone_activity", "sound", false)) {
                    KhanhSounds.alert(client);
                }
            }
            if (events.isEmpty()) {
                iterator.remove();
            }
        }
        activeRedstone.entrySet().removeIf(entry -> entry.getValue() < now);
    }

    public static Set<ChunkPos> activeRedstoneChunks() {
        return Set.copyOf(activeRedstone.keySet());
    }

    public static void probeVanishNow() {
        forceVanishProbe = true;
        nextVanishAt = 0L;
    }

    private static void tickVanish(MinecraftClient client) {
        if (!KhanhFeatures.enabled("vanish_check") && !forceVanishProbe) {
            vanishStage = VanishStage.IDLE;
            return;
        }
        long now = System.currentTimeMillis();
        if (now < nextVanishAt || client.getNetworkHandler() == null) {
            return;
        }
        switch (vanishStage) {
            case IDLE -> {
                String target = KhanhFeatures.text("vanish_check", "target", "Player").trim();
                forceVanishProbe = false;
                if (target.isEmpty() || (KhanhFeatures.bool("vanish_check", "only_absent", true) && inTab(client, target))) {
                    nextVanishAt = now + KhanhFeatures.number("vanish_check", "interval", 120) * 1000L;
                    return;
                }
                vanishTarget = target;
                sawOfflineReply = false;
                String request = KhanhFeatures.text("vanish_check", "request", "tpahere {player}").replace("{player}", target);
                client.getNetworkHandler().sendChatCommand(stripSlash(request));
                vanishStage = VanishStage.WAITING;
                nextVanishAt = now + KhanhFeatures.number("vanish_check", "reply_window", 2000);
                GameplayUtilityService.actionBar(client, "Vanish Check • probing " + target);
            }
            case WAITING -> {
                if (sawOfflineReply) {
                    GameplayUtilityService.actionBar(client, "Vanish Check • " + vanishTarget + " is offline");
                } else {
                    GameplayUtilityService.actionBar(client, "Vanish Check • " + vanishTarget + " may be vanished");
                    if (KhanhFeatures.bool("vanish_check", "sound", true)) {
                        KhanhSounds.warning(client);
                    }
                }
                vanishStage = VanishStage.CANCELLING;
                nextVanishAt = now + 500L;
            }
            case CANCELLING -> {
                String cancel = stripSlash(KhanhFeatures.text("vanish_check", "cancel", "tpacancel"));
                if (!cancel.isBlank()) {
                    client.getNetworkHandler().sendChatCommand(cancel);
                }
                vanishStage = VanishStage.SETTLING;
                nextVanishAt = now + 500L;
            }
            case SETTLING -> {
                vanishStage = VanishStage.IDLE;
                nextVanishAt = now + KhanhFeatures.number("vanish_check", "interval", 120) * 1000L;
            }
        }
    }

    public static void onGameMessage(Text message) {
        if (vanishStage != VanishStage.WAITING || message == null) {
            return;
        }
        String lower = message.getString().toLowerCase(Locale.ROOT);
        if (containsAny(lower, "is not online", "not online", "isn't online", "is offline", "could not find",
            "couldn't find", "never played", "no player")) {
            sawOfflineReply = true;
        }
    }

    private static boolean inTab(MinecraftClient client, String target) {
        if (client.getNetworkHandler() == null) {
            return false;
        }
        for (PlayerListEntry entry : client.getNetworkHandler().getPlayerList()) {
            String name = entry.getProfile().name();
            if (name != null && name.equalsIgnoreCase(target)) {
                return true;
            }
        }
        return false;
    }

    private static String stripSlash(String command) {
        String trimmed = command == null ? "" : command.trim();
        return trimmed.startsWith("/") ? trimmed.substring(1) : trimmed;
    }

    private static boolean containsAny(String text, String... values) {
        for (String value : values) {
            if (text.contains(value)) {
                return true;
            }
        }
        return false;
    }

    private enum VanishStage {
        IDLE,
        WAITING,
        CANCELLING,
        SETTLING
    }
}
