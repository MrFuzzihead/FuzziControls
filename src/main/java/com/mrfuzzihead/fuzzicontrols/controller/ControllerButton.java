package com.mrfuzzihead.fuzzicontrols.controller;

/**
 * Represents every discrete button or axis-as-button that can be bound to a Minecraft action.
 * Axis names follow XInput conventions; DualSense equivalents are noted in comments.
 */
public enum ControllerButton {

    // Face buttons
    /** A (Xbox) / Cross (PS) */
    A,
    /** B (Xbox) / Circle (PS) */
    B,
    /** X (Xbox) / Square (PS) */
    X,
    /** Y (Xbox) / Triangle (PS) */
    Y,

    // Shoulder / trigger buttons
    /** Left Bumper (LB / L1) */
    LEFT_BUMPER,
    /** Right Bumper (RB / R1) */
    RIGHT_BUMPER,
    /** Left Trigger pressed as digital button (LT / L2) */
    LEFT_TRIGGER,
    /** Right Trigger pressed as digital button (RT / R2) */
    RIGHT_TRIGGER,

    // Stick clicks
    /** Left Stick click (LS / L3) */
    LEFT_STICK_CLICK,
    /** Right Stick click (RS / R3) */
    RIGHT_STICK_CLICK,

    // D-Pad
    DPAD_UP,
    DPAD_DOWN,
    DPAD_LEFT,
    DPAD_RIGHT,

    // Menu / system buttons
    /** Start / Options */
    START,
    /** Back / Share / Select */
    BACK,

    // DualSense Edge extras (raw HID only; unbound by default)
    /** Rear left paddle on the DualSense Edge. */
    LEFT_PADDLE,
    /** Rear right paddle on the DualSense Edge. */
    RIGHT_PADDLE,
    /** Front function button Fn1 on the DualSense Edge. */
    FUNCTION_1,
    /** Front function button Fn2 on the DualSense Edge. */
    FUNCTION_2,

    // Axes treated as directional inputs (for action mappings that need a direction)
    LEFT_STICK_UP,
    LEFT_STICK_DOWN,
    LEFT_STICK_LEFT,
    LEFT_STICK_RIGHT,
    RIGHT_STICK_UP,
    RIGHT_STICK_DOWN,
    RIGHT_STICK_LEFT,
    RIGHT_STICK_RIGHT;

    /** Returns true if this button represents a half-axis (directional stick or trigger). */
    public boolean isAxis() {
        return switch (this) {
            case LEFT_TRIGGER, RIGHT_TRIGGER, LEFT_STICK_UP, LEFT_STICK_DOWN, LEFT_STICK_LEFT, LEFT_STICK_RIGHT, RIGHT_STICK_UP, RIGHT_STICK_DOWN, RIGHT_STICK_LEFT, RIGHT_STICK_RIGHT -> true;
            default -> false;
        };
    }
}
