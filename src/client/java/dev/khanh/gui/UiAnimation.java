package dev.khanh.gui;

/** Critically damped spring behind every classic-GUI motion. It never overshoots,
 * and a new target keeps the current velocity, so fast scrolling or hovering glides
 * instead of restarting from a standstill. The duration sets how quickly it settles. */
final class UiAnimation {
    private static final float STEP = 1.0F / 240.0F;
    private float value;
    private float velocity;
    private float target;
    private float omega = 30.0F;
    private long lastNanos;
    private boolean moving;

    UiAnimation(float initialValue) {
        snapTo(initialValue);
    }

    void animateTo(float newTarget, long durationMillis) {
        if (durationMillis <= 1L) {
            if (Float.compare(target, newTarget) != 0 || moving) snapTo(newTarget);
            return;
        }
        omega = 4.8F / (durationMillis / 1000.0F);
        if (Float.compare(target, newTarget) == 0) {
            return;
        }
        value();
        target = newTarget;
        moving = true;
    }

    void snapTo(float newValue) {
        value = newValue;
        target = newValue;
        velocity = 0.0F;
        moving = false;
        lastNanos = System.nanoTime();
    }

    float value() {
        long now = System.nanoTime();
        if (!moving) {
            lastNanos = now;
            return value;
        }
        float elapsed = Math.min(0.05F, (now - lastNanos) / 1_000_000_000.0F);
        lastNanos = now;
        int steps = Math.max(1, (int) Math.ceil(elapsed / STEP));
        float dt = elapsed / steps;
        for (int i = 0; i < steps; i++) {
            float acceleration = -omega * omega * (value - target) - 2.0F * omega * velocity;
            velocity += acceleration * dt;
            value += velocity * dt;
        }
        if (Math.abs(value - target) < 0.002F && Math.abs(velocity) < 0.02F) {
            value = target;
            velocity = 0.0F;
            moving = false;
        }
        return value;
    }

    boolean finished() {
        value();
        return !moving;
    }
}
