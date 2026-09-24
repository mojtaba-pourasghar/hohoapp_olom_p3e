package com.hoohooolom.app.ui;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.hoohooolom.app.R;
import com.hoohooolom.app.tts.SoundManager;
import com.hoohooolom.app.tts.TtsManager;

/**
 * The "آفرین! / اشکالی ندارد!" sheet shown after every answer, and the plain notice used for
 * hints. Always opens fully expanded and scrolls — a long hint must never be able to hide the
 * button that moves the child to the next question.
 */
public final class FeedbackDialog {
    private FeedbackDialog() {}

    public static void show(Context ctx, boolean ok, String message, String buttonLabel, Runnable onDismiss) {
        SoundManager sound = SoundManager.get();
        if (sound != null) {
            if (ok) sound.correct();
            else sound.wrong();
        }

        BottomSheetDialog dialog = build(ctx,
            ok ? "🌟" : "🤔",
            ok ? "آفرین!" : "اشکالی ندارد!",
            ok ? R.color.orange_bg : R.color.pink_bg,
            ok ? R.color.orange_text : R.color.pink_dark,
            message, buttonLabel, onDismiss, true);
        dialog.setCancelable(false);
        dialog.show();
    }

    public static void info(Context ctx, String message) {
        build(ctx, "🦉", "هوهو می‌گوید", R.color.orange_bg, R.color.orange_text,
            message, "باشه", null, true).show();
    }

    private static BottomSheetDialog build(Context ctx, String icon, String title, int iconBgRes,
                                           int titleColorRes, String message, String buttonLabel,
                                           Runnable onDismiss, boolean canRepeat) {
        BottomSheetDialog dialog = new BottomSheetDialog(ctx);
        View content = LayoutInflater.from(ctx).inflate(R.layout.dialog_feedback, null);

        TextView iconView = content.findViewById(R.id.fb_icon);
        iconView.setText(icon);
        iconView.setBackground(UiKit.roundedBg(ContextCompat.getColor(ctx, iconBgRes), 0, 999f, ctx));

        TextView titleView = content.findViewById(R.id.fb_title);
        titleView.setText(title);
        titleView.setTextColor(ContextCompat.getColor(ctx, titleColorRes));

        ((TextView) content.findViewById(R.id.fb_message)).setText(message);

        TextView button = content.findViewById(R.id.fb_button);
        button.setText(buttonLabel);
        UiKit.tapSound(button);
        button.setOnClickListener(v -> {
            dialog.dismiss();
            if (onDismiss != null) onDismiss.run();
        });

        // read the explanation out loud again, for a child who is still working it out
        TextView repeat = content.findViewById(R.id.fb_repeat);
        if (canRepeat && message != null && !message.isEmpty()) {
            repeat.setVisibility(View.VISIBLE);
            repeat.setOnClickListener(v -> {
                TtsManager tts = TtsManager.get();
                if (tts != null) tts.speak(message);
            });
        }

        dialog.setContentView(content);

        // expanded from the start, so nothing important starts out below the fold
        dialog.setOnShowListener(d -> {
            FrameLayout sheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (sheet == null) return;
            BottomSheetBehavior<FrameLayout> behavior = BottomSheetBehavior.from(sheet);
            behavior.setSkipCollapsed(true);
            behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
        });
        if (dialog.getWindow() != null) {
            dialog.getWindow().setNavigationBarColor(Color.WHITE);
        }
        return dialog;
    }
}
