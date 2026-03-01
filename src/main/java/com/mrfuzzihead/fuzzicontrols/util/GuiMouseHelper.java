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
 * <h3>LWJGL 2 Mouse internals (verified via {@code javap} on lwjgl-2.9.4):</h3>
 * <p>
 * {@code Mouse.readBuffer} is a static {@link ByteBuffer} on the {@link Mouse} class itself.
 * {@code Mouse.next()} reads one 22-byte record per call when NOT grabbed (GUI / non-grabbed
 * mode, which is what Minecraft uses when a GUI screen is open):
 *
 * <pre>
 *   byte  [0]    — button index (-1 cast = 0xFF = move-only event; 0 = LMB; 1 = RMB)
 *   byte  [1]    — state (0 = released, 1 = pressed)
 *   int   [2..5] — absolute X position in display pixels (bottom-left origin)
 *   int   [6..9] — absolute Y position in display pixels
 *   int   [10..13] — dwheel (scroll delta, 0 for button events)
 *   long  [14..21] — event timestamp in nanoseconds
 * </pre>
 *
 * {@code Mouse.buttons} is a separate static {@link ByteBuffer} with one byte per button
 * index; a value of 1 means the button is currently held. This is what
 * {@link Mouse#isButtonDown(int)} reads. We write to it directly so that
 * {@link net.minecraft.client.gui.GuiSlot}'s per-frame {@code isButtonDown(0)} check also
 * sees the synthesized press.
 *
 * <p>
 * Both fields are resolved once at class-load time. If reflection fails, the helper falls
 * back to the direct (protected) {@link GuiScreen#mouseClicked} / {@link GuiScreen#mouseMovedOrUp}
 * path, which works for all button-based GUIs but not for {@code GuiSlot} list screens.
 */
public final class GuiMouseHelper {

    /** Byte length of one mouse event record in the LWJGL 2 readBuffer (non-grabbed mode). */
    private static final int EVENT_BYTES = 22;

    private static final Field READ_BUFFER_FIELD; // Mouse.readBuffer : ByteBuffer (static)
    private static final Field BUTTONS_FIELD; // Mouse.buttons : ByteBuffer (static)

    // GuiScreen protected-method fallback
    private static final Method MOUSE_CLICKED;
    private static final Method MOUSE_MOVED_OR_UP;

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
        } catch (NoSuchMethodException e) {
            FuzziControls.LOG.error("[FuzziControls] Failed to reflect GuiScreen mouse methods.", e);
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
     * <li>A 22-byte event record is appended to {@code Mouse.readBuffer} so that Minecraft's
     * {@code Mouse.next()} loop in {@link GuiScreen#handleInput()} processes it — firing
     * {@link GuiScreen#mouseClicked} / {@link GuiScreen#mouseMovedOrUp} at the current
     * cursor position.</li>
     * <li>{@code Mouse.buttons[mouseButton]} is set to 1 (pressed) or 0 (released) so that
     * {@link Mouse#isButtonDown(int)} returns the correct value — enabling
     * {@link net.minecraft.client.gui.GuiSlot}'s per-frame hold-detection to work.</li>
     * </ol>
     *
     * <p>
     * The X/Y values embedded in the event record are taken from {@code Mouse.getX()} /
     * {@code Mouse.getY()}, which already reflect the position set by
     * {@link Mouse#setCursorPosition} from the cursor movement code. Minecraft's
     * {@link GuiScreen#handleMouseInput()} converts these to GUI-scaled coordinates itself.
     *
     * <p>
     * Falls back to direct {@link GuiScreen#mouseClicked} / {@link GuiScreen#mouseMovedOrUp}
     * dispatch if reflection is unavailable.
     *
     * @param screen      active GUI screen (used only for the fallback path)
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

                // 1. Update the buttons array so isButtonDown() returns the correct state.
                if (buttons != null && mouseButton < buttons.capacity()) {
                    buttons.put(mouseButton, pressed ? (byte) 1 : (byte) 0);
                }

                // 2. Inject an event record into readBuffer so Mouse.next() fires.
                // We need to switch to write mode, append, then flip back.
                // readBuffer is normally in read mode (limit = written data, position = read cursor).
                // We compact it to move unread data to the front, append, then flip.
                if (readBuffer != null && readBuffer.capacity() >= EVENT_BYTES) {
                    // readBuffer is normally in read mode. compact() moves any unread bytes
                    // to the front and switches to write mode so we can append.
                    readBuffer.compact();

                    // Only write if there is room for another event.
                    if (readBuffer.remaining() >= EVENT_BYTES) {
                        int cursorX = Mouse.getX();
                        int cursorY = Mouse.getY();
                        readBuffer.put((byte) mouseButton); // button index
                        readBuffer.put(pressed ? (byte) 1 : (byte) 0); // state
                        readBuffer.putInt(cursorX); // abs X
                        readBuffer.putInt(cursorY); // abs Y
                        readBuffer.putInt(0); // dwheel
                        readBuffer.putLong(System.nanoTime()); // timestamp
                    }

                    // Flip back to read mode.
                    readBuffer.flip();
                    return;
                }
            } catch (Exception e) {
                FuzziControls.LOG.warn("[FuzziControls] Mouse event injection failed; using GuiScreen fallback.", e);
            }
        }

        // Fallback: direct protected dispatch (works for GuiButton-based screens, not GuiSlot).
        if (pressed) {
            mouseClicked(screen, guiX, guiY, mouseButton);
        } else {
            mouseMovedOrUp(screen, guiX, guiY, mouseButton);
        }
    }

    /**
     * Invokes {@code screen.mouseClicked(mouseX, mouseY, mouseButton)} reflectively.
     */
    public static void mouseClicked(GuiScreen screen, int mouseX, int mouseY, int mouseButton) {
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
    public static void mouseMovedOrUp(GuiScreen screen, int mouseX, int mouseY, int state) {
        if (MOUSE_MOVED_OR_UP == null) return;
        try {
            MOUSE_MOVED_OR_UP.invoke(screen, mouseX, mouseY, state);
        } catch (Exception e) {
            FuzziControls.LOG.warn("[FuzziControls] mouseMovedOrUp reflection failed.", e);
        }
    }
}
