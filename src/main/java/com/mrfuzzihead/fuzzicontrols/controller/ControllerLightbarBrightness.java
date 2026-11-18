package com.mrfuzzihead.fuzzicontrols.controller;

/** DualSense lightbar brightness presets, in the order the controller accepts. */
public enum ControllerLightbarBrightness {

    HIGH(0),
    MEDIUM(1),
    LOW(2);

    private final int value;

    ControllerLightbarBrightness(int value) {
        this.value = value;
    }

    /** Raw controller value for the lightbar brightness field. */
    public int getValue() {
        return value;
    }

    /** Returns the preset for a raw config value, defaulting to medium. */
    public static ControllerLightbarBrightness fromValue(int value) {
        return switch (value) {
            case 0 -> HIGH;
            case 2 -> LOW;
            default -> MEDIUM;
        };
    }
}
