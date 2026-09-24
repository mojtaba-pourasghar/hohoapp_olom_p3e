package com.hoohooolom.app.ui.screens;

import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.hoohooolom.app.R;
import com.hoohooolom.app.data.AppState;
import com.hoohooolom.app.data.QuizMode;
import com.hoohooolom.app.data.QuizResult;
import com.hoohooolom.app.ui.BaseFragment;
import com.hoohooolom.app.ui.UiKit;

import java.util.List;

import static com.hoohooolom.app.data.PersianDigits.fa;

public class RewardsFragment extends BaseFragment {
    private static final String[] WEEKDAYS = {"ش", "ی", "د", "س", "چ", "پ", "ج"};

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_rewards, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        render(view);
    }

    private void render(View view) {
        AppState s = state();
        List<QuizResult> history = s.getHistory();

        ((TextView) view.findViewById(R.id.rewards_summary)).setText(fa(s.stars) + " ستاره جمع کرده‌ای · " + fa(s.streak) + " روز پیوسته تمرین کرده‌ای");

        LinearLayout weekRow = view.findViewById(R.id.week_row);
        weekRow.removeAllViews();
        int litDays = Math.min(s.streak, 7);
        for (int i = 0; i < WEEKDAYS.length; i++) {
            boolean lit = i < litDays;
            LinearLayout col = UiKit.column(requireContext());
            col.setGravity(Gravity.CENTER);
            TextView mark = UiKit.text(requireContext(), lit ? "★" : "—", 16f, lit ? R.color.orange : R.color.text_faint, true);
            TextView label = UiKit.text(requireContext(), WEEKDAYS[i], 11f, R.color.text_muted, false);
            col.addView(mark);
            col.addView(label);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            col.setLayoutParams(lp);
            weekRow.addView(col);
        }

        boolean earnedFirstLesson = s.stars >= 1;
        boolean earnedCounter = s.stars >= 5;
        boolean earnedTenRight = s.stars >= 10;
        boolean earnedFiveDays = s.streak >= 5;
        boolean earnedSymmetry = history.stream().anyMatch(r -> r.chapter == 0 && r.total > 0 && r.rightCount == r.total);
        boolean earnedFullChapter = history.stream().anyMatch(r -> r.mode == QuizMode.EXAM && r.total > 0 && r.rightCount == r.total);

        String[][] medals = {
            {"★", "نخستین درس", "اولین ستاره‌ات را گرفتی"},
            {"◎", "کاشف", "پنج ستاره گرفتی"},
            {"✓", "ده پاسخ درست", "در تمرین بخش‌ها"},
            {"◆", "پنج روز پیوسته", "هر روز تمرین"},
            {"▲", "دانشمند کوچک", "نمره‌ی کامل در درس ۱"},
            {"●", "درس کامل", "آزمون با نتیجه‌ی کامل"}
        };
        boolean[] earned = {earnedFirstLesson, earnedCounter, earnedTenRight, earnedFiveDays, earnedSymmetry, earnedFullChapter};

        LinearLayout grid = view.findViewById(R.id.medal_grid);
        grid.removeAllViews();
        for (int i = 0; i < medals.length; i++) {
            grid.addView(buildMedalRow(medals[i][0], medals[i][1], medals[i][2], earned[i]));
        }

        LinearLayout hatRow = view.findViewById(R.id.hat_row);
        hatRow.removeAllViews();
        String[] hats = {"دانش‌آموزی", "کاپیتان", "کلاه بارانی", "بدون کلاه"};
        for (String hat : hats) {
            boolean sel = hat.equals(s.hat);
            TextView chip = UiKit.chip(requireContext(), hat,
                ContextCompat.getColor(requireContext(), sel ? R.color.orange_bg : R.color.bg_card),
                ContextCompat.getColor(requireContext(), sel ? R.color.orange : R.color.border_input),
                ContextCompat.getColor(requireContext(), R.color.text_primary));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMarginEnd(UiKit.dp(requireContext(), 6));
            chip.setLayoutParams(lp);
            chip.setOnClickListener(v -> {
                s.setHat(hat);
                render(view);
            });
            hatRow.addView(chip);
        }
    }

    private View buildMedalRow(String icon, String title, String sub, boolean earned) {
        LinearLayout row = UiKit.row(requireContext());
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(UiKit.dp(requireContext(), 12), UiKit.dp(requireContext(), 12), UiKit.dp(requireContext(), 12), UiKit.dp(requireContext(), 12));
        UiKit.applyCardBg(row, requireContext(), earned ? R.color.orange_bg : R.color.bg_card_muted, earned ? R.color.orange_border : R.color.border_muted);
        row.setLayoutParams(UiKit.marginParams(requireContext(), 0, 8));

        TextView badge = UiKit.text(requireContext(), icon, 18f, earned ? R.color.orange_text : R.color.text_faint, true);
        badge.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams badgeLp = new LinearLayout.LayoutParams(UiKit.dp(requireContext(), 40), UiKit.dp(requireContext(), 40));
        badge.setLayoutParams(badgeLp);

        LinearLayout col = UiKit.column(requireContext());
        LinearLayout.LayoutParams colLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        colLp.setMarginStart(UiKit.dp(requireContext(), 10));
        col.setLayoutParams(colLp);
        col.addView(UiKit.text(requireContext(), title, 13.5f, R.color.text_primary, true));
        col.addView(UiKit.text(requireContext(), sub, 11f, R.color.text_muted, false));

        row.addView(badge);
        row.addView(col);
        return row;
    }

    @Override
    protected String entryTip() {
        return "ستاره‌هایت را جمع کن تا کلاه تازه برایم بخری!";
    }
}
