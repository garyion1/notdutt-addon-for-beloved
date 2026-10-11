package dev.khanh.feature;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;

/** Hit Effects: a burst of particles and an optional sound on every hit you land (only you see/hear it). */
public final class HitEffects {
    private HitEffects() { }

    public static void tick(MinecraftClient client) { }

    public static void onAttack(Entity target) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!KhanhFeatures.enabled("hit_effects") || client.world == null) return;
        if (KhanhFeatures.bool("hit_effects", "players_only", false) && !(target instanceof PlayerEntity)) return;
        ParticleEffect particle = switch (KhanhFeatures.choice("hit_effects", "particles", "Sparks")) {
            case "Crits" -> ParticleTypes.ENCHANTED_HIT;
            case "Blood" -> new DustParticleEffect(0xB0101A, 1.3F);
            case "Hearts" -> ParticleTypes.HEART;
            case "Souls" -> ParticleTypes.SOUL;
            case "Off" -> null;
            default -> ParticleTypes.ELECTRIC_SPARK;
        };
        if (particle != null) {
            var random = client.world.random;
            int amount = KhanhFeatures.number("hit_effects", "amount", 12);
            double x = target.getX(), y = target.getY() + target.getHeight() * 0.6, z = target.getZ();
            for (int i = 0; i < amount; i++) {
                client.world.addParticleClient(particle, x + (random.nextDouble() - 0.5) * target.getWidth(),
                    y + (random.nextDouble() - 0.5) * target.getHeight() * 0.6, z + (random.nextDouble() - 0.5) * target.getWidth(),
                    (random.nextDouble() - 0.5) * 0.5, random.nextDouble() * 0.3, (random.nextDouble() - 0.5) * 0.5);
            }
        }
        SoundEvent sound = switch (KhanhFeatures.choice("hit_effects", "sound", "Off")) {
            case "Crit" -> SoundEvents.ENTITY_PLAYER_ATTACK_CRIT;
            case "Ding" -> SoundEvents.BLOCK_NOTE_BLOCK_PLING.value();
            case "Bell" -> SoundEvents.BLOCK_BELL_USE;
            case "Bass" -> SoundEvents.BLOCK_NOTE_BLOCK_BASS.value();
            default -> null;
        };
        if (sound != null) {
            float volume = (float) KhanhFeatures.decimal("hit_effects", "volume", 0.7);
            client.getSoundManager().play(PositionedSoundInstance.ui(sound, 1.0F + client.world.random.nextFloat() * 0.15F, volume));
        }
    }
}
