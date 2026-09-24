package com.hoohooolom.app.data;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
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
 * The catalogue is a JSON array:
 *
 *   [ { "title": "کاربرگ جمع و تفریق", "chapter": 5, "note": "۲ صفحه", "url": "https://…/w1.pdf" } ]
 *
 * «chapter» is the index used everywhere else in the app (فصل ۱ is 0) and may be left out for a
 * sheet that belongs to no single chapter. Only «title» and «url» are required.
 *
 * Where it is read from is set in {@link #CATALOGUE_URL}. Until that is pointed somewhere, the
 * app falls back to the bundled assets/worksheets.json, and the download screen says plainly that
 * no sheets have been published yet rather than pretending to be broken.
 */
public final class WorksheetDownload {

    /**
     * The address of the catalogue. Empty for now — set it to the JSON file's URL once the
     * sheets are hosted, and nothing else in the app has to change.
     */
    public static final String CATALOGUE_URL = "";

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

    /** Every sheet in the bundled catalogue; an empty list when there is none yet. */
    public static List<WorksheetDownload> bundled(Context context) {
        return parse(readAsset(context, BUNDLED_CATALOGUE));
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
        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                JSONObject o = array.optJSONObject(i);
                if (o == null) continue;
                String title = o.optString("title", "").trim();
                String url = o.optString("url", "").trim();
                if (title.isEmpty() || url.isEmpty()) continue;
                out.add(new WorksheetDownload(title, o.optString("note", "").trim(), url,
                    o.has("chapter") ? o.optInt("chapter", -1) : -1));
            }
        } catch (Exception ignored) {
            // a malformed catalogue must not take the screen down; it just shows nothing
        }
        return out;
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
