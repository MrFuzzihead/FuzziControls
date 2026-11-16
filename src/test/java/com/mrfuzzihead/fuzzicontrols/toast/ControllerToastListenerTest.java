package com.mrfuzzihead.fuzzicontrols.toast;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.mrfuzzihead.fuzzicontrols.Config;
import com.mrfuzzihead.fuzzicontrols.controller.ControllerConnection;

public class ControllerToastListenerTest {

    private boolean originalShowControllerToasts;

    private ControllerToastListener listener;

    @Before
    public void setUp() {
        originalShowControllerToasts = Config.showControllerToasts;
        Config.showControllerToasts = true;
        listener = new ControllerToastListener();
        ToastManager.getInstance()
            .clear();
    }

    @After
    public void tearDown() {
        Config.showControllerToasts = originalShowControllerToasts;
        ToastManager.getInstance()
            .clear();
    }

    @Test
    public void connectedEventQueuesAConnectedToast() {
        listener.onControllerConnected(new ControllerConnection("DualSense (HID)", "DualSense Edge", "Bluetooth"));

        assertEquals(
            1,
            ToastManager.getInstance()
                .getQueuedToastCount());

        ToastManager.getInstance()
            .updateForTest(0L);
        Toast toast = ToastManager.getInstance()
            .getVisibleForTest(0)
            .getToast();
        assertNotNull(toast);
        assertEquals(Toast.Type.CONNECTED, toast.getType());
        assertEquals(Toast.Icon.DUALSENSE, toast.getIcon());
        assertEquals("DualSense Edge", toast.getDeviceName());
        assertEquals("Bluetooth", toast.getTransportName());
    }

    @Test
    public void disconnectedEventQueuesADisconnectedToast() {
        listener.onControllerDisconnected(new ControllerConnection("DualSense (HID)", "DualSense", "USB"));

        ToastManager.getInstance()
            .updateForTest(0L);
        Toast toast = ToastManager.getInstance()
            .getVisibleForTest(0)
            .getToast();
        assertEquals(Toast.Type.DISCONNECTED, toast.getType());
        assertEquals("DualSense", toast.getDeviceName());
        assertEquals("USB", toast.getTransportName());
    }

    @Test
    public void xinputUsesTheXboxSprite() {
        listener.onControllerConnected(new ControllerConnection("XInput (slot 0)", "XInput (slot 0)", ""));

        ToastManager.getInstance()
            .updateForTest(0L);
        Toast toast = ToastManager.getInstance()
            .getVisibleForTest(0)
            .getToast();
        assertEquals(Toast.Icon.XBOX, toast.getIcon());
    }

    @Test
    public void unknownDriverUsesTheGenericSprite() {
        listener.onControllerConnected(new ControllerConnection("Custom HID", "Custom Pad", "USB"));

        ToastManager.getInstance()
            .updateForTest(0L);
        Toast toast = ToastManager.getInstance()
            .getVisibleForTest(0)
            .getToast();
        assertEquals(Toast.Icon.GENERIC, toast.getIcon());
    }

    @Test
    public void configToggleSuppressesBothToastTypes() {
        Config.showControllerToasts = false;

        listener.onControllerConnected(new ControllerConnection("XInput", "XInput", ""));
        listener.onControllerDisconnected(new ControllerConnection("XInput", "XInput", ""));

        assertEquals(
            0,
            ToastManager.getInstance()
                .getQueuedToastCount());
        assertEquals(
            0,
            ToastManager.getInstance()
                .getVisibleToastCount());
    }
}
