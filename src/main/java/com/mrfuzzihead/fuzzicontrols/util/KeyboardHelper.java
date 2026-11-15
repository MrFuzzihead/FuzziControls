package com.mrfuzzihead.fuzzicontrols.util;

import java.nio.ByteBuffer;

import com.mrfuzzihead.fuzzicontrols.FuzziControls;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Temporarily patches LWJGL's key-down state for a single GUI mouse-click so that
 * {@code GuiContainer.handleMouseClick()} (and any other code calling
 * {@link org.lwjgl.input.Keyboard#isKeyDown}) sees shift as physically held for the duration
 * of that click, enabling Shift+left-click (stack transfer) without a physical key press.
 *
 * <p>
 * Under lwjgl3ify, {@code isKeyDown(int)} reads {@code org.lwjglx.input.Keyboard.sdlKeyPressedArray}
 * at the SDL scancode produced by {@code KeyCodes.lwjglToSdlScancode}. We write a non-zero byte
 * there (and restore it in a {@code finally}) to synthesise the held key.
 *
 * <p>
 * If the scancode translation fails, or lwjgl3ify is absent (a {@link LinkageError}, not an
 * {@link Exception}), the action runs without shift patching. The failure is logged once rather
 * than on every shift-click.
 */
@SideOnly(Side.CLIENT)
public final class KeyboardHelper {

    /** Set after the first lwjgl3ify failure so the warning and the failed path are not repeated. */
    private static boolean patchUnavailable;

    private KeyboardHelper() {}

    /**
     * Temporarily marks the given key as held, runs {@code action}, then restores the previous
     * state. The restore happens in a {@code finally} block so the key is never left "stuck"
     * even if the action throws.
     *
     * @param keyCode LWJGL key code (e.g. {@link org.lwjgl.input.Keyboard#KEY_LSHIFT})
     * @param action  code to run while the key appears held
     */
    public static void withKeyHeld(int keyCode, Runnable action) {
        if (keyCode < 0 || patchUnavailable) {
            action.run();
            return;
        }

        final int idx;
        final ByteBuffer array;
        try {
            idx = org.lwjglx.input.KeyCodes.lwjglToSdlScancode(keyCode);
            array = org.lwjglx.input.Keyboard.sdlKeyPressedArray;
        } catch (LinkageError e) {
            // A missing org.lwjglx class is a LinkageError (NoClassDefFoundError, NoSuchMethodError,
            // ...), which the old catch (Exception) never saw; it escaped into the client tick / GUI
            // click path. This is permanent for the JVM, so stop retrying and stop logging.
            patchUnavailable = true;
            FuzziControls.LOG.warn(
                "[FuzziControls] KeyboardHelper: lwjgl3ify key state is unavailable ({}). "
                    + "Shift-click will run without the key patch.",
                e.toString());
            action.run();
            return;
        } catch (Exception e) {
            // An unknown key code is not fatal; run without the patch but keep trying other keys.
            FuzziControls.LOG.warn("[FuzziControls] KeyboardHelper: unknown key code; ignoring shift.", e);
            action.run();
            return;
        }

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
     * Returns true if the given LWJGL key is currently physically down. Under lwjgl3ify this
     * reads the same {@code sdlKeyPressedArray} that the synthetic patch writes, so it is only
     * meaningful outside a {@link #withKeyHeld} call.
     *
     * @param keyCode LWJGL key code
     */
    public static boolean isPhysicallyDown(int keyCode) {
        try {
            return org.lwjglx.input.Keyboard.isKeyDown(keyCode);
        } catch (Exception | LinkageError e) {
            return false;
        }
    }
}
