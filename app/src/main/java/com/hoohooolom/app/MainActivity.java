package com.hoohooolom.app;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.PorterDuff;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.hoohooolom.app.data.AppState;
import com.hoohooolom.app.tts.NarrationText;
import com.hoohooolom.app.tts.SoundManager;
import com.hoohooolom.app.ui.Navigator;
import com.hoohooolom.app.ui.Screen;
import com.hoohooolom.app.ui.UiKit;
import com.hoohooolom.app.ui.mascot.HooHooView;
import com.hoohooolom.app.ui.mascot.MascotController;
import com.hoohooolom.app.ui.screens.ChaptersFragment;
import com.hoohooolom.app.ui.screens.ExamIndexFragment;
import com.hoohooolom.app.ui.screens.LessonFragment;
import com.hoohooolom.app.ui.screens.MapFragment;
import com.hoohooolom.app.ui.screens.ParentGateFragment;
import com.hoohooolom.app.ui.screens.ParentPanelFragment;
import com.hoohooolom.app.ui.screens.ProfileFragment;
import com.hoohooolom.app.ui.screens.QuizFragment;
import com.hoohooolom.app.ui.screens.ResultFragment;
import com.hoohooolom.app.ui.screens.RewardsFragment;
import com.hoohooolom.app.ui.screens.SectionsFragment;
import com.hoohooolom.app.ui.screens.SplashFragment;
import com.hoohooolom.app.ui.screens.WorksheetDownloadFragment;
import com.hoohooolom.app.ui.screens.WorksheetIndexFragment;

import java.util.Locale;

public class MainActivity extends AppCompatActivity implements Navigator {

    private MascotController mascotController;
    private View navBar;
    private View mascotOverlay;
    private Screen currentScreen;

    private View navMap, navSections, navWorksheet, navExam, navParent;

    @Override
    protected void attachBaseContext(Context newBase) {
        Locale fa = new Locale("fa", "IR");
        Configuration config = new Configuration(newBase.getResources().getConfiguration());
        config.setLocale(fa);
        config.setLayoutDirection(fa);
        Context context = newBase.createConfigurationContext(config);
        super.attachBaseContext(context);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // the words هوهو says live in res/raw/audio_manifest.txt; read them while the splash shows
        NarrationText.preload(this);

        navBar = findViewById(R.id.nav_bar);
        mascotOverlay = findViewById(R.id.mascot_overlay);

        HooHooView mascotView = findViewById(R.id.mascot_view);
        View bubbleWrap = findViewById(R.id.mascot_bubble_wrap);
        TextView bubbleText = findViewById(R.id.mascot_bubble);
        mascotController = new MascotController(mascotView, mascotOverlay, bubbleWrap, bubbleText);
        findViewById(R.id.mascot_bubble_close).setOnClickListener(v -> mascotController.dismissBubble());

        enableMascotDragging();

        navMap = findViewById(R.id.nav_map);
        navSections = findViewById(R.id.nav_sections);
        navWorksheet = findViewById(R.id.nav_worksheet);
        navExam = findViewById(R.id.nav_exam);
        navParent = findViewById(R.id.nav_parent);

        bindNavItem(navMap, R.drawable.ic_pin, "نقشه", () -> go(Screen.MAP));
        bindNavItem(navSections, R.drawable.ic_pencil, "تمرین", () -> go(Screen.SECTIONS));
        bindNavItem(navWorksheet, R.drawable.ic_sheet, "کاربرگ", () -> go(Screen.WORKSHEET_INDEX));
        bindNavItem(navExam, R.drawable.ic_target, "آزمون", () -> go(Screen.EXAM_INDEX));
        bindNavItem(navParent, R.drawable.ic_shield, "والدین", this::goParent);

        if (savedInstanceState == null) {
            go(Screen.SPLASH);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        SoundManager sound = SoundManager.get();
        if (sound != null) sound.startMusic();
    }

    @Override
    protected void onPause() {
        SoundManager sound = SoundManager.get();
        if (sound != null) sound.pauseMusic();
        super.onPause();
    }

    /**
     * Lets the child pick هوهو up and put it wherever it isn't in the way. The spot is kept as a
     * fraction of the screen so it survives rotation and comes back next time.
     */
    @SuppressLint("ClickableViewAccessibility")
    private void enableMascotDragging() {
        final float[] down = new float[2];
        final float[] origin = new float[2];
        final boolean[] dragging = {false};

        mascotOverlay.setOnTouchListener((view, event) -> {
            ViewGroup parent = (ViewGroup) view.getParent();
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    down[0] = event.getRawX();
                    down[1] = event.getRawY();
                    origin[0] = view.getTranslationX();
                    origin[1] = view.getTranslationY();
                    dragging[0] = false;
                    return true;
                case MotionEvent.ACTION_MOVE: {
                    float dx = event.getRawX() - down[0];
                    float dy = event.getRawY() - down[1];
                    if (!dragging[0] && Math.hypot(dx, dy) < UiKit.dp(this, 8)) return true;
                    dragging[0] = true;
                    view.setTranslationX(clampX(parent, view, origin[0] + dx));
                    view.setTranslationY(clampY(parent, view, origin[1] + dy));
                    return true;
                }
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (dragging[0]) {
                        saveMascotPosition(parent, view);
                    } else {
                        // a tap on هوهو is how the child asks for her tooltip back
                        mascotController.allowBubble();
                        view.performClick();
                    }
                    return true;
                default:
                    return false;
            }
        });

        // wait for a real layout pass before restoring, otherwise the sizes are all zero
        mascotOverlay.getViewTreeObserver().addOnGlobalLayoutListener(
            new android.view.ViewTreeObserver.OnGlobalLayoutListener() {
                @Override
                public void onGlobalLayout() {
                    mascotOverlay.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                    restoreMascotPosition();
                }
            });
    }

    private float clampX(ViewGroup parent, View view, float translation) {
        float min = -view.getLeft();
        float max = parent.getWidth() - view.getLeft() - view.getWidth();
        return Math.max(min, Math.min(translation, max));
    }

    private float clampY(ViewGroup parent, View view, float translation) {
        float min = -view.getTop();
        float max = parent.getHeight() - view.getTop() - view.getHeight();
        return Math.max(min, Math.min(translation, max));
    }

    private void saveMascotPosition(ViewGroup parent, View view) {
        if (parent.getWidth() == 0 || parent.getHeight() == 0) return;
        float x = (view.getLeft() + view.getTranslationX()) / parent.getWidth();
        float y = (view.getTop() + view.getTranslationY()) / parent.getHeight();
        AppState.get().setMascotPosition(x, y);
    }

    /**
     * Puts هوهو back where the child left her. Anything that would land her off-screen — a saved
     * spot from a different screen size, say — is ignored so she can never go missing.
     */
    private void restoreMascotPosition() {
        AppState s = AppState.get();
        if (s.mascotX < 0 || s.mascotY < 0) return;
        ViewGroup parent = (ViewGroup) mascotOverlay.getParent();
        if (parent == null || parent.getWidth() == 0 || mascotOverlay.getWidth() == 0) return;
        if (s.mascotX > 0.97f || s.mascotY > 0.97f) return;

        float targetX = s.mascotX * parent.getWidth();
        float targetY = s.mascotY * parent.getHeight();
        mascotOverlay.setTranslationX(clampX(parent, mascotOverlay, targetX - mascotOverlay.getLeft()));
        mascotOverlay.setTranslationY(clampY(parent, mascotOverlay, targetY - mascotOverlay.getTop()));
    }

    private interface Action { void run(); }

    private void bindNavItem(View item, int iconRes, String label, Action onClick) {
        ImageView icon = item.findViewById(R.id.nav_icon);
        TextView text = item.findViewById(R.id.nav_label);
        icon.setImageResource(iconRes);
        text.setText(label);
        item.setOnClickListener(v -> onClick.run());
        UiKit.tapSound(item);
    }

    @Override
    public void go(Screen screen) {
        go(screen, null);
    }

    @Override
    public void go(Screen screen, Bundle args) {
        Fragment fragment = fragmentFor(screen, args);
        getSupportFragmentManager().beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commitAllowingStateLoss();

        boolean chromeVisible = screen != Screen.SPLASH && screen != Screen.PARENT_GATE;
        navBar.setVisibility(chromeVisible ? View.VISIBLE : View.GONE);
        mascotOverlay.setVisibility(screen == Screen.SPLASH ? View.GONE : View.VISIBLE);
        mascotController.allowBubble(); // a new screen starts with هوهو free to talk again
        mascotController.hideBubble();
        mascotController.returnHome(); // don't leave her parked next to the last screen's content

        if (screen != Screen.SPLASH && currentScreen != null) {
            mascotController.onScreenTransition();
            SoundManager sound = SoundManager.get();
            if (sound != null) sound.page();
        }
        currentScreen = screen;
        updateNavHighlight(screen);
    }

    @Override
    public void goParent() {
        if (AppState.get().parentUnlockedThisSession) {
            go(Screen.PARENT_PANEL);
        } else {
            go(Screen.PARENT_GATE);
        }
    }

    @Override
    public MascotController mascot() {
        return mascotController;
    }

    private void updateNavHighlight(Screen screen) {
        int active = getColor(R.color.orange);
        int inactive = getColor(R.color.text_faint);
        setNavColor(navMap, screen == Screen.MAP ? active : inactive);
        setNavColor(navSections, screen == Screen.SECTIONS ? active : inactive);
        setNavColor(navWorksheet,
            screen == Screen.WORKSHEET_INDEX || screen == Screen.WORKSHEET_DOWNLOAD ? active : inactive);
        setNavColor(navExam, screen == Screen.EXAM_INDEX ? active : inactive);
        setNavColor(navParent, screen == Screen.PARENT_PANEL || screen == Screen.PARENT_GATE ? active : inactive);
    }

    private void setNavColor(View item, int color) {
        ImageView icon = item.findViewById(R.id.nav_icon);
        TextView text = item.findViewById(R.id.nav_label);
        icon.setColorFilter(color, PorterDuff.Mode.SRC_IN);
        text.setTextColor(color);
    }

    @NonNull
    private Fragment fragmentFor(Screen screen, Bundle args) {
        Fragment f;
        switch (screen) {
            case SPLASH: f = new SplashFragment(); break;
            case MAP: f = new MapFragment(); break;
            case CHAPTERS: f = new ChaptersFragment(); break;
            case LESSON: f = new LessonFragment(); break;
            case SECTIONS: f = new SectionsFragment(); break;
            case WORKSHEET_INDEX: f = new WorksheetIndexFragment(); break;
            case WORKSHEET_DOWNLOAD: f = new WorksheetDownloadFragment(); break;
            case EXAM_INDEX: f = new ExamIndexFragment(); break;
            case QUIZ: f = new QuizFragment(); break;
            case RESULT: f = new ResultFragment(); break;
            case REWARDS: f = new RewardsFragment(); break;
            case PROFILE: f = new ProfileFragment(); break;
            case PARENT_GATE: f = new ParentGateFragment(); break;
            case PARENT_PANEL: f = new ParentPanelFragment(); break;
            default: throw new IllegalArgumentException("Unknown screen " + screen);
        }
        if (args != null) f.setArguments(args);
        return f;
    }
}
