package com.hoohooolom.app.tts;

import android.content.Context;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;

/**
 * Plays a narration line. Every line has a key; if a recorded voice file with that name sits in
 * res/raw it is played, otherwise the device's Persian text-to-speech reads the same words. That
 * way the lessons talk on any device today, and swapping in real recordings later is just a
 * matter of dropping files into res/raw (see res/raw/audio_manifest.txt for the full list).
 */
public final class LessonAudio {

    public interface PlaybackListener {
        /** Called once playback starts, with how long the line is expected to take. */
        void onStarted(long durationMs);
        void onFinished();
    }

    private static MediaPlayer player;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private LessonAudio() {}

    public static void play(Context context, String audioKey, String text, PlaybackListener listener) {
        stop();

        int resId = resolveRaw(context, audioKey);
        if (resId != 0) {
            try {
                player = MediaPlayer.create(context.getApplicationContext(), resId);
                if (player != null) {
                    long duration = Math.max(1200, player.getDuration());
                    player.setOnCompletionListener(mp -> {
                        release();
                        if (listener != null) listener.onFinished();
                    });
                    player.start();
                    if (listener != null) listener.onStarted(duration);
                    return;
                }
            } catch (Exception ignored) {
                release();
            }
        }

        // no recording yet: the device voice reads the manifest's spoken spelling of this line,
        // which is word for word what the recorded file will say once it is dropped into res/raw
        String said = NarrationText.forKey(context, audioKey, text);

        long estimate = estimateSpokenMs(said);
        TtsManager tts = TtsManager.get();
        if (listener != null) listener.onStarted(estimate);

        // The engine stays silent when the device has no Persian voice installed, and then it
        // never reports back — so a watchdog always releases the lesson after the expected time.
        final boolean[] done = {false};
        Runnable finishOnce = () -> {
            if (done[0]) return;
            done[0] = true;
            if (listener != null) listener.onFinished();
        };
        MAIN.postDelayed(finishOnce, estimate + 1200);

        if (tts == null) return;
        tts.speak(said, new TtsManager.Callback() {
            @Override public void onDone() { MAIN.post(finishOnce); }
            @Override public void onError() { MAIN.post(finishOnce); }
        });
    }

    /**
     * Plays a run of clips one after another — how a generated question is read in هوهو's own
     * voice. If even one clip is missing the whole line goes to the device voice instead, so the
     * child never hears half a sentence.
     */
    public static void playSequence(Context context, java.util.List<String> keys, String text,
                                    PlaybackListener listener) {
        stop();
        if (keys == null || keys.isEmpty()) {
            play(context, null, text, listener);
            return;
        }

        final int[] ids = new int[keys.size()];
        for (int i = 0; i < ids.length; i++) {
            ids[i] = resolveRaw(context, keys.get(i));
            if (ids[i] == 0) {          // not recorded yet — read the whole line with the device voice
                play(context, null, text, listener);
                return;
            }
        }

        if (listener != null) listener.onStarted(estimateSpokenMs(text));
        playFrom(context.getApplicationContext(), ids, 0, listener);
    }

    private static void playFrom(Context context, int[] ids, int at, PlaybackListener listener) {
        if (at >= ids.length) {
            release();
            if (listener != null) listener.onFinished();
            return;
        }
        try {
            release();
            player = MediaPlayer.create(context, ids[at]);
            if (player == null) {
                if (listener != null) listener.onFinished();
                return;
            }
            player.setOnCompletionListener(mp -> playFrom(context, ids, at + 1, listener));
            player.start();
        } catch (Exception e) {
            release();
            if (listener != null) listener.onFinished();
        }
    }

    public static void stop() {
        release();
        TtsManager tts = TtsManager.get();
        if (tts != null) tts.stop();
    }

    private static void release() {
        if (player != null) {
            try {
                player.release();
            } catch (Exception ignored) {
            }
            player = null;
        }
    }

    /** res/raw/<key>.(mp3|ogg|wav) — returns 0 when no recording has been added yet. */
    private static int resolveRaw(Context context, String audioKey) {
        if (audioKey == null || audioKey.isEmpty()) return 0;
        try {
            return context.getResources().getIdentifier(audioKey, "raw", context.getPackageName());
        } catch (Exception e) {
            return 0;
        }
    }

    /** Rough length of the spoken line, used to pace the stage animation against the voice. */
    public static long estimateSpokenMs(String text) {
        if (text == null || text.isEmpty()) return 1500;
        long ms = 700 + (long) (SpokenText.forSpeech(text).length() * 78);
        // long enough for the whole line: cutting the watchdog short used to clip the narration
        return Math.max(1800, Math.min(ms, 30000));
    }
}
