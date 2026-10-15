package com.mrfuzzihead.fuzzicontrols.util;

import static org.junit.Assert.assertFalse;

import org.junit.Test;

/**
 * Verifies that the lwjgl3ify input-injection helpers degrade gracefully when lwjgl3ify is absent.
 *
 * <p>
 * The mod declares only a soft ordering dependency on lwjgl3ify ({@code after:lwjgl3ify}), but
 * {@code org.lwjglx.input.Mouse} and its injection members are lwjgl3ify extensions. Referencing
 * them on an install without lwjgl3ify throws {@link LinkageError} ({@code NoClassDefFoundError},
 * {@code NoSuchMethodError}, …), which is an {@code Error} and so was <em>not</em> caught by the
 * previous {@code catch (Exception)} — the documented fallback could never engage and the error
 * escaped into the client tick / render loop.
 *
 * <p>
 * The unit test JVM cannot use lwjgl3ify's input API (the class cannot be initialised without a
 * live display), which is the same failure mode as an install without lwjgl3ify, so calling the
 * helper here exercises the hardening.
 */
public class GuiMouseHelperLinkageTest {

    private static boolean classPresent(String className) {
        try {
            Class.forName(className);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    @Test
    public void wheelInjectionNeverThrowsWhenLwjgl3ifyIsAbsent() {
        assertFalse(
            "Test JVM is expected to have no usable lwjgl3ify input API, so the LinkageError path runs",
            classPresent("org.lwjglx.input.Mouse"));

        // Previously this threw NoClassDefFoundError out of the render tick.
        GuiMouseHelper.injectWheelEvent(1.0);
        GuiMouseHelper.injectWheelEvent(-1.0);
    }
}
