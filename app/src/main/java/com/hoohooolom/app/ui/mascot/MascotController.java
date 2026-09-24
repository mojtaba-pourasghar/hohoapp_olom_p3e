package com.hoohooolom.app.ui.mascot;

import android.animation.ObjectAnimator;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.TextView;

import com.hoohooolom.app.data.AppState;
import com.hoohooolom.app.tts.LessonAudio;
import com.hoohooolom.app.tts.NarrationText;
import com.hoohooolom.app.tts.HootSoundPlayer;
import com.hoohooolom.app.tts.SoundManager;
import com.hoohooolom.app.tts.TtsManager;

import java.util.Random;

/**
 * Ties the owl view + its speech bubble + TTS together into the handful of things the rest of the
 * app asks هوهو to do: say a line, celebrate a right answer, comfort a wrong one, or fly a little
 * on screen and loading transitions. هوهو does NOT fly nonstop — only at those moments.
 *
 * The bubble can be closed with its × and switched off entirely from the parent panel, because a
 * character who talks over everything gets annoying fast.
 */
public class MascotController {
    private final HooHooView owl;
    private final View overlay;      // the draggable container holding bubble + owl
    private final View bubbleWrap;
    private final TextView bubbleText;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();

    private Runnable pendingRevert;
    private boolean awayFromHome = false;
    /** Set when the child closes the tooltip with its ×; cleared when they change screen. */
    private boolean bubbleDismissed = false;
    private float homeTranslationX, homeTranslationY;

    public MascotController(HooHooView owl, View overlay, View bubbleWrap, TextView bubbleText) {
        this.owl = owl;
        this.overlay = overlay;
        this.bubbleWrap = bubbleWrap;
        this.bubbleText = bubbleText;
    }

    private boolean flyingAllowed() {
        try {
            return AppState.get().mascotFlies();
        } catch (Exception e) {
            return true;
        }
    }

    private boolean bubblesAllowed() {
        try {
            return AppState.get().tipsEnabled();
        } catch (Exception e) {
            return true;
        }
    }

    private void setBubble(String text) {
        if (bubbleWrap == null || bubbleText == null) return;
        if (!bubblesAllowed() || bubbleDismissed || text == null || text.isEmpty()) {
            bubbleWrap.setVisibility(View.GONE);
            return;
        }
        bubbleText.setText(text);
        bubbleWrap.setVisibility(View.VISIBLE);
    }

    /** Puts the tooltip away for now; the next line هوهو says brings it back. */
    public void hideBubble() {
        if (bubbleWrap != null) bubbleWrap.setVisibility(View.GONE);
    }

    /**
     * The × on the bubble. Closing it means closing it: هوهو keeps teaching and the same words
     * stay on screen in the lesson card, but the tooltip stops popping back up for the rest of
     * this screen. Changing screen, or tapping هوهو herself, brings it back.
     */
    public void dismissBubble() {
        bubbleDismissed = true;
        hideBubble();
    }

    /** Lets the tooltip speak again — on a screen change, or when the child taps هوهو. */
    public void allowBubble() {
        bubbleDismissed = false;
    }

    public boolean isBubbleDismissed() {
        return bubbleDismissed;
    }

    /**
     * Puts a line in the bubble without speaking it — for screens that drive their own audio
     * (the lesson plays recorded narration) and just want هوهو's mouth to move along.
     */
    public void showBubble(String text, boolean speaking) {
        setBubble(text);
        owl.setSpeaking(speaking);
    }

    /** هوهو speaks a line and its beak animates while the audio plays. */
    public void say(String text) {
        setBubble(text);
        owl.setSpeaking(true);
        // a line that is in the manifest has a key, so a recorded clip is used once one exists;
        // otherwise this falls through to the device voice reading the same words
        LessonAudio.play(owl.getContext(), NarrationText.keyForText(text), text,
            new LessonAudio.PlaybackListener() {
                @Override public void onStarted(long durationMs) { }
                @Override public void onFinished() { owl.post(() -> owl.setSpeaking(false)); }
            });
    }

    /** Correct answer: happy bounce, a sparkle, sometimes a hoot, then back to idle. */
    public void celebrate(String praiseText) {
        cancelPendingRevert();
        owl.setMood(HooHooView.Mood.HAPPY);
        owl.setFlying(true);
        bounce();
        SoundManager sound = SoundManager.get();
        if (sound != null) sound.star();
        if (random.nextFloat() < 0.3f) HootSoundPlayer.playHoot();
        if (praiseText != null) say(praiseText);
        pendingRevert = () -> {
            owl.setMood(HooHooView.Mood.IDLE);
            owl.setFlying(false);
        };
        handler.postDelayed(pendingRevert, 2600);
    }

    /** Wrong answer: thoughtful, not flying — no need to make the kid feel worse. */
    public void comfort(String encouragementText) {
        cancelPendingRevert();
        owl.setMood(HooHooView.Mood.THINKING);
        owl.setFlying(false);
        if (encouragementText != null) say(encouragementText);
        pendingRevert = () -> owl.setMood(HooHooView.Mood.IDLE);
        handler.postDelayed(pendingRevert, 2600);
    }

    /**
     * Moves هوهو alongside whatever is being explained — and only alongside.
     *
     * She keeps the horizontal position she was left in, which is the edge of the screen, and
     * travels vertically so she is level with the picture. She never crosses into the lesson
     * itself: her speech bubble hangs above her head, so any step towards the middle ends with
     * the bubble sitting on top of the very thing she is pointing at. The child still sees her
     * fly across and follows her to the right part of the screen; she just stops at the edge.
     *
     * Call returnHome() when the explanation is over.
     */
    public void flyTo(View target) {
        if (overlay == null || target == null || target.getHeight() == 0) return;
        if (!flyingAllowed()) return;

        View root = (View) overlay.getParent();
        if (root == null || overlay.getHeight() == 0) return;

        int[] targetPos = new int[2];
        int[] ownPos = new int[2];
        int[] rootPos = new int[2];
        target.getLocationInWindow(targetPos);
        overlay.getLocationInWindow(ownPos);
        root.getLocationInWindow(rootPos);

        float gap = overlay.getResources().getDisplayMetrics().density * 8f;

        // level with the foot of the picture, so the bubble rises beside it rather than over it
        float wantedTop = targetPos[1] + target.getHeight() - overlay.getHeight();
        float minTop = rootPos[1] + gap;
        float maxTop = rootPos[1] + root.getHeight() - overlay.getHeight() - gap;
        if (maxTop < minTop) maxTop = minTop;
        if (wantedTop < minTop) wantedTop = minTop;
        if (wantedTop > maxTop) wantedTop = maxTop;

        if (!awayFromHome) {
            homeTranslationX = overlay.getTranslationX();
            homeTranslationY = overlay.getTranslationY();
            awayFromHome = true;
        }

        // translationX is left exactly as it was: she stays at her edge of the screen
        glide(overlay.getTranslationX(),
              overlay.getTranslationY() + (wantedTop - ownPos[1]));
    }

    /** Settles back wherever the owl was before it flew off to explain something. */
    public void returnHome() {
        if (overlay == null || !awayFromHome) return;
        awayFromHome = false;
        glide(homeTranslationX, homeTranslationY);
    }

    private void glide(float toX, float toY) {
        cancelPendingRevert();
        owl.setFlying(true);
        ObjectAnimator x = ObjectAnimator.ofFloat(overlay, View.TRANSLATION_X, toX);
        ObjectAnimator y = ObjectAnimator.ofFloat(overlay, View.TRANSLATION_Y, toY);
        x.setDuration(750);
        y.setDuration(750);
        x.setInterpolator(new AccelerateDecelerateInterpolator());
        y.setInterpolator(new AccelerateDecelerateInterpolator());
        x.start();
        y.start();
        pendingRevert = () -> owl.setFlying(false);
        handler.postDelayed(pendingRevert, 800);
    }

    /** Brief flutter when navigating between screens. */
    public void onScreenTransition() {
        cancelPendingRevert();
        owl.setFlying(true);
        bounce();
        pendingRevert = () -> owl.setFlying(false);
        handler.postDelayed(pendingRevert, 1400);
    }

    /** Continuous gentle flight, used on the splash/loading screen only. */
    public void setLoading(boolean loading) {
        owl.setFlying(loading);
        owl.setMood(HooHooView.Mood.IDLE);
    }

    private void bounce() {
        ObjectAnimator anim = ObjectAnimator.ofFloat(owl, View.SCALE_X, 1f, 1.12f, 1f);
        ObjectAnimator anim2 = ObjectAnimator.ofFloat(owl, View.SCALE_Y, 1f, 1.12f, 1f);
        anim.setDuration(420);
        anim2.setDuration(420);
        anim.setInterpolator(new AccelerateDecelerateInterpolator());
        anim2.setInterpolator(new AccelerateDecelerateInterpolator());
        anim.start();
        anim2.start();
    }

    private void cancelPendingRevert() {
        if (pendingRevert != null) handler.removeCallbacks(pendingRevert);
    }
}
