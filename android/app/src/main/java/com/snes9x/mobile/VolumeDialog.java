package com.snes9x.mobile;

import android.app.Dialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

/**
 * The game's volume: a big percentage, a slider and − / + buttons, in steps of 10%. Works by
 * touch and with a controller (the d-pad moves the slider). Changes apply as they are made.
 */
final class VolumeDialog {
    interface Listener {
        void onVolumeChanged(int percent);
    }

    private static final int STEP = 10;

    private VolumeDialog() {
    }

    static void show(Context context, int percent, Listener listener, Runnable onDismiss) {
        Dialog dialog = new Dialog(context);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        int pad = Ui.dp(context, 22);
        LinearLayout panel = new LinearLayout(context);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(pad, pad, pad, Ui.dp(context, 12));
        panel.setBackground(ActionSheet.panelBackground(context));

        panel.addView(Ui.label(context, context.getString(R.string.volume_title), 24, Ui.TEXT));
        TextView subtitle = Ui.text(context, context.getString(R.string.volume_subtitle), 14,
                Ui.TEXT_DIM, false);
        subtitle.setPadding(0, Ui.dp(context, 2), 0, 0);
        panel.addView(subtitle);

        TextView value = Ui.label(context, "", 40, Ui.TEXT);
        value.setGravity(Gravity.CENTER);
        value.setPadding(0, Ui.dp(context, 14), 0, Ui.dp(context, 4));
        panel.addView(value, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        SeekBar slider = new SeekBar(context);
        slider.setMax(100 / STEP);
        slider.setKeyProgressIncrement(1);
        slider.setProgressTintList(ColorStateList.valueOf(Ui.RED));
        slider.setThumbTintList(ColorStateList.valueOf(Ui.RED));
        slider.setProgressBackgroundTintList(ColorStateList.valueOf(Ui.SURFACE_HIGH));
        slider.setFocusable(true);

        int buttonSize = Ui.dp(context, 48);
        TextView minus = roundButton(context, "−");
        TextView plus = roundButton(context, "+");

        LinearLayout row = new LinearLayout(context);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(minus, new LinearLayout.LayoutParams(buttonSize, buttonSize));
        LinearLayout.LayoutParams sliderParams = new LinearLayout.LayoutParams(
                0, Ui.dp(context, 48), 1);
        sliderParams.leftMargin = Ui.dp(context, 6);
        sliderParams.rightMargin = Ui.dp(context, 6);
        row.addView(slider, sliderParams);
        row.addView(plus, new LinearLayout.LayoutParams(buttonSize, buttonSize));
        panel.addView(row);

        Runnable show = () -> {
            int current = slider.getProgress() * STEP;
            value.setText(current == 0 ? context.getString(R.string.volume_mute) : current + "%");
        };
        slider.setProgress(Math.round(percent / (float) STEP));
        show.run();
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                show.run();
                Ui.vibrate(context, false);
                listener.onVolumeChanged(progress * STEP);
            }

            @Override
            public void onStartTrackingTouch(SeekBar bar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar bar) {
            }
        });
        minus.setOnClickListener(v -> slider.setProgress(slider.getProgress() - 1));
        plus.setOnClickListener(v -> slider.setProgress(slider.getProgress() + 1));

        TextView done = Ui.label(context, context.getString(R.string.volume_done), 16, Ui.RED);
        done.setAllCaps(true);
        int buttonPad = Ui.dp(context, 16);
        done.setPadding(buttonPad, Ui.dp(context, 10), buttonPad, Ui.dp(context, 10));
        float radius = Ui.dp(context, 12);
        done.setBackground(Ui.selectable(context, Ui.rounded(0x00000000, radius), radius));
        done.setFocusable(true);
        done.setClickable(true);
        done.setOnClickListener(v -> dialog.dismiss());
        LinearLayout.LayoutParams doneParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        doneParams.gravity = Gravity.END;
        doneParams.topMargin = Ui.dp(context, 8);
        panel.addView(done, doneParams);

        dialog.setContentView(panel);
        ActionSheet.useControllerKeys(dialog);
        if (onDismiss != null) {
            dialog.setOnDismissListener(d -> onDismiss.run());
        }

        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setDimAmount(0.45f);
        }
        dialog.show();
        if (window != null) {
            int screen = context.getResources().getDisplayMetrics().widthPixels;
            window.setLayout(Math.min(screen - Ui.dp(context, 32), Ui.dp(context, 420)),
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            window.setGravity(Gravity.CENTER);
        }
        Ui.focusForKeys(slider);
    }

    private static TextView roundButton(Context context, String symbol) {
        TextView button = Ui.label(context, symbol, 26, 0xFFFFFFFF);
        button.setGravity(Gravity.CENTER);
        float radius = Ui.dp(context, 24);
        button.setBackground(Ui.selectable(context, Ui.rounded(Ui.SHELL, radius), radius));
        button.setClickable(true);
        // The d-pad moves the slider; the buttons are for touch.
        button.setFocusable(false);
        return button;
    }
}
