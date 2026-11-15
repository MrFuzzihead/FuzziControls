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
import com.mrfuzzihead.fuzzicontrols.util.GuiElementActivator;
import com.mrfuzzihead.fuzzicontrols.util.GuiFocusNavigator;
import com.mrfuzzihead.fuzzicontrols.util.GuiFocusRenderer;
import com.mrfuzzihead.fuzzicontrols.util.GuiKeyHelper;
import com.mrfuzzihead.fuzzicontrols.util.GuiMouseHelper;
import com.mrfuzzihead.fuzzicontrols.util.KeyboardHelper;
import com.mrfuzzihead.fuzzicontrols.util.LookStepper;
import com.mrfuzzihead.fuzzicontrols.util.MouseMotionTracker;
import com.mrfuzzihead.fuzzicontrols.util.ScrollStepper;

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
 * RT attack calls {@link KeyBinding#onTick} on the rising edge of the right trigger.
 * {@code driveKey()} holds the key's {@code getIsKeyPressed()} state for continuous block damage.
 * This matches vanilla mouse behavior: one click per physical press for entity/block interaction,
 * held state for continuous-breaking ticks.
 * regardless of what the crosshair is targeting.
 *
 * <p>
 * Registered via {@link cpw.mods.fml.common.FMLCommonHandler} in
 * {@link com.mrfuzzihead.fuzzicontrols.ClientProxy#init}.
 */
public class ControllerTickHandler {

    /**
     * Tap-versus-hold logic for the drop button, including the "this press closed a GUI" block.
     */
    private final DropTapHold dropTapHold = new DropTapHold();

    /**
     * Nanosecond timestamp of the previous rendered frame, used for accurate per-frame delta
     * calculation that is independent of frame rate and game-tick rate.
     * Zero before the first frame.
     */
    private long lastFrameNanos = 0L;

    /** Whether the controller had a given action active on the previous game tick (edge-trigger). */
    private final boolean[] wasActive = new boolean[ControllerAction.values().length];

    /** Whether a connected driver was present on the previous processed game tick (for edge resync). */
    private boolean wasControllerConnected = false;

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
    private final KeyClaimTracker heldKeys = new KeyClaimTracker();

    /** Two-threshold evaluation of stick directions, so a stick resting near a threshold cannot oscillate. */
    private final StickHysteresis stickHysteresis = new StickHysteresis();

    /** Evaluates a direction with hysteresis for axis bindings, falling back to the normal mapping. */
    private boolean withHysteresis(ControllerAction action, ControllerState state, ControllerMapping mapping) {
        return stickHysteresis.isActive(action, mapping, state);
    }

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

    // ---- D-pad discrete GUI navigation ----

    /** Focus-based navigator for D-pad button navigation (only used when {@link Config#dpadNavigation} is true). */
    private final GuiFocusNavigator guiFocusNavigator = new GuiFocusNavigator();

    public ControllerTickHandler() {
        // Wire the navigator to the focus-highlight renderer.
        GuiFocusRenderer.setNavigator(guiFocusNavigator);
    }

    /**
     * When true, the next {@link #handleGuiClick(GUI_LEFT_CLICK)} call should be skipped because
     * the D-pad confirm action already injected a click at the focused button's position.
     */
    private boolean suppressGuiLeftClick = false;

    /**
     * As {@link #suppressGuiLeftClick}, for the right-click action: while D-pad navigation owns the
     * focused element, the right click belongs to that element and must not also be injected at the
     * virtual cursor position.
     */
    private boolean suppressGuiRightClick = false;

    /** Edge-tracking for the five GUI_NAV_* actions. Offsets into {@link #wasActive}. */
    private static final int NAV_UP_IDX = ControllerAction.GUI_NAV_UP.ordinal();
    private static final int NAV_DOWN_IDX = ControllerAction.GUI_NAV_DOWN.ordinal();
    private static final int NAV_LEFT_IDX = ControllerAction.GUI_NAV_LEFT.ordinal();
    private static final int NAV_RIGHT_IDX = ControllerAction.GUI_NAV_RIGHT.ordinal();
    private static final int NAV_CONFIRM_IDX = ControllerAction.GUI_NAV_CONFIRM.ordinal();

    /**
     * Actions whose rising edge is consumed by the in-world path. While a GUI is open these are
     * not processed, so their {@link #wasActive} entries must be advanced each GUI tick; otherwise
     * a button pressed/released inside the screen leaves a stale edge.
     */
    private static final ControllerAction[] IN_WORLD_EDGE_ACTIONS = { ControllerAction.ATTACK,
        ControllerAction.PICK_BLOCK, ControllerAction.INVENTORY, ControllerAction.HOTBAR_NEXT,
        ControllerAction.HOTBAR_PREV, ControllerAction.CHAT, ControllerAction.COMMAND, ControllerAction.PAUSE,
        ControllerAction.SNEAK };

    /** Whether D-pad discrete navigation currently owns the GUI (see {@link DpadNavState}). */
    private final DpadNavState dpadNav = new DpadNavState();

    /** Auto-repeat for held D-pad directions, so long lists can be crossed without mashing. */
    private final NavRepeat navRepeat = new NavRepeat();

    /**
     * Set this flag to true in {@link #onRenderTick} when the mouse has moved since the
     * last game tick. Checked and cleared in {@link #onClientTick} to detect OS mouse
     * movement for deactivating D-pad nav on hybrid input.
     */
    private boolean mouseMovedSinceLastTick = false;

    /**
     * Distinguishes real mouse movement from the cursor warps this mod performs itself, so a
     * D-pad slot confirm or a sub-threshold stick nudge cannot deactivate D-pad navigation.
     */
    private final MouseMotionTracker mouseMotionTracker = new MouseMotionTracker();

    // -------------------------------------------------------------------------
    // Game tick — 20 Hz — movement, buttons, edge triggers
    // -------------------------------------------------------------------------

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;

        Minecraft mc = Minecraft.getMinecraft();

        ControllerManager manager = ControllerManager.getInstance();
        // Focus controls only the hot-plug scan rate (1 s focused, 30 s unfocused); an active
        // driver is still polled every tick so edge state stays in sync across alt-tabs.
        boolean displayActive = Display.isActive();
        manager.setFocused(displayActive);
        manager.tick();
        ControllerState state = manager.getState();
        ControllerMapping mapping = Config.controllerMapping;

        boolean connected = manager.isActive();

        if (!displayActive || !connected) {
            // Do not process controller input while the window is unfocused or no controller is
            // connected. This prevents cursor/button actions from affecting the game when the
            // player is in another application.
            releaseAllControllerKeys();
            // Follow the controller state through the skipped period so no stale edge fires.
            syncEdges(state, mapping);
            wasControllerConnected = connected;
            return;
        }

        // If the controller just (re)connected, sync edges so a button that was held during the
        // disconnected period does not fire an unwanted edge now.
        if (connected && !wasControllerConnected) {
            syncEdges(state, mapping);
        }
        wasControllerConnected = connected;

        // ---- GUI-only path (main menu, inventory, pause, chat, etc.) ----
        // Runs even when thePlayer is null (e.g. main menu).
        if (mc.currentScreen != null) {
            // Initialize cursor to screen center on first tick a screen is open.
            if (!guiCursorInitialized) {
                guiCursorX = mc.displayWidth / 2f;
                guiCursorY = mc.displayHeight / 2f;
                guiCursorInitialized = true;
                warpCursor(mc, guiCursorX, guiCursorY);
            }
            // ---- D-pad discrete navigation state machine ----
            // ---- D-pad discrete navigation state machine (see DpadNavState) ----
            // Activation: any D-pad ↑/↓/←/→ physically pressed (not rising edge — the rising edge is
            // preserved for the actual focus movement below).
            // Deactivation: left-stick movement past the threshold, or OS mouse movement.
            boolean anyDpadPressed = mapping.isActive(ControllerAction.GUI_NAV_UP, state)
                || mapping.isActive(ControllerAction.GUI_NAV_DOWN, state)
                || mapping.isActive(ControllerAction.GUI_NAV_LEFT, state)
                || mapping.isActive(ControllerAction.GUI_NAV_RIGHT, state);

            boolean navActive = dpadNav.update(
                Config.dpadNavigation,
                anyDpadPressed,
                state.leftStickX(),
                state.leftStickY(),
                mouseMovedSinceLastTick);
            GuiFocusRenderer.setNavActive(navActive);
            mouseMovedSinceLastTick = false;

            // ---- D-pad navigation actions (only when config is ON and navigation is active) ----
            if (navActive) {
                guiFocusNavigator.update(mc.currentScreen);

                // 2D spatial navigation: up/down/left/right find the nearest item
                // in that direction based on screen-position centroids. Holding a direction repeats
                // after NavRepeat.INITIAL_DELAY_TICKS so long lists can be crossed without mashing.
                if (navRepeat.update(ControllerAction.GUI_NAV_UP, isNavHeld(NAV_UP_IDX, state, mapping))) {
                    guiFocusNavigator.focusUp();
                }
                if (navRepeat.update(ControllerAction.GUI_NAV_DOWN, isNavHeld(NAV_DOWN_IDX, state, mapping))) {
                    guiFocusNavigator.focusDown();
                }

                // Left/Right: if focused on a slider, adjust value; otherwise navigate.
                if (navRepeat.update(ControllerAction.GUI_NAV_LEFT, isNavHeld(NAV_LEFT_IDX, state, mapping))) {
                    if (guiFocusNavigator.getFocusedButton() != null
                        && GuiFocusNavigator.isSliderButton(guiFocusNavigator.getFocusedButton())) {
                        guiFocusNavigator.sliderLeft(Config.dpadSliderStep);
                    } else {
                        guiFocusNavigator.focusLeft();
                    }
                }
                if (navRepeat.update(ControllerAction.GUI_NAV_RIGHT, isNavHeld(NAV_RIGHT_IDX, state, mapping))) {
                    if (guiFocusNavigator.getFocusedButton() != null
                        && GuiFocusNavigator.isSliderButton(guiFocusNavigator.getFocusedButton())) {
                        guiFocusNavigator.sliderRight(Config.dpadSliderStep);
                    } else {
                        guiFocusNavigator.focusRight();
                    }
                }

                // Confirm (A / left click) and right-click (X) both act on the focused element
                // while D-pad navigation is active, instead of on wherever the virtual cursor is.
                if (isNavRisingEdge(NAV_CONFIRM_IDX, state, mapping)) {
                    activateFocused(0);
                }
                if (mapping.isActive(ControllerAction.GUI_RIGHT_CLICK, state) && !wasGuiRightClick) {
                    activateFocused(1);
                }

                // While an element is focused the mouse buttons belong to it, so suppress the
                // cursor-based paths for both edges. Without this, releasing the button injects a
                // stray mouse-up at the virtual cursor position, which vanilla's
                // GuiContainer.mouseMovedOrUp() turns into a click when a stack is on the cursor.
                boolean navOwnsMouseButtons = guiFocusNavigator.getFocusedItem() != null;
                suppressGuiLeftClick = navOwnsMouseButtons;
                suppressGuiRightClick = navOwnsMouseButtons;
            } else {
                // Keep nav edges clean when dpadNavigation is off or nav is INACTIVE
                consumeNavEdges(state, mapping);
            }

            // Virtual-cursor clicks — unless D-pad navigation already handled the focused element.
            if (!suppressGuiLeftClick) {
                handleGuiClick(ControllerAction.GUI_LEFT_CLICK, 0, state, mapping, mc);
            } else {
                // Advance the edge state so the press neither repeats nor falls through to the
                // cursor on the release tick.
                wasGuiLeftClick = mapping.isActive(ControllerAction.GUI_LEFT_CLICK, state);
            }
            suppressGuiLeftClick = false;

            if (!suppressGuiRightClick) {
                handleGuiClick(ControllerAction.GUI_RIGHT_CLICK, 1, state, mapping, mc);
            } else {
                wasGuiRightClick = mapping.isActive(ControllerAction.GUI_RIGHT_CLICK, state);
            }
            suppressGuiRightClick = false;

            handleGuiShiftClick(state, mapping, mc);

            // If the keyboard Shift key is pressed, clear any controller shift toggle so they
            // don't fight each other.
            if (Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT)) {
                guiShiftToggled = false;
            }

            // B / Circle: inject an Escape key press to back out of any screen.
            // GuiKeyHelper dispatches keyTyped(Escape) directly, which works for inventory, pause
            // menu, main-menu submenus, chat, etc. without needing a player.
            int bIdx = ControllerAction.CLOSE_GUI.ordinal();
            boolean bActive = mapping.isActive(ControllerAction.CLOSE_GUI, state)
                || mapping.isActive(ControllerAction.DROP_ITEM, state);
            if (bActive && !wasActive[bIdx]) {
                // Synthesize Escape: set it down, call keyTyped, then clear it.
                // GuiScreen.keyTyped with keyCode 1 (Escape) calls mc.displayGuiScreen(null)
                // or goes back to the parent screen — same as pressing Esc on the keyboard.
                GuiKeyHelper.injectKey(mc.currentScreen, Keyboard.KEY_ESCAPE, '\0');
                dropTapHold.blockNextRelease();
            }
            wasActive[bIdx] = bActive;
            // Also consume DROP_ITEM edge so it doesn't fire after screen closes.
            wasActive[ControllerAction.DROP_ITEM.ordinal()] = bActive;

            // Follow the in-world edge state through the GUI. Without this a button that was
            // pressed or released inside the screen leaves wasActive stale: the first press after
            // closing can be swallowed, or a held button can fire an unwanted edge.
            syncInWorldEdges(state, mapping);

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

        // Screen is closed — reset cursor init flag and consume GUI-click and nav edges cleanly.
        guiCursorInitialized = false;
        guiShiftToggled = false;
        suppressGuiLeftClick = false;
        suppressGuiRightClick = false;
        dpadNav.reset();
        navRepeat.clear();
        GuiFocusRenderer.setNavActive(false);
        mouseMovedSinceLastTick = false;
        mouseMotionTracker.reset();
        consumeGuiClick(ControllerAction.GUI_LEFT_CLICK, state, mapping);
        consumeGuiClick(ControllerAction.GUI_RIGHT_CLICK, state, mapping);
        consumeGuiClick(ControllerAction.GUI_SHIFT_CLICK, state, mapping);
        consumeNavEdges(state, mapping);

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
        // Stick-direction activation uses hysteresis so small excursions past dead-zone don't
        // cause micro-oscillation.
        boolean fwd = withHysteresis(ControllerAction.MOVE_FORWARD, state, mapping)
            && !isActionBlocked(ControllerAction.MOVE_FORWARD, mapping);
        boolean back = withHysteresis(ControllerAction.MOVE_BACKWARD, state, mapping)
            && !isActionBlocked(ControllerAction.MOVE_BACKWARD, mapping);
        boolean left = withHysteresis(ControllerAction.STRAFE_LEFT, state, mapping)
            && !isActionBlocked(ControllerAction.STRAFE_LEFT, mapping);
        boolean right = withHysteresis(ControllerAction.STRAFE_RIGHT, state, mapping)
            && !isActionBlocked(ControllerAction.STRAFE_RIGHT, mapping);

        driveKey(mc.gameSettings.keyBindForward, fwd);
        driveKey(mc.gameSettings.keyBindBack, back);
        driveKey(mc.gameSettings.keyBindLeft, left);
        driveKey(mc.gameSettings.keyBindRight, right);

        // --- Jump ---
        driveKey(
            mc.gameSettings.keyBindJump,
            mapping.isActive(ControllerAction.JUMP, state) && !isActionBlocked(ControllerAction.JUMP, mapping));

        // --- Sneak ---
        // Toggle mode: rising edge flips sneakToggled; the key is held down/up based on the flag.
        // Hold mode: key state tracks the button directly (same as other held bindings).
        handleSneak(ControllerAction.SNEAK, state, mapping, mc);

        // --- Sprint ---
        driveKey(
            mc.gameSettings.keyBindSprint,
            mapping.isActive(ControllerAction.SPRINT, state) && !isActionBlocked(ControllerAction.SPRINT, mapping));

        // --- Attack (hold) ---
        // Two separate mechanisms for the two attack modes Minecraft uses:
        //
        // 1. func_147115_a(leftClick) — called with getIsKeyPressed() — handles continuous
        // block-damage while holding. Driven by setKeyBindState so getIsKeyPressed() returns true.
        //
        // 2. func_147116_af() — called via the "while (keyBindAttack.isPressed())" loop which
        // consumes the press counter (incremented by KeyBinding.onTick). This is what fires
        // attackEntity() on mobs and clickBlock() on blocks (single click). We call onTick
        // only on the rising edge of RT so isPressed() fires exactly once per physical press,
        // matching how mouse clicks work. Calling onTick every held tick would make creative
        // insta-break (which destroys a block per click attempt) fire 20 times per second.
        int attackIdx = ControllerAction.ATTACK.ordinal();
        boolean attacking = mapping.isActive(ControllerAction.ATTACK, state)
            && !isActionBlocked(ControllerAction.ATTACK, mapping);
        boolean attackJustPressed = attacking && !wasActive[attackIdx];
        driveKey(mc.gameSettings.keyBindAttack, attacking);
        if (attackJustPressed) {
            // Queue one press for func_147116_af() so entity attacks, block clicks, and air
            // swings work on the first tick. Continuous break is handled by driveKey above.
            KeyBinding.onTick(mc.gameSettings.keyBindAttack.getKeyCode());
        }
        wasActive[attackIdx] = attacking;

        // --- Use item / place (hold) ---
        driveKey(
            mc.gameSettings.keyBindUseItem,
            mapping.isActive(ControllerAction.USE_ITEM, state) && !isActionBlocked(ControllerAction.USE_ITEM, mapping));

        // --- Pick block (one-shot) ---
        // Vanilla reads keyBindPickBlock.isPressed(), i.e. the KeyBinding press counter, not the
        // held state. driveKey() only sets `pressed`, so pick block never fired; use the same
        // edge helper as the inventory key.
        handleEdgeTrigger(ControllerAction.PICK_BLOCK, state, mapping, mc.gameSettings.keyBindPickBlock);

        // --- B button: drop item (no GUI open) ---
        handleBButton(ControllerAction.DROP_ITEM, state, mapping, mc);

        // --- Inventory, chat, command, pause and hotbar are all one-shot edges ---
        handleEdgeTrigger(ControllerAction.INVENTORY, state, mapping, mc.gameSettings.keyBindInventory);
        handleEdge(ControllerAction.CHAT, state, mapping, () -> mc.displayGuiScreen(new GuiChat("")));
        handleEdge(ControllerAction.COMMAND, state, mapping, () -> mc.displayGuiScreen(new GuiChat("/")));
        handleEdge(ControllerAction.PAUSE, state, mapping, () -> mc.displayGuiScreen(new GuiIngameMenu()));
        handleEdge(ControllerAction.HOTBAR_NEXT, state, mapping, () -> mc.thePlayer.inventory.changeCurrentItem(-1));
        handleEdge(ControllerAction.HOTBAR_PREV, state, mapping, () -> mc.thePlayer.inventory.changeCurrentItem(1));
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
        if (!manager.isActive()) {
            // Do not carry a stale frame timestamp across a disconnect/reconnect: the first frame
            // after activation would otherwise see a >0.1 s delta and jump the camera/cursor.
            lastFrameNanos = 0L;
            return;
        }

        // Poll fresh controller state each render frame so the camera and cursor never run on
        // staled game-tick data (polled at 20 Hz). For the camera this eliminates micro-stutter
        // when moving the stick between game ticks.
        ControllerState state = manager.pollFresh();

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
            // Track OS mouse movement for D-pad nav deactivation. MouseMotionTracker ignores the
            // motion events our own cursor warps generate, so the virtual cursor and D-pad slot
            // confirms no longer look like the player grabbing the mouse.
            if (mouseMotionTracker.userMoved(Mouse.getX(), Mouse.getY())) {
                mouseMovedSinceLastTick = true;
            }

            // GUI cursor — works on main menu (thePlayer may be null).
            applyGuiCursor(mc, state, deltaSeconds);

            // Right-stick scroll wheel injection (only when a GUI is open).
            // ScrollStepper keeps the delivered rate at Config.scrollSensitivity notches per
            // second regardless of frame rate, with a quadratic response curve for precision.
            if (Config.rightStickScrolling) {
                double wheelDelta = ScrollStepper
                    .wheelDelta(state.rightStickY(), Config.scrollSensitivity, deltaSeconds);
                if (wheelDelta != 0.0) {
                    GuiMouseHelper.injectWheelEvent(wheelDelta);
                }
            }
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
        if (heldKeys.isEmpty()) return;
        for (int code : heldKeys.claimed()) {
            // The controller only owns keys it pressed; a key the keyboard is holding must stay
            // pressed when the controller disconnects or the window loses focus.
            if (!KeyboardHelper.isPhysicallyDown(code)) {
                KeyBinding.setKeyBindState(code, false);
            }
        }
        heldKeys.clear();
        // Also cancel sneak toggle so the player doesn't remain stuck sneaking.
        sneakToggled = false;
    }

    /**
     * Drives a key binding from the controller without interfering with keyboard input.
     *
     * <ul>
     * <li>When {@code active} is true the controller claims the key: {@code setKeyBindState}
     * is called with {@code true} and the key code is recorded in {@code heldKeys}.</li>
     * <li>When {@code active} is false the controller only releases the key if it was the
     * controller itself that last pressed it (i.e. the key is in {@code heldKeys}).
     * If the key is not in the set — meaning the keyboard is driving it — we leave it alone.</li>
     * </ul>
     */
    private void driveKey(KeyBinding binding, boolean active) {
        int code = binding.getKeyCode();
        switch (heldKeys.update(code, active)) {
            case PRESS -> KeyBinding.setKeyBindState(code, true);
            case RELEASE -> KeyBinding.setKeyBindState(code, false);
            case NONE -> {}
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
        boolean active = mapping.isActive(action, state) && !isActionBlocked(action, mapping);
        boolean justPressed = active && !wasActive[idx];

        if (Config.sneakToggle) {
            if (justPressed) {
                sneakToggled = !sneakToggled;
            }

            // If the keyboard sneak key is physically pressed by the player, treat that as
            // intent to take over sneak from the controller. Clear the toggle and release
            // the controller's claim on the key so the keyboard drives it cleanly.
            // We intentionally do NOT guard with heldKeys.isClaimed(...) here — the
            // controller holding the key via driveKey does not mean the keyboard isn't also
            // being pressed by the player.
            int sneakKeyCode = mc.gameSettings.keyBindSneak.getKeyCode();
            if (sneakKeyCode > 0 && KeyboardHelper.isPhysicallyDown(sneakKeyCode) && sneakToggled) {
                sneakToggled = false;
                heldKeys.release(sneakKeyCode);
                // Do NOT set the key state to false here: the keyboard is physically holding it.
                // Clearing it would stop sneak until the player releases and presses the key again.
            }

            driveKey(mc.gameSettings.keyBindSneak, sneakToggled);
        } else {
            driveKey(mc.gameSettings.keyBindSneak, active);
        }

        wasActive[idx] = active;
    }

    private void handleEdgeTrigger(ControllerAction action, ControllerState state, ControllerMapping mapping,
        KeyBinding binding) {
        handleEdge(action, state, mapping, () -> KeyBinding.onTick(binding.getKeyCode()));
    }

    /**
     * Runs a one-shot effect on the rising edge of an action, keeping {@link #wasActive} current.
     *
     * <p>
     * This is the common implementation for inventory, pick block, chat, command, pause and the
     * hotbar buttons. Keeping it in one place is what makes the GUI-open edge sync in
     * {@link #syncInWorldEdges} consistent with the in-world handlers.
     * </p>
     */
    void handleEdge(ControllerAction action, ControllerState state, ControllerMapping mapping, Runnable effect) {
        int idx = action.ordinal();
        boolean active = mapping.isActive(action, state) && !isActionBlocked(action, mapping);
        if (active && !wasActive[idx]) {
            effect.run();
        }
        wasActive[idx] = active;
    }

    /**
     * Advances the edge state for actions handled only in the in-world path, without touching the
     * GUI/navigation/drop state. Called while a screen is open.
     */
    void syncInWorldEdges(ControllerState state, ControllerMapping mapping) {
        for (ControllerAction action : IN_WORLD_EDGE_ACTIONS) {
            wasActive[action.ordinal()] = mapping.isActive(action, state);
        }
    }

    /** Test-only: whether the given action was active on the previous processed tick. */
    boolean wasActiveForTest(ControllerAction action) {
        return wasActive[action.ordinal()];
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
     * Advances every edge-tracking field to the current controller state WITHOUT firing any
     * action. Called while the window is unfocused or the controller is disconnected (and once
     * on (re)connect) so that stale rising edges do not fire the moment processing resumes.
     */
    private void syncEdges(ControllerState state, ControllerMapping mapping) {
        for (ControllerAction action : ControllerAction.values()) {
            wasActive[action.ordinal()] = mapping.isActive(action, state);
        }
        wasGuiLeftClick = mapping.isActive(ControllerAction.GUI_LEFT_CLICK, state);
        wasGuiRightClick = mapping.isActive(ControllerAction.GUI_RIGHT_CLICK, state);
        wasGuiShiftClick = mapping.isActive(ControllerAction.GUI_SHIFT_CLICK, state);
        dropTapHold.reset();
        guiShiftToggled = false;
        suppressGuiLeftClick = false;
        suppressGuiRightClick = false;
        dpadNav.reset();
        navRepeat.clear();
        GuiFocusRenderer.setNavActive(false);
        mouseMovedSinceLastTick = false;
        mouseMotionTracker.reset();
        buttonsHeldOnGuiClose.clear();
        stickHysteresis.clear();
    }

    /**
     * Handles the B / Circle button's in-world drop behaviour. This handler only runs while no
     * GUI is open (the GUI-only path returns earlier), so there is no GUI branch here:
     * <ul>
     * <li>{@link Config#dropEntireStack} = false (default): drop one item on tap (release).</li>
     * <li>{@link Config#dropEntireStack} = true: drop one item on tap, or drop the entire
     * stack after the button is held for 10 ticks (0.5 s). Nothing drops on
     * the initial press — we wait to see whether it's a tap or a hold.</li>
     * </ul>
     *
     * <p>
     * The {@link DropTapHold} state machine ensures that if B was pressed while a GUI was open
     * (causing it to close), no item is dropped on that same press even after the GUI closes.
     */
    private void handleBButton(ControllerAction dropAction, ControllerState state, ControllerMapping mapping,
        Minecraft mc) {

        // Only the drop binding may drop in-world. CLOSE_GUI is handled by the GUI path; treating it
        // as a drop trigger meant remapping CLOSE_GUI to another button caused unwanted drops.
        boolean active = mapping.isActive(dropAction, state) && !isActionBlocked(dropAction, mapping);

        // Tap-versus-hold bookkeeping lives in DropTapHold so it can be unit tested.
        switch (dropTapHold.update(active, Config.dropEntireStack)) {
            case DROP_ONE -> mc.thePlayer.dropOneItem(false);
            case DROP_STACK -> mc.thePlayer.dropOneItem(true);
            case NONE -> {}
        }
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

        warpCursor(mc, guiCursorX, guiCursorY);
    }

    /**
     * Moves the OS cursor to the given position (top-left-origin display pixels) and records it as
     * a position this mod commanded, so the motion event it generates is not mistaken for the
     * player moving the mouse.
     */
    private void warpCursor(Minecraft mc, float displayX, float displayY) {
        int x = (int) displayX;
        int y = (int) displayY;
        Mouse.setCursorPosition(x, mc.displayHeight - 1 - y);
        // Record in the same space Mouse.getX()/getY() report (bottom-left origin).
        mouseMotionTracker.command(x, mc.displayHeight - 1 - y);
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

        boolean active = mapping.isActive(action, state);
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
     * Activates the currently focused element with the given mouse button, honouring the shift-click
     * toggle. Delegates to {@link GuiElementActivator} so the dispatch is shared with the client
     * self-test harness.
     *
     * @param mouseButton 0 = left, 1 = right
     * @return true when a focused element existed and consumed the press
     */
    private boolean activateFocused(int mouseButton) {
        boolean shiftClick = mouseButton == 0 && guiShiftToggled;
        boolean activated = GuiElementActivator
            .activate(guiFocusNavigator, mouseButton, Minecraft.getMinecraft(), shiftClick);
        if (activated && shiftClick) {
            // One shift-click per toggle press.
            guiShiftToggled = false;
        }
        return activated;
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
        boolean active = mapping.isActive(ControllerAction.GUI_SHIFT_CLICK, state);
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
        boolean active = mapping.isActive(action, state);
        if (action == ControllerAction.GUI_LEFT_CLICK) {
            wasGuiLeftClick = active;
        } else if (action == ControllerAction.GUI_RIGHT_CLICK) {
            wasGuiRightClick = active;
        } else if (action == ControllerAction.GUI_SHIFT_CLICK) {
            wasGuiShiftClick = active;
        }
    }

    /**
     * Returns true while a D-pad navigation action is held. Unlike {@link #isNavRisingEdge}, this does
     * not consume edge state: the caller feeds it to {@link NavRepeat}, which decides when a move
     * actually happens (immediately on press, then on a delay/interval).
     *
     * @param actionIdx ordinal of a GUI_NAV_* action
     * @return true when the action's button is currently held
     */
    private boolean isNavHeld(int actionIdx, ControllerState state, ControllerMapping mapping) {
        return mapping.isActive(ControllerAction.values()[actionIdx], state);
    }

    /**
     * Returns true once on the rising edge of a D-pad navigation action.
     * The edge state is consumed by {@link #wasActive} tracking.
     *
     * @param actionIdx ordinal of a GUI_NAV_* action
     * @return true on the first tick the action becomes active
     */
    private boolean isNavRisingEdge(int actionIdx, ControllerState state, ControllerMapping mapping) {
        boolean active = mapping.isActive(ControllerAction.values()[actionIdx], state);
        boolean rising = active && !wasActive[actionIdx];
        wasActive[actionIdx] = active;
        return rising;
    }

    /**
     * Advances the five nav edge-tracking fields without firing any action.
     * Called when {@link Config#dpadNavigation} is false (or no GUI is open) to keep nav
     * edges clean so they don't fire if the user switches dpadNavigation on mid-session.
     */
    private void consumeNavEdges(ControllerState state, ControllerMapping mapping) {
        wasActive[NAV_UP_IDX] = mapping.isActive(ControllerAction.GUI_NAV_UP, state);
        wasActive[NAV_DOWN_IDX] = mapping.isActive(ControllerAction.GUI_NAV_DOWN, state);
        wasActive[NAV_LEFT_IDX] = mapping.isActive(ControllerAction.GUI_NAV_LEFT, state);
        wasActive[NAV_RIGHT_IDX] = mapping.isActive(ControllerAction.GUI_NAV_RIGHT, state);
        wasActive[NAV_CONFIRM_IDX] = mapping.isActive(ControllerAction.GUI_NAV_CONFIRM, state);
        // Drop any pending auto-repeat so re-entering navigation starts from a fresh press.
        navRepeat.clear();
    }

    /**
     * Applies right-stick rotation to the player camera.
     *
     * <p>
     * Called every rendered frame. The delta is scaled by actual elapsed time ({@code deltaSeconds})
     * so the rotation speed is consistent regardless of frame rate. Vertical look honours the
     * player's inverted-mouse preference, which vanilla only applies to the mouse path.
     *
     * @param deltaSeconds seconds elapsed since the previous rendered frame
     */
    private static void applyLook(Minecraft mc, ControllerState state, float deltaSeconds) {
        if (state.rightStickX() == 0f && state.rightStickY() == 0f) return;

        float yawDelta = LookStepper.yawDelta(state.rightStickX(), Config.lookSensitivity, deltaSeconds);
        float pitchDelta = LookStepper
            .pitchDelta(state.rightStickY(), Config.lookSensitivity, deltaSeconds, mc.gameSettings.invertMouse);

        mc.thePlayer.rotationYaw += yawDelta;
        mc.thePlayer.rotationPitch = LookStepper.clampPitch(mc.thePlayer.rotationPitch + pitchDelta);
    }
}
