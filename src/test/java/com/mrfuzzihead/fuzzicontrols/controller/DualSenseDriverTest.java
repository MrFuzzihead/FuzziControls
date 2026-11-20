package com.mrfuzzihead.fuzzicontrols.controller;

import static com.mrfuzzihead.fuzzicontrols.controller.TestDualSense.bluetoothSimpleReport;
import static com.mrfuzzihead.fuzzicontrols.controller.TestDualSense.enhancedReport;
import static com.mrfuzzihead.fuzzicontrols.controller.TestDualSense.usbReport;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.Test;

/**
 * Tests the {@link DualSenseDriver} read loop, transport lifecycle and stale-handle handling with the
 * scripted {@link DualSenseTransport} from {@link TestDualSense} (no hidapi or hardware).
 */
public class DualSenseDriverTest {

    private static final float DEAD_ZONE = 0.15f;

    private static final float TRIGGER_THRESHOLD = 0.2f;

    private static final long START_NANOS = TimeUnit.SECONDS.toNanos(1);

    // -------------------------------------------------------------------------
    // Open / feature report
    // -------------------------------------------------------------------------

    @Test
    public void openReadsTheCalibrationFeatureReport() {
        TestDualSense.ScriptedTransport transport = new TestDualSense.ScriptedTransport();

        DualSenseDriver driver = new DualSenseDriver(transport, System::nanoTime);

        assertTrue(driver.isConnected());
        assertFalse("opening a handle is not proof that reports are arriving", driver.isConnectionConfirmed());
        assertEquals(1, transport.featureReads);
        assertEquals(0x05, transport.lastFeatureReportId);
        assertEquals("", driver.getTransportName());
    }

    @Test
    public void featureReportFailureDoesNotPreventInput() {
        TestDualSense.ScriptedTransport transport = new TestDualSense.ScriptedTransport();
        transport.throwOnFeatureReport = true;
        transport.enqueue(usbReport());

        DualSenseDriver driver = new DualSenseDriver(transport, System::nanoTime);
        ControllerState state = driver.poll(DEAD_ZONE, TRIGGER_THRESHOLD);

        assertTrue(state.isPressed(ControllerButton.A));
        assertEquals("USB", driver.getTransportName());
    }

    @Test
    public void openFailureLeavesTheDriverInactive() {
        TestDualSense.ScriptedTransport transport = new TestDualSense.ScriptedTransport();
        transport.openResult = false;

        DualSenseDriver driver = new DualSenseDriver(transport, System::nanoTime);

        assertFalse(driver.isConnected());
        assertFalse(driver.isConnectionConfirmed());
        assertEquals(ControllerState.empty(), driver.poll(DEAD_ZONE, TRIGGER_THRESHOLD));
        assertEquals("No calibration read after a failed open", 0, transport.featureReads);
    }

    @Test
    public void openExceptionIsCaughtAndClosesTheTransport() {
        TestDualSense.ScriptedTransport transport = new TestDualSense.ScriptedTransport();
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
        TestDualSense.ScriptedTransport transport = new TestDualSense.ScriptedTransport();
        transport.deviceName = "DualSense Edge";

        DualSenseDriver driver = new DualSenseDriver(transport, System::nanoTime);

        assertEquals("DualSense Edge", driver.getDeviceName());
    }

    @Test
    public void edgeExtraButtonsArePassedThroughFromTheTransport() {
        TestDualSense.ScriptedTransport transport = new TestDualSense.ScriptedTransport();
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
        TestDualSense.ScriptedTransport transport = new TestDualSense.ScriptedTransport();
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
        TestDualSense.ScriptedTransport transport = new TestDualSense.ScriptedTransport();
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
        TestDualSense.ScriptedTransport transport = new TestDualSense.ScriptedTransport();
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
        TestDualSense.ScriptedTransport transport = new TestDualSense.ScriptedTransport();
        transport.enqueue(usbReport());

        DualSenseDriver driver = new DualSenseDriver(transport, System::nanoTime);
        driver.poll(DEAD_ZONE, TRIGGER_THRESHOLD);

        driver.setRumble(0.5f, 0.5f);
        driver.setRumble(0.5f, 0.5f);

        assertEquals(1, transport.outputReports.size());
    }

    @Test
    public void rumbleBeforeTheTransportIsKnownIsIgnored() {
        TestDualSense.ScriptedTransport transport = new TestDualSense.ScriptedTransport();

        DualSenseDriver driver = new DualSenseDriver(transport, System::nanoTime);
        driver.setRumble(1f, 1f);

        assertEquals(0, transport.outputReports.size());
    }

    @Test
    public void failedRumbleWriteDoesNotThrow() {
        TestDualSense.ScriptedTransport transport = new TestDualSense.ScriptedTransport();
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
        TestDualSense.ScriptedTransport transport = new TestDualSense.ScriptedTransport();
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
        TestDualSense.ScriptedTransport transport = new TestDualSense.ScriptedTransport();
        transport.enqueue(usbReport());

        DualSenseDriver driver = new DualSenseDriver(transport, System::nanoTime);
        driver.poll(DEAD_ZONE, TRIGGER_THRESHOLD);

        driver.setTriggerEffect(ControllerTrigger.LEFT, ControllerTriggerEffect.BOW, 6);
        assertEquals(1, transport.outputReports.size());
        byte[] report = transport.outputReports.get(0);
        assertEquals(0x02, report[0] & 0xFF);
        assertEquals(DualSenseOutputReport.VALID_FLAG0_ALLOW_LEFT_TRIGGER_FFB, report[1] & 0xFF);
        assertEquals(0x22, report[1 + DualSenseOutputReport.L2_TRIGGER_OFFSET] & 0xFF);
        assertEquals(0, report[1 + DualSenseOutputReport.R2_TRIGGER_OFFSET] & 0xFF);

        driver.setTriggerEffect(ControllerTrigger.LEFT, ControllerTriggerEffect.BOW, 6);
        assertEquals("Unchanged effects must not rewrite", 1, transport.outputReports.size());

        driver.setTriggerEffect(ControllerTrigger.RIGHT, ControllerTriggerEffect.FEEDBACK, 5);
        assertEquals(2, transport.outputReports.size());
        byte[] rightReport = transport.outputReports.get(1);
        assertEquals(DualSenseOutputReport.VALID_FLAG0_ALLOW_RIGHT_TRIGGER_FFB, rightReport[1] & 0xFF);
        assertEquals(0x21, rightReport[1 + DualSenseOutputReport.R2_TRIGGER_OFFSET] & 0xFF);
    }

    @Test
    public void clearingAnIdleTriggerDoesNotWriteAnOffReport() {
        TestDualSense.ScriptedTransport transport = new TestDualSense.ScriptedTransport();
        transport.enqueue(usbReport());

        DualSenseDriver driver = new DualSenseDriver(transport, System::nanoTime);
        driver.poll(DEAD_ZONE, TRIGGER_THRESHOLD);

        driver.setTriggerEffect(ControllerTrigger.LEFT, ControllerTriggerEffect.OFF, 0);
        driver.setTriggerEffect(ControllerTrigger.RIGHT, ControllerTriggerEffect.OFF, 0);

        assertEquals(0, transport.outputReports.size());
    }

    @Test
    public void lightbarStateIsWrittenAndDeduplicated() {
        TestDualSense.ScriptedTransport transport = new TestDualSense.ScriptedTransport();
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
        TestDualSense.ScriptedTransport transport = new TestDualSense.ScriptedTransport();
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
        TestDualSense.ScriptedTransport transport = new TestDualSense.ScriptedTransport();
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
        TestDualSense.ScriptedTransport transport = new TestDualSense.ScriptedTransport();
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

        TestDualSense.ScriptedTransport transport = new TestDualSense.ScriptedTransport();
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

        TestDualSense.ScriptedTransport transport = new TestDualSense.ScriptedTransport();
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
        TestDualSense.ScriptedTransport transport = new TestDualSense.ScriptedTransport();
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
        TestDualSense.ScriptedTransport transport = new TestDualSense.ScriptedTransport();
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
        TestDualSense.ScriptedTransport transport = new TestDualSense.ScriptedTransport();
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
        TestDualSense.ScriptedTransport transport = new TestDualSense.ScriptedTransport();
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
    public void drainingABacklogOfReportsRevivesAHandleTheWatchdogGaveUpOn() {
        AtomicLong clock = new AtomicLong(START_NANOS);
        TestDualSense.ScriptedTransport transport = new TestDualSense.ScriptedTransport();
        transport.enqueue(usbReport());

        DualSenseDriver driver = new DualSenseDriver(transport, clock::get);
        driver.poll(DEAD_ZONE, TRIGGER_THRESHOLD);

        // The client thread froze for longer than the USB watchdog while the controller kept
        // reporting, so its reports are queued but unread and the timestamp looks stale.
        transport.enqueue(usbReport());
        clock.addAndGet(TimeUnit.SECONDS.toNanos(6));
        assertFalse("The watchdog cannot see a frozen client thread", driver.isConnected());

        driver.poll(DEAD_ZONE, TRIGGER_THRESHOLD);

        assertTrue("Draining the queued report proves the handle is still alive", driver.isConnected());
        assertEquals("The handle must not have been released", 0, transport.closeCount);
    }

    @Test
    public void staleUsbHandleUsesTheLongerTimeout() {
        AtomicLong clock = new AtomicLong(START_NANOS);
        TestDualSense.ScriptedTransport transport = new TestDualSense.ScriptedTransport();
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
        TestDualSense.ScriptedTransport transport = new TestDualSense.ScriptedTransport();
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

}
