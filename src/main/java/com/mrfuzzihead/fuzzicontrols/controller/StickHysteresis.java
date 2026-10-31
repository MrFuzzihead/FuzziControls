package com.mrfuzzihead.fuzzicontrols.controller;

import java.util.EnumMap;

/**
 * Two-threshold (Schmitt trigger) evaluation of stick-direction actions.
 *
 * <p>
 * A single threshold makes a stick resting near it oscillate on/off many times a second, which looks
 * like stuttering movement. Two thresholds fix that: a direction only turns on once pushed distinctly
 * past the dead zone, and only turns off once released almost fully.
 *
 * <p>
 * Extracted from {@link ControllerTickHandler} so the thresholds and the axis-to-direction mapping
 * (where a sign error silently inverts movement) can be unit tested.
 */
final class StickHysteresis {

    /** Normalised axis magnitude needed to turn a direction on. */
    static final float ON_THRESHOLD = 0.065f;

    /** Magnitude below which an active direction turns off. Must be lower than {@link #ON_THRESHOLD}. */
    static final float OFF_THRESHOLD = 0.015f;

    private final EnumMap<ControllerAction, Boolean> active = new EnumMap<>(ControllerAction.class);

    /**
     * Updates the state of one stick direction.
     *
     * @param action the stick-direction action being evaluated
     * @param value  deflection magnitude, positive in the action's direction
     * @return true when the direction counts as active this tick
     */
    boolean update(ControllerAction action, float value) {
        boolean wasActive = Boolean.TRUE.equals(active.get(action));
        // Hysteresis: releasing needs a smaller threshold than pressing.
        boolean nowActive = wasActive ? value > OFF_THRESHOLD : value > ON_THRESHOLD;
        active.put(action, nowActive);
        return nowActive;
    }

    /** Forgets all state, e.g. when input processing resumes after being paused. */
    void clear() {
        active.clear();
    }

    /**
     * Returns the deflection magnitude for a stick-direction button, positive in that direction.
     *
     * <p>
     * {@code ControllerState} reports negative Y for up and negative X for left; movement actions need
     * a positive magnitude for their own direction.
     *
     * @param button stick-direction button (any other button yields 0)
     * @param state  current controller state
     * @return positive deflection magnitude, or 0 for non-directional buttons
     */
    static float axisValue(ControllerButton button, ControllerState state) {
        return switch (button) {
            case LEFT_STICK_UP -> -state.leftStickY(); // positive = forward
            case LEFT_STICK_DOWN -> state.leftStickY();
            case LEFT_STICK_LEFT -> -state.leftStickX(); // positive = left
            case LEFT_STICK_RIGHT -> state.leftStickX();
            case RIGHT_STICK_UP -> -state.rightStickY();
            case RIGHT_STICK_DOWN -> state.rightStickY();
            case RIGHT_STICK_LEFT -> -state.rightStickX();
            case RIGHT_STICK_RIGHT -> state.rightStickX();
            default -> 0f;
        };
    }
}
