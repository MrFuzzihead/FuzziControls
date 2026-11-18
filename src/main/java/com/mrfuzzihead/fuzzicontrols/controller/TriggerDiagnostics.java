package com.mrfuzzihead.fuzzicontrols.controller;

import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.settings.KeyBinding;

import com.mrfuzzihead.fuzzicontrols.FuzziControls;

/**
 * Temporary, opt-in trigger diagnostics for held-attack/use bug reports.
 *
 * <p>
 * Start with {@code /fuzzicontrols triggers}; raw trigger bytes and the derived Minecraft key
 * states are logged at INFO for a short window. This is intentionally a diagnostic tool, not a
 * normal gameplay log.
 */
public final class TriggerDiagnostics {

    private static final long WINDOW_MILLIS = 15_000L;

    private static volatile long untilMillis;

    private TriggerDiagnostics() {}

    /** Starts a 15-second diagnostic window. */
    public static void start() {
        untilMillis = System.currentTimeMillis() + WINDOW_MILLIS;
        FuzziControls.LOG.info(
            "[FuzziControls] Trigger diagnostics ON for {}s. Hold R2 and L2 now, then check the log.",
            WINDOW_MILLIS / 1000L);
    }

    /** Whether the diagnostic window is still active. */
    public static boolean isActive() {
        return System.currentTimeMillis() < untilMillis;
    }

    /** Logs the raw trigger bytes from one input report. */
    static void logRawReport(DualSenseReportParser.Layout layout, byte[] report, int length) {
        if (!isActive()) return;
        int[] indices = DualSenseReportParser.triggerIndices(layout);
        if (indices == null || length <= indices[2]) return;

        int lt = report[indices[0]] & 0xFF;
        int rt = report[indices[1]] & 0xFF;
        int buttons1 = report[indices[2]] & 0xFF;
        FuzziControls.LOG.info(
            "[FuzziControls] TRIGDIAG raw layout={} len={} LT=0x{} RT=0x{} BTN1=0x{} digitalL2={} digitalR2={}",
            layout,
            length,
            hex(lt),
            hex(rt),
            hex(buttons1),
            (buttons1 & 0x04) != 0,
            (buttons1 & 0x08) != 0);
    }

    /** Logs the normalised state and the resulting Minecraft key state for one client tick. */
    static void logTick(Minecraft mc, ControllerState state, ControllerMapping mapping, boolean attacking,
        boolean usingItem, boolean displayActive, boolean connected, Set<ControllerButton> heldOnGuiClose,
        Set<Integer> claimedKeys) {
        if (!isActive()) return;

        KeyBinding attackKey = mc.gameSettings.keyBindAttack;
        KeyBinding useKey = mc.gameSettings.keyBindUseItem;
        GuiScreen screen = mc.currentScreen;
        FuzziControls.LOG.info(
            "[FuzziControls] TRIGDIAG tick screen={} world={} player={} display={} connected={} LT={} RT={} attack={} use={} attackKey={} useKey={} heldOnGui={} claimed={}",
            screen != null ? screen.getClass()
                .getSimpleName() : "none",
            mc.theWorld != null,
            mc.thePlayer != null,
            displayActive,
            connected,
            state.leftTrigger(),
            state.rightTrigger(),
            attacking,
            usingItem,
            attackKey.getIsKeyPressed(),
            useKey.getIsKeyPressed(),
            heldOnGuiClose,
            claimedKeys);
    }

    private static String hex(int value) {
        return Integer.toHexString(value)
            .toUpperCase();
    }
}
