package com.hoohooolom.app;

import android.app.Application;

import com.hoohooolom.app.data.AppState;
import com.hoohooolom.app.tts.SoundManager;
import com.hoohooolom.app.tts.TtsManager;

public class HooHooApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        AppState.init(this);
        TtsManager.init(this);
        SoundManager.init(this);
    }
}
