package com.mrfuzzihead.fuzzicontrols.mixins.early;

import net.minecraft.client.gui.GuiSlot;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * {@link @Accessor} and {@link @Invoker} mixin for {@link GuiSlot}.
 *
 * <p>
 * Provides access to private and protected fields/methods needed by the D-pad navigation
 * system to interact with slot-based GUI lists (world selection, server list, etc.)
 * without having to simulate mouse clicks.
 *
 * <p>
 * The {@link #callElementClicked} invoker dispatches directly to the overridden
 * {@code elementClicked} method on the concrete subclass (e.g.
 * {@code GuiSelectWorld.List}), enabling the D-pad to select slot entries.
 */
@Mixin(GuiSlot.class)
public interface GuiSlotAccessors {

    @Invoker("elementClicked")
    void callElementClicked(int slotIndex, boolean doubleClick, int mouseZ, int mouseY);

    @Accessor("selectedElement")
    int getSelectedElement();

    @Accessor("selectedElement")
    void setSelectedElement(int value);

    @Accessor("lastClicked")
    long getLastClicked();

    @Accessor("lastClicked")
    void setLastClicked(long value);

    @Accessor("headerPadding")
    int getHeaderPadding();

    @Accessor("amountScrolled")
    float getAmountScrolled();

    @Accessor("amountScrolled")
    void setAmountScrolled(float value);

    @Invoker("getSize")
    int callGetSize();

    @Invoker("getListWidth")
    int callGetListWidth();
}
