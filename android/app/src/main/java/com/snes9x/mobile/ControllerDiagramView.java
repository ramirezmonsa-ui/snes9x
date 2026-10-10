package com.snes9x.mobile;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.SoundEffectConstants;
import android.view.View;

import java.util.EnumMap;
import java.util.Map;

/**
 * A picture of a Super Nintendo controller. Tapping a button picks it to be changed.
 */
final class ControllerDiagramView extends View {
    interface Listener {
        void onButtonPicked(ControllerMapping.Action action);

        /** The d-pad was tapped; it can't be reassigned. */
        void onDpadPicked();

        /** A controller's d-pad moved to this button; nothing on the picture marks it. */
        void onButtonFocused(ControllerMapping.Action action);
    }

    /** Where a button is on the picture, in layout units (the view is 100 units wide). */
    private static final class Spot {
        final float x;
        final float y;
        final float minWidth;

        Spot(float x, float y, float minWidth) {
            this.x = x;
            this.y = y;
            this.minWidth = minWidth;
        }
    }

    // Order used when moving between buttons with a controller's d-pad.
    private static final ControllerMapping.Action[] ORDER = {
        ControllerMapping.Action.L, ControllerMapping.Action.R,
        ControllerMapping.Action.SELECT, ControllerMapping.Action.START,
        ControllerMapping.Action.Y, ControllerMapping.Action.X,
        ControllerMapping.Action.A, ControllerMapping.Action.B,
    };

    // The picture fills the width, a little below the top so the shoulder
    // badges fit.
    private static final float PICTURE_TOP = 4;
    private static final float HEIGHT = 50;
    private static final float DPAD_X = 21.4f;
    private static final float DPAD_Y = 26.2f;
    private static final float DPAD_R = 9.5f;
    private static final float FACE_R = 3.5f;

    private final Map<ControllerMapping.Action, Spot> spots =
            new EnumMap<>(ControllerMapping.Action.class);
    private final Bitmap picture;
    private final Paint bitmapPaint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    private Listener listener;
    private int keyboardIndex;

    ControllerDiagramView(Context context) {
        super(context);
        picture = BitmapFactory.decodeResource(context.getResources(), R.drawable.controller_picture);
        setFocusable(true);
        setClickable(true);

        spots.put(ControllerMapping.Action.L, new Spot(21.8f, 5.2f, 20));
        spots.put(ControllerMapping.Action.R, new Spot(78.2f, 5.2f, 20));
        spots.put(ControllerMapping.Action.SELECT, new Spot(40.5f, 30.2f, 8));
        spots.put(ControllerMapping.Action.START, new Spot(51.6f, 30.2f, 8));
        spots.put(ControllerMapping.Action.X, new Spot(78.5f, 19f, 2 * FACE_R));
        spots.put(ControllerMapping.Action.Y, new Spot(69f, 26.5f, 2 * FACE_R));
        spots.put(ControllerMapping.Action.A, new Spot(88.2f, 26.4f, 2 * FACE_R));
        spots.put(ControllerMapping.Action.B, new Spot(78.7f, 33.9f, 2 * FACE_R));
    }

    void setListener(Listener listener) {
        this.listener = listener;
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int width = MeasureSpec.getSize(widthSpec);
        setMeasuredDimension(width, Math.round(width * HEIGHT / 100f));
    }

    private float u() {
        return getWidth() / 100f;
    }

    // --- Drawing -------------------------------------------------------------

    @Override
    protected void onDraw(Canvas canvas) {
        float u = u();
        if (u <= 0) {
            return;
        }
        if (picture != null) {
            float height = 100 * u * picture.getHeight() / picture.getWidth();
            rect.set(0, PICTURE_TOP * u, 100 * u, PICTURE_TOP * u + height);
            canvas.drawBitmap(picture, null, rect, bitmapPaint);
        }
    }

    // --- Picking -------------------------------------------------------------

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getActionMasked() != MotionEvent.ACTION_UP) {
            return true;
        }
        float u = u();
        float x = event.getX() / u;
        float y = event.getY() / u;
        ControllerMapping.Action hit = hitTest(x, y);
        if (hit != null) {
            playSoundEffect(SoundEffectConstants.CLICK);
            keyboardIndex = indexOf(hit);
            if (listener != null) {
                listener.onButtonPicked(hit);
            }
        } else if (Math.hypot(x - DPAD_X, y - DPAD_Y) < DPAD_R && listener != null) {
            listener.onDpadPicked();
        }
        return true;
    }

    /** The button nearest to (x, y), if it is close enough for a finger. */
    private ControllerMapping.Action hitTest(float x, float y) {
        ControllerMapping.Action best = null;
        double bestDistance = 0;
        for (Map.Entry<ControllerMapping.Action, Spot> entry : spots.entrySet()) {
            Spot spot = entry.getValue();
            // Wide buttons (shoulders, Select, Start) reach further sideways.
            double dx = Math.max(0, Math.abs(x - spot.x) - (spot.minWidth / 2 - FACE_R));
            double distance = Math.hypot(dx, y - spot.y);
            if (distance < 6 && (best == null || distance < bestDistance)) {
                best = entry.getKey();
                bestDistance = distance;
            }
        }
        return best;
    }

    private static int indexOf(ControllerMapping.Action action) {
        for (int i = 0; i < ORDER.length; i++) {
            if (ORDER[i] == action) {
                return i;
            }
        }
        return 0;
    }

    // With a controller or keyboard: the d-pad moves between buttons, and A
    // or Enter picks the current one.
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT || keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
            if (keyboardIndex < ORDER.length - 1) {
                keyboardIndex++;
                announceFocus();
                return true;
            }
            return false;  // focus moves on to the buttons below
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_UP) {
            if (keyboardIndex > 0) {
                keyboardIndex--;
                announceFocus();
                return true;
            }
            return false;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean performClick() {
        super.performClick();
        if (listener != null) {
            listener.onButtonPicked(ORDER[keyboardIndex]);
        }
        return true;
    }

    @Override
    protected void onFocusChanged(boolean gainFocus, int direction, android.graphics.Rect previous) {
        super.onFocusChanged(gainFocus, direction, previous);
        if (gainFocus) {
            announceFocus();
        }
    }

    private void announceFocus() {
        if (listener != null) {
            listener.onButtonFocused(ORDER[keyboardIndex]);
        }
    }
}
