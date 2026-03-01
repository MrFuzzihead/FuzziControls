package com.mrfuzzihead.fuzzicontrols.controller;

/**
 * Abstraction over a physical controller input source.
 * Implementations exist for XInput ({@link XInputDriver}) and DualSense ({@link DualSenseDriver}).
 */
public interface IControllerDriver {

    /**
     * Returns true if at least one controller is connected and ready to poll.
     */
    boolean isConnected();

    /**
     * Polls the hardware and returns a normalised {@link ControllerState}.
     * Must be called from the client tick thread.
     *
     * @param deadZone         Dead-zone radius in [0, 1] for stick axes.
     * @param triggerThreshold Minimum value in [0, 1] for triggers to register.
     * @return A fresh {@link ControllerState}; never {@code null}.
     *         Returns {@link ControllerState#empty()} when disconnected.
     */
    ControllerState poll(float deadZone, float triggerThreshold);

    /**
     * Releases any native resources held by this driver.
     * Called on mod shutdown or when switching drivers.
     */
    void close();

    /** Human-readable name for logging and config UI. */
    String getDriverName();
}
