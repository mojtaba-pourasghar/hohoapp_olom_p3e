package com.hoohooolom.app.ui;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.hoohooolom.app.data.AppState;
import com.hoohooolom.app.ui.mascot.MascotController;

public abstract class BaseFragment extends Fragment {
    protected AppState state() {
        return AppState.get();
    }

    protected Navigator nav() {
        return (Navigator) requireActivity();
    }

    protected MascotController mascot() {
        return nav().mascot();
    }

    /** Whether everything is open — the free part plus what the subscription adds. */
    protected boolean paid() {
        return com.hoohooolom.app.billing.Entitlement.unlocked(requireContext());
    }

    /**
     * Sends the child to the paywall, telling it what they were reaching for.
     *
     * `what` becomes the first line of that screen («فصل ۳ در نسخه‌ی کامل است»), so the answer to
     * «why can I not open this?» is on the screen that asks for money, not a step away from it.
     */
    protected void goPaywall(String what) {
        android.os.Bundle args = new android.os.Bundle();
        args.putString("wanted", what == null ? "" : what);
        nav().go(Screen.PAYWALL, args);
    }

    /**
     * A chance to deal with the back button before the app leaves the screen.
     *
     * A lesson and a quiz both have steps inside them, and a child pressing back means «the step
     * before this one» far more often than «throw this lesson away». Returning true says the
     * press was used; false lets the activity go back a screen.
     */
    public boolean onBackPressed() {
        return false;
    }

    /** Shown once when the screen first appears; screens override to give هوهو a contextual tip. */
    protected String entryTip() {
        return null;
    }

    @Override
    public void onResume() {
        super.onResume();
        String tip = entryTip();
        if (tip != null) mascot().say(tip);
    }
}
