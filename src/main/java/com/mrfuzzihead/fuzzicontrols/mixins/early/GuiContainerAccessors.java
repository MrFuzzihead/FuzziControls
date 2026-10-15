package com.mrfuzzihead.fuzzicontrols.mixins.early;

import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.inventory.Container;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * {@link @Accessor} and {@link @Invoker} mixin for {@link GuiContainer} (the base class of
 * every inventory screen — the player inventory, chests, crafting tables, furnaces, and any
 * modded container that extends it).
 *
 * <p>
 * Exposes the protected layout fields ({@code guiLeft}/{@code guiTop}/{@code xSize}/{@code ySize})
 * and the protected {@code theSlot} field. These are needed by the D-pad navigation system to
 * map {@link net.minecraft.inventory.Slot} grid positions to on-screen coordinates.
 *
 * <p>
 * {@code Container.inventorySlots} and {@code Slot.xDisplayPosition}/{@code yDisplayPosition}
 * are public fields in 1.7.10, so they are accessed directly without accessors.
 */
@Mixin(GuiContainer.class)
public interface GuiContainerAccessors {

    @Accessor("guiLeft")
    int getGuiLeft();

    @Accessor("guiTop")
    int getGuiTop();

    @Accessor("xSize")
    int getXSize();

    @Accessor("ySize")
    int getYSize();

    @Accessor("inventorySlots")
    Container getInventorySlots();
}
