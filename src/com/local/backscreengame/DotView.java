package com.local.backscreengame;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

/** 屏幕上的点阵预览，方便在没有背屏时也确认内容。 */
public final class DotView extends View {
    private static final int COLS = 17;
    private static final int ROWS = 23;
    private static final int CELLS = COLS * ROWS;

    private final Paint on = new Paint();
    private final int[] buf = new int[CELLS];

    /**
     * XML 反射实例化必须走 (Context, AttributeSet)。
     * 只有单参构造会在 Android 14+ 抛 InflateException。
     */
    public DotView(Context c) {
        super(c);
        init();
    }

    public DotView(Context c, AttributeSet a) {
        super(c, a);
        init();
    }

    public DotView(Context c, AttributeSet a, int defStyleAttr) {
        super(c, a, defStyleAttr);
        init();
    }

    private void init() {
        try {
            on.setColor(Color.WHITE);
            setBackgroundColor(Color.parseColor("#05070A"));
        } catch (Throwable ignore) { }
    }

    public void setFrame(int[] f) {
        if (f == null) return;
        System.arraycopy(f, 0, buf, 0, CELLS);
        try { invalidate(); } catch (Throwable ignore) { }
    }

    public void clear() {
        java.util.Arrays.fill(buf, 0);
        try { invalidate(); } catch (Throwable ignore) { }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        try {
            super.onDraw(canvas);
            int vw = getWidth(), vh = getHeight();
            if (vw <= 0 || vh <= 0) return;          // 还没测量完，直接跳过
            // 格子必须是正方形：背屏物理是 17 宽 x 23 高的竖屏，
            // 如果让格子跟着容器拉伸，预览就失真了。
            float cw = Math.min(vw / (float) COLS, vh / (float) ROWS);
            if (cw <= 0) return;
            float gridW = cw * COLS;
            float gridH = cw * ROWS;
            float ox = (vw - gridW) / 2f;      // 水平居中
            float oy = (vh - gridH) / 2f;      // 垂直居中
            float gap = Math.max(1f, cw * 0.14f);
            for (int r = 0; r < ROWS; r++) {
                for (int c = 0; c < COLS; c++) {
                    int v = buf[r * COLS + c];
                    if (v < 0) v = 0;
                    if (v > 255) v = 255;
                    on.setAlpha(40 + (int) (v * 0.86f));
                    canvas.drawRect(
                            ox + c * cw, oy + r * cw,
                            ox + (c + 1) * cw - gap, oy + (r + 1) * cw - gap,
                            on);
                }
            }
        } catch (Throwable ignore) {
            // 预览画不出来也不能让 App 崩
        }
    }
}
