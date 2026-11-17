package com.mrfuzzihead.fuzzicontrols.gui.widget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;

/**
 * Two-column row used for action bindings and shortcut slots: label on the left, current value on
 * the right.
 */
public class GuiFuzziRowButton extends GuiFuzziButton {

    private String label;
    private String value;
    private boolean selected;

    public GuiFuzziRowButton(int id, int x, int y, int width, int height, String label, String value) {
        super(id, x, y, width, height, label);
        this.label = label;
        this.value = value;
    }

    public void setLabel(String label) {
        this.label = label;
        this.displayString = label;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
    }

    public boolean isSelected() {
        return selected;
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
        } else if (selected) {
            background = 0xD04A6FA8;
        } else if (field_146123_n) {
            background = 0xD03D5F92;
        } else {
            background = 0xA0242424;
        }

        Gui.drawRect(xPosition, yPosition, xPosition + width, yPosition + height, background);
        if (selected) {
            Gui.drawRect(xPosition, yPosition, xPosition + 2, yPosition + height, 0xFF8FC4FF);
        }

        int textY = yPosition + (height - 8) / 2;
        int color = enabled ? (selected ? 0xFFFFFF : 0xD8D8D8) : 0x777777;
        int valueColor = enabled ? (selected ? 0xFFFFFF : 0xAFC8E8) : 0x666666;

        String shownValue = value == null ? "" : value;
        int valueWidth = mc.fontRenderer.getStringWidth(shownValue);
        int available = width - 10 - valueWidth - 6;
        String shownLabel = label == null ? "" : label;
        if (available > 0) {
            shownLabel = mc.fontRenderer.trimStringToWidth(shownLabel, available);
        }

        mc.fontRenderer.drawStringWithShadow(shownLabel, xPosition + 5, textY, color);
        if (valueWidth > 0) {
            mc.fontRenderer.drawStringWithShadow(shownValue, xPosition + width - 5 - valueWidth, textY, valueColor);
        }
    }
}
