package com.mrfuzzihead.fuzzicontrols.util;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

/**
 * Unit tests for the focus-index management logic used by {@link GuiFocusNavigator}.
 *
 * <p>
 * {@link GuiFocusNavigator} depends on Minecraft classes ({@code GuiScreen}, {@code GuiButton},
 * etc.) that are not available in the unit-test classpath. This test class therefore exercises
 * the same arithmetic — wrapping index advancement, previous-wrap, clamping, and reset on
 * screen change — through a minimal {@link FocusIndexDriver} inner class that mirrors the
 * invariants without any Minecraft dependency.
 *
 * <p>
 * Integration / in-game behaviour is covered by manual play-testing according to the
 * D-pad navigation plan ({@code plan-dpadNavigation.prompt.md}).
 */
public class GuiFocusNavigatorTest {

    // -------------------------------------------------------------------------
    // Minimal focus-index driver — mirrors GuiFocusNavigator's core logic
    // -------------------------------------------------------------------------

    /**
     * Self-contained focus index tracker with the same wrapping and reset behaviour as
     * {@link GuiFocusNavigator}. Decoupled from all Minecraft APIs so it can run in the
     * unit-test JVM without a Minecraft classpath.
     */
    private static final class FocusIndexDriver {

        private int index = 0;

        private Object lastKey = null;

        /** Reset to 0 when the key object changes (mirrors screen-change detection). */
        void onKeyChanged(Object key) {
            if (key != lastKey) {
                lastKey = key;
                index = 0;
            }
        }

        void next(int size) {
            if (size == 0) return;
            index = (index + 1) % size;
        }

        void prev(int size) {
            if (size == 0) return;
            index = (index - 1 + size) % size;
        }

        int getIndex() {
            return index;
        }

        /** Returns the safe (clamped) index for a list of the given size. */
        int safeIndex(int size) {
            if (size == 0) return -1;
            return Math.min(index, size - 1);
        }
    }

    // -------------------------------------------------------------------------
    // Fixtures
    // -------------------------------------------------------------------------

    private FocusIndexDriver driver;

    @Before
    public void setUp() {
        driver = new FocusIndexDriver();
    }

    // -------------------------------------------------------------------------
    // onKeyChanged — screen-change reset
    // -------------------------------------------------------------------------

    @Test
    public void onKeyChanged_sameKey_doesNotResetIndex() {
        Object key = new Object();
        driver.onKeyChanged(key);
        driver.next(3); // index = 1
        driver.onKeyChanged(key); // same key — no reset
        assertEquals(1, driver.getIndex());
    }

    @Test
    public void onKeyChanged_differentKey_resetsIndex() {
        Object key1 = new Object();
        Object key2 = new Object();
        driver.onKeyChanged(key1);
        driver.next(3); // index = 1
        driver.onKeyChanged(key2); // new key — reset
        assertEquals(0, driver.getIndex());
    }

    @Test
    public void onKeyChanged_nullKey_doesNotThrow() {
        driver.onKeyChanged(null);
        assertEquals(0, driver.getIndex());
    }

    // -------------------------------------------------------------------------
    // next() — forward advance and wrap
    // -------------------------------------------------------------------------

    @Test
    public void next_advancesIndex() {
        driver.next(5);
        assertEquals(1, driver.getIndex());
        driver.next(5);
        assertEquals(2, driver.getIndex());
    }

    @Test
    public void next_wrapsAtEnd() {
        // list of 3: 0 → 1 → 2 → 0
        driver.next(3);
        driver.next(3);
        driver.next(3); // wraps back to 0
        assertEquals(0, driver.getIndex());
    }

    @Test
    public void next_singleElement_staysAtZero() {
        driver.next(1);
        assertEquals(0, driver.getIndex());
    }

    @Test
    public void next_emptyList_doesNotChangeIndex() {
        driver.next(0);
        assertEquals(0, driver.getIndex());
    }

    // -------------------------------------------------------------------------
    // prev() — backward advance and wrap
    // -------------------------------------------------------------------------

    @Test
    public void prev_atStartWrapsToLast() {
        // index = 0, list of 4 → should go to 3
        driver.prev(4);
        assertEquals(3, driver.getIndex());
    }

    @Test
    public void prev_movesBackward() {
        driver.next(5); // 1
        driver.next(5); // 2
        driver.prev(5); // 1
        assertEquals(1, driver.getIndex());
    }

    @Test
    public void prev_singleElement_staysAtZero() {
        driver.prev(1);
        assertEquals(0, driver.getIndex());
    }

    @Test
    public void prev_emptyList_doesNotChangeIndex() {
        driver.prev(0);
        assertEquals(0, driver.getIndex());
    }

    // -------------------------------------------------------------------------
    // safeIndex() — clamping
    // -------------------------------------------------------------------------

    @Test
    public void safeIndex_emptyList_returnsNegativeOne() {
        assertEquals(-1, driver.safeIndex(0));
    }

    @Test
    public void safeIndex_withinBounds_returnsSameIndex() {
        driver.next(5); // index = 1
        assertEquals(1, driver.safeIndex(5));
    }

    @Test
    public void safeIndex_clampsToLastElement() {
        // If the list shrinks (e.g. a screen rebuilds with fewer buttons) the index must
        // clamp to the last valid position rather than throwing ArrayIndexOutOfBoundsException.
        driver.next(5); // index = 1
        driver.next(5); // index = 2
        driver.next(5); // index = 3
        // Simulate list now having only 2 elements
        assertEquals(1, driver.safeIndex(2)); // clamped from 3 to 1
    }

    // -------------------------------------------------------------------------
    // Round-trip wrapping
    // -------------------------------------------------------------------------

    @Test
    public void roundTrip_nextThenPrev_returnsToStart() {
        driver.next(3); // 0 → 1
        driver.prev(3); // 1 → 0
        assertEquals(0, driver.getIndex());
    }

    @Test
    public void roundTrip_prevThenNext_returnsToStart() {
        driver.prev(3); // 0 → 2
        driver.next(3); // 2 → 0
        assertEquals(0, driver.getIndex());
    }
}
