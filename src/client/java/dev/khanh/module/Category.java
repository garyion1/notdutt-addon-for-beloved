package dev.khanh.module;

public enum Category {
    COMBAT("COMBAT"),
    RENDER("RENDER"),
    PLAYER("PLAYER"),
    MISC("MISC"),
    CLIENT("CLIENT"),
    VISUAL("VISUAL"),
    CONFIG("CONFIG");

    private final String displayName;

    Category(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
