package com.hoohooolom.app.billing;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;

import com.android.vending.billing.IInAppBillingService;

import java.util.ArrayList;
import java.util.List;

/**
 * Talking to کافه‌بازار about the subscription.
 *
 * Bazaar still speaks the long-standing IAB v3 interface: bind to its service, ask what this
 * account owns, and ask for an Intent that opens the store's own payment screen. Every purchase
 * that comes back is checked against the app's public key in {@link Receipt} before it is
 * believed, and only then written into {@link Entitlement}.
 *
 * Nothing here ever decides what is free — that is {@link com.hoohooolom.app.data.Access}. This
 * class only answers «has it been paid for».
 */
public final class Bazaar {

    /** The store's package, and the permission the manifest asks for. */
    public static final String STORE = "com.farsitel.bazaar";

    /**
     * The action to bind the billing service with.
     *
     * ⚠ This one string has to match Bazaar's current sample exactly. If it is wrong the bind
     * simply never succeeds, and every purchase looks like «اتصال برقرار نشد» — which is why the
     * paywall says that out loud instead of failing quietly. Check it against
     * developers.cafebazaar.ir → پرداخت درون‌برنامه‌ای → پیاده‌سازی → جاوا before publishing.
     */
    public static final String BIND_ACTION = "ir.cafebazaar.pardakht.InAppBillingService.BIND";

    /** The API version this interface speaks. */
    private static final int API = 3;

    /** «subs» for a subscription, «inapp» for a one-off purchase. */
    public static final String SUBSCRIPTION = "subs";
    public static final String ONE_OFF = "inapp";

    public static final int PURCHASE_REQUEST = 7301;

    /** Told how a call went, always on the main thread. */
    public interface Answer {
        /** A verified purchase, or null when this account owns nothing. */
        void onPurchase(Receipt.Purchase purchase);

        /** Something got in the way: no store, no connection, a refusal. */
        void onProblem(String message);
    }

    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private final Context app;
    private IInAppBillingService service;
    private ServiceConnection connection;

    public Bazaar(Context context) {
        this.app = context.getApplicationContext();
    }

    /** Whether Bazaar is installed at all. Without it there is nothing to buy through. */
    public static boolean installed(Context context) {
        try {
            context.getPackageManager().getPackageInfo(STORE, 0);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** Binds to the store, then runs `whenReady`. Reports the reason when it cannot. */
    public void connect(Runnable whenReady, java.util.function.Consumer<String> whenNot) {
        if (service != null) {
            MAIN.post(whenReady);
            return;
        }
        if (!installed(app)) {
            MAIN.post(() -> whenNot.accept("برنامه‌ی کافه‌بازار روی این دستگاه نیست."));
            return;
        }

        connection = new ServiceConnection() {
            @Override public void onServiceConnected(ComponentName name, IBinder binder) {
                service = IInAppBillingService.Stub.asInterface(binder);
                MAIN.post(whenReady);
            }

            @Override public void onServiceDisconnected(ComponentName name) {
                service = null;
            }
        };

        Intent intent = new Intent(BIND_ACTION);
        intent.setPackage(STORE);
        try {
            if (!app.bindService(intent, connection, Context.BIND_AUTO_CREATE)) {
                connection = null;
                MAIN.post(() -> whenNot.accept("به سرویسِ پرداختِ بازار وصل نشد."));
            }
        } catch (Exception e) {
            connection = null;
            MAIN.post(() -> whenNot.accept("به سرویسِ پرداختِ بازار وصل نشد: " + e.getMessage()));
        }
    }

    public void disconnect() {
        if (connection != null) {
            try {
                app.unbindService(connection);
            } catch (Exception ignored) {
                // already gone; nothing to let go of
            }
            connection = null;
        }
        service = null;
    }

    /**
     * What this account already owns.
     *
     * Called on every launch, so a subscription bought on another phone — or one bought here and
     * then reinstalled — is picked up without the parent having to do anything. Subscriptions
     * first, then one-off purchases, since either may be the thing that unlocks the app.
     */
    public void restore(Answer answer) {
        connect(() -> new Thread(() -> {
            Receipt.Purchase found = firstOwned(SUBSCRIPTION);
            if (found == null) found = firstOwned(ONE_OFF);
            final Receipt.Purchase result = found;
            MAIN.post(() -> answer.onPurchase(result));
        }, "bazaar-restore").start(),
        problem -> answer.onProblem(problem));
    }

    /** Opens the store's payment screen for one product. The result lands in onActivityResult. */
    public void buy(Activity activity, String sku, String type, Answer answer) {
        connect(() -> new Thread(() -> {
            try {
                Bundle reply = service.getBuyIntent(API, app.getPackageName(), sku, type, "");
                int code = reply.getInt("RESPONSE_CODE", -1);
                android.app.PendingIntent pending = reply.getParcelable("BUY_INTENT");
                if (code != 0 || pending == null) {
                    MAIN.post(() -> answer.onProblem(reason(code)));
                    return;
                }
                MAIN.post(() -> {
                    try {
                        activity.startIntentSenderForResult(pending.getIntentSender(),
                            PURCHASE_REQUEST, new Intent(), 0, 0, 0);
                    } catch (Exception e) {
                        answer.onProblem("صفحه‌ی پرداخت باز نشد.");
                    }
                });
            } catch (Exception e) {
                MAIN.post(() -> answer.onProblem("درخواستِ خرید فرستاده نشد."));
            }
        }, "bazaar-buy").start(),
        problem -> answer.onProblem(problem));
    }

    /** Reads the result Bazaar hands back after its payment screen closes. */
    public static Receipt.Purchase fromResult(Intent data) {
        if (data == null) return null;
        if (data.getIntExtra("RESPONSE_CODE", -1) != 0) return null;
        return Receipt.verify(data.getStringExtra("INAPP_PURCHASE_DATA"),
                              data.getStringExtra("INAPP_DATA_SIGNATURE"));
    }

    /** The first verified purchase of one type, or null. Runs off the main thread. */
    private Receipt.Purchase firstOwned(String type) {
        String page = null;
        try {
            do {
                Bundle reply = service.getPurchases(API, app.getPackageName(), type, page);
                if (reply.getInt("RESPONSE_CODE", -1) != 0) return null;
                List<String> rows = strings(reply, "INAPP_PURCHASE_DATA_LIST");
                List<String> marks = strings(reply, "INAPP_DATA_SIGNATURE_LIST");
                for (int i = 0; i < rows.size(); i++) {
                    String mark = i < marks.size() ? marks.get(i) : null;
                    Receipt.Purchase purchase = Receipt.verify(rows.get(i), mark);
                    if (purchase != null) return purchase;
                }
                page = reply.getString("INAPP_CONTINUATION_TOKEN");
            } while (page != null && !page.isEmpty());
        } catch (RemoteException | RuntimeException e) {
            return null;
        }
        return null;
    }

    private static List<String> strings(Bundle bundle, String key) {
        ArrayList<String> list = bundle.getStringArrayList(key);
        return list == null ? new ArrayList<>() : list;
    }

    /** The store's numeric answers, in words a parent can act on. */
    private static String reason(int code) {
        switch (code) {
            case 1: return "خرید نیمه‌کاره ماند.";
            case 3: return "نسخه‌ی کافه‌بازارِ این دستگاه قدیمی است؛ به‌روزش کن.";
            case 4: return "این محصول در بازار پیدا نشد.";
            case 5: return "درخواستِ خرید درست نبود.";
            case 6: return "بازار خطا داد. چند لحظه بعد دوباره امتحان کن.";
            case 7: return "این برنامه از قبل خریداری شده است.";
            case 8: return "چیزی برای خرید نیست.";
            default: return "خرید انجام نشد (کد " + code + ").";
        }
    }
}
