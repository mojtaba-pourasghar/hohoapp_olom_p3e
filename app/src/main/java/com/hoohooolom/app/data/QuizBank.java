package com.hoohooolom.app.data;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * The science questions for practice, worksheets and exams. Unlike math, science questions
 * cannot be generated from a formula, so each one is written by hand from the book, in
 * data/Chapter<N>Quiz.java, and tagged with its section and level.
 *
 * Two shapes: a multiple-choice question (the right answer first, then the distractors — the
 * order on screen is shuffled per round), and a «درست یا نادرست؟» statement.
 */
public final class QuizBank {
    private QuizBank() {}

    public static final String TRUE = "درست";
    public static final String FALSE = "نادرست";

    public static final class Q {
        public final int chapter;
        public final int section;
        public final int level;
        public final String question;
        /** options.get(0) is always the right one. */
        public final List<String> options;
        public final String why;

        Q(int chapter, int section, int level, String question, List<String> options, String why) {
            this.chapter = chapter;
            this.section = section;
            this.level = level;
            this.question = question;
            this.options = options;
            this.why = why;
        }

        public boolean isTrueFalse() {
            return options.size() == 2 && (options.contains(TRUE) && options.contains(FALSE));
        }
    }

    /** Collects one chapter's questions; section and level are 0-based. */
    public static final class Builder {
        private final int chapter;
        private final List<Q> out = new ArrayList<>();

        public Builder(int chapter) {
            this.chapter = chapter;
        }

        /** Multiple choice: `right` first, then two or three wrong answers. */
        public Builder mcq(int section, int level, String question, String why, String right, String... wrong) {
            List<String> opts = new ArrayList<>();
            opts.add(right);
            opts.addAll(Arrays.asList(wrong));
            out.add(new Q(chapter, section, level, question, opts, why));
            return this;
        }

        /** «درست یا نادرست؟» — `isTrue` says which. */
        public Builder tf(int section, int level, String statement, boolean isTrue, String why) {
            List<String> opts = Arrays.asList(isTrue ? TRUE : FALSE, isTrue ? FALSE : TRUE);
            out.add(new Q(chapter, section, level, "درست یا نادرست؟ " + statement, opts, why));
            return this;
        }

        public List<Q> build() {
            return Collections.unmodifiableList(out);
        }
    }

    private static final java.util.Map<Integer, List<Q>> CACHE = new java.util.HashMap<>();

    /**
     * Every question of one درس: the hand-written bank, plus every multiple-choice question the
     * book pages themselves ask (as easy questions — the child has met them in the lesson).
     */
    public static synchronized List<Q> forChapter(int chapter) {
        List<Q> cached = CACHE.get(chapter);
        if (cached != null) return cached;
        List<Q> all = new ArrayList<>(written(chapter));
        for (int page : PageLessons.pagesOfChapter(chapter)) {
            LessonScript script = PageLessons.forPage(page);
            if (script == null) continue;
            for (LessonStep step : script.steps) {
                if (step.kind != LessonKind.MCQ || step.options == null) continue;
                List<String> opts = new ArrayList<>();
                opts.add(step.options.get(step.correctIndex));
                for (int k = 0; k < step.options.size(); k++) if (k != step.correctIndex) opts.add(step.options.get(k));
                all.add(new Q(chapter, script.section, 0, step.say, opts, step.why));
            }
        }
        List<Q> out = Collections.unmodifiableList(all);
        CACHE.put(chapter, out);
        return out;
    }

    private static List<Q> written(int chapter) {
        switch (chapter) {
            case 0: return Chapter1Quiz.ALL;
            case 1: return Chapter2Quiz.ALL;
            case 2: return Chapter3Quiz.ALL;
            case 3: return Chapter4Quiz.ALL;
            case 4: return Chapter5Quiz.ALL;
            case 5: return Chapter6Quiz.ALL;
            case 6: return Chapter7Quiz.ALL;
            case 7: return Chapter8Quiz.ALL;
            case 8: return Chapter9Quiz.ALL;
            case 9: return Chapter10Quiz.ALL;
            case 10: return Chapter11Quiz.ALL;
            case 11: return Chapter12Quiz.ALL;
            case 12: return Chapter13Quiz.ALL;
            case 13: return Chapter14Quiz.ALL;
            default: return Collections.emptyList();
        }
    }
}
