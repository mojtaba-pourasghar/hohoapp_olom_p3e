package com.hoohooolom.app.ui.screens;

import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.hoohooolom.app.R;
import com.hoohooolom.app.data.Book;
import com.hoohooolom.app.data.VoiceCatalog;
import com.hoohooolom.app.net.Net;
import com.hoohooolom.app.tts.VoiceStore;
import com.hoohooolom.app.ui.BaseFragment;
import com.hoohooolom.app.ui.UiKit;

import java.util.List;

import static com.hoohooolom.app.data.PersianDigits.fa;

/**
 * «دانلود صدای هوهو» — the screen that brings the narration down from the host.
 *
 * The clips are not in the APK: there are four thousand of them and they would make it 143 MB.
 * They live at {@link VoiceCatalog#BASE_URL}, and the app fetches a chapter at a time. Chapter
 * one comes down by itself on the first launch with a connection; the rest trickles in the
 * background, and this screen is where a parent can hurry it along or see where it got to.
 *
 * Without the clips nothing is broken — the device's own voice reads every line — so this screen
 * is about quality, and says as much.
 */
public class VoiceDownloadFragment extends BaseFragment {

    private View root;
    private boolean working;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_list_index, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        root = view;
        ((TextView) view.findViewById(R.id.screen_title)).setText("دانلود صدای هوهو");
        view.findViewById(R.id.chip_container).setVisibility(View.GONE);
        render();

        // the list of what the host has may have changed since last time
        VoiceCatalog.refresh(requireContext(), () -> {
            if (isAdded()) render();
        });
    }

    private int color(int res) {
        return ContextCompat.getColor(requireContext(), res);
    }

    private void render() {
        if (root == null || !isAdded()) return;
        boolean online = Net.online(requireContext());
        List<String> all = VoiceCatalog.keys(requireContext());
        int have = VoiceStore.countHave(requireContext(), all);
        long megabytes = VoiceStore.bytesOnDisk(requireContext()) / (1024 * 1024);

        ((TextView) root.findViewById(R.id.chapter_title)).setText("صدای هوهو روی این دستگاه");
        ((TextView) root.findViewById(R.id.chapter_subtitle)).setText(all.isEmpty()
            ? "فهرستِ صداها هنوز خوانده نشده. وصل شو تا بیاید؛ تا آن وقت هوهو با صدای خودِ دستگاه حرف می‌زند."
            : fa(have) + " گفتار از " + fa(all.size()) + " روی دستگاه است (" + fa((int) megabytes)
              + " مگابایت). هر درسی که صدایش نیامده باشد، با صدای خودِ دستگاه خوانده می‌شود.");

        LinearLayout list = root.findViewById(R.id.list_container);
        list.removeAllViews();

        if (!online) list.addView(notice("اینترنت وصل نیست. وقتی وصل شد، همین صفحه ادامه می‌دهد."));
        if (all.isEmpty() && online) {
            list.addView(notice("فهرستِ صداها از " + VoiceCatalog.BASE_URL + " خوانده نشد."));
        }

        if (!all.isEmpty()) {
            list.addView(rowFor(-1, "واژه‌های سؤال‌ها و عددها", "برای خواندنِ تمرین و کاربرگ و آزمون"));
            for (Book.Chapter chapter : Book.CHAPTERS) {
                list.addView(rowFor(chapter.index, "درس " + chapter.numberFa + ": " + chapter.title,
                    "صفحه‌های " + fa(chapter.firstPage) + " تا " + fa(chapter.lastPage)));
            }
            list.addView(allButtons(all));
        }

        list.addView(backLink());
    }

    /** One line per chapter: how much of it is here, and a button to fetch the rest. */
    private View rowFor(int chapter, String title, String note) {
        List<String> keys = VoiceCatalog.keysOfChapter(requireContext(), chapter);
        int have = VoiceStore.countHave(requireContext(), keys);
        boolean complete = !keys.isEmpty() && have == keys.size();

        LinearLayout card = UiKit.column(requireContext());
        UiKit.applyCardBg(card, requireContext(), R.color.bg_card, R.color.teal_light);
        int pad = UiKit.dp(requireContext(), 14);
        card.setPadding(pad, pad, pad, pad);
        card.setLayoutParams(UiKit.marginParams(requireContext(), 0, 10));

        LinearLayout head = UiKit.row(requireContext());
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView name = UiKit.text(requireContext(), title, 14f, R.color.text_primary, true);
        LinearLayout.LayoutParams grow =
            new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        head.addView(name, grow);
        head.addView(UiKit.text(requireContext(),
            keys.isEmpty() ? "—" : fa(have) + "/" + fa(keys.size()),
            12.5f, complete ? R.color.teal_dark : R.color.text_muted, true));
        card.addView(head);

        card.addView(UiKit.text(requireContext(), note, 11.5f, R.color.text_muted, false),
            UiKit.marginParams(requireContext(), 2, 0));

        ProgressBar bar = new ProgressBar(requireContext(), null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(100);
        bar.setProgress(keys.isEmpty() ? 0 : Math.round(have * 100f / keys.size()));
        bar.setProgressDrawable(ContextCompat.getDrawable(requireContext(), R.drawable.progress_teal));
        LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, UiKit.dp(requireContext(), 7));
        barLp.topMargin = UiKit.dp(requireContext(), 9);
        card.addView(bar, barLp);

        if (!complete && !keys.isEmpty()) {
            TextView button = UiKit.primaryButton(requireContext(), "دانلود صدا",
                color(R.color.teal), R.color.white);
            button.setEnabled(!working);
            button.setOnClickListener(v -> start(chapter));
            card.addView(button, UiKit.marginParams(requireContext(), 10, 0));
        } else if (complete) {
            card.addView(UiKit.text(requireContext(), "✓ کامل است", 12.5f, R.color.teal_dark, true),
                UiKit.marginParams(requireContext(), 8, 0));
        }
        return card;
    }

    private View allButtons(List<String> all) {
        LinearLayout box = UiKit.column(requireContext());
        box.setLayoutParams(UiKit.marginParams(requireContext(), 6, 10));

        boolean missing = VoiceStore.countHave(requireContext(), all) < all.size();
        if (missing) {
            TextView everything = UiKit.primaryButton(requireContext(), "دانلودِ همه‌ی صداها",
                color(R.color.orange), R.color.white);
            everything.setOnClickListener(v -> {
                VoiceStore.pause(false);
                VoiceStore.trickleTheRest(requireContext(), -2);   // -2 skips nothing
                working = true;
                render();
            });
            box.addView(everything);
        }

        TextView clear = UiKit.primaryButton(requireContext(), "پاک کردنِ صداهای دانلودشده",
            color(R.color.bg_card), R.color.pink_dark);
        clear.setOnClickListener(v -> {
            VoiceStore.deleteAll(requireContext());
            render();
        });
        box.addView(clear, UiKit.marginParams(requireContext(), 8, 0));
        return box;
    }

    private void start(int chapter) {
        working = true;
        VoiceStore.pause(false);
        render();
        VoiceStore.downloadChapter(requireContext(), chapter, new VoiceStore.Progress() {
            @Override public void onProgress(int which, int done, int total) {
                if (isAdded()) render();
            }
            @Override public void onFinished(int which, int downloaded, int failed) {
                working = false;
                if (isAdded()) render();
            }
        });
    }

    private View notice(String message) {
        TextView view = UiKit.text(requireContext(), message, 12.5f, R.color.orange_text, false);
        UiKit.applyCardBg(view, requireContext(), R.color.orange_bg, R.color.orange_border);
        int pad = UiKit.dp(requireContext(), 13);
        view.setPadding(pad, pad, pad, pad);
        view.setLayoutParams(UiKit.marginParams(requireContext(), 0, 10));
        return view;
    }

    private View backLink() {
        TextView back = UiKit.text(requireContext(), "‹ بازگشت به نقشه", 13.5f, R.color.teal_dark, true);
        back.setGravity(Gravity.CENTER);
        int pad = UiKit.dp(requireContext(), 14);
        back.setPadding(0, pad, 0, pad);
        UiKit.applyCardBg(back, requireContext(), R.color.bg_card, R.color.teal_border);
        back.setLayoutParams(UiKit.marginParams(requireContext(), 6, 18));
        back.setOnClickListener(v -> nav().go(com.hoohooolom.app.ui.Screen.MAP));
        return back;
    }

    @Override
    protected String entryTip() {
        return null;
    }
}
