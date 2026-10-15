package com.mrfuzzihead.fuzzicontrols.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.Collections;

import org.junit.Test;

/**
 * Tests the DualSense driver's non-blocking read handling and HID report decoding.
 *
 * <p>
 * These exercise the real production code — {@link LastReportCache} (used by
 * {@link DualSenseDriver#poll}) and {@link DualSenseDriver#parseReport} — rather than a stub that
 * duplicated the logic, so a regression in either is caught here.
 */
public class DualSenseDriverCacheTest {

    private static final float DEAD_ZONE = 0.15f;

    private static final float TRIGGER_THRESHOLD = 0.2f;

    /** Builds a report: axes at centre, triggers released, no buttons, D-pad neutral. */
    private static byte[] neutralReport() {
        byte[] data = new byte[64];
        data[1] = (byte) 128; // LX
        data[2] = (byte) 128; // LY
        data[3] = (byte) 128; // RX
        data[4] = (byte) 128; // RY
        data[5] = 0; // LT
        data[6] = 0; // RT
        data[8] = 0x08; // buttons 1 (no face buttons) + D-pad hat = 8 (neutral)
        data[9] = 0; // buttons 2
        return data;
    }

    private static ControllerState parse(byte[] data) {
        return DualSenseDriver.parseReport(data, DEAD_ZONE, TRIGGER_THRESHOLD);
    }

    // -------------------------------------------------------------------------
    // LastReportCache contract
    // -------------------------------------------------------------------------

    @Test
    public void firstPollWithoutReportReturnsEmpty() {
        LastReportCache cache = new LastReportCache();
        assertEquals(ControllerState.empty(), cache.record(null));
    }

    @Test
    public void validReportIsCachedAndReturned() {
        LastReportCache cache = new LastReportCache();
        ControllerState expected = new ControllerState(0.5f, -0.3f, 0f, 0f, 0f, 0f, Collections.emptySet());
        assertSame("A parsed report is returned as-is", expected, cache.record(expected));
    }

    @Test
    public void pollWithoutReportReturnsTheCachedState() {
        // A 250 Hz device polled at 60 Hz returns no report most of the time; returning empty()
        // here would drop every button press and axis movement between reports.
        LastReportCache cache = new LastReportCache();
        ControllerState expected = new ControllerState(0.5f, -0.3f, 0f, 0f, 0f, 0f, Collections.emptySet());
        cache.record(expected);

        for (int poll = 0; poll < 10; poll++) {
            assertSame("A poll with no report must return the cached state", expected, cache.record(null));
        }
    }

    @Test
    public void newerReportReplacesTheCachedState() {
        LastReportCache cache = new LastReportCache();
        ControllerState first = new ControllerState(0.5f, 0f, 0f, 0f, 0f, 0f, Collections.emptySet());
        ControllerState second = new ControllerState(-0.5f, 0f, 0f, 0f, 0f, 0f, Collections.emptySet());

        cache.record(first);
        assertSame(second, cache.record(second));
        assertSame(second, cache.current());
    }

    @Test
    public void clearDropsTheCachedState() {
        LastReportCache cache = new LastReportCache();
        cache.record(new ControllerState(0.5f, 0f, 0f, 0f, 0f, 0f, Collections.emptySet()));

        cache.clear();
        assertEquals(ControllerState.empty(), cache.current());
    }

    // -------------------------------------------------------------------------
    // HID report decoding
    // -------------------------------------------------------------------------

    @Test
    public void centredSticksAndReleasedTriggersDecodeToZero() {
        ControllerState state = parse(neutralReport());
        assertEquals(0f, state.leftStickX(), 0f);
        assertEquals(0f, state.leftStickY(), 0f);
        assertEquals(0f, state.rightStickX(), 0f);
        assertEquals(0f, state.rightStickY(), 0f);
        assertEquals(0f, state.leftTrigger(), 0f);
        assertEquals(0f, state.rightTrigger(), 0f);
        assertTrue(
            state.pressedButtons()
                .isEmpty());
    }

    @Test
    public void stickDeflectionIsScaledAndClamped() {
        byte[] data = neutralReport();
        data[1] = (byte) 0; // LX fully left
        data[2] = (byte) 255; // LY fully down
        ControllerState state = parse(data);

        assertEquals("Full deflection clamps to -1", -1f, state.leftStickX(), 0f);
        // byteToAxis() maps 0..255 onto -1.004..0.996 (centre 128, divisor 127.5), so the positive
        // extreme is a fraction short of +1. It must stay inside the documented [-1, 1] range.
        assertTrue("Full deflection stays within +1", state.leftStickY() <= 1f);
        assertTrue("Full deflection is effectively full", state.leftStickY() > 0.99f);
    }

    @Test
    public void valuesInsideTheDeadZoneDecodeToZero() {
        byte[] data = neutralReport();
        data[1] = (byte) (128 + 10); // LX just outside centre, inside the dead zone
        ControllerState state = parse(data);
        assertEquals(0f, state.leftStickX(), 0f);
    }

    @Test
    public void triggerBelowThresholdIsReleased() {
        byte[] data = neutralReport();
        data[5] = (byte) 40; // ~0.157, below the 0.2 threshold
        data[6] = (byte) 255;
        ControllerState state = parse(data);
        assertEquals(0f, state.leftTrigger(), 0f);
        assertEquals(1f, state.rightTrigger(), 0f);
        assertFalse("A released trigger is not a pressed button", state.isPressed(ControllerButton.LEFT_TRIGGER));
        assertTrue(state.isPressed(ControllerButton.RIGHT_TRIGGER));
    }

    @Test
    public void faceButtonsDecodeFromTheHighNibble() {
        byte[] data = neutralReport();
        data[8] = (byte) (0x08 | 0x20 | 0x80); // neutral hat + A + Y
        ControllerState state = parse(data);

        assertTrue(state.isPressed(ControllerButton.A));
        assertTrue(state.isPressed(ControllerButton.Y));
        assertFalse(state.isPressed(ControllerButton.B));
        assertFalse(state.isPressed(ControllerButton.X));
    }

    @Test
    public void secondButtonByteDecodesShouldersSticksAndMenuButtons() {
        byte[] data = neutralReport();
        data[9] = (byte) (0x01 | 0x02 | 0x10 | 0x20 | 0x40 | 0x80);
        ControllerState state = parse(data);

        assertTrue(state.isPressed(ControllerButton.LEFT_BUMPER));
        assertTrue(state.isPressed(ControllerButton.RIGHT_BUMPER));
        assertTrue(state.isPressed(ControllerButton.BACK));
        assertTrue(state.isPressed(ControllerButton.START));
        assertTrue(state.isPressed(ControllerButton.LEFT_STICK_CLICK));
        assertTrue(state.isPressed(ControllerButton.RIGHT_STICK_CLICK));
    }

    @Test
    public void dpadHatMasksDecodeToTheExpectedDirections() {
        // DualSense encodes the D-pad as a hat: 0=N, 1=NE, 2=E, 3=SE, 4=S, 5=SW, 6=W, 7=NW.
        assertDpad(0, true, false, false, false);
        assertDpad(1, true, true, false, false);
        assertDpad(2, false, true, false, false);
        assertDpad(3, false, true, true, false);
        assertDpad(4, false, false, true, false);
        assertDpad(5, false, false, true, true);
        assertDpad(6, false, false, false, true);
        assertDpad(7, true, false, false, true);
    }

    @Test
    public void neutralDpadHatPressesNothing() {
        byte[] data = neutralReport();
        data[8] = 0x08; // hat value 8 = released
        ControllerState state = parse(data);

        assertFalse(state.isPressed(ControllerButton.DPAD_UP));
        assertFalse(state.isPressed(ControllerButton.DPAD_DOWN));
        assertFalse(state.isPressed(ControllerButton.DPAD_LEFT));
        assertFalse(state.isPressed(ControllerButton.DPAD_RIGHT));
    }

    private static void assertDpad(int hat, boolean up, boolean right, boolean down, boolean left) {
        byte[] data = neutralReport();
        // The hat value occupies the low nibble: 0..7 are directions, 8 is neutral.
        data[8] = (byte) hat;
        ControllerState state = parse(data);

        assertEquals("hat " + hat + " up", up, state.isPressed(ControllerButton.DPAD_UP));
        assertEquals("hat " + hat + " right", right, state.isPressed(ControllerButton.DPAD_RIGHT));
        assertEquals("hat " + hat + " down", down, state.isPressed(ControllerButton.DPAD_DOWN));
        assertEquals("hat " + hat + " left", left, state.isPressed(ControllerButton.DPAD_LEFT));
    }

    @Test
    public void emptyStateHasZeroAxesAndNoButtons() {
        ControllerState empty = ControllerState.empty();
        assertEquals(0f, empty.leftStickX(), 0f);
        assertEquals(0f, empty.leftStickY(), 0f);
        assertEquals(0f, empty.rightStickX(), 0f);
        assertEquals(0f, empty.rightStickY(), 0f);
        assertEquals(0f, empty.leftTrigger(), 0f);
        assertEquals(0f, empty.rightTrigger(), 0f);
        assertTrue(
            "Empty state should have no pressed buttons",
            empty.pressedButtons()
                .isEmpty());
    }
}
