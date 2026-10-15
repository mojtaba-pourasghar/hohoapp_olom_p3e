package com.hoohooolom.app.billing;

import android.util.Base64;

import org.json.JSONObject;

import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;

/**
 * Checking that a purchase really came from Bazaar.
 *
 * The store hands back the purchase as a line of JSON and a signature over it, made with the
 * private half of this app's key. Anyone can write the JSON; only Bazaar can sign it. So the
 * signature is checked against the public half — which is published in the developer panel and
 * is meant to be in the APK — and an unsigned or badly signed purchase is simply not a purchase.
 *
 * Without this step a patched app, or a fake «market», could hand the app any JSON it liked.
 */
public final class Receipt {

    /**
     * This app's public key, from the Bazaar developer panel.
     *
     * Public on purpose: it only lets the app verify, never sign. It ships in every APK.
     */
    // ⚠ Not set yet: هوهو علوم needs its own key from its own page in the Bazaar panel (every
    // app has a different one; the maths app's key would reject every science purchase). Until
    // it is pasted here, no purchase is believed — the release build stays at the free part and
    // the debug build, which has no locks, is unaffected.
    private static final String PUBLIC_KEY = "";

    private static final String ALGORITHM = "SHA1withRSA";

    /** A purchase that has been checked and believed. */
    public static final class Purchase {
        public final String sku;
        public final String token;
        public final long boughtAt;
        /** When a subscription runs out, or 0 for a one-off purchase that never does. */
        public final long until;

        Purchase(String sku, String token, long boughtAt, long until) {
            this.sku = sku;
            this.token = token;
            this.boughtAt = boughtAt;
            this.until = until;
        }
    }

    private Receipt() {}

    /**
     * Reads a purchase, but only if its signature holds.
     *
     * Returns null for anything else: a broken signature, a line that is not the JSON we expect,
     * a purchase in a state other than «bought». Null means «there is no purchase here», and
     * every caller treats it that way.
     */
    public static Purchase verify(String json, String signature) {
        if (json == null || json.isEmpty() || signature == null || signature.isEmpty()) return null;
        if (!signed(json, signature)) return null;
        try {
            JSONObject o = new JSONObject(json);
            // 0 = purchased, 1 = cancelled, 2 = refunded
            if (o.optInt("purchaseState", 1) != 0) return null;
            String sku = o.optString("productId", "");
            String token = o.optString("purchaseToken", "");
            if (sku.isEmpty() || token.isEmpty()) return null;
            return new Purchase(sku, token, o.optLong("purchaseTime", 0),
                                o.optLong("expiryTime", 0));
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean signed(String json, String signature) {
        if (PUBLIC_KEY.isEmpty()) return false;
        try {
            PublicKey key = KeyFactory.getInstance("RSA").generatePublic(
                new X509EncodedKeySpec(Base64.decode(PUBLIC_KEY, Base64.DEFAULT)));
            Signature check = Signature.getInstance(ALGORITHM);
            check.initVerify(key);
            check.update(json.getBytes("UTF-8"));
            return check.verify(Base64.decode(signature, Base64.DEFAULT));
        } catch (Exception e) {
            return false;
        }
    }
}
