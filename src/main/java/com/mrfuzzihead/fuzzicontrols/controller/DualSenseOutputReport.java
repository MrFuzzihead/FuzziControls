package com.mrfuzzihead.fuzzicontrols.controller;

import java.util.zip.CRC32;

/**
 * Builds DualSense output reports.
 *
 * <p>
 * The controller uses two different envelopes for the same 47-byte common payload:
 *
 * <table border="1">
 * <caption>DualSense output reports</caption>
 * <tr>
 * <th>Transport</th>
 * <th>Report ID</th>
 * <th>Size</th>
 * <th>Common payload starts at</th>
 * </tr>
 * <tr>
 * <td>USB</td>
 * <td>{@code 0x02}</td>
 * <td>63</td>
 * <td>1</td>
 * </tr>
 * <tr>
 * <td>Bluetooth</td>
 * <td>{@code 0x31}</td>
 * <td>78</td>
 * <td>3</td>
 * </tr>
 * </table>
 *
 * <p>
 * Bluetooth reports carry a 4-bit sequence number in the high nibble of byte 1 and a required
 * {@code 0x10} tag in byte 2. The last four bytes are a little-endian CRC32 over the whole report
 * (including the report ID), seeded with {@code 0xA2}. This matches Linux's
 * {@code hid-playstation} implementation and is independent of the input-report CRC seed
 * ({@code 0xA1}) handled by {@link DualSenseReportParser}.
 *
 * <p>
 * Rumble uses the classic compatibility mode, which every DualSense firmware supports. The newer
 * "vibration 2" flag is deliberately not used: it requires querying the controller firmware
 * version, and the classic mode still works on current firmware.
 */
final class DualSenseOutputReport {

    static final int USB_REPORT_ID = 0x02;
    static final int BLUETOOTH_REPORT_ID = 0x31;

    static final int STANDARD_USB_REPORT_SIZE = 48;
    static final int EDGE_USB_REPORT_SIZE = 64;
    static final int USB_REPORT_SIZE = STANDARD_USB_REPORT_SIZE;
    static final int BLUETOOTH_REPORT_SIZE = 78;

    static final int USB_COMMON_OFFSET = 1;
    static final int BLUETOOTH_COMMON_OFFSET = 3;

    static final int VALID_FLAG0_OFFSET = 0;
    static final int VALID_FLAG1_OFFSET = 1;
    static final int MOTOR_RIGHT_OFFSET = 2;
    static final int MOTOR_LEFT_OFFSET = 3;
    static final int R2_TRIGGER_OFFSET = 10;
    static final int L2_TRIGGER_OFFSET = 21;
    static final int VALID_FLAG2_OFFSET = 38;
    static final int LIGHTBAR_BRIGHTNESS_OFFSET = 42;
    static final int LIGHTBAR_RED_OFFSET = 44;
    static final int LIGHTBAR_GREEN_OFFSET = 45;
    static final int LIGHTBAR_BLUE_OFFSET = 46;

    static final int VALID_FLAG0_HAPTICS_SELECT = 0x02;
    static final int VALID_FLAG0_COMPATIBLE_VIBRATION = 0x01;
    static final int VALID_FLAG0_ALLOW_RIGHT_TRIGGER_FFB = 0x04;
    static final int VALID_FLAG0_ALLOW_LEFT_TRIGGER_FFB = 0x08;

    static final int VALID_FLAG1_LIGHTBAR_CONTROL_ENABLE = 0x04;
    static final int VALID_FLAG2_BRIGHTNESS_CONTROL_ENABLE = 0x01;

    static final int BLUETOOTH_TAG = 0x10;
    static final int BLUETOOTH_OUTPUT_CRC_SEED = 0xA2;

    private static final int CRC_SIZE = 4;

    private DualSenseOutputReport() {}

    /**
     * Builds a USB rumble output report.
     *
     * @param left  low-frequency / strong motor, 0–255
     * @param right high-frequency / weak motor, 0–255
     * @return complete 63-byte report including the report ID
     */
    static byte[] rumbleUsb(int left, int right) {
        return rumbleUsb(left, right, STANDARD_USB_REPORT_SIZE);
    }

    /** Builds a USB rumble report for the model's declared output-report size. */
    static byte[] rumbleUsb(int left, int right, int reportSize) {
        byte[] report = usbReport(reportSize);
        writeRumble(report, USB_COMMON_OFFSET, left, right);
        return report;
    }

    /**
     * Builds a Bluetooth rumble output report and signs it with the output CRC.
     *
     * @param sequence sequence number, 0–15; the low four bits are used
     * @param left     low-frequency / strong motor, 0–255
     * @param right    high-frequency / weak motor, 0–255
     * @return complete 78-byte report including the report ID and CRC
     */
    static byte[] rumbleBluetooth(int sequence, int left, int right) {
        byte[] report = bluetoothReport(sequence);
        writeRumble(report, BLUETOOTH_COMMON_OFFSET, left, right);
        writeBluetoothCrc(report);
        return report;
    }

    /**
     * Builds a USB report that updates one adaptive-trigger effect. Only that trigger's valid
     * flag is set, so the other trigger, rumble and lighting are retained by the controller.
     *
     * @param trigger trigger to update
     * @param block   effect block, 11 bytes
     * @return complete 63-byte report including the report ID
     */
    static byte[] triggerUsb(ControllerTrigger trigger, byte[] block) {
        return triggerUsb(trigger, block, STANDARD_USB_REPORT_SIZE);
    }

    /** Builds a USB trigger report for the model's declared output-report size. */
    static byte[] triggerUsb(ControllerTrigger trigger, byte[] block, int reportSize) {
        byte[] report = usbReport(reportSize);
        writeTrigger(report, USB_COMMON_OFFSET, trigger, block);
        return report;
    }

    /**
     * Builds a Bluetooth report that updates one adaptive-trigger effect.
     *
     * @param sequence sequence number, 0–15; the low four bits are used
     * @param trigger  trigger to update
     * @param block    effect block, 11 bytes
     * @return complete 78-byte report including the report ID and CRC
     */
    static byte[] triggerBluetooth(int sequence, ControllerTrigger trigger, byte[] block) {
        byte[] report = bluetoothReport(sequence);
        writeTrigger(report, BLUETOOTH_COMMON_OFFSET, trigger, block);
        writeBluetoothCrc(report);
        return report;
    }

    /** Builds a USB lightbar/brightness report. */
    static byte[] lightbarUsb(int red, int green, int blue, int brightness) {
        return lightbarUsb(red, green, blue, brightness, STANDARD_USB_REPORT_SIZE);
    }

    /** Builds a USB lightbar report for the model's declared output-report size. */
    static byte[] lightbarUsb(int red, int green, int blue, int brightness, int reportSize) {
        byte[] report = usbReport(reportSize);
        writeLightbar(report, USB_COMMON_OFFSET, red, green, blue, brightness);
        return report;
    }

    /** Builds a Bluetooth lightbar/brightness report. */
    static byte[] lightbarBluetooth(int sequence, int red, int green, int blue, int brightness) {
        byte[] report = bluetoothReport(sequence);
        writeLightbar(report, BLUETOOTH_COMMON_OFFSET, red, green, blue, brightness);
        writeBluetoothCrc(report);
        return report;
    }

    /**
     * Verifies the CRC of a Bluetooth output report. Exposed for tests and diagnostics.
     *
     * @param report complete report including the report ID
     * @return true when the report is the expected size and its CRC matches
     */
    static boolean hasValidBluetoothCrc(byte[] report) {
        if (report == null || report.length != BLUETOOTH_REPORT_SIZE) return false;

        CRC32 crc = new CRC32();
        crc.update(BLUETOOTH_OUTPUT_CRC_SEED);
        crc.update(report, 0, report.length - CRC_SIZE);

        long expected = (report[report.length - 4] & 0xFFL) | ((report[report.length - 3] & 0xFFL) << 8)
            | ((report[report.length - 2] & 0xFFL) << 16)
            | ((report[report.length - 1] & 0xFFL) << 24);
        return crc.getValue() == expected;
    }

    private static byte[] usbReport(int reportSize) {
        byte[] report = new byte[Math.max(reportSize, STANDARD_USB_REPORT_SIZE)];
        report[0] = (byte) USB_REPORT_ID;
        return report;
    }

    private static byte[] bluetoothReport(int sequence) {
        byte[] report = new byte[BLUETOOTH_REPORT_SIZE];
        report[0] = (byte) BLUETOOTH_REPORT_ID;
        report[1] = (byte) ((sequence & 0x0F) << 4);
        report[2] = (byte) BLUETOOTH_TAG;
        return report;
    }

    private static void writeRumble(byte[] report, int commonOffset, int left, int right) {
        report[commonOffset
            + VALID_FLAG0_OFFSET] = (byte) (VALID_FLAG0_HAPTICS_SELECT | VALID_FLAG0_COMPATIBLE_VIBRATION);
        report[commonOffset + MOTOR_RIGHT_OFFSET] = (byte) clampMotor(right);
        report[commonOffset + MOTOR_LEFT_OFFSET] = (byte) clampMotor(left);
    }

    private static void writeTrigger(byte[] report, int commonOffset, ControllerTrigger trigger, byte[] block) {
        if (trigger == ControllerTrigger.LEFT) {
            report[commonOffset + VALID_FLAG0_OFFSET] = (byte) VALID_FLAG0_ALLOW_LEFT_TRIGGER_FFB;
            copyBlock(report, commonOffset + L2_TRIGGER_OFFSET, block);
        } else if (trigger == ControllerTrigger.RIGHT) {
            report[commonOffset + VALID_FLAG0_OFFSET] = (byte) VALID_FLAG0_ALLOW_RIGHT_TRIGGER_FFB;
            copyBlock(report, commonOffset + R2_TRIGGER_OFFSET, block);
        }
    }

    private static void writeLightbar(byte[] report, int commonOffset, int red, int green, int blue, int brightness) {
        report[commonOffset + VALID_FLAG1_OFFSET] |= (byte) VALID_FLAG1_LIGHTBAR_CONTROL_ENABLE;
        report[commonOffset + VALID_FLAG2_OFFSET] |= (byte) VALID_FLAG2_BRIGHTNESS_CONTROL_ENABLE;
        report[commonOffset + LIGHTBAR_BRIGHTNESS_OFFSET] = (byte) Math.max(0, Math.min(2, brightness));
        report[commonOffset + LIGHTBAR_RED_OFFSET] = (byte) clampMotor(red);
        report[commonOffset + LIGHTBAR_GREEN_OFFSET] = (byte) clampMotor(green);
        report[commonOffset + LIGHTBAR_BLUE_OFFSET] = (byte) clampMotor(blue);
    }

    private static void copyBlock(byte[] report, int offset, byte[] block) {
        if (block == null) return;
        System.arraycopy(block, 0, report, offset, Math.min(block.length, DualSenseTriggerEffect.BLOCK_SIZE));
    }

    private static void writeBluetoothCrc(byte[] report) {
        CRC32 crc = new CRC32();
        crc.update(BLUETOOTH_OUTPUT_CRC_SEED);
        crc.update(report, 0, report.length - CRC_SIZE);
        long value = crc.getValue();

        report[report.length - 4] = (byte) value;
        report[report.length - 3] = (byte) (value >>> 8);
        report[report.length - 2] = (byte) (value >>> 16);
        report[report.length - 1] = (byte) (value >>> 24);
    }

    private static int clampMotor(int value) {
        return Math.max(0, Math.min(255, value));
    }
}
