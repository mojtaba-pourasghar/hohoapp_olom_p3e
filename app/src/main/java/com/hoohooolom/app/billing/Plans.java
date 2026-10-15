package com.hoohooolom.app.billing;

import java.util.Arrays;
import java.util.List;

/**
 * What can be bought, as the Bazaar developer panel knows it.
 *
 * The ids here must be the ids registered in the panel, letter for letter — Bazaar answers «این
 * محصول پیدا نشد» for anything else, and that is the only sign you get. They are gathered in one
 * place so correcting them is one edit, not a search through the app.
 *
 * `title` and `price` are only what the paywall writes on the button. The real price is whatever
 * the panel says; this text is for the moment before the store's own screen opens.
 */
public final class Plans {

    public static final class Plan {
        public final String sku;
        public final String type;        // Bazaar.SUBSCRIPTION or Bazaar.ONE_OFF
        public final String title;
        public final String price;
        public final String note;

        Plan(String sku, String type, String title, String price, String note) {
            this.sku = sku;
            this.type = type;
            this.title = title;
            this.price = price;
            this.note = note;
        }
    }

    /**
     * What is registered in the panel: one product, bought once, and then the app is the
     * family's for good. No subscription to run out, nothing to renew — which for a textbook
     * that is replaced every year is the honest shape.
     */
    // ⚠ The id this product is registered under in the Bazaar panel for هوهو علوم. Until the
    // product exists there, a purchase answers «این محصول پیدا نشد» — the debug build has no
    // locks, so only the release build is affected.
    public static final String SKU = "hoohoo_full_olom_p3e";

    public static final List<Plan> ALL = Arrays.asList(
        new Plan(SKU, Bazaar.ONE_OFF, "نسخه‌ی کامل هوهو علوم سوم",
                 "۲۴۰٬۰۰۰ تومان", "یک بار خرید، برای همیشه — همه‌ی درس‌ها، کاربرگ‌ها و آزمون‌ها"));

    private Plans() {}

    public static Plan bySku(String sku) {
        for (Plan plan : ALL) {
            if (plan.sku.equals(sku)) return plan;
        }
        return null;
    }
}
