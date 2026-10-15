package com.hoohooolom.app.ui.screens;

import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.hoohooolom.app.R;
import com.hoohooolom.app.billing.Bazaar;
import com.hoohooolom.app.billing.Entitlement;
import com.hoohooolom.app.billing.Plans;
import com.hoohooolom.app.billing.Receipt;
import com.hoohooolom.app.data.Access;
import com.hoohooolom.app.ui.BaseFragment;
import com.hoohooolom.app.ui.UiKit;

/**
 * «ادامه‌ی درس‌ها» — the one screen that asks for money.
 *
 * It is reached by tapping a locked chapter, worksheet or exam, so it always knows what the
 * child was trying to reach and says so by name: a paywall that answers «why can I not open
 * this?» rather than one that interrupts.
 *
 * What is already free is spelled out, so a parent can see they are not being asked to pay for
 * a look at the app. The plans come from {@link Plans}; the buying is Bazaar's own screen.
 */
public class PaywallFragment extends BaseFragment {

    private View root;
    private Bazaar bazaar;
    private String busyMessage = "";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_list_index, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        root = view;
        bazaar = new Bazaar(requireContext());
        ((TextView) view.findViewById(R.id.screen_title)).setText("ادامه‌ی درس‌ها");
        view.findViewById(R.id.chip_container).setVisibility(View.GONE);
        render();
    }

    @Override
    public void onDestroyView() {
        if (bazaar != null) bazaar.disconnect();
        super.onDestroyView();
    }

    private void render() {
        if (root == null || !isAdded()) return;
        Bundle args = getArguments();
        String wanted = args != null ? args.getString("wanted", "") : "";

        // a debug build has the locks off; saying «خریده شده» there would be a lie that is
        // easy to carry into a screenshot
        boolean testBuild = !Entitlement.enforced();
        ((TextView) root.findViewById(R.id.chapter_title)).setText(
            testBuild ? "نسخه‌ی آزمایشی"
                : Entitlement.unlocked(requireContext()) ? "نسخه‌ی کامل فعال است" : "ادامه‌ی درس‌ها");
        ((TextView) root.findViewById(R.id.chapter_subtitle)).setText(
            Entitlement.unlocked(requireContext())
                ? "همه‌ی درس‌ها، کاربرگ‌ها و آزمون‌ها باز است. ممنون که هوهو را همراهی می‌کنی!"
                : wanted.isEmpty()
                    ? "بخشِ رایگانِ اپ همیشه باز است؛ بقیه‌ی درس‌ها با یک بار خرید."
                    : wanted + " در نسخه‌ی کامل است.");

        LinearLayout list = root.findViewById(R.id.list_container);
        list.removeAllViews();

        if (testBuild) {
            list.addView(card("⚙ نسخه‌ی آزمایشی — قفل‌ها خاموش‌اند",
                "این بیلدِ debug است و همه‌چیز بازِ باز است، بدونِ خرید. نسخه‌ای که به "
                    + "کافه‌بازار می‌رود release است و قفل‌ها را اعمال می‌کند.", R.color.orange_border));
            list.addView(backLink());
            return;
        }

        if (Entitlement.unlocked(requireContext())) {
            list.addView(card("✓ باز است",
                "هر چیزی که در اپ هست، باز است. اگر روی گوشیِ دیگری هم با همین حسابِ بازار وارد "
                    + "شوی، همان‌جا هم باز می‌شود.", R.color.teal_border));
            list.addView(backLink());
            return;
        }

        list.addView(card("همین حالا رایگان است", Access.freeSummary()
            + "\n\nصدای هوهو روی همه‌ی درس‌ها رایگان است — چه بخشِ رایگان چه نسخه‌ی کامل.",
            R.color.teal_border));

        if (!busyMessage.isEmpty()) {
            list.addView(card("⚠ " + busyMessage,
                "اگر برنامه‌ی کافه‌بازار روی گوشی نیست یا نسخه‌اش قدیمی است، اول آن را نصب و "
                    + "به‌روز کن. بعد همین صفحه را دوباره باز کن.", R.color.orange_border));
        }

        for (Plans.Plan plan : Plans.ALL) list.addView(planCard(plan));

        list.addView(restoreLink());
        list.addView(backLink());
    }

    private View planCard(Plans.Plan plan) {
        LinearLayout card = UiKit.column(requireContext());
        int pad = UiKit.dp(requireContext(), 16);
        card.setPadding(pad, pad, pad, pad);
        UiKit.applyCardBg(card, requireContext(), R.color.bg_card, R.color.teal_border);
        card.setLayoutParams(UiKit.marginParams(requireContext(), 0, 10));

        card.addView(UiKit.text(requireContext(), plan.title, 15f, R.color.text_primary, true));
        card.addView(UiKit.text(requireContext(), plan.note, 12f, R.color.text_muted, false));

        TextView button = UiKit.primaryButton(requireContext(),
            plan.price.isEmpty() ? "خرید از کافه‌بازار" : "خرید — " + plan.price,
            ContextCompat.getColor(requireContext(), R.color.teal), R.color.white);
        button.setOnClickListener(v -> buy(plan));
        card.addView(button, UiKit.marginParams(requireContext(), 10, 0));
        return card;
    }

    private void buy(Plans.Plan plan) {
        busyMessage = "";
        bazaar.buy(requireActivity(), plan.sku, plan.type, new Bazaar.Answer() {
            @Override public void onPurchase(Receipt.Purchase purchase) {
                // the real answer comes back through the activity; nothing to do here
            }

            @Override public void onProblem(String message) {
                if (!isAdded()) return;
                busyMessage = message;
                render();
            }
        });
    }

    /**
     * «خریدم را برگردان» — for a new phone, or after a reinstall.
     *
     * Bazaar keeps the purchase against the account, so this needs no receipt and no support
     * ticket: it asks the store what this account owns and believes the signature.
     */
    private View restoreLink() {
        TextView link = UiKit.text(requireContext(), "خریدم را از بازار برگردان", 13f,
            R.color.teal_dark, true);
        link.setGravity(Gravity.CENTER);
        int vp = UiKit.dp(requireContext(), 13);
        link.setPadding(0, vp, 0, vp);
        UiKit.applyCardBg(link, requireContext(), R.color.bg_card, R.color.teal_border);
        link.setLayoutParams(UiKit.marginParams(requireContext(), 6, 4));
        link.setOnClickListener(v -> {
            link.setText("در حال پرسیدن از بازار…");
            bazaar.restore(new Bazaar.Answer() {
                @Override public void onPurchase(Receipt.Purchase purchase) {
                    if (!isAdded()) return;
                    if (purchase == null) {
                        busyMessage = "با این حسابِ بازار خریدی پیدا نشد.";
                        render();
                        return;
                    }
                    Entitlement.grant(requireContext(), purchase.sku, purchase.token, purchase.until);
                    Toast.makeText(requireContext(), "خریدت برگشت!", Toast.LENGTH_LONG).show();
                    render();
                }

                @Override public void onProblem(String message) {
                    if (!isAdded()) return;
                    busyMessage = message;
                    render();
                }
            });
        });
        UiKit.tapSound(link);
        return link;
    }

    private View card(String title, String body, int borderRes) {
        LinearLayout card = UiKit.column(requireContext());
        int pad = UiKit.dp(requireContext(), 16);
        card.setPadding(pad, pad, pad, pad);
        UiKit.applyCardBg(card, requireContext(), R.color.bg_card, borderRes);
        card.setLayoutParams(UiKit.marginParams(requireContext(), 0, 10));
        card.addView(UiKit.text(requireContext(), title, 14.5f, R.color.text_primary, true));
        TextView text = UiKit.text(requireContext(), body, 12.5f, R.color.text_muted, false);
        text.setLineSpacing(UiKit.dp(requireContext(), 5), 1f);
        card.addView(text, UiKit.marginParams(requireContext(), 6, 0));
        return card;
    }

    private View backLink() {
        TextView link = UiKit.text(requireContext(), "‹ برگرد به درس‌ها", 13.5f, R.color.teal_dark, true);
        link.setGravity(Gravity.CENTER);
        int vp = UiKit.dp(requireContext(), 14);
        link.setPadding(0, vp, 0, vp);
        UiKit.applyCardBg(link, requireContext(), R.color.bg_card, R.color.teal_border);
        link.setLayoutParams(UiKit.marginParams(requireContext(), 6, 8));
        link.setOnClickListener(v -> nav().go(com.hoohooolom.app.ui.Screen.MAP));
        UiKit.tapSound(link);
        return link;
    }

    /** Called by the activity when Bazaar's payment screen closes. */
    public void onPurchaseResult(Receipt.Purchase purchase) {
        if (!isAdded()) return;
        if (purchase == null) {
            busyMessage = "خرید کامل نشد.";
        } else {
            Entitlement.grant(requireContext(), purchase.sku, purchase.token, purchase.until);
            busyMessage = "";
            Toast.makeText(requireContext(), "خرید انجام شد. همه‌ی درس‌ها باز است!",
                Toast.LENGTH_LONG).show();
        }
        render();
    }

    @Override
    protected String entryTip() {
        return "اینجا می‌توانی ادامه‌ی درس‌ها را باز کنی. بخشِ رایگان همیشه مالِ خودت است!";
    }
}
