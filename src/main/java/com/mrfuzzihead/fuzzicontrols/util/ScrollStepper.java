package com.mrfuzzihead.fuzzicontrols.util;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Converts right-stick deflection into a mouse-wheel delta for GUI scrolling.
 *
 * <p>
 * The wheel delta is expressed as <em>notches per second</em> and scaled by the frame's elapsed
 * time, so the delivered scroll rate is identical at any frame rate. The stick's quadratic
 * response curve is applied to the <em>deflection</em> only: squaring the already time-scaled
 * per-frame delta would make the rate proportional to {@code sensitivity² / fps} instead of
 * {@code sensitivity}, i.e. several times too slow and different at every frame rate.
 *
 * @see com.mrfuzzihead.fuzzicontrols.Config#scrollSensitivity
 */
@SideOnly(Side.CLIENT)
public final class ScrollStepper {

    private ScrollStepper() {}

    /**
     * Returns the wheel delta to inject for one frame.
     *
     * <p>
     * Uses the LWJGL / lwjgl3ify sign convention: positive scrolls up (away from the user), which
     * matches {@link org.lwjgl.input.Mouse#getEventDWheel()} and
     * {@code org.lwjglx.input.Mouse.addWheelEvent(double)}. {@code ControllerState.rightStickY()}
     * is {@code -1} for full up, so the result is negated.
     *
     * @param rightStickY  right stick vertical deflection, {@code -1} = full up, {@code +1} = full down
     * @param sensitivity  scroll speed in notches per second at full deflection
     * @param deltaSeconds seconds elapsed since the previous frame
     * @return wheel delta in notches; {@code 0} when the stick is centred or no time has elapsed
     */
    public static double wheelDelta(float rightStickY, float sensitivity, float deltaSeconds) {
        if (rightStickY == 0f || deltaSeconds <= 0f || sensitivity == 0f) return 0.0;

        // Signed quadratic response: |gain| = deflection², sign = deflection direction.
        // Gentle tilt stays precise; full deflection gives the configured speed.
        double gain = rightStickY * (double) Math.abs(rightStickY);
        return -gain * sensitivity * deltaSeconds;
    }
}
