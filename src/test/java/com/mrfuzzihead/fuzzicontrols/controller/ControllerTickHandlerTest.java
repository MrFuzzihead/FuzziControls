package com.mrfuzzihead.fuzzicontrols.controller;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Collections;
import java.util.EnumSet;

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
}
