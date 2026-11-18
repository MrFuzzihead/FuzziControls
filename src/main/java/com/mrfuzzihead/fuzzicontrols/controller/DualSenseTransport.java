package com.mrfuzzihead.fuzzicontrols.controller;

/**
 * Minimal HID transport used by {@link DualSenseDriver}.
 *
 * <p>
 * Extracted so the driver's read/timeout/reconnect logic can be unit tested with a scripted
 * transport, without hidapi or hardware. The production implementation is
 * {@link HidApiDualSenseTransport}.
 */
interface DualSenseTransport {

    /**
     * Opens the first available supported controller.
     *
     * @return true when a handle was opened
     */
    boolean open();

    /**
     * Reads one input report.
     *
     * @param buffer destination buffer
     * @return number of bytes read, {@code 0} when no report is available, or {@code -1} on error
     */
    int read(byte[] buffer);

    /**
     * Reads a feature report for its side effect (the calibration report switches Bluetooth
     * controllers to the enhanced input report).
     *
     * @param buffer   destination buffer
     * @param reportId feature report ID
     * @return number of bytes read, or {@code -1} on error
     */
    int readFeatureReport(byte[] buffer, int reportId);

    /** Returns true while a device handle is open. */
    boolean isOpen();

    /** Releases the handle. Must be safe to call more than once. */
    void close();

    /** Human-readable device name, e.g. {@code DualSense} or {@code DualSense Edge}. */
    String getDeviceName();

    /** Returns true when this transport is a DualSense Edge with the extra paddle/Fn buttons. */
    default boolean isEdge() {
        return false;
    }

    /** Underlying hidapi version, for diagnostics. */
    String getVersion();
}
