package com.mrfuzzihead.fuzzicontrols.controller;

import java.util.Locale;

/**
 * Adaptive-trigger effect presets exposed to gameplay code and the settings screen.
 *
 * <p>
 * The concrete parameter packing is driver-specific. A driver that does not support adaptive
 * triggers can ignore the request.
 */
public enum ControllerTriggerEffect {

    OFF,
    FEEDBACK,
    WEAPON,
    VIBRATION,
    BOW,
    MACHINE;

    /** Parses a config/UI value, falling back when the name is unknown or empty. */
    public static ControllerTriggerEffect fromName(String name, ControllerTriggerEffect fallback) {
        if (name == null || name.trim()
            .isEmpty()) return fallback;
        try {
            return valueOf(
                name.trim()
                    .toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }
}
