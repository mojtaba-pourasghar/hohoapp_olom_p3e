package com.hoohooolom.app.tts;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.speech.tts.TextToSpeech;

import androidx.appcompat.app.AlertDialog;

/**
 * The lessons are spoken, so a device with no Persian voice installed would leave هوهو silent.
 * Android does not let an app install a speech engine by itself, so instead we detect the gap
 * once and hand the parent a one-tap route to fix it.
 */
public final class VoiceSetup {

    private static boolean askedThisRun = false;

    private VoiceSetup() {}

    /** Shows the offer at most once per app run, and only when Persian really is missing. */
    public static void promptIfMissing(Context context) {
        if (askedThisRun) return;
        TtsManager tts = TtsManager.get();
        if (tts == null || !tts.isReady() || tts.isPersianAvailable()) return;
        askedThisRun = true;

        new AlertDialog.Builder(context)
            .setTitle("صدای فارسی نصب نیست")
            .setMessage("این دستگاه صدای فارسی ندارد، برای همین هوهو ساکت است. "
                + "یک موتور گفتار فارسی از بازار یا گوگل‌پلی نصب کنید (مثلاً موتور گفتار سامسونگ یا RHVoice)، "
                + "بعد در تنظیمات اندروید ← زبان و ورودی ← خروجی گفتار، فارسی را انتخاب کنید.\n\n"
                + "تا آن موقع همه‌ی متن‌ها روی صفحه نوشته می‌شوند و درس‌ها کار می‌کنند.")
            .setPositiveButton("نصب صدای فارسی", (d, w) -> openVoiceInstall(context))
            .setNeutralButton("تنظیمات گفتار", (d, w) -> openTtsSettings(context))
            .setNegativeButton("بعداً", null)
            .show();
    }

    private static void openVoiceInstall(Context context) {
        // the engine's own "download voice data" screen, when it offers one
        if (launch(context, new Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA))) return;
        // otherwise send them to the store to pick a Persian engine
        Intent store = new Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q=persian text to speech&c=apps"));
        if (launch(context, store)) return;
        launch(context, new Intent(Intent.ACTION_VIEW,
            Uri.parse("https://play.google.com/store/search?q=persian%20text%20to%20speech&c=apps")));
    }

    private static void openTtsSettings(Context context) {
        if (launch(context, new Intent("com.android.settings.TTS_SETTINGS"))) return;
        launch(context, new Intent(android.provider.Settings.ACTION_SETTINGS));
    }

    private static boolean launch(Context context, Intent intent) {
        try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
