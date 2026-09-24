package com.hoohooolom.app.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;

import com.hoohooolom.app.R;

import java.util.function.Consumer;

/**
 * Small shared helpers for building the app's card/chip/button look in code, since most
 * screens lay out a dynamic list (chapters, levels, sections) rather than fixed content.
 */
public final class UiKit {
    private UiKit() {}

    private static Typeface regularCache;
    private static Typeface boldCache;

    /** Vazirmatn embedded in res/font — the app's single Persian typeface. */
    public static Typeface font(Context c, boolean bold) {
        try {
            if (bold) {
                if (boldCache == null) boldCache = ResourcesCompat.getFont(c, R.font.vazirmatn_bold);
                if (boldCache != null) return boldCache;
            } else {
                if (regularCache == null) regularCache = ResourcesCompat.getFont(c, R.font.vazirmatn_regular);
                if (regularCache != null) return regularCache;
            }
        } catch (Exception ignored) {
        }
        return bold ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT;
    }

    public static void applyFont(android.widget.TextView tv, boolean bold) {
        Typeface tf = font(tv.getContext(), bold);
        if (tf != null) tv.setTypeface(tf);
    }

    /**
     * Gives a control the little wooden tap sound. Attached as a touch listener that never
     * consumes the event, so whatever click handling the view already has keeps working.
     */
    @android.annotation.SuppressLint("ClickableViewAccessibility")
    public static void tapSound(View v) {
        v.setOnTouchListener((view, event) -> {
            if (event.getAction() == android.view.MotionEvent.ACTION_DOWN) {
                com.hoohooolom.app.tts.SoundManager sm = com.hoohooolom.app.tts.SoundManager.get();
                if (sm != null) sm.tap();
            }
            return false;
        });
    }

    public static int dp(Context c, float dp) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, c.getResources().getDisplayMetrics());
    }

    public static int sp(Context c, float sp) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, c.getResources().getDisplayMetrics());
    }

    public static GradientDrawable roundedBg(int fillColor, int strokeColor, float radiusDp, Context c) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fillColor);
        d.setCornerRadius(dp(c, radiusDp));
        if (strokeColor != 0) d.setStroke(dp(c, 1.5f), strokeColor);
        return d;
    }

    public static GradientDrawable pillBg(int fillColor, int strokeColor, Context c) {
        return roundedBg(fillColor, strokeColor, 999f, c);
    }

    /** A filled circle with a solid ring around it — the map's stepping-stone nodes. */
    public static GradientDrawable ringedCircle(int fillColor, int ringColor, int ringWidthPx) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(fillColor);
        d.setStroke(ringWidthPx, ringColor);
        return d;
    }

    public static TextView text(Context c, String s, float sizeSp, int colorRes, boolean bold) {
        TextView tv = new TextView(c);
        tv.setText(s);
        tv.setTextSize(sizeSp);
        tv.setTextColor(ContextCompat.getColor(c, colorRes));
        applyFont(tv, bold);
        return tv;
    }

    public static LinearLayout column(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    public static LinearLayout row(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        return l;
    }

    public static LinearLayout.LayoutParams marginParams(Context c, int topDp, int bottomDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(c, topDp);
        lp.bottomMargin = dp(c, bottomDp);
        return lp;
    }

    public static Button primaryButton(Context c, String label, int bgColor, int textColorRes) {
        Button b = new Button(c);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextColor(ContextCompat.getColor(c, textColorRes));
        applyFont(b, true);
        b.setTextSize(14.5f);
        b.setPadding(dp(c, 20), dp(c, 12), dp(c, 20), dp(c, 12));
        b.setBackground(roundedBg(bgColor, 0, 14f, c));
        b.setStateListAnimator(null);
        return b;
    }

    public static View card(Context c, int fillColorRes, int strokeColorRes) {
        View v = new View(c);
        v.setBackground(roundedBg(ContextCompat.getColor(c, fillColorRes), ContextCompat.getColor(c, strokeColorRes), 18f, c));
        return v;
    }

    public static void applyCardBg(View v, Context c, int fillColorRes, int strokeColorRes) {
        v.setBackground(roundedBg(ContextCompat.getColor(c, fillColorRes), ContextCompat.getColor(c, strokeColorRes), 18f, c));
    }

    public static void applyPillBg(View v, Context c, int fillColor, int strokeColor) {
        v.setBackground(pillBg(fillColor, strokeColor, c));
    }

    private static final String[] KEYPAD_KEYS = {"۱", "۲", "۳", "۴", "۵", "۶", "۷", "۸", "۹", "۰", "⌫", "C"};

    /** The 1-9,0,backspace,clear numeric keypad used for both lesson "num" steps and quiz numeric answers. */
    public static GridLayout buildKeypad(Context c, Consumer<String> onKey) {
        GridLayout grid = new GridLayout(c);
        grid.setColumnCount(3);
        for (String key : KEYPAD_KEYS) {
            TextView keyView = new TextView(c);
            keyView.setText(key);
            keyView.setTextSize(18f);
            applyFont(keyView, true);
            keyView.setGravity(Gravity.CENTER);
            keyView.setTextColor(ContextCompat.getColor(c, R.color.text_primary));
            boolean special = key.equals("⌫") || key.equals("C");
            keyView.setBackground(roundedBg(ContextCompat.getColor(c, special ? R.color.bg_card_muted : R.color.bg_card), ContextCompat.getColor(c, R.color.border_input), 12f, c));
            keyView.setClickable(true);
            keyView.setFocusable(true);
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = dp(c, 92);
            lp.height = dp(c, 52);
            lp.setMargins(dp(c, 5), dp(c, 5), dp(c, 5), dp(c, 5));
            keyView.setLayoutParams(lp);
            keyView.setOnClickListener(v -> onKey.accept(key));
            tapSound(keyView);
            grid.addView(keyView);
        }
        return grid;
    }

    public static TextView chip(Context c, String label, int fillColor, int strokeColor, int textColor) {
        TextView tv = new TextView(c);
        tv.setText(label);
        tv.setTextSize(12.5f);
        applyFont(tv, true);
        tv.setTextColor(textColor);
        tv.setPadding(dp(c, 14), dp(c, 7), dp(c, 14), dp(c, 7));
        tv.setBackground(pillBg(fillColor, strokeColor, c));
        tv.setGravity(Gravity.CENTER);
        return tv;
    }
}
