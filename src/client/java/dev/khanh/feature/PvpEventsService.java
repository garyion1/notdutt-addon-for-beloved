package dev.khanh.feature;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;

/**
 * Pop Counter and Kill Effects. Both only read what the server already sends (totem pops and
 * deaths) and draw things on your screen; nothing is ever sent back.
 */
public final class PvpEventsService {
    private static final Map<String, Integer> POPS = new HashMap<>();
    private static final Map<Integer, Long> HIT_AT = new HashMap<>();
    private static Object lastWorld;

    private PvpEventsService() { }

    public static void tick(MinecraftClient client) {
        if (client.world != lastWorld) {
            lastWorld = client.world;
            POPS.clear();
            HIT_AT.clear();
        }
    }

    /** You hit someone: Kill Effects counts their death as yours for the next few seconds. */
    public static void onAttack(Entity entity) {
        if (entity instanceof PlayerEntity) HIT_AT.put(entity.getId(), System.currentTimeMillis());
    }

    /** Status 35: a totem popped. */
    public static void onTotemPop(Entity entity) {
        if (!(entity instanceof PlayerEntity player) || !KhanhFeatures.enabled("pop_counter")) return;
        MinecraftClient client = MinecraftClient.getInstance();
        boolean self = player == client.player;
        if (self && !KhanhFeatures.bool("pop_counter", "self", false)) return;
        String name = player.getGameProfile().name();
        int count = POPS.merge(name, 1, Integer::sum);
        if (KhanhFeatures.bool("pop_counter", "chat", true)) {
            send(client, Text.literal(name).withColor(0xFFFFFF)
                .append(Text.literal(" popped ").withColor(0xA0A0A0))
                .append(Text.literal(count + (count == 1 ? " totem" : " totems")).withColor(0xFFD84A)));
        }
        if (KhanhFeatures.bool("pop_counter", "sound", false) && !self) KhanhSounds.notify(client);
    }

    /** Status 3: a living entity died. */
    public static void onDeath(Entity entity) {
        if (!(entity instanceof PlayerEntity player)) return;
        MinecraftClient client = MinecraftClient.getInstance();
        String name = player.getGameProfile().name();
        Integer pops = POPS.remove(name);
        if (KhanhFeatures.enabled("pop_counter") && KhanhFeatures.bool("pop_counter", "death_message", true)
            && player != client.player && pops != null && pops > 0) {
            send(client, Text.literal(name).withColor(0xFFFFFF)
                .append(Text.literal(" died after popping ").withColor(0xA0A0A0))
                .append(Text.literal(pops + (pops == 1 ? " totem" : " totems")).withColor(0xFF5555)));
        }
        Long hit = HIT_AT.remove(player.getId());
        boolean mine = hit != null && System.currentTimeMillis() - hit < 10_000L;
        if (KhanhFeatures.enabled("kill_effects") && player != client.player
            && (mine || !KhanhFeatures.bool("kill_effects", "only_my_kills", true))) {
            killEffect(client, player);
        }
    }

    private static void killEffect(MinecraftClient client, PlayerEntity victim) {
        ClientWorld world = client.world;
        if (world == null) return;
        double x = victim.getX(), y = victim.getY(), z = victim.getZ();
        switch (KhanhFeatures.choice("kill_effects", "effect", "Lightning")) {
            case "Totem Burst" -> {
                for (int i = 0; i < 60; i++) world.addParticleClient(ParticleTypes.TOTEM_OF_UNDYING, x, y + 1.0, z,
                    (world.random.nextDouble() - 0.5) * 1.2, world.random.nextDouble() * 0.9, (world.random.nextDouble() - 0.5) * 1.2);
            }
            case "Flames" -> {
                for (int i = 0; i < 50; i++) world.addParticleClient(ParticleTypes.SOUL_FIRE_FLAME, x, y + world.random.nextDouble() * 2.0, z,
                    (world.random.nextDouble() - 0.5) * 0.3, 0.05 + world.random.nextDouble() * 0.15, (world.random.nextDouble() - 0.5) * 0.3);
            }
            case "Hearts" -> {
                for (int i = 0; i < 18; i++) world.addParticleClient(ParticleTypes.HEART, x + (world.random.nextDouble() - 0.5),
                    y + 1.0 + world.random.nextDouble(), z + (world.random.nextDouble() - 0.5), 0.0, 0.1, 0.0);
            }
            default -> {
                LightningEntity bolt = EntityType.LIGHTNING_BOLT.create(world, SpawnReason.TRIGGERED);
                if (bolt != null) {
                    bolt.refreshPositionAfterTeleport(x, y, z);
                    bolt.setCosmetic(true);
                    world.addEntity(bolt);
                }
            }
        }
        if (KhanhFeatures.bool("kill_effects", "message", false)) {
            client.inGameHud.setOverlayMessage(Text.literal("☠ You killed " + victim.getGameProfile().name()).withColor(0xFF5555), false);
        }
    }

    /** Clears the pop count for someone, e.g. when you start a new fight. */
    public static int pops(String name) {
        return POPS.getOrDefault(name, 0);
    }

    private static void send(MinecraftClient client, MutableText text) {
        if (client.player == null) return;
        client.player.sendMessage(Text.literal("[Khanh] ").withColor(0x8A8AFF).append(text), false);
    }
}
