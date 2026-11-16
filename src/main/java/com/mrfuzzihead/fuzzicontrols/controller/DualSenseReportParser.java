package com.mrfuzzihead.fuzzicontrols.controller;

import java.util.EnumSet;
import java.util.Set;
import java.util.zip.CRC32;

/**
 * Decodes the three DualSense input report layouts into a {@link ControllerState}.
 *
 * <p>
 * The controller uses different report layouts depending on how it is connected:
 *
 * <table border="1">
 * <caption>DualSense input reports</caption>
 * <tr>
 * <th>Transport / mode</th>
 * <th>Report ID</th>
 * <th>Length</th>
 * <th>Axis offsets</th>
 * <th>Button offsets</th>
 * </tr>
 * <tr>
 * <td>USB</td>
 * <td>{@code 0x01}</td>
 * <td>64</td>
 * <td>1–4 (sticks), 5–6 (triggers)</td>
 * <td>8, 9</td>
 * </tr>
 * <tr>
 * <td>Bluetooth, simple/default</td>
 * <td>{@code 0x01}</td>
 * <td>10 (or 78 when padded)</td>
 * <td>1–4 (sticks), 8–9 (triggers)</td>
 * <td>5, 6, 7</td>
 * </tr>
 * <tr>
 * <td>Bluetooth, enhanced</td>
 * <td>{@code 0x31}</td>
 * <td>78</td>
 * <td>2–5 (sticks), 6–7 (triggers)</td>
 * <td>9, 10, 11</td>
 * </tr>
 * </table>
 *
 * <p>
 * The Bluetooth-enhanced report ends with a little-endian CRC32 over the report (including the
 * report ID) seeded with {@code 0xA1}. Bluetooth starts in the simple report; reading feature
 * report {@code 0x05} switches it to the enhanced report. This class only decodes reports; the
 * driver owns transport, feature reports and lifecycle.
 *
 * <p>
 * Extracted from {@link DualSenseDriver} so every layout (and the CRC) can be exercised by unit
 * tests without hardware.
 */
final class DualSenseReportParser {

    /** Which input-report layout the given bytes use. */
    enum Layout {
        USB_0X01,
        BLUETOOTH_SIMPLE_0X01,
        BLUETOOTH_ENHANCED_0X31,
        UNKNOWN
    }

    static final int USB_REPORT_ID = 0x01;
    static final int BLUETOOTH_ENHANCED_REPORT_ID = 0x31;
    static final int USB_REPORT_SIZE = 64;
    static final int BLUETOOTH_SIMPLE_REPORT_SIZE = 10;
    static final int BLUETOOTH_ENHANCED_REPORT_SIZE = 78;

    /** CRC32 seed for Bluetooth input reports (Linux hid-playstation / SDL use the same value). */
    static final int BLUETOOTH_INPUT_CRC_SEED = 0xA1;

    private static final int CRC_SIZE = 4;

    private DualSenseReportParser() {}

    /**
     * Identifies the layout of one report.
     *
     * @param data   report bytes, including the report ID in {@code data[0]}
     * @param length number of valid bytes in {@code data}
     * @return the matching layout, or {@link Layout#UNKNOWN} when the report is not understood
     */
    static Layout detect(byte[] data, int length) {
        if (data == null || length <= 0 || length > data.length) return Layout.UNKNOWN;

        int reportId = data[0] & 0xFF;
        if (reportId == BLUETOOTH_ENHANCED_REPORT_ID) {
            return length == BLUETOOTH_ENHANCED_REPORT_SIZE ? Layout.BLUETOOTH_ENHANCED_0X31 : Layout.UNKNOWN;
        }
        if (reportId == USB_REPORT_ID) {
            if (length == USB_REPORT_SIZE) return Layout.USB_0X01;
            // Some Bluetooth stacks pad the 9-byte simple report to the device's maximum report
            // size. The payload still starts at data[1], so it is parsed as the simple layout.
            if (length == BLUETOOTH_SIMPLE_REPORT_SIZE || length == BLUETOOTH_ENHANCED_REPORT_SIZE) {
                return Layout.BLUETOOTH_SIMPLE_0X01;
            }
        }
        return Layout.UNKNOWN;
    }

    /**
     * Verifies the CRC32 trailer of a Bluetooth-enhanced report.
     *
     * @param data   report bytes
     * @param length valid length, expected to be {@value #BLUETOOTH_ENHANCED_REPORT_SIZE}
     * @return true when the report length is correct and the CRC matches
     */
    static boolean hasValidBluetoothCrc(byte[] data, int length) {
        if (data == null || length != BLUETOOTH_ENHANCED_REPORT_SIZE) return false;

        CRC32 crc = new CRC32();
        crc.update(BLUETOOTH_INPUT_CRC_SEED);
        crc.update(data, 0, length - CRC_SIZE);

        long expected = (data[length - 4] & 0xFFL) | ((data[length - 3] & 0xFFL) << 8)
            | ((data[length - 2] & 0xFFL) << 16)
            | ((data[length - 1] & 0xFFL) << 24);
        return crc.getValue() == expected;
    }

    /**
     * Decodes one report using the given layout.
     *
     * <p>
     * The caller is responsible for checking the CRC of an enhanced Bluetooth report first (see
     * {@link #hasValidBluetoothCrc(byte[], int)}); this method does not reject bad CRCs.
     *
     * @param data             report bytes
     * @param length           valid length
     * @param layout           layout returned by {@link #detect(byte[], int)}
     * @param deadZone         stick dead-zone
     * @param triggerThreshold trigger threshold
     * @return the decoded state, or {@link ControllerState#empty()} for a malformed/unknown report
     */
    static ControllerState parse(byte[] data, int length, Layout layout, float deadZone, float triggerThreshold) {
        if (data == null || length <= 0 || length > data.length || layout == null) return ControllerState.empty();

        final int leftX;
        final int leftY;
        final int rightX;
        final int rightY;
        final int leftTrigger;
        final int rightTrigger;
        final int buttons0;
        final int buttons1;
        final int minimumLength;

        switch (layout) {
            case USB_0X01 -> {
                leftX = 1;
                leftY = 2;
                rightX = 3;
                rightY = 4;
                leftTrigger = 5;
                rightTrigger = 6;
                buttons0 = 8;
                buttons1 = 9;
                minimumLength = 10;
            }
            case BLUETOOTH_SIMPLE_0X01 -> {
                leftX = 1;
                leftY = 2;
                rightX = 3;
                rightY = 4;
                leftTrigger = 8;
                rightTrigger = 9;
                buttons0 = 5;
                buttons1 = 6;
                minimumLength = 10;
            }
            case BLUETOOTH_ENHANCED_0X31 -> {
                leftX = 2;
                leftY = 3;
                rightX = 4;
                rightY = 5;
                leftTrigger = 6;
                rightTrigger = 7;
                buttons0 = 9;
                buttons1 = 10;
                minimumLength = 12;
            }
            case UNKNOWN -> {
                return ControllerState.empty();
            }
            default -> {
                return ControllerState.empty();
            }
        }

        if (length < minimumLength) return ControllerState.empty();

        float lx = ControllerState.normaliseAxis(byteToAxis(data[leftX]), deadZone);
        float ly = ControllerState.normaliseAxis(byteToAxis(data[leftY]), deadZone);
        float rx = ControllerState.normaliseAxis(byteToAxis(data[rightX]), deadZone);
        float ry = ControllerState.normaliseAxis(byteToAxis(data[rightY]), deadZone);

        int btn0 = data[buttons0] & 0xFF;
        int btn1 = data[buttons1] & 0xFF;

        float rawLt = byteToTrigger(data[leftTrigger]);
        float rawRt = byteToTrigger(data[rightTrigger]);
        // The Digital L2/R2 bits are a fallback for firmware that reports 0 in the analogue byte
        // while the trigger is fully pressed. SDL applies the same rule.
        if (rawLt == 0f && (btn1 & 0x04) != 0) rawLt = 1f;
        if (rawRt == 0f && (btn1 & 0x08) != 0) rawRt = 1f;

        float lt = ControllerState.normaliseTrigger(rawLt, triggerThreshold);
        float rt = ControllerState.normaliseTrigger(rawRt, triggerThreshold);

        Set<ControllerButton> pressed = EnumSet.noneOf(ControllerButton.class);
        addFaceButtons(pressed, btn0);
        addDpad(pressed, btn0 & 0x0F);
        addShoulderAndMenuButtons(pressed, btn1);
        if (rawLt >= triggerThreshold) pressed.add(ControllerButton.LEFT_TRIGGER);
        if (rawRt >= triggerThreshold) pressed.add(ControllerButton.RIGHT_TRIGGER);

        return new ControllerState(lx, ly, rx, ry, lt, rt, pressed);
    }

    /** Face buttons are the high nibble of the first button byte in every layout. */
    private static void addFaceButtons(Set<ControllerButton> pressed, int buttons0) {
        if ((buttons0 & 0x10) != 0) pressed.add(ControllerButton.X); // Square
        if ((buttons0 & 0x20) != 0) pressed.add(ControllerButton.A); // Cross
        if ((buttons0 & 0x40) != 0) pressed.add(ControllerButton.B); // Circle
        if ((buttons0 & 0x80) != 0) pressed.add(ControllerButton.Y); // Triangle
    }

    /**
     * The D-pad is a hat in the low nibble of the first button byte: 0=N, 1=NE, … 7=NW, 8=neutral.
     */
    private static void addDpad(Set<ControllerButton> pressed, int hat) {
        if (hat == 0 || hat == 1 || hat == 7) pressed.add(ControllerButton.DPAD_UP);
        if (hat == 2 || hat == 1 || hat == 3) pressed.add(ControllerButton.DPAD_RIGHT);
        if (hat == 4 || hat == 3 || hat == 5) pressed.add(ControllerButton.DPAD_DOWN);
        if (hat == 6 || hat == 5 || hat == 7) pressed.add(ControllerButton.DPAD_LEFT);
    }

    /** Shoulder, menu and stick-click buttons are in the second button byte in every layout. */
    private static void addShoulderAndMenuButtons(Set<ControllerButton> pressed, int buttons1) {
        if ((buttons1 & 0x01) != 0) pressed.add(ControllerButton.LEFT_BUMPER);
        if ((buttons1 & 0x02) != 0) pressed.add(ControllerButton.RIGHT_BUMPER);
        if ((buttons1 & 0x10) != 0) pressed.add(ControllerButton.BACK);
        if ((buttons1 & 0x20) != 0) pressed.add(ControllerButton.START);
        if ((buttons1 & 0x40) != 0) pressed.add(ControllerButton.LEFT_STICK_CLICK);
        if ((buttons1 & 0x80) != 0) pressed.add(ControllerButton.RIGHT_STICK_CLICK);
    }

    private static float byteToAxis(byte b) {
        int unsigned = b & 0xFF;
        return (unsigned - 128) / 127.5f;
    }

    private static float byteToTrigger(byte b) {
        return (b & 0xFF) / 255.0f;
    }
}
