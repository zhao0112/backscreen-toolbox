package com.local.backscreengame;

import java.util.ArrayList;
import java.util.List;

/**
 * 背屏计时器管理。
 *
 * 需求：不要只有 25 分钟番茄钟，要能管理任意时长。
 * 实现：多计时器列表 + 任意时长（由页面上的无极滑块设定）。
 */
public final class TimerModule {
    public static final int MODE_STOPWATCH = 0;
    public static final int MODE_COUNTDOWN = 1;

    /** 一个计时器。 */
    public static final class Entry {
        public String label = "";
        public int mode = MODE_COUNTDOWN;
        public int totalSeconds = 25 * 60;
        public long endAt;
        public long startAt;
        public boolean running;
        public boolean finished;

        public Entry(String label, int mode, int totalSeconds) {
            this.label = label;
            this.mode = mode;
            this.totalSeconds = totalSeconds;
        }

        public int seconds() {
            long now = System.currentTimeMillis();
            if (mode == MODE_COUNTDOWN) {
                if (!running) return totalSeconds;
                long ms = endAt - now;
                return ms <= 0 ? 0 : (int) (ms / 1000);
            }
            if (!running && startAt == 0) return 0;
            return (int) ((now - startAt) / 1000);
        }

        public void start() {
            running = true;
            finished = false;
            if (mode == MODE_COUNTDOWN) endAt = System.currentTimeMillis() + totalSeconds * 1000L;
            else startAt = System.currentTimeMillis();
        }

        public void pause() {
            if (!running) return;
            if (mode == MODE_COUNTDOWN && endAt > System.currentTimeMillis()) {
                totalSeconds = (int) ((endAt - System.currentTimeMillis() + 999) / 1000);
            }
            running = false;
        }

        public void reset() {
            running = false;
            finished = false;
            if (mode == MODE_COUNTDOWN) endAt = 0;
        }

        public void toggle() { if (running) pause(); else start(); }

        public void tick() {
            if (!running) return;
            if (mode == MODE_COUNTDOWN && System.currentTimeMillis() >= endAt) {
                running = false;
                finished = true;
            }
        }

        /** 到点后动效已持续多久(ms)，0 表示不在播放。 */
        public long finishAnimMs() {
            if (!finished) return 0;
            long e = System.currentTimeMillis() - (endAt);
            return (e >= 0 && e < 4000) ? e : 0;
        }
    }

    /** 当前选中的计时器下标。 */
    public int selected = 0;

    private final List<Entry> entries = new ArrayList<>();

    public TimerModule() {
        // 默认给几个常用预设
        entries.add(new Entry("番茄钟", MODE_COUNTDOWN, 25 * 60));
        entries.add(new Entry("5分", MODE_COUNTDOWN, 5 * 60));
        entries.add(new Entry("10分", MODE_COUNTDOWN, 10 * 60));
        entries.add(new Entry("15分", MODE_COUNTDOWN, 15 * 60));
        entries.add(new Entry("正计时", MODE_STOPWATCH, 0));
    }

    public List<Entry> all() { return entries; }
    public int count() { return entries.size(); }

    public Entry current() {
        if (entries.isEmpty()) return null;
        if (selected < 0) selected = 0;
        if (selected >= entries.size()) selected = entries.size() - 1;
        return entries.get(selected);
    }

    /** 新增一个自定义倒计时。 */
    public Entry addCountdown(String label, int minutes, int seconds) {
        int total = Math.max(1, minutes * 60 + seconds);
        Entry e = new Entry(label == null || label.isEmpty() ? "自定义" : label,
                MODE_COUNTDOWN, total);
        entries.add(e);
        selected = entries.size() - 1;
        return e;
    }

    /** 新增一个自定义倒计时（按秒）。 */
    public Entry addCountdownSeconds(String label, int seconds) {
        int total = Math.max(1, seconds);
        Entry e = new Entry(label == null || label.isEmpty() ? "自定义" : label,
                MODE_COUNTDOWN, total);
        entries.add(e);
        selected = entries.size() - 1;
        return e;
    }

    public void remove(int index) {
        if (index < 0 || index >= entries.size()) return;
        entries.remove(index);
        if (selected >= entries.size()) selected = entries.size() - 1;
        if (selected < 0) selected = 0;
    }

    public void toggle() { Entry e = current(); if (e != null) e.toggle(); }
    public void reset() { Entry e = current(); if (e != null) e.reset(); }

    public int seconds() { Entry e = current(); return e == null ? 0 : e.seconds(); }
    public boolean running() { Entry e = current(); return e != null && e.running; }
    public int mode() { Entry e = current(); return e == null ? MODE_COUNTDOWN : e.mode; }

    public void tick() {
        for (Entry e : entries) e.tick();
    }

    /** 画到点阵。显示当前计时器。 */
    /**
     * 把计时器名称转成能在 3x5 ASCII 字体里显示的短标签。
     * 中文 -> 拼音首字母（最多 4 个），英文 -> 原样大写。
     */
    private static String asciiLabel(String label) {
        if (label == null) return "";
        String t = label.trim();
        if (t.isEmpty()) return "";
        boolean ascii = true;
        for (int i = 0; i < t.length(); i++) {
            if (t.charAt(i) > 127) { ascii = false; break; }
        }
        if (ascii) {
            String u = t.toUpperCase();
            return u.length() > 4 ? u.substring(0, 4) : u;
        }
        // 中文：取每个字的首个拼音字母（简化的常用映射）
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < t.length() && sb.length() < 4; i++) {
            char ch = t.charAt(i);
            if (ch > 127) {
                String py = pinyin(ch);
                if (py != null) sb.append(py);
            } else if (Character.isLetterOrDigit(ch)) {
                sb.append(Character.toUpperCase(ch));
            }
        }
        return sb.length() == 0 ? "TIME" : sb.toString();
    }

    /** 极简汉字 -> 拼音首字母。只覆盖常用字，认不出就跳过。 */
    private static String pinyin(char ch) {
        switch (ch) {
            case '计': return "J";
            case '时': return "S";
            case '器': return "Q";
            case '番': return "F";
            case '茄': return "Q";
            case '钟': return "Z";
            case '休': return "X";
            case '息': return "X";
            case '专': return "Z";
            case '注': return "Z";
            case '练': return "L";
            case '跑': return "P";
            case '步': return "B";
            case '分': return "F";
            case '秒': return "M";
            case '蛋': return "D";
            case '饭': return "F";
            case '睡': return "S";
            case '醒': return "X";
            case '工': return "G";
            case '作': return "Z";
            case '喝': return "H";
            case '水': return "S";
            case '茶': return "C";
            case '运': return "Y";
            case '动': return "D";
            case '阅': return "Y";
            case '读': return "D";
            case '写': return "X";
            case '码': return "M";
            case '开': return "K";
            case '发': return "F";
            case '会': return "H";
            case '议': return "Y";
            default: return null;
        }
    }

    public void render(DotCanvas c) {
        c.clear();
        Entry e = current();
        if (e == null) { c.textFit(10, "NOTIMER", 60); return; }

        int s = e.seconds();
        int m = s / 60, r = s % 60;
        // 计时器名称（3x5，最多 4 字符 = 12 列，放 cols 1..12）
        // 背屏字体是纯 ASCII，中文会变成 "?"。所以：中文名转拼音缩写，
        // 英文名直接用。太长就截断。
        String nm = asciiLabel(e.label);
        c.text(1, 1, nm, 170);

        // 类型标记 + 位置：分开两行，避免互相挤掉
        String label = e.mode == MODE_STOPWATCH ? "SW" : "TM";
        c.text(1, 3, label, 110);

        // 时间：3x6 紧凑字体
        c.timeCentered(7, m, r, 255);

        // 进度条：轨道暗、填充亮，必须能一眼看出走到哪了
        if (e.mode == MODE_COUNTDOWN && e.totalSeconds > 0) {
            int barW = 15;
            int filled = (int) ((long) (e.totalSeconds - s) * barW / e.totalSeconds);
            filled = Math.max(0, Math.min(barW, filled));
            c.rect(1, 16, barW, 2, 55);      // 轨道
            c.rect(1, 16, filled, 2, 255);    // 已走过的部分
        }

        // 列表位置指示：第 n / 共 m 个（右对齐）
        String pos = (selected + 1) + "/" + count();
        c.textRight(19, pos, 110);

        // 到点动效
        long f = e.finishAnimMs();
        if (f > 0 && ((f / 200) % 2 == 0)) {
            for (int i = 0; i < c.px.length; i += 2) c.px[i] = 255;
            c.textFit(18, "TIMEUP", 0);
        }
    }
}
