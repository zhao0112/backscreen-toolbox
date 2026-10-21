package com.local.backscreengame;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.TextView;

/**
 * 设置页。
 *
 * 分组：背屏 / 权限 / 关于。
 * 所有权限行都显示真实状态，点击跳系统设置。
 */
public final class SettingsActivity extends Activity {

    private final AppState state = AppState.get();
    private NavBar nav;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            setContentView(R.layout.activity_settings);
        } catch (Throwable t) {
            android.util.Log.e("Settings", "inflate failed", t);
            finish();
            return;
        }

        // 底部导航
        try {
            nav = new NavBar(this, findViewById(android.R.id.content));
            nav.setSelected(NavBar.TAB_SETTINGS);
        } catch (Throwable t) {
            android.util.Log.e("Settings", "nav bind failed", t);
        }

        // 权限跳转
        try {
            findViewById(R.id.set_notif).setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    try {
                        startActivity(new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"));
                    } catch (Throwable t) {
                        try { startActivity(new Intent(Settings.ACTION_SETTINGS)); } catch (Throwable ignore) { }
                    }
                }
            });
            findViewById(R.id.set_mic).setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    try {
                        startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                android.net.Uri.parse("package:" + getPackageName())));
                    } catch (Throwable ignore) { }
                }
            });
            findViewById(R.id.set_post).setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    try {
                        startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                android.net.Uri.parse("package:" + getPackageName())));
                    } catch (Throwable ignore) { }
                }
            });
        } catch (Throwable ignore) { }

        // 背屏电源
        try {
            ToggleSwitch sw = findViewById(R.id.set_power);
            sw.setChecked(state.backOn);
            sw.setOnToggle(new ToggleSwitch.OnToggle() {
                @Override public void onToggled(boolean on) {
                    state.backOn = on;
                    try {
                        BackScreenService.start(SettingsActivity.this);
                    } catch (Throwable ignore) { }
                }
            });
        } catch (Throwable ignore) { }
    }

    @Override
    protected void onResume() {
        super.onResume();
        state.refreshPermissions(this);

        try {
            TextView t = findViewById(R.id.set_notif_state);
            if (t != null) {
                t.setText(state.notifPermission ? "已授权 · 验证码与消息可推背屏" : "未授权 · 消息无法推送");
                t.setTextColor(state.notifPermission ? 0xFF35E0B0 : 0xFFFF9F45);
            }
            TextView m = findViewById(R.id.set_mic_state);
            if (m != null) {
                m.setText(state.micPermission ? "已授权 · 仅频谱开启时采集" : "未授权 · 频谱无法开启");
                m.setTextColor(state.micPermission ? 0xFF35E0B0 : 0xFFFF9F45);
            }
            TextView p = findViewById(R.id.set_post_state);
            if (p != null) {
                p.setText(state.postPermission ? "已授权 · 后台运行正常" : "未授权 · 后台会被系统杀掉");
                p.setTextColor(state.postPermission ? 0xFF35E0B0 : 0xFFFF9F45);
            }
            TextView v = findViewById(R.id.set_version);
            if (v != null) {
                v.setText("v10");
            }
        } catch (Throwable ignore) { }
    }
}
