package com.hoohooolom.app.data;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds the question sets from the hand-written bank. Nothing is locked: every chapter and
 * every section is open, and the teacher's position is only ever shown as a bookmark.
 *
 * Each round starts somewhere else in the pool and shuffles the options differently, so a child
 * who does the same set again gets the questions in a new order with the answers moved around.
 */
public final class QuizBuilder {

    private QuizBuilder() {}

    /** Each mode starts at a different place, so the exam never opens like the worksheet. */
    private static int seedOffset(QuizMode mode) {
        switch (mode) {
            case WORKSHEET: return 7;
            case EXAM: return 13;
            default: return 0;
        }
    }

    private static int mix(int a, int b) {
        int h = (a + 1) * 0x9E3779B1 ^ (b + 1) * 0x85EBCA77;
        h ^= h >>> 15;
        h *= 0x2545F491;
        h ^= h >>> 13;
        return h >>> 1;
    }

    public static QuizSession build(QuizMode mode, int ch, int option, int round) {
        List<QuizBank.Q> all = QuizBank.forChapter(ch);
        List<QuizBank.Q> pool = new ArrayList<>();
        int count;
        switch (mode) {
            case PRACTICE: {
                // the section's own questions, easiest first
                for (int lv = 0; lv < 3; lv++) {
                    for (QuizBank.Q q : all) if (q.section == option && q.level == lv) pool.add(q);
                }
                count = Math.min(15, pool.size());
                break;
            }
            case WORKSHEET: {
                // the chosen level first, then the neighbouring levels to make thirty
                for (QuizBank.Q q : all) if (q.level == option) pool.add(q);
                for (int d = 1; d <= 2; d++) {
                    for (QuizBank.Q q : all) {
                        if (Math.abs(q.level - option) == d) pool.add(q);
                    }
                }
                count = Math.min(30, pool.size());
                break;
            }
            default: {
                // the exam jumps between sections: one from each section in turn
                int sections = Book.chapter(ch).sections.size();
                List<List<QuizBank.Q>> bySection = new ArrayList<>();
                for (int s = 0; s < sections; s++) bySection.add(new ArrayList<>());
                for (QuizBank.Q q : all) {
                    if (Math.abs(q.level - option) <= 1 && q.section < sections) bySection.get(q.section).add(q);
                }
                boolean added = true;
                for (int k = 0; added; k++) {
                    added = false;
                    for (List<QuizBank.Q> list : bySection) {
                        if (k < list.size()) {
                            pool.add(list.get(k));
                            added = true;
                        }
                    }
                }
                count = Math.min(15, pool.size());
                break;
            }
        }

        List<QuestionItem> items = new ArrayList<>();
        if (pool.isEmpty()) return new QuizSession(mode, ch, option, round, items);
        // practice keeps its easy-to-hard order and only rotates by round; the others start
        // further along the pool each round
        int start = mode == QuizMode.PRACTICE ? 0 : (seedOffset(mode) + round * count) % pool.size();
        for (int i = 0; i < count; i++) {
            QuizBank.Q q = pool.get((start + i) % pool.size());
            List<String> options = new ArrayList<>(q.options);
            if (!q.isTrueFalse()) {
                // rotate the options so the right answer is not always in the same place
                int shift = mix(i + round * 31, seedOffset(mode) + ch) % options.size();
                List<String> rotated = new ArrayList<>();
                for (int k = 0; k < options.size(); k++) rotated.add(options.get((k + shift) % options.size()));
                options = rotated;
            }
            QuestionItem it = new QuestionItem(q.question, q.options.get(0), 0, q.why, ch, q.section);
            it.options = options;
            items.add(it);
        }
        return new QuizSession(mode, ch, option, round, items);
    }
}
