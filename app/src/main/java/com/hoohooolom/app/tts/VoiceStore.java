package com.hoohooolom.app.tts;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.hoohooolom.app.data.Book;
import com.hoohooolom.app.data.VoiceCatalog;
import com.hoohooolom.app.net.Net;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * هوهو's voice, kept on the device.
 *
 * The clips live on the teacher's host; this is the part that brings them down and remembers
 * where they are. A clip that has been fetched once stays in the app's own storage and is played
 * from there for ever after, so the lessons work with the tablet in flight mode.
 *
 * What gets fetched, and when:
 *   • on launch, if there is a connection — the shared clips every quiz needs, then chapter one,
 *     so a child can open the app and start hearing lessons straight away;
 *   • then, quietly in the background, the other chapters, one clip at a time;
 *   • and on demand, when a screen asks for a line it has not got — see {@link #fetchNow}.
 *
 * Nothing here ever blocks the main thread, and a failed download is simply left for next time.
 */
public final class VoiceStore {

    /** Told when a chapter's download moves on, so a screen can show a bar. */
    public interface Progress {
        void onProgress(int chapter, int done, int total);
        void onFinished(int chapter, int downloaded, int failed);
    }

    private static final String DIR = "voice";

    private static final ExecutorService URGENT = Executors.newFixedThreadPool(3);
    private static final ExecutorService BACKGROUND = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private static final Set<String> inFlight = Collections.synchronizedSet(new LinkedHashSet<>());
    private static final AtomicBoolean trickling = new AtomicBoolean(false);
    private static volatile boolean paused = false;

    private VoiceStore() {}

    // ── where a clip lives ───────────────────────────────────────────────────
    public static File dir(Context context) {
        File dir = new File(context.getFilesDir(), DIR);
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    /** The clip for this key if it has been downloaded, otherwise null. */
    public static File clip(Context context, String key) {
        String name = VoiceCatalog.fileName(context, key);
        if (name == null) return null;
        File file = new File(dir(context), name);
        return file.exists() && file.length() > 256 ? file : null;
    }

    public static boolean has(Context context, String key) {
        return clip(context, key) != null;
    }

    // ── counting, for the download screen ────────────────────────────────────
    public static int countHave(Context context, List<String> keys) {
        int have = 0;
        for (String key : keys) {
            if (has(context, key)) have++;
        }
        return have;
    }

    public static long bytesOnDisk(Context context) {
        long total = 0;
        File[] files = dir(context).listFiles();
        if (files == null) return 0;
        for (File file : files) total += file.length();
        return total;
    }

    public static void deleteAll(Context context) {
        File[] files = dir(context).listFiles();
        if (files == null) return;
        for (File file : files) file.delete();
    }

    // ── fetching ─────────────────────────────────────────────────────────────

    /**
     * Fetches one clip right now because something on screen is waiting for it. `whenReady` runs
     * on the main thread with the file, or with null when it could not be had.
     */
    public static void fetchNow(Context context, String key, java.util.function.Consumer<File> whenReady) {
        Context app = context.getApplicationContext();
        File already = clip(app, key);
        if (already != null) {
            if (whenReady != null) whenReady.accept(already);
            return;
        }
        if (!inFlight.add(key)) return;            // someone else is already getting it
        URGENT.execute(() -> {
            File file = fetchBlocking(app, key);
            inFlight.remove(key);
            if (whenReady != null) MAIN.post(() -> whenReady.accept(file));
        });
    }

    /** Downloads one clip on the calling thread. Returns the file, or null. */
    static File fetchBlocking(Context context, String key) {
        String name = VoiceCatalog.fileName(context, key);
        if (name == null) return null;
        File target = new File(dir(context), name);
        if (target.exists() && target.length() > 256) return target;
        return Net.download(VoiceCatalog.BASE_URL + name, target) ? target : null;
    }

    /**
     * Brings down a whole chapter, reporting as it goes. Chapter -1 means the shared clips the
     * quizzes read numbers from.
     */
    public static void downloadChapter(Context context, int chapter, Progress progress) {
        Context app = context.getApplicationContext();
        List<String> keys = VoiceCatalog.keysOfChapter(app, chapter);
        download(app, chapter, keys, progress);
    }

    private static void download(Context app, int chapter, List<String> keys, Progress progress) {
        List<String> missing = new ArrayList<>();
        for (String key : keys) {
            if (!has(app, key)) missing.add(key);
        }
        final int total = keys.size();
        final int already = total - missing.size();
        if (missing.isEmpty()) {
            if (progress != null) MAIN.post(() -> progress.onFinished(chapter, 0, 0));
            return;
        }

        AtomicInteger done = new AtomicInteger(already);
        AtomicInteger ok = new AtomicInteger();
        AtomicInteger bad = new AtomicInteger();
        AtomicInteger left = new AtomicInteger(missing.size());

        for (String key : missing) {
            URGENT.execute(() -> {
                if (!paused && fetchBlocking(app, key) != null) ok.incrementAndGet();
                else bad.incrementAndGet();
                int at = done.incrementAndGet();
                if (progress != null) MAIN.post(() -> progress.onProgress(chapter, at, total));
                if (left.decrementAndGet() == 0 && progress != null) {
                    MAIN.post(() -> progress.onFinished(chapter, ok.get(), bad.get()));
                }
            });
        }
    }

    // ── what happens on launch ───────────────────────────────────────────────

    /**
     * The opening move: refresh the list of what the host has, then fetch the shared clips and
     * the chapter the child is on (chapter one for a new child), then trickle the rest.
     */
    public static void startOnLaunch(Context context, int chapterInUse) {
        Context app = context.getApplicationContext();
        if (!Net.online(app)) return;
        VoiceCatalog.refresh(app, () -> {
            if (VoiceCatalog.isEmpty(app)) return;
            download(app, -1, VoiceCatalog.sharedKeys(app), null);
            download(app, chapterInUse, VoiceCatalog.keysOfChapter(app, chapterInUse), null);
            trickleTheRest(app, chapterInUse);
        });
    }

    /**
     * The other chapters, one clip at a time on a single thread, so the download never gets in
     * the way of the lesson the child is actually listening to.
     */
    public static void trickleTheRest(Context context, int skipChapter) {
        if (!trickling.compareAndSet(false, true)) return;
        Context app = context.getApplicationContext();
        BACKGROUND.execute(() -> {
            try {
                for (int chapter = 0; chapter < Book.CHAPTERS.size(); chapter++) {
                    if (chapter == skipChapter) continue;
                    for (String key : VoiceCatalog.keysOfChapter(app, chapter)) {
                        if (paused || !Net.online(app)) return;
                        if (has(app, key)) continue;
                        fetchBlocking(app, key);
                        try {
                            Thread.sleep(120);       // stay out of the way of everything else
                        } catch (InterruptedException e) {
                            return;
                        }
                    }
                }
            } finally {
                trickling.set(false);
            }
        });
    }

    public static void pause(boolean stop) {
        paused = stop;
    }

    public static boolean isPaused() {
        return paused;
    }
}
