package com.hoohooolom.app.data;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** A completed practice/worksheet/exam attempt, kept so parents can review it later. */
public class QuizResult {
    public final long timestampMillis;
    public final QuizMode mode;
    public final int chapter;
    public final int option;
    public final int rightCount;
    public final int total;
    public final List<QuizSession.LogEntry> log;

    public QuizResult(long timestampMillis, QuizMode mode, int chapter, int option, int rightCount, int total, List<QuizSession.LogEntry> log) {
        this.timestampMillis = timestampMillis;
        this.mode = mode;
        this.chapter = chapter;
        this.option = option;
        this.rightCount = rightCount;
        this.total = total;
        this.log = log;
    }

    public static QuizResult fromSession(QuizSession s) {
        return new QuizResult(System.currentTimeMillis(), s.mode, s.chapter, s.option, s.rightCount(), s.items.size(), s.buildLog());
    }

    JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("t", timestampMillis);
        o.put("mode", mode.name());
        o.put("ch", chapter);
        o.put("opt", option);
        o.put("right", rightCount);
        o.put("total", total);
        JSONArray arr = new JSONArray();
        for (QuizSession.LogEntry e : log) {
            JSONObject je = new JSONObject();
            je.put("q", e.question);
            je.put("a", e.correctAnswer);
            je.put("mine", e.yourAnswer);
            je.put("ok", e.correct);
            arr.put(je);
        }
        o.put("log", arr);
        return o;
    }

    static QuizResult fromJson(JSONObject o) throws JSONException {
        List<QuizSession.LogEntry> log = new ArrayList<>();
        JSONArray arr = o.optJSONArray("log");
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                JSONObject je = arr.getJSONObject(i);
                log.add(new QuizSession.LogEntry(je.getString("q"), je.getString("a"), je.getString("mine"), je.getBoolean("ok")));
            }
        }
        return new QuizResult(o.getLong("t"), QuizMode.valueOf(o.getString("mode")), o.getInt("ch"), o.getInt("opt"), o.getInt("right"), o.getInt("total"), log);
    }
}
