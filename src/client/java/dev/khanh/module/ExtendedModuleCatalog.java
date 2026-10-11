package dev.khanh.module;

/** Additional client features retained from the previous test build. */
final class ExtendedModuleCatalog {
    @FunctionalInterface interface Registrar { Module add(String id, String name, String description, Category category); }
    private ExtendedModuleCatalog() { }

    static void register(Registrar r) {
        r.add("auto_sprint", "Auto Sprint", "Walks you forward and sprints for you, like auto walk but sprinting", Category.PLAYER)
            .option(ModuleOption.bool("auto_walk", "Auto Walk", "Walk forward on its own (off only sprints while you hold W)", true))
            .option(ModuleOption.bool("stop_in_water", "Stop in Water/Lava", "Stop walking while you are in water or lava", false));
        r.add("client_sounds", "Client Sounds", "Choose whether interface interactions play their sound", Category.CLIENT)
            .option(ModuleOption.bool("muted", "Mute Interface", "Silence GUI and module-toggle sounds", false));
    }
}
