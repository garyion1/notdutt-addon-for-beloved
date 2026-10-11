package dev.khanh.module;

import dev.khanh.config.ConfigManager;
import dev.khanh.feature.FriendService;
import dev.khanh.feature.GameplayUtilityService;
import dev.khanh.feature.KhanhFeatures;
import dev.khanh.feature.WorkAutomationService;
import dev.khanh.gui.SkinGalleryScreen;
import dev.khanh.gui.KhanhConfigScreen;
import net.minecraft.client.MinecraftClient;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ModuleManager {
    private static final java.util.Set<String> VISUAL_IDS = java.util.Set.of(
        "block_outline","full_bright","jump_circles",
        "shader_hands","view_model","swing_animations","item_size",
        "visual_cleanup","motion_blur",
        "bedrock_void_esp","projectile_pearl","health_alert","name_tags","totem_styles","orbit_orbs","rune_circle","motion_trail","crown","cat_ears","devil_horns","spirit_pet","saturn_ring","footprints","star_aura","rain_cloud","energy_shield","custom_glint","inventory_color","clear_water",
        "custom_crosshair","elytra_colors","fov_unbound",
        "zoom","camera_clip",
        "offscreen_players","shulker_preview",
        "target_hud","see_invisible","item_physics",
        "china_hat","wings","fun_skins","custom_cape","fireflies","player_halo","body_aura");
    private final Map<String, Module> byId = new LinkedHashMap<>();
    private final Map<Category, List<Module>> byCategory = new EnumMap<>(Category.class);

    public ModuleManager() {
        for (Category category : Category.values()) {
            byCategory.put(category, new ArrayList<>());
        }

        register("swing_speed", "Swing Speed", "Changes only your local hand swing animation", Category.PLAYER)
            .option(ModuleOption.number("speed", "Speed", "Visual swing speed multiplier, including very slow motion", 0.65, 0.1, 2.2, 0.05));
        register("aim_assist", "Aim Assist", "Smoothly guides your aim toward a nearby enemy", Category.COMBAT)
            .option(ModuleOption.bool("attack_only", "Attack Only", "Aim only while the attack key is held", true))
            .option(ModuleOption.number("range", "Range", "Maximum target distance", 6.0, 2.0, 10.0, 0.25))
            .option(ModuleOption.number("fov", "Field of View", "Horizontal target cone in degrees", 100, 20, 180, 5))
            .option(ModuleOption.bool("through_walls", "Through Walls", "Allow targets without direct visibility", false))
            .option(ModuleOption.bool("instant", "Instant", "Snap immediately instead of smoothing", false))
            .option(ModuleOption.number("speed", "Aim Speed", "How quickly the view reaches the target", 0.18, 0.03, 1.0, 0.01))
            .option(ModuleOption.choice("aim_point", "Aim Point", "Where on the target to aim", "Chest", "Chest", "Head", "Feet", "Closest"))
            .option(ModuleOption.number("max_turn", "Max Turn Speed", "Most degrees per second it will turn; lower looks more natural", 540, 60, 1440, 20))
            .option(ModuleOption.bool("vertical", "Vertical Aim", "Also move your aim up and down", true));
        register("pop_counter", "Pop Counter", "Counts every player's totem pops and tells you in chat, plus how many they popped before dying", Category.COMBAT)
            .option(ModuleOption.bool("chat", "Chat Message", "Say in chat each time someone pops", true))
            .option(ModuleOption.bool("death_message", "Death Message", "Say how many totems someone popped when they die", true))
            .option(ModuleOption.bool("self", "Count Yours", "Also count your own pops", false))
            .option(ModuleOption.bool("sound", "Sound", "Play a sound when someone pops", false));
        register("kill_effects", "Kill Effects", "Lightning, a totem burst, flames or hearts when you kill a player (only you see it)", Category.COMBAT)
            .option(ModuleOption.choice("effect", "Effect", "What happens where they died", "Lightning", "Lightning", "Totem Burst", "Flames", "Hearts"))
            .option(ModuleOption.bool("only_my_kills", "Only My Kills", "Only for players you hit in the last 10 seconds", true))
            .option(ModuleOption.bool("message", "Kill Message", "Show You killed <name> above the hotbar", false));
        register("anti_trap", "Anti Trap", "Locally hides nearby trap entities that obstruct combat", Category.COMBAT)
            .option(ModuleOption.number("interval", "Check Delay", "Ticks between nearby entity checks", 10, 1, 40, 1))
            .option(ModuleOption.number("range", "Range", "Maximum cleanup distance", 4.0, 1.0, 12.0, 0.5))
            .option(ModuleOption.bool("armor_stands", "Armor Stands", "Hide nearby armor stands", true))
            .option(ModuleOption.bool("chest_minecarts", "Chest Minecarts", "Hide nearby chest minecarts", true));
        register("auto_double_hand", "Auto Double Hand", "Selects a second hotbar totem after your offhand totem pops", Category.COMBAT);
        register("auto_inv_totem", "Auto Inventory Totem", "Instantly fills the offhand only while your player inventory is open", Category.COMBAT)
            .option(ModuleOption.bool("from_hotbar", "Use Hotbar", "Use hotbar totems as well as inventory totems", true));
        register("hover_totem", "Hover Totem", "Hover a totem in your inventory to equip it", Category.COMBAT)
            .option(ModuleOption.bool("hotbar_totem", "Hotbar Totem", "Also fill a chosen hotbar slot", true))
            .option(ModuleOption.number("hotbar_slot", "Hotbar Slot", "Chosen replacement-totem slot", 1, 1, 9, 1))
            .option(ModuleOption.bool("select_hotbar", "Select Slot", "Select the chosen slot after filling it", false));
        register("key_pearl", "Key Pearl", "Throws an ender pearl from your hotbar with one key", Category.COMBAT)
            .option(ModuleOption.choice("trigger", "Trigger", "Key used to throw a pearl", "V", "V", "G", "B", "C", "X", "F", "R", "F7"))
            .option(ModuleOption.bool("missing_message", "Missing Message", "Tell you in the action bar when no pearl is available", true))
            .option(ModuleOption.number("delay", "Throw Delay", "Ticks after selecting the pearl", 0, 0, 10, 1))
            .option(ModuleOption.bool("switch_back", "Switch Back", "Return to your previous hotbar slot", true))
            .option(ModuleOption.number("switch_back_delay", "Return Delay", "Ticks before returning", 2, 0, 10, 1));
        register("lunge_macro", "Lunge Macro", "Selects a trident or lunge weapon and attacks the aimed target", Category.COMBAT)
            .option(ModuleOption.choice("trigger", "Trigger", "Key used to start the lunge", "R", "R", "V", "G", "B", "C", "X", "F", "F7"))
            .option(ModuleOption.bool("continuous", "Continuous", "Repeat while the trigger is held", false))
            .option(ModuleOption.number("continuous_delay", "Repeat Delay", "Ticks between held-key lunges", 10, 2, 40, 1))
            .option(ModuleOption.number("hold_ticks", "Equip Delay", "Ticks to hold the lunge weapon before attacking", 2, 0, 12, 1))
            .option(ModuleOption.bool("switch_back", "Switch Back", "Return to your previous hotbar slot", true))
            .option(ModuleOption.number("switch_back_delay", "Return Delay", "Ticks before returning", 3, 0, 12, 1));
        register("auto_trident", "Auto Trident", "Release a charged Riptide trident while holding use", Category.COMBAT)
            .option(ModuleOption.number("charge_ticks", "Charge Ticks", "Minimum trident use ticks before releasing", 10, 10, 30, 1));
        register("auto_crystal", "Auto Crystal", "Reliable hold-to-use crystal combat with optional enemy targeting", Category.COMBAT)
            .option(ModuleOption.choice("activation", "Activation", "Hold use while aiming, or run automatically near enemies", "Hold Use", "Hold Use", "Enemy Auto"))
            .option(ModuleOption.bool("place", "Place Crystals", "Place crystals on nearby obsidian or bedrock", true))
            .option(ModuleOption.bool("break", "Break Crystals", "Break reachable crystals near the enemy", true))
            .option(ModuleOption.bool("auto_obsidian", "Auto Obsidian", "Lay an obsidian base when the aimed block is not a crystal base", true))
            .option(ModuleOption.number("target_range", "Enemy Range", "Maximum range for selecting an enemy player", 7.0, 3.0, 12.0, 0.5))
            .option(ModuleOption.number("reach", "Action Reach", "Maximum crystal action distance", 3.0, 3.0, 6.0, 0.25))
            .option(ModuleOption.number("self_distance", "Self Safety", "Minimum blast-source distance from you", 3.0, 1.5, 6.0, 0.25))
            .option(ModuleOption.number("min_health", "Minimum Health", "Pause the module below this total health", 12.0, 4.0, 36.0, 1.0))
            .option(ModuleOption.number("place_delay", "Place Delay", "Ticks between crystal placements", 1, 0, 20, 1))
            .option(ModuleOption.number("break_delay", "Break Delay", "Ticks between crystal breaks", 1, 0, 20, 1))
            .option(ModuleOption.number("obsidian_delay", "Obsidian Delay", "Ticks between base placements", 2, 0, 20, 1))
            .option(ModuleOption.bool("switch_back", "Switch Back", "Return to your old hotbar slot after the macro", true))
            .option(ModuleOption.number("switch_back_delay", "Switch-Back Delay", "Ticks to keep the combat item selected in automatic mode", 2, 1, 10, 1));
        register("auto_anchor", "Anchor Macro", "Place an anchor and it fills it with glowstone and blows it up for you", Category.COMBAT)
            .option(ModuleOption.choice("activation", "Activation", "Aimed macro, or automatic placement near enemies", "Hold Use", "Hold Use", "Enemy Auto"))
            .option(ModuleOption.bool("on_look", "On Look", "In aimed mode, act when you look at an anchor without holding use", true))
            .option(ModuleOption.bool("instant", "Instant", "In aimed mode, remove artificial switch and explosion waits; charging still needs an observed charged block", true))
            .option(ModuleOption.bool("place", "Place Anchor", "Place a respawn anchor near the enemy", true))
            .option(ModuleOption.bool("charge", "Charge Anchor", "Charge an uncharged anchor using hotbar glowstone", true))
            .option(ModuleOption.bool("detonate", "Detonate Anchor", "Use a charged anchor where anchors explode", true))
            .option(ModuleOption.bool("one_charge", "One Glowstone", "Detonate after one charge; off waits for all four charges", true))
            .option(ModuleOption.number("target_range", "Enemy Range", "Maximum range for selecting an enemy player", 6.0, 3.0, 10.0, 0.5))
            .option(ModuleOption.number("reach", "Action Reach", "Maximum anchor action distance", 5.0, 3.0, 6.0, 0.25))
            .option(ModuleOption.number("self_distance", "Self Safety", "Minimum blast-source distance from you", 3.0, 1.5, 6.0, 0.25))
            .option(ModuleOption.number("min_health", "Minimum Health", "Pause below this health (0 = never pause)", 0.0, 0.0, 36.0, 1.0))
            .option(ModuleOption.number("detonate_slot", "Detonate Slot", "0 chooses a safe slot automatically; 1-9 forces a hotbar slot", 0, 0, 9, 1))
            .option(ModuleOption.number("switch_delay", "Switch Delay", "Ticks after equipping glowstone", 1, 0, 10, 1))
            .option(ModuleOption.number("place_delay", "Place Delay", "Ticks after placing an anchor", 2, 1, 10, 1))
            .option(ModuleOption.number("charge_delay", "Charge Delay", "Ticks between charging and changing item", 2, 1, 10, 1))
            .option(ModuleOption.number("explode_delay", "Explode Delay", "Ticks after changing to the detonation slot", 2, 1, 10, 1))
            .option(ModuleOption.number("cycle_delay", "Cycle Delay", "Ticks before starting another anchor cycle", 6, 1, 20, 1))
            .option(ModuleOption.bool("switch_back", "Switch Back", "Return to your old hotbar slot after detonation", true))
            .option(ModuleOption.number("switch_back_delay", "Switch-Back Delay", "Ticks to keep the detonation item selected", 2, 1, 10, 1));

        registerPvpModules();
        ExtendedModuleCatalog.register(this::register);

        register("storage_esp", "Storage ESP", "Highlights storage blocks with locked-on crosshair tracers", Category.RENDER)
            .option(ModuleOption.choice("esp_style", "Style", "Khanh adds bright corner brackets to every box; Classic is plain", "Khanh", "Khanh", "Classic"))
            .option(ModuleOption.bool("tracers", "Tracers", "Pixel-locked lines from the crosshair to every highlighted block", true))
            .option(ModuleOption.number("tracer_width", "Tracer Width", "How thick the tracer lines are", 1.0, 0.25, 4.0, 0.25))
            .option(ModuleOption.bool("ultimate_range", "Ultimate Range", "Find storage in every loaded chunk, as far as the game can see, with no limit", false))
            .option(ModuleOption.bool("chests", "Chests", "Show normal chests", true))
            .option(ModuleOption.bool("trapped_chests", "Trapped Chests", "Show trapped chests", true))
            .option(ModuleOption.bool("ender_chests", "Ender Chests", "Show ender chests", true))
            .option(ModuleOption.bool("shulkers", "Shulker Boxes", "Show shulker boxes in their dye colour", true))
            .option(ModuleOption.bool("barrels", "Barrels", "Show barrels", true))
            .option(ModuleOption.bool("spawners", "Spawners", "Show mob and trial spawners", true))
            .option(ModuleOption.bool("hoppers", "Hoppers / Dispensers", "Show hoppers, dispensers, droppers and crafters", false))
            .option(ModuleOption.bool("furnaces", "Furnaces / Brewing", "Show furnaces, smokers, blast furnaces and brewing stands", false))
            .option(ModuleOption.bool("enchanting_tables", "Enchanting Tables", "Show enchanting tables", true))
            .option(ModuleOption.bool("moving_pistons", "Moving Pistons", "Show moving piston blocks", true))
            .option(ModuleOption.color("chest_color", "Chest Colour", "Colour wheel for chests", 0xFFFFB21A))
            .option(ModuleOption.color("trapped_chest_color", "Trapped Chest Colour", "Colour wheel for trapped chests", 0xFFFF405D))
            .option(ModuleOption.color("ender_chest_color", "Ender Chest Colour", "Colour wheel for ender chests", 0xFFB76CFF))
            .option(ModuleOption.bool("shulker_dyes", "Shulker Dye Colours", "Shulkers show their own dye colour; off uses the wheel below", true))
            .option(ModuleOption.color("shulker_color", "Shulker Colour", "Colour wheel for shulker boxes", 0xFFFF44FF))
            .option(ModuleOption.color("barrel_color", "Barrel Colour", "Colour wheel for barrels", 0xFF8B5A2B))
            .option(ModuleOption.color("spawner_color", "Spawner Colour", "Colour wheel for spawners", 0xFFFF263D))
            .option(ModuleOption.color("hopper_color", "Hopper Colour", "Colour wheel for hoppers, dispensers and droppers", 0xFFF7F4F6))
            .option(ModuleOption.color("furnace_color", "Furnace Colour", "Colour wheel for furnaces and brewing stands", 0xFFB0B0B0))
            .option(ModuleOption.color("enchanting_color", "Enchanting Table Colour", "Colour wheel for enchanting tables", 0xFF5050FF))
            .option(ModuleOption.color("piston_color", "Moving Piston Colour", "Colour wheel for moving pistons", 0xFF23E200));
        register("deepslate_opacity", "Deepslate Opacity", "See through deepslate while still seeing it: ores, caves and bases show behind it", Category.RENDER)
            .option(ModuleOption.number("opacity", "Opacity", "How solid deepslate looks; lower sees further through", 0.35, 0.05, 0.9, 0.01))
            .option(ModuleOption.bool("all_variants", "All Deepslate Types", "Also cobbled, polished, bricks, tiles and reinforced deepslate", false))
            .option(ModuleOption.bool("light", "Light Behind", "Light up blocks seen through deepslate so they are not pitch black", true));
        register("sus_esp", "Sus Chunk Finder", "Marks suspicious loaded chunks on one fixed layer", Category.RENDER)
            .option(ModuleOption.number("sensitivity", "Sensitivity", "Higher values require less evidence before a chunk is marked", 7, 1, 10, 1))
            .option(ModuleOption.number("simulation_distance", "Simulation Distance", "Loaded chunk radius scanned around your player", 8, 2, 16, 1))
            .option(ModuleOption.choice("detector", "What It Detects", "Choose one original Purzo evidence type or all of them",
                "Amethyst Clusters", "Amethyst Clusters", "Kelp", "Sugar Cane", "Bamboo", "Vines",
                "Cave Vines", "Bee Nests", "Rotated Deepslate", "All Purzo Signals"));
        register("prime_chunk_finder", "Prime Chunk Finder", "Marks cultivated growth and repeated light updates separately; light activity is only a lead", Category.RENDER)
            .option(ModuleOption.number("radius", "Chunk Radius", "Nearby loaded chunks checked continuously", 10, 2, 16, 1))
            .option(ModuleOption.number("min_crops", "Crop Density", "Minimum planted crop blocks in one chunk", 40, 12, 192, 4))
            .option(ModuleOption.number("min_mature", "Mature Growth", "Minimum fully grown crop blocks in that chunk", 24, 6, 128, 2))
            .option(ModuleOption.number("min_farmland", "Farm Soil", "Minimum farmland blocks; helps reject wild vegetation", 24, 6, 128, 2))
            .option(ModuleOption.color("color", "Marker Colour", "Colour of confirmed cultivation markers", 0xFFB8A6FF))
            .option(ModuleOption.bool("labels", "Labels", "Show the crop count on marked chunks", true))
            .option(ModuleOption.bool("tracers", "Tracers", "Draw stable lines to cultivation markers", false));
        register("light_finder", "Light Finder", "Samples underground block light as an exploration lead, not proof of a base", Category.RENDER)
            .option(ModuleOption.number("radius", "Chunk Radius", "Nearby loaded chunks sampled below Y 0", 8, 2, 16, 1))
            .option(ModuleOption.number("min_level", "Light Level", "Minimum block-light level counted in a sample", 10, 1, 15, 1))
            .option(ModuleOption.number("min_samples", "Lit Samples", "Minimum bright samples before a chunk is marked", 4, 1, 32, 1))
            .option(ModuleOption.color("color", "Marker Colour", "Colour of the fixed underground-light layer", 0xFFFFC36C))
            .option(ModuleOption.bool("labels", "Labels", "Show the sample count on marked chunks", true));
        register("amethyst_esp", "Amethyst Cluster ESP", "The proven through-wall cluster, signature and geode scanner from the previous client", Category.RENDER)
            .option(ModuleOption.choice("esp_style", "Style", "Khanh: fill, outline and corner brackets. Corners: brackets only", "Khanh", "Khanh", "Corners", "Outline", "Filled"))
            .option(ModuleOption.number("esp_width", "Line Width", "Thickness of the lines", 2.0, 0.5, 5.0, 0.25))
            .option(ModuleOption.number("range", "Range", "Maximum amethyst render distance", 160, 32, 256, 16))
            .option(ModuleOption.color("color", "Colour", "Colour wheel for every amethyst marker", 0xFFFFFFFF))
            .option(ModuleOption.bool("clusters", "Clusters", "Highlight every loaded amethyst cluster", true))
            .option(ModuleOption.bool("signature_blocks", "Signature Blocks", "Highlight verified amethyst signature blocks", true))
            .option(ModuleOption.bool("geode_centres", "Geode Centres", "Show a compact marker at detected geode centres", true))
            .option(ModuleOption.number("fill_alpha", "Fill Opacity", "Strength of through-wall block fills (255 = fully solid)", 255, 8, 255, 1))
            .option(ModuleOption.number("minimum_hits", "Minimum Signature", "Blocks required before marking an amethyst chunk", 3, 1, 12, 1))
            .option(ModuleOption.bool("labels", "Labels", "Show compact amethyst and geode labels", false));
        register("hole_esp", "Hole ESP", "Highlight deep 1×1 and 3×1 shafts through walls in red", Category.RENDER)
            .option(ModuleOption.choice("esp_style", "Style", "Khanh: fill, outline and corner brackets. Corners: brackets only", "Khanh", "Khanh", "Corners", "Outline", "Filled"))
            .option(ModuleOption.number("esp_width", "Line Width", "Thickness of the lines", 2.0, 0.5, 5.0, 0.25))
            .option(ModuleOption.number("radius", "Radius", "Horizontal search radius", 12, 4, 24, 1))
            .option(ModuleOption.number("vertical_range", "Vertical Range", "Blocks above and below to check", 12, 4, 32, 1))
            .option(ModuleOption.bool("one_by_one", "1×1 Shafts", "Include single-block vertical shafts", true))
            .option(ModuleOption.bool("three_by_one", "3×1 Shafts", "Include three-block vertical shafts", true))
            .option(ModuleOption.number("min_depth", "Minimum Depth", "Consecutive enclosed layers required", 4, 4, 16, 1))
            .option(ModuleOption.number("fill_alpha", "Fill Opacity", "Red fill strength", 120, 0, 220, 1))
            .option(ModuleOption.number("line_width", "Line Width", "Red outline thickness", 2.0, 0.5, 4.0, 0.1))
            .option(ModuleOption.bool("labels", "Labels", "Show hole labels", true))
            .option(ModuleOption.color("color", "Colour", "Hole colour", 0xFFFF3B4E));
        register("spawner_chunk", "Spawner Notifier", "Red beam, nametag and spawner-only chat alert", Category.RENDER)
            .option(ModuleOption.number("range", "Range", "Maximum detection distance", 128, 32, 256, 16))
            .option(ModuleOption.bool("beam", "Red Beam", "Draw a vertical red locator beam", true))
            .option(ModuleOption.bool("box", "Spawner Box", "Draw a solid red box on the spawner", true))
            .option(ModuleOption.bool("nametag", "Nametag", "Show spawner type and distance", true))
            .option(ModuleOption.bool("distance", "Distance", "Include distance in the spawner nametag", true))
            .option(ModuleOption.bool("range_ring", "Range Ring", "Draw a fixed 16-block ring around the spawner", true))
            .option(ModuleOption.bool("tracers", "Stable Tracers", "Draw a thin line to each detected spawner", false))
            .option(ModuleOption.bool("notification", "Notification", "Alert once when a nearby spawner is found", true))
            .option(ModuleOption.bool("sound", "Alert Sound", "Play a sound with the notification", true));
        register("player_esp", "Player ESP", "Highlights nearby players through walls", Category.RENDER)
            .option(ModuleOption.choice("esp_style", "Style", "Khanh: fill, outline and corner brackets. Corners: brackets only", "Khanh", "Khanh", "Corners", "Outline", "Filled"))
            .option(ModuleOption.number("esp_width", "Line Width", "Thickness of the lines", 2.0, 0.5, 5.0, 0.25))
            .option(ModuleOption.number("range", "Range", "Maximum player render distance", 128, 16, 256, 16))
            .option(ModuleOption.bool("labels", "Labels", "Show privacy-aware player labels", true))
            .option(ModuleOption.bool("label_health", "Label Health", "Show the player's current health beside their name", true))
            .option(ModuleOption.bool("label_armor", "Label Armour", "Show armour strength in the player label", false))
            .option(ModuleOption.bool("label_item", "Label Item", "Show the held item in the player label", false))
            .option(ModuleOption.bool("tracers", "Tracers", "Thin crosshair-to-player lines with fixed world endpoints", true))
            .option(ModuleOption.number("tracer_width", "Tracer Width", "How thick player tracer lines are", 0.65, 0.25, 4.0, 0.05))
            .option(ModuleOption.bool("glow_outline", "Glow Outline", "Local silhouette glow and soft lilac edge, visible through walls", true))
            .option(ModuleOption.bool("glow_ring", "Glow Ring", "Soft animated rings around nearby players", true))
            .option(ModuleOption.number("ring_size", "Ring Size", "Radius of the player glow ring", 0.72, 0.45, 1.2, 0.05))
            .option(ModuleOption.bool("soft_fill", "Soft Fill", "Subtle fill behind the player outline", false))
            .option(ModuleOption.color("color", "Player Colour", "Choose the player outline and tracer colour", 0xFFD2A8FF));
        register("name_tags", "Name Tags", "Clean labels for nearby players, mobs and dropped items", Category.RENDER)
            .option(ModuleOption.bool("players", "Players", "Show player names and health", true))
            .option(ModuleOption.bool("mobs", "Mobs", "Show mob names and health", false))
            .option(ModuleOption.choice("health", "Health", "Show health as a number, a bar, both, or not at all", "Both", "Both", "Bar", "Number", "Off"))
            .option(ModuleOption.bool("health_colours", "Health Colours", "Green when healthy, yellow when hurt, red when low", true))
            .option(ModuleOption.bool("items", "Items", "Name every item on the ground, with its count", true))
            .option(ModuleOption.number("item_range", "Item Range", "How far away items get a name", 24, 4, 64, 4))
            .option(ModuleOption.number("range", "Range", "Maximum distance for labels", 48, 8, 96, 8))
            .option(ModuleOption.color("color", "Colour", "Choose the label colour", 0xFFEDEDF4));
        register("bedrock_void_esp", "Bedrock Void ESP", "Highlights real openings through the world's bottom bedrock layer", Category.RENDER)
            .option(ModuleOption.number("range", "Range", "Horizontal scan distance around you", 48, 8, 96, 8))
            .option(ModuleOption.color("color", "Colour", "Choose any outline and fill colour", 0xFF8B5CFF))
            .option(ModuleOption.bool("filled", "Filled", "Draw a translucent fill through the ground", true))
            .option(ModuleOption.number("fill_alpha", "Fill Opacity", "Opacity of the through-wall fill", 72, 0, 220, 1))
            .option(ModuleOption.bool("labels", "Labels", "Show a compact VOID label above openings", true));
        register("projectile_pearl", "Trajectories", "Predicts the paths of flying pearls, arrows and tridents", Category.RENDER)
            .option(ModuleOption.number("range", "Range", "Maximum projectile render distance", 160, 32, 256, 16))
            .option(ModuleOption.color("color", "Colour", "Choose any trajectory colour", 0xFF66D9FF))
            .option(ModuleOption.number("line_width", "Line Width", "Thickness of the predicted path", 1.5, 0.5, 4.0, 0.25))
            .option(ModuleOption.bool("pearls", "Ender Pearls", "Predict thrown ender pearls", true))
            .option(ModuleOption.bool("arrows", "Arrows", "Predict flying arrows", true))
            .option(ModuleOption.bool("tridents", "Tridents", "Predict flying tridents", true))
            .option(ModuleOption.bool("impact", "Impact Marker", "Mark the predicted landing point", true))
            .option(ModuleOption.bool("labels", "Labels", "Show projectile distance at the prediction point", true));
        register("health_alert", "Health Alert", "Blinking red warning, LOW HP readout and a ping when your health gets low", Category.RENDER)
            .option(ModuleOption.number("threshold", "Health Threshold", "Warn at or below this many health points (2 = one heart)", 8, 1, 30, 1))
            .option(ModuleOption.choice("style", "Style", "Blink flashes on and off; Pulse breathes; Solid stays on", "Blink", "Blink", "Pulse", "Solid"))
            .option(ModuleOption.color("color", "Colour", "Colour of the warning", 0xFFFF2A3D))
            .option(ModuleOption.number("opacity", "Strength", "How bright the warning glow is", 65, 10, 150, 5))
            .option(ModuleOption.number("size", "Glow Size", "How far the red glow reaches in from the screen edge", 48, 4, 120, 2))
            .option(ModuleOption.number("blink_speed", "Blink Speed", "Blinks per second (gets faster the lower you are)", 1.0, 0.3, 4.0, 0.1))
            .option(ModuleOption.bool("show_text", "LOW HP Text", "Show LOW HP with your hearts in the middle of the screen", true))
            .option(ModuleOption.number("text_size", "Text Size", "Size of the LOW HP text", 1.6, 0.8, 3.0, 0.1))
            .option(ModuleOption.bool("sound", "Warning Sound", "Ping while your health is low", true))
            .option(ModuleOption.number("sound_every", "Sound Every", "Seconds between pings (faster when very low)", 1.5, 0.5, 5.0, 0.25))
            .option(ModuleOption.bool("message", "Notify", "Show a Low HP message above the hotbar when you first drop low", true))
            .option(ModuleOption.bool("count_absorption", "Count Absorption", "Count golden absorption hearts as health", false));
        register("freecam", "Freecam", "Detached camera while your real player keeps gravity, movement and mining", Category.RENDER)
            .option(ModuleOption.number("speed", "Camera Speed", "Horizontal camera speed; scroll in Freecam to change it live", 0.65, 0.05, 5.0, 0.05))
            .option(ModuleOption.bool("scroll_speed", "Scroll for Speed", "Mouse wheel speeds the camera up or down while in Freecam", true))
            .option(ModuleOption.number("vertical_speed", "Camera Lift", "Vertical viewing-camera speed", 0.55, 0.15, 2.0, 0.05))
            .option(ModuleOption.bool("aim_follows_pitch", "Look Up/Down Aims", "Looking up or down in Freecam also tilts your player's aim, to mine above or below", true))
            .option(ModuleOption.bool("mining", "Mining", "Hold attack to keep mining what your player was looking at when Freecam started, like Free Look", true))
            .option(ModuleOption.number("smoothness", "Smoothness", "How softly the camera speeds up, glides to a stop and turns", 0.55, 0.0, 1.0, 0.05))
            .option(ModuleOption.bool("smooth_look", "Smooth Look", "Ease the view toward the mouse for buttery turning", true))
            .option(ModuleOption.number("chunk_distance", "Render Distance", "Chunks retained around the detached camera", 12, 2, 32, 1))
            .option(ModuleOption.bool("body_follows_keys", "Body Follows Keys", "Also move the real player with Freecam WASD", false));
        register("freecam_mining", "Freecam Mining", "Keep mining with your player while the Freecam camera flies around", Category.PLAYER);
        register("auto_schematic_builder", "Auto Schematic Builder", "Build the selected Litematica placement with Khanh Client's own safe walking; singleplayer or an explicitly allowed private server", Category.MISC)
            .option(ModuleOption.action("start_build", "Start Build", "Enable the builder and explain anything that prevents it from starting", WorkAutomationService::startBuilder))
            .option(ModuleOption.action("check_build", "Check Setup", "Show why the builder is currently idle", WorkAutomationService::checkBuilderSetup))
            .option(ModuleOption.action("pause_build", "Pause Build", "Stop walking and placing without losing your progress", WorkAutomationService::pauseBuilder))
            .option(ModuleOption.action("resume_build", "Resume Build", "Continue the selected placement", WorkAutomationService::resumeBuilder))
            .option(ModuleOption.action("stop_build", "Stop Build", "Turn off the builder and clear its current route", WorkAutomationService::stopBuilder))
            .option(ModuleOption.text("private_server", "Private Server Address", "Exact address of your own server where building automation is permitted; leave empty for singleplayer only", "", 128))
            .option(ModuleOption.number("reach", "Build Reach", "Maximum normal block placement reach", 4.5, 3.0, 4.5, 0.25))
            .option(ModuleOption.number("delay", "Place Delay", "Ticks between placement attempts", 3, 1, 20, 1))
            .option(ModuleOption.bool("restore_slot", "Restore Slot", "Return to the original hotbar slot when stopped", true))
            .option(ModuleOption.bool("auto_walk", "Auto Walk", "Walk to reachable work areas", true))
            .option(ModuleOption.number("walk_radius", "Walk Radius", "Maximum horizontal path distance in blocks", 32, 8, 64, 1))
            .option(ModuleOption.number("route_nodes", "Route Search", "Maximum path nodes checked per route", 2400, 200, 6000, 100))
            .option(ModuleOption.number("scan_budget", "Scan Speed", "Schematic positions checked each tick near your player", 2048, 256, 8192, 256))
            .option(ModuleOption.number("vertical_scan", "Vertical Scan", "How many blocks above and below you to search for the next build target", 16, 4, 48, 2))
            .option(ModuleOption.number("path_height", "Path Height", "Maximum height difference explored by safe walking", 8, 2, 24, 1))
            .option(ModuleOption.number("route_replan", "Route Refresh", "Ticks before recalculating a walking route", 40, 10, 120, 5))
            .option(ModuleOption.number("stuck_timeout", "Stuck Timeout", "Ticks without movement before abandoning a route", 40, 10, 120, 5))
            .option(ModuleOption.number("walk_turn_speed", "Walking Turn", "Maximum degrees to turn toward the next safe step each tick", 22, 5, 60, 1))
            .option(ModuleOption.number("walking_ease", "Walking Ease", "Ease the final part of each turn while following a route", 0.65, 0.2, 1.0, 0.05))
            .option(ModuleOption.number("retry_route", "Retry Blocked Route", "Ticks before trying a previously unreachable work block again", 200, 40, 1200, 20))
            .option(ModuleOption.bool("step_up", "Step Up", "Allow a one-block climb when a safe route needs it", true))
            .option(ModuleOption.bool("step_down", "Step Down", "Allow a one-block descent onto solid ground", true))
            .option(ModuleOption.number("min_health", "Minimum Health", "Pause at or below this many health points", 6, 1, 20, 1))
            .option(ModuleOption.number("min_hunger", "Minimum Hunger", "Pause below this food level", 6, 0, 20, 1))
            .option(ModuleOption.bool("pause_players", "Pause Near Players", "Stop when another player is nearby", false))
            .option(ModuleOption.number("player_radius", "Player Distance", "Distance used by Pause Near Players", 8, 2, 32, 1))
            .option(ModuleOption.number("aim_speed", "Aim Speed", "Maximum degrees to turn per tick", 35, 5, 90, 1))
            .option(ModuleOption.number("look_smoothness", "Look Smoothness", "Ease into the build target instead of turning at a fixed rate", 0.45, 0.15, 1.0, 0.05))
            .option(ModuleOption.number("look_settle", "Look Settle", "Ticks to hold a stable aim before placing", 2, 0, 8, 1))
            .option(ModuleOption.bool("idle_glances", "Look Around", "Glance around gently while waiting for the next work area", true))
            .option(ModuleOption.number("glance_interval", "Glance Interval", "Minimum ticks between idle looks", 80, 30, 240, 10))
            .option(ModuleOption.number("glance_angle", "Glance Angle", "Maximum side-to-side idle look in degrees", 18, 5, 40, 1))
            .option(ModuleOption.number("glance_duration", "Glance Duration", "Ticks spent looking away before returning to the work direction", 12, 4, 30, 1))
            .option(ModuleOption.choice("layer_mode", "Build Layers", "Limit which schematic height is built", "All", "All", "Player Level", "Below Player"))
            .option(ModuleOption.choice("build_order", "Build Order", "Prioritize nearby, lower, or upper blocks", "Nearest", "Nearest", "Bottom First", "Top First"))
            .option(ModuleOption.bool("skip_falling", "Skip Falling Blocks", "Avoid unsupported sand, gravel, and other gravity blocks", true))
            .option(ModuleOption.bool("skip_block_entities", "Skip Block Entities", "Avoid blocks that need stored data such as chests and signs", true))
            .option(ModuleOption.bool("skip_waterlogged", "Skip Waterlogged", "Avoid schematic blocks containing water", true))
            .option(ModuleOption.bool("only_full_blocks", "Full Blocks Only", "Build only solid full-cube blocks", false))
            .option(ModuleOption.choice("hotbar_order", "Hotbar Search", "Choose the first or last matching hotbar slot", "First", "First", "Last"))
            .option(ModuleOption.bool("inventory_refill", "Inventory Refill", "Move a matching stack from your inventory into an empty hotbar slot when needed", true))
            .option(ModuleOption.number("verify_delay", "Placement Check", "Ticks allowed for the world to confirm a placed block", 8, 2, 30, 1))
            .option(ModuleOption.bool("pause_wrong_state", "Pause Wrong State", "Pause when a placed block has the wrong orientation or properties", true))
            .option(ModuleOption.number("retry_failed_place", "Retry Failed Place", "Ticks before retrying a rejected or missing placement", 40, 10, 400, 10))
            .option(ModuleOption.number("retry_missing", "Retry Missing Material", "Ticks before retrying a skipped block whose material is unavailable", 1200, 100, 6000, 100))
            .option(ModuleOption.choice("missing_material", "Missing Materials", "Pause or skip a block when its material is absent", "Pause", "Pause", "Skip"))
            .option(ModuleOption.number("max_placements", "Placement Limit", "Stop after this many accepted placements; zero is unlimited", 0, 0, 4096, 16))
            .option(ModuleOption.bool("status_messages", "Status Messages", "Show automation notices above the hotbar", true));
        register("auto_mineout", "Auto Mineout", "Excavate a selected box with normal mining and safe walking routes", Category.MISC)
            .option(ModuleOption.text("private_server", "Private Server Address", "Exact address of your own server where excavation automation is permitted; leave empty for singleplayer only", "", 128))
            .option(ModuleOption.action("corner_a", "Set Corner A", "Use your current feet position as the first corner", () -> WorkAutomationService.setMineCorner(true)))
            .option(ModuleOption.action("corner_b", "Set Corner B", "Use your current feet position as the opposite corner", () -> WorkAutomationService.setMineCorner(false)))
            .option(ModuleOption.action("clear_corners", "Clear Corners", "Clear the selected excavation area", WorkAutomationService::clearMineCorners))
            .option(ModuleOption.number("reach", "Mine Reach", "Maximum normal block mining reach", 4.5, 3.0, 4.5, 0.25))
            .option(ModuleOption.number("delay", "Block Delay", "Ticks between completed blocks", 2, 0, 20, 1))
            .option(ModuleOption.bool("auto_walk", "Auto Walk", "Walk to reachable work areas", true))
            .option(ModuleOption.number("walk_radius", "Walk Radius", "Maximum horizontal path distance in blocks", 32, 8, 64, 1))
            .option(ModuleOption.number("route_nodes", "Route Search", "Maximum path nodes checked per route", 2400, 200, 6000, 100))
            .option(ModuleOption.number("scan_budget", "Scan Speed", "Blocks checked each tick near your player", 2048, 256, 8192, 256))
            .option(ModuleOption.number("vertical_scan", "Vertical Scan", "How many blocks above and below you to search for the next mine target", 16, 4, 48, 2))
            .option(ModuleOption.number("path_height", "Path Height", "Maximum height difference explored by safe walking", 8, 2, 24, 1))
            .option(ModuleOption.number("route_replan", "Route Refresh", "Ticks before recalculating a walking route", 40, 10, 120, 5))
            .option(ModuleOption.number("stuck_timeout", "Stuck Timeout", "Ticks without movement before abandoning a route", 40, 10, 120, 5))
            .option(ModuleOption.number("walk_turn_speed", "Walking Turn", "Maximum degrees to turn toward the next safe step each tick", 22, 5, 60, 1))
            .option(ModuleOption.number("walking_ease", "Walking Ease", "Ease the final part of each turn while following a route", 0.65, 0.2, 1.0, 0.05))
            .option(ModuleOption.number("retry_route", "Retry Blocked Route", "Ticks before trying a previously unreachable work block again", 200, 40, 1200, 20))
            .option(ModuleOption.bool("step_up", "Step Up", "Allow a one-block climb when a safe route needs it", true))
            .option(ModuleOption.bool("step_down", "Step Down", "Allow a one-block descent onto solid ground", true))
            .option(ModuleOption.number("min_health", "Minimum Health", "Pause at or below this many health points", 6, 1, 20, 1))
            .option(ModuleOption.number("min_hunger", "Minimum Hunger", "Pause below this food level", 6, 0, 20, 1))
            .option(ModuleOption.bool("pause_players", "Pause Near Players", "Stop when another player is nearby", false))
            .option(ModuleOption.number("player_radius", "Player Distance", "Distance used by Pause Near Players", 8, 2, 32, 1))
            .option(ModuleOption.number("aim_speed", "Aim Speed", "Maximum degrees to turn per tick", 35, 5, 90, 1))
            .option(ModuleOption.number("look_smoothness", "Look Smoothness", "Ease into the mine target instead of turning at a fixed rate", 0.45, 0.15, 1.0, 0.05))
            .option(ModuleOption.number("look_settle", "Look Settle", "Ticks to hold a stable aim before mining", 2, 0, 8, 1))
            .option(ModuleOption.bool("idle_glances", "Look Around", "Glance around gently while waiting for the next work area", true))
            .option(ModuleOption.number("glance_interval", "Glance Interval", "Minimum ticks between idle looks", 80, 30, 240, 10))
            .option(ModuleOption.number("glance_angle", "Glance Angle", "Maximum side-to-side idle look in degrees", 18, 5, 40, 1))
            .option(ModuleOption.number("glance_duration", "Glance Duration", "Ticks spent looking away before returning to the work direction", 12, 4, 30, 1))
            .option(ModuleOption.choice("mine_order", "Mine Order", "Prioritize nearby, upper, or lower blocks", "Nearest", "Nearest", "Top Down", "Bottom Up"))
            .option(ModuleOption.bool("skip_ores", "Keep Ores", "Leave ore blocks inside the selection intact", true))
            .option(ModuleOption.bool("skip_containers", "Keep Containers", "Leave chests, barrels, and similar storage intact", true))
            .option(ModuleOption.bool("skip_spawners", "Keep Spawners", "Leave mob spawners intact", true))
            .option(ModuleOption.bool("skip_valuable", "Keep Valuable Blocks", "Leave ancient debris and resource blocks intact", true))
            .option(ModuleOption.bool("preserve_floor", "Protect Footing", "Use the adjustable footing buffer; the block below you is always protected", true))
            .option(ModuleOption.number("floor_margin", "Footing Radius", "Horizontal radius of protected floor blocks", 2, 1, 4, 1))
            .option(ModuleOption.number("liquid_buffer", "Liquid Buffer", "Do not mine close to water or lava", 1, 1, 4, 1))
            .option(ModuleOption.bool("show_boundary", "Show Boundary", "Outline the selected mineout area", true))
            .option(ModuleOption.color("boundary_color", "Boundary Colour", "Colour of the mineout selection outline", 0xFF83D7F5));
        register("skin_protect", "Skin Protect", "Wear any player's skin by typing their username, or pick one from the gallery (only you see it)", Category.PLAYER)
            .option(ModuleOption.action("skin_gallery", "NameMC Skin Gallery", "Browse 120 popular skins and choose one", () ->
                MinecraftClient.getInstance().setScreen(new SkinGalleryScreen(MinecraftClient.getInstance().currentScreen))))
            .option(ModuleOption.text("skin_id", "Selected Skin", "NameMC skin ID; use the gallery to choose", "", 16))
            .option(ModuleOption.bool("slim_arms", "Slim Arms", "Use the slim model for gallery skins that need it", false))
            .option(ModuleOption.text("skin_ign", "Wear Player's Skin", "Type any Minecraft username to wear their skin (beats the gallery)", "", 16))
            .option(ModuleOption.choice("apply_to", "Apply To", "Choose whose skins are replaced", "Self", "Self", "Others", "Everyone"));
        register("freelook", "Free Look", "Look around in third person without turning your player; optionally pass the camera through walls", Category.RENDER)
            .option(ModuleOption.bool("through_walls", "Through Walls", "Disable third-person camera collision in Freelook; this does not X-ray solid blocks", true))
            .option(ModuleOption.bool("see_nametag", "See Nametag", "Show your privacy-aware nametag above your body while using Freelook", true));
        register("block_outline", "Block Outline", "Clean custom-colour line around only the block under your crosshair", Category.RENDER)
            .option(ModuleOption.number("range", "Range", "Maximum distance for the outlined block", 6.0, 3.0, 32.0, 0.5))
            .option(ModuleOption.color("color", "Outline Colour", "Choose any outline colour, including pure black", 0xFFFFFFFF))
            .option(ModuleOption.number("line_width", "Line Width", "Thickness of the clean outer line", 2.5, 0.5, 6.0, 0.25))
            .option(ModuleOption.bool("bounce", "Block Hop", "Hop smoothly whenever the targeted block changes", true))
            .option(ModuleOption.number("bounce_strength", "Hop Height", "Height of the block-to-block hop", 0.10, 0.0, 0.22, 0.01))
            .option(ModuleOption.number("bounce_speed", "Hop Speed", "Speed of the move between targeted blocks", 1.8, 0.5, 4.0, 0.1))
            .option(ModuleOption.bool("fill_enabled", "Soft Fill", "Add a subtle fill inside the outlined block", false))
            .option(ModuleOption.color("fill_color", "Fill Colour", "Choose the block fill colour", 0xFF84B7FF))
            .option(ModuleOption.number("fill_opacity", "Fill Opacity", "Strength of the optional block fill", .18, .05, .45, .01));
        register("full_bright", "Full Bright", "Brightens dark areas locally without changing the world", Category.RENDER)
            .option(ModuleOption.number("brightness", "Brightness", "Local gamma strength", 12.0, 2.0, 16.0, 0.5));
        register("shader_fog", "Custom Sky", "Skies from your resource packs, or animated water, caustic and aurora patterns", Category.VISUAL)
            .option(ModuleOption.choice("sky", "Sky", "Pick a sky from the pictures", "Water", dev.khanh.feature.SkyService.SKIES))
            .option(ModuleOption.number("rotation_speed", "Sky Motion", "How fast picture skies turn, in degrees per second", 0.6, 0.0, 3.0, 0.1))
            .option(ModuleOption.color("color", "Colour", "Tint the entire animated sky", 0xFF8078D8))
            .option(ModuleOption.number("speed", "Speed", "Sky pattern animation speed", 1, .1, 5, .1))
            .option(ModuleOption.number("scale", "Scale", "Sky pattern density", 5, 1, 20, .5))
            .option(ModuleOption.number("intensity", "Intensity", "Brightness of the sky pattern", .7, .05, 1.5, .05))
            .option(ModuleOption.bool("time_enabled", "Custom Time", "Lock the time of day you see (only for you)", false))
            .option(ModuleOption.number("time", "Time of Day", "0 sunrise, 6000 noon, 13000 sunset, 18000 midnight", 6000, 0, 24000, 250))
            .option(ModuleOption.bool("fog_enabled", "Custom Fog", "Your own fog colour and distance", false))
            .option(ModuleOption.color("fog_color", "Fog Colour", "Colour of the fog", 0xFF183047))
            .option(ModuleOption.number("fog", "Fog Distance", "How far away the fog starts", 192, 16, 512, 8));
        register("motion_blur", "Motion Blur", "GPU temporal blur applied to world movement before hands and HUD", Category.VISUAL)
            .option(ModuleOption.number("strength", "Strength", "Previous-frame blend; lower values stay sharper", .22, .05, .65, .02))
            .option(ModuleOption.number("minimum_fps", "Minimum FPS", "Automatically pause the effect below this frame rate", 30, 10, 120, 5));
        register("totem_styles", "Totem Styles", "97 totem styles from your packs and Khanh Client, with pop animation and particle options", Category.VISUAL)
            .option(ModuleOption.choice("style", "Totem Style", "How your totems look", dev.khanh.feature.TotemService.STYLES[0], dev.khanh.feature.TotemService.STYLES))
            .option(ModuleOption.bool("random_each_pop", "Random Each Pop", "Switch to a random totem style every time you pop one", false))
            .option(ModuleOption.choice("pop_animation", "Pop Animation", "The big totem that flies up your screen when you pop", "Normal", "Normal", "Quick", "Off"))
            .option(ModuleOption.choice("pop_particles", "Pop Particles", "The burst of particles when a totem pops", "Normal", "Normal", "Coloured", "Off"))
            .option(ModuleOption.color("particle_color", "Particle Colour", "Colour wheel for Coloured pop particles", 0xFFB76CFF));
        register("orbit_orbs", "Orbiting Orbs", "Solid 3D orbs with comet tails circling your body", Category.VISUAL)
            .option(ModuleOption.color("color", "Colour", "Cosmetic colour", 0xFF9EE8FF))
            .option(ModuleOption.bool("rainbow", "Rainbow", "Slowly cycle through every colour", false))
            .option(ModuleOption.number("size", "Size", "How big it is", 1.0, 0.5, 1.8, 0.05))
            .option(ModuleOption.number("count", "Orbs", "How many orbs circle you", 3, 1, 6, 1));
        register("rune_circle", "Rune Circle", "A slowly turning magic circle of runes under your feet", Category.VISUAL)
            .option(ModuleOption.color("color", "Colour", "Cosmetic colour", 0xFFB58CFF))
            .option(ModuleOption.bool("rainbow", "Rainbow", "Slowly cycle through every colour", false))
            .option(ModuleOption.number("size", "Size", "How big it is", 1.0, 0.5, 1.8, 0.05));
        register("motion_trail", "Motion Trail", "A glowing ribbon that follows you as you move", Category.VISUAL)
            .option(ModuleOption.color("color", "Colour", "Cosmetic colour", 0xFF7FE6F7))
            .option(ModuleOption.bool("rainbow", "Rainbow", "Slowly cycle through every colour", false))
            .option(ModuleOption.number("size", "Size", "How big it is", 1.0, 0.5, 1.8, 0.05))
            .option(ModuleOption.number("length", "Length", "How long the trail is", 24, 8, 48, 2));
        register("crown", "Crown", "A solid gold-style crown with gems, floating on your head", Category.VISUAL)
            .option(ModuleOption.color("color", "Colour", "Cosmetic colour", 0xFFFFD34A))
            .option(ModuleOption.bool("rainbow", "Rainbow", "Slowly cycle through every colour", false))
            .option(ModuleOption.number("size", "Size", "How big it is", 1.0, 0.5, 1.8, 0.05));
        register("cat_ears", "Cat Ears", "Solid 3D cat ears that turn with your head", Category.VISUAL)
            .option(ModuleOption.color("color", "Colour", "Cosmetic colour", 0xFFFF9EC8))
            .option(ModuleOption.bool("rainbow", "Rainbow", "Slowly cycle through every colour", false))
            .option(ModuleOption.number("size", "Size", "How big it is", 1.0, 0.5, 1.8, 0.05));
        register("devil_horns", "Devil Horns", "Solid curved 3D horns that turn with your head", Category.VISUAL)
            .option(ModuleOption.color("color", "Colour", "Cosmetic colour", 0xFFFF4D6A))
            .option(ModuleOption.bool("rainbow", "Rainbow", "Slowly cycle through every colour", false))
            .option(ModuleOption.number("size", "Size", "How big it is", 1.0, 0.5, 1.8, 0.05));
        register("spirit_pet", "Spirit Pet", "A solid spinning crystal that floats along beside you", Category.VISUAL)
            .option(ModuleOption.color("color", "Colour", "Cosmetic colour", 0xFF9EFFC8))
            .option(ModuleOption.bool("rainbow", "Rainbow", "Slowly cycle through every colour", false))
            .option(ModuleOption.number("size", "Size", "How big it is", 1.0, 0.5, 1.8, 0.05));
        register("saturn_ring", "Saturn Ring", "A solid tilted planet ring with tumbling moons", Category.VISUAL)
            .option(ModuleOption.color("color", "Colour", "Cosmetic colour", 0xFFFFB86B))
            .option(ModuleOption.bool("rainbow", "Rainbow", "Slowly cycle through every colour", false))
            .option(ModuleOption.number("size", "Size", "How big it is", 1.0, 0.5, 1.8, 0.05));
        register("footprints", "Glow Footprints", "Glowing footprints that fade behind you", Category.VISUAL)
            .option(ModuleOption.color("color", "Colour", "Cosmetic colour", 0xFF7FE6F7))
            .option(ModuleOption.bool("rainbow", "Rainbow", "Slowly cycle through every colour", false))
            .option(ModuleOption.number("size", "Size", "How big it is", 1.0, 0.5, 1.8, 0.05));
        register("star_aura", "Star Aura", "Tiny stars that rise and twinkle around you", Category.VISUAL)
            .option(ModuleOption.color("color", "Colour", "Cosmetic colour", 0xFFFFF2B0))
            .option(ModuleOption.bool("rainbow", "Rainbow", "Slowly cycle through every colour", false))
            .option(ModuleOption.number("size", "Size", "How big it is", 1.0, 0.5, 1.8, 0.05))
            .option(ModuleOption.number("density", "Density", "How many stars rise", 1.0, 0.3, 2.0, 0.1));
        register("rain_cloud", "Rain Cloud", "Your own little rain cloud, raining just on you", Category.VISUAL)
            .option(ModuleOption.color("color", "Colour", "Cosmetic colour", 0xFF7FB8FF))
            .option(ModuleOption.bool("rainbow", "Rainbow", "Slowly cycle through every colour", false))
            .option(ModuleOption.number("size", "Size", "How big it is", 1.0, 0.5, 1.8, 0.05));
        register("energy_shield", "Energy Shield", "A pulsing faceted glass bubble around you", Category.VISUAL)
            .option(ModuleOption.color("color", "Colour", "Cosmetic colour", 0xFF7FE6F7))
            .option(ModuleOption.bool("rainbow", "Rainbow", "Slowly cycle through every colour", false))
            .option(ModuleOption.number("size", "Size", "How big it is", 1.0, 0.5, 1.8, 0.05));
        register("custom_glint", "Custom Glint", "Enchanted items and armour get a flowing, sparkling PNG wrap instead of vanilla glint", Category.VISUAL)
            .option(ModuleOption.choice("theme", "Wrap Texture", "The PNG enchanted items and armour are wrapped in", "Galaxy",
                dev.khanh.feature.GlintPattern.THEMES))
            .option(ModuleOption.number("strength", "Strength", "How fully the PNG covers the item", 1.0, 0.15, 1.0, 0.05))
            .option(ModuleOption.number("speed", "Flow Speed", "How fast the wrap drifts; 0 holds it still", 0.35, 0.0, 2.0, 0.05))
            .option(ModuleOption.number("sparkle", "Sparkle", "Twinkling stars over the wrap; 0 turns them off", 3.0, 0.0, 5.0, 0.1))
            .option(ModuleOption.number("sparkle_size", "Sparkle Size", "Bigger, fewer stars or tiny, dense ones", 1.0, 0.4, 2.5, 0.05))
            .option(ModuleOption.number("scale", "Pattern Scale", "Zoom the wrap picture in or out", 1.0, 0.25, 4.0, 0.05))
            .option(ModuleOption.number("direction", "Flow Direction", "Which way the wrap drifts, in degrees", 30.0, 0.0, 360.0, 5.0))
            .option(ModuleOption.number("spin", "Pattern Spin", "Slowly rotate the wrap; 0 holds it straight", 0.0, 0.0, 2.0, 0.05))
            .option(ModuleOption.number("brightness", "Brightness", "Make the wrap darker or brighter", 1.0, 0.4, 2.0, 0.05))
            .option(ModuleOption.number("pulse", "Pulse", "Breathe the wrap's brightness in and out", 0.0, 0.0, 1.0, 0.05))
            .option(ModuleOption.bool("tint", "Tint", "Colour the wrap with the wheel below", false))
            .option(ModuleOption.color("tint_color", "Tint Colour", "Colour wheel for the wrap tint", 0xFFFF4FD8))
            .option(ModuleOption.number("tint_amount", "Tint Amount", "How strongly the tint colours the wrap", 0.6, 0.1, 1.0, 0.05))
            .option(ModuleOption.bool("rainbow", "Rainbow", "Cycle the wrap through every colour", false))
            .option(ModuleOption.bool("armor", "Armour", "Wrap enchanted armour you and others wear", true))
            .option(ModuleOption.bool("inventory", "Inventory", "Wrap enchanted items in your inventory and hotbar", true))
            .option(ModuleOption.bool("ground", "Dropped Items", "Wrap enchanted items on the ground and in item frames", true));
        register("inventory_color", "Inventory Colour", "Recolours chests, your inventory and every container screen", Category.VISUAL)
            .option(ModuleOption.color("color", "Colour", "Colour the inventory is tinted", 0xFF3A3A44))
            .option(ModuleOption.bool("rainbow", "Rainbow", "Slowly cycle through every colour instead", false));
        register("clear_water", "Clear Water", "Under water looks exactly like being at the surface: no fog, no blue tint, no zoom", Category.VISUAL)
            .option(ModuleOption.bool("hide_overlay", "Hide Overlay", "Remove the blue underwater screen wash", true))
            .option(ModuleOption.bool("normal_fov", "Normal FOV", "Stop the view zooming in under water", true));
        register("custom_crosshair", "Custom Crosshair", "Your own crosshair with a live preview, outline and a colour for when you aim at a player", Category.VISUAL)
            .option(ModuleOption.choice("style", "Style", "Crosshair shape", "Cross", "Cross", "Cross + Dot", "Dot", "Circle", "Circle + Dot", "Bracket", "T-Shape", "Chevron"))
            .option(ModuleOption.number("size", "Size", "Distance from the centre", 7, 2, 16, 1))
            .option(ModuleOption.number("gap", "Gap", "Clear centre gap", 3, 0, 8, 1))
            .option(ModuleOption.number("thickness", "Thickness", "Line thickness in pixels", 1, 1, 4, 1))
            .option(ModuleOption.bool("outline", "Outline", "A thin dark edge so it reads on any background", true))
            .option(ModuleOption.color("color", "Colour", "Crosshair colour", 0xFFF2F2F6))
            .option(ModuleOption.bool("target_change", "Colour on Player", "Switch colour while you aim at a player", true))
            .option(ModuleOption.color("target_color", "Player Colour", "Colour while aiming at a player", 0xFFFF4D6A));
        register("elytra_colors", "Custom Elytra", "Locally colour your elytra and show the Khanh Client logo on both wings", Category.VISUAL)
            .option(ModuleOption.color("color", "Wing Colour", "Choose any wing colour", 0xFF8B7BFF));
        register("fov_unbound", "FOV Unbound", "Custom camera field of view beyond the vanilla slider", Category.VISUAL)
            .option(ModuleOption.number("fov", "Field of View", "Camera FOV while enabled", 110, 30, 170, 1));
        register("zoom", "Zoom", "Smooth camera zoom: hold the key to zoom, scroll to zoom further in or out", Category.VISUAL)
            .option(ModuleOption.number("fov", "Zoom FOV", "Smaller values zoom further", 24, 5, 70, 1))
            .option(ModuleOption.bool("hold", "Hold to Zoom", "Only zoom while you hold the keybind; let go to zoom out", true))
            .option(ModuleOption.bool("scroll", "Scroll to Adjust", "Scroll while zoomed to zoom further in or out", true))
            .option(ModuleOption.bool("reset", "Reset Each Time", "Start from Zoom FOV every time you zoom (off remembers your scroll)", true))
            .option(ModuleOption.bool("smooth", "Smooth Zoom", "Glide in and out instead of snapping", true))
            .option(ModuleOption.number("smooth_speed", "Smooth Speed", "How fast the zoom glides", 10, 2, 30, 1))
            .option(ModuleOption.bool("lower_sensitivity", "Lower Sensitivity", "Slow the mouse down while zoomed for steadier aim", true));
        register("camera_clip", "Camera Clip", "Let the third-person camera pass through nearby blocks", Category.VISUAL)
            .option(ModuleOption.number("distance", "Camera Distance", "Third-person camera distance while enabled", 4.0, 1.0, 12.0, 0.25));
        register("offscreen_players", "Player Pointers", "Smooth edge pointers for nearby players outside your view", Category.VISUAL)
            .option(ModuleOption.number("range", "Range", "Maximum player distance", 96, 16, 256, 8))
            .option(ModuleOption.number("radius", "Screen Radius", "Distance of pointers from screen centre", 0.33, 0.2, 0.46, 0.01))
            .option(ModuleOption.number("limit", "Player Limit", "Nearest players drawn each frame", 12, 1, 32, 1))
            .option(ModuleOption.bool("distance", "Show Distance", "Show distance next to each pointer", true))
            .option(ModuleOption.color("color", "Colour", "Pointer colour", 0xFFCBB9FF));
        register("shulker_preview", "Shulker Preview", "See the stored items when hovering a shulker box in an inventory", Category.VISUAL)
            .option(ModuleOption.bool("counts", "Item Counts", "Show stack counts inside the preview", true));
        register("fireflies", "Fireflies", "Original soft-glow fireflies, without vanilla particles or pixel sprites", Category.VISUAL)
            .option(ModuleOption.number("count", "Density", "Spawn rate of nearby fireflies", 18, 4, 36, 2));
        register("china_hat", "China Hat", "Solid striped conical hat that follows your head", Category.VISUAL)
            .option(ModuleOption.color("color", "Colour", "Hat colour", 0xFF9C9CFF))
            .option(ModuleOption.number("size", "Size", "Hat width", 1.0, 0.6, 1.6, 0.05))
            .option(ModuleOption.bool("show_hat", "Show Hat", "Draw the conical hat", true))
            .option(ModuleOption.bool("halo", "Halo", "Also draw a clean halo above your head", false));
        register("wings", "Wings", "Solid flapping 3D wings on your back", Category.VISUAL)
            .option(ModuleOption.choice("style", "Style", "Wing silhouette", "Butterfly", "Butterfly", "Feather"))
            .option(ModuleOption.color("color", "Colour", "Wing colour", 0xFF9C9CFF))
            .option(ModuleOption.number("size", "Size", "Wing span", 1.0, 0.6, 1.6, 0.05));
        register("fun_skins", "Fun Skins", "3D skins: Homer Simpson, SpongeBob, Tung Tung Tung Sahur and more brainrot and TV skins (only you see it)", Category.VISUAL)
            .option(ModuleOption.choice("skin", "Skin", "The skin your player wears", "Homer Simpson", dev.khanh.feature.FunSkinService.SKINS))
            .option(ModuleOption.bool("layers_3d", "3D Layers", "Build the skin's outer layer (hair, hats, ties, the bat) as real 3D blocks", true));
        register("custom_cape", "Custom Cape", "Locally display a Khanh Client logo cape with your chosen fabric finish", Category.VISUAL)
            .option(ModuleOption.choice("style", "Style", "Procedural cape finish", "Gradient", "Gradient", "Stripes", "Night"))
            .option(ModuleOption.color("color", "Colour", "Cape colour", 0xFF746CBF));
        register("target_hud", "Target HUD", "Movable enemy-only health and equipment card", Category.VISUAL)
            .option(ModuleOption.bool("health", "Health", "Show the target's visible health", true))
            .option(ModuleOption.bool("equipment", "Equipment", "Show the target's equipped armor and held items", true))
            .option(ModuleOption.bool("distance", "Distance", "Show your distance from the target", true));
        register("see_invisible", "See Invisible", "A local silhouette for invisible players still sent by the server", Category.VISUAL)
            .option(ModuleOption.color("color", "Colour", "Silhouette colour", 0xFFB5D9FF))
            .option(ModuleOption.number("opacity", "Opacity", "Silhouette fill strength", 58, 0, 180, 5))
            .option(ModuleOption.number("range", "Range", "Maximum range in blocks", 96, 16, 192, 8));
        register("player_halo", "Player Halo", "Smooth layered halos above nearby players", Category.VISUAL)
            .option(ModuleOption.color("color", "Colour", "Halo colour wheel", 0xFFB58CFF))
            .option(ModuleOption.number("radius", "Radius", "Size of the halo", 0.38, 0.20, 0.80, 0.02))
            .option(ModuleOption.number("height", "Height", "Distance above the player's eyes", 0.22, -0.10, 0.70, 0.02))
            .option(ModuleOption.number("width", "Line Width", "Core halo thickness", 1.6, 0.6, 4.0, 0.1))
            .option(ModuleOption.number("range", "Range", "Maximum render distance", 96, 16, 192, 8))
            .option(ModuleOption.bool("self", "Show Self", "Show your halo in third person", true))
            .option(ModuleOption.bool("through_walls", "Through Walls", "Keep the halo visible behind blocks", false));
        register("body_aura", "Body Aura", "A soft non-box glow formed around nearby players", Category.VISUAL)
            .option(ModuleOption.color("color", "Colour", "Aura colour wheel", 0xFF74D8FF))
            .option(ModuleOption.number("size", "Size", "Aura radius around the body", 0.72, 0.38, 1.40, 0.02))
            .option(ModuleOption.number("opacity", "Opacity", "Glow strength", 0.55, 0.10, 1.0, 0.05))
            .option(ModuleOption.number("range", "Range", "Maximum render distance", 72, 16, 160, 8))
            .option(ModuleOption.bool("self", "Show Self", "Show your aura in third person", false))
            .option(ModuleOption.bool("through_walls", "Through Walls", "Keep the aura visible behind blocks", false));
        register("item_physics", "Item Physics", "Dropped items lie flat on the ground and rotate while airborne", Category.VISUAL)
            .option(ModuleOption.bool("small", "Small Items", "Render dropped items at half size", false));
        register("discord_rpc", "Discord Presence", "Shows Playing Khanh Client on your Discord profile with your server, a timer and a Discord button", Category.CLIENT, true)
            .option(ModuleOption.text("server", "Server Line", "The line under Khanh Client, e.g. donutsmp.net", "donutsmp.net", 32))
            .option(ModuleOption.bool("show_version", "Show Version", "Add a Khanh Client version line under the server", false))
            .option(ModuleOption.bool("real_server", "Use Real Server", "Show where you really are: menus, singleplayer, LAN, Realms or the server", false))
            .option(ModuleOption.bool("show_ip", "Show Server IP", "With Use Real Server, show the server address (off just says Multiplayer)", true))
            .option(ModuleOption.text("image", "Image Key", "Name of the art asset uploaded to your Discord app", "logo", 24));
        register("creamy_keys", "Creamy Keys", "Soft satisfying keypress sounds while playing", Category.CLIENT)
            .option(ModuleOption.choice("sound", "Key Sound", "Choose a gentle keyboard sound", "Creamy",
                "Creamy", "Soft", "Deep"))
            .option(ModuleOption.number("volume", "Volume", "Keyboard sound level; above 1 layers the sound for extra punch", 1.0, 0.1, 2.0, 0.05))
            .option(ModuleOption.bool("in_chat", "In Chat", "Also play key sounds while typing in chat", false));
        register("visuals", "Notifications", "How long finder pop-ups stay, their sound, and menu blur", Category.CLIENT, true)
            .option(ModuleOption.choice("gui_theme", "GUI Theme", "Choose a complete glass and accent palette", "Khanh Client Black",
                "Khanh Client Black", "Black Glass", "Khanh Client Violet", "Black Ice", "Ultraviolet", "Obsidian", "Arctic", "Rose Quartz", "Emerald Glass", "Crimson Glass",
                "Sunset", "Sakura", "Cyberpunk", "Gold", "Ocean", "Midnight", "Toxic", "Mocha", "Sapphire", "Lavender"))
            .option(ModuleOption.choice("gui_layout", "GUI Layout", "Liquid Glass is the black glass console; Monolith is the classic column browser", "Liquid Glass", "Liquid Glass", "Monolith"))
            .option(ModuleOption.number("finder_seconds", "Notification Time", "Seconds a finder notification stays before fading out", 4, 1, 15, 0.5))
            .option(ModuleOption.bool("finder_sound", "Notification Sound", "Ping when a finder notification pops up", true))
            .option(ModuleOption.bool("blur", "GUI Blur", "Frosted blur behind menus (Khanh Client and Minecraft); off keeps the world sharp", false))
            .option(ModuleOption.choice("gui_finish", "GUI Finish", "Glass uses a shared frosted scene with a smooth refractive rim", "Glass", "Glass", "Clean"))
            .option(ModuleOption.choice("hud_finish", "HUD Finish", "Clean removes decorative layers; Detailed restores cinematic decoration", "Clean", "Clean", "Detailed"))
            .option(ModuleOption.number("gui_zoom", "GUI Zoom", "Scale the complete Khanh Client console", 0.55, 0.40, 1.0, 0.05))
            .option(ModuleOption.choice("accent", "Accent Color", "Use the theme accent or override it", "Theme", "Theme", "Khanh Client Purple", "Cyan", "White", "Rose", "Violet", "Crimson", "Custom"))
            .option(ModuleOption.number("custom_red", "Custom Red", "Red channel for your accent color", 157, 0, 255, 1))
            .option(ModuleOption.number("custom_green", "Custom Green", "Green channel for your accent color", 78, 0, 255, 1))
            .option(ModuleOption.number("custom_blue", "Custom Blue", "Blue channel for your accent color", 255, 0, 255, 1))
            .option(ModuleOption.number("glass_opacity", "Glass Opacity", "Black glass panel strength", 0.90, 0.40, 0.96, 0.04))
            .option(ModuleOption.number("glass_tint", "Accent Tint", "Optional trace of your chosen accent in the black glass", 0.0, 0.0, 0.40, 0.05))
            .option(ModuleOption.bool("glow", "Soft Glow", "Add a restrained outer glow when wanted", true))
            .option(ModuleOption.bool("hud_shadow", "HUD Shadows", "Give HUD cards subtle depth", true))
            .option(ModuleOption.bool("hud_border", "HUD Borders", "Draw clean accent-tinted glass borders", true))
            .option(ModuleOption.bool("animated_accent", "Animated Accent", "Slowly breathe the chosen accent without rainbow flashing", true))
            .option(ModuleOption.number("accent_motion", "Accent Motion", "Strength of the soft animated accent", 0.20, 0.0, 0.55, 0.05))
            .option(ModuleOption.bool("world_pulse", "World Pulse", "Gently pulse ESP fill and beam intensity", true))
            .option(ModuleOption.bool("screen_corners", "Corner Accents", "Add thin cinematic corner marks to the HUD", true))
            .option(ModuleOption.bool("velocity_rails", "Velocity Rails", "Show subtle moving side rails while travelling quickly", true))
            .option(ModuleOption.bool("horizon_ticks", "Horizon Ticks", "Add compact tactical markers along the centre horizon", true))
            .option(ModuleOption.bool("health_edges", "Health Warning", "Tint the screen edges when health becomes low", true))
            .option(ModuleOption.bool("edge_scanlines", "Edge Scanlines", "Add light sci-fi scan marks at the screen edges", true))
            .option(ModuleOption.bool("hud_sheen", "HUD Glass Sheen", "Subtle light along the upper edge of HUD cards", true));
        register("client_colors", "Client Colours", "Choose separate GUI and HUD colours", Category.CLIENT, true)
            .option(ModuleOption.choice("preset", "Colour", "Khanh Client's colour", "Blue", "Blue", "Red", "Orange", "Yellow", "Purple", "Green", "Lime", "Rainbow"))
            .option(ModuleOption.bool("gui_override", "Colour Whole GUI", "Recolour the whole Khanh Client menu (panels, glass and accent) with the wheel", false))
            .option(ModuleOption.color("gui_color", "GUI Colour", "Colour wheel for the whole menu", 0xFF9D4EFF))
            .option(ModuleOption.number("tint_strength", "Tint Strength", "How strongly the colour soaks into the menu's glass", 0.45, 0.1, 1.0, 0.05))
            .option(ModuleOption.bool("hud_override", "Custom HUD Colour", "Use the HUD colour wheel instead of the theme accent", false))
            .option(ModuleOption.color("hud_color", "HUD Colour", "Colour wheel for HUD accents and radar", 0xFF9D4EFF));

        registerCosmeticModules();
        register("jump_circles", "Jump Circles", "Smooth expanding rings at your take-off position", Category.RENDER)
            .option(ModuleOption.choice("style", "Style", "Khanh: ring, inner ring and rising sparks. Classic: two rings", "Khanh", "Khanh", "Classic"))
            .option(ModuleOption.color("color", "Colour", "Ring colour wheel", 0xFF69BCFF))
            .option(ModuleOption.number("radius", "Radius", "Maximum ring radius", 1.8, 0.5, 4.0, 0.1))
            .option(ModuleOption.number("lifetime", "Lifetime", "Fade duration in milliseconds", 1400, 300, 2400, 100))
            .option(ModuleOption.number("width", "Line Width", "Ring thickness", 1.7, 0.7, 4, 0.1))
            .option(ModuleOption.bool("text", "Khanh Client Text", "Write KHANH CLIENT around each ring", true));

        register("player_radar", "Player Radar", "Interpolated glass radar with skin portraits, readable labels and real direction", Category.RENDER, true)
            .option(ModuleOption.number("range", "Range", "Horizontal loaded-player range; height is shown separately", 96, 24, 256, 8))
            .option(ModuleOption.number("size", "Radar Size", "Size of the movable circular radar", 148, 76, 240, 4))
            .option(ModuleOption.bool("names", "Names", "Show usernames beside skin heads", true))
            .option(ModuleOption.number("head_size", "Head Size", "Size of player skin portraits", 12, 8, 20, 1))
            .option(ModuleOption.number("label_limit", "Name Limit", "Maximum collision-free name labels; every portrait still renders", 4, 0, 8, 1))
            .option(ModuleOption.bool("distances", "Distances", "Show horizontal distance beside visible names", true))
            .option(ModuleOption.bool("height", "Height Arrows", "Up/down indicators for players at least four blocks above or below", true))
            .option(ModuleOption.bool("distance_rings", "Distance Rings", "Quiet one-third and two-third range guides", true))
            .option(ModuleOption.bool("cardinals", "Compass Letters", "Show world N/E/S/W around the glass dial", true))
            .option(ModuleOption.number("max_players", "Player Limit", "Nearest players drawn to keep crowded areas responsive", 24, 8, 48, 4))
            .option(ModuleOption.bool("rotate", "View Up", "Forward is up; disable for a fixed north-up map", true));
        register("rtp_map", "RTP Map", "9 by 9 DonutSMP RTP map with region and spot data", Category.RENDER)
            .option(ModuleOption.number("cell_size", "Cell Size", "Pixel size of each 50k by 50k cell", 26, 14, 56, 1))
            .option(ModuleOption.bool("coordinates", "Coordinates", "Show the exact player X and Z under the map", true))
            .option(ModuleOption.bool("legend", "Spot Legend", "Show the glitched and media spot legend", true))
            .option(ModuleOption.bool("region_numbers", "Region Numbers", "Draw the original region IDs inside cells", true))
            .option(ModuleOption.bool("grid", "Map Grid", "Draw the original 50k grid", true))
            .option(ModuleOption.bool("player_indicator", "Player Indicator", "Show player position and direction", true))
            .option(ModuleOption.bool("distance_rings", "Distance Rings", "Show subtle 25k spawn rings", false))
            .option(ModuleOption.bool("spot_zones", "Spot Zones", "Show original glitched and media cells", true))
            .option(ModuleOption.number("panel_opacity", "Panel Opacity", "Original cool-ink panel opacity", 0.68, 0.15, 1.0, 0.01))
            .option(ModuleOption.number("spot_opacity", "Spot Opacity", "Original spot overlay opacity", 0.86, 0.2, 1.0, 0.01))
            .option(ModuleOption.number("number_scale", "Number Size", "Region number scale", 0.78, 0.4, 1.6, 0.02));

        register("auto_relog", "Auto Relog", "Rejoins after a kick or lost connection; can also relog at a safety Y", Category.PLAYER)
            .option(ModuleOption.bool("on_kick", "Reconnect On Kick", "Rejoin the server after being kicked or losing connection", true))
            .option(ModuleOption.number("attempts", "Max Attempts", "Give up after this many rejoins in a row", 5, 1, 20, 1))
            .option(ModuleOption.number("delay", "Relog Delay", "Seconds before reconnecting", 3, 1, 30, 1))
            .option(ModuleOption.bool("safety_relog", "Safety Y Relog", "Leave and rejoin when you fall to the safety Y", true))
            .option(ModuleOption.number("safety_y", "Safety Y", "Leave at or below this Y level", -55, -64, 64, 1));
        register("auto_tool", "Auto Tool", "Best tool while mining; XP bottles automatically when you look down and hold right-click", Category.PLAYER)
            .option(ModuleOption.bool("suitable_only", "Suitable Only", "Do not switch to unsuitable items", true))
            .option(ModuleOption.bool("auto_xp", "Auto XP", "Look down and hold right-click to swap to XP bottles and throw them; let go to swap back", true))
            .option(ModuleOption.number("xp_pitch", "XP Look Angle", "How far down you must look for Auto XP (90 = straight down)", 60, 20, 90, 5));
        register("durability_alert", "Armor Alert", "Warns you when your armor, elytra or tools are about to break", Category.PLAYER)
            .option(ModuleOption.number("threshold", "Warn Below", "Warn at or below this (percent or durability points)", 10, 1, 100, 1))
            .option(ModuleOption.choice("mode", "Measure", "Percent of full durability, or points left", "Percent", "Percent", "Durability"))
            .option(ModuleOption.choice("items", "Items", "Which items to watch", "Armor + Elytra", "Armor + Elytra", "Tools + Weapons", "All Damageable"))
            .option(ModuleOption.bool("equipment", "Worn Items", "Check what you are wearing and holding", true))
            .option(ModuleOption.bool("inventory", "Whole Inventory", "Also check everything in your inventory", false))
            .option(ModuleOption.number("interval", "Check Every", "Ticks between checks", 20, 5, 100, 5))
            .option(ModuleOption.bool("card", "Notification Card", "Warn with a card at the top of the screen (off uses the action bar)", true))
            .option(ModuleOption.bool("sound", "Sound", "Play a warning sound", true));
        register("fly", "Fly", "Fly like creative mode (singleplayer / your own worlds: servers like DonutSMP kick or ban for it)", Category.PLAYER)
            .option(ModuleOption.choice("mode", "Mode", "Creative: double-tap jump to fly. Velocity: move straight from your keys", "Creative", "Creative", "Velocity"))
            .option(ModuleOption.number("speed", "Speed", "Flying speed (1 = creative speed)", 1.0, 0.2, 5.0, 0.1))
            .option(ModuleOption.bool("start_flying", "Start Flying", "Start flying straight away if you turn it on in mid-air", true))
            .option(ModuleOption.bool("anti_kick", "Anti Kick", "Velocity mode dips slightly now and then so you are not floating perfectly still", true));
        register("fast_place", "Fast Place", "Hold right-click to place blocks, crystals and XP bottles every tick", Category.PLAYER)
            .option(ModuleOption.number("delay", "Place Delay", "Ticks between placements (0 = every tick, vanilla is 4)", 0, 0, 4, 1))
            .option(ModuleOption.bool("blocks_only", "Blocks Only", "Only speed up blocks (off also speeds up crystals, XP bottles and more)", false))
            .option(ModuleOption.bool("fast_xp", "Fast XP", "Throw XP bottles even faster than one per tick", true))
            .option(ModuleOption.number("xp_per_tick", "XP Per Tick", "XP bottles thrown each tick while held", 2, 1, 4, 1));

        register("home_reset", "Home Reset", "Two-step home reset helper", Category.PLAYER)
            .option(ModuleOption.choice("slot", "Home Slot", "Home number used by supported servers", "1", "1", "2", "3"))
            .option(ModuleOption.action("run", "Run Home Reset", "Run the configured delete/set-home sequence", GameplayUtilityService::runHomeReset));
        register("admin_detector", "Admin Detector", "Detect watched staff names in the server player list", Category.MISC)
            .option(ModuleOption.text("watch_names", "Staff Names", "Comma-separated exact usernames to watch", "", 512))
            .option(ModuleOption.bool("hud", "Admin List", "Show detected admins in the Staff HUD", true))
            .option(ModuleOption.bool("sound", "Join Sound", "Play a local warning sound without chat", true));
        register("fake_rank", "Fake Rank", "Client-side rank shown before your own name", Category.MISC)
            .option(ModuleOption.choice("rank", "Rank", "Local rank label", "OWNER", "OWNER", "ADMIN", "MOD", "VIP", "KHANH"))
            .option(ModuleOption.choice("color", "Rank Color", "Local rank color", "Khanh Client Pink", "Khanh Client Pink", "Red", "Gold", "Cyan", "Violet", "White"));
        register("weather_alert", "Weather Notifier", "Weather changes shown as quiet HUD alerts", Category.MISC)
            .option(ModuleOption.bool("rain", "Rain", "Alert for rain changes", true))
            .option(ModuleOption.bool("thunder", "Thunder", "Alert for thunder changes", true))
            .option(ModuleOption.bool("sound", "Sound", "Play a local weather cue", true));
        register("name_protect", "Name Protect", "Replaces player names in rendered text", Category.MISC)
            .option(ModuleOption.text("replacement_name", "Your Protected Name", "Name shown locally instead of your real username", "Khanh Client", 24))
            .option(ModuleOption.text("custom_names", "Custom Names", "Fake names for other players: RealName=Fake Name, Other=Another", "", 512))
            .option(ModuleOption.bool("hide_others", "Hide Other Players", "Replace everyone else's names with numbered aliases", true))
            .option(ModuleOption.text("others_name", "Others Alias", "What everyone else is called when hidden (Player 1, Player 2 ...)", "Player", 16));
        register("staff_online", "Staff Online", "Detects staff from tab ranks, server icons, watched names and hidden entries", Category.MISC)
            .option(ModuleOption.choice("detect_by", "Detect By", "How staff are recognised", "Icons + Ranks",
                "Icons + Ranks", "Icons", "Ranks", "Names"))
            .option(ModuleOption.text("watch_names", "Staff Names", "Comma-separated exact usernames to watch", "", 512))
            .option(ModuleOption.text("rank_keywords", "Rank Keywords", "Comma-separated staff rank words", "owner,coowner,manager,administrator,admin,developer,dev,srmod,seniormod,moderator,mod,srhelper,seniorhelper,helper,trialmod,trial,support,staff", 512))
            .option(ModuleOption.text("marker_symbols", "Staff Icons", "Server symbols that identify staff", "★,✦,✧,✪,✯,✰,✵,✶,✷,✸,✹,✺,✻,✼,✽,✾,✿,❂,❈", 256))
            .option(ModuleOption.bool("font_icons", "Server Font Icons", "Count any resource-pack rank icon as staff (can flag normal ranks)", false))
            .option(ModuleOption.bool("show_vanished", "Hidden Staff", "Also check spectator and tab-hidden entries (can include fake entries)", false))
            .option(ModuleOption.bool("hud", "Staff Panel", "Show detected staff on the HUD", true))
            .option(ModuleOption.bool("notification", "Join Alert", "Alert when visible staff appear online", true))
            .option(ModuleOption.bool("sound", "Alert Sound", "Play a warning sound for staff alerts", true));
        register("block_notifier", "Block Notifier", "Alerts when valuable blocks are found nearby", Category.MISC)
            .option(ModuleOption.choice("esp_style", "Style", "Khanh: fill, outline and corner brackets. Corners: brackets only", "Khanh", "Khanh", "Corners", "Outline", "Filled"))
            .option(ModuleOption.number("esp_width", "Line Width", "Thickness of the lines", 2.0, 0.5, 5.0, 0.25))
            .option(ModuleOption.number("radius", "Radius", "Nearby block scan radius", 16, 8, 32, 4))
            .option(ModuleOption.bool("diamond", "Diamond Ore", "Watch normal and deepslate diamond ore", true))
            .option(ModuleOption.bool("emerald", "Emerald Ore", "Watch normal and deepslate emerald ore", true))
            .option(ModuleOption.bool("ancient_debris", "Ancient Debris", "Watch ancient debris", true))
            .option(ModuleOption.bool("gold", "Gold Ore", "Watch overworld and Nether gold ore", false))
            .option(ModuleOption.bool("mob_spawner", "Mob Spawner", "Watch normal mob spawners", true))
            .option(ModuleOption.bool("trial_spawner", "Trial Spawner", "Watch trial spawners", true))
            .option(ModuleOption.bool("vault", "Vaults", "Watch normal and ominous vaults", false))
            .option(ModuleOption.bool("end_portal", "End Portal Frames", "Watch end portal frames", false))
            .option(ModuleOption.choice("color", "Alert Color", "Marker, label, and message color", "Khanh Client Pink", "Khanh Client Pink", "Red", "Gold", "Cyan", "Lime", "Violet", "White", "Custom"))
            .option(ModuleOption.number("custom_red", "Custom Red", "Red channel for the custom alert color", 255, 0, 255, 1))
            .option(ModuleOption.number("custom_green", "Custom Green", "Green channel for the custom alert color", 22, 0, 255, 1))
            .option(ModuleOption.number("custom_blue", "Custom Blue", "Blue channel for the custom alert color", 159, 0, 255, 1))
            .option(ModuleOption.bool("sound", "Sound", "Play a sound when a block is found", true));
        register("fake_pay", "Fake Pay", "Local /pay result that never sends money to the server", Category.MISC)
            .option(ModuleOption.choice("amount_style", "Amount Style", "Short: You paid beloved $100M. As Typed keeps what you wrote; Full writes every digit", "Short", "Short", "As Typed", "Full"))
            .option(ModuleOption.bool("full_stop", "End With a Full Stop", "End the message with a full stop like DonutSMP", true))
            .option(ModuleOption.bool("remove_from_scoreboard", "Remove From Scoreboard", "Subtract fake payments from the locally displayed balance", false))
            .option(ModuleOption.bool("action_bar", "Action Bar", "Also show the confirmation above the hotbar", true))
            .option(ModuleOption.bool("sound", "Confirmation Sound", "Play a confirmation sound", true));

        register("friends", "Friends", "Save friends with /zerofriend add <name> or aim at a player and use the actions below", Category.CLIENT, true)
            .option(ModuleOption.bool("hud", "Friends HUD", "Show saved friends and their online status", true))
            .option(ModuleOption.bool("protect_targets", "Target Protection", "Never treat saved friends as enemy combat targets", true))
            .option(ModuleOption.bool("highlight_esp", "Friend ESP Color", "Use the selected friend color in Player ESP", true))
            .option(ModuleOption.choice("color", "Friend Color", "Player ESP and Friends HUD accent", "Mint", "Mint", "Khanh Client Pink", "Cyan", "Violet", "Gold", "Custom"))
            .option(ModuleOption.number("custom_red", "Custom Red", "Red channel for the custom friend color", 80, 0, 255, 1))
            .option(ModuleOption.number("custom_green", "Custom Green", "Green channel for the custom friend color", 255, 0, 255, 1))
            .option(ModuleOption.number("custom_blue", "Custom Blue", "Blue channel for the custom friend color", 166, 0, 255, 1))
            .option(ModuleOption.action("add_target", "Add Aimed Player", "Add the player currently under your crosshair", FriendService::addTarget))
            .option(ModuleOption.action("remove_target", "Remove Aimed Player", "Remove the aimed player from your friends list", FriendService::removeTarget))
            .option(ModuleOption.action("list", "List Friends", "Print the saved friends list in local chat", FriendService::listInChat))
            .option(ModuleOption.action("clear", "Clear Friends", "Remove every saved friend", FriendService::clear));
        register("spotify_hud", "Spotify HUD", "Now-playing card with album art, progress and controls; works with Spotify on Windows, Mac and Linux", Category.CLIENT, true)
            .option(ModuleOption.bool("hud", "Show Player", "Show the Spotify card on the HUD", true))
            .option(ModuleOption.bool("show_idle", "Show When Idle", "Keep the card visible when Spotify is not playing", true))
            .option(ModuleOption.bool("controls", "Show Controls", "Show previous, play/pause and next buttons (click them while chat is open)", true))
            .option(ModuleOption.bool("demo", "Demo Mode", "Preview the Spotify HUD without opening Spotify", false))
            .option(ModuleOption.action("previous", "Previous Track", "Skip back to the previous song in Spotify", () -> KhanhFeatures.spotify().previous()))
            .option(ModuleOption.action("play_pause", "Play / Pause", "Play or pause Spotify", () -> KhanhFeatures.spotify().playPause()))
            .option(ModuleOption.action("next", "Next Track", "Skip to the next song in Spotify", () -> KhanhFeatures.spotify().next()));

        register("hud", "HUD", "Khanh Client liquid-glass heads-up display", Category.CLIENT, true)
            .option(ModuleOption.bool("watermark", "Khanh Client Watermark", "Show the compact Khanh Client identity pill", true))
            .option(ModuleOption.bool("modules", "Active Modules", "Show the enabled module stack", true))
            .option(ModuleOption.bool("fps", "FPS Counter", "Show your frame rate (and ping on servers) on the HUD", true))
            .option(ModuleOption.bool("coords", "Coordinates", "Show your X, Y, Z and the direction you face", true))
            .option(ModuleOption.bool("keystrokes", "Keystrokes", "Show W A S D, clicks with CPS and jump as you press them", true))
            .option(ModuleOption.bool("armor", "Armor HUD", "Show equipped armor with durability", true))
            .option(ModuleOption.bool("compass", "Radar", "Round radar with players, compass and a sweeping scan line", true))
            .option(ModuleOption.number("radar_range", "Radar Range", "Blocks from you to the radar's rim", 64, 16, 256, 8))
            .option(ModuleOption.bool("radar_mobs", "Radar Mobs", "Also show hostile mobs on the radar", false))
            .option(ModuleOption.number("hud_scale", "HUD Size", "Make all movable Khanh Client HUD widgets smaller or larger", 0.65, 0.35, 1.75, 0.05));
        register("main_menu", "Khanh Client Menus", "Turn each of Khanh Client's menus on or off; off brings back Minecraft's own", Category.CLIENT, true)
            .option(ModuleOption.bool("title_menu", "Main Menu", "Khanh Client's main menu instead of Minecraft's title screen", true))
            .option(ModuleOption.bool("server_menu", "Server List", "Khanh Client's server list with live ping, favicons and quick actions", true))
            .option(ModuleOption.bool("pause_menu", "Pause Menu", "Khanh Client's pause menu when you press Escape", true))
            .option(ModuleOption.bool("loading_screens", "Loading Screens", "Khanh Client's look for world loading, connecting, saving and the startup splash", true));
        register("intro", "Intro", "Cinematic wheel intro when the game starts (click to skip)", Category.CLIENT, true)
            .option(ModuleOption.text("title", "Title", "Big text revealed at the end", "Khanh Client", 24))
            .option(ModuleOption.bool("sound", "Music & Sound", "Play the intro song, wheel ticks and the landing hit", true))
            .option(ModuleOption.number("music_volume", "Music Volume", "How loud the intro song is", 0.9, 0.1, 1.0, 0.05));
        register("fake_stats", "Fake Stats", "Your own DonutSMP sidebar: your name on top, then money, shards, kills, deaths and playtime", Category.MISC)
            .option(ModuleOption.text("title", "Title", "Sidebar title; {name} is your username like on Donut", "{name}", 32))
            .option(ModuleOption.color("title_color", "Title Colour", "Colour of the title", 0xFFFFFFFF))
            .option(ModuleOption.bool("title_bold", "Bold Title", "Draw the title in bold", false))
            .option(ModuleOption.bool("labels", "Labels", "Write Money, Shards, Kills ... before each value", false))
            .option(ModuleOption.bool("coloured_values", "Coloured Values", "Colour the numbers like their icons (Donut keeps them white)", false))
            .option(ModuleOption.bool("spacers", "Blank Lines", "Leave empty lines at the top and above the region", false))
            .option(ModuleOption.bool("show_money", "Show Money", "Show a custom balance line", true))
            .option(ModuleOption.text("money", "Money", "Displayed balance", "$1.25M", 24))
            .option(ModuleOption.bool("show_shards", "Show Shards", "Show a custom shards line", true))
            .option(ModuleOption.text("shards", "Shards", "Displayed shards", "9,999", 24))
            .option(ModuleOption.bool("show_kills", "Show Kills", "Show a custom kills line", true))
            .option(ModuleOption.text("kills", "Kills", "Displayed kills", "250", 24))
            .option(ModuleOption.bool("show_deaths", "Show Deaths", "Show a custom deaths line", true))
            .option(ModuleOption.text("deaths", "Deaths", "Displayed deaths", "0", 24))
            .option(ModuleOption.bool("show_keyall", "Show Keyall", "Show the keyall timer", false))
            .option(ModuleOption.text("keyall", "Keyall", "Leave empty for a live countdown to the next hour", "", 24))
            .option(ModuleOption.bool("show_playtime", "Show Playtime", "Show a custom playtime line", true))
            .option(ModuleOption.text("playtime", "Playtime", "Displayed playtime", "365d 12h", 24))
            .option(ModuleOption.bool("show_team", "Show Team", "Show a team line (only when Team is filled in)", true))
            .option(ModuleOption.text("team", "Team", "Your team name", "", 24))
            .option(ModuleOption.bool("show_region", "Show Region", "Show the region and ping at the bottom", false))
            .option(ModuleOption.text("region", "Region", "Region shown at the bottom", "EU West", 24))
            .option(ModuleOption.bool("real_ping", "Real Ping", "Show your real ping (off uses the number below)", true))
            .option(ModuleOption.number("ping", "Ping", "Ping shown when Real Ping is off", 23, 0, 999, 1));
        register("fps_boost", "Visual FPS Boost", "Reversible visual cleanup and vanilla render tuning for smoother play", Category.VISUAL)
            .option(ModuleOption.choice("graphics", "Graphics", "Vanilla graphics preset while enabled", "Fast", "Keep", "Fast", "Fancy"))
            .option(ModuleOption.choice("particles", "Particles", "Particle amount while enabled", "Decreased", "Keep", "All", "Decreased", "Minimal"))
            .option(ModuleOption.choice("clouds", "Clouds", "Cloud quality while enabled", "Off", "Keep", "Off", "Fast", "Fancy"))
            .option(ModuleOption.bool("entity_shadows", "Entity Shadows", "Render shadows below entities", false))
            .option(ModuleOption.bool("cutout_leaves", "Detailed Leaves", "Keep transparent leaf gaps", false))
            .option(ModuleOption.bool("improved_transparency", "Improved Transparency", "Use the more expensive transparency path", false))
            .option(ModuleOption.bool("vignette", "Vignette", "Render the dark screen-edge vignette", false))
            .option(ModuleOption.number("entity_distance", "Entity Distance", "Percentage of normal entity render distance", 75, 50, 100, 5))
            .option(ModuleOption.number("biome_blend", "Biome Blend", "Biome colour blend radius", 0, 0, 7, 1))
            .option(ModuleOption.number("chunk_fade", "Chunk Fade", "Chunk fade-in duration", 0.0, 0.0, 2.0, 0.1))
            .option(ModuleOption.bool("distance_cap", "Distance Cap", "Limit view and simulation distance while enabled", false))
            .option(ModuleOption.number("max_distance", "Maximum Distance", "Maximum view and simulation chunks", 16, 4, 32, 1))
            .option(ModuleOption.bool("disable_vsync", "Disable VSync", "Allow uncapped frames; may cause screen tearing", false))
            .option(ModuleOption.bool("view_bobbing", "View Bobbing", "Keep first-person walking motion", true))
            .option(ModuleOption.number("fov_effect", "FOV Effects", "Sprint and movement FOV strength", 80, 0, 100, 5))
            .option(ModuleOption.number("damage_tilt", "Damage Tilt", "Camera tilt when taking damage", 40, 0, 100, 5))
            .option(ModuleOption.number("distortion", "Distortion", "Portal and nausea visual strength", 40, 0, 100, 5))
            .option(ModuleOption.number("darkness", "Darkness Pulse", "Darkness effect visual strength", 70, 0, 100, 5));
        register("animations", "Animations", "Smooth liquid motion across Khanh Client", Category.VISUAL, true)
            .option(ModuleOption.number("speed", "Motion Speed", "Interface animation response", 0.22, 0.08, 0.45, 0.01));
        register("config", "Configs", "Five profiles and portable sharing", Category.CONFIG)
            .option(ModuleOption.action("open", "Open Configs", "Manage five profiles and import or copy share codes",
                () -> MinecraftClient.getInstance().setScreen(new KhanhConfigScreen(MinecraftClient.getInstance().currentScreen))));
        registerKhanhModules();
        KhanhCatalog.apply(this);
    }

    /** Modules that are new in Khanh Client. */
    private void registerKhanhModules() {
        register("reach", "Reach", "Hit and use things from further away", Category.COMBAT)
            .option(ModuleOption.number("entity", "Hit Reach", "How far you can hit players and mobs (vanilla 3)", 3.5, 3.0, 6.0, 0.05))
            .option(ModuleOption.number("block", "Block Reach", "How far you can place and break blocks (vanilla 4.5)", 4.5, 4.5, 6.0, 0.05));
        register("hitbox_outline", "Hitbox Outline", "Outlines the real hitbox of players and mobs", Category.RENDER)
            .option(ModuleOption.choice("esp_style", "Style", "Khanh: fill, outline and corner brackets. Corners: brackets only", "Khanh", "Khanh", "Corners", "Outline", "Filled"))
            .option(ModuleOption.number("esp_width", "Line Width", "Thickness of the lines", 2.0, 0.5, 5.0, 0.25))
            .option(ModuleOption.bool("players", "Players", "Outline players", true))
            .option(ModuleOption.bool("mobs", "Mobs", "Outline hostile and passive mobs", false))
            .option(ModuleOption.bool("eyes", "Eye Line", "Show the eye height and where they are looking", true))
            .option(ModuleOption.bool("through_walls", "Through Walls", "See outlines through blocks", false))
            .option(ModuleOption.color("color", "Colour", "Outline colour", 0xFF38BDF8))
            .option(ModuleOption.number("range", "Range", "How far away to outline", 48, 8, 128, 8));
        register("bypasses", "Bypasses", "Sees through anti-xray: ignores fake ores the server hides in stone", Category.RENDER)
            .option(ModuleOption.bool("anti_xray", "Anti-Xray Filter", "Only count ores that touch air, water or lava (fake anti-xray ores never do)", true));
        register("motion_scanner", "Motion Scanner", "A small green pillar in any chunk with a lot of motion: farms, item belts, minecarts, people", Category.RENDER)
            .option(ModuleOption.choice("sensitivity", "Sensitivity", "How much motion lights a chunk up", "Medium", "Low", "Medium", "High"))
            .option(ModuleOption.bool("players", "Count Players", "Players walking around count as motion too", true))
            .option(ModuleOption.number("min_distance", "Min Distance", "Ignore busy chunks closer than this", 24, 0, 128, 4))
            .option(ModuleOption.number("height", "Pillar Height", "How tall the green pillar is", 6, 2, 24, 1))
            .option(ModuleOption.color("color", "Colour", "Pillar colour", 0xFF39FF6A));
        register("cluster_counter", "Cluster Counter", "Counts amethyst clusters and geodes around you", Category.RENDER)
            .option(ModuleOption.bool("hud", "HUD Counter", "Show the counts on screen", true))
            .option(ModuleOption.bool("geodes", "Count Geodes", "Also count whole geodes", true));
        register("block_entity_debug", "Block Entity Debug", "Boxes every block entity: chests, spawners, signs, beacons...", Category.RENDER)
            .option(ModuleOption.choice("esp_style", "Style", "Khanh: fill, outline and corner brackets. Corners: brackets only", "Khanh", "Khanh", "Corners", "Outline", "Filled"))
            .option(ModuleOption.number("esp_width", "Line Width", "Thickness of the lines", 2.0, 0.5, 5.0, 0.25))
            .option(ModuleOption.number("range", "Range", "How far to show block entities", 64, 16, 256, 16))
            .option(ModuleOption.bool("labels", "Labels", "Name each block entity", true))
            .option(ModuleOption.color("color", "Colour", "Box colour", 0xFF38E8FF));
        register("media_player", "Media Player", "Whatever is playing on your PC (YouTube, Apple Music...) on your HUD", Category.CLIENT);
        register("hit_effects", "Hit Effects", "Sparks, crits and sounds every time you hit something", Category.VISUAL)
            .option(ModuleOption.choice("particles", "Particles", "What bursts out when you hit", "Sparks", "Sparks", "Crits", "Blood", "Hearts", "Souls", "Off"))
            .option(ModuleOption.number("amount", "Amount", "How many particles per hit", 12, 2, 40, 1))
            .option(ModuleOption.choice("sound", "Sound", "Sound on each hit", "Off", "Off", "Crit", "Ding", "Bell", "Bass"))
            .option(ModuleOption.number("volume", "Volume", "Hit sound volume", 0.7, 0.1, 1.5, 0.05))
            .option(ModuleOption.bool("players_only", "Players Only", "Only on players", false));
        register("fonts", "Fonts", "Pick the font for the menu and HUD", Category.CLIENT)
            .option(ModuleOption.choice("font", "Font", "Font for Khanh Client's titles and cards", "Xuong", "Xuong", "Inter", "Russo One", "Minecraft", "Minecraft Uniform"));
    }

    private void registerPvpModules() {
        register("auto_hit_crystal","Auto Hit Crystal","Break the crystal under your crosshair while holding attack",Category.COMBAT)
            .option(ModuleOption.bool("hold_attack","Hold Attack","Only act while holding your attack button",true))
            .option(ModuleOption.number("delay","Break Delay","Ticks between attacks",2,1,20,1))
            .option(ModuleOption.number("min_health","Minimum Health","Pause below this health including absorption",8,1,36,1));
        register("auto_jump_reset","Auto Jump Reset","One grounded jump when a new hit is received",Category.COMBAT)
            .option(ModuleOption.number("delay","Jump Delay","Ticks to wait after damage",0,0,5,1))
            .option(ModuleOption.bool("moving_only","Moving Only","Require horizontal movement",true));
        register("auto_totem","Auto Totem","Always refills the offhand at the earliest client opportunity after a local totem pop",Category.COMBAT)
            .option(ModuleOption.bool("health_check","Health Check","Only refill below the health threshold",false))
            .option(ModuleOption.number("health","Health","Health including absorption",12,1,36,1));
        register("crystal_optimizer","Crystal Optimizer","Prevent repeated crystal attack packets during a short confirmation window",Category.COMBAT)
            .option(ModuleOption.number("duplicate_window","Duplicate Window","Ticks before retrying the same still-loaded crystal",2,1,10,1));
        register("double_anchor","Double Anchor","Two server-confirmed charge and detonation cycles on the aimed anchor",Category.COMBAT)
            .option(ModuleOption.bool("hold_use","Hold Use","Hold use to start; otherwise use Run Combo",true))
            .option(ModuleOption.bool("replace","Replace Anchor","Place the second anchor on solid support if consumed",true))
            .option(ModuleOption.number("delay","Step Delay","Ticks between confirmed actions",2,1,10,1))
            .option(ModuleOption.number("min_health","Minimum Health","Pause below this total health",12,4,36,1))
            .option(ModuleOption.number("self_distance","Self Safety","Minimum distance from the blast source",3,1.5,6,.25))
            .option(ModuleOption.action("run","Run Combo","Run on keybind; aim at an existing anchor",dev.khanh.feature.CombatPvpService::requestDoubleAnchor));
        register("elytra_swap","Elytra Swap","Swap your chest equipment with an elytra or chestplate from your hotbar",Category.COMBAT)
            .option(ModuleOption.action("swap","Swap Chest Equipment","Run on keybind; chestplate and elytra must be in the hotbar",dev.khanh.feature.CombatPvpService::requestElytraSwap));
        for(String id:new String[]{"mace_swap","spear_swap"}) register(id,id.equals("mace_swap")?"Mace Swap":"Spear Swap","Select the hotbar weapon before a player attack",Category.COMBAT)
            .option(ModuleOption.bool("switch_back","Switch Back","Restore the original slot unless you manually change it",true))
            .option(ModuleOption.number("return_delay","Return Delay","Ticks before restoring the slot",2,1,10,1))
            .option(ModuleOption.bool("falling_only","Falling Only","Require a downward airborne attack",id.equals("mace_swap")));
        register("no_hit_delay","No Hit Delay","Remove the local miss-click delay; server attack cooldown remains unchanged",Category.COMBAT);
        register("shield_breaker","Shield Breaker","Select an axe for an attack on a blocking player",Category.COMBAT)
            .option(ModuleOption.bool("switch_back","Switch Back","Return to the original hotbar slot",true))
            .option(ModuleOption.number("return_delay","Return Delay","Ticks before restoring the slot",2,1,10,1));
        register("auto_wtap","Sprint Reset","Briefly resets sprint after a confirmed melee hit",Category.COMBAT)
            .option(ModuleOption.bool("only_sprinting","Only While Sprinting","Do nothing unless you were sprinting when the hit landed",true))
            .option(ModuleOption.number("reset_ticks","Reset Length","Ticks before sprint can resume",2,1,5,1))
            .option(ModuleOption.bool("resume","Resume Sprint","Resume while forward is still held",true));
        register("trigger_bot","Trigger Bot","Attack the player actually under your crosshair",Category.COMBAT)
            .option(ModuleOption.number("delay","Attack Delay","Ticks between target attacks",2,1,20,1))
            .option(ModuleOption.number("cooldown","Cooldown","Required fraction of the normal weapon cooldown",.95,.1,1,.05))
            .option(ModuleOption.bool("crit_only","Critical Only","Require a falling airborne player",false));
    }

    private void registerCosmeticModules() {
        register("shader_hands", "Shader Hands", "Outline, glow, glass and colour on your actual first-person hands and held items", Category.PLAYER)
            .option(ModuleOption.choice("mode", "Style", "Original detail, colour, frosted glass, reflective finish or contour tint on hands/items only", "Outline", "Outline", "Tint", "Glass", "Rainbow", "Mirror", "Contour"))
            .option(ModuleOption.bool("outline", "Outline", "Outline the hand and held-item silhouette", true))
            .option(ModuleOption.color("outline_color", "Outline Colour", "Choose the silhouette colour", 0xFF69BCFF))
            .option(ModuleOption.number("width", "Outline Width", "Thickness in screen pixels", 1.1, .5, 4, .1))
            .option(ModuleOption.bool("glow", "Glow", "Soft halo around the silhouette", true))
            .option(ModuleOption.color("glow_color", "Glow Colour", "Choose the halo colour", 0xFF409FFF))
            .option(ModuleOption.number("glow_radius", "Glow Radius", "Halo reach in screen pixels", 5, 2, 16, 1))
            .option(ModuleOption.number("glow_strength", "Glow Strength", "Restrained glow intensity", .35, 0, 2, .05))
            .option(ModuleOption.color("fill_color", "Fill Colour", "Colour used by item finishes", 0xFF69BCFF))
            .option(ModuleOption.number("fill_strength", "Fill Strength", "Blend the visual style with the original item", .35, 0, 1, .05))
            .option(ModuleOption.bool("keep_shading", "Keep Shading", "Preserve visible item detail in tinted modes", true))
            .option(ModuleOption.bool("trail", "Hand Trails", "Optional fading history; adds an extra GPU pass", false))
            .option(ModuleOption.number("trail_duration", "Trail Duration", "How long a moving hand leaves a ghost", .3, .1, .9, .05));
        Module view = register("view_model", "View Model", "Position, rotate and scale your first-person hands independently", Category.PLAYER);
        for (String hand : new String[] {"main", "off"}) {
            String label = hand.equals("main") ? "Main" : "Off";
            for (String axis : new String[] {"x", "y", "z"}) {
                view.option(ModuleOption.number(hand + "_" + axis, label + " " + axis.toUpperCase(), "Local hand offset", 0, -1.5, 1.5, .025));
                view.option(ModuleOption.number(hand + "_r" + axis, label + " Rotate " + axis.toUpperCase(), "Local hand rotation", 0, -90, 90, 1));
            }
            view.option(ModuleOption.number(hand + "_scale", label + " Scale", "Local model size", 1, .5, 1.6, .025));
        }
        register("swing_animations", "Swing Animations", "Visual-only first-person swing styles; never changes attack timing", Category.PLAYER)
            .option(ModuleOption.choice("style", "Style", "Choose how the held item swings", "Smooth",
                dev.khanh.feature.SwingStyle.styles()))
            .option(ModuleOption.number("strength", "Strength", "Swing amplitude", .8, .1, 2, .05))
            .option(ModuleOption.choice("easing", "Motion Curve", "Change the shape of the animation without changing attack timing", "Natural", "Natural", "Smooth", "Sharp"))
            .option(ModuleOption.bool("mirror", "Mirror Swing", "Reverse the sideways movement and rotations", false));
        register("item_size", "Item Size", "Shrink held tools, totems and other items without shrinking your hands or changing gameplay", Category.PLAYER)
            .option(ModuleOption.number("tools", "Tools & Weapons", "First-person tool and weapon size; 1 is normal", .7, .25, 1, .05))
            .option(ModuleOption.number("totems", "Totems", "First-person totem size; 1 is normal", .6, .25, 1, .05))
            .option(ModuleOption.number("other", "Other Items", "First-person size of blocks, food and other held items", .8, .25, 1, .05));
        register("visual_cleanup", "Visual Cleanup", "Optional local overlay cleanup; never removes effects from the server", Category.RENDER)
            .option(ModuleOption.bool("fire", "Hide Fire Overlay", "Keep world fire; hide only the screen overlay", false))
            .option(ModuleOption.bool("vignette", "Hide Vignette", "Hide the dark screen-edge vignette", false))
            .option(ModuleOption.bool("block", "Hide In-Block Overlay", "Hide the suffocation screen texture", false))
            .option(ModuleOption.bool("water", "Hide Water Overlay", "Hide only the underwater screen texture", false))
            .option(ModuleOption.bool("totem", "Hide Totem Animation", "Hide the floating vanilla totem", false))
            .option(ModuleOption.bool("hurt", "No Hurt Tilt", "Hide the camera kick on damage", false))
            .option(ModuleOption.bool("clouds", "Hide Clouds", "Hide only rendered clouds", false))
            .option(ModuleOption.bool("weather", "Hide Weather", "Hide only rendered rain and snow", false));
    }

    private Module register(String id, String name, String description, Category category) {
        return register(id, name, description, category, false);
    }

    private Module register(String id, String name, String description, Category category, boolean enabled) {
        if (byId.containsKey(id)) throw new IllegalStateException("Duplicate module id: " + id);
        if (VISUAL_IDS.contains(id)) category=Category.VISUAL;
        Module module = new Module(id, name, description, category, enabled);
        byId.put(id, module);
        byCategory.get(category).add(module);
        return module;
    }

    /** Removes a module from the client entirely (Khanh's catalog drops everything not on its list). */
    void remove(String id) {
        Module module = byId.remove(id);
        if (module != null) byCategory.get(module.category()).remove(module);
    }

    public List<Module> inCategory(Category category) {
        return Collections.unmodifiableList(byCategory.get(category));
    }

    public Module byId(String id) {
        return byId.get(id);
    }

    public List<Module> all() {
        return List.copyOf(byId.values());
    }
}
