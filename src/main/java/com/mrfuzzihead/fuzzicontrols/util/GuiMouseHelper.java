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
     * Set once lwjgl3ify's injection API has failed, so the failing path is not retried on every
     * click and the warning is logged only once.
     *
     * <p>
     * The mod declares only a soft ordering dependency on lwjgl3ify ({@code after:lwjgl3ify}), but
     * {@code org.lwjglx.input.Mouse} and its {@code sdlMouseButtonFlags} / {@code addButtonEvent} /
     * {@code addWheelEvent} members are lwjgl3ify extensions. On an install without it, referencing
     * them throws {@link LinkageError} ({@code NoClassDefFoundError}, {@code NoSuchMethodError}, …),
     * which is an {@code Error} and therefore was <em>not</em> caught by the previous
     * {@code catch (Exception)} — so the documented fallback could never engage and the error
     * escaped into the client tick / render loop.
     */
    private static boolean lwjgl3ifyInjectionFailed = false;

    /** Logs the one-time warning when the lwjgl3ify injection path is found to be unusable. */
    private static void onLwjgl3ifyFailure(String what, Throwable cause) {
        if (lwjgl3ifyInjectionFailed) return;
        lwjgl3ifyInjectionFailed = true;
        FuzziControls.LOG.warn(
            "[FuzziControls] lwjgl3ify input injection is unavailable ({}: {}). "
                + "GUI clicks fall back to direct dispatch; GUI wheel scrolling is disabled.",
            what,
            cause.toString());
    }

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
        if (!lwjgl3ifyInjectionFailed) {
            try {
                injectLwjgl3ify(mouseButton, pressed);
                return;
            } catch (Exception | LinkageError e) {
                onLwjgl3ifyFailure("addButtonEvent", e);
            }
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
     * Injects a scroll wheel event into the LWJGL event queue via lwjgl3ify.
     * The delta is in "notches" (typically 1.0 per notch). Fractional values are
     * accumulated internally by lwjgl3ify and fired as discrete events when they
     * cross an integer boundary.
     *
     * @param delta scroll wheel delta in notches (positive = scroll up, negative = scroll down)
     */
    public static void injectWheelEvent(double delta) {
        if (lwjgl3ifyInjectionFailed) return;
        try {
            org.lwjglx.input.Mouse.addWheelEvent(delta);
        } catch (Exception | LinkageError e) {
            // Wheel scrolling has no fallback: without lwjgl3ify there is no way to synthesise a
            // scroll event, so scrolling is simply unavailable.
            onLwjgl3ifyFailure("addWheelEvent", e);
        }
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
        if (!lwjgl3ifyInjectionFailed) {
            try {
                final int flag = 1 << mouseButton;
                final int flags = org.lwjglx.input.Mouse.sdlMouseButtonFlags;
                org.lwjglx.input.Mouse.sdlMouseButtonFlags = flags | flag;
                mouseClicked(screen, guiX, guiY, mouseButton);
                org.lwjglx.input.Mouse.sdlMouseButtonFlags = flags & ~flag;
                return;
            } catch (Exception | LinkageError e) {
                onLwjgl3ifyFailure("sdlMouseButtonFlags", e);
            }
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
