package com.mrfuzzihead.fuzzicontrols.controller;

import java.util.EnumSet;
import java.util.Set;

import com.github.strikerx3.jxinput.XInputAxes;
import com.github.strikerx3.jxinput.XInputButtons;
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
        } catch (UnsatisfiedLinkError e) {
            // The JXInput native DLL could not be linked (e.g. wrong architecture, missing runtime, or
            // the library was not extracted into the JVM library search path). UnsatisfiedLinkError is
            // a java.lang.Error, not an Exception, so it must be caught explicitly here.
            FuzziControls.LOG.warn(
                "[FuzziControls] XInput native library failed to link ({}). "
                    + "XInput controller support will be disabled.",
                e.getMessage());
        } catch (Throwable t) {
            FuzziControls.LOG.warn("[FuzziControls] XInput init error: {}", t.getMessage());
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
        XInputAxes axes = components.getAxes();
        XInputButtons buttons = components.getButtons();

        // Axes: JXInput normalises sticks to [-1, 1] (Y positive = stick pushed up).
        // We negate Y so that positive Y means "stick pushed down", matching Minecraft's
        // movement convention (forward = negative Y) and pitch convention (look down = positive).
        float lx = ControllerState.normaliseAxis(axes.lx, deadZone);
        float ly = ControllerState.normaliseAxis(-axes.ly, deadZone);
        float rx = ControllerState.normaliseAxis(axes.rx, deadZone);
        float ry = ControllerState.normaliseAxis(-axes.ry, deadZone);
        float lt = ControllerState.normaliseTrigger(axes.lt, triggerThreshold);
        float rt = ControllerState.normaliseTrigger(axes.rt, triggerThreshold);

        // Collect pressed digital buttons
        Set<ControllerButton> pressed = EnumSet.noneOf(ControllerButton.class);
        if (buttons.a) pressed.add(ControllerButton.A);
        if (buttons.b) pressed.add(ControllerButton.B);
        if (buttons.x) pressed.add(ControllerButton.X);
        if (buttons.y) pressed.add(ControllerButton.Y);
        if (buttons.lShoulder) pressed.add(ControllerButton.LEFT_BUMPER);
        if (buttons.rShoulder) pressed.add(ControllerButton.RIGHT_BUMPER);
        if (buttons.lThumb) pressed.add(ControllerButton.LEFT_STICK_CLICK);
        if (buttons.rThumb) pressed.add(ControllerButton.RIGHT_STICK_CLICK);
        if (buttons.start) pressed.add(ControllerButton.START);
        if (buttons.back) pressed.add(ControllerButton.BACK);
        if (buttons.guide) pressed.add(ControllerButton.GUIDE);
        if (buttons.up) pressed.add(ControllerButton.DPAD_UP);
        if (buttons.down) pressed.add(ControllerButton.DPAD_DOWN);
        if (buttons.left) pressed.add(ControllerButton.DPAD_LEFT);
        if (buttons.right) pressed.add(ControllerButton.DPAD_RIGHT);

        // Triggers as digital
        if (lt >= triggerThreshold) pressed.add(ControllerButton.LEFT_TRIGGER);
        if (rt >= triggerThreshold) pressed.add(ControllerButton.RIGHT_TRIGGER);

        return new ControllerState(lx, ly, rx, ry, lt, rt, pressed);
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
