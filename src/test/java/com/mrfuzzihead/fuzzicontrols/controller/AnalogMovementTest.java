package com.mrfuzzihead.fuzzicontrols.controller;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * Unit tests for {@link AnalogMovement}.
 *
 * <p>
 * The regression these tests exist for: with a controller connected but its left stick centred, the
 * analogue scaling used to overwrite the keyboard's {@code moveForward}/{@code moveStrafe} with
 * {@code 0.0}, so WASD did nothing whenever a gamepad was plugged in.
 */
public class AnalogMovementTest {

    /** Keyboard-only movement must survive an idle (centred) controller. */
    @Test
    public void centredStickLeavesKeyboardMovementUntouched() {
        assertEquals(1f, AnalogMovement.apply(1f, 0f, false), 0f);
        assertEquals(-1f, AnalogMovement.apply(-1f, 0f, false), 0f);
    }

    /** Centred stick while sneaking: vanilla already applied its 0.3 factor; don't touch it. */
    @Test
    public void centredStickLeavesSneakingKeyboardMovementUntouched() {
        assertEquals(0.3f, AnalogMovement.apply(0.3f, 0f, true), 1e-6f);
        assertEquals(-0.3f, AnalogMovement.apply(-0.3f, 0f, true), 1e-6f);
    }

    /** No input at all stays no input. */
    @Test
    public void idleAxisStaysIdle() {
        assertEquals(0f, AnalogMovement.apply(0f, 0.5f, false), 0f);
        assertEquals(0f, AnalogMovement.apply(0f, -0.9f, false), 0f);
        assertEquals(0f, AnalogMovement.apply(0f, 0f, false), 0f);
    }

    /** Full stick deflection behaves like holding the key. */
    @Test
    public void fullDeflectionIsFullSpeed() {
        assertEquals(1f, AnalogMovement.apply(1f, 1f, false), 1e-6f);
        assertEquals(-1f, AnalogMovement.apply(-1f, -1f, false), 1e-6f);
    }

    /** Quadratic response: half tilt is a quarter speed, so gentle tilt is precise. */
    @Test
    public void halfDeflectionIsQuarterSpeed() {
        assertEquals(0.25f, AnalogMovement.apply(1f, 0.5f, false), 1e-6f);
        assertEquals(-0.25f, AnalogMovement.apply(-1f, 0.5f, false), 1e-6f);
    }

    /** Opposite stick and key directions must not flip the sign of the key's direction. */
    @Test
    public void signFollowsTheAxisNotTheRawStickValue() {
        assertEquals(-0.25f, AnalogMovement.apply(-1f, 0.5f, false), 1e-6f);
        assertEquals(0.25f, AnalogMovement.apply(1f, -0.5f, false), 1e-6f);
    }

    /** Sneak factor is applied exactly once to a controller-driven axis, not twice (0.09). */
    @Test
    public void sneakFactorIsNotDoubleApplied() {
        assertEquals(AnalogMovement.SNEAK_FACTOR, AnalogMovement.apply(1f, 1f, true), 1e-6f);
        assertEquals(AnalogMovement.SNEAK_FACTOR * 0.25f, AnalogMovement.apply(1f, 0.5f, true), 1e-6f);
    }

    /** The controller never emits -0.0, which would confuse sign checks downstream. */
    @Test
    public void neverEmitsNegativeZero() {
        assertEquals(0f, AnalogMovement.apply(0f, -0.5f, false), 0f);
    }
}
