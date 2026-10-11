package dev.khanh.feature;

import dev.khanh.KhanhClient;
import dev.khanh.module.Module;
import dev.khanh.module.ModuleOption;
import java.util.Map;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;

public final class KhanhFeatures {
    private static final WorldScanner WORLD_SCANNER = new WorldScanner();
    private static final StaffOnlineService STAFF_ONLINE = new StaffOnlineService();
    private static final SpotifyService SPOTIFY = new SpotifyService();
    private static final CombatAutomationService COMBAT_AUTOMATION = new CombatAutomationService();
    private static final CombatUtilityService COMBAT_UTILITIES = new CombatUtilityService();
    private static final AutoTridentService AUTO_TRIDENT = new AutoTridentService();
    private static final FakeScoreboardService FAKE_SCOREBOARD = new FakeScoreboardService();
    private static final AutoRelog AUTO_RELOG = new AutoRelog();
    private static final PerformanceService PERFORMANCE = new PerformanceService();
    private static LivingEntity target;
    private static int targetTicks;
    private static boolean registered;

    private KhanhFeatures() {
    }

    public static void register() {
        if (registered) {
            return;
        }
        registered = true;

        SkyService.register();

        WorldRenderEvents.END_EXTRACTION.register(WorldOverlayRenderer::extract);
        WorldRenderEvents.END_MAIN.register(context -> MotionBlurService.render());
        // End of the main pass: always runs (Sodium can skip the see-through terrain pass when
        // nothing see-through is on screen, which made the boxes vanish as you looked around).
        WorldRenderEvents.END_MAIN.register(context -> {
            try {
                StorageGlowRenderer.render(context);
            } catch (RuntimeException exception) {
                KhanhClient.LOGGER.warn("Khanh Client storage boxes failed to draw", exception);
            }
        });
        WorldRenderEvents.BEFORE_ENTITIES.register(context -> {
            try {
                CosmeticsPlusService.renderSolid(context);
                VisualEffectService.renderJumpText(context);
            } catch (RuntimeException exception) {
                KhanhClient.LOGGER.warn("Khanh Client solid cosmetics failed to draw", exception);
            }
        });
        net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback.EVENT.register(
            (type, renderer, helper, context) -> {
                if (renderer instanceof net.minecraft.client.render.entity.PlayerEntityRenderer<?> player) {
                    helper.register(new SolidCosmeticsFeature(player));
                    helper.register(new FunSkin3DFeature(player));
                }
            });
        HudElementRegistry.attachElementBefore(
            VanillaHudElements.CHAT,
            Identifier.of(KhanhClient.MOD_ID, "feature_hud"),
            KhanhHudRenderer::render
        );
        HudElementRegistry.attachElementBefore(
            VanillaHudElements.CHAT,
            Identifier.of(KhanhClient.MOD_ID, "standalone_crosshair"),
            KhanhHudRenderer::renderStandaloneCrosshair
        );
        HudElementRegistry.attachElementBefore(
            VanillaHudElements.CHAT,
            Identifier.of(KhanhClient.MOD_ID, "finder_notifications"),
            FinderNotifications::render
        );
        HudElementRegistry.attachElementBefore(
            VanillaHudElements.CHAT,
            Identifier.of(KhanhClient.MOD_ID, "khanh_hud"),
            KhanhHud::render
        );
        HudElementRegistry.attachElementBefore(
            VanillaHudElements.CHAT,
            Identifier.of(KhanhClient.MOD_ID, "cluster_counter"),
            ClusterCounterHud::render
        );
        HudElementRegistry.attachElementBefore(
            VanillaHudElements.CHAT,
            Identifier.of(KhanhClient.MOD_ID, "health_alert"),
            KhanhHudRenderer::renderHealthAlert
        );
        HudElementRegistry.attachElementBefore(
            VanillaHudElements.CHAT,
            Identifier.of(KhanhClient.MOD_ID, "offscreen_player_pointers"),
            OffscreenPlayerPointers::render
        );
        // Launch splash over the title screen. Screen listeners reset on every init
        // (including a window resize), so re-attach while the splash is still playing.
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            // Khanh's own title screen draws the intro itself; this covers the vanilla one.
            if (screen instanceof net.minecraft.client.gui.screen.TitleScreen && IntroOverlay.wantsTitleScreen()) {
                ScreenEvents.afterRender(screen).register((drawn, context, mouseX, mouseY, delta) -> IntroOverlay.render(context));
            }
        });
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (world.isClient() && entity instanceof LivingEntity living && entity != player) {
                setTarget(living);
                PvpEventsService.onAttack(entity);
                HitEffects.onAttack(entity);
            }
            return ActionResult.PASS;
        });
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (world instanceof net.minecraft.client.world.ClientWorld clientWorld && enabled("storage_esp")) {
                WORLD_SCANNER.trackStorageInteraction(clientWorld, hitResult.getBlockPos());
            }
            return ActionResult.PASS;
        });
        ClientSendMessageEvents.ALLOW_COMMAND.register(command -> {
            if (FriendService.handleCommand(command)) {
                return false;
            }
            if (PrivacyService.handleFakePay(command)) {
                return false;
            }
            return true;
        });
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> ClientDiagnosticsService.onGameMessage(message));
    }

    public static void tick(MinecraftClient client) {
        FreecamService.tick(client);
        WorkAutomationService.tick(client);
        SkinProtectService.tick(client);
        CombatPvpService.tick(client);
        if(!CombatPvpService.busy())COMBAT_AUTOMATION.tick(client);
        COMBAT_UTILITIES.tick(client);
        AUTO_TRIDENT.tick(client);
        PrivacyService.tick(client);
        PvpEventsService.tick(client);
        GameplayUtilityService.tick(client);
        MovementAssistService.tick(client);
        ClientDiagnosticsService.tick(client);
        FAKE_SCOREBOARD.tick(client);
        AUTO_RELOG.tick(client);
        tickAutoTool(client);
        tickTarget(client);
        WORLD_SCANNER.tick(client);
        FinderNotifications.tick(client);
        MotionScanner.tick(client);
        HitEffects.tick(client);
        STAFF_ONLINE.tick(client);
        SPOTIFY.tick(client);
        DiscordPresenceService.tick(client);
        PERFORMANCE.tick(client);
        SkyService.tick(client);
        GalaxyGlintService.tick(client);
        DeepslateOpacityService.tick(client);
        ElytraColorService.tick(client);
        CustomCapeService.tick(client);
    }

    public static void shutdown(MinecraftClient client) {
        CombatPvpService.shutdown();
        PERFORMANCE.shutdown(client);
        MotionBlurService.close();
        GalaxyGlintService.restore(client);
        ElytraColorService.restore(client);
        CustomCapeService.restore(client);
    }

    /** A cosmetic you are hovering in the GUI wardrobe: shown as if it were on, so you can try it. */
    public static volatile String previewModule;

    public static boolean enabled(String id) {
        if (id.equals(previewModule)) return true;
        return KhanhClient.modules().byId(id) != null && KhanhClient.modules().byId(id).enabled();
    }

    public static WorldScanner scanner() {
        return WORLD_SCANNER;
    }

    public static StaffOnlineService staffOnline() {
        return STAFF_ONLINE;
    }

    public static SpotifyService spotify() {
        return SPOTIFY;
    }

    public static LivingEntity target() {
        return target;
    }

    public static void setTarget(LivingEntity living) {
        if (living == MinecraftClient.getInstance().player
            || (living instanceof net.minecraft.entity.player.PlayerEntity player
                && FriendService.protectsTargets()
                && FriendService.isFriend(player.getGameProfile().name()))) {
            return;
        }
        target = living;
        targetTicks = 6 * 20;
    }

    public static boolean bool(String moduleId, String optionId, boolean fallback) {
        ModuleOption option = option(moduleId, optionId);
        return option == null ? fallback : option.booleanValue();
    }

    public static int number(String moduleId, String optionId, int fallback) {
        ModuleOption option = option(moduleId, optionId);
        return option == null ? fallback : option.intValue();
    }

    public static double decimal(String moduleId, String optionId, double fallback) {
        ModuleOption option = option(moduleId, optionId);
        return option == null ? fallback : option.numberValue();
    }

    public static String choice(String moduleId, String optionId, String fallback) {
        ModuleOption option = option(moduleId, optionId);
        return option == null ? fallback : option.choiceValue();
    }

    public static int color(String moduleId, String optionId, int fallback) {
        ModuleOption option = option(moduleId, optionId);
        return option == null ? fallback : option.colorValue();
    }

    public static String text(String moduleId, String optionId, String fallback) {
        ModuleOption option = option(moduleId, optionId);
        return option == null ? fallback : option.textValue();
    }

    private static ModuleOption option(String moduleId, String optionId) {
        Module module = KhanhClient.modules().byId(moduleId);
        return module == null ? null : module.option(optionId);
    }

    private static void tickTarget(MinecraftClient client) {
        if (client.player == null || client.world == null) {
            target = null;
            targetTicks = 0;
            return;
        }
        if (client.crosshairTarget instanceof EntityHitResult hit
            && hit.getEntity() instanceof LivingEntity living
            && living != client.player) {
            setTarget(living);
            return;
        }
        if (target == null) {
            return;
        }
        boolean protectedFriend = target instanceof net.minecraft.entity.player.PlayerEntity player
            && FriendService.protectsTargets()
            && FriendService.isFriend(player.getGameProfile().name());
        if (protectedFriend || !target.isAlive() || target.getEntityWorld() != client.world
            || client.player.squaredDistanceTo(target) > 4096.0 || --targetTicks <= 0) {
            target = null;
            targetTicks = 0;
        }
    }

    private static int autoXpPreviousSlot = -1;

    private static void tickAutoTool(MinecraftClient client) {
        if (!enabled("auto_tool") || client.player == null || client.world == null || client.currentScreen != null) {
            autoXpPreviousSlot = -1;
            return;
        }
        tickAutoXp(client);
        if (!client.options.attackKey.isPressed() || !(client.crosshairTarget instanceof BlockHitResult hit)) {
            return;
        }

        BlockState state = client.world.getBlockState(hit.getBlockPos());
        if (state.isAir()) {
            return;
        }
        PlayerInventory inventory = client.player.getInventory();
        int bestSlot = inventory.getSelectedSlot();
        double bestScore = toolScore(inventory.getStack(bestSlot), state);
        for (int slot = 0; slot < PlayerInventory.getHotbarSize(); slot++) {
            double score = toolScore(inventory.getStack(slot), state);
            if (score > bestScore) {
                bestScore = score;
                bestSlot = slot;
            }
        }
        if (bestSlot != inventory.getSelectedSlot()) {
            inventory.setSelectedSlot(bestSlot);
        }
    }

    /**
     * Auto XP: hold right-click while looking down and it swaps to XP bottles from your hotbar
     * (Fast Place then throws them quickly); let go and your old slot comes back.
     */
    private static void tickAutoXp(MinecraftClient client) {
        PlayerInventory inventory = client.player.getInventory();
        boolean wanted = bool("auto_tool", "auto_xp", true) && client.options.useKey.isPressed()
            && client.player.getPitch() >= (float) decimal("auto_tool", "xp_pitch", 60.0);
        if (wanted) {
            if (inventory.getStack(inventory.getSelectedSlot()).isOf(net.minecraft.item.Items.EXPERIENCE_BOTTLE)) return;
            for (int slot = 0; slot < PlayerInventory.getHotbarSize(); slot++) {
                if (inventory.getStack(slot).isOf(net.minecraft.item.Items.EXPERIENCE_BOTTLE)) {
                    if (autoXpPreviousSlot < 0) autoXpPreviousSlot = inventory.getSelectedSlot();
                    inventory.setSelectedSlot(slot);
                    return;
                }
            }
        } else if (autoXpPreviousSlot >= 0) {
            if (inventory.getStack(inventory.getSelectedSlot()).isOf(net.minecraft.item.Items.EXPERIENCE_BOTTLE)
                || inventory.getStack(inventory.getSelectedSlot()).isEmpty()) {
                inventory.setSelectedSlot(autoXpPreviousSlot);
            }
            autoXpPreviousSlot = -1;
        }
    }

    private static double toolScore(ItemStack stack, BlockState state) {
        if (bool("auto_tool", "suitable_only", true) && !stack.isSuitableFor(state)) {
            return 0.0;
        }
        return stack.getMiningSpeedMultiplier(state) + (stack.isSuitableFor(state) ? 1000.0 : 0.0);
    }

    /**
     * Auto Relog: rejoins the server after a kick or lost connection, and optionally drops and
     * rejoins when you fall to the safety Y. Leaving only closes the connection; nothing is sent to
     * the server (no chat, command or extra packet).
     */
    private static final class AutoRelog {
        private ServerInfo lastServer;
        private ServerInfo pendingServer;
        private int reconnectTicks;
        private int attempts;
        private boolean armed = true;
        private boolean wasDisconnected;

        void tick(MinecraftClient client) {
            if (!enabled("auto_relog")) {
                pendingServer = null;
                reconnectTicks = 0;
                attempts = 0;
                armed = true;
                return;
            }
            String module = "auto_relog";
            if (client.world != null && client.getCurrentServerEntry() != null && !client.getCurrentServerEntry().isRealm()) {
                lastServer = client.getCurrentServerEntry();
                if (client.player != null && client.player.age > 100) attempts = 0;   // a stable join resets the count
            }

            // Kicked or lost connection: the disconnected screen appears while we were on a server.
            boolean disconnected = client.currentScreen instanceof net.minecraft.client.gui.screen.DisconnectedScreen;
            if (disconnected && !wasDisconnected && pendingServer == null && lastServer != null
                && bool(module, "on_kick", true) && attempts < number(module, "attempts", 5)) {
                pendingServer = lastServer;
                reconnectTicks = number(module, "delay", 3) * 20;
            }
            wasDisconnected = disconnected;

            if (pendingServer != null) {
                if (reconnectTicks > 0) reconnectTicks--;
                if (reconnectTicks <= 0 && client.world == null) {
                    attempts++;
                    reconnect(client);
                }
                return;
            }
            if (client.player == null || client.world == null || !bool(module, "safety_relog", true)) return;
            int safetyY = number(module, "safety_y", -55);
            if (client.player.getY() > safetyY + 3) armed = true;
            ServerInfo current = client.getCurrentServerEntry();
            if (armed && current != null && !current.isRealm() && client.player.getY() <= safetyY) {
                pendingServer = current;
                reconnectTicks = number(module, "delay", 3) * 20;
                armed = false;
                // Just close the connection, exactly like quitting: no message or packet is sent.
                client.disconnect(Text.literal("Khanh Client Auto Relog"));
            }
        }

        private void reconnect(MinecraftClient client) {
            ServerInfo server = pendingServer;
            pendingServer = null;
            if (server == null) return;
            try {
                Screen parent = new MultiplayerScreen(new TitleScreen());
                ConnectScreen.connect(parent, client, ServerAddress.parse(server.address), server, false, null);
            } catch (Exception exception) {
                KhanhClient.LOGGER.error("Auto Relog could not reconnect to {}", server.address, exception);
                client.setScreen(new MultiplayerScreen(new TitleScreen()));
            }
        }
    }
}
