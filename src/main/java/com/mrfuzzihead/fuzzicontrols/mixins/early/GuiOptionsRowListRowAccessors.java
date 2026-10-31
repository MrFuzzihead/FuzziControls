package com.mrfuzzihead.fuzzicontrols.mixins.early;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiOptionsRowList;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * {@link @Accessor} mixin for {@link GuiOptionsRowList.Row}.
 *
 * <p>
 * Exposes the two private buttons inside each row so the D-pad navigation system
 * can focus individual options rather than treating the entire row as one slot entry.
 */
@Mixin(GuiOptionsRowList.Row.class)
public interface GuiOptionsRowListRowAccessors {

    @Accessor("field_148323_b")
    GuiButton getLeftButton();

    @Accessor("field_148324_c")
    GuiButton getRightButton();
}
