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

    /**
     * Sanity check: every {@link ControllerButton} value must return either true or false from
     * {@link ControllerButton#isAxis()} without throwing — ensuring the switch in
     * {@link ControllerMapping#isActive} covers all enum constants.
     */
    @Test
    public void isAxis_allButtons_noException() {
        for (ControllerButton btn : ControllerButton.values()) {
            try {
                btn.isAxis(); // must not throw
            } catch (Exception e) {
                fail("isAxis() threw for " + btn + ": " + e);
            }
        }
    }

    /**
     * Every axis button (stick directions and triggers) should be classified as axis, and every
     * non-axis button should not be. Together with the individual tests above this ensures the
     * full classification is self-consistent.
     */
    @Test
    public void isAxis_axisButtonCount_matchesExpected() {
        // 4 left-stick directions + 4 right-stick directions + 2 triggers = 10 axis buttons
        int axisCount = 0;
        for (ControllerButton btn : ControllerButton.values()) {
            if (btn.isAxis()) axisCount++;
        }
        assertEquals("Expected exactly 10 axis buttons (4+4 stick directions + 2 triggers)", 10, axisCount);
    }
}
