package com.mrfuzzihead.fuzzicontrols.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Unit tests for {@link LookStepper}.
 *
 * <p>
 * Pins the camera conventions: stick-up looks up (negative pitch, because Minecraft's
 * {@code rotationPitch} grows downwards), the rate is frame-rate independent, pitch is clamped to the
 * vanilla range, and vertical look honours the player's inverted-mouse preference.
 */
public class LookStepperTest {

    private static final float SENSITIVITY = 2f;

    /** Degrees per second at full deflection with the default sensitivity. */
    private static final float RATE = LookStepper.BASE_DEGREES_PER_SECOND * SENSITIVITY;

    private static double totalPitch(float stickY, boolean invertVertical, int fps) {
        double total = 0.0;
        float dt = 1f / fps;
        for (int frame = 0; frame < fps; frame++) {
            total += LookStepper.pitchDelta(stickY, SENSITIVITY, dt, invertVertical);
        }
        return total;
    }

    // -------------------------------------------------------------------------
    // Vertical convention + inverted mouse
    // -------------------------------------------------------------------------

    @Test
    public void stickUpLooksUpByDefault() {
        // rightStickY is -1 for full up; negative pitch delta means "look up".
        assertTrue("Stick up must look up", LookStepper.pitchDelta(-1f, SENSITIVITY, 0.05f, false) < 0f);
        assertEquals(-RATE * 0.05f, LookStepper.pitchDelta(-1f, SENSITIVITY, 0.05f, false), 1e-4f);
    }

    @Test
    public void stickUpLooksDownWhenVerticalLookIsInverted() {
        // Regression: the controller camera used to ignore GameSettings.invertMouse entirely.
        assertTrue(
            "Inverted mouse must flip the vertical direction",
            LookStepper.pitchDelta(-1f, SENSITIVITY, 0.05f, true) > 0f);
        assertEquals(RATE * 0.05f, LookStepper.pitchDelta(-1f, SENSITIVITY, 0.05f, true), 1e-4f);
    }

    @Test
    public void invertIsAnExactMirror() {
        for (float stick : new float[] { -1f, -0.5f, -0.01f, 0.01f, 0.5f, 1f }) {
            float normal = LookStepper.pitchDelta(stick, SENSITIVITY, 1f / 60f, false);
            float inverted = LookStepper.pitchDelta(stick, SENSITIVITY, 1f / 60f, true);
            assertEquals("Inversion must mirror the delta", -normal, inverted, 1e-6f);
        }
    }

    @Test
    public void inversionDoesNotAffectYaw() {
        assertEquals(LookStepper.yawDelta(1f, SENSITIVITY, 0.05f), LookStepper.yawDelta(1f, SENSITIVITY, 0.05f), 0f);
        assertTrue("Stick right must turn right", LookStepper.yawDelta(1f, SENSITIVITY, 0.05f) > 0f);
        assertTrue(LookStepper.yawDelta(-1f, SENSITIVITY, 0.05f) < 0f);
    }

    // -------------------------------------------------------------------------
    // Rate
    // -------------------------------------------------------------------------

    @Test
    public void rateIsFrameRateIndependent() {
        for (int fps : new int[] { 20, 30, 60, 144, 240 }) {
            // Stick up yields a negative (look-up) delta; the magnitude is what must be stable.
            assertEquals("Pitch per second at " + fps + " fps", RATE, Math.abs(totalPitch(-1f, false, fps)), 1e-2);
        }
    }

    @Test
    public void sensitivityScalesLinearly() {
        assertEquals(LookStepper.BASE_DEGREES_PER_SECOND, LookStepper.yawDelta(1f, 1f, 1f), 1e-4f);
        assertEquals(2 * LookStepper.BASE_DEGREES_PER_SECOND, LookStepper.yawDelta(1f, 2f, 1f), 1e-4f);
        assertEquals(10 * LookStepper.BASE_DEGREES_PER_SECOND, LookStepper.yawDelta(1f, 10f, 1f), 1e-4f);
    }

    @Test
    public void deflectionScalesLinearly() {
        float half = LookStepper.yawDelta(0.5f, SENSITIVITY, 0.1f);
        float full = LookStepper.yawDelta(1f, SENSITIVITY, 0.1f);
        assertEquals(full / 2f, half, 1e-5f);
    }

    @Test
    public void centredStickProducesNoRotation() {
        assertEquals(0f, LookStepper.yawDelta(0f, SENSITIVITY, 0.016f), 0f);
        assertEquals(0f, LookStepper.pitchDelta(0f, SENSITIVITY, 0.016f, false), 0f);
        assertEquals(0f, LookStepper.pitchDelta(0f, SENSITIVITY, 0.016f, true), 0f);
    }

    @Test
    public void nonPositiveDeltaSecondsProducesNoRotation() {
        assertEquals(0f, LookStepper.yawDelta(1f, SENSITIVITY, 0f), 0f);
        assertEquals(0f, LookStepper.pitchDelta(1f, SENSITIVITY, 0f, false), 0f);
        assertEquals(0f, LookStepper.yawDelta(1f, SENSITIVITY, -1f), 0f);
        assertEquals(0f, LookStepper.pitchDelta(1f, SENSITIVITY, -1f, true), 0f);
    }

    // -------------------------------------------------------------------------
    // Pitch clamp
    // -------------------------------------------------------------------------

    @Test
    public void pitchIsClampedToTheVanillaRange() {
        assertEquals(90f, LookStepper.clampPitch(120f), 0f);
        assertEquals(-90f, LookStepper.clampPitch(-120f), 0f);
        assertEquals(45f, LookStepper.clampPitch(45f), 0f);
        assertEquals(-45f, LookStepper.clampPitch(-45f), 0f);
        assertEquals(90f, LookStepper.clampPitch(90f), 0f);
        assertEquals(-90f, LookStepper.clampPitch(-90f), 0f);
    }

    @Test
    public void fullyHeldStickCannotFlipTheCameraOver() {
        // One second of full deflection is 360 degrees, so the clamp must hold.
        float pitch = 0f;
        for (int frame = 0; frame < 60; frame++) {
            pitch = LookStepper.clampPitch(pitch + LookStepper.pitchDelta(-1f, SENSITIVITY, 1f / 60f, false));
        }
        assertEquals(-90f, pitch, 0f);
    }
}
