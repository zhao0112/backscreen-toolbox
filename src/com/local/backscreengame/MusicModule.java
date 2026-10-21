package com.local.backscreengame;

/**
 * 音乐伴随：均衡器柱状动效 + 歌名显示。
 *
 * 动效数据来自外部传入的频段强度（与频谱模块共用同一套采集），
 * 所以没有歌名时也能动起来。
 */
public final class MusicModule {

    /**
     * 最近一次的能量值（0–1），给陪伴模式判断"副歌到了"用。
     * 不用 AudioSession 的音量（那是振幅不是感知响度），
     * 而是频谱总能量的归一化值。
     */
    public float energy = 0f;

    /** 能量峰值追踪，用于检测突变（副歌 = 能量突然升高）。 */
    public float peak = 0f;
    public String title = "";
    public String artist = "";
    public boolean playing;

    private long startAt;

    public void restart() { startAt = System.currentTimeMillis(); }

    /** 由外部填入歌名。 */
    public void setTrack(String title, String artist) {
        this.title = title == null ? "" : title;
        this.artist = artist == null ? "" : artist;
        this.playing = true;
        restart();
    }

    public void clearTrack() {
        title = "";
        artist = "";
        playing = false;
    }

    /**
     * 渲染：文字区 row1-10，均衡器 row13-22，互不重叠。
     * 17 列一屏只有 4 个 3x5 字符，所以歌名/歌手都用滚动。
     */
    public void render(DotCanvas c, int[] bands) {
        c.clear();
        if (startAt == 0) restart();

        // 顶部播放标记：三个跳动小块，只占 row0 一行，避免压到下面的歌名
        int anim = (int) ((System.currentTimeMillis() / 140) % 4);
        for (int i = 0; i < 3; i++) {
            int h = ((i + anim) % 3) + 1;
            c.set(1 + i * 2, 0, playing ? 255 : 80);
            if (h >= 2) c.set(1 + i * 2, 1, playing ? 160 : 50);
        }

        // 歌名 row1-5
        if (!title.isEmpty()) {
            int off = (int) ((System.currentTimeMillis() / 400) % 1000);
            c.scrollText(3, sanitize(title), 255, off);
        } else {
            c.textFit(3, "MUSIC", 150);
        }

        // 歌手 row6-10
        if (!artist.isEmpty()) {
            int off2 = (int) ((System.currentTimeMillis() / 500) % 1000);
            c.scrollText(8, sanitize(artist), 110, off2);
        }

        // 分隔线 row11
        c.hline(0, 13, DotCanvas.COLS, 55);

        // 均衡器：row12-22，最多 11 行高
        int base = DotCanvas.ROWS - 1;     // 22
        int maxH = 10;
        int n = bands == null ? 0 : Math.min(bands.length, DotCanvas.COLS);
        for (int i = 0; i < n; i++) {
            int h = (bands[i] * maxH) / 255;
            if (h > maxH) h = maxH;
            if (h < 1) h = 1;
            for (int y = 0; y < h; y++) {
                int row = base - y;
                if (row < 14) break;
                int bright = 90 + (y * 150) / maxH;
                c.set(i + 1, row, bright);
            }
            if (base - h >= 14) c.set(i + 1, base - h, 255);
        }
    }

    /** 只保留字体表里有的字符。 */
    private static String sanitize(String s) {
        StringBuilder sb = new StringBuilder();
        String up = s.toUpperCase();
        for (int i = 0; i < up.length() && sb.length() < 40; i++) {
            char ch = up.charAt(i);
            if ((ch >= 'A' && ch <= 'Z') || (ch >= '0' && ch <= '9')) sb.append(ch);
            else sb.append(' ');
        }
        return sb.toString();
    }
}
