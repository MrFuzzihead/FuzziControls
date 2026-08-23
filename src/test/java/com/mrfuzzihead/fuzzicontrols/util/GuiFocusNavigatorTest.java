package com.mrfuzzihead.fuzzicontrols.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import net.minecraft.client.gui.GuiButton;

import org.junit.Before;
import org.junit.Test;

import com.mrfuzzihead.fuzzicontrols.util.GuiFocusNavigator.FocusableItem;
import com.mrfuzzihead.fuzzicontrols.util.GuiFocusNavigator.ItemType;

/**
 * Unit tests for {@link GuiFocusNavigator}.
 *
 * <p>
 * These tests verify the focus-tracking logic in isolation using
 * {@link GuiFocusNavigator#rebuildForTest(List)} which bypasses the Minecraft GUI screen
 * system.
 */
public class GuiFocusNavigatorTest {

    private GuiFocusNavigator navigator;

    /** Helper to create a focusable button with given id and enabled/visible state. */
    private static GuiButton btn(int id, int x, int y, int w, int h, boolean enabled, boolean visible) {
        GuiButton b = new GuiButton(id, x, y, w, h, "Button " + id);
        b.enabled = enabled;
        b.visible = visible;
        return b;
    }

    /** Helper to create a fully enabled visible button. */
    private static GuiButton btn(int id, int x, int y) {
        return btn(id, x, y, 100, 20, true, true);
    }

    @Before
    public void setUp() {
        navigator = new GuiFocusNavigator();
    }

    // -------------------------------------------------------------------------
    // Initial state
    // -------------------------------------------------------------------------

    @Test
    public void initialFocusIsNullBeforeRebuild() {
        assertNull("No buttons rebuilt yet — focused button should be null", navigator.getFocusedItem());
        assertNull(navigator.getFocusedButton());
    }

    @Test
    public void initialFocusLandsOnFirstButtonAfterRebuild() {
        List<GuiButton> buttons = Arrays.asList(btn(1, 0, 0), btn(2, 0, 20));
        navigator.rebuildForTest(buttons);

        FocusableItem item = navigator.getFocusedItem();
        assertNotNull("After rebuild, focused item should not be null", item);
        assertEquals(ItemType.BUTTON, item.type);
        assertEquals("Focus should land on the first button (index 0)", buttons.get(0), item.button);
        assertNotNull("getFocusedButton should return the first button", navigator.getFocusedButton());
    }

    // -------------------------------------------------------------------------
    // focusNext / focusPrev wrapping
    // -------------------------------------------------------------------------

    @Test
    public void focusNextAdvancesToNextButton() {
        List<GuiButton> buttons = Arrays.asList(btn(1, 0, 0), btn(2, 0, 20));
        navigator.rebuildForTest(buttons);

        navigator.focusNext();
        assertEquals(ItemType.BUTTON, navigator.getFocusedItem().type);
        assertEquals(buttons.get(1), navigator.getFocusedItem().button);
    }

    @Test
    public void focusNextWrapsFromLastToFirst() {
        List<GuiButton> buttons = Arrays.asList(btn(1, 0, 0), btn(2, 0, 20));
        navigator.rebuildForTest(buttons);

        navigator.focusNext(); // now index 1
        navigator.focusNext(); // wrap back to index 0
        assertEquals(buttons.get(0), navigator.getFocusedItem().button);
    }

    @Test
    public void focusPrevWrapsFromFirstToLast() {
        List<GuiButton> buttons = Arrays.asList(btn(1, 0, 0), btn(2, 0, 20));
        navigator.rebuildForTest(buttons);

        navigator.focusPrev(); // wrap to index 1
        assertEquals(buttons.get(1), navigator.getFocusedItem().button);
    }

    @Test
    public void focusPrevThenNextReturnsToOriginalFocus() {
        List<GuiButton> buttons = Arrays.asList(btn(1, 0, 0), btn(2, 0, 20), btn(3, 0, 40));
        navigator.rebuildForTest(buttons);

        GuiButton original = navigator.getFocusedItem().button;
        navigator.focusNext();
        navigator.focusNext();
        navigator.focusPrev();
        navigator.focusPrev();
        assertEquals(
            "After down-down-up-up, focus should return to original",
            original,
            navigator.getFocusedItem().button);
    }

    // -------------------------------------------------------------------------
    // Empty / null button list
    // -------------------------------------------------------------------------

    @Test
    public void emptyList_getFocusedItemReturnsNull() {
        navigator.rebuildForTest(new ArrayList<>());
        assertNull("Empty button list — focused item should be null", navigator.getFocusedItem());
        assertNull(navigator.getFocusedButton());
    }

    @Test
    public void emptyList_focusNextIsNoOp() {
        navigator.rebuildForTest(new ArrayList<>());
        navigator.focusNext();
        assertNull(navigator.getFocusedItem());
    }

    @Test
    public void emptyList_focusPrevIsNoOp() {
        navigator.rebuildForTest(new ArrayList<>());
        navigator.focusPrev();
        assertNull(navigator.getFocusedItem());
    }

    @Test
    public void nullListIsTolerated() {
        navigator.focusNext();
        navigator.focusPrev();
        assertNull(navigator.getFocusedItem());
    }

    // -------------------------------------------------------------------------
    // Visible / enabled filtering
    // -------------------------------------------------------------------------

    @Test
    public void invisibleButtonsAreExcluded() {
        List<GuiButton> buttons = Arrays.asList(
            btn(1, 0, 0, 100, 20, true, true),
            btn(2, 0, 20, 100, 20, true, false), // invisible
            btn(3, 0, 40, 100, 20, true, true));
        navigator.rebuildForTest(buttons);

        assertEquals(2, navigator.getFocusableCount());
        assertEquals(buttons.get(0), navigator.getFocusedItem().button);
        navigator.focusNext();
        assertEquals(buttons.get(2), navigator.getFocusedItem().button);
    }

    @Test
    public void disabledButtonsAreExcluded() {
        List<GuiButton> buttons = Arrays.asList(
            btn(1, 0, 0, 100, 20, true, true),
            btn(2, 0, 20, 100, 20, false, true), // disabled
            btn(3, 0, 40, 100, 20, true, true));
        navigator.rebuildForTest(buttons);

        assertEquals(2, navigator.getFocusableCount());
        assertEquals(buttons.get(0), navigator.getFocusedItem().button);
        navigator.focusNext();
        assertEquals(buttons.get(2), navigator.getFocusedItem().button);
    }

    // -------------------------------------------------------------------------
    // Slider operations (no-op on non-slider buttons)
    // -------------------------------------------------------------------------

    @Test
    public void sliderLeftOnNonSliderIsNoOp() {
        navigator.rebuildForTest(Arrays.asList(btn(1, 0, 0)));
        navigator.sliderLeft(0.05f);
        navigator.sliderRight(0.05f);
        assertNotNull(navigator.getFocusedItem());
    }

    @Test
    public void sliderLeftRightOnEmptyListAreNoOp() {
        navigator.rebuildForTest(new ArrayList<>());
        navigator.sliderLeft(0.05f);
        navigator.sliderRight(0.05f);
    }

    // -------------------------------------------------------------------------
    // Focusable count / index introspection
    // -------------------------------------------------------------------------

    @Test
    public void getFocusableCountReturnsCorrectCount() {
        navigator.rebuildForTest(Arrays.asList(btn(1, 0, 0), btn(2, 0, 20), btn(3, 0, 40)));
        assertEquals(3, navigator.getFocusableCount());
    }

    @Test
    public void getFocusableCountZeroForEmptyList() {
        navigator.rebuildForTest(new ArrayList<>());
        assertEquals(0, navigator.getFocusableCount());
    }

    @Test
    public void getFocusedIndexStartsAtZero() {
        navigator.rebuildForTest(Arrays.asList(btn(1, 0, 0), btn(2, 0, 20)));
        assertEquals(0, navigator.getFocusedIndex());
    }

    @Test
    public void getFocusedIndexUpdatesWithFocusNext() {
        navigator.rebuildForTest(Arrays.asList(btn(1, 0, 0), btn(2, 0, 20)));
        navigator.focusNext();
        assertEquals(1, navigator.getFocusedIndex());
    }

    @Test
    public void getFocusedIndexWrapsWithFocusNext() {
        navigator.rebuildForTest(Arrays.asList(btn(1, 0, 0), btn(2, 0, 20)));
        navigator.focusNext();
        navigator.focusNext();
        assertEquals(0, navigator.getFocusedIndex());
    }

    @Test
    public void getFocusedIndexWrapsWithFocusPrev() {
        navigator.rebuildForTest(Arrays.asList(btn(1, 0, 0), btn(2, 0, 20)));
        navigator.focusPrev();
        assertEquals(1, navigator.getFocusedIndex());
    }

    // -------------------------------------------------------------------------
    // Rebuild replaces existing items
    // -------------------------------------------------------------------------

    @Test
    public void rebuildReplacesExistingButtons() {
        List<GuiButton> firstBatch = Arrays.asList(btn(1, 0, 0), btn(2, 0, 20));
        navigator.rebuildForTest(firstBatch);
        navigator.focusNext();

        List<GuiButton> secondBatch = Arrays.asList(btn(10, 0, 0));
        navigator.rebuildForTest(secondBatch);
        assertEquals(1, navigator.getFocusableCount());
        assertEquals(secondBatch.get(0), navigator.getFocusedItem().button);
    }

    @Test
    public void rebuildWithEmptyListClearsFocus() {
        navigator.rebuildForTest(Arrays.asList(btn(1, 0, 0), btn(2, 0, 20)));
        assertNotNull(navigator.getFocusedItem());
        navigator.rebuildForTest(new ArrayList<>());
        assertNull(navigator.getFocusedItem());
        assertEquals(0, navigator.getFocusableCount());
    }

    // -------------------------------------------------------------------------
    // FocusableItem type parity
    // -------------------------------------------------------------------------

    @Test
    public void focusedItemIsButtonTypeForButtons() {
        navigator.rebuildForTest(Arrays.asList(btn(1, 0, 0)));
        assertEquals(ItemType.BUTTON, navigator.getFocusedItem().type);
        assertNotNull(navigator.getFocusedButton());
    }

    @Test
    public void getFocusedButtonReturnsNullWhenNoFocus() {
        navigator.rebuildForTest(new ArrayList<>());
        assertNull(navigator.getFocusedButton());
    }
}
