package com.local.backscreengame;

/**
 * 全局状态：背屏连接、电源、后台运行、权限。
 * 三个页面共享同一份，任何地方改了别处立刻同步。
 */
public final class AppState {
    private static final AppState INSTANCE = new AppState();

    public boolean backScreenReady;     // 背屏 API 是否可用
    public boolean backOn = true;        // 背屏电源
    public boolean serviceRunning;       // 前台服务
    public boolean micPermission;
    public boolean notifPermission;      // 通知使用权（通知监听）
    public boolean postPermission;       // 前台服务通知权限
    public String backScreenError = "";

    /**
     * 当前背屏显示的内容。
     * 任何页面切到前台都更新它，渲染循环按它推帧 ——
     * 这样「切到计时器页，背屏就显示计时器」自然成立。
     */
    public int backContent = com.local.backscreengame.ToolboxActivity.F_VIDEO;

    /** 背屏句柄，由 ToolboxActivity 初始化时放入，RenderHub 用它推帧。 */
    public BackScreen backScreen;

    public static AppState get() { return INSTANCE; }

    private AppState() { }

    public void refreshPermissions(android.content.Context c) {
        try {
            micPermission = c.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)
                    == android.content.pm.PackageManager.PERMISSION_GRANTED;
        } catch (Throwable t) { micPermission = false; }

        try {
            notifPermission = android.provider.Settings.Secure.getString(
                    c.getContentResolver(), "enabled_notification_listeners") != null;
        } catch (Throwable t) { notifPermission = false; }

        try {
            postPermission = true;
            if (android.os.Build.VERSION.SDK_INT >= 33) {
                postPermission = c.checkSelfPermission("android.permission.POST_NOTIFICATIONS")
                        == android.content.pm.PackageManager.PERMISSION_GRANTED;
            }
        } catch (Throwable t) { postPermission = true; }
    }
}
