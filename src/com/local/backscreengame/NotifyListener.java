package com.local.backscreengame;

import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.os.Bundle;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 通知监听：抓新消息，重点显示验证码。
 *
 * 需要用户在「设置 → 应用 → 特殊权限 → 通知使用权」手动授权，App 无法自动开启。
 */
public final class NotifyListener extends NotificationListenerService {
    public interface Listener {
        void onNewMessage(String source, String text, boolean isCode);
    }

    private static volatile Listener listener;

    public static void setListener(Listener l) { listener = l; }

    /** 验证码模式：4-8 位数字，或含 KEY/CODE 关键字。 */
    private static final Pattern CODE = Pattern.compile("\\b(\\d{4,8})\\b");
    private static final Pattern CODE_KW = Pattern.compile(
            "(?i)(验证码|校验码|动态码|verification\\s*code|otp|code)\\D{0,12}(\\d{4,8})");

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null) return;
        Bundle ex = sbn.getNotification() == null ? null : sbn.getNotification().extras;
        if (ex == null) return;
        CharSequence title = ex.getCharSequence(android.app.Notification.EXTRA_TITLE);
        CharSequence text = ex.getCharSequence(android.app.Notification.EXTRA_TEXT);
        CharSequence big = ex.getCharSequence(android.app.Notification.EXTRA_BIG_TEXT);
        String src = sbn.getPackageName();
        if (title == null && text == null && big == null) return;

        String body = (text == null ? "" : text.toString());
        if (big != null && big.length() > body.length()) body = big.toString();
        String full = (title == null ? "" : title.toString()) + " " + body;

        String code = extractCode(full);
        String source = title != null && title.length() > 0 ? title.toString() : src;

        Listener l = listener;
        if (l != null) {
            try { l.onNewMessage(source, code != null ? code : body.trim(), code != null); }
            catch (Throwable ignore) { }
        }
    }

    /** 从文本里提验证码，没有则返回 null。 */
    public static String extractCode(String s) {
        if (s == null) return null;
        Matcher m = CODE_KW.matcher(s);
        if (m.find()) return m.group(2);
        m = CODE.matcher(s);
        if (m.find()) return m.group(1);
        return null;
    }
}
