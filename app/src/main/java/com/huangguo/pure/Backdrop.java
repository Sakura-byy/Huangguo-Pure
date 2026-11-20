package com.huangguo.pure;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;

/**
 * Liquid Glass 的背景采样子系统。
 *
 * 玻璃要「透」就必须拿到玻璃后面的像素。Android 上没有 Web 的 backdrop-filter，
 * 也不能用 PixelCopy（会把玻璃自身一起拍进去，形成反馈环）。
 * 这里的做法是把「玻璃后面的那个 View」用一个软件 Canvas + 降采样画进位图：
 * 降采样倍数越高，回填到玻璃上时的双线性放大就越糊，天然得到 blur 的效果，
 * 且不依赖 RenderEffect，API 24 起全部可用。
 */
public final class Backdrop {

    /** Dock 这级大玻璃：6 倍降采样，糊得厚，正文完全化开 */
    public static final int DS_HEAVY = 9;
    /** 覆盖在图标上的指示器：2 倍降采样，能看见图标形状被折射 */
    public static final int DS_LIGHT = 2;

    private Backdrop() {
    }

    /** 把 source 画进一张 1/ds 尺寸的位图，尺寸取 source 自身大小 */
    public static Bitmap snapshot(View source, int ds) {
        if (source == null) return null;
        int w = source.getWidth(), h = source.getHeight();
        if (w <= 0 || h <= 0) return null;
        int bw = Math.max(6, w / ds), bh = Math.max(6, h / ds);
        Bitmap bmp;
        try {
            bmp = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888);
        } catch (Throwable t) {
            return null;
        }
        Canvas c = new Canvas(bmp);
        c.scale(bw / (float) w, bh / (float) h);
        try {
            source.draw(c);
        } catch (Throwable t) {
            bmp.recycle();
            return null;
        }
        return bmp;
    }

    /**
     * 只渲染 source 里的某一块矩形（source 坐标系），尺寸再降采样 ds 倍。
     * Dock 只要底部那条，用这个就不必把整页画进软件画布，帧耗时从十几毫秒降到 1~2ms。
     */
    public static Bitmap snapshotRegion(View source, int ds, Rect region) {
        if (source == null || region == null) return null;
        if (region.width() <= 0 || region.height() <= 0) return null;
        int bw = Math.max(4, region.width() / ds);
        int bh = Math.max(4, region.height() / ds);
        Bitmap bmp;
        try {
            bmp = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888);
        } catch (Throwable t) {
            return null;
        }
        Canvas c = new Canvas(bmp);
        float k = bw / (float) region.width();
        c.scale(k, k);
        c.translate(-region.left, -region.top);
        try {
            source.draw(c);
        } catch (Throwable t) {
            bmp.recycle();
            return null;
        }
        return bmp;
    }

/**
 * 可复用的区域采样器：位图 / Canvas 全程复用，按帧采也不产生分配与 GC。
 *
 * 原来 GlassDock 每 700ms 调一次 {@link #snapshotRegion} —— 每次都 createBitmap，
 * 而且 700ms 的间隔让玻璃里的画面明显滞后于列表滚动。用这个类把位图固定下来，
 * 就能把采样提到接近屏幕刷新率，且没有分配压力。
 * 调用方拿到的位图属于本对象，禁止 recycle；尺寸变化时内部自行重建。
 */
public static final class RegionSampler {
    private Bitmap bmp;
    private Canvas canvas;
    private int bw, bh;
    private long lastCostNs;

    /** 自定义内容绘制器：只画与目标区域相交的那部分子树，比整树 draw 快一个数量级 */
    public interface Drawer {
        void draw(Canvas c);
    }

    /** 采样 source 中 region（source 坐标系）那一块，降采样 ds 倍，返回复用位图 */
    public Bitmap sample(View source, int ds, Rect region) {
        return sample(ds, region, c -> source.draw(c));
    }

    /** 同上，但内容由 Drawer 提供（相交裁剪版） */
    public Bitmap sample(int ds, Rect region, Drawer d) {
        if (region == null || region.width() <= 0 || region.height() <= 0) return null;
        int nw = Math.max(4, region.width() / ds);
        int nh = Math.max(4, region.height() / ds);
        long t0 = System.nanoTime();
        if (bmp == null || bmp.isRecycled() || bw != nw || bh != nh) {
            if (bmp != null && !bmp.isRecycled()) bmp.recycle();
            bw = nw;
            bh = nh;
            try {
                bmp = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888);
            } catch (Throwable t) {
                bmp = null;
                return null;
            }
            canvas = new Canvas(bmp);
        }
        canvas.drawColor(0, PorterDuff.Mode.CLEAR);
        float k = bw / (float) region.width();
        canvas.save();
        canvas.scale(k, k);
        canvas.translate(-region.left, -region.top);
        try {
            d.draw(canvas);
        } catch (Throwable ignored) {
            // 子 View 偶发 draw 异常不能拖垮整个玻璃
        }
        canvas.restore();
        lastCostNs = System.nanoTime() - t0;
        return bmp;
    }

    /** 上一次采样耗时（毫秒），供调用方做自适应节流 */
    public long lastCostMs() {
        return lastCostNs / 1000000L;
    }

    public void release() {
        if (bmp != null && !bmp.isRecycled()) bmp.recycle();
        bmp = null;
        canvas = null;
    }
}

/**
 * 相交裁剪绘制：只递归进「与 region 有交集」的子树，逐个叶子 translate + draw。
 * 整树 draw 的开销在于遍历所有节点（RecyclerView 每个可见 ViewHolder 都要画），
 * 与位图尺寸无关；Dock 只需要底部一条，跳过不相交的节点能把单次采样
 * 从 20~40ms 压到 2~6ms，滑动时玻璃才能逐帧折射。
 */
public static void drawRegionCheap(View root, Canvas c, Rect region) {
    c.drawColor(Ui.C_BG);
    drawNode(root, c, region, 0, 0);
}

private static void drawNode(View v, Canvas c, Rect region, int ox, int oy) {
    if (v.getVisibility() != View.VISIBLE) return;
    int l = ox + Math.round(v.getTranslationX());
    int t = oy + Math.round(v.getTranslationY());
    int r = l + v.getWidth();
    int b = t + v.getHeight();
    if (r <= region.left || l >= region.right || b <= region.top || t >= region.bottom) return;

    if (v instanceof ViewGroup) {
        ViewGroup g = (ViewGroup) v;
        int sx = g.getScrollX(), sy = g.getScrollY();
        for (int i = 0; i < g.getChildCount(); i++) {
            View ch = g.getChildAt(i);
            drawNode(ch, c, region, l + ch.getLeft() - sx, t + ch.getTop() - sy);
        }
    } else {
        int save = c.save();
        c.translate(l, t);
        try {
            v.draw(c);
        } catch (Throwable ignored) {
        }
        c.restoreToCount(save);
    }
}

/** 新建一张带尺寸的缓冲位图（尺寸为 0 时返回 null） */
public static Bitmap alloc(int w, int h, int ds) {
        int bw = Math.max(4, w / ds), bh = Math.max(4, h / ds);
        try {
            return Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888);
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * 把 snap（source 的整页快照）里与 target 重合的那一块，铺满 dst。
     * dst 尺寸固定、反复复用，所以拖拽时每帧只做一次小位图拷贝，没有分配压力。
     */
    public static void slice(Bitmap snap, View source, View target, Bitmap dst) {
        if (snap == null || snap.isRecycled() || dst == null || dst.isRecycled()) return;
        if (source == null || target == null) return;
        int sw = source.getWidth(), sh = source.getHeight();
        if (sw <= 0 || sh <= 0) return;

        int[] so = new int[2];
        int[] to = new int[2];
        source.getLocationInWindow(so);
        target.getLocationInWindow(to);

        float kx = snap.getWidth() / (float) sw;
        float ky = snap.getHeight() / (float) sh;

        int x = Math.round((to[0] - so[0]) * kx);
        int y = Math.round((to[1] - so[1]) * ky);
        int w = Math.max(1, Math.round(target.getWidth() * kx));
        int h = Math.max(1, Math.round(target.getHeight() * ky));

        Rect src = new Rect(x, y, x + w, y + h);
        Rect lim = new Rect(0, 0, snap.getWidth(), snap.getHeight());
        if (!src.intersect(lim)) return;

        Canvas c = new Canvas(dst);
        c.drawColor(0, PorterDuff.Mode.CLEAR);
        c.drawBitmap(snap, src, new Rect(0, 0, dst.getWidth(), dst.getHeight()),
                new Paint(Paint.FILTER_BITMAP_FLAG));
    }
}
