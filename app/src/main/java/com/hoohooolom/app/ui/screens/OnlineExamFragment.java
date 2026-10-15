package com.hoohooolom.app.ui.screens;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.hoohooolom.app.R;
import com.hoohooolom.app.data.OnlineExam;
import com.hoohooolom.app.ui.BaseFragment;
import com.hoohooolom.app.ui.Screen;
import com.hoohooolom.app.ui.UiKit;

import java.util.ArrayList;
import java.util.List;

import static com.hoohooolom.app.data.PersianDigits.fa;

/**
 * «آزمون آنلاین» — the exam every child sits on the same evening.
 *
 * It has four faces, and which one shows is decided by the clock, not by a menu:
 *
 *   nothing announced  → says so, and says they will be told when there is one
 *   announced, not yet → sign-up, and a countdown to the evening itself
 *   the doors are open → the paper, one question at a time
 *   sat, or finished   → the mark
 *
 * The paper is answered offline once it has been fetched: every answer is written down as it is
 * tapped, so a child whose signal drops — or whose parent takes the phone away mid-question —
 * comes back to the same paper at the same question rather than to a blank one.
 */
public class OnlineExamFragment extends BaseFragment {

    private OnlineExam exam = new OnlineExam();
    private boolean loaded = false;

    private LinearLayout list;
    private TextView heading;
    private TextView subheading;

    /** One slot per question, -1 for «not answered yet». */
    private final List<Integer> answers = new ArrayList<>();
    private int at = 0;
    private boolean sitting = false;

    private final Handler ticker = new Handler(Looper.getMainLooper());
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (!isAdded()) return;
            render();
            ticker.postDelayed(this, 1000);
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_list_index, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        ((TextView) view.findViewById(R.id.screen_title)).setText("آزمون آنلاین");
        view.findViewById(R.id.chip_container).setVisibility(View.GONE);
        heading = view.findViewById(R.id.chapter_title);
        subheading = view.findViewById(R.id.chapter_subtitle);
        list = view.findViewById(R.id.list_container);

        render();
        OnlineExam.load(requireContext(), fresh -> {
            if (!isAdded()) return;
            exam = fresh;
            loaded = true;
            restoreAnswers();
            render();
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        ticker.postDelayed(tick, 1000);     // the countdown has to move on its own
    }

    @Override
    public void onPause() {
        ticker.removeCallbacks(tick);
        super.onPause();
    }

    /** Back steps through the paper before it leaves it. */
    @Override
    public boolean onBackPressed() {
        if (!sitting || at <= 0) return false;
        at--;
        render();
        return true;
    }

    // ── what is on screen ────────────────────────────────────────────────────

    private void render() {
        if (list == null || !isAdded()) return;
        list.removeAllViews();
        long now = System.currentTimeMillis();

        if (!loaded) {
            heading.setText("آزمون آنلاین");
            subheading.setText("در حال گرفتن خبرِ آزمون…");
            return;
        }
        if (!exam.announced()) {
            showNothingPlanned();
            return;
        }
        if (done()) {
            showMark();
            return;
        }
        if (sitting) {
            showQuestion(now);
            return;
        }
        showDoor(now);
    }

    private void showNothingPlanned() {
        heading.setText("هنوز آزمونی اعلام نشده");
        subheading.setText("آزمون آنلاین نزدیکِ امتحانات برگزار می‌شود.");
        list.addView(card("⏳ فعلاً خبری نیست",
            "وقتی آزمونِ تازه‌ای گذاشته شود، همین‌جا و با یک اعلان به تو خبر می‌دهیم. تا آن موقع "
                + "می‌توانی با آزمون‌های خودِ برنامه تمرین کنی.", R.color.border_card));
        list.addView(link("رفتن به آزمون‌های تمرینی", () -> nav().go(Screen.EXAM_INDEX)));
    }

    /** Before the evening: what it is, when it is, and the sign-up. */
    private void showDoor(long now) {
        boolean signedUp = state().getBoolean(exam.key() + "_registered", false);
        heading.setText("🏆 " + exam.title);
        subheading.setText(exam.description.isEmpty()
            ? "همراه با بچه‌های سراسر کشور، در یک شب." : exam.description);

        StringBuilder when = new StringBuilder();
        if (!exam.dateFa.isEmpty()) when.append("📅 ").append(exam.dateFa);
        if (!exam.timeFa.isEmpty()) {
            if (when.length() > 0) when.append("\n");
            when.append("⏰ ساعت ").append(exam.timeFa);
        }
        when.append(when.length() > 0 ? "\n" : "")
            .append("📝 ").append(fa(exam.questions.size())).append(" سؤال، ")
            .append(fa(exam.durationMinutes)).append(" دقیقه");
        list.addView(card("زمانِ آزمون", when.toString(), R.color.orange_border));

        if (exam.over(now)) {
            list.addView(card("این آزمون تمام شد",
                "وقتش گذشت. منتظرِ آزمونِ بعدی باش — خبرش را می‌دهیم.", R.color.border_card));
            return;
        }

        if (exam.canStart(now)) {
            if (!signedUp) {
                list.addView(card("هنوز ثبت‌نام نکرده‌ای",
                    "همین حالا هم می‌شود وارد شد؛ آزمون باز است.", R.color.teal_border));
            }
            list.addView(button("🚀 ورود و شروع آزمون", R.color.orange, () -> {
                state().setBoolean(exam.key() + "_registered", true);
                sitting = true;
                at = firstUnanswered();
                render();
            }));
            return;
        }

        long left = exam.startsAt - now;
        if (left > 0) {
            list.addView(card("⏳ تا شروعِ آزمون", remaining(left), R.color.teal_border));
        }

        if (signedUp) {
            list.addView(card("✓ ثبت‌نامت انجام شد",
                "سرِ ساعت همین صفحه را باز کن؛ دکمه‌ی شروع همین‌جا سبز می‌شود.", R.color.teal_border));
        } else if (exam.registrationOpen(now)) {
            list.addView(button("✏️ ثبت‌نام در آزمون", R.color.teal, () -> {
                state().setBoolean(exam.key() + "_registered", true);
                Toast.makeText(requireContext(), "ثبت‌نامت انجام شد!", Toast.LENGTH_SHORT).show();
                render();
            }));
        } else {
            list.addView(card("ثبت‌نام هنوز باز نشده",
                "کمی مانده. همین صفحه را سر بزن.", R.color.border_card));
        }
    }

    /** One question, its choices, and a way backwards and forwards through the paper. */
    private void showQuestion(long now) {
        if (exam.startsAt > 0 && now >= exam.endsAt()) {    // the clock ran out mid-question
            hand();
            return;
        }
        if (at < 0 || at >= exam.questions.size()) at = 0;
        OnlineExam.Question q = exam.questions.get(at);

        heading.setText("سؤال " + fa(at + 1) + " از " + fa(exam.questions.size()));
        subheading.setText(exam.startsAt > 0
            ? "⏳ " + remaining(exam.endsAt() - now) + " تا پایان" : exam.title);

        list.addView(card(q.text, "", R.color.teal_border));

        int chosen = answers.get(at);
        for (int i = 0; i < q.options.size(); i++) {
            final int option = i;
            boolean picked = chosen == option;
            TextView row = UiKit.text(requireContext(), fa(i + 1) + ") " + q.options.get(i), 15f,
                picked ? R.color.white : R.color.text_primary, true);
            row.setPadding(UiKit.dp(requireContext(), 16), UiKit.dp(requireContext(), 14),
                UiKit.dp(requireContext(), 16), UiKit.dp(requireContext(), 14));
            row.setBackground(UiKit.roundedBg(
                ContextCompat.getColor(requireContext(), picked ? R.color.teal : R.color.bg_card),
                ContextCompat.getColor(requireContext(), R.color.border_card), 12f, requireContext()));
            row.setLayoutParams(UiKit.marginParams(requireContext(), 0, 8));
            row.setOnClickListener(v -> {
                answers.set(at, option);
                keepAnswers();
                render();
            });
            UiKit.tapSound(row);
            list.addView(row);
        }

        LinearLayout steps = UiKit.column(requireContext());
        steps.setOrientation(LinearLayout.HORIZONTAL);
        steps.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        steps.setLayoutParams(UiKit.marginParams(requireContext(), 12, 0));
        if (at > 0) steps.addView(step("‹ سؤال قبل", () -> { at--; render(); }));
        if (at < exam.questions.size() - 1) {
            steps.addView(step("سؤال بعد ›", () -> { at++; render(); }));
        }
        list.addView(steps);

        int answered = 0;
        for (int a : answers) if (a >= 0) answered++;
        list.addView(card("پاسخ‌داده: " + fa(answered) + " از " + fa(exam.questions.size()),
            answered < exam.questions.size()
                ? "می‌توانی برگردی و سؤال‌های بی‌جواب را پر کنی." : "همه را جواب دادی.",
            R.color.border_card));
        list.addView(button("پایان آزمون و ثبت", R.color.pink_dark, this::hand));
    }

    private View step(String label, Runnable what) {
        TextView button = UiKit.text(requireContext(), label, 14f, R.color.text_primary, true);
        button.setGravity(Gravity.CENTER);
        int pad = UiKit.dp(requireContext(), 13);
        button.setPadding(pad, pad, pad, pad);
        UiKit.applyCardBg(button, requireContext(), R.color.bg_card, R.color.border_card);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMarginEnd(UiKit.dp(requireContext(), 6));
        button.setLayoutParams(lp);
        button.setOnClickListener(v -> what.run());
        UiKit.tapSound(button);
        return button;
    }

    /** The mark, and what the right answers were. */
    private void showMark() {
        int score = state().getInt(exam.key() + "_score", 0);
        int total = exam.totalPoints();
        heading.setText("🌟 کارنامه‌ی آزمون آنلاین");
        subheading.setText(exam.title);

        TextView badge = UiKit.text(requireContext(),
            "امتیاز تو: " + fa(score) + " از " + fa(total), 17f, R.color.white, true);
        badge.setGravity(Gravity.CENTER);
        int pad = UiKit.dp(requireContext(), 20);
        badge.setPadding(pad, pad, pad, pad);
        badge.setBackground(UiKit.roundedBg(
            ContextCompat.getColor(requireContext(), R.color.teal), 0, 16f, requireContext()));
        badge.setLayoutParams(UiKit.marginParams(requireContext(), 0, 12));
        list.addView(badge);

        for (int i = 0; i < exam.questions.size(); i++) {
            OnlineExam.Question q = exam.questions.get(i);
            int mine = i < answers.size() ? answers.get(i) : -1;
            boolean right = mine == q.correctIndex;
            String body = "پاسخ درست: " + q.options.get(
                Math.max(0, Math.min(q.correctIndex, q.options.size() - 1)));
            if (!right) {
                body += "\nپاسخ تو: " + (mine >= 0 && mine < q.options.size()
                    ? q.options.get(mine) : "بی‌جواب");
            }
            if (!q.explanation.isEmpty()) body += "\n" + q.explanation;
            list.addView(card((right ? "✓ " : "✗ ") + "سؤال " + fa(i + 1) + " — " + q.text, body,
                right ? R.color.teal_border : R.color.orange_border));
        }
        list.addView(link("برگرد به آزمون‌ها", () -> nav().go(Screen.EXAM_INDEX)));
    }

    // ── keeping the paper ────────────────────────────────────────────────────

    private boolean done() {
        return state().getBoolean(exam.key() + "_done", false);
    }

    private void hand() {
        int score = 0;
        for (int i = 0; i < exam.questions.size() && i < answers.size(); i++) {
            if (answers.get(i) == exam.questions.get(i).correctIndex) {
                score += exam.questions.get(i).points;
            }
        }
        state().setInt(exam.key() + "_score", score);
        state().setBoolean(exam.key() + "_done", true);
        sitting = false;
        render();
    }

    private int firstUnanswered() {
        for (int i = 0; i < answers.size(); i++) if (answers.get(i) < 0) return i;
        return 0;
    }

    /** The answers so far, as «2,0,-1,3» — small enough to write on every tap. */
    private void keepAnswers() {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < answers.size(); i++) {
            if (i > 0) out.append(',');
            out.append(answers.get(i));
        }
        state().setString(exam.key() + "_answers", out.toString());
    }

    private void restoreAnswers() {
        answers.clear();
        for (int i = 0; i < exam.questions.size(); i++) answers.add(-1);
        String saved = state().getString(exam.key() + "_answers", "");
        if (saved.isEmpty()) return;
        String[] parts = saved.split(",");
        for (int i = 0; i < parts.length && i < answers.size(); i++) {
            try {
                answers.set(i, Integer.parseInt(parts[i].trim()));
            } catch (NumberFormatException ignored) {
                // a saved answer we cannot read is simply an unanswered question
            }
        }
    }

    // ── small pieces ─────────────────────────────────────────────────────────

    /** «۲ روز و ۴ ساعت», «۱۲ دقیقه و ۵ ثانیه» — however far away the thing is. */
    private static String remaining(long millis) {
        long seconds = Math.max(0, millis / 1000);
        long days = seconds / 86400;
        long hours = (seconds % 86400) / 3600;
        long minutes = (seconds % 3600) / 60;
        if (days > 0) return fa(days) + " روز و " + fa(hours) + " ساعت";
        if (hours > 0) return fa(hours) + " ساعت و " + fa(minutes) + " دقیقه";
        return fa(minutes) + " دقیقه و " + fa(seconds % 60) + " ثانیه";
    }

    private View card(String title, String body, int borderRes) {
        LinearLayout card = UiKit.column(requireContext());
        int pad = UiKit.dp(requireContext(), 16);
        card.setPadding(pad, pad, pad, pad);
        UiKit.applyCardBg(card, requireContext(), R.color.bg_card, borderRes);
        card.setLayoutParams(UiKit.marginParams(requireContext(), 0, 10));
        TextView head = UiKit.text(requireContext(), title, 14.5f, R.color.text_primary, true);
        head.setLineSpacing(UiKit.dp(requireContext(), 4), 1f);
        card.addView(head);
        if (!body.isEmpty()) {
            TextView text = UiKit.text(requireContext(), body, 12.5f, R.color.text_muted, false);
            text.setLineSpacing(UiKit.dp(requireContext(), 5), 1f);
            card.addView(text, UiKit.marginParams(requireContext(), 6, 0));
        }
        return card;
    }

    private View button(String label, int colorRes, Runnable what) {
        TextView button = UiKit.primaryButton(requireContext(), label,
            ContextCompat.getColor(requireContext(), colorRes), R.color.white);
        button.setLayoutParams(UiKit.marginParams(requireContext(), 6, 8));
        button.setOnClickListener(v -> what.run());
        UiKit.tapSound(button);
        return button;
    }

    private View link(String label, Runnable what) {
        TextView link = UiKit.text(requireContext(), label, 13.5f, R.color.teal_dark, true);
        link.setGravity(Gravity.CENTER);
        int pad = UiKit.dp(requireContext(), 14);
        link.setPadding(0, pad, 0, pad);
        UiKit.applyCardBg(link, requireContext(), R.color.bg_card, R.color.teal_border);
        link.setLayoutParams(UiKit.marginParams(requireContext(), 6, 8));
        link.setOnClickListener(v -> what.run());
        UiKit.tapSound(link);
        return link;
    }

    @Override
    protected String entryTip() {
        return "اینجا با بچه‌های سراسر کشور در یک آزمون شرکت می‌کنی!";
    }
}
