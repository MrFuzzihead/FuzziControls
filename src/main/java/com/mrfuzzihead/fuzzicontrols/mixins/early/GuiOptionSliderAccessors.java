package com.mrfuzzihead.fuzzicontrols.mixins.early;

import net.minecraft.client.gui.GuiOptionSlider;
import net.minecraft.client.settings.GameSettings;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * {@link @Accessor} mixin for {@link GuiOptionSlider}'s private fields.
 *
 * <p>
 * This avoids reflection when the D-pad navigation system adjusts slider values.
 * Replaces the reflective get/set of {@code sliderValue} and the reflective read of
 * {@code options} with direct generated accessor calls.
 */
@Mixin(GuiOptionSlider.class)
public interface GuiOptionSliderAccessors {

    @Accessor("field_146134_p")
    float getSliderValue();

    @Accessor("field_146134_p")
    void setSliderValue(float value);

    @Accessor("field_146133_q")
    GameSettings.Options getOptions();
}
