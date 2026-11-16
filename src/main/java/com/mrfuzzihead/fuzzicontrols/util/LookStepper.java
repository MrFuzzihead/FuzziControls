package com.mrfuzzihead.fuzzicontrols.util;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Converts right-stick deflection into per-frame camera rotation.
 *
 * <p>
 * Two conventions matter here and are easy to get wrong, so they are pinned by unit tests:
 * <ul>
 * <li>{@code ControllerState.rightStickY()} is {@code -1} for full up and {@code +1} for full down,
 * and Minecraft's {@code rotationPitch} grows downwards, so a stick-up deflection produces a
 * <em>negative</em> pitch delta (look up).</li>
 * <li>Vertical look must honour the player's inverted-mouse preference
 * ({@code GameSettings.invertMouse}), which vanilla applies to the mouse path but which this mod has
 * to apply itself for stick input.</li>
 * </ul>
 *
 * <p>
 * The stick response is a blend of linear and cubic: gentle deflection is precise for aiming, while
 * full deflection still reaches the configured turn rate. Rotation is scaled by elapsed time, so
 * the degrees-per-second rate is identical at any frame rate.
 *
 * @see com.mrfuzzihead.fuzzicontrols.Config#lookSensitivity
 */
@SideOnly(Side.CLIENT)
public final class LookStepper {

    /** Degrees of rotation per second at full stick deflection, before {@code lookSensitivity}. */
    public static final float BASE_DEGREES_PER_SECOND = 180f;

    /** Vanilla clamps pitch to this range. */
    public static final float PITCH_LIMIT = 90f;

    /** Linear share of the response curve; the remaining share is cubic for fine aim. */
    public static final float LINEAR_RESPONSE_WEIGHT = 0.25f;

    private LookStepper() {}

    /**
     * Applies the aim response curve to one stick axis.
     *
     * <p>
     * A purely linear stick makes small corrections twitchy because the same degrees-per-pixel is
     * used at every deflection. This curve keeps full-speed turning at the edge but reduces the
     * centre sensitivity: {@code 0.5} deflection produces {@code 0.21875} of full speed, and
     * {@code 0.1} produces {@code 0.02575}. The function is odd, so signs and inverted look are
     * preserved.
     *
     * @param deflection stick axis in {@code [-1, 1]}; values outside are clamped
     * @return shaped response in {@code [-1, 1]}
     */
    public static float response(float deflection) {
        float clamped = Math.max(-1f, Math.min(1f, deflection));
        float magnitude = Math.abs(clamped);
        float shaped = magnitude * (LINEAR_RESPONSE_WEIGHT + (1f - LINEAR_RESPONSE_WEIGHT) * magnitude * magnitude);
        return Math.copySign(shaped, clamped);
    }

    /**
     * Returns the yaw change for one frame (positive = turn right). Horizontal look is never inverted.
     *
     * @param rightStickX  right stick horizontal deflection, {@code -1} = full left, {@code +1} = full right
     * @param sensitivity  multiplier from {@code Config.lookSensitivity}
     * @param deltaSeconds seconds elapsed since the previous frame
     * @return yaw change in degrees
     */
    public static float yawDelta(float rightStickX, float sensitivity, float deltaSeconds) {
        if (deltaSeconds <= 0f) return 0f;
        return response(rightStickX) * BASE_DEGREES_PER_SECOND * sensitivity * deltaSeconds;
    }

    /**
     * Returns the pitch change for one frame (positive = look down).
     *
     * @param rightStickY    right stick vertical deflection, {@code -1} = full up, {@code +1} = full down
     * @param sensitivity    multiplier from {@code Config.lookSensitivity}
     * @param deltaSeconds   seconds elapsed since the previous frame
     * @param invertVertical whether the player plays with inverted vertical look
     * @return pitch change in degrees
     */
    public static float pitchDelta(float rightStickY, float sensitivity, float deltaSeconds, boolean invertVertical) {
        if (deltaSeconds <= 0f) return 0f;
        float delta = response(rightStickY) * BASE_DEGREES_PER_SECOND * sensitivity * deltaSeconds;
        return invertVertical ? -delta : delta;
    }

    /**
     * Clamps pitch to the vanilla range so the camera cannot flip over.
     *
     * @param pitch accumulated pitch in degrees
     * @return pitch clamped to {@code [-90, 90]}
     */
    public static float clampPitch(float pitch) {
        if (pitch < -PITCH_LIMIT) return -PITCH_LIMIT;
        return pitch > PITCH_LIMIT ? PITCH_LIMIT : pitch;
    }
}
