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
 * automatically retries initialization every {@value #RECONNECT_INTERVAL_TICKS} game ticks
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

    private IControllerDriver activeDriver;
    private ControllerState lastState = ControllerState.empty();

    /** Cached connected flag, refreshed each game tick so {@link #isActive()} does not re-poll hardware. */
    private boolean lastConnected = false;

    /** Counts down to the next reconnection attempt. */
    private int reconnectCooldown = 0;

    /** Creates the concrete drivers; injectable so the selection/reconnect logic can be unit tested. */
    interface DriverFactory {

        IControllerDriver createDualSense();

        IControllerDriver createXInput(int slot);
    }

    /**
     * Creates the real drivers. Declared <em>before</em> {@link #INSTANCE}: static initialisers run in
     * declaration order, and constructing the singleton reads this factory.
     */
    private static final DriverFactory DEFAULT_FACTORY = new DriverFactory() {

        @Override
        public IControllerDriver createDualSense() {
            return new DualSenseDriver();
        }

        @Override
        public IControllerDriver createXInput(int slot) {
            return new XInputDriver(slot);
        }
    };

    private static final ControllerManager INSTANCE = new ControllerManager();

    private final DriverFactory driverFactory;

    /** Test-only: the factory the singleton was built with (guards static initialisation order). */
    DriverFactory getDriverFactoryForTest() {
        return driverFactory;
    }

    /** Returns the global singleton. */
    public static ControllerManager getInstance() {
        return INSTANCE;
    }

    private ControllerManager() {
        this(DEFAULT_FACTORY);
    }

    /** Test seam: lets a test supply fake drivers. */
    ControllerManager(DriverFactory driverFactory) {
        this.driverFactory = driverFactory;
    }

    /**
     * Initializes the driver according to the current {@link Config} settings.
     * Safe to call more than once (e.g. after a config reload or hot-plug retry).
     */
    public void init() {
        if (activeDriver != null) {
            safeClose(activeDriver);
            activeDriver = null;
            lastConnected = false;
        }

        String driverPref = Config.controllerDriver.toLowerCase();
        FuzziControls.LOG.info("[FuzziControls] Requested controller driver: {}", driverPref);

        if (selectDriver(driverPref, false)) return;

        lastConnected = false;
        FuzziControls.LOG.warn("[FuzziControls] No controller detected.");
    }

    /**
     * Tries the configured drivers in order and adopts the first connected one.
     *
     * @param driverPref configured preference ({@code auto}, {@code dualsense} or {@code xinput})
     * @param quiet      true when retrying in the background (suppresses the "no controller" warning)
     * @return true when a driver was selected
     */
    private boolean selectDriver(String driverPref, boolean quiet) {
        if ("auto".equals(driverPref) || "dualsense".equals(driverPref)) {
            IControllerDriver ds = driverFactory.createDualSense();
            if (ds != null && ds.isConnected()) {
                activeDriver = ds;
                lastConnected = true;
                FuzziControls.LOG.info("[FuzziControls] Using DualSense driver.");
                return true;
            }
            safeClose(ds);
        }

        if ("auto".equals(driverPref) || "xinput".equals(driverPref)) {
            int slot = Config.xInputControllerSlot;
            IControllerDriver xi = driverFactory.createXInput(slot);
            if (xi != null && xi.isConnected()) {
                activeDriver = xi;
                lastConnected = true;
                FuzziControls.LOG.info("[FuzziControls] Using XInput driver (slot {}).", slot);
                return true;
            }
            safeClose(xi);
        }

        return false;
    }

    /**
     * Polls the active driver and updates {@link #lastState}.
     * Also handles hot-plug: if no driver is active or the active driver disconnects,
     * re-initialization is attempted every {@value #RECONNECT_INTERVAL_TICKS} ticks.
     * Must be called from the client tick thread.
     */
    public void tick() {
        // If the active driver has disconnected, release it so we attempt a reconnect.
        if (activeDriver != null && !activeDriver.isConnected()) {
            FuzziControls.LOG.info("[FuzziControls] Controller disconnected. Will retry.");
            safeClose(activeDriver);
            activeDriver = null;
            lastState = ControllerState.empty();
            lastConnected = false;
            reconnectCooldown = 0;
        }

        if (activeDriver == null) {
            if (reconnectCooldown > 0) {
                reconnectCooldown--;
                lastState = ControllerState.empty();
                lastConnected = false;
                return;
            }
            // Cooldown expired — try to find a controller.
            reconnectCooldown = RECONNECT_INTERVAL_TICKS;
            tryReconnect();
            if (activeDriver == null) {
                lastState = ControllerState.empty();
                lastConnected = false;
                return;
            }
        }

        try {
            lastState = activeDriver.poll(Config.stickDeadZone, Config.triggerThreshold);
            lastConnected = true;
        } catch (Exception e) {
            // A transient hardware error must never crash the client tick.
            FuzziControls.LOG.warn("[FuzziControls] Controller poll error: {}", e.getMessage());
            safeClose(activeDriver);
            activeDriver = null;
            lastState = ControllerState.empty();
            lastConnected = false;
        }
    }

    /**
     * Silently attempts to initialize a driver without logging "no controller detected" noise.
     * On success, {@link #activeDriver} is set; on failure it remains {@code null}.
     */
    private void tryReconnect() {
        selectDriver(Config.controllerDriver.toLowerCase(), true);
    }

    /** Returns the most recently polled {@link ControllerState}. Never {@code null}. */
    public ControllerState getState() {
        return lastState;
    }

    /**
     * Polls the hardware and returns fresh state without running reconnect/disconnect logic.
     * Safe to call from the render thread between game ticks, e.g. before applying camera
     * rotation, so axis values are never more than one frame stale.
     */
    public ControllerState pollFresh() {
        if (activeDriver != null && lastConnected) {
            try {
                lastState = activeDriver.poll(Config.stickDeadZone, Config.triggerThreshold);
            } catch (Exception e) {
                FuzziControls.LOG.warn("[FuzziControls] Poll error in pollFresh: {}", e.getMessage());
            }
        }
        return lastState;
    }

    /** Returns true if an active, connected driver is in use. */
    public boolean isActive() {
        return activeDriver != null && lastConnected;
    }

    /** Closes and releases the active driver. Called on mod shutdown. */
    public void shutdown() {
        safeClose(activeDriver);
        activeDriver = null;
        lastState = ControllerState.empty();
        lastConnected = false;
        // The shared HidServices instance is released separately by
        // DualSenseDriver.shutdownSharedServices(), which ClientProxy calls from the JVM shutdown
        // hook after this method.
    }

    /** Exposes the active driver name for logging / GUI display. */
    public String getActiveDriverName() {
        return activeDriver != null ? activeDriver.getDriverName() : "None";
    }

    /** Closes a driver without letting a native error propagate. */
    private static void safeClose(IControllerDriver driver) {
        if (driver == null) return;
        try {
            driver.close();
        } catch (Exception e) {
            FuzziControls.LOG.warn("[FuzziControls] Error closing controller driver: {}", e.getMessage());
        }
    }
}
