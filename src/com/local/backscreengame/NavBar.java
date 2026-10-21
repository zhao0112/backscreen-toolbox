package com.local.backscreengame;

import android.app.Activity;
import android.content.Intent;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

/**
 * 底部导航栏。四个页面共用同一套逻辑，保证行为一致。
 *
 * 职责：
 * - 切换页面
 * - 显示选中态（薄荷绿）
 *
 * 关键约定：切到哪个 tab，背屏就显示那个 tab 的内容。
 * 背屏只有一个内容位，所以「陪伴」必须是 tab 而不是内容卡 ——
 * 否则它会和频谱、视频抢同一个位置，开启了也看不到。
 */
public final class NavBar {

    public static final int TAB_BACK = 0;
    public static final int TAB_TIMER = 1;
    public static final int TAB_COMPANION = 2;
    public static final int TAB_SETTINGS = 3;

    private final Activity act;
    private final View root;

    public NavBar(Activity act, View root) {
        this.act = act;
        this.root = root;
        wire();
    }

    private void wire() {
        // 背屏 tab
        find(R.id.nav_back).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (act instanceof ToolboxActivity) return; // 已在背屏页
                act.startActivity(new Intent(act, ToolboxActivity.class));
                act.finish();
            }
        });
        // 计时器 tab
        find(R.id.nav_timer).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (act instanceof TimerManageActivity) return;
                act.startActivity(new Intent(act, TimerManageActivity.class));
                act.finish();
            }
        });
        // 陪伴 tab
        find(R.id.nav_companion).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (act instanceof CompanionActivity) return;
                act.startActivity(new Intent(act, CompanionActivity.class));
                act.finish();
            }
        });
        // 设置 tab
        find(R.id.nav_settings).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (act instanceof SettingsActivity) return;
                act.startActivity(new Intent(act, SettingsActivity.class));
                act.finish();
            }
        });
    }

    /** 设置当前选中的 tab。 */
    public void setSelected(int tab) {
        paint(R.id.nav_back_icon, R.id.nav_back_text, tab == TAB_BACK);
        paint(R.id.nav_timer_icon, R.id.nav_timer_text, tab == TAB_TIMER);
        paint(R.id.nav_companion_icon, R.id.nav_companion_text, tab == TAB_COMPANION);
        paint(R.id.nav_settings_icon, R.id.nav_settings_text, tab == TAB_SETTINGS);
    }

    private void paint(int iconId, int textId, boolean on) {
        View iv = find(iconId);
        View tv = find(textId);
        int c = on ? 0xFF35E0B0 : 0xFF767C85;
        if (iv instanceof ImageView) ((ImageView) iv).setColorFilter(c);
        if (tv instanceof TextView) ((TextView) tv).setTextColor(c);
    }

    private View find(int id) {
        View v = root.findViewById(id);
        if (v == null) v = act.findViewById(id);
        return v;
    }
}
