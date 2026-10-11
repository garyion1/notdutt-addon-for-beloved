package dev.khanh.module;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What Khanh Client is: its five sections and the modules in each, in order. Modules that are not
 * listed here (and are not part of the client's own settings) are retired: hidden and always off.
 */
public final class KhanhCatalog {
    /** One entry in a section: a module id plus the name/blurb Khanh shows for it (null keeps the module's own). */
    public record Entry(String id, String name, String description) { }

    public record Section(String title, String icon, List<Entry> entries) { }

    public static final List<Section> SECTIONS = List.of(
        new Section("PVP", "⚔", List.of(
            new Entry("auto_anchor", "Anchor Macro", "Place an anchor and it fills it with glowstone and blows it up"),
            new Entry("auto_crystal", "Auto Crystal", null),
            new Entry("crystal_optimizer", "Fast Crystal", "Breaks and places end crystals as fast as the server allows"),
            new Entry("fast_place", "Fast Use", "Use blocks, crystals and XP bottles every tick while held"),
            new Entry("auto_totem", "Auto Totem", null),
            new Entry("hover_totem", "Hover Totem", null),
            new Entry("reach", "Reach", "Hit and use things from further away"),
            new Entry("shield_breaker", "Shield Breaker", null),
            new Entry("hitbox_outline", "Hitbox Outline", "Outlines the real hitbox of players and mobs"),
            new Entry("trigger_bot", "Auto Trigger", "Attacks the moment your crosshair is on a player"))),
        new Section("BASE FINDING", "⌖", List.of(
            new Entry("sus_esp", "Sus Chunk Finder", null),
            new Entry("prime_chunk_finder", "Chunk Finder", "Marks chunks with cultivated growth or busy light updates"),
            new Entry("bypasses", "Bypasses", "Sees through anti-xray: ignores fake ores the server hides in stone"),
            new Entry("motion_scanner", "Motion Scanner", "Spots things moving far away: players, minecarts, boats and pets"),
            new Entry("rtp_map", "Region Map", "A live map of the region around you"),
            new Entry("cluster_counter", "Cluster Counter", "Counts amethyst clusters and geodes around you"))),
        new Section("ESP", "◉", List.of(
            new Entry("amethyst_esp", "Cluster ESP", "Through-wall amethyst clusters and geodes"),
            new Entry("block_notifier", "Block ESP", "Highlights the blocks you choose through walls"),
            new Entry("block_entity_debug", "Block Entity Debug", "Boxes every block entity: chests, spawners, signs, beacons..."),
            new Entry("storage_esp", "Storage ESP", null),
            new Entry("player_esp", "Player ESP", null),
            new Entry("hole_esp", "Hole ESP", null))),
        new Section("ESSENTIALS", "✦", List.of(
            new Entry("creamy_keys", "Creamy Key", null),
            new Entry("shader_fog", "Custom Sky", null),
            new Entry("custom_glint", "Custom Glint", null),
            new Entry("spotify_hud", "Music Player", "Spotify on your HUD with art, progress and controls"),
            new Entry("media_player", "Media Player", "Whatever is playing on your PC (YouTube, Apple Music...) on your HUD"),
            new Entry("fps_boost", "Game Quality", "Graphics, particles and render tuning for smoother play"),
            new Entry("swing_speed", "Swing Speed", null),
            new Entry("jump_circles", "Jump Circle", null),
            new Entry("hit_effects", "Hit Effects", "Sparks, crits and sounds every time you hit something"),
            new Entry("fonts", "Fonts", "Pick the font for the menu and HUD"))),
        new Section("CUSTOMIZE", "◐", List.of(
            new Entry("config", "Configs", null),
            new Entry("client_colors", "GUI Colors", "Blue, Red, Orange, Yellow, Purple, Green, Lime or Rainbow")))
    );

    /** The client's own plumbing: not in the module list, but reachable from Settings. */
    public static final Set<String> SETTINGS = Set.of("hud", "intro", "client_sounds");

    private KhanhCatalog() { }

    public static void apply(ModuleManager modules) {
        Map<String, Entry> listed = new LinkedHashMap<>();
        for (Section section : SECTIONS) for (Entry entry : section.entries()) listed.put(entry.id(), entry);
        for (Module module : modules.all()) {
            Entry entry = listed.get(module.id());
            if (entry != null) {
                if (entry.name() != null) module.rename(entry.name(), entry.description());
            } else if (!SETTINGS.contains(module.id())) {
                modules.remove(module.id());   // not on Khanh's list: gone from the client
            }
        }
    }

    public static boolean listed(String id) {
        for (Section section : SECTIONS) for (Entry entry : section.entries()) if (entry.id().equals(id)) return true;
        return false;
    }
}
