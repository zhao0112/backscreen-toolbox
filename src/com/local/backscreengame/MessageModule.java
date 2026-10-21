package com.local.backscreengame;

/**
 * 新消息提示。
 *
 * 17x23 背屏的硬约束：3x5 字体一屏只有 4 个字符。
 * 所以验证码用 3x6 大字（4 位 = 15 列），普通消息用滚动跑马灯。
 * 最长 HOLD_MS 后自动清除。
 */
public final class MessageModule {
    public static final long HOLD_MS = 25000;

    public String source = "";
    public String text = "";
    public boolean isCode;
    public boolean active;
    private long shownAt;

    public void show(String source, String text, boolean isCode) {
        this.source = source == null ? "" : source;
        this.text = text == null ? "" : text;
        this.isCode = isCode;
        this.active = true;
        this.shownAt = System.currentTimeMillis();
    }

    public void clear() {
        active = false;
        source = "";
        text = "";
        isCode = false;
    }

    public void tick() {
        if (!active) return;
        if (System.currentTimeMillis() - shownAt > HOLD_MS) clear();
    }

    public int remainSec() {
        if (!active) return 0;
        long left = HOLD_MS - (System.currentTimeMillis() - shownAt);
        return (int) Math.max(0, left / 1000);
    }

    public void render(DotCanvas c) {
        c.clear();

        if (!active) {
            c.textFit(10, "NOMSG", 55);
            return;
        }

        // 顶部状态条：验证码高亮
        c.rect(0, 0, DotCanvas.COLS, 1, isCode ? 255 : 110);

        if (isCode) {
            // 验证码：3x6 大字，只取后 4 位（4*4-1 = 15 列）
            c.textFit(2, "CODE", 170);
            String digits = digitsOnly(text);
            String show = digits.length() > 4
                    ? digits.substring(digits.length() - 4)
                    : padLeft(digits, 4);
            // 3x6 步进 4，4 字符 = 15 列，居中
            c.text6(1, 8, show, 255);
            // 左：来源（最多4字符，占 col0-14），右：剩余秒数（占 col15-16 用小点阵）
            c.text(0, 15, DotCanvas.fit3x5(clean(source), 4), 140);
        } else {
            // 普通消息：来源一行 + 正文滚动
            c.textFit(3, clean(source), 235);
            int off = (int) ((System.currentTimeMillis() / 320) % 1000);
            c.scrollText(10, clean(text), 190, off);
        }

        // 剩余秒数：只在普通消息时显示（验证码画面空间已满）
        if (!isCode) {
            String rs = remainSec() + "S";
            c.text(DotCanvas.COLS - rs.length() * 4 + 1, 18, rs, 75);
        }
    }

    private static String digitsOnly(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch >= '0' && ch <= '9') sb.append(ch);
        }
        return sb.toString();
    }

    private static String padLeft(String s, int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = s.length(); i < n; i++) sb.append('0');
        return sb.append(s).toString();
    }

    /** 只保留字体表里有的大写字母数字，其余变空格。 */
    private static String clean(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder();
        String up = s.toUpperCase();
        for (int i = 0; i < up.length(); i++) {
            char ch = up.charAt(i);
            if ((ch >= 'A' && ch <= 'Z') || (ch >= '0' && ch <= '9')) sb.append(ch);
            else sb.append(' ');
        }
        return sb.toString();
    }
}