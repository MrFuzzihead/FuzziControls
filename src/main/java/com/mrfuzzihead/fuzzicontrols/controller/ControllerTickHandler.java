package com.mrfuzzihead.fuzzicontrols.controller;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.GuiIngameMenu;
import net.minecraft.client.settings.KeyBinding;

import com.mrfuzzihead.fuzzicontrols.Config;

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
 * <li>{@link TickEvent.RenderTickEvent} (every rendered frame, 60+ Hz) — re-reads the latest
 * cached controller state and applies right-stick camera rotation so it feels as smooth as
 * mouse input.</li>
 * </ul>
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

    // -------------------------------------------------------------------------
    // Game tick — 20 Hz — movement, buttons, edge triggers
    // -------------------------------------------------------------------------

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null || mc.thePlayer == null) return;

        ControllerManager manager = ControllerManager.getInstance();
        manager.tick(); // poll hardware once per game tick

        if (!manager.isActive()) {
            releaseAllControllerKeys();
            return;
        }

        ControllerState state = manager.getState();
        ControllerMapping mapping = Config.controllerMapping;

        // --- Movement (left stick → held key bindings) ---
        // driveKey() only asserts a key as true when the controller wants it, and only
        // releases it (sets to false) when the controller itself was the last one to hold it.
        // This prevents the controller from clearing keys the keyboard is currently pressing.
        driveKey(
            mc.gameSettings.keyBindForward,
            mapping.isActive(ControllerAction.MOVE_FORWARD, state, Config.triggerThreshold));
        driveKey(
            mc.gameSettings.keyBindBack,
            mapping.isActive(ControllerAction.MOVE_BACKWARD, state, Config.triggerThreshold));
        driveKey(
            mc.gameSettings.keyBindLeft,
            mapping.isActive(ControllerAction.STRAFE_LEFT, state, Config.triggerThreshold));
        driveKey(
            mc.gameSettings.keyBindRight,
            mapping.isActive(ControllerAction.STRAFE_RIGHT, state, Config.triggerThreshold));

        // --- Jump ---
        driveKey(mc.gameSettings.keyBindJump, mapping.isActive(ControllerAction.JUMP, state, Config.triggerThreshold));

        // --- Sneak ---
        // Toggle mode: rising edge flips sneakToggled; the key is held down/up based on the flag.
        // Hold mode: key state tracks the button directly (same as other held bindings).
        handleSneak(ControllerAction.SNEAK, state, mapping, mc);

        // --- Sprint ---
        driveKey(
            mc.gameSettings.keyBindSprint,
            mapping.isActive(ControllerAction.SPRINT, state, Config.triggerThreshold));

        // --- Attack (hold) ---
        // Two separate mechanisms for the two attack modes Minecraft uses:
        //
        // 1. func_147115_a(leftClick) — called with getIsKeyPressed() — handles continuous
        // block-damage while holding. Driven by setKeyBindState so getIsKeyPressed() returns true.
        //
        // 2. func_147116_af() — called via the "while (keyBindAttack.isPressed())" loop which
        // consumes the press counter (incremented by KeyBinding.onTick). This is what fires
        // attackEntity() on mobs and clickBlock() on blocks (single click). We call onTick
        // every tick RT is held so the press counter is always ≥ 1 and the loop fires once
        // per game tick — hitting entities, blocks, and air swing alike.
        boolean attacking = mapping.isActive(ControllerAction.ATTACK, state, Config.triggerThreshold);
        driveKey(mc.gameSettings.keyBindAttack, attacking);
        if (attacking && mc.currentScreen == null) {
            // Queue one "press" per game tick so func_147116_af() fires this tick.
            KeyBinding.onTick(mc.gameSettings.keyBindAttack.getKeyCode());
        }

        // --- Use item / place (hold) ---
        driveKey(
            mc.gameSettings.keyBindUseItem,
            mapping.isActive(ControllerAction.USE_ITEM, state, Config.triggerThreshold));

        // --- Pick block ---
        driveKey(
            mc.gameSettings.keyBindPickBlock,
            mapping.isActive(ControllerAction.PICK_BLOCK, state, Config.triggerThreshold));

        // --- B button: close GUI when screen open, otherwise drop item ---
        handleBButton(ControllerAction.DROP_ITEM, ControllerAction.CLOSE_GUI, state, mapping, mc);

        // --- Inventory (edge-triggered) ---
        handleEdgeTrigger(ControllerAction.INVENTORY, state, mapping, mc.gameSettings.keyBindInventory);

        // --- Chat / Command / Pause (edge-triggered, only when no GUI is open) ---
        if (mc.currentScreen == null) {
            handleChat(ControllerAction.CHAT, state, mapping, mc);
            handleCommand(ControllerAction.COMMAND, state, mapping, mc);
            handlePause(ControllerAction.PAUSE, state, mapping, mc);
        } else {
            // Still consume the wasActive state so we don't get spurious fires on GUI close
            consumeEdge(ControllerAction.CHAT, state, mapping);
            consumeEdge(ControllerAction.COMMAND, state, mapping);
            consumeEdge(ControllerAction.PAUSE, state, mapping);
        }

        // --- Hotbar (edge-triggered, bumpers) ---
        handleHotbarNext(ControllerAction.HOTBAR_NEXT, state, mapping, mc);
        handleHotbarPrev(ControllerAction.HOTBAR_PREV, state, mapping, mc);
    }

    // -------------------------------------------------------------------------
    // Render tick — every frame — smooth camera
    // -------------------------------------------------------------------------

    @SubscribeEvent
    public void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null || mc.thePlayer == null) return;

        // Skip camera when a GUI is open (inventory, chat, pause, etc.)
        if (mc.currentScreen != null) return;

        ControllerManager manager = ControllerManager.getInstance();
        if (!manager.isActive()) return;

        ControllerState state = manager.getState();

        // Compute the frame delta time in seconds.
        // partialTicks is in [0, 1] and represents the fraction of a game tick (1/20 s)
        // that has elapsed since the last game tick.
        float partialTick = event.renderTickTime;
        float deltaTicks = partialTick - lastPartialTick;
        // Handle the wrap-around at each game tick boundary (partialTick resets to ~0)
        if (deltaTicks <= 0f) deltaTicks += 1f;
        lastPartialTick = partialTick;
        float deltaSeconds = deltaTicks / 20f;

        applyLook(mc, state, deltaSeconds);
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
        boolean active = mapping.isActive(action, state, Config.triggerThreshold);
        boolean justPressed = active && !wasActive[idx];

        if (Config.sneakToggle) {
            if (justPressed) {
                sneakToggled = !sneakToggled;
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
        boolean active = mapping.isActive(action, state, Config.triggerThreshold);
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

        boolean active = mapping.isActive(dropAction, state, Config.triggerThreshold)
            || mapping.isActive(closeAction, state, Config.triggerThreshold);

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
        boolean active = mapping.isActive(action, state, Config.triggerThreshold);
        if (active && !wasActive[idx]) {
            mc.thePlayer.inventory.changeCurrentItem(-1);
        }
        wasActive[idx] = active;
    }

    private void handleHotbarPrev(ControllerAction action, ControllerState state, ControllerMapping mapping,
        Minecraft mc) {
        int idx = action.ordinal();
        boolean active = mapping.isActive(action, state, Config.triggerThreshold);
        if (active && !wasActive[idx]) {
            mc.thePlayer.inventory.changeCurrentItem(1);
        }
        wasActive[idx] = active;
    }

    private void handlePause(ControllerAction action, ControllerState state, ControllerMapping mapping, Minecraft mc) {
        int idx = action.ordinal();
        boolean active = mapping.isActive(action, state, Config.triggerThreshold);
        if (active && !wasActive[idx]) {
            mc.displayGuiScreen(new GuiIngameMenu());
        }
        wasActive[idx] = active;
    }

    private void handleChat(ControllerAction action, ControllerState state, ControllerMapping mapping, Minecraft mc) {
        int idx = action.ordinal();
        boolean active = mapping.isActive(action, state, Config.triggerThreshold);
        if (active && !wasActive[idx]) {
            mc.displayGuiScreen(new GuiChat(""));
        }
        wasActive[idx] = active;
    }

    private void handleCommand(ControllerAction action, ControllerState state, ControllerMapping mapping,
        Minecraft mc) {
        int idx = action.ordinal();
        boolean active = mapping.isActive(action, state, Config.triggerThreshold);
        if (active && !wasActive[idx]) {
            mc.displayGuiScreen(new GuiChat("/"));
        }
        wasActive[idx] = active;
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
