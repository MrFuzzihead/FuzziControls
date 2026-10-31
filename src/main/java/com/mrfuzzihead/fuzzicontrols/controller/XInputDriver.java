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

    /** Cached result of the native-library probe; {@code null} until first use. */
    private static Boolean xInputSupported;

    /**
     * Returns true when JXInput's native library is usable on this platform.
     *
     * <p>
     * JXInput ships <b>Windows-only</b> natives. Elsewhere (and whenever the natives cannot be
     * extracted) the call throws {@link UnsatisfiedLinkError} — an {@code Error}, not an exception —
     * which would otherwise escape the constructor and crash the game during mod initialisation. On
     * Linux and macOS this mod must simply report no XInput and let the raw-HID drivers (DualSense)
     * handle input.
     *
     * @return true when an XInput device can be opened
     */
    static synchronized boolean isXInputSupported() {
        if (xInputSupported == null) {
            try {
                xInputSupported = XInputDevice.isAvailable();
            } catch (Throwable t) {
                xInputSupported = false;
                FuzziControls.LOG.info(
                    "[FuzziControls] XInput is unavailable on this platform ({}). "
                        + "Only raw-HID controllers such as DualSense will be detected.",
                    t.toString());
            }
        }
        return xInputSupported;
    }

    public XInputDriver(int controllerIndex) {
        this.controllerIndex = controllerIndex;

        if (!isXInputSupported()) return;

        try {
            device = XInputDevice.getDeviceFor(controllerIndex);
            available = true;
            FuzziControls.LOG.info("[FuzziControls] XInputDriver initialised for slot {}.", controllerIndex);
        } catch (XInputNotLoadedException e) {
            FuzziControls.LOG.warn("[FuzziControls] XInput not loaded: {}", e.getMessage());
        } catch (Exception | LinkageError e) {
            FuzziControls.LOG.warn("[FuzziControls] XInput init error: {}", e.toString());
            device = null;
            available = false;
        }
    }

    @Override
    public boolean isConnected() {
        if (!available || device == null) return false;
        try {
            device.poll();
            return device.isConnected();
        } catch (Exception | LinkageError e) {
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
        } catch (Exception | LinkageError e) {
            // LinkageError covers a native library that disappears mid-session (unplugged dongle etc.).
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
