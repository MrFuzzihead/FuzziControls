package com.mrfuzzihead.fuzzicontrols.util;

/**
 * Distinguishes real mouse movement from the cursor warps this mod performs itself.
 *
 * <p>
 * GUI navigation positions the OS cursor with {@code Mouse.setCursorPosition} (the left-stick
 * virtual cursor, and previously the D-pad slot confirm). The platform emits a genuine
 * mouse-motion event for such a warp, so a plain "did the position change?" check reports that the
 * player moved the mouse — which makes the D-pad navigation state machine deactivate itself and
 * swallow the next D-pad press. {@code org.lwjglx.input.Mouse} has {@code ignoreNextMove} /
 * {@code ignoreNextDelta} guards for exactly this, but {@code setCursorPosition} never sets them.
 *
 * <p>
 * This tracker is told which positions the mod commanded, ignores the frames immediately after a
 * warp (while the platform's event is still in flight), and tolerates the rounding introduced by
 * the framebuffer → screen-space conversion inside {@code setCursorPosition}.
 */
public final class MouseMotionTracker {

    /** Differences of up to this many pixels are treated as noise (warp rounding, hand jitter). */
    private static final int TOLERANCE = 2;

    /** Frames to ignore after a commanded warp, while the platform's motion event is in flight. */
    private static final int SETTLE_FRAMES = 2;

    private int lastX;
    private int lastY;
    private int commandedX;
    private int commandedY;
    private int settleFrames;
    private boolean seen;

    /**
     * Forgets the observed position, so the next {@link #userMoved(int, int)} cannot report a move.
     * Called when the screen changes or input processing resumes after being paused.
     */
    public void reset() {
        seen = false;
        settleFrames = 0;
    }

    /**
     * Records a cursor position this mod just commanded via {@code Mouse.setCursorPosition}, so the
     * motion event the platform generates for it is not mistaken for player input.
     *
     * @param x cursor x in the same space as {@code Mouse.getX()}
     * @param y cursor y in the same space as {@code Mouse.getY()}
     */
    public void command(int x, int y) {
        commandedX = x;
        commandedY = y;
        // Only arm the settle window when the cursor actually has to move: repeatedly commanding
        // the position it already occupies (the virtual cursor holding still) must not keep
        // suppressing real mouse movement.
        if (!seen || Math.abs(x - lastX) > TOLERANCE || Math.abs(y - lastY) > TOLERANCE) {
            settleFrames = SETTLE_FRAMES;
        }
    }

    /**
     * Feeds the current cursor position and reports whether the <em>player</em> moved the mouse.
     *
     * @param x current cursor x from {@code Mouse.getX()}
     * @param y current cursor y from {@code Mouse.getY()}
     * @return true only when the change cannot be attributed to a warp this mod commanded
     */
    public boolean userMoved(int x, int y) {
        boolean changed = seen && (Math.abs(x - lastX) > TOLERANCE || Math.abs(y - lastY) > TOLERANCE);
        lastX = x;
        lastY = y;

        if (!seen) {
            seen = true;
            if (settleFrames > 0) settleFrames--;
            return false; // First observation — nothing has moved yet.
        }
        if (settleFrames > 0) {
            settleFrames--;
            return false; // Our own warp has not been delivered yet.
        }
        if (!changed) return false;

        // A position that matches what we commanded is our warp, not the player.
        return Math.abs(x - commandedX) > TOLERANCE || Math.abs(y - commandedY) > TOLERANCE;
    }
}
