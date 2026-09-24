package com.hoohooolom.app.tts;

import android.content.Context;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.speech.tts.Voice;

import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Wraps Android’s built-in text-to-speech engine for هوهو’s narration: Persian locale,
 * a female voice when the device offers one, and a slower delivery so a third-grader can
 * follow. Symbols in the written questions are turned into Persian words first (SpokenText).
 */
public class TtsManager {
    private static TtsManager instance;

    private final TextToSpeech tts;
    private boolean ready = false;
    private final AtomicInteger idCounter = new AtomicInteger();

    public interface Callback {
        default void onStart() {}
        default void onDone() {}
        default void onError() {}
    }

    private TtsManager(Context appContext) {
        tts = new TextToSpeech(appContext, status -> {
            if (status == TextToSpeech.SUCCESS) {
                ready = true;
                configureVoice();
            }
        });
        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override public void onStart(String utteranceId) { dispatch(utteranceId, Event.START); }
            @Override public void onDone(String utteranceId) { dispatch(utteranceId, Event.DONE); }
            @Override public void onError(String utteranceId) { dispatch(utteranceId, Event.ERROR); }
        });
    }

    public static synchronized void init(Context appContext) {
        if (instance == null) instance = new TtsManager(appContext.getApplicationContext());
    }

    public static TtsManager get() {
        return instance;
    }

    private boolean persianAvailable = false;

    /** False when the device has no Persian voice installed — the app then has nothing to speak with. */
    public boolean isPersianAvailable() {
        return persianAvailable;
    }

    public boolean isReady() {
        return ready;
    }

    private void configureVoice() {
        Locale fa = new Locale("fa", "IR");
        int result = tts.setLanguage(fa);
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            result = tts.setLanguage(new Locale("fa"));
        }
        persianAvailable = result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED;

        // هوهو is voiced by a woman, so prefer a female Persian voice when the device has one
        Voice best = null;
        try {
            Set<Voice> voices = tts.getVoices();
            if (voices != null) {
                for (Voice v : voices) {
                    if (v.getLocale() == null) continue;
                    if (!"fa".equals(v.getLocale().getLanguage())) continue;
                    String name = v.getName() == null ? "" : v.getName().toLowerCase(Locale.US);
                    if (name.contains("female") || name.contains("#female") || name.endsWith("-f")) {
                        best = v;
                        break;
                    }
                    if (best == null) best = v; // fall back to any Persian voice
                }
            }
        } catch (Exception ignored) {}
        if (best != null) tts.setVoice(best);

        tts.setPitch(1.04f);      // bright and friendly rather than deep
        tts.setSpeechRate(0.88f); // slower than normal, so a third-grader can follow every word
    }

    private final java.util.Map<String, Callback> callbacks = new java.util.concurrent.ConcurrentHashMap<>();

    private enum Event { START, DONE, ERROR }

    private void dispatch(String id, Event e) {
        Callback cb = callbacks.get(id);
        if (cb == null) return;
        switch (e) {
            case START: cb.onStart(); break;
            case DONE: callbacks.remove(id); cb.onDone(); break;
            case ERROR: callbacks.remove(id); cb.onError(); break;
        }
    }

    /** Speaks a line, replacing anything currently queued. */
    public void speak(String text) {
        speak(text, null);
    }

    public void speak(String text, Callback callback) {
        if (!ready || text == null || text.isEmpty()) return;
        // a lesson line is read with the spelling the manifest gives it (numbers and symbols as
        // words, و اعراب on the words the engine gets wrong); anything else is read as written
        String spoken = SpokenText.forSpeech(NarrationText.spokenForText(text));
        if (spoken.isEmpty()) return;
        String id = "u" + idCounter.incrementAndGet();
        if (callback != null) callbacks.put(id, callback);
        tts.speak(spoken, TextToSpeech.QUEUE_FLUSH, null, id);
    }

    public void stop() {
        if (ready) tts.stop();
    }

    public boolean isSpeaking() {
        return ready && tts.isSpeaking();
    }

    public void shutdown() {
        if (tts != null) tts.shutdown();
    }
}
