package com.mrfuzzihead.fuzzicontrols.gui.widget;

/**
 * Implemented by settings-screen sliders so the shared D-pad focus navigator can adjust them with
 * left/right instead of treating them as ordinary buttons.
 */
public interface ConfigSlider {

    /**
     * Moves the slider by a fraction of its full range.
     *
     * @param delta signed fraction, e.g. {@code -0.05} for one step left
     */
    void step(float delta);
}
