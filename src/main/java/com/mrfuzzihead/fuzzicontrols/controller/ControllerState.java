package com.mrfuzzihead.fuzzicontrols.controller;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Immutable snapshot of a controller's state after normalisation (dead-zone applied, axes clamped to [-1, 1]).
 */
public final class ControllerState {

    // ---- Axes (values in [-1.0, 1.0] after normalisation) ----

    /** Left stick horizontal: -1 = full left, +1 = full right. */
    public final float leftStickX;

    /** Left stick vertical: -1 = full up, +1 = full down (Minecraft Y convention). */
    public final float leftStickY;

    /** Right stick horizontal: -1 = full left, +1 = full right. */
    public final float rightStickX;

    /** Right stick vertical: -1 = full up, +1 = full down. */
    public final float rightStickY;

    /** Left trigger: 0.0 = released, 1.0 = fully pressed. */
    public final float leftTrigger;

    /** Right trigger: 0.0 = released, 1.0 = fully pressed. */
    public final float rightTrigger;

    // ---- Digital buttons ----

    /** Set of buttons considered pressed this tick. */
    private final Set<ControllerButton> pressedButtons;

    // ---- Constructor ----

    public ControllerState(float leftStickX, float leftStickY, float rightStickX, float rightStickY, float leftTrigger,
        float rightTrigger, Set<ControllerButton> pressedButtons) {
        this.leftStickX = leftStickX;
        this.leftStickY = leftStickY;
        this.rightStickX = rightStickX;
        this.rightStickY = rightStickY;
        this.leftTrigger = leftTrigger;
        this.rightTrigger = rightTrigger;
        this.pressedButtons = pressedButtons.isEmpty() ? Collections.emptySet()
            : Collections.unmodifiableSet(EnumSet.copyOf(pressedButtons));
    }

    /** Returns an empty / disconnected state. */
    public static ControllerState empty() {
        return new ControllerState(0f, 0f, 0f, 0f, 0f, 0f, Collections.emptySet());
    }

    // ---- Queries ----

    /** Returns true if the given button is pressed this tick. */
    public boolean isPressed(ControllerButton button) {
        return pressedButtons.contains(button);
    }

    /** Returns an unmodifiable view of all pressed buttons. */
    public Set<ControllerButton> getPressedButtons() {
        return pressedButtons;
    }

    /**
     * Normalises a raw axis value by applying a dead-zone and clamping to [-1, 1].
     *
     * @param raw      Raw axis value in [-1, 1].
     * @param deadZone Values with absolute magnitude below this are treated as 0.
     * @return Normalised, re-scaled value in [-1, 1].
     */
    public static float normaliseAxis(float raw, float deadZone) {
        if (Math.abs(raw) < deadZone) return 0f;
        // Re-scale so the output starts from 0 just past the dead zone
        float sign = raw > 0 ? 1f : -1f;
        float scaled = (Math.abs(raw) - deadZone) / (1f - deadZone);
        return sign * Math.min(1f, scaled);
    }

    /**
     * Normalises a raw trigger value by applying a threshold and clamping to [0, 1].
     *
     * @param raw       Raw trigger value in [0, 1].
     * @param threshold Values below this are treated as 0.
     * @return Normalised value in [0, 1].
     */
    public static float normaliseTrigger(float raw, float threshold) {
        if (raw < threshold) return 0f;
        float scaled = (raw - threshold) / (1f - threshold);
        return Math.min(1f, scaled);
    }

    @Override
    public String toString() {
        return "ControllerState{" + "LS=("
            + leftStickX
            + ","
            + leftStickY
            + ")"
            + " RS=("
            + rightStickX
            + ","
            + rightStickY
            + ")"
            + " LT="
            + leftTrigger
            + " RT="
            + rightTrigger
            + " buttons="
            + pressedButtons
            + '}';
    }
}
