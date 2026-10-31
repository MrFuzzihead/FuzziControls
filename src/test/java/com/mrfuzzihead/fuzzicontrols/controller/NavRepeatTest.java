package com.mrfuzzihead.fuzzicontrols.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

/**
 * Unit tests for {@link NavRepeat}.
 *
 * <p>
 * Holding a D-pad direction must move focus once immediately, stay still for a short delay, then repeat
 * at a steady rate — otherwise crossing a long list means pressing the D-pad once per entry.
 */
public class NavRepeatTest {

    private static final ControllerAction DIRECTION = ControllerAction.GUI_NAV_DOWN;

    private NavRepeat repeat;

    @Before
    public void setUp() {
        repeat = new NavRepeat();
    }

    /** Feeds {@code ticks} consecutive held ticks and returns the tick numbers that fired. */
    private List<Integer> hold(int ticks) {
        List<Integer> fired = new ArrayList<>();
        for (int tick = 1; tick <= ticks; tick++) {
            if (repeat.update(DIRECTION, true)) fired.add(tick);
        }
        return fired;
    }

    @Test
    public void pressFiresImmediately() {
        assertTrue(repeat.update(DIRECTION, true));
    }

    @Test
    public void nothingFiresDuringTheInitialDelay() {
        repeat.update(DIRECTION, true);
        for (int tick = 2; tick < NavRepeat.INITIAL_DELAY_TICKS; tick++) {
            assertFalse("Tick " + tick + " is inside the initial delay", repeat.update(DIRECTION, true));
        }
    }

    @Test
    public void repeatsStartAtTheDelayAndThenFollowTheInterval() {
        List<Integer> fired = hold(2 * NavRepeat.INITIAL_DELAY_TICKS);
        // First press, then the delay boundary, then every interval.
        assertEquals(1, (int) fired.get(0));
        assertEquals(NavRepeat.INITIAL_DELAY_TICKS, (int) fired.get(1));
        int expected = 1 + 1
            + (2 * NavRepeat.INITIAL_DELAY_TICKS - NavRepeat.INITIAL_DELAY_TICKS) / NavRepeat.REPEAT_INTERVAL_TICKS;
        assertEquals(expected, fired.size());

        for (int index = 2; index < fired.size(); index++) {
            assertEquals(
                "Repeats must be evenly spaced",
                NavRepeat.REPEAT_INTERVAL_TICKS,
                fired.get(index) - fired.get(index - 1));
        }
    }

    @Test
    public void releaseStopsRepeatingAndTheNextPressFiresAgain() {
        hold(NavRepeat.INITIAL_DELAY_TICKS + 1);
        assertFalse("Released tick does not fire", repeat.update(DIRECTION, false));
        assertEquals(0, repeat.heldTicks(DIRECTION));
        assertTrue("A fresh press fires immediately", repeat.update(DIRECTION, true));
    }

    @Test
    public void directionsRepeatIndependently() {
        assertTrue(repeat.update(ControllerAction.GUI_NAV_UP, true));
        for (int tick = 1; tick < NavRepeat.INITIAL_DELAY_TICKS; tick++) {
            repeat.update(ControllerAction.GUI_NAV_UP, true);
        }
        // Down has not been held, so its first press still fires immediately.
        assertTrue(repeat.update(ControllerAction.GUI_NAV_DOWN, true));
        assertFalse(repeat.update(ControllerAction.GUI_NAV_DOWN, false));
        assertTrue(repeat.update(ControllerAction.GUI_NAV_DOWN, true));
    }

    @Test
    public void tappingNeverRepeats() {
        for (int tap = 0; tap < 5; tap++) {
            assertTrue("Every tap fires once", repeat.update(DIRECTION, true));
            assertFalse(repeat.update(DIRECTION, false));
        }
    }

    @Test
    public void clearResetsHeldState() {
        hold(NavRepeat.INITIAL_DELAY_TICKS);
        repeat.clear();
        assertEquals(0, repeat.heldTicks(DIRECTION));
        assertTrue("After a clear the direction is treated as a fresh press", repeat.update(DIRECTION, true));
    }

    @Test
    public void clearRestartsTheDelayWindow() {
        // One tick away from the first repeat, then cleared (e.g. navigation deactivated).
        hold(NavRepeat.INITIAL_DELAY_TICKS - 1);
        repeat.clear();

        // A cleared direction is a fresh press: it fires once, then waits out a whole new delay.
        assertTrue(repeat.update(DIRECTION, true));
        for (int tick = 2; tick < NavRepeat.INITIAL_DELAY_TICKS; tick++) {
            assertFalse("Tick " + tick + " must be inside the new delay", repeat.update(DIRECTION, true));
        }
        assertTrue("The new delay ends at the usual boundary", repeat.update(DIRECTION, true));
    }

    @Test
    public void repeatRateIsBoundedAndSane() {
        List<Integer> fired = hold(20);
        // 20 ticks = 1 s: one press plus repeats after a 400 ms delay at 100 ms intervals.
        assertEquals(8, fired.size());
        assertTrue("The delay must be long enough not to fight a deliberate tap", NavRepeat.INITIAL_DELAY_TICKS >= 6);
        assertTrue(
            "Repeats must be fast enough to cross a list, but not a blur",
            NavRepeat.REPEAT_INTERVAL_TICKS >= 1 && NavRepeat.REPEAT_INTERVAL_TICKS <= 4);
    }
}
