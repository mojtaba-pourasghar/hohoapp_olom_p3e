package com.hoohooolom.app.data;

import android.graphics.Color;

/** The three difficulty levels used for worksheets and exams. */
public enum Level {
    EASY(0, "۱", "سطح ۱ — آسان", "سؤال‌های پایه‌ی کتاب", "#3AA79A", "#E9F2E7", "#D5E5D1"),
    MEDIUM(1, "۲", "سطح ۲ — متوسط", "فکر کردن و مقایسه کردن", "#E8973A", "#FDF4E3", "#F0DFBE"),
    HARD(2, "۳", "سطح ۳ — سخت", "سؤال‌های ترکیبی و چالشی", "#D94F7A", "#FBEAF0", "#F3D3DF");

    public final int index;
    public final String numberFa;
    public final String title;
    public final String subtitle;
    public final int chipColor;
    public final int bgColor;
    public final int borderColor;

    Level(int index, String numberFa, String title, String subtitle, String chip, String bg, String border) {
        this.index = index;
        this.numberFa = numberFa;
        this.title = title;
        this.subtitle = subtitle;
        this.chipColor = Color.parseColor(chip);
        this.bgColor = Color.parseColor(bg);
        this.borderColor = Color.parseColor(border);
    }

    public static Level fromIndex(int i) {
        for (Level l : values()) if (l.index == i) return l;
        return EASY;
    }
}
