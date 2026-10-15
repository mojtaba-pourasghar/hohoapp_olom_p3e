package com.hoohooolom.app.ui;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import java.util.ArrayList;
import java.util.List;

import com.hoohooolom.app.R;
import com.hoohooolom.app.data.Book;
import com.hoohooolom.app.data.VoiceCatalog;
import com.hoohooolom.app.net.Net;
import com.hoohooolom.app.tts.VoiceStore;

import static com.hoohooolom.app.data.PersianDigits.fa;

/**
 * «صدای این فصل هنوز نیامده» — the window that stops a child at the door of a chapter whose
 * clips are not on the phone yet, downloads them, and lets them in when it is done.
 *
 * Before this, a chapter whose clips had not arrived simply sounded different: the device's own
 * voice read the lines, and a child had no way of knowing why هوهو suddenly sounded like a
 * robot — or that waiting a minute would fix it. The offer bar inside the lesson was there, but
 * it was a line of text competing with a lesson that had already started talking.
 *
 * So this blocks. It cannot be dismissed, there is nothing behind it to tap, and it closes
 * itself the moment the chapter is complete.
 *
 * With one exception, which matters: **a phone with no connection must not be a locked door.**
 * The app has always worked without the clips — the device reads the same words — so when there
 * is no internet, or the download cannot finish, the window says so and offers a way through
 * with the device's voice. Blocking a child who simply has no signal would make the app
 * unusable on a bus, which is exactly where it gets used.
 */
public final class VoiceGate {

    private VoiceGate() {}

    /**
     * Runs `whenReady` once the chapter's clips are on the phone.
     *
     * When they already are — the common case, since the app fetches ahead in the background —
     * this is just a method call and nothing is shown. Returns the dialog it put up, so the
     * screen that asked can close it if it goes away mid-download; null when nothing was shown.
     */
    public static Dialog require(Fragment host, int chapter, Runnable ready) {
        return requireAll(host, ready, chapter);
    }

    /**
     * The same, for a screen that needs more than one chapter's clips — a quiz reads the shared
     * question words (-1) as well as its own chapter. One window, one bar, all of it.
     */
    public static Dialog requireAll(Fragment host, Runnable ready, int... chapters) {
        Context context = host.requireContext();
        // every way out of this window runs the same thing, and never after the screen has gone
        Runnable whenReady = () -> {
            if (host.isAdded()) ready.run();
        };

        List<Integer> todo = new ArrayList<>();
        for (int chapter : chapters) {
            if (!todo.contains(chapter) && VoiceStore.needs(context, chapter)) todo.add(chapter);
        }
        if (todo.isEmpty()) {
            whenReady.run();
            return null;
        }

        Board board = new Board(context, todo.get(0));
        AlertDialog dialog = new AlertDialog.Builder(context)
            .setView(board.view)
            .setCancelable(false)                 // «تا دانلود تمام نشده نگذار کاری کند»
            .create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
        dialog.setOnKeyListener((d, keyCode, event) ->
            keyCode == android.view.KeyEvent.KEYCODE_BACK);   // back does nothing while it runs
        dialog.show();

        board.onGiveUp = () -> {
            dismiss(dialog);
            whenReady.run();                      // through with the device's voice
        };
        start(host, board, dialog, todo, whenReady);
        return dialog;
    }

    /**
     * Walks the chapters one at a time, but shows them as one job: the bar counts every clip of
     * every chapter, so the child sees one bar fill once rather than two that restart.
     */
    private static void start(Fragment host, Board board, AlertDialog dialog, List<Integer> todo,
                              Runnable whenReady) {
        Context context = host.requireContext();
        if (!Net.online(context)) {
            board.offline();
            return;
        }

        board.working();
        VoiceStore.pause(false);                  // the background trickle may have been halted

        int[] totals = new int[todo.size()];
        int grand = 0;
        for (int i = 0; i < todo.size(); i++) {
            totals[i] = VoiceCatalog.keysOfChapter(context, todo.get(i)).size();
            grand += totals[i];
        }
        step(host, board, dialog, todo, totals, grand, 0, 0, 0, whenReady);
    }

    private static void step(Fragment host, Board board, AlertDialog dialog, List<Integer> todo,
                             int[] totals, int grand, int at, int base, int failedSoFar,
                             Runnable whenReady) {
        Context context = host.requireContext();
        if (at >= todo.size()) {
            dismiss(dialog);
            whenReady.run();
            return;
        }
        int chapter = todo.get(at);
        board.name(chapter);
        VoiceStore.downloadChapter(context, chapter, new VoiceStore.Progress() {
            @Override public void onProgress(int which, int done, int total) {
                if (host.isAdded()) board.progress(base + done, grand);
            }

            @Override public void onFinished(int which, int downloaded, int failed) {
                if (!host.isAdded()) return;
                int failedNow = failedSoFar + failed;
                boolean stillMissing = VoiceStore.needs(context, chapter);
                if (failedNow > 0 && stillMissing) {
                    board.failed(failedNow,
                        () -> start(host, board, dialog, todo, whenReady));   // retry the lot
                    return;
                }
                step(host, board, dialog, todo, totals, grand, at + 1, base + totals[at],
                    failedNow, whenReady);
            }
        });
    }

    private static void dismiss(Dialog dialog) {
        try {
            dialog.dismiss();
        } catch (Exception ignored) {
            // the window went away with its activity; nothing to close
        }
    }

    /** The card inside the window: a title, a line of explanation, a bar, and a count. */
    private static final class Board {
        final View view;
        final Context context;
        private final TextView title;
        private final TextView note;
        private final ProgressBar bar;
        private final TextView count;
        private final TextView action;
        Runnable onGiveUp;

        Board(Context context, int chapter) {
            this.context = context;
            LinearLayout card = UiKit.column(context);
            int pad = UiKit.dp(context, 20);
            card.setPadding(pad, pad, pad, pad);
            UiKit.applyCardBg(card, context, R.color.bg_card, R.color.orange_border);

            String name = chapter < 0 ? "واژه‌های سؤال‌ها"
                : "درس " + Book.chapter(chapter).numberFa;

            title = UiKit.text(context, "🔊 صدای " + name + " هنوز نیامده", 16f,
                R.color.orange_text, true);
            title.setGravity(Gravity.CENTER);
            card.addView(title);

            note = UiKit.text(context,
                "هوهو می‌خواهد خودش این درس را بگوید. چند لحظه صبر کن تا صدایش برسد.",
                13f, R.color.text_primary, false);
            note.setGravity(Gravity.CENTER);
            note.setLineSpacing(UiKit.dp(context, 5), 1f);
            card.addView(note, UiKit.marginParams(context, 10, 0));

            bar = new ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal);
            bar.setMax(100);
            bar.setIndeterminate(true);
            bar.setProgressDrawable(ContextCompat.getDrawable(context, R.drawable.progress_teal));
            LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, UiKit.dp(context, 9));
            barLp.topMargin = UiKit.dp(context, 16);
            card.addView(bar, barLp);

            count = UiKit.text(context, "", 12.5f, R.color.text_muted, true);
            count.setGravity(Gravity.CENTER);
            card.addView(count, UiKit.marginParams(context, 8, 0));

            action = UiKit.text(context, "", 13f, R.color.teal_dark, true);
            action.setGravity(Gravity.CENTER);
            int vp = UiKit.dp(context, 12);
            action.setPadding(0, vp, 0, vp);
            action.setVisibility(View.GONE);
            card.addView(action, UiKit.marginParams(context, 10, 0));

            LinearLayout wrap = UiKit.column(context);
            int side = UiKit.dp(context, 18);
            wrap.setPadding(side, 0, side, 0);
            wrap.addView(card);
            view = wrap;
        }

        /** The chapter being fetched right now, so a two-chapter job says which one it is on. */
        void name(int chapter) {
            title.setText("🔊 صدای " + nameOf(context, chapter) + " هنوز نیامده");
        }

        private static String nameOf(Context context, int chapter) {
            return chapter < 0 ? "واژه\u200cهای سؤال\u200cها" : "درس " + Book.chapter(chapter).numberFa;
        }

        void working() {
            bar.setVisibility(View.VISIBLE);
            bar.setIndeterminate(true);
            count.setText("در حال آماده کردن…");
            action.setVisibility(View.GONE);
        }

        void progress(int done, int total) {
            bar.setIndeterminate(false);
            bar.setProgress(total > 0 ? Math.round(done * 100f / total) : 0);
            int pct = total > 0 ? Math.round(done * 100f / total) : 0;
            count.setText(fa(done) + " از " + fa(total) + " گفتار  (" + fa(pct) + "٪)");
        }

        /**
         * No connection. The lesson still works — the phone reads the same words — so this is
         * the one way out of the window, and it says plainly what it costs.
         */
        void offline() {
            bar.setVisibility(View.GONE);
            title.setText("📵 اینترنت وصل نیست");
            note.setText("برای آوردنِ صدای هوهو اینترنت لازم است. می‌توانی همین حالا ادامه بدهی — "
                + "درس کار می‌کند و جمله‌ها را خودِ گوشی می‌خواند — و هر وقت اینترنت وصل شد، "
                + "صدای هوهو خودش می‌آید.");
            count.setText("");
            offer("ادامه بده", onGiveUp);
        }

        /** Some clips did not arrive. Try again, or go on without them. */
        void failed(int howMany, Runnable retry) {
            bar.setIndeterminate(false);
            title.setText("صدا کامل نیامد");
            note.setText(fa(howMany) + " گفتار نرسید. دوباره امتحان کن، یا ادامه بده و بگذار "
                + "بعداً خودش بیاید.");
            offer("دوباره امتحان کن", retry);
            // and a quieter second way out, so the window can never become a dead end
            count.setText("");
            count.setOnClickListener(v -> {
                if (onGiveUp != null) onGiveUp.run();
            });
            count.setText("ادامه بدون صدای هوهو");
            UiKit.tapSound(count);
        }

        private void offer(String label, Runnable what) {
            action.setText(label);
            action.setVisibility(View.VISIBLE);
            UiKit.applyCardBg(action, context, R.color.teal_bg, R.color.teal_border);
            action.setOnClickListener(v -> {
                if (what != null) what.run();
            });
            UiKit.tapSound(action);
        }
    }
}
