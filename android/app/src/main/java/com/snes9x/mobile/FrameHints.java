package com.snes9x.mobile;

import android.content.Context;
import android.os.Build;
import android.os.PerformanceHintManager;
import android.os.Process;

/**
 * Tells Android how long each frame takes to emulate, so it keeps the CPU fast enough while
 * nobody touches the screen. Without it, some phones only speed up the CPU for a few seconds
 * after a touch and the game slows down again. Needs Android 12; on older versions it does
 * nothing.
 */
final class FrameHints {
    private final PerformanceHintManager.Session session;

    private FrameHints(PerformanceHintManager.Session session) {
        this.session = session;
    }

    /** Call from the thread that runs the frames; null when not supported. */
    static FrameHints create(Context context, long targetFrameNanos) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return null;
        }
        try {
            PerformanceHintManager manager = context.getSystemService(PerformanceHintManager.class);
            if (manager == null) {
                return null;
            }
            PerformanceHintManager.Session session = manager.createHintSession(
                    new int[] {Process.myTid()}, targetFrameNanos);
            return session != null ? new FrameHints(session) : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** How long the last frame took to emulate and draw, not counting the wait after it. */
    void reportWork(long nanos) {
        if (nanos <= 0) {
            return;
        }
        try {
            session.reportActualWorkDuration(nanos);
        } catch (RuntimeException e) {
            // The session can die with the process' priority changes; nothing to do.
        }
    }

    void close() {
        try {
            session.close();
        } catch (RuntimeException e) {
            // Already gone.
        }
    }
}
