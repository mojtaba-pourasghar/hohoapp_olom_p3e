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
import androidx.core.content.ContextCompat;

import com.hoohooolom.app.R;
import com.hoohooolom.app.data.AppState;
import com.hoohooolom.app.data.PersianDigits;
import com.hoohooolom.app.ui.BaseFragment;
import com.hoohooolom.app.ui.FeedbackDialog;
import com.hoohooolom.app.ui.Screen;
import com.hoohooolom.app.ui.UiKit;

public class ParentGateFragment extends BaseFragment {
    private enum Mode { ENTER, SETUP, RECOVER }

    private Mode mode;
    private int setupStage = 0; // 0=enter pin, 1=confirm pin, 2=set recovery question
    private StringBuilder pinDraft = new StringBuilder();
    private String pinFirst = "";
    private int recIdx = 0;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_parent_gate, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        boolean forceSetup = getArguments() != null && getArguments().getBoolean("forceSetup", false);
        mode = (state().hasPin() && !forceSetup) ? Mode.ENTER : Mode.SETUP;
        render(view);
    }

    private void render(View root) {
        AppState s = state();
        TextView title = root.findViewById(R.id.gate_title);
        TextView sub = root.findViewById(R.id.gate_sub);
        TextView alt = root.findViewById(R.id.gate_alt);
        TextView note = root.findViewById(R.id.gate_note);
        LinearLayout content = root.findViewById(R.id.gate_content);
        content.removeAllViews();

        if (mode == Mode.SETUP) {
            title.setText("ساختن رمز والدین");
            sub.setText(setupStage == 2 ? "سؤال بازیابی را انتخاب و پاسخش را ثبت کنید" : setupStage == 1 ? "رمز را یک‌بار دیگر بزنید" : "یک رمز ۴ رقمی بسازید");
            note.setText("سؤال بازیابی را جایی یادداشت نکنید؛ فقط پاسخش را به خاطر بسپارید.");
            alt.setVisibility(View.GONE);
            if (setupStage < 2) content.addView(buildPinEntry(root));
            else content.addView(buildRecoverySetup(root));
        } else if (mode == Mode.ENTER) {
            title.setText("ورود والدین");
            sub.setText("رمز ۴ رقمی را وارد کنید");
            note.setText("پاسخ تمرین‌ها، کاربرگ‌ها و تنظیم «معلم تا کجا درس داده» فقط از این بخش در دسترس است.");
            alt.setVisibility(View.VISIBLE);
            alt.setText("رمز را فراموش کردم");
            alt.setOnClickListener(v -> {
                mode = Mode.RECOVER;
                pinDraft.setLength(0);
                render(root);
            });
            content.addView(buildPinEntry(root));
        } else {
            title.setText("بازیابی رمز");
            sub.setText("به سؤال بازیابی پاسخ دهید");
            note.setText("اگر پاسخ درست باشد، می‌توانید رمز تازه بسازید.");
            alt.setVisibility(View.VISIBLE);
            alt.setText("بازگشت به ورود");
            alt.setOnClickListener(v -> {
                mode = Mode.ENTER;
                render(root);
            });
            content.addView(buildRecoveryAnswer(root, AppState.RECOVERY_QUESTIONS[s.recoveryQuestionIndex]));
        }
    }

    private View buildPinEntry(View root) {
        LinearLayout col = UiKit.column(requireContext());
        col.setGravity(Gravity.CENTER);

        TextView pinLabel = UiKit.text(requireContext(),
            mode == Mode.SETUP && setupStage == 1 ? "رمز را تکرار کنید" : "رمز ۴ رقمی", 13f, R.color.text_primary, true);
        col.addView(pinLabel, UiKit.marginParams(requireContext(), 0, 12));

        LinearLayout dots = UiKit.row(requireContext());
        dots.setGravity(Gravity.CENTER);
        for (int i = 0; i < 4; i++) {
            boolean filled = pinDraft.length() > i;
            boolean isNext = pinDraft.length() == i;
            TextView box = UiKit.text(requireContext(), filled ? "●" : "", 22f, R.color.pink_dark, true);
            box.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(UiKit.dp(requireContext(), 46), UiKit.dp(requireContext(), 56));
            lp.setMargins(UiKit.dp(requireContext(), 5), 0, UiKit.dp(requireContext(), 5), 0);
            box.setLayoutParams(lp);
            box.setBackground(UiKit.roundedBg(
                ContextCompat.getColor(requireContext(), filled ? R.color.pink_bg : R.color.bg_card),
                ContextCompat.getColor(requireContext(), isNext ? R.color.pink : R.color.pink_border), 14f, requireContext()));
            dots.addView(box);
        }
        col.addView(dots, UiKit.marginParams(requireContext(), 0, 16));

        col.addView(UiKit.buildKeypad(requireContext(), key -> {
            if (key.equals("C")) pinDraft.setLength(0);
            else if (key.equals("⌫")) { if (pinDraft.length() > 0) pinDraft.deleteCharAt(pinDraft.length() - 1); }
            else if (pinDraft.length() < 4) pinDraft.append(PersianDigits.en(key));
            render(root);
        }));

        TextView submit = UiKit.text(requireContext(), mode == Mode.SETUP && setupStage == 1 ? "ادامه" : mode == Mode.SETUP ? "ادامه" : "ورود", 15f, R.color.white, true);
        submit.setGravity(Gravity.CENTER);
        submit.setPadding(0, UiKit.dp(requireContext(), 14), 0, UiKit.dp(requireContext(), 14));
        submit.setBackground(UiKit.roundedBg(ContextCompat.getColor(requireContext(), R.color.pink), 0, 16f, requireContext()));
        submit.setLayoutParams(UiKit.marginParams(requireContext(), 14, 0));
        submit.setOnClickListener(v -> onPinSubmit(root));
        col.addView(submit);
        return col;
    }

    private void onPinSubmit(View root) {
        AppState s = state();
        String pin = pinDraft.toString();
        if (mode == Mode.SETUP) {
            if (setupStage == 0) {
                if (pin.length() != 4) {
                    FeedbackDialog.info(requireContext(), "رمز باید ۴ رقم باشد.");
                    return;
                }
                pinFirst = pin;
                pinDraft.setLength(0);
                setupStage = 1;
                render(root);
            } else if (setupStage == 1) {
                if (!pin.equals(pinFirst)) {
                    FeedbackDialog.info(requireContext(), "دو رمز یکسان نبودند. دوباره امتحان کنید.");
                    pinDraft.setLength(0);
                    pinFirst = "";
                    setupStage = 0;
                    render(root);
                    return;
                }
                setupStage = 2;
                pinDraft.setLength(0);
                render(root);
            }
        } else { // ENTER
            if (s.checkPin(pin)) {
                s.parentUnlockedThisSession = true;
                nav().go(Screen.PARENT_PANEL);
            } else {
                FeedbackDialog.info(requireContext(), "رمز درست نیست. اگر فراموش کرده‌اید، «رمز را فراموش کردم» را بزنید.");
                pinDraft.setLength(0);
                render(root);
            }
        }
    }

    private View buildRecoverySetup(View root) {
        LinearLayout col = UiKit.column(requireContext());
        col.setGravity(Gravity.CENTER);

        for (int i = 0; i < AppState.RECOVERY_QUESTIONS.length; i++) {
            int idx = i;
            boolean sel = i == recIdx;
            TextView option = UiKit.chip(requireContext(), AppState.RECOVERY_QUESTIONS[i],
                ContextCompat.getColor(requireContext(), sel ? R.color.pink_bg : R.color.bg_card),
                ContextCompat.getColor(requireContext(), sel ? R.color.pink : R.color.border_input),
                ContextCompat.getColor(requireContext(), R.color.text_primary));
            option.setLayoutParams(UiKit.marginParams(requireContext(), 0, 6));
            option.setOnClickListener(v -> { recIdx = idx; render(root); });
            col.addView(option);
        }

        EditText answer = new EditText(requireContext());
        answer.setHint("پاسخ سؤال بازیابی");
        answer.setInputType(InputType.TYPE_CLASS_TEXT);
        answer.setLayoutParams(UiKit.marginParams(requireContext(), 14, 0));
        col.addView(answer);

        TextView submit = UiKit.text(requireContext(), "ثبت رمز و ورود", 15f, R.color.white, true);
        submit.setGravity(Gravity.CENTER);
        submit.setPadding(0, UiKit.dp(requireContext(), 14), 0, UiKit.dp(requireContext(), 14));
        submit.setBackground(UiKit.roundedBg(ContextCompat.getColor(requireContext(), R.color.pink), 0, 16f, requireContext()));
        submit.setLayoutParams(UiKit.marginParams(requireContext(), 14, 0));
        submit.setOnClickListener(v -> {
            String text = answer.getText().toString().trim();
            if (text.isEmpty()) {
                FeedbackDialog.info(requireContext(), "پاسخ سؤال بازیابی را بنویسید تا اگر رمز را فراموش کردید، بتوانید بازیابی کنید.");
                return;
            }
            AppState s = state();
            s.setPin(pinFirst);
            s.setRecovery(recIdx, text);
            s.parentUnlockedThisSession = true;
            nav().go(Screen.PARENT_PANEL);
        });
        col.addView(submit);
        return col;
    }

    private View buildRecoveryAnswer(View root, String question) {
        LinearLayout col = UiKit.column(requireContext());
        col.setGravity(Gravity.CENTER);
        col.addView(UiKit.text(requireContext(), question, 14f, R.color.text_primary, true));

        EditText answer = new EditText(requireContext());
        answer.setInputType(InputType.TYPE_CLASS_TEXT);
        answer.setLayoutParams(UiKit.marginParams(requireContext(), 12, 0));
        col.addView(answer);

        TextView submit = UiKit.text(requireContext(), "بررسی پاسخ", 15f, R.color.white, true);
        submit.setGravity(Gravity.CENTER);
        submit.setPadding(0, UiKit.dp(requireContext(), 14), 0, UiKit.dp(requireContext(), 14));
        submit.setBackground(UiKit.roundedBg(ContextCompat.getColor(requireContext(), R.color.pink), 0, 16f, requireContext()));
        submit.setLayoutParams(UiKit.marginParams(requireContext(), 14, 0));
        submit.setOnClickListener(v -> {
            AppState s = state();
            if (s.checkRecovery(answer.getText().toString())) {
                mode = Mode.SETUP;
                setupStage = 0;
                pinDraft.setLength(0);
                pinFirst = "";
                render(root);
            } else {
                FeedbackDialog.info(requireContext(), "پاسخ با سؤال بازیابی هم‌خوانی ندارد.");
            }
        });
        col.addView(submit);
        return col;
    }

    @Override
    protected String entryTip() {
        return "این قسمت مخصوص بزرگ‌ترهاست؛ من اینجا منتظر می‌مانم.";
    }
}
