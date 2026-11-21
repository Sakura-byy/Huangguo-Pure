package com.huangguo.pure;

import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.view.View;

/**
 * Compose 玻璃栏的背景数据源。
 *
 * Compose 的 {@code layerBackdrop} 只能录制 Compose 自己绘制的内容，而这个 App 的页面
 * 全是原生 View（RecyclerView / ExoPlayer / 自定义 View），所以走
 * {@code rememberCanvasBackdrop}：Java 侧把玻璃所在矩形从原生视图树里截成一张位图，
 * Compose 侧每帧把它画到 backdrop 上，再由官方 backdrop 库做 vibrancy / blur / lens / 色散。
 *
 * 采样靠 {@link Backdrop.RegionSampler}（位图与 Canvas 复用、相交子树裁剪），
 * 静止时不采样，滚动 / 切页由 {@link #poke()} 按节流触发。
 */
public final class DockBackdrop {

    /** 采样降采样倍数：2 倍即已在 Compose 侧被双线性放大一次，再叠 blur(8dp) 刚好 */
    private static final int DS = 2;
    /** 采样最短间隔（ms）：一次滑动一帧多次回调时不重复画 */
    private static final long MIN_INTERVAL_NS = 8_000_000L;

    private final Backdrop.RegionSampler sampler = new Backdrop.RegionSampler();
    private final Rect region = new Rect();
    private final int[] targetLoc = new int[2];
    private final int[] sourceLoc = new int[2];
    private final Handler main = new Handler(Looper.getMainLooper());

    private View source;
    private View target;
    private Bitmap bitmap;
    private Runnable onChange;
    private long lastSampleNs;
    private boolean destroyed;

    /** 采样来源（页面容器） */
    public void setSource(View v) {
        source = v;
        sample(true);
    }

    /** 玻璃栏自身（用于换算它在 source 里的位置与尺寸） */
    public void setTarget(View v) {
        target = v;
        sample(true);
    }

    public Bitmap getBitmap() {
        return bitmap;
    }

    /** Compose 侧注册重绘回调；传 null 取消 */
    public void setOnChange(Runnable r) {
        onChange = r;
    }

    /** 触摸 / 滚动 / 切页时调用：按节流重采一帧 */
    public void poke() {
        sample(false);
    }

    /** 强制重采（页面切换、主题切换、布局变化） */
    public void refresh() {
        sample(true);
    }

    public void destroy() {
        destroyed = true;
        onChange = null;
        sampler.release();
        bitmap = null;
    }

    private void sample(boolean force) {
        if (destroyed || source == null || target == null) return;
        int w = target.getWidth(), h = target.getHeight();
        if (w <= 0 || h <= 0) return;
        if (!source.isAttachedToWindow() || !target.isAttachedToWindow()) return;
        long now = System.nanoTime();
        if (!force && now - lastSampleNs < MIN_INTERVAL_NS) return;
        lastSampleNs = now;

        target.getLocationInWindow(targetLoc);
        source.getLocationInWindow(sourceLoc);
        int ox = targetLoc[0] - sourceLoc[0];
        int oy = targetLoc[1] - sourceLoc[1];
        region.set(ox, oy, ox + w, oy + h);

        final Bitmap bmp = sampler.sample(DS, region,
                c -> Backdrop.drawRegionCheap(source, c, region));
        if (bmp == null || bmp == bitmap) {
            // RegionSampler 复用同一张位图：内容已更新，也要通知 Compose 重绘
            if (bmp != null) notifyChange();
            return;
        }
        bitmap = bmp;
        notifyChange();
    }

    private void notifyChange() {
        final Runnable r = onChange;
        if (r == null) return;
        if (Looper.myLooper() == Looper.getMainLooper()) {
            r.run();
        } else {
            main.post(r);
        }
    }
}
