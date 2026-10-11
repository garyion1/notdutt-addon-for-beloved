package dev.khanh;

import dev.khanh.gui.KhanhClickGui;
import dev.khanh.gui.KhanhTitleScreen;
import dev.khanh.module.Module;
import java.io.File;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.server.integrated.IntegratedServer;

/**
 * Development-only visual check. Does nothing unless the KHANH_DEVSHOTS environment variable names
 * a world in the dev run's saves folder: then it photographs the title screen, joins that world,
 * sets up storage blocks and an enchanted sword, photographs the HUD, tracers and GUI, and quits.
 */
final class DevShots {
    private static final class ZeroLog {
        static void screen(MinecraftClient client, int tick) {
            KhanhClient.LOGGER.info("[devshots] t={} screen={}", tick, client.currentScreen == null ? "none" : client.currentScreen.getClass().getSimpleName());
        }
    }

    private static final String WORLD = System.getenv("KHANH_DEVSHOTS");
    private static int titleTicks, worldTicks;
    private static boolean titleShot, joining, loadingShot;

    private DevShots() { }

    static void tick(MinecraftClient client) {
        if (WORLD == null || WORLD.isBlank()) return;
        if (client.world == null || client.currentScreen instanceof net.minecraft.client.gui.screen.world.LevelLoadingScreen) {
            if (client.currentScreen instanceof net.minecraft.client.gui.screen.world.LevelLoadingScreen && !loadingShot) {
                loadingShot = true;
                shot(client, "devshot-loading.png");
            }
            if (client.world != null) return;
            if (client.currentScreen instanceof KhanhTitleScreen || client.currentScreen instanceof TitleScreen) {
                titleTicks++;
                if (System.getenv("KHANH_DEVSHOTS_INTRO") != null) {
                    // Intro check: a frame every ~0.6 s through the whole sequence, then quit.
                    if (titleTicks % 20 == 0 && titleTicks <= 320) shot(client, "devshot-intro-" + (titleTicks / 20) + ".png");
                    if (titleTicks == 340) client.scheduleStop();
                    return;
                }
                if (titleTicks == 160 && !titleShot) { titleShot = true; shot(client, "devshot-title.png"); }
                if (titleTicks == 200 && !joining) {
                    joining = true;
                    client.createIntegratedServerLoader().start(WORLD, () -> { });
                }
            }
            return;
        }
        if (client.player == null) return;
        worldTicks++;
        if (System.getenv("KHANH_DEVSHOTS_TOTEM") != null) {
            totemTour(client);
            return;
        }
        if (System.getenv("KHANH_DEVSHOTS_CONFIG") != null) {
            if (worldTicks == 40) configCheck(client);
            if (worldTicks == 60) client.scheduleStop();
            return;
        }
        if (System.getenv("KHANH_DEVSHOTS_FREECAM") != null) {
            freecamTour(client);
            return;
        }
        if (System.getenv("KHANH_DEVSHOTS_COSMETICS") != null) {
            cosmeticTour(client);
            return;
        }
        if (System.getenv("KHANH_DEVSHOTS_ANCHOR") != null) {
            anchorTour(client);
            return;
        }
        if (System.getenv("KHANH_DEVSHOTS_UTILITY") != null) {
            utilityTour(client);
            return;
        }
        switch (worldTicks) {
            case 40 -> {
                enable("storage_esp");
                enable("custom_glint");
                // KHANH_DEVSHOTS_SET="module.option=value;..." sets choices and switches for a run.
                String set = System.getenv("KHANH_DEVSHOTS_SET");
                if (set != null) for (String pair : set.split(";")) {
                    String[] kv = pair.split("=", 2), mo = kv[0].split("\\.", 2);
                    Module module = KhanhClient.modules().byId(mo[0].strip());
                    if (module == null || kv.length < 2) continue;
                    enable(module.id());
                    var option = module.option(mo[1].strip());
                    if (option == null) continue;
                    if (option.kind() == dev.khanh.module.ModuleOption.Kind.BOOLEAN) option.setBoolean(Boolean.parseBoolean(kv[1].strip()));
                    else option.setChoice(kv[1].strip());
                }
                if (System.getenv("KHANH_DEVSHOTS_HUD_OFF") != null) {
                    Module hud = KhanhClient.modules().byId("hud");
                    if (hud != null) hud.setEnabled(false);
                    enable("player_radar");
                }
                if (System.getenv("KHANH_DEVSHOTS_SPOTIFY") != null) {
                    enable("spotify_hud");
                    Module spotify = KhanhClient.modules().byId("spotify_hud");
                    if (spotify != null && spotify.option("demo") != null) spotify.option("demo").setBoolean(true);
                }
                Module storage = KhanhClient.modules().byId("storage_esp");
                if (storage != null && storage.option("ultimate_range") != null)
                    storage.option("ultimate_range").setBoolean(System.getenv("KHANH_DEVSHOTS_ULTIMATE") != null);
                String theme = System.getenv("KHANH_DEVSHOTS_GLINT");
                if (theme != null) {
                    Module glint = KhanhClient.modules().byId("custom_glint");
                    if (glint != null && glint.option("theme") != null) glint.option("theme").setChoice(theme);
                }
                IntegratedServer server = client.getServer();
                if (server != null) server.execute(() -> {
                    String[] commands = {
                        "time set day", "weather clear", "gamemode creative @p",
                        "execute as @p at @s run tp @s ~ ~ ~ 0 10",
                        "execute as @p at @s run fill ~-2 ~ ~3 ~1 ~3 ~6 deepslate",
                        "execute as @p at @s run setblock ~-1 ~1 ~4 deepslate_diamond_ore",
                        "execute as @p at @s run setblock ~0 ~2 ~5 deepslate_gold_ore",
                        "execute as @p at @s run setblock ~4 ~ ~6 chest",
                        "execute as @p at @s run setblock ~-5 ~ ~9 ender_chest",
                        "execute as @p at @s run setblock ~9 ~1 ~14 barrel",
                        "execute as @p at @s run setblock ~-12 ~ ~18 shulker_box",
                        "execute as @p at @s run setblock ~2 ~ ~-8 chest",
                        "execute as @p at @s run setblock ~-14 ~ ~-3 trapped_chest",
                        "item replace entity @p weapon.mainhand with diamond_sword[enchantments={sharpness:5}]",
                        "item replace entity @p armor.chest with diamond_chestplate[enchantments={protection:4}]",
                        "item replace entity @p hotbar.1 with diamond 12",
                        "item replace entity @p hotbar.2 with golden_apple 8",
                        "item replace entity @p hotbar.3 with stone 64",
                        "item replace entity @p hotbar.4 with ender_pearl 16",
                        "item replace entity @p armor.head with iron_helmet",
                        "item replace entity @p armor.legs with diamond_leggings",
                        "item replace entity @p armor.feet with iron_boots",
                        "item replace entity @p hotbar.0 with diamond_sword[enchantments={sharpness:5}]",
                        "execute as @p at @s run summon item ~1 ~0.5 ~3 {PickupDelay:32767s,Item:{id:\"minecraft:netherite_sword\",count:1,components:{\"minecraft:enchantments\":{\"minecraft:sharpness\":3}}}}",
                        "execute as @p at @s run summon item ~-1 ~0.5 ~3 {PickupDelay:32767s,Item:{id:\"minecraft:stick\",count:1}}",
                        "execute as @p at @s run summon zombie ~3 ~ ~7 {NoAI:1b,CustomName:\"Test Zombie\",CustomNameVisible:1b}",
                        "execute as @p at @s run summon armor_stand ~-3 ~ ~6 {ShowArms:1b,equipment:{chest:{id:\"minecraft:diamond_chestplate\",count:1,components:{\"minecraft:enchantments\":{\"minecraft:protection\":4}}},head:{id:\"minecraft:iron_helmet\",count:1}}}",
                    };
                    for (String command : commands) {
                        server.getCommandManager().parseAndExecute(server.getCommandSource(), command);
                    }
                });
            }
            case 140 -> {
                KhanhClient.LOGGER.info("[devshots] wrap sword={} stone={}",
                    dev.khanh.feature.GalaxyGlintService.wraps(client.player.getMainHandStack(),
                        net.minecraft.item.ItemDisplayContext.FIRST_PERSON_RIGHT_HAND),
                    dev.khanh.feature.GalaxyGlintService.wraps(new net.minecraft.item.ItemStack(net.minecraft.item.Items.STONE),
                        net.minecraft.item.ItemDisplayContext.FIRST_PERSON_RIGHT_HAND));
                shot(client, "devshot-hud.png");
            }
            case 100 -> client.player.getInventory().setSelectedSlot(0);
            case 145 -> {
                String extra = System.getenv("KHANH_DEVSHOTS_MODULES");
                if (extra != null) for (String id : extra.split(",")) enable(id.strip());
                client.player.setYaw(-20.0F);
                client.player.setPitch(28.0F);
            }
            case 160 -> dev.khanh.feature.PrivacyService.handleFakePay("/pay beloved 100m");
            case 175 -> shot(client, "devshot-close.png");
            case 185 -> client.player.setYaw(client.player.getYaw() + 70.0F);
            case 215 -> shot(client, "devshot-tracers-turned.png");
            case 220 -> {
                client.player.setPitch(-10.0F);
                client.options.setPerspective(net.minecraft.client.option.Perspective.THIRD_PERSON_FRONT);
            }
            case 245 -> shot(client, "devshot-armor.png");
            case 247 -> {
                IntegratedServer server = client.getServer();
                if (server != null) server.execute(() -> server.getCommandManager()
                    .parseAndExecute(server.getCommandSource(), "gamemode survival @p"));
            }
            case 250 -> {
                client.options.setPerspective(net.minecraft.client.option.Perspective.FIRST_PERSON);
                client.setScreen(new net.minecraft.client.gui.screen.ingame.InventoryScreen(client.player));
            }
            case 275 -> shot(client, "devshot-inventory.png");
            case 280 -> {
                if (System.getenv("KHANH_DEVSHOTS_CLASSIC") != null) {
                    Module visuals = KhanhClient.modules().byId("visuals");
                    if (visuals != null && visuals.option("gui_layout") != null) visuals.option("gui_layout").setChoice(KhanhClickGui.CLASSIC);
                    String open = System.getenv("KHANH_DEVSHOTS_CLASSIC");
                    Module module = KhanhClient.modules().byId(open);
                    client.setScreen(new dev.khanh.gui.KhanhClickGuiScreen(null, module));
                } else client.setScreen(KhanhClickGui.create(null));
            }
            case 341 -> {
                Module visuals = KhanhClient.modules().byId("visuals");
                if (System.getenv("KHANH_DEVSHOTS_CLASSIC") != null && visuals != null && visuals.option("gui_layout") != null)
                    visuals.option("gui_layout").setChoice(KhanhClickGui.GLASS);
            }
            case 340 -> shot(client, "devshot-gui.png");
            case 345 -> client.setScreen(new net.minecraft.client.gui.screen.GameMenuScreen(true));
            case 347, 350, 355, 365 -> ZeroLog.screen(client, worldTicks);
            case 375 -> shot(client, "devshot-pause.png");
            case 380 -> {
                net.minecraft.client.option.ServerList list = new net.minecraft.client.option.ServerList(client);
                list.loadFile();
                boolean hasDonut = false;
                for (int i = 0; i < list.size(); i++) hasDonut |= list.get(i).address.equalsIgnoreCase("donutsmp.net");
                if (!hasDonut) {
                    list.add(new net.minecraft.client.network.ServerInfo("DonutSMP", "donutsmp.net", net.minecraft.client.network.ServerInfo.ServerType.OTHER), false);
                    list.saveFile();
                }
                if (list.size() == 0) {
                    list.add(new net.minecraft.client.network.ServerInfo("Local Test", "localhost", net.minecraft.client.network.ServerInfo.ServerType.OTHER), false);
                    list.add(new net.minecraft.client.network.ServerInfo("Broken Address", "nowhere.invalid", net.minecraft.client.network.ServerInfo.ServerType.OTHER), false);
                    list.saveFile();
                }
                client.setScreen(new net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen(null));
            }
            case 420 -> shot(client, "devshot-servers.png");
            case 430 -> {
                if (System.getenv("KHANH_DEVSHOTS_HUD_OFF") != null) enable("hud");
                client.scheduleStop();
            }
            default -> { }
        }
    }

    private static net.minecraft.util.math.BlockPos anchorAt;

    /** Anchor Macro check: place an anchor by hand and log whether the macro charges and blows it up. */
    private static void anchorTour(MinecraftClient client) {
        IntegratedServer server = client.getServer();
        if (worldTicks >= 60 && worldTicks <= 120 && anchorAt != null && worldTicks % 2 == 0) {
            var state = client.world.getBlockState(anchorAt);
            KhanhClient.LOGGER.info("[devshots] anchor t={} block={} slot={} held={}", worldTicks,
                state.getBlock().getTranslationKey().replace("block.minecraft.", ""),
                client.player.getInventory().getSelectedSlot(), client.player.getMainHandStack().getItem());
        }
        switch (worldTicks) {
            case 40 -> {
                enable("auto_anchor");
                run(server, "time set day", "gamemode survival @p", "effect give @p resistance 60 255 true",
                    "effect give @p fire_resistance 60 0 true", "execute as @p at @s run tp @s ~ ~ ~ 0 50",
                    "execute as @p at @s run fill ~-3 ~ ~-3 ~3 ~3 ~3 air", "clear @p",
                    "item replace entity @p hotbar.0 with diamond_sword", "item replace entity @p hotbar.1 with respawn_anchor 8",
                    "item replace entity @p hotbar.2 with glowstone 64");
            }
            case 50 -> {
                client.player.setPitch(55.0F);
                client.player.getInventory().setSelectedSlot(1);
            }
            case 55 -> {
                if (client.crosshairTarget instanceof net.minecraft.util.hit.BlockHitResult hit) {
                    anchorAt = hit.getBlockPos().offset(hit.getSide());
                    client.interactionManager.interactBlock(client.player, net.minecraft.util.Hand.MAIN_HAND, hit);
                    client.player.swingHand(net.minecraft.util.Hand.MAIN_HAND);
                    KhanhClient.LOGGER.info("[devshots] placed anchor at {}", anchorAt);
                }
            }
            case 62 -> shot(client, "devshot-anchor.png");
            case 125 -> client.scheduleStop();
            default -> { }
        }
    }

    /** Fast XP, Health Alert, Fake Stats and the cosmetics wardrobe, with counts in the log. */
    private static void utilityTour(MinecraftClient client) {
        IntegratedServer server = client.getServer();
        switch (worldTicks) {
            case 40 -> {
                for (String id : new String[] {"fast_place", "auto_tool", "fake_stats", "health_alert", "crown", "wings",
                    "pop_counter", "kill_effects", "durability_alert"}) enable(id);
                run(server, "time set day", "weather clear", "gamemode survival @p", "effect clear @p",
                    "execute as @p at @s run tp @s ~ ~ ~ 0 0", "clear @p",
                    "item replace entity @p hotbar.3 with experience_bottle 64", "item replace entity @p hotbar.0 with stone_sword",
                    "item replace entity @p armor.chest with diamond_chestplate[damage=520]");
            }
            case 70 -> {
                var chest = client.player.getEquippedStack(net.minecraft.entity.EquipmentSlot.CHEST);
                KhanhClient.LOGGER.info("[devshots] armor alert on={} chest={} dmg={}/{} items={}", dev.khanh.feature.KhanhFeatures.enabled("durability_alert"),
                    chest.getItem(), chest.getDamage(), chest.getMaxDamage(), dev.khanh.feature.KhanhFeatures.choice("durability_alert", "items", "?"));
                shot(client, "devshot-armor-alert.png");
            }
            case 55 -> {
                client.player.getInventory().setSelectedSlot(0);
                client.player.setPitch(80.0F);
                client.options.useKey.setPressed(true);
            }
            case 75 -> {
                client.options.useKey.setPressed(false);
                int left = 0;
                for (int i = 0; i < 9; i++) if (client.player.getInventory().getStack(i).isOf(net.minecraft.item.Items.EXPERIENCE_BOTTLE))
                    left += client.player.getInventory().getStack(i).getCount();
                KhanhClient.LOGGER.info("[devshots] xp thrown in 20 ticks={} selected={}", 64 - left, client.player.getInventory().getSelectedSlot());
            }
            case 82 -> KhanhClient.LOGGER.info("[devshots] slot after release={}", client.player.getInventory().getSelectedSlot());
            case 85 -> {
                client.player.setPitch(5.0F);
                run(server, "kill @e[type=!player]", "damage @p 13 minecraft:generic");
            }
            case 96 -> {
                var dummy = new net.minecraft.client.network.OtherClientPlayerEntity(client.world,
                    new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "beloved"));
                var look = client.player.getRotationVector().multiply(6.0);
                dummy.refreshPositionAndAngles(client.player.getX() + look.x, client.player.getY(), client.player.getZ() + look.z, 0.0F, 0.0F);
                dev.khanh.feature.PvpEventsService.onTotemPop(dummy);
                dev.khanh.feature.PvpEventsService.onTotemPop(dummy);
                dev.khanh.feature.PvpEventsService.onAttack(dummy);
                dev.khanh.feature.PvpEventsService.onDeath(dummy);
                KhanhClient.LOGGER.info("[devshots] lightning entities={}", java.util.stream.StreamSupport.stream(client.world.getEntities().spliterator(), false)
                    .filter(e -> e instanceof net.minecraft.entity.LightningEntity).count());
            }
            case 97 -> shot(client, "devshot-kill-effect.png");
            case 100 -> shot(client, "devshot-health-a.png");
            case 108 -> shot(client, "devshot-health-b.png");
            case 110 -> run(server, "effect give @p instant_health 1 3");
            case 112 -> client.player.setPitch(-89.0F);
            case 117 -> shot(client, "devshot-sky-up.png");
            case 120 -> client.setScreen(KhanhClickGui.create(null));
            case 170 -> shot(client, "devshot-wardrobe.png");
            case 175 -> client.scheduleStop();
            default -> { }
        }
    }

    /** 3D Totems check: held totem, a real pop (cinematic), third person and inventory. */
    private static void totemTour(MinecraftClient client) {
        IntegratedServer server = client.getServer();
        switch (worldTicks) {
            case 40 -> {
                enable("totem_styles");
                String style = System.getenv("KHANH_DEVSHOTS_TOTEM");
                Module totems = KhanhClient.modules().byId("totem_styles");
                if (totems != null && !style.isBlank() && totems.option("style") != null) totems.option("style").setChoice(style);
                if (totems != null && System.getenv("KHANH_DEVSHOTS_POP") != null) {
                    totems.option("pop_particles").setChoice("Coloured");
                    totems.option("pop_animation").setChoice("Quick");
                }
                run(server, "time set day", "weather clear", "gamemode survival @p", "execute as @p at @s run tp @s ~ ~ ~ 0 0",
                    "item replace entity @p hotbar.0 with totem_of_undying", "item replace entity @p weapon.offhand with totem_of_undying",
                    "item replace entity @p hotbar.1 with totem_of_undying",
                    "execute as @p at @s run summon item ~0.6 ~0.4 ~2.2 {PickupDelay:32767s,Item:{id:\"minecraft:totem_of_undying\",count:1}}");
            }
            case 80 -> client.player.getInventory().setSelectedSlot(0);
            case 110 -> shot(client, "devshot-totem-hand.png");
            case 115 -> run(server, "damage @p 100 minecraft:generic");
            case 125 -> shot(client, "devshot-totem-pop1.png");
            case 140 -> shot(client, "devshot-totem-pop2.png");
            case 160 -> shot(client, "devshot-totem-pop3.png");
            case 190 -> client.options.setPerspective(net.minecraft.client.option.Perspective.THIRD_PERSON_FRONT);
            case 210 -> shot(client, "devshot-totem-3p.png");
            case 215 -> {
                client.options.setPerspective(net.minecraft.client.option.Perspective.FIRST_PERSON);
                client.player.setPitch(35.0F);
            }
            case 235 -> shot(client, "devshot-totem-ground.png");
            case 240 -> client.setScreen(new net.minecraft.client.gui.screen.ingame.InventoryScreen(client.player));
            case 260 -> shot(client, "devshot-totem-inventory.png");
            case 270 -> client.scheduleStop();
            default -> { }
        }
    }

    /** Configs check: save/load a slot and round-trip a share code, logging each result. */
    private static void configCheck(MinecraftClient client) {
        var modules = KhanhClient.modules();
        var style = modules.byId("custom_crosshair").option("style");
        style.setChoice("Circle");
        KhanhClient.LOGGER.info("[devshots] config save: {}", dev.khanh.config.ConfigManager.saveProfile(modules, 3, "Dev Test"));
        style.setChoice("Bracket");
        KhanhClient.LOGGER.info("[devshots] config load: {} -> style {}", dev.khanh.config.ConfigManager.loadProfile(modules, 3),
            modules.byId("custom_crosshair").option("style").choiceValue());
        KhanhClient.LOGGER.info("[devshots] config copy: {}", dev.khanh.config.ConfigManager.copySixDigitCode(modules, null));
        String code = client.keyboard.getClipboard();
        modules.byId("custom_crosshair").option("style").setChoice("Chevron");
        KhanhClient.LOGGER.info("[devshots] config import: {} -> style {} (code length {})",
            dev.khanh.config.ConfigManager.importShareCode(modules, code),
            modules.byId("custom_crosshair").option("style").choiceValue(), code.length());
        KhanhClient.LOGGER.info("[devshots] config delete: {}", dev.khanh.config.ConfigManager.deleteProfile(3));
    }

    private static net.minecraft.util.math.Vec3d lastCam;

    /** Freecam check: hold forward for a second, let go, and log how the camera speeds up and glides. */
    private static void freecamTour(MinecraftClient client) {
        if (worldTicks >= 100 && worldTicks <= 130 && dev.khanh.feature.FreecamService.active()) {
            var cam = dev.khanh.feature.FreecamService.position(1.0F);
            if (lastCam != null) KhanhClient.LOGGER.info("[devshots] freecam t={} speed={}", worldTicks,
                String.format(java.util.Locale.ROOT, "%.3f", cam.distanceTo(lastCam)));
            lastCam = cam;
        }
        switch (worldTicks) {
            case 40 -> run(client.getServer(), "time set day", "weather clear", "gamemode survival @p",
                "execute as @p at @s run tp @s ~ ~ ~ 0 0", "execute as @p at @s run setblock ~ ~1 ~2 dirt");
            case 55 -> enable("freecam");
            case 60 -> {
                // Fly the camera away and turn it: the body must still mine what it looked at.
                client.options.forwardKey.setPressed(true);
                dev.khanh.feature.FreecamService.changeFreeLookDirection(900, 300);
                client.options.attackKey.setPressed(true);
            }
            case 100 -> {
                client.options.attackKey.setPressed(false);
                client.options.forwardKey.setPressed(false);
                var pos = client.player.getBlockPos().add(0, 1, 2);
                KhanhClient.LOGGER.info("[devshots] freecam mining: block at {} is now {}; camera pitch {} body pitch {}", pos.toShortString(),
                    client.world.getBlockState(pos).getBlock().getName().getString(),
                    Math.round(dev.khanh.feature.FreecamService.pitch(1.0F)), Math.round(client.player.getPitch()));
            }
            case 102 -> client.options.forwardKey.setPressed(true);
            case 115 -> shot(client, "devshot-freecam-moving.png");
            case 118 -> client.options.forwardKey.setPressed(false);
            case 125 -> shot(client, "devshot-freecam-stopped.png");
            case 130 -> {
                Module freecam = KhanhClient.modules().byId("freecam");
                if (freecam != null) freecam.setEnabled(false);
            }
            case 140 -> client.scheduleStop();
            default -> { }
        }
    }

    /** Solid cosmetics check: front, back, a jump with its ring text, and the inventory preview. */
    private static void cosmeticTour(MinecraftClient client) {
        IntegratedServer server = client.getServer();
        String tag = System.getenv("KHANH_DEVSHOTS_COSMETICS");
        if (worldTicks >= 120 && worldTicks <= 144) client.player.setPitch(55.0F);
        if (worldTicks >= 60 && worldTicks <= 115) client.player.setPitch(worldTicks < 95 ? 5.0F : 15.0F);
        switch (worldTicks) {
            case 40 -> {
                java.util.List<String> wanted = java.util.Arrays.stream(tag.split(",")).map(String::strip).toList();
                java.util.List<String> cosmetics = new java.util.ArrayList<>(java.util.List.of(dev.khanh.feature.CosmeticsPlusService.IDS));
                cosmetics.addAll(java.util.List.of("china_hat", "wings", "jump_circles", "player_halo", "body_aura"));
                for (String id : cosmetics) {
                    Module module = KhanhClient.modules().byId(id);
                    if (module != null && module.enabled() && !wanted.contains(id)) module.setEnabled(false);
                }
                for (String id : wanted) enable(id);
                Module storage = KhanhClient.modules().byId("storage_esp");
                if (storage != null && storage.enabled() && !wanted.contains("storage_esp")) storage.setEnabled(false);
                String skin = System.getenv("KHANH_DEVSHOTS_SKIN");
                Module fun = KhanhClient.modules().byId("fun_skins");
                if (skin != null && fun != null && fun.option("skin") != null) fun.option("skin").setChoice(skin);
                run(server, "time set day", "weather clear", "gamemode creative @p", "execute as @p at @s run tp @s ~ ~ ~ 0 0",
                    "execute as @p at @s run fill ~-4 ~ ~-4 ~4 ~3 ~4 air", "execute as @p at @s run fill ~-4 ~-1 ~-4 ~4 ~-1 ~4 smooth_stone",
                    "item replace entity @p armor.head with air", "item replace entity @p armor.chest with air",
                    "item replace entity @p armor.legs with air", "item replace entity @p armor.feet with air", "clear @p");
            }
            case 60 -> {
                client.player.setPitch(5.0F);
                client.options.setPerspective(net.minecraft.client.option.Perspective.THIRD_PERSON_FRONT);
            }
            case 90 -> shot(client, "devshot-cosmetic-front.png");
            case 95 -> {
                client.options.setPerspective(net.minecraft.client.option.Perspective.THIRD_PERSON_BACK);
                client.player.setPitch(15.0F);
            }
            case 115 -> shot(client, "devshot-cosmetic-back.png");
            case 135 -> client.player.jump();
            case 144 -> shot(client, "devshot-cosmetic-jump.png");
            case 160 -> {
                client.options.setPerspective(net.minecraft.client.option.Perspective.THIRD_PERSON_FRONT);
                client.player.setPitch(30.0F);
                client.player.setYaw(client.player.getYaw() + 35.0F);
            }
            case 185 -> shot(client, "devshot-cosmetic-angle.png");
            case 186 -> {
                if (System.getenv("KHANH_DEVSHOTS_SKYUP") != null) {
                    client.options.setPerspective(net.minecraft.client.option.Perspective.FIRST_PERSON);
                    client.player.setPitch(-89.0F);
                }
            }
            case 189 -> {
                if (System.getenv("KHANH_DEVSHOTS_SKYUP") != null) shot(client, "devshot-sky-up.png");
            }
            case 190 -> {
                client.options.setPerspective(net.minecraft.client.option.Perspective.FIRST_PERSON);
                client.setScreen(new net.minecraft.client.gui.screen.ingame.InventoryScreen(client.player));
            }
            case 210 -> shot(client, "devshot-cosmetic-inventory.png");
            case 215 -> client.scheduleStop();
            default -> { }
        }
    }

    private static void run(IntegratedServer server, String... commands) {
        if (server == null) return;
        server.execute(() -> {
            for (String command : commands) server.getCommandManager().parseAndExecute(server.getCommandSource(), command);
        });
    }

    private static void enable(String id) {
        Module module = KhanhClient.modules().byId(id);
        if (module != null && !module.enabled()) module.setEnabled(true);
    }

    private static void shot(MinecraftClient client, String name) {
        KhanhClient.LOGGER.info("[devshots] {} screen={}", name, client.currentScreen == null ? "none" : client.currentScreen.getClass().getName());
        ScreenshotRecorder.saveScreenshot(new File(client.runDirectory.getPath()), name, client.getFramebuffer(), 1,
            message -> KhanhClient.LOGGER.info("[devshots] {}", message.getString()));
    }
}
