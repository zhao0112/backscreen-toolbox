package com.local.backscreengame;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

/**
 * 陪伴页 —— 独立的入口，不是「正在显示」里的一项。
 *
 * 为什么要单独一页：
 * 之前 NOMI 跟视频、消息、频谱平级摆在内容网格里，
 * 结果它看起来像第七个工具，而不是一个一直在的陪伴。
 * 工具箱的逻辑是"挑一个显示什么"，陪伴的逻辑是"它在不在"。
 * 这两件事不该用同一个界面表达。
 *
 * 这一页只回答两个问题：
 *   1. 它现在什么心情（能实时看到）
 *   2. 要不要开着它
 */
public final class CompanionActivity extends Activity {

    private DotView preview;
    private TextView stateView, silentView, switchView;
    private ToggleSwitch power;

    private final Companion companion = RenderHub.get().companion;
    private final DotCanvas companionCanvas = new DotCanvas();
    private final android.os.Handler handler = new android.os.Handler();

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            try {
                companion.render(companionCanvas);
                if (preview != null) preview.setFrame(companionCanvas.px);
                updateState();
            } catch (Throwable t) {
                // 预览画不出来不能崩
            }
            handler.postDelayed(this, 1000 / 12);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            setContentView(R.layout.activity_companion);
        } catch (Throwable t) {
            Toast.makeText(this, "陪伴页加载失败", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        preview = findViewById(R.id.cp_preview);
        stateView = findViewById(R.id.cp_state);
        silentView = findViewById(R.id.cp_silent);
        switchView = findViewById(R.id.cp_switch_label);
        power = findViewById(R.id.cp_power);

        // 开关
        if (power != null) {
            power.setChecked(companion.isOn());
            power.setOnToggle(new ToggleSwitch.OnToggle() {
                @Override public void onToggled(boolean on) {
                    setCompanion(on);
                }
            });
        }

        // 底部导航
        try {
            nav = new NavBar(this, findViewById(R.id.cp_root));
            nav.setSelected(NavBar.TAB_COMPANION);
        } catch (Throwable ignore) { }

        // 进页面就把背屏切到陪伴 ——
        // 和计时器页同一套规则：你在哪个 tab，背屏就是那个东西。
        AppState.get().backContent = ToolboxActivity.F_FROG;
        RenderHub.get().onContentChanged();

        updateState();
    }

    private NavBar nav;

    private void setCompanion(boolean on) {
        if (on) {
            companion.start();
            Toast.makeText(this, "陪伴已开启", Toast.LENGTH_SHORT).show();
        } else {
            companion.stop();
            Toast.makeText(this, "陪伴已关闭", Toast.LENGTH_SHORT).show();
        }
        // 开关切了也要保证背屏内容是陪伴，
        // 否则用户在这里开了开关，背屏还在显示频谱。
        AppState.get().backContent = ToolboxActivity.F_FROG;
        RenderHub.get().onContentChanged();
        updateState();
    }

    private void updateState() {
        if (stateView != null) {
            stateView.setText(companion.isOn() ? companion.stateName() : "没在");
            stateView.setTextColor(companion.isOn() ? 0xFF35E0B0 : 0xFF6B7480);
        }
        if (silentView != null) {
            long m = companion.silentMinutes();
            silentView.setText(m < 1 ? "一直在你身边" : "安静了 " + m + " 分钟");
        }
        if (switchView != null) {
            switchView.setText(companion.isOn() ? "已开启" : "开启陪伴");
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 每次回到这个 tab 都把背屏切回来 ——
        // 你可能刚从频谱页过来，背屏还显示着频谱。
        AppState.get().backContent = ToolboxActivity.F_FROG;
        RenderHub.get().onContentChanged();
        handler.post(tick);
        updateState();
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(tick);
    }
}