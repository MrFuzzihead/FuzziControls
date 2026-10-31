package com.mrfuzzihead.fuzzicontrols.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
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

        FakeDriver(String name, boolean connected) {
            this.name = name;
            this.connected = connected;
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
        public void close() {
            closed = true;
        }

        @Override
        public String getDriverName() {
            return name;
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

    @Before
    public void setUp() {
        originalDriver = Config.controllerDriver;
        originalSlot = Config.xInputControllerSlot;
        Config.controllerDriver = "auto";
        Config.xInputControllerSlot = 0;
        factory = new FakeFactory();
        manager = new ControllerManager(factory);
    }

    /** Restores the global config statics this test class has to touch. */
    @After
    public void restoreConfig() {
        Config.controllerDriver = originalDriver;
        Config.xInputControllerSlot = originalSlot;
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
    public void failedReconnectBacksOffForTheConfiguredInterval() {
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
    // pollFresh / shutdown
    // -------------------------------------------------------------------------

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
