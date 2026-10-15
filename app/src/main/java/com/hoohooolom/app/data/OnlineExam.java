package com.hoohooolom.app.data;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.hoohooolom.app.net.Net;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * The nationwide online exam — one paper, sat by every child on the same evening.
 *
 * Unlike the practice sets, these questions are not generated: a real exam has to be the same
 * paper for everyone, so it comes down from the host as a file the teacher writes. Nothing about
 * it is in the APK, which is the point — an exam can be announced for next month without anyone
 * installing anything.
 *
 *   { "active": true,
 *     "id": "aban-1405",
 *     "title": "آزمون آنلاین علوم سوم",
 *     "description": "…",
 *     "date": "۱۵ آبان ۱۴۰۵",  "time": "۱۶:۰۰",
 *     "startsAt": 1762436400000,     // epoch ms; 0 or missing = open as soon as it is active
 *     "registerFrom": 1761226800000, // epoch ms; when sign-up opens. 0 = already open
 *     "durationMinutes": 45,
 *     "questions": [ { "text": "…", "options": ["…"], "correctIndex": 2,
 *                      "explanation": "…", "points": 10 } ] }
 *
 * The file is cached on the device the moment it is read, so the exam screen opens instantly and
 * a child who loses signal in the middle of the paper still has every question in hand.
 */
public final class OnlineExam {

    public static final String URL = "http://mp-apdl.ir/grade-3/olom/exam.json";
    private static final String CACHED = "online-exam.json";

    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    public boolean active;
    public String id = "";
    public String title = "آزمون آنلاین علوم سوم";
    public String description = "";
    public String dateFa = "";
    public String timeFa = "";
    public long startsAt;
    public long registerFrom;
    public int durationMinutes = 45;
    public final List<Question> questions = new ArrayList<>();

    public static final class Question {
        public String text = "";
        public String explanation = "";
        public final List<String> options = new ArrayList<>();
        public int correctIndex;
        public int points = 1;
    }

    /** Whether there is an exam worth showing a child at all. */
    public boolean announced() {
        return active && !questions.isEmpty();
    }

    /** Sign-up is open — «نزدیک امتحانات», the weeks before the paper. */
    public boolean registrationOpen(long now) {
        if (!announced()) return false;
        if (registerFrom > 0 && now < registerFrom) return false;
        return startsAt <= 0 || now < endsAt();
    }

    /** The doors are open and the paper can be started. */
    public boolean canStart(long now) {
        if (!announced()) return false;
        if (startsAt <= 0) return true;
        return now >= startsAt && now < endsAt();
    }

    public boolean over(long now) {
        return announced() && startsAt > 0 && now >= endsAt();
    }

    public long endsAt() {
        return startsAt <= 0 ? Long.MAX_VALUE
            : startsAt + Math.max(1, durationMinutes) * 60_000L;
    }

    public int totalPoints() {
        int total = 0;
        for (Question q : questions) total += q.points;
        return total;
    }

    /** A key that changes when the teacher posts a different exam, so last term's answers stay put. */
    public String key() {
        return id.isEmpty() ? "exam_" + startsAt : "exam_" + id;
    }

    // ── reading it ───────────────────────────────────────────────────────────

    /**
     * Hands back the exam: the cached copy first if there is one, then the fresh one from the
     * host if it can be reached. `whenReady` may therefore run twice — once fast, once right —
     * and always on the main thread.
     */
    public static void load(Context context, Consumer<OnlineExam> whenReady) {
        Context app = context.getApplicationContext();
        String cached = readCache(app);
        if (cached != null) {
            OnlineExam fromDisk = parse(cached);
            if (fromDisk != null) whenReady.accept(fromDisk);
        }
        if (!Net.online(app)) {
            if (cached == null) whenReady.accept(new OnlineExam());
            return;
        }
        IO.execute(() -> {
            String fresh = Net.text(URL);
            OnlineExam parsed = fresh == null ? null : parse(fresh);
            if (parsed != null) writeCache(app, fresh);
            MAIN.post(() -> {
                if (parsed != null) whenReady.accept(parsed);
                else if (cached == null) whenReady.accept(new OnlineExam());
            });
        });
    }

    static OnlineExam parse(String json) {
        try {
            JSONObject o = new JSONObject(json);
            OnlineExam exam = new OnlineExam();
            exam.active = o.optBoolean("active", false);
            exam.id = o.optString("id", "");
            exam.title = o.optString("title", exam.title);
            exam.description = o.optString("description", "");
            exam.dateFa = o.optString("date", o.optString("shamsiDate", ""));
            exam.timeFa = o.optString("time", "");
            exam.startsAt = o.optLong("startsAt", 0L);
            exam.registerFrom = o.optLong("registerFrom", 0L);
            exam.durationMinutes = o.optInt("durationMinutes", 45);

            JSONArray arr = o.optJSONArray("questions");
            for (int i = 0; arr != null && i < arr.length(); i++) {
                JSONObject q = arr.optJSONObject(i);
                if (q == null) continue;
                Question question = new Question();
                question.text = q.optString("text", "");
                question.explanation = q.optString("explanation", "");
                question.correctIndex = q.optInt("correctIndex", 0);
                question.points = Math.max(1, q.optInt("points", 1));
                JSONArray options = q.optJSONArray("options");
                for (int j = 0; options != null && j < options.length(); j++) {
                    question.options.add(options.optString(j, ""));
                }
                // a question nobody can answer is worse than no question
                if (!question.text.isEmpty() && question.options.size() >= 2) {
                    exam.questions.add(question);
                }
            }
            return exam;
        } catch (Exception broken) {
            return null;     // a half-written file on the host must not empty the screen
        }
    }

    private static File cacheFile(Context context) {
        return new File(context.getFilesDir(), CACHED);
    }

    private static String readCache(Context context) {
        File file = cacheFile(context);
        if (!file.exists()) return null;
        try {
            return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        } catch (IOException unreadable) {
            return null;
        }
    }

    private static void writeCache(Context context, String json) {
        try (FileOutputStream out = new FileOutputStream(cacheFile(context))) {
            out.write(json.getBytes(StandardCharsets.UTF_8));
        } catch (IOException ignored) {
            // the exam still works this session; it just will not be there offline
        }
    }
}
