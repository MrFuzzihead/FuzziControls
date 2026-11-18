package com.mrfuzzihead.fuzzicontrols.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.mrfuzzihead.fuzzicontrols.Config;

/**
 * Unit tests for {@link ControllerManager}'s driver selection, polling and hot-plug state machine.
 *
 * <p>
 * The manager previously had no coverage at all, even though it owns the failure modes that make a
 * controller "just not work": driver preference order, a driver that disconnects, a poll that throws,
 * and the reconnect back-off. Drivers are supplied through {@link ControllerManager.DriverFactory} so
 * no hardware or native library is involved.
 */
public class ControllerManagerTest {

    /** Scriptable driver: records calls and returns whatever the test sets. */
    private static final class FakeDriver implements IControllerDriver {

        private final String name;

        private boolean connected;

        private boolean closed;

        private boolean throwOnPoll;

        private int pollCount;

        private ControllerState nextState = ControllerState.empty();

        private String deviceName;

        private String transportName = "";

        private boolean connectionConfirmed = true;

        private float rumbleLeft;

        private float rumbleRight;

        FakeDriver(String name, boolean connected) {
            this.name = name;
            this.connected = connected;
            this.deviceName = name;
        }

        FakeDriver withDevice(String deviceName, String transportName) {
            this.deviceName = deviceName;
            this.transportName = transportName;
            return this;
        }

        @Override
        public boolean isConnected() {
            return connected;
        }

        @Override
        public ControllerState poll(float deadZone, float triggerThreshold) {
            pollCount++;
            if (throwOnPoll) throw new IllegalStateException("boom");
            return nextState;
        }

        @Override
        public void setRumble(float left, float right) {
            rumbleLeft = left;
            rumbleRight = right;
        }

        @Override
        public void close() {
            closed = true;
        }

        @Override
        public String getDriverName() {
            return name;
        }

        @Override
        public String getDeviceName() {
            return deviceName;
        }

        @Override
        public String getTransportName() {
            return transportName;
        }

        @Override
        public boolean isConnectionConfirmed() {
            return connectionConfirmed;
        }
    }

    /** Records connection events as compact strings so tests can assert their order. */
    private static final class RecordingListener implements ControllerManager.ConnectionListener {

        private final List<String> events = new ArrayList<>();

        @Override
        public void onControllerConnected(ControllerConnection connection) {
            events.add("+" + connection.getDeviceName() + "/" + connection.getTransportName());
        }

        @Override
        public void onControllerDisconnected(ControllerConnection connection) {
            events.add("-" + connection.getDeviceName() + "/" + connection.getTransportName());
        }
    }

    /** Factory that hands out pre-scripted drivers and records what was asked for. */
    private static final class FakeFactory implements ControllerManager.DriverFactory {

        private final List<FakeDriver> dualSense = new ArrayList<>();

        private final List<FakeDriver> xInput = new ArrayList<>();

        private int dualSenseRequests;

        private int xInputRequests;

        private int lastSlot = -1;

        FakeFactory queueDualSense(FakeDriver... drivers) {
            Collections.addAll(dualSense, drivers);
            return this;
        }

        FakeFactory queueXInput(FakeDriver... drivers) {
            Collections.addAll(xInput, drivers);
            return this;
        }

        @Override
        public IControllerDriver createDualSense() {
            dualSenseRequests++;
            return dualSense.isEmpty() ? new FakeDriver("DualSense (HID)", false) : dualSense.remove(0);
        }

        @Override
        public IControllerDriver createXInput(int slot) {
            xInputRequests++;
            lastSlot = slot;
            return xInput.isEmpty() ? new FakeDriver("XInput", false) : xInput.remove(0);
        }
    }

    private FakeFactory factory;

    private ControllerManager manager;

    private String originalDriver;

    private int originalSlot;

    private boolean originalEnabled;

    private boolean originalRumbleEnabled;

    private float originalRumbleIntensity;

    @Before
    public void setUp() {
        originalDriver = Config.controllerDriver;
        originalSlot = Config.xInputControllerSlot;
        originalEnabled = Config.controllerEnabled;
        originalRumbleEnabled = Config.rumbleEnabled;
        originalRumbleIntensity = Config.rumbleIntensity;
        Config.controllerDriver = "auto";
        Config.xInputControllerSlot = 0;
        Config.controllerEnabled = true;
        Config.rumbleEnabled = true;
        Config.rumbleIntensity = 1f;
        factory = new FakeFactory();
        manager = new ControllerManager(factory);
    }

    /** Restores the global config statics this test class has to touch. */
    @After
    public void restoreConfig() {
        Config.controllerDriver = originalDriver;
        Config.xInputControllerSlot = originalSlot;
        Config.controllerEnabled = originalEnabled;
        Config.rumbleEnabled = originalRumbleEnabled;
        Config.rumbleIntensity = originalRumbleIntensity;
    }

    // -------------------------------------------------------------------------
    // Rumble scheduling
    // -------------------------------------------------------------------------

    @Test
    public void pulseRumbleAppliesTheScaledStrengthAndStopsAfterItsDuration() {
        FakeDriver ds = new FakeDriver("DualSense (HID)", true);
        factory.queueDualSense(ds);
        manager.init();
        Config.rumbleIntensity = 0.5f;

        manager.pulseRumble(1f, 0.5f, 2);

        assertEquals(0.5f, ds.rumbleLeft, 0.0001f);
        assertEquals(0.25f, ds.rumbleRight, 0.0001f);

        manager.tick();
        assertEquals("The pulse is still active on its first tick", 0.5f, ds.rumbleLeft, 0.0001f);

        manager.tick();
        assertEquals("The pulse must stop when its duration expires", 0f, ds.rumbleLeft, 0.0001f);
        assertEquals(0f, ds.rumbleRight, 0.0001f);
    }

    @Test
    public void weakerPulseDoesNotReplaceOrExtendAStrongerPulse() {
        FakeDriver ds = new FakeDriver("DualSense (HID)", true);
        factory.queueDualSense(ds);
        manager.init();

        manager.pulseRumble(0.9f, 0.7f, 8);
        manager.pulseRumble(0.2f, 0.1f, 8);

        assertEquals(0.9f, ds.rumbleLeft, 0.0001f);
        assertEquals(0.7f, ds.rumbleRight, 0.0001f);
    }

    @Test
    public void disablingRumbleStopsTheActivePulse() {
        FakeDriver ds = new FakeDriver("DualSense (HID)", true);
        factory.queueDualSense(ds);
        manager.init();

        manager.pulseRumble(1f, 1f, 8);
        Config.rumbleEnabled = false;
        manager.tick();

        assertEquals(0f, ds.rumbleLeft, 0.0001f);
        assertEquals(0f, ds.rumbleRight, 0.0001f);
    }

    @Test
    public void disconnectedDriverStopsRumbleBeforeClose() {
        FakeDriver ds = new FakeDriver("DualSense (HID)", true);
        factory.queueDualSense(ds);
        manager.init();

        manager.pulseRumble(1f, 1f, 8);
        ds.connected = false;
        manager.tick();

        assertEquals(0f, ds.rumbleLeft, 0.0001f);
        assertTrue(ds.closed);
    }

    // -------------------------------------------------------------------------
    // Driver selection
    // -------------------------------------------------------------------------

    @Test
    public void initPrefersDualSense() {
        FakeDriver ds = new FakeDriver("DualSense (HID)", true);
        FakeDriver xi = new FakeDriver("XInput (slot 0)", true);
        factory.queueDualSense(ds)
            .queueXInput(xi);

        manager.init();

        assertTrue(manager.isActive());
        assertEquals("DualSense (HID)", manager.getActiveDriverName());
        assertFalse("XInput must not be opened when DualSense is present", xi.closed);
        assertEquals("XInput must not even be created", 0, factory.xInputRequests);
    }

    @Test
    public void initFallsBackToXInputAndClosesTheDualSenseProbe() {
        FakeDriver ds = new FakeDriver("DualSense (HID)", false);
        FakeDriver xi = new FakeDriver("XInput (slot 0)", true);
        factory.queueDualSense(ds)
            .queueXInput(xi);

        manager.init();

        assertTrue(manager.isActive());
        assertEquals("XInput (slot 0)", manager.getActiveDriverName());
        assertTrue("The failed DualSense probe must be closed", ds.closed);
    }

    @Test
    public void initWithNothingConnectedIsInactive() {
        FakeDriver ds = new FakeDriver("DualSense (HID)", false);
        FakeDriver xi = new FakeDriver("XInput", false);
        factory.queueDualSense(ds)
            .queueXInput(xi);

        manager.init();

        assertFalse(manager.isActive());
        assertEquals("None", manager.getActiveDriverName());
        assertEquals("", manager.getActiveDeviceName());
        assertEquals("", manager.getActiveTransportName());
        assertTrue(ds.closed);
        assertTrue(xi.closed);
    }

    @Test
    public void xinputPreferenceSkipsDualSenseEntirely() {
        Config.controllerDriver = "xinput";
        factory.queueXInput(new FakeDriver("XInput", true));

        manager.init();

        assertTrue(manager.isActive());
        assertEquals("DualSense must not be probed when the user forces XInput", 0, factory.dualSenseRequests);
    }

    @Test
    public void dualsensePreferenceSkipsXInputEntirely() {
        Config.controllerDriver = "dualsense";
        factory.queueDualSense(new FakeDriver("DualSense", false));

        manager.init();

        assertFalse(manager.isActive());
        assertEquals("XInput must not be probed when the user forces DualSense", 0, factory.xInputRequests);
    }

    @Test
    public void initPassesTheConfiguredXInputSlot() {
        Config.xInputControllerSlot = 3;
        factory.queueXInput(new FakeDriver("XInput", true));

        manager.init();

        assertEquals(3, factory.lastSlot);
    }

    @Test
    public void noneDisablesAllProbing() {
        Config.controllerDriver = "none";

        manager.init();
        for (int tick = 0; tick < 200; tick++) {
            manager.tick();
        }

        assertFalse(manager.isActive());
        assertEquals("None", manager.getActiveDriverName());
        assertEquals("driver=none must never create a DualSense probe", 0, factory.dualSenseRequests);
        assertEquals("driver=none must never create an XInput probe", 0, factory.xInputRequests);
    }

    @Test
    public void masterDisableStopsAllProbingEvenWithADriverSelected() {
        Config.controllerDriver = "auto";
        Config.controllerEnabled = false;

        manager.init();
        for (int tick = 0; tick < 200; tick++) {
            manager.tick();
        }

        assertFalse(manager.isActive());
        assertEquals(0, factory.dualSenseRequests);
        assertEquals(0, factory.xInputRequests);
    }

    @Test
    public void masterDisableClosesAnActiveDriver() {
        FakeDriver driver = new FakeDriver("DualSense", true);
        factory.queueDualSense(driver);
        manager.init();
        assertTrue(manager.isActive());

        Config.controllerEnabled = false;
        manager.init();

        assertFalse(manager.isActive());
        assertTrue("Disabling must release the active handle", driver.closed);
    }

    @Test
    public void switchingToNoneClosesTheActiveDriver() {
        FakeDriver driver = new FakeDriver("DualSense", true);
        factory.queueDualSense(driver);
        manager.init();
        assertTrue(manager.isActive());

        Config.controllerDriver = "none";
        manager.init();

        assertFalse(manager.isActive());
        assertEquals("None", manager.getActiveDriverName());
        assertTrue("Disabling must release the active handle", driver.closed);
    }

    @Test
    public void unknownDriverDisablesProbing() {
        Config.controllerDriver = "dual-sense"; // typo: must not silently probe forever

        manager.init();
        for (int tick = 0; tick < 200; tick++) {
            manager.tick();
        }

        assertFalse(manager.isActive());
        assertEquals(0, factory.dualSenseRequests);
        assertEquals(0, factory.xInputRequests);
    }

    @Test
    public void initFailureDoesNotProbeAgainOnTheNextTicks() {
        factory.queueDualSense(new FakeDriver("DualSense", false));
        factory.queueXInput(new FakeDriver("XInput", false));

        manager.init();
        int requestsAfterInit = factory.dualSenseRequests + factory.xInputRequests;
        assertEquals(2, requestsAfterInit);

        for (int tick = 0; tick < 10; tick++) {
            manager.tick();
        }

        assertEquals(
            "init() already probed; the next probe must wait for the reconnect interval",
            requestsAfterInit,
            factory.dualSenseRequests + factory.xInputRequests);
    }

    @Test
    public void reinitClosesThePreviousDriver() {
        FakeDriver first = new FakeDriver("DualSense", true);
        factory.queueDualSense(first, new FakeDriver("DualSense", true));

        manager.init();
        manager.init();

        assertTrue("Re-initialising must release the previous driver", first.closed);
        assertTrue(manager.isActive());
    }

    @Test
    public void factoryReturningNullIsTolerated() {
        ControllerManager nullManager = new ControllerManager(new ControllerManager.DriverFactory() {

            @Override
            public IControllerDriver createDualSense() {
                return null;
            }

            @Override
            public IControllerDriver createXInput(int slot) {
                return null;
            }
        });

        nullManager.init();

        assertFalse(nullManager.isActive());
    }

    // -------------------------------------------------------------------------
    // Polling
    // -------------------------------------------------------------------------

    @Test
    public void tickPollsTheActiveDriver() {
        FakeDriver driver = new FakeDriver("DualSense", true);
        ControllerState expected = new ControllerState(0.5f, 0f, 0f, 0f, 0f, 0f, Collections.emptySet());
        driver.nextState = expected;
        factory.queueDualSense(driver);

        manager.init();
        manager.tick();

        assertEquals(1, driver.pollCount);
        assertSame(expected, manager.getState());
    }

    @Test
    public void tickWithoutADriverKeepsTheStateEmpty() {
        factory.queueDualSense(new FakeDriver("DualSense", false));
        manager.init();

        manager.tick();

        assertFalse(manager.isActive());
        assertEquals(ControllerState.empty(), manager.getState());
    }

    @Test
    public void pollFailureClosesTheDriverAndClearsState() {
        FakeDriver driver = new FakeDriver("DualSense", true);
        driver.throwOnPoll = true;
        factory.queueDualSense(driver);

        manager.init();
        manager.tick();

        assertTrue("A poll error must release the driver", driver.closed);
        assertFalse(manager.isActive());
        assertEquals(ControllerState.empty(), manager.getState());
    }

    // -------------------------------------------------------------------------
    // Hot-plug
    // -------------------------------------------------------------------------

    @Test
    public void disconnectIsDetectedAndDriverReleased() {
        FakeDriver driver = new FakeDriver("DualSense", true);
        factory.queueDualSense(driver);

        manager.init();
        driver.connected = false;
        manager.tick();

        assertTrue("A disconnected driver must be closed", driver.closed);
        assertEquals(ControllerState.empty(), manager.getState());
    }

    @Test
    public void disconnectTriggersImmediateReconnectAttempt() {
        FakeDriver first = new FakeDriver("DualSense", true);
        FakeDriver second = new FakeDriver("DualSense", true);
        factory.queueDualSense(first, second);

        manager.init();
        first.connected = false;
        manager.tick();

        assertEquals("The reconnect attempt happens on the same tick", 2, factory.dualSenseRequests);
        assertTrue("The replacement driver is adopted", manager.isActive());
    }

    @Test
    public void failedReconnectWaitsForTheFocusedInterval() {
        FakeDriver first = new FakeDriver("DualSense", true);
        factory.queueDualSense(first);

        manager.init();
        first.connected = false;

        // Disconnect tick: one attempt (the factory then keeps returning disconnected probes).
        manager.tick();
        int attemptsAfterDisconnect = factory.dualSenseRequests;
        assertEquals(2, attemptsAfterDisconnect);

        // The next ticks must not hammer the driver factory.
        for (int tick = 0; tick < 10; tick++) {
            manager.tick();
        }
        assertEquals("Reconnect attempts must be rate-limited", attemptsAfterDisconnect, factory.dualSenseRequests);
    }

    @Test
    public void reconnectSucceedsOnceADeviceAppears() {
        FakeDriver first = new FakeDriver("DualSense", true);
        factory.queueDualSense(first);
        manager.init();
        first.connected = false;

        // First tick: detect disconnect and fail to reconnect (factory has no more drivers).
        manager.tick();
        assertFalse(manager.isActive());

        // Wait out the back-off, then make a device available.
        FakeDriver late = new FakeDriver("DualSense", true);
        factory.queueDualSense(late);
        for (int tick = 0; tick < 100 && !manager.isActive(); tick++) {
            manager.tick();
        }

        assertTrue("A device appearing later must be picked up", manager.isActive());
    }

    // -------------------------------------------------------------------------
    // Connection listeners
    // -------------------------------------------------------------------------

    @Test
    public void connectEventWaitsForTheFirstPollSoTransportIsKnown() {
        FakeDriver driver = new FakeDriver("DualSense (HID)", true).withDevice("DualSense Edge", "Bluetooth");
        factory.queueDualSense(driver);
        RecordingListener listener = new RecordingListener();
        manager.addConnectionListener(listener);

        manager.init();
        assertTrue("a selected driver is not announced before its first report", listener.events.isEmpty());

        manager.tick();

        assertEquals(Collections.singletonList("+DualSense Edge/Bluetooth"), listener.events);
    }

    @Test
    public void connectEventWaitsForDriverConfirmation() {
        FakeDriver driver = new FakeDriver("DualSense (HID)", true).withDevice("DualSense", "USB");
        driver.connectionConfirmed = false;
        factory.queueDualSense(driver);
        RecordingListener listener = new RecordingListener();
        manager.addConnectionListener(listener);
        manager.init();

        manager.tick();
        assertTrue("an opened but unconfirmed handle must not announce a connection", listener.events.isEmpty());

        driver.connectionConfirmed = true;
        manager.tick();
        assertEquals(Collections.singletonList("+DualSense/USB"), listener.events);
    }

    @Test
    public void unconfirmedStaleHandleDoesNotEmitAConnectDisconnectPair() {
        FakeDriver stale = new FakeDriver("DualSense (HID)", true).withDevice("DualSense", "USB");
        stale.connectionConfirmed = false;
        factory.queueDualSense(stale);
        RecordingListener listener = new RecordingListener();
        manager.addConnectionListener(listener);
        manager.init();

        // First poll opens the stale handle but produces no valid report, then the next tick sees
        // the handle disappear. Neither transition was ever announced to the player.
        manager.tick();
        stale.connected = false;
        manager.tick();

        assertTrue(listener.events.isEmpty());
    }

    @Test
    public void disconnectAndReconnectInTheSameTickEmitsBothEvents() {
        FakeDriver first = new FakeDriver("DualSense (HID)", true).withDevice("DualSense", "USB");
        FakeDriver second = new FakeDriver("DualSense (HID)", true).withDevice("DualSense", "USB");
        factory.queueDualSense(first, second);
        RecordingListener listener = new RecordingListener();
        manager.addConnectionListener(listener);
        manager.init();
        manager.tick();
        listener.events.clear();

        first.connected = false;
        manager.tick();

        assertEquals(Arrays.asList("-DualSense/USB", "+DualSense/USB"), listener.events);
    }

    @Test
    public void pollFailureEmitsDisconnected() {
        FakeDriver driver = new FakeDriver("DualSense (HID)", true).withDevice("DualSense", "USB");
        factory.queueDualSense(driver);
        RecordingListener listener = new RecordingListener();
        manager.addConnectionListener(listener);
        manager.init();
        manager.tick();
        listener.events.clear();

        driver.throwOnPoll = true;
        manager.tick();

        assertEquals(Collections.singletonList("-DualSense/USB"), listener.events);
    }

    @Test
    public void reinitialisingTheSameDeviceDoesNotEmitConnectionEvents() {
        FakeDriver first = new FakeDriver("DualSense (HID)", true).withDevice("DualSense", "USB");
        FakeDriver second = new FakeDriver("DualSense (HID)", true).withDevice("DualSense", "USB");
        factory.queueDualSense(first, second);
        RecordingListener listener = new RecordingListener();
        manager.addConnectionListener(listener);
        manager.init();
        manager.tick();
        listener.events.clear();

        manager.init();
        manager.tick();

        assertTrue("a config reload of the same device must not produce a toast pair", listener.events.isEmpty());
    }

    @Test
    public void switchingDeviceEmitsDisconnectThenConnect() {
        FakeDriver first = new FakeDriver("DualSense (HID)", true).withDevice("DualSense", "USB");
        FakeDriver second = new FakeDriver("DualSense (HID)", true).withDevice("DualSense Edge", "Bluetooth");
        factory.queueDualSense(first, second);
        RecordingListener listener = new RecordingListener();
        manager.addConnectionListener(listener);
        manager.init();
        manager.tick();
        listener.events.clear();

        manager.init();
        manager.tick();

        assertEquals(Arrays.asList("-DualSense/USB", "+DualSense Edge/Bluetooth"), listener.events);
    }

    @Test
    public void aDriverThatFailsBeforeItsFirstPollDoesNotEmitPhantomDisconnect() {
        FakeDriver driver = new FakeDriver("DualSense (HID)", true).withDevice("DualSense", "USB");
        factory.queueDualSense(driver);
        RecordingListener listener = new RecordingListener();
        manager.addConnectionListener(listener);
        manager.init();

        driver.throwOnPoll = true;
        manager.tick();

        assertTrue("no connect toast was shown, so there must be no disconnect toast", listener.events.isEmpty());
    }

    @Test
    public void aDriverThatDisappearsBeforeItsFirstPollDoesNotEmitPhantomDisconnect() {
        FakeDriver driver = new FakeDriver("DualSense (HID)", true).withDevice("DualSense", "USB");
        factory.queueDualSense(driver);
        RecordingListener listener = new RecordingListener();
        manager.addConnectionListener(listener);
        manager.init();

        driver.connected = false;
        manager.tick();

        assertTrue("no connect toast was shown, so there must be no disconnect toast", listener.events.isEmpty());
    }

    @Test
    public void removedListenerStopsReceivingEvents() {
        FakeDriver driver = new FakeDriver("DualSense (HID)", true).withDevice("DualSense", "USB");
        factory.queueDualSense(driver);
        RecordingListener listener = new RecordingListener();
        manager.addConnectionListener(listener);
        manager.removeConnectionListener(listener);

        manager.init();
        manager.tick();

        assertTrue(listener.events.isEmpty());
    }

    @Test
    public void pollFreshCanCompleteAPendingConnectNotification() {
        FakeDriver driver = new FakeDriver("DualSense (HID)", true).withDevice("DualSense", "USB");
        factory.queueDualSense(driver);
        manager.init();
        RecordingListener listener = new RecordingListener();
        manager.addConnectionListener(listener);

        manager.pollFresh();

        assertEquals(Collections.singletonList("+DualSense/USB"), listener.events);
    }

    // -------------------------------------------------------------------------
    // pollFresh / shutdown
    // -------------------------------------------------------------------------

    @Test
    public void pollFreshFailureClosesTheDriverAndClearsState() {
        FakeDriver driver = new FakeDriver("DualSense", true);
        factory.queueDualSense(driver);
        manager.init();
        driver.throwOnPoll = true;

        assertEquals(ControllerState.empty(), manager.pollFresh());

        assertTrue("A render-frame poll error must release the handle", driver.closed);
        assertFalse(manager.isActive());
        assertEquals(ControllerState.empty(), manager.getState());
    }

    @Test
    public void pollFreshPollsWithoutReconnectLogic() {
        FakeDriver driver = new FakeDriver("DualSense", true);
        factory.queueDualSense(driver);
        manager.init();

        manager.pollFresh();

        assertEquals(1, driver.pollCount);
        assertEquals("pollFresh must not create drivers", 1, factory.dualSenseRequests);
    }

    @Test
    public void pollFreshWithoutADriverReturnsTheEmptyState() {
        factory.queueDualSense(new FakeDriver("DualSense", false));
        manager.init();

        assertEquals(ControllerState.empty(), manager.pollFresh());
    }

    @Test
    public void singletonHasADriverFactory() {
        // Guards static-initialisation order: DEFAULT_FACTORY must be assigned before INSTANCE is
        // constructed. Getting it wrong crashes the client at startup (driverFactory == null), which
        // the injected-factory tests above cannot catch.
        ControllerManager singleton = ControllerManager.getInstance();
        assertSame(singleton, ControllerManager.getInstance());
        assertNotNull("The singleton must have a driver factory", singleton.getDriverFactoryForTest());
        assertFalse(singleton.isActive());
        assertEquals("None", singleton.getActiveDriverName());
    }

    @Test
    public void focusedProbingRepeatsEverySecond() {
        manager.init(); // init() performs one probe
        int afterInit = factory.dualSenseRequests + factory.xInputRequests;

        for (int tick = 0; tick < 20; tick++) {
            manager.tick();
        }

        assertTrue(
            "A focused window must probe again after one second",
            factory.dualSenseRequests + factory.xInputRequests > afterInit);
    }

    @Test
    public void unfocusedProbingIsThrottledToThirtySeconds() {
        manager.setFocused(false);
        manager.init(); // init() performs one probe
        int afterInit = factory.dualSenseRequests + factory.xInputRequests;

        for (int tick = 0; tick < 200; tick++) {
            manager.tick();
        }

        assertEquals(
            "200 ticks (10 s) must not reach the 30 s unfocused probe",
            afterInit,
            factory.dualSenseRequests + factory.xInputRequests);
    }

    @Test
    public void focusGainProbesImmediately() {
        manager.setFocused(false);
        manager.init();
        int afterInit = factory.dualSenseRequests + factory.xInputRequests;

        manager.setFocused(true);
        manager.tick();

        assertTrue(
            "Returning focus must probe on the next tick",
            factory.dualSenseRequests + factory.xInputRequests > afterInit);
    }

    @Test
    public void shutdownClosesTheDriver() {
        FakeDriver driver = new FakeDriver("DualSense", true);
        factory.queueDualSense(driver);
        manager.init();

        manager.shutdown();

        assertTrue(driver.closed);
        assertFalse(manager.isActive());
        assertEquals(ControllerState.empty(), manager.getState());
        assertEquals("None", manager.getActiveDriverName());
        assertNull(null);
    }
}
