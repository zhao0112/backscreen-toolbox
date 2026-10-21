package com.local.backscreengame;

/*
 * BackScreen.java
 *
 * Copyright (c) 2026 HarryLoong (xzl01)
 * Copyright (c) 2026 Will (zhao0112) — 后续修改
 *
 * 来源：https://github.com/xzl01/back-screen-game （MIT License）
 *
 * 本文件在原作者实现基础上沿用，MIT 许可要求保留此版权与许可声明。
 * 许可全文见仓库根目录 LICENSE。
 *
 * 通过反射访问 StepOS 的 display_ext 服务（StepOSDisplayManager）。
 * 服务端（StepOSDisplayManagerService）对背屏相关方法没有权限校验；
 * App 端以 debuggable=true 安装以绕过非 SDK 接口限制。
 */

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;

import java.lang.reflect.Method;

public final class BackScreen {
    private static final String TAG = "BackSnake";

    private Object manager;
    private Method setFrame;
    private Method setAgentState;
    private Method setEnabled;
    private Method setDisplayMode;
    private Method setBrightness;
    private Method setAutoBrightness;
    private Method clearFrame;
    private Method isAutoRotate;
    private Method setAutoRotate;

    private boolean prevAutoRotate = true;
    private boolean autoRotateSaved = false;
    private SharedPreferences prefs;

    public String lastError = "";
    public boolean ready = false;

    public boolean init(Context context) {
        try {
            prefs = context.getSharedPreferences("backscreen", Context.MODE_PRIVATE);
            Class<?> cls = Class.forName("android.hardware.display.StepOSDisplayManager");
            manager = context.getSystemService("display_ext");
            if (manager == null) {
                lastError = "display_ext 服务为 null";
                return false;
            }
            setFrame = cls.getMethod("setBackScreenAgentFrame", int[].class, Bundle.class);
            setAgentState = cls.getMethod("setBackScreenAgentState", String.class, Bundle.class);
            setEnabled = cls.getMethod("setBackScreenEnabled", boolean.class);
            setDisplayMode = cls.getMethod("setBackScreenDisplayMode", int.class);
            setBrightness = cls.getMethod("setBackScreenBrightness", int.class);
            setAutoBrightness = cls.getMethod("setBackScreenAutoBrightnessEnabled", boolean.class);
            clearFrame = cls.getMethod("clearBackScreenAgentFrame");
            isAutoRotate = cls.getMethod("isBackScreenAutoRotateEnabled");
            setAutoRotate = cls.getMethod("setBackScreenAutoRotateEnabled", boolean.class);
            ready = true;
            lastError = "";
            return true;
        } catch (Throwable t) {
            ready = false;
            lastError = t.getClass().getSimpleName() + ": " + t.getMessage();
            Log.e(TAG, "init failed", t);
            return false;
        }
    }

    /** 开游戏前：打开背屏、常亮、固定亮度、关背屏自动旋转、进入 agent 工作态。 */
    public boolean start() {
        if (!ready) return false;
        try {
            setEnabled.invoke(manager, true);
            setDisplayMode.invoke(manager, 1);          // 1 = ALWAYS 常亮
            setAutoBrightness.invoke(manager, false);
            setBrightness.invoke(manager, 200);
            // 关掉背屏自动旋转，否则倾斜手机控制方向时画面会跟着转 90°
            try {
                Object v = isAutoRotate.invoke(manager);
                prevAutoRotate = (v instanceof Boolean) && (Boolean) v;
                autoRotateSaved = true;
            } catch (Throwable ignore) {
                autoRotateSaved = false;
            }
            if (autoRotateSaved && prefs != null) {
                prefs.edit().putBoolean("pending_restore", true)
                        .putBoolean("prev_auto_rotate", prevAutoRotate).apply();
            }
            setAutoRotate.invoke(manager, false);
            setAgentState.invoke(manager, "wake_ai_key_click", null);
            return true;
        } catch (Throwable t) {
            lastError = describe(t);
            Log.e(TAG, "start failed", t);
            return false;
        }
    }

    /** 推一帧：int[391]，按 row*17+col 索引，值 0-255 是每个点的亮度。 */
    public boolean send(int[] frame) {
        if (!ready) return false;
        try {
            setFrame.invoke(manager, frame, null);
            return true;
        } catch (Throwable t) {
            lastError = describe(t);
            Log.e(TAG, "send failed", t);
            return false;
        }
    }

    /** 退出游戏：清自定义帧、恢复显示模式 auto、恢复背屏自动旋转。 */
    public void stop() {
        if (!ready) return;
        try {
            clearFrame.invoke(manager);
        } catch (Throwable ignore) {
        }
        try {
            setDisplayMode.invoke(manager, 0);          // 0 = AUTO
        } catch (Throwable ignore) {
        }
        if (autoRotateSaved) {
            try {
                setAutoRotate.invoke(manager, prevAutoRotate);
            } catch (Throwable ignore) {
            }
            autoRotateSaved = false;
        }
        if (prefs != null) prefs.edit().remove("pending_restore").apply();
    }

    /** App 被强杀/升级后，下次启动时把背屏恢复干净（显示模式、帧、自动旋转）。 */
    public void restorePending() {
        if (!ready || prefs == null) return;
        if (!prefs.getBoolean("pending_restore", false)) return;
        boolean prev = prefs.getBoolean("prev_auto_rotate", true);
        try { clearFrame.invoke(manager); } catch (Throwable ignore) { }
        try { setDisplayMode.invoke(manager, 0); } catch (Throwable ignore) { }
        try { setAutoRotate.invoke(manager, prev); } catch (Throwable ignore) { }
        prefs.edit().remove("pending_restore").apply();
        Log.i(TAG, "restorePending: back screen state restored");
    }

    private static String describe(Throwable t) {
        Throwable c = t.getCause() != null ? t.getCause() : t;
        return c.getClass().getSimpleName() + ": " + c.getMessage();
    }
}
