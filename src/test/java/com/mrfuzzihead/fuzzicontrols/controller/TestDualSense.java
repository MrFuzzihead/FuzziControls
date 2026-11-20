package com.mrfuzzihead.fuzzicontrols.controller;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.zip.CRC32;

/**
 * Scripted DualSense hardware for unit tests: a {@link DualSenseTransport} that hands out queued
 * reports, plus report builders for the three input layouts.
 *
 * <p>
 * Shared by {@link DualSenseDriverTest} (driver read loop, timeouts) and {@link ControllerManagerTest}
 * (the manager's liveness/announcement behaviour), so both suites exercise the same report bytes and
 * neither needs hidapi or a real controller.
 */
final class TestDualSense {

    private TestDualSense() {}

    /** A USB {@code 0x01} report with Cross/A pressed and both sticks centred. */
    static byte[] usbReport() {
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

    /** A Bluetooth simple {@code 0x01} report (10 bytes, no CRC). */
    static byte[] bluetoothSimpleReport() {
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

    /** A Bluetooth enhanced {@code 0x31} report (78 bytes, CRC32 over a {@code 0xA1} prefix). */
    static byte[] enhancedReport() {
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

    /** Scripted transport: queues reports and records feature-report calls. */
    static final class ScriptedTransport implements DualSenseTransport {

        final Deque<byte[]> reports = new ArrayDeque<>();

        boolean open;

        boolean openResult = true;

        boolean throwOnOpen;

        boolean throwOnFeatureReport;

        boolean errorNextRead;

        boolean throwOnRead;

        int featureReads;

        int lastFeatureReportId = -1;

        int closeCount;

        final List<byte[]> outputReports = new ArrayList<>();

        boolean failOutputReport;

        String deviceName = "DualSense";

        boolean edge;

        ScriptedTransport enqueue(byte[]... newReports) {
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
}
