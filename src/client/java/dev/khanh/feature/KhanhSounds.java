package dev.khanh.feature;

import dev.khanh.KhanhClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

/** Short CC0 interface cues bundled with Khanh Client instead of Minecraft sound events. */
public final class KhanhSounds {
    private static final SoundEvent UI_HOVER = sound("ui_hover");
    private static final SoundEvent UI_BACK = sound("ui_back");
    private static final SoundEvent UI_SELECT = sound("ui_select");
    private static final SoundEvent UI_OPEN = sound("ui_open");
    private static final SoundEvent UI_TOGGLE = sound("ui_toggle");
    private static final SoundEvent NOTIFY = sound("notify");
    private static final SoundEvent WARNING = sound("warning");
    private static final SoundEvent ALERT = sound("alert");
    private static final SoundEvent KEY_CREAMY = sound("key_creamy");
    private static final SoundEvent KEY_SOFT = sound("key_soft");
    private static final SoundEvent KEY_DEEP = sound("key_deep");
    private static long lastKeySoundNanos;

    private KhanhSounds() {
    }

    public static void playInterface(MinecraftClient client, float cue) {
        if (KhanhFeatures.enabled("client_sounds") && KhanhFeatures.bool("client_sounds", "muted", false)) return;
        if (cue <= 0.74F) {
            play(client, UI_HOVER, 0.15F, 1.0F);
        } else if (cue < 0.90F) {
            play(client, UI_BACK, 0.20F, 1.0F);
        } else if (cue < 1.13F) {
            play(client, UI_SELECT, 0.22F, 1.0F);
        } else if (cue < 1.27F) {
            play(client, UI_OPEN, 0.25F, 1.0F);
        } else {
            play(client, UI_TOGGLE, 0.27F, 1.0F);
        }
    }

    /** Module switches: a bright rising click turning on, a soft low one turning off. */
    public static void toggle(MinecraftClient client, boolean on) {
        if (KhanhFeatures.enabled("client_sounds") && KhanhFeatures.bool("client_sounds", "muted", false)) return;
        play(client, UI_TOGGLE, 0.27F, on ? 1.16F : 0.80F);
    }

    public static void notify(MinecraftClient client) {
        play(client, NOTIFY, 0.55F, 1.0F);
    }

    public static void warning(MinecraftClient client) {
        play(client, WARNING, 0.48F, 1.0F);
    }

    public static void alert(MinecraftClient client) {
        alert(client, 1.0F);
    }

    public static void alert(MinecraftClient client, float pitch) {
        play(client, ALERT, 0.46F, Math.max(0.72F, Math.min(1.35F, pitch)));
    }


    public static void creamyKey(MinecraftClient client) {
        long now = System.nanoTime();
        if (now - lastKeySoundNanos < 22_000_000L) return;
        lastKeySoundNanos = now;
        SoundEvent sound = switch (KhanhFeatures.choice("creamy_keys", "sound", "Creamy")) {
            case "Soft" -> KEY_SOFT;
            case "Deep" -> KEY_DEEP;
            default -> KEY_CREAMY;
        };
        // One sound is capped at full volume, so louder settings layer a second copy on top.
        float volume = (float) KhanhFeatures.decimal("creamy_keys", "volume", 1.0);
        play(client, sound, Math.min(1.0F, volume), 1.0F);
        if (volume > 1.0F) play(client, sound, Math.min(1.0F, volume - 1.0F), 1.0F);
    }

    private static void play(MinecraftClient client, SoundEvent sound, float volume, float pitch) {
        if (client != null) {
            client.getSoundManager().play(PositionedSoundInstance.ui(sound, pitch, volume));
        }
    }

    private static SoundEvent sound(String path) {
        return SoundEvent.of(Identifier.of(KhanhClient.MOD_ID, path));
    }
}
