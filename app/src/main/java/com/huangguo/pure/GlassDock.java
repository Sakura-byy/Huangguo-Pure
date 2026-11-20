package com.huangguo.pure;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;
import android.view.Choreographer;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;

import androidx.core.content.ContextCompat;

/**
 * Liquid Glass 底部 Dock —— 按 Kyant0/AndroidLiquidGlass 的 LiquidBottomTabs 三层结构重写：
 *
 *   ① 容器玻璃（Capsule 64dp，折射页面背景，四周彩虹色散，按压时轻微放大）
 *   ② 中性图标层（所有图标固定网格位置，中心零位移零形变）
 *   ③ pill 指示器（不透明盖住下层，折射「模糊背景 + accent 图标」合成图）
 *
 * 无重影的关键：pill 是一块「不透明」玻璃，它把下层中性图标完全盖住，
 * 只在 pill 内显示自己 backdrop 里的 accent 图标；backdrop 是一整张 Dock 大图，
 * 边缘折射采样到的是相邻的真实像素而不是透明区，因此不会出现双层图标或背景透出。
 */
public class GlassDock extends View {

    public interface OnSelect {
        void onSelect(int index);
    }

    private static final float PAD_DP = 4f;
    /** 容器玻璃高度（View 高 78dp，玻璃居中 64dp，pill 按压凸出） */
    private static final float GLASS_H_DP = 64f;
    private static final float PILL_H_DP = 56f;
    private static final float ICON_DP = 26f;
    private static final float LABEL_SP = 10.5f;
    private static final float GAP_DP = 3f;
    /** 上游 pressedScale = 78 / 56 */
    private static final float PRESS_PILL = 78f / 56f;
    /** 合成图外扩：给折射边缘留出采样余量 */
    private static final float MARGIN_DP = 40f;
    private static final long GAP_FLOOR_MS = 32L;
    private static final long GAP_CEIL_MS = 400L;

    private final int count;
    private final String[] labels;
    private final Drawable[] icons;
    private final OnSelect listener;
    private final Choreographer choreo = Choreographer.getInstance();
    private final Backdrop.RegionSampler containerSampler = new Backdrop.RegionSampler();
    private final Backdrop.RegionSampler sceneSampler = new Backdrop.RegionSampler();

    private final float padPx;
    private final float glassHpx;
    private final float pillHpx;
    private final float iconPx;
    private final float labelPx;
    private final float gapPx;
    private final float marginPx;

    /** ① 容器玻璃：折射页面背景 */
    private final LiquidGlassDrawable glassDd;
    /** ③ pill 透镜：折射「背景 + accent 图标」合成大图 */
    private final LiquidGlassDrawable pillGlass;

    private final Paint filterPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    /** 容器外缘明确描边：独立于 LiquidGlassDrawable rim，保证静止/按压全程可见 */
    private final Paint containerEdgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path containerEdgePath = new Path();
    private final RectF containerEdgeRect = new RectF();
    private final RectF pillRect = new RectF();
    private final RectF pillLens = new RectF();
    private final RectF pillClip = new RectF();
    private final Rect region = new Rect();

    /** pill 合成大图（dock + 外扩）：模糊背景 + containerColor + accent 图标 */
    private Bitmap sceneBmp;
    private Canvas sceneCanvas;
    private final Rect sceneDst = new Rect();
    private boolean sceneDirty = true;

    private View backdropSource;
    private int selected = 0;
    private float itemW = 1f;
    /** pill 所在格位（0..count-1，可小数：拖动跟手） */
    private float pillPos = 0f;
    private float lastPillPos = 0f;
    private float pillVel = 0f;
    /** 按压进度 0..1，同时驱动容器放大、pill 放大、pill 折射/色散 */
    private float pressT = 0f;

    private float downX = 0f;
    private boolean dragging = false;

    private ValueAnimator pressAnim;
    private ValueAnimator pillAnim;

    private boolean sampling = false;
    private boolean bgDirty = true;
    private long lastSampleAt = 0L;
    private long gapMs = 120L;
    private long lastTouchMs = 0L;
    private final int[] soBuf = new int[2];
    private final int[] goBuf = new int[2];
    private long prevFrameNs = 0L;

    /** 外部触摸时拍一下：切到高频采样档 */
    public void poke() {
        lastTouchMs = android.os.SystemClock.uptimeMillis();
    }

    private final Choreographer.FrameCallback frameCb = new Choreographer.FrameCallback() {
        @Override
        public void doFrame(long frameTimeNanos) {
            if (!sampling) return;
            long frameDt = prevFrameNs == 0L ? 16_000_000L : frameTimeNanos - prevFrameNs;
            prevFrameNs = frameTimeNanos;
            boolean uiStruggling = frameDt > 28_000_000L;

            long now = android.os.SystemClock.uptimeMillis();
            if (!uiStruggling && now - lastSampleAt >= gapMs) {
                lastSampleAt = now;
                sampleBackdrop();
                bgDirty = true;
                long cost = containerSampler.lastCostMs();
                boolean touching = now - lastTouchMs < 400L;
                long floor = touching ? GAP_FLOOR_MS : 200L;
                gapMs = Math.max(floor, Math.min(GAP_CEIL_MS, cost * 4L));
            }
            choreo.postFrameCallback(this);
        }
    };

    public GlassDock(Context c, String[] labels, int[] iconRes, OnSelect listener) {
        super(c);
        this.count = labels.length;
        this.labels = labels;
        this.listener = listener;
        this.icons = new Drawable[count];
        for (int i = 0; i < count; i++) {
            Drawable d = ContextCompat.getDrawable(c, iconRes[i]);
            if (d != null) d = d.mutate();
            icons[i] = d;
        }

        padPx = Ui.dp(c, PAD_DP);
        glassHpx = Ui.dp(c, GLASS_H_DP);
        pillHpx = Ui.dp(c, PILL_H_DP);
        iconPx = Ui.dp(c, ICON_DP);
        gapPx = Ui.dp(c, GAP_DP);
        marginPx = Ui.dp(c, MARGIN_DP);
        labelPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, LABEL_SP,
                c.getResources().getDisplayMetrics());

        // ① 容器玻璃：只折射页面背景 + 白色高光 rim，容器四周不画任何 RGB 色散描边。
        //    彩虹色散只在按压凸起的 pill 边缘出现（chroma 独立由 pillGlass 控制）。
        glassDd = new LiquidGlassDrawable(Ui.dp(c, 32),
                Ui.darkGlass ? 0x66121522 : 0x66FFFFFF,
                Ui.dp(c, 12), 0f);
        // 容器 rim：均匀一圈细边（静止/按压全程可见，参考图容器外缘始终有边框）。
        // 深色用白边、浅色用墨边；三段同色保证 LinearGradient 不出现透明断点。
        // 容器内缘 rim 交给 onDraw 的液态玻璃高光边统一处理，这里设为全透明避免双重描边
        glassDd.rim(0x00FFFFFF, 0x00FFFFFF, 0x00FFFFFF);

        // ③ pill：色散随按压从 0 增长；上游 lens(10dp,14dp, chromaticAberration=true)
        pillGlass = new LiquidGlassDrawable(Ui.dp(c, 28),
                Ui.darkGlass ? 0x0A121824 : 0x08FFFFFF,
                0f, 0f);
        pillGlass.rim(0xE0FFFFFF, 0x1FFFFFFF, 0x59FFFFFF);

        labelPaint.setTextAlign(Paint.Align.CENTER);
        labelPaint.setTypeface(Typeface.DEFAULT);
        labelPaint.setAntiAlias(true);
        setClickable(true);
        setFocusable(true);
    }

    // ------------------------------------------------------------------ 尺寸

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        itemW = Math.max(1f, (w - padPx * 2f) / count);
        pillPos = Math.max(0f, Math.min(count - 1f, pillPos));
        lastPillPos = pillPos;
        sceneDirty = true;
        invalidate();
        sampleBackdrop();
    }

    // ------------------------------------------------------------------ 绘制

    @Override
    protected void onDraw(Canvas canvas) {
        final int w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;

        // 容器轻微放大（上游 scale = 1 + 16dp / width，只作用于容器玻璃，不动图标）
        float cScale = 1f + (Ui.dp(getContext(), 16f) / w) * pressT;
        float gw = w * cScale;
        float gh = glassHpx * cScale;
        float gt = (h - gh) / 2f;
        glassDd.setBounds(Math.round((w - gw) / 2f), Math.round(gt),
                Math.round((w + gw) / 2f), Math.round(gt + gh));

        updatePillGeom(w, h);
        ensureScene(w, h);

        // ① 容器玻璃
        glassDd.draw(canvas);

        // ② 中性图标层：固定网格，中心零位移零形变
        drawItems(canvas, h, false);

        // ③ pill：不透明盖住下层中性图标，内部显示 accent 图标 + 边缘折射/色散
        if (sceneBmp != null && !sceneBmp.isRecycled()) {
            float progress = pressT;
            pillGlass.refract(Ui.dp(getContext(), 10f) * progress);
            pillGlass.chroma(Ui.dp(getContext(), 4.5f) * progress);
            pillGlass.setLens(pillLens);
            pillGlass.setClip(pillClip, pillRect.height() / 2f);
            pillGlass.setBackdrop(sceneBmp);
            pillGlass.setBounds(-Math.round(marginPx), -Math.round(marginPx),
                    w + Math.round(marginPx), h + Math.round(marginPx));
            pillGlass.draw(canvas);
        }

        // ①-b 容器液态玻璃高光边：柔和白边 + 墨色极淡内衬（无硬描边线）
        containerEdgeRect.set(
                Math.round((w - gw) / 2f), gt,
                Math.round((w + gw) / 2f), gt + gh);
        containerEdgePath.reset();
        containerEdgePath.addRoundRect(containerEdgeRect,
                Ui.dp(getContext(), 32), Ui.dp(getContext(), 32), Path.Direction.CW);
        containerEdgePaint.setStyle(Paint.Style.STROKE);
        // 液态玻璃边：白色半透明高光，靠 glass 折射产生柔和亮边，不用实线
        containerEdgePaint.setStrokeWidth(Ui.dp(getContext(), 3f));
        containerEdgePaint.setColor(Ui.darkGlass ? 0x4DFFFFFF : 0x8CFFFFFF);
        canvas.drawPath(containerEdgePath, containerEdgePaint);
    }

    /** pill 几何 + 速度形变（对齐上游 velocity scaleX/scaleY） */
    private void updatePillGeom(int w, int h) {
        float cx = padPx + pillPos * itemW + itemW / 2f;
        float cy = h / 2f;
        float scale = 1f + (PRESS_PILL - 1f) * pressT;
        float pw = itemW * scale;
        float ph = pillHpx * scale;

        float vel = Math.max(-3f, Math.min(3f, pillVel / 10f));
        float sx = 1f / (1f - clamp(vel * 0.75f, -0.2f, 0.2f));
        float sy = 1f - clamp(vel * 0.25f, -0.2f, 0.2f);
        pw *= sx;
        ph *= sy;

        pillRect.set(cx - pw / 2f, cy - ph / 2f, cx + pw / 2f, cy + ph / 2f);
        // drawable 的 bounds 已整体偏移 -margin，其内部坐标恰好等于 dock 像素坐标，
        // 所以 lens / clip 直接用 pillRect，不能再加 margin（否则 pill 偏移 40dp）。
        pillLens.set(pillRect);
        pillClip.set(pillRect);
    }

    /**
     * 合成 pill 的 backdrop 大图：模糊页面背景铺满 + containerColor + accent 图标固定网格。
     * 背景随滚动更新（bgDirty），尺寸变化时重建。图标在固定位置，不随 pillPos 移动。
     */
    private void ensureScene(int w, int h) {
        int m = Math.round(marginPx);
        int bw = w + m * 2, bh = h + m * 2;
        if (sceneBmp == null || sceneBmp.isRecycled()
                || sceneBmp.getWidth() != bw || sceneBmp.getHeight() != bh) {
            if (sceneBmp != null && !sceneBmp.isRecycled()) sceneBmp.recycle();
            try {
                sceneBmp = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888);
            } catch (Throwable t) {
                sceneBmp = null;
                return;
            }
            sceneCanvas = new Canvas(sceneBmp);
            sceneDirty = true;
        }
        if (!sceneDirty) return;

        sceneCanvas.drawColor(0, PorterDuff.Mode.CLEAR);
        // 先铺不透明底色，杜绝「背景透出」
        sceneCanvas.drawColor(Ui.darkGlass ? 0xFF0A0E15 : Ui.C_BG);

        if (backdropSource != null && isShown() && backdropSource.isShown()) {
            backdropSource.getLocationInWindow(soBuf);
            getLocationInWindow(goBuf);
            int ox = goBuf[0] - soBuf[0];
            int oy = goBuf[1] - soBuf[1];
            region.set(ox, oy, ox + w, oy + h);
            Bitmap bg = sceneSampler.sample(Backdrop.DS_HEAVY, region,
                    c -> Backdrop.drawRegionCheap(backdropSource, c, region));
            if (bg != null && !bg.isRecycled()) {
                sceneDst.set(0, 0, bw, bh);
                sceneCanvas.drawBitmap(bg, null, sceneDst, filterPaint);
            }
        }
        sceneCanvas.drawColor(Ui.darkGlass ? 0x66121522 : 0x66FFFFFF);
        sceneCanvas.save();
        sceneCanvas.translate(m, m);
        drawItems(sceneCanvas, h, true);
        sceneCanvas.restore();

        sceneDirty = false;
    }

    /** 画图标 + 文字：accent=true 用选中蓝（pill 内），否则中性色（pill 外） */
    private void drawItems(Canvas canvas, int h, boolean accent) {
        float blockH = iconPx + gapPx + labelPx;
        float top = (h - blockH) / 2f;
        float baseline = top + iconPx + gapPx + labelPx * 0.80f;
        int color = accent
                ? Ui.accent()
                : (Ui.darkGlass ? 0xFFF2F4F8 : 0xFF101722);

        for (int i = 0; i < count; i++) {
            float cx = padPx + itemW * (i + 0.5f);
            Drawable d = icons[i];
            if (d != null) {
                int l = Math.round(cx - iconPx / 2f);
                int t = Math.round(top);
                d.setColorFilter(color, PorterDuff.Mode.SRC_IN);
                d.setBounds(l, t, l + Math.round(iconPx), t + Math.round(iconPx));
                d.draw(canvas);
            }
            labelPaint.setColor(color);
            labelPaint.setTextSize(labelPx);
            labelPaint.setFakeBoldText(false);
            canvas.drawText(labels[i], cx, baseline, labelPaint);
        }
    }

    // ------------------------------------------------------------------ 采样

    public void setBackdropSource(View source) {
        backdropSource = source;
        startSampling();
    }

    public void refresh() {
        lastSampleAt = 0L;
        sampleBackdrop();
        startSampling();
    }

    private void startSampling() {
        if (sampling || !isAttachedToWindow()) return;
        sampling = true;
        choreo.postFrameCallback(frameCb);
    }

    private void stopSampling() {
        sampling = false;
        choreo.removeFrameCallback(frameCb);
    }

    private void sampleBackdrop() {
        if (backdropSource == null || getWidth() <= 0 || getHeight() <= 0) return;
        if (!isShown() || !backdropSource.isShown()) return;

        backdropSource.getLocationInWindow(soBuf);
        getLocationInWindow(goBuf);
        int ox = goBuf[0] - soBuf[0];
        int oy = goBuf[1] - soBuf[1];
        int glassTop = Math.round((getHeight() - glassHpx) / 2f);
        region.set(ox, oy + glassTop, ox + getWidth(), oy + glassTop + Math.round(glassHpx));

        Bitmap snap = containerSampler.sample(Backdrop.DS_HEAVY, region,
                c -> Backdrop.drawRegionCheap(backdropSource, c, region));
        if (snap == null) return;
        glassDd.setBackdrop(snap);
        bgDirty = true;
        sceneDirty = true;
        invalidate();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        startSampling();
    }

    @Override
    protected void onDetachedFromWindow() {
        stopSampling();
        containerSampler.release();
        sceneSampler.release();
        glassDd.setBackdrop(null);
        pillGlass.setBackdrop(null);
        if (sceneBmp != null && !sceneBmp.isRecycled()) sceneBmp.recycle();
        sceneBmp = null;
        sceneCanvas = null;
        cancel(pressAnim);
        cancel(pillAnim);
        super.onDetachedFromWindow();
    }

    private static void cancel(ValueAnimator a) {
        if (a != null) a.cancel();
    }

    // ------------------------------------------------------------------ 触摸

    private int indexAt(float x) {
        int i = (int) ((x - padPx) / itemW);
        return Math.max(0, Math.min(count - 1, i));
    }

    private static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = e.getX();
                dragging = false;
                lastPillPos = pillPos;
                animatePress(true);
                return true;

            case MotionEvent.ACTION_MOVE: {
                float dx = e.getX() - downX;
                if (!dragging && Math.abs(dx) > Ui.dp(getContext(), 7f)) dragging = true;
                if (!dragging) return true;
                cancel(pillAnim);
                float next = clamp((e.getX() - padPx) / itemW - 0.5f, 0f, count - 1f);
                pillVel = next - lastPillPos;
                lastPillPos = next;
                pillPos = next;
                invalidate();
                return true;
            }

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                animatePress(false);
                int target = dragging ? Math.round(pillPos) : indexAt(e.getX());
                target = Math.max(0, Math.min(count - 1, target));
                dragging = false;
                pillVel = 0f;
                if (target != selected) {
                    select(target);
                } else {
                    animatePill(target, true);
                }
                performClick();
                return true;
            }

            default:
                return super.onTouchEvent(e);
        }
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }

    private void animatePress(final boolean down) {
        cancel(pressAnim);
        final float from = pressT;
        final float to = down ? 1f : 0f;
        if (Math.abs(from - to) < 0.001f) {
            pressT = to;
            invalidate();
            return;
        }
        pressAnim = ValueAnimator.ofFloat(from, to);
        pressAnim.setDuration(down ? 130 : 260);
        pressAnim.setInterpolator(down
                ? new DecelerateInterpolator()
                : new OvershootInterpolator(1.1f));
        pressAnim.addUpdateListener(a -> {
            pressT = (float) a.getAnimatedValue();
            invalidate();
        });
        pressAnim.start();
    }

    private void animatePill(final int index, boolean animate) {
        cancel(pillAnim);
        if (!animate || Math.abs(pillPos - index) < 0.001f) {
            pillPos = index;
            lastPillPos = index;
            pillVel = 0f;
            invalidate();
            return;
        }
        pillAnim = ValueAnimator.ofFloat(pillPos, index);
        pillAnim.setDuration(300);
        pillAnim.setInterpolator(new OvershootInterpolator(1.02f));
        pillAnim.addUpdateListener(a -> {
            float v = (float) a.getAnimatedValue();
            pillVel = v - lastPillPos;
            lastPillPos = v;
            pillPos = v;
            invalidate();
        });
        pillAnim.start();
    }

    // ------------------------------------------------------------------ 对外

    public int selected() {
        return selected;
    }

    public void select(int index) {
        setSelected(index, true);
        if (listener != null) listener.onSelect(index);
    }

    public void setSelected(int index, boolean animate) {
        if (index < 0 || index >= count) return;
        boolean changed = index != selected;
        selected = index;
        animatePill(index, animate);
        if (changed && animate) performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
    }
}
