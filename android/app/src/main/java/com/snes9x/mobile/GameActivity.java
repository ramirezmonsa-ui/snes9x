package com.snes9x.mobile;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.hardware.input.InputManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.format.DateUtils;
import android.view.Gravity;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

/** Plays the ROM passed in the intent data. */
public class GameActivity extends Activity
        implements SurfaceHolder.Callback, InputManager.InputDeviceListener {
    private EmulatorThread emulator;
    private GamepadView gamepad;
    private View pauseButton;
    private String gameName;
    private boolean loaded;

    // The emulator is shared by the whole app. Each game screen gets a number
    // when it loads its game, so a screen that is closing late doesn't save
    // over a game opened after it.
    private static int latestGame;
    private int gameNumber;

    // Buttons held on the touch screen and on a physical controller.
    private int touchButtons;
    private int keyButtons;
    private int axisButtons;
    private int triggerButtons;

    private InputManager inputManager;
    private boolean controllerConnected;
    private int openMenus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        FrameLayout root = new FrameLayout(this);
        SurfaceView surface = new SurfaceView(this);
        surface.getHolder().addCallback(this);
        root.addView(surface);
        gamepad = new GamepadView(this);
        gamepad.setListener(mask -> {
            touchButtons = mask;
            updateButtons();
        });
        root.addView(gamepad);

        // Pause button at the top, the easiest way to reach the menu.
        ImageView pause = new ImageView(this);
        pauseButton = pause;
        pause.setImageDrawable(Ui.icon(this, R.drawable.ic_pause, 0xFFFFFFFF));
        int pausePad = Ui.dp(this, 10);
        pause.setPadding(pausePad, pausePad, pausePad, pausePad);
        pause.setBackground(Ui.rounded(0x66000000, Ui.dp(this, 22)));
        pause.setAlpha(0.8f);
        pause.setContentDescription(getString(R.string.menu_paused));
        pause.setOnClickListener(v -> showMenu());
        FrameLayout.LayoutParams pauseParams = new FrameLayout.LayoutParams(
                Ui.dp(this, 44), Ui.dp(this, 44), Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        pauseParams.topMargin = Ui.dp(this, 14);
        root.addView(pause, pauseParams);

        setContentView(root);
        hideSystemBars();

        inputManager = (InputManager) getSystemService(Context.INPUT_SERVICE);
        inputManager.registerInputDeviceListener(this, null);
        updateControllerState();

        Uri uri = getIntent().getData();
        if (uri == null) {
            finish();
            return;
        }

        gameName = RomLoader.displayName(getContentResolver(), uri);
        RomLoader.Rom rom;
        try {
            rom = RomLoader.read(getContentResolver(), uri);
        } catch (IOException | SecurityException e) {
            fail(getString(R.string.error_reading, e.getMessage()));
            return;
        }

        gamepad.setConsole(rom.console);
        if (!NativeBridge.init(getApplicationInfo().nativeLibraryDir, rom.console.coreLibrary,
                dir("system").getAbsolutePath(), dir("saves").getAbsolutePath())) {
            fail(getString(R.string.error_core));
            return;
        }
        if (!NativeBridge.loadGame(rom.data, gameName)) {
            fail(getString(R.string.error_loading));
            return;
        }
        loaded = true;
        gameNumber = ++latestGame;
        loadSaveRam();

        emulator = new EmulatorThread();
        emulator.setAlignTop(isPortrait());
        emulator.start();
    }

    private void fail(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        finish();
    }

    private boolean isPortrait() {
        return getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT;
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (emulator != null) {
            emulator.setAlignTop(isPortrait());
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideSystemBars();
        if (emulator != null && openMenus == 0) {
            emulator.setPaused(false);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (emulator != null) {
            emulator.setPaused(true);
        }
        storeSaveRam();
    }

    @Override
    protected void onDestroy() {
        inputManager.unregisterInputDeviceListener(this);
        if (emulator != null) {
            emulator.shutdown();
            emulator = null;
        }
        storeSaveRam();
        super.onDestroy();
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        if (emulator != null) {
            emulator.setSurface(holder);
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        if (emulator != null) {
            emulator.setSurface(null);
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            hideSystemBars();
        }
    }

    @SuppressWarnings("deprecation")
    private void hideSystemBars() {
        if (Build.VERSION.SDK_INT >= 30) {
            getWindow().setDecorFitsSystemWindows(false);
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                controller.hide(WindowInsets.Type.systemBars());
                controller.setSystemBarsBehavior(
                        WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
        }
    }

    // --- Menu ---------------------------------------------------------------

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        showMenu();
    }

    private void showMenu() {
        if (openMenus > 0) {
            return;
        }
        pauseForMenu();
        // Forget held buttons so nothing stays pressed after closing the menu.
        keyButtons = 0;
        axisButtons = 0;
        triggerButtons = 0;
        updateButtons();

        File state = stateFile();
        String saved = state.isFile()
                ? getString(R.string.state_saved_at, DateUtils.getRelativeTimeSpanString(
                        state.lastModified(), System.currentTimeMillis(),
                        DateUtils.MINUTE_IN_MILLIS))
                : getString(R.string.state_none);
        boolean touchVisible = gamepad.getVisibility() == View.VISIBLE;

        new ActionSheet(this)
                .title(gameName)
                .subtitle(getString(R.string.menu_paused))
                .action(Ui.GREEN, R.drawable.ic_play, getString(R.string.menu_resume), null)
                .action(Ui.BLUE, R.drawable.ic_save, getString(R.string.menu_save_state),
                        getString(R.string.menu_save_state_detail), this::saveState)
                .action(Ui.YELLOW, R.drawable.ic_history, getString(R.string.menu_load_state), saved,
                        this::loadState)
                .action(Ui.SHELL, R.drawable.ic_gamepad, getString(touchVisible
                        ? R.string.menu_hide_controls : R.string.menu_show_controls),
                        () -> setTouchControlsVisible(!touchVisible))
                .action(Ui.DARK, R.drawable.ic_refresh, getString(R.string.menu_reset), this::confirmReset)
                .danger(R.drawable.ic_exit, getString(R.string.menu_quit), this::finish)
                .onDismiss(this::onMenuClosed)
                .show();
    }

    private void confirmReset() {
        pauseForMenu();
        new ActionSheet(this)
                .title(getString(R.string.reset_title))
                .subtitle(getString(R.string.reset_body))
                .danger(R.drawable.ic_refresh, getString(R.string.reset_confirm), NativeBridge::reset)
                .action(Ui.GREEN, R.drawable.ic_play, getString(R.string.cancel), null)
                .onDismiss(this::onMenuClosed)
                .show();
    }

    /** The pause button goes with the touch controls, so nothing covers the game without them. */
    private void setTouchControlsVisible(boolean visible) {
        int visibility = visible ? View.VISIBLE : View.GONE;
        gamepad.setVisibility(visibility);
        pauseButton.setVisibility(visibility);
    }

    private void pauseForMenu() {
        openMenus++;
        if (emulator != null) {
            emulator.setPaused(true);
        }
    }

    /** Resumes the game once the last open menu closes. */
    private void onMenuClosed() {
        openMenus--;
        if (openMenus > 0) {
            return;
        }
        hideSystemBars();
        if (emulator != null && !isFinishing()) {
            emulator.setPaused(false);
        }
    }

    // --- Saves --------------------------------------------------------------

    private File dir(String name) {
        File dir = new File(getFilesDir(), name);
        if (!dir.isDirectory()) {
            dir.mkdirs();
        }
        return dir;
    }

    private File stateFile() {
        return new File(dir("states"), gameName + ".state");
    }

    private File saveRamFile() {
        return new File(dir("saves"), gameName + ".srm");
    }

    private void saveState() {
        byte[] state = NativeBridge.saveState();
        if (state != null && writeFile(stateFile(), state)) {
            Toast.makeText(this, R.string.state_saved, Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, R.string.state_save_failed, Toast.LENGTH_SHORT).show();
        }
    }

    private void loadState() {
        byte[] state = readFile(stateFile());
        if (state == null) {
            Toast.makeText(this, R.string.state_missing, Toast.LENGTH_SHORT).show();
        } else if (NativeBridge.loadState(state)) {
            Toast.makeText(this, R.string.state_loaded, Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, R.string.state_load_failed, Toast.LENGTH_SHORT).show();
        }
    }

    private void loadSaveRam() {
        byte[] sram = readFile(saveRamFile());
        if (sram != null) {
            NativeBridge.setSaveRam(sram);
        }
    }

    private void storeSaveRam() {
        if (!loaded || gameNumber != latestGame) {
            return;
        }
        byte[] sram = NativeBridge.getSaveRam();
        if (sram != null) {
            writeFile(saveRamFile(), sram);
        }
    }

    private static boolean writeFile(File file, byte[] data) {
        File temp = new File(file.getPath() + ".tmp");
        try (FileOutputStream out = new FileOutputStream(temp)) {
            out.write(data);
        } catch (IOException e) {
            return false;
        }
        return temp.renameTo(file);
    }

    private static byte[] readFile(File file) {
        if (!file.isFile()) {
            return null;
        }
        try (FileInputStream in = new FileInputStream(file)) {
            byte[] data = new byte[(int) file.length()];
            int offset = 0;
            while (offset < data.length) {
                int n = in.read(data, offset, data.length - offset);
                if (n < 0) {
                    return null;
                }
                offset += n;
            }
            return data;
        } catch (IOException e) {
            return null;
        }
    }

    // --- Physical controllers -----------------------------------------------

    private void updateButtons() {
        NativeBridge.setButtons(touchButtons | keyButtons | axisButtons | triggerButtons);
    }

    private static int buttonForKey(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_UP: return NativeBridge.BUTTON_UP;
            case KeyEvent.KEYCODE_DPAD_DOWN: return NativeBridge.BUTTON_DOWN;
            case KeyEvent.KEYCODE_DPAD_LEFT: return NativeBridge.BUTTON_LEFT;
            case KeyEvent.KEYCODE_DPAD_RIGHT: return NativeBridge.BUTTON_RIGHT;
            // Android names the buttons by position like an Xbox pad, the SNES
            // has A on the right and B at the bottom.
            case KeyEvent.KEYCODE_BUTTON_A: return NativeBridge.BUTTON_B;
            case KeyEvent.KEYCODE_BUTTON_B: return NativeBridge.BUTTON_A;
            case KeyEvent.KEYCODE_BUTTON_X: return NativeBridge.BUTTON_Y;
            case KeyEvent.KEYCODE_BUTTON_Y: return NativeBridge.BUTTON_X;
            case KeyEvent.KEYCODE_BUTTON_L1: return NativeBridge.BUTTON_L;
            case KeyEvent.KEYCODE_BUTTON_R1: return NativeBridge.BUTTON_R;
            case KeyEvent.KEYCODE_BUTTON_L2: return NativeBridge.BUTTON_L;
            case KeyEvent.KEYCODE_BUTTON_R2: return NativeBridge.BUTTON_R;
            case KeyEvent.KEYCODE_BUTTON_START: return NativeBridge.BUTTON_START;
            case KeyEvent.KEYCODE_BUTTON_SELECT: return NativeBridge.BUTTON_SELECT;
            // Keyboard, handy on Chromebooks and with the emulator.
            case KeyEvent.KEYCODE_X: return NativeBridge.BUTTON_A;
            case KeyEvent.KEYCODE_Z: return NativeBridge.BUTTON_B;
            case KeyEvent.KEYCODE_S: return NativeBridge.BUTTON_X;
            case KeyEvent.KEYCODE_A: return NativeBridge.BUTTON_Y;
            case KeyEvent.KEYCODE_Q: return NativeBridge.BUTTON_L;
            case KeyEvent.KEYCODE_W: return NativeBridge.BUTTON_R;
            case KeyEvent.KEYCODE_ENTER: return NativeBridge.BUTTON_START;
            case KeyEvent.KEYCODE_SPACE: return NativeBridge.BUTTON_SELECT;
            default: return 0;
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        int keyCode = event.getKeyCode();
        if (keyCode == KeyEvent.KEYCODE_BUTTON_MODE) {
            if (event.getAction() == KeyEvent.ACTION_UP) {
                showMenu();
            }
            return true;
        }
        int button = buttonForKey(keyCode);
        if (button == 0) {
            return super.dispatchKeyEvent(event);
        }
        if (isController(event.getDevice())) {
            onControllerUsed();
        }
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            keyButtons |= button;
        } else if (event.getAction() == KeyEvent.ACTION_UP) {
            keyButtons &= ~button;
        }
        updateButtons();
        return true;
    }

    @Override
    public boolean dispatchGenericMotionEvent(MotionEvent event) {
        if ((event.getSource() & InputDevice.SOURCE_JOYSTICK) != InputDevice.SOURCE_JOYSTICK
                || event.getAction() != MotionEvent.ACTION_MOVE) {
            return super.dispatchGenericMotionEvent(event);
        }
        // Left stick and the hat switch both drive the d-pad.
        float x = event.getAxisValue(MotionEvent.AXIS_HAT_X);
        float y = event.getAxisValue(MotionEvent.AXIS_HAT_Y);
        if (Math.abs(x) < 0.5f && Math.abs(y) < 0.5f) {
            x = event.getAxisValue(MotionEvent.AXIS_X);
            y = event.getAxisValue(MotionEvent.AXIS_Y);
        }
        int buttons = 0;
        if (x < -0.5f) {
            buttons |= NativeBridge.BUTTON_LEFT;
        } else if (x > 0.5f) {
            buttons |= NativeBridge.BUTTON_RIGHT;
        }
        if (y < -0.5f) {
            buttons |= NativeBridge.BUTTON_UP;
        } else if (y > 0.5f) {
            buttons |= NativeBridge.BUTTON_DOWN;
        }
        axisButtons = buttons;

        // Analog triggers (L2/R2) act as L and R. Depending on the mode,
        // controllers report them as the trigger or the brake/gas axes.
        int triggers = 0;
        if (Math.max(event.getAxisValue(MotionEvent.AXIS_LTRIGGER),
                event.getAxisValue(MotionEvent.AXIS_BRAKE)) > 0.5f) {
            triggers |= NativeBridge.BUTTON_L;
        }
        if (Math.max(event.getAxisValue(MotionEvent.AXIS_RTRIGGER),
                event.getAxisValue(MotionEvent.AXIS_GAS)) > 0.5f) {
            triggers |= NativeBridge.BUTTON_R;
        }
        triggerButtons = triggers;

        if (buttons != 0 || triggers != 0) {
            onControllerUsed();
        }
        updateButtons();
        return true;
    }

    // Touch controls hide by themselves while a controller is plugged in or
    // paired, and come back when it is disconnected.

    private static boolean isController(InputDevice device) {
        if (device == null || device.isVirtual()) {
            return false;
        }
        int sources = device.getSources();
        return (sources & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD
                || (sources & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK;
    }

    private void updateControllerState() {
        boolean connected = false;
        for (int id : InputDevice.getDeviceIds()) {
            if (isController(InputDevice.getDevice(id))) {
                connected = true;
                break;
            }
        }
        if (connected != controllerConnected) {
            controllerConnected = connected;
            setTouchControlsVisible(!connected);
            if (connected) {
                Toast.makeText(this, R.string.controller_connected, Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void onControllerUsed() {
        if (!controllerConnected) {
            controllerConnected = true;
            setTouchControlsVisible(false);
        }
    }

    @Override
    public void onInputDeviceAdded(int deviceId) {
        updateControllerState();
    }

    @Override
    public void onInputDeviceRemoved(int deviceId) {
        updateControllerState();
    }

    @Override
    public void onInputDeviceChanged(int deviceId) {
        updateControllerState();
    }
}
