package com.local.backscreengame;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

/**
 * 开关控件 —— 不用系统 Switch（样式不可控），自己画。
 * 46dp × 27dp，开启时轨道变强调色，滑块右移。
 */
public final class ToggleSwitch extends View {

    public interface OnToggle {
        void onToggled(boolean on);
    }

    private boolean on;
    private boolean enabled = true;
    private OnToggle listener;

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint knob = new Paint(Paint.ANTI_ALIAS_FLAG);

    private static final int ACCENT = 0xFF35E0B0;
    private static final int TRACK_OFF = 0xFF23262A;
    private static final int LINE = 0x1FFFFFFF;

    public ToggleSwitch(Context c) { super(c); init(); }
    public ToggleSwitch(Context c, AttributeSet a) { super(c, a); init(); }
    public ToggleSwitch(Context c, AttributeSet a, int d) { super(c, a, d); init(); }

    private void init() {
        setClickable(true);
        setFocusable(true);
    }

    public void setOnToggle(OnToggle l) { listener = l; }

    public void setChecked(boolean v) {
        if (on != v) { on = v; invalidate(); }
    }

    public boolean isChecked() { return on; }

    public void setSwitchEnabled(boolean v) {
        enabled = v;
        setAlpha(v ? 1f : 0.4f);
    }

    @Override
    protected void onDraw(Canvas c) {
        super.onDraw(c);
        float w = getWidth(), h = getHeight();
        float r = h / 2;

        // 轨道
        fill.setColor(on ? ACCENT : TRACK_OFF);
        c.drawRoundRect(0, 0, w, h, r, r, fill);
        if (!on) {
            fill.setStyle(Paint.Style.STROKE);
            fill.setStrokeWidth(1);
            fill.setColor(LINE);
            c.drawRoundRect(0.5f, 0.5f, w - 0.5f, h - 0.5f, r, r, fill);
            fill.setStyle(Paint.Style.FILL);
        }

        // 滑块
        float kr = h / 2 - 2.5f;
        float cx = on ? w - kr - 2.5f : kr + 2.5f;
        knob.setColor(0xFFFFFFFF);
        c.drawCircle(cx, h / 2, kr, knob);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (!enabled) return false;
        if (e.getActionMasked() == MotionEvent.ACTION_UP) {
            on = !on;
            invalidate();
            try { performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY); }
            catch (Throwable ignore) { }
            if (listener != null) listener.onToggled(on);
            performClick();
            return true;
        }
        return true;
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }
}
