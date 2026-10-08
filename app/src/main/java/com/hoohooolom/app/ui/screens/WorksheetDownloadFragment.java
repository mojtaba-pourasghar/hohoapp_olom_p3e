package com.hoohooolom.app.ui.screens;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.hoohooolom.app.R;
import com.hoohooolom.app.data.AppState;
import com.hoohooolom.app.data.Book;
import com.hoohooolom.app.data.SheetStore;
import com.hoohooolom.app.data.WorksheetDownload;
import com.hoohooolom.app.ui.BaseFragment;
import com.hoohooolom.app.ui.UiKit;

import java.io.File;
import java.io.OutputStream;
import java.util.List;

import static com.hoohooolom.app.data.PersianDigits.fa;

/**
 * «دانلود کاربرگ» — printable sheets, kept separate from the 30-question sets that are answered
 * inside the app. Nothing here is generated: each sheet is a PDF listed in a catalogue.
 *
 * The download happens here, in the app. It used to be handed to the browser, which took the
 * child out of the app and, on a phone with no browser set up, landed on «برنامه‌ای پیدا نشد».
 * Now the button turns into a progress bar, and when the sheet has arrived the card offers two
 * things: open it in a PDF reader, or save a copy wherever the parent wants.
 *
 * The catalogue lives on the teacher's host and is cached on the device, so the screen opens
 * instantly and new sheets turn up without a new APK. When there is nothing published yet, it
 * says so in as many words rather than showing a broken-looking blank page.
 */
public class WorksheetDownloadFragment extends BaseFragment {
    private int currentChapter;

    /** The sheet «ذخیره» is for, kept while the system picker is open. */
    private WorksheetDownload saving;

    /**
     * Saving a copy goes through the system's own «create a document» picker.
     *
     * That way the parent chooses the folder, there is nothing to ask permission for on any
     * Android version, and the file lands somewhere they can find again.
     */
    private final ActivityResultLauncher<String> saveAs =
        registerForActivityResult(new ActivityResultContracts.CreateDocument("application/pdf"), uri -> {
            WorksheetDownload sheet = saving;
            saving = null;
            if (uri == null || sheet == null) return;        // the parent backed out
            try (OutputStream out = requireContext().getContentResolver().openOutputStream(uri)) {
                boolean ok = out != null && SheetStore.copyTo(requireContext(), sheet, out);
                toast(ok ? "کاربرگ ذخیره شد." : "ذخیره نشد.");
            } catch (Exception e) {
                toast("ذخیره نشد.");
            }
        });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_list_index, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        AppState s = state();
        Bundle args = getArguments();
        currentChapter = args != null ? args.getInt("chapter", s.taughtChapter) : s.taughtChapter;
        ((TextView) view.findViewById(R.id.screen_title)).setText("دانلود کاربرگ");
        render(view);

        // the list is shown from the copy on the device straight away, then brought up to date
        // in the background — so new sheets appear without a new APK
        WorksheetDownload.refresh(requireContext(), changed -> {
            if (changed && isAdded()) render(view);
        });
    }

    private void render(View view) {
        AppState s = state();
        Book.Chapter ch = Book.chapter(currentChapter);

        FrameLayout chipContainer = view.findViewById(R.id.chip_container);
        chipContainer.removeAllViews();
        chipContainer.addView(ScreenHelpers.buildChapterChipRow(requireContext(), s, currentChapter, i -> {
            currentChapter = i;
            render(view);
        }));

        ((TextView) view.findViewById(R.id.chapter_title)).setText("درس " + ch.numberFa + ": " + ch.title);
        ((TextView) view.findViewById(R.id.chapter_subtitle))
            .setText("کاربرگ‌های چاپی برای نوشتن روی کاغذ. اینجا جدا از کاربرگ‌های داخلِ اپ است.");

        LinearLayout list = view.findViewById(R.id.list_container);
        list.removeAllViews();

        List<WorksheetDownload> sheets = WorksheetDownload.forChapter(requireContext(), currentChapter);
        if (sheets.isEmpty()) {
            list.addView(emptyCard());
        } else {
            for (WorksheetDownload sheet : sheets) list.addView(sheetCard(sheet));
        }

        list.addView(backLink());
    }

    /** Honest empty state: nothing is published yet, and it says why. */
    private View emptyCard() {
        LinearLayout card = UiKit.column(requireContext());
        int pad = UiKit.dp(requireContext(), 18);
        card.setPadding(pad, pad, pad, pad);
        UiKit.applyCardBg(card, requireContext(), R.color.orange_bg, R.color.orange_border);
        card.setLayoutParams(UiKit.marginParams(requireContext(), 0, 10));

        card.addView(UiKit.text(requireContext(), "هنوز کاربرگ چاپی گذاشته نشده", 15f, R.color.orange_text, true));
        TextView body = UiKit.text(requireContext(),
            "این بخش آماده است و به محض اینکه فایل‌های کاربرگ جایی گذاشته شوند، فهرستشان همین‌جا "
                + "می‌آید و با یک ضربه دانلود می‌شوند.\n\n"
                + "تا آن وقت، کاربرگ‌های داخلِ اپ کار می‌کنند: سی سؤال در هر کاربرگ، همین‌جا جواب می‌دهی.",
            13f, R.color.text_primary, false);
        body.setLineSpacing(UiKit.dp(requireContext(), 5), 1f);
        card.addView(body, UiKit.marginParams(requireContext(), 8, 0));
        return card;
    }

    private View sheetCard(WorksheetDownload sheet) {
        LinearLayout card = UiKit.column(requireContext());
        int pad = UiKit.dp(requireContext(), 16);
        card.setPadding(pad, pad, pad, pad);
        UiKit.applyCardBg(card, requireContext(), R.color.bg_card, R.color.teal_border);
        card.setLayoutParams(UiKit.marginParams(requireContext(), 0, 10));

        card.addView(UiKit.text(requireContext(), sheet.title, 14.5f, R.color.text_primary, true));
        if (!sheet.note.isEmpty()) {
            card.addView(UiKit.text(requireContext(), sheet.note, 11.5f, R.color.text_muted, false));
        }

        // everything below the title is swapped in place: button → progress → open/save
        LinearLayout area = UiKit.column(requireContext());
        area.setLayoutParams(UiKit.marginParams(requireContext(), 12, 0));
        card.addView(area);

        if (SheetStore.has(requireContext(), sheet)) showReady(area, sheet);
        else showDownloadButton(area, sheet);
        return card;
    }

    /** Before the download: one button. */
    private void showDownloadButton(LinearLayout area, WorksheetDownload sheet) {
        area.removeAllViews();
        TextView button = UiKit.primaryButton(requireContext(), "دانلود ⤓",
            color(R.color.teal), R.color.white);
        button.setOnClickListener(v -> start(area, sheet));
        area.addView(button);
    }

    /** While it runs: a bar, a percentage, and a way out. */
    private void start(LinearLayout area, WorksheetDownload sheet) {
        area.removeAllViews();

        TextView label = UiKit.text(requireContext(), "در حال دانلود…", 12.5f, R.color.text_muted, true);
        area.addView(label);

        ProgressBar bar = new ProgressBar(requireContext(), null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(100);
        bar.setProgressDrawable(ContextCompat.getDrawable(requireContext(), R.drawable.progress_teal));
        LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, UiKit.dp(requireContext(), 7));
        barLp.topMargin = UiKit.dp(requireContext(), 9);
        area.addView(bar, barLp);

        TextView stop = UiKit.text(requireContext(), "انصراف", 12.5f, R.color.text_muted, true);
        stop.setGravity(Gravity.CENTER);
        int vp = UiKit.dp(requireContext(), 10);
        stop.setPadding(0, vp, 0, vp);
        stop.setOnClickListener(v -> {
            SheetStore.cancel(sheet);
            label.setText("در حال ایستادن…");
        });
        UiKit.tapSound(stop);
        area.addView(stop);

        SheetStore.download(requireContext(), sheet, new SheetStore.Progress() {
            @Override public void onBytes(long soFar, long total) {
                if (!isAdded()) return;
                if (total > 0) {
                    bar.setIndeterminate(false);
                    bar.setProgress((int) (soFar * 100 / total));
                    label.setText(fa((int) (soFar * 100 / total)) + "٪ — " + size(soFar)
                        + " از " + size(total));
                } else {
                    // no Content-Length: show it moving, and how much has come
                    bar.setIndeterminate(true);
                    label.setText(size(soFar) + " دانلود شد");
                }
            }

            @Override public void onDone(File file) {
                if (!isAdded()) return;
                showReady(area, sheet);
            }

            @Override public void onFailed() {
                if (!isAdded()) return;
                showDownloadButton(area, sheet);
                toast("دانلود نشد. اینترنت را چک کن و دوباره بزن.");
            }
        });
    }

    /** Afterwards: open it, save a copy, or get rid of it. */
    private void showReady(LinearLayout area, WorksheetDownload sheet) {
        area.removeAllViews();

        File file = SheetStore.fileFor(requireContext(), sheet);
        area.addView(UiKit.text(requireContext(), "✓ روی گوشی هست — " + size(file.length()),
            12.5f, R.color.teal_dark, true));

        LinearLayout buttons = UiKit.row(requireContext());
        buttons.setLayoutParams(UiKit.marginParams(requireContext(), 10, 0));
        buttons.addView(actionButton("باز کن", color(R.color.teal), R.color.white,
            () -> open(sheet)));
        buttons.addView(actionButton("ذخیره", color(R.color.bg_card), R.color.teal_dark,
            () -> {
                saving = sheet;
                saveAs.launch(SheetStore.suggestedName(sheet));
            }));
        area.addView(buttons);

        TextView remove = UiKit.text(requireContext(), "پاک کردن از گوشی", 11.5f, R.color.text_muted, false);
        remove.setGravity(Gravity.CENTER);
        int vp = UiKit.dp(requireContext(), 9);
        remove.setPadding(0, vp, 0, vp);
        remove.setOnClickListener(v -> {
            SheetStore.delete(requireContext(), sheet);
            showDownloadButton(area, sheet);
        });
        UiKit.tapSound(remove);
        area.addView(remove);
    }

    /** Two buttons side by side, each taking half the row. */
    private TextView actionButton(String label, int fill, int textColorRes, Runnable action) {
        TextView button = UiKit.text(requireContext(), label, 13.5f, textColorRes, true);
        button.setGravity(Gravity.CENTER);
        int vp = UiKit.dp(requireContext(), 12);
        button.setPadding(0, vp, 0, vp);
        button.setBackground(UiKit.roundedBg(fill, color(R.color.teal_border), 14f, requireContext()));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
            LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMarginEnd(UiKit.dp(requireContext(), 4));
        lp.setMarginStart(UiKit.dp(requireContext(), 4));
        button.setLayoutParams(lp);
        button.setOnClickListener(v -> action.run());
        UiKit.tapSound(button);
        return button;
    }

    /** Hands the downloaded file to a PDF reader, as a content:// Uri. */
    private void open(WorksheetDownload sheet) {
        File file = SheetStore.fileFor(requireContext(), sheet);
        if (!file.exists()) return;
        try {
            Uri uri = FileProvider.getUriForFile(requireContext(),
                requireContext().getPackageName() + ".sheets", file);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, "application/pdf");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            // no PDF reader on this phone — saving it is still useful, so say that
            toast("برنامه‌ای برای باز کردن PDF روی گوشی نیست. «ذخیره» را بزن و با رایانه چاپش کن.");
        } catch (Exception e) {
            toast("باز نشد.");
        }
    }

    /** A size a parent reads at a glance: whole megabytes with one decimal, or kilobytes. */
    private String size(long bytes) {
        if (bytes >= 1048576) {
            long tenths = Math.round(bytes / 104857.6);
            return fa(tenths / 10) + "٫" + fa(tenths % 10) + " مگابایت";
        }
        return fa(Math.max(1, bytes / 1024)) + " کیلوبایت";
    }

    private int color(int res) {
        return ContextCompat.getColor(requireContext(), res);
    }

    private void toast(String message) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
    }

    private View backLink() {
        TextView link = UiKit.text(requireContext(), "‹ کاربرگ‌های داخل اپ", 13.5f, R.color.teal_dark, true);
        link.setGravity(Gravity.CENTER);
        int vp = UiKit.dp(requireContext(), 14);
        link.setPadding(0, vp, 0, vp);
        UiKit.applyCardBg(link, requireContext(), R.color.bg_card, R.color.teal_border);
        link.setLayoutParams(UiKit.marginParams(requireContext(), 6, 8));
        link.setOnClickListener(v -> {
            Bundle args = new Bundle();
            args.putInt("chapter", currentChapter);
            nav().go(com.hoohooolom.app.ui.Screen.WORKSHEET_INDEX, args);
        });
        UiKit.tapSound(link);
        return link;
    }

    @Override
    protected String entryTip() {
        return "اینجا کاربرگ‌های چاپی است. اگر کاغذ و مداد دوست داری، از همین‌جا دانلود کن!";
    }
}
