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

    /**
     * Human-readable device name for toasts and the settings screen, e.g. {@code DualSense Edge}.
     * Defaults to the driver name for drivers that only expose one device type.
     */
    default String getDeviceName() {
        return getDriverName();
    }

    /**
     * Human-readable transport, e.g. {@code USB} or {@code Bluetooth}. Empty when the driver does
     * not distinguish transports (XInput) or does not know yet.
     */
    default String getTransportName() {
        return "";
    }

    /**
     * Returns true once the driver has confirmed that it is receiving usable input.
     *
     * <p>
     * A hot-plug probe can successfully open a stale HID handle that fails on its first read (a
     * Bluetooth controller that is still disappearing is the common case). The manager delays the
     * connect toast until this returns true, so that handle does not produce a connect/disconnect
     * pair. Drivers that can confirm the device at open time keep the default {@code true}.
     */
    default boolean isConnectionConfirmed() {
        return true;
    }
}
