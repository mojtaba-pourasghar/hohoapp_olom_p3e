package com.hoohooolom.app.data;

/**
 * What is free, and what needs the full version.
 *
 * The rules live here, on their own, with no Android in sight: one place to read, one place to
 * change, and a thing that can be checked without a phone. Whether it has been bought is a
 * different question and lives in billing/Entitlement — this class only answers «is
 * this piece inside the free part?».
 *
 * The free part is a real taste of the app, not a locked shop window:
 *
 *   • درس ۱، بخش ۱ و ۲ — the book pages and the section summary, all of it
 *   • the practice sets of those two sections
 *   • one worksheet and one exam (the easy level of فصل ۱)
 *   • two printable sheets, chosen by the teacher
 *   • every narration clip, since they live on the host and locking them buys nothing
 *
 * Nothing is hidden: a locked piece is still listed, with a 🔒, and tapping it explains why.
 */
public final class Access {

    /** The chapter whose beginning is free — فصل ۱. */
    public static final int FREE_CHAPTER = 0;

    /** How many of its sections are free: بخش ۱ و ۲. */
    public static final int FREE_SECTIONS = 2;

    /** The one free worksheet and the one free exam: the easy level of the free chapter. */
    public static final int FREE_LEVEL = 0;

    /** How many printable sheets are free when the catalogue does not say which. */
    public static final int FREE_SHEETS = 2;

    private Access() {}

    /** A section of the book: free only at the start of the free chapter. */
    public static boolean section(int chapter, int section) {
        return chapter == FREE_CHAPTER && section >= 0 && section < FREE_SECTIONS;
    }

    /**
     * A page of the book, by its page number.
     *
     * A page belongs to whichever section covers it, so the free pages are exactly the pages of
     * the free sections — worked out from the section the page sits in, never hard-coded.
     */
    public static boolean page(int page) {
        LessonScript script = PageLessons.forPage(page);
        return script != null && section(script.chapter, script.section);
    }

    /**
     * A set of questions.
     *
     * For تمرین the option is the section; for کاربرگ and آزمون it is the level. One worksheet
     * and one exam are free — the easy level of the free chapter — which is what «چند تا» came
     * down to: enough to see what they are, not enough to replace the book.
     */
    public static boolean quiz(QuizMode mode, int chapter, int option) {
        if (mode == QuizMode.PRACTICE) return section(chapter, option);
        return chapter == FREE_CHAPTER && option == FREE_LEVEL;
    }

    /**
     * What this class needs to know about a printable sheet, and nothing else.
     *
     * Kept to an interface so these rules stay plain Java: they compile and are checked without
     * an Android SDK, the same way the lesson classes are.
     */
    public interface Sheet {
        /** Tells one sheet from another — the file's address. */
        String id();

        /** Whether the catalogue marked this one free. */
        boolean marked();
    }

    /**
     * A printable sheet.
     *
     * The catalogue decides: a sheet with «"free": true» is free. When no sheet is marked — an
     * older catalogue, or one written by hand — the first two of the list stand in, so the free
     * offer never silently becomes nothing.
     */
    public static boolean sheet(java.util.List<? extends Sheet> all, Sheet sheet) {
        for (Sheet one : all) {
            if (one.marked()) return sheet.marked();
        }
        int seen = 0;
        for (Sheet one : all) {
            if (one.id().equals(sheet.id())) return seen < FREE_SHEETS;
            seen++;
        }
        return false;
    }

    /** «فصل ۱ (بخش ۱ و ۲)» and the like — what the paywall says is already yours. */
    public static String freeSummary() {
        Book.Chapter first = Book.chapter(FREE_CHAPTER);
        return "درس " + first.numberFa + "، بخش ۱ و ۲ · یک کاربرگ · یک آزمون · دو کاربرگ چاپی";
    }
}
