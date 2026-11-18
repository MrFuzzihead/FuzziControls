package com.mrfuzzihead.fuzzicontrols.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.CRC32;

import org.junit.Test;

/**
 * Tests the {@link DualSenseDriver} read loop, transport lifecycle and stale-handle handling with a
 * scripted {@link DualSenseTransport} (no hidapi or hardware).
 */
public class DualSenseDriverTest {

    private static final float DEAD_ZONE = 0.15f;

    private static final float TRIGGER_THRESHOLD = 0.2f;

    private static final long START_NANOS = TimeUnit.SECONDS.toNanos(1);

    /** Scripted transport: queues reports and records feature-report calls. */
    private static final class FakeTransport implements DualSenseTransport {

        private final Deque<byte[]> reports = new ArrayDeque<>();

        private boolean open;

        private boolean openResult = true;

        private boolean throwOnOpen;

        private boolean throwOnFeatureReport;

        private boolean errorNextRead;

        private boolean throwOnRead;

        private int featureReads;

        private int lastFeatureReportId = -1;

        private int closeCount;

        private final List<byte[]> outputReports = new ArrayList<>();

        private boolean failOutputReport;

        private String deviceName = "DualSense";

        private boolean edge;

        FakeTransport enqueue(byte[]... newReports) {
            for (byte[] report : newReports) {
                reports.add(report);
            }
            return this;
        }

        @Override
        public boolean open() {
            if (throwOnOpen) throw new IllegalStateException("open boom");
            open = openResult;
            return open;
        }

        @Override
        public int read(byte[] buffer) {
            if (!open) return -1;
            if (throwOnRead) throw new IllegalStateException("read boom");
            if (errorNextRead) {
                errorNextRead = false;
                return -1;
            }

            byte[] report = reports.poll();
            if (report == null) return 0;

            System.arraycopy(report, 0, buffer, 0, report.length);
            return report.length;
        }

        @Override
        public int readFeatureReport(byte[] buffer, int reportId) {
            featureReads++;
            lastFeatureReportId = reportId;
            if (throwOnFeatureReport) throw new IllegalStateException("feature boom");
            return 41;
        }

        @Override
        public int writeOutputReport(byte[] report) {
            if (failOutputReport) return -1;
            outputReports.add(report.clone());
            return report.length;
        }

        @Override
        public boolean isOpen() {
            return open;
        }

        @Override
        public void close() {
            open = false;
            closeCount++;
        }

        @Override
        public String getDeviceName() {
            return deviceName;
        }

        @Override
        public boolean isEdge() {
            return edge;
        }

        @Override
        public String getVersion() {
            return "test";
        }
    }

    // -------------------------------------------------------------------------
    // Open / feature report
    // -------------------------------------------------------------------------

    @Test
    public void openReadsTheCalibrationFeatureReport() {
        FakeTransport transport = new FakeTransport();

        DualSenseDriver driver = new DualSenseDriver(transport, System::nanoTime);

        assertTrue(driver.isConnected());
        assertFalse("opening a handle is not proof that reports are arriving", driver.isConnectionConfirmed());
        assertEquals(1, transport.featureReads);
        assertEquals(0x05, transport.lastFeatureReportId);
        assertEquals("", driver.getTransportName());
    }

    @Test
    public void featureReportFailureDoesNotPreventInput() {
        FakeTransport transport = new FakeTransport();
        transport.throwOnFeatureReport = true;
        transport.enqueue(usbReport());

        DualSenseDriver driver = new DualSenseDriver(transport, System::nanoTime);
        ControllerState state = driver.poll(DEAD_ZONE, TRIGGER_THRESHOLD);

        assertTrue(state.isPressed(ControllerButton.A));
        assertEquals("USB", driver.getTransportName());
    }

    @Test
    public void openFailureLeavesTheDriverInactive() {
        FakeTransport transport = new FakeTransport();
        transport.openResult = false;

        DualSenseDriver driver = new DualSenseDriver(transport, System::nanoTime);

        assertFalse(driver.isConnected());
        assertFalse(driver.isConnectionConfirmed());
        assertEquals(ControllerState.empty(), driver.poll(DEAD_ZONE, TRIGGER_THRESHOLD));
        assertEquals("No calibration read after a failed open", 0, transport.featureReads);
    }

    @Test
    public void openExceptionIsCaughtAndClosesTheTransport() {
        FakeTransport transport = new FakeTransport();
        transport.throwOnOpen = true;

        DualSenseDriver driver = new DualSenseDriver(transport, System::nanoTime);

        assertFalse(driver.isConnected());
        assertEquals(1, transport.closeCount);
    }

    @Test
    public void nullTransportIsSafe() {
        DualSenseDriver driver = new DualSenseDriver(null, System::nanoTime);

        assertFalse(driver.isConnected());
        assertEquals(ControllerState.empty(), driver.poll(DEAD_ZONE, TRIGGER_THRESHOLD));
        assertEquals("DualSense", driver.getDeviceName());
        assertEquals("", driver.getTransportName());
        driver.close();
    }

    @Test
    public void deviceNameIsForwardedToTheDriver() {
        FakeTransport transport = new FakeTransport();
        transport.deviceName = "DualSense Edge";

        DualSenseDriver driver = new DualSenseDriver(transport, System::nanoTime);

        assertEquals("DualSense Edge", driver.getDeviceName());
    }

    @Test
    public void edgeExtraButtonsArePassedThroughFromTheTransport() {
        FakeTransport transport = new FakeTransport();
        transport.deviceName = "DualSense Edge";
        transport.edge = true;
        byte[] report = usbReport();
        report[10] = (byte) 0xF0;
        transport.enqueue(report);

        DualSenseDriver driver = new DualSenseDriver(transport, System::nanoTime);
        ControllerState state = driver.poll(DEAD_ZONE, TRIGGER_THRESHOLD);

        assertTrue(state.isPressed(ControllerButton.LEFT_PADDLE));
        assertTrue(state.isPressed(ControllerButton.RIGHT_PADDLE));
        assertTrue(state.isPressed(ControllerButton.FUNCTION_1));
        assertTrue(state.isPressed(ControllerButton.FUNCTION_2));
    }

    @Test
    public void standardDualSenseIgnoresTheEdgeButtonBits() {
        FakeTransport transport = new FakeTransport();
        byte[] report = usbReport();
        report[10] = (byte) 0xF0;
        transport.enqueue(report);

        DualSenseDriver driver = new DualSenseDriver(transport, System::nanoTime);
        ControllerState state = driver.poll(DEAD_ZONE, TRIGGER_THRESHOLD);

        assertFalse(state.isPressed(ControllerButton.LEFT_PADDLE));
        assertFalse(state.isPressed(ControllerButton.RIGHT_PADDLE));
        assertFalse(state.isPressed(ControllerButton.FUNCTION_1));
        assertFalse(state.isPressed(ControllerButton.FUNCTION_2));
    }

    // -------------------------------------------------------------------------
    // Rumble output reports
    // -------------------------------------------------------------------------

    @Test
    public void usbRumbleWritesAUsbOutputReport() {
        FakeTransport transport = new FakeTransport();
        transport.enqueue(usbReport());

        DualSenseDriver driver = new DualSenseDriver(transport, System::nanoTime);
        driver.poll(DEAD_ZONE, TRIGGER_THRESHOLD);
        driver.setRumble(1f, 0.5f);

        assertEquals(1, transport.outputReports.size());
        byte[] report = transport.outputReports.get(0);
        assertEquals(DualSenseOutputReport.USB_REPORT_SIZE, report.length);
        assertEquals(0x02, report[0] & 0xFF);
        assertEquals(255, report[4] & 0xFF);
        assertEquals(128, report[3] & 0xFF);
    }

    @Test
    public void bluetoothRumbleUsesSequencedReportsWithValidCrc() {
        FakeTransport transport = new FakeTransport();
        transport.enqueue(enhancedReport());

        DualSenseDriver driver = new DualSenseDriver(transport, System::nanoTime);
        driver.poll(DEAD_ZONE, TRIGGER_THRESHOLD);

        driver.setRumble(1f, 0f);
        driver.setRumble(0f, 1f);

        assertEquals(2, transport.outputReports.size());
        byte[] first = transport.outputReports.get(0);
        byte[] second = transport.outputReports.get(1);
        assertEquals(0x31, first[0] & 0xFF);
        assertEquals("First Bluetooth output sequence is zero", 0x00, first[1] & 0xFF);
        assertEquals("Sequence increments for every report", 0x10, second[1] & 0xFF);
        assertTrue(DualSenseOutputReport.hasValidBluetoothCrc(first));
        assertTrue(DualSenseOutputReport.hasValidBluetoothCrc(second));
    }

    @Test
    public void identicalRumbleValuesDoNotRewrite() {
        FakeTransport transport = new FakeTransport();
        transport.enqueue(usbReport());

        DualSenseDriver driver = new DualSenseDriver(transport, System::nanoTime);
        driver.poll(DEAD_ZONE, TRIGGER_THRESHOLD);

        driver.setRumble(0.5f, 0.5f);
        driver.setRumble(0.5f, 0.5f);

        assertEquals(1, transport.outputReports.size());
    }

    @Test
    public void rumbleBeforeTheTransportIsKnownIsIgnored() {
        FakeTransport transport = new FakeTransport();

        DualSenseDriver driver = new DualSenseDriver(transport, System::nanoTime);
        driver.setRumble(1f, 1f);

        assertEquals(0, transport.outputReports.size());
    }

    @Test
    public void failedRumbleWriteDoesNotThrow() {
        FakeTransport transport = new FakeTransport();
        transport.enqueue(usbReport());
        transport.failOutputReport = true;

        DualSenseDriver driver = new DualSenseDriver(transport, System::nanoTime);
        driver.poll(DEAD_ZONE, TRIGGER_THRESHOLD);
        driver.setRumble(1f, 1f);

        assertEquals(0, transport.outputReports.size());
        assertTrue(driver.isConnected());
    }

    @Test
    public void batteryStatusIsExposedAfterAParsedReport() {
        FakeTransport transport = new FakeTransport();
        byte[] report = usbReport();
        report[53] = 0x15;
        transport.enqueue(report);

        DualSenseDriver driver = new DualSenseDriver(transport, System::nanoTime);
        assertEquals(-1, driver.getBatteryPercent());

        driver.poll(DEAD_ZONE, TRIGGER_THRESHOLD);

        assertEquals(55, driver.getBatteryPercent());
        assertTrue(driver.isBatteryCharging());
    }

    @Test
    public void adaptiveTriggerEffectsAreWrittenAndDeduplicated() {
        FakeTransport transport = new FakeTransport();
        transport.enqueue(usbReport());

        DualSenseDriver driver = new DualSenseDriver(transport, System::nanoTime);
        driver.poll(DEAD_ZONE, TRIGGER_THRESHOLD);

        driver.setTriggerEffect(ControllerTrigger.LEFT, ControllerTriggerEffect.BOW, 6);
        assertEquals(1, transport.outputReports.size());
        byte[] report = transport.outputReports.get(0);
        assertEquals(0x02, report[0] & 0xFF);
        assertEquals(
            DualSenseOutputReport.VALID_FLAG0_ALLOW_LEFT_TRIGGER_FFB
                | DualSenseOutputReport.VALID_FLAG0_ALLOW_RIGHT_TRIGGER_FFB,
            report[1] & 0xFF);
        assertEquals(0x22, report[1 + DualSenseOutputReport.L2_TRIGGER_OFFSET] & 0xFF);

        driver.setTriggerEffect(ControllerTrigger.LEFT, ControllerTriggerEffect.BOW, 6);
        assertEquals("Unchanged effects must not rewrite", 1, transport.outputReports.size());

        driver.setTriggerEffect(ControllerTrigger.RIGHT, ControllerTriggerEffect.FEEDBACK, 5);
        assertEquals(2, transport.outputReports.size());
        assertEquals(0x21, transport.outputReports.get(1)[1 + DualSenseOutputReport.R2_TRIGGER_OFFSET] & 0xFF);
    }

    @Test
    public void lightbarStateIsWrittenAndDeduplicated() {
        FakeTransport transport = new FakeTransport();
        transport.enqueue(usbReport());

        DualSenseDriver driver = new DualSenseDriver(transport, System::nanoTime);
        driver.poll(DEAD_ZONE, TRIGGER_THRESHOLD);

        driver.setLightbar(10, 20, 30, ControllerLightbarBrightness.LOW);
        assertEquals(1, transport.outputReports.size());
        byte[] report = transport.outputReports.get(0);
        assertEquals(10, report[45] & 0xFF);
        assertEquals(20, report[46] & 0xFF);
        assertEquals(30, report[47] & 0xFF);
        assertEquals(2, report[43] & 0xFF);

        driver.setLightbar(10, 20, 30, ControllerLightbarBrightness.LOW);
        assertEquals("Unchanged lightbar state must not rewrite", 1, transport.outputReports.size());
    }

    // -------------------------------------------------------------------------
    // Report routing
    // -------------------------------------------------------------------------

    @Test
    public void usbReportIsParsed() {
        FakeTransport transport = new FakeTransport();
        transport.enqueue(usbReport());

        DualSenseDriver driver = new DualSenseDriver(transport, System::nanoTime);
        ControllerState state = driver.poll(DEAD_ZONE, TRIGGER_THRESHOLD);

        assertTrue(state.isPressed(ControllerButton.A));
        assertTrue(state.isPressed(ControllerButton.LEFT_BUMPER));
        assertTrue("a parsed report confirms the connection", driver.isConnectionConfirmed());
        assertEquals("USB", driver.getTransportName());
        assertEquals("USB input must not trigger the Bluetooth enhanced-mode retry", 1, transport.featureReads);
    }

    @Test
    public void bluetoothSimpleReportIsParsedAndTriggersOneEnhancedModeRetry() {
        FakeTransport transport = new FakeTransport();
        transport.enqueue(bluetoothSimpleReport());

        DualSenseDriver driver = new DualSenseDriver(transport, System::nanoTime);
        ControllerState state = driver.poll(DEAD_ZONE, TRIGGER_THRESHOLD);

        assertTrue(state.isPressed(ControllerButton.A));
        assertTrue(state.isPressed(ControllerButton.LEFT_BUMPER));
        assertEquals("Bluetooth", driver.getTransportName());
        assertEquals("Open-time read plus one retry after the first simple report", 2, transport.featureReads);

        // A second simple report must not trigger another feature read.
        transport.enqueue(bluetoothSimpleReport());
        driver.poll(DEAD_ZONE, TRIGGER_THRESHOLD);
        assertEquals(2, transport.featureReads);
    }

    @Test
    public void bluetoothEnhancedReportIsParsedWithoutARetry() {
        FakeTransport transport = new FakeTransport();
        transport.enqueue(enhancedReport());

        DualSenseDriver driver = new DualSenseDriver(transport, System::nanoTime);
        ControllerState state = driver.poll(DEAD_ZONE, TRIGGER_THRESHOLD);

        assertTrue(state.isPressed(ControllerButton.A));
        assertTrue(state.isPressed(ControllerButton.LEFT_BUMPER));
        assertEquals("Bluetooth", driver.getTransportName());
        assertEquals(1, transport.featureReads);
    }

    @Test
    public void reportWithAnInvalidCrcIsIgnored() {
        byte[] corrupt = enhancedReport();
        corrupt[2] ^= 0x01;

        FakeTransport transport = new FakeTransport();
        transport.enqueue(corrupt);

        DualSenseDriver driver = new DualSenseDriver(transport, System::nanoTime);

        assertEquals(ControllerState.empty(), driver.poll(DEAD_ZONE, TRIGGER_THRESHOLD));
        assertFalse("a CRC failure must not confirm the connection", driver.isConnectionConfirmed());

        // A valid report afterwards must still parse.
        transport.enqueue(usbReport());
        assertTrue(
            driver.poll(DEAD_ZONE, TRIGGER_THRESHOLD)
                .isPressed(ControllerButton.A));
        assertTrue(driver.isConnectionConfirmed());
    }

    @Test
    public void unknownReportIsIgnored() {
        byte[] unknown = new byte[DualSenseReportParser.USB_REPORT_SIZE];
        unknown[0] = 0x02;

        FakeTransport transport = new FakeTransport();
        transport.enqueue(unknown);

        DualSenseDriver driver = new DualSenseDriver(transport, System::nanoTime);

        assertEquals(ControllerState.empty(), driver.poll(DEAD_ZONE, TRIGGER_THRESHOLD));

        transport.enqueue(usbReport());
        assertTrue(
            driver.poll(DEAD_ZONE, TRIGGER_THRESHOLD)
                .isPressed(ControllerButton.A));
    }

    @Test
    public void noReportReturnsTheCachedState() {
        FakeTransport transport = new FakeTransport();
        transport.enqueue(usbReport());

        DualSenseDriver driver = new DualSenseDriver(transport, System::nanoTime);
        ControllerState first = driver.poll(DEAD_ZONE, TRIGGER_THRESHOLD);

        assertSame(
            "A poll without a report must return the cached state",
            first,
            driver.poll(DEAD_ZONE, TRIGGER_THRESHOLD));
    }

    @Test
    public void readErrorClosesTheTransportAndReturnsEmpty() {
        FakeTransport transport = new FakeTransport();
        transport.enqueue(usbReport());

        DualSenseDriver driver = new DualSenseDriver(transport, System::nanoTime);
        driver.poll(DEAD_ZONE, TRIGGER_THRESHOLD);

        transport.errorNextRead = true;
        assertEquals(ControllerState.empty(), driver.poll(DEAD_ZONE, TRIGGER_THRESHOLD));

        assertFalse(transport.isOpen());
        assertFalse(driver.isConnected());
        assertFalse("a closed driver must wait for a new report", driver.isConnectionConfirmed());
        assertEquals("", driver.getTransportName());
    }

    @Test
    public void readExceptionClosesTheTransportAndReturnsEmpty() {
        FakeTransport transport = new FakeTransport();
        transport.enqueue(usbReport());

        DualSenseDriver driver = new DualSenseDriver(transport, System::nanoTime);
        driver.poll(DEAD_ZONE, TRIGGER_THRESHOLD);

        transport.throwOnRead = true;
        assertEquals(ControllerState.empty(), driver.poll(DEAD_ZONE, TRIGGER_THRESHOLD));
        assertFalse(transport.isOpen());
    }

    // -------------------------------------------------------------------------
    // Stale-handle detection
    // -------------------------------------------------------------------------

    @Test
    public void staleBluetoothHandleIsTreatedAsDisconnectedAfterTwoSeconds() {
        AtomicLong clock = new AtomicLong(START_NANOS);
        FakeTransport transport = new FakeTransport();
        transport.enqueue(enhancedReport());

        DualSenseDriver driver = new DualSenseDriver(transport, clock::get);
        driver.poll(DEAD_ZONE, TRIGGER_THRESHOLD);
        assertEquals("Bluetooth", driver.getTransportName());

        clock.addAndGet(TimeUnit.SECONDS.toNanos(1));
        assertTrue("One second without a report is normal", driver.isConnected());

        clock.addAndGet(TimeUnit.SECONDS.toNanos(2));
        assertFalse("Three seconds without a Bluetooth report is stale", driver.isConnected());
    }

    @Test
    public void staleUsbHandleUsesTheLongerTimeout() {
        AtomicLong clock = new AtomicLong(START_NANOS);
        FakeTransport transport = new FakeTransport();
        transport.enqueue(usbReport());

        DualSenseDriver driver = new DualSenseDriver(transport, clock::get);
        driver.poll(DEAD_ZONE, TRIGGER_THRESHOLD);
        assertEquals("USB", driver.getTransportName());

        clock.addAndGet(TimeUnit.SECONDS.toNanos(3));
        assertTrue("USB uses a five-second timeout", driver.isConnected());

        clock.addAndGet(TimeUnit.SECONDS.toNanos(3));
        assertFalse("Six seconds without a USB report is stale", driver.isConnected());
    }

    @Test
    public void closeReleasesTheTransportAndResetsState() {
        FakeTransport transport = new FakeTransport();
        transport.enqueue(usbReport());

        DualSenseDriver driver = new DualSenseDriver(transport, System::nanoTime);
        driver.poll(DEAD_ZONE, TRIGGER_THRESHOLD);

        driver.close();

        assertFalse(transport.isOpen());
        assertFalse(driver.isConnected());
        assertFalse("a closed driver must wait for a new report", driver.isConnectionConfirmed());
        assertEquals("", driver.getTransportName());
        assertEquals(ControllerState.empty(), driver.poll(DEAD_ZONE, TRIGGER_THRESHOLD));
    }

    // -------------------------------------------------------------------------
    // Report fixtures
    // -------------------------------------------------------------------------

    private static byte[] usbReport() {
        byte[] report = new byte[DualSenseReportParser.USB_REPORT_SIZE];
        report[0] = 0x01;
        report[1] = 0;
        report[2] = (byte) 128;
        report[3] = (byte) 255;
        report[4] = (byte) 128;
        report[5] = (byte) 200;
        report[6] = 0;
        report[8] = 0x28;
        report[9] = 0x01;
        return report;
    }

    private static byte[] bluetoothSimpleReport() {
        byte[] report = new byte[DualSenseReportParser.BLUETOOTH_SIMPLE_REPORT_SIZE];
        report[0] = 0x01;
        report[1] = 0;
        report[2] = (byte) 128;
        report[3] = (byte) 255;
        report[4] = (byte) 128;
        report[5] = 0x28;
        report[6] = 0x01;
        report[7] = 0;
        report[8] = (byte) 200;
        report[9] = 0;
        return report;
    }

    private static byte[] enhancedReport() {
        byte[] report = new byte[DualSenseReportParser.BLUETOOTH_ENHANCED_REPORT_SIZE];
        report[0] = 0x31;
        report[1] = 0x02;
        report[2] = 0;
        report[3] = (byte) 128;
        report[4] = (byte) 255;
        report[5] = (byte) 128;
        report[6] = (byte) 200;
        report[7] = 0;
        report[9] = 0x28;
        report[10] = 0x01;

        CRC32 crc = new CRC32();
        crc.update(DualSenseReportParser.BLUETOOTH_INPUT_CRC_SEED);
        crc.update(report, 0, report.length - 4);
        long value = crc.getValue();
        report[74] = (byte) value;
        report[75] = (byte) (value >>> 8);
        report[76] = (byte) (value >>> 16);
        report[77] = (byte) (value >>> 24);
        return report;
    }
}
