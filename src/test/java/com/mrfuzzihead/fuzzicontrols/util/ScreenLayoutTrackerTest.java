package com.mrfuzzihead.fuzzicontrols.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Unit tests for {@link ScreenLayoutTracker}.
 *
 * <p>
 * The counter is the signal {@link GuiFocusNavigator} uses to discard cached slot geometry after a
 * screen is re-initialised (window resize, fullscreen toggle, GUI scale change) — the case where a
 * resize left the focus highlight and synthetic clicks pointing at the pre-resize layout.
 */
public class ScreenLayoutTrackerTest {

    @Test
    public void generationIncreasesOnEveryBump() {
        int before = ScreenLayoutTracker.getGeneration();

        ScreenLayoutTracker.bumpGeneration();
        assertEquals(before + 1, ScreenLayoutTracker.getGeneration());

        ScreenLayoutTracker.bumpGeneration();
        assertEquals(before + 2, ScreenLayoutTracker.getGeneration());
    }

    @Test
    public void generationIsMonotonic() {
        int previous = ScreenLayoutTracker.getGeneration();
        for (int i = 0; i < 5; i++) {
            ScreenLayoutTracker.bumpGeneration();
            int current = ScreenLayoutTracker.getGeneration();
            assertTrue("Generation must never go backwards", current > previous);
            previous = current;
        }
    }

    @Test
    public void generationOnlyChangesWhenBumped() {
        int generation = ScreenLayoutTracker.getGeneration();
        assertEquals(generation, ScreenLayoutTracker.getGeneration());
        assertEquals(generation, ScreenLayoutTracker.getGeneration());
    }
}
