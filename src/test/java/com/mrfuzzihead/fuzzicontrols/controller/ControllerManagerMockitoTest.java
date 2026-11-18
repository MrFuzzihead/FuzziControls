package com.mrfuzzihead.fuzzicontrols.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.mrfuzzihead.fuzzicontrols.Config;

/**
 * Mockito-backed tests for {@link ControllerManager}'s driver selection and polling contract.
 *
 * <p>
 * These deliberately overlap with the hand-written fakes in {@link ControllerManagerTest}: that
 * suite is the detailed behaviour spec, while this one proves the Mockito setup works and pins the
 * interaction contract (which driver is polled/closed at each step). Mockito 5's inline mock maker
 * is used so final/package-private types can be mocked without {@code mockito-inline}.
 * </p>
 */
public class ControllerManagerMockitoTest {

    private String originalDriver;

    private int originalSlot;

    private boolean originalEnabled;

    @Before
    public void setUp() {
        originalDriver = Config.controllerDriver;
        originalSlot = Config.xInputControllerSlot;
        originalEnabled = Config.controllerEnabled;
        Config.controllerDriver = "auto";
        Config.xInputControllerSlot = 0;
        Config.controllerEnabled = true;
    }

    @After
    public void restoreConfig() {
        Config.controllerDriver = originalDriver;
        Config.xInputControllerSlot = originalSlot;
        Config.controllerEnabled = originalEnabled;
    }

    @Test
    public void connectedDualSenseIsSelectedPolledAndClosed() {
        ControllerManager.DriverFactory factory = mock(ControllerManager.DriverFactory.class);
        IControllerDriver dualSense = mock(IControllerDriver.class);
        when(factory.createDualSense()).thenReturn(dualSense);
        when(dualSense.isConnected()).thenReturn(true);
        when(dualSense.getDriverName()).thenReturn("DualSense (HID)");
        when(dualSense.getDeviceName()).thenReturn("DualSense");
        when(dualSense.getTransportName()).thenReturn("USB");
        ControllerState expected = new ControllerState(0.5f, 0f, 0f, 0f, 0f, 0f, Collections.emptySet());
        when(dualSense.poll(anyFloat(), anyFloat())).thenReturn(expected);

        ControllerManager manager = new ControllerManager(factory);
        manager.init();

        assertTrue(manager.isActive());
        assertEquals("DualSense (HID)", manager.getActiveDriverName());
        assertEquals("DualSense", manager.getActiveDeviceName());
        assertEquals("USB", manager.getActiveTransportName());

        manager.tick();

        assertSame(expected, manager.getState());
        verify(dualSense).poll(anyFloat(), anyFloat());
        verify(factory, never()).createXInput(anyInt());

        manager.shutdown();
        verify(dualSense).close();
        assertFalse(manager.isActive());
    }

    @Test
    public void failedDualSenseProbeIsClosedAndFallsBackToXInput() {
        ControllerManager.DriverFactory factory = mock(ControllerManager.DriverFactory.class);
        IControllerDriver dualSense = mock(IControllerDriver.class);
        IControllerDriver xInput = mock(IControllerDriver.class);
        when(factory.createDualSense()).thenReturn(dualSense);
        when(factory.createXInput(0)).thenReturn(xInput);
        when(dualSense.isConnected()).thenReturn(false);
        when(xInput.isConnected()).thenReturn(true);
        when(xInput.getDriverName()).thenReturn("XInput (slot 0)");
        when(xInput.poll(anyFloat(), anyFloat())).thenReturn(ControllerState.empty());

        ControllerManager manager = new ControllerManager(factory);
        manager.init();

        assertTrue(manager.isActive());
        assertEquals("XInput (slot 0)", manager.getActiveDriverName());
        verify(dualSense).close();
        verify(factory).createXInput(0);

        manager.tick();
        verify(xInput).poll(anyFloat(), anyFloat());
    }

    @Test
    public void pollFailureClosesTheDriverAndClearsTheState() {
        ControllerManager.DriverFactory factory = mock(ControllerManager.DriverFactory.class);
        IControllerDriver dualSense = mock(IControllerDriver.class);
        when(factory.createDualSense()).thenReturn(dualSense);
        when(dualSense.isConnected()).thenReturn(true);
        when(dualSense.poll(anyFloat(), anyFloat())).thenThrow(new IllegalStateException("boom"));

        ControllerManager manager = new ControllerManager(factory);
        manager.init();
        manager.tick();

        assertFalse(manager.isActive());
        assertEquals(ControllerState.empty(), manager.getState());
        verify(dualSense).close();
    }
}
