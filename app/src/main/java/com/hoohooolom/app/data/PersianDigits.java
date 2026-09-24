package com.hoohooolom.app.data;

/** Converts between Latin and Persian (Farsi) digit characters, matching the textbook's numeral style. */
public final class PersianDigits {
    private static final char[] FA = {'۰', '۱', '۲', '۳', '۴', '۵', '۶', '۷', '۸', '۹'};

    private PersianDigits() {}

    public static String fa(long n) {
        StringBuilder out = new StringBuilder();
        String s = Long.toString(n);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= '0' && c <= '9') out.append(FA[c - '0']);
            else out.append(c);
        }
        return out.toString();
    }

    public static String fa(int n) {
        return fa((long) n);
    }

    /** Converts a string containing Persian digits back to Latin digits. */
    public static String en(String s) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            int idx = indexOf(c);
            if (idx >= 0) out.append((char) ('0' + idx));
            else out.append(c);
        }
        return out.toString();
    }

    private static int indexOf(char c) {
        for (int i = 0; i < FA.length; i++) if (FA[i] == c) return i;
        return -1;
    }
}
