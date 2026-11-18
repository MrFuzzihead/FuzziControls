package com.mrfuzzihead.fuzzicontrols.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

import org.junit.Test;

/**
 * Regression tests for the in-world edge state that must be kept current while a GUI is open.
 *
 * <p>
 * The in-world path is skipped while {@code mc.currentScreen != null}. If its {@code wasActive}
 * entries are not advanced there, a button pressed or released inside the screen leaves a stale
 * edge: the first press after closing is swallowed, or a held button fires an unwanted action.
 * {@link ControllerTickHandler#syncInWorldEdges} is the fix; these tests exercise the pure part
 * without a live Minecraft client.
 * </p>
 */
public class ControllerTickHandlerTest {

    @Test
    public void oneShotEdgeFiresOnlyOnTheRisingEdge() {
        ControllerTickHandler handler = new ControllerTickHandler();
        ControllerMapping mapping = new ControllerMapping(); // JUMP is bound to A
        ControllerState pressed = new ControllerState(0f, 0f, 0f, 0f, 0f, 0f, EnumSet.of(ControllerButton.A));
        int[] runs = { 0 };

        handler.handleEdge(ControllerAction.JUMP, pressed, mapping, () -> runs[0]++);
        handler.handleEdge(ControllerAction.JUMP, pressed, mapping, () -> runs[0]++);
        handler.handleEdge(ControllerAction.JUMP, ControllerState.empty(), mapping, () -> runs[0]++);
        handler.handleEdge(ControllerAction.JUMP, pressed, mapping, () -> runs[0]++);

        assertEquals("One effect per physical press, not per held tick", 2, runs[0]);
    }

    @Test
    public void inWorldEdgesTrackStateWhileAGuiIsOpen() {
        ControllerTickHandler handler = new ControllerTickHandler();
        ControllerMapping mapping = new ControllerMapping();

        // RT is the default ATTACK binding.
        ControllerState triggerHeld = new ControllerState(0f, 0f, 0f, 0f, 0f, 1f, Collections.emptySet());
        handler.syncInWorldEdges(triggerHeld, mapping);
        assertTrue("ATTACK must be consumed while the GUI is open", handler.wasActiveForTest(ControllerAction.ATTACK));

        // X is the default PICK_BLOCK binding.
        ControllerState xHeld = new ControllerState(0f, 0f, 0f, 0f, 0f, 0f, EnumSet.of(ControllerButton.X));
        handler.syncInWorldEdges(xHeld, mapping);
        assertFalse("ATTACK must be released", handler.wasActiveForTest(ControllerAction.ATTACK));
        assertTrue(
            "PICK_BLOCK must be consumed while the GUI is open",
            handler.wasActiveForTest(ControllerAction.PICK_BLOCK));

        handler.syncInWorldEdges(ControllerState.empty(), mapping);
        assertFalse("PICK_BLOCK must be released", handler.wasActiveForTest(ControllerAction.PICK_BLOCK));
    }

    @Test
    public void onlyTheDropBindingCanDropWhenCloseGuiUsesAnotherButton() {
        ControllerTickHandler handler = new ControllerTickHandler();
        ControllerMapping mapping = new ControllerMapping();
        mapping.bind(ControllerAction.DROP_ITEM, ControllerButton.B);
        mapping.bind(ControllerAction.CLOSE_GUI, ControllerButton.A);

        assertFalse(
            "CLOSE_GUI must not drop an item when it is rebound to another button",
            handler.isDropActive(pressed(ControllerButton.A), mapping));
        assertTrue("DROP_ITEM must still drop", handler.isDropActive(pressed(ControllerButton.B), mapping));
    }

    @Test
    public void dropFollowsItsOwnBindingWhenTheButtonsAreSwapped() {
        ControllerTickHandler handler = new ControllerTickHandler();
        ControllerMapping mapping = new ControllerMapping();
        mapping.bind(ControllerAction.DROP_ITEM, ControllerButton.A);
        mapping.bind(ControllerAction.CLOSE_GUI, ControllerButton.B);

        assertTrue(
            "DROP_ITEM must follow its rebound button",
            handler.isDropActive(pressed(ControllerButton.A), mapping));
        assertFalse(
            "CLOSE_GUI must still not drop after the swap",
            handler.isDropActive(pressed(ControllerButton.B), mapping));
    }

    @Test
    public void controllerDisconnectDoesNotReleaseKeyboardHeldKeys() {
        KeyClaimTracker held = new KeyClaimTracker();
        held.update(17, true); // W: physically held on the keyboard
        held.update(57, true); // space: only held by the controller

        List<Integer> released = new ArrayList<>();
        ControllerTickHandler.releaseUnheldControllerKeys(held, code -> code == 17, released::add);

        assertEquals(Collections.singletonList(57), released);
    }

    @Test
    public void mouseButtonCodesAreDriveable() {
        assertTrue("attack is a negative mouse-button code", ControllerTickHandler.isDriveableKeyCode(-100));
        assertTrue("use item is a negative mouse-button code", ControllerTickHandler.isDriveableKeyCode(-99));
        assertTrue(ControllerTickHandler.isDriveableKeyCode(17));
        assertFalse("0 means no binding", ControllerTickHandler.isDriveableKeyCode(0));
    }

    private static ControllerState pressed(ControllerButton button) {
        return new ControllerState(0f, 0f, 0f, 0f, 0f, 0f, EnumSet.of(button));
    }
}
