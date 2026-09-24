package com.hoohooolom.app.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;
import android.widget.FrameLayout;

import java.util.ArrayList;
import java.util.List;

/**
 * The learning-path map: draws the design's winding trail and drops each section's node onto it
 * at a fixed percentage position, so the trail and the stops always line up at any screen size.
 * Curve control points are the design's SVG path on a 0-100 box, scaled to this view.
 */
public class PathMapView extends FrameLayout {

    /** {x, y} pairs on a 0-100 grid: start point, then cubic curves as (c1, c2, end) triples. */
    private static final float[][] CURVE = {
        {72, 82},
        {60, 78}, {40, 74}, {44, 69},
        {48, 63}, {68, 62}, {66, 57},
        {63, 51}, {42, 52}, {38, 45},
        {34, 38}, {60, 39}, {62, 33},
        {64, 26}, {46, 26}, {44, 16}
    };

    private final Paint trailPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path trail = new Path();
    private final List<float[]> nodePositions = new ArrayList<>();

    public PathMapView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setWillNotDraw(false);
        trailPaint.setStyle(Paint.Style.STROKE);
        trailPaint.setColor(Color.parseColor("#CBE3C5"));
        trailPaint.setStrokeCap(Paint.Cap.ROUND);
        trailPaint.setStrokeJoin(Paint.Join.ROUND);
        trailPaint.setStrokeWidth(UiKit.dp(context, 9));
    }

    /** Adds a node centred at (leftPercent, topPercent) of this view. */
    public void addNode(View node, float topPercent, float leftPercent) {
        nodePositions.add(new float[]{topPercent, leftPercent});
        addView(node, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
    }

    public void clearNodes() {
        nodePositions.clear();
        removeAllViews();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        buildTrail(w, h);
    }

    private void buildTrail(int w, int h) {
        trail.reset();
        if (w <= 0 || h <= 0) return;
        trail.moveTo(x(CURVE[0][0], w), y(CURVE[0][1], h));
        for (int i = 1; i + 2 < CURVE.length; i += 3) {
            trail.cubicTo(
                x(CURVE[i][0], w), y(CURVE[i][1], h),
                x(CURVE[i + 1][0], w), y(CURVE[i + 1][1], h),
                x(CURVE[i + 2][0], w), y(CURVE[i + 2][1], h));
        }
    }

    private static float x(float pct, int w) {
        return w * pct / 100f;
    }

    private static float y(float pct, int h) {
        return h * pct / 100f;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawPath(trail, trailPaint);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int w = right - left;
        int h = bottom - top;
        for (int i = 0; i < getChildCount() && i < nodePositions.size(); i++) {
            View child = getChildAt(i);
            float[] pos = nodePositions.get(i);
            int cx = Math.round(x(pos[1], w));
            int cy = Math.round(y(pos[0], h));
            int cw = child.getMeasuredWidth();
            int chh = child.getMeasuredHeight();
            child.layout(cx - cw / 2, cy - chh / 2, cx + cw / 2, cy + chh / 2);
        }
    }
}
