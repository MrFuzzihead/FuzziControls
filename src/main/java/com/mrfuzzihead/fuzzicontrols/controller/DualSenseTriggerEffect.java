package com.mrfuzzihead.fuzzicontrols.controller;

/**
 * Builds the 11-byte adaptive-trigger effect blocks understood by the DualSense.
 *
 * <p>
 * The byte layouts follow Nielk1's TriggerEffectGenerator and the Linux
 * {@code hid-playstation} documentation. Each block is one mode byte followed by ten
 * mode-dependent parameter bytes. The current presets use the same sensible defaults that the
 * settings screen exposes through a single strength slider.
 */
final class DualSenseTriggerEffect {

    static final int BLOCK_SIZE = 11;

    static final int MODE_OFF = 0x05;
    static final int MODE_FEEDBACK = 0x21;
    static final int MODE_WEAPON = 0x25;
    static final int MODE_VIBRATION = 0x26;
    static final int MODE_BOW = 0x22;
    static final int MODE_MACHINE = 0x27;

    private DualSenseTriggerEffect() {}

    static byte[] off() {
        byte[] block = new byte[BLOCK_SIZE];
        block[0] = (byte) MODE_OFF;
        return block;
    }

    static byte[] from(ControllerTriggerEffect effect, int strength) {
        if (effect == null || effect == ControllerTriggerEffect.OFF) return off();
        int force = clamp(strength, 1, 8);

        return switch (effect) {
            case FEEDBACK -> feedback(2, force);
            case WEAPON -> weapon(2, 7, force);
            case VIBRATION -> vibration(2, force, 30);
            case BOW -> bow(1, 6, force, 8);
            case MACHINE -> machine(0, 9, 3, clamp(force, 1, 7), 30, 5);
            case OFF -> off();
        };
    }

    static byte[] feedback(int position, int strength) {
        int force = clamp(strength, 1, 8);
        int start = clamp(position, 0, 9);
        int forceValue = (force - 1) & 0x07;
        int activeZones = 0;
        int forceZones = 0;
        for (int zone = start; zone < 10; zone++) {
            activeZones |= 1 << zone;
            forceZones |= forceValue << (3 * zone);
        }
        return packedZones(MODE_FEEDBACK, activeZones, forceZones);
    }

    static byte[] weapon(int start, int end, int strength) {
        int first = clamp(start, 2, 7);
        int last = clamp(end, first + 1, 8);
        int force = clamp(strength, 1, 8);
        int startAndStopZones = (1 << first) | (1 << last);

        byte[] block = new byte[BLOCK_SIZE];
        block[0] = (byte) MODE_WEAPON;
        block[1] = (byte) startAndStopZones;
        block[2] = (byte) (startAndStopZones >>> 8);
        block[3] = (byte) (force - 1);
        return block;
    }

    static byte[] vibration(int position, int amplitude, int frequency) {
        int start = clamp(position, 0, 9);
        int force = clamp(amplitude, 1, 8);
        int forceValue = (force - 1) & 0x07;
        int activeZones = 0;
        int amplitudeZones = 0;
        for (int zone = start; zone < 10; zone++) {
            activeZones |= 1 << zone;
            amplitudeZones |= forceValue << (3 * zone);
        }

        byte[] block = packedZones(MODE_VIBRATION, activeZones, amplitudeZones);
        block[9] = (byte) clamp(frequency, 1, 255);
        return block;
    }

    static byte[] bow(int start, int end, int strength, int snapForce) {
        int first = clamp(start, 0, 7);
        int last = clamp(end, first + 1, 8);
        int force = clamp(strength, 1, 8);
        int snap = clamp(snapForce, 1, 8);
        int startAndStopZones = (1 << first) | (1 << last);
        int forcePair = ((force - 1) & 0x07) | (((snap - 1) & 0x07) << 3);

        byte[] block = new byte[BLOCK_SIZE];
        block[0] = (byte) MODE_BOW;
        block[1] = (byte) startAndStopZones;
        block[2] = (byte) (startAndStopZones >>> 8);
        block[3] = (byte) forcePair;
        block[4] = (byte) (forcePair >>> 8);
        return block;
    }

    static byte[] machine(int start, int end, int amplitudeA, int amplitudeB, int frequency, int period) {
        int first = clamp(start, 0, 8);
        int last = clamp(end, first + 1, 9);
        int a = clamp(amplitudeA, 0, 7);
        int b = clamp(amplitudeB, 0, 7);
        int startAndStopZones = (1 << first) | (1 << last);
        int strengthPair = (a & 0x07) | ((b & 0x07) << 3);

        byte[] block = new byte[BLOCK_SIZE];
        block[0] = (byte) MODE_MACHINE;
        block[1] = (byte) startAndStopZones;
        block[2] = (byte) (startAndStopZones >>> 8);
        block[3] = (byte) strengthPair;
        block[4] = (byte) clamp(frequency, 1, 255);
        block[5] = (byte) clamp(period, 0, 255);
        return block;
    }

    private static byte[] packedZones(int mode, int activeZones, int forceZones) {
        byte[] block = new byte[BLOCK_SIZE];
        block[0] = (byte) mode;
        block[1] = (byte) activeZones;
        block[2] = (byte) (activeZones >>> 8);
        block[3] = (byte) forceZones;
        block[4] = (byte) (forceZones >>> 8);
        block[5] = (byte) (forceZones >>> 16);
        block[6] = (byte) (forceZones >>> 24);
        return block;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
