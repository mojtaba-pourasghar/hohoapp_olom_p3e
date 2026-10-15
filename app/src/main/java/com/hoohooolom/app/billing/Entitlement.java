package com.hoohooolom.app.billing;

import android.content.Context;
import android.content.SharedPreferences;

import com.hoohooolom.app.BuildConfig;

/**
 * Whether this phone has the subscription — the answer every locked screen asks for.
 *
 * Kept on the device, because the app must work on the bus with no signal: a subscription that
 * was verified once stays good until it runs out, and a child is never shut out of a paid lesson
 * because Bazaar could not be reached this minute.
 *
 * The purchase itself is verified against the store's RSA key before it is written here (see
 * {@link Receipt}), so what is stored is the conclusion, not a claim from outside.
 */
public final class Entitlement {

    private static final String PREFS = "entitlement";
    private static final String KEY_UNTIL = "until";      // 0 = never bought, Long.MAX = forever
    private static final String KEY_TOKEN = "token";
    private static final String KEY_SKU = "sku";
    private static final String KEY_CHECKED = "checked";

    /** A subscription stays trusted this long after the last word from the store. */
    private static final long GRACE_MS = 7L * 24 * 60 * 60 * 1000;

    private Entitlement() {}

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /**
     * Whether the locks apply at all.
     *
     * They do not in a debug build: developing or testing a chapter should not mean buying it
     * again on every phone, and the lock is never the thing under test. The release build — the
     * one that goes to کافه‌بازار — enforces it. Set in app/build.gradle, one line per type.
     */
    public static boolean enforced() {
        return BuildConfig.ENFORCE_LOCKS;
    }

    /**
     * Everything beyond the free part is open.
     *
     * This is the one place the question is answered, so «off in debug» cannot be true on one
     * screen and false on another.
     */
    public static boolean unlocked(Context context) {
        if (!enforced()) return true;
        SharedPreferences p = prefs(context);
        long until = p.getLong(KEY_UNTIL, 0);
        if (until == 0) return false;
        if (until == Long.MAX_VALUE) return true;          // bought outright, no expiry
        long now = System.currentTimeMillis();
        if (now <= until) return true;

        // past its end, but the store may simply not have been reachable to renew it
        long checked = p.getLong(KEY_CHECKED, 0);
        return checked > 0 && now - checked < GRACE_MS && now - until < GRACE_MS;
    }

    /** Remembers a verified purchase. `until` is 0 for a lifetime unlock. */
    public static void grant(Context context, String sku, String token, long until) {
        prefs(context).edit()
            .putString(KEY_SKU, sku == null ? "" : sku)
            .putString(KEY_TOKEN, token == null ? "" : token)
            .putLong(KEY_UNTIL, until <= 0 ? Long.MAX_VALUE : until)
            .putLong(KEY_CHECKED, System.currentTimeMillis())
            .apply();
    }

    /**
     * The store answered and there is no purchase. Clears the unlock — but only when the answer
     * really came from the store, never on a failure to ask.
     */
    public static void revoke(Context context) {
        prefs(context).edit()
            .remove(KEY_SKU).remove(KEY_TOKEN).remove(KEY_UNTIL)
            .putLong(KEY_CHECKED, System.currentTimeMillis())
            .apply();
    }

    /** Notes that the store confirmed what we already have, without changing it. */
    public static void confirmed(Context context) {
        prefs(context).edit().putLong(KEY_CHECKED, System.currentTimeMillis()).apply();
    }

    public static String sku(Context context) {
        return prefs(context).getString(KEY_SKU, "");
    }

    public static long until(Context context) {
        return prefs(context).getLong(KEY_UNTIL, 0);
    }
}
