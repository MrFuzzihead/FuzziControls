package com.mrfuzzihead.fuzzicontrols.controller;

/**
 * Converts a normalised controller stick axis into a Minecraft movement input value.
 *
 * <p>
 * Vanilla fills {@code moveForward}/{@code moveStrafe} with {@code -1/0/+1} from the key bindings and
 * multiplies them by {@value #SNEAK_FACTOR} while sneaking. This mod replaces that value with an
 * analogue magnitude, but <b>only when the controller is actually driving that axis</b> — otherwise a
 * connected-but-idle controller would overwrite the keyboard's WASD value with zero, making WASD
 * appear broken whenever a gamepad is plugged in.
 *
 * <p>
 * The maths lives here (rather than inline in the mixin) so the "don't clobber the keyboard" contract
 * and the sneak-speed handling can be unit tested.
 */
public final class AnalogMovement {

    /** Vanilla sneak multiplier applied to {@code AIMoveSpeed} inside {@code MovementInputFromOptions}. */
    public static final float SNEAK_FACTOR = 0.3f;

    private AnalogMovement() {}

    /**
     * Replaces one movement input axis with the stick's analogue value.
     *
     * @param inputValue axis value vanilla produced this tick (sign = direction, {@code 0} = key not held)
     * @param stickValue normalised stick deflection in the same sign convention, within {@code [-1, 1]}
     * @param sneaking   whether the player is sneaking this tick
     * @return the value to store back; {@code inputValue} unchanged when the controller is not driving it
     */
    public static float apply(float inputValue, float stickValue, boolean sneaking) {
        // Nothing to do if the axis is idle in-game, or the stick is centred (dead zone -> 0.0).
        // The second check is what keeps the keyboard in control while a controller merely sits
        // connected: moveForward is 1 from W, stick is 0, so we must leave the 1 alone.
        if (inputValue == 0f || stickValue == 0f) return inputValue;

        // Quadratic curve so gentle tilt is precise.
        float scaled = Math.copySign(stickValue * stickValue, inputValue);

        // Overwriting the vanilla value also discards vanilla's sneak scaling, so re-apply it once.
        return sneaking ? scaled * SNEAK_FACTOR : scaled;
    }
}
