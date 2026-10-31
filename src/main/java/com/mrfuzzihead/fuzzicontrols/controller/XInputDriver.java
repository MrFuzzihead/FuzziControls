package com.mrfuzzihead.fuzzicontrols.controller;

import com.github.strikerx3.jxinput.XInputComponents;
import com.github.strikerx3.jxinput.XInputDevice;
import com.github.strikerx3.jxinput.exceptions.XInputNotLoadedException;
import com.mrfuzzihead.fuzzicontrols.FuzziControls;

/**
 * XInput controller driver backed by JXInput (com.github.strikerx3.jxinput).
 * Polls controller slot {@code controllerIndex} (0–3).
 */
public class XInputDriver implements IControllerDriver {

    private final int controllerIndex;
    private XInputDevice device;
    private boolean available = false;

    public XInputDriver(int controllerIndex) {
        this.controllerIndex = controllerIndex;
        try {
            if (XInputDevice.isAvailable()) {
                device = XInputDevice.getDeviceFor(controllerIndex);
                available = true;
                FuzziControls.LOG.info("[FuzziControls] XInputDriver initialised for slot {}.", controllerIndex);
            } else {
                FuzziControls.LOG.warn("[FuzziControls] XInput native library not available on this system.");
            }
        } catch (XInputNotLoadedException e) {
            FuzziControls.LOG.warn("[FuzziControls] XInput not loaded: {}", e.getMessage());
        } catch (Exception e) {
            FuzziControls.LOG.warn("[FuzziControls] XInput init error: {}", e.getMessage());
        }
    }

    @Override
    public boolean isConnected() {
        if (!available || device == null) return false;
        try {
            device.poll();
            return device.isConnected();
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public ControllerState poll(float deadZone, float triggerThreshold) {
        if (!available || device == null) return ControllerState.empty();

        try {
            if (!device.poll() || !device.isConnected()) {
                return ControllerState.empty();
            }
        } catch (Exception e) {
            return ControllerState.empty();
        }

        XInputComponents components = device.getComponents();

        return XInputReportMapper.map(components.getAxes(), components.getButtons(), deadZone, triggerThreshold);
    }

    @Override
    public void close() {
        device = null;
        available = false;
    }

    @Override
    public String getDriverName() {
        return "XInput (slot " + controllerIndex + ")";
    }
}
