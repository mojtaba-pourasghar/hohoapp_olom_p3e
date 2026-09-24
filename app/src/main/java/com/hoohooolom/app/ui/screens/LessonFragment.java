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
import com.hoohooolom.app.data.Book;
import com.hoohooolom.app.data.Lessons;
import com.hoohooolom.app.data.PageLessons;
import com.hoohooolom.app.data.LessonKind;
import com.hoohooolom.app.data.LessonScript;
import com.hoohooolom.app.data.LessonStep;
import com.hoohooolom.app.tts.LessonAudio;
import com.hoohooolom.app.tts.SoundManager;
import com.hoohooolom.app.ui.BaseFragment;
import com.hoohooolom.app.ui.FeedbackDialog;
import com.hoohooolom.app.ui.LessonStageView;
import com.hoohooolom.app.ui.Screen;
import com.hoohooolom.app.ui.UiKit;

import static com.hoohooolom.app.data.PersianDigits.fa;

/**
 * The lesson itself: هوهو narrates each step while a matching animation plays, then — once the
 * explanation is finished — the child gets their turn. Narration comes from a recorded file when
 * one exists in res/raw, otherwise from the device's Persian voice.
 */
public class LessonFragment extends BaseFragment {

    private int chapter, section;
    /** > 0 when this is a lesson for one page of the printed book. */
    private int page;
    private LessonScript script;
    private int stepIndex = 0;
    private final StringBuilder typed = new StringBuilder();
    private boolean answered = false;
    private boolean narrating = false;
    private int starsThisLesson = 0;

    private LessonStageView stage;
    private View rootView;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_lesson, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        rootView = view;
        Bundle args = getArguments();
        chapter = args != null ? args.getInt("chapter", 0) : 0;
        section = args != null ? args.getInt("section", 0) : 0;
        page = args != null ? args.getInt("page", 0) : 0;
        // a page of the book, or the section's own summary lesson
        script = page > 0 ? PageLessons.forPage(page) : Lessons.forSection(chapter, section);
        // a page carries its own chapter and section, so walking into the next chapter's first
        // page keeps the header, the practice button and the saved progress on the right chapter
        if (script != null) {
            section = script.section;
            if (page > 0) chapter = script.chapter;
        }

        stage = view.findViewById(R.id.lesson_stage);
        onTap(view, R.id.lesson_back, () -> {
            LessonAudio.stop();
            nav().go(Screen.MAP);
        });

        if (script == null) {
            ((TextView) view.findViewById(R.id.lesson_caption)).setText("درسِ صوتی این بخش هنوز آماده نیست.");
            return;
        }

        Book.Chapter ch = Book.chapter(chapter);
        ((TextView) view.findViewById(R.id.lesson_title)).setText(page > 0
            ? "درس " + ch.numberFa + " · " + ch.title + " — " + ch.sections.get(section)
            : "خلاصه‌ی بخش " + fa(section + 1) + ": " + ch.sections.get(section));
        onTap(view, R.id.lesson_replay, this::togglePlayback);
        onTap(view, R.id.lesson_prev, () -> goToStep(stepIndex - 1));
        onTap(view, R.id.lesson_next, () -> goToStep(stepIndex + 1));

        // come back to the step the child had reached instead of starting the lesson over
        int saved = page > 0 ? state().pageStep(page) : state().lessonStep(chapter, section);
        stepIndex = saved > 0 && saved < script.steps.size() ? saved : 0;

        renderStep();
    }

    @Override
    public void onDestroyView() {
        LessonAudio.stop();
        if (stage != null) stage.stop();
        super.onDestroyView();
    }

    // ---------- step rendering ----------

    private void renderStep() {
        LessonStep step = script.steps.get(stepIndex);
        answered = false;
        typed.setLength(0);

        setText(R.id.lesson_step_count,
            (page > 0 ? "قسمت " + fa(PageLessons.pagesOfChapter(chapter).indexOf(page) + 1) + " (صفحه‌ی " + fa(page) + " کتاب)"
                : "مرورِ بخش") + " · گام " + fa(stepIndex + 1) + " از " + fa(script.steps.size()));
        setText(R.id.lesson_say, step.say);
        setText(R.id.lesson_caption, step.caption);

        boolean teaching = step.kind == LessonKind.TEACH || step.kind == LessonKind.DONE;
        setText(R.id.lesson_phase, teaching ? "هوهو توضیح می‌دهد" : "نوبت توست");

        View stageCard = rootView.findViewById(R.id.lesson_stage_card);
        if (stageCard != null) stageCard.setVisibility(step.hasStage() ? View.VISIBLE : View.GONE);
        if (stage != null) {
            // on the child's turn the picture they answer about is already in view
            stage.setSpec(step.stage, !teaching);
            // on a question, the picture keeps its answer to itself until the child has answered
            stage.setRevealAnswers(teaching || answered || state().parentUnlockedThisSession);
            stage.setTapListener(step.kind == LessonKind.TAP ? (x, y) -> onPageTap(step, x, y) : null);
        }

        setShown(R.id.lesson_prev, stepIndex != 0);
        setShown(R.id.lesson_next, stepIndex < script.steps.size() - 1);

        if (page > 0) state().savePageStep(page, stepIndex);
        else state().saveLessonStep(chapter, section, stepIndex);

        renderDots();
        buildContent(step);
        // on the next frame: an outgoing screen's teardown stops audio, and that would kill the
        // line that the page we just arrived at is trying to say
        rootView.post(this::narrateCurrentStep);
    }

    /** Binds a tap only when that layout actually has the control. */
    private void onTap(View root, int id, Runnable action) {
        View target = root.findViewById(id);
        if (target != null) target.setOnClickListener(v -> action.run());
    }

    private void setText(int id, String value) {
        TextView target = rootView == null ? null : rootView.findViewById(id);
        if (target != null) target.setText(value);
    }

    private void setShown(int id, boolean shown) {
        View target = rootView == null ? null : rootView.findViewById(id);
        if (target != null) target.setVisibility(shown ? View.VISIBLE : View.INVISIBLE);
    }

    /** ▶ starts the line again; ⏸ stops both the voice and the moving picture. */
    private void togglePlayback() {
        if (narrating) {
            LessonAudio.stop();
            if (stage != null) stage.stop();
            narrating = false;
            mascot().showBubble(script.steps.get(stepIndex).say, false);
            updateContinueEnabled();
            updateReplayButton();
        } else {
            narrateCurrentStep();
        }
    }

    private void updateReplayButton() {
        TextView replay = rootView == null ? null : rootView.findViewById(R.id.lesson_replay);
        if (replay != null) replay.setText(narrating ? "⏸" : "▶");
    }

    /** Free movement through the lesson, in either direction, at any time. */
    private void goToStep(int target) {
        if (target < 0 || target >= script.steps.size()) return;
        LessonAudio.stop();
        stepIndex = target;
        renderStep();
    }

    private void renderDots() {
        LinearLayout dots = rootView.findViewById(R.id.lesson_dots);
        dots.removeAllViews();
        for (int i = 0; i < script.steps.size(); i++) {
            View dot = new View(requireContext());
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(UiKit.dp(requireContext(), 12), UiKit.dp(requireContext(), 6));
            lp.setMargins(UiKit.dp(requireContext(), 2), 0, UiKit.dp(requireContext(), 2), 0);
            dot.setLayoutParams(lp);
            int colorRes = i < stepIndex ? R.color.teal : i == stepIndex ? R.color.orange : R.color.border_card;
            dot.setBackground(UiKit.roundedBg(ContextCompat.getColor(requireContext(), colorRes), 0, 999f, requireContext()));
            int target = i;
            dot.setOnClickListener(v -> goToStep(target));
            dots.addView(dot);
        }
    }

    /** Speaks the current line and runs its animation for exactly as long as the voice lasts. */
    private void narrateCurrentStep() {
        LessonStep step = script.steps.get(stepIndex);
        narrate(step.audioKey, step.say, true);
    }

    /** The extra explanation the child asked for with "یک مثال دیگر". */
    private void narrateExample() {
        LessonStep step = script.steps.get(stepIndex);
        if (!step.hasExample()) return;
        narrate(step.exampleAudioKey, step.exampleSay, false);
    }

    private void narrate(String audioKey, String text, boolean replayStage) {
        narrating = true;
        updateContinueEnabled();
        updateReplayButton();
        mascot().showBubble(text, true);

        // fly over to the picture being explained, so the child looks where هوهو is looking
        View stageCard = rootView.findViewById(R.id.lesson_stage_card);
        if (stageCard.getVisibility() == View.VISIBLE) mascot().flyTo(stageCard);

        LessonAudio.play(requireContext(), audioKey, text, new LessonAudio.PlaybackListener() {
            @Override public void onStarted(long durationMs) {
                if (!isAdded()) return;
                if (replayStage) stage.play(durationMs);
            }
            @Override public void onFinished() {
                if (!isAdded()) return;
                narrating = false;
                mascot().showBubble(text, false);
                mascot().returnHome();
                updateContinueEnabled();
                updateReplayButton();
            }
        });
    }

    // ---------- the child's turn ----------

    private TextView continueButton;

    private void buildContent(LessonStep step) {
        LinearLayout content = rootView.findViewById(R.id.lesson_content);
        content.removeAllViews();
        continueButton = null;

        switch (step.kind) {
            case TEACH: {
                LinearLayout buttons = UiKit.row(requireContext());
                continueButton = bigButton("فهمیدم، برویم!", R.color.teal, R.color.white);
                continueButton.setOnClickListener(v -> advance());

                TextView again = bigButton("دوباره بگو", R.color.bg_card, R.color.text_primary);
                again.setBackground(UiKit.roundedBg(ContextCompat.getColor(requireContext(), R.color.bg_card),
                    ContextCompat.getColor(requireContext(), R.color.border_input), 16f, requireContext()));
                again.setOnClickListener(v -> narrateCurrentStep());

                LinearLayout.LayoutParams left = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
                left.setMarginEnd(UiKit.dp(requireContext(), 5));
                LinearLayout.LayoutParams right = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
                right.setMarginStart(UiKit.dp(requireContext(), 5));
                buttons.addView(continueButton, left);
                buttons.addView(again, right);
                content.addView(buttons);

                if (step.hasExample()) {
                    TextView example = bigButton("یک مثال دیگر بزن", R.color.orange_bg, R.color.orange_text);
                    example.setBackground(UiKit.roundedBg(
                        ContextCompat.getColor(requireContext(), R.color.orange_bg),
                        ContextCompat.getColor(requireContext(), R.color.orange_border), 16f, requireContext()));
                    example.setOnClickListener(v -> narrateExample());
                    content.addView(example, UiKit.marginParams(requireContext(), 10, 0));
                }

                updateContinueEnabled();
                break;
            }
            case DONE: {
                if (page > 0) {
                    state().markPageDone(page);
                    state().savePageStep(page, 0);
                } else {
                    state().markSectionLessonDone(chapter, section);
                    state().saveLessonStep(chapter, section, 0);
                }
                content.addView(buildDoneCard());
                break;
            }
            case MCQ: {
                content.addView(heading("جواب را انتخاب کن"));
                content.addView(revealButton());
                for (int i = 0; i < step.options.size(); i++) {
                    int idx = i;
                    TextView btn = UiKit.text(requireContext(), step.options.get(i), 24f, R.color.text_primary, true);
                    btn.setGravity(Gravity.CENTER);
                    btn.setPadding(0, UiKit.dp(requireContext(), 20), 0, UiKit.dp(requireContext(), 20));
                    btn.setBackground(UiKit.roundedBg(ContextCompat.getColor(requireContext(), R.color.bg_card),
                        ContextCompat.getColor(requireContext(), R.color.border_input), 18f, requireContext()));
                    btn.setLayoutParams(UiKit.marginParams(requireContext(), 0, 10));
                    btn.setOnClickListener(v -> {
                        if (answered) return;
                        answered = true;
                        onLessonAnswer(idx == step.correctIndex, step.why);
                    });
                    content.addView(btn);
                }
                break;
            }
            case TAP: {
                content.addView(heading("روی تصویرِ کتاب، جای درست را پیدا کن و بزن"));
                content.addView(revealButton());
                TextView note = UiKit.text(requireContext(),
                    "انگشتت را روی همان تصویری بگذار که هوهو پرسید.", 13f, R.color.text_muted, false);
                note.setGravity(Gravity.CENTER);
                content.addView(note, UiKit.marginParams(requireContext(), 0, 6));
                break;
            }
            case NUM: {
                content.addView(heading("عدد را بنویس"));
                content.addView(revealButton());

                TextView display = UiKit.text(requireContext(), "⬜", 30f, R.color.text_primary, true);
                display.setGravity(Gravity.CENTER);
                display.setPadding(0, UiKit.dp(requireContext(), 16), 0, UiKit.dp(requireContext(), 16));
                display.setBackground(ContextCompat.getDrawable(requireContext(), R.drawable.bg_answer_box));
                content.addView(display, UiKit.marginParams(requireContext(), 0, 12));

                LinearLayout keypadWrap = new LinearLayout(requireContext());
                keypadWrap.setGravity(Gravity.CENTER);
                keypadWrap.addView(UiKit.buildKeypad(requireContext(), key -> {
                    if (answered) return;
                    if (key.equals("C")) typed.setLength(0);
                    else if (key.equals("⌫")) { if (typed.length() > 0) typed.deleteCharAt(typed.length() - 1); }
                    else if (typed.length() < 5) typed.append(key);
                    display.setText(typed.length() == 0 ? "⬜" : typed.toString());
                }));
                content.addView(keypadWrap);

                TextView submit = bigButton("بفرست", R.color.orange, R.color.white);
                submit.setOnClickListener(v -> {
                    if (answered || typed.length() == 0) return;
                    answered = true;
                    onLessonAnswer(typed.toString().equals(step.answerFa), step.why);
                });
                content.addView(submit, UiKit.marginParams(requireContext(), 12, 0));
                break;
            }
        }
    }

    /** The child touched the page on a TAP step: right if it lands on one of the targets. */
    private void onPageTap(LessonStep step, float x, float y) {
        if (answered) return;
        boolean right;
        if (step.tapActors != null) {
            // a scene: right when the touch lands on one of the pictures asked for
            int hit = stage == null ? -1 : stage.actorAt(x, y);
            right = false;
            for (int a : step.tapActors) if (a == hit) right = true;
            if (!right && hit < 0) {
                // a touch on bare backdrop is not an answer yet — let the child aim again
                mascot().say("روی خودِ تصویرها بزن.");
                return;
            }
        } else {
            right = step.hits(x, y);
        }
        SoundManager sound = SoundManager.get();
        if (sound != null) sound.tap();
        if (stage != null) stage.markTap(x, y, right);
        answered = true;
        if (right && stage != null) {
            if (step.tapActors != null) stage.showActors(step.tapActors);
            else stage.showTargets(step.tapTargets);
        }
        onLessonAnswer(right, step.why);
    }

    private View buildDoneCard() {
        LinearLayout card = UiKit.column(requireContext());
        card.setGravity(Gravity.CENTER);
        card.setPadding(UiKit.dp(requireContext(), 20), UiKit.dp(requireContext(), 20), UiKit.dp(requireContext(), 20), UiKit.dp(requireContext(), 20));
        card.setBackground(UiKit.roundedBg(ContextCompat.getColor(requireContext(), R.color.orange_bg),
            ContextCompat.getColor(requireContext(), R.color.orange_border), 22f, requireContext()));

        LinearLayout diamonds = UiKit.row(requireContext());
        diamonds.setGravity(Gravity.CENTER);
        for (int i = 0; i < 3; i++) {
            View d = new View(requireContext());
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(UiKit.dp(requireContext(), 20), UiKit.dp(requireContext(), 20));
            lp.setMargins(UiKit.dp(requireContext(), 4), 0, UiKit.dp(requireContext(), 4), 0);
            d.setLayoutParams(lp);
            d.setRotation(45f);
            d.setBackground(UiKit.roundedBg(ContextCompat.getColor(requireContext(), R.color.orange), 0, 4f, requireContext()));
            diamonds.addView(d);
        }
        card.addView(diamonds);

        TextView title = UiKit.text(requireContext(), "درس تمام شد!", 18f, R.color.text_primary, true);
        title.setGravity(Gravity.CENTER);
        card.addView(title, UiKit.marginParams(requireContext(), 12, 0));

        TextView sub = UiKit.text(requireContext(),
            fa(starsThisLesson) + " ستاره گرفتی. حالا تمرین‌های همین بخش را حل کن.", 13f, R.color.text_muted, false);
        sub.setGravity(Gravity.CENTER);
        card.addView(sub, UiKit.marginParams(requireContext(), 6, 12));

        int next = page > 0 ? nextPage() : 0;
        TextView cta = bigButton(next > 0 ? "برویم قسمتِ بعد" : "تمرین‌های این بخش",
            R.color.teal, R.color.white);
        cta.setOnClickListener(v -> {
            LessonAudio.stop();
            Bundle args = new Bundle();
            args.putInt("chapter", chapter);
            if (next > 0) {
                args.putInt("page", next);
                nav().go(Screen.LESSON, args);
            } else {
                args.putString("mode", "PRACTICE");
                args.putInt("option", section);
                nav().go(Screen.QUIZ, args);
            }
        });
        card.addView(cta);

        if (next > 0) {
            TextView practice = bigButton("تمرین‌های این بخش", R.color.bg_card, R.color.text_primary);
            practice.setBackground(UiKit.roundedBg(
                ContextCompat.getColor(requireContext(), R.color.bg_card),
                ContextCompat.getColor(requireContext(), R.color.border_input), 16f, requireContext()));
            practice.setOnClickListener(v -> {
                LessonAudio.stop();
                Bundle args = new Bundle();
                args.putString("mode", "PRACTICE");
                args.putInt("chapter", chapter);
                args.putInt("option", section);
                nav().go(Screen.QUIZ, args);
            });
            card.addView(practice, UiKit.marginParams(requireContext(), 9, 0));
        }
        return card;
    }

    /** The next page of the book, across chapter borders, or 0 at the very last page. */
    private int nextPage() {
        return PageLessons.nextPage(page);
    }

    /** While هوهو is still talking, the "got it" button waits — the child listens first. */
    private void updateContinueEnabled() {
        if (continueButton == null) return;
        boolean ready = !narrating;
        continueButton.setAlpha(ready ? 1f : 0.45f);
        continueButton.setEnabled(ready);
        continueButton.setText(ready ? "فهمیدم، برویم!" : "هوهو دارد توضیح می‌دهد…");
    }

    /**
     * The answer belongs to the child until they have tried. This only opens it once a parent has
     * signed in, exactly like the answers in the quiz.
     */
    private View revealButton() {
        boolean unlocked = state().parentUnlockedThisSession;
        TextView button = UiKit.text(requireContext(),
            unlocked ? "نمایش پاسخ (باز است)" : "نمایش پاسخ — ورود والدین", 12.5f,
            R.color.pink_dark, true);
        button.setGravity(Gravity.CENTER);
        button.setPadding(0, UiKit.dp(requireContext(), 11), 0, UiKit.dp(requireContext(), 11));
        button.setBackground(ContextCompat.getDrawable(requireContext(), R.drawable.bg_help_button_pink));
        button.setLayoutParams(UiKit.marginParams(requireContext(), 0, 10));
        button.setOnClickListener(v -> {
            if (!state().parentUnlockedThisSession) {
                LessonAudio.stop();
                nav().goParent();
                return;
            }
            if (stage != null) {
                LessonStep current = script.steps.get(stepIndex);
                stage.setRevealAnswers(true);
                if (current.kind == LessonKind.TAP) {
                    if (current.tapActors != null) stage.showActors(current.tapActors);
                    else stage.showTargets(current.tapTargets);
                }
            }
        });
        UiKit.tapSound(button);
        return button;
    }

    private TextView heading(String label) {
        TextView tv = UiKit.text(requireContext(), label, 15f, R.color.text_primary, true);
        tv.setLayoutParams(UiKit.marginParams(requireContext(), 0, 10));
        return tv;
    }

    private TextView bigButton(String label, int bgColorRes, int textColorRes) {
        TextView btn = UiKit.text(requireContext(), label, 15f, textColorRes, true);
        btn.setGravity(Gravity.CENTER);
        btn.setPadding(0, UiKit.dp(requireContext(), 15), 0, UiKit.dp(requireContext(), 15));
        btn.setBackground(UiKit.roundedBg(ContextCompat.getColor(requireContext(), bgColorRes), 0, 16f, requireContext()));
        btn.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        UiKit.tapSound(btn);
        return btn;
    }

    private void onLessonAnswer(boolean correct, String why) {
        LessonAudio.stop();
        AppState s = state();
        if (correct && stage != null) stage.setRevealAnswers(true);
        if (correct) {
            s.addStars(1);
            starsThisLesson++;
            mascot().celebrate(why);
            FeedbackDialog.show(requireContext(), true, why, "گام بعد", this::advance);
        } else {
            // «why» explains the right answer, so a wrong try gets a nudge instead of the answer
            String nudge = script.steps.get(stepIndex).kind == LessonKind.TAP
                ? "این نبود. یک بار دیگر با دقّت به تصویر نگاه کن و جای درست را بزن."
                : "این نبود. یک بار دیگر فکر کن؛ می‌توانی دوباره گوش بدهی.";
            mascot().comfort(nudge);
            // a fresh go: the page, the question and هوهو's voice start again
            FeedbackDialog.show(requireContext(), false, nudge, "دوباره تلاش کن", this::renderStep);
        }
    }

    private void advance() {
        goToStep(Math.min(stepIndex + 1, script.steps.size() - 1));
    }

    @Override
    protected String entryTip() {
        return null; // the lesson narrates itself
    }
}
