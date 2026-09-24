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
import com.hoohooolom.app.ui.Screen;

public class ExamIndexFragment extends BaseFragment {
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
        ((TextView) view.findViewById(R.id.screen_title)).setText("آزمون‌ها");
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
            .setText("آزمون از همه‌ی بخش‌های این درس است؛ مثل امتحان مدرسه. هر چهارده درس و هر سه سطح باز است.");

        LinearLayout list = view.findViewById(R.id.list_container);
        list.removeAllViews();
        for (Level level : Level.values()) {
            // a half-finished set is picked up where it stopped, so the card says so
            boolean halfDone = s.hasAttempt("EXAM_" + ch.index + "_" + level.index);
            list.addView(ScreenHelpers.buildLevelCard(requireContext(), level,
                halfDone ? "آزمون نیمه‌کاره — از همان سؤال ادامه می‌دهی" : "۱۵ سؤال چندگزینه‌ای · از همه‌ی بخش‌های فصل", () -> {
                Bundle args = new Bundle();
                args.putString("mode", "EXAM");
                args.putInt("chapter", ch.index);
                args.putInt("option", level.index);
                nav().go(Screen.QUIZ, args);
            }));
        }
    }

    @Override
    protected String entryTip() {
        return "آزمون از همه‌ی بخش‌های این درس است؛ مثل امتحان مدرسه.";
    }
}
