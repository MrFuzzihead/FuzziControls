package com.mrfuzzihead.fuzzicontrols.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraftforge.client.event.GuiScreenEvent;

import org.lwjgl.opengl.GL11;

import com.mrfuzzihead.fuzzicontrols.Config;
import com.mrfuzzihead.fuzzicontrols.mixins.early.GuiSlotAccessors;
import com.mrfuzzihead.fuzzicontrols.util.GuiFocusNavigator.FocusableItem;
import com.mrfuzzihead.fuzzicontrols.util.GuiFocusNavigator.ItemType;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Renders a pulsing highlight border around the currently focused GUI element when
 * D-pad navigation is active.
 *
 * <p>
 * The renderer only draws when {@link #navActive} is true, which is toggled by the
 * D-pad navigation state machine in {@link com.mrfuzzihead.fuzzicontrols.controller.ControllerTickHandler}.
 * This prevents the highlight from appearing when the left-stick cursor is in use,
 * when the mouse is being used, or when no D-pad navigation has been initiated.
 *
 * <p>
 * Supports real {@link GuiButton} instances, virtual slot entries (from
 * {@link GuiSlot}-based lists like the world/sever selector), and inventory
 * {@link net.minecraft.inventory.Slot} instances from {@link GuiContainer} screens.
 *
 * <p>
 * Registered on {@link net.minecraftforge.common.MinecraftForge#EVENT_BUS} for
 * {@link GuiScreenEvent.DrawScreenEvent.Post} in
 * {@link com.mrfuzzihead.fuzzicontrols.ClientProxy#init}.
 */
@SideOnly(Side.CLIENT)
public final class GuiFocusRenderer {

    /** Shared navigator instance, set by {@link #setNavigator(GuiFocusNavigator)}. */
    private static GuiFocusNavigator navigator;

    /**
     * Whether D-pad navigation is currently active on this screen.
     * Toggled by {@link com.mrfuzzihead.fuzzicontrols.controller.ControllerTickHandler}
     * via {@link #setNavActive(boolean)}. When false, the highlight is not drawn even
     * if a focused item exists.
     */
    private static boolean navActive = false;

    /**
     * Sets the navigator instance to query for the currently focused element.
     * Called during {@link com.mrfuzzihead.fuzzicontrols.controller.ControllerTickHandler} initialisation.
     */
    public static void setNavigator(GuiFocusNavigator nav) {
        navigator = nav;
    }

    /**
     * Toggles whether the highlight should render. Set to true when D-pad navigation
     * activates, false when it deactivates (left-stick movement, mouse movement, or
     * screen close).
     */
    public static void setNavActive(boolean active) {
        navActive = active;
    }

    @SubscribeEvent
    public void onDrawScreenPost(GuiScreenEvent.DrawScreenEvent.Post event) {
        if (!Config.dpadNavigation) return;
        if (navigator == null) return;
        if (!navActive) return;

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.currentScreen == null) return;

        FocusableItem focused = navigator.getFocusedItem();
        if (focused == null) return;

        // Determine bounding box in scaled GUI coordinates.
        int x, y, w, h;

        if (focused.type == ItemType.BUTTON) {
            GuiButton button = focused.button;
            if (!button.visible) return;
            x = button.xPosition;
            y = button.yPosition;
            w = button.getButtonWidth();
            h = button.height > 0 ? button.height : 20;
        } else if (focused.type == ItemType.INVENTORY_SLOT) {
            // Slot background is 18x18 centred at (guiLeft + xDisplayPosition, guiTop + yDisplayPosition)
            x = focused.guiLeft + focused.inventorySlot.xDisplayPosition - 1;
            y = focused.guiTop + focused.inventorySlot.yDisplayPosition - 1;
            w = 18;
            h = 18;
        } else {
            // GuiSlot entry — use the slot's layout to calculate the entry bounds.
            // GuiSlot.drawScreen clips entries to the list viewport with a scissor rectangle, so
            // drawing the border for an entry scrolled out of view would paint it over unrelated
            // UI (and A would activate an entry the player cannot see).
            if (!navigator.isFocusedItemVisible()) return;
            GuiSlotAccessors slotAcc = (GuiSlotAccessors) focused.slot;
            int slotLeft = focused.slot.width / 2 - slotAcc.callGetListWidth() / 2;
            int scrollOffset = (int) Math.ceil(slotAcc.getAmountScrolled());
            int entryTop = focused.slot.top + focused.slot.headerPadding
                - scrollOffset
                + 4
                + focused.slotIndex * focused.slot.slotHeight;
            x = slotLeft;
            y = entryTop;
            w = slotAcc.callGetListWidth();
            h = focused.slot.slotHeight;
        }

        // Pulsing alpha: oscillates between ~120 and ~200 every ~400 ms
        long ms = System.currentTimeMillis();
        float pulse = (float) Math.sin(ms / 200.0);
        int alpha = 140 + (int) (60 * pulse);
        alpha = Math.max(50, Math.min(220, alpha));

        // Draw a 2-pixel border
        int topColor = (alpha << 24) | 0xFFFFFF;
        int leftColor = (alpha << 24) | 0xFFFFFF;
        int bottomColor = (Math.max(alpha - 30, 0) << 24) | 0xCCCCCC;
        int rightColor = (Math.max(alpha - 30, 0) << 24) | 0xCCCCCC;

        drawRect(x - 2, y - 2, x + w + 2, y, topColor);
        drawRect(x - 2, y + h, x + w + 2, y + h + 2, bottomColor);
        drawRect(x - 2, y, x, y + h, leftColor);
        drawRect(x + w, y, x + w + 2, y + h, rightColor);
    }

    /**
     * Draws a filled rectangle using immediate-mode GL.
     *
     * <p>
     * Coordinates are in scaled GUI space (top-left origin). The shared
     * {@link net.minecraft.client.renderer.Tessellator} is deliberately not used: this renderer runs
     * from {@code DrawScreenEvent.Post}, so it must neither depend on nor disturb tessellator state
     * that a screen (or another mod) may have left behind.
     */
    private static void drawRect(int left, int top, int right, int bottom, int color) {
        if (left >= right || top >= bottom) return;

        float a = (float) (color >> 24 & 255) / 255.0F;
        float r = (float) (color >> 16 & 255) / 255.0F;
        float g = (float) (color >> 8 & 255) / 255.0F;
        float b = (float) (color & 255) / 255.0F;

        GL11.glEnable(GL11.GL_BLEND);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glColor4f(r, g, b, a);

        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex2i(left, bottom);
        GL11.glVertex2i(right, bottom);
        GL11.glVertex2i(right, top);
        GL11.glVertex2i(left, top);
        GL11.glEnd();

        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_BLEND);
    }
}
