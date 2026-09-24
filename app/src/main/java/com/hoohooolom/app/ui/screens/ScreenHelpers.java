package com.hoohooolom.app.ui.screens;

import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.hoohooolom.app.R;
import com.hoohooolom.app.data.AppState;
import com.hoohooolom.app.data.Book;
import com.hoohooolom.app.data.Level;
import com.hoohooolom.app.ui.UiKit;

import java.util.function.IntConsumer;

/** Small pieces shared by Sections / Worksheet-index / Exam-index (chapter chips, level cards). */
final class ScreenHelpers {
    private ScreenHelpers() {}

    static HorizontalScrollView buildChapterChipRow(Context c, AppState s, int selected, IntConsumer onPick) {
        HorizontalScrollView scroll = new HorizontalScrollView(c);
        scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout row = UiKit.row(c);
        row.setPadding(0, 0, 0, UiKit.dp(c, 4));
        for (Book.Chapter ch : Book.CHAPTERS) {
            boolean sel = ch.index == selected;
            // every chapter is open; the teacher's position is a bookmark, not a lock
            boolean here = ch.index == s.taughtChapter;
            String label = "درس " + ch.numberFa + (here ? " ★" : "");
            TextView chip = UiKit.chip(c,
                label,
                sel ? ContextCompat.getColor(c, R.color.orange_bg) : Color.WHITE,
                ContextCompat.getColor(c, sel ? R.color.orange : R.color.border_card),
                ContextCompat.getColor(c, sel ? R.color.orange_text : R.color.text_primary));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMarginEnd(UiKit.dp(c, 6));
            chip.setLayoutParams(lp);
            chip.setOnClickListener(v -> onPick.accept(ch.index));
            row.addView(chip);
        }
        scroll.addView(row);
        return scroll;
    }

    static android.view.View buildLevelCard(Context c, Level level, String meta, Runnable onClick) {
        LinearLayout card = UiKit.column(c);
        card.setPadding(UiKit.dp(c, 16), UiKit.dp(c, 16), UiKit.dp(c, 16), UiKit.dp(c, 16));
        card.setBackground(UiKit.roundedBg(level.bgColor, level.borderColor, 18f, c));
        card.setLayoutParams(UiKit.marginParams(c, 0, 11));

        LinearLayout headRow = UiKit.row(c);
        headRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView numChip = new TextView(c);
        numChip.setText(level.numberFa);
        UiKit.applyFont(numChip, true);
        numChip.setTextColor(Color.WHITE);
        numChip.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams chipLp = new LinearLayout.LayoutParams(UiKit.dp(c, 34), UiKit.dp(c, 34));
        numChip.setLayoutParams(chipLp);
        numChip.setBackground(UiKit.roundedBg(level.chipColor, 0, 10f, c));
        headRow.addView(numChip);

        LinearLayout textCol = UiKit.column(c);
        LinearLayout.LayoutParams textLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        textLp.setMarginStart(UiKit.dp(c, 10));
        textCol.setLayoutParams(textLp);
        textCol.addView(UiKit.text(c, level.title, 14.5f, R.color.text_primary, true));
        textCol.addView(UiKit.text(c, level.subtitle, 11.5f, R.color.text_muted, false));
        headRow.addView(textCol);

        card.addView(headRow);
        TextView metaView = UiKit.text(c, meta, 11.5f, R.color.text_muted, false);
        metaView.setLayoutParams(UiKit.marginParams(c, 10, 0));
        card.addView(metaView);

        card.setClickable(true);
        card.setFocusable(true);
        card.setOnClickListener(v -> onClick.run());
        return card;
    }
}
