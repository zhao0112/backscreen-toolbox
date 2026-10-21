package com.local.backscreengame;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.MotionEvent;
import android.view.View;

/**
 * 手绘画布：直接在 17×23 点阵网格上画。
 *
 * 为什么不先画在高清画布再降采样？
 * 背屏只有 391 个点，降采样会吃掉所有细节。
 * 直接在点阵上画，每一笔就是一个或几个真实像素点，所见即所得。
 *
 * 交互：
 * - 手指按下/移动 -> 涂点（Bresenham 连线，避免快速滑动断线）
 * - 橡皮模式 -> 擦点
 * - 撤销 -> 回退一步
 */
public final class DrawView extends View {

    public static final int COLS = 17;
    public static final int ROWS = 23;

    /** 布局缓存：由 ensureLayout() 算出，onDraw 和触摸命中共用同一份。 */
    private float gap = 0f;      // 视觉缝隙
    private float cell = 0f;     // 格子边长（正方形）
    private float step = 0f;     // 格心间距
    private float originX = 0f, originY = 0f;
    private boolean laidOut = false;

    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint emptyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    /** 391 个点的亮度，0–255。 */
    private final int[] px = new int[COLS * ROWS];

    /** 撤销栈：每一步存一份快照（391 个 int = 1.5KB，几十步没问题）。 */
    private final java.util.ArrayDeque<int[]> undoStack = new java.util.ArrayDeque<int[]>();
    private static final int MAX_UNDO = 40;

    private boolean eraser = false;
    private int brushValue = 255;

    private int lastCol = -1, lastRow = -1;

    public interface Listener {
        /** 画面变化（绘制或撤销）。 */
        void onDrawChanged();
        /** 点击「投射到背屏」。 */
        void onProject();
    }

    private Listener listener;

    public DrawView(android.content.Context context) {
        super(context);
        init();
    }

    public DrawView(android.content.Context context, android.util.AttributeSet a) {
        super(context, a);
        init();
    }

    public DrawView(android.content.Context context, android.util.AttributeSet a, int defStyleAttr) {
        super(context, a, defStyleAttr);
        init();
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        laidOut = false;
        invalidate();
    }

    private void init() {
        dotPaint.setStyle(Paint.Style.FILL);
        dotPaint.setColor(Color.WHITE);
        gridPaint.setStyle(Paint.Style.STROKE);
        gridPaint.setStrokeWidth(1f);
        gridPaint.setColor(0x26FFFFFF);
        emptyPaint.setStyle(Paint.Style.FILL);
        emptyPaint.setColor(0x14FFFFFF);
        setBackgroundColor(0xFF0A0B0C);
    }

    public void setListener(Listener l) { listener = l; }

    public void setEraser(boolean on) { eraser = on; }

    public boolean isEraser() { return eraser; }

    /** 当前画面（可直接推给背屏）。 */
    public int[] frame() { return px; }

    public boolean isEmpty() {
        for (int v : px) if (v != 0) return false;
        return true;
    }

    public void clearCanvas() {
        pushUndo();
        java.util.Arrays.fill(px, 0);
        invalidate();
        notifyChanged();
    }

    public void undo() {
        int[] prev = undoStack.pollLast();
        if (prev == null) return;
        System.arraycopy(prev, 0, px, 0, px.length);
        invalidate();
        notifyChanged();
    }

    public boolean canUndo() { return !undoStack.isEmpty(); }

    private void pushUndo() {
        undoStack.addLast(px.clone());
        while (undoStack.size() > MAX_UNDO) undoStack.pollFirst();
    }

    private void notifyChanged() {
        if (listener != null) listener.onDrawChanged();
    }

    // ---------------- 触摸 ----------------

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        try {
            // 触摸可能早于首帧 onDraw，先把布局算出来
            if (!laidOut) ensureLayout(getWidth(), getHeight());
            int action = e.getActionMasked();
            float x = e.getX(), y = e.getY();
            int col = colAt(x), row = rowAt(y);

            if (action == MotionEvent.ACTION_DOWN) {
                if (col < 0 || row < 0) return true;
                pushUndo();
                lastCol = col; lastRow = row;
                paintAt(col, row);
                return true;
            }
            if (action == MotionEvent.ACTION_MOVE) {
                if (col < 0 || row < 0) return true;
                if (lastCol < 0) {
                    lastCol = col; lastRow = row;
                    paintAt(col, row);
                } else {
                    // Bresenham 连线，快速滑动不断线
                    lineTo(lastCol, lastRow, col, row);
                    lastCol = col; lastRow = row;
                }
                return true;
            }
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                lastCol = -1; lastRow = -1;
                return true;
            }
        } catch (Throwable ignore) { }
        return true;
    }

    private void paintAt(int col, int row) {
        int i = row * COLS + col;
        px[i] = eraser ? 0 : brushValue;
        invalidate();
        notifyChanged();
    }

    /** Bresenham 直线。 */
    private void lineTo(int x0, int y0, int x1, int y1) {
        int dx = Math.abs(x1 - x0), dy = Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1, sy = y0 < y1 ? 1 : -1;
        int err = dx - dy;
        int x = x0, y = y0;
        while (true) {
            if (x >= 0 && x < COLS && y >= 0 && y < ROWS) {
                px[y * COLS + x] = eraser ? 0 : brushValue;
            }
            if (x == x1 && y == y1) break;
            int e2 = err * 2;
            if (e2 > -dy) { err -= dy; x += sx; }
            if (e2 < dx) { err += dx; y += sy; }
        }
        invalidate();
        notifyChanged();
    }

    private int colAt(float x) {
        if (step <= 0) return -1;
        int c = (int) ((x - originX) / step);
        return (c < 0 || c >= COLS) ? -1 : c;
    }

    private int rowAt(float y) {
        if (step <= 0) return -1;
        int r = (int) ((y - originY) / step);
        return (r < 0 || r >= ROWS) ? -1 : r;
    }

    // ---------------- 绘制 ----------------

    /**
     * 算布局并写入字段。
     *
     * 必须写成「赋值给字段」而不是「声明同名局部变量」——
     * 否则局部变量会遮蔽字段，触摸命中的 colAt/rowAt 读到的是永远 0 的字段，
     * 结果是画布看得到但点不动。
     */
    private boolean ensureLayout(int vw, int vh) {
        float availW = vw - 12f;
        float availH = vh - 12f;
        if (availW <= 0 || availH <= 0) return false;

        // 背屏物理是 17 列 x 23 行。每列等宽、每行等高，
        // 所以「一列宽 : 一行高」必须等于 17:23，否则整块形变。
        // step = 一列宽 = 一行高，视觉缝隙从 step 里扣，不参与比例。
        this.step = Math.min(availW / COLS, availH / ROWS);
        this.cell = step / 1.16f;
        if (cell <= 0) return false;
        this.gap = step - cell;

        // 占位 = 17 个格距 - 1 个缝隙（最后一格右边不重复算缝隙）
        float gridW = COLS * step - gap;
        float gridH = ROWS * step - gap;
        this.originX = (vw - gridW) / 2f;
        this.originY = (vh - gridH) / 2f;
        this.laidOut = true;
        return true;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        try {
            super.onDraw(canvas);
            int vw = getWidth(), vh = getHeight();
            if (vw <= 0 || vh <= 0) return;

            if (!ensureLayout(vw, vh)) return;

            for (int r = 0; r < ROWS; r++) {
                for (int c = 0; c < COLS; c++) {
                    float x = originX + c * step;
                    float y = originY + r * step;
                    // 空格子给一点底色，让人看得出可以点
                    canvas.drawRect(x, y, x + cell, y + cell, emptyPaint);
                    int v = px[r * COLS + c];
                    if (v > 0) {
                        dotPaint.setAlpha(Math.max(0, Math.min(255, v)));
                        dotPaint.setColor(0xFFFFFFFF);
                        canvas.drawRect(x, y, x + cell, y + cell, dotPaint);
                    }
                }
            }
        } catch (Throwable ignore) { }
    }
}
