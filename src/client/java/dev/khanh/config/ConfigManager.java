package dev.khanh.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.khanh.feature.FriendService;
import dev.khanh.gui.HudLayout;
import dev.khanh.module.Module;
import dev.khanh.module.ModuleManager;
import dev.khanh.module.ModuleOption;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

public final class ConfigManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("khanh-client.json");
    private static final Path LEGACY_CONFIG_PATH = CONFIG_PATH.getParent().resolve("zerofire-client.json");
    private static final Path CODE_DIRECTORY = CONFIG_PATH.getParent().resolve("khanh-client-codes");
    private static final Path PROFILE_DIRECTORY = CONFIG_PATH.getParent().resolve("khanh-client-profiles");
    private static final int GUI_VERSION = 59;

    private ConfigManager() {
    }

    public static void load(ModuleManager modules) {
        if (Files.isRegularFile(CONFIG_PATH)) {
            loadFrom(CONFIG_PATH, modules);
            return;
        }
        if (Files.isRegularFile(LEGACY_CONFIG_PATH)) {
            loadFrom(LEGACY_CONFIG_PATH, modules);
            enableZeroGlass(modules);
            save(modules);
        }
    }

    private static void enableZeroGlass(ModuleManager modules) {
        Module visuals = modules.byId("visuals");
        if (visuals != null) {
            visuals.setEnabled(true);
            for (ModuleOption option : visuals.options()) {
                if (option.kind() == ModuleOption.Kind.BOOLEAN) option.setBoolean(true);
                if (option.id().equals("gui_finish")) option.setChoice("Glass");
                if (option.id().equals("hud_finish")) option.setChoice("Detailed");
            }
        }
        Module hud = modules.byId("hud");
        if (hud != null) {
            hud.setEnabled(true);
        }
    }

    private static void loadFrom(Path path, ModuleManager modules) {
        if (!Files.isRegularFile(path)) {
            return;
        }

        try {
            JsonObject root = GSON.fromJson(Files.readString(path, StandardCharsets.UTF_8), JsonObject.class);
            if (root == null) {
                return;
            }
            int guiVersion = root.has("guiVersion") ? root.get("guiVersion").getAsInt() : 1;

            JsonObject moduleStates = root.has("modules") ? root.getAsJsonObject("modules") : null;
            if (moduleStates != null) {
                if (guiVersion < 42) {
                    if (savedEnabled(moduleStates, "no_fire") || savedEnabled(moduleStates, "no_vignette"))
                        moduleStates.addProperty("visual_cleanup", true);
                    if (savedEnabled(moduleStates, "block_overlay")) moduleStates.addProperty("block_outline", true);
                    if (savedEnabled(moduleStates, "world_tweaks")) moduleStates.addProperty("skys", true);
                    if (savedEnabled(moduleStates, "cosmetics") && root.has("options")
                        && root.get("options").isJsonObject()) {
                        JsonObject allOptions = root.getAsJsonObject("options");
                        JsonObject old = allOptions.has("cosmetics") && allOptions.get("cosmetics").isJsonObject()
                            ? allOptions.getAsJsonObject("cosmetics") : new JsonObject();
                        boolean halo = !old.has("halo") || old.get("halo").getAsBoolean();
                        boolean hat = old.has("hat") && old.get("hat").getAsBoolean();
                        String wings = old.has("wings") ? old.get("wings").getAsString() : "Butterfly";
                        if (halo || hat) moduleStates.addProperty("china_hat", true);
                        if (!wings.equals("Off")) moduleStates.addProperty("wings", true);
                    }
                }
                if (!moduleStates.has("amethyst_esp") && moduleStates.has("cluster_esp")) {
                    moduleStates.add("amethyst_esp", moduleStates.get("cluster_esp"));
                }
                for (Module module : modules.all()) {
                    JsonElement value = moduleStates.get(module.id());
                    if (value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean()) {
                        module.setEnabled(value.getAsBoolean());
                    }
                }
                if (guiVersion < 29) {
                    Module radar = modules.byId("player_radar");
                    if (radar != null) radar.setEnabled(true);
                }
                if (guiVersion < 30) {
                    Module skies = modules.byId("skys");
                    if (skies != null) skies.setEnabled(true);
                }
            }

            JsonObject keybinds = root.has("keybinds") ? root.getAsJsonObject("keybinds") : null;
            if (keybinds != null && !keybinds.has("amethyst_esp") && keybinds.has("cluster_esp")) {
                keybinds.add("amethyst_esp", keybinds.get("cluster_esp"));
            }
            for (Module module : modules.all()) {
                JsonElement value = keybinds == null ? null : keybinds.get(module.id());
                module.setKeybind(value != null && value.isJsonPrimitive() ? value.getAsInt() : -1);
            }

            JsonObject actionKeybinds = root.has("actionKeybinds") ? root.getAsJsonObject("actionKeybinds") : null;
            for (Module module : modules.all()) {
                JsonObject savedModule = actionKeybinds != null && actionKeybinds.has(module.id())
                    ? actionKeybinds.getAsJsonObject(module.id()) : null;
                for (ModuleOption option : module.options()) {
                    if (option.kind() != ModuleOption.Kind.ACTION) {
                        continue;
                    }
                    JsonElement value = savedModule == null ? null : savedModule.get(option.id());
                    option.setActionKeybind(value != null && value.isJsonPrimitive() ? value.getAsInt() : -1);
                }
            }

            JsonObject optionStates = root.has("options") ? root.getAsJsonObject("options") : null;
            if (optionStates != null) {
                if (guiVersion < 42) migrateVisualOptions(moduleStates, optionStates);
                if (guiVersion < 37 && optionStates.has("player_hud")) {
                    JsonObject previous = optionStates.getAsJsonObject("player_hud");
                    JsonObject hudOptions = optionStates.has("hud") ? optionStates.getAsJsonObject("hud") : new JsonObject();
                    for (String field : new String[] {"skin", "name", "health", "fps", "ping", "clock", "coordinates"}) {
                        if (previous.has(field) && !hudOptions.has("info_" + field)) {
                            hudOptions.add("info_" + field, previous.get(field));
                        }
                    }
                    JsonElement previousModule = moduleStates == null ? null : moduleStates.get("player_hud");
                    if (previousModule != null && previousModule.isJsonPrimitive()
                        && previousModule.getAsJsonPrimitive().isBoolean()
                        && !previousModule.getAsBoolean()) hudOptions.addProperty("info", false);
                    optionStates.add("hud", hudOptions);
                }
                for (Module module : modules.all()) {
                    JsonObject savedModule = optionStates.has(module.id()) ? optionStates.getAsJsonObject(module.id()) : null;
                    if (savedModule == null) {
                        continue;
                    }
                    for (ModuleOption option : module.options()) {
                        JsonElement value = savedModule.get(option.id());
                        if ((value == null || !value.isJsonPrimitive())
                            && module.id().equals("block_outline")
                            && option.kind() == ModuleOption.Kind.COLOR
                            && option.id().equals("outline_color")) {
                            option.setColor(legacyBlockOutlineColor(savedModule));
                            continue;
                        }
                        if (value == null || !value.isJsonPrimitive()) {
                            continue;
                        }
                        switch (option.kind()) {
                            case BOOLEAN -> {
                                boolean savedBoolean = value.getAsBoolean();
                                // 0.2.58: Storage ESP tracers were rebuilt as pixel-locked lines and are on by default.
                                // 0.2.91: Fast Place speeds up every item by default (crystals, XP bottles...).
                                if (guiVersion < 55 && module.id().equals("fast_place") && option.id().equals("blocks_only")) savedBoolean = false;
                                // 0.2.95: Name Tags now names items on the ground by default.
                                if (guiVersion < 59 && module.id().equals("name_tags") && option.id().equals("items")) savedBoolean = true;
                                // 0.2.93: the GUI blur is gone by request; turn off old saves that had it on.
                                if (guiVersion < 58 && module.id().equals("visuals") && option.id().equals("blur")) savedBoolean = false;
                                // 0.2.92: Fake Stats matches the real Donut board (no labels, keyall, region or blank lines).
                                if (guiVersion < 57 && module.id().equals("fake_stats") && java.util.Set.of("labels", "spacers",
                                    "show_keyall", "show_region", "title_bold").contains(option.id())) savedBoolean = false;
                                if (guiVersion < 52 && module.id().equals("storage_esp")
                                    && option.id().equals("tracers")) savedBoolean = true;
                                // 0.2.54: icon-only and hidden-entry matching flagged normal players as staff.
                                if (guiVersion < 50 && module.id().equals("staff_online")
                                    && (option.id().equals("font_icons") || option.id().equals("show_vanished"))) savedBoolean = false;
                                if (guiVersion < 35 && module.id().equals("player_esp")
                                    && option.id().equals("tracers")) savedBoolean = true;
                                if (guiVersion < 24 && module.id().equals("visuals") && option.id().equals("gui_blur")) {
                                    savedBoolean = true;
                                }
                                if (guiVersion < 20 && module.id().equals("visuals") && option.id().equals("gui_blur")) {
                                    savedBoolean = true;
                                }
                                if (guiVersion < 15 && module.id().equals("visuals") && option.id().equals("gui_blur")) {
                                    savedBoolean = true;
                                }
                                if (guiVersion < 4 && module.id().equals("visuals")
                                    && (option.id().equals("glow") || option.id().equals("animated_accent"))) {
                                    savedBoolean = false;
                                }
                                option.setBoolean(savedBoolean);
                            }
                            case NUMBER -> {
                                double savedNumber = value.getAsDouble();
                                // 0.2.88: Creamy Keys was far too quiet; lift old settings to the new scale.
                                // 0.2.91: Fast Place should place every tick out of the box.
                                if (guiVersion < 56 && module.id().equals("fast_place") && option.id().equals("delay")) savedNumber = 0;
                                // 0.2.95: Anchor Macro no longer stops at 6 hearts by default.
                                if (guiVersion < 59 && module.id().equals("auto_anchor") && option.id().equals("min_health")
                                    && savedNumber == 12.0) savedNumber = 0.0;
                                if (guiVersion < 54 && module.id().equals("creamy_keys") && option.id().equals("volume"))
                                    savedNumber = Math.max(1.0, savedNumber * 4.0);
                                if (guiVersion < 34 && module.id().equals("visuals") && option.id().equals("gui_zoom")
                                    && Math.abs(savedNumber - 0.86) < 0.001) savedNumber = 0.76;
                                if (guiVersion < 35 && module.id().equals("visuals") && option.id().equals("gui_zoom")
                                    && Math.abs(savedNumber - 0.76) < 0.001) savedNumber = 0.68;
                                if (guiVersion < 39 && module.id().equals("visuals") && option.id().equals("gui_zoom")
                                    && Math.abs(savedNumber - 0.68) < 0.001) savedNumber = 0.55;
                                if (guiVersion < 34 && module.id().equals("sus_esp") && option.id().equals("cluster_threshold")
                                    && Math.abs(savedNumber - 24.0) < 0.001) savedNumber = 48.0;
                                if (guiVersion < 36 && module.id().equals("sus_esp")) {
                                    if (option.id().equals("cluster_threshold") && Math.abs(savedNumber - 48.0) < 0.001)
                                        savedNumber = 12.0;
                                    if (option.id().equals("local_clusters") && Math.abs(savedNumber - 4.0) < 0.001)
                                        savedNumber = 1.0;
                                    if (option.id().equals("region_chunks") && Math.abs(savedNumber - 2.0) < 0.001)
                                        savedNumber = 1.0;
                                }
                                if (guiVersion < 28 && module.id().equals("shader_hands") && option.id().equals("glow_radius")
                                    && Math.abs(savedNumber - 7) < .001) savedNumber = 5;
                                if (guiVersion < 28 && module.id().equals("shader_hands") && option.id().equals("glow_strength")
                                    && Math.abs(savedNumber - .7) < .001) savedNumber = .35;
                                // Refresh only previous defaults; preserve deliberately customised sizes.
                                if (guiVersion < 27 && module.id().equals("hud") && option.id().equals("hud_scale")
                                    && Math.abs(savedNumber - 0.70) < 0.001) savedNumber = 0.65;
                                if (guiVersion < 27 && module.id().equals("shader_hands") && option.id().equals("width")
                                    && Math.abs(savedNumber - 1.6) < 0.001) savedNumber = 1.1;
                                if (module.id().equals("storage_esp") && option.id().equals("tracer_width") && guiVersion < 26) {
                                    savedNumber = 0.5;
                                }
                                if (module.id().equals("hud") && option.id().equals("hud_scale")
                                    && Math.abs(savedNumber - 0.72) < 0.001) {
                                    savedNumber = 0.56;
                                }
                                if (guiVersion < 4 && module.id().equals("visuals") && option.id().equals("glass_tint")) {
                                    savedNumber = Math.min(savedNumber, 0.10);
                                }
                                if (guiVersion < 4 && module.id().equals("visuals") && option.id().equals("glass_opacity")
                                    && Math.abs(savedNumber - 0.72) < 0.001) {
                                    savedNumber = 0.80;
                                }
                                if (guiVersion < 5 && module.id().equals("sus_esp") && option.id().equals("alpha")
                                    && savedNumber <= 42.0) {
                                    savedNumber = 104.0;
                                }
                                if (guiVersion < 6 && module.id().equals("sus_esp") && option.id().equals("alpha")
                                    && savedNumber <= 104.0) {
                                    savedNumber = 180.0;
                                }
                                if (guiVersion < 6 && module.id().equals("sus_esp") && option.id().equals("layer_y")
                                    && Math.abs(savedNumber - 64.0) < 0.001) {
                                    savedNumber = 55.0;
                                }
                                if (guiVersion < 6 && module.id().equals("sus_esp") && option.id().equals("radius")
                                    && Math.abs(savedNumber - 5.0) < 0.001) {
                                    savedNumber = 8.0;
                                }
                                if (guiVersion < 7 && module.id().equals("visuals") && option.id().equals("glass_opacity")
                                    && Math.abs(savedNumber - 0.80) < 0.001) {
                                    savedNumber = 0.86;
                                }
                                if (guiVersion < 7 && module.id().equals("visuals") && option.id().equals("glass_tint")
                                    && Math.abs(savedNumber - 0.10) < 0.001) {
                                    savedNumber = 0.12;
                                }
                                if (guiVersion < 7 && module.id().equals("block_outline") && option.id().equals("bounce_strength")
                                    && savedNumber > 0.10) {
                                    savedNumber = 0.10;
                                }
                                if (guiVersion < 10 && module.id().equals("auto_crystal")) {
                                    if (option.id().equals("reach") && Math.abs(savedNumber - 5.0) < 0.001) savedNumber = 3.0;
                                    if ((option.id().equals("place_delay") || option.id().equals("break_delay"))
                                        && Math.abs(savedNumber - 2.0) < 0.001) savedNumber = 1.0;
                                    if (option.id().equals("obsidian_delay") && Math.abs(savedNumber - 4.0) < 0.001) savedNumber = 2.0;
                                }
                                option.setNumber(savedNumber);
                            }
                            case CHOICE -> {
                                String savedChoice = value.getAsString();
                                // 0.2.95: Skin Protect is about your own look; the old default changed everyone.
                                if (guiVersion < 59 && module.id().equals("skin_protect") && option.id().equals("apply_to")
                                    && savedChoice.equals("Everyone")) savedChoice = "Self";
                                if (guiVersion < 32 && module.id().equals("visuals") && option.id().equals("gui_theme"))
                                    savedChoice = "Khanh Client Black";
                                if (guiVersion < 30 && module.id().equals("visuals") && option.id().equals("gui_theme")
                                    && savedChoice.equals("Khanh Client Violet")) savedChoice = "Black Glass";
                                if (guiVersion < 30 && module.id().equals("skys") && option.id().equals("sky"))
                                    savedChoice = "Crystal Skies";
                                if (guiVersion < 24 && module.id().equals("visuals") && option.id().equals("gui_finish")) {
                                    savedChoice = "Glass";
                                }
                                if (guiVersion < 23 && module.id().equals("visuals") && option.id().equals("gui_layout")) {
                                    savedChoice = savedChoice.equals("Water") ? "Dropdown"
                                        : savedChoice.equals("Dropdown") ? "Columns" : savedChoice;
                                }
                                if (guiVersion < 22 && module.id().equals("visuals") && option.id().equals("gui_layout")) {
                                    savedChoice = "Columns";
                                }
                                if (guiVersion < 12 && module.id().equals("visuals") && option.id().equals("gui_layout")) {
                                    savedChoice = savedChoice.equals("List") ? "Compact Columns" : "Columns";
                                }
                                if (guiVersion < 11 && module.id().equals("visuals") && option.id().equals("gui_layout")) {
                                    savedChoice = "Columns";
                                }
                                if (guiVersion < 8 && module.id().equals("visuals") && option.id().equals("gui_layout")) {
                                    savedChoice = "Columns";
                                }
                                if (module.id().equals("visuals") && option.id().equals("gui_layout")) {
                                    if (savedChoice.equals("Dropdown") || savedChoice.equals("Water")) savedChoice = "Cards";
                                    if (savedChoice.equals("Compact Columns")) savedChoice = "Columns";
                                }
                                // 0.2.46 introduces the Liquid Glass console as the default browser.
                                if (guiVersion < 48 && module.id().equals("visuals") && option.id().equals("gui_layout")) {
                                    savedChoice = "Liquid Glass";
                                }
                                if (guiVersion < 9 && module.id().equals("storage_esp")) {
                                    if (option.id().equals("mode") && savedChoice.equals("Glow")) savedChoice = "Outline";
                                    if (option.id().equals("chest_color") && savedChoice.equals("Gold")) savedChoice = "Classic Orange";
                                    if (option.id().equals("barrel_color") && savedChoice.equals("Gold")) savedChoice = "Classic Brown";
                                    if (option.id().equals("ender_color") && savedChoice.equals("Purple")) savedChoice = "Classic Purple";
                                    if (option.id().equals("shulker_color") && savedChoice.equals("Pink")) savedChoice = "Classic Pink";
                                }
                                if (guiVersion < 10 && module.id().equals("storage_esp")
                                    && !savedChoice.startsWith("Classic ")) {
                                    if (savedChoice.endsWith(" Orange")) savedChoice = "Classic Orange";
                                    if (savedChoice.endsWith(" Red")) savedChoice = "Classic Red";
                                    if (savedChoice.endsWith(" Purple")) savedChoice = "Classic Purple";
                                    if (savedChoice.endsWith(" Pink")) savedChoice = "Classic Pink";
                                    if (savedChoice.endsWith(" Brown")) savedChoice = "Classic Brown";
                                    if (savedChoice.endsWith(" Green")) savedChoice = "Classic Green";
                                    if (savedChoice.endsWith(" Gray")) savedChoice = "Classic Gray";
                                }
                                // Theme names were "Zero Black" etc.; they are "Khanh Client Black" now.
                                if (savedChoice.startsWith("Zero ") && !savedChoice.startsWith("Khanh Client"))
                                    savedChoice = "Khanh Client " + savedChoice.substring(5);
                                option.setChoice(savedChoice);
                            }
                            case COLOR -> option.setColor(guiVersion < 57 && module.id().equals("fake_stats")
                                && option.id().equals("title_color") ? 0xFFFFFFFF : value.getAsInt());
                            case TEXT -> {
                                String savedText = value.getAsString();
                                // Older builds shipped the splash title as "67client" or "Zero" + "fire"; the client is Khanh Client.
                                if (module.id().equals("intro") && option.id().equals("title")
                                    && (savedText.equalsIgnoreCase("67client") || savedText.equalsIgnoreCase("Zero" + "fire")))
                                    savedText = "Khanh Client";
                                // The protected name used to default to plain "Zero"; the brand is Khanh Client.
                                if (module.id().equals("name_protect") && option.id().equals("replacement_name")
                                    && savedText.equals("Zero")) savedText = "Khanh Client";
                                // 0.2.91: Fake Stats now copies DonutSMP's board, title included.
                                if (guiVersion < 57 && module.id().equals("fake_stats") && option.id().equals("title")
                                    && (savedText.equals("KHANH CLIENT") || savedText.equals("ZERO") || savedText.equals("DonutSMP"))) savedText = "{name}";
                                option.setText(savedText);
                            }
                            case ACTION -> { }
                        }
                    }
                }
            }

            JsonArray friends = root.has("friends") && root.get("friends").isJsonArray()
                ? root.getAsJsonArray("friends") : null;
            List<String> names = new ArrayList<>();
            if (friends != null) {
                for (JsonElement value : friends) {
                    if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
                        names.add(value.getAsString());
                    }
                }
            }
            FriendService.replaceAll(names);

            JsonObject hud = root.has("hudLayout") ? root.getAsJsonObject("hudLayout") : null;
            for (HudLayout.Widget widget : HudLayout.Widget.values()) {
                HudLayout.State state = HudLayout.widget(widget);
                state.positioned = false;
                state.scale = 1.0F;
                JsonObject saved = hud != null && hud.has(widget.name())
                    ? hud.getAsJsonObject(widget.name()) : null;
                if (saved == null) continue;
                state.scale = saved.has("scale")
                    ? Math.max(0.5F, Math.min(1.75F, saved.get("scale").getAsFloat())) : 1.0F;
                boolean hasPosition = saved.has("x") && saved.has("y");
                state.positioned = saved.has("positioned")
                    ? saved.get("positioned").getAsBoolean() && hasPosition : hasPosition;
                if (hasPosition) {
                    state.x = saved.get("x").getAsInt();
                    state.y = saved.get("y").getAsInt();
                    state.refWidth = saved.has("refWidth") ? saved.get("refWidth").getAsInt() : 0;
                    state.refHeight = saved.has("refHeight") ? saved.get("refHeight").getAsInt() : 0;
                    // Pre-0.2.61 positions were absolute pixels for one window size and could land
                    // mid-screen on another; reset those once so widgets return to their defaults.
                    if (state.refWidth <= 0 || state.refHeight <= 0) state.positioned = false;
                }
            }
        } catch (Exception exception) {
            System.err.println("Khanh Client could not read its config: " + exception.getMessage());
        }
    }

    private static boolean savedEnabled(JsonObject states, String id) {
        return states != null && states.has(id) && states.get(id).isJsonPrimitive()
            && states.get(id).getAsJsonPrimitive().isBoolean() && states.get(id).getAsBoolean();
    }

    private static JsonObject optionsFor(JsonObject options, String id) {
        if (options.has(id) && options.get(id).isJsonObject()) return options.getAsJsonObject(id);
        JsonObject created = new JsonObject();
        options.add(id, created);
        return created;
    }

    private static void copyOption(JsonObject from, String oldName, JsonObject to, String newName) {
        if (from.has(oldName) && !to.has(newName)) to.add(newName, from.get(oldName));
    }

    private static void migrateVisualOptions(JsonObject states, JsonObject options) {
        if (savedEnabled(states, "no_fire") || savedEnabled(states, "no_vignette")) {
            JsonObject cleanup = optionsFor(options, "visual_cleanup");
            if (savedEnabled(states, "no_fire")) cleanup.addProperty("fire", true);
            if (savedEnabled(states, "no_vignette")) cleanup.addProperty("vignette", true);
        }
        if (options.has("block_overlay") && options.get("block_overlay").isJsonObject()) {
            JsonObject old = options.getAsJsonObject("block_overlay");
            JsonObject outline = optionsFor(options, "block_outline");
            copyOption(old, "color", outline, "fill_color");
            copyOption(old, "opacity", outline, "fill_opacity");
            if (savedEnabled(states, "block_overlay")) outline.addProperty("fill_enabled", true);
        }
        if (options.has("world_tweaks") && options.get("world_tweaks").isJsonObject()) {
            JsonObject old = options.getAsJsonObject("world_tweaks");
            JsonObject skies = optionsFor(options, "skys");
            for (String key : new String[] {"time_enabled", "time", "fog_enabled", "fog_color", "fog"})
                copyOption(old, key, skies, key);
            if (!savedEnabled(states, "world_tweaks")) {
                skies.addProperty("time_enabled", false);
                skies.addProperty("fog_enabled", false);
            }
        }
        if (savedEnabled(states, "cosmetics") && options.has("cosmetics")
            && options.get("cosmetics").isJsonObject()) {
            JsonObject old = options.getAsJsonObject("cosmetics");
            JsonObject hat = optionsFor(options, "china_hat");
            JsonObject wings = optionsFor(options, "wings");
            copyOption(old, "color", hat, "color");
            copyOption(old, "color", wings, "color");
            copyOption(old, "size", hat, "size");
            copyOption(old, "size", wings, "size");
            copyOption(old, "halo", hat, "halo");
            copyOption(old, "hat", hat, "show_hat");
            copyOption(old, "wings", wings, "style");
            if (!old.has("hat")) hat.addProperty("show_hat", false);
        }
    }

    private static int legacyBlockOutlineColor(JsonObject savedModule) {
        String selected = savedModule.has("color") ? savedModule.get("color").getAsString() : "Khanh Client Pink";
        return switch (selected) {
            case "Custom" -> 0xFF000000
                | (legacyChannel(savedModule, "custom_red", 255) << 16)
                | (legacyChannel(savedModule, "custom_green", 22) << 8)
                | legacyChannel(savedModule, "custom_blue", 159);
            case "Rose" -> 0xFFFF477E;
            case "Violet" -> 0xFFB76CFF;
            case "Cyan" -> 0xFF42D9FF;
            case "Lime" -> 0xFF64F58D;
            case "Red" -> 0xFFFF263D;
            case "White" -> 0xFFF5F3F4;
            default -> 0xFFFF20AA;
        };
    }

    private static int legacyChannel(JsonObject savedModule, String key, int fallback) {
        if (!savedModule.has(key) || !savedModule.get(key).isJsonPrimitive()) {
            return fallback;
        }
        return Math.max(0, Math.min(255, savedModule.get(key).getAsInt()));
    }

    public static void save(ModuleManager modules) {
        saveTo(CONFIG_PATH, modules);
    }

    private static JsonObject snapshot(ModuleManager modules) {
        JsonObject root = new JsonObject();
        root.addProperty("guiVersion", GUI_VERSION);
        JsonObject moduleStates = new JsonObject();
        for (Module module : modules.all()) {
            moduleStates.addProperty(module.id(), module.enabled());
        }
        root.add("modules", moduleStates);

        JsonObject keybinds = new JsonObject();
        for (Module module : modules.all()) {
            keybinds.addProperty(module.id(), module.keybind());
        }
        root.add("keybinds", keybinds);

        JsonObject actionKeybinds = new JsonObject();
        for (Module module : modules.all()) {
            JsonObject savedModule = new JsonObject();
            for (ModuleOption option : module.options()) {
                if (option.kind() == ModuleOption.Kind.ACTION) {
                    savedModule.addProperty(option.id(), option.actionKeybind());
                }
            }
            if (!savedModule.entrySet().isEmpty()) {
                actionKeybinds.add(module.id(), savedModule);
            }
        }
        root.add("actionKeybinds", actionKeybinds);

        JsonObject optionStates = new JsonObject();
        for (Module module : modules.all()) {
            JsonObject savedModule = new JsonObject();
            for (ModuleOption option : module.options()) {
                switch (option.kind()) {
                    case BOOLEAN -> savedModule.addProperty(option.id(), option.booleanValue());
                    case NUMBER -> savedModule.addProperty(option.id(), option.numberValue());
                    case CHOICE -> savedModule.addProperty(option.id(), option.choiceValue());
                    case COLOR -> savedModule.addProperty(option.id(), option.colorValue());
                    case TEXT -> savedModule.addProperty(option.id(), option.textValue());
                    case ACTION -> { }
                }
            }
            optionStates.add(module.id(), savedModule);
        }
        root.add("options", optionStates);

        JsonArray friends = new JsonArray();
        for (String name : FriendService.names()) {
            friends.add(name);
        }
        root.add("friends", friends);

        JsonObject hud = new JsonObject();
        for (HudLayout.Widget widget : HudLayout.Widget.values()) {
            HudLayout.State state = HudLayout.widget(widget);
            if (!state.positioned && Math.abs(state.scale - 1.0F) < 0.001F) continue;
            JsonObject saved = new JsonObject();
            saved.addProperty("positioned", state.positioned);
            if (state.positioned) {
                saved.addProperty("x", state.x);
                saved.addProperty("y", state.y);
                saved.addProperty("refWidth", state.refWidth);
                saved.addProperty("refHeight", state.refHeight);
            }
            saved.addProperty("scale", state.scale);
            hud.add(widget.name(), saved);
        }
        root.add("hudLayout", hud);
        return root;
    }

    private static void saveTo(Path path, ModuleManager modules) {
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(snapshot(modules)), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            System.err.println("Khanh Client could not save its config: " + exception.getMessage());
        }
    }

    public static void saveProfile(ModuleManager modules) {
        String name = selectedProfile(modules);
        saveTo(profilePath(name), modules);
        notifyPlayer("Saved config " + name);
    }

    public static void loadProfile(ModuleManager modules) {
        String name = selectedProfile(modules);
        Path path = profilePath(name);
        if (!Files.isRegularFile(path)) {
            notifyPlayer("Config " + name + " has not been saved yet");
            return;
        }
        loadFrom(path, modules);
        save(modules);
        notifyPlayer("Loaded config " + name);
    }

    public static boolean profileExists(int slot) {
        checkSlot(slot);
        return Files.isRegularFile(profilePath(slot));
    }

    public static String profileName(int slot) {
        checkSlot(slot);
        Path path = profilePath(slot);
        if (Files.isRegularFile(path)) {
            try {
                JsonObject saved = GSON.fromJson(Files.readString(path, StandardCharsets.UTF_8), JsonObject.class);
                if (saved != null && saved.has("profileName")) {
                    String name = saved.get("profileName").getAsString().strip();
                    if (!name.isEmpty()) return name;
                }
            } catch (Exception ignored) { }
        }
        return "Profile " + slot;
    }

    public static String saveProfile(ModuleManager modules, int slot, String requestedName) {
        checkSlot(slot);
        String name = requestedName == null ? "" : requestedName.strip();
        if (name.isEmpty()) name = "Profile " + slot;
        if (name.length() > 24) name = name.substring(0, 24);
        try {
            Files.createDirectories(PROFILE_DIRECTORY);
            JsonObject settings = snapshot(modules);
            settings.addProperty("profileName", name);
            Path target = profilePath(slot);
            Path staged = Files.createTempFile(PROFILE_DIRECTORY, "slot-" + slot + "-", ".json");
            try {
                Files.writeString(staged, GSON.toJson(settings), StandardCharsets.UTF_8);
                try { Files.move(staged, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
                catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                    Files.move(staged, target, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally { Files.deleteIfExists(staged); }
            return "Saved " + name;
        } catch (IOException error) {
            return "Save failed: " + error.getMessage();
        }
    }

    public static String loadProfile(ModuleManager modules, int slot) {
        checkSlot(slot);
        Path path = profilePath(slot);
        if (!Files.isRegularFile(path)) return "Profile " + slot + " is empty";
        try {
            JsonObject stored = GSON.fromJson(Files.readString(path, StandardCharsets.UTF_8), JsonObject.class);
            if (stored == null || !stored.has("modules") || !stored.has("options"))
                return "Load failed: profile data is incomplete";
            if (Files.isRegularFile(CONFIG_PATH))
                Files.copy(CONFIG_PATH, CONFIG_PATH.resolveSibling("khanh-client-before-profile-load.json"),
                    StandardCopyOption.REPLACE_EXISTING);
            loadFrom(path, modules);
            save(modules);
            return "Loaded " + profileName(slot);
        } catch (IOException error) {
            return "Load failed: " + error.getMessage();
        }
    }

    public static String deleteProfile(int slot) {
        checkSlot(slot);
        Path current = profilePath(slot);
        if (!Files.isRegularFile(current)) return "Profile " + slot + " is already empty";
        try {
            Path archive = PROFILE_DIRECTORY.resolve("deleted");
            Files.createDirectories(archive);
            long when = System.currentTimeMillis();
            Files.move(current, archive.resolve("slot-" + slot + "-" + when + "-zero.json"));
            return "Profile " + slot + " removed (backup kept in deleted/)";
        } catch (IOException error) {
            return "Delete failed: " + error.getMessage();
        }
    }

    public static String copyPortableCode(ModuleManager modules) {
        try {
            String code = createShareCode(modules);
            MinecraftClient.getInstance().keyboard.setClipboard(code);
            return "Portable code copied — send it to a friend";
        } catch (Exception error) {
            return "Export failed: " + error.getMessage();
        }
    }

    public static String copySixDigitCode(ModuleManager modules) {
        try {
            String code = storeShortCode(createShareCode(modules));
            MinecraftClient.getInstance().keyboard.setClipboard(code);
            return "Copied " + code + " — six-digit codes work on this device";
        } catch (Exception error) {
            return "Export failed: " + error.getMessage();
        }
    }

    /** The GUI's "Copy share code": a full code that imports on any PC, not a this-device-only number. */
    public static String copySixDigitCode(ModuleManager modules, Consumer<String> finished) {
        try {
            String code = createShareCode(modules);
            MinecraftClient.getInstance().keyboard.setClipboard(code);
            return "Copied share code — paste it into Import on any PC";
        } catch (Exception error) {
            return "Export failed: " + error.getMessage();
        }
    }

    public static String importShareCodeAsync(ModuleManager modules, String enteredCode, Consumer<String> finished) {
        return importShareCode(modules, enteredCode == null ? "" : enteredCode.trim());
    }

    public static void exportShareCode(ModuleManager modules) {
        try {
            String code = storeShortCode(createShareCode(modules));
            Module config = modules.byId("config");
            if (config != null && config.option("code") != null) config.option("code").setText(code);
            save(modules);
            MinecraftClient.getInstance().keyboard.setClipboard(code);
            notifyPlayer("Copied six-digit config code " + code);
        } catch (Exception exception) {
            notifyPlayer("Could not create share code: " + exception.getMessage());
        }
    }

    /**
     * A compact share code: only what differs from Khanh Client's defaults (modules switched on or
     * off, changed settings, keys and moved HUD widgets). Short enough to paste in a Discord message.
     * Private text (names, IDs) and friends are never included.
     */
    private static String createShareCode(ModuleManager modules) {
        JsonObject full = snapshot(modules);
        JsonObject code = new JsonObject();
        code.addProperty("guiVersion", GUI_VERSION);
        code.addProperty("compact", true);
        JsonObject states = new JsonObject(), keys = new JsonObject(), options = new JsonObject(), actions = new JsonObject();
        for (Module module : modules.all()) {
            if (module.enabled() != module.defaultEnabled()) states.addProperty(module.id(), module.enabled());
            if (module.keybind() >= 0) keys.addProperty(module.id(), module.keybind());
            JsonObject changed = new JsonObject(), actionKeys = new JsonObject();
            for (ModuleOption option : module.options()) {
                if (option.kind() == ModuleOption.Kind.ACTION) {
                    if (option.actionKeybind() >= 0) actionKeys.addProperty(option.id(), option.actionKeybind());
                    continue;
                }
                if (option.kind() == ModuleOption.Kind.TEXT || option.isDefault()) continue;
                changed.add(option.id(), full.getAsJsonObject("options").getAsJsonObject(module.id()).get(option.id()));
            }
            if (!changed.entrySet().isEmpty()) options.add(module.id(), changed);
            if (!actionKeys.entrySet().isEmpty()) actions.add(module.id(), actionKeys);
        }
        code.add("modules", states);
        code.add("keybinds", keys);
        code.add("options", options);
        code.add("actionKeybinds", actions);
        if (full.has("hudLayout")) code.add("hudLayout", full.get("hudLayout"));
        return ConfigShareCodec.encode(code);
    }

    /**
     * Like {@code current}, but every module state and setting back at its default, keeping this
     * PC's private text and keys, so a compact code reproduces the sharer's setup exactly.
     */
    private static JsonObject defaultsSnapshot(ModuleManager modules, JsonObject current) {
        JsonObject root = current.deepCopy();
        JsonObject states = root.getAsJsonObject("modules"), options = root.getAsJsonObject("options");
        for (Module module : modules.all()) {
            states.addProperty(module.id(), module.defaultEnabled());
            JsonObject saved = options.has(module.id()) ? options.getAsJsonObject(module.id()) : new JsonObject();
            for (ModuleOption option : module.options()) {
                switch (option.kind()) {
                    case BOOLEAN -> saved.addProperty(option.id(), option.defaultBooleanValue());
                    case NUMBER -> saved.addProperty(option.id(), option.defaultNumberValue());
                    case CHOICE -> saved.addProperty(option.id(), option.defaultChoiceValue());
                    case COLOR -> saved.addProperty(option.id(), option.defaultColorValue());
                    default -> { }
                }
            }
            options.add(module.id(), saved);
        }
        return root;
    }

    private static String storeShortCode(String encoded) throws IOException {
        Files.createDirectories(CODE_DIRECTORY);
        java.util.zip.CRC32 checksum = new java.util.zip.CRC32();
        checksum.update(encoded.getBytes(StandardCharsets.UTF_8));
        int seed = (int) (checksum.getValue() % 1_000_000L);
        for (int attempt = 0; attempt < 1_000_000; attempt++) {
            String code = String.format(java.util.Locale.ROOT, "%06d", (seed + attempt) % 1_000_000);
            Path path = CODE_DIRECTORY.resolve(code + ".zf2");
            if (!Files.exists(path) || Files.readString(path, StandardCharsets.UTF_8).equals(encoded)) {
                Files.writeString(path, encoded, StandardCharsets.UTF_8);
                return code;
            }
        }
        throw new IOException("No free six-digit config code is available");
    }

    public static String importShareCode(ModuleManager modules) {
        return importShareCode(modules, MinecraftClient.getInstance().keyboard.getClipboard());
    }

    public static String importShareCode(ModuleManager modules, String enteredCode) {
        try {
            String requested = enteredCode == null ? "" : enteredCode.trim();
            String encoded = requested;
            if (requested.matches("[0-9]{6}")) {
                Path stored = CODE_DIRECTORY.resolve(requested + ".zf2");
                if (!Files.isRegularFile(stored)) throw new IOException("Code " + requested + " is not saved on this device");
                encoded = Files.readString(stored, StandardCharsets.UTF_8).trim();
            }
            JsonObject imported=ConfigShareCodec.decode(encoded),root=snapshot(modules);
            if (imported.has("compact") && imported.get("compact").getAsBoolean()) root = defaultsSnapshot(modules, root);
            for (Module module:modules.all()) {
                JsonObject options=imported.getAsJsonObject("options").has(module.id())
                    ? imported.getAsJsonObject("options").getAsJsonObject(module.id()):null;
                if (options==null) continue;
                for (ModuleOption option:module.options()) if (options.has(option.id())) {
                    JsonElement value=options.get(option.id());
                    if (option.kind()==ModuleOption.Kind.TEXT || option.kind()==ModuleOption.Kind.ACTION) {options.remove(option.id());continue;}
                    boolean valid=switch(option.kind()) {
                        case BOOLEAN -> value.getAsJsonPrimitive().isBoolean();
                        case NUMBER,COLOR -> value.getAsJsonPrimitive().isNumber();
                        case CHOICE -> value.getAsJsonPrimitive().isString();
                        default -> false;
                    };
                    if (!valid) throw new IOException("Wrong value type for "+option.label());
                    if (option.kind()==ModuleOption.Kind.COLOR && (value.getAsDouble()!=Math.rint(value.getAsDouble())
                        || value.getAsDouble()<Integer.MIN_VALUE || value.getAsDouble()>Integer.MAX_VALUE))
                        throw new IOException("Invalid colour value");
                }
            }
            // Merge into the local snapshot, preserving private text and friends.
            for (var section:imported.entrySet()) {
                if (section.getKey().equals("guiVersion")) {root.addProperty("guiVersion",GUI_VERSION);continue;}
                if (section.getKey().equals("compact")) continue;
                // Sections this config does not have yet (or plain values) are taken as they are.
                if (!section.getValue().isJsonObject() || !root.has(section.getKey()) || !root.get(section.getKey()).isJsonObject()) {
                    root.add(section.getKey(), section.getValue());
                    continue;
                }
                JsonObject existing=root.getAsJsonObject(section.getKey());
                for (var group:section.getValue().getAsJsonObject().entrySet()) {
                    if ((section.getKey().equals("options") || section.getKey().equals("actionKeybinds")) && group.getValue().isJsonObject()) {
                        JsonObject target=existing.has(group.getKey()) && existing.get(group.getKey()).isJsonObject()
                            ? existing.getAsJsonObject(group.getKey()) : new JsonObject();
                        for (var setting:group.getValue().getAsJsonObject().entrySet()) target.add(setting.getKey(),setting.getValue());
                        existing.add(group.getKey(),target);
                    } else existing.add(group.getKey(),group.getValue());
                }
            }
            Files.createDirectories(CONFIG_PATH.getParent());
            Files.writeString(CONFIG_PATH.resolveSibling("khanh-client-before-code-import.json"),GSON.toJson(snapshot(modules)),StandardCharsets.UTF_8);
            Path staged=Files.createTempFile(CONFIG_PATH.getParent(),"zero-code-",".json");
            try {
                Files.writeString(staged,GSON.toJson(root),StandardCharsets.UTF_8);
                try {Files.move(staged,CONFIG_PATH,java.nio.file.StandardCopyOption.ATOMIC_MOVE,java.nio.file.StandardCopyOption.REPLACE_EXISTING);}
                catch(java.nio.file.AtomicMoveNotSupportedException ignored) {Files.move(staged,CONFIG_PATH,java.nio.file.StandardCopyOption.REPLACE_EXISTING);}
            } finally {Files.deleteIfExists(staged);}
            load(modules);
            save(modules);
            String message = requested.matches("[0-9]{6}")
                ? "Imported config code " + requested : "Imported Khanh Client share code";
            notifyPlayer(message);
            return message;
        } catch (Exception exception) {
            String message = "Could not import share code: " + exception.getMessage();
            notifyPlayer(message);
            return message;
        }
    }

    private static String selectedProfile(ModuleManager modules) {
        Module config = modules.byId("config");
        ModuleOption option = config == null ? null : config.option("profile");
        return option == null ? "Slot 1" : option.choiceValue();
    }

    private static Path profilePath(String name) {
        String filename = name.toLowerCase().replace(' ', '-') + ".json";
        return CONFIG_PATH.getParent().resolve("khanh-client-profiles").resolve(filename);
    }

    private static Path profilePath(int slot) {
        return PROFILE_DIRECTORY.resolve("slot-" + slot + ".json");
    }

    private static void checkSlot(int slot) {
        if (slot < 1 || slot > 5) throw new IllegalArgumentException("Profile must be 1–5");
    }

    private static void notifyPlayer(String message) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            client.player.sendMessage(Text.literal("[Khanh Client] " + message), true);
        }
    }
}
