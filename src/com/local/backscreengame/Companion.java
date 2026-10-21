package com.local.backscreengame;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
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

    /**
     * 情绪状态。只描述「它在干什么」。
     *
     * 亮度不是状态 —— 它是贯穿所有状态的独立维度，见 brightness()。
     * 把亮度做成状态是错的：夜里收到通知不该"变成另一个状态"，
     * 而应该是"通知脸 + 很暗"。
     */
    public static final int S_IDLE = 0;      // 静默：一颗呼吸点
    public static final int S_LISTEN = 1;    // 聆听：你真的在说话
    public static final int S_HAPPY = 2;     // 开心：副歌到了
    public static final int S_THINK = 3;     // 思考：通知后在想是谁
    public static final int S_NOTIFY = 4;    // 通知：有人找你
    public static final int S_SLEEP = 5;     // 睡觉：闭眼，几乎熄灭

    private boolean on = false;
    private int state = S_IDLE;

    /** 进入陪伴模式的时间（毫秒）。 */
    private long since = 0;

    /** 上次给通知反应的时间 —— 用来避免通知连发时疯狂切换。 */
    private long lastNotifyAt = 0;

    /** 音乐能量（0–1），由 MusicModule 的分析结果喂进来。 */
    private float musicEnergy = 0f;
    private float musicPeak = 0f;

    /** 麦克风是否在工作。 */
    private boolean micOn = false;

    /** 你现在是不是真的在说话（由 RenderHub.isSpeaking() 喂进来）。 */
    private boolean speaking = false;

    /** 环境光（lux），0 表示传感器不可用。 */
    private float ambientLux = -1f;

    /**
     * 当前亮度系数（0–1）。
     *
     * 环境光没有传感器时退回时钟判断。
     * 这个值必须平滑过渡 —— 亮度突然跳变在暗处非常刺眼。
     */
    private float brightness = 1.0f;

    /** 距离上次「有意义的交互」过了多久。 */
    private long lastInteraction = 0;

    private final SpriteAnim anim = NomiFace.anim();

    // ---------------- 环境光传感器 ----------------
    //
    // 为什么需要：夜里它还全亮，是因为之前没有亮度调节。
    // 只用时钟判断白天/夜晚是不够的 ——
    // 白天在暗房间里、或者夜里开着灯，真实光线和时钟完全相反。

    private SensorManager sensorManager;
    private Sensor lightSensor;
    private boolean lightAvailable = false;

    /**
     * 启动环境光监听。
     *
     * 这台机器可能没有光线传感器（背屏设备常配的是距离/接近传感器），
     * 所以启动失败要能安静地退回时钟判断，而不是崩。
     */
    public void startLightSensor(android.content.Context ctx) {
        try {
            sensorManager = (SensorManager) ctx.getSystemService(
                    android.content.Context.SENSOR_SERVICE);
            if (sensorManager == null) return;
            lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
            if (lightSensor == null) {
                // 没有光线传感器：ambientLux 保持 -1，走时钟兜底
                lightAvailable = false;
                Log.i(TAG, "no light sensor, using clock");
                return;
            }
            lightAvailable = sensorManager.registerListener(
                    lightListener, lightSensor,
                    SensorManager.SENSOR_DELAY_NORMAL);
            Log.i(TAG, "light sensor registered: " + lightAvailable);
        } catch (Throwable t) {
            lightAvailable = false;
            Log.w(TAG, "light sensor failed", t);
        }
    }

    private final SensorEventListener lightListener = new SensorEventListener() {
        @Override public void onSensorChanged(SensorEvent e) {
            try {
                if (e == null || e.values == null || e.values.length < 1) return;
                ambientLux = e.values[0];
            } catch (Throwable ignore) { }
        }
        @Override public void onAccuracyChanged(Sensor s, int a) { }
    };

    private void stopLightSensor() {
        try {
            if (sensorManager != null && lightAvailable) {
                sensorManager.unregisterListener(lightListener);
            }
        } catch (Throwable ignore) { }
    }

    // ---------------- 开关 ----------------

    public boolean isOn() { return on; }

    private android.content.Context appCtx;

    public void start(android.content.Context ctx) {
        if (on) return;
        on = true;
        appCtx = ctx;
        startLightSensor(ctx);
        since = SystemClock.elapsedRealtime();
        lastInteraction = since;
        state = S_IDLE;
        anim.restart();
        Log.i(TAG, "companion start");
    }

    public void stop() {
        if (!on) return;
        on = false;
        stopLightSensor();
        state = S_IDLE;
        musicEnergy = 0f;
        musicPeak = 0f;
        micOn = false;
        speaking = false;
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
            case S_LISTEN: return "在听你说话";
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

    /** 麦克风是否在工作（只是权限/状态，不代表有人在说话）。 */
    public void setMicOn(boolean on) {
        micOn = on;
    }

    /**
     * 你是不是真的在说话。
     *
     * 这个和「麦克风开着」是两件事：
     * 麦克风开着可能只是频谱功能在跑，环境里没人说话。
     */
    public void setSpeaking(boolean on) {
        speaking = on;
    }

    /** 环境光（lux）。传负数表示不可用。 */
    public void setAmbientLight(float lux) {
        ambientLux = lux;
    }

    /**
     * 目标亮度：按环境光算，夜间暗、白天亮。
     *
     * 环境光不可用时退回时钟 —— 但时钟只是兜底，
     * 因为白天在暗房间里 / 夜里开着灯，靠时钟是错的。
     */
    private float targetBrightness() {
        float t;
        if (ambientLux >= 0) {
            // 环境光越暗越暗。50lux 以下接近全黑环境。
            if (ambientLux <= 8) t = 0.10f;         // 深夜/关灯
            else if (ambientLux <= 60) t = 0.22f;   // 夜晚室内
            else if (ambientLux <= 250) t = 0.55f;  // 傍晚/阴天
            else t = 1.0f;                           // 白天
        } else {
            // 兜底：没有传感器，用时钟
            int h = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY);
            if (h >= 23 || h < 6) t = 0.10f;
            else if (h < 9 || h >= 19) t = 0.45f;
            else t = 1.0f;
        }
        // 睡眠态再压暗一档
        if (state == S_SLEEP) t = Math.min(t, 0.16f);
        return t;
    }

    /** 当前亮度系数（给 UI 显示用）。 */
    public float brightness() { return brightness; }

    /** 环境光不可用时为 true（UI 要告诉用户用的是时钟判断）。 */
    public boolean lightUnknown() { return ambientLux < 0; }

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

        // 亮度平滑过渡：暗处跳变非常刺眼，所以每帧只走一小步。
        float target = targetBrightness();
        float diff = target - brightness;
        if (Math.abs(diff) > 0.002f) {
            // 变暗比变亮慢一点 —— 突然变亮更刺眼
            float rate = diff < 0 ? 0.004f : 0.010f;
            brightness += diff * rate;
            if (Math.abs(target - brightness) < 0.01f) brightness = target;
        }

        // 通知态持续一段时间后转思考
        if (state == S_NOTIFY && silent > 8000L) {
            state = S_THINK;
        }
        if (state == S_THINK && silent > 20000L) {
            state = S_IDLE;
        }

        // 你真的在说话 -> 看着你。
        // 注意是 speaking（真实人声检测），不是 micOn（麦克风开着）。
        if (speaking) {
            state = S_LISTEN;
            return;
        }
        // 说完话之后回到静默，不要一直停在聆听脸
        if (state == S_LISTEN && !speaking) {
            state = S_IDLE;
        }

        // 音乐能量突变 = 副歌 = 开心
        if (musicPeak > 0.72f && musicEnergy > 0.45f) {
            state = S_HAPPY;
            return;
        }
        if (state == S_HAPPY && musicEnergy < 0.2f) {
            state = S_IDLE;
        }

        // 深夜睡觉：优先看环境光（真黑了就睡），
        // 传感器不可用时退回时钟。
        boolean dark = false;
        if (ambientLux >= 0) {
            dark = ambientLux <= 8;
        } else {
            int hour = java.util.Calendar.getInstance().get(
                    java.util.Calendar.HOUR_OF_DAY);
            dark = hour >= 23 || hour < 6;
        }
        if (dark && (state == S_IDLE || state == S_HAPPY)) {
            state = S_SLEEP;
            return;
        }
        if (!dark && state == S_SLEEP) {
            state = S_IDLE;
        }

        // 白天太久不理它也会犯困
        if (silent > 300_000L && state == S_IDLE && !dark) {
            state = S_SLEEP;
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
            // 亮度是独立维度：所有状态画完之后统一缩放。
            // 不是"某个状态很暗"，而是"不管什么状态，晚上都暗"。
            c.dim(brightness);
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
        // 呼吸点放在正中（17x23 的中心是第 11 行，第 8 列）
        int center = 11 * DotCanvas.COLS + 8;
        // 满亮度 190 —— 之后会被 dim() 按环境光缩放
        int bright = 60 + (int) (v * 130);
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
                                c.px[rr * DotCanvas.COLS + cc] = 55;
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
        return stateName() + " · 亮度 " + Math.round(brightness * 100) + "%";
    }
}