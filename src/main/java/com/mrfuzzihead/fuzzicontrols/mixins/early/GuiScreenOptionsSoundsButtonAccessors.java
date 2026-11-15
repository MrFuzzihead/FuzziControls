package com.mrfuzzihead.fuzzicontrols.mixins.early;

import net.minecraft.client.audio.SoundCategory;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * {@link Accessor} mixin for the private {@code GuiScreenOptionsSounds$Button} slider.
 *
 * <p>
 * Music &amp; Sounds uses a private inner {@code GuiButton} subclass for its sliders. The previous
 * implementation reached its fields with reflection using hard-coded SRG names, which works in a
 * production install but silently fails in a dev environment where the fields carry MCP names.
 * An accessor mixin is remapped by the refmap in both environments and avoids the per-press
 * reflection lookup.
 * </p>
 *
 * <p>
 * Exposes the slider value ({@code field_146156_o}), the {@link SoundCategory}
 * ({@code field_146153_r}), and the label ({@code field_146152_s}); the button's
 * {@code displayString} is public and is updated directly.
 * </p>
 */
@Mixin(targets = "net.minecraft.client.gui.GuiScreenOptionsSounds$Button")
public interface GuiScreenOptionsSoundsButtonAccessors {

    @Accessor("field_146156_o")
    float getSliderValue();

    @Accessor("field_146156_o")
    void setSliderValue(float value);

    @Accessor("field_146153_r")
    SoundCategory getSoundCategory();

    @Accessor("field_146152_s")
    String getLabel();
}
