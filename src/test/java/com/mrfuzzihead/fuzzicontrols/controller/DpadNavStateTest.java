package com.mrfuzzihead.fuzzicontrols.controller;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

/**
 * Unit tests for {@link DpadNavState}.
 *
 * <p>
 * This state machine decides who owns the GUI: the D-pad focus system or the virtual cursor. A
 * regression here is what made D-pad navigation switch itself off after a slot confirm (bug #6), so
 * every transition is pinned.
 */
public class DpadNavStateTest {

    private DpadNavState state;

    @Before
    public void setUp() {
        state = new DpadNavState();
    }

    /** Convenience: update with a centred stick and no mouse movement. */
    private boolean update(boolean dpadPressed) {
        return state.update(true, dpadPressed, 0f, 0f, false);
    }

    @Test
    public void startsInactive() {
        assertFalse(state.isActive());
        assertFalse(update(false));
    }

    @Test
    public void dpadPressActivates() {
        assertTrue(update(true));
        assertTrue(state.isActive());
    }

    @Test
    public void naveStaysActiveWhileTheDpadIsHeld() {
        assertTrue(update(true));
        assertTrue(update(true));
        assertTrue(update(true));
    }

    @Test
    public void releasingTheDpadKeepsNavigationActive() {
        // Releasing must not hand control back: the player may be between presses.
        assertTrue(update(true));
        assertTrue(update(false));
        assertTrue(update(false));
    }

    @Test
    public void disabledConfigForcesInactive() {
        assertTrue(update(true));
        assertFalse(state.update(false, true, 0f, 0f, false));
        assertFalse(state.isActive());
    }

    @Test
    public void stickPastTheThresholdDeactivates() {
        update(true);
        float past = DpadNavState.STICK_DEACTIVATE_THRESHOLD + 0.01f;
        assertFalse(state.update(true, false, past, 0f, false));
        assertFalse(state.isActive());
    }

    @Test
    public void stickInsideTheThresholdKeepsNavigationActive() {
        update(true);
        float inside = DpadNavState.STICK_DEACTIVATE_THRESHOLD - 0.01f;
        assertTrue(state.update(true, true, inside, -inside, false));
    }

    @Test
    public void mouseMovementDeactivates() {
        update(true);
        assertFalse(state.update(true, false, 0f, 0f, true));
        assertFalse(state.isActive());
    }

    @Test
    public void mouseMovementWhileInactiveChangesNothing() {
        assertFalse(state.update(true, false, 0f, 0f, true));
        assertFalse(state.isActive());
    }

    @Test
    public void pressArrivingInTheSameTickAsAMouseMoveIsConsumed() {
        // Documents the ordering: activation happens first, then the deactivation signal, so a press
        // that lands in the same tick as a mouse move leaves navigation inactive and its focus
        // movement is skipped. Bug #6 removed the usual cause of this (the mod's own cursor warps);
        // this test keeps the resulting behaviour explicit.
        assertFalse(state.update(true, true, 0f, 0f, true));
        assertFalse(state.isActive());
    }

    @Test
    public void navigationCanBeReactivatedAfterDeactivation() {
        update(true);
        state.update(true, false, 0f, 0f, true);
        assertFalse(state.isActive());

        // Next D-pad press (no deactivating signal this tick) takes control again.
        assertTrue(update(true));
    }

    @Test
    public void stickPushWithoutAnyDpadPressDoesNotActivate() {
        assertFalse(state.update(true, false, 0.9f, 0f, false));
        assertFalse(state.update(true, false, 0f, -0.9f, false));
    }

    @Test
    public void resetForcesInactive() {
        assertTrue(update(true));
        state.reset();
        assertFalse(state.isActive());
    }
}
