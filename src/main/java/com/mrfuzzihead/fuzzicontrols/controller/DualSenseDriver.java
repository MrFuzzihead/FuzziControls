package com.mrfuzzihead.fuzzicontrols.controller;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

import org.hid4java.jna.HidApi;

import com.mrfuzzihead.fuzzicontrols.FuzziControls;

/**
 * DualSense controller driver using raw HID reports.
 *
 * <p>
 * <b>Why the low-level hidapi API?</b> hid4java's {@code HidServices.getHidDevice(vid, pid, ...)}
 * first calls {@code hid_enumerate(0, 0)}: it enumerates <em>every</em> HID device on the system and
 * only then filters in Java. On a Windows machine with a normal set of keyboards, mice, headsets and
 * RGB devices that measured ~500 ms per probe, which used to block the client tick thread every
 * 3 seconds (see {@code docs/REFACTOR_PLAN.md} section 2). {@link HidApi#open(int, int, String)}
 * lets hidapi apply the vendor/product filter natively; it measured ~2 ms when no controller is
 * present and ~14 ms when one is, and it does not start hid4java's 500 ms background scanner thread.
 *
 * <p>
 * <b>Report formats:</b> both USB and Bluetooth DualSense/DualSense Edge controllers are supported.
 * USB uses report {@code 0x01} (64 bytes). Bluetooth starts with a simple {@code 0x01} report
 * (10 bytes) and switches to an enhanced {@code 0x31} report (78 bytes, CRC32) after a feature
 * report is read. {@link DualSenseReportParser} decodes all three layouts. Reading feature report
 * {@code 0x05} on open requests the enhanced Bluetooth report; if that fails, the simple report is
 * still fully usable for sticks, buttons and triggers.
 *
 * <p>
 * <b>Lifecycle:</b> one transport per driver instance. {@link #close()} releases it. A read error
 * ({@code -1}) is treated as a disconnect rather than "no new report", so an unplug closes the
 * handle and lets {@link ControllerManager} reconnect instead of returning a frozen button state.
 * Bluetooth devices can stop delivering reports without returning an error, so the driver also
 * considers a handle stale after {@value #BLUETOOTH_TIMEOUT_SECONDS} seconds without a report.
 */
public class DualSenseDriver implements IControllerDriver {

    /** The only feature report this driver reads; also switches Bluetooth to the enhanced report. */
    private static final int FEATURE_CALIBRATION_REPORT_ID = 0x05;

    private static final int FEATURE_REPORT_BUFFER_SIZE = 64;

    /** Bluetooth enhanced reports are 78 bytes; leave room for a padded simple report too. */
    private static final int REPORT_BUFFER_SIZE = 128;

    private static final int USB_TIMEOUT_SECONDS = 5;

    private static final int BLUETOOTH_TIMEOUT_SECONDS = 2;

    private static final long USB_TIMEOUT_NANOS = TimeUnit.SECONDS.toNanos(USB_TIMEOUT_SECONDS);

    private static final long BLUETOOTH_TIMEOUT_NANOS = TimeUnit.SECONDS.toNanos(BLUETOOTH_TIMEOUT_SECONDS);

    /** How the controller is currently connected; unknown until the first report. */
    private enum ConnectionType {
        UNKNOWN,
        USB,
        BLUETOOTH
    }

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

    private final DualSenseTransport transport;

    private final LongSupplier nanoClock;

    /** Reused across polls; the parser consumes it before the next read. */
    private final byte[] reportBuffer = new byte[REPORT_BUFFER_SIZE];

    /** Reused for the one-off calibration/enhanced-mode feature report. */
    private final byte[] featureBuffer = new byte[FEATURE_REPORT_BUFFER_SIZE];

    private final LastReportCache reportCache = new LastReportCache();

    /** Timestamp of the last report (or the open, before the first report). */
    private long lastReportNanos;

    private ConnectionType connectionType = ConnectionType.UNKNOWN;

    /** Whether we have already retried the enhanced-mode feature report after a simple BT report. */
    private boolean enhancedModeRetried;

    /** Whether the detected transport has been logged for this connection. */
    private boolean transportLogged;

    /** True after the first parsed input report; a successfully opened stale handle never sets it. */
    private boolean receivedValidReport;

    /** Rate-limits the diagnostic log for an unsupported report. */
    private boolean unknownReportLogged;

    /** Rate-limits the diagnostic log for a CRC mismatch. */
    private boolean crcMismatchLogged;

    /** Bluetooth output reports carry a wrapping 4-bit sequence number. */
    private int outputSequence;

    /** Last motor values written, so repeated identical pulses do not spam HID writes. */
    private int lastRumbleLeft = -1;

    private int lastRumbleRight = -1;

    /** Last adaptive-trigger blocks, so unchanged effects do not rewrite the output report. */
    private byte[] lastLeftTrigger;

    private byte[] lastRightTrigger;

    /** Last lightbar state, so the per-tick apply call does not spam HID writes. */
    private int lastLightbarRed = -1;

    private int lastLightbarGreen = -1;

    private int lastLightbarBlue = -1;

    private int lastLightbarBrightness = -1;

    /** Latest battery reading from the input report; -1 until a report with status bytes arrives. */
    private int batteryPercent = -1;

    private boolean batteryCharging;

    /** Rate-limits a failing output-report write. */
    private boolean outputWriteFailedLogged;

    /** Whether a battery value has been logged for this connection. */
    private boolean batteryLogged;

    public DualSenseDriver() {
        this(ensureHid() ? new HidApiDualSenseTransport() : null, System::nanoTime);
    }

    /** Test seam: a scripted transport with no hidapi/hardware. */
    DualSenseDriver(DualSenseTransport transport) {
        this(transport, System::nanoTime);
    }

    /** Test seam: a scripted transport and clock. */
    DualSenseDriver(DualSenseTransport transport, LongSupplier nanoClock) {
        this.transport = transport;
        this.nanoClock = nanoClock != null ? nanoClock : System::nanoTime;

        if (transport == null) return;

        try {
            if (!transport.open()) {
                FuzziControls.LOG.debug("[FuzziControls] DualSense/DualSense Edge not found; driver standby.");
                return;
            }

            lastReportNanos = this.nanoClock.getAsLong();
            FuzziControls.LOG.info(
                "[FuzziControls] {} controller detected and opened (hidapi {}).",
                transport.getDeviceName(),
                transport.getVersion());

            // Read the calibration report once. On Bluetooth this also switches the controller
            // from the simple 0x01 report to the enhanced 0x31 report. Failure is non-fatal: the
            // simple report still carries sticks, buttons and triggers.
            requestEnhancedReports();
        } catch (Throwable t) {
            FuzziControls.LOG.warn("[FuzziControls] DualSense open failed: {}", t.toString());
            close();
        }
    }

    @Override
    public boolean isConnected() {
        if (transport == null || !transport.isOpen()) return false;

        // A Bluetooth device can stop delivering reports without returning an error. Treat a long
        // gap as a disconnect so the manager releases the handle instead of returning a frozen
        // button state forever.
        if (lastReportNanos == 0L) return true;

        long timeout = connectionType == ConnectionType.BLUETOOTH ? BLUETOOTH_TIMEOUT_NANOS : USB_TIMEOUT_NANOS;
        long elapsed = nanoClock.getAsLong() - lastReportNanos;
        if (elapsed > timeout) {
            FuzziControls.LOG.debug(
                "[FuzziControls] No DualSense report for {} ms; treating the device as disconnected.",
                TimeUnit.NANOSECONDS.toMillis(elapsed));
            return false;
        }

        return true;
    }

    @Override
    public ControllerState poll(float deadZone, float triggerThreshold) {
        if (transport == null || !transport.isOpen()) return ControllerState.empty();

        try {
            // Drain every report that is waiting; the last one is the freshest state.
            ControllerState fresh = null;
            int read;
            while ((read = transport.read(reportBuffer)) > 0) {
                // Any report proves the handle is still alive, even if we cannot parse it.
                lastReportNanos = nanoClock.getAsLong();

                DualSenseReportParser.Layout layout = DualSenseReportParser.detect(reportBuffer, read);
                if (layout == DualSenseReportParser.Layout.UNKNOWN) {
                    logUnknownReportOnce(read);
                    continue;
                }

                if (layout == DualSenseReportParser.Layout.BLUETOOTH_ENHANCED_0X31
                    && !DualSenseReportParser.hasValidBluetoothCrc(reportBuffer, read)) {
                    logCrcMismatchOnce();
                    continue;
                }

                receivedValidReport = true;

                if (layout == DualSenseReportParser.Layout.USB_0X01) {
                    connectionType = ConnectionType.USB;
                } else {
                    connectionType = ConnectionType.BLUETOOTH;

                    // The open-time feature report can arrive before the controller is ready.
                    // Retry once after the first simple Bluetooth report; the next report should
                    // then use the enhanced layout.
                    if (layout == DualSenseReportParser.Layout.BLUETOOTH_SIMPLE_0X01 && !enhancedModeRetried) {
                        enhancedModeRetried = true;
                        requestEnhancedReports();
                    }
                }

                if (!transportLogged) {
                    transportLogged = true;
                    FuzziControls.LOG
                        .info("[FuzziControls] DualSense reports are using the {} transport.", getTransportName());
                }

                DualSenseReportParser.Battery battery = DualSenseReportParser.parseBattery(reportBuffer, read, layout);
                if (battery != null) {
                    batteryPercent = battery.percent();
                    batteryCharging = battery.charging();
                    if (!batteryLogged) {
                        batteryLogged = true;
                        FuzziControls.LOG.info(
                            "[FuzziControls] DualSense battery: {}%{}.",
                            batteryPercent,
                            batteryCharging ? " (charging)" : "");
                    }
                }

                fresh = DualSenseReportParser
                    .parse(reportBuffer, read, layout, deadZone, triggerThreshold, transport.isEdge());
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

    @Override
    public void setRumble(float left, float right) {
        if (!canWriteOutput()) return;

        int leftMotor = toMotor(left);
        int rightMotor = toMotor(right);
        if (leftMotor == lastRumbleLeft && rightMotor == lastRumbleRight) return;

        byte[] report = connectionType == ConnectionType.BLUETOOTH
            ? DualSenseOutputReport.rumbleBluetooth(outputSequence, leftMotor, rightMotor)
            : DualSenseOutputReport.rumbleUsb(leftMotor, rightMotor);

        if (!writeReport(report)) return;
        lastRumbleLeft = leftMotor;
        lastRumbleRight = rightMotor;
    }

    @Override
    public void setTriggerEffect(ControllerTrigger trigger, ControllerTriggerEffect effect, int strength) {
        if (!canWriteOutput() || trigger == null) return;

        byte[] block = DualSenseTriggerEffect.from(effect, strength);
        boolean left = trigger == ControllerTrigger.LEFT;
        byte[] previous = left ? lastLeftTrigger : lastRightTrigger;
        if (Arrays.equals(previous, block)) return;

        byte[] leftBlock = left ? block : lastLeftTrigger;
        byte[] rightBlock = left ? lastRightTrigger : block;
        if (leftBlock == null) leftBlock = DualSenseTriggerEffect.off();
        if (rightBlock == null) rightBlock = DualSenseTriggerEffect.off();

        byte[] report = connectionType == ConnectionType.BLUETOOTH
            ? DualSenseOutputReport.triggersBluetooth(outputSequence, leftBlock, rightBlock)
            : DualSenseOutputReport.triggersUsb(leftBlock, rightBlock);

        if (!writeReport(report)) return;
        lastLeftTrigger = leftBlock;
        lastRightTrigger = rightBlock;
    }

    @Override
    public void setLightbar(int red, int green, int blue, ControllerLightbarBrightness brightness) {
        if (!canWriteOutput()) return;

        int r = clampColor(red);
        int g = clampColor(green);
        int b = clampColor(blue);
        int level = brightness != null ? brightness.getValue() : ControllerLightbarBrightness.MEDIUM.getValue();
        if (r == lastLightbarRed && g == lastLightbarGreen
            && b == lastLightbarBlue
            && level == lastLightbarBrightness) {
            return;
        }

        byte[] report = connectionType == ConnectionType.BLUETOOTH
            ? DualSenseOutputReport.lightbarBluetooth(outputSequence, r, g, b, level)
            : DualSenseOutputReport.lightbarUsb(r, g, b, level);

        if (!writeReport(report)) return;
        lastLightbarRed = r;
        lastLightbarGreen = g;
        lastLightbarBlue = b;
        lastLightbarBrightness = level;
    }

    @Override
    public int getBatteryPercent() {
        return batteryPercent;
    }

    @Override
    public boolean isBatteryCharging() {
        return batteryCharging;
    }

    @Override
    public void close() {
        if (transport != null) {
            try {
                transport.close();
            } catch (Throwable t) {
                FuzziControls.LOG.debug("[FuzziControls] DualSense device close error: {}", t.toString());
            }
        }

        reportCache.clear();
        lastReportNanos = 0L;
        connectionType = ConnectionType.UNKNOWN;
        enhancedModeRetried = false;
        transportLogged = false;
        receivedValidReport = false;
        unknownReportLogged = false;
        crcMismatchLogged = false;
        outputSequence = 0;
        lastRumbleLeft = -1;
        lastRumbleRight = -1;
        lastLeftTrigger = null;
        lastRightTrigger = null;
        lastLightbarRed = -1;
        lastLightbarGreen = -1;
        lastLightbarBlue = -1;
        lastLightbarBrightness = -1;
        batteryPercent = -1;
        batteryCharging = false;
        outputWriteFailedLogged = false;
        batteryLogged = false;
    }

    /** True when an output report can be sent on a known transport. */
    private boolean canWriteOutput() {
        return transport != null && transport.isOpen() && connectionType != ConnectionType.UNKNOWN;
    }

    /** Sends one output report, advancing the Bluetooth sequence only on success. */
    private boolean writeReport(byte[] report) {
        int written;
        try {
            written = transport.writeOutputReport(report);
        } catch (Throwable t) {
            written = -1;
        }

        if (written < 0) {
            if (!outputWriteFailedLogged) {
                outputWriteFailedLogged = true;
                FuzziControls.LOG.debug("[FuzziControls] DualSense output report failed; ignoring.");
            }
            return false;
        }

        if (connectionType == ConnectionType.BLUETOOTH) {
            outputSequence = (outputSequence + 1) & 0x0F;
        }
        return true;
    }

    /** Converts a normalised motor value to the DualSense's 0–255 range. */
    private static int toMotor(float value) {
        float clamped = Math.max(0f, Math.min(1f, value));
        return Math.round(clamped * 255f);
    }

    private static int clampColor(int value) {
        return Math.max(0, Math.min(255, value));
    }

    @Override
    public String getDriverName() {
        return "DualSense (HID)";
    }

    @Override
    public String getDeviceName() {
        return transport != null ? transport.getDeviceName() : "DualSense";
    }

    @Override
    public String getTransportName() {
        return switch (connectionType) {
            case USB -> "USB";
            case BLUETOOTH -> "Bluetooth";
            case UNKNOWN -> "";
        };
    }

    @Override
    public boolean isConnectionConfirmed() {
        return receivedValidReport;
    }

    private void requestEnhancedReports() {
        if (transport == null || !transport.isOpen()) return;

        try {
            int read = transport.readFeatureReport(featureBuffer, FEATURE_CALIBRATION_REPORT_ID);
            if (read < 0) {
                FuzziControls.LOG.debug(
                    "[FuzziControls] DualSense calibration feature report unavailable; "
                        + "continuing with input reports.");
            }
        } catch (Throwable t) {
            FuzziControls.LOG.debug("[FuzziControls] DualSense feature report failed: {}", t.toString());
        }
    }

    private void logUnknownReportOnce(int length) {
        if (unknownReportLogged) return;
        unknownReportLogged = true;
        FuzziControls.LOG.debug(
            "[FuzziControls] Ignoring unsupported DualSense report id=0x{} length={}.",
            Integer.toHexString(reportBuffer[0] & 0xFF),
            length);
    }

    private void logCrcMismatchOnce() {
        if (crcMismatchLogged) return;
        crcMismatchLogged = true;
        FuzziControls.LOG.debug(
            "[FuzziControls] Ignoring DualSense Bluetooth report with an invalid CRC32; "
                + "check the connection if this repeats.");
    }
}
