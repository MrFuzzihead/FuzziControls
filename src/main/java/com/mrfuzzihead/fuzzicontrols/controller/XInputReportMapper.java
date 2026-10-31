package com.mrfuzzihead.fuzzicontrols.controller;

import java.util.EnumSet;
import java.util.Set;

import com.github.strikerx3.jxinput.XInputAxes;
import com.github.strikerx3.jxinput.XInputButtons;

/**
 * Maps a JXInput component snapshot onto a {@link ControllerState}.
 *
 * <p>
 * Extracted from {@link XInputDriver} so the axis conventions and button mapping can be unit tested
 * without the XInput native library. The conventions pinned here are easy to get wrong and expensive
 * when they are: a sign error flips vertical look or movement, and a wrong button index silently
 * rebinds an action.
 */
final class XInputReportMapper {

    private XInputReportMapper() {}

    /**
     * Converts one poll of JXInput components into a normalised state.
     *
     * @param axes             JXInput axes; sticks are already in [-1, 1] with positive Y = up
     * @param buttons          JXInput digital button flags
     * @param deadZone         stick dead-zone radius for {@link ControllerState#normaliseAxis}
     * @param triggerThreshold trigger threshold for {@link ControllerState#normaliseTrigger}
     * @return the normalised state
     */
    static ControllerState map(XInputAxes axes, XInputButtons buttons, float deadZone, float triggerThreshold) {
        // JXInput normalises sticks to [-1, 1] with positive Y meaning "stick pushed up". Y is
        // negated so positive means "pushed down", matching Minecraft's movement convention
        // (forward = negative Y) and its pitch convention (look down = positive pitch).
        float lx = ControllerState.normaliseAxis(axes.lx, deadZone);
        float ly = ControllerState.normaliseAxis(-axes.ly, deadZone);
        float rx = ControllerState.normaliseAxis(axes.rx, deadZone);
        float ry = ControllerState.normaliseAxis(-axes.ry, deadZone);
        float lt = ControllerState.normaliseTrigger(axes.lt, triggerThreshold);
        float rt = ControllerState.normaliseTrigger(axes.rt, triggerThreshold);

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

        // Triggers as digital buttons, compared against the raw trigger value rather than the
        // already-thresholded normalised one so the configured threshold is applied exactly once.
        if (axes.lt >= triggerThreshold) pressed.add(ControllerButton.LEFT_TRIGGER);
        if (axes.rt >= triggerThreshold) pressed.add(ControllerButton.RIGHT_TRIGGER);

        return new ControllerState(lx, ly, rx, ry, lt, rt, pressed);
    }
}
