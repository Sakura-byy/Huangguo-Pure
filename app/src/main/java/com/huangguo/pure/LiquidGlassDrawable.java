package com.huangguo.pure;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

/**
 * Liquid Glass 折射层 —— 对齐 Kyant0/AndroidLiquidGlass 的 drawBackdrop 效果链。
 *
 * 效果链：backdrop → 径向 lens 位移 → RGB 三通道色散 → tint → sheen → rim。
 *
 * 关键设计：
 *   1) lens 区域可独立指定（{@link #setLens(RectF)}）：backdrop 可以是比显示区域更大的一张图，
 *      形变只在 lens 中心附近的窄环发生，顶点向外偏移时仍能采样到有效像素（不会露透明层 → 无重影）。
 *   2) 中心 82% 半径完全不动，形变/色散只发生在最外 18% 环（用户要求中心零形变、零色散）。
 *   3) 形变权重 sin(t·π) 保证边缘顶点归零、网格连续无空洞；
 *      色散权重 t 保证越靠边缘越强 —— 彩虹沿弧形边缘分布。
 */
public class LiquidGlassDrawable extends Drawable {

    private static final int COLS = 24;
    private static final int ROWS = 10;
    /** 中心不动区半径比：0.82 内顶点零位移、零色散，形变只在最外 18% 边缘环 */
    private static final float CORE_R = 0.82f;

    private final Paint meshPaint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    private final Paint tintPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sheenPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fallbackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path shape = new Path();
    private final Path clip = new Path();
    private final RectF r = new RectF();
    private final RectF lens = new RectF();
    private final RectF layerRect = new RectF();
    private boolean hasLens = false;
    private boolean hasClip = false;

    private final PorterDuffColorFilter fRed =
            new PorterDuffColorFilter(0xFFFF0000, PorterDuff.Mode.MULTIPLY);
    private final PorterDuffColorFilter fGreen =
            new PorterDuffColorFilter(0xFF00FF00, PorterDuff.Mode.MULTIPLY);
    private final PorterDuffColorFilter fBlue =
            new PorterDuffColorFilter(0xFF0000FF, PorterDuff.Mode.MULTIPLY);
    private final PorterDuffXfermode ADD = new PorterDuffXfermode(PorterDuff.Mode.ADD);

    private float radius;
    private int tint;
    private float refract;
    private float chroma;
    private int rimA, rimB, rimC;
    private Bitmap backdrop;
    private float[] verts, vertsR, vertsB;
    private float[] dispK, dispUx, dispUy;
    private Shader fallbackShader, rimShader, sheenShader;
    private float meshCx = Float.NaN, meshCy = Float.NaN;
    private float meshRx = -1f, meshRy = -1f, meshRefract = -1f;

    public LiquidGlassDrawable(float radiusPx) {
        this(radiusPx, 0x3D0B101A, 0f, 0f);
    }

    public LiquidGlassDrawable(float radiusPx, int tint, float refractPx, float chromaPx) {
        this.radius = radiusPx;
        this.tint = tint;
        this.refract = refractPx;
        this.chroma = chromaPx;
        this.rimA = 0xA6FFFFFF;
        this.rimB = 0x1FFFFFFF;
        this.rimC = 0x59FFE6B8;
        rimPaint.setStyle(Paint.Style.STROKE);
        rimPaint.setStrokeWidth(1.2f);
        fallbackPaint.setStyle(Paint.Style.FILL);
    }

    public LiquidGlassDrawable rim(int a, int b, int c) {
        rimA = a; rimB = b; rimC = c;
        rimShader = null;
        invalidateSelf();
        return this;
    }

    public LiquidGlassDrawable tint(int color) {
        tint = color;
        invalidateSelf();
        return this;
    }

    public LiquidGlassDrawable refract(float px) {
        refract = px;
        invalidateSelf();
        return this;
    }

    public LiquidGlassDrawable radius(float px) {
        radius = px;
        invalidateSelf();
        return this;
    }

    public LiquidGlassDrawable chroma(float px) {
        chroma = px;
        invalidateSelf();
        return this;
    }

    /** 形变中心区域（drawable 坐标系）；null 表示用整个 bounds 的中心 */
    public LiquidGlassDrawable setLens(RectF rect) {
        if (rect == null) {
            hasLens = false;
        } else {
            hasLens = true;
            lens.set(rect);
        }
        invalidateSelf();
        return this;
    }

    /** 额外裁剪（drawable 坐标系）；用于把大 backdrop 裁成 pill 胶囊 */
    public LiquidGlassDrawable setClip(RectF rect, float corner) {
        if (rect == null) {
            hasClip = false;
        } else {
            hasClip = true;
            clip.reset();
            clip.addRoundRect(rect.left, rect.top, rect.right, rect.bottom, corner, corner, Path.Direction.CW);
        }
        invalidateSelf();
        return this;
    }

    public void setBackdrop(Bitmap bmp) {
        backdrop = bmp;
        invalidateSelf();
    }

    public Bitmap backdrop() {
        return backdrop;
    }

    @Override
    public void onBoundsChange(Rect bounds) {
        fallbackShader = null;
        rimShader = null;
        sheenShader = null;
    }

    @Override
    public void draw(Canvas canvas) {
        Rect b = getBounds();
        if (b.width() <= 0 || b.height() <= 0) return;

        r.set(b.left, b.top, b.right, b.bottom);
        shape.reset();
        shape.addRoundRect(r, radius, radius, Path.Direction.CW);

        int save = canvas.save();
        // 裁剪：优先用 setClip 指定的 pill 胶囊，否则用自身圆角矩形
        canvas.clipPath(hasClip ? clip : shape);

        if (backdrop != null && !backdrop.isRecycled()) {
            drawRefracted(canvas);
        } else {
            if (fallbackShader == null) {
                fallbackShader = new LinearGradient(r.left, r.top, r.left, r.bottom,
                        new int[]{0xFF1B2233, 0xFF111722, 0xFF0A0E15},
                        new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP);
            }
            fallbackPaint.setShader(fallbackShader);
            canvas.drawRect(r, fallbackPaint);
        }

        tintPaint.setColor(tint);
        canvas.drawRect(r, tintPaint);

        if (sheenShader == null) {
            sheenShader = new LinearGradient(r.left, r.top, r.left, r.top + r.height() * 0.62f,
                    0x3DFFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP);
        }
        sheenPaint.setShader(sheenShader);
        canvas.drawRect(r, sheenPaint);

        canvas.restoreToCount(save);

        Path outline = hasClip ? clip : shape;
        // rim 恒为白色液态玻璃高光；色散由 drawRefracted 的 RGB 三通道 mesh 分离承担
        if (rimShader == null) {
            rimShader = new LinearGradient(r.left, r.top, r.right, r.bottom,
                    new int[]{rimA, rimB, rimC, rimB},
                    new float[]{0f, 0.42f, 0.78f, 1f}, Shader.TileMode.CLAMP);
        }
        rimPaint.setShader(rimShader);
        rimPaint.setStrokeWidth(hasClip ? 1.2f : 2.2f);
        canvas.drawPath(outline, rimPaint);
    }

    private void drawRefracted(Canvas canvas) {
        buildMesh();
        if (verts == null) return;

        if (chroma <= 0.01f) {
            // 无色散：单次不透明 mesh 铺满，100% 盖住下层图标，中心零形变。
            meshPaint.setXfermode(null);
            meshPaint.setColorFilter(null);
            canvas.drawBitmapMesh(backdrop, COLS, ROWS, verts, 0, null, 0, meshPaint);
            return;
        }

        // 真·玻璃色散：G 基准、R 沿法线向外、B 向内各一套顶点，MULTIPLY 提单通道
        // 再 ADD 相加。中心 dispK=0 三套顶点重合精确还原原色；边缘沿弧线柔和分离。
        buildDispersionMesh();
        int layer = canvas.saveLayer(null, null);

        meshPaint.setXfermode(null);
        meshPaint.setColorFilter(fGreen);
        canvas.drawBitmapMesh(backdrop, COLS, ROWS, verts, 0, null, 0, meshPaint);

        meshPaint.setXfermode(ADD);
        meshPaint.setColorFilter(fRed);
        canvas.drawBitmapMesh(backdrop, COLS, ROWS, vertsR, 0, null, 0, meshPaint);

        meshPaint.setColorFilter(fBlue);
        canvas.drawBitmapMesh(backdrop, COLS, ROWS, vertsB, 0, null, 0, meshPaint);

        meshPaint.setXfermode(null);
        meshPaint.setColorFilter(null);
        canvas.restoreToCount(layer);
    }

    private void buildMesh() {
        int need = 2 * (COLS + 1) * (ROWS + 1);
        float w = r.width(), h = r.height();
        if (w <= 0f || h <= 0f) return;

        RectF lr = hasLens ? lens : r;
        float cx = lr.centerX(), cy = lr.centerY();
        float rx = Math.max(1f, lr.width() / 2f), ry = Math.max(1f, lr.height() / 2f);

        // 缓存命中：尺寸 / lens / 折射强度都没变则跳过重建
        if (verts != null && verts.length == need
                && Math.abs(meshCx - cx) < 0.5f && Math.abs(meshCy - cy) < 0.5f
                && Math.abs(meshRx - rx) < 0.5f && Math.abs(meshRy - ry) < 0.5f
                && Math.abs(meshRefract - refract) < 0.01f) return;

        if (verts == null || verts.length != need) {
            verts = new float[need];
            vertsR = new float[need];
            vertsB = new float[need];
            dispK = new float[need / 2];
            dispUx = new float[need / 2];
            dispUy = new float[need / 2];
        }
        meshCx = cx; meshCy = cy; meshRx = rx; meshRy = ry; meshRefract = refract;

        float mag0 = Math.min(refract, Math.min(rx, ry) * 0.25f);

        int n = 0, v = 0;
        for (int j = 0; j <= ROWS; j++) {
            float fy = j / (float) ROWS;
            float by = r.top + fy * h;
            for (int i = 0; i <= COLS; i++) {
                float fx = i / (float) COLS;
                float bx = r.left + fx * w;

                float nx = (bx - cx) / rx;
                float ny = (by - cy) / ry;
                float rad = (float) Math.hypot(nx, ny);

                // 中心 0.82 内完全不动；0.82→1.0 的窄环按 sin 收拢，边界归零保证网格连续。
                float t = (rad - CORE_R) / (1f - CORE_R);
                if (t < 0f) t = 0f;
                if (t > 1f) t = 1f;
                float wave = (float) Math.sin(t * Math.PI);
                float mag = mag0 * wave;
                float ux = rad > 1e-4f ? nx / rad : 0f;
                float uy = rad > 1e-4f ? ny / rad : 0f;

                verts[n++] = bx + ux * mag;
                verts[n++] = by + uy * mag;
                // 形变权重用 wave（边缘归零、网格连续），色散权重用 t（越靠边缘越强）：
                // 中心(rad<0.82)两者皆 0 → 三通道零偏移零分离；弧线边缘 t→1 → 光散最强。
                dispK[v] = t;
                dispUx[v] = ux;
                dispUy[v] = uy;
                v++;
            }
        }
    }

    /** 三通道顶点：在基准 mesh 上，R 沿径向 +chroma·t，B 沿径向 -chroma·t */
    private void buildDispersionMesh() {
        if (vertsR == null || vertsB == null || dispK == null) return;
        int n = 0, v = 0;
        for (int j = 0; j <= ROWS; j++) {
            for (int i = 0; i <= COLS; i++) {
                float d = chroma * dispK[v];
                float dx = dispUx[v] * d;
                float dy = dispUy[v] * d;
                vertsR[n] = verts[n] + dx;
                vertsR[n + 1] = verts[n + 1] + dy;
                vertsB[n] = verts[n] - dx;
                vertsB[n + 1] = verts[n + 1] - dy;
                n += 2;
                v++;
            }
        }
    }

    @Override
    public void getOutline(Outline outline) {
        Rect b = getBounds();
        outline.setRoundRect(b, radius);
    }

    @Override
    public void setAlpha(int alpha) {
        tintPaint.setAlpha(alpha);
        invalidateSelf();
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        tintPaint.setColorFilter(colorFilter);
        invalidateSelf();
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
