package dev.khanh.feature;

import dev.khanh.KhanhClient;
import dev.khanh.gui.KhanhClickGuiScreen;
import dev.khanh.gui.SmoothGuiShapes;
import java.util.Random;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.joml.Matrix3x2fStack;
import org.lwjgl.glfw.GLFW;

/**
 * The cinematic launch intro: on black, with drifting dust and slow light rays, a prize wheel of
 * client name cards fades in and spins, ticking past each card, slows, overshoots and settles on the
 * Khanh logo. A flash, a shockwave and a camera shake land it; the wheel zooms away, the logo flies
 * to the centre and KHANH CLIENT slams in letter by letter, then everything fades into the menu.
 * Plays once per launch; any key or click skips it.
 */
public final class IntroOverlay {
    private static final Identifier LOGO = Identifier.of(KhanhClient.MOD_ID, "textures/gui/khanh-logo.png");
    private static final String[] RIVALS = {
        "METEOR", "LUNAR", "BADLION", "FEATHER", "WURST", "FUTURE", "RUSHERHACK", "THUNDER", "LIQUIDBOUNCE", "IMPACT", "ANUBIS"
    };
    private static final int[] TINTS = {
        0xFF7C3AED, 0xFF2563EB, 0xFFDC2626, 0xFF0EA5E9, 0xFF16A34A, 0xFFF59E0B, 0xFFEF4444, 0xFF8B5CF6, 0xFF14B8A6, 0xFFE11D48, 0xFFD4A017
    };
    private static final int SEGMENTS = RIVALS.length + 1;   // the last one is Khanh
    private static final float SPIN_START = 0.55F, SPIN_LENGTH = 4.1F, LAND = SPIN_START + SPIN_LENGTH;
    private static final float REVEAL = LAND + 0.55F, END = REVEAL + 2.6F;

    private static long startAt = -1L;
    private static boolean started, clockStarted, whooshed, landed, revealed;
    private static int lastTick = Integer.MIN_VALUE;
    private static float skipFrom = -1.0F;
    private static final float[][] DUST = new float[90][4];

    private IntroOverlay() { }

    /** True while the intro is on screen. */
    public static boolean playing() {
        return startAt >= 0L;
    }

    /** True while the intro should draw over the title screen; starts it on the first call. */
    public static boolean wantsTitleScreen() {
        if (!started) {
            started = true;
            if (!KhanhFeatures.enabled("intro")) return false;
            Random random = new Random();
            for (float[] dust : DUST) {
                dust[0] = random.nextFloat(); dust[1] = random.nextFloat();
                dust[2] = 0.3F + random.nextFloat() * 1.4F; dust[3] = random.nextFloat() * 6.28F;
            }
            startAt = System.nanoTime();
        }
        return startAt >= 0L;
    }

    public static void render(DrawContext context) {
        if (startAt < 0L) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.getOverlay() != null || !clockStarted) {
            // Still under Mojang's loading screen: stay black, start the show the moment it lifts.
            context.createNewRootLayer();
            context.fill(0, 0, context.getScaledWindowWidth(), context.getScaledWindowHeight(), 0xFF030306);
            if (client.getOverlay() != null) return;
            clockStarted = true;
            startAt = System.nanoTime();
            if (KhanhFeatures.bool("intro", "sound", true)) {
                client.getSoundManager().play(PositionedSoundInstance.ui(
                    SoundEvent.of(Identifier.of(KhanhClient.MOD_ID, "intro_theme")), 1.0F, 1.0F));   // full volume
            }
        }
        float t = (System.nanoTime() - startAt) / 1_000_000_000.0F;
        // Any key or click: jump to a quick fade-out.
        long window = client.getWindow().getHandle();
        if (skipFrom < 0.0F && t > 0.3F && (GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS
            || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_ESCAPE) == GLFW.GLFW_PRESS || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_SPACE) == GLFW.GLFW_PRESS
            || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_ENTER) == GLFW.GLFW_PRESS)) {
            skipFrom = t;
        }
        float fadeOut = skipFrom >= 0.0F ? clamp((t - skipFrom) / 0.45F) : clamp((t - (END - 0.7F)) / 0.7F);
        if (fadeOut >= 1.0F) {
            startAt = -1L;
            return;
        }
        boolean sound = KhanhFeatures.bool("intro", "sound", true);

        context.createNewRootLayer();
        int width = context.getScaledWindowWidth(), height = context.getScaledWindowHeight();
        float cover = 1.0F - fadeOut;
        int accent = VisualTheme.preset();
        context.fillGradient(0, 0, width, height, a(cover) << 24 | 0x030306, a(cover) << 24 | 0x07080D);

        Matrix3x2fStack m = context.getMatrices();
        float cx = width / 2.0F, cy = height / 2.0F + height * 0.03F;
        float radius = Math.min(width, height) * 0.40F;

        // Camera shake right after the landing.
        float shake = t > LAND && t < LAND + 0.35F ? (1.0F - (t - LAND) / 0.35F) * 5.0F : 0.0F;
        m.pushMatrix();
        m.translate((float) Math.sin(t * 91.0) * shake, (float) Math.cos(t * 77.0) * shake);

        drawRays(context, cx, cy, radius, t, cover, accent);
        drawDust(context, width, height, t, cover, accent);

        // Spin: ease-out with a small overshoot, landing the Khanh card under the pointer.
        float segment = 360.0F / SEGMENTS;
        float target = 360.0F * 6.0F - segment * (SEGMENTS - 1);
        float spin = clamp((t - SPIN_START) / SPIN_LENGTH);
        float angle = target * easeOutBack(spin) - 18.0F * clamp(1.0F - t / SPIN_START);
        float speed = target * (easeOutBack(clamp((t + 0.016F - SPIN_START) / SPIN_LENGTH)) - easeOutBack(spin));

        // Tick each time a card passes the pointer.
        int tick = (int) Math.floor((angle + segment / 2.0F) / segment);
        if (tick != lastTick && t > SPIN_START && t < LAND + 0.1F) {
            if (lastTick != Integer.MIN_VALUE && sound) play(client, SoundEvents.UI_BUTTON_CLICK.value(), 1.4F + 0.6F * spin, 0.12F);
            lastTick = tick;
        }
        if (!whooshed && t > SPIN_START - 0.1F) {
            whooshed = true;
            // the intro song plays from the start; no extra whoosh
        }
        if (!landed && t >= LAND) {
            landed = true;
            if (sound) {
                play(client, SoundEvents.ENTITY_GENERIC_EXPLODE.value(), 1.8F, 0.18F);
            }
        }

        // The wheel: fades/scales in, then zooms away for the reveal.
        float wheelIn = easeOutCubic(clamp(t / 0.6F));
        float zoom = easeInCubic(clamp((t - REVEAL + 0.25F) / 0.6F));
        float wheelAlpha = cover * wheelIn * (1.0F - zoom);
        if (wheelAlpha > 0.01F) {
            m.pushMatrix();
            m.translate(cx, cy);
            float scale = (0.82F + 0.18F * wheelIn) * (1.0F + zoom * 1.8F);
            m.scale(scale, scale);
            // Motion blur: faint copies trailing behind while it spins fast.
            if (speed > 2.0F) {
                for (int ghost = 2; ghost >= 1; ghost--) drawWheel(context, radius, angle - speed * ghost * 0.9F, wheelAlpha * 0.18F, accent, t, false);
            }
            drawWheel(context, radius, angle, wheelAlpha, accent, t, t > LAND);
            m.popMatrix();
            drawPointer(context, cx, cy - radius - 4.0F, wheelAlpha, accent, t);
        }

        // Landing flash and shockwave.
        if (t > LAND && t < LAND + 0.9F) {
            float k = (t - LAND) / 0.9F;
            int ring = Math.round(radius * (0.2F + k * 2.2F));
            SmoothGuiShapes.rect(context, Math.round(cx) - ring, Math.round(cy) - ring, ring * 2, ring * 2, ring,
                a(cover * (1.0F - k) * 0.85F) << 24 | (accent & 0xFFFFFF), true);
            float flash = clamp(1.0F - k * 3.0F);
            if (flash > 0.0F) context.fill(0, 0, width, height, a(flash * 0.55F * cover) << 24 | 0xFFFFFF);
        }

        // Reveal: the logo flies to the centre and grows, then the title slams in.
        float logoIn = easeOutBack(clamp((t - REVEAL + 0.35F) / 0.7F));
        if (logoIn > 0.0F) {
            float size = Math.min(width, height) * 0.24F * logoIn;
            float fly = 1.0F - easeOutCubic(clamp((t - REVEAL + 0.35F) / 0.7F));
            float lx = cx, ly = cy - radius * 0.62F * fly - height * 0.06F * (1.0F - fly);
            float pulse = 1.0F + 0.03F * (float) Math.sin(t * 6.0);
            glow(context, lx, ly, size * 0.9F * pulse, accent, cover * 0.55F);
            m.pushMatrix();
            m.translate(lx, ly);
            m.scale(size * pulse / 64.0F, size * pulse / 64.0F);
            context.drawTexture(RenderPipelines.GUI_TEXTURED, LOGO, -32, -32, 0F, 0F, 64, 64, 64, 64, 64, 64, a(cover) << 24 | 0xFFFFFF);
            m.popMatrix();
            drawTitle(context, client.textRenderer, cx, ly + size * 0.62F + 6.0F, t - REVEAL - 0.25F, cover, accent);
        }
        if (!revealed && t > REVEAL) {
            revealed = true;
            if (sound) play(client, SoundEvents.BLOCK_BEACON_ACTIVATE, 1.5F, 0.25F);
        }

        m.popMatrix();
        // Letterbox bars for the film look.
        int bar = Math.round(height * 0.07F * cover);
        context.fill(0, 0, width, bar, a(cover) << 24);
        context.fill(0, height - bar, width, height, a(cover) << 24);
        if (t > 1.0F && skipFrom < 0.0F) {
            Text hint = KhanhClickGuiScreen.fontText("Click to skip");
            context.drawText(client.textRenderer, hint, width - client.textRenderer.getWidth(hint) - 10, height - bar / 2 - 4,
                a(cover * 0.45F) << 24 | 0xFFFFFF, false);
        }
    }

    private static void drawWheel(DrawContext context, float radius, float angle, float alpha, int accent, float t, boolean landedGlow) {
        Matrix3x2fStack m = context.getMatrices();
        int r = Math.round(radius);
        // Rim: accent halo, metal ring, dark face.
        SmoothGuiShapes.circle(context, 0, 0, r + 9, a(alpha * 0.35F) << 24 | (accent & 0xFFFFFF));
        SmoothGuiShapes.circle(context, 0, 0, r + 5, a(alpha) << 24 | 0x2A2D36);
        SmoothGuiShapes.circle(context, 0, 0, r, a(alpha) << 24 | 0x0E1016);
        SmoothGuiShapes.circle(context, 0, 0, Math.round(r * 0.96F), a(alpha) << 24 | 0x12141B);
        float segment = 360.0F / SEGMENTS;
        TextRenderer font = MinecraftClient.getInstance().textRenderer;
        for (int i = 0; i < SEGMENTS; i++) {
            float at = angle + i * segment;
            // Spoke and rim peg between cards.
            m.pushMatrix();
            m.rotate((float) Math.toRadians(at + segment / 2.0F));
            context.fill(-1, -r + 2, 1, -Math.round(r * 0.27F), a(alpha * 0.5F) << 24 | 0x3A3E4A);
            SmoothGuiShapes.circle(context, 0, -r + 1, 3, a(alpha) << 24 | 0xD9DEE8);
            m.popMatrix();
            // The card, reading along the rim.
            m.pushMatrix();
            m.rotate((float) Math.toRadians(at));
            m.translate(0.0F, -radius * 0.62F);
            // Cards point outward like a prize wheel's labels, so neighbours never overlap.
            m.rotate((float) Math.toRadians(-90.0));
            boolean khanh = i == SEGMENTS - 1;
            int w = Math.round(radius * 0.58F), h = Math.round(radius * 0.24F);
            int tint = khanh ? accent : TINTS[i];
            if (khanh && landedGlow) {
                float pulse = 0.6F + 0.4F * (float) Math.sin(t * 9.0);
                SmoothGuiShapes.rect(context, -w / 2 - 6, -h / 2 - 6, w + 12, h + 12, 10, a(alpha * pulse * 0.6F) << 24 | (accent & 0xFFFFFF), false);
            }
            SmoothGuiShapes.rect(context, -w / 2, -h / 2, w, h, 7, a(alpha) << 24 | 0x181B24, false);
            SmoothGuiShapes.rect(context, w / 2 - 4, -h / 2, 4, h, 2, a(alpha) << 24 | (tint & 0xFFFFFF), false);
            SmoothGuiShapes.rect(context, -w / 2, -h / 2, w, h, 7, a(alpha * 0.35F) << 24 | (tint & 0xFFFFFF), true);
            if (khanh) {
                int icon = Math.round(h * 0.86F);
                m.pushMatrix();
                m.rotate((float) Math.toRadians(90.0));
                context.drawTexture(RenderPipelines.GUI_TEXTURED, LOGO, -icon / 2, -icon / 2, 0F, 0F, icon, icon, icon, icon, icon, icon,
                    a(alpha) << 24 | 0xFFFFFF);
                m.popMatrix();
            } else {
                Text name = KhanhClickGuiScreen.fontText(RIVALS[i]);
                float scale = Math.min(1.0F, (w - 10.0F) / Math.max(1, font.getWidth(name)));
                m.pushMatrix();
                m.scale(scale, scale);
                context.drawText(font, name, -font.getWidth(name) / 2, -3, a(alpha * 0.92F) << 24 | 0xE8EAF0, false);
                m.popMatrix();
            }
            m.popMatrix();
        }
        // Hub with a small logo.
        int hub = Math.round(r * 0.24F);
        SmoothGuiShapes.circle(context, 0, 0, hub + 3, a(alpha) << 24 | 0x2A2D36);
        SmoothGuiShapes.circle(context, 0, 0, hub, a(alpha) << 24 | 0x0B0C10);
        int icon = Math.round(hub * 1.3F);
        context.drawTexture(RenderPipelines.GUI_TEXTURED, LOGO, -icon / 2, -icon / 2, 0F, 0F, icon, icon, icon, icon, icon, icon,
            a(alpha * 0.9F) << 24 | 0xFFFFFF);
    }

    /** The pointer at the top: a glowing arrow that kicks when the wheel lands. */
    private static void drawPointer(DrawContext context, float x, float y, float alpha, int accent, float t) {
        Matrix3x2fStack m = context.getMatrices();
        float kick = t > LAND && t < LAND + 0.3F ? (float) Math.sin((t - LAND) / 0.3F * Math.PI) * 5.0F : 0.0F;
        TextRenderer font = MinecraftClient.getInstance().textRenderer;
        glow(context, x, y + 6, 18, accent, alpha * 0.5F);
        m.pushMatrix();
        m.translate(x, y - kick);
        m.scale(3.2F, 3.2F);
        context.drawText(font, "▼", -font.getWidth("▼") / 2, -4, a(alpha) << 24 | (accent & 0xFFFFFF), false);
        m.popMatrix();
    }

    /** KHANH CLIENT: letters slam down one after another, then a thin line and the version slide in. */
    private static void drawTitle(DrawContext context, TextRenderer font, float cx, float y, float t, float cover, int accent) {
        Matrix3x2fStack m = context.getMatrices();
        String title = "KHANH CLIENT";
        float scale = 2.6F, spacing = 2.0F;
        float total = 0.0F;
        for (int i = 0; i < title.length(); i++) total += font.getWidth(KhanhClickGuiScreen.fontText(String.valueOf(title.charAt(i)))) * scale + spacing;
        float x = cx - total / 2.0F;
        int split = title.indexOf(' ');
        for (int i = 0; i < title.length(); i++) {
            Text letter = KhanhClickGuiScreen.fontText(String.valueOf(title.charAt(i)));
            float w = font.getWidth(letter) * scale;
            float k = easeOutBack(clamp((t - 0.05F - i * 0.045F) / 0.35F));
            if (k > 0.0F) {
                m.pushMatrix();
                m.translate(x + w / 2.0F, y + (1.0F - k) * -26.0F);
                float s = scale * (1.4F - 0.4F * k);
                m.scale(s, s);
                int colour = split < 0 || i < split ? 0xFFFFFF : accent & 0xFFFFFF;
                context.drawText(font, letter, -font.getWidth(letter) / 2, 0, a(cover * clamp(k)) << 24 | colour, false);
                m.popMatrix();
            }
            x += w + spacing;
        }
        float line = easeOutCubic(clamp((t - 0.6F) / 0.5F));
        if (line > 0.0F) {
            int half = Math.round(total * 0.42F * line);
            int lineY = Math.round(y + 30);
            context.fill(Math.round(cx) - half, lineY, Math.round(cx) + half, lineY + 1, a(cover * 0.8F) << 24 | (accent & 0xFFFFFF));
            Text version = KhanhClickGuiScreen.fontText("v" + KhanhClient.VERSION + "  ·  FABRIC 1.21.11");
            context.drawText(font, version, Math.round(cx) - font.getWidth(version) / 2, lineY + 6, a(cover * line * 0.7F) << 24 | 0xC9CDD8, false);
        }
    }

    /** Slow rotating light rays behind the wheel. */
    private static void drawRays(DrawContext context, float cx, float cy, float radius, float t, float alpha, int accent) {
        Matrix3x2fStack m = context.getMatrices();
        float strength = clamp(t / 1.0F) * alpha;
        for (int i = 0; i < 14; i++) {
            m.pushMatrix();
            m.translate(cx, cy);
            m.rotate((float) Math.toRadians(i * (360.0 / 14) + t * 6.0));
            int length = Math.round(radius * 2.6F);
            int w = 10 + (i % 3) * 8;
            context.fillGradient(-w / 2, -length, w / 2, 0, 0, a(strength * 0.07F) << 24 | (accent & 0xFFFFFF));
            m.popMatrix();
        }
    }

    /** Tiny glowing dust drifting up through the scene. */
    private static void drawDust(DrawContext context, int width, int height, float t, float alpha, int accent) {
        for (float[] dust : DUST) {
            float x = dust[0] * width + (float) Math.sin(t * 0.7 + dust[3]) * 14.0F;
            float y = ((dust[1] - t * 0.04F * dust[2]) % 1.0F + 1.0F) % 1.0F * height;
            float twinkle = 0.4F + 0.6F * (float) Math.abs(Math.sin(t * 1.3 + dust[3]));
            int colour = dust[2] > 1.2F ? accent & 0xFFFFFF : 0xFFFFFF;
            SmoothGuiShapes.circle(context, Math.round(x), Math.round(y), dust[2] > 1.0F ? 2 : 1, a(alpha * twinkle * 0.5F) << 24 | colour);
        }
    }

    private static void glow(DrawContext context, float x, float y, float size, int colour, float alpha) {
        for (int i = 4; i >= 1; i--) {
            int r = Math.round(size * (0.5F + i * 0.18F));
            SmoothGuiShapes.circle(context, Math.round(x), Math.round(y), r, a(alpha * 0.12F) << 24 | (colour & 0xFFFFFF));
        }
    }

    private static void play(MinecraftClient client, SoundEvent sound, float pitch, float volume) {
        client.getSoundManager().play(PositionedSoundInstance.ui(sound, pitch, volume));
    }

    private static int a(float alpha) {
        return Math.round(clamp(alpha) * 255.0F);
    }

    private static float clamp(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }

    private static float easeOutCubic(float x) {
        return 1.0F - (float) Math.pow(1.0F - x, 3.0);
    }

    private static float easeInCubic(float x) {
        return x * x * x;
    }

    /** Ease-out that runs a touch past the end and settles back: the wheel's overshoot. */
    private static float easeOutBack(float x) {
        float c1 = 1.20F, c3 = c1 + 1.0F;
        return 1.0F + c3 * (float) Math.pow(x - 1.0F, 3.0) + c1 * (float) Math.pow(x - 1.0F, 2.0);
    }
}
