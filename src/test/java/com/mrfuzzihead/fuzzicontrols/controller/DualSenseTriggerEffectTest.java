package com.mrfuzzihead.fuzzicontrols.controller;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** Pins the 11-byte adaptive-trigger effect layouts used by the DualSense output report. */
public class DualSenseTriggerEffectTest {

    @Test
    public void offIsTheNeutralMode() {
        byte[] block = DualSenseTriggerEffect.off();

        assertEquals(DualSenseTriggerEffect.BLOCK_SIZE, block.length);
        assertEquals(0x05, block[0] & 0xFF);
        for (int i = 1; i < block.length; i++) {
            assertEquals(0, block[i] & 0xFF);
        }
    }

    @Test
    public void feedbackPacksActiveZonesAndStrength() {
        byte[] block = DualSenseTriggerEffect.feedback(2, 5);

        assertEquals(0x21, block[0] & 0xFF);
        assertEquals("zones 2-9", 0xFC, block[1] & 0xFF);
        assertEquals(0x03, block[2] & 0xFF);
        assertEquals(0x00, block[3] & 0xFF);
        assertEquals(0x49, block[4] & 0xFF);
        assertEquals(0x92, block[5] & 0xFF);
        assertEquals(0x24, block[6] & 0xFF);
    }

    @Test
    public void weaponStoresStartStopAndStrength() {
        byte[] block = DualSenseTriggerEffect.weapon(2, 7, 8);

        assertEquals(0x25, block[0] & 0xFF);
        assertEquals(0x84, block[1] & 0xFF);
        assertEquals(0x00, block[2] & 0xFF);
        assertEquals(7, block[3] & 0xFF);
    }

    @Test
    public void vibrationAddsFrequency() {
        byte[] block = DualSenseTriggerEffect.vibration(2, 5, 30);

        assertEquals(0x26, block[0] & 0xFF);
        assertEquals(0xFC, block[1] & 0xFF);
        assertEquals(30, block[9] & 0xFF);
    }

    @Test
    public void bowPacksStrengthAndSnapForce() {
        byte[] block = DualSenseTriggerEffect.bow(1, 6, 6, 8);

        assertEquals(0x22, block[0] & 0xFF);
        assertEquals(0x42, block[1] & 0xFF);
        assertEquals(0x00, block[2] & 0xFF);
        assertEquals(0x3D, block[3] & 0xFF);
        assertEquals(0x00, block[4] & 0xFF);
    }

    @Test
    public void machinePacksAmplitudesFrequencyAndPeriod() {
        byte[] block = DualSenseTriggerEffect.machine(0, 9, 3, 7, 30, 5);

        assertEquals(0x27, block[0] & 0xFF);
        assertEquals(0x01, block[1] & 0xFF);
        assertEquals(0x02, block[2] & 0xFF);
        assertEquals(0x3B, block[3] & 0xFF);
        assertEquals(30, block[4] & 0xFF);
        assertEquals(5, block[5] & 0xFF);
    }

    @Test
    public void presetFactoryClampsStrengthAndMapsOff() {
        assertArrayEquals(
            DualSenseTriggerEffect.feedback(2, 8),
            DualSenseTriggerEffect.from(ControllerTriggerEffect.FEEDBACK, 99));
        assertArrayEquals(DualSenseTriggerEffect.off(), DualSenseTriggerEffect.from(ControllerTriggerEffect.OFF, 8));
        assertArrayEquals(DualSenseTriggerEffect.off(), DualSenseTriggerEffect.from(null, 8));
    }
}
