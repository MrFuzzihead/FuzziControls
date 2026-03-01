package com.mrfuzzihead.fuzzicontrols.controller;

import com.mrfuzzihead.fuzzicontrols.Config;
import com.mrfuzzihead.fuzzicontrols.FuzziControls;

/**
 * Singleton that owns the active {@link IControllerDriver}, polls it every client tick,
 * and exposes the latest {@link ControllerState}.
 *
 * <p>
 * Driver selection order (unless overridden by config):
 * <ol>
 * <li>DualSense (HID) — if connected</li>
 * <li>XInput — first connected slot (0–3)</li>
 * <li>None — returns {@link ControllerState#empty()}</li>
 * </ol>
 *
 * <p>
 * <b>Hot-plug:</b> When no driver is active (or the active driver disconnects), the manager
 * automatically retries initialisation every {@value #RECONNECT_INTERVAL_TICKS} game ticks
 * (~{@value #RECONNECT_INTERVAL_SECONDS} seconds) so controllers plugged in after game launch
 * are detected without a restart.
 *
 * <p>
 * <b>Performance:</b> The disconnect check ({@code activeDriver.isConnected()}) runs every
 * tick but is zero-overhead — it reuses the same native {@code poll()} call that reading
 * input already requires. The reconnect probe ({@code tryReconnect()}) only runs once every
 * {@value #RECONNECT_INTERVAL_TICKS} ticks and involves constructing one or two driver objects
 * with native library calls; the cost is comparable to a single frame of normal polling and
 * is imperceptible in practice.
 */
public class ControllerManager {

    /** How many game ticks between reconnection attempts (20 ticks = 1 s). */
    private static final int RECONNECT_INTERVAL_TICKS = 60; // 3 seconds

    private static final int RECONNECT_INTERVAL_SECONDS = RECONNECT_INTERVAL_TICKS / 20;

    private static ControllerManager instance;

    private IControllerDriver activeDriver;
    private ControllerState lastState = ControllerState.empty();

    /** Counts down to the next reconnection attempt. */
    private int reconnectCooldown = 0;

    /** Returns the global singleton, creating it if needed. */
    public static ControllerManager getInstance() {
        if (instance == null) {
            instance = new ControllerManager();
        }
        return instance;
    }

    private ControllerManager() {}

    /**
     * Initialises the driver according to the current {@link Config} settings.
     * Safe to call more than once (e.g. after a config reload or hot-plug retry).
     */
    public void init() {
        if (activeDriver != null) {
            activeDriver.close();
            activeDriver = null;
        }

        String driverPref = Config.controllerDriver.toLowerCase();
        FuzziControls.LOG.info("[FuzziControls] Requested controller driver: {}", driverPref);

        if ("auto".equals(driverPref) || "dualsense".equals(driverPref)) {
            DualSenseDriver ds = new DualSenseDriver();
            if (ds.isConnected()) {
                activeDriver = ds;
                FuzziControls.LOG.info("[FuzziControls] Using DualSense driver.");
                return;
            } else {
                ds.close();
            }
        }

        if ("auto".equals(driverPref) || "xinput".equals(driverPref)) {
            int slot = Config.xInputControllerSlot;
            XInputDriver xi = new XInputDriver(slot);
            if (xi.isConnected()) {
                activeDriver = xi;
                FuzziControls.LOG.info("[FuzziControls] Using XInput driver (slot {}).", slot);
                return;
            } else {
                xi.close();
            }
        }

        FuzziControls.LOG.warn("[FuzziControls] No controller detected.");
    }

    /**
     * Polls the active driver and updates {@link #lastState}.
     * Also handles hot-plug: if no driver is active or the active driver disconnects,
     * re-initialisation is attempted every {@value #RECONNECT_INTERVAL_TICKS} ticks.
     * Must be called from the client tick thread.
     */
    public void tick() {
        // If the active driver has disconnected, release it so we attempt a reconnect.
        if (activeDriver != null && !activeDriver.isConnected()) {
            FuzziControls.LOG.info("[FuzziControls] Controller disconnected. Will retry.");
            activeDriver.close();
            activeDriver = null;
            lastState = ControllerState.empty();
            reconnectCooldown = 0;
        }

        if (activeDriver == null) {
            if (reconnectCooldown > 0) {
                reconnectCooldown--;
                lastState = ControllerState.empty();
                return;
            }
            // Cooldown expired — try to find a controller.
            reconnectCooldown = RECONNECT_INTERVAL_TICKS;
            tryReconnect();
            if (activeDriver == null) {
                lastState = ControllerState.empty();
                return;
            }
        }

        lastState = activeDriver.poll(Config.stickDeadZone, Config.triggerThreshold);
    }

    /**
     * Silently attempts to initialise a driver without logging "no controller detected" noise.
     * On success, {@link #activeDriver} is set; on failure it remains {@code null}.
     */
    private void tryReconnect() {
        String driverPref = Config.controllerDriver.toLowerCase();

        if ("auto".equals(driverPref) || "dualsense".equals(driverPref)) {
            DualSenseDriver ds = new DualSenseDriver();
            if (ds.isConnected()) {
                activeDriver = ds;
                FuzziControls.LOG.info("[FuzziControls] Controller connected — using DualSense driver.");
                return;
            }
            ds.close();
        }

        if ("auto".equals(driverPref) || "xinput".equals(driverPref)) {
            XInputDriver xi = new XInputDriver(Config.xInputControllerSlot);
            if (xi.isConnected()) {
                activeDriver = xi;
                FuzziControls.LOG.info(
                    "[FuzziControls] Controller connected — using XInput driver (slot {}).",
                    Config.xInputControllerSlot);
                return;
            }
            xi.close();
        }
    }

    /** Returns the most recently polled {@link ControllerState}. Never {@code null}. */
    public ControllerState getState() {
        return lastState;
    }

    /** Returns true if an active, connected driver is in use. */
    public boolean isActive() {
        return activeDriver != null && activeDriver.isConnected();
    }

    /** Closes and releases the active driver. Called on mod shutdown. */
    public void shutdown() {
        if (activeDriver != null) {
            activeDriver.close();
            activeDriver = null;
        }
        lastState = ControllerState.empty();
    }

    /** Exposes the active driver name for logging / GUI display. */
    public String getActiveDriverName() {
        return activeDriver != null ? activeDriver.getDriverName() : "None";
    }
}
