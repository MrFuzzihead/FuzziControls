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
 *
 * <p>
 * There are two separate halves to the analogue path: {@link AnalogMovement#apply} provides the
 * <em>direction</em> (vanilla normalises the vector anyway), and
 * {@link AnalogMovement#speedScale} provides the <em>magnitude</em> that the {@code moveFlying}
 * mixin multiplies into the movement friction.
 */
public class AnalogMovementTest {

    /** Keyboard-only movement must survive an idle (centred) controller. */
    @Test
    public void centredStickLeavesKeyboardMovementUntouched() {
        assertEquals(1f, AnalogMovement.apply(1f, 0f), 0f);
        assertEquals(-1f, AnalogMovement.apply(-1f, 0f), 0f);
    }

    /** Centred stick while sneaking: vanilla already applied its 0.3 factor; don't touch it. */
    @Test
    public void centredStickLeavesSneakingKeyboardMovementUntouched() {
        assertEquals(0.3f, AnalogMovement.apply(0.3f, 0f), 1e-6f);
        assertEquals(-0.3f, AnalogMovement.apply(-0.3f, 0f), 1e-6f);
    }

    /** No input at all stays no input. */
    @Test
    public void idleAxisStaysIdle() {
        assertEquals(0f, AnalogMovement.apply(0f, 0.5f), 0f);
        assertEquals(0f, AnalogMovement.apply(0f, -0.9f), 0f);
        assertEquals(0f, AnalogMovement.apply(0f, 0f), 0f);
    }

    /** Full stick deflection behaves like holding the key. */
    @Test
    public void fullDeflectionIsFullSpeed() {
        assertEquals(1f, AnalogMovement.apply(1f, 1f), 1e-6f);
        assertEquals(-1f, AnalogMovement.apply(-1f, -1f), 1e-6f);
        assertEquals(1f, AnalogMovement.speedScale(0f, 1f), 1e-6f);
        assertEquals(1f, AnalogMovement.speedScale(0f, -1f), 1e-6f);
    }

    /** Direction stays linear in the stick axis; speed scaling is applied separately. */
    @Test
    public void halfDeflectionKeepsTheStickDirection() {
        assertEquals(0.5f, AnalogMovement.apply(1f, 0.5f), 1e-6f);
        assertEquals(-0.5f, AnalogMovement.apply(-1f, 0.5f), 1e-6f);
        assertEquals(0.25f, AnalogMovement.speedScale(0f, 0.5f), 1e-6f);
    }

    /** Opposite stick and key directions must not flip the sign of the key's direction. */
    @Test
    public void signFollowsTheAxisNotTheRawStickValue() {
        assertEquals(-0.5f, AnalogMovement.apply(-1f, 0.5f), 1e-6f);
        assertEquals(0.5f, AnalogMovement.apply(1f, -0.5f), 1e-6f);
    }

    /** Sneak factor is applied once, to the radial speed scaling. */
    @Test
    public void sneakFactorIsNotDoubleApplied() {
        assertEquals(AnalogMovement.SNEAK_FACTOR, AnalogMovement.speedScale(0f, 1f, true), 1e-6f);
        assertEquals(AnalogMovement.SNEAK_FACTOR * 0.25f, AnalogMovement.speedScale(0f, 0.5f, true), 1e-6f);
    }

    /** The controller never emits -0.0, which would confuse sign checks downstream. */
    @Test
    public void neverEmitsNegativeZero() {
        assertEquals(0f, AnalogMovement.apply(0f, -0.5f), 0f);
    }

    // -------------------------------------------------------------------------
    // Radial speed scale used to preserve magnitude through Entity.moveFlying()
    // -------------------------------------------------------------------------

    @Test
    public void speedScaleUsesTheRadialMagnitude() {
        assertEquals(0f, AnalogMovement.speedScale(0f, 0f), 0f);
        assertEquals(0.25f, AnalogMovement.speedScale(0.5f, 0f), 1e-6f);
        assertEquals(0.25f, AnalogMovement.speedScale(0f, -0.5f), 1e-6f);
        assertEquals(1f, AnalogMovement.speedScale(1f, 0f), 1e-6f);
        assertEquals("Diagonal half deflection is 0.5 speed", 0.5f, AnalogMovement.speedScale(0.5f, 0.5f), 1e-6f);
    }

    @Test
    public void speedScaleClampsAnOverdrivenDiagonal() {
        assertEquals(1f, AnalogMovement.speedScale(1f, 1f), 0f);
        assertEquals(1f, AnalogMovement.speedScale(-2f, 0f), 0f);
    }

    @Test
    public void nonFiniteInputIsIgnored() {
        assertEquals(0f, AnalogMovement.speedScale(Float.NaN, 0f), 0f);

        AnalogMovement.setAnalogSpeedScale(Float.NaN);
        assertEquals(-1f, AnalogMovement.getAnalogSpeedScale(), 0f);
    }

    @Test
    public void frictionScalingLeavesVanillaAloneWhenInactive() {
        assertEquals(0.1f, AnalogMovement.scaleFriction(0.1f, -1f), 0f);
        assertEquals(0.1f, AnalogMovement.scaleFriction(0.1f, 1f), 0f);
        assertEquals(0.1f, AnalogMovement.scaleFriction(0.1f, Float.NaN), 0f);
        assertEquals(0.05f, AnalogMovement.scaleFriction(0.1f, 0.5f), 1e-6f);
    }

    @Test
    public void activeSpeedScaleCanBeSetAndCleared() {
        AnalogMovement.setAnalogSpeedScale(0.5f);
        assertEquals(0.5f, AnalogMovement.getAnalogSpeedScale(), 1e-6f);

        AnalogMovement.setAnalogSpeedScale(-1f);
        assertEquals(-1f, AnalogMovement.getAnalogSpeedScale(), 0f);

        AnalogMovement.setAnalogSpeedScale(2f);
        assertEquals(1f, AnalogMovement.getAnalogSpeedScale(), 0f);

        AnalogMovement.clearAnalogSpeedScale();
        assertEquals(-1f, AnalogMovement.getAnalogSpeedScale(), 0f);
    }
}
