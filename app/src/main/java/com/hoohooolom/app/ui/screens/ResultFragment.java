package com.hoohooolom.app.ui.screens;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hoohooolom.app.R;
import com.hoohooolom.app.data.AppState;
import com.hoohooolom.app.data.Book;
import com.hoohooolom.app.data.Lessons;
import com.hoohooolom.app.data.QuizMode;
import com.hoohooolom.app.data.QuizSession;
import com.hoohooolom.app.data.QuizSessionHolder;
import com.hoohooolom.app.ui.BaseFragment;
import com.hoohooolom.app.ui.Screen;
import com.hoohooolom.app.ui.UiKit;

import static com.hoohooolom.app.data.PersianDigits.fa;

public class ResultFragment extends BaseFragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_result, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        QuizSession res = QuizSessionHolder.get();
        if (res == null) {
            nav().go(Screen.MAP);
            return;
        }
        AppState s = state();
        int right = res.rightCount();
        java.util.List<QuizSession.LogEntry> log = res.buildLog();
        boolean good = right >= res.items.size() * 0.8;
        boolean passed = right >= res.items.size() * 0.7;

        com.hoohooolom.app.tts.SoundManager sound = com.hoohooolom.app.tts.SoundManager.get();
        if (sound != null && good) sound.win();

        ((TextView) view.findViewById(R.id.result_score)).setText(fa(right) + "/" + fa(res.items.size()));
        ((TextView) view.findViewById(R.id.result_title)).setText(good ? "عالی بود!" : passed ? "خوب بود!" : "خوب شروع کردی");
        ((TextView) view.findViewById(R.id.result_msg)).setText(good
            ? "این بخش را خوب یاد گرفته‌ای."
            : passed ? "تقریباً یاد گرفتی. یک دور دیگر هم تمرین کن تا محکم شود."
            : "چند سؤال را با هوهو دوباره کار کن، بعد همین تمرین را تکرار کن.");

        buildNextStep(view, res, s, passed);

        LinearLayout list = view.findViewById(R.id.result_list);
        list.removeAllViews();
        int shown = Math.min(6, log.size());
        for (int i = 0; i < shown; i++) {
            QuizSession.LogEntry e = log.get(i);
            LinearLayout row = UiKit.column(requireContext());
            row.setPadding(UiKit.dp(requireContext(), 12), UiKit.dp(requireContext(), 10), UiKit.dp(requireContext(), 12), UiKit.dp(requireContext(), 10));
            UiKit.applyCardBg(row, requireContext(), R.color.bg_card, e.correct ? R.color.teal_border : R.color.pink_border);
            row.setLayoutParams(UiKit.marginParams(requireContext(), 0, 7));
            row.addView(UiKit.text(requireContext(), (e.correct ? "✓ " : "✕ ") + e.question, 13f, R.color.text_primary, true));
            String answerLine = e.correct ? ("پاسخ تو: " + e.yourAnswer)
                : (s.parentUnlockedThisSession ? ("پاسخ درست: " + e.correctAnswer + " · پاسخ تو: " + e.yourAnswer) : "پاسخ درست با ورود والدین دیده می‌شود");
            TextView answerView = UiKit.text(requireContext(), answerLine, 11.5f, e.correct ? R.color.teal_dark : R.color.pink_dark, false);
            answerView.setLayoutParams(UiKit.marginParams(requireContext(), 3, 0));
            row.addView(answerView);
            list.addView(row);
        }

        TextView answersBtn = view.findViewById(R.id.result_answers_btn);
        answersBtn.setText(s.parentUnlockedThisSession ? "پاسخ‌ها نمایش داده شد" : "دیدن پاسخ‌ها (والدین)");
        answersBtn.setOnClickListener(v -> {
            if (!s.parentUnlockedThisSession) nav().goParent();
        });

        view.findViewById(R.id.result_retry).setOnClickListener(v -> {
            Bundle args = new Bundle();
            args.putString("mode", res.mode.name());
            args.putInt("chapter", res.chapter);
            args.putInt("option", res.option);
            nav().go(Screen.QUIZ, args);
        });
        view.findViewById(R.id.result_map).setOnClickListener(v -> nav().go(Screen.MAP));
    }

    /**
     * The teacher-like next move: once the child has shown they understood a section, walk them
     * to the next section the teacher has covered; at the end of a chapter, hand them the
     * chapter's worksheet instead.
     */
    private void buildNextStep(View view, QuizSession res, AppState s, boolean passed) {
        TextView nextStep = view.findViewById(R.id.result_next_step);
        if (res.mode != QuizMode.PRACTICE || !passed) {
            nextStep.setVisibility(View.GONE);
            return;
        }

        Book.Chapter ch = Book.chapter(res.chapter);
        int nextSection = res.option + 1;

        if (nextSection < ch.sections.size()) {
            nextStep.setVisibility(View.VISIBLE);
            nextStep.setText("برویم بخش بعد: " + ch.sections.get(nextSection));
            nextStep.setOnClickListener(v -> {
                Bundle args = new Bundle();
                args.putInt("chapter", res.chapter);
                if (Lessons.has(res.chapter, nextSection)) {
                    args.putInt("section", nextSection);
                    nav().go(Screen.LESSON, args);
                } else {
                    args.putString("mode", "PRACTICE");
                    args.putInt("option", nextSection);
                    nav().go(Screen.QUIZ, args);
                }
            });
            return;
        }

        nextStep.setVisibility(View.VISIBLE);
        nextStep.setText("درس تمام شد! کاربرگ درس " + ch.numberFa + " را بگیر");
        nextStep.setOnClickListener(v -> {
            Bundle args = new Bundle();
            args.putInt("chapter", res.chapter);
            nav().go(Screen.WORKSHEET_INDEX, args);
        });
    }

    @Override
    protected String entryTip() {
        return "کارنامه‌ات را ببین؛ سؤال‌های اشتباه را با هم دوباره کار می‌کنیم.";
    }
}
