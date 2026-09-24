package com.hoohooolom.app.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.LruCache;

import com.hoohooolom.app.data.Scene;

import java.io.InputStream;
import java.util.List;
import java.util.Locale;

/**
 * Draws a {@link Scene}: a painted backdrop, the book's cut-out pictures as actors that enter,
 * move and keep gently alive, and the teacher's chalk around them — arrows, labels, rain, steam,
 * bubbles, sun rays and light beams.
 *
 * Two clocks drive it. `progress` (0…1) follows هوهو's voice: things appear and travel when she
 * talks about them. The wall clock keeps the scene breathing afterwards — a butterfly still
 * bobs, clouds still drift — so the picture never freezes while the child is thinking.
 */
final class SceneRenderer {

    static final float W = 1000f, H = 600f;

    private static final LruCache<String, Bitmap> ART = new LruCache<String, Bitmap>(28 * 1024 * 1024) {
        @Override protected int sizeOf(String key, Bitmap value) { return value.getByteCount(); }
    };

    private final Context context;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bmp = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Path path = new Path();
    private final RectF rect = new RectF();

    private Scene scene;
    /** Wall-clock moment each item first appeared, so its entrance plays once, at its own pace. */
    private long[] appeared;
    private final long born = System.nanoTime();

    // stage → screen
    private float scale = 1f, offX, offY;
    /** Where each actor was last drawn, in stage units — for taps and rings. */
    private RectF[] actorBounds;

    SceneRenderer(Context context) {
        this.context = context;
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        stroke.setStrokeJoin(Paint.Join.ROUND);
        text.setTextAlign(Paint.Align.CENTER);
        text.setTypeface(UiKit.font(context, true));
    }

    void setScene(Scene scene) {
        this.scene = scene;
        this.appeared = new long[scene == null ? 0 : scene.items.size()];
        this.actorBounds = new RectF[scene == null ? 0 : scene.actors.size()];
        for (int i = 0; i < actorBounds.length; i++) actorBounds[i] = new RectF();
    }

    /** Replays every entrance, as when هوهو starts the line again. */
    void restart() {
        if (appeared != null) java.util.Arrays.fill(appeared, 0L);
    }

    float toStageX(float viewX) { return (viewX - offX) / scale; }
    float toStageY(float viewY) { return (viewY - offY) / scale; }

    /** The top-most actor under a stage point, with a finger's worth of slack; -1 when none. */
    int actorAt(float x, float y) {
        if (actorBounds == null) return -1;
        float slack = 18f;
        for (int i = actorBounds.length - 1; i >= 0; i--) {
            RectF b = actorBounds[i];
            if (b.width() <= 0) continue;
            if (x >= b.left - slack && x <= b.right + slack && y >= b.top - slack && y <= b.bottom + slack) return i;
        }
        return -1;
    }

    RectF boundsOf(int actor) {
        return actor >= 0 && actor < actorBounds.length ? actorBounds[actor] : null;
    }

    // ───────────────────────── drawing ─────────────────────────

    void draw(Canvas canvas, float vw, float vh, float progress, boolean answersHidden) {
        if (scene == null) return;
        long now = System.nanoTime();
        float secs = (now - born) / 1e9f;
        scale = Math.min(vw / W, vh / H);
        offX = (vw - W * scale) / 2f;
        offY = (vh - H * scale) / 2f;

        drawBackdrop(canvas, vw, vh, secs);

        int saved = canvas.save();
        canvas.translate(offX, offY);
        canvas.scale(scale, scale);

        List<Scene.Item> items = scene.items;
        int actor = 0;
        for (int i = 0; i < items.size(); i++) {
            Scene.Item it = items.get(i);
            int myActor = it.kind == Scene.Kind.IMG ? actor++ : -1;
            if (progress < it.t && !(progress >= 1f)) {
                if (myActor >= 0) actorBounds[myActor].setEmpty();
                continue;
            }
            if (appeared[i] == 0L) appeared[i] = now;
            float age = (now - appeared[i]) / 1e9f;
            switch (it.kind) {
                case IMG: drawActor(canvas, it, myActor, progress, age, secs); break;
                case TEXT: if (!(it.hidden && answersHidden)) drawLabel(canvas, it, age); break;
                case ARROW: drawArrow(canvas, it, age); break;
                case BEAM: drawBeam(canvas, it, age, secs); break;
                case RING: drawRing(canvas, actorBounds[it.target], age, secs, 0xFFE8973A); break;
                case RAIN: if (progress <= it.t2 || it.t2 >= 1f) drawRain(canvas, it, secs, false); break;
                case SNOW: if (progress <= it.t2 || it.t2 >= 1f) drawRain(canvas, it, secs, true); break;
                case STEAM: if (progress <= it.t2 || it.t2 >= 1f) drawSteam(canvas, it, secs); break;
                case BUBBLES: if (progress <= it.t2 || it.t2 >= 1f) drawBubbles(canvas, it, secs); break;
                case RAYS: drawRays(canvas, it, age, secs); break;
                case SPARKLE: drawSparkle(canvas, it, age, secs); break;
                case BOX: drawBox(canvas, it, age); break;
            }
        }
        canvas.restoreToCount(saved);
    }

    /** Rings round the given actors — the right answers, once the child has had a go. */
    void drawAnswerRings(Canvas canvas, int[] actors) {
        if (actors == null) return;
        float secs = (System.nanoTime() - born) / 1e9f;
        int saved = canvas.save();
        canvas.translate(offX, offY);
        canvas.scale(scale, scale);
        for (int a : actors) {
            RectF b = boundsOf(a);
            if (b != null && b.width() > 0) drawRing(canvas, b, 1f, secs, 0xFF3AA79A);
        }
        canvas.restoreToCount(saved);
    }

    /** A dot and ring where the child touched, in stage units. */
    void drawTouch(Canvas canvas, float x, float y, boolean right, float age) {
        int saved = canvas.save();
        canvas.translate(offX, offY);
        canvas.scale(scale, scale);
        int c = right ? 0xFF3AA79A : 0xFFD94F7A;
        stroke.setColor(c);
        stroke.setStrokeWidth(6f);
        canvas.drawCircle(x, y, 16f + 22f * Math.min(1f, age / 0.35f), stroke);
        fill.setColor(c);
        canvas.drawCircle(x, y, 9f, fill);
        canvas.restoreToCount(saved);
    }

    // ── actors ──

    private void drawActor(Canvas canvas, Scene.Item it, int index, float progress, float age, float secs) {
        Bitmap b = art(it.name);
        float w = it.w;
        float h = b != null ? w * b.getHeight() / (float) b.getWidth() : w * 0.75f;
        float x = it.x, y = it.y, s = 1f, rot = 0f, alpha = 1f;

        // scripted motions, following the voice
        for (Scene.Motion m : scene.motions) {
            if (m.actor != index) continue;
            float k = progress >= 1f ? 1f : ease((progress - m.t1) / Math.max(0.001f, m.t2 - m.t1));
            if (k <= 0f) continue;
            switch (m.kind) {
                case "move": x = x + (m.a - x) * k; y = y + (m.b - y) * k; break;
                case "scale": s = s + (m.a - s) * k; break;
                case "turn": rot = rot + m.a * k; break;
                case "hide": alpha *= 1f - k; break;
                case "show": break;
            }
        }
        for (Scene.Motion m : scene.motions) {
            if (m.actor == index && m.kind.equals("show")) {
                float k = progress >= 1f ? 1f : ease((progress - m.t1) / 0.08f);
                alpha *= Math.max(0f, k);
            }
        }

        // the entrance, on its own short clock
        float e = ease(age / 0.55f);
        switch (it.enter) {
            case "pop": s *= e < 1f ? 0.3f + 0.7f * e + 0.12f * (float) Math.sin(e * Math.PI) : 1f; break;
            case "grow": s *= 0.05f + 0.95f * e; break;
            case "fade": alpha *= e; break;
            case "left": x -= (1f - e) * 700f; break;
            case "right": x += (1f - e) * 700f; break;
            case "top": y -= (1f - e) * 600f; break;
            case "bottom": y += (1f - e) * 600f; break;
            default: break;
        }
        if (!it.enter.equals("fade") && !it.enter.equals("none")) alpha *= Math.min(1f, e * 3f);

        // the gentle life that keeps going after the line
        boolean flip = false;
        for (String fx : it.fx) {
            switch (fx) {
                case "bob": y += 8f * (float) Math.sin(secs * 2.6 + index); break;
                case "float": x += 10f * (float) Math.sin(secs * 0.9 + index); y += 6f * (float) Math.cos(secs * 1.3 + index); break;
                case "sway": rot += 5f * (float) Math.sin(secs * 2.0 + index); break;
                case "spin": rot += (secs * 90f) % 360f; break;
                case "shake": x += 4f * (float) Math.sin(secs * 30.0) * (0.5f + 0.5f * (float) Math.sin(secs * 1.7)); break;
                case "pulse": s *= 1f + 0.05f * (float) Math.sin(secs * 4.0 + index); break;
                case "tilt": rot -= 9f; break;
                case "flip": flip = true; break;
            }
        }

        float hw = w * s / 2f, hh = h * s / 2f;
        actorBounds[index].set(x - hw, y - hh, x + hw, y + hh);
        if (alpha <= 0.01f) {
            actorBounds[index].setEmpty();
            return;
        }

        int saved = canvas.save();
        canvas.translate(x, y);
        if (rot != 0f) canvas.rotate(rot);
        if (flip) canvas.scale(-1f, 1f);
        rect.set(-hw, -hh, hw, hh);
        // a soft shadow grounds the picture on the stage
        fill.setColor(Color.argb((int) (40 * alpha), 0, 0, 0));
        canvas.drawOval(-hw * 0.8f, hh * 0.92f, hw * 0.8f, hh * 1.06f, fill);
        if (b != null) {
            bmp.setAlpha((int) (255 * alpha));
            canvas.drawBitmap(b, null, rect, bmp);
        } else {
            fill.setColor(Color.argb((int) (255 * alpha), 230, 230, 230));
            canvas.drawRoundRect(rect, 20f, 20f, fill);
        }
        canvas.restoreToCount(saved);
    }

    private Bitmap art(String name) {
        Bitmap cached = ART.get(name);
        if (cached != null) return cached;
        try (InputStream in = context.getAssets().open("art/" + name + ".webp")) {
            Bitmap b = BitmapFactory.decodeStream(in);
            if (b != null) ART.put(name, b);
            return b;
        } catch (Exception e) {
            return null;
        }
    }

    // ── chalk ──

    private void drawLabel(Canvas canvas, Scene.Item it, float age) {
        float e = ease(age / 0.4f);
        text.setTextSize(it.size);
        float tw = text.measureText(it.name);
        float pad = it.size * 0.45f;
        float s = 0.6f + 0.4f * e;
        int saved = canvas.save();
        canvas.translate(it.x, it.y);
        canvas.scale(s, s);
        rect.set(-tw / 2f - pad, -it.size * 0.78f, tw / 2f + pad, it.size * 0.52f);
        fill.setColor(Color.argb((int) (235 * e), 255, 255, 255));
        canvas.drawRoundRect(rect, it.size * 0.5f, it.size * 0.5f, fill);
        int c = color(it.color);
        stroke.setColor(c);
        stroke.setAlpha((int) (255 * e));
        stroke.setStrokeWidth(3.5f);
        canvas.drawRoundRect(rect, it.size * 0.5f, it.size * 0.5f, stroke);
        text.setColor(it.color.equals("white") || it.color.equals("yellow") ? 0xFF2E3B36 : c);
        text.setAlpha((int) (255 * e));
        canvas.drawText(it.name, 0, 0, text);
        text.setAlpha(255);
        canvas.restoreToCount(saved);
    }

    private void drawArrow(Canvas canvas, Scene.Item it, float age) {
        float k = ease(age / 0.6f);
        float x1 = it.x, y1 = it.y, x2 = it.w, y2 = it.h;
        float mx = (x1 + x2) / 2f, my = (y1 + y2) / 2f;
        float cx = mx, cy = my;
        if (it.curve) {
            float dx = x2 - x1, dy = y2 - y1;
            cx = mx + dy * 0.35f;
            cy = my - dx * 0.35f;
        }
        // point along the quadratic at k
        float ex = q(x1, cx, x2, k), ey = q(y1, cy, y2, k);
        path.reset();
        path.moveTo(x1, y1);
        int steps = 24;
        for (int i = 1; i <= steps; i++) {
            float t = k * i / steps;
            path.lineTo(q(x1, cx, x2, t), q(y1, cy, y2, t));
        }
        int c = color(it.color);
        stroke.setColor(0x55FFFFFF);
        stroke.setStrokeWidth(15f);
        canvas.drawPath(path, stroke);
        stroke.setColor(c);
        stroke.setStrokeWidth(8f);
        canvas.drawPath(path, stroke);
        // head, pointing along the curve's direction at its tip
        float tx = q(x1, cx, x2, Math.max(0f, k - 0.04f)), ty = q(y1, cy, y2, Math.max(0f, k - 0.04f));
        double ang = Math.atan2(ey - ty, ex - tx);
        float len = 26f;
        path.reset();
        path.moveTo(ex, ey);
        path.lineTo(ex - len * (float) Math.cos(ang - 0.5), ey - len * (float) Math.sin(ang - 0.5));
        path.lineTo(ex - len * (float) Math.cos(ang + 0.5), ey - len * (float) Math.sin(ang + 0.5));
        path.close();
        fill.setColor(c);
        canvas.drawPath(path, fill);
    }

    private void drawBeam(Canvas canvas, Scene.Item it, float age, float secs) {
        float k = ease(age / 0.7f);
        float x1 = it.x, y1 = it.y;
        float x2 = x1 + (it.w - x1) * k, y2 = y1 + (it.h - y1) * k;
        int c = color(it.color);
        stroke.setColor((c & 0x00FFFFFF) | 0x55000000);
        stroke.setStrokeWidth(22f);
        canvas.drawLine(x1, y1, x2, y2, stroke);
        stroke.setColor(c);
        stroke.setStrokeWidth(7f);
        canvas.drawLine(x1, y1, x2, y2, stroke);
        if (k >= 1f) {
            float p = (secs * 0.8f) % 1f;
            fill.setColor(Color.WHITE);
            canvas.drawCircle(x1 + (it.w - x1) * p, y1 + (it.h - y1) * p, 9f, fill);
        }
    }

    private void drawRing(Canvas canvas, RectF b, float age, float secs, int color) {
        if (b == null || b.width() <= 0) return;
        float a = Math.min(1f, age / 0.3f);
        float grow = 10f + 5f * (float) (0.5 + 0.5 * Math.sin(secs * 4.2));
        rect.set(b.left - grow, b.top - grow, b.right + grow, b.bottom + grow);
        stroke.setColor(color);
        stroke.setAlpha((int) (80 * a));
        stroke.setStrokeWidth(18f);
        canvas.drawRoundRect(rect, 26f, 26f, stroke);
        stroke.setAlpha((int) (255 * a));
        stroke.setStrokeWidth(6f);
        canvas.drawRoundRect(rect, 26f, 26f, stroke);
        // a spark tracing the ring
        float per = 2f * (rect.width() + rect.height());
        float d = (secs * 0.4f % 1f) * per;
        float sx, sy;
        if (d < rect.width()) { sx = rect.right - d; sy = rect.top; }
        else if (d < rect.width() + rect.height()) { sx = rect.left; sy = rect.top + d - rect.width(); }
        else if (d < 2 * rect.width() + rect.height()) { sx = rect.left + d - rect.width() - rect.height(); sy = rect.bottom; }
        else { sx = rect.right; sy = rect.bottom - (d - 2 * rect.width() - rect.height()); }
        fill.setColor(Color.WHITE);
        fill.setAlpha((int) (255 * a));
        canvas.drawCircle(sx, sy, 12f, fill);
        fill.setColor(0xFFD94F7A);
        fill.setAlpha((int) (255 * a));
        canvas.drawCircle(sx, sy, 7f, fill);
        fill.setAlpha(255);
    }

    private void drawRain(Canvas canvas, Scene.Item it, float secs, boolean snow) {
        int n = (int) Math.max(12, it.w * it.h / 2600f);
        for (int i = 0; i < n; i++) {
            float seed = (i * 0.618f) % 1f;
            float px = it.x + (seed * it.w + i * 37f) % it.w;
            float speed = snow ? 0.18f : 0.9f;
            float py = it.y + ((secs * speed + seed * 3.1f + i * 0.13f) % 1f) * it.h;
            if (snow) {
                px += 8f * (float) Math.sin(secs * 1.5 + i);
                fill.setColor(0xEEFFFFFF);
                canvas.drawCircle(px, py, 5f + (i % 3), fill);
            } else {
                stroke.setColor(0xCC4A90D9);
                stroke.setStrokeWidth(4f);
                canvas.drawLine(px, py, px - 4f, py + 20f, stroke);
            }
        }
    }

    private void drawSteam(Canvas canvas, Scene.Item it, float secs) {
        for (int i = 0; i < 7; i++) {
            float p = (secs * 0.35f + i / 7f) % 1f;
            float px = it.x + 18f * (float) Math.sin(p * 6 + i) ;
            float py = it.y - p * 170f;
            fill.setColor(Color.argb((int) (150 * (1f - p)), 255, 255, 255));
            canvas.drawCircle(px, py, 14f + p * 26f, fill);
            stroke.setColor(Color.argb((int) (60 * (1f - p)), 150, 160, 170));
            stroke.setStrokeWidth(2f);
            canvas.drawCircle(px, py, 14f + p * 26f, stroke);
        }
    }

    private void drawBubbles(Canvas canvas, Scene.Item it, float secs) {
        for (int i = 0; i < 9; i++) {
            float p = (secs * 0.45f + i / 9f) % 1f;
            float px = it.x + 12f * (float) Math.sin(p * 9 + i * 2);
            float py = it.y - p * 200f;
            float r = 5f + (i % 4) * 3f;
            stroke.setColor(Color.argb((int) (220 * (1f - p * 0.7f)), 255, 255, 255));
            stroke.setStrokeWidth(3f);
            canvas.drawCircle(px, py, r, stroke);
            fill.setColor(Color.argb((int) (90 * (1f - p)), 200, 235, 255));
            canvas.drawCircle(px, py, r, fill);
        }
    }

    private void drawRays(Canvas canvas, Scene.Item it, float age, float secs) {
        float a = Math.min(1f, age / 0.6f);
        float r = it.w;
        fill.setShader(new RadialGradient(it.x, it.y, r * 1.8f,
            new int[]{Color.argb((int) (200 * a), 255, 236, 140), Color.argb(0, 255, 236, 140)}, null, Shader.TileMode.CLAMP));
        canvas.drawCircle(it.x, it.y, r * 1.8f, fill);
        fill.setShader(null);
        stroke.setColor(Color.argb((int) (220 * a), 255, 200, 60));
        stroke.setStrokeWidth(7f);
        for (int i = 0; i < 12; i++) {
            double ang = Math.toRadians(i * 30 + secs * 12);
            float r1 = r * 1.15f, r2 = r * (1.45f + 0.12f * (float) Math.sin(secs * 3 + i));
            canvas.drawLine(it.x + r1 * (float) Math.cos(ang), it.y + r1 * (float) Math.sin(ang),
                it.x + r2 * (float) Math.cos(ang), it.y + r2 * (float) Math.sin(ang), stroke);
        }
        fill.setColor(Color.argb((int) (255 * a), 255, 214, 70));
        canvas.drawCircle(it.x, it.y, r, fill);
    }

    private void drawSparkle(Canvas canvas, Scene.Item it, float age, float secs) {
        for (int i = 0; i < 8; i++) {
            double ang = Math.toRadians(i * 45 + 20);
            float d = age < 0.8f ? 20f + 80f * ease(age / 0.8f) : 60f + 8f * (float) Math.sin(secs * 3 + i);
            float px = it.x + d * (float) Math.cos(ang), py = it.y + d * (float) Math.sin(ang);
            float s = 7f + 4f * (float) Math.sin(secs * 5 + i);
            fill.setColor(i % 2 == 0 ? 0xFFFFD447 : 0xFFFFFFFF);
            path.reset();
            path.moveTo(px, py - s * 1.6f);
            path.lineTo(px + s * 0.5f, py - s * 0.5f);
            path.lineTo(px + s * 1.6f, py);
            path.lineTo(px + s * 0.5f, py + s * 0.5f);
            path.lineTo(px, py + s * 1.6f);
            path.lineTo(px - s * 0.5f, py + s * 0.5f);
            path.lineTo(px - s * 1.6f, py);
            path.lineTo(px - s * 0.5f, py - s * 0.5f);
            path.close();
            canvas.drawPath(path, fill);
        }
    }

    private void drawBox(Canvas canvas, Scene.Item it, float age) {
        float a = Math.min(1f, age / 0.4f);
        rect.set(it.x, it.y, it.w, it.h);
        int c = color(it.color);
        fill.setColor(Color.argb((int) (170 * a), Color.red(c), Color.green(c), Color.blue(c)));
        canvas.drawRoundRect(rect, 28f, 28f, fill);
        stroke.setColor(Color.argb((int) (120 * a), 255, 255, 255));
        stroke.setStrokeWidth(4f);
        canvas.drawRoundRect(rect, 28f, 28f, stroke);
    }

    // ── backdrops, drawn over the whole view so the stage never shows bare bands ──

    private void drawBackdrop(Canvas canvas, float vw, float vh, float secs) {
        String bg = scene.bg;
        float gy = offY + 430f * scale;   // where the ground starts on stage
        switch (bg) {
            case "sky": case "farm": case "park": case "desert": case "snow": case "sea": {
                gradient(canvas, 0, 0, vw, vh, 0xFF8FD3F4, 0xFFE8F7FF);
                drawClouds(canvas, vw, secs);
                if (bg.equals("sea")) {
                    gradient(canvas, 0, gy - 40 * scale, vw, vh, 0xFF3D9BD6, 0xFF1F6FA8);
                    drawWaves(canvas, vw, gy - 40 * scale, secs, 0x55FFFFFF);
                } else if (bg.equals("desert")) {
                    hills(canvas, vw, vh, gy, 0xFFF3D38B, 0xFFE9BE6B);
                } else if (bg.equals("snow")) {
                    hills(canvas, vw, vh, gy, 0xFFFFFFFF, 0xFFE3EEF7);
                } else if (bg.equals("sky")) {
                    hills(canvas, vw, vh, gy + 60 * scale, 0xFFB9E3A0, 0xFF8CCB76);
                } else {
                    hills(canvas, vw, vh, gy, 0xFF9ED47F, 0xFF6DB85C);
                }
                break;
            }
            case "water":
                gradient(canvas, 0, 0, vw, vh, 0xFF6EC6F0, 0xFF2170B0);
                drawWaves(canvas, vw, offY + 120 * scale, secs, 0x44FFFFFF);
                drawWaves(canvas, vw, offY + 320 * scale, secs + 1.3f, 0x33FFFFFF);
                break;
            case "night":
                gradient(canvas, 0, 0, vw, vh, 0xFF0F1B3D, 0xFF27406E);
                for (int i = 0; i < 40; i++) {
                    float sx = (i * 173.3f) % vw, sy = (i * 97.7f) % (vh * 0.8f);
                    fill.setColor(Color.argb((int) (140 + 110 * Math.sin(secs * 2 + i)), 255, 255, 255));
                    canvas.drawCircle(sx, sy, 2f + (i % 3), fill);
                }
                break;
            case "kitchen": case "warm":
                gradient(canvas, 0, 0, vw, vh, 0xFFFFF1D9, 0xFFFBE0B8);
                fill.setColor(0xFFC98A52);
                canvas.drawRect(0, gy + 30 * scale, vw, vh, fill);
                fill.setColor(0xFFB57840);
                canvas.drawRect(0, gy + 30 * scale, vw, gy + 44 * scale, fill);
                break;
            case "class":
                gradient(canvas, 0, 0, vw, vh, 0xFFEAF6EE, 0xFFD7EEDD);
                fill.setColor(0xFFD9B98C);
                canvas.drawRect(0, gy + 40 * scale, vw, vh, fill);
                break;
            case "shop":
                gradient(canvas, 0, 0, vw, vh, 0xFFFFF6E8, 0xFFFFE9CC);
                stroke.setColor(0x33A0703C);
                stroke.setStrokeWidth(10f * scale);
                for (int i = 1; i < 4; i++) canvas.drawLine(0, offY + i * 140 * scale, vw, offY + i * 140 * scale, stroke);
                break;
            case "lab":
                gradient(canvas, 0, 0, vw, vh, 0xFFE6F6F4, 0xFFD2EEEA);
                stroke.setColor(0x223AA79A);
                stroke.setStrokeWidth(2f);
                for (float gx = offX % (40 * scale); gx < vw; gx += 40 * scale) canvas.drawLine(gx, 0, gx, vh, stroke);
                for (float gy2 = offY % (40 * scale); gy2 < vh; gy2 += 40 * scale) canvas.drawLine(0, gy2, vw, gy2, stroke);
                break;
            default:
                gradient(canvas, 0, 0, vw, vh, 0xFFFFFBF2, 0xFFFDF0DA);
                break;
        }
    }

    private void gradient(Canvas canvas, float x1, float y1, float x2, float y2, int top, int bottom) {
        fill.setShader(new LinearGradient(0, y1, 0, y2, top, bottom, Shader.TileMode.CLAMP));
        canvas.drawRect(x1, y1, x2, y2, fill);
        fill.setShader(null);
    }

    private void hills(Canvas canvas, float vw, float vh, float gy, int back, int front) {
        path.reset();
        path.moveTo(0, gy);
        path.cubicTo(vw * 0.25f, gy - 60 * scale, vw * 0.45f, gy + 20 * scale, vw * 0.7f, gy - 40 * scale);
        path.cubicTo(vw * 0.85f, gy - 70 * scale, vw * 0.95f, gy - 10 * scale, vw, gy - 20 * scale);
        path.lineTo(vw, vh);
        path.lineTo(0, vh);
        path.close();
        fill.setColor(back);
        canvas.drawPath(path, fill);
        path.reset();
        path.moveTo(0, gy + 50 * scale);
        path.cubicTo(vw * 0.3f, gy + 10 * scale, vw * 0.6f, gy + 70 * scale, vw, gy + 30 * scale);
        path.lineTo(vw, vh);
        path.lineTo(0, vh);
        path.close();
        fill.setColor(front);
        canvas.drawPath(path, fill);
    }

    private void drawClouds(Canvas canvas, float vw, float secs) {
        fill.setColor(0xDDFFFFFF);
        for (int i = 0; i < 3; i++) {
            float cx = ((i * 0.37f + secs * 0.012f) % 1.2f - 0.1f) * vw;
            float cy = offY + (60 + i * 55) * scale;
            float r = (28 + i * 6) * scale;
            canvas.drawCircle(cx, cy, r, fill);
            canvas.drawCircle(cx + r, cy + r * 0.2f, r * 0.8f, fill);
            canvas.drawCircle(cx - r, cy + r * 0.25f, r * 0.7f, fill);
        }
    }

    private void drawWaves(Canvas canvas, float vw, float y, float secs, int color) {
        stroke.setColor(color);
        stroke.setStrokeWidth(4f * scale);
        for (int row = 0; row < 3; row++) {
            path.reset();
            float yy = y + row * 40 * scale;
            for (float x = 0; x <= vw; x += 10) {
                float wy = yy + 8 * scale * (float) Math.sin(x / (40 * scale) + secs * 1.5 + row);
                if (x == 0) path.moveTo(x, wy); else path.lineTo(x, wy);
            }
            canvas.drawPath(path, stroke);
        }
    }

    // ── helpers ──

    private static float q(float a, float c, float b, float t) {
        float u = 1f - t;
        return u * u * a + 2 * u * t * c + t * t * b;
    }

    static float ease(float t) {
        t = Math.max(0f, Math.min(1f, t));
        return t * t * (3f - 2f * t);
    }

    static int color(String name) {
        switch (name) {
            case "teal": return 0xFF3AA79A;
            case "pink": return 0xFFD94F7A;
            case "blue": return 0xFF3E8ED0;
            case "green": return 0xFF4CAF50;
            case "red": return 0xFFE0463C;
            case "yellow": return 0xFFF5C518;
            case "purple": return 0xFF8E5CC2;
            case "white": return 0xFFFFFFFF;
            case "ink": return 0xFF2E3B36;
            default: return 0xFFE8973A;
        }
    }

    static String lower(String s) {
        return s.toLowerCase(Locale.US);
    }
}
