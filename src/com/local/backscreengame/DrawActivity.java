package com.local.backscreengame;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

/**
 * 手绘页。
 *
 * 流程：直接背屏点阵上画 -> 投射到背屏。
 * 画完切到别的页面，手绘内容仍在（存在 RenderHub 里）。
 */
public final class DrawActivity extends Activity implements DrawView.Listener {

    private DrawView canvas;
    private TextView statusView;
    private TextView undoBtn, eraserBtn, clearBtn, projectBtn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            setContentView(R.layout.activity_draw);
        } catch (Throwable t) {
            Toast.makeText(this, "手绘页加载失败", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        canvas = findViewById(R.id.dr_canvas);
        statusView = findViewById(R.id.dr_status);
        undoBtn = findViewById(R.id.dr_undo);
        eraserBtn = findViewById(R.id.dr_eraser);
        clearBtn = findViewById(R.id.dr_clear);
        projectBtn = findViewById(R.id.dr_project);

        if (canvas != null) canvas.setListener(this);

        bind(undoBtn, new Runnable() {
            @Override public void run() {
                if (canvas != null) canvas.undo();
                updateUi();
            }
        });
        bind(eraserBtn, new Runnable() {
            @Override public void run() {
                if (canvas == null) return;
                canvas.setEraser(!canvas.isEraser());
                updateUi();
            }
        });
        bind(clearBtn, new Runnable() {
            @Override public void run() {
                if (canvas == null) return;
                canvas.clearCanvas();
                RenderHub.get().clearDrawing();
                updateUi();
            }
        });
        bind(projectBtn, new Runnable() {
            @Override public void run() {
                project();
            }
        });

        updateUi();
    }

    private void bind(View v, final Runnable r) {
        if (v == null) return;
        v.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { r.run(); }
        });
    }

    /** 投射到背屏：保存画面 + 切内容 + 回背屏页。 */
    private void project() {
        if (canvas == null) return;
        if (canvas.isEmpty()) {
            Toast.makeText(this, "先画点什么", Toast.LENGTH_SHORT).show();
            return;
        }
        RenderHub hub = RenderHub.get();
        hub.setDrawing(canvas.frame());
        // 切到背屏页，并让背屏显示手绘内容
        AppState.get().backContent = ToolboxActivity.F_DRAW;
        hub.onContentChanged();
        Toast.makeText(this, "已投射到背屏", Toast.LENGTH_SHORT).show();
        finish();
    }

    @Override
    public void onDrawChanged() {
        runOnUiThread(new Runnable() {
            @Override public void run() {
                // 实时同步到 RenderHub，切页面也不丢
                if (canvas != null) RenderHub.get().setDrawing(canvas.frame());
                updateUi();
            }
        });
    }

    @Override
    public void onProject() { project(); }

    private void updateUi() {
        if (canvas == null) return;
        if (statusView != null) {
            statusView.setText(canvas.isEraser() ? "橡皮模式" : "画笔模式");
        }
        if (eraserBtn != null) {
            eraserBtn.setText(canvas.isEraser() ? "画笔" : "橡皮");
            eraserBtn.setBackgroundResource(canvas.isEraser()
                    ? R.drawable.bg_btn_primary : R.drawable.bg_btn_ghost);
            eraserBtn.setTextColor(canvas.isEraser() ? 0xFF08090A : 0xFFD0D6E0);
        }
        if (undoBtn != null) undoBtn.setAlpha(canvas.canUndo() ? 1f : 0.4f);
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateUi();
    }
}
