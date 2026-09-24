package com.hoohooolom.app.ui.screens;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hoohooolom.app.R;
import com.hoohooolom.app.data.AppState;
import com.hoohooolom.app.data.Book;
import com.hoohooolom.app.data.Level;
import com.hoohooolom.app.ui.BaseFragment;
import com.hoohooolom.app.ui.UiKit;
import com.hoohooolom.app.ui.Screen;

public class WorksheetIndexFragment extends BaseFragment {
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
        ((TextView) view.findViewById(R.id.screen_title)).setText("کاربرگ‌ها");
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
            .setText("کاربرگ ۳۰ سؤالی را همین‌جا می‌نویسی؛ کاغذ لازم نیست. هر چهارده درس و هر سه سطح باز است.");

        LinearLayout list = view.findViewById(R.id.list_container);
        list.removeAllViews();
        for (Level level : Level.values()) {
            // a half-finished set is picked up where it stopped, so the card says so
            boolean halfDone = s.hasAttempt("WORKSHEET_" + ch.index + "_" + level.index);
            list.addView(ScreenHelpers.buildLevelCard(requireContext(), level,
                halfDone ? "کاربرگ نیمه‌کاره — از همان سؤال ادامه می‌دهی" : "۳۰ سؤال · همین‌جا جواب می‌نویسی", () -> {
                Bundle args = new Bundle();
                args.putString("mode", "WORKSHEET");
                args.putInt("chapter", ch.index);
                args.putInt("option", level.index);
                nav().go(Screen.QUIZ, args);
            }));
        }

        // a second, separate way to get worksheets: ready-made sheets to print, not to answer
        // on screen. Its own screen so the two never get mixed up.
        list.addView(downloadLink(ch.index));
    }

    /** Doorway to «دانلود کاربرگ» — kept visually apart from the sets answered inside the app. */
    private View downloadLink(int chapter) {
        LinearLayout card = UiKit.column(requireContext());
        int pad = UiKit.dp(requireContext(), 16);
        card.setPadding(pad, pad, pad, pad);
        UiKit.applyCardBg(card, requireContext(), R.color.bg_card_alt, R.color.border_card);
        card.setLayoutParams(UiKit.marginParams(requireContext(), 14, 8));

        card.addView(UiKit.text(requireContext(), "دانلود کاربرگ ⤓", 14.5f, R.color.teal_dark, true));
        card.addView(UiKit.text(requireContext(),
            "کاربرگ‌های چاپی برای نوشتن روی کاغذ — جدا از سی سؤالِ بالا.",
            11.5f, R.color.text_muted, false));

        card.setOnClickListener(v -> {
            Bundle args = new Bundle();
            args.putInt("chapter", chapter);
            nav().go(Screen.WORKSHEET_DOWNLOAD, args);
        });
        UiKit.tapSound(card);
        return card;
    }

    @Override
    protected String entryTip() {
        return "کاربرگ ۳۰ سؤالی را همین‌جا می‌نویسی؛ کاغذ لازم نیست.";
    }
}
