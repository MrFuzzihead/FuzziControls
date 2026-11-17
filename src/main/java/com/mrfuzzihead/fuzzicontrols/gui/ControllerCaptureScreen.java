package com.mrfuzzihead.fuzzicontrols.gui;

import com.mrfuzzihead.fuzzicontrols.controller.ControllerState;

/**
 * Implemented by a screen that wants to consume raw controller input instead of letting the normal
 * GUI navigation/click path run.
 *
 * <p>
 * The binding-capture overlay uses this so pressing B can be bound instead of closing the screen,
 * and pressing A can be bound instead of clicking the focused button.
 */
public interface ControllerCaptureScreen {

    /** Returns true while the screen is waiting for controller input. */
    boolean isCapturingControllerInput();

    /** Called once per client tick with the latest normalized controller state. */
    void captureControllerState(ControllerState state);
}
