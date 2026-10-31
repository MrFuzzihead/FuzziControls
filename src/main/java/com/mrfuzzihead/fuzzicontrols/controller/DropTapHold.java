package com.mrfuzzihead.fuzzicontrols.controller;

/**
 * Tap-versus-hold logic for the drop button (B / Circle).
 *
 * <p>
 * Nothing drops on the initial press, because the press may turn out to be either a tap (drop one
 * item) or a hold (drop the whole stack). A tap is only known on release, and the hold fires exactly
 * once when the button has been held for {@link #HOLD_TICKS} ticks.
 *
 * <p>
 * A press that closed a GUI must not also drop an item, so the caller marks it with
 * {@link #blockNextRelease()} — that is the {@code dropBlockedByGui} flag, moved here so the state
 * machine can be unit tested.
 */
final class DropTapHold {

    /** Ticks the button must be held before the whole stack is dropped (10 ticks = 0.5 s). */
    static final int HOLD_TICKS = 10;

    /** What the caller should do with the item in hand this tick. */
    enum Action {
        /** Nothing to do. */
        NONE,
        /** Drop a single item. */
        DROP_ONE,
        /** Drop the entire stack. */
        DROP_STACK
    }

    private int heldTicks;

    private boolean holdFired;

    private boolean blocked;

    /**
     * Advances the state machine by one tick.
     *
     * @param pressed         whether the drop button is held this tick
     * @param dropEntireStack whether holding should drop the whole stack
     * @return the action to perform
     */
    Action update(boolean pressed, boolean dropEntireStack) {
        if (!pressed) {
            // Release: a genuine tap drops one item. A hold already dropped the stack, and a press
            // that closed a GUI must not drop anything.
            Action result = (!blocked && !holdFired && heldTicks > 0) ? Action.DROP_ONE : Action.NONE;
            reset();
            return result;
        }

        if (heldTicks == 0) {
            // Fresh press: start counting, nothing drops yet.
            heldTicks = 1;
            holdFired = false;
            blocked = false;
            return Action.NONE;
        }

        if (blocked) return Action.NONE;

        heldTicks++;
        if (dropEntireStack && !holdFired && heldTicks >= HOLD_TICKS) {
            holdFired = true;
            return Action.DROP_STACK;
        }
        return Action.NONE;
    }

    /**
     * Marks the current press as having been consumed by closing a GUI, so its release does not also
     * drop an item.
     */
    void blockNextRelease() {
        blocked = true;
    }

    /** Forgets all state (screen closed, input paused, controller reconnected). */
    void reset() {
        heldTicks = 0;
        holdFired = false;
        blocked = false;
    }

    /** Number of ticks the button has been held for the current press. Test/diagnostic use. */
    int heldTicks() {
        return heldTicks;
    }
}
