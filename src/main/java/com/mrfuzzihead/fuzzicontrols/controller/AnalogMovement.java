package com.mrfuzzihead.fuzzicontrols.controller;

/**
 * Converts a normalised controller stick axis into a Minecraft movement input value.
 *
 * <p>
 * Vanilla fills {@code moveForward}/{@code moveStrafe} with {@code -1/0/+1} from the key bindings and
 * multiplies them by {@value #SNEAK_FACTOR} while sneaking. This mod replaces that value with the
 * stick's direction, but <b>only when the controller is actually driving that axis</b> — otherwise a
 * connected-but-idle controller would overwrite the keyboard's WASD value with zero, making WASD
 * appear broken whenever a gamepad is plugged in.
 *
 * <p>
 * Vanilla normalises the movement vector, so the magnitude is applied separately by scaling the
 * friction in {@code MixinEntity_MoveFlying}. The maths lives here (rather than inline in the mixin)
 * so the "don't clobber the keyboard" contract, radial speed curve and sneak-speed handling can be
 * unit tested.
 */
public final class AnalogMovement {

    /** Vanilla sneak multiplier applied to {@code AIMoveSpeed} inside {@code MovementInputFromOptions}. */
    public static final float SNEAK_FACTOR = 0.3f;

    /**
     * Radial speed scale for the local player's current movement tick, or {@code -1} when analogue
     * scaling is inactive. Set by {@link com.mrfuzzihead.fuzzicontrols.mixins.early.MixinEntityPlayerSP}
     * and consumed by {@code MixinEntity_MoveFlying} when it scales {@code Entity.moveFlying()}'s
     * friction. Client thread only.
     */
    private static float analogSpeedScale = -1f;

    private AnalogMovement() {}

    /**
     * Replaces one movement input axis with the stick's deflection for <em>direction</em>.
     *
     * <p>
     * The returned value is deliberately linear in the stick axis. Vanilla
     * {@code Entity.moveFlying()} normalises the input vector to length 1, so this value only
     * determines direction; the analogue speed is applied separately by
     * {@link #speedScale(float, float, boolean)} through the movement mixin. Squaring the axes here
     * would distort diagonal movement (a 30-degree stick would move at 18 degrees, for example).
     *
     * @param inputValue axis value vanilla produced this tick (sign = direction, {@code 0} = key not held)
     * @param stickValue normalised stick deflection in the same sign convention, within {@code [-1, 1]}
     * @return the value to store back; {@code inputValue} unchanged when the controller is not driving it
     */
    public static float apply(float inputValue, float stickValue) {
        // Nothing to do if the axis is idle in-game, or the stick is centred (dead zone -> 0.0).
        // The second check is what keeps the keyboard in control while a controller merely sits
        // connected: moveForward is 1 from W, stick is 0, so we must leave the 1 alone.
        if (inputValue == 0f || stickValue == 0f) return inputValue;

        // Keep the stick vector's true direction; magnitude is applied by the moveFlying mixin.
        return Math.copySign(stickValue, inputValue);
    }

    /**
     * Returns the radial speed factor for a stick position, in {@code [0, 1]}.
     *
     * <p>
     * Vanilla's {@code Entity.moveFlying()} normalises any non-zero input vector to length 1, which
     * discards the analogue magnitude. The movement mixin keeps the per-axis values only for
     * <em>direction</em> and uses this radial factor to scale the movement friction instead. Squaring
     * the radial magnitude gives the familiar "half tilt = quarter speed" analogue response.
     *
     * @param stickX normalised horizontal stick deflection
     * @param stickY normalised vertical stick deflection
     * @return radial speed factor, clamped to {@code [0, 1]}
     */
    public static float speedScale(float stickX, float stickY) {
        float magnitude = (float) Math.sqrt(stickX * stickX + stickY * stickY);
        if (!Float.isFinite(magnitude)) return 0f;
        magnitude = Math.min(1f, magnitude);
        return magnitude * magnitude;
    }

    /**
     * Returns the radial speed factor including the vanilla sneak multiplier.
     *
     * @param stickX   normalised horizontal stick deflection
     * @param stickY   normalised vertical stick deflection
     * @param sneaking whether the player is sneaking this tick
     * @return radial speed factor, clamped to {@code [0, 1]}
     */
    public static float speedScale(float stickX, float stickY, boolean sneaking) {
        float scale = speedScale(stickX, stickY);
        return sneaking ? scale * SNEAK_FACTOR : scale;
    }

    /** Sets the radial speed factor for the current movement tick. */
    public static void setAnalogSpeedScale(float scale) {
        if (!Float.isFinite(scale) || scale < 0f) {
            analogSpeedScale = -1f;
            return;
        }
        analogSpeedScale = Math.min(1f, scale);
    }

    /** Clears the radial speed factor, leaving keyboard/full-speed movement untouched. */
    public static void clearAnalogSpeedScale() {
        analogSpeedScale = -1f;
    }

    /** Returns the current radial speed factor, or {@code -1} when inactive. */
    public static float getAnalogSpeedScale() {
        return analogSpeedScale;
    }

    /**
     * Scales a movement friction value by the current analogue factor.
     *
     * @param friction vanilla friction for this movement tick
     * @param scale    radial speed factor, or {@code -1} to leave the friction unchanged
     * @return scaled friction, or {@code friction} when analogue scaling is inactive
     */
    public static float scaleFriction(float friction, float scale) {
        if (!Float.isFinite(scale) || scale < 0f || scale >= 1f) return friction;
        return friction * scale;
    }
}
