package com.mrfuzzihead.fuzzicontrols.controller;

import static org.junit.Assert.*;

import java.util.EnumSet;

import org.junit.Test;

/**
 * Unit tests for {@link ControllerState} normalisation helpers and state queries.
 */
public class ControllerStateTest {

    private static final float EPSILON = 1e-5f;

    // -------------------------------------------------------------------------
    // normaliseAxis
    // -------------------------------------------------------------------------

    @Test
    public void normaliseAxis_zeroInput_returnsZero() {
        assertEquals(0f, ControllerState.normaliseAxis(0f, 0.15f), EPSILON);
    }

    @Test
    public void normaliseAxis_withinDeadZone_returnsZero() {
        assertEquals(0f, ControllerState.normaliseAxis(0.10f, 0.15f), EPSILON);
        assertEquals(0f, ControllerState.normaliseAxis(-0.10f, 0.15f), EPSILON);
    }

    @Test
    public void normaliseAxis_atDeadZoneBoundary_returnsZero() {
        // Exactly at dead-zone edge is still inside
        assertEquals(0f, ControllerState.normaliseAxis(0.15f, 0.15f), EPSILON);
    }

    @Test
    public void normaliseAxis_justOutsideDeadZone_returnsSmallPositive() {
        float result = ControllerState.normaliseAxis(0.16f, 0.15f);
        assertTrue("Expected small positive value, got " + result, result > 0f);
    }

    @Test
    public void normaliseAxis_fullPositive_returnsOne() {
        assertEquals(1f, ControllerState.normaliseAxis(1f, 0.15f), EPSILON);
    }

    @Test
    public void normaliseAxis_fullNegative_returnsNegativeOne() {
        assertEquals(-1f, ControllerState.normaliseAxis(-1f, 0.15f), EPSILON);
    }

    @Test
    public void normaliseAxis_overRange_clampedToOne() {
        // Raw values > 1 (hardware noise) are clamped
        assertEquals(1f, ControllerState.normaliseAxis(1.5f, 0.15f), EPSILON);
    }

    @Test
    public void normaliseAxis_zeroDeadZone_rawPassthrough() {
        float raw = 0.5f;
        assertEquals(raw, ControllerState.normaliseAxis(raw, 0f), EPSILON);
    }

    // -------------------------------------------------------------------------
    // normaliseTrigger
    // -------------------------------------------------------------------------

    @Test
    public void normaliseTrigger_belowThreshold_returnsZero() {
        assertEquals(0f, ControllerState.normaliseTrigger(0.10f, 0.20f), EPSILON);
    }

    @Test
    public void normaliseTrigger_atThreshold_returnsZero() {
        assertEquals(0f, ControllerState.normaliseTrigger(0.20f, 0.20f), EPSILON);
    }

    @Test
    public void normaliseTrigger_fullPress_returnsOne() {
        assertEquals(1f, ControllerState.normaliseTrigger(1f, 0.20f), EPSILON);
    }

    @Test
    public void normaliseTrigger_overRange_clampedToOne() {
        assertEquals(1f, ControllerState.normaliseTrigger(1.5f, 0.20f), EPSILON);
    }

    @Test
    public void normaliseTrigger_zeroThreshold_rawPassthrough() {
        float raw = 0.5f;
        assertEquals(raw, ControllerState.normaliseTrigger(raw, 0f), EPSILON);
    }

    // -------------------------------------------------------------------------
    // ControllerState construction and isPressed
    // -------------------------------------------------------------------------

    @Test
    public void isPressed_buttonInSet_returnsTrue() {
        ControllerState state = new ControllerState(
            0,
            0,
            0,
            0,
            0,
            0,
            EnumSet.of(ControllerButton.A, ControllerButton.B));
        assertTrue(state.isPressed(ControllerButton.A));
        assertTrue(state.isPressed(ControllerButton.B));
    }

    @Test
    public void isPressed_buttonNotInSet_returnsFalse() {
        ControllerState state = new ControllerState(0, 0, 0, 0, 0, 0, EnumSet.of(ControllerButton.A));
        assertFalse(state.isPressed(ControllerButton.X));
    }

    @Test
    public void empty_hasNoButtonsPressed() {
        ControllerState state = ControllerState.empty();
        for (ControllerButton btn : ControllerButton.values()) {
            assertFalse("Expected no buttons pressed in empty state, but " + btn + " was", state.isPressed(btn));
        }
    }

    @Test
    public void empty_allAxesZero() {
        ControllerState state = ControllerState.empty();
        assertEquals(0f, state.leftStickX(), EPSILON);
        assertEquals(0f, state.leftStickY(), EPSILON);
        assertEquals(0f, state.rightStickX(), EPSILON);
        assertEquals(0f, state.rightStickY(), EPSILON);
        assertEquals(0f, state.leftTrigger(), EPSILON);
        assertEquals(0f, state.rightTrigger(), EPSILON);
    }

    @Test
    public void getPressedButtons_isUnmodifiable() {
        ControllerState state = new ControllerState(0, 0, 0, 0, 0, 0, EnumSet.of(ControllerButton.Y));
        try {
            state.pressedButtons()
                .add(ControllerButton.A);
            fail("Expected UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) {
            // pass
        }
    }
}
