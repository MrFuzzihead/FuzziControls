package com.mrfuzzihead.fuzzicontrols.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Pins the DualSense output-report envelope, motor offsets and Bluetooth CRC.
 *
 * <p>
 * These offsets are easy to transpose (motor right precedes motor left in the common payload) and
 * a wrong CRC makes the controller silently ignore every rumble command over Bluetooth.
 */
public class DualSenseOutputReportTest {

    @Test
    public void usbRumbleUsesTheUsbEnvelope() {
        byte[] report = DualSenseOutputReport.rumbleUsb(200, 100);

        assertEquals(DualSenseOutputReport.USB_REPORT_SIZE, report.length);
        assertEquals(0x02, report[0] & 0xFF);
        assertEquals(
            "Haptics select + compatible vibration",
            DualSenseOutputReport.VALID_FLAG0_HAPTICS_SELECT | DualSenseOutputReport.VALID_FLAG0_COMPATIBLE_VIBRATION,
            report[1] & 0xFF);
        assertEquals("Motor right is before motor left", 100, report[3] & 0xFF);
        assertEquals(200, report[4] & 0xFF);
    }

    @Test
    public void bluetoothRumbleUsesTheBluetoothEnvelopeAndValidCrc() {
        byte[] report = DualSenseOutputReport.rumbleBluetooth(7, 200, 100);

        assertEquals(DualSenseOutputReport.BLUETOOTH_REPORT_SIZE, report.length);
        assertEquals(0x31, report[0] & 0xFF);
        assertEquals("Sequence number is the high nibble", 0x70, report[1] & 0xFF);
        assertEquals(0x10, report[2] & 0xFF);
        assertEquals(
            DualSenseOutputReport.VALID_FLAG0_HAPTICS_SELECT | DualSenseOutputReport.VALID_FLAG0_COMPATIBLE_VIBRATION,
            report[3] & 0xFF);
        assertEquals("Motor right is before motor left", 100, report[5] & 0xFF);
        assertEquals(200, report[6] & 0xFF);
        assertTrue(DualSenseOutputReport.hasValidBluetoothCrc(report));
    }

    @Test
    public void bluetoothCrcDetectsAChangedPayload() {
        byte[] report = DualSenseOutputReport.rumbleBluetooth(0, 10, 20);
        report[5] ^= 0x01;

        assertFalse(DualSenseOutputReport.hasValidBluetoothCrc(report));
    }

    @Test
    public void sequenceWrapsToFourBits() {
        byte[] report = DualSenseOutputReport.rumbleBluetooth(0x1F, 0, 0);

        assertEquals("Only the low four bits are stored", 0xF0, report[1] & 0xFF);
    }

    @Test
    public void usbTriggerReportSetsOnlyTheTriggerFlagsAndBothBlocks() {
        byte[] left = DualSenseTriggerEffect.bow(1, 6, 6, 8);
        byte[] right = DualSenseTriggerEffect.feedback(2, 5);

        byte[] report = DualSenseOutputReport.triggersUsb(left, right);

        assertEquals(DualSenseOutputReport.USB_REPORT_SIZE, report.length);
        assertEquals(0x02, report[0] & 0xFF);
        assertEquals(
            DualSenseOutputReport.VALID_FLAG0_ALLOW_LEFT_TRIGGER_FFB
                | DualSenseOutputReport.VALID_FLAG0_ALLOW_RIGHT_TRIGGER_FFB,
            report[1] & 0xFF);
        assertEquals(0x22, report[1 + DualSenseOutputReport.L2_TRIGGER_OFFSET] & 0xFF);
        assertEquals(0x21, report[1 + DualSenseOutputReport.R2_TRIGGER_OFFSET] & 0xFF);
    }

    @Test
    public void bluetoothTriggerReportIsSigned() {
        byte[] report = DualSenseOutputReport
            .triggersBluetooth(3, DualSenseTriggerEffect.bow(1, 6, 6, 8), DualSenseTriggerEffect.feedback(2, 5));

        assertEquals(0x31, report[0] & 0xFF);
        assertEquals(0x30, report[1] & 0xFF);
        assertEquals(
            DualSenseOutputReport.VALID_FLAG0_ALLOW_LEFT_TRIGGER_FFB
                | DualSenseOutputReport.VALID_FLAG0_ALLOW_RIGHT_TRIGGER_FFB,
            report[3] & 0xFF);
        assertTrue(DualSenseOutputReport.hasValidBluetoothCrc(report));
    }

    @Test
    public void usbLightbarReportSetsColorAndBrightnessFlags() {
        byte[] report = DualSenseOutputReport.lightbarUsb(10, 20, 30, 2);

        assertEquals(DualSenseOutputReport.VALID_FLAG1_LIGHTBAR_CONTROL_ENABLE, report[2] & 0xFF);
        assertEquals(DualSenseOutputReport.VALID_FLAG2_BRIGHTNESS_CONTROL_ENABLE, report[39] & 0xFF);
        assertEquals(2, report[43] & 0xFF);
        assertEquals(10, report[45] & 0xFF);
        assertEquals(20, report[46] & 0xFF);
        assertEquals(30, report[47] & 0xFF);
    }

    @Test
    public void bluetoothLightbarReportIsSigned() {
        byte[] report = DualSenseOutputReport.lightbarBluetooth(1, 10, 20, 30, 0);

        assertEquals(0x31, report[0] & 0xFF);
        assertEquals(DualSenseOutputReport.VALID_FLAG1_LIGHTBAR_CONTROL_ENABLE, report[4] & 0xFF);
        assertEquals(DualSenseOutputReport.VALID_FLAG2_BRIGHTNESS_CONTROL_ENABLE, report[41] & 0xFF);
        assertTrue(DualSenseOutputReport.hasValidBluetoothCrc(report));
    }

    @Test
    public void motorValuesAreClamped() {
        byte[] report = DualSenseOutputReport.rumbleUsb(-1, 999);

        assertEquals(0, report[4] & 0xFF);
        assertEquals(255, report[3] & 0xFF);
    }
}
