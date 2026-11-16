package com.mrfuzzihead.fuzzicontrols.toast;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Draws {@link ToastManager}'s queue over both the in-game HUD and open GUI screens.
 *
 * <p>
 * The HUD path is skipped while a screen is open so a toast is never drawn twice in the same frame;
 * {@link GuiScreenEvent.DrawScreenEvent.Post} then draws it on top of the screen. This matters for
 * the main menu, where a controller can connect before a world is loaded.
 */
@SideOnly(Side.CLIENT)
public final class ToastRenderer {

    @SubscribeEvent
    public void onRenderOverlay(RenderGameOverlayEvent.Post event) {
        if (event.type != RenderGameOverlayEvent.ElementType.ALL) return;

        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.currentScreen != null) return;

        ToastManager.getInstance()
            .render(mc, event.resolution);
    }

    @SubscribeEvent
    public void onDrawScreenPost(GuiScreenEvent.DrawScreenEvent.Post event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.currentScreen == null) return;

        ToastManager.getInstance()
            .render(mc, new ScaledResolution(mc, mc.displayWidth, mc.displayHeight));
    }
}
