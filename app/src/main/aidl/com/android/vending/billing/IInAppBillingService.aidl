/*
 * The in-app billing interface, as Bazaar (کافه‌بازار) exposes it.
 *
 * This is the long-standing IAB v3 interface: Bazaar kept the same package and method shapes, so
 * the file sits under com.android.vending.billing even though the service lives in
 * com.farsitel.bazaar. The names and argument order are fixed by the service on the other side —
 * nothing here may be renamed or reordered.
 */
package com.android.vending.billing;

import android.os.Bundle;

interface IInAppBillingService {

    /** Whether this version of the store supports the given purchase type ("inapp" / "subs"). */
    int isBillingSupported(int apiVersion, String packageName, String type);

    /** Prices and titles for up to 20 SKUs, named in the bundle under ITEM_ID_LIST. */
    Bundle getSkuDetails(int apiVersion, String packageName, String type, in Bundle skusBundle);

    /** An Intent to start the store's own purchase screen with. */
    Bundle getBuyIntent(int apiVersion, String packageName, String sku, String type,
                        String developerPayload);

    /** What this account already owns, a page at a time. */
    Bundle getPurchases(int apiVersion, String packageName, String type, String continuationToken);

    /** Uses up a one-off purchase so it can be bought again. Not for subscriptions. */
    int consumePurchase(int apiVersion, String packageName, String purchaseToken);
}
