package com.mrfuzzihead.fuzzicontrols.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.zip.CRC32;

import org.junit.Test;

/**
 * Tests the DualSense USB and Bluetooth report layouts and the Bluetooth CRC.
 *
 * <p>
 * The enhanced Bluetooth vector at the bottom was generated with Python's zlib
 * ({@code crc32(b"\xA1" + report[:-4])}) so the Java CRC implementation is checked against an
 * independent implementation rather than against itself.
 */
public class DualSenseReportParserTest {

    private static final float DEAD_ZONE = 0.15f;

    private static final float TRIGGER_THRESHOLD = 0.2f;

    // -------------------------------------------------------------------------
    // Layout detection
    // -------------------------------------------------------------------------

    @Test
    public void detectsUsbReport() {
        byte[] report = new byte[DualSenseReportParser.USB_REPORT_SIZE];
        report[0] = 0x01;
        assertEquals(DualSenseReportParser.Layout.USB_0X01, DualSenseReportParser.detect(report, report.length));
    }

    @Test
    public void detectsBluetoothSimpleReport() {
        byte[] report = new byte[DualSenseReportParser.BLUETOOTH_SIMPLE_REPORT_SIZE];
        report[0] = 0x01;
        assertEquals(
            DualSenseReportParser.Layout.BLUETOOTH_SIMPLE_0X01,
            DualSenseReportParser.detect(report, report.length));
    }

    @Test
    public void detectsPaddedBluetoothSimpleReport() {
        byte[] report = new byte[DualSenseReportParser.BLUETOOTH_ENHANCED_REPORT_SIZE];
        report[0] = 0x01;
        assertEquals(
            DualSenseReportParser.Layout.BLUETOOTH_SIMPLE_0X01,
            DualSenseReportParser.detect(report, report.length));
    }

    @Test
    public void detectsBluetoothEnhancedReport() {
        byte[] report = enhancedReport();
        assertEquals(
            DualSenseReportParser.Layout.BLUETOOTH_ENHANCED_0X31,
            DualSenseReportParser.detect(report, report.length));
    }

    @Test
    public void rejectsUnknownReports() {
        assertEquals(DualSenseReportParser.Layout.UNKNOWN, DualSenseReportParser.detect(null, 0));
        assertEquals(DualSenseReportParser.Layout.UNKNOWN, DualSenseReportParser.detect(new byte[0], 0));

        byte[] wrongId = new byte[DualSenseReportParser.USB_REPORT_SIZE];
        wrongId[0] = 0x02;
        assertEquals(DualSenseReportParser.Layout.UNKNOWN, DualSenseReportParser.detect(wrongId, wrongId.length));

        byte[] truncatedEnhanced = new byte[20];
        truncatedEnhanced[0] = 0x31;
        assertEquals(
            DualSenseReportParser.Layout.UNKNOWN,
            DualSenseReportParser.detect(truncatedEnhanced, truncatedEnhanced.length));

        byte[] oddUsbLength = new byte[20];
        oddUsbLength[0] = 0x01;
        assertEquals(
            DualSenseReportParser.Layout.UNKNOWN,
            DualSenseReportParser.detect(oddUsbLength, oddUsbLength.length));
    }

    // -------------------------------------------------------------------------
    // Battery status
    // -------------------------------------------------------------------------

    @Test
    public void usbBatteryStatusDecodesCapacityAndCharging() {
        byte[] report = usbReport();
        report[53] = 0x15; // 50-59% band, charging

        DualSenseReportParser.Battery battery = DualSenseReportParser
            .parseBattery(report, report.length, DualSenseReportParser.Layout.USB_0X01);

        assertEquals(55, battery.percent());
        assertTrue(battery.charging());
        assertFalse(battery.full());
    }

    @Test
    public void bluetoothEnhancedBatteryStatusUsesTheShiftedOffset() {
        byte[] report = enhancedReport();
        report[54] = 0x2A; // full, capacity nibble is ignored for full

        DualSenseReportParser.Battery battery = DualSenseReportParser
            .parseBattery(report, report.length, DualSenseReportParser.Layout.BLUETOOTH_ENHANCED_0X31);

        assertEquals(100, battery.percent());
        assertTrue(battery.full());
    }

    @Test
    public void simpleBluetoothReportHasNoBatteryStatus() {
        byte[] report = bluetoothSimpleReport();

        assertEquals(
            null,
            DualSenseReportParser
                .parseBattery(report, report.length, DualSenseReportParser.Layout.BLUETOOTH_SIMPLE_0X01));
    }

    // -------------------------------------------------------------------------
    // USB layout (regression: existing behaviour)
    // -------------------------------------------------------------------------

    @Test
    public void usbReportDecodesAxesButtonsAndTriggers() {
        byte[] report = usbReport();
        ControllerState state = parse(report, DualSenseReportParser.Layout.USB_0X01);

        assertEquals(-1f, state.leftStickX(), 0f);
        assertEquals(0f, state.leftStickY(), 0f);
        assertTrue("RX is nearly full right", state.rightStickX() > 0.99f);
        assertEquals(0f, state.rightStickY(), 0f);
        assertTrue("LT is above the threshold", state.leftTrigger() > 0.7f);
        assertEquals(0f, state.rightTrigger(), 0f);

        assertTrue(state.isPressed(ControllerButton.A));
        assertTrue(state.isPressed(ControllerButton.LEFT_BUMPER));
        assertTrue(state.isPressed(ControllerButton.LEFT_TRIGGER));
        assertFalse(state.isPressed(ControllerButton.DPAD_UP));
        assertFalse(state.isPressed(ControllerButton.DPAD_DOWN));
    }

    @Test
    public void usbDigitalTriggerBitIsAFallbackWhenTheAnalogueValueIsZero() {
        byte[] report = usbReport();
        report[5] = 0; // LT analogue byte reads zero
        report[9] = 0x04; // but the digital L2 bit is set

        ControllerState state = parse(report, DualSenseReportParser.Layout.USB_0X01);

        assertEquals(1f, state.leftTrigger(), 0f);
        assertTrue(state.isPressed(ControllerButton.LEFT_TRIGGER));
    }

    // -------------------------------------------------------------------------
    // Bluetooth simple layout
    // -------------------------------------------------------------------------

    @Test
    public void bluetoothSimpleReportUsesTheUnshiftedStickAndShiftedButtonOffsets() {
        byte[] report = bluetoothSimpleReport();
        ControllerState state = parse(report, DualSenseReportParser.Layout.BLUETOOTH_SIMPLE_0X01);

        assertEquals(-1f, state.leftStickX(), 0f);
        assertEquals(0f, state.leftStickY(), 0f);
        assertTrue("RX is nearly full right", state.rightStickX() > 0.99f);
        assertEquals(0f, state.rightStickY(), 0f);
        assertTrue("LT is read from byte 8", state.leftTrigger() > 0.7f);
        assertEquals(0f, state.rightTrigger(), 0f);

        assertTrue(state.isPressed(ControllerButton.A));
        assertTrue(state.isPressed(ControllerButton.LEFT_BUMPER));
        assertTrue(state.isPressed(ControllerButton.LEFT_TRIGGER));
    }

    @Test
    public void bluetoothSimpleDigitalTriggerBitIsAFallback() {
        byte[] report = bluetoothSimpleReport();
        report[8] = 0; // LT analogue byte
        report[6] = 0x04; // digital L2 bit

        ControllerState state = parse(report, DualSenseReportParser.Layout.BLUETOOTH_SIMPLE_0X01);

        assertEquals(1f, state.leftTrigger(), 0f);
        assertTrue(state.isPressed(ControllerButton.LEFT_TRIGGER));
    }

    // -------------------------------------------------------------------------
    // Bluetooth enhanced layout
    // -------------------------------------------------------------------------

    @Test
    public void bluetoothEnhancedReportUsesShiftedAxisAndButtonOffsets() {
        byte[] report = enhancedReport();
        ControllerState state = parse(report, DualSenseReportParser.Layout.BLUETOOTH_ENHANCED_0X31);

        assertEquals(-1f, state.leftStickX(), 0f);
        assertEquals(0f, state.leftStickY(), 0f);
        assertTrue("RX is nearly full right", state.rightStickX() > 0.99f);
        assertEquals(0f, state.rightStickY(), 0f);
        assertTrue("LT is read from byte 6", state.leftTrigger() > 0.7f);
        assertEquals(0f, state.rightTrigger(), 0f);

        assertTrue(state.isPressed(ControllerButton.A));
        assertTrue(state.isPressed(ControllerButton.LEFT_BUMPER));
        assertTrue(state.isPressed(ControllerButton.LEFT_TRIGGER));
    }

    // -------------------------------------------------------------------------
    // DualSense Edge extras
    // -------------------------------------------------------------------------

    @Test
    public void edgeButtonsAreDecodedFromAllReportLayouts() {
        byte[] usb = usbReport();
        usb[10] = (byte) 0xF0;
        assertEdgeButtons(parse(usb, DualSenseReportParser.Layout.USB_0X01, true));

        byte[] simple = bluetoothSimpleReport();
        simple[7] = (byte) 0xF0;
        assertEdgeButtons(parse(simple, DualSenseReportParser.Layout.BLUETOOTH_SIMPLE_0X01, true));

        byte[] enhanced = enhancedReport();
        enhanced[11] = (byte) 0xF0;
        assertEdgeButtons(parse(enhanced, DualSenseReportParser.Layout.BLUETOOTH_ENHANCED_0X31, true));
    }

    @Test
    public void edgeButtonBitsAreIgnoredOnAStandardDualSense() {
        byte[] usb = usbReport();
        usb[10] = (byte) 0xF0;
        ControllerState state = parse(usb, DualSenseReportParser.Layout.USB_0X01);

        assertFalse(state.isPressed(ControllerButton.LEFT_PADDLE));
        assertFalse(state.isPressed(ControllerButton.RIGHT_PADDLE));
        assertFalse(state.isPressed(ControllerButton.FUNCTION_1));
        assertFalse(state.isPressed(ControllerButton.FUNCTION_2));
    }

    @Test
    public void edgeButtonsAreReleasedWhenTheBitsClear() {
        byte[] usb = usbReport();
        usb[10] = (byte) 0x40;
        ControllerState pressed = parse(usb, DualSenseReportParser.Layout.USB_0X01, true);
        assertTrue(pressed.isPressed(ControllerButton.LEFT_PADDLE));
        assertFalse(pressed.isPressed(ControllerButton.RIGHT_PADDLE));

        usb[10] = 0;
        ControllerState released = parse(usb, DualSenseReportParser.Layout.USB_0X01, true);
        assertFalse(released.isPressed(ControllerButton.LEFT_PADDLE));
    }

    @Test
    public void bluetoothEnhancedReportMatchesReferenceCrcVector() {
        // Generated with Python: zlib.crc32(b"\xA1" + report[:-4]) == 0x78616D27
        byte[] report = enhancedReport();
        report[74] = 0x27;
        report[75] = 0x6D;
        report[76] = 0x61;
        report[77] = 0x78;

        assertTrue(DualSenseReportParser.hasValidBluetoothCrc(report, report.length));

        report[2] ^= 0x01;
        assertFalse(
            "A changed report byte must invalidate the CRC",
            DualSenseReportParser.hasValidBluetoothCrc(report, report.length));
    }

    @Test
    public void crcRejectsWrongLengths() {
        byte[] report = new byte[DualSenseReportParser.BLUETOOTH_SIMPLE_REPORT_SIZE];
        report[0] = 0x01;
        assertFalse(DualSenseReportParser.hasValidBluetoothCrc(report, report.length));
        assertFalse(DualSenseReportParser.hasValidBluetoothCrc(null, 0));
    }

    // -------------------------------------------------------------------------
    // Malformed input
    // -------------------------------------------------------------------------

    @Test
    public void malformedReportsDecodeToEmpty() {
        assertEquals(
            ControllerState.empty(),
            DualSenseReportParser.parse(null, 0, DualSenseReportParser.Layout.USB_0X01, DEAD_ZONE, TRIGGER_THRESHOLD));
        assertEquals(
            ControllerState.empty(),
            DualSenseReportParser
                .parse(new byte[5], 5, DualSenseReportParser.Layout.USB_0X01, DEAD_ZONE, TRIGGER_THRESHOLD));
        assertEquals(
            ControllerState.empty(),
            DualSenseReportParser.parse(
                new byte[10],
                10,
                DualSenseReportParser.Layout.BLUETOOTH_ENHANCED_0X31,
                DEAD_ZONE,
                TRIGGER_THRESHOLD));
        assertEquals(
            ControllerState.empty(),
            DualSenseReportParser
                .parse(new byte[64], 64, DualSenseReportParser.Layout.UNKNOWN, DEAD_ZONE, TRIGGER_THRESHOLD));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static ControllerState parse(byte[] report, DualSenseReportParser.Layout layout) {
        return parse(report, layout, false);
    }

    private static ControllerState parse(byte[] report, DualSenseReportParser.Layout layout, boolean edge) {
        return DualSenseReportParser.parse(report, report.length, layout, DEAD_ZONE, TRIGGER_THRESHOLD, edge);
    }

    private static void assertEdgeButtons(ControllerState state) {
        assertTrue(state.isPressed(ControllerButton.LEFT_PADDLE));
        assertTrue(state.isPressed(ControllerButton.RIGHT_PADDLE));
        assertTrue(state.isPressed(ControllerButton.FUNCTION_1));
        assertTrue(state.isPressed(ControllerButton.FUNCTION_2));
    }

    /** USB: LX=0, LY=128, RX=255, RY=128, LT=200, RT=0, A + neutral hat, LB. */
    private static byte[] usbReport() {
        byte[] report = new byte[DualSenseReportParser.USB_REPORT_SIZE];
        report[0] = 0x01;
        report[1] = 0;
        report[2] = (byte) 128;
        report[3] = (byte) 255;
        report[4] = (byte) 128;
        report[5] = (byte) 200;
        report[6] = 0;
        report[8] = 0x28; // neutral D-pad hat (8) + Cross/A (0x20)
        report[9] = 0x01; // LB
        return report;
    }

    /** Bluetooth simple: same values as {@link #usbReport()} with the shifted button/trigger bytes. */
    private static byte[] bluetoothSimpleReport() {
        byte[] report = new byte[DualSenseReportParser.BLUETOOTH_SIMPLE_REPORT_SIZE];
        report[0] = 0x01;
        report[1] = 0;
        report[2] = (byte) 128;
        report[3] = (byte) 255;
        report[4] = (byte) 128;
        report[5] = 0x28; // neutral D-pad hat (8) + Cross/A (0x20)
        report[6] = 0x01; // LB
        report[7] = 0;
        report[8] = (byte) 200; // LT
        report[9] = 0; // RT
        return report;
    }

    /** Bluetooth enhanced: same values as {@link #usbReport()} with a valid CRC trailer. */
    private static byte[] enhancedReport() {
        byte[] report = new byte[DualSenseReportParser.BLUETOOTH_ENHANCED_REPORT_SIZE];
        report[0] = 0x31;
        report[1] = 0x02;
        report[2] = 0;
        report[3] = (byte) 128;
        report[4] = (byte) 255;
        report[5] = (byte) 128;
        report[6] = (byte) 200; // LT
        report[7] = 0; // RT
        report[9] = 0x28; // neutral D-pad hat (8) + Cross/A (0x20)
        report[10] = 0x01; // LB

        CRC32 crc = new CRC32();
        crc.update(DualSenseReportParser.BLUETOOTH_INPUT_CRC_SEED);
        crc.update(report, 0, report.length - 4);
        long value = crc.getValue();
        report[74] = (byte) value;
        report[75] = (byte) (value >>> 8);
        report[76] = (byte) (value >>> 16);
        report[77] = (byte) (value >>> 24);
        return report;
    }
}
