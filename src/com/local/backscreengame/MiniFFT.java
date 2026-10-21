package com.local.backscreengame;

/**
 * 自实现 FFT 频谱。
 *
 * 为什么不用 Visualizer：这台设备上 Visualizer 引擎直接返回
 * "Cannot initialize Visualizer engine, error: -3"，完全不可用。
 * 所以从 AudioRecord 的裸 PCM 自己算频谱。
 *
 * Radix-2 Cooley-Tukey，N=1024，Hann 窗。
 */
public final class MiniFFT {
    public static final int N = 1024;
    private static final int LOG_N = 10;          // log2(1024)

    private final float[] re = new float[N];
    private final float[] im = new float[N];
    private final float[] cos = new float[N / 2];
    private final float[] sin = new float[N / 2];
    private final float[] window = new float[N];
    private final float[] mag = new float[N / 2];
    private final float[] bandSmooth = new float[64];

    public MiniFFT() {
        for (int i = 0; i < N / 2; i++) {
            cos[i] = (float) Math.cos(-2 * Math.PI * i / N);
            sin[i] = (float) Math.sin(-2 * Math.PI * i / N);
        }
        // Hann 窗
        for (int i = 0; i < N; i++) {
            window[i] = (float) (0.5 * (1 - Math.cos(2 * Math.PI * i / (N - 1))));
        }
    }

    /**
     * 计算频谱。
     * @param pcm   交错采样的 16 位 PCM（mono）
     * @param len   有效样本数
     * @return 频段强度 0..255，长度 = 64
     */
    public int[] spectrum(short[] pcm, int len) {
        int n = Math.min(len, N);
        if (n < 64) return null;

        // 1) 先减均值去掉直流
        float mean = 0f;
        for (int i = 0; i < n; i++) mean += pcm[i];
        mean /= n;

        // 2) 加 Hann 窗
        for (int i = 0; i < N; i++) {
            float s = 0f;
            if (i < n) s = (pcm[i] - mean) / 32768f;
            re[i] = s * window[i];
            im[i] = 0f;
        }

        fft();

        // 3) 加窗会重新引入直流残留（bin0 能量很大），
        //    这里把 bin0 和 bin1 强制清零，否则低频段全部饱和成 255
        re[0] = 0f; im[0] = 0f;
        re[1] = 0f; im[1] = 0f;

        // 幅度谱
        for (int i = 0; i < N / 2; i++) {
            float m = (float) Math.sqrt(re[i] * re[i] + im[i] * im[i]);
            mag[i] = m;
        }

        // 映射到对数频带（更符合人耳）
        int BANDS = 64;
        int[] out = new int[BANDS];
        float nyquist = 22050f;   // 44.1k 采样
        for (int b = 0; b < BANDS; b++) {
            // 20Hz - 16kHz 对数分布
            float f0 = 20f * (float) Math.pow(16000f / 20f, b / (double) BANDS);
            float f1 = 20f * (float) Math.pow(16000f / 20f, (b + 1) / (double) BANDS);
            int i0 = (int) (f0 / nyquist * (N / 2));
            int i1 = (int) (f1 / nyquist * (N / 2));
            // 低频段可能不足 1 个 bin：保证至少 2 个，否则该段永远取不到真实峰值
            if (i1 <= i0) i1 = i0 + 2;
            if (i1 > N / 2) i1 = N / 2;
            if (i0 >= N / 2) i0 = N / 2 - 1;
            float peak = 0f;
            for (int i = i0; i < i1; i++) {
                if (mag[i] > peak) peak = mag[i];
            }
            // dB 映射
            float db = 20f * (float) Math.log10(peak + 1e-7f);
            float norm = (db + 60f) / 60f;      // -60dB..0dB -> 0..1
            if (norm < 0) norm = 0;
            if (norm > 1) norm = 1;
            // 平滑，避免跳动
            bandSmooth[b] = bandSmooth[b] * 0.55f + norm * 0.45f;
            out[b] = (int) (bandSmooth[b] * 255f);
        }
        return out;
    }

    /** 原地 radix-2 FFT。 */
    private void fft() {
        // 位反转置换
        for (int i = 1, j = 0; i < N; i++) {
            int bit = N >> 1;
            for (; (j & bit) != 0; bit >>= 1) j ^= bit;
            j ^= bit;
            if (i < j) {
                float tr = re[i]; re[i] = re[j]; re[j] = tr;
                float ti = im[i]; im[i] = im[j]; im[j] = ti;
            }
        }
        // 蝶形运算
        for (int len = 2; len <= N; len <<= 1) {
            int half = len >> 1;
            int step = N / len;
            for (int i = 0; i < N; i += len) {
                int k = 0;
                for (int j = 0; j < half; j++) {
                    int a = i + j;
                    int b = i + j + half;
                    float wr = cos[k];
                    float wi = sin[k];
                    float tr = re[b] * wr - im[b] * wi;
                    float ti = re[b] * wi + im[b] * wr;
                    re[b] = re[a] - tr;
                    im[b] = im[a] - ti;
                    re[a] += tr;
                    im[a] += ti;
                    k += step;
                }
            }
        }
    }
}
