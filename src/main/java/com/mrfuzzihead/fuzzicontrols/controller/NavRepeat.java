package com.mrfuzzihead.fuzzicontrols.controller;

import java.util.Arrays;

/**
 * Auto-repeat for held D-pad directions.
 *
 * <p>
 * Discrete GUI navigation originally fired once per press, so crossing a twenty-entry list meant
 * twenty presses. Console UIs instead move once immediately and then repeat after a short delay, which
 * this class provides.
 *
 * <p>
 * Repeat is counted in game ticks (20 Hz) rather than milliseconds so behaviour is deterministic and
 * testable: {@link #INITIAL_DELAY_TICKS} of 8 is 400 ms before the first repeat, then
 * {@link #REPEAT_INTERVAL_TICKS} of 2 gives 10 moves per second while held.
 */
final class NavRepeat {

    /** Ticks a direction must be held before it starts repeating (8 ticks = 400 ms). */
    static final int INITIAL_DELAY_TICKS = 8;

    /** Ticks between repeats once repeating (2 ticks = 100 ms). */
    static final int REPEAT_INTERVAL_TICKS = 2;

    /** Consecutive held ticks per action, indexed by ordinal. */
    private final int[] heldTicks = new int[ControllerAction.values().length];

    /**
     * Advances one direction by one tick.
     *
     * @param action the D-pad navigation action
     * @param active whether the direction is currently held
     * @return true when the caller should move focus this tick
     */
    boolean update(ControllerAction action, boolean active) {
        int slot = action.ordinal();

        if (!active) {
            heldTicks[slot] = 0;
            return false;
        }

        int held = heldTicks[slot] + 1;
        heldTicks[slot] = held;

        // Press: fire immediately so a tap always moves exactly once.
        if (held == 1) return true;

        // Initial delay before repeating.
        if (held < INITIAL_DELAY_TICKS) return false;

        // First repeat lands on the delay boundary, then every interval after it.
        if (held == INITIAL_DELAY_TICKS) return true;
        return (held - INITIAL_DELAY_TICKS) % REPEAT_INTERVAL_TICKS == 0;
    }

    /** Returns how many consecutive ticks the given action has been held. Test/diagnostic use. */
    int heldTicks(ControllerAction action) {
        return heldTicks[action.ordinal()];
    }

    /** Forgets every direction's held state (navigation deactivated, screen changed, input paused). */
    void clear() {
        Arrays.fill(heldTicks, 0);
    }
}
