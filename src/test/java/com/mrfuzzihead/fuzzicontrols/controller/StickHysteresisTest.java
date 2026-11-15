package com.mrfuzzihead.fuzzicontrols.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Collections;
import java.util.EnumSet;

import org.junit.Before;
import org.junit.Test;

/**
 * Unit tests for {@link StickHysteresis}.
 *
 * <p>
 * Pins the two-threshold behaviour (a stick resting near a single threshold makes movement stutter)
 * and the axis-to-direction mapping, where a sign error silently inverts movement.
 */
public class StickHysteresisTest {

    private StickHysteresis hysteresis;

    @Before
    public void setUp() {
        hysteresis = new StickHysteresis();
    }

    @Test
    public void directionTurnsOnOnlyPastTheOnThreshold() {
        assertFalse(hysteresis.update(ControllerAction.MOVE_FORWARD, 0.05f));
        assertFalse(hysteresis.update(ControllerAction.MOVE_FORWARD, StickHysteresis.ON_THRESHOLD));
        assertTrue(hysteresis.update(ControllerAction.MOVE_FORWARD, StickHysteresis.ON_THRESHOLD + 0.01f));
    }

    @Test
    public void activeDirectionStaysOnUntilAlmostReleased() {
        assertTrue(hysteresis.update(ControllerAction.MOVE_FORWARD, 0.5f));
        // Back into the hysteresis band: still on, even though it would not have turned on here.
        assertTrue(hysteresis.update(ControllerAction.MOVE_FORWARD, 0.05f));
        assertTrue(hysteresis.update(ControllerAction.MOVE_FORWARD, 0.02f));
        assertFalse(hysteresis.update(ControllerAction.MOVE_FORWARD, 0.005f));
    }

    @Test
    public void jitterInsideTheBandNeverToggles() {
        // Watched from OFF: values between the thresholds must not turn the direction on.
        for (int tick = 0; tick < 20; tick++) {
            float value = (tick % 2 == 0) ? 0.02f : 0.05f;
            assertFalse("Tick " + tick + " must stay off", hysteresis.update(ControllerAction.MOVE_FORWARD, value));
        }

        // Watched from ON: the same values must not turn it off.
        assertTrue(hysteresis.update(ControllerAction.MOVE_FORWARD, 0.5f));
        for (int tick = 0; tick < 20; tick++) {
            float value = (tick % 2 == 0) ? 0.02f : 0.05f;
            assertTrue("Tick " + tick + " must stay on", hysteresis.update(ControllerAction.MOVE_FORWARD, value));
        }
    }

    @Test
    public void digitalBindingFallsBackToTheNormalMapping() {
        ControllerMapping mapping = new ControllerMapping();
        mapping.bind(ControllerAction.MOVE_FORWARD, ControllerButton.A);
        ControllerState pressed = new ControllerState(0f, 0f, 0f, 0f, 0f, 0f, EnumSet.of(ControllerButton.A));

        assertTrue(
            "A digital MOVE_FORWARD binding must still move",
            hysteresis.isActive(ControllerAction.MOVE_FORWARD, mapping, pressed, 0.2f));
        assertFalse(
            "A released digital binding must not move",
            hysteresis.isActive(ControllerAction.MOVE_FORWARD, mapping, ControllerState.empty(), 0.2f));
    }

    @Test
    public void unboundActionIsInactive() {
        ControllerMapping mapping = new ControllerMapping();
        mapping.bind(ControllerAction.MOVE_FORWARD, null);
        assertFalse(hysteresis.isActive(ControllerAction.MOVE_FORWARD, mapping, ControllerState.empty(), 0.2f));
    }

    @Test
    public void axisBindingKeepsTheHysteresisBehaviour() {
        ControllerMapping mapping = new ControllerMapping(); // MOVE_FORWARD = LEFT_STICK_UP
        ControllerState half = new ControllerState(0f, -0.5f, 0f, 0f, 0f, 0f, Collections.emptySet());
        ControllerState nearCentre = new ControllerState(0f, -0.02f, 0f, 0f, 0f, 0f, Collections.emptySet());
        ControllerState centred = ControllerState.empty();

        assertTrue(hysteresis.isActive(ControllerAction.MOVE_FORWARD, mapping, half, 0.2f));
        assertTrue(
            "Inside the hysteresis band the direction stays on",
            hysteresis.isActive(ControllerAction.MOVE_FORWARD, mapping, nearCentre, 0.2f));
        assertFalse(hysteresis.isActive(ControllerAction.MOVE_FORWARD, mapping, centred, 0.2f));
    }

    @Test
    public void thresholdsAreOrdered() {
        assertTrue(
            "The release threshold must be lower than the press threshold",
            StickHysteresis.OFF_THRESHOLD < StickHysteresis.ON_THRESHOLD);
    }

    @Test
    public void stateIsTrackedPerAction() {
        assertTrue(hysteresis.update(ControllerAction.MOVE_FORWARD, 0.5f));
        assertFalse(
            "Another action must not inherit the state",
            hysteresis.update(ControllerAction.STRAFE_LEFT, 0.02f));
    }

    @Test
    public void clearResetsEveryDirection() {
        assertTrue(hysteresis.update(ControllerAction.MOVE_FORWARD, 0.5f));
        hysteresis.clear();
        // Still inside the band, but the direction no longer counts as active.
        assertFalse(hysteresis.update(ControllerAction.MOVE_FORWARD, 0.05f));
    }

    // -------------------------------------------------------------------------
    // Axis-to-direction mapping
    // -------------------------------------------------------------------------

    private static ControllerState sticks(float leftX, float leftY) {
        return new ControllerState(leftX, leftY, 0f, 0f, 0f, 0f, Collections.emptySet());
    }

    @Test
    public void forwardIsPositiveWhenTheStickIsPushedUp() {
        ControllerState up = sticks(0f, -0.8f);
        assertEquals(0.8f, StickHysteresis.axisValue(ControllerButton.LEFT_STICK_UP, up), 1e-6f);
        // The opposite direction yields a negative magnitude, which hysteresis treats as off.
        assertTrue(StickHysteresis.axisValue(ControllerButton.LEFT_STICK_DOWN, up) <= 0f);
    }

    @Test
    public void backwardIsPositiveWhenTheStickIsPushedDown() {
        ControllerState down = sticks(0f, 0.8f);
        assertEquals(0.8f, StickHysteresis.axisValue(ControllerButton.LEFT_STICK_DOWN, down), 1e-6f);
        assertTrue(StickHysteresis.axisValue(ControllerButton.LEFT_STICK_UP, down) <= 0f);
    }

    @Test
    public void leftAndRightArePositiveInTheirOwnDirection() {
        ControllerState left = sticks(-0.6f, 0f);
        ControllerState right = sticks(0.6f, 0f);
        assertEquals(0.6f, StickHysteresis.axisValue(ControllerButton.LEFT_STICK_LEFT, left), 1e-6f);
        assertEquals(0.6f, StickHysteresis.axisValue(ControllerButton.LEFT_STICK_RIGHT, right), 1e-6f);
        assertTrue(StickHysteresis.axisValue(ControllerButton.LEFT_STICK_RIGHT, left) <= 0f);
    }

    @Test
    public void rightStickUsesTheSameConventions() {
        ControllerState state = new ControllerState(0f, 0f, -0.7f, -0.9f, 0f, 0f, Collections.emptySet());
        assertEquals(0.7f, StickHysteresis.axisValue(ControllerButton.RIGHT_STICK_LEFT, state), 1e-6f);
        assertEquals(0.9f, StickHysteresis.axisValue(ControllerButton.RIGHT_STICK_UP, state), 1e-6f);
    }

    @Test
    public void nonDirectionalButtonsYieldZero() {
        ControllerState state = sticks(0.5f, 0.5f);
        assertEquals(0f, StickHysteresis.axisValue(ControllerButton.A, state), 0f);
        assertEquals(0f, StickHysteresis.axisValue(ControllerButton.RIGHT_TRIGGER, state), 0f);
    }
}
