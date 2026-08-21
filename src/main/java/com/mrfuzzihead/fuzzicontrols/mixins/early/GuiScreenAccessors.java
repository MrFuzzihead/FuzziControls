package com.mrfuzzihead.fuzzicontrols.mixins.early;

import net.minecraft.client.gui.GuiScreen;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * {@link @Invoker} accessors for {@link GuiScreen}'s protected mouse and keyboard methods.
 * These three methods are {@code protected} in the base class and overriding subclasses prevent
 * widening them via an access transformer (53+ compile errors confirmed). An {@code @Invoker}
 * mixin is the correct solution — no reflection, no AT breakage.
 */
@Mixin(GuiScreen.class)
public interface GuiScreenAccessors {

    @Invoker("keyTyped")
    void callKeyTyped(char typedChar, int keyCode);

    @Invoker("mouseClicked")
    void callMouseClicked(int mouseX, int mouseY, int mouseButton);

    @Invoker("mouseMovedOrUp")
    void callMouseMovedOrUp(int mouseX, int mouseY, int mouseButton);
}
