package com.mrfuzzihead.fuzzicontrols.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import org.junit.Test;

/**
 * Tests that {@link XInputDriver} degrades gracefully when JXInput's native library is unavailable.
 *
 * <p>
 * JXInput ships Windows-only natives. On Linux and macOS the load throws {@link UnsatisfiedLinkError},
 * which is an {@code Error} — it escaped the constructor's {@code catch (Exception)} and crashed the
 * game during mod initialisation (found by the CI client self-test, which runs on Linux). A missing
 * input backend must never take the client down.
 */
public class XInputDriverTest {

    @Test
    public void supportProbeNeverThrowsAndIsStable() {
        // Must answer on every platform, whether or not the natives load, and be cached.
        boolean first = XInputDriver.isXInputSupported();
        boolean second = XInputDriver.isXInputSupported();
        assertEquals("The probe result must be cached and stable", first, second);
    }

    @Test
    public void constructionNeverThrowsWithoutNatives() {
        XInputDriver driver = new XInputDriver(0);
        assertNotNull("The driver must always be constructible", driver.getDriverName());
        // With no usable backend it reports itself as disconnected rather than failing.
        if (!XInputDriver.isXInputSupported()) {
            assertFalse("Without natives the driver must report disconnected", driver.isConnected());
            assertNotNull("poll must still answer", driver.poll(0.15f, 0.2f));
        }
        driver.close();
        driver.close();
    }
}
