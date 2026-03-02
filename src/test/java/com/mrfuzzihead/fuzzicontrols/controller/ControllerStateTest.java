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
    public void normaliseAxis_justOutsideDeadZoneNegative_returnsSmallNegative() {
        float result = ControllerState.normaliseAxis(-0.16f, 0.15f);
        assertTrue("Expected small negative value, got " + result, result < 0f);
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
    public void normaliseAxis_underRange_clampedToNegativeOne() {
        assertEquals(-1f, ControllerState.normaliseAxis(-1.5f, 0.15f), EPSILON);
    }

    @Test
    public void normaliseAxis_zeroDeadZone_rawPassthrough() {
        float raw = 0.5f;
        assertEquals(raw, ControllerState.normaliseAxis(raw, 0f), EPSILON);
    }

    @Test
    public void normaliseAxis_outputMonotonicallyIncreases() {
        // Verify the rescaled value grows as the raw input grows past the dead zone
        float low = ControllerState.normaliseAxis(0.3f, 0.15f);
        float high = ControllerState.normaliseAxis(0.6f, 0.15f);
        assertTrue("normaliseAxis output should increase as raw input increases", high > low);
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

    @Test
    public void normaliseTrigger_justAboveThreshold_returnsSmallPositive() {
        float result = ControllerState.normaliseTrigger(0.21f, 0.20f);
        assertTrue("Just above threshold should return a small positive value, got " + result, result > 0f);
    }

    // -------------------------------------------------------------------------
    // ControllerState construction and accessors
    // -------------------------------------------------------------------------

    @Test
    public void constructor_storesAllAxisValues() {
        ControllerState state = new ControllerState(
            0.1f,
            0.2f, // leftStickX, leftStickY
            0.3f,
            0.4f, // rightStickX, rightStickY
            0.5f,
            0.6f, // leftTrigger, rightTrigger
            EnumSet.noneOf(ControllerButton.class));
        assertEquals(0.1f, state.leftStickX(), EPSILON);
        assertEquals(0.2f, state.leftStickY(), EPSILON);
        assertEquals(0.3f, state.rightStickX(), EPSILON);
        assertEquals(0.4f, state.rightStickY(), EPSILON);
        assertEquals(0.5f, state.leftTrigger(), EPSILON);
        assertEquals(0.6f, state.rightTrigger(), EPSILON);
    }

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

    @Test
    public void isPressed_allButtonsFromEnumSet_correctlyTracked() {
        EnumSet<ControllerButton> pressed = EnumSet
            .of(ControllerButton.A, ControllerButton.X, ControllerButton.START, ControllerButton.RIGHT_BUMPER);
        ControllerState state = new ControllerState(0, 0, 0, 0, 0, 0, pressed);
        for (ControllerButton btn : ControllerButton.values()) {
            if (pressed.contains(btn)) {
                assertTrue("Expected " + btn + " to be pressed", state.isPressed(btn));
            } else {
                assertFalse("Expected " + btn + " to NOT be pressed", state.isPressed(btn));
            }
        }
    }

    // -------------------------------------------------------------------------
    // Analog movement — raw stick values for moveForward / moveStrafing scaling
    // -------------------------------------------------------------------------

    /**
     * These tests verify the sign conventions used by the analog movement code in
     * ControllerTickHandler. When analogMovement is enabled, the tick handler sets:
     * 
     * <pre>
     *   mc.thePlayer.moveForward  = -leftStickY()  (negative Y = push up = forward = positive moveForward)
     *   mc.thePlayer.moveStrafing = -leftStickX()  (negative X = push left = strafe left = positive moveStrafing)
     * </pre>
     */

    @Test
    public void analogMovement_stickPushedFullyForward_leftStickYIsNegativeOne() {
        // After dead-zone normalisation, pushing stick fully forward yields leftStickY = -1.
        float raw = ControllerState.normaliseAxis(-1f, 0.15f);
        assertEquals("Full forward push should normalise to -1.0", -1f, raw, EPSILON);
        // The tick handler negates this to get +1.0 for moveForward (full speed forward).
        assertEquals(1f, -raw, EPSILON);
    }

    @Test
    public void analogMovement_stickPushedHalfForward_leftStickYIsBetweenZeroAndNegativeOne() {
        float halfRaw = -0.5f;
        float normalised = ControllerState.normaliseAxis(halfRaw, 0.15f);
        assertTrue("Half-forward normalised value should be negative", normalised < 0f);
        assertTrue("Half-forward normalised value magnitude should be < 1", Math.abs(normalised) < 1f);
        // moveForward = -normalised would be between 0 and 1 (partial speed).
        float moveForward = -normalised;
        assertTrue("moveForward from half push should be between 0 and 1", moveForward > 0f && moveForward < 1f);
    }

    @Test
    public void analogMovement_stickPushedFullyLeft_leftStickXIsNegativeOne() {
        float raw = ControllerState.normaliseAxis(-1f, 0.15f);
        assertEquals(-1f, raw, EPSILON);
        // moveStrafing = -(-1) = +1 (strafe left at full speed)
        assertEquals(1f, -raw, EPSILON);
    }

    @Test
    public void analogMovement_stickPushedFullyRight_leftStickXIsPositiveOne() {
        float raw = ControllerState.normaliseAxis(1f, 0.15f);
        assertEquals(1f, raw, EPSILON);
        // moveStrafing = -(+1) = -1 (strafe right at full speed)
        assertEquals(-1f, -raw, EPSILON);
    }

    @Test
    public void analogMovement_withinDeadZone_producesZeroSpeed() {
        float raw = ControllerState.normaliseAxis(0.1f, 0.15f);
        assertEquals("Within dead-zone should give zero (no movement)", 0f, raw, EPSILON);
        assertEquals("moveForward from dead-zone should be zero", 0f, -raw, EPSILON);
    }
}
