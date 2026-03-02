package com.mrfuzzihead.fuzzicontrols.util;

import java.lang.reflect.Method;

import net.minecraft.client.gui.GuiScreen;

import com.mrfuzzihead.fuzzicontrols.FuzziControls;

/**
 * Injects synthetic keyboard events into a {@link GuiScreen} by calling its
 * {@code keyTyped(char, int)} method reflectively.
 *
 * <h3>Why reflection instead of an Access Transformer?</h3>
 * <p>
 * {@code GuiScreen.keyTyped} is {@code protected}. An AT can widen it to {@code public}, but
 * doing so causes a compile failure for every subclass that also declares it as
 * {@code protected} (e.g. {@code GuiChat}, {@code GuiContainer}, dozens of vanilla screens).
 * Java does not allow a subclass to narrow the access of an inherited method, so widening the
 * base class breaks all overriding subclasses. Reflection is therefore the correct and only
 * mod-side solution — it is resolved once at class-load time and incurs negligible overhead.
 *
 * <p>
 * This is used to synthesize an Escape key press (keycode 1) when the B / Circle button
 * is pressed while a GUI is open, backing out of the current screen universally.
 */
public final class GuiKeyHelper {

    private static final Method KEY_TYPED;

    static {
        Method keyTyped = null;
        try {
            keyTyped = GuiScreen.class.getDeclaredMethod("keyTyped", char.class, int.class);
            keyTyped.setAccessible(true);
        } catch (NoSuchMethodException e) {
            FuzziControls.LOG.error(
                "[FuzziControls] Failed to reflect GuiScreen.keyTyped — "
                    + "B-button back navigation will be unavailable.",
                e);
        }
        KEY_TYPED = keyTyped;
    }

    private GuiKeyHelper() {}

    /**
     * Calls {@code screen.keyTyped(typedChar, keyCode)} reflectively.
     *
     * <p>
     * For Escape, pass {@code keyCode = 1} and {@code typedChar = '\0'}. Every vanilla
     * {@link GuiScreen} implementation handles keyCode 1 by closing itself or navigating
     * to its parent screen, identical to pressing Escape on the keyboard.
     *
     * @param screen    the currently active {@link GuiScreen}
     * @param keyCode   LWJGL key code (e.g. {@link org.lwjgl.input.Keyboard#KEY_ESCAPE} = 1)
     * @param typedChar the character typed (use {@code '\0'} for non-printable keys)
     */
    public static void injectKey(GuiScreen screen, int keyCode, char typedChar) {
        if (KEY_TYPED == null) return;
        try {
            KEY_TYPED.invoke(screen, typedChar, keyCode);
        } catch (Exception e) {
            FuzziControls.LOG.warn("[FuzziControls] GuiKeyHelper.injectKey failed.", e);
        }
    }
}
