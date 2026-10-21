package com.local.backscreengame;

import android.media.AudioRecord;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

/**
 * 背屏渲染中枢。
 *
 * 为什么需要它：
 * 以前推帧逻辑写在 ToolboxActivity 里，用户切到计时器页后
 * Activity 被 pause/stop，背屏就显示旧内容或停更。
 *
 * 现在：
 * - 循环跑在 RenderHub 里（全局单例），永远不停
 * - 各页面只负责设置 AppState.backContent
 * - 切到计时器页 -> backContent = 计时器 -> 背屏立刻显示计时器
 *
 * 前台服务保证进程不被杀，循环就能一直跑。
 */
public final class RenderHub {

    private static RenderHub instance;
    private static final String TAG = "RenderHub";

    public final MessageModule message = new MessageModule();
    public final MusicModule music = new MusicModule();
    public final SpectrumModule spectrum = new SpectrumModule();
    public final SpriteAnim nomi = NomiFace.anim();
    public final TimerModule timer = TimerStore.get();

    private final DotCanvas canvas = new DotCanvas();
    private final int[] frame = new int[DotCanvas.COLS * DotCanvas.ROWS];
    private final int[] bands = new int[SpectrumModule.BANDS];

    private final Handler handler = new Handler(Looper.getMainLooper());

    // 视频
    private byte[] videoData;
    private int[] videoOffsets;
    private int videoCount;
    private int videoFps = 10;
    private int videoCursor;
    private boolean useBuiltinAnim = false;
    private float builtinPhase = 0f;

    // 频谱采集
    private AudioRecord recorder;
    private Thread audioThread;
    private volatile boolean audioRunning;
    private int audioRate = 44100;
    private String audioError = "";

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            try {
                renderFrame();
            } catch (Throwable t) {
                Log.e(TAG, "render crashed", t);
            }
            handler.postDelayed(this, 1000 / 20);
        }
    };

    private RenderHub() { }

    public static synchronized RenderHub get() {
        if (instance == null) instance = new RenderHub();
        return instance;
    }

    // ---------------- 生命周期 ----------------

    /** 启动推帧循环（幂等）。 */
    public void startLoop() {
        handler.removeCallbacks(tick);
        handler.post(tick);
    }

    /** 背屏内容切换时调用，立刻重画一帧。 */
    public void onContentChanged() {
        try {
            renderFrame();
        } catch (Throwable t) {
            Log.e(TAG, "content change render failed", t);
        }
    }

    public int[] currentFrame() { return frame; }

    public boolean isAudioRunning() { return audioRunning; }

    public String audioError() { return audioError; }

    public void setAudioError(String e) { audioError = e == null ? "" : e; }

    public int videoCount() { return videoCount; }

    public int videoFps() { return videoFps; }

    public int[] bands() { return bands; }

    // ---------------- 渲染 ----------------

    /**
     * 渲染一帧，返回当前帧（页面预览用）。
     *
     * 注意：不要在这里加 DotCanvas 参数 —— 参数名会遮蔽本类的 canvas 字段，
     * 导致拷��没被渲染过的那份画布（预览全黑）。v11 就是踩了这个坑。
     */
    public int[] renderInto() {
        renderFrame();
        return frame;
    }

    private void renderFrame() {
        int content = AppState.get().backContent;
        switch (content) {
            case ToolboxActivity.F_VIDEO:    renderVideo(); break;
            case ToolboxActivity.F_TIMER:    timer.tick(); timer.render(canvas); break;
            case ToolboxActivity.F_MESSAGE:  message.tick(); message.render(canvas); break;
            case ToolboxActivity.F_MUSIC:    music.render(canvas, bands); break;
            case ToolboxActivity.F_SPECTRUM: spectrum.render(canvas, bands); break;
            case ToolboxActivity.F_FROG:     nomi.render(canvas); break;
            default: canvas.clear(); break;
        }
        System.arraycopy(canvas.px, 0, frame, 0, frame.length);

        // 推给背屏
        try {
            BackScreen bs = AppState.get().backScreen;
            if (bs != null && bs.ready && AppState.get().backOn) {
                bs.send(frame);
            }
        } catch (Throwable t) {
            Log.e(TAG, "send to back screen failed", t);
        }
    }

    private void renderVideo() {
        if (videoCount == 0 || videoData == null || videoOffsets == null) {
            // 无素材 -> 内置程序化动画
            if (useBuiltinAnim) renderBuiltinAnim();
            else canvas.clear();
            return;
        }
        int off = videoOffsets[videoCursor];
        if (off < 0 || off + frame.length > videoData.length) {
            canvas.clear();
            return;
        }
        for (int i = 0; i < frame.length; i++) {
            frame[i] = videoData[off + i] & 0xFF;
        }
        System.arraycopy(frame, 0, canvas.px, 0, frame.length);
        videoCursor = (videoCursor + 1) % videoCount;
    }

    // ---------------- 视频加载 ----------------

    public void loadVideo(android.content.Context ctx) {
        try {
            byte[] meta = readAll(ctx.getAssets().open("frames.txt"));
            for (String line : new String(meta, "UTF-8").split("\n")) {
                if (line.startsWith("fps=")) videoFps = Integer.parseInt(line.substring(4).trim());
            }
            videoData = readAll(ctx.getAssets().open("frames.bin"));
            byte[] idx = readAll(ctx.getAssets().open("frames.idx"));
            videoCount = idx.length / 4;
            videoOffsets = new int[videoCount];
            // frames.idx 是小端存的，DataInputStream.readInt 是大端，会读出负数
            for (int i = 0; i < videoCount; i++) {
                int o = i * 4;
                videoOffsets[i] = (idx[o] & 0xFF)
                        | ((idx[o + 1] & 0xFF) << 8)
                        | ((idx[o + 2] & 0xFF) << 16)
                        | ((idx[o + 3] & 0xFF) << 24);
            }
        } catch (Throwable e) {
            // 没有素材时不算错误：改用内置的程序化动画
            Log.i(TAG, "no video assets, using built-in animation: " + e);
            videoCount = 0; videoData = null; videoOffsets = null;
            useBuiltinAnim = true;
        }
    }

    /**
     * 内置程序化动画（无素材时的回退）。
     * 一个旋转的螺旋 + 呼吸光晕，纯算法生成，不含任何第三方素材。
     */
    private void renderBuiltinAnim() {
        builtinPhase += 0.045f;
        if (builtinPhase > 6.2832f) builtinPhase -= 6.2832f;

        java.util.Arrays.fill(canvas.px, 0);
        float cx = 8.0f, cy = 11.0f;
        for (float t = 0; t < 6.2832f; t += 0.22f) {
            float ang = t + builtinPhase;
            float r = 1.0f + t * 1.25f;
            int x = Math.round(cx + r * (float) Math.cos(ang));
            int y = Math.round(cy + r * (float) Math.sin(ang) * 0.85f);
            if (x < 0 || x >= 17 || y < 0 || y >= 23) continue;
            // 越靠外越暗，形成渐隐
            int v = 255 - Math.round(t * 26f);
            if (v < 40) v = 40;
            canvas.px[y * 17 + x] = v;
        }
        // 中心一点
        canvas.px[11 * 17 + 8] = 255;
    }

    private static byte[] readAll(java.io.InputStream is) throws java.io.IOException {
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = is.read(buf)) > 0) bos.write(buf, 0, n);
        is.close();
        return bos.toByteArray();
    }

    // ---------------- 频谱采集 ----------------

    public void startAudio(android.app.Activity act) {
        try {
            if (act.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                act.requestPermissions(
                        new String[]{android.Manifest.permission.RECORD_AUDIO}, 100);
                return;
            }
        } catch (Throwable t) {
            Log.e(TAG, "permission check failed", t);
            return;
        }
        audioError = "";
        int[] rates = {44100, 22050, 16000, 8000};
        Throwable lastErr = null;

        for (int rate : rates) {
            try {
                int minBuf = AudioRecord.getMinBufferSize(rate,
                        android.media.MediaRecorder.AudioSource.MIC,
                        android.media.AudioFormat.CHANNEL_IN_MONO);
                if (minBuf <= 0) {
                    lastErr = new IllegalStateException("采样率 " + rate + " 不支持");
                    continue;
                }
                final int bufSize = Math.max(minBuf, 8192);
                AudioRecord rec = new AudioRecord(android.media.MediaRecorder.AudioSource.MIC, rate,
                        android.media.AudioFormat.CHANNEL_IN_MONO,
                        android.media.AudioFormat.ENCODING_PCM_16BIT, bufSize);
                if (rec.getState() != AudioRecord.STATE_INITIALIZED) {
                    rec.release();
                    lastErr = new IllegalStateException("AudioRecord 初始化失败");
                    continue;
                }
                rec.startRecording();
                recorder = rec;
                audioRate = rate;
                break;
            } catch (Throwable t) {
                lastErr = t;
            }
        }

        if (recorder == null) {
            audioRunning = false;
            audioError = "麦克风打开失败：" + (lastErr == null ? "未知" : lastErr.getMessage());
            Log.e(TAG, "AudioRecord failed", lastErr);
            return;
        }
        audioRunning = true;
        startFFTThread();
    }

    /** 频谱线程：自己读 PCM + 自己做 FFT。Visualizer 引擎在这台机上不可用。 */
    private void startFFTThread() {
        if (audioThread != null) return;
        final MiniFFT fft = new MiniFFT();
        audioThread = new Thread(new Runnable() {
            @Override public void run() {
                final short[] buf = new short[MiniFFT.N * 2];
                int carry = 0;
                while (audioRunning) {
                    try {
                        AudioRecord r = recorder;
                        if (r == null) break;
                        int n = r.read(buf, carry, buf.length - carry);
                        if (n <= 0) continue;

                        int total = carry + n;
                        if (total >= MiniFFT.N) {
                            int[] spec = fft.spectrum(buf, MiniFFT.N);
                            if (spec != null) {
                                for (int b = 0; b < SpectrumModule.BANDS; b++) {
                                    int lo = b * spec.length / SpectrumModule.BANDS;
                                    int hi = (b + 1) * spec.length / SpectrumModule.BANDS;
                                    if (hi <= lo) hi = lo + 1;
                                    int peak = 0;
                                    for (int i = lo; i < hi && i < spec.length; i++) {
                                        if (spec[i] > peak) peak = spec[i];
                                    }
                                    bands[b] = peak;
                                }
                            }
                            int rest = total - MiniFFT.N;
                            System.arraycopy(buf, MiniFFT.N, buf, 0, rest);
                            carry = rest;
                        } else {
                            carry = total;
                        }
                    } catch (Throwable t) {
                        Log.e(TAG, "fft thread died", t);
                        break;
                    }
                }
            }
        }, "back-fft");
        audioThread.start();
    }

    public void stopAudio() {
        audioRunning = false;
        if (audioThread != null) {
            try { audioThread.join(300); } catch (Throwable ignore) { }
            audioThread = null;
        }
        try {
            if (recorder != null) { recorder.stop(); recorder.release(); recorder = null; }
        } catch (Throwable ignore) { }
        java.util.Arrays.fill(bands, 0);
    }
}
