package com.mrfuzzihead.fuzzicontrols.util;

import net.minecraft.client.gui.GuiScreen;

import com.mrfuzzihead.fuzzicontrols.mixins.early.GuiScreenAccessors;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Injects synthetic keyboard events into a {@link GuiScreen} by calling its
 * {@code keyTyped(char, int)} method via a mixin {@link GuiScreenAccessors @Invoker}.
 *
 * <p>
 * This is used to synthesize an Escape key press (keycode 1) when the B / Circle button
 * is pressed while a GUI is open, backing out of the current screen universally.
 */
@SideOnly(Side.CLIENT)
public final class GuiKeyHelper {

    private GuiKeyHelper() {}

    /**
     * Calls {@code screen.keyTyped(typedChar, keyCode)} via a mixin invoker.
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
        try {
            ((GuiScreenAccessors) screen).callKeyTyped(typedChar, keyCode);
        } catch (Exception e) {
            // The invoker is applied at class load by the mixin; it is always available for any
            // GuiScreen-instantiating class. If this somehow fails, the click is simply lost.
        }
    }
}
