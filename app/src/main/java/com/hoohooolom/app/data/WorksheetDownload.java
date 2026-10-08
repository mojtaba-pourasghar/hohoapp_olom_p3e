package com.hoohooolom.app.data;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.hoohooolom.app.net.Net;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * A printable worksheet the child (or the parent) can download.
 *
 * These are NOT the 30-question sets the app generates — those are answered on screen and need no
 * paper. These are ready-made sheets meant to be printed, and they come from a catalogue outside
 * the app so new sheets can be added without shipping a new APK.
 *
 * The catalogue is the index.json that tools/voice-studio writes beside the sheets:
 *
 *   { "baseUrl": "http://…/karbarg/",
 *     "sheets": [ { "title": "کاربرگ جمع و تفریق", "chapter": 5, "note": "۲ صفحه",
 *                   "file": "jam-tafrigh/sheet.pdf" } ] }
 *
 * An older flat array of objects carrying a whole «url» each still reads, so nothing breaks if
 * the file is written by hand.
 *
 * «chapter» is the index used everywhere else in the app (فصل ۱ is 0) and may be left out for a
 * sheet that belongs to no single chapter.
 *
 * The list is fetched from the host, then kept on the device: the screen opens instantly from
 * the cached copy and is refreshed in the background whenever there is a connection. A copy also
 * ships in assets, so the very first launch has something to show.
 */
public final class WorksheetDownload {

    /** Where the sheets and their index live. */
    public static final String BASE_URL = "http://mp-apdl.ir/grade-3/olom/karbarg/";
    public static final String CATALOGUE_URL = BASE_URL + "index.json";

    private static final String CACHED = "worksheets-cache.json";

    /** Shipped with the app so the screen has something to show before the URL is set. */
    private static final String BUNDLED_CATALOGUE = "worksheets.json";

    public final String title;
    public final String note;
    public final String url;
    public final int chapter; // -1 when the sheet is not tied to one chapter

    public WorksheetDownload(String title, String note, String url, int chapter) {
        this.title = title;
        this.note = note;
        this.url = url;
        this.chapter = chapter;
    }

    public static boolean hasRemoteCatalogue() {
        return CATALOGUE_URL != null && !CATALOGUE_URL.isEmpty();
    }

    /**
     * Every sheet known right now: the copy fetched from the host if there is one, otherwise the
     * copy that shipped with the app. Never touches the network, so it is safe on the main
     * thread — {@link #refresh} is what goes and looks.
     */
    public static List<WorksheetDownload> bundled(Context context) {
        List<WorksheetDownload> cached = parse(readCached(context));
        return cached.isEmpty() ? parse(readAsset(context, BUNDLED_CATALOGUE)) : cached;
    }

    /**
     * Fetches the list again off the main thread and keeps it. `whenDone` runs with true when
     * the list changed, so a screen can redraw itself.
     */
    public static void refresh(Context context, java.util.function.Consumer<Boolean> whenDone) {
        Context app = context.getApplicationContext();
        new Thread(() -> {
            boolean changed = false;
            if (Net.online(app)) {
                String json = Net.text(CATALOGUE_URL);
                if (json != null && !parse(json).isEmpty() && !json.equals(readCached(app))) {
                    writeCached(app, json);
                    changed = true;
                }
            }
            final boolean result = changed;
            if (whenDone != null) new Handler(Looper.getMainLooper()).post(() -> whenDone.accept(result));
        }, "worksheet-index").start();
    }

    /** The sheets that belong to one chapter, plus the ones that belong to no chapter. */
    public static List<WorksheetDownload> forChapter(Context context, int chapter) {
        List<WorksheetDownload> out = new ArrayList<>();
        for (WorksheetDownload sheet : bundled(context)) {
            if (sheet.chapter == chapter || sheet.chapter < 0) out.add(sheet);
        }
        return out;
    }

    static List<WorksheetDownload> parse(String json) {
        List<WorksheetDownload> out = new ArrayList<>();
        if (json == null || json.trim().isEmpty()) return out;
        String base = BASE_URL;
        try {
            JSONArray array;
            String trimmed = json.trim();
            if (trimmed.startsWith("{")) {                 // { baseUrl, sheets: [...] }
                JSONObject root = new JSONObject(trimmed);
                String declared = root.optString("baseUrl", "").trim();
                if (!declared.isEmpty()) base = declared.endsWith("/") ? declared : declared + "/";
                array = root.optJSONArray("sheets");
                if (array == null) return out;
            } else {
                array = new JSONArray(trimmed);
            }
            for (int i = 0; i < array.length(); i++) {
                JSONObject o = array.optJSONObject(i);
                if (o == null) continue;
                String title = o.optString("title", "").trim();
                String url = o.optString("url", "").trim();
                if (url.isEmpty()) {                       // the index.json form: base + file
                    String file = o.optString("file", "").trim();
                    if (!file.isEmpty()) url = base + file;
                }
                if (title.isEmpty() || url.isEmpty()) continue;
                out.add(new WorksheetDownload(title, o.optString("note", "").trim(), url,
                    o.has("chapter") ? o.optInt("chapter", -1) : -1));
            }
        } catch (Exception ignored) {
            // a malformed catalogue must not take the screen down; it just shows nothing
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
                new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
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

    private static String readAsset(Context context, String name) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(context.getAssets().open(name), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line).append('\n');
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }
}
