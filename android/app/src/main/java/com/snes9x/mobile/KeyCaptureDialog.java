package com.snes9x.mobile;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Picks the controller button for one action: the SNES button on the left, an arrow, and on the
 * right an empty box that shows each button pressed on the player's controller. Pressing another
 * one replaces it; nothing is saved until OK.
 */
final class KeyCaptureDialog extends Dialog {
    interface Listener {
        void onKeyPicked(ControllerMapping.Action action, int keyCode);
    }

    private final ControllerMapping.Action action;
    private final Listener listener;
    private final TextView box;
    private final TextView ok;
    private final TextView cancel;
    private final ObjectAnimator pulse;
    private int keyCode = KeyEvent.KEYCODE_UNKNOWN;

    /**
     * @param chip the SNES button (or the extra function's icon) shown on the left
     * @param current what presses this action now
     */
    KeyCaptureDialog(Context context, ControllerMapping.Action action, View chip, String title,
            String current, Listener listener) {
        super(context);
        this.action = action;
        this.listener = listener;
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        int pad = Ui.dp(context, 22);
        LinearLayout panel = new LinearLayout(context);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(Gravity.CENTER_HORIZONTAL);
        panel.setPadding(pad, pad, pad, Ui.dp(context, 12));
        panel.setBackground(ActionSheet.panelBackground(context));

        panel.addView(Ui.label(context, title, 22, Ui.TEXT));

        // SNES button → controller button.
        LinearLayout pair = new LinearLayout(context);
        pair.setGravity(Gravity.CENTER);
        pair.setPadding(0, Ui.dp(context, 18), 0, Ui.dp(context, 8));
        pair.addView(chip);

        TextView arrow = Ui.label(context, "→", 34, Ui.TEXT_DIM);
        arrow.setGravity(Gravity.CENTER);
        arrow.setPadding(Ui.dp(context, 14), 0, Ui.dp(context, 14), Ui.dp(context, 4));
        pair.addView(arrow);

        int size = Ui.dp(context, 64);
        box = Ui.label(context, "", 24, 0xFFFFFFFF);
        box.setGravity(Gravity.CENTER);
        box.setMinWidth(size);
        box.setPadding(Ui.dp(context, 12), 0, Ui.dp(context, 12), 0);
        box.setSingleLine(true);
        box.setBackground(emptyBox(context));
        // While the box has the focus, every button pressed goes into it.
        box.setFocusable(true);
        pair.addView(box, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, size));
        panel.addView(pair);

        TextView message = Ui.text(context, context.getString(R.string.controls_capture_prompt), 15,
                Ui.TEXT, false);
        message.setGravity(Gravity.CENTER);
        panel.addView(message);
        TextView now = Ui.label(context, context.getString(R.string.controls_capture_now, current),
                14, Ui.TEXT_DIM);
        now.setGravity(Gravity.CENTER);
        now.setPadding(0, Ui.dp(context, 4), 0, 0);
        panel.addView(now);

        LinearLayout buttons = new LinearLayout(context);
        buttons.setGravity(Gravity.END);
        buttons.setPadding(0, Ui.dp(context, 12), 0, 0);
        cancel = textButton(context, R.string.cancel, Ui.TEXT_DIM);
        cancel.setOnClickListener(v -> {
            Ui.vibrate(context, false);
            cancel();
        });
        buttons.addView(cancel);
        ok = textButton(context, R.string.controls_capture_ok, Ui.RED);
        ok.setOnClickListener(v -> save());
        setOkEnabled(false);
        buttons.addView(ok);
        panel.addView(buttons, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        setContentView(panel);

        // The empty box breathes while waiting for the first button.
        pulse = ObjectAnimator.ofFloat(box, View.ALPHA, 1f, 0.4f);
        pulse.setDuration(700);
        pulse.setRepeatMode(ValueAnimator.REVERSE);
        pulse.setRepeatCount(ValueAnimator.INFINITE);
        setOnDismissListener(d -> pulse.cancel());
    }

    private static GradientDrawable emptyBox(Context context) {
        GradientDrawable empty = Ui.rounded(0x00000000, Ui.dp(context, 18));
        empty.setStroke(Ui.dp(context, 2), Ui.TEXT_DIM, Ui.dp(context, 6), Ui.dp(context, 4));
        return empty;
    }

    private static TextView textButton(Context context, int text, int color) {
        TextView button = Ui.label(context, context.getString(text), 16, color);
        button.setAllCaps(true);
        int pad = Ui.dp(context, 16);
        button.setPadding(pad, Ui.dp(context, 10), pad, Ui.dp(context, 10));
        float radius = Ui.dp(context, 12);
        button.setBackground(Ui.selectable(context, Ui.rounded(0x00000000, radius), radius));
        button.setFocusable(true);
        button.setClickable(true);
        return button;
    }

    private void setOkEnabled(boolean enabled) {
        ok.setEnabled(enabled);
        ok.setFocusable(enabled);
        ok.setAlpha(enabled ? 1f : 0.35f);
    }

    @Override
    public void show() {
        Window window = getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setDimAmount(0.35f);
        }
        super.show();
        if (window != null) {
            int screenWidth = getContext().getResources().getDisplayMetrics().widthPixels;
            window.setLayout(Math.min(screenWidth - Ui.dp(getContext(), 48), Ui.dp(getContext(), 380)),
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            window.setGravity(Gravity.CENTER);
        }
        box.requestFocus();
        pulse.start();
    }

    /** Shows the pressed button in the box; it replaces the one shown before. */
    private void pressed(int code) {
        keyCode = code;
        pulse.cancel();
        box.setAlpha(1f);
        box.setText(ControllerMapping.keyName(getContext(), code));
        box.setBackground(Ui.rounded(Ui.DARK, Ui.dp(getContext(), 18)));
        setOkEnabled(true);
        Ui.vibrate(getContext(), false);
    }

    private void save() {
        if (keyCode == KeyEvent.KEYCODE_UNKNOWN) {
            return;
        }
        Ui.vibrate(getContext(), true);
        listener.onKeyPicked(action, keyCode);
        dismiss();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        int code = event.getKeyCode();
        boolean down = event.getAction() == KeyEvent.ACTION_DOWN;
        if (code == KeyEvent.KEYCODE_BACK) {
            if (!down) {
                cancel();
            }
            return true;
        }
        // The d-pad can't be assigned; it moves between the box and the
        // buttons, so OK can be pressed from a controller too.
        if (ControllerMapping.isDpad(code)) {
            if (down) {
                if (code == KeyEvent.KEYCODE_DPAD_DOWN || code == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    (ok.isEnabled() ? ok : cancel).requestFocus();
                } else if (code == KeyEvent.KEYCODE_DPAD_UP) {
                    box.requestFocus();
                } else if (code == KeyEvent.KEYCODE_DPAD_LEFT) {
                    (getCurrentFocus() == ok ? cancel : box).requestFocus();
                }
            }
            return true;
        }
        View focused = getCurrentFocus();
        boolean onButton = focused == ok || focused == cancel;
        if (onButton && (code == KeyEvent.KEYCODE_BUTTON_A || code == KeyEvent.KEYCODE_ENTER)) {
            if (!down) {
                focused.performClick();
            }
            return true;
        }
        if (down && event.getRepeatCount() == 0) {
            pressed(code);
            box.requestFocus();
        }
        return true;
    }

    /** Analog triggers that don't send key presses count as LT and RT. */
    @Override
    public boolean dispatchGenericMotionEvent(MotionEvent event) {
        if ((event.getSource() & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK) {
            float left = Math.max(event.getAxisValue(MotionEvent.AXIS_LTRIGGER),
                    event.getAxisValue(MotionEvent.AXIS_BRAKE));
            float right = Math.max(event.getAxisValue(MotionEvent.AXIS_RTRIGGER),
                    event.getAxisValue(MotionEvent.AXIS_GAS));
            int code = left > 0.5f ? KeyEvent.KEYCODE_BUTTON_L2
                    : right > 0.5f ? KeyEvent.KEYCODE_BUTTON_R2 : KeyEvent.KEYCODE_UNKNOWN;
            if (code != KeyEvent.KEYCODE_UNKNOWN && code != keyCode) {
                pressed(code);
            }
            return true;
        }
        return super.dispatchGenericMotionEvent(event);
    }
}
