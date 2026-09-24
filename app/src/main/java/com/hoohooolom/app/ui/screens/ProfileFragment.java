package com.hoohooolom.app.ui.screens;

import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;

import com.hoohooolom.app.R;
import com.hoohooolom.app.data.AppState;
import com.hoohooolom.app.data.Book;
import com.hoohooolom.app.data.QuizResult;
import com.hoohooolom.app.ui.BaseFragment;
import com.hoohooolom.app.ui.UiKit;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static com.hoohooolom.app.data.PersianDigits.fa;

public class ProfileFragment extends BaseFragment {
    private static final String[] WEEKDAYS = {"ش", "ی", "د", "س", "چ", "پ", "ج"};

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_profile, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        AppState s = state();

        TextView nameView = view.findViewById(R.id.profile_name);
        nameView.setText(s.learnerName == null || s.learnerName.isEmpty() ? "نام خودت را بنویس" : s.learnerName);
        nameView.setOnClickListener(v -> showRenameDialog(nameView, s));

        ((TextView) view.findViewById(R.id.profile_hat)).setText("کلاه: " + s.hat);

        LinearLayout statsRow = view.findViewById(R.id.profile_stats_row);
        statsRow.removeAllViews();
        statsRow.addView(statTile(fa(s.stars), "ستاره"));
        statsRow.addView(statTile(fa(s.taughtSection + 1), "بخشِ کلاس"));
        statsRow.addView(statTile(fa(s.streak), "روز پیوسته"));

        List<QuizResult> history = s.getHistory();
        LinearLayout weekBars = view.findViewById(R.id.week_bars);
        weekBars.removeAllViews();
        LocalDate today = LocalDate.now(ZoneId.systemDefault());
        int[] counts = new int[7];
        int max = 1;
        for (QuizResult r : history) {
            LocalDate d = Instant.ofEpochMilli(r.timestampMillis).atZone(ZoneId.systemDefault()).toLocalDate();
            long diff = java.time.temporal.ChronoUnit.DAYS.between(d, today);
            if (diff >= 0 && diff < 7) {
                counts[6 - (int) diff]++;
            }
        }
        for (int c : counts) max = Math.max(max, c);
        for (int i = 0; i < 7; i++) {
            weekBars.addView(buildBar(WEEKDAYS[i], counts[i], max));
        }

        LinearLayout recentList = view.findViewById(R.id.recent_list);
        recentList.removeAllViews();
        int shown = Math.min(3, history.size());
        if (shown == 0) {
            recentList.addView(UiKit.text(requireContext(), "هنوز تمرینی انجام نداده‌ای. از نقشه شروع کن!", 12.5f, R.color.text_muted, false));
        }
        for (int i = 0; i < shown; i++) {
            recentList.addView(buildRecentRow(history.get(i)));
        }
    }

    private void showRenameDialog(TextView nameView, AppState s) {
        EditText input = new EditText(requireContext());
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setText(s.learnerName);
        new AlertDialog.Builder(requireContext())
            .setTitle("اسمت چیه؟")
            .setView(input)
            .setPositiveButton("ثبت", (d, w) -> {
                s.learnerName = input.getText().toString().trim();
                nameView.setText(s.learnerName.isEmpty() ? "نام خودت را بنویس" : s.learnerName);
            })
            .setNegativeButton("بی‌خیال", null)
            .show();
    }

    private View statTile(String value, String label) {
        LinearLayout col = UiKit.column(requireContext());
        col.setGravity(Gravity.CENTER);
        col.setPadding(0, UiKit.dp(requireContext(), 10), 0, UiKit.dp(requireContext(), 10));
        UiKit.applyCardBg(col, requireContext(), R.color.teal_bg, R.color.teal_border);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMarginEnd(UiKit.dp(requireContext(), 6));
        col.setLayoutParams(lp);
        TextView v = UiKit.text(requireContext(), value, 18f, R.color.teal_dark, true);
        v.setGravity(Gravity.CENTER);
        TextView l = UiKit.text(requireContext(), label, 11f, R.color.text_muted, false);
        l.setGravity(Gravity.CENTER);
        col.addView(v);
        col.addView(l);
        return col;
    }

    private View buildBar(String label, int count, int max) {
        LinearLayout col = UiKit.column(requireContext());
        col.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        LinearLayout.LayoutParams colLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f);
        colLp.setMargins(UiKit.dp(requireContext(), 3), 0, UiKit.dp(requireContext(), 3), 0);
        col.setLayoutParams(colLp);

        View bar = new View(requireContext());
        int heightDp = 6 + Math.round((count / (float) max) * 56);
        LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(UiKit.dp(requireContext(), 999), UiKit.dp(requireContext(), heightDp));
        bar.setLayoutParams(barLp);
        bar.setBackground(UiKit.roundedBg(ContextCompat.getColor(requireContext(), count > 0 ? R.color.teal : R.color.border_card), 0, 6f, requireContext()));

        TextView label2 = UiKit.text(requireContext(), label, 10.5f, R.color.text_muted, false);
        label2.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        labelLp.topMargin = UiKit.dp(requireContext(), 4);

        col.addView(bar);
        col.addView(label2, labelLp);
        return col;
    }

    private View buildRecentRow(QuizResult r) {
        Book.Chapter ch = Book.chapter(r.chapter);
        String modeTitle = r.mode.name().equals("PRACTICE") ? "تمرین" : r.mode.name().equals("WORKSHEET") ? "کاربرگ" : "آزمون";

        LinearLayout row = UiKit.row(requireContext());
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(UiKit.dp(requireContext(), 12), UiKit.dp(requireContext(), 10), UiKit.dp(requireContext(), 12), UiKit.dp(requireContext(), 10));
        UiKit.applyCardBg(row, requireContext(), R.color.bg_card, R.color.border_green);
        row.setLayoutParams(UiKit.marginParams(requireContext(), 0, 7));

        LinearLayout col = UiKit.column(requireContext());
        LinearLayout.LayoutParams colLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        col.setLayoutParams(colLp);
        col.addView(UiKit.text(requireContext(), modeTitle + " — درس " + ch.numberFa + ": " + ch.title, 13f, R.color.text_primary, true));

        row.addView(col);
        row.addView(UiKit.text(requireContext(), fa(r.rightCount) + "/" + fa(r.total), 13f, R.color.teal_dark, true));
        return row;
    }

    @Override
    protected String entryTip() {
        return "اینجا می‌بینی تا کجا جلو رفته‌ای.";
    }
}
