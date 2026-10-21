package com.local.backscreengame;

import android.os.SystemClock;
import android.util.Log;

/**
 * 陪伴模式。
 *
 * 这不是一个"显示 NOMI 表情"的功能开关，而是一套状态机。
 * 核心原则：陪伴不需要用户操作，它自己待着，偶尔有反应。
 *
 * 为什么这样设计（这是产品判断，不是技术选择）：
 *
 * 1. 391 个点承载不了"信息"，只能承载"状态"。
 *    所以陪伴的输出是情绪和存在感，不是通知内容、不是歌词。
 *
 * 2. 陪伴 vs 工具的区别在于"要不要你的注意力"。
 *    工具要你盯着它看，陪伴不需要 —— 所以默认状态是"静默"，
 *    只有在你主动翻过来看的时候才有明显反馈。
 *
 * 3. 陪伴要有"时间感"。它知道你多久没理它了，
 *    表情会随着这个时间变化，而不是永远同一个待机脸。
 *
 * 4. 通知只给表情，不给内容。
 *    背屏是唯一能"假装没看到通知"的地方 —— 主屏亮屏有社交压力，
 *    背屏没有。这是背屏独占的价值。
 *
 * 状态优先级（高到低）：
 *   通知反应 > 音乐共鸣 > 思考 > 静默陪伴
 *
 * 所有状态都由本地信号驱动（通知、麦克风、计时器），
 * 不依赖 Amoo 内部状态。
 */
public final class Companion {

    private static final String TAG = "Companion";

    /** 情绪状态。背屏只显示这些，不显示文字。 */
    public static final int S_IDLE = 0;      // 静默陪伴：几乎不亮
    public static final int S_LISTEN = 1;    // 聆听：你在说话，它在听
    public static final int S_HAPPY = 2;     // 开心：副歌到了 / 好消息
    public static final int S_THINK = 3;     // 思考：正在处理
    public static final int S_NOTIFY = 4;    // 通知：有人找你
    public static final int S_SLEEP = 5;     // 睡觉：深夜

    private boolean on = false;
    private int state = S_IDLE;

    /** 进入陪伴模式的时间（毫秒）。 */
    private long since = 0;

    /** 上次给通知反应的时间 —— 用来避免通知连发时疯狂切换。 */
    private long lastNotifyAt = 0;

    /** 音乐能量（0–1），由 MusicModule 的分析结果喂进来。 */
    private float musicEnergy = 0f;
    private float musicPeak = 0f;

    /** 麦克风是否在工作（有人在说话）。 */
    private boolean listening = false;

    /** 距离上次「有意义的交互」过了多久。 */
    private long lastInteraction = 0;

    private final SpriteAnim anim = NomiFace.anim();

    // ---------------- 开关 ----------------

    public boolean isOn() { return on; }

    public void start() {
        if (on) return;
        on = true;
        since = SystemClock.elapsedRealtime();
        lastInteraction = since;
        state = S_IDLE;
        anim.restart();
        Log.i(TAG, "companion start");
    }

    public void stop() {
        if (!on) return;
        on = false;
        state = S_IDLE;
        musicEnergy = 0f;
        musicPeak = 0f;
        listening = false;
        Log.i(TAG, "companion stop");
    }

    /** 用户回来了（翻过手机看背屏）。 */
    public void poke() {
        lastInteraction = SystemClock.elapsedRealtime();
    }

    public int state() { return state; }

    /** 当前状态的短文案，给设置页显示用。 */
    public String stateName() {
        switch (state) {
            case S_LISTEN: return "在听";
            case S_HAPPY: return "开心";
            case S_THINK: return "在想事情";
            case S_NOTIFY: return "有消息";
            case S_SLEEP: return "睡着了";
            default: return "安静待着";
        }
    }

    // ---------------- 外部信号 ----------------

    /**
     * 通知到达。
     *
     * 只给表情不给内容 —— 这是设计决定，不是技术限制。
     * 背屏告诉你「有人找我」，不告诉你「谁说了什么」。
     */
    public void onNotify() {
        lastInteraction = SystemClock.elapsedRealtime();
        long now = SystemClock.elapsedRealtime();
        // 30 秒内不重复触发通知态，否则连发通知会让表情抽搐
        if (now - lastNotifyAt < 30_000L) return;
        lastNotifyAt = now;
        state = S_NOTIFY;
    }

    /** 音乐能量（0–1）。副歌时峰值会突然升高。 */
    public void setMusic(float energy, float peak) {
        musicEnergy = energy;
        musicPeak = peak;
    }

    /** 麦克风是否在工作。 */
    public void setListening(boolean on) {
        listening = on;
    }

    // ---------------- 状态推进 ----------------

    /**
     * 每次推帧调用。决定当前该显示哪个表情。
     *
     * 优先级：通知 > 音乐 > 聆听 > 思考 > 静默。
     * 「思考」不是检测出来的，是通知之后的那段时间 ——
     * 收到通知先给表情，然后进入"在想这是谁"的思考态。
     */
    public void update() {
        try {
            advance();
        } catch (Throwable t) {
            // 状态推进失败就退回静默，不影响渲染
            state = S_IDLE;
        }
    }

    private void advance() {
        if (!on) return;

        long now = SystemClock.elapsedRealtime();
        long silent = now - lastInteraction;

        // 深夜自动睡觉
        int hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY);
        if (hour >= 23 || hour < 6) {
            state = S_SLEEP;
            return;
        }

        // 通知态持续一段时间后，转成思考
        if (state == S_NOTIFY && silent > 8000L) {
            state = S_THINK;
        }
        // 思考态再过一段时间，回归静默
        if (state == S_THINK && silent > 20000L) {
            state = S_IDLE;
        }

        // 麦克风在工作 = 你在说话 = 它在听
        if (listening && silent < 4000L) {
            state = S_LISTEN;
            return;
        }

        // 音乐能量突变 = 副歌 = 开心
        if (musicPeak > 0.72f && musicEnergy > 0.45f) {
            state = S_HAPPY;
            return;
        }
        if (state == S_HAPPY && musicEnergy < 0.2f) {
            state = S_IDLE;
        }

        // 长时间没有任何交互 = 睡觉（白天也算疲倦）
        if (silent > 300_000L && state == S_IDLE) {
            state = S_SLEEP;
            return;
        }
        if (silent < 30_000L && state == S_SLEEP) {
            state = S_IDLE;
        }
    }

    /** 久没理它了有多久（分钟）。 */
    public long silentMinutes() {
        if (!on) return 0;
        return (SystemClock.elapsedRealtime() - lastInteraction) / 60000L;
    }

    // ---------------- 渲染 ----------------

    /**
     * 把当前情绪画到背屏。
     *
     * 静默陪伴不是"什么都不画" —— 那样背屏就是死的。
     * 它是一颗极暗的呼吸点：不要求你注意，但你知道它在。
     */
    public void render(DotCanvas c) {
        // 兜底：render 每秒调 20 次，抛异常会打断整个推帧循环，
        // 背屏直接黑掉。所以这里必须自己吃掉所有异常。
        try {
            if (!on) {
                c.clear();
                return;
            }
            update();

            switch (state) {
                case S_SLEEP:
                case S_LISTEN:
                    anim.render(c);
                    break;
                case S_NOTIFY:
                case S_HAPPY:
                case S_THINK:
                    anim.render(c);
                    break;
                default:
                    // 静默：一颗极暗的呼吸点
                    renderBreathing(c);
                    break;
            }
        } catch (Throwable t) {
            // 宁可这一帧不画，也不能让推帧循环挂掉
            try { c.clear(); } catch (Throwable ignore) { }
        }
    }

    /**
     * 静默陪伴：一个缓慢明灭的点。
     *
     * 这是"陪伴"和"工具"的分界线 ——
     * 工具要你的注意力，陪伴不需要。
     */
    private void renderBreathing(DotCanvas c) {
        c.clear();
        long t = SystemClock.elapsedRealtime() / 100L;   // 每 100ms 一档
        double phase = (t % 70) / 70.0;
        // 用余弦做呼吸曲线，底部不是全黑，而是留一点
        double v = (1 - Math.cos(phase * Math.PI * 2)) / 2.0;
        int center = 11 * 17 + 8;    // 中心点
        int bright = 40 + (int) (v * 90);
        c.px[center] = bright;

        // 偶尔（每 12 秒）向外扩一圈涟漪 —— "我还在"
        long ripple = (SystemClock.elapsedRealtime() / 1000L) % 12;
        if (ripple < 3) {
            int ring = (int) ripple;    // 0,1,2
            int r = 3 + ring * 2;
            for (int dr = -r; dr <= r; dr++) {
                for (int dc = -r; dc <= r; dc++) {
                    int d2 = dr * dr + dc * dc;
                    if (d2 == r * r) {
                        int rr = 11 + dr, cc = 8 + dc;
                        if (rr >= 0 && rr < DotCanvas.ROWS && cc >= 0 && cc < DotCanvas.COLS) {
                            if (c.px[rr * DotCanvas.COLS + cc] == 0) {
                                c.px[rr * DotCanvas.COLS + cc] = 30;
                            }
                        }
                    }
                }
            }
        }
    }

    /** 供设置页显示的一句状态说明。 */
    public String describe() {
        if (!on) return "未开启";
        return stateName() + " · 安静 " + silentMinutes() + " 分钟";
    }
}