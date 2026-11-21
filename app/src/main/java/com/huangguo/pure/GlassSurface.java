package com.huangguo.pure;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.FrameLayout;

/**
 * 一块会自己采背景的液态玻璃容器：用来做悬浮按钮、圆角小卡这类「浮在内容上」的件。
 *
 * 性能关键：只把 source 里「玻璃自己占的那一小块」画进可复用位图（RegionSampler），
 * 不再把整页 snapshot + slice —— 旧版每 380ms 全页软件光栅化一次，
 * 和 Dock 的帧级采样叠在一起就是主线程卡死的来源。现在区域只有按钮大小，
 * 单次采样的成本与 Dock 的区域采样同量级。
 */
public class GlassSurface extends FrameLayout {

    /**
     * 采样周期。原来 900ms：滑动页面时玻璃里的背景要等将近 1 秒才换一帧，
     * 看着像「贴纸」而不是玻璃。现在按 30fps 走，同时用上一帧实际耗时兜底，
     * 采样本身变慢时自动拉长间隔，页面里内容再重也不会把主线程塞满。
     */
    private static final long PERIOD = 33L;

    /** 兜底倍率：上一帧采样花了 ms，就至少隔 3 倍时间再来下一次 */
    private static final long COST_FACTOR = 3L;

    /** 区域采样的降采样倍数：区域只有按钮大小，4 倍仍有层次，比原来 9 倍清楚得多 */
    private static final int DS = 4;

    /** 本块玻璃自己的降采样倍数；顶栏调大做成磨砂，按钮保持小值保留细节 */
    private int ds = DS;

    /** 本块玻璃的采样周期（ms） */
    private long period = PERIOD;

    private final LiquidGlassDrawable glass;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final Backdrop.RegionSampler regionSampler = new Backdrop.RegionSampler();
    private final Rect region = new Rect();
    private final int[] soBuf = new int[2];
    private final int[] goBuf = new int[2];

    private View source;
    private boolean looping;

    /** 调整采样周期（ms）：面积大的玻璃可以放慢，肉眼看不出来但省 CPU */
    public GlassSurface samplePeriod(long ms) {
        period = Math.max(16L, ms);
        return this;
    }

    /** 调整降采样倍数（越大越糊、越省） */
    public GlassSurface sampleDs(int value) {
        ds = Math.max(1, value);
        return this;
    }

    private final Runnable loop = new Runnable() {
        @Override
        public void run() {
            refresh();
            if (!looping) return;
            long cost = regionSampler.lastCostMs();
            ui.postDelayed(this, Math.max(period, cost * COST_FACTOR));
        }
    };

    public GlassSurface(Context c, float radiusDp, int tint, float refractDp) {
        super(c);
        glass = new LiquidGlassDrawable(Ui.dp(c, radiusDp), tint,
                Ui.dp(c, refractDp), 0.9f);
        setBackground(glass);
        setClipToOutline(false);
    }

    public LiquidGlassDrawable glass() {
        return glass;
    }

    /** source 是玻璃「后面」的那一层，通常是页面容器 */
    public GlassSurface bind(View source) {
        this.source = source;
        if (isAttachedToWindow()) start();
        return this;
    }

    private void start() {
        if (looping) return;
        looping = true;
        ui.removeCallbacks(loop);
        ui.post(loop);
    }

    private void stop() {
        looping = false;
        ui.removeCallbacks(loop);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        start();
    }

    @Override
    protected void onDetachedFromWindow() {
        stop();
        regionSampler.release();
        glass.setBackdrop(null);
        super.onDetachedFromWindow();
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        refresh();
    }

    /** 采样区域在自身矩形基础上的偏移（px）：顶栏用它去采「下方滚上来的内容」 */
    protected int sampleShiftX() {
        return 0;
    }

    protected int sampleShiftY() {
        return 0;
    }

    public void refresh() {
        if (source == null || getWidth() <= 0 || getHeight() <= 0) return;
        if (!isShown()) return;
        // 必须用屏幕上绝对坐标：PopupWindow 里的玻璃自成一层 window，
        // getLocationInWindow 拿到的是弹窗内部坐标，减出来是个负数区域，
        // 结果采样到页面左上角 —— 菜单里会莫名出现标题文字（「我的」就是这么来的）。
        source.getLocationOnScreen(soBuf);
        getLocationOnScreen(goBuf);
        int left = goBuf[0] - soBuf[0] + sampleShiftX();
        int top = goBuf[1] - soBuf[1] + sampleShiftY();
        region.set(left, top, left + getWidth(), top + getHeight());
        Bitmap snap = regionSampler.sample(ds, region,
                (c) -> Backdrop.drawRegionCheap(source, c, region));
        if (snap == null) return;
        glass.setBackdrop(snap);
    }
}
