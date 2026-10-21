package com.local.backscreengame;

/** 3x6 像素字体。17 列背屏最多放 4 个字符（4*4-1=15 列）。
 *  用于时间 MM:SS 之外的紧凑显示；3x5 不够高，3x6 刚好。 */
public final class FONT3X6 {
    private FONT3X6() {}

    public static String[] lookup(char ch) {
        switch (ch) {
            case 48: return new String[] { "111", "101", "101", "101", "101", "111" };
            case 49: return new String[] { "010", "110", "010", "010", "010", "111" };
            case 50: return new String[] { "111", "001", "111", "100", "100", "111" };
            case 51: return new String[] { "111", "001", "111", "001", "001", "111" };
            case 52: return new String[] { "101", "101", "111", "001", "001", "001" };
            case 53: return new String[] { "111", "100", "111", "001", "001", "111" };
            case 54: return new String[] { "111", "100", "111", "101", "101", "111" };
            case 55: return new String[] { "111", "001", "001", "001", "001", "001" };
            case 56: return new String[] { "111", "101", "111", "101", "101", "111" };
            case 57: return new String[] { "111", "101", "111", "001", "001", "111" };
            case 58: return new String[] { "000", "010", "000", "000", "010", "000" };
            case 32: return new String[] { "000", "000", "000", "000", "000", "000" };
            case 80: return new String[] { "110", "101", "110", "100", "100", "100" };
            case 77: return new String[] { "101", "111", "111", "101", "101", "101" };
            case 65: return new String[] { "010", "101", "111", "101", "101", "101" };
            case 68: return new String[] { "110", "101", "101", "101", "101", "110" };
            case 78: return new String[] { "101", "111", "111", "111", "101", "101" };
            case 71: return new String[] { "111", "100", "101", "101", "101", "111" };
            case 79: return new String[] { "111", "101", "101", "101", "101", "111" };
            case 85: return new String[] { "101", "101", "101", "101", "101", "111" };
            case 84: return new String[] { "111", "010", "010", "010", "010", "010" };
            case 73: return new String[] { "111", "010", "010", "010", "010", "111" };
            case 83: return new String[] { "111", "100", "111", "001", "001", "111" };
            case 67: return new String[] { "111", "100", "100", "100", "100", "111" };
            case 76: return new String[] { "100", "100", "100", "100", "100", "111" };
            case 66: return new String[] { "110", "101", "110", "101", "101", "110" };
            case 69: return new String[] { "111", "100", "111", "100", "100", "111" };
            case 72: return new String[] { "101", "101", "111", "101", "101", "101" };
            case 82: return new String[] { "110", "101", "110", "101", "101", "101" };
            case 70: return new String[] { "111", "100", "111", "100", "100", "100" };
            case 75: return new String[] { "101", "101", "110", "101", "101", "101" };
            case 86: return new String[] { "101", "101", "101", "101", "101", "010" };
            case 87: return new String[] { "101", "101", "111", "111", "101", "101" };
            case 88: return new String[] { "101", "101", "010", "101", "101", "101" };
            case 89: return new String[] { "101", "101", "010", "010", "010", "010" };
            case 90: return new String[] { "111", "001", "010", "100", "100", "111" };
            case 74: return new String[] { "001", "001", "001", "001", "101", "111" };
            case 81: return new String[] { "111", "101", "101", "111", "001", "001" };
            default: return null;
        }
    }
}
