package com.mrfuzzihead.fuzzicontrols.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.client.event.GuiScreenEvent;

import org.lwjgl.opengl.GL11;

import com.mrfuzzihead.fuzzicontrols.Config;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * Renders an animated focus highlight around the currently focused {@link GuiButton} in any
 * open GUI screen when D-pad navigation is enabled ({@link Config#dpadNavigation} = true).
 *
 * <p>
 * The highlight is a colored border rectangle drawn using vanilla GL line calls. The alpha
 * pulses sinusoidally so the selection is unmistakable without being distracting. No texture
 * assets are required.
 *
 * <p>
 * Registered on {@link net.minecraftforge.common.MinecraftForge#EVENT_BUS} in
 * {@link com.mrfuzzihead.fuzzicontrols.ClientProxy#init}.
 */
public final class GuiFocusRenderer {

    /** Highlight border width in pixels. */
    private static final float BORDER_WIDTH = 2.0f;

    /** Base RGB colour for the highlight (light blue, matching vanilla selection). */
    private static final float HIGHLIGHT_R = 0.4f;

    private static final float HIGHLIGHT_G = 0.8f;

    private static final float HIGHLIGHT_B = 1.0f;

    /** The shared navigator instance supplied by {@link com.mrfuzzihead.fuzzicontrols.ClientProxy}. */
    private final GuiFocusNavigator navigator;

    public GuiFocusRenderer(GuiFocusNavigator navigator) {
        this.navigator = navigator;
    }

    @SubscribeEvent
    public void onGuiDrawPost(GuiScreenEvent.DrawScreenEvent.Post event) {
        if (!Config.dpadNavigation) return;

        Minecraft mc = Minecraft.getMinecraft();
        GuiScreen screen = mc.currentScreen;
        if (screen == null) return;

        GuiButton focused = navigator.getFocusedButton(screen);
        if (focused == null) return;

        // Pulse alpha between 0.5 and 1.0 over a 1-second cycle.
        double pulse = 0.5 + 0.5 * Math.sin(System.currentTimeMillis() / 500.0 * Math.PI);
        float alpha = (float) (0.5 + 0.5 * pulse);

        int x1 = focused.xPosition - 2;
        int y1 = focused.yPosition - 2;
        int x2 = focused.xPosition + focused.width + 2;
        int y2 = focused.yPosition + focused.height + 2;

        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_LINE_BIT | GL11.GL_COLOR_BUFFER_BIT);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glLineWidth(BORDER_WIDTH);
        GL11.glColor4f(HIGHLIGHT_R, HIGHLIGHT_G, HIGHLIGHT_B, alpha);

        GL11.glBegin(GL11.GL_LINE_LOOP);
        GL11.glVertex2f(x1, y1);
        GL11.glVertex2f(x2, y1);
        GL11.glVertex2f(x2, y2);
        GL11.glVertex2f(x1, y2);
        GL11.glEnd();

        GL11.glPopAttrib();
    }
}
