package com.mrfuzzihead.fuzzicontrols.controller;

import java.util.Locale;

/**
 * A controller-button to keyboard-key shortcut.
 *
 * <p>
 * This is deliberately separate from {@link ControllerAction}: the action enum describes
 * Minecraft semantics that the mod itself handles, while a shortcut simply forwards a controller
 * button to an arbitrary keyboard key. That lets players drive modded {@code KeyBinding}s without
 * this mod having to know anything about the mod that registered them.
 *
 * <p>
 * Shortcuts only work for keys that are registered through Minecraft's {@code KeyBinding} system
 * or that can be driven by {@code KeyBinding.setKeyBindState}. Mods that poll LWJGL's raw keyboard
 * state directly are outside this mechanism.
 */
public final class KeyboardShortcut {

    /** How the shortcut drives the keyboard key. */
    public enum Mode {
        /** Key is held for as long as the controller button is held. */
        HOLD,
        /** Key receives one press edge per controller-button press. */
        TAP
    }

    private ControllerButton button;
    private int keyCode;
    private Mode mode;

    /** Creates an unbound shortcut. */
    public KeyboardShortcut() {
        this(null, 0, Mode.HOLD);
    }

    public KeyboardShortcut(ControllerButton button, int keyCode, Mode mode) {
        this.button = button;
        this.keyCode = Math.max(0, keyCode);
        this.mode = mode != null ? mode : Mode.HOLD;
    }

    /** Returns the controller button, or {@code null} when unbound. */
    public ControllerButton getButton() {
        return button;
    }

    /** Returns the LWJGL key code, or {@code 0} when unbound. */
    public int getKeyCode() {
        return keyCode;
    }

    public Mode getMode() {
        return mode;
    }

    /** Returns true when both a controller button and a keyboard key are assigned. */
    public boolean isBound() {
        return button != null && keyCode > 0;
    }

    public void setButton(ControllerButton button) {
        this.button = button;
    }

    public void setKeyCode(int keyCode) {
        this.keyCode = Math.max(0, keyCode);
    }

    public void setMode(Mode mode) {
        this.mode = mode != null ? mode : Mode.HOLD;
    }

    public void clear() {
        button = null;
        keyCode = 0;
        mode = Mode.HOLD;
    }

    /** Serialises to the config format {@code BUTTON:KEYCODE:MODE}. */
    public String serialise() {
        if (!isBound()) return "NONE";
        return button.name() + ":" + keyCode + ":" + mode.name();
    }

    /**
     * Parses {@code BUTTON:KEYCODE:MODE}. Invalid or empty values produce an unbound shortcut
     * rather than throwing, so a hand-edited config cannot stop the game from loading.
     */
    public static KeyboardShortcut parse(String value) {
        KeyboardShortcut shortcut = new KeyboardShortcut();
        if (value == null) return shortcut;
        String trimmed = value.trim();
        if (trimmed.isEmpty() || "NONE".equalsIgnoreCase(trimmed)) return shortcut;

        String[] parts = trimmed.split(":", -1);
        if (parts.length < 2) return shortcut;
        try {
            ControllerButton button = ControllerButton.valueOf(
                parts[0].trim()
                    .toUpperCase(Locale.ROOT));
            int keyCode = Integer.parseInt(parts[1].trim());
            Mode mode = parts.length >= 3 ? Mode.valueOf(
                parts[2].trim()
                    .toUpperCase(Locale.ROOT))
                : Mode.HOLD;
            if (keyCode <= 0) return shortcut;
            return new KeyboardShortcut(button, keyCode, mode);
        } catch (IllegalArgumentException e) {
            return shortcut;
        }
    }

    @Override
    public String toString() {
        return serialise();
    }
}
