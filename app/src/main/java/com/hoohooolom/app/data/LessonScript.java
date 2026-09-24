package com.hoohooolom.app.data;

import java.util.List;

/** The full voice-guided lesson for one (chapter, section). */
public class LessonScript {
    public final int chapter;
    public final int section;
    /** Page this section starts on in the printed 1404 textbook, shown in the lesson header. */
    public final int bookPage;
    public final List<LessonStep> steps;

    public LessonScript(int chapter, int section, int bookPage, List<LessonStep> steps) {
        this.chapter = chapter;
        this.section = section;
        this.bookPage = bookPage;
        this.steps = steps;
    }
}
