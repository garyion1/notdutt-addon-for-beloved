package dev.khanh.feature;

import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFW;

/** Client-only input samples for the CPS and keystrokes widgets. */
public final class InputMetrics {
    private static final long[] LEFT = new long[128];
    private static final long[] RIGHT = new long[128];
    private static int leftIndex;
    private static int rightIndex;

    private InputMetrics() { }

    public static void press(long window, int button) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.getWindow().getHandle() != window || client.currentScreen != null || client.world == null) return;
        long now = System.nanoTime();
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) LEFT[leftIndex++ & 127] = now;
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) RIGHT[rightIndex++ & 127] = now;
    }

    public static int cps(boolean right) {
        long[] samples = right ? RIGHT : LEFT;
        long recent = System.nanoTime() - 1_000_000_000L;
        int count = 0;
        for (long sample : samples) if (sample > recent) count++;
        return count;
    }

    public static boolean key(int code) {
        MinecraftClient client = MinecraftClient.getInstance();
        return client.currentScreen == null && client.world != null
            && GLFW.glfwGetKey(client.getWindow().getHandle(), code) == GLFW.GLFW_PRESS;
    }

    public static boolean mouse(int code) {
        MinecraftClient client = MinecraftClient.getInstance();
        return client.currentScreen == null && client.world != null
            && GLFW.glfwGetMouseButton(client.getWindow().getHandle(), code) == GLFW.GLFW_PRESS;
    }
}
