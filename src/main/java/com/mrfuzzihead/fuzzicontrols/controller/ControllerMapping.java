package com.mrfuzzihead.fuzzicontrols.controller;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * Holds the mapping from every {@link ControllerAction} to a {@link ControllerButton}.
 *
 * <p>
 * Default bindings follow the standard console Minecraft layout documented at
 * <a href="https://minecraft.fandom.com/wiki/Controls">...</a> (Controller section).
 *
 * <p>
 * The mapping is mutable so that {@link com.mrfuzzihead.fuzzicontrols.Config} can
 * override individual bindings from the configuration file.
 *
 * <p>
 * <b>When changing {@link #applyDefaults()}, update {@code BUTTON_BINDINGS.md} in the
 * project root to keep the human-readable reference in sync.</b>
 */
public class ControllerMapping {

    private final Map<ControllerAction, ControllerButton> actionToButton;

    /** Creates a mapping pre-populated with the default wiki bindings. */
    public ControllerMapping() {
        actionToButton = new EnumMap<>(ControllerAction.class);
        applyDefaults();
    }

    /** Populates (or resets) all bindings to the standard Minecraft controller defaults. */
    public void applyDefaults() {
        // Movement — left stick
        actionToButton.put(ControllerAction.MOVE_FORWARD, ControllerButton.LEFT_STICK_UP);
        actionToButton.put(ControllerAction.MOVE_BACKWARD, ControllerButton.LEFT_STICK_DOWN);
        actionToButton.put(ControllerAction.STRAFE_LEFT, ControllerButton.LEFT_STICK_LEFT);
        actionToButton.put(ControllerAction.STRAFE_RIGHT, ControllerButton.LEFT_STICK_RIGHT);

        // Camera (LOOK_UP/DOWN/LEFT/RIGHT) — handled natively by applyLook() in the tick
        // handler using raw axis values; not routed through the action mapping.

        // Actions
        actionToButton.put(ControllerAction.JUMP, ControllerButton.A);
        actionToButton.put(ControllerAction.SNEAK, ControllerButton.RIGHT_STICK_CLICK);
        actionToButton.put(ControllerAction.SPRINT, ControllerButton.LEFT_STICK_CLICK);
        actionToButton.put(ControllerAction.ATTACK, ControllerButton.RIGHT_TRIGGER);
        actionToButton.put(ControllerAction.USE_ITEM, ControllerButton.LEFT_TRIGGER);
        actionToButton.put(ControllerAction.PICK_BLOCK, ControllerButton.X);

        // Hotbar — bumpers (RB = next slot, LB = previous slot)
        actionToButton.put(ControllerAction.HOTBAR_NEXT, ControllerButton.RIGHT_BUMPER);
        actionToButton.put(ControllerAction.HOTBAR_PREV, ControllerButton.LEFT_BUMPER);

        // UI
        actionToButton.put(ControllerAction.INVENTORY, ControllerButton.Y);
        actionToButton.put(ControllerAction.DROP_ITEM, ControllerButton.B);
        actionToButton.put(ControllerAction.CLOSE_GUI, ControllerButton.B);
        actionToButton.put(ControllerAction.CHAT, ControllerButton.BACK);
        actionToButton.put(ControllerAction.PAUSE, ControllerButton.START);
        // COMMAND intentionally left unbound by default — bind DPAD_DOWN (or any button)
        // via config if desired.

        // GUI interaction — only active when a GuiScreen is open
        // A / Cross = left-click; X / Square = right-click; LT = shift-click
        actionToButton.put(ControllerAction.GUI_LEFT_CLICK, ControllerButton.A);
        actionToButton.put(ControllerAction.GUI_RIGHT_CLICK, ControllerButton.X);
        actionToButton.put(ControllerAction.GUI_SHIFT_CLICK, ControllerButton.LEFT_TRIGGER);

        // D-Pad — all directions unbound by default
        // DPAD_UP, DPAD_DOWN, DPAD_LEFT, DPAD_RIGHT intentionally left unbound

        // D-Pad GUI navigation — only active when Config.dpadNavigation = true and a GUI is open
        actionToButton.put(ControllerAction.GUI_NAV_UP, ControllerButton.DPAD_UP);
        actionToButton.put(ControllerAction.GUI_NAV_DOWN, ControllerButton.DPAD_DOWN);
        actionToButton.put(ControllerAction.GUI_NAV_LEFT, ControllerButton.DPAD_LEFT);
        actionToButton.put(ControllerAction.GUI_NAV_RIGHT, ControllerButton.DPAD_RIGHT);
        actionToButton.put(ControllerAction.GUI_NAV_CONFIRM, ControllerButton.A);
    }

    /** Returns the button bound to the given action, or {@code null} if unbound. */
    public ControllerButton getButton(ControllerAction action) {
        return actionToButton.get(action);
    }

    /** Binds an action to a button, replacing any existing binding for that action. */
    public void bind(ControllerAction action, ControllerButton button) {
        actionToButton.put(action, button);
    }

    /**
     * Returns true if the given action is active in the provided {@link ControllerState}.
     * Handles both digital buttons and axis-driven bindings.
     */
    public boolean isActive(ControllerAction action, ControllerState state, float triggerThreshold) {
        ControllerButton button = actionToButton.get(action);
        if (button == null) return false;

        return switch (button) {
            case LEFT_TRIGGER -> state.leftTrigger() >= triggerThreshold;
            case RIGHT_TRIGGER -> state.rightTrigger() >= triggerThreshold;
            case LEFT_STICK_UP -> state.leftStickY() < -triggerThreshold;
            case LEFT_STICK_DOWN -> state.leftStickY() > triggerThreshold;
            case LEFT_STICK_LEFT -> state.leftStickX() < -triggerThreshold;
            case LEFT_STICK_RIGHT -> state.leftStickX() > triggerThreshold;
            case RIGHT_STICK_UP -> state.rightStickY() < -triggerThreshold;
            case RIGHT_STICK_DOWN -> state.rightStickY() > triggerThreshold;
            case RIGHT_STICK_LEFT -> state.rightStickX() < -triggerThreshold;
            case RIGHT_STICK_RIGHT -> state.rightStickX() > triggerThreshold;
            default -> state.isPressed(button);
        };
    }

    /** Returns an unmodifiable view of all action → button entries. */
    public Map<ControllerAction, ControllerButton> getAllBindings() {
        return Collections.unmodifiableMap(actionToButton);
    }
}
