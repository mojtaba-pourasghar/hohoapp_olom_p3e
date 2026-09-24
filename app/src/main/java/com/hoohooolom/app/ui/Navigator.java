package com.hoohooolom.app.ui;

import android.os.Bundle;

import com.hoohooolom.app.ui.mascot.MascotController;

public interface Navigator {
    void go(Screen screen);
    void go(Screen screen, Bundle args);
    /** Opens the parent gate; if already unlocked this session, goes straight to the parent panel. */
    void goParent();
    MascotController mascot();
}
