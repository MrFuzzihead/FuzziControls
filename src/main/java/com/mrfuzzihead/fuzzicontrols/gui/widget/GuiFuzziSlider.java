package com.mrfuzzihead.fuzzicontrols.gui.widget;

import java.util.Locale;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;

/**
 * Compact settings slider with a label, a numeric value and a clickable/draggable track.
 */
public class GuiFuzziSlider extends GuiButton implements ConfigSlider {

    private final String label;
    private final float min;
    private final float max;
    private final int decimals;
    private final float displayFactor;
    private final String suffix;
    private final Runnable onCommit;

    private float value;
    private boolean dragging;

    public GuiFuzziSlider(int id, int x, int y, int width, int height, String label, float min, float max, float value,
        int decimals, float displayFactor, String suffix, Runnable onCommit) {
        super(id, x, y, width, height, label);
        this.label = label;
        this.min = min;
        this.max = max;
        this.decimals = Math.max(0, decimals);
        this.displayFactor = displayFactor;
        this.suffix = suffix == null ? "" : suffix;
        this.onCommit = onCommit;
        this.value = clamp(value);
        updateDisplay();
    }

    public float getValue() {
        return value;
    }

    public void setValue(float value) {
        this.value = clamp(value);
        updateDisplay();
    }

    @Override
    public void step(float delta) {
        if (!enabled || delta == 0f) return;
        setValue(value + delta * (max - min));
        commit();
    }

    private float clamp(float candidate) {
        return Math.max(min, Math.min(max, candidate));
    }

    private void updateDisplay() {
        displayString = label + ": " + formatValue();
    }

    private String formatValue() {
        return String.format(Locale.ROOT, "%." + decimals + "f", value * displayFactor) + suffix;
    }

    private void commit() {
        if (onCommit != null) onCommit.run();
    }

    @Override
    public void drawButton(Minecraft mc, int mouseX, int mouseY) {
        if (!visible) return;

        this.field_146123_n = mouseX >= xPosition && mouseY >= yPosition
            && mouseX < xPosition + width
            && mouseY < yPosition + height;

        int background = !enabled ? 0x80202020 : (field_146123_n || dragging ? 0xD03D5F92 : 0xC02C2C2C);
        Gui.drawRect(xPosition, yPosition, xPosition + width, yPosition + height, background);

        int textY = yPosition + 3;
        int color = enabled ? 0xE0E0E0 : 0x777777;
        mc.fontRenderer.drawStringWithShadow(label, xPosition + 5, textY, color);

        String shown = formatValue();
        int valueWidth = mc.fontRenderer.getStringWidth(shown);
        mc.fontRenderer.drawStringWithShadow(shown, xPosition + width - 5 - valueWidth, textY, 0xAFC8E8);

        int trackLeft = xPosition + 6;
        int trackRight = xPosition + width - 6;
        int trackY = yPosition + height - 6;
        Gui.drawRect(trackLeft, trackY, trackRight, trackY + 2, 0xFF555555);

        float fraction = max == min ? 0f : (value - min) / (max - min);
        int knobX = trackLeft + Math.round(fraction * (trackRight - trackLeft));
        Gui.drawRect(knobX - 2, trackY - 2, knobX + 3, trackY + 4, enabled ? 0xFF8FC4FF : 0xFF777777);
    }

    @Override
    public boolean mousePressed(Minecraft mc, int mouseX, int mouseY) {
        if (!enabled || !visible) return false;
        if (mouseX >= xPosition && mouseY >= yPosition && mouseX < xPosition + width && mouseY < yPosition + height) {
            dragging = true;
            updateFromMouse(mouseX);
            return true;
        }
        return false;
    }

    @Override
    protected void mouseDragged(Minecraft mc, int mouseX, int mouseY) {
        if (dragging) {
            updateFromMouse(mouseX);
        }
    }

    @Override
    public void mouseReleased(int mouseX, int mouseY) {
        if (dragging) {
            dragging = false;
            commit();
        }
    }

    private void updateFromMouse(int mouseX) {
        int trackLeft = xPosition + 6;
        int trackRight = xPosition + width - 6;
        float fraction = trackRight == trackLeft ? 0f : (float) (mouseX - trackLeft) / (float) (trackRight - trackLeft);
        fraction = Math.max(0f, Math.min(1f, fraction));
        value = min + fraction * (max - min);
        updateDisplay();
    }
}
