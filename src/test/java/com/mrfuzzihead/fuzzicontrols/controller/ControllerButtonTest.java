package com.mrfuzzihead.fuzzicontrols.controller;

import static org.junit.Assert.*;

import org.junit.Test;

/**
 * Unit tests for {@link ControllerButton} helper methods.
 */
public class ControllerButtonTest {

    @Test
    public void isAxis_triggers_returnsTrue() {
        assertTrue(ControllerButton.LEFT_TRIGGER.isAxis());
        assertTrue(ControllerButton.RIGHT_TRIGGER.isAxis());
    }

    @Test
    public void isAxis_stickDirections_returnsTrue() {
        assertTrue(ControllerButton.LEFT_STICK_UP.isAxis());
        assertTrue(ControllerButton.LEFT_STICK_DOWN.isAxis());
        assertTrue(ControllerButton.LEFT_STICK_LEFT.isAxis());
        assertTrue(ControllerButton.LEFT_STICK_RIGHT.isAxis());
        assertTrue(ControllerButton.RIGHT_STICK_UP.isAxis());
        assertTrue(ControllerButton.RIGHT_STICK_DOWN.isAxis());
        assertTrue(ControllerButton.RIGHT_STICK_LEFT.isAxis());
        assertTrue(ControllerButton.RIGHT_STICK_RIGHT.isAxis());
    }

    @Test
    public void isAxis_faceButtons_returnsFalse() {
        assertFalse(ControllerButton.A.isAxis());
        assertFalse(ControllerButton.B.isAxis());
        assertFalse(ControllerButton.X.isAxis());
        assertFalse(ControllerButton.Y.isAxis());
    }

    @Test
    public void isAxis_shoulderButtons_returnsFalse() {
        assertFalse(ControllerButton.LEFT_BUMPER.isAxis());
        assertFalse(ControllerButton.RIGHT_BUMPER.isAxis());
    }

    @Test
    public void isAxis_stickClicks_returnsFalse() {
        assertFalse(ControllerButton.LEFT_STICK_CLICK.isAxis());
        assertFalse(ControllerButton.RIGHT_STICK_CLICK.isAxis());
    }

    @Test
    public void isAxis_dpad_returnsFalse() {
        assertFalse(ControllerButton.DPAD_UP.isAxis());
        assertFalse(ControllerButton.DPAD_DOWN.isAxis());
        assertFalse(ControllerButton.DPAD_LEFT.isAxis());
        assertFalse(ControllerButton.DPAD_RIGHT.isAxis());
    }

    @Test
    public void isAxis_menuButtons_returnsFalse() {
        assertFalse(ControllerButton.START.isAxis());
        assertFalse(ControllerButton.BACK.isAxis());
        assertFalse(ControllerButton.GUIDE.isAxis());
    }
}
