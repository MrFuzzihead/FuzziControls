package com.mrfuzzihead.fuzzicontrols.util;

import java.awt.Point;

import net.minecraft.client.Minecraft;

import org.lwjgl.input.Keyboard;

import com.mrfuzzihead.fuzzicontrols.mixins.early.GuiScreenAccessors;
import com.mrfuzzihead.fuzzicontrols.util.GuiFocusNavigator.FocusableItem;
import com.mrfuzzihead.fuzzicontrols.util.GuiFocusNavigator.ItemType;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Activates the element currently focused by {@link GuiFocusNavigator}.
 *
 * <p>
 * Extracted from {@link com.mrfuzzihead.fuzzicontrols.controller.ControllerTickHandler} so the click
 * dispatch can be exercised by the client self-test harness as well as by controller input. The click
 * is dispatched with the element's own coordinates: {@code mouseClicked} receives them as method
 * parameters, so {@code GuiContainer.getSlotAtPosition()} finds the right slot without moving the OS
 * cursor (moving it would make the platform emit a mouse motion event, which the navigation state
 * machine would read as the player grabbing the mouse).
 */
@SideOnly(Side.CLIENT)
public final class GuiElementActivator {

    private GuiElementActivator() {}

    /**
     * Activates the focused element.
     *
     * @param navigator   navigator holding the focus
     * @param mouseButton 0 = left, 1 = right
     * @param mc          client instance
     * @param shiftClick  true to deliver a Shift+left-click (moves a whole stack)
     * @return true when a focused element existed and consumed the press
     */
    public static boolean activate(GuiFocusNavigator navigator, int mouseButton, Minecraft mc, boolean shiftClick) {
        FocusableItem focused = navigator.getFocusedItem();
        if (focused == null) return false;

        if (focused.type == ItemType.SLOT_ENTRY) {
            // List entries only respond to a left click.
            if (mouseButton == 0) {
                navigator.confirmSlotEntry();
            }
            return true;
        }

        // Right-clicking only has meaning for container slots: vanilla's GuiScreen.mouseClicked()
        // ignores the button index when scanning buttonList, so dispatching a right click at a
        // focused button would press it, and a slider would jump to the click position.
        if (mouseButton != 0 && focused.type != ItemType.INVENTORY_SLOT) return true;
        if (mouseButton == 0 && focused.button != null && GuiFocusNavigator.isSliderButton(focused.button)) {
            return true;
        }

        Point centre = navigator.getFocusedItemCenter();
        if (centre == null) return true;

        // Already in scaled GUI coordinates, which is the space mouseClicked()/getSlotAtPosition()
        // expect.
        final int guiX = centre.x;
        final int guiY = centre.y;

        try {
            GuiScreenAccessors accessors = (GuiScreenAccessors) mc.currentScreen;
            if (mouseButton == 0 && shiftClick) {
                // Hold shift for the whole press/release pair, and dispatch the release directly so
                // it lands on the focused element rather than wherever the OS cursor is.
                KeyboardHelper.withKeyHeld(Keyboard.KEY_LSHIFT, () -> {
                    GuiMouseHelper.directMouseClicked(mc.currentScreen, 0, guiX, guiY);
                    accessors.callMouseMovedOrUp(guiX, guiY, 0);
                });
            } else {
                accessors.callMouseClicked(guiX, guiY, mouseButton);
                accessors.callMouseMovedOrUp(guiX, guiY, mouseButton);
            }
        } catch (Exception ignored) {
            // A screen that rejects synthetic clicks must not break input handling.
        }
        return true;
    }
}
