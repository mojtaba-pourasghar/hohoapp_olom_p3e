package com.hoohooolom.app.tts;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.SoundPool;

import com.hoohooolom.app.R;
import com.hoohooolom.app.data.AppState;

/**
 * The app's small sounds — a tap under every button, a chime for a right answer, a sparkle for a
 * star — plus the quiet background loop. Both are switchable from the parent panel, and both stay
 * well under the narration so هوهو is never drowned out.
 */
public final class SoundManager {

    private static SoundManager instance;

    private final Context appContext;
    private final SoundPool pool;
    private final int tapId, correctId, wrongId, starId, pageId, winId;
    private MediaPlayer music;

    private SoundManager(Context context) {
        this.appContext = context.getApplicationContext();
        pool = new SoundPool.Builder()
            .setMaxStreams(4)
            .setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build())
            .build();
        tapId = pool.load(appContext, R.raw.sfx_tap, 1);
        correctId = pool.load(appContext, R.raw.sfx_correct, 1);
        wrongId = pool.load(appContext, R.raw.sfx_wrong, 1);
        starId = pool.load(appContext, R.raw.sfx_star, 1);
        pageId = pool.load(appContext, R.raw.sfx_page, 1);
        winId = pool.load(appContext, R.raw.sfx_win, 1);
    }

    public static synchronized void init(Context context) {
        if (instance == null) instance = new SoundManager(context);
    }

    public static SoundManager get() {
        return instance;
    }

    private boolean effectsOn() {
        try {
            return AppState.get().soundEffects();
        } catch (Exception e) {
            return false;
        }
    }

    private void play(int soundId, float volume) {
        if (!effectsOn() || soundId == 0) return;
        try {
            pool.play(soundId, volume, volume, 1, 0, 1f);
        } catch (Exception ignored) {
        }
    }

    public void tap() { play(tapId, 0.45f); }
    public void correct() { play(correctId, 0.75f); }
    public void wrong() { play(wrongId, 0.55f); }
    public void star() { play(starId, 0.6f); }
    public void page() { play(pageId, 0.4f); }
    public void win() { play(winId, 0.8f); }

    // ---------- background music ----------

    /** Starts the loop if the parent has it switched on; safe to call repeatedly. */
    public void startMusic() {
        boolean wanted;
        try {
            wanted = AppState.get().music();
        } catch (Exception e) {
            return;
        }
        if (!wanted) {
            stopMusic();
            return;
        }
        if (music != null && music.isPlaying()) return;
        try {
            music = MediaPlayer.create(appContext, R.raw.bgm_calm);
            if (music == null) return;
            music.setLooping(true);
            music.setVolume(0.22f, 0.22f); // deliberately under the voice
            music.start();
        } catch (Exception ignored) {
            music = null;
        }
    }

    public void stopMusic() {
        if (music == null) return;
        try {
            music.stop();
            music.release();
        } catch (Exception ignored) {
        }
        music = null;
    }

    public void pauseMusic() {
        try {
            if (music != null && music.isPlaying()) music.pause();
        } catch (Exception ignored) {
        }
    }

    public void resumeMusic() {
        try {
            if (music != null && !music.isPlaying()) music.start();
            else if (music == null) startMusic();
        } catch (Exception ignored) {
        }
    }

    /** Called after the parent flips a switch, so the change takes effect straight away. */
    public void applySettings() {
        startMusic();
    }
}
