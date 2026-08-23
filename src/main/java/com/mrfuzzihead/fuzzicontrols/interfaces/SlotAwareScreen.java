package com.mrfuzzihead.fuzzicontrols.interfaces;

import javax.annotation.Nullable;

import net.minecraft.client.gui.GuiSlot;

/**
 * Interface implemented by the {@link GuiScreen} mixin to expose a cached {@link GuiSlot}
 * reference without reflection.
 *
 * <p>
 * Screens with a slot-based list (world selection, server list, language list, etc.) will
 * have their inner {@code GuiSlot} subclass field detected via the {@code MixinGuiScreen_SlotTracker}
 * mixin after {@code setWorldAndResolution()} runs. The cached slot can then be read by the
 * D-pad navigation system via this interface.
 */
public interface SlotAwareScreen {

    /**
     * Returns the first {@link GuiSlot} instance found on this screen, or {@code null} if
     * the screen has no slot (e.g. pure button screens like the main menu).
     */
    @Nullable
    GuiSlot getSlot();
}
