package com.mrfuzzihead.fuzzicontrols.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import net.minecraft.client.settings.GameSettings;

import org.junit.Test;

/**
 * Unit tests for {@link SliderStepper}.
 *
 * <p>
 * Regression coverage for the D-pad slider bug where adding {@code dpadSliderStep} to a
 * slider's normalised value and re-normalising through the option's snapping function made
 * the value snap straight back, so options with a coarse discrete grid (Mipmap Levels,
 * Anisotropic Filtering, …) could not be adjusted at all.
 *
 * <p>
 * The real {@link GameSettings.Options} enum is used rather than a fake, because the bug was
 * a mismatch between the configured step and the option's own value grid.
 */
public class SliderStepperTest {

    /** The default {@code dpadSliderStep} from Config. */
    private static final float STEP = 0.05f;

    private static float up(GameSettings.Options option, float current) {
        return SliderStepper.nextValue(option, current, STEP, true);
    }

    private static float down(GameSettings.Options option, float current) {
        return SliderStepper.nextValue(option, current, STEP, false);
    }

    /** Walks a slider in one direction, returning every normalised value visited. */
    private static float[] walk(GameSettings.Options option, float start, boolean increase, int presses) {
        float[] visited = new float[presses + 1];
        visited[0] = start;
        for (int i = 0; i < presses; i++) {
            visited[i + 1] = SliderStepper.nextValue(option, visited[i], STEP, increase);
        }
        return visited;
    }

    // -------------------------------------------------------------------------
    // Regression: coarse discrete grids must actually move
    // -------------------------------------------------------------------------

    @Test
    public void mipmapLevels_eachPressMovesTheSlider() {
        // Mipmap grid is 0.25 apart; the old code re-snapped 0.25 + 0.05 straight back to 0.25.
        assertEquals(
            "Old behaviour: 0.25 + 0.05 snapped back to 0.25",
            0.5f,
            up(GameSettings.Options.MIPMAP_LEVELS, 0.25f),
            0f);
    }

    @Test
    public void mipmapLevels_walksTheFullGridUp() {
        float[] visited = walk(GameSettings.Options.MIPMAP_LEVELS, 0f, true, 4);
        assertEquals(0.25f, visited[1], 0f);
        assertEquals(0.5f, visited[2], 0f);
        assertEquals(0.75f, visited[3], 0f);
        assertEquals(1.0f, visited[4], 0f);
    }

    @Test
    public void mipmapLevels_walksTheFullGridDown() {
        float[] visited = walk(GameSettings.Options.MIPMAP_LEVELS, 1f, false, 4);
        assertEquals(0.75f, visited[1], 0f);
        assertEquals(0.5f, visited[2], 0f);
        assertEquals(0.25f, visited[3], 0f);
        assertEquals(0f, visited[4], 0f);
    }

    @Test
    public void mipmapLevels_clampsAtBothEnds() {
        assertEquals(1.0f, up(GameSettings.Options.MIPMAP_LEVELS, 1.0f), 0f);
        assertEquals(0f, down(GameSettings.Options.MIPMAP_LEVELS, 0f), 0f);
    }

    @Test
    public void anisotropicFiltering_eachPressMovesTheSlider() {
        // Power-of-two grid: 1, 2, 4, 8, 16 -> normalised 0, 0.0667, 0.2, 0.4667, 1.
        // Every one of these used to snap back to itself.
        float[] starts = { 0f, 1f / 15f, 0.2f, 7f / 15f, 1f };
        for (float start : starts) {
            if (start < 1f) {
                assertNotEquals(
                    "Anisotropic Filtering should move up from " + start,
                    start,
                    up(GameSettings.Options.ANISOTROPIC_FILTERING, start),
                    0f);
            }
            if (start > 0f) {
                assertNotEquals(
                    "Anisotropic Filtering should move down from " + start,
                    start,
                    down(GameSettings.Options.ANISOTROPIC_FILTERING, start),
                    0f);
            }
        }
    }

    @Test
    public void anisotropicFiltering_walksTheFullGridUp() {
        float[] visited = walk(GameSettings.Options.ANISOTROPIC_FILTERING, 0f, true, 4);
        assertEquals(1f / 15f, visited[1], 1e-5f);
        assertEquals(0.2f, visited[2], 1e-5f);
        assertEquals(7f / 15f, visited[3], 1e-5f);
        assertEquals(1.0f, visited[4], 1e-5f);
    }

    @Test
    public void renderDistance_eachPressMovesTheSlider() {
        // Grid of 1 chunk over a 2-16 range = 0.0714 per step.
        float[] visited = walk(GameSettings.Options.RENDER_DISTANCE, 0f, true, 3);
        for (int i = 1; i < visited.length; i++) {
            assertTrue("Render distance must advance on press " + i, visited[i] > visited[i - 1]);
        }
    }

    @Test
    public void framerateLimit_eachPressMovesTheSlider() {
        // Grid of 10 fps over a 10-260 range = 0.04 per step (finer than dpadSliderStep).
        float[] visited = walk(GameSettings.Options.FRAMERATE_LIMIT, 0f, true, 3);
        for (int i = 1; i < visited.length; i++) {
            assertTrue("Framerate limit must advance on press " + i, visited[i] > visited[i - 1]);
        }
    }

    @Test
    public void nonCanonicalCurrentValue_stillMoves() {
        // A slider dragged with the mouse stores a continuous value; stepping must still work.
        float moved = up(GameSettings.Options.MIPMAP_LEVELS, 0.3f);
        assertTrue("Stepping from a non-grid value must still move", moved > 0.3f);
        float movedDown = down(GameSettings.Options.MIPMAP_LEVELS, 0.3f);
        assertTrue("Stepping down from a non-grid value must still move", movedDown < 0.3f);
    }

    // -------------------------------------------------------------------------
    // Continuous options keep the configured fraction semantics
    // -------------------------------------------------------------------------

    @Test
    public void continuousOption_movesByTheConfiguredFraction() {
        assertEquals(0.05f, up(GameSettings.Options.GAMMA, 0f), 1e-5f);
        assertEquals(0.55f, up(GameSettings.Options.GAMMA, 0.5f), 1e-5f);
        assertEquals(0.45f, down(GameSettings.Options.GAMMA, 0.5f), 1e-5f);
    }

    @Test
    public void continuousOption_clampsAtBothEnds() {
        assertEquals(1.0f, up(GameSettings.Options.GAMMA, 1.0f), 0f);
        assertEquals(0f, down(GameSettings.Options.GAMMA, 0f), 0f);
        assertEquals(1.0f, up(GameSettings.Options.GAMMA, 0.99f), 0f);
        assertEquals(0f, down(GameSettings.Options.GAMMA, 0.01f), 0f);
    }

    @Test
    public void fractionLargerThanGridStep_usesTheFraction() {
        // 0.3 > the 0.25 mipmap grid: the plain fraction move already crosses a grid point.
        assertEquals(0.5f, SliderStepper.nextValue(GameSettings.Options.MIPMAP_LEVELS, 0.25f, 0.3f, true), 0f);
    }

    // -------------------------------------------------------------------------
    // Generic invariant + input handling
    // -------------------------------------------------------------------------

    @Test
    public void everyFloatOption_movesInBothDirectionsFromMidRange() {
        for (GameSettings.Options option : GameSettings.Options.values()) {
            if (!option.getEnumFloat()) continue;

            float mid = option.normalizeValue(option.denormalizeValue(0.5f));
            if (mid > 0f) {
                assertTrue(
                    option + " must step down from " + mid + " (was " + down(option, mid) + ")",
                    down(option, mid) < mid);
            }
            if (mid < 1f) {
                assertTrue(
                    option + " must step up from " + mid + " (was " + up(option, mid) + ")",
                    up(option, mid) > mid);
            }
        }
    }

    @Test
    public void zeroFraction_isANoOp() {
        assertEquals(0.5f, SliderStepper.nextValue(GameSettings.Options.MIPMAP_LEVELS, 0.5f, 0f, true), 0f);
        assertEquals(0.5f, SliderStepper.nextValue(GameSettings.Options.MIPMAP_LEVELS, 0.5f, 0f, false), 0f);
    }

    @Test
    public void fractionIsTreatedAsAMagnitude() {
        // The direction comes from `increase` (the caller passes delta and delta > 0),
        // so a negative fraction still steps by its magnitude in the requested direction.
        assertEquals(0.5f, SliderStepper.nextValue(GameSettings.Options.MIPMAP_LEVELS, 0.25f, -0.05f, true), 0f);
        assertEquals(0.25f, SliderStepper.nextValue(GameSettings.Options.MIPMAP_LEVELS, 0.5f, -0.05f, false), 0f);
    }

    @Test
    public void nullOption_isANoOp() {
        assertEquals(0.5f, SliderStepper.nextValue(null, 0.5f, STEP, true), 0f);
    }

    @Test
    public void outOfRangeCurrentValue_isClamped() {
        assertEquals(0f, down(GameSettings.Options.GAMMA, -0.5f), 0f);
        assertEquals(1.0f, up(GameSettings.Options.GAMMA, 1.5f), 0f);
    }
}
