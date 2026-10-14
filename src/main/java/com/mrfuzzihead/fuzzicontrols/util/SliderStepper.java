package com.mrfuzzihead.fuzzicontrols.util;

import net.minecraft.client.settings.GameSettings;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Computes the next value of a vanilla {@link net.minecraft.client.gui.GuiOptionSlider} when the
 * D-pad steps it left or right.
 *
 * <p>
 * A slider's stored value is <em>normalised</em> to [0, 1], but most options only allow a small
 * set of values: {@link GameSettings.Options#denormalizeValue(float)} rounds the value to the
 * option's own step (or, for anisotropic filtering, up to the next power of two) and
 * {@link GameSettings.Options#normalizeValue(float)} maps it back. Adding a fraction of the range
 * and re-normalising therefore <em>silently does nothing</em> whenever the option's step is
 * coarser than that fraction — e.g. Mipmap Levels (steps of 0.25) or Anisotropic Filtering
 * (power-of-two steps of 0.067 – 0.133), so those sliders could not be moved at all.
 *
 * <p>
 * This class keeps the configured fraction as the normal behaviour (so continuous options move by
 * {@code dpadSliderStep} of their range), but when that step snaps back to the current value it
 * walks forward on the option's own grid instead. Every press therefore moves the slider by at
 * least one grid step, unless it is already at the end of its range.
 *
 * @see com.mrfuzzihead.fuzzicontrols.Config#dpadSliderStep
 */
@SideOnly(Side.CLIENT)
public final class SliderStepper {

    /** Smallest change in normalised slider space that counts as a real move. */
    private static final float EPSILON = 1e-4f;

    /**
     * Resolution used when walking an option's discrete grid. Only reached when the requested
     * step was swallowed, which implies a grid step larger than {@code dpadSliderStep} (>= 0.01),
     * so this is always at least an order of magnitude finer than the grid being searched.
     */
    private static final int GRID_PROBES = 1000;

    private SliderStepper() {}

    /**
     * Returns the normalised slider value one press away from {@code currentNormalized}.
     *
     * @param option            the option backing the slider (never {@code null} in practice)
     * @param currentNormalized current normalised slider value, normally in [0, 1]
     * @param fraction          step size as a fraction of the slider's full range
     * @param increase          {@code true} to step right/up, {@code false} to step left/down
     * @return the new normalised value; equal to {@code currentNormalized} when no move is possible
     */
    public static float nextValue(GameSettings.Options option, float currentNormalized, float fraction,
        boolean increase) {
        float current = clamp01(currentNormalized);
        if (option == null) return current;

        float step = Math.abs(fraction);
        if (step <= 0f) return current;

        // Already at the end of the range in this direction — nothing to do.
        if (increase ? current >= 1f - EPSILON : current <= EPSILON) return current;

        // Normal behaviour: move by the configured fraction of the slider's range.
        float requested = clamp01(increase ? current + step : current - step);
        float snapped = option.normalizeValue(option.denormalizeValue(requested));
        if (moved(current, snapped, increase)) return snapped;

        // The option's discrete grid is coarser than the requested step, so the move landed
        // inside the current grid cell. Walk to the next grid point in this direction instead.
        float probeStep = 1f / GRID_PROBES;
        for (int i = 1; i <= GRID_PROBES; i++) {
            float probe = clamp01(increase ? current + i * probeStep : current - i * probeStep);
            float candidate = option.normalizeValue(option.denormalizeValue(probe));
            if (moved(current, candidate, increase)) return candidate;
            if (probe <= 0f || probe >= 1f) break;
        }

        return current;
    }

    private static boolean moved(float from, float to, boolean increase) {
        return increase ? to > from + EPSILON : to < from - EPSILON;
    }

    private static float clamp01(float value) {
        if (value < 0f) return 0f;
        return value > 1f ? 1f : value;
    }
}
