package com.mrfuzzihead.fuzzicontrols.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Unit tests for {@link ScrollStepper}.
 *
 * <p>
 * Regression coverage for the right-stick scroll bug where the quadratic response was applied
 * <em>after</em> scaling by the frame delta, making the delivered rate proportional to
 * {@code sensitivity² / fps}. At the default sensitivity of 10 that produced ~1.67 notches/s at
 * 60 fps (one notch every 0.6 s — visibly janky) and only ~0.69 notches/s at 144 fps.
 */
public class ScrollStepperTest {

    /** The default {@code scrollSensitivity} from Config. */
    private static final float SENSITIVITY = 10f;

    /** Total wheel delta delivered over one second at the given frame rate. */
    private static double notchesPerSecond(float stickY, float sensitivity, int fps) {
        double total = 0.0;
        float dt = 1f / fps;
        for (int frame = 0; frame < fps; frame++) {
            total += ScrollStepper.wheelDelta(stickY, sensitivity, dt);
        }
        return total;
    }

    // -------------------------------------------------------------------------
    // Regression: the rate must not depend on the frame rate
    // -------------------------------------------------------------------------

    @Test
    public void rateIsFrameRateIndependent() {
        double expected = SENSITIVITY;
        for (int fps : new int[] { 20, 30, 60, 75, 144, 240 }) {
            assertEquals(
                "Scroll rate at " + fps + " fps must equal the configured notches per second",
                expected,
                notchesPerSecond(-1f, SENSITIVITY, fps),
                1e-3);
        }
    }

    @Test
    public void fullDeflectionDeliversConfiguredNotchesPerSecond() {
        assertEquals(10.0, notchesPerSecond(-1f, SENSITIVITY, 60), 1e-3);
        assertEquals(25.0, notchesPerSecond(-1f, 25f, 60), 1e-3);
        assertEquals(1.0, notchesPerSecond(-1f, 1f, 60), 1e-3);
    }

    @Test
    public void oldBehaviourWouldHaveBeenFrameRateDependent() {
        // Documents the bug: squaring the time-scaled per-frame delta gives sensitivity²/fps.
        // Kept as an explicit assertion so a regression to that formula is obvious.
        for (int fps : new int[] { 30, 60, 144 }) {
            float dt = 1f / fps;
            float oldPerFrame = SENSITIVITY * dt;
            oldPerFrame = Math.signum(oldPerFrame) * oldPerFrame * oldPerFrame;
            double oldRate = Math.abs(oldPerFrame * fps);
            assertTrue("The old formula really was frame-rate dependent", Math.abs(oldRate - SENSITIVITY) > 1.0);
        }
    }

    // -------------------------------------------------------------------------
    // Response curve
    // -------------------------------------------------------------------------

    @Test
    public void halfDeflectionGivesAQuarterOfTheRate() {
        assertEquals(0.25 * SENSITIVITY, Math.abs(notchesPerSecond(-0.5f, SENSITIVITY, 60)), 1e-3);
    }

    @Test
    public void smallDeflectionIsPrecise() {
        // 10 % deflection -> 1 % of full speed (0.1 notches/s), i.e. one notch every 10 s.
        assertEquals(0.1, Math.abs(notchesPerSecond(-0.1f, SENSITIVITY, 60)), 1e-3);
    }

    @Test
    public void sensitivityScalesLinearly() {
        double atTen = Math.abs(notchesPerSecond(-0.5f, 10f, 60));
        double atTwenty = Math.abs(notchesPerSecond(-0.5f, 20f, 60));
        assertEquals(atTen * 2.0, atTwenty, 1e-3);
    }

    // -------------------------------------------------------------------------
    // Direction and edge cases
    // -------------------------------------------------------------------------

    @Test
    public void stickUpScrollsUp() {
        // rightStickY is -1 for full up; positive wheel delta scrolls up.
        assertTrue(
            "Stick up must produce a positive (up) wheel delta",
            ScrollStepper.wheelDelta(-1f, SENSITIVITY, 0.016f) > 0.0);
        assertTrue(
            "Stick up must produce a positive (up) wheel delta",
            ScrollStepper.wheelDelta(-0.4f, SENSITIVITY, 0.016f) > 0.0);
    }

    @Test
    public void stickDownScrollsDown() {
        assertTrue(ScrollStepper.wheelDelta(1f, SENSITIVITY, 0.016f) < 0.0);
        assertTrue(ScrollStepper.wheelDelta(0.4f, SENSITIVITY, 0.016f) < 0.0);
    }

    @Test
    public void centredStickProducesNoEvent() {
        assertEquals(0.0, ScrollStepper.wheelDelta(0f, SENSITIVITY, 0.016f), 0.0);
    }

    @Test
    public void nonPositiveDeltaSecondsProducesNoEvent() {
        // First frame after a focus change has deltaSeconds == 0.
        assertEquals(0.0, ScrollStepper.wheelDelta(-1f, SENSITIVITY, 0f), 0.0);
        assertEquals(0.0, ScrollStepper.wheelDelta(-1f, SENSITIVITY, -1f), 0.0);
    }

    @Test
    public void zeroSensitivityProducesNoEvent() {
        assertEquals(0.0, ScrollStepper.wheelDelta(-1f, 0f, 0.016f), 0.0);
    }

    @Test
    public void deltaIsProportionalToElapsedTime() {
        // A 100 ms hitch must deliver the same total as six 16.6 ms frames.
        double hitch = ScrollStepper.wheelDelta(-1f, SENSITIVITY, 0.1f);
        double frames = 0.0;
        for (int i = 0; i < 6; i++) {
            frames += ScrollStepper.wheelDelta(-1f, SENSITIVITY, 1f / 60f);
        }
        assertEquals(hitch, frames, 1e-3);
    }
}
