package com.mrfuzzihead.fuzzicontrols.controller;

/**
 * Immutable snapshot of the controller that is currently connected.
 *
 * <p>
 * Captured before a driver is closed, because a closed driver no longer reports its device or
 * transport. The snapshot is passed to {@link ControllerManager.ConnectionListener} so UI code can
 * show a disconnect toast with the same device name that the connect toast used.
 */
public final class ControllerConnection {

    private final String driverName;
    private final String deviceName;
    private final String transportName;

    public ControllerConnection(String driverName, String deviceName, String transportName) {
        this.driverName = nonNull(driverName);
        this.deviceName = nonNull(deviceName);
        this.transportName = nonNull(transportName);
    }

    /** Driver implementation name, e.g. {@code DualSense (HID)}. */
    public String getDriverName() {
        return driverName;
    }

    /** Human-readable device name, e.g. {@code DualSense Edge}. */
    public String getDeviceName() {
        return deviceName;
    }

    /** Human-readable transport, e.g. {@code USB} or {@code Bluetooth}; may be empty. */
    public String getTransportName() {
        return transportName;
    }

    /**
     * Returns true when both snapshots describe the same physical device, ignoring transport.
     * Used when re-initialising the driver (for example after {@code /fuzzicontrols reload}) so a
     * same-device reconnect does not produce a spurious disconnect/connect toast pair.
     */
    public boolean hasSameDevice(ControllerConnection other) {
        return other != null && driverName.equals(other.driverName) && deviceName.equals(other.deviceName);
    }

    private static String nonNull(String value) {
        return value != null ? value : "";
    }
}
