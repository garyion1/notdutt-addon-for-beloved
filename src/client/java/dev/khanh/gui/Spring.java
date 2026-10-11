package dev.khanh.gui;

import dev.khanh.feature.KhanhFeatures;

/** Damped spring integrated in fixed sub-steps, so motion is identical at any frame
 * rate. With Animations off every spring settles instantly. */
public final class Spring {
    /** Panels, dropdowns and entrances: one visible overshoot. */
    public static final float[] PANEL = {280.0F, 17.5F, 0.9F};
    /** Hover and press scale. */
    public static final float[] BOUNCE = {420.0F, 14.0F, 1.0F};
    /** Toggle wobble. */
    public static final float[] WOBBLE = {340.0F, 10.0F, 0.7F};
    /** Critically damped: scrolling, hover glows and values that track the mouse. */
    public static final float[] SMOOTH = {170.0F, 26.0F, 1.0F};
    /** A soft glide with a hint of settle: sliding highlights and view changes. */
    public static final float[] GLIDE = {210.0F, 23.0F, 1.0F};

    private static final float STEP = 1.0F / 240.0F;
    private final float stiffness;
    private final float damping;
    private final float mass;
    private float value;
    private float velocity;
    private float target;
    private long lastNanos;

    public Spring(float[] config, float initial) {
        stiffness = config[0];
        damping = config[1];
        mass = config[2];
        value = initial;
        target = initial;
    }

    public Spring to(float newTarget) {
        target = newTarget;
        return this;
    }

    /** Displace the spring without moving its rest point, e.g. a ±6° wobble. */
    public void kick(float displacedValue) {
        update();
        value = displacedValue;
    }

    public void snap(float newValue) {
        value = newValue;
        target = newValue;
        velocity = 0.0F;
        lastNanos = System.nanoTime();
    }

    public float target() {
        return target;
    }

    public float value() {
        update();
        return value;
    }

    public boolean settled() {
        update();
        return velocity == 0.0F && value == target;
    }

    private void update() {
        long now = System.nanoTime();
        if (lastNanos == 0L) {
            lastNanos = now;
            return;
        }
        float elapsed = Math.min(0.064F, (now - lastNanos) / 1_000_000_000.0F);
        lastNanos = now;
        if (!KhanhFeatures.enabled("animations")) {
            value = target;
            velocity = 0.0F;
            return;
        }
        if (elapsed <= 0.0F || (velocity == 0.0F && value == target)) {
            return;
        }
        int steps = Math.max(1, (int) Math.ceil(elapsed / STEP));
        float dt = elapsed / steps;
        for (int i = 0; i < steps; i++) {
            float force = -stiffness * (value - target) - damping * velocity;
            velocity += force / mass * dt;
            value += velocity * dt;
        }
        if (Math.abs(value - target) < 0.0005F && Math.abs(velocity) < 0.005F) {
            value = target;
            velocity = 0.0F;
        }
    }
}
