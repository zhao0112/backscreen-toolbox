package com.local.backscreengame;

import android.graphics.Color;
import android.graphics.Paint;

/**
 * 17x23 背屏点阵画布。所有功能共用。
 * 坐标: col 0-16, row 0-22
 */
public final class DotCanvas {
    public static final int COLS = 17;
    public static final int ROWS = 23;
    public static final int CELLS = COLS * ROWS;

    public final int[] px = new int[CELLS];

    public DotCanvas() { clear(); }

    public void clear() { java.util.Arrays.fill(px, 0); }

    public void set(int col, int row, int v) {
        if (col < 0 || col >= COLS || row < 0 || row >= ROWS) return;
        px[row * COLS + col] = v;
    }

    public int get(int col, int row) {
        if (col < 0 || col >= COLS || row < 0 || row >= ROWS) return 0;
        return px[row * COLS + col];
    }

    public void fill(int v) { java.util.Arrays.fill(px, v); }

    /** 画一条水平线。 */
    public void hline(int col, int row, int len, int v) {
        for (int i = 0; i < len; i++) set(col + i, row, v);
    }

    /** 画一条竖线。 */
    public void vline(int col, int row, int len, int v) {
        for (int i = 0; i < len; i++) set(col, row + i, v);
    }

    public void rect(int x, int y, int w, int h, int v) {
        for (int r = y; r < y + h; r++)
            for (int c = x; c < x + w; c++) set(c, r, v);
    }

    /** 画一个 3x5 的像素字符。字体覆盖 0-9 A-Z 和少量符号。 */
    public void char3x5(int x, int y, char ch, int v) {
        String[] g = FONT3X5.lookup(ch);
        if (g == null) g = FONT3X5.lookup('?');
        for (int r = 0; r < 5 && r < g.length; r++) {
            String row = g[r];
            for (int c = 0; c < 3 && c < row.length(); c++)
                if (row.charAt(c) == '1') set(x + c, y + r, v);
        }
    }

    /** 画一串文字，返回结束后的列位置。 */
    public int text(int x, int y, String s, int v) {
        int cx = x;
        for (int i = 0; i < s.length(); i++) {
            char3x5(cx, y, s.charAt(i), v);
            cx += 4;
        }
        return cx;
    }

    /** 居中画一串文字。 */
    public int textCentered(int y, String s, int v) {
        int w = s.length() * 4 - 1;
        return text(Math.max(0, (COLS - w) / 2), y, s, v);
    }

    /** 把 5x7 的大字符画上（用于数字大字）。 */
    public void bigChar(int x, int y, char ch, int v) {
        String[] g = FONT5X7.lookup(ch);
        if (g == null) g = FONT5X7.lookup('?');
        for (int r = 0; r < 7 && r < g.length; r++) {
            String row = g[r];
            for (int c = 0; c < 5 && c < row.length(); c++)
                if (row.charAt(c) == '1') set(x + c, y + r, v);
        }
    }

    public int bigText(int x, int y, String s, int v) {
        int cx = x;
        for (int i = 0; i < s.length(); i++) {
            bigChar(cx, y, s.charAt(i), v);
            cx += 6;
        }
        return cx;
    }

    public int bigTextCentered(int y, String s, int v) {
        int w = s.length() * 6 - 1;
        return bigText(Math.max(0, (COLS - w) / 2), y, s, v);
    }

    /** 3x6 字形，步进 4 列。17 列最多 4 个字符。 */
    public void char3x6(int x, int y, char ch, int v) {
        String[] g = FONT3X6.lookup(ch);
        if (g == null) g = FONT3X6.lookup('?');
        for (int r = 0; r < 6 && r < g.length; r++) {
            String row = g[r];
            for (int c = 0; c < 3 && c < row.length(); c++)
                if (row.charAt(c) == '1') set(x + c, y + r, v);
        }
    }

    /** 3x6 文字，步进 4 列。 */
    public int text6(int x, int y, String s, int v) {
        int cx = x;
        for (int i = 0; i < s.length(); i++) {
            char3x6(cx, y, s.charAt(i), v);
            cx += 4;
        }
        return cx;
    }

    /**
     * 时间专用：MM:SS 单行居中。
     * MM 和 SS 用 3x6（各 7 列），冒号用 1 列点，总宽 15 列，放得下。
     */
    public void timeCentered(int y, int minutes, int seconds, int v) {
        String mm = two(minutes);
        String ss = two(seconds);
        int totalW = 7 + 1 + 7;   // MM(7) + ':'(1) + SS(7)
        int x = Math.max(0, (COLS - totalW) / 2);
        text6(x, y, mm, v);
        set(x + 7, y + 1, v);
        set(x + 7, y + 4, v);
        text6(x + 8, y, ss, v);
    }

    private static String two(int n) {
        return (n < 10 ? "0" : "") + n;
    }

    /** 按宽度裁剪字符串，保证不溢出。maxChars 个 3x5 字符。 */
    public static String fit3x5(String s, int maxChars) {
        if (s == null) return "";
        String t = s.toUpperCase();
        return t.length() <= maxChars ? t : t.substring(0, maxChars);
    }

    /** 右对齐文字（不裁剪，按实际宽度靠右）。 */
    public int textRight(int y, String s, int v) {
        if (s == null) return 0;
        int w = s.length() * 3 - 1;
        int x = Math.max(0, COLS - w);
        text(x, y, s, v);
        return x;
    }

    /** 居中文字，自动裁剪到能放下的长度。 */
    public int textFit(int y, String s, int v) {
        return textCentered(y, fit3x5(s, 4), v);
    }

    /**
     * 3 列小字体（2 宽 + 1 间隔 = 步进 3），17 列可放 6 个字符。
     * 用于正文这类需要多字的场景。
     */
    public void char3x5narrow(int x, int y, char ch, int v) {
        String[] g = FONT3X5.lookup(ch);
        if (g == null) g = FONT3X5.lookup('?');
        for (int r = 0; r < 5 && r < g.length; r++) {
            String row = g[r];
            for (int c = 0; c < 3 && c < row.length(); c++)
                if (row.charAt(c) == '1') set(x + c, y + r, v);
        }
    }

    /**
     * 滚动正文：3x5 字体、步进 4 列，17 列一屏 4 字。
     * 文本更长时整行左移循环，像跑马灯。
     * offset 由调用方按时间推进。
     */
    public void scrollText(int y, String s, int v, int offset) {
        String t = s == null ? "" : s.toUpperCase();
        int visible = 4;                       // 一屏 4 字
        if (t.isEmpty()) return;
        if (t.length() <= visible) {
            textCentered(y, t, v);
            return;
        }
        // 用空格补长，实现无缝循环
        String padded = t + "     ";
        int start = Math.floorMod(offset, padded.length());
        String win = padded.substring(start, start + visible);
        textCentered(y, win, v);
    }

    /** 3x5 字体在一屏内最多能放的字符数。 */
    public static int maxChars3x5() { return 4; }
}
