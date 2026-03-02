package com.mrfuzzihead.fuzzicontrols.controller;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.GuiIngameMenu;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.settings.KeyBinding;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.Display;

import com.mrfuzzihead.fuzzicontrols.Config;
import com.mrfuzzihead.fuzzicontrols.util.GuiKeyHelper;
import com.mrfuzzihead.fuzzicontrols.util.GuiMouseHelper;
import com.mrfuzzihead.fuzzicontrols.util.KeyboardHelper;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * Client-side tick handler that reads controller input and synthesizes Minecraft actions.
 *
 * <p>
 * Responsibilities are split across two event types:
 * <ul>
 * <li>{@link TickEvent.ClientTickEvent} (20 Hz) — polls the controller, drives held key-bindings
 * (movement, jump, attack, …) and edge-triggered actions (inventory, chat, hotbar, …).</li>
 * <li>{@link TickEvent.RenderTickEvent} (every rendered frame, 60+ Hz) — when no GUI is open,
 * applies right-stick camera rotation. When a GUI is open, moves the virtual cursor using the
 * left stick and positions the real OS cursor via {@link Mouse#setCursorPosition}.</li>
 * </ul>
 *
 * <p>
 * <b>GUI navigation:</b> When {@code mc.currentScreen != null} the left stick drives a virtual
 * cursor (quadratic speed curve, configurable via {@link Config#inventoryCursorSensitivity}).
 * A (Xbox) / Cross (PS) fires a left-click and X (Xbox) / Square (PS) fires a right-click at
 * the cursor position via the access-transformed {@link GuiScreen#mouseClicked} /
 * {@link GuiScreen#mouseMovedOrUp}. This works with every vanilla and mod GUI automatically.
 *
 * <p>
 * RT attack calls {@link KeyBinding#onTick} on the attack key binding every tick it is held.
 * This increments the press counter that Minecraft's {@code runTick()} loop drains via
 * {@code keyBindAttack.isPressed()}, causing {@code func_147116_af()} to fire once per game
 * tick and deliver {@code attackEntity()} / {@code clickBlock()} / air-swing correctly
 * regardless of what the crosshair is targeting.
 *
 * <p>
 * Registered via {@link cpw.mods.fml.common.FMLCommonHandler} in
 * {@link com.mrfuzzihead.fuzzicontrols.ClientProxy#init}.
 */
public class ControllerTickHandler {

    /**
     * Degrees per second of camera rotation at maximum stick deflection, at sensitivity 1.0.
     * Scaled by {@link Config#lookSensitivity}.
     */
    private static final float BASE_DEGREES_PER_SECOND = 180f;

    /**
     * Number of game ticks the drop button must be held before it is considered a "hold"
     * (i.e. drop the entire stack). At 20 Hz, 10 ticks = 0.5 seconds.
     */
    private static final int DROP_HOLD_TICKS = 10;

    /** Fractional tick elapsed since the last game tick, provided by RenderTickEvent. */
    private float lastPartialTick = 0f;

    /**
     * Nanosecond timestamp of the previous rendered frame, used for accurate per-frame delta
     * calculation that is independent of frame rate and game-tick rate.
     * Zero before the first frame.
     */
    private long lastFrameNanos = 0L;

    /** Whether the controller had a given action active on the previous game tick (edge-trigger). */
    private final boolean[] wasActive = new boolean[ControllerAction.values().length];

    /** How many consecutive ticks the drop button has been held down. */
    private int dropHeldTicks = 0;

    /** True once the hold-drop has fired this press so we don't repeat it. */
    private boolean dropHoldFired = false;

    /**
     * True when the B button was released after closing a GUI screen, and the button has not
     * yet been physically released. Prevents the drop-item logic from firing on the first
     * "no-GUI" tick following a GUI close.
     */
    private boolean dropBlockedByGui = false;

    /**
     * Tracks whether the sneak key is currently toggled on (only meaningful when
     * {@link Config#sneakToggle} is true).
     */
    private boolean sneakToggled = false;

    /**
     * Set of key codes that the controller is currently holding down.
     * We only call {@link KeyBinding#setKeyBindState} with {@code false} for keys we
     * previously set to {@code true} — this prevents the controller from overriding
     * keyboard input when the corresponding controller axis is in the dead-zone.
     */
    private final java.util.HashSet<Integer> controllerHeldKeys = new java.util.HashSet<>();

    /**
     * Fractional GUI cursor position in <em>screen pixels</em> (not scaled GUI coordinates).
     * Updated every render frame while a GUI is open. Clamped to the display bounds.
     */
    private float guiCursorX = 0f;

    private float guiCursorY = 0f;

    /**
     * Whether the GUI cursor position has been initialized to the display center.
     * Reset to false whenever a new GUI screen opens.
     */
    private boolean guiCursorInitialized = false;

    /** Tracks previous press state for GUI left-click (edge detection). */
    private boolean wasGuiLeftClick = false;

    /** Tracks previous press state for GUI right-click (edge detection). */
    private boolean wasGuiRightClick = false;

    /** Tracks previous press state for GUI shift-click (edge detection). */
    private boolean wasGuiShiftClick = false;

    /**
     * When true, the next GUI left-click will be a shift+left-click (moves entire stack).
     * Toggled by {@link ControllerAction#GUI_SHIFT_CLICK}. Cleared automatically when
     * the physical keyboard Shift key is pressed, or when the GUI closes.
     */
    private boolean guiShiftToggled = false;

    /**
     * Buttons that were physically held when a GUI screen last closed. Each button in this set
     * is blocked from triggering any in-world action until it has been fully released (i.e. it
     * is no longer in the active {@link ControllerState}). This prevents shared-button actions
     * such as A (GUI_LEFT_CLICK in a menu, JUMP in-world) from firing the in-world action
     * simply because the player was still holding the button at the moment the GUI closed —
     * regardless of how long they hold it.
     */
    private final java.util.EnumSet<ControllerButton> buttonsHeldOnGuiClose = java.util.EnumSet
        .noneOf(ControllerButton.class);

    // -------------------------------------------------------------------------
    // Game tick — 20 Hz — movement, buttons, edge triggers
    // -------------------------------------------------------------------------

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;

        // Do not process controller input when the game window does not have focus.
        // This prevents cursor movement and button presses from affecting the game while
        // the player is in another application.
        if (!Display.isActive()) {
            releaseAllControllerKeys();
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();

        ControllerManager manager = ControllerManager.getInstance();
        manager.tick(); // poll hardware once per game tick

        if (!manager.isActive()) {
            releaseAllControllerKeys();
            return;
        }

        ControllerState state = manager.getState();
        ControllerMapping mapping = Config.controllerMapping;

        // ---- GUI-only path (main menu, inventory, pause, chat, etc.) ----
        // Runs even when thePlayer is null (e.g. main menu).
        if (mc.currentScreen != null) {
            // Initialize cursor to screen center on first tick a screen is open.
            if (!guiCursorInitialized) {
                guiCursorX = mc.displayWidth / 2f;
                guiCursorY = mc.displayHeight / 2f;
                guiCursorInitialized = true;
                Mouse.setCursorPosition((int) guiCursorX, (int) guiCursorY);
            }
            handleGuiClick(ControllerAction.GUI_LEFT_CLICK, 0, state, mapping, mc);
            handleGuiClick(ControllerAction.GUI_RIGHT_CLICK, 1, state, mapping, mc);
            handleGuiShiftClick(state, mapping, mc);

            // If the keyboard Shift key is pressed, clear any controller shift toggle so they
            // don't fight each other.
            if (Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT)) {
                guiShiftToggled = false;
            }

            // B / Circle: inject an Escape key press to back out of any screen.
            // Using handleKeyboardInput with Escape (keycode 1) is universal — it works for
            // inventory, pause menu, main-menu submenus, chat, etc., without needing a player.
            int bIdx = ControllerAction.CLOSE_GUI.ordinal();
            boolean bActive = mapping.isActive(ControllerAction.CLOSE_GUI, state, Config.triggerThreshold)
                || mapping.isActive(ControllerAction.DROP_ITEM, state, Config.triggerThreshold);
            if (bActive && !wasActive[bIdx]) {
                mc.currentScreen.handleKeyboardInput();
                // Synthesize Escape: set it down, call keyTyped, then clear it.
                // GuiScreen.keyTyped with keyCode 1 (Escape) calls mc.displayGuiScreen(null)
                // or goes back to the parent screen — same as pressing Esc on the keyboard.
                GuiKeyHelper.injectKey(mc.currentScreen, Keyboard.KEY_ESCAPE, '\0');
                dropBlockedByGui = true;
            }
            wasActive[bIdx] = bActive;
            // Also consume DROP_ITEM edge so it doesn't fire after screen closes.
            wasActive[ControllerAction.DROP_ITEM.ordinal()] = bActive;

            // Consume in-world action edges so they don't fire on GUI close.
            consumeEdge(ControllerAction.CHAT, state, mapping);
            consumeEdge(ControllerAction.COMMAND, state, mapping);
            consumeEdge(ControllerAction.PAUSE, state, mapping);

            // Snapshot every button that is currently held. When the GUI closes next tick,
            // each of these buttons will be blocked from triggering in-world actions until
            // it is fully released. This prevents e.g. A held to click "Back to Game" from
            // also triggering JUMP after the screen closes, no matter how long it is held.
            buttonsHeldOnGuiClose.clear();
            for (ControllerButton btn : ControllerButton.values()) {
                if (state.isPressed(btn)) {
                    buttonsHeldOnGuiClose.add(btn);
                }
            }
            return;
        }

        // Screen is closed — reset cursor init flag and consume GUI-click edges cleanly.
        guiCursorInitialized = false;
        guiShiftToggled = false;
        consumeGuiClick(ControllerAction.GUI_LEFT_CLICK, state, mapping);
        consumeGuiClick(ControllerAction.GUI_RIGHT_CLICK, state, mapping);
        consumeGuiClick(ControllerAction.GUI_SHIFT_CLICK, state, mapping);

        // Remove any buttons from the post-GUI block set that have now been released.
        // A button is unblocked the moment it is no longer physically held.
        if (!buttonsHeldOnGuiClose.isEmpty()) {
            buttonsHeldOnGuiClose.removeIf(btn -> !state.isPressed(btn));
        }

        // ---- In-world path (requires a player) ----
        if (mc.theWorld == null || mc.thePlayer == null) return;

        // --- Movement (left stick → held key bindings) ---
        // driveKey() only asserts a key as true when the controller wants it, and only
        // releases it (sets to false) when the controller itself was the last one to hold it.
        // This prevents the controller from clearing keys the keyboard is currently pressing.
        boolean fwd = mapping.isActive(ControllerAction.MOVE_FORWARD, state, Config.triggerThreshold)
            && !isActionBlocked(ControllerAction.MOVE_FORWARD, mapping);
        boolean back = mapping.isActive(ControllerAction.MOVE_BACKWARD, state, Config.triggerThreshold)
            && !isActionBlocked(ControllerAction.MOVE_BACKWARD, mapping);
        boolean left = mapping.isActive(ControllerAction.STRAFE_LEFT, state, Config.triggerThreshold)
            && !isActionBlocked(ControllerAction.STRAFE_LEFT, mapping);
        boolean right = mapping.isActive(ControllerAction.STRAFE_RIGHT, state, Config.triggerThreshold)
            && !isActionBlocked(ControllerAction.STRAFE_RIGHT, mapping);

        driveKey(mc.gameSettings.keyBindForward, fwd);
        driveKey(mc.gameSettings.keyBindBack, back);
        driveKey(mc.gameSettings.keyBindLeft, left);
        driveKey(mc.gameSettings.keyBindRight, right);

        // --- Jump ---
        driveKey(
            mc.gameSettings.keyBindJump,
            mapping.isActive(ControllerAction.JUMP, state, Config.triggerThreshold)
                && !isActionBlocked(ControllerAction.JUMP, mapping));

        // --- Sneak ---
        // Toggle mode: rising edge flips sneakToggled; the key is held down/up based on the flag.
        // Hold mode: key state tracks the button directly (same as other held bindings).
        handleSneak(ControllerAction.SNEAK, state, mapping, mc);

        // --- Sprint ---
        driveKey(
            mc.gameSettings.keyBindSprint,
            mapping.isActive(ControllerAction.SPRINT, state, Config.triggerThreshold)
                && !isActionBlocked(ControllerAction.SPRINT, mapping));

        // --- Attack (hold) ---
        // Two separate mechanisms for the two attack modes Minecraft uses:
        //
        // 1. func_147115_a(leftClick) — called with getIsKeyPressed() — handles continuous
        // block-damage while holding. Driven by setKeyBindState so getIsKeyPressed() returns true.
        //
        // 2. func_147116_af() — called via the "while (keyBindAttack.isPressed())" loop which
        // consumes the press counter (incremented by KeyBinding.onTick). This is what fires
        // attackEntity() on mobs and clickBlock() on blocks (single click). We call onTick
        // every tick RT is held so the press counter is always >= 1 and the loop fires once
        // per game tick — hitting entities, blocks, and air swing alike.
        boolean attacking = mapping.isActive(ControllerAction.ATTACK, state, Config.triggerThreshold)
            && !isActionBlocked(ControllerAction.ATTACK, mapping);
        driveKey(mc.gameSettings.keyBindAttack, attacking);
        if (attacking) {
            // Queue one "press" per game tick so func_147116_af() fires this tick.
            KeyBinding.onTick(mc.gameSettings.keyBindAttack.getKeyCode());
        }

        // --- Use item / place (hold) ---
        driveKey(
            mc.gameSettings.keyBindUseItem,
            mapping.isActive(ControllerAction.USE_ITEM, state, Config.triggerThreshold)
                && !isActionBlocked(ControllerAction.USE_ITEM, mapping));

        // --- Pick block ---
        driveKey(
            mc.gameSettings.keyBindPickBlock,
            mapping.isActive(ControllerAction.PICK_BLOCK, state, Config.triggerThreshold)
                && !isActionBlocked(ControllerAction.PICK_BLOCK, mapping));

        // --- B button: drop item (no GUI open) ---
        handleBButton(ControllerAction.DROP_ITEM, ControllerAction.CLOSE_GUI, state, mapping, mc);

        // --- Inventory (edge-triggered) ---
        handleEdgeTrigger(ControllerAction.INVENTORY, state, mapping, mc.gameSettings.keyBindInventory);

        // --- Chat / Command / Pause (edge-triggered) ---
        handleChat(ControllerAction.CHAT, state, mapping, mc);
        handleCommand(ControllerAction.COMMAND, state, mapping, mc);
        handlePause(ControllerAction.PAUSE, state, mapping, mc);

        // --- Hotbar (edge-triggered, bumpers) ---
        handleHotbarNext(ControllerAction.HOTBAR_NEXT, state, mapping, mc);
        handleHotbarPrev(ControllerAction.HOTBAR_PREV, state, mapping, mc);
    }

    // -------------------------------------------------------------------------
    // Render tick — every frame — smooth camera OR GUI cursor
    // -------------------------------------------------------------------------

    @SubscribeEvent
    public void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;

        // Do not move the cursor or camera when the game window is not focused.
        if (!Display.isActive()) {
            lastFrameNanos = 0L; // reset delta so there's no jump when focus returns
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();

        ControllerManager manager = ControllerManager.getInstance();
        if (!manager.isActive()) return;

        ControllerState state = manager.getState();

        // Compute accurate frame delta using wall-clock time.
        // partialTick from RenderTickEvent only has game-tick resolution and breaks down at
        // very high frame rates (e.g. pause menu renders at uncapped FPS), causing the cursor
        // to move far faster than in-game. System.nanoTime() gives true elapsed time.
        long nowNanos = System.nanoTime();
        float deltaSeconds;
        if (lastFrameNanos == 0L) {
            deltaSeconds = 0f;
        } else {
            deltaSeconds = (nowNanos - lastFrameNanos) / 1_000_000_000f;
            // Clamp to 100 ms to avoid a huge jump after a freeze/GC pause.
            if (deltaSeconds > 0.1f) deltaSeconds = 0.1f;
        }
        lastFrameNanos = nowNanos;

        if (mc.currentScreen != null) {
            // GUI cursor — works on main menu (thePlayer may be null).
            applyGuiCursor(mc, state, deltaSeconds);
        } else if (mc.theWorld != null && mc.thePlayer != null) {
            // In-world camera — only when a world is loaded.
            applyLook(mc, state, deltaSeconds);
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Releases all key bindings the controller is currently holding and clears the tracking
     * set. Called when the controller disconnects mid-game to prevent keys from getting stuck.
     */
    private void releaseAllControllerKeys() {
        if (controllerHeldKeys.isEmpty()) return;
        for (int code : controllerHeldKeys) {
            KeyBinding.setKeyBindState(code, false);
        }
        controllerHeldKeys.clear();
        // Also cancel sneak toggle so the player doesn't remain stuck sneaking.
        sneakToggled = false;
    }

    /**
     * Drives a key binding from the controller without interfering with keyboard input.
     *
     * <ul>
     * <li>When {@code active} is true the controller claims the key: {@code setKeyBindState}
     * is called with {@code true} and the key code is recorded in {@link #controllerHeldKeys}.</li>
     * <li>When {@code active} is false the controller only releases the key if it was the
     * controller itself that last pressed it (i.e. the key is in {@link #controllerHeldKeys}).
     * If the key is not in the set — meaning the keyboard is driving it — we leave it alone.</li>
     * </ul>
     */
    private void driveKey(KeyBinding binding, boolean active) {
        int code = binding.getKeyCode();
        if (active) {
            KeyBinding.setKeyBindState(code, true);
            controllerHeldKeys.add(code);
        } else if (controllerHeldKeys.remove(code)) {
            // Only release keys the controller itself held; never clear keyboard input.
            KeyBinding.setKeyBindState(code, false);
        }
    }

    /**
     * Handles the sneak key binding with toggle or hold behavior depending on
     * {@link Config#sneakToggle}.
     *
     * <ul>
     * <li><b>Toggle mode (default):</b> each rising edge of the sneak button flips
     * {@link #sneakToggled}. The sneak key is held down while {@code sneakToggled} is true.</li>
     * <li><b>Hold mode:</b> the sneak key tracks the button state directly via
     * {@link #driveKey}, behaving like any other held binding.</li>
     * </ul>
     *
     * <p>
     * In both modes the controller does not interfere with keyboard sneak input when the
     * sneak button is not active (the {@link #driveKey} non-interference contract applies).
     */
    private void handleSneak(ControllerAction action, ControllerState state, ControllerMapping mapping, Minecraft mc) {
        int idx = action.ordinal();
        boolean active = mapping.isActive(action, state, Config.triggerThreshold) && !isActionBlocked(action, mapping);
        boolean justPressed = active && !wasActive[idx];

        if (Config.sneakToggle) {
            if (justPressed) {
                sneakToggled = !sneakToggled;
            }

            // If the keyboard sneak key is physically pressed by the player, treat that as
            // intent to take over sneak from the controller. Clear the toggle and release
            // the controller's claim on the key so the keyboard drives it cleanly.
            // We intentionally do NOT guard with !controllerHeldKeys.contains here — the
            // controller holding the key via driveKey does not mean the keyboard isn't also
            // being pressed by the player.
            int sneakKeyCode = mc.gameSettings.keyBindSneak.getKeyCode();
            if (sneakKeyCode > 0 && KeyboardHelper.isPhysicallyDown(sneakKeyCode) && sneakToggled) {
                sneakToggled = false;
                controllerHeldKeys.remove(sneakKeyCode);
                KeyBinding.setKeyBindState(sneakKeyCode, false);
            }

            driveKey(mc.gameSettings.keyBindSneak, sneakToggled);
        } else {
            driveKey(mc.gameSettings.keyBindSneak, active);
        }

        wasActive[idx] = active;
    }

    private void handleEdgeTrigger(ControllerAction action, ControllerState state, ControllerMapping mapping,
        KeyBinding binding) {
        int idx = action.ordinal();
        boolean active = mapping.isActive(action, state, Config.triggerThreshold) && !isActionBlocked(action, mapping);
        if (active && !wasActive[idx]) {
            KeyBinding.onTick(binding.getKeyCode());
        }
        wasActive[idx] = active;
    }

    /** Advances wasActive without performing any action — used to keep edge-trigger state clean. */
    private void consumeEdge(ControllerAction action, ControllerState state, ControllerMapping mapping) {
        int idx = action.ordinal();
        wasActive[idx] = mapping.isActive(action, state, Config.triggerThreshold);
    }

    /**
     * Returns true if the button bound to the given action is currently in the
     * {@link #buttonsHeldOnGuiClose} block set — meaning the button was held when a GUI
     * closed and has not been released yet. While blocked, the action must not fire.
     *
     * <p>
     * Axis-mapped actions (stick directions, triggers) are never blocked because they produce
     * continuous analogue values rather than discrete presses and are not typically held across
     * a GUI close.
     */
    private boolean isActionBlocked(ControllerAction action, ControllerMapping mapping) {
        if (buttonsHeldOnGuiClose.isEmpty()) return false;
        ControllerButton btn = mapping.getButton(action);
        if (btn == null || btn.isAxis()) return false;
        return buttonsHeldOnGuiClose.contains(btn);
    }

    /**
     * Consumes (advances edge state for) every in-world action without actually firing any of
     * them. Called for one tick immediately after a GUI closes so that buttons held during
     * the GUI (e.g. A held to click "Back to Game") do not trigger their in-world equivalents
     * (e.g. jump) on that same tick.
     */
    private void consumeAllInWorldEdges(ControllerState state, ControllerMapping mapping, Minecraft mc) {
        for (ControllerAction action : ControllerAction.values()) {
            wasActive[action.ordinal()] = mapping.isActive(action, state, Config.triggerThreshold);
        }
        // Also release any keys the controller was holding so they are not stuck.
        releaseAllControllerKeys();
    }

    /**
     * Handles the B / Circle button with dual behavior:
     * <ul>
     * <li>If a GUI screen is currently open: close it on the rising edge.</li>
     * <li>If no GUI is open and the button was <em>not</em> used to close a GUI this press:
     * <ul>
     * <li>{@link Config#dropEntireStack} = false (default): drop one item on tap (release).</li>
     * <li>{@link Config#dropEntireStack} = true: drop one item on tap, or drop the entire
     * stack after the button is held for {@value #DROP_HOLD_TICKS} ticks. Nothing drops on
     * the initial press — we wait to see whether it's a tap or a hold.</li>
     * </ul>
     * </li>
     * </ul>
     *
     * <p>
     * The {@link #dropBlockedByGui} flag ensures that if B was pressed while a GUI was open
     * (causing it to close), no item is dropped on that same press even after the GUI closes.
     */
    private void handleBButton(ControllerAction dropAction, ControllerAction closeAction, ControllerState state,
        ControllerMapping mapping, Minecraft mc) {

        int dropIdx = dropAction.ordinal();
        int closeIdx = closeAction.ordinal();

        boolean active = (mapping.isActive(dropAction, state, Config.triggerThreshold)
            && !isActionBlocked(dropAction, mapping))
            || (mapping.isActive(closeAction, state, Config.triggerThreshold)
                && !isActionBlocked(closeAction, mapping));

        boolean justPressed = active && !wasActive[dropIdx];
        boolean justReleased = !active && wasActive[dropIdx];

        if (mc.currentScreen != null) {
            // ---- GUI is open ----
            if (justPressed) {
                mc.thePlayer.closeScreen();
                // Block drop logic for this entire press.
                dropBlockedByGui = true;
            }
            dropHeldTicks = 0;
            dropHoldFired = false;
        } else {
            // ---- No GUI ----
            if (justReleased) {
                // Button released — clear the GUI-close block regardless of mode.
                if (!dropBlockedByGui && !dropHoldFired) {
                    // It was a genuine tap with no GUI interaction — drop one item.
                    mc.thePlayer.dropOneItem(false);
                }
                dropBlockedByGui = false;
                dropHeldTicks = 0;
                dropHoldFired = false;
            } else if (justPressed) {
                // Fresh press with no GUI open — start counting.
                dropHeldTicks = 1;
                dropHoldFired = false;
                dropBlockedByGui = false;
            } else if (active && !dropBlockedByGui) {
                dropHeldTicks++;
                if (Config.dropEntireStack && !dropHoldFired && dropHeldTicks >= DROP_HOLD_TICKS) {
                    mc.thePlayer.dropOneItem(true);
                    dropHoldFired = true;
                }
            }
        }

        wasActive[dropIdx] = active;
        wasActive[closeIdx] = active;
    }

    private void handleHotbarNext(ControllerAction action, ControllerState state, ControllerMapping mapping,
        Minecraft mc) {
        int idx = action.ordinal();
        boolean active = mapping.isActive(action, state, Config.triggerThreshold) && !isActionBlocked(action, mapping);
        if (active && !wasActive[idx]) {
            mc.thePlayer.inventory.changeCurrentItem(-1);
        }
        wasActive[idx] = active;
    }

    private void handleHotbarPrev(ControllerAction action, ControllerState state, ControllerMapping mapping,
        Minecraft mc) {
        int idx = action.ordinal();
        boolean active = mapping.isActive(action, state, Config.triggerThreshold) && !isActionBlocked(action, mapping);
        if (active && !wasActive[idx]) {
            mc.thePlayer.inventory.changeCurrentItem(1);
        }
        wasActive[idx] = active;
    }

    private void handlePause(ControllerAction action, ControllerState state, ControllerMapping mapping, Minecraft mc) {
        int idx = action.ordinal();
        boolean active = mapping.isActive(action, state, Config.triggerThreshold) && !isActionBlocked(action, mapping);
        if (active && !wasActive[idx]) {
            mc.displayGuiScreen(new GuiIngameMenu());
        }
        wasActive[idx] = active;
    }

    private void handleChat(ControllerAction action, ControllerState state, ControllerMapping mapping, Minecraft mc) {
        int idx = action.ordinal();
        boolean active = mapping.isActive(action, state, Config.triggerThreshold) && !isActionBlocked(action, mapping);
        if (active && !wasActive[idx]) {
            mc.displayGuiScreen(new GuiChat(""));
        }
        wasActive[idx] = active;
    }

    private void handleCommand(ControllerAction action, ControllerState state, ControllerMapping mapping,
        Minecraft mc) {
        int idx = action.ordinal();
        boolean active = mapping.isActive(action, state, Config.triggerThreshold) && !isActionBlocked(action, mapping);
        if (active && !wasActive[idx]) {
            mc.displayGuiScreen(new GuiChat("/"));
        }
        wasActive[idx] = active;
    }

    /**
     * Reference display area used to calibrate cursor speed.
     * At this resolution the raw {@link Config#inventoryCursorSensitivity} value applies directly.
     * For larger windows the speed is increased proportionally so the cursor traverses the same
     * <em>fraction</em> of the screen in the same time regardless of window size.
     */
    private static final float REFERENCE_WIDTH = 854f;

    private static final float REFERENCE_HEIGHT = 480f;

    private void applyGuiCursor(Minecraft mc, ControllerState state, float deltaSeconds) {
        float dx = state.leftStickX();
        float dy = -state.leftStickY(); // invert Y: stick up = cursor up = increasing LWJGL Y

        if (dx == 0f && dy == 0f) return;

        // Scale sensitivity by the ratio of the current display area to the reference area.
        // This keeps the cursor traversal time (fraction of screen per second) constant
        // regardless of whether the game runs in a small window or full-screen 4K.
        float widthScale = mc.displayWidth / REFERENCE_WIDTH;
        float heightScale = mc.displayHeight / REFERENCE_HEIGHT;
        float sensitivity = Config.inventoryCursorSensitivity * widthScale;

        // Quadratic scaling: gentle tilt = precise, full deflection = fast.
        // sensitivity is in display pixels/s at full deflection (at the reference resolution).
        guiCursorX += Math.signum(dx) * dx * dx * sensitivity * deltaSeconds;
        guiCursorY += Math.signum(dy) * dy * dy * (Config.inventoryCursorSensitivity * heightScale) * deltaSeconds;

        // Clamp to display bounds.
        guiCursorX = Math.max(0f, Math.min(mc.displayWidth - 1, guiCursorX));
        guiCursorY = Math.max(0f, Math.min(mc.displayHeight - 1, guiCursorY));

        Mouse.setCursorPosition((int) guiCursorX, (int) guiCursorY);
    }

    /**
     * Fires a GUI mouse-button press or release by injecting a real LWJGL mouse event into
     * {@link Mouse}'s internal event queue. This ensures both {@link GuiScreen#handleMouseInput()}
     * and {@link Mouse#isButtonDown(int)} (used by {@link net.minecraft.client.gui.GuiSlot}) see
     * the click, fixing selection in world lists, server lists, and similar slot-based GUIs.
     *
     * <p>
     * Coordinates are converted from LWJGL display-pixel space (bottom-left origin) to scaled
     * GUI space (top-left origin) for the fallback path.
     *
     * @param mouseButton 0 = left, 1 = right
     */
    private void handleGuiClick(ControllerAction action, int mouseButton, ControllerState state,
        ControllerMapping mapping, Minecraft mc) {

        boolean active = mapping.isActive(action, state, Config.triggerThreshold);
        boolean wasClick = action == ControllerAction.GUI_LEFT_CLICK ? wasGuiLeftClick : wasGuiRightClick;
        boolean justPressed = active && !wasClick;
        boolean justReleased = !active && wasClick;

        if (mc.currentScreen != null && (justPressed || justReleased)) {
            // Scaled GUI coords for the fallback path (direct GuiScreen dispatch).
            ScaledResolution sr = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight);
            int guiX = (int) (guiCursorX * sr.getScaledWidth() / mc.displayWidth);
            int guiY = sr.getScaledHeight() - (int) (guiCursorY * sr.getScaledHeight() / mc.displayHeight) - 1;

            // If shift is toggled on for GUI clicks and this is a left-click press, dispatch
            // directly and synchronously while the LWJGL keyboard buffer is patched to report
            // Shift as held. This ensures GuiContainer.handleMouseClick() sees the shift for
            // the exact duration of the call, moving the entire item stack.
            // The normal injectMouseButton path writes to Mouse.readBuffer (processed
            // asynchronously on the next game-loop iteration) which would be too late.
            boolean applyShift = guiShiftToggled && mouseButton == 0 && justPressed;
            if (applyShift) {
                final int gx = guiX;
                final int gy = guiY;
                final int btn = mouseButton;
                KeyboardHelper.withKeyHeld(
                    Keyboard.KEY_LSHIFT,
                    () -> GuiMouseHelper.directMouseClicked(mc.currentScreen, btn, gx, gy));
                // Also send the corresponding release through the normal queue so the screen
                // sees a complete press/release cycle.
                GuiMouseHelper.injectMouseButton(mc.currentScreen, mouseButton, false, guiX, guiY);
                // Consume the shift toggle — one shift-click per toggle press.
                guiShiftToggled = false;
            } else {
                GuiMouseHelper.injectMouseButton(mc.currentScreen, mouseButton, justPressed, guiX, guiY);
            }
        }

        if (action == ControllerAction.GUI_LEFT_CLICK) {
            wasGuiLeftClick = active;
        } else {
            wasGuiRightClick = active;
        }
    }

    /**
     * Handles {@link ControllerAction#GUI_SHIFT_CLICK}: on the rising edge of the bound button,
     * toggles {@link #guiShiftToggled}. The next GUI left-click will then be delivered with
     * the Shift key held, causing Minecraft to move the entire item stack.
     *
     * <p>
     * The toggle is also cleared when:
     * <ul>
     * <li>A shift-click is successfully delivered (consumed in {@link #handleGuiClick}).</li>
     * <li>The physical keyboard Shift key is pressed (checked in {@link #onClientTick}).</li>
     * <li>A GUI screen closes (reset in {@link #onClientTick}).</li>
     * </ul>
     */
    private void handleGuiShiftClick(ControllerState state, ControllerMapping mapping, Minecraft mc) {
        int idx = ControllerAction.GUI_SHIFT_CLICK.ordinal();
        boolean active = mapping.isActive(ControllerAction.GUI_SHIFT_CLICK, state, Config.triggerThreshold);
        boolean justPressed = active && !wasGuiShiftClick;
        if (justPressed) {
            guiShiftToggled = !guiShiftToggled;
        }
        wasGuiShiftClick = active;
    }

    /**
     * Advances the GUI click edge state without performing any action.
     * Called when no screen is open to keep {@link #wasGuiLeftClick}/{@link #wasGuiRightClick}/
     * {@link #wasGuiShiftClick} clean so rising edges don't carry over into a newly opened GUI.
     */
    private void consumeGuiClick(ControllerAction action, ControllerState state, ControllerMapping mapping) {
        boolean active = mapping.isActive(action, state, Config.triggerThreshold);
        if (action == ControllerAction.GUI_LEFT_CLICK) {
            wasGuiLeftClick = active;
        } else if (action == ControllerAction.GUI_RIGHT_CLICK) {
            wasGuiRightClick = active;
        } else if (action == ControllerAction.GUI_SHIFT_CLICK) {
            wasGuiShiftClick = active;
        }
    }

    /**
     * Applies right-stick rotation to the player camera.
     *
     * <p>
     * Called every rendered frame. The delta is scaled by actual elapsed time ({@code deltaSeconds})
     * so the rotation speed is consistent regardless of frame rate.
     *
     * <p>
     * With {@code rightStickY} already negated in the driver (push up → negative Y), a negative Y
     * value here means "look up", which maps to a negative pitch change — matching Minecraft's
     * convention that negative pitch = looking up.
     *
     * @param deltaSeconds seconds elapsed since the previous rendered frame
     */
    private static void applyLook(Minecraft mc, ControllerState state, float deltaSeconds) {
        if (state.rightStickX() == 0f && state.rightStickY() == 0f) return;

        float degreesPerSecond = BASE_DEGREES_PER_SECOND * Config.lookSensitivity;
        float yawDelta = state.rightStickX() * degreesPerSecond * deltaSeconds;
        float pitchDelta = state.rightStickY() * degreesPerSecond * deltaSeconds;

        mc.thePlayer.rotationYaw += yawDelta;
        mc.thePlayer.rotationPitch += pitchDelta;

        // Clamp pitch to [-90, 90]
        mc.thePlayer.rotationPitch = Math.max(-90f, Math.min(90f, mc.thePlayer.rotationPitch));
    }
}
