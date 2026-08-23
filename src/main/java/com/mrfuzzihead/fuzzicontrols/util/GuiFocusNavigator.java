package com.mrfuzzihead.fuzzicontrols.util;

import java.awt.Point;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiOptionSlider;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiSlot;

import com.mrfuzzihead.fuzzicontrols.interfaces.SlotAwareScreen;
import com.mrfuzzihead.fuzzicontrols.mixins.early.GuiOptionSliderAccessors;
import com.mrfuzzihead.fuzzicontrols.mixins.early.GuiScreenAccessors;
import com.mrfuzzihead.fuzzicontrols.mixins.early.GuiSlotAccessors;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Reusable focus-based navigation system for GUI screens.
 *
 * <p>
 * Tracks a "focused element" index across any open {@link GuiScreen}, moving it with
 * {@link #focusNext()}/{@link #focusPrev()}. The focusable items are a combined list of
 * <em>slot entries</em> (from {@link GuiSlot}-based lists like the world/sever selection)
 * followed by <em>real buttons</em> from the screen's {@link GuiScreen#buttonList}.
 *
 * <p>
 * On each screen change (detected by identity comparison), both lists are rebuilt.
 * Within the same screen, the button visibility/enabled state is re-checked every
 * {@link #update} call so newly-enabled buttons are immediately reachable.
 *
 * <p>
 * Slot entries are interacted with via the {@link GuiSlotAccessors} mixin invoker,
 * bypassing the mouse system entirely.
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
     * Cached count of enabled+visible buttons from the last rebuild. Used to detect
     * state changes within the same screen (e.g. buttons enabled after a slot click).
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
            // Same screen: only rebuild if button state changed (visibility/enabled).
            int newHash = buttonStateHash(screen);
            if (newHash != lastButtonStateHash) {
                rebuildFocusableList(screen);
                // Clamp focus index if the new list is smaller.
                if (focusedIndex >= focusableItems.size()) {
                    focusedIndex = focusableItems.isEmpty() ? 0 : focusableItems.size() - 1;
                }
            }
        }
    }

    /**
     * Moves focus to the previous (up) item, wrapping around to the last.
     * No-op if no focusable items exist.
     */
    public void focusPrev() {
        if (focusableItems.isEmpty()) return;
        focusedIndex = (focusedIndex - 1 + focusableItems.size()) % focusableItems.size();
    }

    /**
     * Moves focus to the next (down) item, wrapping around to the first.
     * No-op if no focusable items exist.
     */
    public void focusNext() {
        if (focusableItems.isEmpty()) return;
        focusedIndex = (focusedIndex + 1) % focusableItems.size();
    }

    /**
     * Returns the currently focused {@link FocusableItem}, or {@code null} if the list
     * is empty or the index is out of range.
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
     * Returns the center point (in display pixels, LWJGL bottom-left origin) of the
     * currently focused item, or {@code null} if no item is focused.
     *
     * <p>
     * For buttons, calculates from the button's bounding box in scaled GUI coordinates.
     * For slot entries, calculates from the slot's layout parameters.
     */
    @Nullable
    public Point getFocusedCenter() {
        FocusableItem item = getFocusedItem();
        if (item == null) return null;

        Minecraft mc = Minecraft.getMinecraft();
        float scaleX = (float) mc.displayWidth / (float) mc.currentScreen.width;
        float scaleY = (float) mc.displayHeight / (float) mc.currentScreen.height;

        if (item.type == ItemType.BUTTON) {
            GuiButton button = item.button;
            int centerX = (int) ((button.xPosition + button.getButtonWidth() / 2f) * scaleX);
            int centerY = (int) ((button.yPosition + button.height / 2f) * scaleY);
            return new Point(centerX, mc.displayHeight - centerY);
        }

        // Slot entry: calculate GUI-space center of the entry.
        GuiSlot slot = item.slot;
        GuiSlotAccessors slotAcc = (GuiSlotAccessors) slot;
        int guiX = slot.width / 2;
        int scrollOffset = (int) Math.ceil(slotAcc.getAmountScrolled());
        int guiY = slot.top + slot.headerPadding
            - scrollOffset
            + 4
            + item.slotIndex * slot.slotHeight
            + slot.slotHeight / 2;

        int displayX = (int) (guiX * scaleX);
        int displayY = (int) (guiY * scaleY);
        return new Point(displayX, mc.displayHeight - displayY);
    }

    /**
     * Activates the focused slot entry by calling {@code elementClicked} on the slot
     * via the {@link GuiSlotAccessors} mixin invoker. Also updates the slot's selected
     * element tracking for double-click detection.
     *
     * <p>
     * No-op if the focused item is not a slot entry.
     */
    public void confirmSlotEntry() {
        FocusableItem item = getFocusedItem();
        if (item == null || item.type != ItemType.SLOT_ENTRY) return;

        GuiSlotAccessors slotAcc = (GuiSlotAccessors) item.slot;
        Minecraft mc = Minecraft.getMinecraft();

        // Calculate GUI-space coordinates (matching what drawScreen would pass).
        int scrollOffset = (int) Math.ceil(slotAcc.getAmountScrolled());
        int guiY = item.slot.top + item.slot.headerPadding
            - scrollOffset
            + 4
            + item.slotIndex * item.slot.slotHeight
            + item.slot.slotHeight / 2;
        int guiX = item.slot.width / 2;

        // Detect double-click via the slot's own tracking.
        boolean doubleClick = item.slotIndex == slotAcc.getSelectedElement()
            && System.currentTimeMillis() - slotAcc.getLastClicked() < 250L;

        slotAcc.callElementClicked(item.slotIndex, doubleClick, guiX, guiY);
        slotAcc.setSelectedElement(item.slotIndex);
        slotAcc.setLastClicked(System.currentTimeMillis());
    }

    /**
     * Adjusts the focused slider left (decrease value) by the given step fraction.
     * No-op if the focused item is not a slider button.
     */
    public void sliderLeft(float step) {
        adjustSlider(-step);
    }

    /**
     * Adjusts the focused slider right (increase value) by the given step fraction.
     * No-op if the focused item is not a slider button.
     */
    public void sliderRight(float step) {
        adjustSlider(step);
    }

    // -------------------------------------------------------------------------
    // Package-private test support
    // -------------------------------------------------------------------------

    /** Returns the index of the currently focused item. */
    int getFocusedIndex() {
        return focusedIndex;
    }

    /** Returns the number of focusable items. */
    int getFocusableCount() {
        return focusableItems.size();
    }

    /**
     * Package-private test helper: rebuilds the focusable list directly from a
     * pre-built list of buttons (no slots). Allows unit tests to verify focus-tracking
     * logic without a Minecraft runtime.
     */
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
    // Internals
    // -------------------------------------------------------------------------

    /**
     * Full rebuild of the focusable list. Uses the {@link SlotAwareScreen} interface
     * (provided by the {@code MixinGuiScreen_SlotTracker} mixin) to discover
     * {@link GuiSlot} instances without reflection. Slot entries come first in the
     * list, then real buttons.
     */
    private void rebuildFocusableList(GuiScreen screen) {
        List<FocusableItem> items = new ArrayList<>();

        // 1. Discover GuiSlot via the SlotAwareScreen interface (mixin-injected).
        if (screen instanceof SlotAwareScreen aware) {
            GuiSlot slot = aware.getSlot();
            if (slot != null) {
                GuiSlotAccessors slotAcc = (GuiSlotAccessors) slot;
                int count = slotAcc.callGetSize();
                for (int i = 0; i < count; i++) {
                    items.add(new FocusableItem(slot, i));
                }
            }
        }

        // 2. Add visible + enabled buttons from the screen.
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

    /**
     * Retrieves the button list from the given screen via the mixin accessor, or
     * falls back to reflection.
     */
    @SuppressWarnings("unchecked")
    @Nullable
    private static List<GuiButton> getScreenButtonList(GuiScreen screen) {
        try {
            return ((GuiScreenAccessors) screen).getButtonList();
        } catch (Exception e) {
            try {
                Field f = GuiScreen.class.getDeclaredField("buttonList");
                f.setAccessible(true);
                return (List<GuiButton>) f.get(screen);
            } catch (Exception ex) {
                return null;
            }
        }
    }

    /**
     * Computes a hash of the button state (visible+enabled counts and names) for
     * change detection. Uses the screen directly (not our filtered list) to detect
     * when buttons become enabled/disabled or visible/invisible.
     */
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

    // -------------------------------------------------------------------------
    // Slider adjustment
    // -------------------------------------------------------------------------

    /**
     * Adjusts the value of a focused {@link GuiOptionSlider} by the given delta.
     * The delta is a fraction of the slider's full range [0.0, 1.0].
     */
    private void adjustSlider(float delta) {
        GuiButton button = getFocusedButton();
        if (!(button instanceof GuiOptionSlider slider)) return;

        GuiOptionSliderAccessors sliderAcc = (GuiOptionSliderAccessors) slider;
        float current = sliderAcc.getSliderValue();
        float newValue = Math.max(0.0f, Math.min(1.0f, current + delta));

        Minecraft mc = Minecraft.getMinecraft();
        // Apply the option value — this may quantize/clamp it.
        float denormalized = sliderAcc.getOptions()
            .denormalizeValue(newValue);
        mc.gameSettings.setOptionFloatValue(sliderAcc.getOptions(), denormalized);

        // Re-normalize after the game setting has clamped/quantized it, and update
        // the display string — exactly what GuiOptionSlider.mouseDragged does.
        float normalized = sliderAcc.getOptions()
            .normalizeValue(denormalized);
        sliderAcc.setSliderValue(normalized);
        slider.displayString = mc.gameSettings.getKeyBinding(sliderAcc.getOptions());
    }
}
