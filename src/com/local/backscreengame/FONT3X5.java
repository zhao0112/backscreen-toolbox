package com.local.backscreengame;

/** 3x5 像素字体 */
public final class FONT3X5 {
    private FONT3X5() {}

    public static String[] lookup(char ch) {
        switch (ch) {
            case '0': return new String[] { "111", "101", "101", "101", "111" };
            case '1': return new String[] { "010", "110", "010", "010", "111" };
            case '2': return new String[] { "111", "001", "111", "100", "111" };
            case '3': return new String[] { "111", "001", "111", "001", "111" };
            case '4': return new String[] { "101", "101", "111", "001", "001" };
            case '5': return new String[] { "111", "100", "111", "001", "111" };
            case '6': return new String[] { "111", "100", "111", "101", "111" };
            case '7': return new String[] { "111", "001", "001", "001", "001" };
            case '8': return new String[] { "111", "101", "111", "101", "111" };
            case '9': return new String[] { "111", "101", "111", "001", "111" };
            case 'A': return new String[] { "111", "101", "111", "101", "101" };
            case 'B': return new String[] { "110", "101", "110", "101", "110" };
            case 'C': return new String[] { "111", "100", "100", "100", "111" };
            case 'D': return new String[] { "110", "101", "101", "101", "110" };
            case 'E': return new String[] { "111", "100", "111", "100", "111" };
            case 'F': return new String[] { "111", "100", "111", "100", "100" };
            case 'G': return new String[] { "111", "100", "101", "101", "111" };
            case 'H': return new String[] { "101", "101", "111", "101", "101" };
            case 'I': return new String[] { "111", "010", "010", "010", "111" };
            case 'J': return new String[] { "001", "001", "001", "101", "111" };
            case 'K': return new String[] { "101", "101", "110", "101", "101" };
            case 'L': return new String[] { "100", "100", "100", "100", "111" };
            case 'M': return new String[] { "101", "111", "111", "101", "101" };
            case 'N': return new String[] { "101", "111", "111", "111", "101" };
            case 'O': return new String[] { "111", "101", "101", "101", "111" };
            case 'P': return new String[] { "111", "101", "111", "100", "100" };
            case 'Q': return new String[] { "111", "101", "101", "111", "001" };
            case 'R': return new String[] { "111", "101", "110", "101", "101" };
            case 'S': return new String[] { "111", "100", "111", "001", "111" };
            case 'T': return new String[] { "111", "010", "010", "010", "010" };
            case 'U': return new String[] { "101", "101", "101", "101", "111" };
            case 'V': return new String[] { "101", "101", "101", "101", "010" };
            case 'W': return new String[] { "101", "101", "111", "111", "101" };
            case 'X': return new String[] { "101", "101", "010", "101", "101" };
            case 'Y': return new String[] { "101", "101", "010", "010", "010" };
            case 'Z': return new String[] { "111", "001", "010", "100", "111" };
            case ' ': return new String[] { "000", "000", "000", "000", "000" };
            case ':': return new String[] { "000", "010", "000", "010", "000" };
            case '.': return new String[] { "000", "000", "000", "000", "010" };
            case '-': return new String[] { "000", "000", "111", "000", "000" };
            case '/': return new String[] { "001", "001", "010", "100", "100" };
            case '!': return new String[] { "010", "010", "010", "000", "010" };
            case '?': return new String[] { "111", "001", "010", "000", "010" };
            case '+': return new String[] { "000", "010", "111", "010", "000" };
            case '%': return new String[] { "101", "001", "010", "100", "101" };
            case '#': return new String[] { "101", "111", "101", "111", "101" };
            case '(': return new String[] { "001", "010", "010", "010", "001" };
            case ')': return new String[] { "100", "010", "010", "010", "100" };
            case '*': return new String[] { "000", "101", "010", "101", "000" };
            case ',': return new String[] { "000", "000", "000", "010", "100" };
            case 39: return new String[] { "010", "010", "000", "000", "000" };
            case '=': return new String[] { "000", "111", "000", "111", "000" };
            case '<': return new String[] { "001", "010", "100", "010", "001" };
            case '>': return new String[] { "100", "010", "001", "010", "100" };
            default: return null;
        }
    }
}
