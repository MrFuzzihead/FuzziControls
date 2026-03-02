package com.mrfuzzihead.fuzzicontrols.controller;

import static org.junit.Assert.*;

import java.util.Collections;

import org.junit.Test;

/**
 * Unit tests for the DualSense driver's non-blocking read / last-state cache logic.
 *
 * <p>
 * Because the real {@link DualSenseDriver} requires physical hardware and HID4Java, these
 * tests exercise the {@link ControllerState} caching contract in isolation using a
 * minimal stub that mirrors the cache behaviour added to {@link DualSenseDriver}.
 */
public class DualSenseDriverCacheTest {

    /**
     * Simulates the DualSense poll() caching contract:
     * <ul>
     * <li>When a valid read is returned, it is stored as {@code lastState} and returned.</li>
     * <li>When a subsequent read returns fewer than 10 bytes (no new report available from
     * a non-blocking read), the cached {@code lastState} is returned instead of
     * {@link ControllerState#empty()}.</li>
     * </ul>
     */
    private static class StubDriver {

        private ControllerState lastState = null;

        /**
         * Mirrors the cache logic inside {@code DualSenseDriver.poll()}.
         *
         * @param simulatedBytesRead number of bytes returned by the simulated HID read
         * @param stateToCache       the state that would be built if the read succeeded
         */
        ControllerState poll(int simulatedBytesRead, ControllerState stateToCache) {
            if (simulatedBytesRead < 10) {
                return lastState != null ? lastState : ControllerState.empty();
            }
            lastState = stateToCache;
            return lastState;
        }
    }

    @Test
    public void firstPoll_noReport_returnsEmpty() {
        StubDriver driver = new StubDriver();
        ControllerState result = driver.poll(0, null);
        assertEquals("No cached state — should return empty()", ControllerState.empty(), result);
    }

    @Test
    public void firstPoll_validReport_returnsAndCachesState() {
        StubDriver driver = new StubDriver();
        ControllerState expected = new ControllerState(0.5f, -0.3f, 0f, 0f, 0f, 0f, Collections.emptySet());
        ControllerState result = driver.poll(64, expected);
        assertSame("Valid read should return and cache the new state", expected, result);
    }

    @Test
    public void subsequentPoll_noReport_returnsLastValidState() {
        StubDriver driver = new StubDriver();
        ControllerState expected = new ControllerState(0.5f, -0.3f, 0f, 0f, 0f, 0f, Collections.emptySet());

        // First poll succeeds and caches the state.
        driver.poll(64, expected);

        // Second poll — non-blocking read returns 0 bytes (no new report).
        ControllerState result = driver.poll(0, null);
        assertSame("Non-blocking miss should return the previously cached state", expected, result);
    }

    @Test
    public void subsequentPoll_validReport_updatesCache() {
        StubDriver driver = new StubDriver();
        ControllerState first = new ControllerState(0.5f, 0f, 0f, 0f, 0f, 0f, Collections.emptySet());
        ControllerState second = new ControllerState(-0.5f, 0f, 0f, 0f, 0f, 0f, Collections.emptySet());

        driver.poll(64, first);
        ControllerState result = driver.poll(64, second);

        assertSame("A new valid read should update and return the new cached state", second, result);
    }

    @Test
    public void multipleMisses_returnsSameCachedState() {
        StubDriver driver = new StubDriver();
        ControllerState cached = new ControllerState(0f, 0f, 0.8f, 0f, 0f, 0f, Collections.emptySet());
        driver.poll(64, cached);

        // Simulate several non-blocking misses in a row (e.g. DualSense polled at 60 Hz but
        // reports at 250 Hz — most polls between reports return 0 bytes).
        for (int i = 0; i < 10; i++) {
            ControllerState result = driver.poll(0, null);
            assertSame("Every miss should return the same cached state", cached, result);
        }
    }

    @Test
    public void emptyState_hasZeroAxesAndNoButtons() {
        ControllerState empty = ControllerState.empty();
        assertEquals(0f, empty.leftStickX(), 0f);
        assertEquals(0f, empty.leftStickY(), 0f);
        assertEquals(0f, empty.rightStickX(), 0f);
        assertEquals(0f, empty.rightStickY(), 0f);
        assertEquals(0f, empty.leftTrigger(), 0f);
        assertEquals(0f, empty.rightTrigger(), 0f);
        assertTrue(
            "Empty state should have no pressed buttons",
            empty.pressedButtons()
                .isEmpty());
    }
}
