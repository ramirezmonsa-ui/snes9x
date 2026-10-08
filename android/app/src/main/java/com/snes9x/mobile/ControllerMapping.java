package com.snes9x.mobile;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.KeyEvent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Which controller (or keyboard) button does what. The d-pad and sticks always move; everything
 * else can be reassigned from the "Configurar control" screen.
 */
final class ControllerMapping {
    /** Something a button can do. */
    enum Action {
        B("B", NativeBridge.BUTTON_B),
        A("A", NativeBridge.BUTTON_A),
        Y("Y", NativeBridge.BUTTON_Y),
        X("X", NativeBridge.BUTTON_X),
        L("L", NativeBridge.BUTTON_L),
        R("R", NativeBridge.BUTTON_R),
        START("Start", NativeBridge.BUTTON_START),
        SELECT("Select", NativeBridge.BUTTON_SELECT),
        FAST_FORWARD(null, 0),
        REWIND(null, 0),
        MENU(null, 0);

        /** Name of the SNES button, or null for the app's own actions. */
        final String button;
        /** The game button it presses, or 0. */
        final int mask;

        Action(String button, int mask) {
            this.button = button;
            this.mask = mask;
        }
    }

    private static final String PREFS = "controls";
    private static final String KEY = "map";

    private final Context context;
    private final Map<Integer, Action> map = new LinkedHashMap<>();

    ControllerMapping(Context context) {
        this.context = context.getApplicationContext();
        load();
    }

    /** What a key does, or null if nothing. */
    Action get(int keyCode) {
        return map.get(keyCode);
    }

    /** The keys assigned to an action, in the order they were assigned. */
    List<Integer> keysFor(Action action) {
        List<Integer> keys = new ArrayList<>();
        for (Map.Entry<Integer, Action> entry : map.entrySet()) {
            if (entry.getValue() == action) {
                keys.add(entry.getKey());
            }
        }
        return keys;
    }

    /** Makes {@code keyCode} do {@code action}, taking it away from whatever it did before. */
    void assign(int keyCode, Action action) {
        map.remove(keyCode);
        map.put(keyCode, action);
        save();
    }

    void clear(Action action) {
        map.values().removeIf(value -> value == action);
        save();
    }

    void reset() {
        map.clear();
        putDefaults(map);
        save();
    }

    /** The d-pad always moves; those keys can't be reassigned. */
    static boolean isDpad(int keyCode) {
        return keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_DPAD_DOWN
                || keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT
                || keyCode == KeyEvent.KEYCODE_DPAD_CENTER;
    }

    private static void putDefaults(Map<Integer, Action> map) {
        // Android names gamepad buttons by position like an Xbox pad; the
        // SNES has A on the right and B at the bottom.
        map.put(KeyEvent.KEYCODE_BUTTON_A, Action.B);
        map.put(KeyEvent.KEYCODE_BUTTON_B, Action.A);
        map.put(KeyEvent.KEYCODE_BUTTON_X, Action.Y);
        map.put(KeyEvent.KEYCODE_BUTTON_Y, Action.X);
        map.put(KeyEvent.KEYCODE_BUTTON_L1, Action.L);
        map.put(KeyEvent.KEYCODE_BUTTON_R1, Action.R);
        map.put(KeyEvent.KEYCODE_BUTTON_START, Action.START);
        map.put(KeyEvent.KEYCODE_BUTTON_SELECT, Action.SELECT);
        map.put(KeyEvent.KEYCODE_BUTTON_R2, Action.FAST_FORWARD);
        map.put(KeyEvent.KEYCODE_BUTTON_L2, Action.REWIND);
        // Stick clicks are unused by these consoles. Back paddles (like M1
        // and M2 on EasySMX pads) can be set on the controller to copy them.
        map.put(KeyEvent.KEYCODE_BUTTON_THUMBR, Action.FAST_FORWARD);
        map.put(KeyEvent.KEYCODE_BUTTON_THUMBL, Action.REWIND);
        map.put(KeyEvent.KEYCODE_BUTTON_MODE, Action.MENU);
        // Keyboard, handy on Chromebooks and with the emulator.
        map.put(KeyEvent.KEYCODE_X, Action.A);
        map.put(KeyEvent.KEYCODE_Z, Action.B);
        map.put(KeyEvent.KEYCODE_S, Action.X);
        map.put(KeyEvent.KEYCODE_A, Action.Y);
        map.put(KeyEvent.KEYCODE_Q, Action.L);
        map.put(KeyEvent.KEYCODE_W, Action.R);
        map.put(KeyEvent.KEYCODE_ENTER, Action.START);
        map.put(KeyEvent.KEYCODE_SPACE, Action.SELECT);
        map.put(KeyEvent.KEYCODE_TAB, Action.FAST_FORWARD);
        map.put(KeyEvent.KEYCODE_DEL, Action.REWIND);
    }

    // Stored as "keyCode:ACTION" pairs separated by ";".
    private void load() {
        String stored = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null);
        if (stored == null) {
            putDefaults(map);
            return;
        }
        for (String pair : stored.split(";")) {
            int colon = pair.indexOf(':');
            if (colon <= 0) {
                continue;
            }
            try {
                map.put(Integer.parseInt(pair.substring(0, colon)),
                        Action.valueOf(pair.substring(colon + 1)));
            } catch (IllegalArgumentException e) {
                // Skip entries from an older or newer version.
            }
        }
    }

    private void save() {
        StringBuilder out = new StringBuilder();
        for (Map.Entry<Integer, Action> entry : map.entrySet()) {
            out.append(entry.getKey()).append(':').append(entry.getValue().name()).append(';');
        }
        SharedPreferences.Editor editor =
                context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit();
        editor.putString(KEY, out.toString()).apply();
    }

    /** A short name for a key, as printed on most controllers. */
    static String keyName(Context context, int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_BUTTON_A: return "A";
            case KeyEvent.KEYCODE_BUTTON_B: return "B";
            case KeyEvent.KEYCODE_BUTTON_X: return "X";
            case KeyEvent.KEYCODE_BUTTON_Y: return "Y";
            case KeyEvent.KEYCODE_BUTTON_L1: return "LB";
            case KeyEvent.KEYCODE_BUTTON_R1: return "RB";
            case KeyEvent.KEYCODE_BUTTON_L2: return "LT";
            case KeyEvent.KEYCODE_BUTTON_R2: return "RT";
            case KeyEvent.KEYCODE_BUTTON_THUMBL: return "L3";
            case KeyEvent.KEYCODE_BUTTON_THUMBR: return "R3";
            case KeyEvent.KEYCODE_BUTTON_START: return "Start";
            case KeyEvent.KEYCODE_BUTTON_SELECT: return "Select";
            case KeyEvent.KEYCODE_BUTTON_MODE: return "Home";
            default:
                break;
        }
        if (keyCode >= KeyEvent.KEYCODE_BUTTON_1 && keyCode <= KeyEvent.KEYCODE_BUTTON_16) {
            return context.getString(R.string.key_numbered, keyCode - KeyEvent.KEYCODE_BUTTON_1 + 1);
        }
        String name = KeyEvent.keyCodeToString(keyCode);
        if (name.startsWith("KEYCODE_")) {
            name = name.substring("KEYCODE_".length());
        }
        if (name.startsWith("BUTTON_")) {
            name = name.substring("BUTTON_".length());
        }
        // Keyboard keys get a hint, so "A" on the keyboard isn't mistaken for
        // the controller's A.
        boolean gamepadKey = KeyEvent.isGamepadButton(keyCode);
        return gamepadKey ? name : context.getString(R.string.key_keyboard, name);
    }
}
