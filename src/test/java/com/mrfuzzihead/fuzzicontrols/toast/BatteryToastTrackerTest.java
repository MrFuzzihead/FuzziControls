package com.mrfuzzihead.fuzzicontrols.toast;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Tests the one-shot low-battery notification policy. */
public class BatteryToastTrackerTest {

    @Test
    public void firesOnceWhenCrossingTheThreshold() {
        BatteryToastTracker tracker = new BatteryToastTracker();

        assertFalse(tracker.update(40, false, 15));
        assertTrue(tracker.update(15, false, 15));
        assertFalse("must not repeat while still low", tracker.update(14, false, 15));
        assertFalse(tracker.update(10, false, 15));
    }

    @Test
    public void resetsAfterChargingComfortablyAboveTheThreshold() {
        BatteryToastTracker tracker = new BatteryToastTracker();

        assertTrue(tracker.update(10, false, 15));
        assertFalse(tracker.update(10, true, 15));
        assertFalse(tracker.update(30, false, 15));
        assertTrue("a second real low-battery crossing should notify", tracker.update(15, false, 15));
    }

    @Test
    public void disconnectResetsTheOneShot() {
        BatteryToastTracker tracker = new BatteryToastTracker();

        assertTrue(tracker.update(10, false, 15));
        assertFalse(tracker.update(-1, false, 15));
        assertTrue(tracker.update(10, false, 15));
    }

    @Test
    public void chargingAtLowBatteryDoesNotNotify() {
        BatteryToastTracker tracker = new BatteryToastTracker();

        assertFalse(tracker.update(5, true, 15));
        assertTrue(tracker.update(5, false, 15));
    }
}
