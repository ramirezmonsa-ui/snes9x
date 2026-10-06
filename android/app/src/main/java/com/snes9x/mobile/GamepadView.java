package com.snes9x.mobile;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

/** On-screen SNES controller drawn over the game. */
public class GamepadView extends View {
    public interface Listener {
        void onButtonsChanged(int mask);
    }

    private static final int DPAD_MASK = NativeBridge.BUTTON_UP | NativeBridge.BUTTON_DOWN
            | NativeBridge.BUTTON_LEFT | NativeBridge.BUTTON_RIGHT;

    /** A round or rectangular button. */
    private static final class Button {
        final int mask;
        final String label;
        final int color;
        final RectF bounds = new RectF();
        boolean round;

        Button(int mask, String label, int color) {
            this.mask = mask;
            this.label = label;
            this.color = color;
        }

        boolean contains(float x, float y, float slop) {
            if (round) {
                float dx = x - bounds.centerX();
                float dy = y - bounds.centerY();
                float r = bounds.width() / 2 + slop;
                return dx * dx + dy * dy <= r * r;
            }
            return x >= bounds.left - slop && x <= bounds.right + slop
                    && y >= bounds.top - slop && y <= bounds.bottom + slop;
        }
    }

    // Colors of the SNES PAL/Japanese controller face buttons.
    private final Button[] buttons = {
        new Button(NativeBridge.BUTTON_A, "A", 0xFFD32F2F),
        new Button(NativeBridge.BUTTON_B, "B", 0xFFF9A825),
        new Button(NativeBridge.BUTTON_X, "X", 0xFF1976D2),
        new Button(NativeBridge.BUTTON_Y, "Y", 0xFF388E3C),
        new Button(NativeBridge.BUTTON_L, "L", 0xFF9E9E9E),
        new Button(NativeBridge.BUTTON_R, "R", 0xFF9E9E9E),
        new Button(NativeBridge.BUTTON_SELECT, "SELECT", 0xFF757575),
        new Button(NativeBridge.BUTTON_START, "START", 0xFF757575),
    };

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF scratch = new RectF();

    private float dpadX;
    private float dpadY;
    private float dpadRadius;
    private float slop;

    private int mask;
    private Listener listener;

    public GamepadView(Context context) {
        super(context);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setColor(0x80FFFFFF);
        text.setColor(0xD0FFFFFF);
        text.setTextAlign(Paint.Align.CENTER);
        text.setFakeBoldText(true);
        setHapticFeedbackEnabled(true);
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        float base = Math.min(w, h);
        float margin = base * 0.05f;
        float faceRadius = base * 0.07f;
        float clusterRadius = faceRadius * 2.2f;
        slop = faceRadius * 0.25f;
        stroke.setStrokeWidth(base * 0.006f);

        dpadRadius = base * 0.18f;
        dpadX = margin + dpadRadius;
        dpadY = h - margin * 2 - dpadRadius;

        // A on the right, B at the bottom, X at the top, Y on the left.
        float cx = w - margin - clusterRadius - faceRadius;
        float cy = dpadY;
        setRound(buttons[0], cx + clusterRadius, cy, faceRadius);
        setRound(buttons[1], cx, cy + clusterRadius, faceRadius);
        setRound(buttons[2], cx, cy - clusterRadius, faceRadius);
        setRound(buttons[3], cx - clusterRadius, cy, faceRadius);

        // Shoulder buttons above each cluster.
        float shoulderWidth = base * 0.28f;
        float shoulderHeight = base * 0.09f;
        float shoulderBottom = Math.min(dpadY - dpadRadius, cy - clusterRadius - faceRadius) - margin;
        buttons[4].bounds.set(margin, shoulderBottom - shoulderHeight,
                margin + shoulderWidth, shoulderBottom);
        buttons[5].bounds.set(w - margin - shoulderWidth, shoulderBottom - shoulderHeight,
                w - margin, shoulderBottom);

        // Select and Start at the bottom center.
        float pillWidth = base * 0.17f;
        float pillHeight = base * 0.06f;
        float gap = base * 0.03f;
        float pillBottom = h - margin;
        buttons[6].bounds.set(w / 2f - gap / 2 - pillWidth, pillBottom - pillHeight,
                w / 2f - gap / 2, pillBottom);
        buttons[7].bounds.set(w / 2f + gap / 2, pillBottom - pillHeight,
                w / 2f + gap / 2 + pillWidth, pillBottom);
    }

    private static void setRound(Button button, float x, float y, float radius) {
        button.round = true;
        button.bounds.set(x - radius, y - radius, x + radius, y + radius);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        drawDpad(canvas);
        for (Button button : buttons) {
            boolean pressed = (mask & button.mask) != 0;
            fill.setColor(button.color);
            fill.setAlpha(pressed ? 230 : 110);
            float textSize;
            if (button.round) {
                float r = button.bounds.width() / 2;
                canvas.drawCircle(button.bounds.centerX(), button.bounds.centerY(), r, fill);
                canvas.drawCircle(button.bounds.centerX(), button.bounds.centerY(), r, stroke);
                textSize = r * 0.9f;
            } else {
                float r = button.bounds.height() / 2;
                canvas.drawRoundRect(button.bounds, r, r, fill);
                canvas.drawRoundRect(button.bounds, r, r, stroke);
                textSize = button.bounds.height() * (button.label.length() > 1 ? 0.45f : 0.6f);
            }
            text.setTextSize(textSize);
            canvas.drawText(button.label, button.bounds.centerX(),
                    button.bounds.centerY() - (text.descent() + text.ascent()) / 2, text);
        }
    }

    private void drawDpad(Canvas canvas) {
        float arm = dpadRadius / 3;
        fill.setColor(Color.DKGRAY);
        fill.setAlpha(120);
        scratch.set(dpadX - arm, dpadY - dpadRadius, dpadX + arm, dpadY + dpadRadius);
        canvas.drawRoundRect(scratch, arm * 0.3f, arm * 0.3f, fill);
        canvas.drawRoundRect(scratch, arm * 0.3f, arm * 0.3f, stroke);
        scratch.set(dpadX - dpadRadius, dpadY - arm, dpadX + dpadRadius, dpadY + arm);
        canvas.drawRoundRect(scratch, arm * 0.3f, arm * 0.3f, fill);
        canvas.drawRoundRect(scratch, arm * 0.3f, arm * 0.3f, stroke);

        fill.setColor(Color.WHITE);
        fill.setAlpha(200);
        float d = dpadRadius - arm;
        if ((mask & NativeBridge.BUTTON_UP) != 0) {
            canvas.drawCircle(dpadX, dpadY - d, arm * 0.5f, fill);
        }
        if ((mask & NativeBridge.BUTTON_DOWN) != 0) {
            canvas.drawCircle(dpadX, dpadY + d, arm * 0.5f, fill);
        }
        if ((mask & NativeBridge.BUTTON_LEFT) != 0) {
            canvas.drawCircle(dpadX - d, dpadY, arm * 0.5f, fill);
        }
        if ((mask & NativeBridge.BUTTON_RIGHT) != 0) {
            canvas.drawCircle(dpadX + d, dpadY, arm * 0.5f, fill);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        int newMask = 0;
        for (int i = 0; i < event.getPointerCount(); i++) {
            boolean lifted = (action == MotionEvent.ACTION_POINTER_UP && i == event.getActionIndex())
                    || action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL;
            if (!lifted) {
                newMask |= hitTest(event.getX(i), event.getY(i));
            }
        }

        if (newMask != mask) {
            // A light tick when a new button goes down.
            if ((newMask & ~mask & ~DPAD_MASK) != 0) {
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            }
            mask = newMask;
            if (listener != null) {
                listener.onButtonsChanged(mask);
            }
            invalidate();
        }
        return true;
    }

    private int hitTest(float x, float y) {
        float dx = x - dpadX;
        float dy = y - dpadY;
        float reach = dpadRadius * 1.3f;
        if (dx * dx + dy * dy <= reach * reach) {
            return dpadDirection(dx, dy);
        }

        int hit = 0;
        for (Button button : buttons) {
            if (button.contains(x, y, slop)) {
                hit |= button.mask;
            }
        }
        return hit;
    }

    /** Eight-way direction for a touch relative to the d-pad center. */
    private int dpadDirection(float dx, float dy) {
        if (dx * dx + dy * dy < (dpadRadius * 0.2f) * (dpadRadius * 0.2f)) {
            return 0;
        }
        double angle = Math.toDegrees(Math.atan2(-dy, dx));
        if (angle < 0) {
            angle += 360;
        }
        // Each of the eight sectors is 45 degrees wide, starting with "right"
        // centered on 0 degrees.
        int sector = (int) Math.round(angle / 45.0) % 8;
        switch (sector) {
            case 0: return NativeBridge.BUTTON_RIGHT;
            case 1: return NativeBridge.BUTTON_RIGHT | NativeBridge.BUTTON_UP;
            case 2: return NativeBridge.BUTTON_UP;
            case 3: return NativeBridge.BUTTON_UP | NativeBridge.BUTTON_LEFT;
            case 4: return NativeBridge.BUTTON_LEFT;
            case 5: return NativeBridge.BUTTON_LEFT | NativeBridge.BUTTON_DOWN;
            case 6: return NativeBridge.BUTTON_DOWN;
            default: return NativeBridge.BUTTON_DOWN | NativeBridge.BUTTON_RIGHT;
        }
    }
}
