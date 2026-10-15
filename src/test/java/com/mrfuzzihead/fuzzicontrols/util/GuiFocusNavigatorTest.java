package com.mrfuzzihead.fuzzicontrols.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

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

    // -------------------------------------------------------------------------
    // Inventory slot navigation
    // -------------------------------------------------------------------------

    @Test
    public void inventorySlotFocusedItemIsInventorySlotType() {
        navigator.rebuildForTestInventory(new int[] { 44, 62 }, new int[] { 8, 8 }, 0, 0);
        assertEquals(ItemType.INVENTORY_SLOT, navigator.getFocusedItem().type);
        assertNull(navigator.getFocusedButton());
    }

    @Test
    public void inventorySlotFocusStartsAtFirstSlot() {
        navigator.rebuildForTestInventory(new int[] { 44, 62 }, new int[] { 8, 8 }, 0, 0);
        assertEquals(0, navigator.getFocusedIndex());
    }

    @Test
    public void inventorySlotEmptyListIsNoOp() {
        navigator.rebuildForTestInventory(new int[] {}, new int[] {}, 0, 0);
        assertNull(navigator.getFocusedItem());
        navigator.focusNext();
        assertNull(navigator.getFocusedItem());
        navigator.focusPrev();
        assertNull(navigator.getFocusedItem());
    }

    @Test
    public void inventorySlotNullListsAreNoOp() {
        navigator.rebuildForTestInventory(null, null, 0, 0);
        assertNull(navigator.getFocusedItem());
    }

    @Test
    public void inventorySlotNavigatesHorizontally() {
        // 3 slots in a row: (44,8), (62,8), (80,8) — guiLeft=0, guiTop=0
        navigator.rebuildForTestInventory(new int[] { 44, 62, 80 }, new int[] { 8, 8, 8 }, 0, 0);
        assertEquals(0, navigator.getFocusedIndex());

        navigator.focusRight();
        assertEquals(1, navigator.getFocusedIndex());

        navigator.focusRight();
        assertEquals(2, navigator.getFocusedIndex());

        // Wrap: right from last should go to first
        navigator.focusRight();
        assertEquals(0, navigator.getFocusedIndex());
    }

    @Test
    public void inventorySlotNavigatesVertically() {
        // 3 slots in a column: (44,8), (44,26), (44,44) — guiLeft=0, guiTop=0
        navigator.rebuildForTestInventory(new int[] { 44, 44, 44 }, new int[] { 8, 26, 44 }, 0, 0);
        assertEquals(0, navigator.getFocusedIndex());

        navigator.focusDown();
        assertEquals(1, navigator.getFocusedIndex());

        navigator.focusDown();
        assertEquals(2, navigator.getFocusedIndex());

        // Wrap: down from last should go to first
        navigator.focusDown();
        assertEquals(0, navigator.getFocusedIndex());
    }

    @Test
    public void inventorySlotFocusWrapsUpFromFirst() {
        navigator.rebuildForTestInventory(new int[] { 44, 44 }, new int[] { 8, 26 }, 0, 0);
        navigator.focusUp(); // wraps to index 1
        assertEquals(1, navigator.getFocusedIndex());
    }

    @Test
    public void inventorySlotFocusMovesInGrid() {
        // 2x2 grid: slots at (8,8), (26,8), (8,26), (26,26) — guiLeft=0, guiTop=0
        navigator.rebuildForTestInventory(new int[] { 8, 26, 8, 26 }, new int[] { 8, 8, 26, 26 }, 0, 0);
        assertEquals(0, navigator.getFocusedIndex());

        // Right from (8,8) → (26,8)
        navigator.focusRight();
        assertEquals(1, navigator.getFocusedIndex());

        // Down from (26,8) → (26,26)
        navigator.focusDown();
        assertEquals(3, navigator.getFocusedIndex());

        // Left from (26,26) → (8,26)
        navigator.focusLeft();
        assertEquals(2, navigator.getFocusedIndex());

        // Up from (8,26) → (8,8)
        navigator.focusUp();
        assertEquals(0, navigator.getFocusedIndex());
    }

    @Test
    public void inventorySlotGuiOffsetAffectsPosition() {
        // Same slot positions with different guiLeft — should still navigate correctly
        // because spatial navigation uses relative positions preserved by the same offset.
        navigator.rebuildForTestInventory(new int[] { 44, 62 }, new int[] { 8, 8 }, 100, 50);
        assertEquals(0, navigator.getFocusedIndex());
        navigator.focusRight();
        assertEquals(1, navigator.getFocusedIndex());
        navigator.focusLeft();
        assertEquals(0, navigator.getFocusedIndex());
    }

    @Test
    public void inventorySlotGetFocusedItemCenterReturnsNullForEmpty() {
        navigator.rebuildForTestInventory(new int[] {}, new int[] {}, 0, 0);
        assertNull(navigator.getFocusedItemCenter());
    }

    // -------------------------------------------------------------------------
    // Layout invalidation (window resize / fullscreen toggle / GUI scale change)
    // -------------------------------------------------------------------------
    //
    // A window resize, fullscreen toggle or GUI scale change re-runs initGui() on the *same*
    // screen object, so the screen-change check does not fire and the button list (and its hash)
    // is identical. Before the fix the cached guiLeft/guiTop and GuiSlot references were reused,
    // leaving the focus highlight and the synthetic slot clicks pointing at the pre-resize layout.
    //
    // These tests drive the pure change-detection logic directly, plus the rebuild wiring through
    // update(). update() is called with a null screen: instantiating a GuiScreen in the unit test
    // JVM is impossible (GuiScreen's static initialiser needs a live Minecraft), and with no
    // container/GuiSlot involved the detection path never dereferences the screen.

    @Test
    public void layoutChange_detectedFromGeneration() {
        assertTrue(
            "A screen re-initialisation must invalidate cached layout",
            GuiFocusNavigator.layoutChanged(2, 1, -1, -1, -1, -1));
        assertFalse(GuiFocusNavigator.layoutChanged(1, 1, -1, -1, -1, -1));
        assertFalse(
            "First build captures the current generation",
            GuiFocusNavigator.layoutChanged(0, 0, -1, -1, -1, -1));
    }

    @Test
    public void layoutChange_detectedFromSlotCounts() {
        assertTrue(
            "A container gaining/losing slots must invalidate cached layout",
            GuiFocusNavigator.layoutChanged(1, 1, 10, 45, -1, -1));
        assertTrue(
            "A GuiSlot list gaining/losing entries must invalidate cached layout",
            GuiFocusNavigator.layoutChanged(1, 1, -1, -1, 12, 11));
        assertFalse(GuiFocusNavigator.layoutChanged(1, 1, 45, 45, 12, 12));
    }

    @Test
    public void update_rebuildsWhenScreenIsReinitialised() {
        navigator.update(null);
        int afterFirstBuild = navigator.getRebuildCountForTest();
        assertEquals("First update builds the list", 1, afterFirstBuild);

        navigator.update(null);
        assertEquals("Unchanged layout must not rebuild", afterFirstBuild, navigator.getRebuildCountForTest());

        // setWorldAndResolution ran again (resize / fullscreen / GUI scale).
        ScreenLayoutTracker.bumpGeneration();
        navigator.update(null);
        assertEquals(
            "A re-initialised screen must rebuild its focus list",
            afterFirstBuild + 1,
            navigator.getRebuildCountForTest());
    }

    @Test
    public void clampIndex_preservesValidFocusAcrossResize() {
        // Focus survives a rebuild when the item still exists (resize of the same screen).
        assertEquals(2, GuiFocusNavigator.clampIndex(2, 3));
        assertEquals(0, GuiFocusNavigator.clampIndex(0, 3));
        assertEquals(2, GuiFocusNavigator.clampIndex(2, 3));
    }

    @Test
    public void clampIndex_clampsWhenListShrinks() {
        // A smaller GUI scale can leave fewer focusable items than the cached index.
        assertEquals(0, GuiFocusNavigator.clampIndex(2, 1));
        assertEquals(0, GuiFocusNavigator.clampIndex(5, 0));
        assertEquals(0, GuiFocusNavigator.clampIndex(-1, 3));
    }

    @Test
    public void layoutRebuildIsIdempotent() {
        // Two consecutive updates with no layout or button change rebuild at most once.
        navigator.update(null);
        int afterFirstBuild = navigator.getRebuildCountForTest();
        navigator.update(null);
        navigator.update(null);
        assertEquals(afterFirstBuild, navigator.getRebuildCountForTest());
    }
}
