package com.mrfuzzihead.fuzzicontrols.controller;

import java.util.EnumSet;
import java.util.Set;

import org.hid4java.HidDevice;
import org.hid4java.HidManager;
import org.hid4java.HidServices;
import org.hid4java.HidServicesSpecification;

import com.mrfuzzihead.fuzzicontrols.FuzziControls;

public class DualSenseDriver implements IControllerDriver {

    private static final short VENDOR_ID = (short) 0x054C;
    private static final short PRODUCT_ID = (short) 0x0CE6;
    private static final int REPORT_SIZE = 64;
    private static final int REPORT_MIN_BYTES = 10;

    private HidServices hidServices;
    private HidDevice device;
    private ControllerState lastState = null;

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
        try {
            ControllerState latest = lastState;
            int read;
            while ((read = device.read(data, 0)) >= REPORT_MIN_BYTES) {
                latest = parseReport(data, deadZone, triggerThreshold);
            }
            if (latest != null) {
                lastState = latest;
                return latest;
            }
            return ControllerState.empty();
        } catch (Exception e) {
            FuzziControls.LOG.debug("[FuzziControls] DualSense read error: {}", e.getMessage());
            return lastState != null ? lastState : ControllerState.empty();
        }
    }

    private static ControllerState parseReport(byte[] data, float deadZone, float triggerThreshold) {
        float lx = byteToAxis(data[1]);
        float ly = byteToAxis(data[2]);
        float rx = byteToAxis(data[3]);
        float ry = byteToAxis(data[4]);
        float rawLt = byteToTrigger(data[5]);
        float rawRt = byteToTrigger(data[6]);

        lx = ControllerState.normaliseAxis(lx, deadZone);
        ly = ControllerState.normaliseAxis(ly, deadZone);
        rx = ControllerState.normaliseAxis(rx, deadZone);
        ry = ControllerState.normaliseAxis(ry, deadZone);
        float lt = ControllerState.normaliseTrigger(rawLt, triggerThreshold);
        float rt = ControllerState.normaliseTrigger(rawRt, triggerThreshold);

        int btnByte1 = data[8] & 0xFF;
        int btnByte2 = data[9] & 0xFF;
        int dpad = btnByte1 & 0x0F;

        Set<ControllerButton> pressed = EnumSet.noneOf(ControllerButton.class);

        if ((btnByte1 & 0x10) != 0) pressed.add(ControllerButton.X);
        if ((btnByte1 & 0x20) != 0) pressed.add(ControllerButton.A);
        if ((btnByte1 & 0x40) != 0) pressed.add(ControllerButton.B);
        if ((btnByte1 & 0x80) != 0) pressed.add(ControllerButton.Y);

        if ((btnByte2 & 0x01) != 0) pressed.add(ControllerButton.LEFT_BUMPER);
        if ((btnByte2 & 0x02) != 0) pressed.add(ControllerButton.RIGHT_BUMPER);
        if (rawLt >= triggerThreshold) pressed.add(ControllerButton.LEFT_TRIGGER);
        if (rawRt >= triggerThreshold) pressed.add(ControllerButton.RIGHT_TRIGGER);

        if ((btnByte2 & 0x10) != 0) pressed.add(ControllerButton.BACK);
        if ((btnByte2 & 0x20) != 0) pressed.add(ControllerButton.START);
        if ((btnByte2 & 0x40) != 0) pressed.add(ControllerButton.LEFT_STICK_CLICK);
        if ((btnByte2 & 0x80) != 0) pressed.add(ControllerButton.RIGHT_STICK_CLICK);

        if (dpad == 0 || dpad == 1 || dpad == 7) pressed.add(ControllerButton.DPAD_UP);
        if (dpad == 2 || dpad == 1 || dpad == 3) pressed.add(ControllerButton.DPAD_RIGHT);
        if (dpad == 4 || dpad == 3 || dpad == 5) pressed.add(ControllerButton.DPAD_DOWN);
        if (dpad == 6 || dpad == 5 || dpad == 7) pressed.add(ControllerButton.DPAD_LEFT);

        return new ControllerState(lx, ly, rx, ry, lt, rt, pressed);
    }

    @Override
    public void close() {
        if (device != null) {
            try {
                device.close();
            } catch (Exception e) {
                FuzziControls.LOG.debug("[FuzziControls] DualSense device close error: {}", e.getMessage());
            }
            device = null;
        }
        if (hidServices != null) {
            try {
                hidServices.shutdown();
            } catch (Exception e) {
                FuzziControls.LOG.debug("[FuzziControls] DualSense HID services shutdown error: {}", e.getMessage());
            }
            hidServices = null;
        }
        lastState = null;
    }

    @Override
    public String getDriverName() {
        return "DualSense (HID)";
    }

    private static float byteToAxis(byte b) {
        int unsigned = b & 0xFF;
        return (unsigned - 128) / 127.5f;
    }

    private static float byteToTrigger(byte b) {
        return (b & 0xFF) / 255.0f;
    }
}
