package com.local.backscreengame;

/**
 * 麦克风频谱「山形」。15 个频段，三档灵敏度。
 * 只在主动开始后采集，不保存不上传，停止后释放麦克风。
 */
public final class SpectrumModule {
    public static final int BANDS = 15;
    public int sensitivity = 1;   // 0 低 1 中 2 高
    public int[] heights = new int[BANDS];

    private static final float[] SENS = {0.6f, 1.0f, 1.8f};

    /** 输入 0..255 的频段强度，渲染成山形。 */
    /**
     * 「山形」渲染：柱顶画实线，柱身画渐暗的填充，形成山形轮廓。
     * 用峰值衰减映射，让小音量也有可见高度（否则小声时是一片空白）。
     */
    public void render(DotCanvas c, int[] raw) {
        c.clear();
        float k = SENS[Math.max(0, Math.min(2, sensitivity))];

        // 自适应基线：把输入映射到 0-1，再加底噪，让安静时也有低矮山形
        int peak = 1;
        for (int i = 0; i < BANDS; i++) {
            int v = raw != null && i < raw.length ? raw[i] : 0;
            if (v > peak) peak = v;
        }
        float norm = 220f / Math.max(1, peak);

        int base = 21;          // 山脚所在行
        int maxH = 17;          // 最高可到 row 4

        for (int i = 0; i < BANDS; i++) {
            int v = raw != null && i < raw.length ? raw[i] : 0;
            float n = Math.min(1f, (v * norm / 255f) * k);
            int h = (int) (n * maxH);
            if (h < 1) h = 1;   // 至少 1 行，静音时也是一条平底山脊
            h = Math.min(maxH, h);
            heights[i] = h;

            // 柱身：越靠下越暗，形成实体感
            for (int y = 0; y < h; y++) {
                int row = base - y;
                int v2 = 70 + (int) ((y + 1.0f) / maxH * 150);
                if (v2 > 255) v2 = 255;
                c.set(i + 1, row, v2);
            }
            // 峰顶：最亮的一行，做出轮廓
            c.set(i + 1, base - h, 255);
        }

        // 峰顶连成折线，山形才连续
        for (int i = 0; i < BANDS - 1; i++) {
            int a = heights[i], b = heights[i + 1];
            int lo = Math.min(a, b);
            for (int y = 0; y <= lo; y++) c.set(i + 1, base - y, 255);
        }

        c.hline(1, base + 1, BANDS, 90);
    }
}
