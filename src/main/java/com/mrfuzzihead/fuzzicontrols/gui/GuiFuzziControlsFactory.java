package com.mrfuzzihead.fuzzicontrols.gui;

import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;

import cpw.mods.fml.client.IModGuiFactory;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Forge mod-list entry point for the FuzziControls settings screen.
 */
@SideOnly(Side.CLIENT)
public class GuiFuzziControlsFactory implements IModGuiFactory {

    @Override
    public void initialize(Minecraft minecraftInstance) {
        // No initialization required; the screen reads the global Config directly.
    }

    @Override
    public Class<? extends GuiScreen> mainConfigGuiClass() {
        return GuiFuzziControls.class;
    }

    @Override
    public Set<RuntimeOptionCategoryElement> runtimeGuiCategories() {
        return null;
    }

    @Override
    public RuntimeOptionGuiHandler getHandlerFor(RuntimeOptionCategoryElement element) {
        return null;
    }
}
