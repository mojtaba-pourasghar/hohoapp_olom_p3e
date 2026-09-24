package com.hoohooolom.app.data;

import android.content.Context;
import android.content.SharedPreferences;

import com.hoohooolom.app.util.Hash;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * All persisted + session state for the app, kept on-device only (SharedPreferences).
 * Mirrors the design prototype's single-component state, split out because a native
 * app has many screens instead of one re-rendering component.
 */
public final class AppState {
    private static final String PREFS = "hoohoo_math_v1";
    private static final int MAX_HISTORY = 60;

    private static AppState instance;

    private final SharedPreferences prefs;

    // --- gamification ---
    public int stars;
    public int streak;
    public String hat;
    public String learnerName;

    // --- teacher pace: how far the teacher has actually taught ---
    public int taughtChapter;
    public int taughtSection;

    // --- settings toggles, all of which actually do something ---
    public static final int SETTING_READ_ALOUD = 0;    // هوهو reads questions out loud
    public static final int SETTING_SOUND_EFFECTS = 1; // taps, praise, stars
    public static final int SETTING_MUSIC = 2;         // the quiet background loop
    public static final int SETTING_TIPS = 3;          // هوهو's speech bubble
    public static final int SETTING_FLY = 4;           // هوهو flies over to what she explains
    private static final int SETTINGS_VERSION = 4;

    // the tooltip is off by default: the lesson already prints what هوهو says, and the bubble
    // only gets in the way unless a parent asks for it
    public boolean[] settings = {true, true, false, false, true};

    public boolean readAloud() { return settings[SETTING_READ_ALOUD]; }
    public boolean soundEffects() { return settings[SETTING_SOUND_EFFECTS]; }
    public boolean music() { return settings[SETTING_MUSIC]; }
    public boolean tipsEnabled() { return settings[SETTING_TIPS]; }
    public boolean mascotFlies() { return settings[SETTING_FLY]; }

    // --- parent gate ---
    private String pinHash; // null until parent sets one up
    public int recoveryQuestionIndex;
    private String recoveryAnswerHash;
    public boolean parentUnlockedThisSession = false; // not persisted: re-enter PIN each app launch

    // --- history, for the parent panel ---
    private final List<QuizResult> history = new ArrayList<>();

    /** Sections whose lesson the child has finished, as "chapter:section" keys. */
    private final Set<String> completedSections = new HashSet<>();

    /** Book pages whose lesson the child has finished, as page numbers. */
    private final Set<String> completedPages = new HashSet<>();

    public static final String[] RECOVERY_QUESTIONS = {
        "نام مدرسه‌ی فرزندم چیست؟",
        "نام معلم کلاس سوم فرزندم چیست؟",
        "نام حیوان خانگی یا عروسک محبوب فرزندم چیست؟"
    };

    private AppState(Context appContext) {
        prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        load();
        bumpStreakOnOpen();
    }

    public static synchronized void init(Context appContext) {
        if (instance == null) instance = new AppState(appContext.getApplicationContext());
    }

    public static AppState get() {
        if (instance == null) throw new IllegalStateException("AppState.init() was not called");
        return instance;
    }

    public boolean hasPin() {
        return pinHash != null;
    }

    public void setPin(String fourDigitPin) {
        this.pinHash = Hash.sha256(fourDigitPin);
        persist();
    }

    public boolean checkPin(String candidate) {
        return pinHash != null && pinHash.equals(Hash.sha256(candidate));
    }

    public void setRecovery(int questionIndex, String answer) {
        this.recoveryQuestionIndex = questionIndex;
        this.recoveryAnswerHash = Hash.sha256(Hash.normalize(answer));
        persist();
    }

    public boolean checkRecovery(String candidateAnswer) {
        return recoveryAnswerHash != null && recoveryAnswerHash.equals(Hash.sha256(Hash.normalize(candidateAnswer)));
    }

    public boolean hasRecovery() {
        return recoveryAnswerHash != null;
    }

    public void setTeacherPace(int chapter, int section) {
        this.taughtChapter = chapter;
        this.taughtSection = section;
        persist();
    }

    public void addStars(int n) {
        stars += n;
        persist();
    }

    public void toggleSetting(int i) {
        settings[i] = !settings[i];
        persist();
    }

    public void setHat(String hat) {
        this.hat = hat;
        persist();
    }

    /**
     * Where the child has dragged هوهو to, as a fraction of the screen. -1 means "never moved",
     * so the mascot stays in its default corner.
     */
    public float mascotX = -1f;
    public float mascotY = -1f;

    public void setMascotPosition(float xFraction, float yFraction) {
        this.mascotX = xFraction;
        this.mascotY = yFraction;
        prefs.edit().putFloat("mascotX", xFraction).putFloat("mascotY", yFraction).apply();
    }

    public List<QuizResult> getHistory() {
        return Collections.unmodifiableList(history);
    }

    public void recordResult(QuizSession session) {
        history.add(0, QuizResult.fromSession(session));
        while (history.size() > MAX_HISTORY) history.remove(history.size() - 1);
        persist();
    }

    private static String sectionKey(int chapter, int section) {
        return chapter + ":" + section;
    }

    /** The child finished this section's voice lesson, so the app can offer the next one. */
    public void markSectionLessonDone(int chapter, int section) {
        completedSections.add(sectionKey(chapter, section));
        persist();
    }

    public boolean isSectionLessonDone(int chapter, int section) {
        return completedSections.contains(sectionKey(chapter, section));
    }

    /**
     * How many rounds of this exact practice set / worksheet / exam the child has already done.
     * Each round shifts the question generator's seed, so the same section or level can be
     * practised endlessly without ever repeating the same fifteen or thirty questions.
     */
    public int getRound(String key) {
        return prefs.getInt("round_" + key, 0);
    }

    public void bumpRound(String key) {
        prefs.edit().putInt("round_" + key, getRound(key) + 1).apply();
    }

    /**
     * An unfinished worksheet / exam / practice set: which question the child was on and every
     * answer given so far. Saved on every answer and on leaving, so closing the app in the middle
     * of a thirty-question worksheet never means starting over.
     */
    public void saveAttempt(String key, int index, List<String> answers) {
        JSONObject o = new JSONObject();
        try {
            o.put("i", index);
            JSONArray arr = new JSONArray();
            for (String a : answers) arr.put(a == null ? "" : a);
            o.put("a", arr);
        } catch (JSONException e) {
            return;
        }
        prefs.edit().putString("attempt_" + key, o.toString()).apply();
    }

    public int attemptIndex(String key) {
        JSONObject o = attempt(key);
        return o == null ? -1 : o.optInt("i", 0);
    }

    /** The saved answers, or null when there is no unfinished attempt for this key. */
    public List<String> attemptAnswers(String key) {
        JSONObject o = attempt(key);
        if (o == null) return null;
        JSONArray arr = o.optJSONArray("a");
        if (arr == null) return null;
        List<String> out = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) out.add(arr.optString(i, ""));
        return out;
    }

    public boolean hasAttempt(String key) {
        return attempt(key) != null;
    }

    public void clearAttempt(String key) {
        prefs.edit().remove("attempt_" + key).apply();
    }

    private JSONObject attempt(String key) {
        String raw = prefs.getString("attempt_" + key, null);
        if (raw == null) return null;
        try {
            return new JSONObject(raw);
        } catch (JSONException e) {
            return null;
        }
    }

    /** Which step of a book page's lesson the child had reached, so it resumes there. */
    public void savePageStep(int page, int step) {
        prefs.edit().putInt("pageStep_" + page, step).apply();
    }

    public int pageStep(int page) {
        return prefs.getInt("pageStep_" + page, 0);
    }

    /** The book pages whose lesson the child has finished, so the list can tick them off. */
    public void markPageDone(int page) {
        completedPages.add(String.valueOf(page));
        persist();
    }

    public boolean isPageDone(int page) {
        return completedPages.contains(String.valueOf(page));
    }

    /**
     * Which chapter the map is showing. This is only where the child is *looking* — the
     * bookmark of how far the class has got stays in taughtChapter, and nothing is locked.
     */
    public void saveMapChapter(int chapter) {
        prefs.edit().putInt("mapChapter", chapter).apply();
    }

    public int mapChapter() {
        int c = prefs.getInt("mapChapter", -1);
        return c < 0 || c >= Book.CHAPTERS.size() ? taughtChapter : c;
    }

    /** Which step of a section's voice lesson the child had reached, so it resumes there. */
    public void saveLessonStep(int chapter, int section, int step) {
        prefs.edit().putInt("lesson_" + sectionKey(chapter, section), step).apply();
    }

    public int lessonStep(int chapter, int section) {
        return prefs.getInt("lesson_" + sectionKey(chapter, section), 0);
    }

    private void bumpStreakOnOpen() {
        LocalDate today = LocalDate.now(ZoneId.systemDefault());
        long todayEpoch = today.toEpochDay();
        long last = prefs.getLong("lastActiveEpochDay", -1);
        if (last == todayEpoch) {
            // already counted today
        } else if (last == todayEpoch - 1) {
            streak += 1;
        } else if (last >= 0) {
            streak = 1;
        } else {
            streak = Math.max(streak, 1);
        }
        prefs.edit().putLong("lastActiveEpochDay", todayEpoch).apply();
        persist();
    }

    private void persist() {
        SharedPreferences.Editor e = prefs.edit();
        e.putInt("stars", stars);
        e.putInt("streak", streak);
        e.putString("hat", hat == null ? "دانش‌آموزی" : hat);
        e.putString("learnerName", learnerName == null ? "" : learnerName);
        e.putInt("taughtChapter", taughtChapter);
        e.putInt("taughtSection", taughtSection);
        for (int i = 0; i < settings.length; i++) e.putBoolean("setting" + i, settings[i]);
        e.putString("pinHash", pinHash);
        e.putInt("recIdx", recoveryQuestionIndex);
        e.putString("recAnswerHash", recoveryAnswerHash);
        e.putString("history", historyToJson());
        e.putStringSet("completedSections", new HashSet<>(completedSections));
        e.putStringSet("completedPages", new HashSet<>(completedPages));
        e.apply();
    }

    private void load() {
        stars = prefs.getInt("stars", 0);
        streak = prefs.getInt("streak", 0);
        hat = prefs.getString("hat", "دانش‌آموزی");
        learnerName = prefs.getString("learnerName", "");
        taughtChapter = prefs.getInt("taughtChapter", 0);
        taughtSection = prefs.getInt("taughtSection", 0);
        // the toggles changed meaning in version 2, so old values are dropped rather than carried over
        if (prefs.getInt("settingsVersion", 1) == SETTINGS_VERSION) {
            for (int i = 0; i < settings.length; i++) settings[i] = prefs.getBoolean("setting" + i, settings[i]);
        } else {
            prefs.edit().putInt("settingsVersion", SETTINGS_VERSION).apply();
        }
        pinHash = prefs.getString("pinHash", null);
        recoveryQuestionIndex = prefs.getInt("recIdx", 0);
        recoveryAnswerHash = prefs.getString("recAnswerHash", null);
        historyFromJson(prefs.getString("history", null));
        completedSections.clear();
        Set<String> saved = prefs.getStringSet("completedSections", null);
        if (saved != null) completedSections.addAll(saved);
        completedPages.clear();
        Set<String> savedPages = prefs.getStringSet("completedPages", null);
        if (savedPages != null) completedPages.addAll(savedPages);
        mascotX = prefs.getFloat("mascotX", -1f);
        mascotY = prefs.getFloat("mascotY", -1f);
    }

    private String historyToJson() {
        JSONArray arr = new JSONArray();
        try {
            for (QuizResult r : history) arr.put(r.toJson());
        } catch (JSONException ignored) {}
        return arr.toString();
    }

    private void historyFromJson(String s) {
        history.clear();
        if (s == null) return;
        try {
            JSONArray arr = new JSONArray(s);
            for (int i = 0; i < arr.length(); i++) {
                history.add(QuizResult.fromJson(arr.getJSONObject(i)));
            }
        } catch (JSONException ignored) {}
    }
}
