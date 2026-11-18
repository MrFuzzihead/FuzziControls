package com.mrfuzzihead.fuzzicontrols.gui.widget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;

/**
 * Flat, modern-looking button used throughout the FuzziControls settings screen.
 *
 * <p>
 * Vanilla's 1.7.10 button texture is fixed at 20 px tall and looks out of place in the compact
 * tab/row layout. Drawing the background ourselves also makes 14 px action rows and 16 px tabs
 * possible without stretching the vanilla texture.
 */
public class GuiFuzziButton extends GuiButton {

    protected boolean active;
    protected int textColor = 0xE0E0E0;

    public GuiFuzziButton(int id, int x, int y, int width, int height, String text) {
        super(id, x, y, width, height, text);
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public void setTextColor(int textColor) {
        this.textColor = textColor;
    }

    @Override
    public void drawButton(Minecraft mc, int mouseX, int mouseY) {
        if (!visible) return;

        this.field_146123_n = mouseX >= xPosition && mouseY >= yPosition
            && mouseX < xPosition + width
            && mouseY < yPosition + height;

        int background;
        if (!enabled) {
            background = 0x80202020;
        } else if (active) {
            background = 0xD04A6FA8;
        } else if (field_146123_n) {
            background = 0xD03D5F92;
        } else {
            background = 0xC02C2C2C;
        }

        Gui.drawRect(xPosition, yPosition, xPosition + width, yPosition + height, background);
        Gui.drawRect(xPosition, yPosition, xPosition + width, yPosition + 1, 0x50FFFFFF);
        Gui.drawRect(xPosition, yPosition + height - 1, xPosition + width, yPosition + height, 0x50000000);
        if (active) {
            Gui.drawRect(xPosition, yPosition, xPosition + 2, yPosition + height, 0xFF8FC4FF);
        }

        int color = enabled ? textColor : 0x777777;
        int textY = yPosition + (height - 8) / 2;
        if (active) color = 0xFFFFFF;
        drawCenteredString(mc.fontRenderer, displayString, xPosition + width / 2, textY, color);
    }

    public boolean isHovered() {
        return field_146123_n;
    }
}
