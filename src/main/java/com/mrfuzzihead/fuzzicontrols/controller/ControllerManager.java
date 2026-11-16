package com.mrfuzzihead.fuzzicontrols.controller;

import java.util.Locale;

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
 * <b>Hot-plug:</b> While the game window is focused, the manager probes for a controller every
 * {@value #FOCUSED_RECONNECT_TICKS} ticks (~{@value #FOCUSED_RECONNECT_SECONDS} second), so a
 * controller plugged in during play is usable almost immediately. While the window is unfocused or
 * minimized it probes only every {@value #UNFOCUSED_RECONNECT_TICKS} ticks
 * (~{@value #UNFOCUSED_RECONNECT_SECONDS} seconds); returning focus triggers an immediate probe.
 * The active driver's disconnect is detected on the next client tick.
 *
 * <p>
 * <b>Disabling:</b> set {@code driver = none} in the config to stop all controller probing
 * (including the hot-plug retries). Unknown driver names are treated the same way and logged.
 *
 * <p>
 * <b>Performance:</b> The old reconnect probe constructed a {@code DualSenseDriver} that called
 * hid4java's {@code HidServices.getHidDevice(vid, pid, ...)}, which enumerates every HID device in
 * the system (~500 ms on a typical Windows machine) and also left hid4java's 500 ms scanner thread
 * running. The driver now opens with hidapi's native VID/PID filter instead (~2 ms idle, ~14 ms
 * when a controller is present), so the 1 Hz focused probe costs roughly 0.2% of one core and no
 * longer blocks a frame. The XInput probe is ~20 µs.
 */
public class ControllerManager {

    /** Probe interval while the game window is focused (20 ticks = 1 s). */
    private static final int FOCUSED_RECONNECT_TICKS = 20;

    /** Probe interval while the game window is unfocused/minimized (600 ticks = 30 s). */
    private static final int UNFOCUSED_RECONNECT_TICKS = 600;

    private static final int FOCUSED_RECONNECT_SECONDS = FOCUSED_RECONNECT_TICKS / 20;

    private static final int UNFOCUSED_RECONNECT_SECONDS = UNFOCUSED_RECONNECT_TICKS / 20;

    private IControllerDriver activeDriver;
    private ControllerState lastState = ControllerState.empty();

    /** Cached connected flag, refreshed each game tick so {@link #isActive()} does not re-poll hardware. */
    private boolean lastConnected = false;

    /** Counts down to the next reconnection attempt. */
    private int reconnectCooldown = 0;

    /** Whether the game window currently has focus; controls the hot-plug probe interval. */
    private boolean focused = true;

    /** True for {@code driver = none} (or an unknown value): no driver is created or polled. */
    private boolean driverDisabled = false;

    /** Normalized driver preference captured at {@link #init()}, so retries do not re-read the config. */
    private String driverPref = "auto";

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
        lastState = ControllerState.empty();
        reconnectCooldown = 0;
        driverDisabled = false;

        String requested = Config.controllerDriver;
        driverPref = requested == null ? ""
            : requested.trim()
                .toLowerCase(Locale.ROOT);
        if (driverPref.isEmpty()) driverPref = "auto";

        if ("none".equals(driverPref)) {
            driverDisabled = true;
            lastConnected = false;
            FuzziControls.LOG.info("[FuzziControls] Controller support disabled (driver=none).");
            return;
        }

        if (!isKnownDriver(driverPref)) {
            driverDisabled = true;
            lastConnected = false;
            FuzziControls.LOG.warn(
                "[FuzziControls] Unknown controller driver '{}'; expected auto, xinput, dualsense or none. "
                    + "Controller support is disabled until the config is fixed.",
                requested);
            return;
        }

        FuzziControls.LOG.info("[FuzziControls] Requested controller driver: {}", driverPref);

        if (selectDriver(driverPref)) {
            lastConnected = true;
            return;
        }

        lastConnected = false;
        // init() already paid for one probe; do not repeat it on the very next tick.
        reconnectCooldown = focused ? FOCUSED_RECONNECT_TICKS : UNFOCUSED_RECONNECT_TICKS;
        FuzziControls.LOG.info(
            "[FuzziControls] No controller detected; probing every {}s while focused ({}s when unfocused).",
            FOCUSED_RECONNECT_SECONDS,
            UNFOCUSED_RECONNECT_SECONDS);
    }

    private static boolean isKnownDriver(String pref) {
        return "auto".equals(pref) || "xinput".equals(pref) || "dualsense".equals(pref);
    }

    /**
     * Tries the configured drivers in order and adopts the first connected one.
     *
     * @param pref normalized driver preference ({@code auto}, {@code dualsense} or {@code xinput})
     * @return true when a driver was selected
     */
    private boolean selectDriver(String pref) {
        if ("auto".equals(pref) || "dualsense".equals(pref)) {
            IControllerDriver ds = driverFactory.createDualSense();
            if (ds != null && ds.isConnected()) {
                activeDriver = ds;
                lastConnected = true;
                FuzziControls.LOG.info("[FuzziControls] Using DualSense driver.");
                return true;
            }
            safeClose(ds);
        }

        if ("auto".equals(pref) || "xinput".equals(pref)) {
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
     * Also handles hot-plug: while no driver is active the manager probes every
     * {@value #FOCUSED_RECONNECT_TICKS} ticks when the window is focused and every
     * {@value #UNFOCUSED_RECONNECT_TICKS} ticks when it is not. A disconnect is detected and
     * retried immediately.
     * Must be called from the client tick thread.
     */
    public void tick() {
        if (driverDisabled) {
            lastState = ControllerState.empty();
            lastConnected = false;
            return;
        }

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
                // Probe on the tick the countdown reaches zero, so a 20-tick interval is 1 s
                // rather than 1 s plus one tick.
                if (reconnectCooldown > 0) {
                    lastState = ControllerState.empty();
                    lastConnected = false;
                    return;
                }
            }

            if (!tryReconnect()) {
                reconnectCooldown = focused ? FOCUSED_RECONNECT_TICKS : UNFOCUSED_RECONNECT_TICKS;
                lastState = ControllerState.empty();
                lastConnected = false;
                return;
            }

            reconnectCooldown = 0;
        }

        try {
            lastState = activeDriver.poll(Config.stickDeadZone, Config.triggerThreshold);
            lastConnected = true;
        } catch (Exception | LinkageError e) {
            // A transient hardware/native error must never crash the client tick.
            FuzziControls.LOG.warn("[FuzziControls] Controller poll error: {}", e.toString());
            safeClose(activeDriver);
            activeDriver = null;
            lastState = ControllerState.empty();
            lastConnected = false;
            reconnectCooldown = 0;
        }
    }

    /**
     * Updates the hot-plug probe schedule when the game window gains or loses focus.
     *
     * <p>
     * A focus gain triggers an immediate probe (cooldown 0) so a controller plugged in while the
     * player was alt-tabbed is picked up on the next tick. A focus loss while no controller is
     * active defers probing to the unfocused interval instead of leaving a 1 Hz scan running in
     * the background.
     * </p>
     *
     * @param focused true when the game window is active
     */
    public void setFocused(boolean focused) {
        if (this.focused == focused) return;
        this.focused = focused;
        if (driverDisabled) return;

        if (focused) {
            reconnectCooldown = 0;
        } else if (activeDriver == null) {
            reconnectCooldown = UNFOCUSED_RECONNECT_TICKS;
        }
    }

    /**
     * Silently attempts to initialize a driver without logging "no controller detected" noise.
     * On success, {@link #activeDriver} is set; on failure it remains {@code null}.
     *
     * @return true when a driver was adopted
     */
    private boolean tryReconnect() {
        return selectDriver(driverPref);
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
            } catch (Exception | LinkageError e) {
                // A render-frame poll error is usually fatal to the handle. Close it once and let
                // the next tick reconnect; logging this per frame would flood the log.
                FuzziControls.LOG.warn("[FuzziControls] Poll error in pollFresh: {}", e.toString());
                safeClose(activeDriver);
                activeDriver = null;
                lastState = ControllerState.empty();
                lastConnected = false;
                reconnectCooldown = 0;
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
        // The process-wide hidapi library is released separately by
        // DualSenseDriver.shutdownHid(), which ClientProxy calls from the JVM shutdown hook after
        // this method.
    }

    /** Exposes the active driver name for logging / GUI display. */
    public String getActiveDriverName() {
        return activeDriver != null ? activeDriver.getDriverName() : "None";
    }

    /** Human-readable name of the active device (e.g. {@code DualSense Edge}), or {@code ""}. */
    public String getActiveDeviceName() {
        return activeDriver != null ? activeDriver.getDeviceName() : "";
    }

    /** Human-readable transport of the active device (e.g. {@code USB}), or {@code ""}. */
    public String getActiveTransportName() {
        return activeDriver != null ? activeDriver.getTransportName() : "";
    }

    /** Closes a driver without letting a native error propagate. */
    private static void safeClose(IControllerDriver driver) {
        if (driver == null) return;
        try {
            driver.close();
        } catch (Exception | LinkageError e) {
            FuzziControls.LOG.warn("[FuzziControls] Error closing controller driver: {}", e.toString());
        }
    }
}
