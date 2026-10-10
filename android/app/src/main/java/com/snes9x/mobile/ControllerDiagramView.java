package com.snes9x.mobile;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.SoundEffectConstants;
import android.view.View;

import java.util.EnumMap;
import java.util.Map;

/**
 * A drawing of a Super Nintendo controller with a label box for each button, joined to it by a
 * line. Each box shows which button of the player's controller presses that button; tapping a
 * box (or the button) picks it to be changed.
 */
final class ControllerDiagramView extends View {
    interface Listener {
        void onButtonPicked(ControllerMapping.Action action);

        /** The d-pad was tapped; it can't be reassigned. */
        void onDpadPicked();
    }

    /** Text for each box: the controller button that presses it. */
    interface Labels {
        String keysFor(ControllerMapping.Action action);
    }

    /** Where a button is drawn, its label box, and the line between them. */
    private static final class Callout {
        final RectF button;     // in layout units (the view is 100 units wide)
        final boolean round;
        final RectF box;
        final float[] line;     // x, y pairs
        final int color;

        Callout(RectF button, boolean round, RectF box, float[] line, int color) {
            this.button = button;
            this.round = round;
            this.box = box;
            this.line = line;
            this.color = color;
        }
    }

    // Order used when moving between buttons with a controller's d-pad.
    private static final ControllerMapping.Action[] ORDER = {
        ControllerMapping.Action.L, ControllerMapping.Action.R,
        ControllerMapping.Action.X, ControllerMapping.Action.Y,
        ControllerMapping.Action.A, ControllerMapping.Action.SELECT,
        ControllerMapping.Action.START, ControllerMapping.Action.B,
    };

    private static final float HEIGHT = 66;
    private static final float DPAD_X = 30;
    private static final float DPAD_Y = 34;
    private static final float FACE_X = 68;
    private static final float FACE_Y = 34;
    private static final float FACE_GAP = 7.2f;
    private static final float FACE_R = 3.7f;

    private final Map<ControllerMapping.Action, Callout> callouts =
            new EnumMap<>(ControllerMapping.Action.class);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF scaled = new RectF();
    private final Path path = new Path();

    private Listener listener;
    private Labels labels;
    private ControllerMapping.Action selected;
    private int keyboardIndex;

    ControllerDiagramView(Context context) {
        super(context);
        stroke.setStyle(Paint.Style.STROKE);
        text.setTextAlign(Paint.Align.CENTER);
        setFocusable(true);
        setClickable(true);

        // Shoulders, with their boxes above.
        callouts.put(ControllerMapping.Action.L, new Callout(new RectF(17, 10, 39, 17), false,
                new RectF(18, 1, 32, 8), new float[] {25, 8, 25, 10}, 0));
        callouts.put(ControllerMapping.Action.R, new Callout(new RectF(61, 10, 83, 17), false,
                new RectF(68, 1, 82, 8), new float[] {75, 8, 75, 10}, 0));
        // Face buttons: X and Y labelled to the left, A to the right, B below.
        callouts.put(ControllerMapping.Action.X, new Callout(face(0, -1), true,
                new RectF(46.5f, 23.3f, 60.5f, 30.3f),
                new float[] {60.5f, FACE_Y - FACE_GAP, FACE_X - FACE_R, FACE_Y - FACE_GAP}, Ui.BLUE));
        callouts.put(ControllerMapping.Action.Y, new Callout(face(-1, 0), true,
                new RectF(39.5f, 30.5f, 53.5f, 37.5f),
                new float[] {53.5f, FACE_Y, FACE_X - FACE_GAP - FACE_R, FACE_Y}, Ui.GREEN));
        callouts.put(ControllerMapping.Action.A, new Callout(face(1, 0), true,
                new RectF(85.5f, 30.5f, 99.5f, 37.5f),
                new float[] {FACE_X + FACE_GAP + FACE_R, FACE_Y, 85.5f, FACE_Y}, Ui.RED));
        callouts.put(ControllerMapping.Action.B, new Callout(face(0, 1), true,
                new RectF(61, 57, 75, 64),
                new float[] {FACE_X, FACE_Y + FACE_GAP + FACE_R, FACE_X, 57}, Ui.YELLOW));
        // Select and Start, labelled below.
        callouts.put(ControllerMapping.Action.SELECT, new Callout(new RectF(38, 40.6f, 46, 43.4f),
                false, new RectF(29, 57, 43, 64), new float[] {42, 43.4f, 42, 57}, 0));
        callouts.put(ControllerMapping.Action.START, new Callout(new RectF(48, 40.6f, 56, 43.4f),
                false, new RectF(45, 57, 59, 64), new float[] {52, 43.4f, 52, 57}, 0));
    }

    private static RectF face(int dx, int dy) {
        float cx = FACE_X + dx * FACE_GAP;
        float cy = FACE_Y + dy * FACE_GAP;
        return new RectF(cx - FACE_R, cy - FACE_R, cx + FACE_R, cy + FACE_R);
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

    private RectF scale(RectF r, float u) {
        scaled.set(r.left * u, r.top * u, r.right * u, r.bottom * u);
        return scaled;
    }

    // --- Drawing -------------------------------------------------------------

    @Override
    protected void onDraw(Canvas canvas) {
        float u = u();
        if (u <= 0) {
            return;
        }

        // Shoulders behind the body.
        fill.setColor(0xFFC4C1BC);
        for (ControllerMapping.Action action : new ControllerMapping.Action[] {
                ControllerMapping.Action.L, ControllerMapping.Action.R}) {
            RectF r = scale(callouts.get(action).button, u);
            canvas.drawRoundRect(r, 3 * u, 3 * u, fill);
        }

        // Body, in the warm grey of the original controller.
        scaled.set(12 * u, 14 * u, 88 * u, 54 * u);
        fill.setColor(0xFFDCD9D3);
        canvas.drawRoundRect(scaled, 20 * u, 20 * u, fill);
        stroke.setColor(0xFFB8B4AD);
        stroke.setStrokeWidth(0.4f * u);
        canvas.drawRoundRect(scaled, 20 * u, 20 * u, stroke);

        // D-pad in its round recess.
        fill.setColor(0xFFD0CDC7);
        canvas.drawCircle(DPAD_X * u, DPAD_Y * u, 10 * u, fill);
        stroke.setColor(0xFFC2BEB7);
        stroke.setStrokeWidth(0.35f * u);
        canvas.drawCircle(DPAD_X * u, DPAD_Y * u, 10 * u, stroke);
        fill.setColor(0xFF77767B);
        float arm = 2.7f * u;
        float reach = 8 * u;
        scaled.set(DPAD_X * u - arm, DPAD_Y * u - reach, DPAD_X * u + arm, DPAD_Y * u + reach);
        canvas.drawRoundRect(scaled, 0.8f * u, 0.8f * u, fill);
        scaled.set(DPAD_X * u - reach, DPAD_Y * u - arm, DPAD_X * u + reach, DPAD_Y * u + arm);
        canvas.drawRoundRect(scaled, 0.8f * u, 0.8f * u, fill);

        // Dark grey shell behind the face buttons, with the two diagonal
        // grooves joining Y to X and B to A.
        fill.setColor(0xFF8C8A8F);
        canvas.drawCircle(FACE_X * u, FACE_Y * u, 12.5f * u, fill);
        fill.setColor(0xFFCFCCC6);
        float grooveHalf = (FACE_GAP * 0.7071f + FACE_R + 0.9f) * u;
        float grooveWidth = (FACE_R + 0.9f) * u;
        for (int side : new int[] {-1, 1}) {
            // Centre of each groove, halfway between its two buttons.
            float gx = (FACE_X + side * FACE_GAP / 2) * u;
            float gy = (FACE_Y + side * FACE_GAP / 2) * u;
            canvas.save();
            canvas.rotate(-45, gx, gy);
            scaled.set(gx - grooveHalf, gy - grooveWidth, gx + grooveHalf, gy + grooveWidth);
            canvas.drawRoundRect(scaled, grooveWidth, grooveWidth, fill);
            canvas.restore();
        }

        // Lines first, so the boxes and buttons cover their ends.
        stroke.setColor(0xFF6E6C72);
        stroke.setStrokeWidth(0.35f * u);
        for (Callout callout : callouts.values()) {
            path.reset();
            path.moveTo(callout.line[0] * u, callout.line[1] * u);
            for (int i = 2; i < callout.line.length; i += 2) {
                path.lineTo(callout.line[i] * u, callout.line[i + 1] * u);
            }
            canvas.drawPath(path, stroke);
        }

        for (Map.Entry<ControllerMapping.Action, Callout> entry : callouts.entrySet()) {
            drawButton(canvas, entry.getKey(), entry.getValue(), u);
            drawBox(canvas, entry.getKey(), entry.getValue(), u);
        }
    }

    private void drawButton(Canvas canvas, ControllerMapping.Action action, Callout callout, float u) {
        RectF r = scale(callout.button, u);
        if (callout.round) {
            fill.setColor(callout.color);
            canvas.drawCircle(r.centerX(), r.centerY(), r.width() / 2, fill);
            text.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD_ITALIC));
            text.setTextSize(3.4f * u);
            text.setColor(0xFFFFFFFF);
            canvas.drawText(action.button, r.centerX(), r.centerY() + 1.2f * u, text);
        } else if (action == ControllerMapping.Action.SELECT || action == ControllerMapping.Action.START) {
            // Slanted, like on the real controller.
            fill.setColor(0xFF77767B);
            canvas.save();
            canvas.rotate(-35, r.centerX(), r.centerY());
            canvas.drawRoundRect(r, r.height() / 2, r.height() / 2, fill);
            canvas.restore();
            text.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD_ITALIC));
            text.setTextSize(2.2f * u);
            text.setColor(0xFF4A494E);
            canvas.drawText(action.button.toUpperCase(java.util.Locale.ROOT), r.centerX(),
                    r.bottom + 4.2f * u, text);
        } else {
            text.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD_ITALIC));
            text.setTextSize(3.4f * u);
            text.setColor(Ui.TEXT);
            canvas.drawText(action.button, r.centerX(), r.centerY() + 1.2f * u, text);
        }
    }

    /** The label: a small copy of the SNES button, then the controller key that presses it. */
    private void drawBox(Canvas canvas, ControllerMapping.Action action, Callout callout, float u) {
        RectF box = scale(callout.box, u);
        float radius = 1.6f * u;
        fill.setColor(Ui.SURFACE);
        canvas.drawRoundRect(box, radius, radius, fill);
        boolean picked = action == selected;
        boolean focusedHere = isFocused() && ORDER[keyboardIndex] == action;
        stroke.setColor(picked ? Ui.RED : focusedHere ? Ui.FOCUS : 0xFFB9B6B1);
        stroke.setStrokeWidth((picked || focusedHere ? 0.7f : 0.3f) * u);
        canvas.drawRoundRect(box, radius, radius, stroke);

        // Chip with the SNES button.
        float chipX = box.left + 3.4f * u;
        float cy = box.centerY();
        if (callout.round) {
            fill.setColor(callout.color);
            canvas.drawCircle(chipX, cy, 2.3f * u, fill);
            text.setColor(0xFFFFFFFF);
        } else {
            fill.setColor(Ui.SHELL);
            scaled.set(chipX - 2.5f * u, cy - 1.8f * u, chipX + 2.5f * u, cy + 1.8f * u);
            canvas.drawRoundRect(scaled, 0.9f * u, 0.9f * u, fill);
            text.setColor(0xFFFFFFFF);
        }
        String chip = action == ControllerMapping.Action.SELECT ? "SE"
                : action == ControllerMapping.Action.START ? "ST" : action.button;
        text.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD_ITALIC));
        text.setTextSize((chip.length() > 1 ? 2 : 2.8f) * u);
        canvas.drawText(chip, chipX, cy + 1f * u, text);

        // The controller key(s).
        String keys = labels != null ? labels.keysFor(action) : "";
        text.setTypeface(Typeface.DEFAULT_BOLD);
        text.setTextSize(2.7f * u);
        text.setColor(picked ? Ui.RED : Ui.TEXT);
        float available = box.right - (chipX + 2.8f * u) - 0.8f * u;
        String shown = keys;
        while (shown.length() > 1 && text.measureText(shown) > available) {
            shown = shown.substring(0, shown.length() - 2) + "…";
        }
        canvas.drawText(shown, (chipX + 2.8f * u + box.right) / 2, cy + 0.95f * u, text);
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
        } else if (Math.hypot(x - DPAD_X, y - DPAD_Y) < 10 && listener != null) {
            listener.onDpadPicked();
        }
        return true;
    }

    /** The button whose box or shape is at (x, y), with some slack for fingers. */
    private ControllerMapping.Action hitTest(float x, float y) {
        float slack = 1.5f;
        for (Map.Entry<ControllerMapping.Action, Callout> entry : callouts.entrySet()) {
            RectF box = entry.getValue().box;
            if (x >= box.left - slack && x <= box.right + slack
                    && y >= box.top - slack && y <= box.bottom + slack) {
                return entry.getKey();
            }
        }
        for (Map.Entry<ControllerMapping.Action, Callout> entry : callouts.entrySet()) {
            RectF button = entry.getValue().button;
            if (x >= button.left - slack && x <= button.right + slack
                    && y >= button.top - slack && y <= button.bottom + slack) {
                return entry.getKey();
            }
        }
        return null;
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
    // or Enter picks the one with the blue outline.
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
