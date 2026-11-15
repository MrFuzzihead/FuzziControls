package com.mrfuzzihead.fuzzicontrols.controller;

import java.util.EnumSet;
import java.util.Set;

import org.hid4java.jna.HidApi;
import org.hid4java.jna.HidDeviceStructure;

import com.mrfuzzihead.fuzzicontrols.FuzziControls;

/**
 * DualSense controller driver using raw HID reports.
 *
 * <p>
 * <b>Why the low-level hidapi API?</b> hid4java's {@code HidServices.getHidDevice(vid, pid, ...)}
 * first calls {@code hid_enumerate(0, 0)}: it enumerates <em>every</em> HID device on the system and
 * only then filters in Java. On a Windows machine with a normal set of keyboards, mice, headsets and
 * RGB devices that measured ~500 ms per probe, which used to block the client tick thread every
 * 3 seconds (see {@code docs/REFACTOR_PLAN.md} section 2). {@code HidApi.open(vid, pid, null)} lets
 * hidapi apply the vendor/product filter natively; it measured ~2 ms when no controller is present
 * and ~14 ms when one is, and it does not start hid4java's 500 ms background scanner thread.
 *
 * <p>
 * <b>Report format:</b> only the USB input report {@code 0x01} (64 bytes) is parsed. A Bluetooth
 * DualSense uses report {@code 0x31} (78 bytes, CRC32) and a DualSense Edge uses PID {@code 0x0DF2};
 * neither is supported yet. Non-USB reports are ignored rather than misparsed.
 *
 * <p>
 * <b>Lifecycle:</b> one raw handle per driver instance. {@link #close()} releases it. A read error
 * ({@code -1}) is treated as a disconnect rather than "no new report", so an unplug closes the
 * handle and lets {@link ControllerManager} reconnect instead of returning a frozen button state.
 */
public class DualSenseDriver implements IControllerDriver {

    private static final int VENDOR_ID = 0x054C;
    private static final int PRODUCT_ID = 0x0CE6;

    /** The only input report this parser understands (USB, 64 bytes). Bluetooth uses 0x31. */
    private static final int USB_INPUT_REPORT_ID = 0x01;

    private static final int REPORT_SIZE = 64;
    private static final int REPORT_MIN_BYTES = 10;

    /** {@code HidApi.init()} is process-wide and only needs to run once. */
    private static boolean hidInitialised;

    /** A failed native load is permanent for this JVM; remember it instead of retrying every probe. */
    private static boolean hidInitFailed;

    /**
     * Initialises the native hidapi library once.
     *
     * @return true when the library is ready for {@link HidApi#open(int, int, String)}
     */
    private static synchronized boolean ensureHid() {
        if (hidInitialised) return true;
        if (hidInitFailed) return false;

        try {
            HidApi.init();
            hidInitialised = true;
            return true;
        } catch (Throwable t) {
            // JNA/hidapi load failures are often Errors (UnsatisfiedLinkError), not Exceptions.
            hidInitFailed = true;
            FuzziControls.LOG.warn(
                "[FuzziControls] hidapi is unavailable ({}). DualSense support is disabled; "
                    + "XInput may still work on Windows.",
                t.toString());
            return false;
        }
    }

    /** Releases the process-wide hidapi library. Called from the JVM shutdown hook. */
    public static synchronized void shutdownHid() {
        if (!hidInitialised) return;
        try {
            HidApi.exit();
        } catch (Throwable t) {
            FuzziControls.LOG.debug("[FuzziControls] hidapi shutdown error: {}", t.toString());
        }
        hidInitialised = false;
        hidInitFailed = false;
    }

    /** Raw hidapi handle, or null when no controller is open. */
    private HidDeviceStructure handle;

    /** Reused across polls; the parser consumes it before the next read. */
    private final byte[] reportBuffer = new byte[REPORT_SIZE];

    private final LastReportCache reportCache = new LastReportCache();

    public DualSenseDriver() {
        if (!ensureHid()) return;

        try {
            // Native VID/PID filter: ~2 ms when absent, ~14 ms when present. No full enumeration,
            // no background scanner, no second open() (HidServices.open() leaked the first handle).
            handle = HidApi.open(VENDOR_ID, PRODUCT_ID, null);
            if (handle != null) {
                FuzziControls.LOG.info("[FuzziControls] DualSense controller detected and opened.");
            } else {
                FuzziControls.LOG.debug("[FuzziControls] DualSense not found; driver standby.");
            }
        } catch (Throwable t) {
            FuzziControls.LOG.warn("[FuzziControls] DualSense open failed: {}", t.toString());
            handle = null;
        }
    }

    @Override
    public boolean isConnected() {
        return handle != null;
    }

    @Override
    public ControllerState poll(float deadZone, float triggerThreshold) {
        if (handle == null) return ControllerState.empty();

        try {
            // Drain every report that is waiting; the last one is the freshest state.
            ControllerState fresh = null;
            int read;
            while ((read = HidApi.read(handle, reportBuffer, 0)) >= REPORT_MIN_BYTES) {
                if ((reportBuffer[0] & 0xFF) != USB_INPUT_REPORT_ID) {
                    // Bluetooth report (0x31) or a different report: not ours, do not misparse it.
                    continue;
                }
                fresh = parseReport(reportBuffer, deadZone, triggerThreshold);
            }

            if (read < 0) {
                // -1 is an error (unplug, invalid handle), not "no data" (0). Keeping the cached
                // state here would leave buttons stuck forever because isConnected() stayed true.
                FuzziControls.LOG.debug("[FuzziControls] DualSense read error; releasing the device.");
                close();
                return ControllerState.empty();
            }

            return reportCache.record(fresh);
        } catch (Throwable t) {
            FuzziControls.LOG.debug("[FuzziControls] DualSense poll failed: {}", t.toString());
            close();
            return ControllerState.empty();
        }
    }

    /**
     * Decodes one HID input report.
     *
     * <p>
     * Package-private so the report decoding (axis scaling, D-pad hat masks, button bits) can be
     * unit tested without hardware.
     *
     * @param data             report bytes; must contain at least {@value #REPORT_MIN_BYTES} bytes
     * @param deadZone         stick dead-zone passed to {@link ControllerState#normaliseAxis}
     * @param triggerThreshold trigger threshold passed to {@link ControllerState#normaliseTrigger}
     * @return the decoded state
     */
    static ControllerState parseReport(byte[] data, float deadZone, float triggerThreshold) {
        float lx = byteToAxis(data[1]);
        float ly = byteToAxis(data[2]);
        float rx = byteToAxis(data[3]);
        float ry = byteToAxis(data[4]);
        float rawLt = byteToTrigger(data[5]);
        float rawRt = byteToTrigger(data[6]);

        lx = ControllerState.normaliseAxis(lx, deadZone);
        ly = ControllerState.normaliseAxis(ly, deadZone);
        rx = ControllerState.normaliseAxis(rx, deadZone);
        ry = ControllerState.normaliseAxis(ry, deadZone);
        float lt = ControllerState.normaliseTrigger(rawLt, triggerThreshold);
        float rt = ControllerState.normaliseTrigger(rawRt, triggerThreshold);

        int btnByte1 = data[8] & 0xFF;
        int btnByte2 = data[9] & 0xFF;
        int dpad = btnByte1 & 0x0F;

        Set<ControllerButton> pressed = EnumSet.noneOf(ControllerButton.class);

        if ((btnByte1 & 0x10) != 0) pressed.add(ControllerButton.X);
        if ((btnByte1 & 0x20) != 0) pressed.add(ControllerButton.A);
        if ((btnByte1 & 0x40) != 0) pressed.add(ControllerButton.B);
        if ((btnByte1 & 0x80) != 0) pressed.add(ControllerButton.Y);

        if ((btnByte2 & 0x01) != 0) pressed.add(ControllerButton.LEFT_BUMPER);
        if ((btnByte2 & 0x02) != 0) pressed.add(ControllerButton.RIGHT_BUMPER);
        if (rawLt >= triggerThreshold) pressed.add(ControllerButton.LEFT_TRIGGER);
        if (rawRt >= triggerThreshold) pressed.add(ControllerButton.RIGHT_TRIGGER);

        if ((btnByte2 & 0x10) != 0) pressed.add(ControllerButton.BACK);
        if ((btnByte2 & 0x20) != 0) pressed.add(ControllerButton.START);
        if ((btnByte2 & 0x40) != 0) pressed.add(ControllerButton.LEFT_STICK_CLICK);
        if ((btnByte2 & 0x80) != 0) pressed.add(ControllerButton.RIGHT_STICK_CLICK);

        if (dpad == 0 || dpad == 1 || dpad == 7) pressed.add(ControllerButton.DPAD_UP);
        if (dpad == 2 || dpad == 1 || dpad == 3) pressed.add(ControllerButton.DPAD_RIGHT);
        if (dpad == 4 || dpad == 3 || dpad == 5) pressed.add(ControllerButton.DPAD_DOWN);
        if (dpad == 6 || dpad == 5 || dpad == 7) pressed.add(ControllerButton.DPAD_LEFT);

        return new ControllerState(lx, ly, rx, ry, lt, rt, pressed);
    }

    @Override
    public void close() {
        if (handle != null) {
            try {
                HidApi.close(handle);
            } catch (Throwable t) {
                FuzziControls.LOG.debug("[FuzziControls] DualSense device close error: {}", t.toString());
            }
            handle = null;
        }
        reportCache.clear();
    }

    @Override
    public String getDriverName() {
        return "DualSense (HID)";
    }

    private static float byteToAxis(byte b) {
        int unsigned = b & 0xFF;
        return (unsigned - 128) / 127.5f;
    }

    private static float byteToTrigger(byte b) {
        return (b & 0xFF) / 255.0f;
    }
}
