package com.mrfuzzihead.fuzzicontrols.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiOptions;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.client.event.GuiScreenEvent;

import com.mrfuzzihead.fuzzicontrols.gui.widget.GuiFuzziButton;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Adds a FuzziControls entry to the vanilla Options screen.
 *
 * <p>
 * Using Forge's GUI events rather than another mixin keeps this compatible with mods that replace
 * or wrap {@link GuiOptions}; if the vanilla screen is not present the button is simply not added.
 */
@SideOnly(Side.CLIENT)
public final class OptionsMenuButtonHandler {

    private static final int BUTTON_ID = -9001;

    @SuppressWarnings("unchecked")
    @SubscribeEvent
    public void onInitGui(GuiScreenEvent.InitGuiEvent.Post event) {
        if (!(event.gui instanceof GuiOptions)) return;

        for (Object element : event.buttonList) {
            if (element instanceof GuiFuzziButton button && button.id == BUTTON_ID) return;
        }

        int buttonWidth = 110;
        int x = event.gui.width - buttonWidth - 4;
        int y = 4;
        event.buttonList.add(new GuiFuzziButton(BUTTON_ID, x, y, buttonWidth, 18, "FuzziControls"));
    }

    @SubscribeEvent
    public void onActionPerformed(GuiScreenEvent.ActionPerformedEvent.Pre event) {
        if (event.button == null || event.button.id != BUTTON_ID) return;
        GuiScreen parent = event.gui;
        Minecraft.getMinecraft()
            .displayGuiScreen(new GuiFuzziControls(parent));
        event.setCanceled(true);
    }
}
