package com.mrfuzzihead.fuzzicontrols.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

/**
 * Unit tests for {@link KeyClaimTracker}.
 *
 * <p>
 * The invariant under test is the one that keeps the keyboard usable alongside a controller: the mod
 * may only clear a key it pressed itself. Clearing a key the player is holding down makes WASD or
 * shift appear stuck, which is nearly impossible to diagnose from a bug report.
 */
public class KeyClaimTrackerTest {

    private static final int W = 17;

    private static final int SHIFT = 42;

    private KeyClaimTracker tracker;

    @Before
    public void setUp() {
        tracker = new KeyClaimTracker();
    }

    @Test
    public void pressingClaimsTheKey() {
        assertEquals(KeyClaimTracker.Action.PRESS, tracker.update(W, true));
        assertTrue(tracker.isClaimed(W));
    }

    @Test
    public void releasingAClaimedKeyReleasesIt() {
        tracker.update(W, true);
        assertEquals(KeyClaimTracker.Action.RELEASE, tracker.update(W, false));
        assertFalse(tracker.isClaimed(W));
    }

    @Test
    public void neverReleasesAKeyTheControllerDidNotPress() {
        // The keyboard is holding W; the controller must leave it alone.
        assertEquals(KeyClaimTracker.Action.NONE, tracker.update(W, false));
        assertFalse(tracker.isClaimed(W));
    }

    @Test
    public void repeatingAPressKeepsTheClaim() {
        tracker.update(W, true);
        assertEquals(KeyClaimTracker.Action.PRESS, tracker.update(W, true));
        assertEquals(KeyClaimTracker.Action.RELEASE, tracker.update(W, false));
    }

    @Test
    public void keysAreTrackedIndependently() {
        tracker.update(W, true);
        tracker.update(SHIFT, true);

        assertEquals(KeyClaimTracker.Action.RELEASE, tracker.update(SHIFT, false));
        assertEquals("W must still be claimed", KeyClaimTracker.Action.RELEASE, tracker.update(W, false));
    }

    @Test
    public void explicitReleaseGivesUpTheClaim() {
        tracker.update(SHIFT, true);
        assertTrue("The controller was holding shift", tracker.release(SHIFT));
        assertFalse("Nothing to give up the second time", tracker.release(SHIFT));
        // After giving up the claim, a later release must not touch the key (keyboard owns it now).
        assertEquals(KeyClaimTracker.Action.NONE, tracker.update(SHIFT, false));
    }

    @Test
    public void clearForgetsEveryClaim() {
        tracker.update(W, true);
        tracker.update(SHIFT, true);
        tracker.clear();

        assertTrue(tracker.isEmpty());
        assertEquals(KeyClaimTracker.Action.NONE, tracker.update(W, false));
    }

    @Test
    public void claimedCodesAreReported() {
        tracker.update(W, true);
        tracker.update(SHIFT, true);

        assertEquals(
            2,
            tracker.claimed()
                .size());
        assertTrue(
            tracker.claimed()
                .contains(W));
        assertTrue(
            tracker.claimed()
                .contains(SHIFT));
    }

    @Test
    public void emptyUntilSomethingIsPressed() {
        assertTrue(tracker.isEmpty());
        tracker.update(W, false);
        assertTrue("A non-claiming update must not add anything", tracker.isEmpty());
    }

    @Test
    public void claimedViewIsUnmodifiable() {
        tracker.update(W, true);
        try {
            tracker.claimed()
                .clear();
            throw new AssertionError("claimed() must be unmodifiable");
        } catch (UnsupportedOperationException expected) {
            // expected
        }
    }
}
