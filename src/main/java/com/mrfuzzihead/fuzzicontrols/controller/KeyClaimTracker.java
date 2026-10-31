package com.mrfuzzihead.fuzzicontrols.controller;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Tracks which key bindings the controller is currently holding down.
 *
 * <p>
 * The mod must never clear a key the player is holding on the keyboard: it only releases keys it
 * pressed itself. That contract lives here so it can be unit tested — getting it wrong makes WASD or
 * shift stick (or fight the keyboard) in game, which is hard to diagnose from a bug report.
 */
final class KeyClaimTracker {

    /** What {@link #update(int, boolean)} wants the caller to do with the key state. */
    enum Action {
        /** Assert the key as pressed. */
        PRESS,
        /** Clear the key state, because the controller was holding it. */
        RELEASE,
        /** Leave the key alone (the keyboard owns it). */
        NONE
    }

    private final Set<Integer> claimed = new HashSet<>();

    /**
     * Decides what to do with one key binding.
     *
     * @param keyCode  the binding's key code
     * @param wantHeld whether the controller wants the key held
     * @return the action for the caller to apply
     */
    Action update(int keyCode, boolean wantHeld) {
        if (wantHeld) {
            claimed.add(keyCode);
            return Action.PRESS;
        }
        // Only release keys the controller itself claimed; otherwise the keyboard is holding it.
        return claimed.remove(keyCode) ? Action.RELEASE : Action.NONE;
    }

    /**
     * Gives up the claim on a key without deciding the action, e.g. when the player presses the
     * keyboard sneak key and takes over from the toggle.
     *
     * @return true when the controller was holding that key
     */
    boolean release(int keyCode) {
        return claimed.remove(keyCode);
    }

    /** Returns true when the controller is currently holding that key. */
    boolean isClaimed(int keyCode) {
        return claimed.contains(keyCode);
    }

    /** Snapshot of every claimed key code. */
    Set<Integer> claimed() {
        return Collections.unmodifiableSet(claimed);
    }

    /** Returns true when no key is claimed. */
    boolean isEmpty() {
        return claimed.isEmpty();
    }

    /** Forgets every claim (controller disconnected, input paused). */
    void clear() {
        claimed.clear();
    }
}
