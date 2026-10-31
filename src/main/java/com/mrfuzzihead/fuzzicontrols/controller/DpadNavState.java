package com.mrfuzzihead.fuzzicontrols.controller;

/**
 * Whether D-pad discrete navigation currently owns the GUI.
 *
 * <p>
 * Navigation turns on as soon as a D-pad direction is pressed, and hands control back when the player
 * uses the left stick (the virtual cursor) or the mouse. Extracted from
 * {@link ControllerTickHandler} so the transitions — and the ordering quirk that a press arriving in
 * the same tick as a deactivating signal is consumed — are pinned by tests.
 */
final class DpadNavState {

    /**
     * Left-stick deflection that hands control back to the virtual cursor. Deliberately well above
     * the dead zone so resting drift does not cancel navigation.
     */
    static final float STICK_DEACTIVATE_THRESHOLD = 0.15f;

    private boolean active;

    /**
     * Advances the state by one tick.
     *
     * @param enabled        whether discrete navigation is configured on
     * @param anyDpadPressed whether any D-pad direction is currently held
     * @param leftStickX     left stick horizontal deflection
     * @param leftStickY     left stick vertical deflection
     * @param mouseMoved     whether the player moved the mouse since the last tick
     * @return true when navigation owns the GUI after this update
     */
    boolean update(boolean enabled, boolean anyDpadPressed, float leftStickX, float leftStickY, boolean mouseMoved) {
        if (!enabled) {
            active = false;
            return false;
        }

        if (anyDpadPressed && !active) active = true;

        if (active && (Math.abs(leftStickX) > STICK_DEACTIVATE_THRESHOLD
            || Math.abs(leftStickY) > STICK_DEACTIVATE_THRESHOLD)) {
            active = false;
        }

        if (active && mouseMoved) active = false;

        return active;
    }

    /** Returns true while discrete navigation owns the GUI. */
    boolean isActive() {
        return active;
    }

    /** Forgets the current state (screen changed, input paused). */
    void reset() {
        active = false;
    }
}
