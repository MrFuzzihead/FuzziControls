package com.mrfuzzihead.fuzzicontrols.controller;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Tests the hold/tap behaviour of {@link GuiShiftClick}.
 *
 * <p>
 * The regression this guards: holding LT/L2 used to deliver only the first Shift+click, so moving
 * several stacks required releasing and re-pressing the trigger between each one. Holding the
 * button must now keep the mode armed for every click until release, while a tap still arms one
 * click.
 */
public class GuiShiftClickTest {

    @Test
    public void startsDisarmed() {
        GuiShiftClick shift = new GuiShiftClick();
        assertFalse(shift.isArmed());
        assertFalse(shift.isHeld());
    }

    @Test
    public void tapArmsExactlyOneClick() {
        GuiShiftClick shift = new GuiShiftClick();

        shift.update(true, false);
        assertTrue(shift.isArmed());
        assertTrue(shift.isHeld());

        shift.update(false, false);
        assertFalse(shift.isHeld());
        assertTrue("A tap leaves the next click armed", shift.isArmed());

        shift.onClickDelivered();
        assertFalse("The tap-armed click is consumed", shift.isArmed());

        shift.onClickDelivered();
        assertFalse("A second click is not a Shift+click", shift.isArmed());
    }

    @Test
    public void holdingKeepsEveryClickArmedUntilRelease() {
        GuiShiftClick shift = new GuiShiftClick();

        shift.update(true, false);

        shift.onClickDelivered();
        assertTrue("The hold stays armed after the first click", shift.isArmed());

        shift.onClickDelivered();
        assertTrue("The hold stays armed after the second click", shift.isArmed());

        shift.update(true, false);
        assertTrue("Still held, still armed", shift.isArmed());

        shift.update(false, false);
        assertFalse("Releasing the button ends a used hold", shift.isArmed());
    }

    @Test
    public void holdingWithoutAClickStillArmsOneClick() {
        GuiShiftClick shift = new GuiShiftClick();

        shift.update(true, false);
        shift.update(false, false);

        assertTrue(shift.isArmed());
        shift.onClickDelivered();
        assertFalse(shift.isArmed());
    }

    @Test
    public void aNewTapAfterAUsedHoldArmsAgain() {
        GuiShiftClick shift = new GuiShiftClick();

        shift.update(true, false);
        shift.onClickDelivered();
        shift.update(false, false);
        assertFalse(shift.isArmed());

        shift.update(true, false);
        shift.update(false, false);
        assertTrue("A fresh tap arms the next click", shift.isArmed());
    }

    @Test
    public void tappingTwiceDoesNotDisarm() {
        GuiShiftClick shift = new GuiShiftClick();

        shift.update(true, false);
        shift.update(false, false);
        assertTrue(shift.isArmed());

        shift.update(true, false);
        shift.update(false, false);
        assertTrue("A second tap must not toggle the armed state off", shift.isArmed());

        shift.onClickDelivered();
        assertFalse(shift.isArmed());
    }

    @Test
    public void keyboardShiftTakesPriority() {
        GuiShiftClick shift = new GuiShiftClick();

        shift.update(true, true); // LT held while the keyboard Shift is down
        assertFalse("Keyboard Shift suppresses the controller mode", shift.isArmed());
        assertTrue(shift.isHeld());

        shift.onClickDelivered();
        shift.update(false, true);
        assertFalse(shift.isArmed());

        shift.update(false, false);
        assertFalse("Releasing the controller after keyboard suppression does not arm", shift.isArmed());
    }

    @Test
    public void pressingKeyboardShiftWhileArmedClearsTheMode() {
        GuiShiftClick shift = new GuiShiftClick();

        shift.update(true, false);
        assertTrue(shift.isArmed());

        shift.update(true, true);
        assertFalse(shift.isArmed());
    }

    @Test
    public void resetClearsEverything() {
        GuiShiftClick shift = new GuiShiftClick();

        shift.update(true, false);
        shift.onClickDelivered();
        shift.reset();

        assertFalse(shift.isArmed());
        assertFalse(shift.isHeld());

        shift.onClickDelivered();
        assertFalse(shift.isArmed());

        shift.update(true, false);
        assertTrue("A fresh hold after reset arms again", shift.isArmed());
    }
}
