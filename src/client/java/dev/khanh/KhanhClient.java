package dev.khanh;

import dev.khanh.config.ConfigManager;
import dev.khanh.feature.FreecamService;
import dev.khanh.feature.KhanhFeatures;
import dev.khanh.feature.KhanhSounds;
import dev.khanh.gui.GlassGuiRenderer;
import dev.khanh.gui.LiquidGlass;
import dev.khanh.gui.KhanhClickGuiScreen;
import dev.khanh.gui.KhanhClickGui;
import dev.khanh.gui.KhanhConfigScreen;
import dev.khanh.module.ModuleManager;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class KhanhClient implements ClientModInitializer {
    public static final String MOD_ID = "khanh";
    public static final String NAME = "Khanh Client";
    public static final String VERSION = net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer(MOD_ID)
        .map(container -> container.getMetadata().getVersion().getFriendlyString()).orElse("dev");
    public static final Logger LOGGER = LoggerFactory.getLogger(NAME);

    private static final ModuleManager MODULES = new ModuleManager();
    private static boolean rightShiftDown;
    private static boolean shadersWarmed;
    private static boolean freecamKeyDown;
    private static final Map<String, Boolean> MODULE_KEY_STATES = new HashMap<>();
    private static final Map<String, Boolean> ACTION_KEY_STATES = new HashMap<>();

    public static ModuleManager modules() {
        return MODULES;
    }

    @Override
    public void onInitializeClient() {
        ConfigManager.load(MODULES);
        KhanhFeatures.register();

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            DevShots.tick(client);
            if (!shadersWarmed && client.getOverlay() == null
                && (client.currentScreen instanceof net.minecraft.client.gui.screen.TitleScreen
                    || client.currentScreen instanceof dev.khanh.gui.KhanhTitleScreen || client.world != null)) {
                shadersWarmed = true;
                LiquidGlass.ready();
                GlassGuiRenderer.warmUp();
            }
            boolean pressed = GLFW.glfwGetKey(client.getWindow().getHandle(), GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
            // Not while Minecraft is still loading: models and textures aren't ready yet.
            if (pressed && !rightShiftDown && !(client.currentScreen instanceof KhanhClickGui) && client.getOverlay() == null) {
                client.setScreen(KhanhClickGui.create(client.currentScreen));
            }
            rightShiftDown = pressed;
            boolean freecamPressed = GLFW.glfwGetKey(client.getWindow().getHandle(), GLFW.GLFW_KEY_F6) == GLFW.GLFW_PRESS;
            if (freecamPressed && !freecamKeyDown && client.currentScreen == null
                && MODULES.byId("freecam") != null
                && MODULES.byId("freecam").keybind() != GLFW.GLFW_KEY_F6) {
                FreecamService.toggle();
            }
            freecamKeyDown = freecamPressed;
            for (dev.khanh.module.Module module : MODULES.all()) {
                int key = module.keybind();
                boolean modulePressed = key >= 0
                    && GLFW.glfwGetKey(client.getWindow().getHandle(), key) == GLFW.GLFW_PRESS;
                boolean wasPressed = MODULE_KEY_STATES.getOrDefault(module.id(), false);
                if (module.id().equals("zoom") && key >= 0 && dev.khanh.feature.KhanhFeatures.bool("zoom", "hold", true)) {
                    // Hold to Zoom: on while the key is held, off the moment you let go.
                    boolean want = modulePressed && client.currentScreen == null;
                    if (module.enabled() != want && (modulePressed != wasPressed || !modulePressed)) module.setEnabled(want);
                } else if (modulePressed && !wasPressed && client.currentScreen == null) {
                    if (module.id().equals("config")) {
                        client.setScreen(new KhanhConfigScreen(client.currentScreen));
                        KhanhSounds.playInterface(client, 1.08F);
                    } else {
                        module.toggle();
                        ConfigManager.save(MODULES);
                        KhanhSounds.playInterface(client, module.enabled() ? 1.34F : 0.82F);
                        if (client.player != null && !module.id().equals("freecam")) {
                            client.player.sendMessage(Text.literal("[Khanh Client] " + module.name() + " "
                                + (module.enabled() ? "enabled" : "disabled")), true);
                        }
                    }
                }
                MODULE_KEY_STATES.put(module.id(), modulePressed);
                for (dev.khanh.module.ModuleOption option : module.options()) {
                    if (option.kind() != dev.khanh.module.ModuleOption.Kind.ACTION) {
                        continue;
                    }
                    int actionKey = option.actionKeybind();
                    String actionId = module.id() + ':' + option.id();
                    boolean actionPressed = actionKey >= 0
                        && GLFW.glfwGetKey(client.getWindow().getHandle(), actionKey) == GLFW.GLFW_PRESS;
                    boolean actionWasPressed = ACTION_KEY_STATES.getOrDefault(actionId, false);
                    if (actionPressed && !actionWasPressed && client.currentScreen == null) {
                        option.trigger();
                        KhanhSounds.playInterface(client, 1.24F);
                        if (client.player != null) {
                            client.player.sendMessage(Text.literal("[Khanh Client] RUN • " + option.label()), true);
                        }
                    }
                    ACTION_KEY_STATES.put(actionId, actionPressed);
                }
            }
            KhanhFeatures.tick(client);
        });

        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            KhanhFeatures.shutdown(client);
            ConfigManager.save(MODULES);
        });
        LOGGER.info("{} {} initialized", NAME, VERSION);
    }

}
