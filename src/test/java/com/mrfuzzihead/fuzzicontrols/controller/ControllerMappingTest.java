package com.mrfuzzihead.fuzzicontrols.controller;

import static org.junit.Assert.*;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import org.junit.Before;
import org.junit.Test;

/**
 * Unit tests for {@link ControllerMapping} default bindings, rebinding, and isActive logic.
 */
public class ControllerMappingTest {

    /**
     * Actions intentionally left unbound in the default mapping:
     * <ul>
     * <li>LOOK_* — camera is driven natively by raw axis values in applyLook(), not via the
     * mapping.</li>
     * <li>DPAD_UP/DOWN/LEFT/RIGHT — all D-pad directions are unbound by default.</li>
     * <li>COMMAND — unbound by default; configurable via {@code COMMAND=DPAD_DOWN} in
     * config.</li>
     * </ul>
     */
    private static final Set<ControllerAction> INTENTIONALLY_UNBOUND = EnumSet.of(
        ControllerAction.LOOK_UP,
        ControllerAction.LOOK_DOWN,
        ControllerAction.LOOK_LEFT,
        ControllerAction.LOOK_RIGHT,
        ControllerAction.DPAD_UP,
        ControllerAction.DPAD_DOWN,
        ControllerAction.DPAD_LEFT,
        ControllerAction.DPAD_RIGHT,
        ControllerAction.COMMAND);

    private ControllerMapping mapping;

    @Before
    public void setUp() {
        mapping = new ControllerMapping();
    }

    // -------------------------------------------------------------------------
    // Default bindings coverage
    // -------------------------------------------------------------------------

    @Test
    public void allBoundActionsHaveDefaultBinding() {
        for (ControllerAction action : ControllerAction.values()) {
            if (INTENTIONALLY_UNBOUND.contains(action)) continue;
            assertNotNull("Action " + action + " has no default binding", mapping.getButton(action));
        }
    }

    @Test
    public void intentionallyUnboundActionsAreNull() {
        for (ControllerAction action : INTENTIONALLY_UNBOUND) {
            assertNull(
                "Action " + action + " should be unbound by default but has: " + mapping.getButton(action),
                mapping.getButton(action));
        }
    }

    @Test
    public void defaultBinding_jump_isA() {
        assertEquals(ControllerButton.A, mapping.getButton(ControllerAction.JUMP));
    }

    @Test
    public void defaultBinding_sneak_isRightStickClick() {
        assertEquals(ControllerButton.RIGHT_STICK_CLICK, mapping.getButton(ControllerAction.SNEAK));
    }

    @Test
    public void defaultBinding_sprint_isLeftStickClick() {
        assertEquals(ControllerButton.LEFT_STICK_CLICK, mapping.getButton(ControllerAction.SPRINT));
    }

    @Test
    public void defaultBinding_attack_isRightTrigger() {
        assertEquals(ControllerButton.RIGHT_TRIGGER, mapping.getButton(ControllerAction.ATTACK));
    }

    @Test
    public void defaultBinding_useItem_isLeftTrigger() {
        assertEquals(ControllerButton.LEFT_TRIGGER, mapping.getButton(ControllerAction.USE_ITEM));
    }

    @Test
    public void defaultBinding_inventory_isY() {
        assertEquals(ControllerButton.Y, mapping.getButton(ControllerAction.INVENTORY));
    }

    @Test
    public void defaultBinding_drop_isB() {
        assertEquals(ControllerButton.B, mapping.getButton(ControllerAction.DROP_ITEM));
    }

    @Test
    public void defaultBinding_chat_isBack() {
        assertEquals(ControllerButton.BACK, mapping.getButton(ControllerAction.CHAT));
    }

    @Test
    public void defaultBinding_pause_isStart() {
        assertEquals(ControllerButton.START, mapping.getButton(ControllerAction.PAUSE));
    }

    @Test
    public void defaultBinding_hotbarNext_isRightBumper() {
        assertEquals(ControllerButton.RIGHT_BUMPER, mapping.getButton(ControllerAction.HOTBAR_NEXT));
    }

    @Test
    public void defaultBinding_hotbarPrev_isLeftBumper() {
        assertEquals(ControllerButton.LEFT_BUMPER, mapping.getButton(ControllerAction.HOTBAR_PREV));
    }

    @Test
    public void defaultBinding_pickBlock_isX() {
        assertEquals(ControllerButton.X, mapping.getButton(ControllerAction.PICK_BLOCK));
    }

    @Test
    public void defaultBinding_command_isUnbound() {
        assertNull(
            "COMMAND should be unbound by default (configurable via COMMAND=DPAD_DOWN in config)",
            mapping.getButton(ControllerAction.COMMAND));
    }

    @Test
    public void defaultBinding_closeGui_isB() {
        assertEquals(ControllerButton.B, mapping.getButton(ControllerAction.CLOSE_GUI));
    }

    @Test
    public void defaultBinding_guiLeftClick_isA() {
        assertEquals(ControllerButton.A, mapping.getButton(ControllerAction.GUI_LEFT_CLICK));
    }

    @Test
    public void defaultBinding_guiRightClick_isX() {
        assertEquals(ControllerButton.X, mapping.getButton(ControllerAction.GUI_RIGHT_CLICK));
    }

    @Test
    public void defaultBinding_guiShiftClick_isLeftTrigger() {
        assertEquals(ControllerButton.LEFT_TRIGGER, mapping.getButton(ControllerAction.GUI_SHIFT_CLICK));
    }

    /**
     * GUI_SHIFT_CLICK shares LEFT_TRIGGER with USE_ITEM in-world. The tick handler applies the
     * correct action based on whether a GUI screen is open.
     */
    @Test
    public void guiShiftClick_sharesSameButtonAsUseItem() {
        assertEquals(
            "GUI_SHIFT_CLICK and USE_ITEM must share LEFT_TRIGGER so the tick handler can use context",
            mapping.getButton(ControllerAction.USE_ITEM),
            mapping.getButton(ControllerAction.GUI_SHIFT_CLICK));
    }

    @Test
    public void isActive_guiShiftClick_activeWhenLeftTriggerPressed() {
        ControllerState state = new ControllerState(0, 0, 0, 0, 0.8f, 0, Collections.emptySet());
        assertTrue(mapping.isActive(ControllerAction.GUI_SHIFT_CLICK, state, 0.2f));
    }

    @Test
    public void isActive_guiShiftClick_inactiveWhenLeftTriggerBelowThreshold() {
        ControllerState state = new ControllerState(0, 0, 0, 0, 0.1f, 0, Collections.emptySet());
        assertFalse(mapping.isActive(ControllerAction.GUI_SHIFT_CLICK, state, 0.2f));
    }

    /**
     * GUI_LEFT_CLICK shares button A with JUMP. Both should be bound to the same button. The tick
     * handler decides which action to apply based on whether a screen is open.
     */
    @Test
    public void guiLeftClick_sharesSameButtonAsJump() {
        assertEquals(
            "GUI_LEFT_CLICK and JUMP must share button A so the tick handler can use context to distinguish them",
            mapping.getButton(ControllerAction.JUMP),
            mapping.getButton(ControllerAction.GUI_LEFT_CLICK));
    }

    /**
     * GUI_RIGHT_CLICK shares button X with PICK_BLOCK. Both should be bound to the same button.
     */
    @Test
    public void guiRightClick_sharesSameButtonAsPickBlock() {
        assertEquals(
            "GUI_RIGHT_CLICK and PICK_BLOCK must share button X so the tick handler can use context to distinguish them",
            mapping.getButton(ControllerAction.PICK_BLOCK),
            mapping.getButton(ControllerAction.GUI_RIGHT_CLICK));
    }

    // ---- Movement / camera (axis-mapped) ----

    @Test
    public void defaultBinding_moveForward_isLeftStickUp() {
        assertEquals(ControllerButton.LEFT_STICK_UP, mapping.getButton(ControllerAction.MOVE_FORWARD));
    }

    @Test
    public void defaultBinding_moveBackward_isLeftStickDown() {
        assertEquals(ControllerButton.LEFT_STICK_DOWN, mapping.getButton(ControllerAction.MOVE_BACKWARD));
    }

    @Test
    public void defaultBinding_strafeLeft_isLeftStickLeft() {
        assertEquals(ControllerButton.LEFT_STICK_LEFT, mapping.getButton(ControllerAction.STRAFE_LEFT));
    }

    @Test
    public void defaultBinding_strafeRight_isLeftStickRight() {
        assertEquals(ControllerButton.LEFT_STICK_RIGHT, mapping.getButton(ControllerAction.STRAFE_RIGHT));
    }

    // -------------------------------------------------------------------------
    // Rebinding
    // -------------------------------------------------------------------------

    @Test
    public void bind_replacesExistingBinding() {
        mapping.bind(ControllerAction.JUMP, ControllerButton.X);
        assertEquals(ControllerButton.X, mapping.getButton(ControllerAction.JUMP));
    }

    @Test
    public void bind_toNull_unbindsAction() {
        mapping.bind(ControllerAction.JUMP, null);
        assertNull("Binding JUMP to null should leave it unbound", mapping.getButton(ControllerAction.JUMP));
    }

    @Test
    public void applyDefaults_resetsReboundAction() {
        mapping.bind(ControllerAction.JUMP, ControllerButton.X);
        mapping.applyDefaults();
        assertEquals(ControllerButton.A, mapping.getButton(ControllerAction.JUMP));
    }

    @Test
    public void applyDefaults_resetsUnboundActionBackToDefault() {
        mapping.bind(ControllerAction.JUMP, null);
        mapping.applyDefaults();
        assertEquals(ControllerButton.A, mapping.getButton(ControllerAction.JUMP));
    }

    @Test
    public void getAllBindings_isUnmodifiable() {
        Map<ControllerAction, ControllerButton> bindings = mapping.getAllBindings();
        try {
            bindings.put(ControllerAction.JUMP, ControllerButton.GUIDE);
            fail("Expected UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) {
            // pass
        }
    }

    // -------------------------------------------------------------------------
    // isActive — digital button
    // -------------------------------------------------------------------------

    @Test
    public void isActive_digitalButton_pressed_returnsTrue() {
        ControllerState state = new ControllerState(0, 0, 0, 0, 0, 0, EnumSet.of(ControllerButton.A));
        assertTrue(mapping.isActive(ControllerAction.JUMP, state, 0.2f));
    }

    @Test
    public void isActive_digitalButton_notPressed_returnsFalse() {
        ControllerState state = ControllerState.empty();
        assertFalse(mapping.isActive(ControllerAction.JUMP, state, 0.2f));
    }

    @Test
    public void isActive_unboundAction_returnsFalse() {
        // COMMAND is unbound by default — should always return false regardless of state
        ControllerState state = new ControllerState(0, 0, 0, 0, 0, 0, EnumSet.allOf(ControllerButton.class));
        assertFalse(
            "Unbound action should return false even when all buttons are pressed",
            mapping.isActive(ControllerAction.COMMAND, state, 0.2f));
    }

    @Test
    public void isActive_guiLeftClick_activeWhenAPressed() {
        ControllerState state = new ControllerState(0, 0, 0, 0, 0, 0, EnumSet.of(ControllerButton.A));
        assertTrue(mapping.isActive(ControllerAction.GUI_LEFT_CLICK, state, 0.2f));
    }

    @Test
    public void isActive_guiRightClick_activeWhenXPressed() {
        ControllerState state = new ControllerState(0, 0, 0, 0, 0, 0, EnumSet.of(ControllerButton.X));
        assertTrue(mapping.isActive(ControllerAction.GUI_RIGHT_CLICK, state, 0.2f));
    }

    @Test
    public void isActive_guiLeftClick_inactiveWhenANotPressed() {
        ControllerState state = ControllerState.empty();
        assertFalse(mapping.isActive(ControllerAction.GUI_LEFT_CLICK, state, 0.2f));
    }

    // -------------------------------------------------------------------------
    // isActive — axis-driven actions
    // -------------------------------------------------------------------------

    @Test
    public void isActive_leftStickUp_positiveY_isForward() {
        // leftStickY < -threshold means UP/forward
        ControllerState state = new ControllerState(0, -0.5f, 0, 0, 0, 0, Collections.emptySet());
        assertTrue(mapping.isActive(ControllerAction.MOVE_FORWARD, state, 0.2f));
    }

    @Test
    public void isActive_leftStickDown_negativeY_isBackward() {
        ControllerState state = new ControllerState(0, 0.5f, 0, 0, 0, 0, Collections.emptySet());
        assertTrue(mapping.isActive(ControllerAction.MOVE_BACKWARD, state, 0.2f));
    }

    @Test
    public void isActive_leftStickLeft_negativX_isStrafeLeft() {
        // leftStickX < -threshold means strafe left
        ControllerState state = new ControllerState(-0.5f, 0, 0, 0, 0, 0, Collections.emptySet());
        assertTrue(mapping.isActive(ControllerAction.STRAFE_LEFT, state, 0.2f));
    }

    @Test
    public void isActive_leftStickRight_positiveX_isStrafeRight() {
        ControllerState state = new ControllerState(0.5f, 0, 0, 0, 0, 0, Collections.emptySet());
        assertTrue(mapping.isActive(ControllerAction.STRAFE_RIGHT, state, 0.2f));
    }

    @Test
    public void isActive_leftStickNeutral_noMovement() {
        ControllerState state = new ControllerState(0, 0, 0, 0, 0, 0, Collections.emptySet());
        assertFalse(mapping.isActive(ControllerAction.MOVE_FORWARD, state, 0.2f));
        assertFalse(mapping.isActive(ControllerAction.MOVE_BACKWARD, state, 0.2f));
        assertFalse(mapping.isActive(ControllerAction.STRAFE_LEFT, state, 0.2f));
        assertFalse(mapping.isActive(ControllerAction.STRAFE_RIGHT, state, 0.2f));
    }

    @Test
    public void isActive_rightStickUp_negativY_isLookUp() {
        // rightStickY < -threshold when bound to LOOK_UP
        mapping.bind(ControllerAction.LOOK_UP, ControllerButton.RIGHT_STICK_UP);
        ControllerState state = new ControllerState(0, 0, 0, -0.5f, 0, 0, Collections.emptySet());
        assertTrue(mapping.isActive(ControllerAction.LOOK_UP, state, 0.2f));
    }

    @Test
    public void isActive_rightStickDown_positivY_isLookDown() {
        mapping.bind(ControllerAction.LOOK_DOWN, ControllerButton.RIGHT_STICK_DOWN);
        ControllerState state = new ControllerState(0, 0, 0, 0.5f, 0, 0, Collections.emptySet());
        assertTrue(mapping.isActive(ControllerAction.LOOK_DOWN, state, 0.2f));
    }

    @Test
    public void isActive_rightStickLeft_negativX_isLookLeft() {
        mapping.bind(ControllerAction.LOOK_LEFT, ControllerButton.RIGHT_STICK_LEFT);
        ControllerState state = new ControllerState(0, 0, -0.5f, 0, 0, 0, Collections.emptySet());
        assertTrue(mapping.isActive(ControllerAction.LOOK_LEFT, state, 0.2f));
    }

    @Test
    public void isActive_rightStickRight_positivX_isLookRight() {
        mapping.bind(ControllerAction.LOOK_RIGHT, ControllerButton.RIGHT_STICK_RIGHT);
        ControllerState state = new ControllerState(0, 0, 0.5f, 0, 0, 0, Collections.emptySet());
        assertTrue(mapping.isActive(ControllerAction.LOOK_RIGHT, state, 0.2f));
    }

    @Test
    public void isActive_rightTrigger_aboveThreshold_isAttack() {
        ControllerState state = new ControllerState(0, 0, 0, 0, 0, 0.8f, Collections.emptySet());
        assertTrue(mapping.isActive(ControllerAction.ATTACK, state, 0.2f));
    }

    @Test
    public void isActive_rightTrigger_belowThreshold_notAttack() {
        ControllerState state = new ControllerState(0, 0, 0, 0, 0, 0.1f, Collections.emptySet());
        assertFalse(mapping.isActive(ControllerAction.ATTACK, state, 0.2f));
    }

    @Test
    public void isActive_rightTrigger_exactlyAtThreshold_isAttack() {
        // Triggers use >= so exactly at the threshold value IS considered active
        ControllerState state = new ControllerState(0, 0, 0, 0, 0, 0.2f, Collections.emptySet());
        assertTrue(
            "Trigger exactly at threshold should register as active (uses >=)",
            mapping.isActive(ControllerAction.ATTACK, state, 0.2f));
    }

    @Test
    public void isActive_leftTrigger_aboveThreshold_isUseItem() {
        ControllerState state = new ControllerState(0, 0, 0, 0, 0.8f, 0, Collections.emptySet());
        assertTrue(mapping.isActive(ControllerAction.USE_ITEM, state, 0.2f));
    }

    @Test
    public void isActive_leftTrigger_belowThreshold_notUseItem() {
        ControllerState state = new ControllerState(0, 0, 0, 0, 0.1f, 0, Collections.emptySet());
        assertFalse(mapping.isActive(ControllerAction.USE_ITEM, state, 0.2f));
    }

    // -------------------------------------------------------------------------
    // isActive — axis direction exclusivity (opposing directions cancel out)
    // -------------------------------------------------------------------------

    @Test
    public void isActive_leftStickForward_doesNotActivateBackward() {
        ControllerState state = new ControllerState(0, -0.8f, 0, 0, 0, 0, Collections.emptySet());
        assertTrue(mapping.isActive(ControllerAction.MOVE_FORWARD, state, 0.2f));
        assertFalse(mapping.isActive(ControllerAction.MOVE_BACKWARD, state, 0.2f));
    }

    @Test
    public void isActive_leftStickRight_doesNotActivateStrafeLeft() {
        ControllerState state = new ControllerState(0.8f, 0, 0, 0, 0, 0, Collections.emptySet());
        assertTrue(mapping.isActive(ControllerAction.STRAFE_RIGHT, state, 0.2f));
        assertFalse(mapping.isActive(ControllerAction.STRAFE_LEFT, state, 0.2f));
    }
}
