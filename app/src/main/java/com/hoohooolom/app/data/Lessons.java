package com.hoohooolom.app.data;

import static com.hoohooolom.app.data.PersianDigits.fa;

import java.util.ArrayList;
import java.util.List;

/**
 * «خلاصه‌ی بخش» — the review lesson of one section, built from that section's own book pages:
 * هوهو names what the section covered, then asks again every question those pages asked (on the
 * same pictures), and closes. Built from the pages rather than written separately, so a review
 * can never drift out of step with the pages it reviews.
 */
public final class Lessons {
    private Lessons() {}

    /** The review lesson for this section, or null when the section has no pages. */
    public static LessonScript forSection(int chapter, int section) {
        List<Integer> pages = PageLessons.pagesOfSection(chapter, section);
        if (pages.isEmpty()) return null;
        Book.Chapter ch = Book.chapter(chapter);
        String name = ch.sections.get(section);
        String key = String.format(java.util.Locale.US, "ch%02d_s%d_", chapter + 1, section + 1);

        List<LessonStep> steps = new ArrayList<>();
        LessonScript first = PageLessons.forPage(pages.get(0));
        StageSpec cover = first != null && !first.steps.isEmpty() ? first.steps.get(0).stage : StageSpec.NONE;
        steps.add(LessonStep.teach(key + "intro",
            "مرورِ بخشِ «" + name + "» از درسِ " + ch.numberFa + ". این بخش صفحه‌ی " + fa(pages.get(0))
                + (pages.size() > 1 ? " تا " + fa(pages.get(pages.size() - 1)) : "")
                + " کتاب است. بیا دوباره سؤال‌هایش را با هم جواب بدهیم تا خوب یادت بماند.",
            "مرورِ بخش: " + name, cover));

        for (int page : pages) {
            LessonScript script = PageLessons.forPage(page);
            if (script == null) continue;
            for (LessonStep step : script.steps) {
                if (step.kind == LessonKind.MCQ || step.kind == LessonKind.NUM || step.kind == LessonKind.TAP) {
                    steps.add(step);
                }
            }
        }
        steps.add(LessonStep.done(key + "done",
            "آفرین! مرورِ بخشِ «" + name + "» تمام شد. حالا تمرین‌های همین بخش را حل کن.",
            "مرور تمام شد"));
        return new LessonScript(chapter, section, pages.get(0), steps);
    }

    public static boolean has(int chapter, int section) {
        return !PageLessons.pagesOfSection(chapter, section).isEmpty();
    }
}
