import com.hoohooolom.app.data.*;

import java.util.*;

/** Run by tools/check_lessons.sh: every page, stop, tap target, section and question set checked. */
public class CheckLessons {
    static int errors = 0;

    static void fail(String msg) {
        System.out.println("ERROR: " + msg);
        errors++;
    }

    static void rects(String where, int[] r) {
        if (r == null) return;
        if (r.length % 4 != 0) fail(where + ": rectangle numbers not in fours");
        for (int i = 0; i + 3 < r.length; i += 4) {
            if (r[i] < 0 || r[i + 1] < 0 || r[i + 2] > 1000 || r[i + 3] > 1000) fail(where + ": outside the page " + Arrays.toString(Arrays.copyOfRange(r, i, i + 4)));
            if (r[i] >= r[i + 2] || r[i + 1] >= r[i + 3]) fail(where + ": empty rectangle " + Arrays.toString(Arrays.copyOfRange(r, i, i + 4)));
        }
    }

    public static void main(String[] args) {
        Set<String> keys = new HashSet<>();
        int steps = 0, pages = 0;
        for (int ch = 0; ch < Book.CHAPTER_COUNT; ch++) {
            Book.Chapter c = Book.chapter(ch);
            for (int p = c.firstPage; p <= c.lastPage; p++) {
                LessonScript s = PageLessons.forPage(p);
                if (s == null) { fail("page " + p + " has no lesson"); continue; }
                pages++;
                if (s.chapter != ch) fail("page " + p + " says chapter " + s.chapter);
                if (s.bookPage != p) fail("page " + p + " says bookPage " + s.bookPage);
                if (s.section < 0 || s.section >= c.sections.size()) fail("page " + p + " has section " + s.section);
                if (s.steps.get(s.steps.size() - 1).kind != LessonKind.DONE) fail("page " + p + " does not end with done");
                for (LessonStep st : s.steps) {
                    steps++;
                    if (!keys.add(st.audioKey)) fail("duplicate key " + st.audioKey);
                    if (st.hasExample() && !keys.add(st.exampleAudioKey)) fail("duplicate key " + st.exampleAudioKey);
                    if (!st.audioKey.startsWith(String.format("p%03d_", p))) fail(st.audioKey + " is on page " + p);
                    if (st.hasStage()) {
                        if (st.stage.page != p) fail(st.audioKey + " shows page " + st.stage.page);
                        rects(st.audioKey + " stops", st.stage.stops);
                    }
                    if (st.kind == LessonKind.TAP) {
                        rects(st.audioKey + " targets", st.tapTargets);
                        if (!st.hasStage()) fail(st.audioKey + " TAP without a page");
                    }
                    if (st.kind == LessonKind.MCQ && (st.correctIndex < 0 || st.correctIndex >= st.options.size())) fail(st.audioKey + " bad answer index");
                    if (st.kind != LessonKind.TEACH && st.kind != LessonKind.DONE && (st.why == null || st.why.isEmpty())) fail(st.audioKey + " has no feedback");
                }
            }
            for (int sec = 0; sec < c.sections.size(); sec++) {
                if (!Lessons.has(ch, sec)) fail("chapter " + ch + " section " + sec + " has no pages");
                QuizSession practice = QuizBuilder.build(QuizMode.PRACTICE, ch, sec, 0);
                if (practice.items.size() < 4) fail("chapter " + ch + " section " + sec + ": only " + practice.items.size() + " practice questions");
            }
            List<QuizBank.Q> bank = QuizBank.forChapter(ch);
            for (QuizBank.Q q : bank) {
                if (q.section >= c.sections.size()) fail("quiz of chapter " + ch + " points at section " + q.section + ": " + q.question);
                if (new HashSet<>(q.options).size() != q.options.size()) fail("duplicate options: " + q.question);
            }
            int ws = QuizBuilder.build(QuizMode.WORKSHEET, ch, 1, 0).items.size();
            int ex = QuizBuilder.build(QuizMode.EXAM, ch, 1, 0).items.size();
            System.out.printf("درس %2d: %2d pages, bank %2d, worksheet %2d, exam %2d%n", ch + 1,
                PageLessons.pagesOfChapter(ch).size(), bank.size(), ws, ex);
        }
        System.out.println("pages " + pages + ", steps " + steps + ", narration keys " + keys.size());
        if (errors > 0) {
            System.out.println(errors + " problem(s)");
            System.exit(1);
        }
        System.out.println("lesson data OK");
    }
}
