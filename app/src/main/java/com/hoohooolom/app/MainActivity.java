package com.hoohooolom.app;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.PorterDuff;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import android.widget.Toast;
import java.util.Stack;

import com.hoohooolom.app.billing.Bazaar;
import com.hoohooolom.app.billing.Entitlement;
import com.hoohooolom.app.billing.Receipt;

import com.hoohooolom.app.data.AppState;
import com.hoohooolom.app.tts.NarrationText;
import com.hoohooolom.app.tts.SoundManager;
import com.hoohooolom.app.data.Notices;
import com.hoohooolom.app.tts.VoiceStore;
import com.hoohooolom.app.ui.Announcements;
import com.hoohooolom.app.ui.BaseFragment;
import com.hoohooolom.app.ui.Navigator;
import com.hoohooolom.app.ui.Screen;
import com.hoohooolom.app.ui.UiKit;
import com.hoohooolom.app.ui.mascot.HooHooView;
import com.hoohooolom.app.ui.mascot.MascotController;
import com.hoohooolom.app.ui.screens.ChaptersFragment;
import com.hoohooolom.app.ui.screens.ExamIndexFragment;
import com.hoohooolom.app.ui.screens.LessonFragment;
import com.hoohooolom.app.ui.screens.MapFragment;
import com.hoohooolom.app.ui.screens.OnlineExamFragment;
import com.hoohooolom.app.ui.screens.ParentGateFragment;
import com.hoohooolom.app.ui.screens.ParentPanelFragment;
import com.hoohooolom.app.ui.screens.PaywallFragment;
import com.hoohooolom.app.ui.screens.ProfileFragment;
import com.hoohooolom.app.ui.screens.QuizFragment;
import com.hoohooolom.app.ui.screens.ResultFragment;
import com.hoohooolom.app.ui.screens.RewardsFragment;
import com.hoohooolom.app.ui.screens.SectionsFragment;
import com.hoohooolom.app.ui.screens.SplashFragment;
import com.hoohooolom.app.ui.screens.VoiceDownloadFragment;
import com.hoohooolom.app.ui.screens.WorksheetDownloadFragment;
import com.hoohooolom.app.ui.screens.WorksheetIndexFragment;

import java.util.Locale;

public class MainActivity extends AppCompatActivity implements Navigator {

    private MascotController mascotController;
    private View navBar;
    private View mascotOverlay;
    private Screen currentScreen;

    private View navMap, navSections, navWorksheet, navExam, navParent;
    private long lastBackPressTime = 0;

    /**
     * Where the child has been, so back retraces it.
     *
     * The arguments travel with the screen. Without them, backing out of a lesson landed on
     * «chapter 1 section 1» whatever chapter the child had actually come from, which reads as the
     * app losing their place.
     */
    private static final class Step {
        final Screen screen;
        final Bundle args;
        Step(Screen screen, Bundle args) {
            this.screen = screen;
            this.args = args;
        }
    }

    private final Stack<Step> backStack = new Stack<>();
    private Bundle currentArgs;

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

        // the clips themselves are on the host, not in the APK. With a connection, the shared
        // ones and the chapter the child is on come down now so the first lesson already has
        // هوهو's own voice; the other chapters trickle in behind that.
        VoiceStore.startOnLaunch(this, AppState.get().taughtChapter);

        // Asks Bazaar what this account owns, once per launch. That is how a subscription bought
        // on another phone — or on this one before a reinstall — comes back without the parent
        // doing anything. A purchase that has gone is cleared, but only when the store actually
        // answered: a phone with no signal must never lock a paid child out.
        restore = new Bazaar(this);
        restore.restore(new Bazaar.Answer() {
            @Override public void onPurchase(Receipt.Purchase purchase) {
                if (purchase == null) Entitlement.revoke(MainActivity.this);
                else Entitlement.grant(MainActivity.this, purchase.sku, purchase.token, purchase.until);
            }

            @Override public void onProblem(String message) {
                // no store, no connection, nothing to conclude — what is stored stays
            }
        });

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

        // The news. Whatever is already on the device is shown at once; the host is asked for
        // more in the background, and anything new that arrives goes to the phone's tray so it
        // can bring the child back tomorrow rather than only when they happen to open the app.
        Announcements.post(this);
        Notices.refresh(this, () -> {
            Announcements.post(this);
            Announcements.askIfUseful(this);
        });
        Announcements.askIfUseful(this);

        if (savedInstanceState == null) {
            Screen opened = screenFromNotice(getIntent());
            go(opened != null ? opened : Screen.SPLASH);
        }

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (currentScreen == Screen.SPLASH) {
                    finish();
                    return;
                }
                // the screen itself gets first refusal: inside a lesson or a quiz, back means
                // «the step before this one», not «throw this away»
                Fragment showing = getSupportFragmentManager().findFragmentById(R.id.fragment_container);
                if (showing instanceof BaseFragment && ((BaseFragment) showing).onBackPressed()) {
                    return;
                }
                if (!backStack.isEmpty()) {
                    Step prev = backStack.pop();
                    go(prev.screen, prev.args, false);
                    return;
                }
                // nothing behind this screen: the first press warns, the second one leaves
                long now = System.currentTimeMillis();
                if (now - lastBackPressTime < 2000) {
                    finish();
                } else {
                    lastBackPressTime = now;
                    Toast.makeText(MainActivity.this,
                        "برای خروج از برنامه دوباره دکمه بازگشت را بزنید", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    /** Tapped a notice in the tray while the app was already running. */
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        Screen opened = screenFromNotice(intent);
        if (opened != null) go(opened);
    }

    /**
     * The screen a notice points at, or null. An unknown name is not an error — an older copy of
     * the app simply opens as usual rather than crashing on news written for a newer one.
     */
    private Screen screenFromNotice(Intent intent) {
        if (intent == null) return null;
        String id = intent.getStringExtra("notice_id");
        if (id != null) Notices.markSeen(this, id);
        String name = intent.getStringExtra("notice_screen");
        if (name == null || name.isEmpty()) return null;
        try {
            return Screen.valueOf(name);
        } catch (IllegalArgumentException unknown) {
            return null;
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

    /** Kept so the binding to the store can be let go of when the activity goes. */
    private Bazaar restore;

    @Override
    protected void onDestroy() {
        if (restore != null) restore.disconnect();
        super.onDestroy();
    }

    /**
     * The answer from Bazaar's payment screen.
     *
     * It arrives at the activity, not at the fragment that asked, so it is handed on to the
     * paywall if that is still what is on screen — and the purchase is verified and recorded
     * either way, so a parent who hits «back» the moment they have paid still gets what they
     * paid for.
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != Bazaar.PURCHASE_REQUEST) return;

        Receipt.Purchase purchase = Bazaar.fromResult(data);
        if (purchase != null) {
            Entitlement.grant(this, purchase.sku, purchase.token, purchase.until);
        }
        Fragment showing = getSupportFragmentManager().findFragmentById(R.id.fragment_container);
        if (showing instanceof PaywallFragment) {
            ((PaywallFragment) showing).onPurchaseResult(purchase);
        }
    }

    public void go(Screen screen) {
        go(screen, null, true);
    }

    @Override
    public void go(Screen screen, Bundle args) {
        go(screen, args, true);
    }

    public void go(Screen screen, Bundle args, boolean addToHistory) {
        if (addToHistory && currentScreen != null && currentScreen != Screen.SPLASH) {
            // Re-rendering the same screen with new arguments — picking another chapter from the
            // chip row — is not a place the child has been; it replaces the one they are on.
            // Without this, back taps through every chip they tried before leaving.
            if (!backStack.isEmpty() && backStack.peek().screen == currentScreen
                && screen == currentScreen) {
                backStack.pop();
            }
            if (screen != currentScreen) backStack.push(new Step(currentScreen, currentArgs));
        }
        currentArgs = args;
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
            case VOICE_DOWNLOAD: f = new VoiceDownloadFragment(); break;
            case PAYWALL: f = new PaywallFragment(); break;
            case EXAM_INDEX: f = new ExamIndexFragment(); break;
            case ONLINE_EXAM: f = new OnlineExamFragment(); break;
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
