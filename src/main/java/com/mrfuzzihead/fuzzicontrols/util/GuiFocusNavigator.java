package com.mrfuzzihead.fuzzicontrols.util;

import java.awt.Point;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiOptionSlider;
import net.minecraft.client.gui.GuiOptionsRowList;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiSlot;

import com.mrfuzzihead.fuzzicontrols.interfaces.SlotAwareScreen;
import com.mrfuzzihead.fuzzicontrols.mixins.early.GuiOptionSliderAccessors;
import com.mrfuzzihead.fuzzicontrols.mixins.early.GuiOptionsRowListRowAccessors;
import com.mrfuzzihead.fuzzicontrols.mixins.early.GuiScreenAccessors;
import com.mrfuzzihead.fuzzicontrols.mixins.early.GuiSlotAccessors;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 2D spatial focus navigation for GUI screens.
 *
 * <p>
 * Tracks focus across a combined list of slot entries (from {@link GuiSlot} lists like
 * the world/server selector) and real buttons from the screen's {@code buttonList}.
 * Movement uses <em>nearest-neighbor</em> search in the pressed D-pad direction,
 * based on each element's screen-space center coordinates. This mirrors console Minecraft's
 * behavior: pressing up goes to the closest item above, pressing right goes to the
 * closest item to the right, etc., regardless of the order items were added.
 *
 * <p>
 * When no element exists in the pressed direction, focus wraps to the far side
 * (e.g., pressing right on the rightmost item goes to the leftmost item with a
 * similar Y coordinate).
 */
@SideOnly(Side.CLIENT)
public final class GuiFocusNavigator {

    /** Distinguishes a real GUI button from a virtual slot entry in the focusable list. */
    public enum ItemType {
        BUTTON,
        SLOT_ENTRY
    }

    /** A focusable item: either a real {@link GuiButton} or a virtual slot entry. */
    public static final class FocusableItem {

        public final ItemType type;
        /** The real button (non-null for {@link ItemType#BUTTON}). */
        public final GuiButton button;
        /** The slot that owns this entry (non-null for {@link ItemType#SLOT_ENTRY}). */
        public final GuiSlot slot;
        /** The entry index within the slot (valid for {@link ItemType#SLOT_ENTRY}). */
        public final int slotIndex;

        FocusableItem(GuiButton button) {
            this.type = ItemType.BUTTON;
            this.button = button;
            this.slot = null;
            this.slotIndex = -1;
        }

        FocusableItem(GuiSlot slot, int slotIndex) {
            this.type = ItemType.SLOT_ENTRY;
            this.button = null;
            this.slot = slot;
            this.slotIndex = slotIndex;
        }
    }

    /** The screen whose button list we last inspected. Used to detect screen changes. */
    private GuiScreen currentScreen;

    /**
     * Ordered list of all focusable items: slot entries first (if any), then buttons.
     * Built by {@link #rebuildFocusableList(GuiScreen)}.
     */
    private List<FocusableItem> focusableItems = Collections.emptyList();

    /** Index into {@link #focusableItems} of the currently focused element. */
    private int focusedIndex = 0;

    /**
     * Cached button state hash. Used to detect state changes within the same screen
     * (e.g. buttons enabled after a slot click).
     */
    private int lastButtonStateHash = 0;

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Call every game tick while a GUI is open. Rebuilds the focusable list if the
     * screen has changed, or refreshes button visibility if buttons changed on the
     * same screen.
     *
     * @param screen the currently active {@link GuiScreen}, never null when called
     */
    public void update(GuiScreen screen) {
        if (screen != currentScreen) {
            currentScreen = screen;
            rebuildFocusableList(screen);
            focusedIndex = 0;
        } else {
            int newHash = buttonStateHash(screen);
            if (newHash != lastButtonStateHash) {
                rebuildFocusableList(screen);
                if (focusedIndex >= focusableItems.size()) {
                    focusedIndex = focusableItems.isEmpty() ? 0 : focusableItems.size() - 1;
                }
            }
        }
    }

    /**
     * Moves focus to the nearest item above the current one. Wraps to the bottom
     * if no item is above.
     */
    public void focusUp() {
        if (focusableItems.isEmpty()) return;
        int target = findNearestInDirection(focusedIndex, Direction.UP);
        if (target < 0) target = findNearestInDirection(focusedIndex, Direction.WRAP_UP);
        if (target >= 0) focusedIndex = target;
    }

    /**
     * Moves focus to the nearest item below the current one. Wraps to the top
     * if no item is below.
     */
    public void focusDown() {
        if (focusableItems.isEmpty()) return;
        int target = findNearestInDirection(focusedIndex, Direction.DOWN);
        if (target < 0) target = findNearestInDirection(focusedIndex, Direction.WRAP_DOWN);
        if (target >= 0) focusedIndex = target;
    }

    /**
     * Moves focus to the nearest item to the left. Wraps to the rightmost
     * item if none found to the left.
     */
    public void focusLeft() {
        if (focusableItems.isEmpty()) return;
        int target = findNearestInDirection(focusedIndex, Direction.LEFT);
        if (target < 0) target = findNearestInDirection(focusedIndex, Direction.WRAP_LEFT);
        if (target >= 0) focusedIndex = target;
    }

    /**
     * Moves focus to the nearest item to the right. Wraps to the leftmost
     * item if none found to the right.
     */
    public void focusRight() {
        if (focusableItems.isEmpty()) return;
        int target = findNearestInDirection(focusedIndex, Direction.RIGHT);
        if (target < 0) target = findNearestInDirection(focusedIndex, Direction.WRAP_RIGHT);
        if (target >= 0) focusedIndex = target;
    }

    // ---- Legacy focusNext/focusPrev delegates ----

    /** Legacy: moves focus down (wrapping). */
    public void focusNext() {
        focusDown();
    }

    /** Legacy: moves focus up (wrapping). */
    public void focusPrev() {
        focusUp();
    }

    /**
     * Returns the currently focused {@link FocusableItem}, or {@code null}.
     */
    @Nullable
    public FocusableItem getFocusedItem() {
        if (focusableItems.isEmpty() || focusedIndex < 0 || focusedIndex >= focusableItems.size()) {
            return null;
        }
        return focusableItems.get(focusedIndex);
    }

    /**
     * Returns the currently focused {@link GuiButton}, or {@code null} if the focused
     * item is a slot entry or no item is focused.
     */
    @Nullable
    public GuiButton getFocusedButton() {
        FocusableItem item = getFocusedItem();
        return item != null && item.type == ItemType.BUTTON ? item.button : null;
    }

    /**
     * Returns the center point (in GUI scaled coordinates, top-left origin) of the
     * currently focused item, or {@code null} if no item is focused.
     */
    @Nullable
    public Point getFocusedItemCenter() {
        FocusableItem item = getFocusedItem();
        if (item == null) return null;
        return getItemCenter(item);
    }

    /**
     * Returns the center point (in display pixels, LWJGL bottom-left origin) of the
     * currently focused item, or {@code null} if no item is focused.
     */
    @Nullable
    public Point getFocusedCenter() {
        FocusableItem item = getFocusedItem();
        if (item == null) return null;

        Minecraft mc = Minecraft.getMinecraft();
        Point guiCenter = getItemCenter(item);
        float scaleX = (float) mc.displayWidth / (float) mc.currentScreen.width;
        float scaleY = (float) mc.displayHeight / (float) mc.currentScreen.height;

        int displayX = (int) (guiCenter.x * scaleX);
        int displayY = (int) (guiCenter.y * scaleY);
        return new Point(displayX, mc.displayHeight - displayY);
    }

    /**
     * Activates the focused slot entry by calling {@code elementClicked} on the slot
     * via the {@link GuiSlotAccessors} mixin invoker.
     */
    public void confirmSlotEntry() {
        FocusableItem item = getFocusedItem();
        if (item == null || item.type != ItemType.SLOT_ENTRY) return;

        GuiSlotAccessors slotAcc = (GuiSlotAccessors) item.slot;
        Minecraft mc = Minecraft.getMinecraft();

        Point guiCenter = getItemCenter(item);
        int scrollOffset = (int) Math.ceil(slotAcc.getAmountScrolled());

        boolean doubleClick = item.slotIndex == slotAcc.getSelectedElement()
            && System.currentTimeMillis() - slotAcc.getLastClicked() < 250L;

        slotAcc.callElementClicked(item.slotIndex, doubleClick, guiCenter.x, guiCenter.y);
        slotAcc.setSelectedElement(item.slotIndex);
        slotAcc.setLastClicked(System.currentTimeMillis());
    }

    /**
     * Adjusts the focused slider left (decrease value) by the given step.
     */
    public void sliderLeft(float step) {
        adjustSlider(-step);
    }

    /**
     * Adjusts the focused slider right (increase value) by the given step.
     */
    public void sliderRight(float step) {
        adjustSlider(step);
    }

    // -------------------------------------------------------------------------
    // Package-private test support
    // -------------------------------------------------------------------------

    int getFocusedIndex() {
        return focusedIndex;
    }

    int getFocusableCount() {
        return focusableItems.size();
    }

    void rebuildForTest(List<GuiButton> buttons) {
        currentScreen = null;
        focusableItems = new ArrayList<>();
        if (buttons != null) {
            for (GuiButton btn : buttons) {
                if (btn.visible && btn.enabled) {
                    focusableItems.add(new FocusableItem(btn));
                }
            }
        }
        focusableItems = Collections.unmodifiableList(focusableItems);
        focusedIndex = 0;
    }

    // -------------------------------------------------------------------------
    // 2D spatial navigation
    // -------------------------------------------------------------------------

    /** Cardinal direction for spatial focus search. */
    private enum Direction {
        UP,
        DOWN,
        LEFT,
        RIGHT,
        /** Wrapping variants — find the farthest item in the opposite direction. */
        WRAP_UP,
        WRAP_DOWN,
        WRAP_LEFT,
        WRAP_RIGHT
    }

    /**
     * Finds the index of the item nearest to {@code fromIdx} in the given direction
     * using overlap-aware spatial navigation.
     *
     * <p>
     * <b>Vertical movement (UP/DOWN):</b> first looks for items whose horizontal bounds
     * overlap the current item's horizontal span (i.e., items in the same column).
     * If any are found, only those are considered — picking the closest vertically.
     * If none overlap, falls back to nearest-neighbor with perpendicular-distance scoring.
     *
     * <p>
     * <b>Horizontal movement (LEFT/RIGHT):</b> same logic but using vertical overlap
     * (same row) instead.
     *
     * <p>
     * <b>Wrapping:</b> finds the closest item in the <em>opposite</em> direction
     * from the far side of the screen.
     */
    private int findNearestInDirection(int fromIdx, Direction dir) {
        FocusableItem from = focusableItems.get(fromIdx);
        AxisAligned fromBox = getItemBox(from);

        List<Integer> sameGroup = new ArrayList<>();
        List<Integer> otherGroup = new ArrayList<>();

        for (int i = 0; i < focusableItems.size(); i++) {
            if (i == fromIdx) continue;

            FocusableItem candidate = focusableItems.get(i);
            AxisAligned box = getItemBox(candidate);

            boolean overlaps = switch (dir) {
                case UP, DOWN, WRAP_UP, WRAP_DOWN -> fromBox.horizontallyOverlaps(box);
                case LEFT, RIGHT, WRAP_LEFT, WRAP_RIGHT -> fromBox.verticallyOverlaps(box);
            };

            boolean ok = switch (dir) {
                case UP -> box.centerY < fromBox.centerY;
                case DOWN -> box.centerY > fromBox.centerY;
                case LEFT -> box.centerX < fromBox.centerX;
                case RIGHT -> box.centerX > fromBox.centerX;
                case WRAP_UP -> box.centerY > fromBox.centerY;
                case WRAP_DOWN -> box.centerY < fromBox.centerY;
                case WRAP_LEFT -> box.centerX > fromBox.centerX;
                case WRAP_RIGHT -> box.centerX < fromBox.centerX;
            };

            if (!ok) continue;

            if (overlaps) {
                sameGroup.add(i);
            } else {
                otherGroup.add(i);
            }
        }

        // Prefer same-row/column candidates. Fall back to nearest-neighbor.
        List<Integer> pool = sameGroup.isEmpty() ? otherGroup : sameGroup;
        if (pool.isEmpty()) return -1;

        boolean horiz = dir == Direction.LEFT || dir == Direction.RIGHT
            || dir == Direction.WRAP_LEFT
            || dir == Direction.WRAP_RIGHT;
        return pickClosest(fromBox, pool, horiz, dir);
    }

    /**
     * Picks the item from {@code pool} closest to {@code fromBox} in the given axis.
     */
    private int pickClosest(AxisAligned fromBox, List<Integer> pool, boolean horiz, Direction dir) {
        boolean isWrap = switch (dir) {
            case WRAP_UP, WRAP_DOWN, WRAP_LEFT, WRAP_RIGHT -> true;
            default -> false;
        };

        int bestIdx = -1;
        double bestScore = Double.MAX_VALUE;

        for (int i : pool) {
            AxisAligned box = getItemBox(focusableItems.get(i));

            double primaryDist = horiz ? Math.abs(box.centerX - fromBox.centerX)
                : Math.abs(box.centerY - fromBox.centerY);

            double perpDist = horiz ? Math.abs(box.centerY - fromBox.centerY) : Math.abs(box.centerX - fromBox.centerX);

            double score;
            if (isWrap) {
                score = -primaryDist + perpDist * 0.5;
            } else {
                score = primaryDist + perpDist * 0.1;
            }

            if (score < bestScore) {
                bestScore = score;
                bestIdx = i;
            }
        }
        return bestIdx;
    }

    /** Simple bounding box with overlap tests. */
    private static final class AxisAligned {

        final int left, right, top, bottom;
        final float centerX, centerY;

        AxisAligned(int left, int right, int top, int bottom) {
            this.left = left;
            this.right = right;
            this.top = top;
            this.bottom = bottom;
            this.centerX = (left + right) / 2f;
            this.centerY = (top + bottom) / 2f;
        }

        boolean horizontallyOverlaps(AxisAligned other) {
            return this.left < other.right && this.right > other.left;
        }

        boolean verticallyOverlaps(AxisAligned other) {
            return this.top < other.bottom && this.bottom > other.top;
        }
    }

    /**
     * Returns the scaled GUI-space center of the given item (top-left origin).
     */
    private static Point getItemCenter(FocusableItem item) {
        if (item.type == ItemType.BUTTON) {
            GuiButton btn = item.button;
            return new Point(
                btn.xPosition + btn.getButtonWidth() / 2,
                btn.yPosition + (btn.height > 0 ? btn.height : 20) / 2);
        }

        // Slot entry
        GuiSlotAccessors slotAcc = (GuiSlotAccessors) item.slot;
        int scrollOffset = (int) Math.ceil(slotAcc.getAmountScrolled());
        int y = item.slot.top + item.slot.headerPadding
            - scrollOffset
            + 4
            + item.slotIndex * item.slot.slotHeight
            + item.slot.slotHeight / 2;
        return new Point(item.slot.width / 2, y);
    }

    /**
     * Returns the bounding box of the given item in scaled GUI coordinates (top-left origin).
     */
    private static AxisAligned getItemBox(FocusableItem item) {
        if (item.type == ItemType.BUTTON) {
            GuiButton btn = item.button;
            return new AxisAligned(
                btn.xPosition,
                btn.xPosition + btn.getButtonWidth(),
                btn.yPosition,
                btn.yPosition + (btn.height > 0 ? btn.height : 20));
        }

        GuiSlotAccessors slotAcc = (GuiSlotAccessors) item.slot;
        int scrollOffset = (int) Math.ceil(slotAcc.getAmountScrolled());
        int y = item.slot.top + item.slot.headerPadding - scrollOffset + 4 + item.slotIndex * item.slot.slotHeight;
        int slotLeft = item.slot.width / 2 - slotAcc.callGetListWidth() / 2;
        return new AxisAligned(slotLeft, slotLeft + slotAcc.callGetListWidth(), y, y + item.slot.slotHeight);
    }

    // -------------------------------------------------------------------------
    // Internals
    // -------------------------------------------------------------------------

    private void rebuildFocusableList(GuiScreen screen) {
        List<FocusableItem> items = new ArrayList<>();

        if (screen instanceof SlotAwareScreen aware) {
            GuiSlot slot = aware.getSlot();
            if (slot != null) {
                GuiSlotAccessors slotAcc = (GuiSlotAccessors) slot;

                // GuiOptionsRowList (Video Settings) rows contain individual buttons;
                // enumerate them separately for individual focus.
                if (slot instanceof GuiOptionsRowList rowList) {
                    int count = slotAcc.callGetSize();
                    for (int i = 0; i < count; i++) {
                        Object row = rowList.getListEntry(i);
                        GuiOptionsRowListRowAccessors rowAcc = (GuiOptionsRowListRowAccessors) row;
                        GuiButton left = rowAcc.getLeftButton();
                        GuiButton right = rowAcc.getRightButton();
                        if (left != null) {
                            items.add(new FocusableItem(left));
                        }
                        if (right != null) {
                            items.add(new FocusableItem(right));
                        }
                    }
                } else {
                    int count = slotAcc.callGetSize();
                    for (int i = 0; i < count; i++) {
                        items.add(new FocusableItem(slot, i));
                    }
                }
            }
        }

        List<GuiButton> buttons = getScreenButtonList(screen);
        if (buttons != null) {
            for (GuiButton btn : buttons) {
                if (btn.visible && btn.enabled) {
                    items.add(new FocusableItem(btn));
                }
            }
        }

        focusableItems = Collections.unmodifiableList(items);
        lastButtonStateHash = buttonStateHash(screen, buttons);
    }

    @SuppressWarnings("unchecked")
    @Nullable
    private static List<GuiButton> getScreenButtonList(GuiScreen screen) {
        try {
            return ((GuiScreenAccessors) screen).getButtonList();
        } catch (Exception e) {
            try {
                java.lang.reflect.Field f = GuiScreen.class.getDeclaredField("buttonList");
                f.setAccessible(true);
                return (List<GuiButton>) f.get(screen);
            } catch (Exception ex) {
                return null;
            }
        }
    }

    private static int buttonStateHash(GuiScreen screen) {
        return buttonStateHash(screen, getScreenButtonList(screen));
    }

    private static int buttonStateHash(GuiScreen screen, List<GuiButton> buttons) {
        if (buttons == null) return 0;
        int hash = buttons.size();
        for (GuiButton btn : buttons) {
            hash = hash * 31 + (btn.visible ? 1 : 0);
            hash = hash * 31 + (btn.enabled ? 1 : 0);
            hash = hash * 31 + btn.id;
        }
        return hash;
    }

    @SuppressWarnings("unchecked")
    private void adjustSlider(float delta) {
        GuiButton button = getFocusedButton();
        if (button == null) return;

        // ---- Vanilla GuiOptionSlider (Video Settings sliders) ----
        if (button instanceof GuiOptionSlider slider) {
            GuiOptionSliderAccessors sliderAcc = (GuiOptionSliderAccessors) slider;
            float current = sliderAcc.getSliderValue();
            float newValue = Math.max(0.0f, Math.min(1.0f, current + delta));
            Minecraft mc = Minecraft.getMinecraft();
            float denormalized = sliderAcc.getOptions()
                .denormalizeValue(newValue);
            mc.gameSettings.setOptionFloatValue(sliderAcc.getOptions(), denormalized);
            float normalized = sliderAcc.getOptions()
                .normalizeValue(denormalized);
            sliderAcc.setSliderValue(normalized);
            slider.displayString = mc.gameSettings.getKeyBinding(sliderAcc.getOptions());
            return;
        }

        // ---- GuiScreenOptionsSounds.Button (Music & Sounds sliders) ----
        // Private inner class slider. Uses reflection since the class isn't
        // accessible for a mixin. Fields: field_146156_o (float sliderValue),
        // field_146153_r (SoundCategory), field_146152_s (String label).
        try {
            Class<?> clazz = button.getClass();
            if (!clazz.getName()
                .equals("net.minecraft.client.gui.GuiScreenOptionsSounds$Button")) {
                return;
            }
            java.lang.reflect.Field valueField = clazz.getDeclaredField("field_146156_o");
            java.lang.reflect.Field categoryField = clazz.getDeclaredField("field_146153_r");
            java.lang.reflect.Field labelField = clazz.getDeclaredField("field_146152_s");
            valueField.setAccessible(true);
            categoryField.setAccessible(true);
            labelField.setAccessible(true);
            float current = valueField.getFloat(button);
            float newValue = Math.max(0.0f, Math.min(1.0f, current + delta));
            valueField.setFloat(button, newValue);
            Minecraft mc = Minecraft.getMinecraft();
            Object soundCategory = categoryField.get(button);
            mc.gameSettings.setSoundLevel((net.minecraft.client.audio.SoundCategory) soundCategory, newValue);
            // Update display string: label + ": " + percentage or "Off"
            // Replicates GuiScreenOptionsSounds.func_146504_a logic
            float level = mc.gameSettings.getSoundLevel((net.minecraft.client.audio.SoundCategory) soundCategory);
            String offStr = net.minecraft.client.resources.I18n.format("options.off");
            String pct = level == 0.0F ? offStr : (int) (level * 100.0F) + "%";
            button.displayString = labelField.get(button) + ": " + pct;
        } catch (Exception ignored) {}
    }

    /**
     * Returns true if the given button is a slider-type control that should be
     * adjusted by D-pad left/right rather than navigated past.
     *
     * <p>
     * Checks for both {@link GuiOptionSlider} (vanilla video settings) and
     * {@code GuiScreenOptionsSounds$Button} (Music & Sounds custom slider).
     */
    public static boolean isSliderButton(GuiButton button) {
        if (button instanceof GuiOptionSlider) return true;
        // GuiScreenOptionsSounds.Button is a private inner class slider.
        return button.getClass()
            .getName()
            .equals("net.minecraft.client.gui.GuiScreenOptionsSounds$Button");
    }
}
