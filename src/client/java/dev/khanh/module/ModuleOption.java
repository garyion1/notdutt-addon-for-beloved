package dev.khanh.module;

import java.util.List;
import java.util.Locale;

public final class ModuleOption {
    public enum Kind {
        BOOLEAN,
        NUMBER,
        CHOICE,
        COLOR,
        TEXT,
        ACTION
    }

    private final String id;
    private final String label;
    private final String description;
    private final Kind kind;
    private final double minimum;
    private final double maximum;
    private final double step;
    private final List<String> choices;
    private final Runnable action;
    private final int maximumTextLength;
    private boolean booleanValue;
    private double numberValue;
    private int choiceIndex;
    private int colorValue;
    private String textValue;
    private int actionKeybind = -1;
    // Registration values, so any setting can return to its default.
    private final boolean defaultBoolean;
    private final double defaultNumber;
    private final int defaultChoiceIndex;
    private final int defaultColor;
    private final String defaultText;

    private ModuleOption(String id, String label, String description, Kind kind, boolean booleanValue,
                         double numberValue, double minimum, double maximum, double step,
                         List<String> choices, int choiceIndex, Runnable action, String textValue,
                         int maximumTextLength, int colorValue) {
        this.id = id;
        this.label = label;
        this.description = description;
        this.kind = kind;
        this.booleanValue = booleanValue;
        this.numberValue = numberValue;
        this.minimum = minimum;
        this.maximum = maximum;
        this.step = step;
        this.choices = List.copyOf(choices);
        this.choiceIndex = choiceIndex;
        this.colorValue = 0xFF000000 | (colorValue & 0x00FFFFFF);
        this.action = action;
        this.textValue = textValue == null ? "" : textValue;
        this.maximumTextLength = Math.max(1, maximumTextLength);
        this.defaultBoolean = this.booleanValue;
        this.defaultNumber = this.numberValue;
        this.defaultChoiceIndex = this.choiceIndex;
        this.defaultColor = this.colorValue;
        this.defaultText = this.textValue;
    }

    /** True when the setting still has the value it was registered with. */
    public boolean isDefault() {
        return switch (kind) {
            case BOOLEAN -> booleanValue == defaultBoolean;
            case NUMBER -> Math.abs(numberValue - defaultNumber) < Math.max(1e-9, step / 2.0);
            case CHOICE -> choiceIndex == defaultChoiceIndex;
            case COLOR -> colorValue == defaultColor;
            case TEXT -> textValue.equals(defaultText);
            case ACTION -> true;
        };
    }

    public boolean defaultBooleanValue() { return defaultBoolean; }
    public double defaultNumberValue() { return defaultNumber; }
    public String defaultChoiceValue() { return choices.isEmpty() ? "" : choices.get(defaultChoiceIndex); }
    public int defaultColorValue() { return defaultColor; }

    public void reset() {
        booleanValue = defaultBoolean;
        numberValue = defaultNumber;
        choiceIndex = defaultChoiceIndex;
        colorValue = defaultColor;
        textValue = defaultText;
    }

    public static ModuleOption bool(String id, String label, String description, boolean value) {
        return new ModuleOption(id, label, description, Kind.BOOLEAN, value, 0.0, 0.0, 1.0, 1.0, List.of(), 0, null, "", 1, 0);
    }

    public static ModuleOption number(String id, String label, String description, double value, double minimum, double maximum, double step) {
        return new ModuleOption(id, label, description, Kind.NUMBER, false, value, minimum, maximum, step, List.of(), 0, null, "", 1, 0);
    }

    public static ModuleOption choice(String id, String label, String description, String value, String... choices) {
        List<String> values = List.of(choices);
        int index = Math.max(0, values.indexOf(value));
        return new ModuleOption(id, label, description, Kind.CHOICE, false, 0.0, 0.0, 1.0, 1.0, values, index, null, "", 1, 0);
    }

    public static ModuleOption color(String id, String label, String description, int value) {
        return new ModuleOption(id, label, description, Kind.COLOR, false, 0.0, 0.0, 1.0, 1.0,
            List.of(), 0, null, "", 1, value);
    }

    public static ModuleOption text(String id, String label, String description, String value, int maximumLength) {
        return new ModuleOption(id, label, description, Kind.TEXT, false, 0.0, 0.0, 1.0, 1.0,
            List.of(), 0, null, value, maximumLength, 0);
    }

    public static ModuleOption action(String id, String label, String description, Runnable action) {
        return new ModuleOption(id, label, description, Kind.ACTION, false, 0.0, 0.0, 1.0, 1.0, List.of(), 0, action, "", 1, 0);
    }

    public String id() {
        return id;
    }

    public String label() {
        return label;
    }

    public String description() {
        return description;
    }

    public Kind kind() {
        return kind;
    }

    public boolean booleanValue() {
        return booleanValue;
    }

    public double numberValue() {
        return numberValue;
    }

    public int intValue() {
        return (int) Math.round(numberValue);
    }

    public String choiceValue() {
        return choices.isEmpty() ? "" : choices.get(choiceIndex);
    }

    public String textValue() {
        return textValue;
    }

    public int colorValue() {
        return colorValue;
    }

    public void setColor(int value) {
        if (kind == Kind.COLOR) {
            colorValue = 0xFF000000 | (value & 0x00FFFFFF);
        }
    }

    public void setText(String value) {
        if (kind != Kind.TEXT) {
            return;
        }
        String cleaned = value == null ? "" : value.replace('\n', ' ').replace('\r', ' ');
        int count = cleaned.codePointCount(0, cleaned.length());
        if (count > maximumTextLength) {
            cleaned = cleaned.substring(0, cleaned.offsetByCodePoints(0, maximumTextLength));
        }
        textValue = cleaned;
    }

    public void appendText(String value) {
        setText(textValue + (value == null ? "" : value));
    }

    public void deleteLastTextCharacter() {
        if (kind == Kind.TEXT && !textValue.isEmpty()) {
            textValue = textValue.substring(0, textValue.offsetByCodePoints(textValue.length(), -1));
        }
    }

    public double ratio() {
        return maximum <= minimum ? 0.0 : (numberValue - minimum) / (maximum - minimum);
    }

    public void toggle() {
        if (kind == Kind.BOOLEAN) {
            booleanValue = !booleanValue;
        }
    }

    public void setBoolean(boolean value) {
        if (kind == Kind.BOOLEAN) {
            booleanValue = value;
        }
    }

    public void setNumber(double value) {
        if (kind != Kind.NUMBER) {
            return;
        }
        double snapped = Math.round(value / step) * step;
        numberValue = Math.max(minimum, Math.min(maximum, snapped));
    }

    public void setFromRatio(double ratio) {
        setNumber(minimum + Math.max(0.0, Math.min(1.0, ratio)) * (maximum - minimum));
    }

    public void cycle(int direction) {
        if (kind != Kind.CHOICE || choices.isEmpty()) {
            return;
        }
        choiceIndex = Math.floorMod(choiceIndex + direction, choices.size());
    }

    public void setChoice(String value) {
        if (kind != Kind.CHOICE) {
            return;
        }
        int index = choices.indexOf(value);
        if (index >= 0) {
            choiceIndex = index;
        }
    }

    public void trigger() {
        if (kind == Kind.ACTION && action != null) {
            action.run();
        }
    }

    public int actionKeybind() {
        return kind == Kind.ACTION ? actionKeybind : -1;
    }

    public void setActionKeybind(int keybind) {
        if (kind == Kind.ACTION) {
            actionKeybind = keybind >= 0 && keybind <= org.lwjgl.glfw.GLFW.GLFW_KEY_LAST ? keybind : -1;
        }
    }

    public String displayValue() {
        return switch (kind) {
            case BOOLEAN -> booleanValue ? "ON" : "OFF";
            case CHOICE -> choiceValue();
            case COLOR -> String.format(Locale.ROOT, "#%06X", colorValue & 0x00FFFFFF);
            case TEXT -> textValue;
            case ACTION -> "RUN";
            case NUMBER -> Math.abs(numberValue - Math.rint(numberValue)) < 0.0001
                ? Integer.toString((int) Math.rint(numberValue))
                : Math.abs(numberValue) < 0.1
                    ? String.format(Locale.ROOT, "%.3f", numberValue)
                    : String.format(Locale.ROOT, "%.1f", numberValue);
        };
    }
}
