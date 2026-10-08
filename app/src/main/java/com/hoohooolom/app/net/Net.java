package com.hoohooolom.app.net;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * The small amount of networking this app does: fetch a text file, fetch a binary file, and say
 * whether there is a connection at all.
 *
 * Everything the app downloads — the narration clips, the worksheet catalogue — is a plain file
 * on the teacher's own host, so there is nothing here about sessions, tokens or retries beyond
 * what a download needs.
 */
public final class Net {

    private static final int CONNECT_MS = 12000;
    private static final int READ_MS = 30000;

    private Net() {}

    /** Whether the device thinks it can reach the internet right now. */
    public static boolean online(Context context) {
        try {
            ConnectivityManager cm = (ConnectivityManager)
                context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return false;
            NetworkCapabilities caps = cm.getNetworkCapabilities(cm.getActiveNetwork());
            return caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
        } catch (Exception e) {
            return false;
        }
    }

    /** The body of a text file, or null when it could not be fetched. */
    public static String text(String url) {
        HttpURLConnection connection = null;
        try {
            connection = open(url);
            if (connection.getResponseCode() / 100 != 2) return null;
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                StringBuilder out = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) out.append(line).append('\n');
                return out.toString();
            }
        } catch (Exception e) {
            return null;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    /**
     * Told how far a download has got: bytes so far and the total, or -1 for the total when the
     * server does not say. Returning false stops the download — that is the cancel button.
     */
    public interface Watcher {
        boolean onBytes(long soFar, long total);
    }

    /**
     * Downloads to a temporary file and only then moves it into place, so a dropped connection
     * can never leave a half file that later looks like a finished one.
     */
    public static boolean download(String url, File target) {
        return download(url, target, null);
    }

    /**
     * The same download, reporting its progress.
     *
     * A printable worksheet is a megabyte or two of PDF: long enough that a page with no sign of
     * movement looks stuck. The watcher is called as the bytes arrive, and a watcher that says
     * stop leaves nothing behind but the discarded .part file.
     */
    public static boolean download(String url, File target, Watcher watcher) {
        HttpURLConnection connection = null;
        File part = new File(target.getParentFile(), target.getName() + ".part");
        try {
            File parent = target.getParentFile();
            if (parent != null) parent.mkdirs();
            connection = open(url);
            if (connection.getResponseCode() / 100 != 2) return false;
            long total = connection.getContentLengthLong();

            try (InputStream in = connection.getInputStream();
                 FileOutputStream out = new FileOutputStream(part)) {
                byte[] buffer = new byte[16384];
                long soFar = 0;
                int read;
                while ((read = in.read(buffer)) > 0) {
                    out.write(buffer, 0, read);
                    soFar += read;
                    if (watcher != null && !watcher.onBytes(soFar, total)) {
                        part.delete();
                        return false;
                    }
                }
            }
            if (part.length() < 256) {          // too small to be a recording or a sheet
                part.delete();
                return false;
            }
            if (target.exists()) target.delete();
            return part.renameTo(target);
        } catch (Exception e) {
            part.delete();
            return false;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static HttpURLConnection open(String url) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(CONNECT_MS);
        connection.setReadTimeout(READ_MS);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("User-Agent", "HooHooOlom");
        return connection;
    }
}
