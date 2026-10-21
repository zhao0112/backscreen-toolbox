package com.local.backscreengame;

/** 4x6 紧凑大字体（时间显示用，4列步进5列） */
public final class FONT4X6 {
    private FONT4X6() {}

    public static String[] lookup(char ch) {
        switch (ch) {
            case 48: return new String[] { "1111", "1001", "1001", "1001", "1001", "1111" };
            case 49: return new String[] { "0010", "0110", "0010", "0010", "0010", "0111" };
            case 50: return new String[] { "1111", "0001", "1111", "1000", "1000", "1111" };
            case 51: return new String[] { "1111", "0001", "0111", "0001", "0001", "1111" };
            case 52: return new String[] { "1001", "1001", "1111", "0001", "0001", "0001" };
            case 53: return new String[] { "1111", "1000", "1111", "0001", "0001", "1111" };
            case 54: return new String[] { "1111", "1000", "1111", "1001", "1001", "1111" };
            case 55: return new String[] { "1111", "0001", "0010", "0100", "0100", "0100" };
            case 56: return new String[] { "1111", "1001", "1111", "1001", "1001", "1111" };
            case 57: return new String[] { "1111", "1001", "1111", "0001", "0001", "1111" };
            case 32: return new String[] { "0000", "0000", "0000", "0000", "0000", "0000" };
            default: return null;
        }
    }
}
