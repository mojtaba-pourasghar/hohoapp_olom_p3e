package com.hoohooolom.app.data;

import java.util.Arrays;
import java.util.List;

/**
 * The 3rd-grade science textbook (علوم تجربی سوم دبستان، ۱۴۰۵): 14 lessons (درس). Each درس is
 * split into the sections its pages naturally fall into — a different number for each درس,
 * so always ask `chapter.sections.size()` rather than assuming a count.
 *
 * In the code a درس is still called a "chapter", the name the rest of the app grew up with.
 */
public final class Book {

    public static final class Chapter {
        public final int index;
        public final String numberFa;
        public final String title;
        /** Where this درس starts and ends in the printed book. */
        public final int firstPage;
        public final int lastPage;
        public final List<String> sections;

        Chapter(int index, String numberFa, String title, int firstPage, int lastPage, String... sections) {
            this.index = index;
            this.numberFa = numberFa;
            this.title = title;
            this.firstPage = firstPage;
            this.lastPage = lastPage;
            this.sections = Arrays.asList(sections);
        }
    }

    public static final List<Chapter> CHAPTERS = Arrays.asList(
        new Chapter(0, "۱", "زنگ علوم", 7, 12,
            "مشاهده و یادداشت", "جدول و نمودار", "آزمایش و نتیجه‌گیری"),
        new Chapter(1, "۲", "خوراکی‌ها", 13, 22,
            "بشقاب سلامت", "فایده‌ی گروه‌های غذایی", "خوراکی‌های بسته‌بندی‌شده", "انتخاب خوراکی سالم"),
        new Chapter(2, "۳", "اندازه‌گیری مواد", 23, 32,
            "مقایسه‌ی سنگینی", "ترازو و جرم", "حجم"),
        new Chapter(3, "۴", "موادّ اطراف ما", 33, 44,
            "جامد، مایع و گاز", "هوا و گازها", "تغییر حالت مواد"),
        new Chapter(4, "۵", "آب، ماده‌ی با ارزش", 45, 52,
            "چرخه‌ی آب", "آب شور و شیرین", "تبخیر"),
        new Chapter(5, "۶", "زندگی ما و آب", 53, 60,
            "آب باران و خاک", "رود و آب زیرزمینی", "تصفیه‌ی آب"),
        new Chapter(6, "۷", "نور و مشاهده‌ی اجسام", 61, 70,
            "منبع نور", "بازتاب نور و دیدن", "آینه‌ها"),
        new Chapter(7, "۸", "جست‌وجو کنیم و بسازیم", 71, 74,
            "ساختن آینه"),
        new Chapter(8, "۹", "نیرو، همه جا (۱)", 75, 84,
            "کشیدن و هل دادن", "اثرات نیرو"),
        new Chapter(9, "۱۰", "نیرو، همه جا (۲)", 85, 92,
            "آهن‌ربا و کشش زمین", "اهرم"),
        new Chapter(10, "۱۱", "بکارید و ببینید", 93, 102,
            "ریشه‌ها", "برگ‌ها", "دانه‌ها و کشف رابطه"),
        new Chapter(11, "۱۲", "هر کدام جای خود (۱)", 103, 110,
            "مهره‌دار و بی‌مهره", "جای زندگی و تنفّس", "دسته‌بندی جانوران"),
        new Chapter(12, "۱۳", "هر کدام جای خود (۲)", 111, 124,
            "ماهی‌ها", "دوزیستان", "خزندگان و پرندگان", "پستانداران"),
        new Chapter(13, "۱۴", "از گذشته تا آینده", 125, 128,
            "نگه‌داری موادّ غذایی")
    );

    private Book() {}

    public static Chapter chapter(int index) {
        return CHAPTERS.get(Math.max(0, Math.min(CHAPTERS.size() - 1, index)));
    }

    public static final int CHAPTER_COUNT = 14;
    /** The most sections any درس has; screens that lay sections out size themselves from it. */
    public static final int MAX_SECTIONS = 4;
}
