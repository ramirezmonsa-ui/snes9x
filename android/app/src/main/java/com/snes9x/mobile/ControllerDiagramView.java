package com.snes9x.mobile;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.SoundEffectConstants;
import android.view.View;

import java.util.EnumMap;
import java.util.Map;

/**
 * A picture of a Super Nintendo controller with a badge on each button showing which button of
 * the player's controller presses it. Tapping a button picks it to be changed.
 */
final class ControllerDiagramView extends View {
    interface Listener {
        void onButtonPicked(ControllerMapping.Action action);

        /** The d-pad was tapped; it can't be reassigned. */
        void onDpadPicked();
    }

    /** Text for each badge: the controller button that presses it. */
    interface Labels {
        String keysFor(ControllerMapping.Action action);
    }

    /** Where a button is on the picture, in layout units (the view is 100 units wide). */
    private static final class Spot {
        final float x;
        final float y;
        final float minWidth;
        final int color;

        Spot(float x, float y, float minWidth, int color) {
            this.x = x;
            this.y = y;
            this.minWidth = minWidth;
            this.color = color;
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
    private static final float BADGE_HEIGHT = 6.4f;
    private static final float DPAD_X = 21.6f;
    private static final float DPAD_Y = 27.4f;
    private static final float DPAD_R = 8.5f;
    private static final float FACE_R = 3.5f;
    private static final int BADGE_DARK = 0xE6404045;

    private final Map<ControllerMapping.Action, Spot> spots =
            new EnumMap<>(ControllerMapping.Action.class);
    private final Bitmap picture;
    private final Paint bitmapPaint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final RectF ring = new RectF();

    private Listener listener;
    private Labels labels;
    private ControllerMapping.Action selected;
    private int keyboardIndex;

    ControllerDiagramView(Context context) {
        super(context);
        picture = BitmapFactory.decodeResource(context.getResources(), R.drawable.controller_picture);
        stroke.setStyle(Paint.Style.STROKE);
        text.setTextAlign(Paint.Align.CENTER);
        text.setTypeface(Typeface.DEFAULT_BOLD);
        setFocusable(true);
        setClickable(true);

        spots.put(ControllerMapping.Action.L, new Spot(23.2f, 5.2f, 11, BADGE_DARK));
        spots.put(ControllerMapping.Action.R, new Spot(76.8f, 5.2f, 11, BADGE_DARK));
        spots.put(ControllerMapping.Action.SELECT, new Spot(36.5f, 35.5f, 9, BADGE_DARK));
        spots.put(ControllerMapping.Action.START, new Spot(55.5f, 35.5f, 9, BADGE_DARK));
        spots.put(ControllerMapping.Action.X, new Spot(78.2f, 19.4f, 2 * FACE_R, Ui.BLUE));
        spots.put(ControllerMapping.Action.Y, new Spot(68.9f, 26.6f, 2 * FACE_R, Ui.GREEN));
        spots.put(ControllerMapping.Action.A, new Spot(87.7f, 26.4f, 2 * FACE_R, Ui.RED));
        spots.put(ControllerMapping.Action.B, new Spot(78.4f, 33.7f, 2 * FACE_R, Ui.YELLOW));
    }

    void setListener(Listener listener) {
        this.listener = listener;
    }

    void setLabels(Labels labels) {
        this.labels = labels;
        invalidate();
    }

    void setSelected(ControllerMapping.Action action) {
        selected = action;
        invalidate();
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
        for (ControllerMapping.Action action : ORDER) {
            drawBadge(canvas, action, spots.get(action), u);
        }
    }

    /** A pill over the button, in its color, with the controller key that presses it. */
    private void drawBadge(Canvas canvas, ControllerMapping.Action action, Spot spot, float u) {
        boolean picked = action == selected;
        boolean focusedHere = isFocused() && ORDER[keyboardIndex] == action;
        String keys = labels != null ? labels.keysFor(action) : "";
        String shown = picked ? "?" : keys;
        text.setTextSize(3.5f * u);
        float padding = 2.4f * u;
        float maxWidth = 22 * u;
        while (shown.length() > 1 && text.measureText(shown) + padding > maxWidth) {
            shown = shown.substring(0, shown.length() - 2) + "…";
        }
        float width = Math.max(spot.minWidth * u, text.measureText(shown) + padding);
        float height = BADGE_HEIGHT * u;
        rect.set(spot.x * u - width / 2, spot.y * u - height / 2,
                spot.x * u + width / 2, spot.y * u + height / 2);
        float radius = height / 2;

        if (picked || focusedHere) {
            // A ring around the badge: red while waiting for a button, blue
            // for the one a controller's d-pad is on.
            float out = 1.3f * u;
            ring.set(rect.left - out, rect.top - out, rect.right + out, rect.bottom + out);
            fill.setColor(0xFFFFFFFF);
            canvas.drawRoundRect(ring, radius + out, radius + out, fill);
            stroke.setColor(picked ? Ui.RED : Ui.FOCUS);
            stroke.setStrokeWidth(0.9f * u);
            canvas.drawRoundRect(ring, radius + out, radius + out, stroke);
        }

        fill.setColor(spot.color);
        canvas.drawRoundRect(rect, radius, radius, fill);
        stroke.setColor(0xFFFFFFFF);
        stroke.setStrokeWidth(0.5f * u);
        canvas.drawRoundRect(rect, radius, radius, stroke);

        text.setColor(spot.color == Ui.YELLOW ? Ui.TEXT : 0xFFFFFFFF);
        canvas.drawText(shown, rect.centerX(), rect.centerY() + 1.25f * u, text);
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
    // or Enter picks the one with the blue ring.
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT || keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
            if (keyboardIndex < ORDER.length - 1) {
                keyboardIndex++;
                invalidate();
                return true;
            }
            return false;  // focus moves on to the buttons below
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_UP) {
            if (keyboardIndex > 0) {
                keyboardIndex--;
                invalidate();
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
        invalidate();
    }
}
