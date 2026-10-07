package com.snes9x.mobile;

import java.nio.ByteBuffer;

/** Thin wrapper over the libretro cores, see src/main/cpp/frontend.cpp. */
public final class NativeBridge {
    static {
        System.loadLibrary("frontend");
    }

    /** Largest frame the core can produce, in pixels. */
    public static final int MAX_WIDTH = 604;
    public static final int MAX_HEIGHT = 480;

    // Button bits, matching RETRO_DEVICE_ID_JOYPAD_*.
    public static final int BUTTON_B = 1 << 0;
    public static final int BUTTON_Y = 1 << 1;
    public static final int BUTTON_SELECT = 1 << 2;
    public static final int BUTTON_START = 1 << 3;
    public static final int BUTTON_UP = 1 << 4;
    public static final int BUTTON_DOWN = 1 << 5;
    public static final int BUTTON_LEFT = 1 << 6;
    public static final int BUTTON_RIGHT = 1 << 7;
    public static final int BUTTON_A = 1 << 8;
    public static final int BUTTON_X = 1 << 9;
    public static final int BUTTON_L = 1 << 10;
    public static final int BUTTON_R = 1 << 11;

    private NativeBridge() {
    }

    /**
     * Makes {@code coreLibrary} (for example "libsnes9x_libretro.so", found in {@code libraryDir})
     * the active emulator. Returns false if it can't be loaded.
     */
    public static native boolean init(String libraryDir, String coreLibrary, String systemDir,
            String saveDir);

    public static native boolean loadGame(byte[] rom, String name);

    public static native double getFps();

    public static native int getSampleRate();

    public static native float getAspectRatio();

    public static native void setButtons(int mask);

    /**
     * Runs one frame. Returns the number of audio samples written to {@code audio}, or -1 if no
     * game is loaded. {@code size} receives the frame width and height, or 0 when there is no new
     * frame.
     */
    public static native int runFrame(ByteBuffer video, short[] audio, int[] size);

    public static native void reset();

    public static native byte[] saveState();

    public static native boolean loadState(byte[] state);

    public static native byte[] getSaveRam();

    public static native void setSaveRam(byte[] sram);
}
