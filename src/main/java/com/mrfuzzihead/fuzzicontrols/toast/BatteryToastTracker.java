package com.mrfuzzihead.fuzzicontrols.toast;

/**
 * One-shot low-battery notification state.
 *
 * <p>
 * The tracker fires once when the percentage crosses below the threshold, resets after the
 * controller charges comfortably above it or disconnects, and does not fire while charging.
 */
final class BatteryToastTracker {

    private boolean notified;

    /**
     * Updates the tracker with the latest battery reading.
     *
     * @param percent   battery percentage, or {@code -1} when unknown/disconnected
     * @param charging  whether the controller reports charging
     * @param threshold low-battery threshold in percent
     * @return true exactly once per low-battery crossing
     */
    boolean update(int percent, boolean charging, int threshold) {
        if (percent < 0) {
            notified = false;
            return false;
        }

        if (charging || percent > threshold + 5) {
            if (percent > threshold) notified = false;
            return false;
        }

        if (notified || percent > threshold) return false;

        notified = true;
        return true;
    }
}
