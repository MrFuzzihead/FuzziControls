package com.mrfuzzihead.fuzzicontrols.util;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;

import net.minecraft.client.gui.GuiScreen;

import org.lwjgl.input.Mouse;

import com.mrfuzzihead.fuzzicontrols.FuzziControls;

/**
 * Injects synthetic mouse-button events into LWJGL so that every part of Minecraft's GUI system
 * sees the controller click:
 * <ul>
 * <li>{@link GuiScreen#handleMouseInput()} — reads the event queue via {@code Mouse.next()}</li>
 * <li>{@link Mouse#isButtonDown(int)} — used by {@link net.minecraft.client.gui.GuiSlot}</li>
 * </ul>
 *
 * <h3>Two backends</h3>
 * <ul>
 * <li><b>lwjgl3ify / org.lwjglx (Java 25 target):</b> uses the public injection API —
 * {@code org.lwjglx.input.Mouse.addButtonEvent(int, boolean)} to enqueue the click and the public
 * {@code sdlMouseButtonFlags} bit-mask so {@code Mouse.isButtonDown} reports the correct state.</li>
 * <li><b>Vanilla LWJGL 2:</b> falls back to reflecting {@code Mouse.readBuffer} /
 * {@code Mouse.buttons} internal fields, or to direct {@link GuiScreen} dispatch.</li>
 * </ul>
 *
 * <p>
 * {@code GuiScreen.mouseClicked} / {@code mouseMovedOrUp} are {@code protected} and are deliberately
 * invoked reflectively: widening them with an access transformer breaks the 50+ vanilla/mod
 * subclasses that override them as {@code protected}. These two methods are Minecraft-only and are
 * therefore lwjgl3ify-agnostic.
 */
public final class GuiMouseHelper {

    private GuiMouseHelper() {}

    /** True when lwjgl3ify (org.lwjglx) is present; decided once at class load. */
    private static final boolean USING_LWJGL3IFY = isLwjgl3ifyPresent();

    // -------------------------------------------------------------------------
    // Minecraft GuiScreen methods — reflection required because an AT would break
    // all protected-override subclasses. Unaffected by lwjgl3ify.
    // -------------------------------------------------------------------------
    private static final Method MOUSE_CLICKED; // GuiScreen.mouseClicked(int,int,int)
    private static final Method MOUSE_MOVED_OR_UP; // GuiScreen.mouseMovedOrUp(int,int,int)

    // -------------------------------------------------------------------------
    // LWJGL 2 fallback only — reflecting the private Mouse buffers.
    // -------------------------------------------------------------------------
    private static final Field READ_BUFFER_FIELD;
    private static final Field BUTTONS_FIELD;

    static {
        Method clicked = null;
        Method movedOrUp = null;
        try {
            clicked = GuiScreen.class.getDeclaredMethod("mouseClicked", int.class, int.class, int.class);
            clicked.setAccessible(true);
            movedOrUp = GuiScreen.class.getDeclaredMethod("mouseMovedOrUp", int.class, int.class, int.class);
            movedOrUp.setAccessible(true);
        } catch (Exception e) {
            FuzziControls.LOG.error(
                "[FuzziControls] Could not reflect GuiScreen mouse methods — GUI click navigation will be unavailable.",
                e);
        }
        MOUSE_CLICKED = clicked;
        MOUSE_MOVED_OR_UP = movedOrUp;

        Field readBuf = null;
        Field buttons = null;
        if (!USING_LWJGL3IFY) {
            try {
                readBuf = Mouse.class.getDeclaredField("readBuffer");
                readBuf.setAccessible(true);
                buttons = Mouse.class.getDeclaredField("buttons");
                buttons.setAccessible(true);
            } catch (Exception e) {
                FuzziControls.LOG.warn(
                    "[FuzziControls] Could not reflect LWJGL Mouse fields — "
                        + "GuiSlot click navigation (world/server lists) will fall back to direct dispatch.",
                    e);
            }
        }
        READ_BUFFER_FIELD = readBuf;
        BUTTONS_FIELD = buttons;
    }

    /** Byte length of one mouse event record in the LWJGL 2 readBuffer (non-grabbed mode). */
    private static final int EVENT_BYTES = 22;

    /**
     * Synthesizes a mouse button press or release so both Minecraft's event-driven path
     * ({@code mouseClicked}/{@code handleMouseInput}) and the per-frame {@code isButtonDown}
     * poll ({@code GuiSlot}) see it.
     *
     * @param screen      active GUI screen (used for the fallback path)
     * @param mouseButton 0 = left button, 1 = right button
     * @param pressed     true = button down, false = button up
     * @param guiX        scaled GUI X for the fallback path
     * @param guiY        scaled GUI Y for the fallback path
     */
    public static void injectMouseButton(GuiScreen screen, int mouseButton, boolean pressed, int guiX, int guiY) {
        if (USING_LWJGL3IFY) {
            try {
                injectLwjgl3ify(mouseButton, pressed);
                return;
            } catch (Exception e) {
                FuzziControls.LOG
                    .warn("[FuzziControls] lwjgl3ify mouse injection failed; using direct dispatch fallback.", e);
            }
        } else if (injectLwjgl2(mouseButton, pressed)) {
            return;
        }
        // Fallback: direct reflective dispatch.
        if (pressed) {
            mouseClicked(screen, guiX, guiY, mouseButton);
        } else {
            mouseMovedOrUp(screen, guiX, guiY, mouseButton);
        }
    }

    /**
     * lwjgl3ify path: enqueue a button event and update the public SDL button-state bitmask that
     * {@code Mouse.isButtonDown} reads. {@code org.lwjglx.input.Mouse} is only referenced from
     * this branch, so it is resolved lazily and never loaded on a non-lwjgl3ify runtime.
     */
    private static void injectLwjgl3ify(int mouseButton, boolean pressed) {
        final int flag = 1 << mouseButton;
        final int flags = org.lwjglx.input.Mouse.sdlMouseButtonFlags;
        org.lwjglx.input.Mouse.sdlMouseButtonFlags = pressed ? (flags | flag) : (flags & ~flag);
        org.lwjglx.input.Mouse.addButtonEvent(mouseButton, pressed);
    }

    /**
     * Vanilla LWJGL2 path: patch {@code Mouse.isButtonDown} state and append a synthetic event
     * record to {@code Mouse.readBuffer} for {@code Mouse.next()} to pick up.
     *
     * @return true if the injection succeeded; false to signal the caller to use direct dispatch.
     */
    private static boolean injectLwjgl2(int mouseButton, boolean pressed) {
        if (READ_BUFFER_FIELD == null || BUTTONS_FIELD == null) return false;
        try {
            ByteBuffer readBuffer = (ByteBuffer) READ_BUFFER_FIELD.get(null);
            ByteBuffer buttons = (ByteBuffer) BUTTONS_FIELD.get(null);

            if (buttons != null && mouseButton < buttons.capacity()) {
                buttons.put(mouseButton, pressed ? (byte) 1 : (byte) 0);
            }
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
            }
            return true;
        } catch (Exception e) {
            FuzziControls.LOG.warn("[FuzziControls] Mouse event injection failed; using direct dispatch fallback.", e);
            return false;
        }
    }

    /**
     * Dispatches a mouse press directly and synchronously to the screen (used for shift-clicks,
     * where the keyboard state must be patched for exactly the duration of the dispatch call).
     * Also updates the button-state mask for {@link Mouse#isButtonDown(int)} consistency.
     *
     * @param screen      the currently active GUI screen
     * @param mouseButton 0 = left, 1 = right
     * @param guiX        scaled GUI X coordinate (top-left origin)
     * @param guiY        scaled GUI Y coordinate (top-left origin)
     */
    public static void directMouseClicked(GuiScreen screen, int mouseButton, int guiX, int guiY) {
        if (USING_LWJGL3IFY) {
            try {
                final int flag = 1 << mouseButton;
                final int flags = org.lwjglx.input.Mouse.sdlMouseButtonFlags;
                org.lwjglx.input.Mouse.sdlMouseButtonFlags = flags | flag;
                mouseClicked(screen, guiX, guiY, mouseButton);
                org.lwjglx.input.Mouse.sdlMouseButtonFlags = flags & ~flag;
                return;
            } catch (Exception e) {
                FuzziControls.LOG.warn("[FuzziControls] directMouseClicked (lwjgl3ify) failed.", e);
            }
        }
        // LWJGL 2 path: set button state for isButtonDown() consistency while clicking.
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
        mouseClicked(screen, guiX, guiY, mouseButton);
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

    /** Invokes {@code screen.mouseClicked(mouseX, mouseY, mouseButton)} reflectively. */
    private static void mouseClicked(GuiScreen screen, int mouseX, int mouseY, int mouseButton) {
        if (MOUSE_CLICKED == null) return;
        try {
            MOUSE_CLICKED.invoke(screen, mouseX, mouseY, mouseButton);
        } catch (Exception e) {
            FuzziControls.LOG.warn("[FuzziControls] mouseClicked reflection failed.", e);
        }
    }

    /** Invokes {@code screen.mouseMovedOrUp(mouseX, mouseY, state)} reflectively. */
    private static void mouseMovedOrUp(GuiScreen screen, int mouseX, int mouseY, int state) {
        if (MOUSE_MOVED_OR_UP == null) return;
        try {
            MOUSE_MOVED_OR_UP.invoke(screen, mouseX, mouseY, state);
        } catch (Exception e) {
            FuzziControls.LOG.warn("[FuzziControls] mouseMovedOrUp reflection failed.", e);
        }
    }

    private static boolean isLwjgl3ifyPresent() {
        try {
            Class.forName("org.lwjglx.input.Mouse", false, GuiMouseHelper.class.getClassLoader());
            return true;
        } catch (Throwable t) {
            return false;
        }
    }
}
