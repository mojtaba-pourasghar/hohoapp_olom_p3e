package com.hoohooolom.app.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.LruCache;
import android.view.Choreographer;
import android.view.MotionEvent;
import android.view.View;

import com.hoohooolom.app.data.PersianDigits;
import com.hoohooolom.app.data.StageSpec;

import java.io.InputStream;
import java.util.Locale;

/**
 * The moving picture هوهو shows while explaining: a real page of the science book. The camera
 * starts on the whole page, flies into each picture the step talks about, rings it with a
 * glowing frame and a travelling spark, and moves on to the next — timed to the narration, so the
 * child's eyes are always on the picture هوهو is talking about.
 *
 * On a TAP step the same view becomes the child's answer sheet: they touch the picture they think
 * is right, and the fragment checks the spot against the step's targets.
 *
 * Animation runs on Choreographer rather than ValueAnimator: an animator obeys the system's
 * «animation duration scale», and on a device with animations switched off it never moves.
 */
public class LessonStageView extends View {

    public interface TapListener {
        /** x and y in thousandths of the page. */
        void onPageTap(float x, float y);
    }

    private static final int TEAL = Color.parseColor("#3AA79A");
    private static final int ORANGE = Color.parseColor("#E8973A");
    private static final int PINK = Color.parseColor("#D94F7A");
    private static final int BACKDROP = Color.parseColor("#EEF3EC");

    /** Decoded pages, so stepping back and forth inside one page never decodes it twice. */
    private static final LruCache<Integer, Bitmap> PAGES = new LruCache<>(4);

    private final Paint bitmapPaint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF tmp = new RectF();
    private final RectF box = new RectF();

    private StageSpec spec = StageSpec.NONE;
    private Bitmap page;
    /**
     * While a question is on screen the page must not give the answer away. Hidden by default,
     * so a screen that forgets to say otherwise can never leak it.
     */
    private boolean answersHidden = true;
    /** 0 = the whole page, 1 = settled on the last stop. */
    private float progress = 0f;
    private boolean playing = false;
    private long startNanos;
    private long durationMs = 1;
    private boolean looping = false;

    private TapListener tapListener;
    private float tapX = -1, tapY = -1;
    private boolean tapRight;
    private long tapNanos;
    /** Targets drawn in green once the child has answered a TAP step. */
    private int[] shownTargets;
    /** SCENE: the actors ringed in green once the child has answered. */
    private int[] shownActors;
    private final SceneRenderer sceneRenderer;

    // the camera: centre of the view in page pixels, and page pixels → screen pixels
    private float camX, camY, camScale;
    /** The stop the camera has arrived at, or -1 while it is travelling or on the whole page. */
    private int liveStop = -1;

    private final Choreographer.FrameCallback frame = new Choreographer.FrameCallback() {
        @Override
        public void doFrame(long now) {
            if (!looping) return;
            if (playing) {
                float t = (now - startNanos) / 1_000_000f / durationMs;
                if (t >= 1f) {
                    t = 1f;
                    playing = false;
                }
                progress = t;
            }
            invalidate();
            Choreographer.getInstance().postFrameCallback(this);
        }
    };

    public LessonStageView(Context context, AttributeSet attrs) {
        super(context, attrs);
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeJoin(Paint.Join.ROUND);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTypeface(UiKit.font(context, true));
        sceneRenderer = new SceneRenderer(context);
    }

    // ---------- public API ----------

    public void setSpec(StageSpec spec) {
        setSpec(spec, false);
    }

    /**
     * `settled` starts the scene already on its last stop — used on the child's turn, so the
     * picture they have to answer about is in view before هوهو even starts talking.
     */
    public void setSpec(StageSpec spec, boolean settled) {
        this.spec = spec == null ? StageSpec.NONE : spec;
        this.page = this.spec.kind == StageSpec.Kind.BOOK ? load(getContext(), this.spec.page) : null;
        sceneRenderer.setScene(this.spec.kind == StageSpec.Kind.SCENE ? this.spec.scene : null);
        this.shownActors = null;
        this.progress = settled ? 1f : 0f;
        this.playing = false;
        this.tapX = -1;
        this.shownTargets = null;
        startLoop();
        invalidate();
    }

    public StageSpec getSpec() {
        return spec;
    }

    /** Hides the book's own answer patch until the child has answered, or a parent asks to see it. */
    public void setRevealAnswers(boolean reveal) {
        this.answersHidden = !reveal;
        invalidate();
    }

    public boolean answersHidden() {
        return answersHidden;
    }

    /** A non-null listener turns the page into something the child can touch. */
    public void setTapListener(TapListener listener) {
        this.tapListener = listener;
        setClickable(listener != null);
        invalidate();
    }

    /** Marks where the child touched: a green ring when right, a pink one when not. */
    public void markTap(float x, float y, boolean right) {
        tapX = x;
        tapY = y;
        tapRight = right;
        tapNanos = System.nanoTime();
        invalidate();
    }

    /** SCENE: which actor sits under a tap (stage units), or -1. */
    public int actorAt(float x, float y) {
        return sceneRenderer.actorAt(x, y);
    }

    /** SCENE: rings the right pictures, once the child has had their go. */
    public void showActors(int[] actors) {
        shownActors = actors;
        invalidate();
    }

    /** Rings the right answers on the page, once the child has had their go. */
    public void showTargets(int[] targets) {
        shownTargets = targets;
        invalidate();
    }

    /** Runs the tour once over the given duration. */
    public void play(long durationMs) {
        if (spec.isNone()) return;
        this.durationMs = Math.max(1600, durationMs);
        this.startNanos = System.nanoTime();
        this.progress = 0f;
        this.playing = true;
        sceneRenderer.restart();
        startLoop();
    }

    /** Stops the tour where it is; the page stays on screen. */
    public void stop() {
        playing = false;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        startLoop();
    }

    @Override
    protected void onDetachedFromWindow() {
        looping = false;
        playing = false;
        Choreographer.getInstance().removeFrameCallback(frame);
        super.onDetachedFromWindow();
    }

    private void startLoop() {
        if (looping || !isAttachedToWindow() || spec.isNone()) return;
        looping = true;
        Choreographer.getInstance().postFrameCallback(frame);
    }

    // ---------- page loading ----------

    private static Bitmap load(Context context, int pageNumber) {
        Bitmap cached = PAGES.get(pageNumber);
        if (cached != null) return cached;
        String name = String.format(Locale.US, "book/p%03d.webp", pageNumber);
        try (InputStream in = context.getAssets().open(name)) {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inPreferredConfig = Bitmap.Config.RGB_565;
            Bitmap bmp = BitmapFactory.decodeStream(in, null, o);
            if (bmp != null) PAGES.put(pageNumber, bmp);
            return bmp;
        } catch (Exception e) {
            return null;
        }
    }

    // ---------- the camera ----------

    /** Stop i as a rectangle in page pixels, or the whole page for i < 0. */
    private void stopRect(int i, RectF out) {
        float bw = page.getWidth(), bh = page.getHeight();
        if (i < 0 || spec.stopCount() == 0) {
            out.set(0, 0, bw, bh);
            return;
        }
        int[] s = spec.stops;
        out.set(s[i * 4] * bw / 1000f, s[i * 4 + 1] * bh / 1000f,
            s[i * 4 + 2] * bw / 1000f, s[i * 4 + 3] * bh / 1000f);
    }

    /** Camera (centre x, centre y, scale) that frames `r` with some air round it. */
    private float[] frameCamera(RectF r, boolean wide, float w, float h) {
        float bw = page.getWidth(), bh = page.getHeight();
        boolean whole = r.width() >= bw * 0.99f && r.height() >= bh * 0.99f;
        float padFactor = whole ? 1.0f : wide ? 1.9f : 1.22f;
        float rw = r.width() * padFactor, rh = r.height() * padFactor;
        float scale = Math.min(w / rw, h / rh);
        // never zoom so far that the page turns to mush: at least ~20% of its width stays in view
        float maxScale = w / (bw * 0.2f);
        scale = Math.min(scale, maxScale);
        return new float[]{r.centerX(), r.centerY(), scale};
    }

    private static float ease(float t) {
        t = Math.max(0f, Math.min(1f, t));
        return t * t * (3f - 2f * t);
    }

    /** Places the camera for the current progress; returns how far into the live stop's hold we are. */
    private float updateCamera(float w, float h) {
        int n = spec.stopCount();
        boolean wide = spec.effect == StageSpec.Effect.SPOT;
        RectF r = tmp;
        if (n == 0) {
            stopRect(-1, r);
            float[] c = frameCamera(r, false, w, h);
            // a whole page still breathes a little, so the scene never looks frozen
            camX = c[0];
            camY = c[1];
            camScale = c[2] * (1f + 0.05f * ease(progress));
            liveStop = -1;
            return 0f;
        }
        float seg = 1f / n;
        int i = Math.min(n - 1, (int) (progress / seg));
        float local = (progress - i * seg) / seg;
        if (progress >= 1f) {
            i = n - 1;
            local = 1f;
        }
        stopRect(i - 1, r);
        float[] from = frameCamera(r, wide && i > 0, w, h);
        if (i == 0 && progress <= 0f) {
            camX = from[0];
            camY = from[1];
            camScale = from[2];
            liveStop = -1;
            return 0f;
        }
        stopRect(i, r);
        float[] to = frameCamera(r, wide, w, h);
        float travel = ease(local / 0.3f);
        camX = from[0] + (to[0] - from[0]) * travel;
        camY = from[1] + (to[1] - from[1]) * travel;
        camScale = (float) Math.exp(Math.log(from[2]) + (Math.log(to[2]) - Math.log(from[2])) * travel);
        float hold = local <= 0.3f ? 0f : (local - 0.3f) / 0.7f;
        if (!wide) camScale *= 1f + 0.04f * hold;        // a slow push-in while هوهو talks
        liveStop = travel >= 0.98f ? i : -1;
        return hold;
    }

    private float toScreenX(float px, float w) {
        return (px - camX) * camScale + w / 2f;
    }

    private float toScreenY(float py, float h) {
        return (py - camY) * camScale + h / 2f;
    }

    /** A rectangle given in ‰ of the page, onto the screen. */
    private void permilleToScreen(int x1, int y1, int x2, int y2, float w, float h, RectF out) {
        float bw = page.getWidth(), bh = page.getHeight();
        out.set(toScreenX(x1 * bw / 1000f, w), toScreenY(y1 * bh / 1000f, h),
            toScreenX(x2 * bw / 1000f, w), toScreenY(y2 * bh / 1000f, h));
    }

    // ---------- drawing ----------

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth(), h = getHeight();
        canvas.drawColor(BACKDROP);
        if (w <= 0 || h <= 0 || spec.isNone()) return;
        if (spec.kind == StageSpec.Kind.SCENE) {
            drawScene(canvas, w, h);
            return;
        }
        if (page == null) {
            textPaint.setColor(Color.GRAY);
            textPaint.setTextSize(sp(14));
            canvas.drawText("صفحه‌ی " + PersianDigits.fa(spec.page), w / 2, h / 2, textPaint);
            return;
        }

        float hold = updateCamera(w, h);
        float bw = page.getWidth(), bh = page.getHeight();

        // the page itself, with a soft shadow under it like paper on a desk
        float left = toScreenX(0, w), top = toScreenY(0, h);
        float right = toScreenX(bw, w), bottom = toScreenY(bh, h);
        fill.setColor(0x22000000);
        canvas.drawRect(left + dp(3), top + dp(4), right + dp(3), bottom + dp(4), fill);
        tmp.set(left, top, right, bottom);
        canvas.drawBitmap(page, null, tmp, bitmapPaint);

        long now = System.nanoTime();
        float pulse = (float) (0.5 + 0.5 * Math.sin(now / 1_000_000_000.0 * 4.2));

        if (liveStop >= 0) drawFocus(canvas, w, h, hold, pulse, now);

        // the book's own answer, hidden under a note until the child has answered
        if (answersHidden && spec.cover != null) {
            int[] c = spec.cover;
            permilleToScreen(c[0], c[1], c[2], c[3], w, h, tmp);
            fill.setColor(Color.parseColor("#FDF4E3"));
            canvas.drawRoundRect(tmp, dp(8), dp(8), fill);
            ring.setColor(ORANGE);
            ring.setStrokeWidth(dp(2));
            canvas.drawRoundRect(tmp, dp(8), dp(8), ring);
            textPaint.setColor(ORANGE);
            textPaint.setTextSize(Math.min(sp(26), tmp.height() * 0.7f));
            canvas.drawText("؟", tmp.centerX(), tmp.centerY() + textPaint.getTextSize() * 0.35f, textPaint);
        }

        // after a TAP answer: the right places, ringed in green
        if (shownTargets != null) {
            ring.setColor(TEAL);
            ring.setStrokeWidth(dp(3.5f));
            for (int i = 0; i + 3 < shownTargets.length; i += 4) {
                permilleToScreen(shownTargets[i], shownTargets[i + 1], shownTargets[i + 2], shownTargets[i + 3], w, h, tmp);
                tmp.inset(-dp(4), -dp(4));
                canvas.drawRoundRect(tmp, dp(12), dp(12), ring);
            }
        }

        // where the child touched
        if (tapX >= 0) {
            float age = (now - tapNanos) / 1_000_000f;
            float sx = toScreenX(tapX * bw / 1000f, w), sy = toScreenY(tapY * bh / 1000f, h);
            float grow = Math.min(1f, age / 350f);
            ring.setColor(tapRight ? TEAL : PINK);
            ring.setStrokeWidth(dp(4));
            canvas.drawCircle(sx, sy, dp(10) + dp(14) * grow, ring);
            fill.setColor(tapRight ? TEAL : PINK);
            canvas.drawCircle(sx, sy, dp(6), fill);
        }

        if (tapListener != null && tapX < 0) {
            textPaint.setColor(Color.WHITE);
            textPaint.setTextSize(sp(13));
            String hint = "روی تصویر بزن";
            float tw = textPaint.measureText(hint) + dp(22);
            tmp.set(w / 2 - tw / 2, h - dp(34), w / 2 + tw / 2, h - dp(8));
            fill.setColor(PINK);
            fill.setAlpha((int) (200 + 55 * pulse));
            canvas.drawRoundRect(tmp, dp(13), dp(13), fill);
            fill.setAlpha(255);
            canvas.drawText(hint, w / 2, h - dp(16), textPaint);
        }
    }

    private void drawScene(Canvas canvas, float w, float h) {
        sceneRenderer.draw(canvas, w, h, progress, answersHidden);
        sceneRenderer.drawAnswerRings(canvas, shownActors);
        long now = System.nanoTime();
        if (tapX >= 0) sceneRenderer.drawTouch(canvas, tapX, tapY, tapRight, (now - tapNanos) / 1e9f);
        if (tapListener != null && tapX < 0) drawTapHint(canvas, w, h, (float) (0.5 + 0.5 * Math.sin(now / 1e9 * 4.2)));
    }

    private void drawTapHint(Canvas canvas, float w, float h, float pulse) {
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(sp(13));
        String hint = "روی تصویر بزن";
        float tw = textPaint.measureText(hint) + dp(22);
        tmp.set(w / 2 - tw / 2, h - dp(34), w / 2 + tw / 2, h - dp(8));
        fill.setColor(PINK);
        fill.setAlpha((int) (200 + 55 * pulse));
        canvas.drawRoundRect(tmp, dp(13), dp(13), fill);
        fill.setAlpha(255);
        canvas.drawText(hint, w / 2, h - dp(16), textPaint);
    }

    /** The glowing ring round the live picture, the travelling spark, and the torch for SPOT. */
    private void drawFocus(Canvas canvas, float w, float h, float hold, float pulse, long now) {
        stopRect(liveStop, tmp);
        float x1 = toScreenX(tmp.left, w), y1 = toScreenY(tmp.top, h);
        float x2 = toScreenX(tmp.right, w), y2 = toScreenY(tmp.bottom, h);
        float grow = dp(4) + dp(3) * pulse;
        box.set(x1 - grow, y1 - grow, x2 + grow, y2 + grow);
        float appear = progress >= 1f ? 1f : Math.min(1f, hold * 5f);

        if (spec.effect == StageSpec.Effect.SPOT) {
            // a torch on the page: everything but the picture goes dim
            path.reset();
            path.setFillType(Path.FillType.EVEN_ODD);
            path.addRect(0, 0, w, h, Path.Direction.CW);
            path.addRoundRect(box, dp(14), dp(14), Path.Direction.CW);
            fill.setColor(Color.argb((int) (120 * appear), 20, 30, 28));
            canvas.drawPath(path, fill);
        }

        ring.setColor(ORANGE);
        ring.setAlpha((int) (90 * appear));
        ring.setStrokeWidth(dp(9));
        canvas.drawRoundRect(box, dp(14), dp(14), ring);
        ring.setAlpha((int) (255 * appear));
        ring.setStrokeWidth(dp(3.2f));
        canvas.drawRoundRect(box, dp(14), dp(14), ring);
        ring.setAlpha(255);

        // a spark travelling round the ring, like a finger tracing the picture
        float bwid = box.width(), bhei = box.height();
        float per = 2 * (bwid + bhei);
        float d = (now / 1_000_000f % 2600f) / 2600f * per;
        float sx, sy;
        if (d < bwid) { sx = box.right - d; sy = box.top; }
        else if (d < bwid + bhei) { sx = box.left; sy = box.top + (d - bwid); }
        else if (d < 2 * bwid + bhei) { sx = box.left + (d - bwid - bhei); sy = box.bottom; }
        else { sx = box.right; sy = box.bottom - (d - 2 * bwid - bhei); }
        fill.setColor(Color.WHITE);
        fill.setAlpha((int) (255 * appear));
        canvas.drawCircle(sx, sy, dp(6.5f), fill);
        fill.setColor(PINK);
        fill.setAlpha((int) (255 * appear));
        canvas.drawCircle(sx, sy, dp(4f), fill);
        fill.setAlpha(255);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (tapListener != null && spec.kind == StageSpec.Kind.SCENE) {
            if (event.getAction() == MotionEvent.ACTION_DOWN) return true;
            if (event.getAction() == MotionEvent.ACTION_UP) {
                tapListener.onPageTap(sceneRenderer.toStageX(event.getX()), sceneRenderer.toStageY(event.getY()));
                performClick();
                return true;
            }
            return super.onTouchEvent(event);
        }
        if (tapListener == null || page == null || spec.isNone()) return super.onTouchEvent(event);
        if (event.getAction() == MotionEvent.ACTION_DOWN) return true;
        if (event.getAction() == MotionEvent.ACTION_UP) {
            float w = getWidth(), h = getHeight();
            float px = camX + (event.getX() - w / 2f) / camScale;
            float py = camY + (event.getY() - h / 2f) / camScale;
            float x = px / page.getWidth() * 1000f, y = py / page.getHeight() * 1000f;
            if (x >= 0 && x <= 1000 && y >= 0 && y <= 1000) tapListener.onPageTap(x, y);
            performClick();
            return true;
        }
        return super.onTouchEvent(event);
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }

    private float dp(float v) {
        return v * getResources().getDisplayMetrics().density;
    }

    private float sp(float v) {
        return v * getResources().getDisplayMetrics().scaledDensity;
    }
}
