package com.local.backscreengame;

/**
 * 点阵动画引擎。
 * 每帧是一个用 \n 分行的字符串，每行必须严格 17 字符（17x23 背屏）。
 * '#' = 亮点, '+' = 中灰点, '.' = 暗。
 */
public final class SpriteAnim {
    public final String[] frames;
    public final int fps;

    public SpriteAnim(String[] frames, int fps) {
        this.frames = frames;
        this.fps = fps;
    }

    private long startAt;

    public void restart() { startAt = System.currentTimeMillis(); }

    public int currentFrame() {
        if (frames.length == 0) return 0;
        if (startAt == 0) restart();
        int idx = (int) ((System.currentTimeMillis() - startAt) * fps / 1000);
        return Math.floorMod(idx, frames.length);
    }

    /** 启动自检：每帧必须是 23 行、每行 17 列。 */
    public static String validate() {
        for (int i = 0; i < frogLaugh().frames.length; i++) {
            String[] lines = frogLaugh().frames[i].split("\n");
            if (lines.length != DotCanvas.ROWS)
                return "帧" + i + " 行数 " + lines.length + "，应为 " + DotCanvas.ROWS;
            for (int r = 0; r < lines.length; r++)
                if (lines[r].length() != DotCanvas.COLS)
                    return "帧" + i + " 行" + r + " 宽 " + lines[r].length() + "，应为 " + DotCanvas.COLS;
        }
        return "OK";
    }

    /** 把当前帧画到点阵。 */
    public void render(DotCanvas c) {
        c.clear();
        if (frames.length == 0) return;
        String[] lines = frames[currentFrame()].split("\n");
        for (int r = 0; r < Math.min(lines.length, DotCanvas.ROWS); r++) {
            String line = lines[r];
            for (int col = 0; col < Math.min(line.length(), DotCanvas.COLS); col++) {
                char ch = line.charAt(col);
                if (ch == '#') c.set(col, r, 255);
                else if (ch == '+') c.set(col, r, 140);
            }
        }
    }

    /**
     * 奶蛙大笑：程序生成的三帧笑脸，行宽已校验为 17。
     * 帧0 闭眼微笑 / 帧1 张嘴大笑 / 帧2 眯眼大笑。
     */
    public static SpriteAnim frogLaugh() {
        String[] f = {
            // 帧0
            ".................\n" +
            ".................\n" +
            ".....+#####+.....\n" +
            "....####+####....\n" +
            "...##+.....+##...\n" +
            "..+#+.......+#+..\n" +
            "..##.........##..\n" +
            ".+#...........#+.\n" +
            ".##..#.....#..##.\n" +
            ".##..#.....#..##.\n" +
            ".#+...........+#.\n" +
            ".#+...........+#.\n" +
            ".#+...........+#.\n" +
            ".##...........##.\n" +
            ".##...........##.\n" +
            ".+#..+.....+..#+.\n" +
            "..##.##+.+##.##..\n" +
            "..+#+.+###+.+#+..\n" +
            "...##+.....+##...\n" +
            "....####++###....\n" +
            ".....+#####+.....\n" +
            ".................\n" +
            ".................",

            // 帧1
            ".................\n" +
            ".................\n" +
            ".....+#####+.....\n" +
            "....####+####....\n" +
            "...##+.....+##...\n" +
            "..+#+.......+#+..\n" +
            "..##.........##..\n" +
            ".+#..#.....#..#+.\n" +
            ".##..#.....#..##.\n" +
            ".##..#.....#..##.\n" +
            ".#+..#.....#..+#.\n" +
            ".#+...........+#.\n" +
            ".#+....+++....+#.\n" +
            ".##..+#####+..##.\n" +
            ".##.+#######+.##.\n" +
            ".+#.+#######+.#+.\n" +
            "..##.#######.##..\n" +
            "..+#+.+###+.+#+..\n" +
            "...##+.....+##...\n" +
            "....####++###....\n" +
            ".....+#####+.....\n" +
            ".................\n" +
            ".................",

            // 帧2
            ".................\n" +
            ".................\n" +
            ".....+#####+.....\n" +
            "....####+####....\n" +
            "...##+.....+##...\n" +
            "..+#+.......+#+..\n" +
            "..##.........##..\n" +
            ".+#...........#+.\n" +
            ".##.+++...+++.##.\n" +
            ".##.+++...+++.##.\n" +
            ".#+...........+#.\n" +
            ".#+...........+#.\n" +
            ".#+...........+#.\n" +
            ".##...+###+...##.\n" +
            ".##..#######..##.\n" +
            ".+#..#######..#+.\n" +
            "..##..#####+.##..\n" +
            "..+#+..+++..+#+..\n" +
            "...##+.....+##...\n" +
            "....####++###....\n" +
            ".....+#####+.....\n" +
            ".................\n" +
            "................."
        };
        return new SpriteAnim(f, 4);
    }
}
