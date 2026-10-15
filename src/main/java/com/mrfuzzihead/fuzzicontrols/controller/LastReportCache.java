package com.mrfuzzihead.fuzzicontrols.controller;

/**
 * Holds the most recent successfully parsed {@link ControllerState}.
 *
 * <p>
 * The DualSense driver reads HID reports in a non-blocking loop, so most polls of a 250 Hz device
 * return no report at all. Returning {@link ControllerState#empty()} for those polls would drop
 * every button and axis between reports, so the last valid state is cached and returned instead.
 * Extracted from {@link DualSenseDriver} so the caching contract can be unit tested without HID
 * hardware.
 */
final class LastReportCache {

    private ControllerState last;

    /**
     * Records a freshly parsed state, if there was one, and returns the state to use for this poll.
     *
     * @param fresh state parsed from a new report, or {@code null} when no report was available
     * @return {@code fresh} when present, otherwise the previously recorded state, otherwise
     *         {@link ControllerState#empty()}
     */
    ControllerState record(ControllerState fresh) {
        if (fresh != null) {
            last = fresh;
        }
        return current();
    }

    /** Returns the last recorded state, or {@link ControllerState#empty()} if none was recorded. */
    ControllerState current() {
        return last != null ? last : ControllerState.empty();
    }

    /** Forgets the recorded state (device closed / disconnected). */
    void clear() {
        last = null;
    }
}
