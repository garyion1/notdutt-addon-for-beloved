package me.tpaburst;

public final class SharedState {
    public static volatile boolean pauseTPA = false;

    private SharedState() {
    }
}
