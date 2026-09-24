package com.hoohooolom.app.ui.screens;

import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import java.util.List;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.hoohooolom.app.R;
import com.hoohooolom.app.data.AppState;
import com.hoohooolom.app.data.Book;
import com.hoohooolom.app.data.Lessons;
import com.hoohooolom.app.data.PageLessons;
import com.hoohooolom.app.data.PersianDigits;
import com.hoohooolom.app.ui.BaseFragment;
import com.hoohooolom.app.ui.Screen;
import com.hoohooolom.app.ui.UiKit;


public class SectionsFragment extends BaseFragment {
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
        ((TextView) view.findViewById(R.id.screen_title)).setText("تمرین بخش‌ها");
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
        ((TextView) view.findViewById(R.id.chapter_subtitle)).setText(PageLessons.hasPages(ch.index)
            ? "هوهو همین صفحه‌های کتاب را یکی‌یکی درس می‌دهد. هیچ درسی و هیچ بخشی قفل نیست."
            : "هر بخش تمرین جدا دارد و همه‌شان باز است.");

        LinearLayout list = view.findViewById(R.id.list_container);
        list.removeAllViews();
        for (int i = 0; i < ch.sections.size(); i++) {
            list.addView(buildRow(ch, i));
        }
        list.addView(buildChapterLink("کاربرگ‌های این درس ›", R.color.orange_bg, R.color.orange_border, R.color.orange_text,
            () -> {
                Bundle args = new Bundle();
                args.putInt("chapter", ch.index);
                nav().go(Screen.WORKSHEET_INDEX, args);
            }));
        list.addView(buildChapterLink("آزمون این درس ›", R.color.pink_bg, R.color.pink_border, R.color.pink_dark,
            () -> {
                Bundle args = new Bundle();
                args.putInt("chapter", ch.index);
                nav().go(Screen.EXAM_INDEX, args);
            }));
    }

    /** Shortcut off the bottom of the section list, so a finished chapter stays one tap away. */
    private View buildChapterLink(String label, int bgRes, int borderRes, int textRes, Runnable onClick) {
        TextView link = UiKit.text(requireContext(), label, 13.5f, textRes, true);
        link.setGravity(Gravity.CENTER);
        link.setPadding(0, UiKit.dp(requireContext(), 14), 0, UiKit.dp(requireContext(), 14));
        UiKit.applyCardBg(link, requireContext(), bgRes, borderRes);
        link.setLayoutParams(UiKit.marginParams(requireContext(), 0, 8));
        link.setOnClickListener(v -> onClick.run());
        UiKit.tapSound(link);
        return link;
    }

    private View buildRow(Book.Chapter ch, int i) {
        LinearLayout card = UiKit.column(requireContext());
        card.setPadding(UiKit.dp(requireContext(), 14), UiKit.dp(requireContext(), 14), UiKit.dp(requireContext(), 14), UiKit.dp(requireContext(), 14));
        UiKit.applyCardBg(card, requireContext(), R.color.bg_card, R.color.border_green);
        card.setLayoutParams(UiKit.marginParams(requireContext(), 0, 10));

        AppState s = state();
        boolean learned = s.isSectionLessonDone(ch.index, i);
        boolean hasLesson = Lessons.has(ch.index, i);
        String practiceKey = "PRACTICE_" + ch.index + "_" + i;
        int nextRound = s.getRound(practiceKey) + 1;
        boolean halfDone = s.hasAttempt(practiceKey);

        String title = PersianDigits.fa(i + 1) + ". " + ch.sections.get(i) + (learned ? "  ✓" : "");
        String sub = halfDone
            ? "تمرین نیمه‌کاره داری؛ از همان‌جا ادامه می‌دهی."
            : "۱۵ سؤال تازه در هر دور · دور " + PersianDigits.fa(nextRound);
        card.addView(UiKit.text(requireContext(), title, 14.5f, R.color.text_primary, true));
        card.addView(UiKit.text(requireContext(), sub, 11.5f, R.color.text_muted, false));

        // the book's own pages, in book order — the main teaching for this section
        List<Integer> pages = PageLessons.pagesOfSection(ch.index, i);
        if (!pages.isEmpty()) {
            card.addView(UiKit.text(requireContext(), "صفحه‌های کتاب:", 11.5f, R.color.teal_dark, true),
                UiKit.marginParams(requireContext(), 9, 0));
            card.addView(buildPageRow(ch.index, pages));
        }

        LinearLayout buttons = UiKit.row(requireContext());
        buttons.setLayoutParams(UiKit.marginParams(requireContext(), 10, 0));

        if (hasLesson) {
            TextView lesson = pill(learned ? "خلاصه‌ی بخش ✓" : "خلاصه‌ی بخش", R.color.orange, R.color.white);
            lesson.setOnClickListener(v -> {
                Bundle args = new Bundle();
                args.putInt("chapter", ch.index);
                args.putInt("section", i);
                nav().go(Screen.LESSON, args);
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            lp.setMarginEnd(UiKit.dp(requireContext(), 6));
            buttons.addView(lesson, lp);
        }

        TextView practice = pill(halfDone ? "ادامه بده" : "تمرین کن", R.color.teal, R.color.white);
        practice.setOnClickListener(v -> {
            Bundle args = new Bundle();
            args.putString("mode", "PRACTICE");
            args.putInt("chapter", ch.index);
            args.putInt("option", i);
            nav().go(Screen.QUIZ, args);
        });
        buttons.addView(practice, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        card.addView(buttons);
        return card;
    }

    /** One chip per page of the book, so the child walks the pages the way the book prints them. */
    private View buildPageRow(int chapter, List<Integer> pages) {
        AppState s = state();
        HorizontalScrollView scroll = new HorizontalScrollView(requireContext());
        scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout row = UiKit.row(requireContext());
        row.setPadding(0, UiKit.dp(requireContext(), 6), 0, UiKit.dp(requireContext(), 2));

        for (int page : pages) {
            boolean done = s.isPageDone(page);
            TextView chip = UiKit.chip(requireContext(),
                "صفحه‌ی " + PersianDigits.fa(page) + (done ? " ✓" : ""),
                ContextCompat.getColor(requireContext(), done ? R.color.teal_bg : R.color.orange_bg),
                ContextCompat.getColor(requireContext(), done ? R.color.teal_border : R.color.orange_border),
                ContextCompat.getColor(requireContext(), done ? R.color.teal_dark : R.color.orange_text));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMarginEnd(UiKit.dp(requireContext(), 6));
            chip.setLayoutParams(lp);
            chip.setOnClickListener(v -> {
                Bundle args = new Bundle();
                args.putInt("chapter", chapter);
                args.putInt("page", page);
                nav().go(Screen.LESSON, args);
            });
            UiKit.tapSound(chip);
            row.addView(chip);
        }
        scroll.addView(row);
        return scroll;
    }

    private TextView pill(String label, int bgRes, int textRes) {
        TextView tv = UiKit.text(requireContext(), label, 13f, textRes, true);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(0, UiKit.dp(requireContext(), 11), 0, UiKit.dp(requireContext(), 11));
        tv.setBackground(UiKit.roundedBg(ContextCompat.getColor(requireContext(), bgRes), 0, 999f, requireContext()));
        UiKit.tapSound(tv);
        return tv;
    }

    @Override
    protected String entryTip() {
        return "هر بخش ۱۵ تمرین دارد؛ از آسان شروع می‌شود.";
    }
}
