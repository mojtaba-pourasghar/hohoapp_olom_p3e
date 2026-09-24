package com.hoohooolom.app.ui.screens;

import android.animation.ObjectAnimator;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hoohooolom.app.R;
import com.hoohooolom.app.tts.TtsManager;
import com.hoohooolom.app.tts.VoiceSetup;
import com.hoohooolom.app.ui.BaseFragment;
import com.hoohooolom.app.ui.Screen;
import com.hoohooolom.app.ui.mascot.HooHooView;

public class SplashFragment extends BaseFragment {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable autoAdvance;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_splash, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        HooHooView owl = view.findViewById(R.id.splash_owl);
        owl.setFlying(true);

        ProgressBar progress = view.findViewById(R.id.splash_progress);
        ObjectAnimator anim = ObjectAnimator.ofInt(progress, "progress", 8, 100);
        anim.setDuration(1600);
        anim.start();

        view.findViewById(R.id.splash_start).setOnClickListener(v -> goToMap());

        TtsManager tts = TtsManager.get();
        if (tts != null) tts.speak("سلام! من هوهو هستم، معلم علوم تو. بیا با هم علوم سوم دبستان را کشف کنیم.");

        autoAdvance = this::goToMap;
        handler.postDelayed(autoAdvance, 2600);
    }

    private void goToMap() {
        if (!isAdded()) return;
        handler.removeCallbacks(autoAdvance);
        nav().go(Screen.MAP);
        // the lessons are spoken, so tell the parent right away if this device has no Persian voice
        VoiceSetup.promptIfMissing(requireContext());
    }

    @Override
    public void onDestroyView() {
        handler.removeCallbacks(autoAdvance);
        super.onDestroyView();
    }

    @Override
    protected String entryTip() {
        return null; // splash speaks its own greeting above; skip the generic entry-tip
    }
}
