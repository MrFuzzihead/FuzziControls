package com.mrfuzzihead.fuzzicontrols.util;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Verifies that {@link KeyboardHelper} degrades gracefully when lwjgl3ify is unavailable.
 *
 * <p>
 * A missing {@code org.lwjglx} class is a {@link LinkageError}, not an {@link Exception}. The old
 * code only caught {@code Exception}, so the shift-click path (and the sneaking keyboard-takeover
 * check) could throw {@code NoClassDefFoundError} into the client tick. The unit test JVM has no
 * usable lwjgl3ify input API, which is the same failure mode as an install without lwjgl3ify.
 * </p>
 */
public class KeyboardHelperLinkageTest {

    private static boolean classPresent(String className) {
        try {
            Class.forName(className);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    @Test
    public void withKeyHeldRunsTheActionWhenLwjgl3ifyIsAbsent() {
        assertFalse(
            "Test JVM is expected to have no usable lwjgl3ify input API, so the LinkageError path runs",
            classPresent("org.lwjglx.input.Keyboard"));

        final boolean[] ran = { false };
        KeyboardHelper.withKeyHeld(42, () -> ran[0] = true);

        assertTrue("The action must run even when the key patch is unavailable", ran[0]);
    }

    @Test
    public void isPhysicallyDownNeverThrowsWhenLwjgl3ifyIsAbsent() {
        assertFalse(KeyboardHelper.isPhysicallyDown(42));
    }
}
