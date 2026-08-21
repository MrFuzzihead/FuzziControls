package com.mrfuzzihead.fuzzicontrols.util;

import java.lang.reflect.Field;
import java.nio.ByteBuffer;

import org.lwjgl.input.Keyboard;

import com.mrfuzzihead.fuzzicontrols.FuzziControls;

/**
 * Temporarily patches LWJGL's key-down state for a single GUI mouse-click so that
 * {@code GuiContainer.handleMouseClick()} (and any other code calling
 * {@link Keyboard#isKeyDown}) sees shift as physically held for the duration of that click,
 * enabling Shift+left-click (stack transfer) without a physical key press.
 *
 * <h3>Two backends</h3>
 * <ul>
 * <li><b>lwjgl3ify / org.lwjglx (Java 25 target):</b> {@code org.lwjglx.input.Keyboard.isKeyDown(int)}
 * reads the public {@code sdlKeyPressedArray} ByteBuffer at the index
 * {@code KeyCodes.lwjglToSdlScancode(key)}. We write a non-zero byte there (and restore it in a
 * {@code finally}) to synthesise the held key.</li>
 * <li><b>Vanilla LWJGL 2:</b> reflects {@code Keyboard.keyDownBuffer} (indexed directly by LWJGL
 * key code).</li>
 * </ul>
 *
 * <p>
 * If the relevant backend is unavailable the helper degrades gracefully to calling the action
 * directly (the click still fires, just without shift). Both {@code org.lwjglx} types are only
 * referenced from branches guarded by {@link #USING_LWJGL3IFY}, so they are resolved lazily and
 * never loaded on a non-lwjgl3ify runtime.
 */
public final class KeyboardHelper {

    /** True when lwjgl3ify (org.lwjglx) is present; decided once at class load. */
    private static final boolean USING_LWJGL3IFY = isLwjgl3ifyPresent();

    /** LWJGL 2 fallback only. */
    private static final Field KEY_DOWN_BUFFER_FIELD;

    static {
        Field field = null;
        if (!USING_LWJGL3IFY) {
            try {
                field = Keyboard.class.getDeclaredField("keyDownBuffer");
                field.setAccessible(true);
            } catch (Exception e) {
                FuzziControls.LOG.warn(
                    "[FuzziControls] Could not reflect Keyboard.keyDownBuffer — "
                        + "Shift+left-click (stack transfer) will not synthesize shift correctly.",
                    e);
            }
        }
        KEY_DOWN_BUFFER_FIELD = field;
    }

    private KeyboardHelper() {}

    /**
     * Temporarily marks the given key as held, runs {@code action}, then restores the previous
     * state. The restore happens in a {@code finally} block so the key is never left "stuck"
     * even if the action throws.
     *
     * @param keyCode LWJGL key code (e.g. {@link Keyboard#KEY_LSHIFT})
     * @param action  code to run while the key appears held
     */
    public static void withKeyHeld(int keyCode, Runnable action) {
        if (USING_LWJGL3IFY) {
            withKeyHeldLwjgl3ify(keyCode, action);
            return;
        }
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
     * lwjgl3ify path: {@code isKeyDown(int)} reads {@code sdlKeyPressedArray} at the SDL scancode
     * produced by {@code KeyCodes.lwjglToSdlScancode}. Write a non-zero byte there for the duration
     * of the action and restore it afterwards.
     */
    private static void withKeyHeldLwjgl3ify(int keyCode, Runnable action) {
        if (keyCode < 0) {
            action.run();
            return;
        }
        final int idx;
        try {
            idx = org.lwjglx.input.KeyCodes.lwjglToSdlScancode(keyCode);
        } catch (Exception e) {
            FuzziControls.LOG.warn("[FuzziControls] KeyboardHelper: unknown key code; ignoring shift.", e);
            action.run();
            return;
        }
        final ByteBuffer array = org.lwjglx.input.Keyboard.sdlKeyPressedArray;
        if (array == null || idx <= 0 || idx >= array.limit()) {
            action.run();
            return;
        }
        final byte original = array.get(idx);
        array.put(idx, (byte) 1);
        try {
            action.run();
        } finally {
            array.put(idx, original);
        }
    }

    /**
     * Returns true if the given LWJGL key is currently physically down. Under lwjgl3ify this reads
     * the same {@code sdlKeyPressedArray} that the synthetic patch writes, so it is only meaningful
     * outside a {@link #withKeyHeld} call.
     *
     * @param keyCode LWJGL key code
     */
    public static boolean isPhysicallyDown(int keyCode) {
        if (USING_LWJGL3IFY) {
            return org.lwjglx.input.Keyboard.isKeyDown(keyCode);
        }
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

    private static boolean isLwjgl3ifyPresent() {
        try {
            Class.forName("org.lwjglx.input.Mouse", false, KeyboardHelper.class.getClassLoader());
            return true;
        } catch (Throwable t) {
            return false;
        }
    }
}
