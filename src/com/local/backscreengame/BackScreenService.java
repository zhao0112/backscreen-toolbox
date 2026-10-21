package com.local.backscreengame;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

/**
 * 前台服务：让背屏在 App 退到后台时继续工作。
 *
 * 关键点：
 * - 必须用 startForeground()，否则 Android 8+ 会被系统杀掉
 * - 需要 FOREGROUND_SERVICE 权限
 * - Android 14+ 还要 FOREGROUND_SERVICE_MICROPHONE（如果开麦）
 */
public final class BackScreenService extends Service {
    public static final String CHANNEL_ID = "backscreen_service";
    public static final int NOTIFICATION_ID = 1001;

    private static final String ACTION_START = "com.local.backscreengame.START";
    private static final String ACTION_STOP = "com.local.backscreengame.STOP";

    public static void start(Context ctx) {
        Intent i = new Intent(ctx, BackScreenService.class);
        i.setAction(ACTION_START);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ctx.startForegroundService(i);
        } else {
            ctx.startService(i);
        }
    }

    public static void stop(Context ctx) {
        try {
            Intent i = new Intent(ctx, BackScreenService.class);
            i.setAction(ACTION_STOP);
            ctx.startService(i);
        } catch (Throwable ignore) { }
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onCreate() {
        super.onCreate();
        createChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : null;
        if (ACTION_STOP.equals(action)) {
            stopSelf();
            return START_NOT_STICKY;
        }

        // 走前台通知，进程存活优先级提高
        try {
            startForeground(NOTIFICATION_ID, buildNotification());
        } catch (Throwable t) {
            // 某些系统需要 POST_NOTIFICATIONS 权限，失败也不该崩
            android.util.Log.e("BackToolbox", "startForeground failed", t);
            stopSelf();
            return START_NOT_STICKY;
        }

        // 保持存活
        return START_STICKY;
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        try {
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm == null || nm.getNotificationChannel(CHANNEL_ID) != null) return;
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID, "背屏服务", NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("保持背屏在后台运行");
            ch.setShowBadge(false);
            ch.setSound(null, null);
            nm.createNotificationChannel(ch);
        } catch (Throwable ignore) { }
    }

    private Notification buildNotification() {
        Notification.Builder b;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            b = new Notification.Builder(this, CHANNEL_ID);
        } else {
            b = new Notification.Builder(this);
        }
        b.setContentTitle("STEP NEO 背屏")
                .setContentText("背屏运行中")
                .setSmallIcon(android.R.drawable.ic_menu_compass)
                .setOngoing(true);
        try {
            Intent open = new Intent(this, ToolboxActivity.class);
            open.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            PendingIntent pi = PendingIntent.getActivity(
                    this, 0, open,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            b.setContentIntent(pi);
        } catch (Throwable ignore) { }
        return b.build();
    }

    @Override
    public void onDestroy() {
        try { stopForeground(true); } catch (Throwable ignore) { }
        super.onDestroy();
    }
}
