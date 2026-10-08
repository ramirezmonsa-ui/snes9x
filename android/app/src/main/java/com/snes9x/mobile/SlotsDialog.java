package com.snes9x.mobile;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.text.format.DateUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Grid of save slots with a picture of each; picking one calls back with its number. */
final class SlotsDialog {
    interface Listener {
        void onSlotPicked(int slot);
    }

    private SlotsDialog() {
    }

    /** {@code onDismiss} runs after {@code listener}, once the dialog is gone. */
    static void show(Context context, SaveSlots slots, Listener listener, Runnable onDismiss) {
        Dialog dialog = new Dialog(context);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        int[] picked = {-1};

        int pad = Ui.dp(context, 20);
        LinearLayout panel = new LinearLayout(context);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(pad, pad, pad, pad);
        panel.setBackground(ActionSheet.panelBackground(context));

        panel.addView(Ui.label(context, context.getString(R.string.slots_title), 24, Ui.TEXT));
        TextView hint = Ui.text(context, context.getString(R.string.slots_hint), 14, Ui.TEXT_DIM, false);
        hint.setPadding(0, Ui.dp(context, 2), 0, Ui.dp(context, 12));
        panel.addView(hint);

        LinearLayout grid = new LinearLayout(context);
        grid.setOrientation(LinearLayout.VERTICAL);
        int columns = 3;
        int gap = Ui.dp(context, 10);
        LinearLayout row = null;
        View first = null;
        int total = SaveSlots.COUNT + 1;
        for (int i = 0; i < total; i++) {
            // Manual slots first, the automatic one last.
            int slot = i < SaveSlots.COUNT ? i + 1 : SaveSlots.AUTO;
            if (i % columns == 0) {
                row = new LinearLayout(context);
                LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                if (i > 0) {
                    rowParams.topMargin = gap;
                }
                grid.addView(row, rowParams);
            }
            View card = card(context, slots, slot, v -> {
                picked[0] = slot;
                dialog.dismiss();
            });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
            if (i % columns > 0) {
                params.leftMargin = gap;
            }
            row.addView(card, params);
            if (first == null) {
                first = card;
            }
        }
        ScrollView scroll = new ScrollView(context);
        scroll.addView(grid);
        panel.addView(scroll);

        dialog.setContentView(panel);
        ActionSheet.useControllerKeys(dialog);
        dialog.setOnDismissListener(d -> {
            if (picked[0] >= 0) {
                listener.onSlotPicked(picked[0]);
            }
            if (onDismiss != null) {
                onDismiss.run();
            }
        });

        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setDimAmount(0.45f);
        }
        dialog.show();
        if (window != null) {
            int screen = context.getResources().getDisplayMetrics().widthPixels;
            window.setLayout(Math.min(screen - Ui.dp(context, 32), Ui.dp(context, 520)),
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            window.setGravity(Gravity.CENTER);
        }
        if (first != null) {
            Ui.focusForKeys(first);
        }
    }

    private static View card(Context context, SaveSlots slots, int slot, View.OnClickListener click) {
        float radius = Ui.dp(context, 16);
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(context, 6);
        card.setPadding(pad, pad, pad, Ui.dp(context, 8));
        card.setBackground(Ui.selectable(context, Ui.rounded(Ui.BACKGROUND, radius), radius));
        card.setFocusable(true);
        card.setClickable(true);
        card.setOnClickListener(click);

        Bitmap picture = slots.thumbnail(slot);
        if (picture != null) {
            ImageView image = new ImageView(context);
            image.setImageBitmap(picture);
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            image.setBackground(Ui.rounded(0xFF000000, Ui.dp(context, 10)));
            image.setClipToOutline(true);
            card.addView(image, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(context, 66)));
        } else {
            TextView empty = Ui.text(context, context.getString(slots.exists(slot)
                    ? R.string.slot_saved : R.string.slot_empty), 13, Ui.TEXT_DIM, false);
            empty.setGravity(Gravity.CENTER);
            empty.setBackground(Ui.rounded(Ui.SURFACE_HIGH, Ui.dp(context, 10)));
            card.addView(empty, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(context, 66)));
        }

        String name = slot == SaveSlots.AUTO
                ? context.getString(R.string.slot_auto)
                : context.getString(R.string.slot_number, slot);
        TextView label = Ui.label(context, name, 14, Ui.TEXT);
        label.setPadding(Ui.dp(context, 4), Ui.dp(context, 6), 0, 0);
        card.addView(label);

        long time = slots.time(slot);
        TextView when = Ui.text(context, time > 0
                ? DateUtils.getRelativeTimeSpanString(time, System.currentTimeMillis(),
                        DateUtils.MINUTE_IN_MILLIS).toString()
                : "—", 11, Ui.TEXT_DIM, false);
        when.setSingleLine(true);
        when.setPadding(Ui.dp(context, 4), 0, 0, 0);
        card.addView(when);
        return card;
    }
}
