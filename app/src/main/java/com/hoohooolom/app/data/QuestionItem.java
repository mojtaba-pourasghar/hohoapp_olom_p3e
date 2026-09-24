package com.hoohooolom.app.data;

import java.util.List;

/** A single generated practice/worksheet/exam question. */
public class QuestionItem {
    public final String question;
    public final String answerFa;   // Persian-digit answer text, e.g. "۱۲"
    public final long rawAnswer;
    public final String hint;
    public final int chapter;
    public final int section;

    /** Only populated for exam (multiple-choice) mode. */
    public List<String> options;

    public QuestionItem(String question, String answerFa, long rawAnswer, String hint, int chapter, int section) {
        this.question = question;
        this.answerFa = answerFa;
        this.rawAnswer = rawAnswer;
        this.hint = hint;
        this.chapter = chapter;
        this.section = section;
    }
}
