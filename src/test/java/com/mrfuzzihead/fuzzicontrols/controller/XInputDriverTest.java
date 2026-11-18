package com.mrfuzzihead.fuzzicontrols.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.Test;

import com.github.strikerx3.jxinput.XInputDevice;

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
    public void connectionChecksUseThePolledFlagWithoutCallingTheDevice() {
        XInputDevice device = mock(XInputDevice.class);
        when(device.isConnected()).thenReturn(true);
        XInputDriver driver = new XInputDriver(0, device);

        assertTrue("The constructor captures JXInput's initial connected state", driver.isConnected());
        verify(device, never()).poll();

        when(device.poll()).thenReturn(false);
        assertEquals(ControllerState.empty(), driver.poll(0.15f, 0.2f));
        assertFalse("A failed poll updates the cached connection state", driver.isConnected());
        verify(device, times(1)).poll();

        driver.close();
    }

    @Test
    public void rumbleMapsNormalisedValuesToXInputsRange() {
        XInputDevice device = mock(XInputDevice.class);
        when(device.isConnected()).thenReturn(true);
        XInputDriver driver = new XInputDriver(0, device);

        driver.setRumble(1f, 0.5f);
        verify(device).setVibration(65535, 32768);

        driver.setRumble(-1f, 2f);
        verify(device).setVibration(0, 65535);
    }

    @Test
    public void closeStopsRumbleBeforeReleasingTheDevice() {
        XInputDevice device = mock(XInputDevice.class);
        when(device.isConnected()).thenReturn(true);
        XInputDriver driver = new XInputDriver(0, device);

        driver.close();

        verify(device).setVibration(0, 0);
    }

    @Test
    public void rumbleIsIgnoredWhenDisconnected() {
        XInputDevice device = mock(XInputDevice.class);
        when(device.isConnected()).thenReturn(false);
        XInputDriver driver = new XInputDriver(0, device);

        driver.setRumble(1f, 1f);
        verify(device, never())
            .setVibration(org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt());
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
