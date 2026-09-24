package com.hoohooolom.app.ui.screens;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hoohooolom.app.R;
import com.hoohooolom.app.data.AppState;
import com.hoohooolom.app.data.Book;
import com.hoohooolom.app.data.WorksheetDownload;
import com.hoohooolom.app.ui.BaseFragment;
import com.hoohooolom.app.ui.UiKit;

import java.util.List;

/**
 * «دانلود کاربرگ» — printable sheets, kept separate from the 30-question sets that are answered
 * inside the app. Nothing here is generated: each sheet is a file listed in a catalogue, opened
 * with whatever the device uses for downloads, so no storage permission is needed.
 *
 * The catalogue is empty until {@link WorksheetDownload#CATALOGUE_URL} is pointed at one. Rather
 * than show a broken-looking blank page, the screen says so in as many words.
 */
public class WorksheetDownloadFragment extends BaseFragment {
    private int currentChapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_list_index, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        AppState s = state();
        Bundle args = getArguments();
        currentChapter = args != null ? args.getInt("chapter", s.taughtChapter) : s.taughtChapter;
        ((TextView) view.findViewById(R.id.screen_title)).setText("دانلود کاربرگ");
        render(view);
    }

    private void render(View view) {
        AppState s = state();
        Book.Chapter ch = Book.chapter(currentChapter);

        FrameLayout chipContainer = view.findViewById(R.id.chip_container);
        chipContainer.removeAllViews();
        chipContainer.addView(ScreenHelpers.buildChapterChipRow(requireContext(), s, currentChapter, i -> {
            currentChapter = i;
            render(view);
        }));

        ((TextView) view.findViewById(R.id.chapter_title)).setText("درس " + ch.numberFa + ": " + ch.title);
        ((TextView) view.findViewById(R.id.chapter_subtitle))
            .setText("کاربرگ‌های چاپی برای نوشتن روی کاغذ. اینجا جدا از کاربرگ‌های داخلِ اپ است.");

        LinearLayout list = view.findViewById(R.id.list_container);
        list.removeAllViews();

        List<WorksheetDownload> sheets = WorksheetDownload.forChapter(requireContext(), currentChapter);
        if (sheets.isEmpty()) {
            list.addView(emptyCard());
        } else {
            for (WorksheetDownload sheet : sheets) list.addView(sheetCard(sheet));
        }

        list.addView(backLink());
    }

    /** Honest empty state: nothing is published yet, and it says why. */
    private View emptyCard() {
        LinearLayout card = UiKit.column(requireContext());
        int pad = UiKit.dp(requireContext(), 18);
        card.setPadding(pad, pad, pad, pad);
        UiKit.applyCardBg(card, requireContext(), R.color.orange_bg, R.color.orange_border);
        card.setLayoutParams(UiKit.marginParams(requireContext(), 0, 10));

        card.addView(UiKit.text(requireContext(), "هنوز کاربرگ چاپی گذاشته نشده", 15f, R.color.orange_text, true));
        TextView body = UiKit.text(requireContext(),
            "این بخش آماده است و به محض اینکه فایل‌های کاربرگ جایی گذاشته شوند، فهرستشان همین‌جا "
                + "می‌آید و با یک ضربه دانلود می‌شوند.\n\n"
                + "تا آن وقت، کاربرگ‌های داخلِ اپ کار می‌کنند: سی سؤال در هر کاربرگ، همین‌جا جواب می‌دهی.",
            13f, R.color.text_primary, false);
        body.setLineSpacing(UiKit.dp(requireContext(), 5), 1f);
        card.addView(body, UiKit.marginParams(requireContext(), 8, 0));
        return card;
    }

    private View sheetCard(WorksheetDownload sheet) {
        LinearLayout card = UiKit.column(requireContext());
        int pad = UiKit.dp(requireContext(), 16);
        card.setPadding(pad, pad, pad, pad);
        UiKit.applyCardBg(card, requireContext(), R.color.bg_card, R.color.teal_border);
        card.setLayoutParams(UiKit.marginParams(requireContext(), 0, 10));

        card.addView(UiKit.text(requireContext(), sheet.title, 14.5f, R.color.text_primary, true));
        if (!sheet.note.isEmpty()) {
            card.addView(UiKit.text(requireContext(), sheet.note, 11.5f, R.color.text_muted, false));
        }

        TextView button = UiKit.text(requireContext(), "دانلود ⤓", 13.5f, R.color.white, true);
        button.setGravity(Gravity.CENTER);
        int vp = UiKit.dp(requireContext(), 12);
        button.setPadding(0, vp, 0, vp);
        button.setBackground(UiKit.roundedBg(
            androidx.core.content.ContextCompat.getColor(requireContext(), R.color.teal), 0, 14f, requireContext()));
        button.setLayoutParams(UiKit.marginParams(requireContext(), 12, 0));
        button.setOnClickListener(v -> open(sheet.url));
        UiKit.tapSound(button);
        card.addView(button);
        return card;
    }

    /** Hands the file to the device — browser or download manager, whichever is set up. */
    private void open(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(requireContext(), "برنامه‌ای برای باز کردن این فایل پیدا نشد.", Toast.LENGTH_LONG).show();
        }
    }

    private View backLink() {
        TextView link = UiKit.text(requireContext(), "‹ کاربرگ‌های داخل اپ", 13.5f, R.color.teal_dark, true);
        link.setGravity(Gravity.CENTER);
        int vp = UiKit.dp(requireContext(), 14);
        link.setPadding(0, vp, 0, vp);
        UiKit.applyCardBg(link, requireContext(), R.color.bg_card, R.color.teal_border);
        link.setLayoutParams(UiKit.marginParams(requireContext(), 6, 8));
        link.setOnClickListener(v -> {
            Bundle args = new Bundle();
            args.putInt("chapter", currentChapter);
            nav().go(com.hoohooolom.app.ui.Screen.WORKSHEET_INDEX, args);
        });
        UiKit.tapSound(link);
        return link;
    }

    @Override
    protected String entryTip() {
        return "اینجا کاربرگ‌های چاپی است. اگر کاغذ و مداد دوست داری، از همین‌جا دانلود کن!";
    }
}
