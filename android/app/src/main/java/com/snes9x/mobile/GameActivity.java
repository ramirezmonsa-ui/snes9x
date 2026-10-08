package com.snes9x.mobile;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Bitmap;
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
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;

/** Plays the ROM passed in the intent data. */
public class GameActivity extends Activity
        implements SurfaceHolder.Callback, InputManager.InputDeviceListener {
    private EmulatorThread emulator;
    private GamepadView gamepad;
    private ImageView pauseButton;
    private ImageView fastForwardButton;
    private ImageView rewindButton;
    private TextView speedBadge;
    private String gameName;
    private boolean loaded;
    private SaveSlots slots;
    private SharedPreferences prefs;
    private float gameAspect = 4f / 3f;

    // The automatic save is only written once the player has decided whether
    // to continue from the previous one, so it isn't overwritten by mistake.
    private boolean autoSaveReady;

    // The emulator is shared by the whole app. Each game screen gets a number
    // when it loads its game, so a screen that is closing late doesn't save
    // over a game opened after it.
    private static int latestGame;
    private int gameNumber;

    // Buttons held on the touch screen and on a physical controller.
    private int touchButtons;
    private int keyButtons;
    private int axisButtons;

    // Triggers: R2 turns fast-forward on and off, L2 rewinds while held.
    // Some controllers send them as keys, others as axes, some as both; once
    // keys are seen the axes are ignored so a press doesn't count twice.
    private boolean triggerKeysSeen;
    private boolean fastForwardTriggerDown;
    private boolean rewindHeld;

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

        // Rewind, pause and fast-forward. They go next to the picture, never
        // over it (see placeTopButtons), and hide with the touch controls.
        rewindButton = topButton(R.drawable.ic_fast_rewind, R.string.rewind);
        rewindButton.setOnTouchListener((v, event) -> {
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                setRewinding(true);
            } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                setRewinding(false);
            }
            return true;
        });
        pauseButton = topButton(R.drawable.ic_pause, R.string.menu_paused);
        pauseButton.setOnClickListener(v -> showMenu());
        fastForwardButton = topButton(R.drawable.ic_fast_forward, R.string.fast_forward);
        fastForwardButton.setOnClickListener(v -> toggleFastForward());
        int size = Ui.dp(this, 44);
        for (ImageView button : new ImageView[] {rewindButton, pauseButton, fastForwardButton}) {
            root.addView(button, new FrameLayout.LayoutParams(size, size, Gravity.TOP | Gravity.START));
        }
        root.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight,
                oldBottom) -> placeTopButtons(right - left, bottom - top));

        // Shows "⏩ x3" or "⏪" while active, also when playing with a
        // controller and the buttons above are hidden.
        speedBadge = Ui.label(this, "", 15, 0xFFFFFFFF);
        speedBadge.setBackground(Ui.rounded(0x99000000, Ui.dp(this, 14)));
        speedBadge.setPadding(Ui.dp(this, 12), Ui.dp(this, 4), Ui.dp(this, 12), Ui.dp(this, 4));
        speedBadge.setVisibility(View.GONE);
        FrameLayout.LayoutParams badgeParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.END);
        badgeParams.topMargin = Ui.dp(this, 20);
        badgeParams.rightMargin = Ui.dp(this, 16);
        root.addView(speedBadge, badgeParams);

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
        slots = new SaveSlots(dir("states"), gameName);
        prefs = getSharedPreferences("games", MODE_PRIVATE);
        gameAspect = NativeBridge.getAspectRatio();
        View content = findViewById(android.R.id.content);
        placeTopButtons(content.getWidth(), content.getHeight());

        emulator = new EmulatorThread();
        emulator.setAlignTop(isPortrait());
        emulator.setFilter(prefs.getInt(filterKey(), EmulatorThread.FILTER_SHARP));
        emulator.start();

        if (slots.exists(SaveSlots.AUTO)) {
            askToContinue();
        } else {
            autoSaveReady = true;
        }
    }

    /**
     * Puts rewind, pause and fast-forward where they don't cover the game: under the picture in
     * portrait, and in the black side bands in landscape. The picture's position follows the
     * same rules as EmulatorThread.draw().
     */
    private void placeTopButtons(int width, int height) {
        if (width == 0 || height == 0) {
            return;
        }
        int pictureWidth = width;
        int pictureHeight = Math.round(width / gameAspect);
        if (pictureHeight > height) {
            pictureHeight = height;
            pictureWidth = Math.round(height * gameAspect);
        }
        int pictureLeft = (width - pictureWidth) / 2;
        int pictureTop = isPortrait() ? 0 : (height - pictureHeight) / 2;

        int size = Ui.dp(this, 44);
        int gap = Ui.dp(this, 14);
        int band = pictureLeft;  // width of each black side band
        if (!isPortrait() && band >= size + gap) {
            // Rewind on the left, fast-forward and pause on the right.
            int leftX = (band - size) / 2;
            int rightX = pictureLeft + pictureWidth + (band - size) / 2;
            move(rewindButton, leftX, gap);
            move(fastForwardButton, rightX, gap);
            move(pauseButton, rightX, gap + size + gap);
        } else {
            // A row under the picture (or at the top if there is no room).
            int rowWidth = size * 3 + gap * 2;
            int x = (width - rowWidth) / 2;
            int y = pictureTop + pictureHeight + gap;
            if (y + size > height) {
                y = gap;
            }
            move(rewindButton, x, y);
            move(pauseButton, x + size + gap, y);
            move(fastForwardButton, x + (size + gap) * 2, y);
        }
    }

    private static void move(View view, int x, int y) {
        view.setTranslationX(x);
        view.setTranslationY(y);
    }

    private ImageView topButton(int icon, int description) {
        ImageView button = new ImageView(this);
        button.setImageDrawable(Ui.icon(this, icon, 0xFFFFFFFF));
        int pad = Ui.dp(this, 10);
        button.setPadding(pad, pad, pad, pad);
        button.setBackground(Ui.rounded(0x66000000, Ui.dp(this, 22)));
        button.setAlpha(0.85f);
        button.setContentDescription(getString(description));
        return button;
    }

    // --- Fast-forward and rewind --------------------------------------------

    private void toggleFastForward() {
        if (emulator == null) {
            return;
        }
        emulator.setFastForward(!emulator.isFastForward());
        updateSpeedIndicators();
    }

    private void setRewinding(boolean rewinding) {
        if (emulator == null || rewinding == rewindHeld) {
            return;
        }
        rewindHeld = rewinding;
        emulator.setRewinding(rewinding);
        updateSpeedIndicators();
    }

    private void updateSpeedIndicators() {
        boolean fast = emulator != null && emulator.isFastForward();
        fastForwardButton.setBackground(Ui.rounded(fast ? Ui.RED : 0x66000000, Ui.dp(this, 22)));
        rewindButton.setBackground(Ui.rounded(rewindHeld ? Ui.BLUE : 0x66000000, Ui.dp(this, 22)));
        if (rewindHeld) {
            speedBadge.setText(getString(R.string.badge_rewind));
            speedBadge.setVisibility(View.VISIBLE);
        } else if (fast) {
            speedBadge.setText(getString(R.string.badge_fast_forward));
            speedBadge.setVisibility(View.VISIBLE);
        } else {
            speedBadge.setVisibility(View.GONE);
        }
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
        storeAutoSave();
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
        updateButtons();
        setRewinding(false);

        boolean touchVisible = gamepad.getVisibility() == View.VISIBLE;

        new ActionSheet(this)
                .title(gameName)
                .subtitle(getString(R.string.menu_paused))
                .action(Ui.GREEN, R.drawable.ic_play, getString(R.string.menu_resume), null)
                .action(Ui.BLUE, R.drawable.ic_save, getString(R.string.menu_slots),
                        getString(R.string.menu_slots_detail), this::showSlots)
                .action(Ui.YELLOW, R.drawable.ic_tv, getString(R.string.menu_screen,
                        filterName(prefs.getInt(filterKey(), EmulatorThread.FILTER_SHARP))),
                        null, this::chooseFilter)
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

    /** Rewind, pause and fast-forward go with the touch controls, so nothing covers the game without them. */
    private void setTouchControlsVisible(boolean visible) {
        int visibility = visible ? View.VISIBLE : View.GONE;
        gamepad.setVisibility(visibility);
        rewindButton.setVisibility(visibility);
        pauseButton.setVisibility(visibility);
        fastForwardButton.setVisibility(visibility);
    }

    // --- Screen filter ------------------------------------------------------

    private String filterKey() {
        return "filter:" + gameName;
    }

    private String filterName(int filter) {
        switch (filter) {
            case EmulatorThread.FILTER_SMOOTH: return getString(R.string.filter_smooth);
            case EmulatorThread.FILTER_CRT: return getString(R.string.filter_crt);
            default: return getString(R.string.filter_sharp);
        }
    }

    private void chooseFilter() {
        pauseForMenu();
        new ActionSheet(this)
                .title(getString(R.string.filter_title))
                .subtitle(getString(R.string.filter_subtitle))
                .action(Ui.BLUE, R.drawable.ic_tv, getString(R.string.filter_sharp),
                        getString(R.string.filter_sharp_detail),
                        () -> setFilter(EmulatorThread.FILTER_SHARP))
                .action(Ui.GREEN, R.drawable.ic_tv, getString(R.string.filter_smooth),
                        getString(R.string.filter_smooth_detail),
                        () -> setFilter(EmulatorThread.FILTER_SMOOTH))
                .action(Ui.RED, R.drawable.ic_tv, getString(R.string.filter_crt),
                        getString(R.string.filter_crt_detail),
                        () -> setFilter(EmulatorThread.FILTER_CRT))
                .onDismiss(this::onMenuClosed)
                .show();
    }

    private void setFilter(int filter) {
        prefs.edit().putInt(filterKey(), filter).apply();
        if (emulator != null) {
            emulator.setFilter(filter);
        }
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

    private void showSlots() {
        pauseForMenu();
        SlotsDialog.show(this, slots, this::showSlotActions, this::onMenuClosed);
    }

    private void showSlotActions(int slot) {
        pauseForMenu();
        String name = slot == SaveSlots.AUTO
                ? getString(R.string.slot_auto) : getString(R.string.slot_number, slot);
        ActionSheet sheet = new ActionSheet(this).title(name);
        if (slots.exists(slot)) {
            sheet.subtitle(getString(R.string.state_saved_at, DateUtils.getRelativeTimeSpanString(
                    slots.time(slot), System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS)));
            sheet.action(Ui.YELLOW, R.drawable.ic_history, getString(R.string.slot_load),
                    () -> loadSlot(slot));
        } else {
            sheet.subtitle(getString(R.string.slot_empty));
        }
        if (slot != SaveSlots.AUTO) {
            sheet.action(Ui.BLUE, R.drawable.ic_save, getString(R.string.slot_save),
                    slots.exists(slot) ? getString(R.string.slot_overwrite) : null,
                    () -> saveSlot(slot));
        }
        sheet.action(Ui.SHELL, R.drawable.ic_play, getString(R.string.cancel), null)
                .onDismiss(this::onMenuClosed)
                .show();
    }

    private void saveSlot(int slot) {
        Bitmap picture = emulator != null ? emulator.snapshot() : null;
        if (slots.save(slot, NativeBridge.saveState(), picture)) {
            Toast.makeText(this, R.string.state_saved, Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, R.string.state_save_failed, Toast.LENGTH_SHORT).show();
        }
    }

    private void loadSlot(int slot) {
        byte[] state = slots.load(slot);
        if (state != null && NativeBridge.loadState(state)) {
            Toast.makeText(this, R.string.state_loaded, Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, R.string.state_load_failed, Toast.LENGTH_SHORT).show();
        }
    }

    /** On opening a game that was left mid-play, offer to continue from there. */
    private void askToContinue() {
        pauseForMenu();
        new ActionSheet(this)
                .title(getString(R.string.continue_title))
                .subtitle(getString(R.string.continue_subtitle, DateUtils.getRelativeTimeSpanString(
                        slots.time(SaveSlots.AUTO), System.currentTimeMillis(),
                        DateUtils.MINUTE_IN_MILLIS)))
                .action(Ui.GREEN, R.drawable.ic_play, getString(R.string.continue_yes),
                        () -> loadSlot(SaveSlots.AUTO))
                .action(Ui.SHELL, R.drawable.ic_refresh, getString(R.string.continue_no),
                        getString(R.string.continue_no_detail), null)
                .onDismiss(() -> {
                    autoSaveReady = true;
                    onMenuClosed();
                })
                .show();
    }

    private void storeAutoSave() {
        if (!loaded || !autoSaveReady || gameNumber != latestGame || emulator == null) {
            return;
        }
        slots.save(SaveSlots.AUTO, NativeBridge.saveState(), emulator.snapshot());
    }

    private File saveRamFile() {
        return new File(dir("saves"), gameName + ".srm");
    }

    private void loadSaveRam() {
        byte[] sram = SaveSlots.readFile(saveRamFile());
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
            SaveSlots.writeFile(saveRamFile(), sram);
        }
    }

    // --- Physical controllers -----------------------------------------------

    private void updateButtons() {
        NativeBridge.setButtons(touchButtons | keyButtons | axisButtons);
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
        // R2 (or Tab) toggles fast-forward, L2 (or Backspace) rewinds while held.
        if (keyCode == KeyEvent.KEYCODE_BUTTON_R2 || keyCode == KeyEvent.KEYCODE_TAB) {
            triggerKeysSeen |= keyCode == KeyEvent.KEYCODE_BUTTON_R2;
            if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
                toggleFastForward();
            }
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_BUTTON_L2 || keyCode == KeyEvent.KEYCODE_DEL) {
            triggerKeysSeen |= keyCode == KeyEvent.KEYCODE_BUTTON_L2;
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                setRewinding(true);
            } else if (event.getAction() == KeyEvent.ACTION_UP) {
                setRewinding(false);
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

        // Analog triggers. Depending on the mode, controllers report them
        // as the trigger or the brake/gas axes.
        boolean triggerUsed = false;
        if (!triggerKeysSeen) {
            float left = Math.max(event.getAxisValue(MotionEvent.AXIS_LTRIGGER),
                    event.getAxisValue(MotionEvent.AXIS_BRAKE));
            float right = Math.max(event.getAxisValue(MotionEvent.AXIS_RTRIGGER),
                    event.getAxisValue(MotionEvent.AXIS_GAS));
            if (left > 0.5f) {
                setRewinding(true);
                triggerUsed = true;
            } else if (left < 0.3f && rewindHeld) {
                setRewinding(false);
            }
            if (right > 0.5f && !fastForwardTriggerDown) {
                fastForwardTriggerDown = true;
                toggleFastForward();
                triggerUsed = true;
            } else if (right < 0.3f) {
                fastForwardTriggerDown = false;
            }
        }

        if (buttons != 0 || triggerUsed) {
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
