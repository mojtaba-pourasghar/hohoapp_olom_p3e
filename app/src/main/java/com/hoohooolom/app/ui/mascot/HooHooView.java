package com.hoohooolom.app.ui.mascot;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

/**
 * هوهو the owl, hand-drawn on a Canvas so no image assets are needed. Draws itself fresh
 * every frame from the view's current size, so it looks right at any size (small overlay
 * bubble, big splash-screen hero, etc). Continuously bobs/blinks/flaps at an idle pace;
 * callers drive mood + flying + speaking to match what's happening on screen.
 */
public class HooHooView extends View {

    public enum Mood { IDLE, HAPPY, THINKING }

    private Mood mood = Mood.IDLE;
    private boolean flying = false;
    private boolean speaking = false;
    private boolean facingLeft = false;

    private final Paint bodyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint wingPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint facePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint eyeWhitePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pupilPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint beakPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint flowerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint flowerCenterPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint lashPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint blushPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint smilePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final long startTimeNanos = System.nanoTime();
    private long lastBlinkAtMs = 0;
    private long blinkStartedAtMs = -1;
    private float eyeOpenness = 1f;

    private final android.view.Choreographer.FrameCallback frameCallback = new android.view.Choreographer.FrameCallback() {
        @Override
        public void doFrame(long frameTimeNanos) {
            if (!running) return;
            tick();
            invalidate();
            android.view.Choreographer.getInstance().postFrameCallback(this);
        }
    };
    private boolean running = false;

    public HooHooView(Context context, AttributeSet attrs) {
        super(context, attrs);
        bodyPaint.setColor(Color.parseColor("#E8973A"));
        wingPaint.setColor(Color.parseColor("#D8811F"));
        facePaint.setColor(Color.parseColor("#FDF4E3"));
        eyeWhitePaint.setColor(Color.WHITE);
        pupilPaint.setColor(Color.parseColor("#3B3027"));
        beakPaint.setColor(Color.parseColor("#D8811F"));
        flowerPaint.setColor(Color.parseColor("#D94F7A"));
        flowerCenterPaint.setColor(Color.parseColor("#F7C94B"));
        lashPaint.setColor(Color.parseColor("#3B3027"));
        lashPaint.setStyle(Paint.Style.STROKE);
        lashPaint.setStrokeCap(Paint.Cap.ROUND);
        blushPaint.setColor(Color.parseColor("#66E78BA6"));
        smilePaint.setColor(Color.parseColor("#3B3027"));
        smilePaint.setStyle(Paint.Style.STROKE);
        smilePaint.setStrokeWidth(4f);
        smilePaint.setStrokeCap(Paint.Cap.ROUND);
    }

    /*
     * Driven by the Choreographer rather than a ValueAnimator on purpose: an animator obeys the
     * system "animator duration scale", so on a device where animations are switched off هوهو
     * would freeze. The frame callback keeps breathing regardless.
     */
    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        running = true;
        android.view.Choreographer.getInstance().postFrameCallback(frameCallback);
    }

    @Override
    protected void onDetachedFromWindow() {
        running = false;
        android.view.Choreographer.getInstance().removeFrameCallback(frameCallback);
        super.onDetachedFromWindow();
    }

    public void setMood(Mood mood) {
        this.mood = mood;
        invalidate();
    }

    public void setFlying(boolean flying) {
        this.flying = flying;
    }

    public void setSpeaking(boolean speaking) {
        this.speaking = speaking;
    }

    public void setFacingLeft(boolean left) {
        this.facingLeft = left;
        invalidate();
    }

    private void tick() {
        long now = System.currentTimeMillis();
        if (blinkStartedAtMs < 0 && now - lastBlinkAtMs > 3800) {
            blinkStartedAtMs = now;
        }
        if (blinkStartedAtMs >= 0) {
            float t = (now - blinkStartedAtMs) / 220f;
            if (t >= 1f) {
                blinkStartedAtMs = -1;
                lastBlinkAtMs = now;
                eyeOpenness = 1f;
            } else {
                eyeOpenness = (float) (1f - Math.sin(Math.PI * Math.min(t, 1f)));
            }
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;
        float cx = w / 2f, cy = h / 2f;
        float scale = Math.min(w, h);

        canvas.save();
        if (facingLeft) canvas.scale(-1f, 1f, cx, cy);

        double t = (System.nanoTime() - startTimeNanos) / 1e9;
        double bobPeriod = flying ? 0.9 : 2.8;
        float bob = (float) (Math.sin(t / bobPeriod * 2 * Math.PI) * scale * (flying ? 0.018 : 0.03));

        // while explaining, the wings gesture along with the beak — a teacher talking with her hands
        double flapPeriod = flying ? 0.4 : speaking ? 0.85 : 3.6;
        double flapAmpDeg = flying ? 24 : speaking ? 11 : 4;
        double flapBaseDeg = flying ? 4 : -6;
        float flapL = (float) (flapBaseDeg + Math.sin(t / flapPeriod * 2 * Math.PI) * flapAmpDeg);
        float flapR = -flapL;

        float bodyR = scale * 0.30f;
        float bodyCx = cx, bodyCy = cy + bob;

        canvas.save();
        canvas.rotate(flapL, bodyCx - bodyR * 0.75f, bodyCy);
        canvas.drawRoundRect(bodyCx - bodyR * 1.55f, bodyCy - bodyR * 0.22f, bodyCx - bodyR * 0.35f, bodyCy + bodyR * 0.22f, bodyR * 0.22f, bodyR * 0.22f, wingPaint);
        canvas.restore();

        canvas.save();
        canvas.rotate(flapR, bodyCx + bodyR * 0.75f, bodyCy);
        canvas.drawRoundRect(bodyCx + bodyR * 0.35f, bodyCy - bodyR * 0.22f, bodyCx + bodyR * 1.55f, bodyCy + bodyR * 0.22f, bodyR * 0.22f, bodyR * 0.22f, wingPaint);
        canvas.restore();

        canvas.drawCircle(bodyCx, bodyCy, bodyR, bodyPaint);

        RectF face = new RectF(bodyCx - bodyR * 0.72f, bodyCy - bodyR * 0.62f, bodyCx + bodyR * 0.72f, bodyCy + bodyR * 0.5f);
        canvas.drawRoundRect(face, bodyR * 0.4f, bodyR * 0.34f, facePaint);

        float eyeR = bodyR * 0.32f;
        float eyeY = bodyCy - bodyR * 0.16f;
        float eyeDX = bodyR * 0.4f;
        boolean thinking = mood == Mood.THINKING;
        drawEye(canvas, bodyCx - eyeDX, eyeY, eyeR, eyeOpenness, thinking);
        drawEye(canvas, bodyCx + eyeDX, eyeY, eyeR, eyeOpenness, thinking);

        // rosy cheeks either side of the beak
        canvas.drawCircle(bodyCx - bodyR * 0.62f, eyeY + eyeR * 0.95f, bodyR * 0.13f, blushPaint);
        canvas.drawCircle(bodyCx + bodyR * 0.62f, eyeY + eyeR * 0.95f, bodyR * 0.13f, blushPaint);

        float talkPulse = speaking ? (float) (0.22 * Math.abs(Math.sin(t * 2 * Math.PI / 0.22))) : 0f;
        float beakY = bodyCy + bodyR * 0.12f;
        float beakH = bodyR * 0.22f * (1f + talkPulse);
        Path beak = new Path();
        beak.moveTo(bodyCx - bodyR * 0.16f, beakY);
        beak.lineTo(bodyCx + bodyR * 0.16f, beakY);
        beak.lineTo(bodyCx, beakY + beakH);
        beak.close();
        canvas.drawPath(beak, beakPaint);

        if (mood == Mood.HAPPY) {
            RectF smileRect = new RectF(bodyCx - bodyR * 0.22f, beakY + bodyR * 0.14f, bodyCx + bodyR * 0.22f, beakY + bodyR * 0.42f);
            canvas.drawArc(smileRect, 20, 140, false, smilePaint);
        }

        drawFlower(canvas, bodyCx - bodyR * 0.72f, bodyCy - bodyR * 0.74f, bodyR * 0.17f);

        canvas.restore();
    }

    private void drawEye(Canvas canvas, float ex, float ey, float r, float openness, boolean thinking) {
        canvas.save();
        canvas.scale(1f, Math.max(0.08f, openness), ex, ey);
        canvas.drawCircle(ex, ey, r, eyeWhitePaint);
        float pupilOffsetY = thinking ? -r * 0.15f : 0;
        canvas.drawCircle(ex, ey + pupilOffsetY, r * 0.42f, pupilPaint);

        // three lashes swept up and outwards
        lashPaint.setStrokeWidth(r * 0.14f);
        float dir = ex < getWidth() / 2f ? -1f : 1f;
        for (int i = 0; i < 3; i++) {
            double angle = Math.toRadians(-118 + i * 26) * (dir > 0 ? 1 : -1);
            float sx = ex + (float) Math.cos(angle) * r * 0.95f;
            float sy = ey + (float) Math.sin(angle) * r * 0.95f;
            float lx = ex + (float) Math.cos(angle) * r * 1.42f;
            float ly = ey + (float) Math.sin(angle) * r * 1.42f;
            canvas.drawLine(sx, sy, lx, ly, lashPaint);
        }
        canvas.restore();
    }

    /** The little flower tucked over one ear — quickest way to read the character as female. */
    private void drawFlower(Canvas canvas, float cx, float cy, float petalR) {
        for (int i = 0; i < 5; i++) {
            double angle = Math.toRadians(i * 72 - 90);
            canvas.drawCircle(
                cx + (float) Math.cos(angle) * petalR,
                cy + (float) Math.sin(angle) * petalR,
                petalR * 0.78f, flowerPaint);
        }
        canvas.drawCircle(cx, cy, petalR * 0.62f, flowerCenterPaint);
    }
}
