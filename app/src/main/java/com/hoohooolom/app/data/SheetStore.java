package com.hoohooolom.app.data;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.hoohooolom.app.net.Net;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * The printable worksheets, on the device.
 *
 * A sheet used to be handed to the browser with an ACTION_VIEW, which took the child out of the
 * app, sometimes into a download manager and sometimes into nothing at all — and on a phone with
 * no browser set up, into «برنامه‌ای پیدا نشد». Now the PDF is fetched here, with a progress bar
 * where the download button was, and afterwards the parent chooses: open it, or save a copy.
 *
 * The files live in the app's own folder, so no storage permission is needed and they go away
 * with the app. «ذخیره» copies one out to wherever the parent picks, through the system picker.
 */
public final class SheetStore {

    /** Where a download has got to, on the main thread. */
    public interface Progress {
        void onBytes(long soFar, long total);

        void onDone(File file);

        void onFailed();
    }

    private static final String DIR = "sheets";
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    /** Sheets being fetched right now, so two taps cannot start the same download twice. */
    private static final Set<String> inFlight = Collections.synchronizedSet(new HashSet<>());

    /** Cancelled sheets, by url — the download thread checks this and gives up. */
    private static final Set<String> cancelled = Collections.synchronizedSet(new HashSet<>());

    private SheetStore() {}

    /**
     * The file a sheet is kept in.
     *
     * Named after the sheet's own path on the host, flattened: two sheets called sheet.pdf in
     * different folders are different files, and must not land on top of each other.
     */
    public static File fileFor(Context context, WorksheetDownload sheet) {
        return new File(new File(context.getFilesDir(), DIR), localName(sheet));
    }

    public static boolean has(Context context, WorksheetDownload sheet) {
        File file = fileFor(context, sheet);
        return file.exists() && file.length() > 256;
    }

    public static boolean isDownloading(WorksheetDownload sheet) {
        return inFlight.contains(sheet.url);
    }

    /** The name to suggest when the parent saves a copy — the sheet's own title, as a file. */
    public static String suggestedName(WorksheetDownload sheet) {
        String title = sheet.title == null ? "" : sheet.title.trim();
        if (title.isEmpty()) title = "karbarg";
        // «/» and the like cannot be in a file name, and a long title is no use as one
        title = title.replaceAll("[\\\\/:*?\"<>|\\n\\r\\t]", " ").replaceAll("\\s+", " ").trim();
        if (title.length() > 60) title = title.substring(0, 60).trim();
        return title + ".pdf";
    }

    /**
     * Fetches a sheet, reporting progress. Does nothing when it is already here or already on
     * its way; `progress` is always called on the main thread.
     */
    public static void download(Context context, WorksheetDownload sheet, Progress progress) {
        Context app = context.getApplicationContext();
        File target = fileFor(app, sheet);
        if (has(app, sheet)) {
            MAIN.post(() -> progress.onDone(target));
            return;
        }
        if (!inFlight.add(sheet.url)) return;
        cancelled.remove(sheet.url);

        new Thread(() -> {
            boolean ok = Net.download(sheet.url, target, (soFar, total) -> {
                MAIN.post(() -> progress.onBytes(soFar, total));
                return !cancelled.contains(sheet.url);
            });
            inFlight.remove(sheet.url);
            cancelled.remove(sheet.url);
            MAIN.post(() -> {
                if (ok) progress.onDone(target);
                else progress.onFailed();
            });
        }, "sheet-download").start();
    }

    /** Stops a download in flight. The half-written file is discarded, not kept. */
    public static void cancel(WorksheetDownload sheet) {
        if (inFlight.contains(sheet.url)) cancelled.add(sheet.url);
    }

    public static boolean delete(Context context, WorksheetDownload sheet) {
        return fileFor(context, sheet).delete();
    }

    /** Copies a downloaded sheet into a place the parent picked. */
    public static boolean copyTo(Context context, WorksheetDownload sheet, OutputStream out) {
        File file = fileFor(context, sheet);
        if (!file.exists()) return false;
        try (InputStream in = new FileInputStream(file)) {
            byte[] buffer = new byte[16384];
            int read;
            while ((read = in.read(buffer)) > 0) out.write(buffer, 0, read);
            out.flush();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static String localName(WorksheetDownload sheet) {
        String url = sheet.url == null ? "" : sheet.url;
        int slash = url.indexOf("/karbarg/");
        String tail = slash >= 0 ? url.substring(slash + "/karbarg/".length()) : url;
        String flat = tail.replaceAll("[^A-Za-z0-9._-]", "_");
        if (flat.length() > 80) flat = flat.substring(flat.length() - 80);
        if (!flat.toLowerCase().endsWith(".pdf")) flat = flat + ".pdf";
        return flat;
    }
}
