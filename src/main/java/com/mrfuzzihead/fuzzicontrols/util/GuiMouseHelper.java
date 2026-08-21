package com.mrfuzzihead.fuzzicontrols.util;

import net.minecraft.client.gui.GuiScreen;

import com.mrfuzzihead.fuzzicontrols.FuzziControls;
import com.mrfuzzihead.fuzzicontrols.mixins.early.GuiScreenAccessors;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Injects synthetic mouse-button events into LWJGL so that every part of Minecraft's GUI system
 * sees the controller click:
 * <ul>
 * <li>{@link GuiScreen#handleMouseInput()} — reads the event queue via {@code Mouse.next()}</li>
 * <li>{@link org.lwjgl.input.Mouse#isButtonDown(int)} — used by
 * {@link net.minecraft.client.gui.GuiSlot}</li>
 * </ul>
 *
 * <h3>Backend</h3>
 * This helper uses the lwjgl3ify public injection API
 * ({@code org.lwjglx.input.Mouse.addButtonEvent}/{@code sdlMouseButtonFlags}) for event
 * synthesis. If that fails, it falls back to direct {@link GuiScreenAccessors} invoker dispatch.
 *
 * <p>
 * {@code GuiScreen.mouseClicked} / {@code mouseMovedOrUp} are {@code protected}. An
 * access-transformer would break the 50+ vanilla/mod subclasses; a mixin {@code @Invoker}
 * is the correct fix.
 */
@SideOnly(Side.CLIENT)
public final class GuiMouseHelper {

    private GuiMouseHelper() {}

    /**
     * Synthesizes a mouse button press or release so both Minecraft's event-driven path
     * and the per-frame {@code isButtonDown} poll see it.
     *
     * @param screen      active GUI screen (used for the fallback path)
     * @param mouseButton 0 = left button, 1 = right button
     * @param pressed     true = button down, false = button up
     * @param guiX        scaled GUI X for the fallback path
     * @param guiY        scaled GUI Y for the fallback path
     */
    public static void injectMouseButton(GuiScreen screen, int mouseButton, boolean pressed, int guiX, int guiY) {
        try {
            injectLwjgl3ify(mouseButton, pressed);
            return;
        } catch (Exception e) {
            FuzziControls.LOG
                .debug("[FuzziControls] lwjgl3ify mouse injection failed; using direct dispatch fallback.", e);
        }
        if (pressed) {
            mouseClicked(screen, guiX, guiY, mouseButton);
        } else {
            mouseMovedOrUp(screen, guiX, guiY, mouseButton);
        }
    }

    /**
     * lwjgl3ify path: enqueue a button event and update the public SDL button-state bitmask
     * that {@code Mouse.isButtonDown} reads.
     */
    private static void injectLwjgl3ify(int mouseButton, boolean pressed) {
        final int flag = 1 << mouseButton;
        final int flags = org.lwjglx.input.Mouse.sdlMouseButtonFlags;
        org.lwjglx.input.Mouse.sdlMouseButtonFlags = pressed ? (flags | flag) : (flags & ~flag);
        org.lwjglx.input.Mouse.addButtonEvent(mouseButton, pressed);
    }

    /**
     * Dispatches a mouse press synchronously (used for shift-clicks, where the keyboard
     * state must be patched for exactly the duration of the dispatch).
     *
     * @param screen      the currently active GUI screen
     * @param mouseButton 0 = left, 1 = right
     * @param guiX        scaled GUI X coordinate (top-left origin)
     * @param guiY        scaled GUI Y coordinate (top-left origin)
     */
    public static void directMouseClicked(GuiScreen screen, int mouseButton, int guiX, int guiY) {
        try {
            final int flag = 1 << mouseButton;
            final int flags = org.lwjglx.input.Mouse.sdlMouseButtonFlags;
            org.lwjglx.input.Mouse.sdlMouseButtonFlags = flags | flag;
            mouseClicked(screen, guiX, guiY, mouseButton);
            org.lwjglx.input.Mouse.sdlMouseButtonFlags = flags & ~flag;
            return;
        } catch (Exception e) {
            FuzziControls.LOG.warn("[FuzziControls] directMouseClicked (lwjgl3ify) failed; trying invoker.", e);
        }
        mouseClicked(screen, guiX, guiY, mouseButton);
    }

    /** Calls {@code screen.mouseClicked(mouseX, mouseY, mouseButton)} via the mixin invoker. */
    private static void mouseClicked(GuiScreen screen, int mouseX, int mouseY, int mouseButton) {
        try {
            ((GuiScreenAccessors) screen).callMouseClicked(mouseX, mouseY, mouseButton);
        } catch (Exception e) {
            FuzziControls.LOG.warn("[FuzziControls] Invoker mouseClicked failed.", e);
        }
    }

    /** Calls {@code screen.mouseMovedOrUp(mouseX, mouseY, state)} via the mixin invoker. */
    private static void mouseMovedOrUp(GuiScreen screen, int mouseX, int mouseY, int state) {
        try {
            ((GuiScreenAccessors) screen).callMouseMovedOrUp(mouseX, mouseY, state);
        } catch (Exception e) {
            FuzziControls.LOG.warn("[FuzziControls] Invoker mouseMovedOrUp failed.", e);
        }
    }
}
