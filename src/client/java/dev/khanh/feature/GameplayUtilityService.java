package dev.khanh.feature;

import dev.khanh.KhanhClient;
import dev.khanh.module.Module;
import dev.khanh.config.ConfigManager;
import dev.khanh.mixin.MinecraftClientAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;

/** Native Khanh Client implementations of small stateful gameplay utilities. */
public final class GameplayUtilityService {
    private static Object lastWorld;
    private static boolean pearlArmed;
    private static boolean reloadArmed;
    private static boolean rainKnown;
    private static boolean thunderKnown;
    private static boolean lastRain;
    private static boolean lastThunder;
    private static int pendingHomeSlot = -1;
    private static int homeResetTicks;
    private static int orderCooldown;

    private GameplayUtilityService() {
    }

    public static void tick(MinecraftClient client) {
        if (client.world != lastWorld) {
            lastWorld = client.world;
            pearlArmed = false;
            reloadArmed = false;
            rainKnown = false;
            thunderKnown = false;
            pendingHomeSlot = -1;
            homeResetTicks = 0;
            orderCooldown = 0;
        }
        if (client.player == null || client.world == null) {
            return;
        }
        tickOneShot(client, "chunk_reloader", false);
        tickWeather(client);
        tickHomeReset(client);
        tickAutoOrder(client);
        tickFastPlace(client);
    }

    private static void tickOneShot(MinecraftClient client, String moduleId, boolean pearl) {
        Module module = KhanhClient.modules().byId(moduleId);
        if (module == null) {
            return;
        }
        if (!module.enabled()) {
            if (pearl) pearlArmed = false;
            else reloadArmed = false;
            return;
        }
        boolean armed = pearl ? pearlArmed : reloadArmed;
        if (!armed) {
            if (pearl) {
                pearlArmed = true;
                throwPearl(client);
            } else {
                reloadArmed = true;
                reloadChunks();
            }
            module.setEnabled(false);
            ConfigManager.save(KhanhClient.modules());
        }
    }

    private static void throwPearl(MinecraftClient client) {
        if (client.interactionManager == null) {
            return;
        }
        int slot = findHotbar(client, Items.ENDER_PEARL);
        if (slot < 0) {
            actionBar(client, "Key Pearl • no pearl in the hotbar");
            return;
        }
        int previous = client.player.getInventory().getSelectedSlot();
        client.player.getInventory().setSelectedSlot(slot);
        client.interactionManager.interactItem(client.player, Hand.MAIN_HAND);
        client.player.swingHand(Hand.MAIN_HAND);
        if (KhanhFeatures.bool("key_pearl", "switch_back", true)) {
            client.player.getInventory().setSelectedSlot(previous);
        }
    }


    private static int findHotbar(MinecraftClient client, net.minecraft.item.Item item) {
        for (int slot = 0; slot < 9; slot++) {
            if (client.player.getInventory().getStack(slot).isOf(item)) {
                return slot;
            }
        }
        return -1;
    }

    private static void tickWeather(MinecraftClient client) {
        if (!KhanhFeatures.enabled("weather_alert")) {
            rainKnown = false;
            thunderKnown = false;
            return;
        }
        boolean raining = client.world.isRaining();
        boolean thundering = client.world.isThundering();
        if (rainKnown && raining != lastRain && KhanhFeatures.bool("weather_alert", "rain", true)) {
            actionBar(client, raining ? "Weather • rain started" : "Weather • rain cleared");
            weatherSound(client, raining ? 1.05F : 1.32F);
        }
        if (thunderKnown && thundering != lastThunder && KhanhFeatures.bool("weather_alert", "thunder", true)) {
            actionBar(client, thundering ? "Weather • thunder started" : "Weather • thunder cleared");
            weatherSound(client, thundering ? 0.72F : 1.24F);
        }
        rainKnown = true;
        thunderKnown = true;
        lastRain = raining;
        lastThunder = thundering;
    }

    private static void weatherSound(MinecraftClient client, float pitch) {
        if (KhanhFeatures.bool("weather_alert", "sound", true)) {
            KhanhSounds.alert(client, pitch);
        }
    }

    public static void reloadChunks() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.worldRenderer != null) {
            client.worldRenderer.reload();
            actionBar(client, "Chunk renderer reloaded");
        }
    }

    public static void runHomeReset() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.getNetworkHandler() == null) {
            return;
        }
        int slot;
        try {
            slot = Integer.parseInt(KhanhFeatures.choice("home_reset", "slot", "1"));
        } catch (NumberFormatException ignored) {
            slot = 1;
        }
        client.getNetworkHandler().sendChatCommand("delhome " + slot);
        pendingHomeSlot = slot;
        homeResetTicks = 20;
        actionBar(client, "Home Reset • deleting home " + slot);
    }

    private static void tickHomeReset(MinecraftClient client) {
        if (pendingHomeSlot < 0 || homeResetTicks <= 0 || --homeResetTicks > 0) {
            return;
        }
        if (client.getNetworkHandler() != null) {
            client.getNetworkHandler().sendChatCommand("sethome " + pendingHomeSlot);
            actionBar(client, "Home Reset • home " + pendingHomeSlot + " set");
        }
        pendingHomeSlot = -1;
    }

    private static void tickAutoOrder(MinecraftClient client) {
        if (!KhanhFeatures.enabled("auto_order")) {
            orderCooldown = 0;
            return;
        }
        if (orderCooldown > 0) {
            orderCooldown--;
            return;
        }
        double range = KhanhFeatures.number("auto_order", "range", 12);
        for (net.minecraft.client.network.AbstractClientPlayerEntity player : client.world.getPlayers()) {
            if (player == client.player || player.squaredDistanceTo(client.player) > range * range
                || !KhanhFeatures.staffOnline().containsRawName(player.getGameProfile().name())) {
                continue;
            }
            if (client.getNetworkHandler() != null) {
                client.getNetworkHandler().sendChatCommand("order " + player.getGameProfile().name());
                actionBar(client, "Auto Order • nearby staff detected");
                orderCooldown = KhanhFeatures.number("auto_order", "delay", 10) * 20;
            }
            break;
        }
    }

    public static void runOrder() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null && client.getNetworkHandler() != null) {
            client.getNetworkHandler().sendChatCommand("order");
            actionBar(client, "Auto Order • command sent");
        }
    }

    private static void tickFastPlace(MinecraftClient client) {
        if (!KhanhFeatures.enabled("fast_place")) {
            return;
        }
        if (KhanhFeatures.bool("fast_place", "blocks_only", false)
            && !(client.player.getMainHandStack().getItem() instanceof BlockItem)
            && !(client.player.getOffHandStack().getItem() instanceof BlockItem)) {
            return;
        }
        MinecraftClientAccessor accessor = (MinecraftClientAccessor) client;
        // XP bottles: extra throws each tick while right-click is held.
        if (KhanhFeatures.bool("fast_place", "fast_xp", true) && client.currentScreen == null && client.options.useKey.isPressed()
            && client.player.getMainHandStack().isOf(net.minecraft.item.Items.EXPERIENCE_BOTTLE)) {
            int extra = KhanhFeatures.number("fast_place", "xp_per_tick", 2) - 1;
            for (int i = 0; i < extra && client.player.getMainHandStack().isOf(net.minecraft.item.Items.EXPERIENCE_BOTTLE); i++) {
                accessor.khanh$doItemUse();
            }
        }
        int delay = KhanhFeatures.number("fast_place", "delay", 0);
        if (accessor.khanh$getItemUseCooldown() > delay) {
            accessor.khanh$setItemUseCooldown(delay);
        }
    }

    public static void actionBar(MinecraftClient client, String message) {
        if (client.player != null) {
            client.player.sendMessage(Text.literal("[Khanh Client] " + message), true);
        }
    }
}
