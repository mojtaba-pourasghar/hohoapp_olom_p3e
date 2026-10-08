package com.hoohooolom.app.tts;

import android.content.Context;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;

import com.hoohooolom.app.data.VoiceCatalog;

import java.io.File;
import java.util.List;

/**
 * Plays a narration line. Every line has a key, and the clip for it is looked for in three
 * places, in this order:
 *
 *   1. the clips downloaded from the host and kept on the device ({@link VoiceStore});
 *   2. res/raw, which is where the short effects live and where a clip can still be dropped by
 *      hand during development;
 *   3. nowhere — and then the device's own Persian text-to-speech reads the same words.
 *
 * So the lesson always talks. When a clip is missing but the host has it, the download is
 * started in the background while the device voice covers this one line, and the next time the
 * child reaches it هوهو's own voice is there.
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

        File downloaded = VoiceStore.clip(context, audioKey);
        if (downloaded != null && startFile(context, downloaded, listener)) return;

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

        // the host has this line but the device has not got it yet: read it aloud now and fetch
        // the clip quietly, so the second time round it is هوهو's own voice
        if (audioKey != null && VoiceCatalog.fileName(context, audioKey) != null) {
            VoiceStore.fetchNow(context, audioKey, null);
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
    public static void playSequence(Context context, List<String> keys, String text,
                                    PlaybackListener listener) {
        stop();
        if (keys == null || keys.isEmpty()) {
            play(context, null, text, listener);
            return;
        }

        // a question is read from word clips; they may be downloaded files, res/raw entries, or
        // missing — and if even one is missing the whole line goes to the device voice, so the
        // child never hears half a sentence in one voice and half in another
        final List<Object> pieces = new java.util.ArrayList<>();
        boolean complete = true;
        for (String key : keys) {
            File file = VoiceStore.clip(context, key);
            if (file != null) {
                pieces.add(file);
                continue;
            }
            int id = resolveRaw(context, key);
            if (id != 0) {
                pieces.add(id);
                continue;
            }
            complete = false;
            if (VoiceCatalog.fileName(context, key) != null) VoiceStore.fetchNow(context, key, null);
        }
        if (!complete) {
            play(context, null, text, listener);
            return;
        }

        if (listener != null) listener.onStarted(estimateSpokenMs(text));
        playFrom(context.getApplicationContext(), pieces, 0, listener);
    }

    /** Plays one downloaded clip. Returns false when the file would not open. */
    private static boolean startFile(Context context, File file, PlaybackListener listener) {
        try {
            player = new MediaPlayer();
            player.setDataSource(file.getAbsolutePath());
            player.prepare();
            long duration = Math.max(1200, player.getDuration());
            player.setOnCompletionListener(mp -> {
                release();
                if (listener != null) listener.onFinished();
            });
            player.start();
            if (listener != null) listener.onStarted(duration);
            return true;
        } catch (Exception e) {
            release();
            return false;
        }
    }

    private static void playFrom(Context context, List<Object> pieces, int at, PlaybackListener listener) {
        if (at >= pieces.size()) {
            release();
            if (listener != null) listener.onFinished();
            return;
        }
        try {
            release();
            Object piece = pieces.get(at);
            if (piece instanceof File) {
                player = new MediaPlayer();
                player.setDataSource(((File) piece).getAbsolutePath());
                player.prepare();
            } else {
                player = MediaPlayer.create(context, (Integer) piece);
            }
            if (player == null) {
                if (listener != null) listener.onFinished();
                return;
            }
            player.setOnCompletionListener(mp -> playFrom(context, pieces, at + 1, listener));
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
