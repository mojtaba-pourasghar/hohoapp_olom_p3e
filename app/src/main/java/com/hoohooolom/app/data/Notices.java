package com.hoohooolom.app.data;

import android.content.Context;
import android.content.SharedPreferences;
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

/**
 * خبرها — what the teacher wants every installed copy to know.
 *
 * A new chapter is up, a printable worksheet has been added, the online exam opens on Thursday,
 * the full version is half price this week. None of that is worth an APK, and none of it should
 * need a push service: the app already asks the host for a worksheet list and a voice catalogue
 * every time it starts, so it asks for this at the same time.
 *
 *   { "notices": [
 *       { "id": "aban-exam",            // must not change; it is how «already seen» is kept
 *         "title": "آزمون آنلاین آبان",
 *         "body": "پنج‌شنبه ساعت ۱۶، با بچه‌های سراسر کشور.",
 *         "screen": "ONLINE_EXAM",      // optional: where tapping it goes
 *         "from": 1761000000000,        // optional epoch ms; before this it is not shown
 *         "until": 1763000000000 } ] }  // optional epoch ms; after this it disappears by itself
 *
 * Nothing is sent anywhere. The app reads a file; it does not report who read it, and no account,
 * device id or token leaves the phone.
 */
public final class Notices {

    public static final String URL = "http://mp-apdl.ir/grade-3/olom/notices.json";

    private static final String CACHED = "notices-cache.json";
    private static final String PREFS = "notices";
    private static final String SEEN = "seen";          // shown in the app
    private static final String TOLD = "told";          // sent to the phone's notification tray

    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private Notices() {}

    public static final class Notice {
        public final String id;
        public final String title;
        public final String body;
        public final String screen;
        public final long from;
        public final long until;

        Notice(String id, String title, String body, String screen, long from, long until) {
            this.id = id;
            this.title = title;
            this.body = body;
            this.screen = screen;
            this.from = from;
            this.until = until;
        }

        boolean current(long now) {
            if (from > 0 && now < from) return false;
            return until <= 0 || now <= until;
        }
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** Whether the parent has left announcements on. On by default; one switch turns them off. */
    public static boolean allowed(Context context) {
        return AppState.get().getBoolean("notices_on", true);
    }

    public static void allow(Context context, boolean on) {
        AppState.get().setBoolean("notices_on", on);
    }

    // ── reading ──────────────────────────────────────────────────────────────

    /** Everything currently worth showing, newest first, from the copy on the device. */
    public static List<Notice> current(Context context) {
        List<Notice> out = new ArrayList<>();
        if (!allowed(context)) return out;
        long now = System.currentTimeMillis();
        for (Notice notice : parse(readCache(context))) {
            if (notice.current(now)) out.add(notice);
        }
        return out;
    }

    /** The ones the child has not waved away yet. */
    public static List<Notice> unseen(Context context) {
        List<Notice> out = new ArrayList<>();
        for (Notice notice : current(context)) {
            if (!prefs(context).getBoolean(SEEN + "_" + notice.id, false)) out.add(notice);
        }
        return out;
    }

    public static void markSeen(Context context, String id) {
        prefs(context).edit().putBoolean(SEEN + "_" + id, true).apply();
    }

    /** True the first time only — so the same news never reaches the phone twice. */
    public static boolean claimForTray(Context context, String id) {
        if (prefs(context).getBoolean(TOLD + "_" + id, false)) return false;
        prefs(context).edit().putBoolean(TOLD + "_" + id, true).apply();
        return true;
    }

    /** Fetches the list in the background and calls back on the main thread when it has changed. */
    public static void refresh(Context context, Runnable whenChanged) {
        Context app = context.getApplicationContext();
        if (!Net.online(app)) return;
        IO.execute(() -> {
            String fresh = Net.text(URL);
            if (fresh == null) return;
            if (parse(fresh).isEmpty() && !fresh.contains("notices")) return;   // not our file
            String before = readCache(app);
            if (fresh.equals(before)) return;
            writeCache(app, fresh);
            if (whenChanged != null) MAIN.post(whenChanged);
        });
    }

    static List<Notice> parse(String json) {
        List<Notice> out = new ArrayList<>();
        if (json == null || json.isEmpty()) return out;
        try {
            JSONObject root = new JSONObject(json);
            JSONArray arr = root.optJSONArray("notices");
            if (arr == null) arr = root.optJSONArray("items");
            for (int i = 0; arr != null && i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) continue;
                String id = o.optString("id", "");
                String title = o.optString("title", "");
                if (id.isEmpty() || title.isEmpty()) continue;   // nothing to show, nothing to keep
                out.add(new Notice(id, title,
                    o.optString("body", o.optString("content", "")),
                    o.optString("screen", ""),
                    o.optLong("from", 0L),
                    o.optLong("until", 0L)));
            }
        } catch (Exception broken) {
            // a half-uploaded file on the host is not worth a crash on a child's phone
        }
        return out;
    }

    private static File cacheFile(Context context) {
        return new File(context.getFilesDir(), CACHED);
    }

    private static String readCache(Context context) {
        File file = cacheFile(context.getApplicationContext());
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
            // it will simply be fetched again next time
        }
    }
}
