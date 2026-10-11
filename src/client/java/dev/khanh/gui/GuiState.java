package dev.khanh.gui;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.khanh.KhanhClient;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.fabricmc.loader.api.FabricLoader;

/** The menu's own preferences: starred modules, recently used modules and the interface
 * accent. Kept in one small file beside the main config so it never touches module data. */
final class GuiState {
    /** Modules that live on the Settings page instead of the module list (search still finds them). */
    public static final java.util.Set<String> SETTINGS_ONLY = java.util.Set.of("client_colors");

    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("khanh-client-gui.json");
    private static final Path OLD_FAVOURITES = FabricLoader.getInstance().getConfigDir().resolve("khanh-client-favorites.json");
    private static final int RECENT_LIMIT = 12;
    private static final Gson GSON = new Gson();
    private static Set<String> favourites;
    private static List<String> recent;
    private static int accent;
    private static String seenVersion = "";

    private GuiState() {
    }

    static boolean favourite(String id) {
        load();
        return favourites.contains(id);
    }

    static boolean toggleFavourite(String id) {
        load();
        boolean now = !favourites.remove(id);
        if (now) favourites.add(id);
        save();
        return now;
    }

    static int favouriteCount() {
        load();
        return favourites.size();
    }

    /** Most recent first. */
    static List<String> recent() {
        load();
        return List.copyOf(recent);
    }

    static void used(String id) {
        load();
        if (!recent.isEmpty() && recent.get(0).equals(id)) return;
        recent.remove(id);
        recent.add(0, id);
        while (recent.size() > RECENT_LIMIT) recent.remove(recent.size() - 1);
        save();
    }

    /** The interface accent, or 0 for Zero blue. */
    static int accent() {
        load();
        return accent;
    }

    /**
     * The accent every Khanh Client menu shares: a Client Colours GUI override, else the accent
     * picked in Settings, else Khanh Client's black-and-white default.
     */
    static int interfaceAccent() {
        if (dev.khanh.feature.KhanhFeatures.enabled("client_colors")
            && dev.khanh.feature.KhanhFeatures.bool("client_colors", "gui_override", false)) {
            return dev.khanh.feature.KhanhFeatures.color("client_colors", "gui_color", 0xFF9D4EFF);
        }
        int custom = accent();
        return custom != 0 ? 0xFF000000 | custom : 0xFFF2F2F6;
    }

    /**
     * How well a module matches a search (lower is better; -1 = no match): exact name, then name
     * starting with the query, a word in the name starting with it, name containing it,
     * description, and finally a setting's name. The best match is always first in the list.
     */
    static int searchRank(dev.khanh.module.Module module, String query) {
        String q = query.toLowerCase(java.util.Locale.ROOT).strip();
        if (q.isEmpty()) return 0;
        String name = module.name().toLowerCase(java.util.Locale.ROOT);
        if (name.equals(q)) return 0;
        if (name.startsWith(q)) return 1;
        for (String word : name.split("[ /]+")) if (word.startsWith(q)) return 2;
        if (name.contains(q)) return 3;
        if (module.description().toLowerCase(java.util.Locale.ROOT).contains(q)) return 4;
        for (dev.khanh.module.ModuleOption option : module.options()) {
            if (option.label().toLowerCase(java.util.Locale.ROOT).contains(q)) return 5;
        }
        return -1;
    }

    /** The last version whose "What's new" card was shown. */
    static String seenVersion() {
        load();
        return seenVersion;
    }

    static void setSeenVersion(String version) {
        load();
        seenVersion = version;
        save();
    }

    static void setAccent(int color) {
        load();
        accent = color;
        save();
    }

    private static void load() {
        if (favourites != null) return;
        favourites = new LinkedHashSet<>();
        recent = new ArrayList<>();
        try {
            if (Files.isRegularFile(FILE)) {
                JsonObject root = GSON.fromJson(Files.readString(FILE, StandardCharsets.UTF_8), JsonObject.class);
                if (root != null) {
                    readList(root.getAsJsonArray("favourites"), favourites);
                    readList(root.getAsJsonArray("recent"), recent);
                    if (root.has("accent")) accent = root.get("accent").getAsInt();
                    if (root.has("seenVersion")) seenVersion = root.get("seenVersion").getAsString();
                }
            } else if (Files.isRegularFile(OLD_FAVOURITES)) {
                JsonArray old = GSON.fromJson(Files.readString(OLD_FAVOURITES, StandardCharsets.UTF_8), JsonArray.class);
                readList(old, favourites);
                save();
            }
        } catch (IOException | RuntimeException error) {
            KhanhClient.LOGGER.warn("Khanh Client could not read its menu preferences", error);
        }
    }

    private static void readList(JsonArray array, java.util.Collection<String> into) {
        if (array == null) return;
        for (JsonElement element : array) into.add(element.getAsString());
    }

    private static void save() {
        JsonObject root = new JsonObject();
        JsonArray fav = new JsonArray();
        favourites.forEach(fav::add);
        JsonArray rec = new JsonArray();
        recent.forEach(rec::add);
        root.add("favourites", fav);
        root.add("recent", rec);
        root.addProperty("accent", accent);
        root.addProperty("seenVersion", seenVersion);
        try {
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, GSON.toJson(root), StandardCharsets.UTF_8);
        } catch (IOException error) {
            KhanhClient.LOGGER.warn("Khanh Client could not save its menu preferences", error);
        }
    }
}
