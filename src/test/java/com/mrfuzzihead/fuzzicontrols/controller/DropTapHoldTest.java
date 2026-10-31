package com.mrfuzzihead.fuzzicontrols.controller;

import static org.junit.Assert.assertEquals;

import org.junit.Before;
import org.junit.Test;

/**
 * Unit tests for {@link DropTapHold}.
 *
 * <p>
 * The drop button has three mutually exclusive outcomes — nothing, one item on a tap, the whole stack
 * on a hold — plus "this press closed a GUI, so drop nothing". Getting the ordering wrong either
 * duplicates drops or throws a stack on the ground when a screen closes, so each path is pinned here.
 */
public class DropTapHoldTest {

    private DropTapHold drop;

    @Before
    public void setUp() {
        drop = new DropTapHold();
    }

    @Test
    public void freshPressDoesNotDropAnything() {
        // The press could still turn out to be a hold, so nothing drops yet.
        assertEquals(DropTapHold.Action.NONE, drop.update(true, true));
    }

    @Test
    public void tapDropsOneItemOnRelease() {
        drop.update(true, true);
        assertEquals(DropTapHold.Action.DROP_ONE, drop.update(false, true));
    }

    @Test
    public void releaseWithoutAnyPressDropsNothing() {
        assertEquals(DropTapHold.Action.NONE, drop.update(false, true));
    }

    @Test
    public void holdDropsTheStackExactlyOnce() {
        drop.update(true, true);
        for (int tick = 1; tick < DropTapHold.HOLD_TICKS - 1; tick++) {
            assertEquals("Tick " + tick + " must not drop yet", DropTapHold.Action.NONE, drop.update(true, true));
        }

        assertEquals(DropTapHold.Action.DROP_STACK, drop.update(true, true));
        // Holding longer must not drop repeatedly.
        for (int tick = 0; tick < 10; tick++) {
            assertEquals(DropTapHold.Action.NONE, drop.update(true, true));
        }
        // And the release after a hold must not drop another single item.
        assertEquals(DropTapHold.Action.NONE, drop.update(false, true));
    }

    @Test
    public void holdDropsOneItemWhenWholeStackIsDisabled() {
        drop.update(true, true);
        for (int tick = 0; tick < DropTapHold.HOLD_TICKS * 2; tick++) {
            assertEquals(DropTapHold.Action.NONE, drop.update(true, false));
        }
        assertEquals(DropTapHold.Action.DROP_ONE, drop.update(false, false));
    }

    @Test
    public void pressThatClosedAGuiDropsNothing() {
        drop.update(true, true);
        drop.blockNextRelease();
        assertEquals(DropTapHold.Action.NONE, drop.update(false, true));
    }

    @Test
    public void pressThatClosedAGuiDoesNotStartHolding() {
        drop.update(true, true);
        drop.blockNextRelease();
        for (int tick = 0; tick < DropTapHold.HOLD_TICKS * 2; tick++) {
            assertEquals("A blocked press must never drop a stack", DropTapHold.Action.NONE, drop.update(true, true));
        }
        assertEquals(DropTapHold.Action.NONE, drop.update(false, true));
    }

    @Test
    public void blockAppliesOnlyToTheCurrentPress() {
        drop.update(true, true);
        drop.blockNextRelease();
        assertEquals(DropTapHold.Action.NONE, drop.update(false, true));

        // The next press is a normal one again.
        drop.update(true, true);
        assertEquals(DropTapHold.Action.DROP_ONE, drop.update(false, true));
    }

    @Test
    public void heldTicksAreCounted() {
        drop.update(true, true);
        assertEquals(1, drop.heldTicks());
        drop.update(true, true);
        assertEquals(2, drop.heldTicks());
        drop.update(false, true);
        assertEquals(0, drop.heldTicks());
    }

    @Test
    public void resetForgetsAPendingHold() {
        drop.update(true, true);
        for (int tick = 0; tick < DropTapHold.HOLD_TICKS; tick++) {
            drop.update(true, true);
        }
        drop.reset();
        // After a reset the release is not treated as a tap of a previous press.
        assertEquals(DropTapHold.Action.NONE, drop.update(false, true));
    }

    @Test
    public void releaseAfterResetDoesNotDrop() {
        // Mirrors input resync (alt-tab, reconnect): a held button must not drop on release.
        drop.update(true, true);
        drop.reset();
        assertEquals(DropTapHold.Action.NONE, drop.update(false, true));
        assertEquals(DropTapHold.Action.NONE, drop.update(false, true));
    }
}
