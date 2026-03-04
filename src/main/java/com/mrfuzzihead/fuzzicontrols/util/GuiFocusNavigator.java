package com.mrfuzzihead.fuzzicontrols.util;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiOptionSlider;
import net.minecraft.client.gui.GuiScreen;

import com.mrfuzzihead.fuzzicontrols.Config;
import com.mrfuzzihead.fuzzicontrols.FuzziControls;

/**
 * Reusable focus-navigation logic for D-pad driven GUI navigation.
 *
 * <p>
 * Inspects the {@code buttonList} field of the currently open {@link GuiScreen} to build an
 * ordered list of focusable elements. The focused index advances with
 * {@link #focusNext(GuiScreen)} / {@link #focusPrev(GuiScreen)}, and the focused button is
 * activated by the D-pad confirm action in
 * {@link com.mrfuzzihead.fuzzicontrols.controller.ControllerTickHandler}.
 *
 * <p>
 * Slider support is provided via {@link #sliderLeft(GuiScreen)} and
 * {@link #sliderRight(GuiScreen)}, which adjust the slider value by
 * {@link Config#dpadSliderStep} per D-pad press. Slider value adjustment uses reflection to
 * access the internal {@code sliderValue} field — this is documented in the
 * Reflection/Mixins/AT tracker section of {@code plan-controllerSupport.prompt.md}.
 *
 * <p>
 * The navigator resets automatically when a new screen is detected (screen identity changes).
 * All operations are no-ops when {@code buttonList} is empty or the focused button is null.
 */
public final class GuiFocusNavigator {

    // -------------------------------------------------------------------------
    // Reflection — GuiScreen.buttonList (protected List<GuiButton>)
    // -------------------------------------------------------------------------

    /** Reflected reference to {@code GuiScreen.buttonList}. */
    private static final Field BUTTON_LIST_FIELD;

    // -------------------------------------------------------------------------
    // Reflection — GuiOptionSlider.sliderValue (float, private)
    // -------------------------------------------------------------------------

    /**
     * Reflected reference to {@code GuiOptionSlider.sliderValue}.
     * {@code null} if reflection failed at class load; slider adjustment is then a no-op.
     *
     * <p>
     * Documented in plan-controllerSupport.prompt.md Reflection/Mixins/AT tracker.
     * Future improvement: replace with a Mixin {@code @Accessor} on {@code GuiOptionSlider}.
     */
    private static final Field OPTION_SLIDER_VALUE;

    /**
     * Reflected reference to {@code GuiButton.mouseDragged(Minecraft, int, int)} (protected).
     * Used to notify sliders of a value change so they update their displayed label.
     * {@code null} if reflection failed.
     */
    private static final Method MOUSE_DRAGGED;

    static {
        Field buttonListField = null;
        try {
            buttonListField = GuiScreen.class.getDeclaredField("buttonList");
            buttonListField.setAccessible(true);
        } catch (NoSuchFieldException e) {
            FuzziControls.LOG.warn(
                "[FuzziControls] GuiFocusNavigator: could not reflect GuiScreen.buttonList; "
                    + "D-pad navigation will be unavailable.",
                e);
        }
        BUTTON_LIST_FIELD = buttonListField;

        Field optionSliderValue = null;
        try {
            optionSliderValue = GuiOptionSlider.class.getDeclaredField("sliderValue");
            optionSliderValue.setAccessible(true);
        } catch (NoSuchFieldException e) {
            FuzziControls.LOG.warn(
                "[FuzziControls] GuiFocusNavigator: could not reflect GuiOptionSlider.sliderValue; "
                    + "D-pad slider adjustment for vanilla option sliders will be unavailable.",
                e);
        }
        OPTION_SLIDER_VALUE = optionSliderValue;

        Method mouseDragged = null;
        try {
            mouseDragged = GuiButton.class.getDeclaredMethod("mouseDragged", Minecraft.class, int.class, int.class);
            mouseDragged.setAccessible(true);
        } catch (NoSuchMethodException e) {
            FuzziControls.LOG.warn(
                "[FuzziControls] GuiFocusNavigator: could not reflect GuiButton.mouseDragged; "
                    + "slider value labels may not update after D-pad adjustment.",
                e);
        }
        MOUSE_DRAGGED = mouseDragged;
    }

    // -------------------------------------------------------------------------
    // State
    // -------------------------------------------------------------------------

    /** The screen that was open when {@link #currentFocusIndex} was last updated. */
    private GuiScreen lastScreen = null;

    /** Index into the current screen's {@code buttonList} of the focused button. */
    private int currentFocusIndex = 0;

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Must be called once per game tick (or whenever the active screen may have changed).
     * Resets focus to index 0 whenever the open screen changes.
     *
     * @param screen the currently active {@link GuiScreen}, or {@code null} if none
     */
    public void onScreenChanged(GuiScreen screen) {
        if (screen != lastScreen) {
            lastScreen = screen;
            currentFocusIndex = 0;
        }
    }

    /**
     * Advances focus to the next focusable button, wrapping from the last element back to 0.
     * No-op if the current screen has no focusable buttons.
     *
     * @param screen the currently active {@link GuiScreen}
     */
    public void focusNext(GuiScreen screen) {
        List<GuiButton> buttons = getButtons(screen);
        if (buttons == null || buttons.isEmpty()) return;
        currentFocusIndex = (currentFocusIndex + 1) % buttons.size();
    }

    /**
     * Moves focus to the previous focusable button, wrapping from 0 back to the last element.
     * No-op if the current screen has no focusable buttons.
     *
     * @param screen the currently active {@link GuiScreen}
     */
    public void focusPrev(GuiScreen screen) {
        List<GuiButton> buttons = getButtons(screen);
        if (buttons == null || buttons.isEmpty()) return;
        int size = buttons.size();
        currentFocusIndex = (currentFocusIndex - 1 + size) % size;
    }

    /**
     * Returns the currently focused {@link GuiButton}, or {@code null} if the screen has no
     * focusable buttons or the screen is null.
     *
     * @param screen the currently active {@link GuiScreen}
     * @return the focused button, or {@code null}
     */
    public GuiButton getFocusedButton(GuiScreen screen) {
        List<GuiButton> buttons = getButtons(screen);
        if (buttons == null || buttons.isEmpty()) return null;
        int safeIndex = Math.min(currentFocusIndex, buttons.size() - 1);
        return buttons.get(safeIndex);
    }

    /**
     * Returns the center X coordinate of the currently focused button in GUI (scaled) space.
     * Returns -1 if there is no focused button.
     *
     * @param screen the currently active {@link GuiScreen}
     */
    public int getFocusedCenterX(GuiScreen screen) {
        GuiButton btn = getFocusedButton(screen);
        if (btn == null) return -1;
        return btn.xPosition + btn.width / 2;
    }

    /**
     * Returns the center Y coordinate of the currently focused button in GUI (scaled) space.
     * Returns -1 if there is no focused button.
     *
     * @param screen the currently active {@link GuiScreen}
     */
    public int getFocusedCenterY(GuiScreen screen) {
        GuiButton btn = getFocusedButton(screen);
        if (btn == null) return -1;
        return btn.yPosition + btn.height / 2;
    }

    /**
     * Decreases the value of the currently focused slider by {@link Config#dpadSliderStep}.
     * If the focused button is not a slider, this is a no-op.
     *
     * @param screen the currently active {@link GuiScreen}
     */
    public void sliderLeft(GuiScreen screen) {
        adjustSlider(screen, -Config.dpadSliderStep);
    }

    /**
     * Increases the value of the currently focused slider by {@link Config#dpadSliderStep}.
     * If the focused button is not a slider, this is a no-op.
     *
     * @param screen the currently active {@link GuiScreen}
     */
    public void sliderRight(GuiScreen screen) {
        adjustSlider(screen, Config.dpadSliderStep);
    }

    /**
     * Returns true if the currently focused button is a {@link GuiOptionSlider}.
     *
     * @param screen the currently active {@link GuiScreen}
     */
    public boolean isFocusedSlider(GuiScreen screen) {
        GuiButton btn = getFocusedButton(screen);
        return btn instanceof GuiOptionSlider;
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Retrieves the {@code buttonList} from the given {@link GuiScreen} via reflected field.
     * Returns {@code null} if the screen is null or the field is not accessible.
     */
    @SuppressWarnings("unchecked")
    private static List<GuiButton> getButtons(GuiScreen screen) {
        if (screen == null || BUTTON_LIST_FIELD == null) return null;
        try {
            return (List<GuiButton>) BUTTON_LIST_FIELD.get(screen);
        } catch (Exception e) {
            FuzziControls.LOG.debug("[FuzziControls] GuiFocusNavigator: could not access buttonList.", e);
            return null;
        }
    }

    /**
     * Adjusts the focused {@link GuiOptionSlider}'s value by {@code delta} (clamped to [0, 1]).
     */
    private void adjustSlider(GuiScreen screen, float delta) {
        GuiButton btn = getFocusedButton(screen);
        if (btn == null || !(btn instanceof GuiOptionSlider) || OPTION_SLIDER_VALUE == null) return;

        try {
            float current = (float) OPTION_SLIDER_VALUE.get(btn);
            float next = Math.max(0f, Math.min(1f, current + delta));
            OPTION_SLIDER_VALUE.set(btn, next);
            // Trigger the slider's built-in onChange logic by calling mouseDragged
            // reflectively. This updates the displayed value label and applies the setting.
            if (MOUSE_DRAGGED != null) {
                int sliderX = btn.xPosition + (int) (next * btn.width);
                MOUSE_DRAGGED.invoke(btn, Minecraft.getMinecraft(), sliderX, btn.yPosition);
            }
        } catch (Exception e) {
            FuzziControls.LOG.warn("[FuzziControls] GuiFocusNavigator: slider adjustment failed.", e);
        }
    }
}
