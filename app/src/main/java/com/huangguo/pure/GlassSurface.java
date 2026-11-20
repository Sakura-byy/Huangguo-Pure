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

    /** 按钮背后只有极光慢漂，900ms 一轮足够鲜活；每轮一次整树遍历，频率直接决定卡不卡 */
    private static final long PERIOD = 900L;

    private final LiquidGlassDrawable glass;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final Backdrop.RegionSampler regionSampler = new Backdrop.RegionSampler();
    private final Rect region = new Rect();
    private final int[] soBuf = new int[2];
    private final int[] goBuf = new int[2];

    private View source;
    private boolean looping;

    private final Runnable loop = new Runnable() {
        @Override
        public void run() {
            refresh();
            if (looping) ui.postDelayed(this, PERIOD);
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

    public void refresh() {
        if (source == null || getWidth() <= 0 || getHeight() <= 0) return;
        if (!isShown()) return;
        source.getLocationInWindow(soBuf);
        getLocationInWindow(goBuf);
        region.set(goBuf[0] - soBuf[0], goBuf[1] - soBuf[1],
                goBuf[0] - soBuf[0] + getWidth(), goBuf[1] - soBuf[1] + getHeight());
        Bitmap snap = regionSampler.sample(Backdrop.DS_HEAVY, region,
                (c) -> Backdrop.drawRegionCheap(source, c, region));
        if (snap == null) return;
        glass.setBackdrop(snap);
    }
}
