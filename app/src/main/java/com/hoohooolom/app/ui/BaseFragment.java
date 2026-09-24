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
