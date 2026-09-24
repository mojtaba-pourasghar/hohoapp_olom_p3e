package com.hoohooolom.app.ui.screens;

import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;

import com.hoohooolom.app.R;
import com.hoohooolom.app.data.AppState;
import com.hoohooolom.app.data.Book;
import com.hoohooolom.app.data.QuizResult;
import com.hoohooolom.app.data.QuizSession;
import com.hoohooolom.app.ui.BaseFragment;
import com.hoohooolom.app.ui.Screen;
import com.hoohooolom.app.ui.UiKit;

import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static com.hoohooolom.app.data.PersianDigits.fa;

public class ParentPanelFragment extends BaseFragment {
    private static final String[] SETTING_LABELS = {
        "خواندن صوتی سؤال‌ها", "صدای دکمه‌ها و تشویق‌ها", "موسیقی ملایم پس‌زمینه",
        "نمایش تولتیپ هوهو", "پرواز هوهو روی صفحه"
    };

    private int browseChapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_parent_panel, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (!state().parentUnlockedThisSession) {
            nav().go(Screen.PARENT_GATE);
            return;
        }
        browseChapter = state().taughtChapter;

        view.findViewById(R.id.panel_lock).setOnClickListener(v -> {
            state().parentUnlockedThisSession = false;
            nav().go(Screen.MAP);
        });
        view.findViewById(R.id.panel_change_pin).setOnClickListener(v -> {
            Bundle args = new Bundle();
            args.putBoolean("forceSetup", true);
            nav().go(Screen.PARENT_GATE, args);
        });

        render(view);
    }

    private void render(View view) {
        AppState s = state();
        List<QuizResult> history = s.getHistory();

        LinearLayout statsRow = view.findViewById(R.id.panel_stats_row);
        statsRow.removeAllViews();
        LocalDate today = LocalDate.now(ZoneId.systemDefault());
        long weekCount = history.stream().filter(r -> {
            LocalDate d = Instant.ofEpochMilli(r.timestampMillis).atZone(ZoneId.systemDefault()).toLocalDate();
            return java.time.temporal.ChronoUnit.DAYS.between(d, today) < 7;
        }).count();
        int totalRight = history.stream().mapToInt(r -> r.rightCount).sum();
        int totalAll = history.stream().mapToInt(r -> r.total).sum();
        String pctText = totalAll == 0 ? "—" : fa(Math.round((totalRight / (float) totalAll) * 100)) + "٪";

        statsRow.addView(statTile(fa(weekCount) + " بار", "تمرین این هفته"));
        statsRow.addView(statTile(pctText, "پاسخ درست"));
        statsRow.addView(statTile(fa(s.streak) + " روز", "زنجیره"));

        FrameLayout chipHolder = view.findViewById(R.id.panel_chapter_chips);
        chipHolder.removeAllViews();
        chipHolder.addView(ScreenHelpers.buildChapterChipRow(requireContext(), s, s.taughtChapter, i -> {
            s.setTeacherPace(i, 0);
            browseChapter = i;
            render(view);
        }));

        Book.Chapter ch = Book.chapter(s.taughtChapter);
        LinearLayout sectionRows = view.findViewById(R.id.panel_section_rows);
        sectionRows.removeAllViews();
        for (int i = 0; i < ch.sections.size(); i++) {
            sectionRows.addView(buildSectionRow(ch, i, s));
        }

        LinearLayout insights = view.findViewById(R.id.panel_insights);
        insights.removeAllViews();
        for (String tip : buildInsights(history)) {
            insights.addView(insightCard(tip));
        }

        LinearLayout settings = view.findViewById(R.id.panel_settings);
        settings.removeAllViews();
        for (int i = 0; i < SETTING_LABELS.length; i++) {
            settings.addView(buildSettingRow(SETTING_LABELS[i], i, s));
        }

        LinearLayout results = view.findViewById(R.id.panel_results);
        results.removeAllViews();
        int shown = Math.min(8, history.size());
        if (shown == 0) {
            results.addView(UiKit.text(requireContext(), "هنوز نتیجه‌ای ثبت نشده است.", 12.5f, R.color.text_muted, false));
        }
        for (int i = 0; i < shown; i++) {
            results.addView(buildResultRow(history.get(i)));
        }
    }

    private View statTile(String value, String label) {
        LinearLayout col = UiKit.column(requireContext());
        col.setGravity(Gravity.CENTER);
        col.setPadding(0, UiKit.dp(requireContext(), 10), 0, UiKit.dp(requireContext(), 10));
        UiKit.applyCardBg(col, requireContext(), R.color.teal_bg, R.color.teal_border);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMarginEnd(UiKit.dp(requireContext(), 6));
        col.setLayoutParams(lp);
        TextView v = UiKit.text(requireContext(), value, 15f, R.color.teal_dark, true);
        v.setGravity(Gravity.CENTER);
        TextView l = UiKit.text(requireContext(), label, 10.5f, R.color.text_muted, false);
        l.setGravity(Gravity.CENTER);
        col.addView(v);
        col.addView(l);
        return col;
    }

    /**
     * One section in the «teacher's pace» list. This is a bookmark the parent moves, not a gate:
     * every section is open to the child either way, so no row is drawn greyed out — a faded row
     * reads as locked even when it is not.
     */
    private View buildSectionRow(Book.Chapter ch, int i, AppState s) {
        boolean taught = i <= s.taughtSection;
        LinearLayout row = UiKit.row(requireContext());
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(UiKit.dp(requireContext(), 12), UiKit.dp(requireContext(), 11), UiKit.dp(requireContext(), 12), UiKit.dp(requireContext(), 11));
        UiKit.applyCardBg(row, requireContext(), R.color.bg_card, taught ? R.color.teal_border : R.color.border_card);
        row.setLayoutParams(UiKit.marginParams(requireContext(), 0, 6));

        TextView label = UiKit.text(requireContext(), fa(i + 1) + ". " + ch.sections.get(i), 13f, R.color.text_primary, true);
        LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        label.setLayoutParams(labelLp);

        TextView mark = UiKit.text(requireContext(), taught ? "درس داده شد" : "هنوز نه — ولی باز است",
            11.5f, taught ? R.color.teal_dark : R.color.text_muted, true);

        row.addView(label);
        row.addView(mark);
        row.setOnClickListener(v -> {
            s.setTeacherPace(ch.index, i);
            render(getView());
        });
        return row;
    }

    private List<String> buildInsights(List<QuizResult> history) {
        List<String> tips = new ArrayList<>();
        if (history.isEmpty()) {
            tips.add("هنوز داده‌ی کافی برای تحلیل نیست. بعد از چند تمرین، اینجا نکته می‌بینید.");
            return tips;
        }
        Map<Integer, int[]> byChapter = new HashMap<>(); // chapter -> [right, total]
        for (QuizResult r : history) {
            int[] agg = byChapter.computeIfAbsent(r.chapter, k -> new int[2]);
            agg[0] += r.rightCount;
            agg[1] += r.total;
        }
        int worstChapter = -1;
        float worstRatio = 2f;
        for (Map.Entry<Integer, int[]> e : byChapter.entrySet()) {
            if (e.getValue()[1] < 5) continue; // need a minimum sample
            float ratio = e.getValue()[0] / (float) e.getValue()[1];
            if (ratio < worstRatio) {
                worstRatio = ratio;
                worstChapter = e.getKey();
            }
        }
        if (worstChapter >= 0 && worstRatio < 0.7f) {
            Book.Chapter ch = Book.chapter(worstChapter);
            tips.add("در درس " + ch.numberFa + " (" + ch.title + ")، " + fa(Math.round(worstRatio * 100)) + "٪ پاسخ‌ها درست بوده. پیشنهاد: کاربرگ تمرینی همین فصل را با او کار کنید.");
        } else {
            tips.add("پیشرفت خوبی دارد! می‌توانید سطح سخت‌تر کاربرگ‌ها را هم امتحان کنید.");
        }
        return tips;
    }

    private View insightCard(String text) {
        LinearLayout row = UiKit.column(requireContext());
        row.setPadding(UiKit.dp(requireContext(), 12), UiKit.dp(requireContext(), 12), UiKit.dp(requireContext(), 12), UiKit.dp(requireContext(), 12));
        UiKit.applyCardBg(row, requireContext(), R.color.orange_bg, R.color.orange_border);
        row.setLayoutParams(UiKit.marginParams(requireContext(), 0, 6));
        row.addView(UiKit.text(requireContext(), text, 12.5f, R.color.orange_text, false));
        return row;
    }

    private View buildSettingRow(String label, int index, AppState s) {
        LinearLayout row = UiKit.row(requireContext());
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(UiKit.dp(requireContext(), 12), UiKit.dp(requireContext(), 8), UiKit.dp(requireContext(), 12), UiKit.dp(requireContext(), 8));
        UiKit.applyCardBg(row, requireContext(), R.color.bg_card, R.color.border_green);
        row.setLayoutParams(UiKit.marginParams(requireContext(), 0, 6));

        TextView text = UiKit.text(requireContext(), label, 13f, R.color.text_primary, false);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        text.setLayoutParams(lp);

        Switch sw = new Switch(requireContext());
        sw.setChecked(s.settings[index]);
        sw.setOnCheckedChangeListener((btn, checked) -> {
            if (checked == s.settings[index]) return;
            s.toggleSetting(index);
            com.hoohooolom.app.tts.SoundManager sound = com.hoohooolom.app.tts.SoundManager.get();
            if (sound != null) sound.applySettings(); // music starts/stops as soon as it's flipped
        });

        row.addView(text);
        row.addView(sw);
        return row;
    }

    private View buildResultRow(QuizResult r) {
        Book.Chapter ch = Book.chapter(r.chapter);
        String modeTitle = r.mode.name().equals("PRACTICE") ? "تمرین" : r.mode.name().equals("WORKSHEET") ? "کاربرگ" : "آزمون";
        String date = new SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.US).format(new java.util.Date(r.timestampMillis));

        LinearLayout row = UiKit.row(requireContext());
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(UiKit.dp(requireContext(), 12), UiKit.dp(requireContext(), 10), UiKit.dp(requireContext(), 12), UiKit.dp(requireContext(), 10));
        UiKit.applyCardBg(row, requireContext(), R.color.bg_card, R.color.border_green);
        row.setLayoutParams(UiKit.marginParams(requireContext(), 0, 6));

        LinearLayout col = UiKit.column(requireContext());
        LinearLayout.LayoutParams colLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        col.setLayoutParams(colLp);
        col.addView(UiKit.text(requireContext(), modeTitle + " — درس " + ch.numberFa + ": " + ch.title, 13f, R.color.text_primary, true));
        col.addView(UiKit.text(requireContext(), date, 10.5f, R.color.text_faint, false));

        row.addView(col);
        row.addView(UiKit.text(requireContext(), fa(r.rightCount) + "/" + fa(r.total), 13f, R.color.teal_dark, true));
        row.setOnClickListener(v -> showResultDetail(r));
        return row;
    }

    private void showResultDetail(QuizResult r) {
        StringBuilder sb = new StringBuilder();
        for (QuizSession.LogEntry e : r.log) {
            sb.append(e.correct ? "✓ " : "✕ ").append(e.question).append('\n');
            sb.append("   پاسخ فرزند شما: ").append(e.yourAnswer).append(" · پاسخ درست: ").append(e.correctAnswer).append("\n\n");
        }
        new AlertDialog.Builder(requireContext())
            .setTitle("جزئیات پاسخ‌ها")
            .setMessage(sb.length() == 0 ? "جزئیاتی ثبت نشده است." : sb.toString())
            .setPositiveButton("باشه", (d, w) -> d.dismiss())
            .show();
    }

    @Override
    protected String entryTip() {
        return "اینجا بابا و مامان تنظیم می‌کنند که معلم تا کجا درس داده.";
    }
}
