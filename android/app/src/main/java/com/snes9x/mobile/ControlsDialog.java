package com.snes9x.mobile;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

/**
 * Lets the player choose which controller button does each action: tap a button on the picture
 * of the controller (or one of the extra functions below it), then press the button for it in
 * the popup that opens.
 */
final class ControlsDialog extends Dialog {
    /** Actions that aren't on the SNES controller, listed under the drawing. */
    private static final ControllerMapping.Action[] EXTRAS = {
        ControllerMapping.Action.FAST_FORWARD, ControllerMapping.Action.REWIND,
        ControllerMapping.Action.MENU,
    };

    private final ControllerMapping mapping;
    private final ControllerDiagramView diagram;
    private final LinearLayout list;
    private final TextView hint;

    ControlsDialog(Context context, ControllerMapping mapping) {
        super(context);
        this.mapping = mapping;
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        int pad = Ui.dp(context, 20);
        LinearLayout panel = new LinearLayout(context);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(pad, pad, pad, Ui.dp(context, 12));
        panel.setBackground(ActionSheet.panelBackground(context));

        // Title, with an (i) for the tip about extra buttons.
        LinearLayout header = new LinearLayout(context);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(Ui.label(context, context.getString(R.string.controls_title), 24, Ui.TEXT),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView info = Ui.label(context, "i", 18, 0xFFFFFFFF);
        info.setGravity(Gravity.CENTER);
        info.setTypeface(android.graphics.Typeface.create("serif", android.graphics.Typeface.BOLD_ITALIC));
        float infoRadius = Ui.dp(context, 17);
        info.setBackground(Ui.selectable(context, Ui.rounded(Ui.SHELL, infoRadius), infoRadius));
        info.setFocusable(true);
        info.setClickable(true);
        info.setContentDescription(context.getString(R.string.controls_info_title));
        info.setOnClickListener(v -> {
            Ui.vibrate(context, false);
            new ActionSheet(context)
                    .title(context.getString(R.string.controls_info_title))
                    .subtitle(context.getString(R.string.controls_paddles))
                    .action(Ui.GREEN, R.drawable.ic_play, context.getString(R.string.controls_ok), null)
                    .show();
        });
        header.addView(info, new LinearLayout.LayoutParams(Ui.dp(context, 34), Ui.dp(context, 34)));
        panel.addView(header);

        hint = Ui.text(context, context.getString(R.string.controls_hint), 14, Ui.TEXT_DIM, false);
        hint.setPadding(0, Ui.dp(context, 2), 0, Ui.dp(context, 8));
        panel.addView(hint);

        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);

        diagram = new ControllerDiagramView(context);
        diagram.setListener(new ControllerDiagramView.Listener() {
            @Override
            public void onButtonPicked(ControllerMapping.Action action) {
                capture(action);
            }

            @Override
            public void onDpadPicked() {
                Ui.vibrate(context, false);
                hint.setText(R.string.controls_dpad);
            }

            @Override
            public void onButtonFocused(ControllerMapping.Action action) {
                if (Ui.keyNavigation) {
                    hint.setText(context.getString(R.string.controls_focused,
                            actionName(context, action), keysText(context, action)));
                }
            }
        });
        content.addView(diagram, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView extras = Ui.label(context, context.getString(R.string.controls_extra), 14, Ui.TEXT_DIM);
        extras.setAllCaps(true);
        extras.setLetterSpacing(0.08f);
        extras.setPadding(0, Ui.dp(context, 10), 0, Ui.dp(context, 2));
        content.addView(extras);

        list = new LinearLayout(context);
        list.setOrientation(LinearLayout.VERTICAL);
        content.addView(list);

        ScrollView scroll = new ScrollView(context);
        scroll.addView(content);
        panel.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        LinearLayout buttons = new LinearLayout(context);
        buttons.setGravity(Gravity.END);
        buttons.setPadding(0, Ui.dp(context, 10), 0, 0);
        buttons.addView(textButton(context, R.string.controls_reset, Ui.TEXT_DIM, v -> {
            mapping.reset();
            hint.setText(R.string.controls_hint);
            refresh();
        }));
        buttons.addView(textButton(context, R.string.controls_done, Ui.RED, v -> dismiss()));
        panel.addView(buttons);

        setContentView(panel);
        refresh();
    }

    @Override
    public void show() {
        Window window = getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setDimAmount(0.45f);
        }
        super.show();
        if (window != null) {
            int screenWidth = getContext().getResources().getDisplayMetrics().widthPixels;
            window.setLayout(Math.min(screenWidth - Ui.dp(getContext(), 24), Ui.dp(getContext(), 520)),
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            window.setGravity(Gravity.CENTER);
        }
        Ui.focusForKeys(diagram);
    }

    private void refresh() {
        diagram.invalidate();
        list.removeAllViews();
        Context context = getContext();
        for (ControllerMapping.Action action : EXTRAS) {
            list.addView(row(context, action));
        }
    }

    private View row(Context context, ControllerMapping.Action action) {
        float radius = Ui.dp(context, 14);
        LinearLayout row = new LinearLayout(context);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(Ui.dp(context, 52));
        row.setPadding(Ui.dp(context, 10), Ui.dp(context, 6), Ui.dp(context, 12), Ui.dp(context, 6));
        row.setBackground(Ui.selectable(context, Ui.rounded(0x00000000, radius), radius));
        row.setFocusable(true);
        row.setClickable(true);

        row.addView(chip(context, action, 38));

        LinearLayout labels = new LinearLayout(context);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.setPadding(Ui.dp(context, 14), 0, 0, 0);
        labels.addView(Ui.label(context, actionName(context, action), 16, Ui.TEXT));
        labels.addView(Ui.text(context, keysText(context, action), 13, Ui.TEXT_DIM, false));
        row.addView(labels, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        row.setOnClickListener(v -> capture(action));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = Ui.dp(context, 2);
        row.setLayoutParams(params);
        return row;
    }

    private String keysText(Context context, ControllerMapping.Action action) {
        List<Integer> keys = mapping.keysFor(action);
        StringBuilder out = new StringBuilder();
        for (int key : keys) {
            // Keyboard keys are listed only when nothing else is assigned.
            if (!KeyEvent.isGamepadButton(key) && keys.size() > 1) {
                continue;
            }
            if (out.length() > 0) {
                out.append(" · ");
            }
            out.append(ControllerMapping.keyName(context, key));
        }
        return out.length() > 0 ? out.toString() : context.getString(R.string.controls_none);
    }

    /** Opens the popup that waits for the new button. */
    private void capture(ControllerMapping.Action action) {
        Context context = getContext();
        Ui.vibrate(context, false);
        new KeyCaptureDialog(context, action, chip(context, action, 64), actionName(context, action),
                keysText(context, action), (picked, keyCode) -> {
                    mapping.assign(keyCode, picked);
                    hint.setText(R.string.controls_hint);
                    refresh();
                }).show();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        // A picks the selected button or row, B closes.
        int keyCode = event.getKeyCode();
        if (keyCode == KeyEvent.KEYCODE_BUTTON_A) {
            if (event.getAction() == KeyEvent.ACTION_UP) {
                View focused = getCurrentFocus();
                if (focused != null) {
                    focused.performClick();
                }
            }
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_BUTTON_B) {
            if (event.getAction() == KeyEvent.ACTION_UP) {
                dismiss();
            }
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    /**
     * The SNES button in its color (a grey pill for L, R, Select and Start), or the icon of an
     * extra function, {@code sizeDp} tall.
     */
    private static View chip(Context context, ControllerMapping.Action action, int sizeDp) {
        int size = Ui.dp(context, sizeDp);
        if (action.button == null) {
            View icon = Ui.faceButton(context, actionIcon(action), actionColor(action), size);
            icon.setLayoutParams(new LinearLayout.LayoutParams(size, size));
            return icon;
        }
        boolean wide = action.button.length() > 1;
        TextView chip = Ui.label(context, action.button, sizeDp * (wide ? 0.3f : 0.45f), 0xFFFFFFFF);
        chip.setGravity(Gravity.CENTER);
        chip.setBackground(Ui.rounded(chipColor(action), size / 2f));
        chip.setLayoutParams(new LinearLayout.LayoutParams(
                wide ? Math.round(size * 1.55f) : size, size));
        return chip;
    }

    static String actionName(Context context, ControllerMapping.Action action) {
        switch (action) {
            case FAST_FORWARD: return context.getString(R.string.fast_forward);
            case REWIND: return context.getString(R.string.rewind);
            case MENU: return context.getString(R.string.controls_menu);
            default: return context.getString(R.string.controls_button, action.button);
        }
    }

    private static int chipColor(ControllerMapping.Action action) {
        switch (action) {
            case A: return Ui.RED;
            case B: return Ui.YELLOW;
            case X: return Ui.BLUE;
            case Y: return Ui.GREEN;
            default: return Ui.SHELL;
        }
    }

    private static int actionIcon(ControllerMapping.Action action) {
        switch (action) {
            case FAST_FORWARD: return R.drawable.ic_fast_forward;
            case REWIND: return R.drawable.ic_fast_rewind;
            default: return R.drawable.ic_pause;
        }
    }

    private static int actionColor(ControllerMapping.Action action) {
        switch (action) {
            case FAST_FORWARD: return Ui.RED;
            case REWIND: return Ui.BLUE;
            default: return Ui.DARK;
        }
    }

    private static TextView textButton(Context context, int text, int color, View.OnClickListener click) {
        TextView button = Ui.label(context, context.getString(text), 16, color);
        button.setAllCaps(true);
        int pad = Ui.dp(context, 14);
        button.setPadding(pad, Ui.dp(context, 10), pad, Ui.dp(context, 10));
        float radius = Ui.dp(context, 12);
        button.setBackground(Ui.selectable(context, Ui.rounded(0x00000000, radius), radius));
        button.setFocusable(true);
        button.setClickable(true);
        button.setOnClickListener(v -> {
            Ui.vibrate(context, false);
            click.onClick(v);
        });
        return button;
    }
}
