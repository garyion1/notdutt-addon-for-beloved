package dev.khanh.module;

import java.util.ArrayList;
import java.util.List;

public final class Module {
    private final String id;
    private String name;
    private String description;
    private boolean retired;
    private final Category category;
    private final List<ModuleOption> options = new ArrayList<>();
    private boolean enabled;
    private final boolean defaultEnabled;
    private int keybind = -1;

    public Module(String id, String name, String description, Category category, boolean enabled) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.category = category;
        this.enabled = enabled;
        this.defaultEnabled = enabled;
    }

    /** Khanh's catalog can give a module its own name and blurb. */
    public void rename(String name, String description) {
        this.name = name;
        if (description != null) this.description = description;
    }

    /** Retired modules are not part of Khanh Client: hidden everywhere and always off. */
    public boolean retired() {
        return retired;
    }

    public void retire() {
        retired = true;
        enabled = false;
    }

    /** Whether the module starts on before anyone changes it. */
    public boolean defaultEnabled() {
        return defaultEnabled;
    }

    public String id() {
        return id;
    }

    public String name() {
        return name;
    }

    public String description() {
        return description;
    }

    public Category category() {
        return category;
    }

    public boolean enabled() {
        return enabled;
    }

    public List<ModuleOption> options() {
        return List.copyOf(options);
    }

    public Module option(ModuleOption option) {
        options.add(option);
        return this;
    }

    /** True when any setting differs from its default. */
    public boolean hasChangedOptions() {
        for (ModuleOption option : options) if (!option.isDefault()) return true;
        return false;
    }

    /** Returns every setting to its default; the on/off state and keybind are kept. */
    public void resetOptions() {
        for (ModuleOption option : options) option.reset();
    }

    public ModuleOption option(String id) {
        return options.stream().filter(option -> option.id().equals(id)).findFirst().orElse(null);
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled && !retired;
    }

    public void toggle() {
        enabled = !enabled && !retired;
    }

    public int keybind() {
        return keybind;
    }

    public void setKeybind(int keybind) {
        this.keybind = keybind >= 0 && keybind <= org.lwjgl.glfw.GLFW.GLFW_KEY_LAST ? keybind : -1;
    }
}
