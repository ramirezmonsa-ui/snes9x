package com.snes9x.mobile;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.StateListDrawable;
import android.util.TypedValue;
import android.widget.TextView;

/** Colors and drawable helpers shared by the screens. */
final class Ui {
    // Colors of the Super Nintendo controller.
    static final int BACKGROUND = 0xFFE3E1DE;   // controller body
    static final int SURFACE = 0xFFF4F3F1;      // lighter plastic
    static final int SURFACE_HIGH = 0xFFCFCDC9;
    static final int SHELL = 0xFF8E8C92;        // grey area behind the buttons
    static final int DARK = 0xFF55545A;         // d-pad
    static final int TEXT = 0xFF2F2E34;
    static final int TEXT_DIM = 0xFF77757C;

    static final int RED = 0xFFD7262E;          // A
    static final int YELLOW = 0xFFF5B71F;       // B
    static final int BLUE = 0xFF1F55C6;         // X
    static final int GREEN = 0xFF1A9548;        // Y

    static final int ACCENT = RED;
    static final int FOCUS = BLUE;
    static final int DANGER = RED;

    /** Cover gradients, one per face button color, picked per game from its name. */
    private static final int[][] COVERS = {
        {0xFFE5434A, 0xFFB51B22},
        {0xFFF9C84A, 0xFFE09A0C},
        {0xFF3A6FDB, 0xFF173F9A},
        {0xFF2DB062, 0xFF117236},
    };

    private Ui() {
    }

    static int dp(Context context, float value) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
                context.getResources().getDisplayMetrics()));
    }

    static GradientDrawable rounded(int color, float radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        return drawable;
    }

    static GradientDrawable gradient(int[] colors, float radius) {
        GradientDrawable drawable = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR, colors);
        drawable.setCornerRadius(radius);
        return drawable;
    }

    static int[] coverColors(String name) {
        return COVERS[Math.abs(name.hashCode() % COVERS.length)];
    }

    /** Color of the console tag on game cards. */
    static int consoleColor(Console console) {
        switch (console) {
            case GBA: return 0xFF4C3FA8;   // the indigo of the original GBA
            case GBC: return 0xFF0E8A8A;
            case GB: return 0xFF6B7A2E;    // the green of the original screen
            default: return DARK;
        }
    }

    /** White reads poorly on the yellow cover, so that one gets dark text. */
    static int coverTextColor(String name) {
        return coverColors(name) == COVERS[1] ? 0xFF3B2A00 : 0xFFFFFFFF;
    }

    /**
     * Background for something tappable: a ripple on touch, and an accent outline when it has
     * focus, so it is clear what is selected when navigating with a controller.
     */
    static Drawable selectable(Context context, Drawable base, float radius) {
        GradientDrawable focus = new GradientDrawable();
        focus.setColor(0x00000000);
        focus.setCornerRadius(radius);
        focus.setStroke(dp(context, 3), FOCUS);

        StateListDrawable states = new StateListDrawable();
        states.addState(new int[] {android.R.attr.state_focused}, focus);
        states.addState(new int[] {}, new GradientDrawable());

        RippleDrawable ripple = new RippleDrawable(ColorStateList.valueOf(0x22000000),
                base, rounded(0xFFFFFFFF, radius));
        return new LayerDrawable(new Drawable[] {ripple, states});
    }

    static Drawable icon(Context context, int res, int color) {
        Drawable drawable = context.getDrawable(res).mutate();
        drawable.setTint(color);
        return drawable;
    }

    static TextView text(Context context, CharSequence value, float sp, int color, boolean bold) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        view.setTextColor(color);
        if (bold) {
            view.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        }
        return view;
    }

    /** Bold italic condensed text, like the SELECT and START labels. */
    static TextView label(Context context, CharSequence value, float sp, int color) {
        TextView view = text(context, value, sp, color, false);
        view.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD_ITALIC));
        return view;
    }

    /** A solid round "button" with a white icon, like the controller's face buttons. */
    static android.widget.ImageView faceButton(Context context, int iconRes, int color, int size) {
        android.widget.ImageView view = new android.widget.ImageView(context);
        view.setImageDrawable(icon(context, iconRes, 0xFFFFFFFF));
        int pad = size / 4;
        view.setPadding(pad, pad, pad, pad);
        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        circle.setColor(color);
        circle.setStroke(Math.max(1, size / 24), 0x33000000);
        view.setBackground(circle);
        view.setElevation(size / 12f);
        return view;
    }

    /** The four colored dots of the Super Nintendo logo. */
    static android.view.View logoDots(Context context) {
        android.widget.LinearLayout dots = new android.widget.LinearLayout(context);
        dots.setGravity(android.view.Gravity.CENTER_VERTICAL);
        int size = dp(context, 10);
        for (int color : new int[] {RED, YELLOW, GREEN, BLUE}) {
            android.view.View dot = new android.view.View(context);
            GradientDrawable circle = new GradientDrawable();
            circle.setShape(GradientDrawable.OVAL);
            circle.setColor(color);
            dot.setBackground(circle);
            android.widget.LinearLayout.LayoutParams params =
                    new android.widget.LinearLayout.LayoutParams(size, size);
            params.rightMargin = dp(context, 4);
            dots.addView(dot, params);
        }
        return dots;
    }

    /** "Super Mario World" becomes "SM", "zelda" becomes "Z". */
    static String initials(String name) {
        StringBuilder out = new StringBuilder();
        for (String word : name.replaceAll("[^\\p{L}\\p{N} ]", " ").trim().split("\\s+")) {
            if (!word.isEmpty()) {
                out.appendCodePoint(Character.toUpperCase(word.codePointAt(0)));
                if (out.length() >= 2) {
                    break;
                }
            }
        }
        return out.length() == 0 ? "?" : out.toString();
    }
}
