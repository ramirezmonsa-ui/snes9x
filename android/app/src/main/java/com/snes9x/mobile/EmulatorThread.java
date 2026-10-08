package com.snes9x.mobile;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Build;
import android.os.Process;
import android.os.SystemClock;
import android.view.Surface;
import android.view.SurfaceHolder;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Runs the emulator, draws each frame to the surface and plays the audio. Writing the audio is
 * blocking, which is what keeps the game running at the right speed.
 */
final class EmulatorThread extends Thread {
    private final ByteBuffer video = ByteBuffer
            .allocateDirect(NativeBridge.MAX_WIDTH * NativeBridge.MAX_HEIGHT * 2)
            .order(ByteOrder.nativeOrder());
    private final short[] audio = new short[8192];
    private final int[] size = new int[2];
    private final Rect source = new Rect();
    private final Rect destination = new Rect();
    private final Paint paint = new Paint();

    private final Object lock = new Object();
    private SurfaceHolder holder;
    private boolean paused;
    private boolean stopped;

    /** How the picture is drawn. */
    static final int FILTER_SHARP = 0;
    static final int FILTER_SMOOTH = 1;
    static final int FILTER_CRT = 2;

    /** Frames run per drawn frame while fast-forwarding. */
    private static final int FAST_FORWARD_SPEED = 3;

    private Bitmap frame;
    private AudioTrack track;
    private float aspectRatio = 4f / 3f;
    private boolean alignTop;

    private volatile boolean fastForward;
    private volatile boolean rewinding;
    private volatile int filter = FILTER_SHARP;

    // Dark lines between the picture's rows for the CRT look, one pixel wide
    // and stretched across the screen.
    private Bitmap scanlines;
    private int scanlineRows;
    private final Paint scanlinePaint = new Paint();

    EmulatorThread() {
        super("Snes9x");
        // Nearest-neighbour scaling keeps the pixels sharp.
        paint.setFilterBitmap(false);
    }

    void setSurface(SurfaceHolder holder) {
        synchronized (lock) {
            this.holder = holder;
            lock.notifyAll();
        }
    }

    /** In portrait the game goes at the top so the controls fit underneath. */
    void setAlignTop(boolean alignTop) {
        this.alignTop = alignTop;
    }

    void setFastForward(boolean fastForward) {
        this.fastForward = fastForward;
    }

    boolean isFastForward() {
        return fastForward;
    }

    /** While true the game runs backwards. */
    void setRewinding(boolean rewinding) {
        this.rewinding = rewinding;
    }

    void setFilter(int filter) {
        this.filter = filter;
    }

    /** A copy of the frame on screen, or null if nothing was drawn yet. */
    Bitmap snapshot() {
        synchronized (video) {
            return frame == null ? null : frame.copy(Bitmap.Config.RGB_565, false);
        }
    }

    void setPaused(boolean paused) {
        synchronized (lock) {
            this.paused = paused;
            lock.notifyAll();
        }
    }

    void shutdown() {
        synchronized (lock) {
            stopped = true;
            lock.notifyAll();
        }
        try {
            join(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void run() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO);
        aspectRatio = NativeBridge.getAspectRatio();
        long frameNanos = (long) (1_000_000_000L / NativeBridge.getFps());
        createAudioTrack(NativeBridge.getSampleRate());

        long nextFrame = System.nanoTime();
        while (true) {
            SurfaceHolder surface;
            synchronized (lock) {
                while (!stopped && (paused || holder == null)) {
                    if (track != null) {
                        track.pause();
                    }
                    try {
                        lock.wait();
                    } catch (InterruptedException e) {
                        stopped = true;
                    }
                    nextFrame = System.nanoTime();
                }
                if (stopped) {
                    break;
                }
                surface = holder;
            }

            if (rewinding) {
                // No sound while rewinding; two snapshots a frame (each is
                // a few frames apart) so it runs back at about 2.5x.
                if (track != null) {
                    track.pause();
                    track.flush();
                }
                if (NativeBridge.rewindStep(video, size)) {
                    NativeBridge.rewindStep(video, size);
                }
                if (size[0] > 0 && size[1] > 0) {
                    draw(surface, size[0], size[1]);
                }
                nextFrame += frameNanos;
                long wait = (nextFrame - System.nanoTime()) / 1_000_000L;
                if (wait > 0) {
                    SystemClock.sleep(wait);
                } else if (wait < -100) {
                    nextFrame = System.nanoTime();
                }
                continue;
            }

            int samples = NativeBridge.runFrame(video, audio, size);
            if (samples < 0) {
                break;
            }
            if (fastForward) {
                // Only the first frame's sound is played, so the audio
                // still paces the loop but the game runs several times
                // faster.
                for (int i = 1; i < FAST_FORWARD_SPEED; i++) {
                    if (NativeBridge.runFrame(video, null, size) < 0) {
                        break;
                    }
                }
            }
            if (size[0] > 0 && size[1] > 0) {
                draw(surface, size[0], size[1]);
            }

            if (track != null) {
                if (track.getPlayState() != AudioTrack.PLAYSTATE_PLAYING) {
                    track.play();
                }
                track.write(audio, 0, samples);
            } else {
                // No audio: pace the frames with the clock instead.
                nextFrame += frameNanos;
                long wait = (nextFrame - System.nanoTime()) / 1_000_000L;
                if (wait > 0) {
                    SystemClock.sleep(wait);
                } else if (wait < -100) {
                    nextFrame = System.nanoTime();
                }
            }
        }

        if (track != null) {
            track.release();
            track = null;
        }
    }

    /** A 1 x screenHeight strip that darkens the bottom of each picture row. */
    private Bitmap scanlines(int rows, int screenHeight) {
        if (scanlines == null || scanlines.getHeight() != screenHeight || scanlineRows != rows) {
            Bitmap strip = Bitmap.createBitmap(1, Math.max(1, screenHeight), Bitmap.Config.ARGB_8888);
            for (int y = 0; y < strip.getHeight(); y++) {
                float position = (y + 0.5f) * rows / screenHeight;
                float within = position - (float) Math.floor(position);
                // Dark band over the lower ~40% of every row.
                strip.setPixel(0, y, within > 0.6f ? 0x73000000 : 0x00000000);
            }
            scanlines = strip;
            scanlineRows = rows;
        }
        return scanlines;
    }

    private void createAudioTrack(int sampleRate) {
        int minSize = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_STEREO,
                AudioFormat.ENCODING_PCM_16BIT);
        if (minSize <= 0) {
            return;
        }
        // About 70ms of buffer: enough to avoid crackling, small enough to
        // keep the sound in sync with the picture.
        int bufferSize = Math.max(minSize, sampleRate * 4 * 70 / 1000);
        try {
            AudioAttributes attributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build();
            AudioFormat format = new AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .build();
            track = new AudioTrack(attributes, format, bufferSize, AudioTrack.MODE_STREAM,
                    AudioManager.AUDIO_SESSION_ID_GENERATE);
            if (track.getState() != AudioTrack.STATE_INITIALIZED) {
                track.release();
                track = null;
            }
        } catch (RuntimeException e) {
            track = null;
        }
    }

    private void draw(SurfaceHolder holder, int width, int height) {
        synchronized (video) {
            if (frame == null || frame.getWidth() != width || frame.getHeight() != height) {
                frame = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);
            }
            video.rewind();
            frame.copyPixelsFromBuffer(video);
        }

        Surface surface = holder.getSurface();
        if (surface == null || !surface.isValid()) {
            return;
        }
        Canvas canvas;
        try {
            canvas = Build.VERSION.SDK_INT >= 23 ? surface.lockHardwareCanvas() : surface.lockCanvas(null);
        } catch (RuntimeException e) {
            return;
        }
        if (canvas == null) {
            return;
        }
        try {
            int cw = canvas.getWidth();
            int ch = canvas.getHeight();
            int w = cw;
            int h = Math.round(cw / aspectRatio);
            if (h > ch) {
                h = ch;
                w = Math.round(ch * aspectRatio);
            }
            int left = (cw - w) / 2;
            int top = alignTop ? 0 : (ch - h) / 2;
            canvas.drawColor(Color.BLACK);
            source.set(0, 0, width, height);
            destination.set(left, top, left + w, top + h);
            int mode = filter;
            paint.setFilterBitmap(mode != FILTER_SHARP);
            canvas.drawBitmap(frame, source, destination, paint);
            if (mode == FILTER_CRT) {
                canvas.drawBitmap(scanlines(height, h), null, destination, scanlinePaint);
            }
        } finally {
            try {
                surface.unlockCanvasAndPost(canvas);
            } catch (RuntimeException e) {
                // The surface went away while drawing.
            }
        }
    }
}
