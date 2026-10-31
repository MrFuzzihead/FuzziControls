package com.mrfuzzihead.fuzzicontrols.mixins.early;

import java.util.List;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * {@link @Invoker} accessors for {@link GuiScreen}'s protected mouse and keyboard methods.
 * These three methods are {@code protected} in the base class and overriding subclasses prevent
 * widening them via an access transformer (53+ compile errors confirmed). An {@code @Invoker}
 * mixin is the correct solution — no reflection, no AT breakage.
 *
 * <p>
 * Also provides an {@link @Accessor} for the {@code buttonList} field, used by the D-pad
 * navigation system to enumerate focusable buttons.
 */
@Mixin(GuiScreen.class)
public interface GuiScreenAccessors {

    @Invoker("keyTyped")
    void callKeyTyped(char typedChar, int keyCode);

    @Invoker("mouseClicked")
    void callMouseClicked(int mouseX, int mouseY, int mouseButton);

    @Invoker("mouseMovedOrUp")
    void callMouseMovedOrUp(int mouseX, int mouseY, int mouseButton);

    @Accessor("buttonList")
    List<GuiButton> getButtonList();
}
