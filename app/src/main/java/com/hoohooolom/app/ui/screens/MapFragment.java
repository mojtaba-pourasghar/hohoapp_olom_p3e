package com.hoohooolom.app.ui.screens;

import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.hoohooolom.app.R;
import com.hoohooolom.app.data.AppState;
import com.hoohooolom.app.data.Book;
import com.hoohooolom.app.data.Lessons;
import com.hoohooolom.app.data.PageLessons;
import com.hoohooolom.app.ui.BaseFragment;
import com.hoohooolom.app.ui.PathMapView;
import com.hoohooolom.app.ui.Screen;
import com.hoohooolom.app.ui.UiKit;

import static com.hoohooolom.app.data.PersianDigits.fa;

public class MapFragment extends BaseFragment {

    /**
     * {top%, left%} stops along the trail, bottom to top, zig-zagging. A درس has between one and
     * four sections, so the stops are spread over the height for however many there are, with
     * the exam always last at the top.
     */
    private static float[] nodePosition(int i, int total) {
        float top = 80f - (80f - 16f) * i / Math.max(1, total - 1);
        float left = i == total - 1 ? 44f : (i % 2 == 0 ? 70f : 40f);
        return new float[]{top, left};
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_map, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        view.findViewById(R.id.btn_profile).setOnClickListener(v -> nav().go(Screen.PROFILE));
        view.findViewById(R.id.btn_parent).setOnClickListener(v -> nav().goParent());
        view.findViewById(R.id.see_chapters).setOnClickListener(v -> nav().go(Screen.CHAPTERS));

        render(view, state().mapChapter());
    }

    /** Draws the whole screen for one chapter. Tapping another chapter chip calls it again. */
    private void render(View view, int chapterIndex) {
        AppState s = state();
        Book.Chapter ch = Book.chapter(chapterIndex);
        boolean isTaughtChapter = chapterIndex == s.taughtChapter;

        buildChapterRow(view, chapterIndex);
        buildStarRow(view, s);

        int sectionCount = ch.sections.size();
        int doneSections = 0;
        for (int i = 0; i < ch.sections.size(); i++) {
            if (s.isSectionLessonDone(ch.index, i)) doneSections++;
        }

        String name = s.learnerName == null || s.learnerName.isEmpty() ? "قهرمان کوچولو" : s.learnerName;
        ((TextView) view.findViewById(R.id.map_greeting)).setText(isTaughtChapter
            ? "سلام " + name + "! کلاس تا «" + ch.sections.get(Math.min(s.taughtSection, sectionCount - 1)) + "» رسیده؛ اما هر بخشی را بخواهی باز است."
            : "درس " + ch.numberFa + ": " + ch.title + " — هر وقت بخواهی می‌توانی همین‌جا شروع کنی.");
        ((TextView) view.findViewById(R.id.map_subtitle)).setText(
            "درس " + ch.numberFa + ": " + ch.title + " · صفحه‌های " + fa(ch.firstPage) + " تا " + fa(ch.lastPage)
                + " · " + fa(doneSections) + " بخش از " + fa(sectionCount) + " را کار کرده‌ای");
        ((ProgressBar) view.findViewById(R.id.map_progress))
            .setProgress(Math.round((doneSections / (float) sectionCount) * 100));

        PathMapView map = view.findViewById(R.id.path_map);
        map.clearNodes();
        for (int i = 0; i < sectionCount; i++) {
            float[] at = nodePosition(i, sectionCount + 1);
            map.addNode(buildSectionNode(ch, i, s, isTaughtChapter), at[0], at[1]);
        }
        float[] examAt = nodePosition(sectionCount, sectionCount + 1);
        map.addNode(buildExamNode(ch), examAt[0], examAt[1]);
    }

    /** One chip per chapter. The chosen one is filled in; the taught one carries a star. */
    private void buildChapterRow(View view, int chosen) {
        LinearLayout row = view.findViewById(R.id.map_chapter_row);
        if (row == null) return;              // older layouts without the picker
        row.removeAllViews();
        for (int i = 0; i < Book.CHAPTERS.size(); i++) {
            Book.Chapter c = Book.CHAPTERS.get(i);
            boolean on = i == chosen;
            String label = "درس " + c.numberFa + (i == state().taughtChapter ? " ★" : "");
            TextView chip = UiKit.chip(requireContext(), label,
                color(on ? R.color.teal : R.color.bg_card),
                color(on ? R.color.teal : R.color.teal_light),
                color(on ? R.color.white : R.color.text_primary));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMarginEnd(UiKit.dp(requireContext(), 7));
            final int pick = i;
            chip.setOnClickListener(v -> {
                state().saveMapChapter(pick);
                render(view, pick);
            });
            row.addView(chip, lp);
        }
    }

    private void buildStarRow(View view, AppState s) {
        LinearLayout chipRow = view.findViewById(R.id.chip_row);
        chipRow.removeAllViews();
        TextView starsChip = UiKit.chip(requireContext(), fa(s.stars) + " ستاره",
            color(R.color.orange_bg), color(R.color.orange_border), color(R.color.orange_text));
        TextView streakChip = UiKit.chip(requireContext(), fa(s.streak) + " روز",
            color(R.color.pink_bg), color(R.color.pink_border), color(R.color.pink_dark));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMarginEnd(UiKit.dp(requireContext(), 6));
        chipRow.addView(starsChip, lp);
        chipRow.addView(streakChip, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
    }

    private int color(int res) {
        return ContextCompat.getColor(requireContext(), res);
    }

    private View buildSectionNode(Book.Chapter ch, int i, AppState s, boolean isTaughtChapter) {
        // nothing on the trail is locked; the orange node is only where the class has got to
        boolean active = isTaughtChapter && i == Math.min(s.taughtSection, ch.sections.size() - 1);
        boolean childDone = s.isSectionLessonDone(ch.index, i);

        int circleColor = active ? R.color.orange : childDone ? R.color.teal : R.color.teal_light;
        String badge = active ? "اینجا" : childDone ? "✓" : "برو";
        int size = active ? 88 : 68;

        LinearLayout node = UiKit.column(requireContext());
        node.setGravity(Gravity.CENTER_HORIZONTAL);

        TextView circle = UiKit.text(requireContext(), badge, active ? 13f : 14f, R.color.white, true);
        circle.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams circleLp = new LinearLayout.LayoutParams(UiKit.dp(requireContext(), size), UiKit.dp(requireContext(), size));
        circle.setLayoutParams(circleLp);
        circle.setBackground(UiKit.ringedCircle(color(circleColor), Color.WHITE, UiKit.dp(requireContext(), 5)));
        circle.setElevation(UiKit.dp(requireContext(), 4));

        TextView label = UiKit.text(requireContext(), ch.sections.get(i), 11.5f, R.color.text_primary, true);
        label.setGravity(Gravity.CENTER);
        label.setMaxWidth(UiKit.dp(requireContext(), 150));
        label.setPadding(UiKit.dp(requireContext(), 9), UiKit.dp(requireContext(), 4), UiKit.dp(requireContext(), 9), UiKit.dp(requireContext(), 4));
        label.setBackground(UiKit.roundedBg(Color.parseColor("#F2FFFFFF"), 0, 9f, requireContext()));
        LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        labelLp.topMargin = UiKit.dp(requireContext(), 7);

        node.addView(circle);
        node.addView(label, labelLp);

        node.setOnClickListener(v -> {
            Bundle args = new Bundle();
            args.putInt("chapter", ch.index);
            // the book comes first: open the page of this section the child has not finished yet
            java.util.List<Integer> pages = PageLessons.pagesOfSection(ch.index, i);
            int nextPage = 0;
            for (int page : pages) {
                if (!state().isPageDone(page)) { nextPage = page; break; }
            }
            if (nextPage == 0 && !pages.isEmpty()) nextPage = pages.get(0);

            if (nextPage > 0) {
                args.putInt("page", nextPage);
                nav().go(Screen.LESSON, args);
            } else if (Lessons.has(ch.index, i)) {
                args.putInt("section", i);
                nav().go(Screen.LESSON, args);
            } else {
                args.putString("mode", "PRACTICE");
                args.putInt("option", i);
                nav().go(Screen.QUIZ, args);
            }
        });
        return node;
    }

    private View buildExamNode(Book.Chapter ch) {
        LinearLayout node = UiKit.column(requireContext());
        node.setGravity(Gravity.CENTER_HORIZONTAL);

        TextView circle = UiKit.text(requireContext(), "آزمون", 12f, R.color.white, true);
        circle.setGravity(Gravity.CENTER);
        circle.setLayoutParams(new LinearLayout.LayoutParams(UiKit.dp(requireContext(), 72), UiKit.dp(requireContext(), 72)));
        circle.setBackground(UiKit.ringedCircle(color(R.color.pink), Color.WHITE, UiKit.dp(requireContext(), 5)));
        circle.setElevation(UiKit.dp(requireContext(), 4));

        TextView label = UiKit.text(requireContext(), "آزمون درس " + ch.numberFa, 11.5f, R.color.text_primary, true);
        label.setGravity(Gravity.CENTER);
        label.setPadding(UiKit.dp(requireContext(), 9), UiKit.dp(requireContext(), 4), UiKit.dp(requireContext(), 9), UiKit.dp(requireContext(), 4));
        label.setBackground(UiKit.roundedBg(Color.parseColor("#F2FFFFFF"), 0, 9f, requireContext()));
        LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        labelLp.topMargin = UiKit.dp(requireContext(), 7);

        node.addView(circle);
        node.addView(label, labelLp);
        node.setOnClickListener(v -> {
            Bundle args = new Bundle();
            args.putInt("chapter", ch.index);
            nav().go(Screen.EXAM_INDEX, args);
        });
        return node;
    }

    @Override
    protected String entryTip() {
        return "بالای صفحه درس را عوض کن، بعد روی هر دایره‌ای که خواستی بزن. هیچ‌کدام قفل نیست!";
    }
}
