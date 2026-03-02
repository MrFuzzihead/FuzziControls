package com.mrfuzzihead.fuzzicontrols.util;

import java.lang.reflect.Field;
import java.nio.ByteBuffer;

import org.lwjgl.input.Keyboard;

import com.mrfuzzihead.fuzzicontrols.FuzziControls;

/**
 * Temporarily patches LWJGL 2's {@link Keyboard#isKeyDown(int)} result for a specific key
 * by writing directly into the internal {@code keyDownBuffer} byte array.
 *
 * <p>
 * This is used to synthesize a held Shift key for a single GUI mouse-click so that
 * {@code GuiContainer.handleMouseClick()} (and any other code that calls
 * {@link Keyboard#isKeyDown}) sees the shift as physically held for the duration of that
 * click, enabling Shift+left-click (stack transfer) without requiring a physical key press.
 *
 * <h3>LWJGL 2 Keyboard internals</h3>
 * {@code Keyboard.keyDownBuffer} is a static {@link ByteBuffer} indexed by LWJGL key code.
 * A non-zero byte means the key is considered down by {@link Keyboard#isKeyDown(int)}.
 * We write {@code 1} before the click and restore the original value immediately after.
 *
 * <p>
 * If reflection fails (e.g. on an unusual JVM), the helper becomes a no-op — the click
 * still fires, just without shift.
 */
public final class KeyboardHelper {

    private static final Field KEY_DOWN_BUFFER_FIELD;

    static {
        Field field = null;
        try {
            field = Keyboard.class.getDeclaredField("keyDownBuffer");
            field.setAccessible(true);
        } catch (Exception e) {
            FuzziControls.LOG.warn(
                "[FuzziControls] Could not reflect Keyboard.keyDownBuffer — "
                    + "Shift+left-click (stack transfer) will not synthesize shift correctly.",
                e);
        }
        KEY_DOWN_BUFFER_FIELD = field;
    }

    private KeyboardHelper() {}

    /**
     * Temporarily marks the given key as held in LWJGL's internal key state, runs
     * {@code action}, then restores the previous state.
     *
     * <p>
     * The restore happens in a {@code finally} block so the key is never left "stuck"
     * even if the action throws.
     *
     * @param keyCode LWJGL key code (e.g. {@link Keyboard#KEY_LSHIFT})
     * @param action  code to run while the key appears held
     */
    public static void withKeyHeld(int keyCode, Runnable action) {
        if (KEY_DOWN_BUFFER_FIELD == null || keyCode < 0) {
            action.run();
            return;
        }

        ByteBuffer buf = null;
        byte original = 0;
        boolean patched = false;

        try {
            buf = (ByteBuffer) KEY_DOWN_BUFFER_FIELD.get(null);
            if (buf != null && keyCode < buf.capacity()) {
                original = buf.get(keyCode);
                buf.put(keyCode, (byte) 1);
                patched = true;
            }
        } catch (Exception e) {
            FuzziControls.LOG.warn("[FuzziControls] KeyboardHelper: failed to set key state.", e);
        }

        try {
            action.run();
        } finally {
            if (patched && buf != null) {
                try {
                    buf.put(keyCode, original);
                } catch (Exception e) {
                    FuzziControls.LOG.warn("[FuzziControls] KeyboardHelper: failed to restore key state.", e);
                }
            }
        }
    }

    /**
     * Returns true if the given LWJGL key is currently physically down according to
     * the internal buffer. Equivalent to {@link Keyboard#isKeyDown(int)} but reads the
     * buffer directly, bypassing any synthetic state we may have set.
     *
     * @param keyCode LWJGL key code
     * @return true if the key byte in the buffer is non-zero
     */
    public static boolean isPhysicallyDown(int keyCode) {
        if (KEY_DOWN_BUFFER_FIELD == null || keyCode < 0) {
            return Keyboard.isKeyDown(keyCode);
        }
        try {
            ByteBuffer buf = (ByteBuffer) KEY_DOWN_BUFFER_FIELD.get(null);
            if (buf != null && keyCode < buf.capacity()) {
                return buf.get(keyCode) != 0;
            }
        } catch (Exception e) {
            // fall through
        }
        return Keyboard.isKeyDown(keyCode);
    }
}
