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
    static final int BACKGROUND = 0xFF0E0E13;
    static final int SURFACE = 0xFF1A1A22;
    static final int SURFACE_HIGH = 0xFF26262F;
    static final int TEXT = 0xFFF2F2F7;
    static final int TEXT_DIM = 0xFF9A9AA8;
    static final int ACCENT = 0xFF8B5CF6;
    static final int ACCENT_DARK = 0xFF6D28D9;
    static final int DANGER = 0xFFF87171;

    /** Cover gradients, picked per game from its name. */
    private static final int[][] COVERS = {
        {0xFF7C3AED, 0xFF4338CA},
        {0xFFDB2777, 0xFF9333EA},
        {0xFFEA580C, 0xFFDC2626},
        {0xFF0891B2, 0xFF2563EB},
        {0xFF059669, 0xFF0D9488},
        {0xFFCA8A04, 0xFFEA580C},
        {0xFF4F46E5, 0xFF0EA5E9},
        {0xFFBE123C, 0xFF7C2D12},
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

    /**
     * Background for something tappable: a ripple on touch, and an accent outline when it has
     * focus, so it is clear what is selected when navigating with a controller.
     */
    static Drawable selectable(Context context, Drawable base, float radius) {
        GradientDrawable focus = new GradientDrawable();
        focus.setColor(0x00000000);
        focus.setCornerRadius(radius);
        focus.setStroke(dp(context, 3), ACCENT);

        StateListDrawable states = new StateListDrawable();
        states.addState(new int[] {android.R.attr.state_focused}, focus);
        states.addState(new int[] {}, new GradientDrawable());

        RippleDrawable ripple = new RippleDrawable(ColorStateList.valueOf(0x33FFFFFF),
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
