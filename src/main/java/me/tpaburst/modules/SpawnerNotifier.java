package me.tpaburst.modules;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import me.tpaburst.TPABurstAddon;
import me.tpaburst.ui.SpawnerAlerts;
import me.tpaburst.ui.SpawnerNotifierToast;
import meteordevelopment.meteorclient.events.game.GameJoinedEvent;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.Renderer2D;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.renderer.text.TextRenderer;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.render.NametagUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.MobSpawnerBlockEntity;
import net.minecraft.block.spawner.MobSpawnerLogic;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;
import org.joml.Vector3d;

public final class SpawnerNotifier extends Module {
    public enum AlertStyle {
        Toast,
        Hud,
        Both
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");
    private final SettingGroup sgAlerts = settings.createGroup("Alerts");

    private final Setting<Integer> range = sgGeneral.add(new IntSetting.Builder()
        .name("range")
        .description("How far around you to look for spawners.")
        .defaultValue(256)
        .min(8)
        .sliderMax(512)
        .build()
    );

    public final Setting<SettingColor> pillarColor = sgRender.add(new ColorSetting.Builder()
        .name("pillar-color")
        .description("Colour of the 3x3 beacon pillar.")
        .defaultValue(new SettingColor(255, 0, 0, 80))
        .build()
    );

    private final Setting<Integer> pillarOpacity = sgRender.add(new IntSetting.Builder()
        .name("pillar-opacity")
        .defaultValue(80)
        .range(0, 255)
        .sliderMax(255)
        .build()
    );

    private final Setting<Boolean> spawnerEsp = sgRender.add(new BoolSetting.Builder()
        .name("spawner-esp")
        .description("Highlights the spawner block itself.")
        .defaultValue(true)
        .build()
    );

    private final Setting<SettingColor> spawnerColor = sgRender.add(new ColorSetting.Builder()
        .name("spawner-color")
        .defaultValue(new SettingColor(255, 0, 0, 150))
        .visible(spawnerEsp::get)
        .build()
    );

    private final Setting<Integer> spawnerAlpha = sgRender.add(new IntSetting.Builder()
        .name("spawner-alpha")
        .defaultValue(80)
        .range(0, 255)
        .sliderMax(255)
        .visible(spawnerEsp::get)
        .build()
    );

    private final Setting<Double> textScale = sgRender.add(new DoubleSetting.Builder()
        .name("text-scale")
        .defaultValue(1.2)
        .min(0.1)
        .sliderMax(5.0)
        .build()
    );

    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("render-mode")
        .defaultValue(ShapeMode.Both)
        .build()
    );

    private final Setting<AlertStyle> alertStyle = sgAlerts.add(new EnumSetting.Builder<AlertStyle>()
        .name("alert-style")
        .description("Toast = vanilla corner toast. Hud = the movable 'spawner-alerts' HUD element. Both = show both.")
        .defaultValue(AlertStyle.Toast)
        .build()
    );

    private final Setting<Boolean> toastAlerts = sgAlerts.add(new BoolSetting.Builder()
        .name("toast-alerts")
        .description("Shows a popup (toast and/or HUD) when a new spawner is found.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> toastDuration = sgAlerts.add(new IntSetting.Builder()
        .name("toast-duration")
        .description("How long popups stay visible, in milliseconds.")
        .defaultValue(6500)
        .range(2500, 15000)
        .sliderRange(2500, 15000)
        .visible(toastAlerts::get)
        .build()
    );

    private final Setting<SettingColor> progressBarColor = sgAlerts.add(new ColorSetting.Builder()
        .name("progress-bar-color")
        .description("Accent used by popups, the HUD and the in-world labels.")
        .defaultValue(new SettingColor(224, 69, 69, 255))
        .build()
    );

    private final Setting<Boolean> chatAlerts = sgAlerts.add(new BoolSetting.Builder()
        .name("chat-alerts")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> soundAlerts = sgAlerts.add(new BoolSetting.Builder()
        .name("sound-alerts")
        .defaultValue(true)
        .build()
    );

    private final Map<BlockPos, String> trackedSpawners = new HashMap<>();
    private final Map<BlockPos, String> resolvedTypes = new HashMap<>();
    private final Set<Long> alertedSpawnerKeys = new HashSet<>();
    private final Color workingPillarColor = new Color();
    private final Color workingSpawnerColor = new Color();
    private final Color labelAccent = new Color();
    private final Color labelBg = new Color(16, 17, 22, 200);
    private final Color labelTitle = new Color(255, 255, 255, 255);
    private final Color labelSub = new Color(201, 205, 214, 230);
    private boolean inWorld;
    private boolean scanNow;
    private int preTickDelay = 0;

    public SpawnerNotifier() {
        super(TPABurstAddon.CATEGORY, "spawner-noti", "Highlights spawners with a 3x3 filled vertical pillar, alerts, and text tags.");
    }

    public int getTrackedCount() {
        return trackedSpawners.size();
    }

    public int getAccentRgb() {
        SettingColor c = progressBarColor.get();
        return c.r << 16 | c.g << 8 | c.b;
    }

    @Override
    public void onActivate() {
        reset();
    }

    @Override
    public void onDeactivate() {
        reset();
        SpawnerAlerts.clear();
    }

    private void reset() {
        trackedSpawners.clear();
        resolvedTypes.clear();
        alertedSpawnerKeys.clear();
        preTickDelay = 0;
        scanNow = true;
        inWorld = false;
    }

    @EventHandler
    private void onGameJoined(GameJoinedEvent event) {
        reset();
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        reset();
        SpawnerAlerts.clear();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.world == null || mc.player == null) {
            inWorld = false;
            return;
        }

        if (!inWorld) {
            reset();
            inWorld = true;
        }

        if (scanNow) {
            scanNow = false;
        } else {
            if (++preTickDelay < 20) return;
            preTickDelay = 0;
        }

        int r = range.get();
        int rSq = r * r;
        BlockPos playerPos = mc.player.getBlockPos();
        World world = mc.world;
        Map<BlockPos, String> current = new HashMap<>();
        Set<Long> seenKeys = new HashSet<>();
        int px = playerPos.getX();
        int pz = playerPos.getZ();

        for (int cx = (px - r) >> 4; cx <= (px + r) >> 4; cx++) {
            for (int cz = (pz - r) >> 4; cz <= (pz + r) >> 4; cz++) {
                if (!world.getChunkManager().isChunkLoaded(cx, cz)) continue;
                WorldChunk chunk = world.getChunk(cx, cz);

                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    if (!(be instanceof MobSpawnerBlockEntity spawner)) continue;
                    BlockPos pos = spawner.getPos();
                    double distSq = PlayerUtils.squaredDistanceTo(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
                    if (distSq > rSq) continue;

                    String type = spawnerType(spawner, pos);
                    current.put(pos, type);
                    long key = pos.asLong();
                    seenKeys.add(key);
                    if (alertedSpawnerKeys.add(key)) alert(type, pos);
                }
            }
        }

        alertedSpawnerKeys.removeIf(key -> !seenKeys.contains(key));
        resolvedTypes.keySet().removeIf(pos -> !seenKeys.contains(pos.asLong()));
        trackedSpawners.clear();
        trackedSpawners.putAll(current);
    }

    private void alert(String type, BlockPos pos) {
        boolean popups = toastAlerts.get();
        AlertStyle style = alertStyle.get();
        boolean toast = popups && style != AlertStyle.Hud && mc.getToastManager() != null;
        boolean hud = popups && style != AlertStyle.Toast;

        if (toast) {
            mc.getToastManager().add(new SpawnerNotifierToast(type, pos.getX(), pos.getY(), pos.getZ(),
                toastDuration.get(), soundAlerts.get(), getAccentRgb()));
        }
        if (hud) {
            SpawnerAlerts.push(type, pos.getX(), pos.getY(), pos.getZ(), toastDuration.get(), getAccentRgb());
        }
        if (!toast && soundAlerts.get()) {
            mc.player.playSound(SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), 1.0F, 1.0F);
        }
        if (chatAlerts.get()) {
            mc.player.sendMessage(Text.literal("§c[Spawner Noti] §f" + type + " Spawner §7at X: " + pos.getX() + " Y: " + pos.getY() + " Z: " + pos.getZ()), false);
        }
    }

    private String spawnerType(MobSpawnerBlockEntity spawner, BlockPos pos) {
        String type = readType(spawner);
        String cached = resolvedTypes.get(pos);
        if (badType(type) && cached != null && !badType(cached)) return cached;
        if (!badType(type)) {
            resolvedTypes.put(pos, type);
            return type;
        }
        return cached != null ? cached : type;
    }

    private boolean badType(String type) {
        return type == null || type.isEmpty() || "Unknown".equals(type) || "Pig".equals(type);
    }

    private String readType(MobSpawnerBlockEntity spawner) {
        try {
            if (mc.world != null) {
                String fromNbt = typeFromNbt(spawner.toInitialChunkDataNbt(mc.world.getRegistryManager()));
                if (fromNbt != null) return fromNbt;
                fromNbt = typeFromNbt(spawner.createNbt(mc.world.getRegistryManager()));
                if (fromNbt != null) return fromNbt;
            }
            MobSpawnerLogic logic = spawner.getLogic();
            if (logic != null) {
                Entity entity = logic.getRenderedEntity(mc.world, spawner.getPos());
                if (entity != null) return entity.getType().getName().getString();
            }
        } catch (Exception ignored) {
        }
        return "Unknown";
    }

    private String typeFromNbt(NbtCompound nbt) {
        if (nbt == null) return null;
        for (String key : new String[]{"SpawnData", "spawn_data"}) {
            if (!nbt.contains(key)) continue;
            NbtCompound spawnData = nbt.getCompound(key).orElse(null);
            if (spawnData == null) continue;
            if (spawnData.contains("entity")) {
                String name = nameFromEntityNbt(spawnData.getCompound("entity").orElse(null));
                if (name != null) return name;
            }
            String name = nameFromEntityNbt(spawnData);
            if (name != null) return name;
        }
        return null;
    }

    private String nameFromEntityNbt(NbtCompound entityNbt) {
        if (entityNbt == null || !entityNbt.contains("id")) return null;
        String rawId = entityNbt.getString("id").orElse("");
        if (rawId.isEmpty()) return null;
        EntityType<?> entityType = Registries.ENTITY_TYPE.get(Identifier.of(rawId));
        return entityType != null ? entityType.getName().getString() : null;
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (mc.world == null || mc.player == null || trackedSpawners.isEmpty()) return;
        workingPillarColor.set(pillarColor.get());
        workingPillarColor.a = pillarOpacity.get();
        workingSpawnerColor.set(spawnerColor.get());
        workingSpawnerColor.a = spawnerAlpha.get();

        for (BlockPos pos : trackedSpawners.keySet()) {
            double x = pos.getX();
            double y = pos.getY();
            double z = pos.getZ();
            event.renderer.box(x - 1.0, -60.0, z - 1.0, x + 2.0, 150.0, z + 2.0, workingPillarColor, workingPillarColor, shapeMode.get(), 0);
            if (spawnerEsp.get()) {
                event.renderer.box(x, y, z, x + 1.0, y + 1.0, z + 1.0, workingSpawnerColor, workingSpawnerColor, shapeMode.get(), 0);
            }
        }
    }

    @EventHandler
    private void onRender2D(Render2DEvent event) {
        if (mc.world == null || trackedSpawners.isEmpty()) return;
        SettingColor accent = progressBarColor.get();
        labelAccent.set(accent.r, accent.g, accent.b, 255);

        for (Map.Entry<BlockPos, String> entry : trackedSpawners.entrySet()) {
            BlockPos pos = entry.getKey();
            Vector3d textPos = new Vector3d(pos.getX() + 0.5, pos.getY() + 1.35, pos.getZ() + 0.5);
            renderSpawnerLabel(textPos, entry.getValue(), pos);
        }
    }

    private void renderSpawnerLabel(Vector3d textPos, String mobType, BlockPos pos) {
        if (!NametagUtils.to2D(textPos, textScale.get())) return;
        NametagUtils.begin(textPos);
        TextRenderer text = TextRenderer.get();
        text.begin(1.0, false, true);

        String tag = "Spawner";
        int dist = mc.player != null ? (int) Math.sqrt(mc.player.squaredDistanceTo(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5)) : 0;
        String distLabel = dist + "m";
        double mobW = text.getWidth(mobType);
        double gapW = text.getWidth(" ");
        double tagW = text.getWidth(tag);
        double sepW = text.getWidth(" · ");
        double distW = text.getWidth(distLabel);
        double contentW = mobW + gapW + tagW + sepW + distW;
        double lineH = text.getHeight();
        double padX = 6.0;
        double padY = 3.0;
        double boxW = contentW + padX * 2.0;
        double boxH = lineH + padY * 2.0;
        double left = -boxW / 2.0;
        double top = -padY;

        Renderer2D.COLOR.begin();
        Renderer2D.COLOR.quad(left, top, boxW, boxH, labelBg);
        Renderer2D.COLOR.quad(left, top, 2.0, boxH, labelAccent);
        Renderer2D.COLOR.quad(left, top + boxH - 1.0, boxW, 1.0, labelAccent);
        Renderer2D.COLOR.render();

        double x = -contentW / 2.0;
        text.render(mobType, x, 0.0, labelTitle);
        x += mobW + gapW;
        text.render(tag, x, 0.0, labelAccent);
        x += tagW + sepW;
        text.render(distLabel, x, 0.0, labelSub);
        text.end();
        NametagUtils.end();
    }

    @Override
    public String getInfoString() {
        return Integer.toString(trackedSpawners.size());
    }
}
