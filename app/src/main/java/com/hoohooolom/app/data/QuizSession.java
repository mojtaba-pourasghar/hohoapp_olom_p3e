package com.hoohooolom.app.data;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * An in-progress or completed practice/worksheet/exam attempt.
 *
 * The whole set is built up front and every answer is kept against its question index, so the
 * child can move back and forward freely, change an earlier answer, leave and come back.
 */
public class QuizSession {
    public final QuizMode mode;
    public final int chapter;
    public final int option; // section index for PRACTICE, level index for WORKSHEET/EXAM
    public final int round;  // which generated set this attempt used
    public final List<QuestionItem> items;

    public int index = 0;
    /** What the child has entered for each question; null means not answered yet. */
    private final String[] given;

    public QuizSession(QuizMode mode, int chapter, int option, int round, List<QuestionItem> items) {
        this.mode = mode;
        this.chapter = chapter;
        this.option = option;
        this.round = round;
        this.items = items;
        this.given = new String[items.size()];
    }

    public QuestionItem current() {
        return items.get(index);
    }

    public boolean isLast() {
        return index >= items.size() - 1;
    }

    public String answerAt(int i) {
        return i >= 0 && i < given.length ? given[i] : null;
    }

    public String currentAnswer() {
        return answerAt(index);
    }

    public void record(int i, String answer) {
        if (i >= 0 && i < given.length) given[i] = answer;
    }

    public boolean isCorrectAt(int i) {
        String a = answerAt(i);
        return a != null && a.equals(items.get(i).answerFa);
    }

    public int answeredCount() {
        int n = 0;
        for (String g : given) if (g != null) n++;
        return n;
    }

    public int rightCount() {
        int n = 0;
        for (int i = 0; i < given.length; i++) if (isCorrectAt(i)) n++;
        return n;
    }

    public boolean allAnswered() {
        return answeredCount() == given.length;
    }

    /** The first question still without an answer, or the last one when everything is done. */
    public int firstUnanswered() {
        for (int i = 0; i < given.length; i++) if (given[i] == null) return i;
        return given.length - 1;
    }

    /** Snapshot of the answers, for saving the attempt so it can be resumed. */
    public List<String> answers() {
        return new ArrayList<>(Arrays.asList(given));
    }

    public void restoreAnswers(List<String> saved) {
        if (saved == null) return;
        for (int i = 0; i < given.length && i < saved.size(); i++) {
            String a = saved.get(i);
            given[i] = (a == null || a.isEmpty()) ? null : a;
        }
    }

    /** The per-question record handed to the result screen and the parent panel. */
    public List<LogEntry> buildLog() {
        List<LogEntry> log = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            QuestionItem it = items.get(i);
            String mine = answerAt(i);
            log.add(new LogEntry(it.question, it.answerFa, mine == null ? "—" : mine, isCorrectAt(i)));
        }
        return log;
    }

    public static class LogEntry {
        public final String question;
        public final String correctAnswer;
        public final String yourAnswer;
        public final boolean correct;

        public LogEntry(String question, String correctAnswer, String yourAnswer, boolean correct) {
            this.question = question;
            this.correctAnswer = correctAnswer;
            this.yourAnswer = yourAnswer;
            this.correct = correct;
        }
    }
}
