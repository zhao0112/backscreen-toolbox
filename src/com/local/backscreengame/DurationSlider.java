package com.local.backscreengame;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

import java.util.Locale;

/**
 * 时长滑块 —— 无极连续调节。
 *
 * 没有档位、没有吸附、没有刻度。手指停在哪，值就是哪。
 *
 * 映射是一条连续的分段线性曲线（非等比），让低时长区也有足够行程：
 *   1 分 ~ 15 分   →  轨道前 30%
 *   15 分 ~ 60 分  →  轨道 30% ~ 75%
 *   60 分 ~ 120 分 →  轨道 75% ~ 100%
 * 曲线连续且单调，所以拖动完全跟手，不存在"跳档"。
 *
 * 交互：
 *   - 横向拖动：连续调时长
 *   - 纵向拖动：微调，1 像素 = 1 秒
 *   - 数值变化时给一点点震动反馈
 */
public final class DurationSlider extends View {

    public interface OnDurationChange {
        /**
         * @param seconds 新的总时长
         * @param fromUser true 表示用户正在操作/松手，false 表示程序设置
         */
        void onDurationChanged(int seconds, boolean fromUser);
    }

    private static final int MIN_SEC = 60;        // 1 分钟
    private static final int MAX_SEC = 120 * 60;  // 2 小时

    // 断点
    private static final int B1 = 60;             // 1 分
    private static final int B2 = 15 * 60;        // 15 分
    private static final int B3 = 60 * 60;        // 60 分
    private static final float T1 = 0f;
    private static final float T2 = 0.30f;
    private static final float T3 = 0.75f;
    private static final float T4 = 1f;

    private int valueSec = 25 * 60;
    private OnDurationChange listener;

    // 拖拽
    private boolean dragging;
    private float downX, downY, lastY;
    private boolean verticalMode;

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint handlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();

    private static final int ACCENT = 0xFF35E0B0;
    private static final int TEXT = 0xFFEAF2F5;
    private static final int MUTE = 0xFF767C85;
    private static final int TRACK = 0xFF161D25;

    public DurationSlider(Context c) { super(c); init(); }
    public DurationSlider(Context c, AttributeSet a) { super(c, a); init(); }
    public DurationSlider(Context c, AttributeSet a, int d) { super(c, a, d); init(); }

    private void init() {
        fill.setColor(ACCENT);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(2);
        stroke.setColor(0x1FFFFFFF);
        handlePaint.setStyle(Paint.Style.STROKE);
        handlePaint.setStrokeWidth(3.5f);
        handlePaint.setColor(ACCENT);
        setClickable(true);
        setFocusable(true);
    }

    public void setOnDurationChange(OnDurationChange l) { listener = l; }

    /** 设置当前值。notify=true 时回调 onDurationChanged(seconds,false)。 */
    public void setValueSeconds(int sec, boolean notify) {
        valueSec = clamp(sec);
        invalidate();
        if (notify && listener != null) listener.onDurationChanged(valueSec, false);
    }

    public int getValueSeconds() { return valueSec; }

    private static int clamp(int s) {
        if (s < MIN_SEC) return MIN_SEC;
        if (s > MAX_SEC) return MAX_SEC;
        return s;
    }

    // ---------- 连续映射 ----------

    /** 值 → 轨道位置比例 [0,1] */
    private static float valueToT(int v) {
        if (v <= B2) {
            float u = (v - B1) / (float) (B2 - B1);
            return T1 + u * (T2 - T1);
        } else if (v <= B3) {
            float u = (v - B2) / (float) (B3 - B2);
            return T2 + u * (T3 - T2);
        } else {
            float u = (v - B3) / (float) (MAX_SEC - B3);
            return T3 + u * (T4 - T3);
        }
    }

    /** 轨道位置比例 → 值。完全连续，无吸附。 */
    private static int tToValue(float t) {
        if (t < 0f) t = 0f;
        if (t > 1f) t = 1f;
        float v;
        if (t <= T2) {
            v = B1 + (t - T1) / (T2 - T1) * (B2 - B1);
        } else if (t <= T3) {
            v = B2 + (t - T2) / (T3 - T2) * (B3 - B2);
        } else {
            v = B3 + (t - T3) / (T4 - T3) * (MAX_SEC - B3);
        }
        return clamp(Math.round(v));
    }

    // ---------- 几何 ----------

    private float trackLeft() { return dp(26); }
    private float trackRight() { return getWidth() - dp(26); }
    private float trackTop() { return getHeight() - dp(26); }
    private float trackH() { return dp(12); }
    private float handleR() { return dp(12); }

    private float valueToPos(int v) {
        float l = trackLeft(), rr = trackRight();
        return l + valueToT(v) * (rr - l);
    }

    private float posToT(float x) {
        float l = trackLeft(), rr = trackRight();
        float t = (x - l) / Math.max(1f, (rr - l));
        if (t < 0f) t = 0f;
        if (t > 1f) t = 1f;
        return t;
    }

    // ---------- 绘制 ----------

    @Override
    protected void onDraw(Canvas c) {
        super.onDraw(c);
        float w = getWidth();

        // 滑块只做控制，不画文字：时间读数由布局里的 TextView 负责，
        // 避免同一个数字出现两次（那是"乱"的来源）。

        // 轨道
        float ty = trackTop();
        float l = trackLeft(), rr = trackRight();
        r.set(l, ty, rr, ty + trackH());
        fill.setColor(TRACK);
        c.drawRoundRect(r, trackH() / 2, trackH() / 2, fill);

        // 已选
        float hx = valueToPos(valueSec);
        r.set(l, ty, Math.max(l + trackH() / 2, hx), ty + trackH());
        fill.setColor(ACCENT);
        c.drawRoundRect(r, trackH() / 2, trackH() / 2, fill);

        // 描边
        r.set(l, ty, rr, ty + trackH());
        c.drawRoundRect(r, trackH() / 2, trackH() / 2, stroke);

        // 把手：整圆完整可见，不越界
        float cy = ty + trackH() / 2;
        handlePaint.setColor(ACCENT);
        handlePaint.setStyle(Paint.Style.STROKE);
        c.drawCircle(hx, cy, handleR(), handlePaint);
        handlePaint.setStyle(Paint.Style.FILL);
        handlePaint.setColor(0xFF0B0F14);
        c.drawCircle(hx, cy, handleR() - dp(3.5f), handlePaint);
        handlePaint.setColor(ACCENT);
        c.drawRect(hx - dp(1.5f), cy - dp(4.5f), hx + dp(1.5f), cy + dp(4.5f), handlePaint);
    }

    // ---------- 触摸 ----------

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        float x = e.getX(), y = e.getY();

        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                downX = x; downY = y; lastY = y;
                dragging = true;
                verticalMode = false;
                getParent().requestDisallowInterceptTouchEvent(true);
                // 手指不在把手上就直接跳过去
                float hx = valueToPos(valueSec);
                if (Math.abs(x - hx) > handleR() * 1.8f) {
                    applyT(posToT(x));
                    haptic();
                }
                return true;
            }
            case MotionEvent.ACTION_MOVE: {
                if (!dragging) return true;
                float dx = Math.abs(x - downX);
                float dy = Math.abs(y - downY);

                // 纵向意图判定：纵向位移明显超过横向就切微调模式
                if (!verticalMode && dy > dp(6) && dy > dx) {
                    verticalMode = true;
                }

                if (verticalMode) {
                    // 1 像素 = 1 秒，连续
                    float delta = (lastY - y);
                    lastY = y;
                    setValueSeconds(clamp(valueSec + Math.round(delta)), true);
                } else {
                    applyT(posToT(x));
                }
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                dragging = false;
                if (listener != null) listener.onDurationChanged(valueSec, true);
                performClick();
                return true;
            }
        }
        return super.onTouchEvent(e);
    }

    private void applyT(float t) {
        int v = tToValue(t);
        if (v != valueSec) {
            valueSec = v;
            invalidate();
            if (listener != null) listener.onDurationChanged(valueSec, true);
            haptic();
        }
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }

    private void haptic() {
        try { performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK); }
        catch (Throwable t) { /* 设备不支持震动则忽略 */ }
    }

    private float dp(float v) {
        return v * getResources().getDisplayMetrics().density;
    }

    /** 格式：mm:ss 或 h:mm:ss */
    public static String format(int sec) {
        int h = sec / 3600, m = (sec % 3600) / 60, s = sec % 60;
        if (h > 0) return String.format(Locale.US, "%d:%02d:%02d", h, m, s);
        return String.format(Locale.US, "%d:%02d", m, s);
    }
}
