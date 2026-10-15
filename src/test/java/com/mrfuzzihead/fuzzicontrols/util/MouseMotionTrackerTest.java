package com.mrfuzzihead.fuzzicontrols.util;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

/**
 * Unit tests for {@link MouseMotionTracker}.
 *
 * <p>
 * Regression coverage for D-pad navigation switching itself off: the platform emits a real
 * mouse-motion event when this mod warps the cursor, and the old absolute-position comparison read
 * that as "the player grabbed the mouse". The tick handler then deactivated navigation and consumed
 * the D-pad edges, so the next D-pad press did nothing.
 */
public class MouseMotionTrackerTest {

    private MouseMotionTracker tracker;

    @Before
    public void setUp() {
        tracker = new MouseMotionTracker();
    }

    @Test
    public void firstObservationIsNotMovement() {
        assertFalse("Nothing has moved yet on the first sample", tracker.userMoved(100, 100));
    }

    @Test
    public void stationaryCursorIsNotMovement() {
        tracker.userMoved(100, 100);
        assertFalse(tracker.userMoved(100, 100));
        assertFalse(tracker.userMoved(100, 100));
    }

    @Test
    public void realMovementIsDetected() {
        tracker.userMoved(100, 100);
        assertTrue("A clear jump is player movement", tracker.userMoved(140, 100));
        assertTrue(tracker.userMoved(140, 160));
    }

    @Test
    public void jitterWithinToleranceIsIgnored() {
        tracker.userMoved(100, 100);
        assertFalse("1 px of hand jitter is noise", tracker.userMoved(101, 100));
        assertFalse(tracker.userMoved(101, 101));
        assertFalse(tracker.userMoved(100, 100));
    }

    // -------------------------------------------------------------------------
    // Regression: our own cursor warps must not count as player movement
    // -------------------------------------------------------------------------

    @Test
    public void commandedWarpIsNotUserMovement() {
        // The virtual cursor / D-pad confirm warps the cursor to (400, 300).
        tracker.command(400, 300);

        // The platform's motion event may land a frame later, and the position we observe in the
        // meantime is still the pre-warp one.
        assertFalse("Warp in flight must be ignored", tracker.userMoved(100, 100));
        assertFalse("Warp landing must be ignored", tracker.userMoved(400, 300));
        assertFalse("Staying at the commanded position is not movement", tracker.userMoved(400, 300));
    }

    @Test
    public void warpRoundingIsTolerated() {
        // setCursorPosition converts framebuffer -> screen space and back, so the observed
        // position can differ from the commanded one by a pixel or two.
        tracker.command(400, 300);
        tracker.userMoved(400, 300);
        assertFalse(tracker.userMoved(401, 299));
        assertFalse(tracker.userMoved(402, 302));
    }

    @Test
    public void userMovementAfterWarpSettlesIsStillDetected() {
        tracker.command(400, 300);
        assertFalse("Warp still in flight", tracker.userMoved(400, 300));
        assertFalse("Settle window", tracker.userMoved(400, 300));
        assertTrue("A real mouse move after the settle window must still deactivate nav", tracker.userMoved(600, 300));
    }

    @Test
    public void warpsToTheCurrentPositionDoNotBlockUserMovement() {
        // The virtual cursor can command the position it already occupies; that must not keep
        // re-arming the settle window, otherwise real mouse movement would never be noticed.
        tracker.userMoved(400, 300);
        tracker.command(400, 300);
        assertFalse(tracker.userMoved(400, 300));
        assertTrue(tracker.userMoved(600, 300));
    }

    @Test
    public void repeatedWarpsAreAllIgnored() {
        // Mirrors the virtual cursor: one commanded warp per rendered frame while the stick is
        // deflected.
        int x = 100;
        for (int frame = 0; frame < 20; frame++) {
            x += 5;
            tracker.command(x, 100);
            assertFalse("Frame " + frame + " warp must not count as movement", tracker.userMoved(x, 100));
        }
    }

    @Test
    public void commandFromAStalePositionDoesNotLeakMovement() {
        tracker.userMoved(100, 100);
        tracker.command(500, 500);
        assertFalse(tracker.userMoved(100, 100)); // settle frames absorb the pre-warp position
        assertFalse(tracker.userMoved(500, 500));
        assertTrue(tracker.userMoved(700, 700));
    }

    @Test
    public void resetForgetsObservedPosition() {
        tracker.userMoved(100, 100);
        assertTrue(tracker.userMoved(140, 100));

        tracker.reset();
        assertFalse("After a reset the next sample cannot report movement", tracker.userMoved(140, 100));
        assertTrue("Real movement is detected again afterwards", tracker.userMoved(200, 100));
    }

    @Test
    public void resetAlsoClearsSettleState() {
        tracker.command(400, 300);
        tracker.reset();
        assertFalse(tracker.userMoved(100, 100));
        assertTrue("A reset must not leave the tracker stuck ignoring input", tracker.userMoved(200, 100));
    }
}
