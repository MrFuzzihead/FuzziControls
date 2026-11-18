package com.mrfuzzihead.fuzzicontrols.controller;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

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

    /** UI listeners notified after the first successful poll and whenever the active device goes away. */
    private final List<ConnectionListener> connectionListeners = new CopyOnWriteArrayList<>();

    /** Re-send an active rumble value periodically so a dropped output report cannot stick. */
    private static final int RUMBLE_REFRESH_TICKS = 10;

    /** Remaining game ticks for the current rumble pulse. */
    private int rumbleTicks;

    /** Current normalised motor values, already scaled by {@link Config#rumbleIntensity}. */
    private float rumbleLeft;

    private float rumbleRight;

    /** Last values successfully handed to the driver. */
    private float appliedRumbleLeft;

    private float appliedRumbleRight;

    private boolean rumbleApplied;

    /** Counts down to the next periodic output-report refresh. */
    private int rumbleRefreshCooldown;

    /**
     * A driver selected by {@link #init()} or a reconnect is not announced until its first poll,
     * so the toast can include the USB/Bluetooth transport that is only known after a report.
     */
    private boolean connectedNotificationPending = false;

    /** Receives connection/disconnection events after the manager has committed the state change. */
    public interface ConnectionListener {

        void onControllerConnected(ControllerConnection connection);

        void onControllerDisconnected(ControllerConnection connection);
    }

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
        ControllerConnection previous = snapshotConnection(activeDriver);
        boolean previousAnnounced = !connectedNotificationPending;

        if (activeDriver != null) {
            stopRumble();
            clearTriggerEffects();
            safeClose(activeDriver);
            activeDriver = null;
            lastConnected = false;
        }
        connectedNotificationPending = false;
        lastState = ControllerState.empty();
        reconnectCooldown = 0;
        driverDisabled = false;

        String requested = Config.controllerDriver;
        driverPref = requested == null ? ""
            : requested.trim()
                .toLowerCase(Locale.ROOT);
        if (driverPref.isEmpty()) driverPref = "auto";

        if (!Config.controllerEnabled) {
            driverDisabled = true;
            lastConnected = false;
            FuzziControls.LOG.info("[FuzziControls] Controller support disabled in the settings.");
            if (previousAnnounced) notifyDisconnected(previous);
            return;
        }

        if ("none".equals(driverPref)) {
            driverDisabled = true;
            lastConnected = false;
            FuzziControls.LOG.info("[FuzziControls] Controller support disabled (driver=none).");
            if (previousAnnounced) notifyDisconnected(previous);
            return;
        }

        if (!isKnownDriver(driverPref)) {
            driverDisabled = true;
            lastConnected = false;
            FuzziControls.LOG.warn(
                "[FuzziControls] Unknown controller driver '{}'; expected auto, xinput, dualsense or none. "
                    + "Controller support is disabled until the config is fixed.",
                requested);
            if (previousAnnounced) notifyDisconnected(previous);
            return;
        }

        FuzziControls.LOG.info("[FuzziControls] Requested controller driver: {}", driverPref);

        if (selectDriver(driverPref)) {
            lastConnected = true;
            ControllerConnection current = snapshotConnection(activeDriver);

            // /fuzzicontrols reload closes and reopens the same device. Do not turn that into a
            // disconnect/connect toast pair; only report a genuine device change.
            if (previousAnnounced && previous != null && !previous.hasSameDevice(current)) {
                notifyDisconnected(previous);
            }
            if (previous == null || !previous.hasSameDevice(current)) {
                connectedNotificationPending = true;
            }
            return;
        }

        lastConnected = false;
        if (previousAnnounced) notifyDisconnected(previous);

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
            stopRumble();
            lastState = ControllerState.empty();
            lastConnected = false;
            return;
        }

        if (activeDriver == null || !lastConnected) {
            stopRumble();
        } else {
            updateRumble();
        }

        // If the active driver has disconnected, release it so we attempt a reconnect.
        if (activeDriver != null && !activeDriver.isConnected()) {
            ControllerConnection previous = snapshotConnection(activeDriver);
            boolean previousAnnounced = !connectedNotificationPending;
            FuzziControls.LOG.info("[FuzziControls] Controller disconnected. Will retry.");
            stopRumble();
            clearTriggerEffects();
            safeClose(activeDriver);
            activeDriver = null;
            lastState = ControllerState.empty();
            lastConnected = false;
            reconnectCooldown = 0;
            connectedNotificationPending = false;
            if (previousAnnounced) notifyDisconnected(previous);
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
            connectedNotificationPending = true;
        }

        try {
            lastState = activeDriver.poll(Config.stickDeadZone, Config.triggerThreshold);
            lastConnected = true;
            notifyConnectedIfPending();
        } catch (Exception | LinkageError e) {
            // A transient hardware/native error must never crash the client tick.
            FuzziControls.LOG.warn("[FuzziControls] Controller poll error: {}", e.toString());
            ControllerConnection previous = snapshotConnection(activeDriver);
            boolean previousAnnounced = !connectedNotificationPending;
            stopRumble();
            clearTriggerEffects();
            safeClose(activeDriver);
            activeDriver = null;
            lastState = ControllerState.empty();
            lastConnected = false;
            reconnectCooldown = 0;
            connectedNotificationPending = false;
            if (previousAnnounced) notifyDisconnected(previous);
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
                notifyConnectedIfPending();
            } catch (Exception | LinkageError e) {
                // A render-frame poll error is usually fatal to the handle. Close it once and let
                // the next tick reconnect; logging this per frame would flood the log.
                FuzziControls.LOG.warn("[FuzziControls] Poll error in pollFresh: {}", e.toString());
                ControllerConnection previous = snapshotConnection(activeDriver);
                boolean previousAnnounced = !connectedNotificationPending;
                stopRumble();
                clearTriggerEffects();
                safeClose(activeDriver);
                activeDriver = null;
                lastState = ControllerState.empty();
                lastConnected = false;
                reconnectCooldown = 0;
                connectedNotificationPending = false;
                if (previousAnnounced) notifyDisconnected(previous);
            }
        }
        return lastState;
    }

    /**
     * Starts a short rumble pulse. Overlapping pulses use the stronger value, and a weaker pulse
     * does not shorten or replace an active stronger one.
     *
     * <p>
     * Called from client-side gameplay feedback only. The value is scaled by
     * {@link Config#rumbleIntensity}; disabled rumble is a no-op.
     *
     * @param left          low-frequency / strong motor in {@code [0, 1]}
     * @param right         high-frequency / weak motor in {@code [0, 1]}
     * @param durationTicks pulse length in client ticks
     */
    public void pulseRumble(float left, float right, int durationTicks) {
        if (!Config.rumbleEnabled || durationTicks <= 0 || activeDriver == null || !lastConnected) return;

        float intensity = clamp01(Config.rumbleIntensity);
        if (intensity <= 0f) return;

        float nextLeft = clamp01(left) * intensity;
        float nextRight = clamp01(right) * intensity;
        float nextTotal = nextLeft + nextRight;
        float currentTotal = rumbleLeft + rumbleRight;

        // A weak pulse (e.g. mining) must not keep a strong damage pulse alive, and must not
        // override it while it is still active.
        if (rumbleTicks > 0 && nextTotal + 0.0001f < currentTotal) return;

        rumbleLeft = nextLeft;
        rumbleRight = nextRight;
        rumbleTicks = Math.max(rumbleTicks, durationTicks);
        rumbleRefreshCooldown = RUMBLE_REFRESH_TICKS;
        applyRumble();
    }

    /** Applies one adaptive-trigger effect to the active controller, if supported. */
    public void setTriggerEffect(ControllerTrigger trigger, ControllerTriggerEffect effect, int strength) {
        if (activeDriver == null || !lastConnected) return;
        try {
            activeDriver.setTriggerEffect(trigger, effect, strength);
        } catch (Exception | LinkageError e) {
            FuzziControls.LOG.debug("[FuzziControls] Could not apply adaptive trigger effect: {}", e.toString());
        }
    }

    /** Removes both adaptive-trigger effects. */
    public void clearTriggerEffects() {
        setTriggerEffect(ControllerTrigger.LEFT, ControllerTriggerEffect.OFF, 0);
        setTriggerEffect(ControllerTrigger.RIGHT, ControllerTriggerEffect.OFF, 0);
    }

    /** Applies the configured lightbar color and brightness to the active controller. */
    public void applyLightbar() {
        if (activeDriver == null || !lastConnected || !Config.lightbarEnabled) return;
        try {
            activeDriver.setLightbar(
                Config.lightbarRed,
                Config.lightbarGreen,
                Config.lightbarBlue,
                ControllerLightbarBrightness.fromValue(Config.lightbarBrightness));
        } catch (Exception | LinkageError e) {
            FuzziControls.LOG.debug("[FuzziControls] Could not apply lightbar state: {}", e.toString());
        }
    }

    /** Battery percentage of the active controller, or {@code -1} when unknown/unsupported. */
    public int getActiveBatteryPercent() {
        return activeDriver != null && lastConnected ? activeDriver.getBatteryPercent() : -1;
    }

    /** Whether the active controller reports that it is charging. */
    public boolean isActiveBatteryCharging() {
        return activeDriver != null && lastConnected && activeDriver.isBatteryCharging();
    }

    /** Stops any active rumble immediately; safe to call when no controller is present. */
    public void stopRumble() {
        if (activeDriver != null && rumbleApplied) {
            try {
                activeDriver.setRumble(0f, 0f);
            } catch (Exception | LinkageError e) {
                FuzziControls.LOG.debug("[FuzziControls] Could not stop controller rumble: {}", e.toString());
            }
        }

        rumbleTicks = 0;
        rumbleLeft = 0f;
        rumbleRight = 0f;
        appliedRumbleLeft = 0f;
        appliedRumbleRight = 0f;
        rumbleApplied = false;
        rumbleRefreshCooldown = 0;
    }

    /** Advances pulse timing and refreshes the hardware value periodically. */
    private void updateRumble() {
        if (!Config.rumbleEnabled || Config.rumbleIntensity <= 0f) {
            stopRumble();
            return;
        }
        if (rumbleTicks <= 0) {
            stopRumble();
            return;
        }

        rumbleTicks--;
        if (rumbleTicks <= 0) {
            stopRumble();
            return;
        }

        if (--rumbleRefreshCooldown <= 0) {
            rumbleRefreshCooldown = RUMBLE_REFRESH_TICKS;
            applyRumble();
        }
    }

    /** Sends the current values to the hardware, skipping no-op writes. */
    private void applyRumble() {
        if (activeDriver == null || !lastConnected) return;
        if (rumbleApplied && Math.abs(rumbleLeft - appliedRumbleLeft) < 0.0001f
            && Math.abs(rumbleRight - appliedRumbleRight) < 0.0001f) {
            return;
        }

        try {
            activeDriver.setRumble(rumbleLeft, rumbleRight);
            appliedRumbleLeft = rumbleLeft;
            appliedRumbleRight = rumbleRight;
            rumbleApplied = true;
        } catch (Exception | LinkageError e) {
            FuzziControls.LOG.debug("[FuzziControls] Could not apply controller rumble: {}", e.toString());
        }
    }

    private static float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    /** Returns true if an active, connected driver is in use. */
    public boolean isActive() {
        return activeDriver != null && lastConnected;
    }

    /** Registers a listener for connect/disconnect events. */
    public void addConnectionListener(ConnectionListener listener) {
        if (listener != null) connectionListeners.add(listener);
    }

    /** Removes a previously registered connection listener. */
    public void removeConnectionListener(ConnectionListener listener) {
        if (listener != null) connectionListeners.remove(listener);
    }

    /** Closes and releases the active driver. Called on mod shutdown. */
    public void shutdown() {
        ControllerConnection previous = snapshotConnection(activeDriver);
        boolean previousAnnounced = !connectedNotificationPending;
        stopRumble();
        clearTriggerEffects();
        safeClose(activeDriver);
        activeDriver = null;
        lastState = ControllerState.empty();
        lastConnected = false;
        connectedNotificationPending = false;
        if (previousAnnounced) notifyDisconnected(previous);
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

    private void notifyConnectedIfPending() {
        if (!connectedNotificationPending || activeDriver == null) return;
        // A HID handle can open successfully and then fail its first read (a Bluetooth controller
        // that is still disappearing is the usual case). Wait for a real report before announcing
        // the connection; otherwise that stale handle produces a spurious connect/disconnect pair.
        if (!activeDriver.isConnectionConfirmed()) return;
        connectedNotificationPending = false;
        notifyConnected(snapshotConnection(activeDriver));
    }

    private static ControllerConnection snapshotConnection(IControllerDriver driver) {
        if (driver == null) return null;

        String driverName = safeName(driver::getDriverName, "Controller");
        String deviceName = safeName(driver::getDeviceName, driverName);
        String transportName = safeName(driver::getTransportName, "");
        return new ControllerConnection(driverName, deviceName, transportName);
    }

    private static String safeName(Supplier<String> getter, String fallback) {
        try {
            String value = getter.get();
            return value != null ? value : fallback;
        } catch (Exception | LinkageError e) {
            return fallback;
        }
    }

    private void notifyConnected(ControllerConnection connection) {
        if (connection == null) return;
        for (ConnectionListener listener : connectionListeners) {
            try {
                listener.onControllerConnected(connection);
            } catch (Exception | LinkageError e) {
                FuzziControls.LOG.warn("[FuzziControls] Connection listener failed: {}", e.toString());
            }
        }
    }

    private void notifyDisconnected(ControllerConnection connection) {
        if (connection == null) return;
        for (ConnectionListener listener : connectionListeners) {
            try {
                listener.onControllerDisconnected(connection);
            } catch (Exception | LinkageError e) {
                FuzziControls.LOG.warn("[FuzziControls] Connection listener failed: {}", e.toString());
            }
        }
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
