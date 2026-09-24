package com.hoohooolom.app.data;

import java.util.ArrayList;
import java.util.List;

/**
 * The printed book taught page by page. Each page of the real book is its own lesson, sitting
 * next to the section reviews rather than in a separate place — the child works through the
 * book's own pages, in the book's own order.
 *
 * All fourteen lessons are here: صفحه‌های ۷ تا ۱۲۸ of the 1405 edition.
 */
public final class PageLessons {
    private PageLessons() {}

    /** The lesson for one printed page, or null when that page has none. */
    public static LessonScript forPage(int page) {
        for (int chapter = 0; chapter < Book.CHAPTER_COUNT; chapter++) {
            Book.Chapter ch = Book.chapter(chapter);
            if (page >= ch.firstPage && page <= ch.lastPage) return scriptOf(chapter, page);
        }
        return null;
    }

    private static LessonScript scriptOf(int chapter, int page) {
        switch (chapter) {
            case 0: return Chapter1Pages.forPage(page);
            case 1: return Chapter2Pages.forPage(page);
            case 2: return Chapter3Pages.forPage(page);
            case 3: return Chapter4Pages.forPage(page);
            case 4: return Chapter5Pages.forPage(page);
            case 5: return Chapter6Pages.forPage(page);
            case 6: return Chapter7Pages.forPage(page);
            case 7: return Chapter8Pages.forPage(page);
            case 8: return Chapter9Pages.forPage(page);
            case 9: return Chapter10Pages.forPage(page);
            case 10: return Chapter11Pages.forPage(page);
            case 11: return Chapter12Pages.forPage(page);
            case 12: return Chapter13Pages.forPage(page);
            case 13: return Chapter14Pages.forPage(page);
            default: return null;
        }
    }

    /** The book pages of one درس that have a lesson, in book order. */
    public static List<Integer> pagesOfChapter(int chapter) {
        switch (chapter) {
            case 0: return Chapter1Pages.pages();
            case 1: return Chapter2Pages.pages();
            case 2: return Chapter3Pages.pages();
            case 3: return Chapter4Pages.pages();
            case 4: return Chapter5Pages.pages();
            case 5: return Chapter6Pages.pages();
            case 6: return Chapter7Pages.pages();
            case 7: return Chapter8Pages.pages();
            case 8: return Chapter9Pages.pages();
            case 9: return Chapter10Pages.pages();
            case 10: return Chapter11Pages.pages();
            case 11: return Chapter12Pages.pages();
            case 12: return Chapter13Pages.pages();
            case 13: return Chapter14Pages.pages();
            default: return new ArrayList<>();
        }
    }

    /** The pages that belong to one section of a درس, in book order. */
    public static List<Integer> pagesOfSection(int chapter, int section) {
        List<Integer> out = new ArrayList<>();
        for (int page : pagesOfChapter(chapter)) {
            LessonScript script = forPage(page);
            if (script != null && script.section == section) out.add(page);
        }
        return out;
    }

    public static boolean hasPages(int chapter) {
        return !pagesOfChapter(chapter).isEmpty();
    }

    /** Every page of the book that has a lesson, درس after درس, in book order. */
    public static List<Integer> allPages() {
        List<Integer> out = new ArrayList<>();
        for (int chapter = 0; chapter < Book.CHAPTER_COUNT; chapter++) {
            out.addAll(pagesOfChapter(chapter));
        }
        return out;
    }

    /**
     * The page that comes after this one in the printed book, or 0 at the last page. It walks
     * across درس borders, so «برویم صفحه‌ی بعد کتاب» carries the child from the end of one درس
     * straight into the opening page of the next.
     */
    public static int nextPage(int page) {
        List<Integer> pages = allPages();
        int at = pages.indexOf(page);
        return at >= 0 && at < pages.size() - 1 ? pages.get(at + 1) : 0;
    }
}
