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
 * If the scancode translation fails the action runs without shift patching.
 */
@SideOnly(Side.CLIENT)
public final class KeyboardHelper {

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
     * Returns true if the given LWJGL key is currently physically down. Under lwjgl3ify this
     * reads the same {@code sdlKeyPressedArray} that the synthetic patch writes, so it is only
     * meaningful outside a {@link #withKeyHeld} call.
     *
     * @param keyCode LWJGL key code
     */
    public static boolean isPhysicallyDown(int keyCode) {
        try {
            return org.lwjglx.input.Keyboard.isKeyDown(keyCode);
        } catch (Exception e) {
            return false;
        }
    }
}
