package com.mrfuzzihead.fuzzicontrols.controller;

/**
 * Every Minecraft action that the controller can trigger, matching the
 * "Controller" section of <a href="https://minecraft.fandom.com/wiki/Controls">...</a>.
 */
public enum ControllerAction {

    // ---- Movement ----
    /** Move forward (left stick up). */
    MOVE_FORWARD,
    /** Move backward (left stick down). */
    MOVE_BACKWARD,
    /** Strafe left (left stick left). */
    STRAFE_LEFT,
    /** Strafe right (left stick right). */
    STRAFE_RIGHT,

    // ---- Camera ----
    /** Look up (right stick up). */
    LOOK_UP,
    /** Look down (right stick down). */
    LOOK_DOWN,
    /** Look left (right stick left). */
    LOOK_LEFT,
    /** Look right (right stick right). */
    LOOK_RIGHT,

    // ---- Actions ----
    /** Jump — A / Cross. */
    JUMP,
    /** Sneak / crouch — Right stick click (RS / R3). */
    SNEAK,
    /** Toggle sprint — Left stick click (LS / L3). */
    SPRINT,

    /** Attack / mine (hold) — Right trigger (RT / R2). */
    ATTACK,
    /** Use item / place block (hold) — Left trigger (LT / L2). */
    USE_ITEM,

    /** Pick block (middle click) — Right bumper (RB / R1). */
    PICK_BLOCK,

    // ---- Hotbar ----
    /** Select next hotbar slot — D-pad right. */
    HOTBAR_NEXT,
    /** Select previous hotbar slot — D-pad left. */
    HOTBAR_PREV,

    // ---- UI ----
    /** Open/close inventory — Y / Triangle. */
    INVENTORY,
    /** Drop held item (tap) or drop stack (hold) — B / Circle. */
    DROP_ITEM,
    /** Close the currently open GUI (inventory, chat, pause menu, etc.) — B / Circle. */
    CLOSE_GUI,
    /** Open chat — Back / Select. */
    CHAT,
    /** Pause / open menu — Start / Options. */
    PAUSE,

    // ---- Commands / extras ----
    /** Open commands (similar to chat but prefixed with /) — Left bumper (LB / L1). */
    COMMAND,

    // ---- GUI interaction (only active when a GuiScreen is open) ----
    /**
     * Left-click the current GUI cursor position — A (Xbox) / Cross (PS).
     * Only fires when a GUI screen is open.
     */
    GUI_LEFT_CLICK,
    /**
     * Right-click the current GUI cursor position — X (Xbox) / Square (PS).
     * Only fires when a GUI screen is open.
     */
    GUI_RIGHT_CLICK,

    // ---- D-Pad (all unbound by default, user-configurable) ----
    DPAD_UP,
    DPAD_DOWN,
    DPAD_LEFT,
    DPAD_RIGHT
}
