package com.local.backscreengame;

/**
 * 计时器的全局单例。
 * 工具箱页和管理页共享同一份数据，切换页面不丢状态。
 */
public final class TimerStore {
    private static TimerModule instance;

    private TimerStore() { }

    public static synchronized TimerModule get() {
        if (instance == null) instance = new TimerModule();
        return instance;
    }
}
