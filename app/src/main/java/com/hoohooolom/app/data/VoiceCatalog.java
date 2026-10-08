package com.hoohooolom.app.data;

import android.content.Context;

import com.hoohooolom.app.net.Net;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * What narration clips exist on the host, and what each one is called.
 *
 * The clips are not inside the APK any more — four thousand of them made it 143 MB. They sit on
 * the teacher's own host and the app fetches what it needs. This class is the list of what is
 * there: a key («p008_01») to a file name («p008_01.mp3»), because the recording tool may write
 * mp3, ogg or wav and the app should not have to guess.
 *
 * The list comes from <base>/index.json, written by tools/voice-studio when the clips are made.
 * It is cached in app storage after the first fetch, and a copy ships in assets so the very first
 * launch — before any network — still knows what to ask for.
 */
public final class VoiceCatalog {

    /** The folder the narration is uploaded to. */
    public static final String BASE_URL = "http://mp-apdl.ir/grade-3/olom/audio/";
    public static final String INDEX_URL = BASE_URL + "index.json";

    private static final String BUNDLED = "voice-index.json";
    private static final String CACHED = "voice-index.json";

    private static volatile Map<String, String> files = null;   // key → file name
    private static volatile boolean refreshing = false;

    private VoiceCatalog() {}

    /** Key → file name for everything published. Empty until something has been read. */
    public static Map<String, String> files(Context context) {
        Map<String, String> loaded = files;
        if (loaded != null) return loaded;
        synchronized (VoiceCatalog.class) {
            if (files == null) {
                Map<String, String> fromCache = parse(readCached(context));
                files = fromCache.isEmpty() ? parse(readAsset(context)) : fromCache;
            }
            return files;
        }
    }

    public static String fileName(Context context, String key) {
        if (key == null || key.isEmpty()) return null;
        return files(context).get(key);
    }

    public static String urlFor(Context context, String key) {
        String name = fileName(context, key);
        return name == null ? null : BASE_URL + name;
    }

    public static boolean isEmpty(Context context) {
        return files(context).isEmpty();
    }

    /** Every key the host has, in manifest order where that is known. */
    public static List<String> keys(Context context) {
        return new ArrayList<>(files(context).keySet());
    }

    /**
     * Fetches the list again in the background. Safe to call on every launch: it only replaces
     * what is in memory when the fetch actually worked.
     */
    public static void refresh(Context context, Runnable whenDone) {
        if (refreshing) return;
        refreshing = true;
        Context app = context.getApplicationContext();
        new Thread(() -> {
            String json = Net.text(INDEX_URL);
            Map<String, String> fetched = parse(json);
            if (!fetched.isEmpty()) {
                files = fetched;
                writeCached(app, json);
            }
            refreshing = false;
            if (whenDone != null) whenDone.run();
        }, "voice-index").start();
    }

    /**
     * Which chapter a key belongs to, 0-based like everywhere else in the app. Shared clips —
     * the number words and the question phrases, which every quiz needs — come back as -1 so
     * they can be fetched first and kept whatever chapter the child is on.
     */
    public static int chapterOf(String key) {
        if (key == null || key.isEmpty()) return -1;
        if (key.startsWith("n_") || key.startsWith("q_") || key.equals("va")) return -1;
        if (key.startsWith("ch")) {
            int underscore = key.indexOf('_');
            try {
                return Integer.parseInt(key.substring(2, underscore)) - 1;
            } catch (Exception e) {
                return -1;
            }
        }
        // t061_03 — a part of a lesson, named after the book page it teaches
        if (key.startsWith("t") && key.length() > 4) {
            try {
                int page = Integer.parseInt(key.substring(1, 4));
                for (Book.Chapter chapter : Book.CHAPTERS) {
                    if (page >= chapter.firstPage && page <= chapter.lastPage) return chapter.index;
                }
            } catch (Exception ignored) {
            }
        }
        return -1;
    }

    /** The keys of one chapter, plus (for chapter 0 and shared) the ones every screen needs. */
    public static List<String> keysOfChapter(Context context, int chapter) {
        List<String> out = new ArrayList<>();
        for (String key : files(context).keySet()) {
            if (chapterOf(key) == chapter) out.add(key);
        }
        Collections.sort(out);
        return out;
    }

    public static List<String> sharedKeys(Context context) {
        return keysOfChapter(context, -1);
    }

    private static Map<String, String> parse(String json) {
        Map<String, String> out = new HashMap<>();
        if (json == null || json.trim().isEmpty()) return out;
        try {
            JSONObject root = new JSONObject(json);
            JSONObject map = root.optJSONObject("files");
            if (map == null) return out;
            for (Iterator<String> it = map.keys(); it.hasNext(); ) {
                String key = it.next();
                String name = map.optString(key, "").trim();
                if (!name.isEmpty()) out.put(key, name);
            }
        } catch (Exception ignored) {
            // a broken index must not stop the app; it just means nothing is known yet
        }
        return out;
    }

    private static File cacheFile(Context context) {
        return new File(context.getFilesDir(), CACHED);
    }

    private static String readCached(Context context) {
        File file = cacheFile(context);
        if (!file.exists()) return "";
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new java.io.FileInputStream(file), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line).append('\n');
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private static void writeCached(Context context, String json) {
        try (FileOutputStream out = new FileOutputStream(cacheFile(context))) {
            out.write(json.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) {
        }
    }

    private static String readAsset(Context context) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(context.getAssets().open(BUNDLED), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line).append('\n');
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }
}
