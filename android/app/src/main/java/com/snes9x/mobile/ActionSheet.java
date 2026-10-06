package com.snes9x.mobile;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * A rounded panel with a title and a list of big actions. Works with touch and with a
 * controller: the d-pad moves between actions, A picks one and B closes the panel.
 */
final class ActionSheet {
    private static final class Action {
        final int icon;
        final CharSequence label;
        final CharSequence detail;
        final boolean danger;
        final Runnable run;

        Action(int icon, CharSequence label, CharSequence detail, boolean danger, Runnable run) {
            this.icon = icon;
            this.label = label;
            this.detail = detail;
            this.danger = danger;
            this.run = run;
        }
    }

    private final Context context;
    private final List<Action> actions = new ArrayList<>();
    private CharSequence title;
    private CharSequence subtitle;
    private Runnable onDismiss;
    private Action chosen;

    ActionSheet(Context context) {
        this.context = context;
    }

    ActionSheet title(CharSequence title) {
        this.title = title;
        return this;
    }

    ActionSheet subtitle(CharSequence subtitle) {
        this.subtitle = subtitle;
        return this;
    }

    ActionSheet action(int icon, CharSequence label, Runnable run) {
        return action(icon, label, null, run);
    }

    ActionSheet action(int icon, CharSequence label, CharSequence detail, Runnable run) {
        actions.add(new Action(icon, label, detail, false, run));
        return this;
    }

    ActionSheet danger(int icon, CharSequence label, Runnable run) {
        actions.add(new Action(icon, label, null, true, run));
        return this;
    }

    ActionSheet onDismiss(Runnable onDismiss) {
        this.onDismiss = onDismiss;
        return this;
    }

    Dialog show() {
        Dialog dialog = new Dialog(context);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        int pad = Ui.dp(context, 20);
        LinearLayout panel = new LinearLayout(context);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(pad, pad, pad, Ui.dp(context, 12));
        panel.setBackground(Ui.rounded(Ui.SURFACE, Ui.dp(context, 28)));

        if (title != null) {
            TextView titleView = Ui.text(context, title, 22, Ui.TEXT, true);
            titleView.setMaxLines(2);
            titleView.setEllipsize(TextUtils.TruncateAt.END);
            panel.addView(titleView);
        }
        if (subtitle != null) {
            TextView subtitleView = Ui.text(context, subtitle, 14, Ui.TEXT_DIM, false);
            subtitleView.setPadding(0, Ui.dp(context, 2), 0, 0);
            panel.addView(subtitleView);
        }

        LinearLayout list = new LinearLayout(context);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(0, Ui.dp(context, 12), 0, 0);
        View first = null;
        for (Action action : actions) {
            View row = row(action, dialog);
            list.addView(row);
            if (first == null) {
                first = row;
            }
        }
        ScrollView scroll = new ScrollView(context);
        scroll.addView(list);
        panel.addView(scroll);

        dialog.setContentView(panel);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setGravity(Gravity.CENTER);
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setDimAmount(0.6f);
        }

        dialog.setOnKeyListener((d, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_UP) {
                return keyCode == KeyEvent.KEYCODE_BUTTON_A || keyCode == KeyEvent.KEYCODE_BUTTON_B;
            }
            if (keyCode == KeyEvent.KEYCODE_BUTTON_A) {
                View focused = dialog.getCurrentFocus();
                if (focused != null) {
                    focused.performClick();
                }
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_BUTTON_B) {
                dialog.cancel();
                return true;
            }
            return false;
        });
        // The chosen action runs once the panel is gone, before onDismiss, so
        // an action can open another panel.
        dialog.setOnDismissListener(d -> {
            if (chosen != null && chosen.run != null) {
                chosen.run.run();
            }
            if (onDismiss != null) {
                onDismiss.run();
            }
        });

        dialog.show();
        if (window != null) {
            int screen = context.getResources().getDisplayMetrics().widthPixels;
            int width = Math.min(screen - Ui.dp(context, 32), Ui.dp(context, 420));
            window.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        if (first != null) {
            first.requestFocus();
        }
        return dialog;
    }

    private View row(Action action, Dialog dialog) {
        int color = action.danger ? Ui.DANGER : Ui.TEXT;
        float radius = Ui.dp(context, 16);

        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(Ui.dp(context, 60));
        int pad = Ui.dp(context, 14);
        row.setPadding(pad, Ui.dp(context, 8), pad, Ui.dp(context, 8));
        row.setBackground(Ui.selectable(context, Ui.rounded(0x00000000, radius), radius));
        row.setFocusable(true);
        row.setClickable(true);

        // Icon in a soft tinted circle.
        ImageView icon = new ImageView(context);
        icon.setImageDrawable(Ui.icon(context, action.icon, action.danger ? Ui.DANGER : Ui.ACCENT));
        int iconPad = Ui.dp(context, 9);
        icon.setPadding(iconPad, iconPad, iconPad, iconPad);
        icon.setBackground(Ui.rounded(action.danger ? 0x26F87171 : 0x268B5CF6, Ui.dp(context, 21)));
        row.addView(icon, new LinearLayout.LayoutParams(Ui.dp(context, 42), Ui.dp(context, 42)));

        LinearLayout labels = new LinearLayout(context);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.setPadding(Ui.dp(context, 16), 0, 0, 0);
        labels.addView(Ui.text(context, action.label, 17, color, true));
        if (action.detail != null) {
            labels.addView(Ui.text(context, action.detail, 13, Ui.TEXT_DIM, false));
        }
        row.addView(labels, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        row.setOnClickListener(v -> {
            chosen = action;
            dialog.dismiss();
        });

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = Ui.dp(context, 4);
        row.setLayoutParams(params);
        return row;
    }
}
