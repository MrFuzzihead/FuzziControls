package com.mrfuzzihead.fuzzicontrols.controller;

import org.hid4java.jna.HidApi;
import org.hid4java.jna.HidDeviceInfoStructure;
import org.hid4java.jna.HidDeviceStructure;

import com.mrfuzzihead.fuzzicontrols.FuzziControls;

/**
 * {@link DualSenseTransport} backed by hidapi through hid4java's low-level JNA bindings.
 *
 * <p>
 * Opens the native VID/PID filter directly ({@link HidApi#open(int, int, String)}) rather than
 * hid4java's {@code HidServices}, which enumerates every HID device on the system and starts a
 * 500 ms background scanner thread. Both the DualSense ({@code 0x0CE6}) and DualSense Edge
 * ({@code 0x0DF2}) product IDs are tried.
 */
final class HidApiDualSenseTransport implements DualSenseTransport {

    private static final int VENDOR_ID = 0x054C;
    private static final int DUAL_SENSE_PRODUCT_ID = 0x0CE6;
    private static final int DUAL_SENSE_EDGE_PRODUCT_ID = 0x0DF2;

    private static final int[] PRODUCT_IDS = { DUAL_SENSE_PRODUCT_ID, DUAL_SENSE_EDGE_PRODUCT_ID };

    /** Largest output report is the 78-byte Bluetooth report; hid4java expects the body without ID. */
    private static final int MAX_OUTPUT_REPORT_BODY_SIZE = 77;

    private final byte[] outputReportBody = new byte[MAX_OUTPUT_REPORT_BODY_SIZE];

    /** One-time diagnostic so a present-but-unopenable device is visible in the log. */
    private static boolean openDiagnosticLogged;

    private HidDeviceStructure handle;

    private int productId = -1;

    @Override
    public boolean open() {
        close();

        for (int candidate : PRODUCT_IDS) {
            try {
                handle = HidApi.open(VENDOR_ID, candidate, null);
            } catch (Throwable t) {
                // JNA/hidapi load failures can be Errors, not Exceptions.
                handle = null;
                FuzziControls.LOG.debug(
                    "[FuzziControls] DualSense open failed for PID 0x{}: {}",
                    Integer.toHexString(candidate),
                    t.toString());
            }

            if (handle != null) {
                productId = candidate;
                openDiagnosticLogged = false;
                return true;
            }
        }

        logOpenDiagnostic();
        return false;
    }

    @Override
    public int read(byte[] buffer) {
        if (handle == null) return -1;
        return HidApi.read(handle, buffer, 0);
    }

    @Override
    public int readFeatureReport(byte[] buffer, int reportId) {
        if (handle == null) return -1;
        return HidApi.getFeatureReport(handle, buffer, (byte) reportId);
    }

    @Override
    public int writeOutputReport(byte[] report) {
        if (handle == null || report == null || report.length < 2) return -1;

        int bodyLength = report.length - 1;
        if (bodyLength > outputReportBody.length) return -1;

        // hid4java's low-level write prepends the report ID itself and copies from offset 0, so
        // pass the body without the ID and it separately.
        System.arraycopy(report, 1, outputReportBody, 0, bodyLength);
        return HidApi.write(handle, outputReportBody, bodyLength, report[0]);
    }

    @Override
    public boolean isOpen() {
        return handle != null;
    }

    @Override
    public void close() {
        if (handle == null) return;

        try {
            HidApi.close(handle);
        } catch (Throwable t) {
            FuzziControls.LOG.debug("[FuzziControls] DualSense device close error: {}", t.toString());
        }

        handle = null;
        productId = -1;
    }

    @Override
    public String getDeviceName() {
        return productId == DUAL_SENSE_EDGE_PRODUCT_ID ? "DualSense Edge" : "DualSense";
    }

    @Override
    public boolean isEdge() {
        return productId == DUAL_SENSE_EDGE_PRODUCT_ID;
    }

    @Override
    public int getUsbOutputReportSize() {
        return isEdge() ? DualSenseOutputReport.EDGE_USB_REPORT_SIZE : DualSenseOutputReport.STANDARD_USB_REPORT_SIZE;
    }

    @Override
    public String getVersion() {
        return getVersionStatic();
    }

    /**
     * Logs once whether a matching HID device exists but could not be opened. This is the most
     * useful signal for Bluetooth failures: the controller can appear in the OS device list while
     * hidapi is denied access or cannot open that transport.
     */
    private static synchronized void logOpenDiagnostic() {
        if (openDiagnosticLogged) return;
        openDiagnosticLogged = true;

        try {
            int found = 0;
            for (int productId : PRODUCT_IDS) {
                HidDeviceInfoStructure root = HidApi.enumerateDevices(VENDOR_ID, productId);
                if (root == null) continue;

                try {
                    HidDeviceInfoStructure device = root;
                    while (device != null) {
                        found++;
                        device = device.next();
                    }
                } finally {
                    HidApi.freeEnumeration(root);
                }
            }

            if (found > 0) {
                FuzziControls.LOG.warn(
                    "[FuzziControls] {} DualSense HID interface(s) found but none could be opened. "
                        + "Another application may have exclusive access, or hidapi {} cannot open "
                        + "this transport (Bluetooth is the usual suspect).",
                    found,
                    getVersionStatic());
            } else {
                FuzziControls.LOG
                    .debug("[FuzziControls] No DualSense HID device found (hidapi {}).", getVersionStatic());
            }
        } catch (Throwable t) {
            FuzziControls.LOG.debug("[FuzziControls] DualSense HID enumeration failed: {}", t.toString());
        }
    }

    private static String getVersionStatic() {
        try {
            String version = HidApi.getVersion();
            return version != null ? version : "unknown";
        } catch (Throwable t) {
            return "unknown";
        }
    }
}
