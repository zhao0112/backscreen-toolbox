package com.local.backscreengame;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioRecord;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * 背屏主页面。
 *
 * 结构（Operate 表面 —— 用户来操作，不是来浏览）：
 *   1. 状态徽章：背屏是否连上
 *   2. Hero：17×23 实时预览 + 当前内容
 *   3. 背屏电源：主开关
 *   4. 正在显示：6 种内容，2 列网格
 *   5. 上下文：只显示当前内容相关的设置
 */
public final class ToolboxActivity extends Activity {

    private static final String TAG = "BackToolbox";
    private static final int COLS = 17;
    private static final int ROWS = 23;
    private static final int CELLS = COLS * ROWS;

    // 内容 ID
    public static final int F_VIDEO = 0;
    public static final int F_TIMER = 1;
    public static final int F_MESSAGE = 2;
    public static final int F_MUSIC = 3;
    public static final int F_SPECTRUM = 4;
    public static final int F_FROG = 5;

    private BackScreen backScreen;
    // 模块状态统一放在 RenderHub，页面只读
    private final RenderHub hub = RenderHub.get();
    private final TimerModule timer = TimerStore.get();
    private final MessageModule message = hub.message;
    private final SpriteAnim nomi = hub.nomi;

    private final AppState state = AppState.get();
    private NavBar nav;

    /** 当前显示的内容（与全局状态同步）。 */
    private int currentFeature() { return state.backContent; }

    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextView statusView;
    private DotView dotView;

    /**
     * 推帧循环交给 RenderHub（全局单例）。
     * 页面切走时循环不���，背屏继续工作；页面切回来时预览立即跟上。
     */
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            try {
                int[] f = hub.renderInto();
                if (dotView != null) dotView.setFrame(f);
            } catch (Throwable t) {
                if (!renderErrorShown) {
                    renderErrorShown = true;
                    Log.e(TAG, "renderFrame crashed", t);
                    try { if (statusView != null) statusView.setText("渲染异常：" + describe(t)); }
                    catch (Throwable ignore) { }
                }
            }
            handler.postDelayed(this, 1000 / 20);
        }
    };

    private boolean renderErrorShown = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        installCrashGuard();
        try {
            super.onCreate(savedInstanceState);
        } catch (Throwable t) {
            fatal("super.onCreate 失败：" + describe(t));
            return;
        }

        try {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } catch (Throwable ignore) { }

        try {
            setContentView(R.layout.activity_toolbox);
            statusView = findViewById(R.id.tb_status);
            dotView = findViewById(R.id.tb_dots);
        } catch (Throwable t) {
            Log.e(TAG, "setContentView failed", t);
            fatal("界面加载失败：" + describe(t));
            return;
        }

        // 背屏初始化
        try {
            backScreen = new BackScreen();
            if (!backScreen.init(this)) {
                state.backScreenReady = false;
                state.backScreenError = backScreen.lastError;
                Log.w(TAG, "back screen unavailable: " + backScreen.lastError);
            } else {
                state.backScreenReady = true;
                state.backScreen = backScreen;
                backScreen.restorePending();
                state.backOn = backScreen.start();
                if (state.backOn) {
                    try { BackScreenService.start(this); } catch (Throwable t) {
                        Log.e(TAG, "service start failed", t);
                    }
                }
            }
        } catch (Throwable t) {
            Log.e(TAG, "backScreen init crashed", t);
            backScreen = new BackScreen();
            state.backScreenReady = false;
            state.backScreenError = describe(t);
        }

        // 视频加载
        try {
            hub.loadVideo(this);
        } catch (Throwable t) {
            Log.e(TAG, "loadVideo crashed", t);
        }

        NotifyListener.setListener(new NotifyListener.Listener() {
            @Override
            public void onNewMessage(String source, String text, boolean isCode) {
                message.show(source, text, isCode);
                if (currentFeature() == F_MESSAGE) hub.onContentChanged();
            }
        });

        bindContent(R.id.tb_tile_video, F_VIDEO);
        bindContent(R.id.tb_tile_timer, F_TIMER);
        bindContent(R.id.tb_tile_msg, F_MESSAGE);
        bindContent(R.id.tb_tile_music, F_MUSIC);
        bindContent(R.id.tb_tile_spec, F_SPECTRUM);
        bindContent(R.id.tb_tile_frog, F_FROG);

        // 背屏电源
        try {
            ToggleSwitch power = findViewById(R.id.tb_power);
            if (power != null) {
                power.setChecked(state.backOn);
                power.setOnToggle(new ToggleSwitch.OnToggle() {
                    @Override public void onToggled(boolean on) {
                        setBackPower(on);
                    }
                });
            }
        } catch (Throwable t) { Log.e(TAG, "power bind failed", t); }

        // 频谱：麦克风权限入口
        try {
            findViewById(R.id.tb_mic_action).setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    if (hub.isAudioRunning()) {
                        hub.stopAudio();
                    } else if (hasMicPermission()) {
                        hub.startAudio(ToolboxActivity.this);
                    } else {
                        requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 100);
                    }
                    updateUi();
                }
            });
        } catch (Throwable t) { Log.e(TAG, "mic bind failed", t); }

        // 通知使用权
        try {
            findViewById(R.id.tb_notif_action).setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    try {
                        startActivity(new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"));
                    } catch (Throwable t) {
                        try { startActivity(new Intent(android.provider.Settings.ACTION_SETTINGS)); }
                        catch (Throwable ignore) { }
                    }
                }
            });
        } catch (Throwable t) { Log.e(TAG, "notif bind failed", t); }

        // 底部导航（共享组件）
        try {
            nav = new NavBar(this, findViewById(R.id.tb_root));
            nav.setSelected(NavBar.TAB_BACK);
        } catch (Throwable t) {
            Log.e(TAG, "nav bind failed", t);
        }
    }

    private void backScreenStopQuiet() {
        try { if (backScreen != null) backScreen.stop(); } catch (Throwable ignore) { }
    }

    // ---------------- 交互绑定 ----------------

    /** 内容卡：点一下就切背屏显示的内容。 */
    private void bindContent(int viewId, final int feature) {
        try {
            View v = findViewById(viewId);
            if (v == null) return;
            v.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View view) {
                    // 切内容
                    state.backContent = feature;
                    hub.onContentChanged();
                    // 频谱需要麦克风，切过去顺手开
                    if (feature == F_SPECTRUM && !hub.isAudioRunning()) {
                        if (hasMicPermission()) hub.startAudio(ToolboxActivity.this);
                    }
                    updateUi();
                }
            });
        } catch (Throwable t) {
            Log.e(TAG, "bindContent failed id=" + viewId, t);
        }
    }

    /** 背屏电源开关。 */
    private void setBackPower(boolean on) {
        try {
            if (on) {
                if (backScreen == null) backScreen = new BackScreen();
                if (!backScreen.init(this)) {
                    state.backOn = false;
                    state.backScreenError = backScreen.lastError;
                    updateUi();
                    return;
                }
                state.backScreen = backScreen;
                state.backScreenReady = true;
                state.backOn = backScreen.start();
                if (state.backOn) {
                    try { BackScreenService.start(this); } catch (Throwable ignore) { }
                }
            } else {
                backScreenStopQuiet();
                state.backOn = false;
                try { BackScreenService.stop(this); } catch (Throwable ignore) { }
            }
        } catch (Throwable t) {
            Log.e(TAG, "setBackPower failed", t);
            state.backOn = false;
        }
        updateUi();
    }

    private boolean hasMicPermission() {
        try {
            return checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                    == PackageManager.PERMISSION_GRANTED;
        } catch (Throwable t) {
            return false;
        }
    }

    // ---------------- UI 更新 ----------------

    private void updateUi() {
        try {
            if (statusView != null) {
                StringBuilder sb = new StringBuilder();
                sb.append(state.backScreenReady ? "背屏已连接" : "背屏不可用");
                sb.append(state.backOn ? " · 背屏开" : " · 背屏关");

                String name = CONTENT_NAMES[currentFeature()];
                if (!name.isEmpty()) sb.append(" · ").append(name);

                String live = "";
                if (currentFeature() == F_VIDEO) live = hub.videoCount() > 0 ? " · 循环" : " · 内置动画";
                if (currentFeature() == F_SPECTRUM) live = hub.isAudioRunning() ? " · 实时" : "";
                sb.append(live);
                statusView.setText(sb.toString());
            }

            // 上下文区：只显示当前内容相关的
            // 上下文区：只显示当前内容相关的设置
            setVisible(R.id.tb_context, currentFeature() == F_SPECTRUM);
            setVisible(R.id.tb_notif_row, currentFeature() == F_MESSAGE);

            // 麦克风状态文案
            try {
                TextView ms = findViewById(R.id.tb_mic_state);
                if (ms != null) {
                    if (hub.isAudioRunning()) {
                        ms.setText("已开启 · 录音不保存不上传");
                        ms.setTextColor(0xFF35E0B0);
                    } else if (!hasMicPermission()) {
                        ms.setText("未授权麦克风");
                        ms.setTextColor(0xFFFF9F45);
                    } else {
                        ms.setText(hub.audioError().isEmpty() ? "未开启" : hub.audioError());
                        ms.setTextColor(0xFFFF9F45);
                    }
                }
                TextView ma = findViewById(R.id.tb_mic_action);
                if (ma != null) {
                    ma.setText(hub.isAudioRunning() ? "关闭采集"
                            : (hasMicPermission() ? "开启采集" : "去授权"));
                }
            } catch (Throwable ignore) { }

            // 内容卡的选中态
            int[] ids = { R.id.tb_tile_video, R.id.tb_tile_timer, R.id.tb_tile_msg,
                          R.id.tb_tile_music, R.id.tb_tile_spec, R.id.tb_tile_frog };
            int[] feats = { F_VIDEO, F_TIMER, F_MESSAGE, F_MUSIC, F_SPECTRUM, F_FROG };
            for (int i = 0; i < ids.length; i++) {
                try {
                    View v = findViewById(ids[i]);
                    if (v == null) continue;
                    boolean on = (feats[i] == currentFeature());
                    v.setBackgroundResource(on ? R.drawable.bg_card_hi : R.drawable.bg_card);
                    v.setAlpha(on ? 1f : 0.62f);
                } catch (Throwable ignore) { }
            }
        } catch (Throwable t) {
            Log.e(TAG, "updateUi failed", t);
        }
    }

    private void setVisible(int id, boolean vis) {
        try {
            View v = findViewById(id);
            if (v != null) v.setVisibility(vis ? View.VISIBLE : View.GONE);
        } catch (Throwable ignore) { }
    }

    @Override
    protected void onResume() {
        super.onResume();
        handler.removeCallbacks(tick);
        hub.startLoop();
        handler.post(tick);
        updateUi();
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(tick);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(tick);
        super.onDestroy();
    }

    // ---------------- 崩溃兜底 ----------------

    private void installCrashGuard() {
        try {
            final Thread.UncaughtExceptionHandler prev =
                    Thread.getDefaultUncaughtExceptionHandler();
            Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
                @Override public void uncaughtException(Thread t, Throwable e) {
                    try {
                        java.io.File dir = getExternalFilesDir(null);
                        if (dir == null) dir = getFilesDir();
                        java.io.File f = new java.io.File(dir, "crash.log");
                        java.io.PrintWriter w = new java.io.PrintWriter(
                                new java.io.FileOutputStream(f, true), true);
                        w.println(new java.util.Date() + " " + e);
                        e.printStackTrace(w);
                        w.close();
                    } catch (Throwable ignore) { }
                    if (prev != null) prev.uncaughtException(t, e);
                }
            });
        } catch (Throwable ignore) { }
    }

    private static final String[] CONTENT_NAMES = {
        "动画", "计时器", "消息", "音乐", "频谱", "NOMI 表情"
    };

    /** 致命错误：用系统对话框，不依赖本 App 布局。 */
    private void fatal(String msg) {
        Log.e(TAG, "FATAL " + msg);
        try {
            android.app.AlertDialog.Builder b = new android.app.AlertDialog.Builder(this);
            b.setTitle("背屏工具箱启动失败");
            b.setMessage(msg + "\n\n已写入 crash.log");
            b.setPositiveButton("退出", new android.content.DialogInterface.OnClickListener() {
                @Override public void onClick(android.content.DialogInterface d, int w) { finish(); }
            });
            b.setCancelable(false);
            b.show();
        } catch (Throwable t) {
            Log.e(TAG, "fatal dialog failed", t);
        }
    }

    private static String describe(Throwable t) {
        if (t == null) return "未知错误";
        Throwable c = t.getCause() != null ? t.getCause() : t;
        String msg = c.getMessage();
        return c.getClass().getSimpleName() + (msg == null ? "" : ": " + msg);
    }
}
