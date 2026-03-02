package com.mrfuzzihead.fuzzicontrols.util;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;

import net.minecraft.client.gui.GuiScreen;

import org.lwjgl.input.Mouse;

import com.mrfuzzihead.fuzzicontrols.FuzziControls;

/**
 * Injects synthetic mouse-button events into LWJGL 2's {@link Mouse} so that every part of
 * Minecraft's GUI system sees the controller click:
 * <ul>
 * <li>{@link GuiScreen#handleMouseInput()} — reads the event queue via {@code Mouse.next()}</li>
 * <li>{@link Mouse#isButtonDown(int)} — reads from a separate {@code buttons} ByteBuffer</li>
 * <li>{@link net.minecraft.client.gui.GuiSlot} — polls {@code isButtonDown(0)} every frame</li>
 * </ul>
 *
 * <h3>Why reflection for Minecraft methods?</h3>
 * <p>
 * {@code GuiScreen.mouseClicked} and {@code GuiScreen.mouseMovedOrUp} are {@code protected}.
 * A Forge Access Transformer (AT) can widen a method to {@code public}, but Java forbids a
 * subclass from <em>narrowing</em> the access of an overridden method. Applying
 * {@code public} to the base class would cause every vanilla/Forge subclass that overrides
 * these methods as {@code protected} (53+ classes) to fail to compile. Reflection is
 * therefore the only correct mod-side approach — both methods are resolved once at
 * class-load time via {@code getDeclaredMethod} so the overhead is negligible at runtime.
 *
 * <h3>Why reflection for LWJGL fields?</h3>
 * <p>
 * {@code Mouse.readBuffer} and {@code Mouse.buttons} are private static fields of the LWJGL
 * {@link Mouse} class. ATs and Mixins only target Minecraft/Forge classes, not third-party
 * libraries like LWJGL. Reflection is the only option.
 *
 * <h3>LWJGL 2 Mouse internals (verified via {@code javap} on lwjgl-2.9.4):</h3>
 * <p>
 * {@code Mouse.readBuffer} is a static {@link ByteBuffer}.
 * {@code Mouse.next()} reads one 22-byte record per call when NOT grabbed:
 *
 * <pre>
 *   byte  [0]    — button index
 *   byte  [1]    — state (0 = released, 1 = pressed)
 *   int   [2..5] — absolute X
 *   int   [6..9] — absolute Y
 *   int   [10..13] — dwheel
 *   long  [14..21] — timestamp (nanos)
 * </pre>
 *
 * {@code Mouse.buttons} has one byte per button; non-zero = held. Read by
 * {@link Mouse#isButtonDown(int)} and by {@link net.minecraft.client.gui.GuiSlot}.
 */
public final class GuiMouseHelper {

    /** Byte length of one mouse event record in the LWJGL 2 readBuffer (non-grabbed mode). */
    private static final int EVENT_BYTES = 22;

    // -------------------------------------------------------------------------
    // LWJGL fields — reflection unavoidable; ATs/Mixins cannot target LWJGL
    // -------------------------------------------------------------------------
    private static final Field READ_BUFFER_FIELD; // Mouse.readBuffer : ByteBuffer (static)
    private static final Field BUTTONS_FIELD; // Mouse.buttons : ByteBuffer (static)

    // -------------------------------------------------------------------------
    // Minecraft GuiScreen methods — reflection required because AT would break
    // all protected-override subclasses (53+ compile errors confirmed)
    // -------------------------------------------------------------------------
    private static final Method MOUSE_CLICKED; // GuiScreen.mouseClicked(int,int,int)
    private static final Method MOUSE_MOVED_OR_UP; // GuiScreen.mouseMovedOrUp(int,int,int)

    static {
        Field readBufField = null;
        Field buttonsField = null;
        try {
            readBufField = Mouse.class.getDeclaredField("readBuffer");
            readBufField.setAccessible(true);
            buttonsField = Mouse.class.getDeclaredField("buttons");
            buttonsField.setAccessible(true);
        } catch (Exception e) {
            FuzziControls.LOG.warn(
                "[FuzziControls] Could not reflect LWJGL Mouse fields — "
                    + "GuiSlot click navigation (world/server lists) will fall back to direct dispatch.",
                e);
        }
        READ_BUFFER_FIELD = readBufField;
        BUTTONS_FIELD = buttonsField;

        Method clicked = null;
        Method movedOrUp = null;
        try {
            clicked = GuiScreen.class.getDeclaredMethod("mouseClicked", int.class, int.class, int.class);
            clicked.setAccessible(true);
            movedOrUp = GuiScreen.class.getDeclaredMethod("mouseMovedOrUp", int.class, int.class, int.class);
            movedOrUp.setAccessible(true);
        } catch (Exception e) {
            FuzziControls.LOG.error(
                "[FuzziControls] Could not reflect GuiScreen mouse methods — "
                    + "GUI click navigation will be unavailable.",
                e);
        }
        MOUSE_CLICKED = clicked;
        MOUSE_MOVED_OR_UP = movedOrUp;
    }

    private GuiMouseHelper() {}

    /**
     * Synthesizes a mouse button press or release.
     *
     * <p>
     * Two things happen:
     * <ol>
     * <li>A 22-byte event record is appended to {@code Mouse.readBuffer} so that
     * {@link GuiScreen#handleInput()} processes it via {@code Mouse.next()} — firing
     * {@code mouseClicked} / {@code mouseMovedOrUp} at the current cursor position.</li>
     * <li>{@code Mouse.buttons[mouseButton]} is set so that {@link Mouse#isButtonDown(int)}
     * returns the correct value, enabling {@link net.minecraft.client.gui.GuiSlot}'s
     * per-frame hold-detection.</li>
     * </ol>
     *
     * <p>
     * Falls back to direct reflective dispatch of {@code mouseClicked} / {@code mouseMovedOrUp}
     * if the LWJGL buffer injection is unavailable.
     *
     * @param screen      active GUI screen (used for the fallback path)
     * @param mouseButton 0 = left button, 1 = right button
     * @param pressed     true = button down, false = button up
     * @param guiX        scaled GUI X for the fallback path
     * @param guiY        scaled GUI Y for the fallback path
     */
    public static void injectMouseButton(GuiScreen screen, int mouseButton, boolean pressed, int guiX, int guiY) {

        if (READ_BUFFER_FIELD != null && BUTTONS_FIELD != null) {
            try {
                ByteBuffer readBuffer = (ByteBuffer) READ_BUFFER_FIELD.get(null);
                ByteBuffer buttons = (ByteBuffer) BUTTONS_FIELD.get(null);

                // 1. Update buttons so isButtonDown() returns the correct state.
                if (buttons != null && mouseButton < buttons.capacity()) {
                    buttons.put(mouseButton, pressed ? (byte) 1 : (byte) 0);
                }

                // 2. Inject an event record so Mouse.next() fires.
                if (readBuffer != null && readBuffer.capacity() >= EVENT_BYTES) {
                    readBuffer.compact();
                    if (readBuffer.remaining() >= EVENT_BYTES) {
                        int cursorX = Mouse.getX();
                        int cursorY = Mouse.getY();
                        readBuffer.put((byte) mouseButton);
                        readBuffer.put(pressed ? (byte) 1 : (byte) 0);
                        readBuffer.putInt(cursorX);
                        readBuffer.putInt(cursorY);
                        readBuffer.putInt(0);
                        readBuffer.putLong(System.nanoTime());
                    }
                    readBuffer.flip();
                    return;
                }
            } catch (Exception e) {
                FuzziControls.LOG
                    .warn("[FuzziControls] Mouse event injection failed; using direct dispatch fallback.", e);
            }
        }

        // Fallback: direct reflective dispatch.
        if (pressed) {
            mouseClicked(screen, guiX, guiY, mouseButton);
        } else {
            mouseMovedOrUp(screen, guiX, guiY, mouseButton);
        }
    }

    /**
     * Dispatches a mouse press directly and synchronously to the screen, bypassing the
     * LWJGL {@code Mouse.readBuffer} async event queue. Used for shift-clicks where the
     * keyboard state must be patched for exactly the duration of the dispatch call.
     *
     * <p>
     * Also updates {@code Mouse.buttons[mouseButton]} for {@link Mouse#isButtonDown(int)}
     * consistency.
     *
     * @param screen      the currently active GUI screen
     * @param mouseButton 0 = left, 1 = right
     * @param guiX        scaled GUI X coordinate (top-left origin)
     * @param guiY        scaled GUI Y coordinate (top-left origin)
     */
    public static void directMouseClicked(GuiScreen screen, int mouseButton, int guiX, int guiY) {
        // Update the buttons buffer so isButtonDown() returns true during this click.
        if (BUTTONS_FIELD != null) {
            try {
                ByteBuffer buttons = (ByteBuffer) BUTTONS_FIELD.get(null);
                if (buttons != null && mouseButton < buttons.capacity()) {
                    buttons.put(mouseButton, (byte) 1);
                }
            } catch (Exception e) {
                FuzziControls.LOG.warn("[FuzziControls] directMouseClicked: failed to set buttons state.", e);
            }
        }

        // Synchronous reflective dispatch — shift state is still patched at this point.
        mouseClicked(screen, guiX, guiY, mouseButton);

        // Release the button in the buffer immediately after the click.
        if (BUTTONS_FIELD != null) {
            try {
                ByteBuffer buttons = (ByteBuffer) BUTTONS_FIELD.get(null);
                if (buttons != null && mouseButton < buttons.capacity()) {
                    buttons.put(mouseButton, (byte) 0);
                }
            } catch (Exception e) {
                FuzziControls.LOG.warn("[FuzziControls] directMouseClicked: failed to clear buttons state.", e);
            }
        }
    }

    /**
     * Invokes {@code screen.mouseClicked(mouseX, mouseY, mouseButton)} reflectively.
     */
    private static void mouseClicked(GuiScreen screen, int mouseX, int mouseY, int mouseButton) {
        if (MOUSE_CLICKED == null) return;
        try {
            MOUSE_CLICKED.invoke(screen, mouseX, mouseY, mouseButton);
        } catch (Exception e) {
            FuzziControls.LOG.warn("[FuzziControls] mouseClicked reflection failed.", e);
        }
    }

    /**
     * Invokes {@code screen.mouseMovedOrUp(mouseX, mouseY, state)} reflectively.
     */
    private static void mouseMovedOrUp(GuiScreen screen, int mouseX, int mouseY, int state) {
        if (MOUSE_MOVED_OR_UP == null) return;
        try {
            MOUSE_MOVED_OR_UP.invoke(screen, mouseX, mouseY, state);
        } catch (Exception e) {
            FuzziControls.LOG.warn("[FuzziControls] mouseMovedOrUp reflection failed.", e);
        }
    }
}
