package com.mrfuzzihead.fuzzicontrols.toast;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

import com.mrfuzzihead.fuzzicontrols.toast.ToastManager.ToastInstance;

/**
 * Unit tests for the toast queue and its 1.12-style slide/hold/hide timing.
 *
 * <p>
 * Rendering itself needs a live OpenGL context, so these tests drive the same
 * {@link ToastManager#updateForTest(long)} path the renderer uses and assert the geometry the
 * renderer would draw.
 */
public class ToastManagerTest {

    private long now;

    private ToastManager manager;

    @Before
    public void setUp() {
        now = 0L;
        manager = new ToastManager(() -> now);
    }

    private void add(Toast.Type type, String device, String transport) {
        manager.add(new Toast(type, device, transport));
    }

    @Test
    public void newToastStartsOffscreenAndSlidesIn() {
        add(Toast.Type.CONNECTED, "DualSense Edge", "Bluetooth");
        manager.updateForTest(0L);

        ToastInstance toast = manager.getVisibleForTest(0);
        assertNotNull(toast);
        assertEquals("a new toast starts just off the right edge", 320, toast.getX(320, 0L));

        // 300/600 = 0.5, squared = 0.25, so 40 px of the 160 px panel is visible.
        assertEquals(280, toast.getX(320, 300L));

        now = 600L;
        manager.updateForTest(now);
        assertEquals("after 600 ms the toast is fully on screen", 160, toast.getX(320, now));
    }

    @Test
    public void toastStaysForFiveSecondsAfterSlidingInThenHides() {
        add(Toast.Type.CONNECTED, "DualSense", "USB");
        manager.updateForTest(0L);
        ToastInstance toast = manager.getVisibleForTest(0);

        now = 600L;
        manager.updateForTest(now);
        assertFalse(toast.isFinished(now));

        now = 5_599L;
        manager.updateForTest(now);
        assertFalse("duration starts after the slide-in", toast.isFinished(now));
        assertEquals("still fully visible just before the timeout", 160, toast.getX(320, now));

        now = 5_600L;
        manager.updateForTest(now);
        assertFalse("hide animation has just started", toast.isFinished(now));
        assertEquals("hide starts from the fully visible position", 160, toast.getX(320, now));

        now = 6_200L;
        manager.updateForTest(now);
        assertNull("the finished toast releases its slot", manager.getVisibleForTest(0));
    }

    @Test
    public void extraToastsWaitForAFreeSlot() {
        for (int i = 0; i < ToastManager.MAX_VISIBLE + 2; i++) {
            add(Toast.Type.CONNECTED, "Controller " + i, "");
        }

        manager.updateForTest(0L);
        assertEquals(ToastManager.MAX_VISIBLE, manager.getVisibleToastCount());
        assertEquals(2, manager.getQueuedToastCount());

        // Simulate the renderer's normal per-frame updates: the hold expires at 5.6 s, the
        // 600 ms hide animation finishes at 6.2 s, then the two queued toasts take the slots.
        now = 600L;
        manager.updateForTest(now);
        now = 5_600L;
        manager.updateForTest(now);
        now = 6_200L;
        manager.updateForTest(now);
        assertEquals("the two queued toasts fill the freed slots", 2, manager.getVisibleToastCount());
        assertEquals(0, manager.getQueuedToastCount());
    }

    @Test
    public void clearDropsVisibleAndQueuedToasts() {
        for (int i = 0; i < ToastManager.MAX_VISIBLE + 1; i++) {
            add(Toast.Type.DISCONNECTED, "Controller", "");
        }
        manager.updateForTest(0L);

        manager.clear();

        assertEquals(0, manager.getVisibleToastCount());
        assertEquals(0, manager.getQueuedToastCount());
    }

    @Test
    public void nullToastIsIgnored() {
        manager.add(null);

        manager.updateForTest(0L);

        assertEquals(0, manager.getVisibleToastCount());
        assertEquals(0, manager.getQueuedToastCount());
    }

    @Test
    public void toastDataIsPreservedForTheRenderer() {
        Toast toast = new Toast(Toast.Type.DISCONNECTED, "DualSense Edge", "Bluetooth");

        assertEquals(Toast.Type.DISCONNECTED, toast.getType());
        assertEquals(Toast.Icon.GENERIC, toast.getIcon());
        assertEquals("DualSense Edge", toast.getDeviceName());
        assertEquals("Bluetooth", toast.getTransportName());
        assertTrue(toast.getDurationMillis() > 0L);
    }

    @Test
    public void iconCanBeSelectedForTheDeviceFamily() {
        Toast toast = new Toast(Toast.Type.CONNECTED, Toast.Icon.XBOX, "XInput (slot 0)", "");

        assertEquals(Toast.Icon.XBOX, toast.getIcon());
    }
}
