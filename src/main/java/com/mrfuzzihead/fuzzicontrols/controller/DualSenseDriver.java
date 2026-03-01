package com.mrfuzzihead.fuzzicontrols.controller;

import java.util.EnumSet;
import java.util.Set;

import org.hid4java.HidDevice;
import org.hid4java.HidManager;
import org.hid4java.HidServices;
import org.hid4java.HidServicesSpecification;

import com.mrfuzzihead.fuzzicontrols.FuzziControls;

/**
 * DualSense (PS5) controller driver using raw HID via hid4java.
 *
 * <p>
 * Reports are read from USB HID report ID 0x01 (USB mode) which has the following
 * relevant byte layout starting at byte 1:
 *
 * <pre>
 *   [1]  Left stick X   (0–255, 128 = center)
 *   [2]  Left stick Y   (0–255, 128 = center, 0 = up)
 *   [3]  Right stick X
 *   [4]  Right stick Y
 *   [5]  Left trigger   (0–255)
 *   [6]  Right trigger  (0–255)
 *   [8]  Buttons byte 1 (bits: square, cross, circle, triangle, L1, R1, L2, R2)
 *   [9]  Buttons byte 2 (bits: create/share, options, L3, R3, PS, touchpad, mute, —)
 *   [9]  D-pad nibble in low 4 bits of byte 8 (0=N, 1=NE, 2=E, 3=SE, 4=S, 5=SW, 6=W, 7=NW, 8=none)
 * </pre>
 *
 * @see <a href="https://controllers.fandom.com/wiki/Sony_DualSense">DualSense HID report format</a>
 */
public class DualSenseDriver implements IControllerDriver {

    /** Sony vendor ID. */
    private static final short VENDOR_ID = (short) 0x054C;

    /** DualSense product ID. */
    private static final short PRODUCT_ID = (short) 0x0CE6;

    /** USB HID report size for the DualSense in USB mode. */
    private static final int REPORT_SIZE = 64;

    private HidServices hidServices;
    private HidDevice device;

    public DualSenseDriver() {
        try {
            HidServicesSpecification spec = new HidServicesSpecification();
            spec.setAutoShutdown(false);
            hidServices = HidManager.getHidServices(spec);
            device = hidServices.getHidDevice(VENDOR_ID, PRODUCT_ID, null);
            if (device != null && device.open()) {
                FuzziControls.LOG.info("[FuzziControls] DualSense controller detected and opened.");
            } else {
                device = null;
                FuzziControls.LOG.info("[FuzziControls] DualSense not found; driver standby.");
            }
        } catch (Exception e) {
            FuzziControls.LOG.warn("[FuzziControls] DualSense driver failed to initialise: {}", e.getMessage());
            device = null;
        }
    }

    @Override
    public boolean isConnected() {
        return device != null && device.isOpen();
    }

    @Override
    public ControllerState poll(float deadZone, float triggerThreshold) {
        if (!isConnected()) return ControllerState.empty();

        byte[] data = new byte[REPORT_SIZE];
        int read = device.read(data, 5); // 5 ms timeout
        if (read < 10) return ControllerState.empty();

        // Sticks: bytes 1-4, triggers: bytes 5-6
        float lx = byteToAxis(data[1]);
        float ly = byteToAxis(data[2]);
        float rx = byteToAxis(data[3]);
        float ry = byteToAxis(data[4]);
        float lt = byteToTrigger(data[5]);
        float rt = byteToTrigger(data[6]);

        // Apply normalization
        lx = ControllerState.normaliseAxis(lx, deadZone);
        ly = ControllerState.normaliseAxis(ly, deadZone);
        rx = ControllerState.normaliseAxis(rx, deadZone);
        ry = ControllerState.normaliseAxis(ry, deadZone);
        lt = ControllerState.normaliseTrigger(lt, triggerThreshold);
        rt = ControllerState.normaliseTrigger(rt, triggerThreshold);

        // Buttons byte 8: bits 7-4 = triangle, circle, cross, square; bits 3-0 = d-pad
        int btnByte1 = data[8] & 0xFF;
        int btnByte2 = data[9] & 0xFF;
        int dpad = btnByte1 & 0x0F;

        Set<ControllerButton> pressed = EnumSet.noneOf(ControllerButton.class);

        // Face buttons (PS labels → Xbox equivalents stored in ControllerButton)
        if ((btnByte1 & 0x10) != 0) pressed.add(ControllerButton.X); // Square
        if ((btnByte1 & 0x20) != 0) pressed.add(ControllerButton.A); // Cross
        if ((btnByte1 & 0x40) != 0) pressed.add(ControllerButton.B); // Circle
        if ((btnByte1 & 0x80) != 0) pressed.add(ControllerButton.Y); // Triangle

        // Shoulder / triggers as digital
        if ((btnByte2 & 0x01) != 0) pressed.add(ControllerButton.LEFT_BUMPER);
        if ((btnByte2 & 0x02) != 0) pressed.add(ControllerButton.RIGHT_BUMPER);
        if (lt >= triggerThreshold) pressed.add(ControllerButton.LEFT_TRIGGER);
        if (rt >= triggerThreshold) pressed.add(ControllerButton.RIGHT_TRIGGER);

        // Share / Options / L3 / R3 / PS (byte 9 shifted by the byte9 layout)
        if ((btnByte2 & 0x10) != 0) pressed.add(ControllerButton.BACK); // Create/Share
        if ((btnByte2 & 0x20) != 0) pressed.add(ControllerButton.START); // Options
        if ((btnByte2 & 0x40) != 0) pressed.add(ControllerButton.LEFT_STICK_CLICK); // L3
        if ((btnByte2 & 0x80) != 0) pressed.add(ControllerButton.RIGHT_STICK_CLICK); // R3

        // D-pad (0=N,1=NE,2=E,3=SE,4=S,5=SW,6=W,7=NW,8=none)
        if (dpad == 0 || dpad == 1 || dpad == 7) pressed.add(ControllerButton.DPAD_UP);
        if (dpad == 2 || dpad == 1 || dpad == 3) pressed.add(ControllerButton.DPAD_RIGHT);
        if (dpad == 4 || dpad == 3 || dpad == 5) pressed.add(ControllerButton.DPAD_DOWN);
        if (dpad == 6 || dpad == 5 || dpad == 7) pressed.add(ControllerButton.DPAD_LEFT);

        return new ControllerState(lx, ly, rx, ry, lt, rt, pressed);
    }

    @Override
    public void close() {
        if (device != null) {
            device.close();
            device = null;
        }
        if (hidServices != null) {
            hidServices.shutdown();
            hidServices = null;
        }
    }

    @Override
    public String getDriverName() {
        return "DualSense (HID)";
    }

    /** Converts an unsigned byte stick value [0, 255] to a float in [-1, 1]. */
    private static float byteToAxis(byte b) {
        int unsigned = b & 0xFF;
        return (unsigned - 128) / 127.5f;
    }

    /** Converts an unsigned byte trigger value [0, 255] to a float in [0, 1]. */
    private static float byteToTrigger(byte b) {
        return (b & 0xFF) / 255.0f;
    }
}
