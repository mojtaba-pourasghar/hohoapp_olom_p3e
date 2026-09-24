package com.hoohooolom.app.tts;

import android.content.Context;
import android.util.Log;

import com.hoohooolom.app.R;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * The words هوهو actually says, read out of res/raw/audio_manifest.txt.
 *
 * Every narration line in the lessons has a key, and the manifest holds three columns for it:
 * the key, the text as it is written on screen, and the same sentence spelled out the way it is
 * to be spoken — numbers as words, symbols as words, and the handful of words the device engine
 * mispronounces written with their vowels (کَسر، مَخرَج، مِحوَر …).
 *
 * Until the recordings exist, the device voice reads that third column rather than the written
 * text. So what the child hears now is exactly what the recorded files will say later, and the
 * lessons can be checked before a single clip is made.
 */
public final class NarrationText {

    private static volatile Map<String, String> spoken;   // key → how it is said
    private static volatile Map<String, String> keyOfText; // written line → its key
    private static volatile boolean loading;

    private NarrationText() {}

    /** Reads the manifest off the main thread; harmless to call more than once. */
    public static void preload(Context context) {
        if (spoken != null || loading) return;
        loading = true;
        Context app = context.getApplicationContext();
        new Thread(() -> {
            load(app);
            loading = false;
        }, "narration-manifest").start();
    }

    /**
     * The spoken spelling of a line, or the written text when the manifest has nothing for it
     * (a key that was added to a lesson but not yet to the manifest, or the file still loading).
     */
    public static String forKey(Context context, String key, String written) {
        if (key == null || key.isEmpty()) return written;
        Map<String, String> map = spoken;
        if (map == null) {
            preload(context);
            return written;
        }
        String said = map.get(key);
        return said == null || said.isEmpty() ? written : said;
    }

    /**
     * The spoken spelling of a line found by the words themselves, for the places that speak
     * without knowing a key — هوهو's bubble, the praise after an answer. Returns the text
     * unchanged when the manifest has no such line.
     */
    public static String spokenForText(String written) {
        String key = keyForText(written);
        Map<String, String> map = spoken;
        if (key == null || map == null) return written;
        String said = map.get(key);
        return said == null || said.isEmpty() ? written : said;
    }

    /** The narration key of a written line, or null when it is not one of the lesson lines. */
    public static String keyForText(String written) {
        Map<String, String> index = keyOfText;
        if (index == null || written == null) return null;
        return index.get(written.trim());
    }

    private static void load(Context context) {
        Map<String, String> map = new HashMap<>(4096);
        Map<String, String> byText = new HashMap<>(4096);
        try (InputStream in = context.getResources().openRawResource(R.raw.audio_manifest);
             BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.charAt(0) == '#') continue;
                String[] cols = line.split("\\|");
                if (cols.length < 2) continue;
                String key = cols[0].trim();
                // column 3 is the spoken spelling; fall back to column 2 where it is missing
                String say = (cols.length >= 3 ? cols[2] : cols[1]).trim();
                if (key.isEmpty() || say.isEmpty()) continue;
                map.put(key, say);
                String written = cols[1].trim();
                if (!written.isEmpty()) byText.put(written, key);
            }
        } catch (Exception e) {
            Log.w("NarrationText", "could not read audio_manifest.txt", e);
        }
        keyOfText = byText;
        spoken = map;          // published last: spoken != null means both maps are ready
    }
}
