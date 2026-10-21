package com.local.backscreengame;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.util.Log;

/**
 * 计时器页。
 *
 * 交互原则：**只有一个设置入口 —— 无极滑块**。
 * 没有预设、没有 +/-、没有输入框、没有档位。
 * 滑块拖到哪里，时长就是多少。
 *
 * 页面职责：
 * 1. 展示当前计时器（大字时间）
 * 2. 用滑块设定时长
 * 3. 开始 / 重置
 * 4. 管理其他计时器（新建 = 用当前滑块时长）
 *
 * 副作用：进入本页时，背屏内容切换为计时器。
 */
public final class TimerManageActivity extends Activity {

    private TimerModule timer;
    private LinearLayout listBox;
    private TextView bigTime, bigName, subtitle;
    private DurationSlider slider;
    private NavBar nav;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean syncingSlider = false;

    private final Runnable refresh = new Runnable() {
        @Override public void run() {
            renderList();
            handler.postDelayed(this, 500);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_timer_manage);

        timer = TimerStore.get();

        listBox = findViewById(R.id.tm_list);
        bigTime = findViewById(R.id.tm_big_time);
        bigName = findViewById(R.id.tm_big_name);
        subtitle = findViewById(R.id.tm_subtitle);
        slider = findViewById(R.id.tm_slider);

        // 滑块 = 唯一的时长设置入口
        if (slider != null) {
            slider.setOnDurationChange(new DurationSlider.OnDurationChange() {
                @Override public void onDurationChanged(int seconds, boolean fromUser) {
                    if (syncingSlider) return;
                    TimerModule.Entry e = timer.current();
                    if (e == null || e.running) return;
                    // 直接改总时长，倒计时会跟着走
                    e.totalSeconds = seconds;
                    e.startAt = 0;
                    e.endAt = 0;
                    e.finished = false;
                    renderList();
                }
            });
        }

        findViewById(R.id.tm_start).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { timer.toggle(); renderList(); }
        });
        findViewById(R.id.tm_reset).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { timer.reset(); renderList(); }
        });
        // 新建：直接用当前滑块的时长，不再弹窗、不再要名字
        findViewById(R.id.tm_add).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                int sec = slider != null ? slider.getValueSeconds() : 25 * 60;
                timer.addCountdownSeconds(autoLabel(sec), sec);
                renderList();
                toast("已新建：" + DurationSlider.format(sec));
            }
        });
    }

    private static String autoLabel(int sec) {
        int h = sec / 3600, m = (sec % 3600) / 60, s = sec % 60;
        if (h > 0) return h + "时" + (m > 0 ? m + "分" : "");
        if (m > 0) return m + "分" + (s > 0 ? s + "秒" : "");
        return s + "秒";
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 底部导航
        if (nav == null) {
            try { nav = new NavBar(this, findViewById(android.R.id.content)); }
            catch (Throwable t) { Log.e("TimerManage", "nav bind failed", t); }
        }
        if (nav != null) nav.setSelected(NavBar.TAB_TIMER);

        // 关键：切到计时器页 -> 背屏就显示计时器
        AppState.get().backContent = ToolboxActivity.F_TIMER;
        RenderHub.get().onContentChanged();

        handler.removeCallbacks(refresh);
        handler.post(refresh);
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(refresh);
    }

    // ---------------- 渲染 ----------------

    private void renderList() {
        if (listBox == null) return;

        TimerModule.Entry cur = timer.current();

        // 主卡：时间 + 名称 + 状态
        if (bigTime != null && cur != null) {
            bigTime.setText(DurationSlider.format(cur.seconds()));
            bigName.setText(cur.label);
        }
        if (subtitle != null) {
            int running = 0;
            for (TimerModule.Entry e : timer.all()) if (e.running) running++;
            subtitle.setText(running > 0
                    ? running + " 个运行中"
                    : "横向拖动设定时长");
        }

        // 滑块同步（只在没跑的时候跟随当前计时器）
        if (slider != null && cur != null
                && !cur.running && cur.mode == TimerModule.MODE_COUNTDOWN) {
            syncingSlider = true;
            slider.setValueSeconds(cur.totalSeconds, false);
            syncingSlider = false;
        }

        // 列表：极简行 —— 点整行切换当前，只有一个播放/暂停按钮
        listBox.removeAllViews();
        int curIdx = timer.selected;
        for (int i = 0; i < timer.count(); i++) {
            final int idx = i;
            TimerModule.Entry e = timer.all().get(i);
            boolean on = (i == curIdx);

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(13), dp(12), dp(8), dp(12));
            row.setBackgroundResource(on ? R.drawable.bg_card_hi : R.drawable.bg_card);
            row.setClickable(true);
            row.setFocusable(true);
            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(56));
            rp.setMargins(0, 0, 0, dp(7));
            row.setLayoutParams(rp);

            // 点整行 = 选中它
            row.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    timer.selected = idx;
                    renderList();
                }
            });

            // 名称 + 状态
            LinearLayout col = new LinearLayout(this);
            col.setOrientation(LinearLayout.VERTICAL);
            TextView nm = new TextView(this);
            nm.setText(e.label);
            nm.setTextSize(14);
            nm.setTextColor(0xFFF7F8F8);
            col.addView(nm);
            TextView st = new TextView(this);
            String s;
            if (e.mode == TimerModule.MODE_STOPWATCH) s = "正计时";
            else if (e.finished) s = "已到点";
            else if (e.running) s = "运行中";
            else s = DurationSlider.format(e.totalSeconds);
            st.setText(s);
            st.setTextSize(11.5f);
            st.setTextColor(e.running ? 0xFF35E0B0 : 0xFF9BA3AD);
            col.addView(st);

            LinearLayout.LayoutParams colp = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
            colp.setMargins(dp(2), 0, 0, 0);
            row.addView(col, colp);

            // 时间
            TextView tv = new TextView(this);
            tv.setText(DurationSlider.format(e.seconds()));
            tv.setTextSize(16);
            tv.setTextColor(0xFFF7F8F8);
            tv.setGravity(Gravity.CENTER);
            row.addView(tv, new LinearLayout.LayoutParams(dp(74),
                    LinearLayout.LayoutParams.WRAP_CONTENT));

            // 只有一个按钮：播放 / 暂停
            row.addView(iconBtn(e.running ? R.drawable.ic_pause : R.drawable.ic_play,
                    e.running ? 0xFF35E0B0 : 0xFF9BA3AD, new View.OnClickListener() {
                        @Override public void onClick(View v) {
                            timer.selected = idx;
                            timer.all().get(idx).toggle();
                            renderList();
                        }
                    }));

            listBox.addView(row);
        }
    }

    private View iconBtn(int icon, int tint, View.OnClickListener l) {
        ImageView v = new ImageView(this);
        v.setImageResource(icon);
        v.setColorFilter(tint);
        v.setClickable(true);
        v.setFocusable(true);
        v.setOnClickListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(34), dp(34));
        lp.setMargins(dp(2), 0, 0, 0);
        v.setLayoutParams(lp);
        return v;
    }
}
