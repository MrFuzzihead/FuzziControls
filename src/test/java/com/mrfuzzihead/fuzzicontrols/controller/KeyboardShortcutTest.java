package com.mrfuzzihead.fuzzicontrols.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Unit tests for the controller-button to keyboard-key shortcut value object. */
public class KeyboardShortcutTest {

    @Test
    public void parsesHoldAndTapShortcuts() {
        KeyboardShortcut hold = KeyboardShortcut.parse("A:33:HOLD");
        assertTrue(hold.isBound());
        assertEquals(ControllerButton.A, hold.getButton());
        assertEquals(33, hold.getKeyCode());
        assertEquals(KeyboardShortcut.Mode.HOLD, hold.getMode());

        KeyboardShortcut tap = KeyboardShortcut.parse("RIGHT_STICK_CLICK:42:TAP");
        assertTrue(tap.isBound());
        assertEquals(KeyboardShortcut.Mode.TAP, tap.getMode());
    }

    @Test
    public void missingModeDefaultsToHold() {
        KeyboardShortcut shortcut = KeyboardShortcut.parse("B:30");
        assertTrue(shortcut.isBound());
        assertEquals(KeyboardShortcut.Mode.HOLD, shortcut.getMode());
    }

    @Test
    public void noneAndInvalidValuesAreUnbound() {
        assertFalse(
            KeyboardShortcut.parse(null)
                .isBound());
        assertFalse(
            KeyboardShortcut.parse("")
                .isBound());
        assertFalse(
            KeyboardShortcut.parse("NONE")
                .isBound());
        assertFalse(
            KeyboardShortcut.parse("NOT_A_BUTTON:30:HOLD")
                .isBound());
        assertFalse(
            KeyboardShortcut.parse("A:not-a-number:HOLD")
                .isBound());
        assertFalse(
            KeyboardShortcut.parse("A:0:HOLD")
                .isBound());
        assertFalse(
            KeyboardShortcut.parse("A:-1:HOLD")
                .isBound());
    }

    @Test
    public void serialiseRoundTrips() {
        KeyboardShortcut shortcut = new KeyboardShortcut(ControllerButton.X, 44, KeyboardShortcut.Mode.TAP);
        assertEquals("X:44:TAP", shortcut.serialise());
        KeyboardShortcut parsed = KeyboardShortcut.parse(shortcut.serialise());
        assertEquals(shortcut.getButton(), parsed.getButton());
        assertEquals(shortcut.getKeyCode(), parsed.getKeyCode());
        assertEquals(shortcut.getMode(), parsed.getMode());
    }

    @Test
    public void clearMakesTheShortcutUnbound() {
        KeyboardShortcut shortcut = new KeyboardShortcut(ControllerButton.Y, 19, KeyboardShortcut.Mode.HOLD);
        shortcut.clear();
        assertFalse(shortcut.isBound());
        assertEquals("NONE", shortcut.serialise());
    }
}
