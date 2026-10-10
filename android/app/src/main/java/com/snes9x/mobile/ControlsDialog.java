package com.snes9x.mobile;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

/**
 * Lets the player choose which controller button does each action: tap a button on the drawing
 * of the controller (or one of the extra functions below it), then press the button for it.
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

    /** The action waiting for a button press, or null. */
    private ControllerMapping.Action listening;
    private TextView listeningLabel;

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
        info.setOnClickListener(v -> new ActionSheet(context)
                .title(context.getString(R.string.controls_info_title))
                .subtitle(context.getString(R.string.controls_paddles))
                .action(Ui.GREEN, R.drawable.ic_play, context.getString(R.string.controls_ok), null)
                .show());
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
                startListening(action, null);
            }

            @Override
            public void onDpadPicked() {
                stopListening();
                hint.setText(R.string.controls_dpad);
            }

            @Override
            public void onButtonFocused(ControllerMapping.Action action) {
                if (listening == null && Ui.keyNavigation) {
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
            stopListening();
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

        int size = Ui.dp(context, 38);
        if (action.button != null) {
            TextView chip = Ui.label(context, action.button, action.button.length() > 1 ? 11 : 17,
                    0xFFFFFFFF);
            chip.setGravity(Gravity.CENTER);
            chip.setBackground(Ui.rounded(chipColor(action), size / 2f));
            row.addView(chip, new LinearLayout.LayoutParams(
                    action.button.length() > 1 ? Ui.dp(context, 58) : size, size));
        } else {
            row.addView(Ui.faceButton(context, actionIcon(action), actionColor(action), size),
                    new LinearLayout.LayoutParams(size, size));
        }

        LinearLayout labels = new LinearLayout(context);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.setPadding(Ui.dp(context, 14), 0, 0, 0);
        labels.addView(Ui.label(context, actionName(context, action), 16, Ui.TEXT));
        TextView keys = Ui.text(context, keysText(context, action), 13, Ui.TEXT_DIM, false);
        labels.addView(keys);
        row.addView(labels, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        row.setOnClickListener(v -> startListening(action, keys));
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

    private void startListening(ControllerMapping.Action action, TextView label) {
        stopListening();
        listening = action;
        listeningLabel = label;
        if (label != null) {
            label.setText(R.string.controls_press);
            label.setTextColor(Ui.RED);
        }
        hint.setText(getContext().getString(R.string.controls_listening,
                actionName(getContext(), action), keysText(getContext(), action)));
    }

    private void stopListening() {
        listening = null;
        listeningLabel = null;
        hint.setText(R.string.controls_hint);
    }

    private void assign(int keyCode) {
        ControllerMapping.Action action = listening;
        stopListening();
        mapping.assign(keyCode, action);
        refresh();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        int keyCode = event.getKeyCode();
        if (listening != null) {
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                if (event.getAction() == KeyEvent.ACTION_UP) {
                    stopListening();
                    refresh();
                }
                return true;
            }
            if (ControllerMapping.isDpad(keyCode)) {
                return true;  // the d-pad always moves
            }
            if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
                assign(keyCode);
            }
            return true;
        }
        // While not listening: A picks the selected row, B closes.
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

    /** Analog triggers that don't send key presses count as LT and RT. */
    @Override
    public boolean dispatchGenericMotionEvent(MotionEvent event) {
        if (listening != null
                && (event.getSource() & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK) {
            float left = Math.max(event.getAxisValue(MotionEvent.AXIS_LTRIGGER),
                    event.getAxisValue(MotionEvent.AXIS_BRAKE));
            float right = Math.max(event.getAxisValue(MotionEvent.AXIS_RTRIGGER),
                    event.getAxisValue(MotionEvent.AXIS_GAS));
            if (left > 0.5f) {
                assign(KeyEvent.KEYCODE_BUTTON_L2);
                return true;
            }
            if (right > 0.5f) {
                assign(KeyEvent.KEYCODE_BUTTON_R2);
                return true;
            }
        }
        return super.dispatchGenericMotionEvent(event);
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
        button.setOnClickListener(click);
        return button;
    }
}
