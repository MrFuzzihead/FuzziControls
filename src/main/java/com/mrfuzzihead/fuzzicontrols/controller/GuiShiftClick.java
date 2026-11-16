package com.mrfuzzihead.fuzzicontrols.controller;

/**
 * Hold/tap state machine for {@link ControllerAction#GUI_SHIFT_CLICK}.
 *
 * <p>
 * Desired behaviour:
 * <ul>
 * <li><b>Hold</b> the shift button and every left-click is a Shift+click (move a whole stack). The
 * mode stays armed until the button is released, so repeatedly clicking A/X while holding LT/L2
 * moves stack after stack.</li>
 * <li><b>Tap</b> the shift button and the next left-click is a Shift+click, even after the button is
 * released. This keeps the original one-shot behaviour for players who prefer a tap.</li>
 * <li>Pressing the physical keyboard Shift takes priority and clears the controller's armed state.</li>
 * </ul>
 *
 * <p>
 * Extracted from {@link ControllerTickHandler} so the hold/tap transitions can be unit tested
 * without a client or a GUI.
 */
final class GuiShiftClick {

    /** True when the next left-click should be delivered as Shift+click. */
    private boolean armed;

    /** True while the bound controller button is physically held. */
    private boolean held;

    /** Whether a Shift+click has already been delivered during the current hold. */
    private boolean usedWhileHeld;

    /** Previous button state, for rising/falling edge detection. */
    private boolean wasActive;

    /**
     * Updates the state once per GUI tick.
     *
     * @param active        whether the bound shift button is currently active
     * @param keyboardShift whether the physical keyboard Shift key is down
     */
    void update(boolean active, boolean keyboardShift) {
        if (keyboardShift) {
            // Keyboard Shift wins. Remember if this hold has been used so releasing the controller
            // button later does not leave a one-shot arm behind.
            armed = false;
            usedWhileHeld = active;
            held = active;
            wasActive = active;
            return;
        }

        boolean rising = active && !wasActive;
        if (rising) {
            usedWhileHeld = false;
        }

        if (active) {
            // While held, keep the mode armed for every click, not just the first.
            armed = true;
            held = true;
        } else if (wasActive) {
            // Button released. A hold that delivered at least one Shift+click ends here; a tap
            // leaves the mode armed for one more click.
            held = false;
            if (usedWhileHeld) {
                armed = false;
            }
        } else {
            held = false;
        }

        wasActive = active;
    }

    /**
     * Records that a Shift+click was delivered.
     *
     * <p>
     * While the button is held the mode stays armed for the next click. A tap-armed one-shot is
     * consumed.
     */
    void onClickDelivered() {
        if (held) {
            usedWhileHeld = true;
        } else {
            armed = false;
        }
    }

    /** True when the next left-click should be a Shift+click. */
    boolean isArmed() {
        return armed;
    }

    /** True while the shift button is held. Test/diagnostic use. */
    boolean isHeld() {
        return held;
    }

    /** Clears all state; called when a GUI closes or the controller disconnects. */
    void reset() {
        armed = false;
        held = false;
        usedWhileHeld = false;
        wasActive = false;
    }
}
