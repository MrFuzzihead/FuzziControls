package com.mrfuzzihead.fuzzicontrols.util;

/**
 * Monotonic counter bumped whenever a screen's layout is (re)built.
 *
 * <p>
 * {@link net.minecraft.client.gui.GuiScreen#setWorldAndResolution} calls {@code initGui()} and is
 * invoked again on the <em>same</em> screen object when the window is resized, fullscreen is
 * toggled or the GUI scale changes ({@code Minecraft.resize} → {@code setWorldAndResolution}).
 * Re-initialisation recreates the {@code GuiSlot} of list screens and moves the
 * {@code guiLeft}/{@code guiTop} origin of container screens, but leaves the button list — and
 * therefore any button-based change detection — identical.
 *
 * <p>
 * {@link com.mrfuzzihead.fuzzicontrols.mixins.early.MixinGuiScreen_SlotTracker} bumps this counter
 * after every {@code setWorldAndResolution}; {@link GuiFocusNavigator} compares it against the
 * generation it captured at its last rebuild so cached slot geometry is never used after a resize.
 */
public final class ScreenLayoutTracker {

    /**
     * Bumped on every screen (re)initialisation. {@code volatile} because the counter is written
     * from the injected {@code setWorldAndResolution} hook and read by the tick/render handlers.
     */
    private static volatile int generation = 0;

    private ScreenLayoutTracker() {}

    /** Records that a screen's layout has just been (re)built. */
    public static void bumpGeneration() {
        generation++;
    }

    /**
     * Returns the current layout generation. A value different from the one captured at the last
     * rebuild means the screen was re-initialised and all cached layout data must be discarded.
     */
    public static int getGeneration() {
        return generation;
    }
}
