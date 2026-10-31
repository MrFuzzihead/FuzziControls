package com.mrfuzzihead.fuzzicontrols.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

import com.github.strikerx3.jxinput.XInputAxes;
import com.github.strikerx3.jxinput.XInputButtons;

/**
 * Unit tests for {@link XInputReportMapper}.
 *
 * <p>
 * XInput had no test coverage at all. These pin the axis conventions (Y is negated so positive means
 * "pushed down"), dead-zone and trigger-threshold handling, and every digital button mapping — the
 * kind of silent error that would rebind an action or invert the camera for XInput users only.
 */
public class XInputReportMapperTest {

    private static final float DEAD_ZONE = 0.15f;

    private static final float TRIGGER_THRESHOLD = 0.2f;

    private XInputAxes axes;

    private XInputButtons buttons;

    /** JXInput's structs have protected constructors; subclasses give the test access. */
    private static final class TestAxes extends XInputAxes {
    }

    private static final class TestButtons extends XInputButtons {
    }

    @Before
    public void setUp() {
        axes = new TestAxes();
        buttons = new TestButtons();
    }

    private ControllerState map() {
        return XInputReportMapper.map(axes, buttons, DEAD_ZONE, TRIGGER_THRESHOLD);
    }

    // -------------------------------------------------------------------------
    // Axis conventions
    // -------------------------------------------------------------------------

    @Test
    public void centredSticksProduceZero() {
        ControllerState state = map();
        assertEquals(0f, state.leftStickX(), 0f);
        assertEquals(0f, state.leftStickY(), 0f);
        assertEquals(0f, state.rightStickX(), 0f);
        assertEquals(0f, state.rightStickY(), 0f);
        assertTrue(
            state.pressedButtons()
                .isEmpty());
    }

    @Test
    public void verticalAxesAreNegated() {
        // JXInput reports positive Y for "pushed up"; ControllerState uses -1 for up.
        axes.ly = 1f;
        axes.ry = 1f;
        ControllerState state = map();
        assertEquals("Stick up must be negative Y", -1f, state.leftStickY(), 0f);
        assertEquals("Stick up must be negative Y", -1f, state.rightStickY(), 0f);

        axes.ly = -1f;
        axes.ry = -1f;
        state = map();
        assertEquals(1f, state.leftStickY(), 0f);
        assertEquals(1f, state.rightStickY(), 0f);
    }

    @Test
    public void horizontalAxesAreNotNegated() {
        axes.lx = 1f;
        axes.rx = -1f;
        ControllerState state = map();
        assertEquals("Stick right must be positive X", 1f, state.leftStickX(), 0f);
        assertEquals(-1f, state.rightStickX(), 0f);
    }

    @Test
    public void valuesInsideTheDeadZoneAreZero() {
        axes.lx = 0.1f;
        axes.ly = -0.1f;
        ControllerState state = map();
        assertEquals(0f, state.leftStickX(), 0f);
        assertEquals(0f, state.leftStickY(), 0f);
    }

    @Test
    public void halfDeflectionIsRescaledPastTheDeadZone() {
        // normaliseAxis((0.575 - 0.15) / 0.85) = 0.5
        axes.lx = 0.575f;
        assertEquals(0.5f, map().leftStickX(), 1e-5f);
    }

    // -------------------------------------------------------------------------
    // Triggers
    // -------------------------------------------------------------------------

    @Test
    public void triggerBelowThresholdIsReleased() {
        axes.lt = 0.19f;
        axes.rt = 1f;
        ControllerState state = map();

        assertEquals(0f, state.leftTrigger(), 0f);
        assertEquals(1f, state.rightTrigger(), 0f);
        assertFalse("Below-threshold trigger is not a pressed button", state.isPressed(ControllerButton.LEFT_TRIGGER));
        assertTrue(state.isPressed(ControllerButton.RIGHT_TRIGGER));
    }

    @Test
    public void triggerAtThresholdRegisters() {
        axes.lt = TRIGGER_THRESHOLD;
        ControllerState state = map();
        assertEquals("At-threshold normalises to 0 but counts as pressed", 0f, state.leftTrigger(), 1e-6f);
        assertTrue(state.isPressed(ControllerButton.LEFT_TRIGGER));
    }

    @Test
    public void partialTriggerIsRescaledAboveTheThreshold() {
        axes.rt = 0.6f;
        // (0.6 - 0.2) / (1 - 0.2) = 0.5
        assertEquals(0.5f, map().rightTrigger(), 1e-5f);
    }

    // -------------------------------------------------------------------------
    // Digital buttons
    // -------------------------------------------------------------------------

    @Test
    public void faceButtonsMap() {
        buttons.a = true;
        buttons.b = true;
        buttons.x = true;
        buttons.y = true;
        ControllerState state = map();

        assertTrue(state.isPressed(ControllerButton.A));
        assertTrue(state.isPressed(ControllerButton.B));
        assertTrue(state.isPressed(ControllerButton.X));
        assertTrue(state.isPressed(ControllerButton.Y));
    }

    @Test
    public void shouldersAndStickClicksMap() {
        buttons.lShoulder = true;
        buttons.rShoulder = true;
        buttons.lThumb = true;
        buttons.rThumb = true;
        ControllerState state = map();

        assertTrue(state.isPressed(ControllerButton.LEFT_BUMPER));
        assertTrue(state.isPressed(ControllerButton.RIGHT_BUMPER));
        assertTrue(state.isPressed(ControllerButton.LEFT_STICK_CLICK));
        assertTrue(state.isPressed(ControllerButton.RIGHT_STICK_CLICK));
    }

    @Test
    public void menuAndGuideButtonsMap() {
        buttons.start = true;
        buttons.back = true;
        buttons.guide = true;
        ControllerState state = map();

        assertTrue(state.isPressed(ControllerButton.START));
        assertTrue(state.isPressed(ControllerButton.BACK));
        // XInput reports the guide button; the DualSense path cannot, which is why it has no default.
        assertTrue(state.isPressed(ControllerButton.GUIDE));
    }

    @Test
    public void dpadMaps() {
        buttons.up = true;
        buttons.down = true;
        buttons.left = true;
        buttons.right = true;
        ControllerState state = map();

        assertTrue(state.isPressed(ControllerButton.DPAD_UP));
        assertTrue(state.isPressed(ControllerButton.DPAD_DOWN));
        assertTrue(state.isPressed(ControllerButton.DPAD_LEFT));
        assertTrue(state.isPressed(ControllerButton.DPAD_RIGHT));
    }

    @Test
    public void unsetButtonsAreNotPressed() {
        buttons.a = true;
        ControllerState state = map();

        assertTrue(state.isPressed(ControllerButton.A));
        for (ControllerButton button : ControllerButton.values()) {
            if (button == ControllerButton.A) continue;
            assertFalse("Unexpected press of " + button, state.isPressed(button));
        }
    }

    @Test
    public void fullSnapshotMapsEverything() {
        axes.lx = 1f;
        axes.ly = -1f;
        axes.rx = 0.5f;
        axes.ry = -0.5f;
        axes.lt = 1f;
        axes.rt = 0.4f;
        buttons.a = buttons.b = buttons.x = buttons.y = true;
        buttons.lShoulder = buttons.rShoulder = true;
        buttons.lThumb = buttons.rThumb = true;
        buttons.start = buttons.back = true;
        buttons.up = buttons.down = buttons.left = buttons.right = true;

        ControllerState state = map();

        assertEquals(1f, state.leftStickX(), 0f);
        assertEquals(1f, state.leftStickY(), 0f);
        // Sticks are rescaled past the dead zone: (0.5 - 0.15) / (1 - 0.15).
        assertEquals(0.4117647f, state.rightStickX(), 1e-5f);
        assertEquals(0.4117647f, state.rightStickY(), 1e-5f);
        assertEquals(1f, state.leftTrigger(), 0f);
        assertEquals(0.25f, state.rightTrigger(), 1e-5f);
        // 14 digital buttons (guide is not set) plus both triggers as digital buttons.
        assertEquals(
            16,
            state.pressedButtons()
                .size());
    }
}
