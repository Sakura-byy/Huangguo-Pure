package com.huangguo.pure;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.os.SystemClock;
import android.view.View;

/** 背景极光：三团缓慢漂移的柔光，给玻璃面板提供背衬层次 */
public class Aurora extends View {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float phase;
    private float r0, r1, r2;
    private int w, h;
    /** 26 秒一圈的慢漂：相位直接按开机时间算，不用 ValueAnimator */
    private static final long CYCLE_MS = 26000L;
    /** 重绘节流到 ~15fps：全屏 3 团 RadialGradient 每帧重绘 GPU 顶不住，
     *  26 秒一圈的慢漂在这个帧率下肉眼无差别 */
    private static final long PAINT_GAP_MS = 66L;
    private long lastTickAt = 0L;
    /** 活跃时长累计：暂停期间不累加，恢复时从原位继续，不会跳变 */
    private long activeMs = 0L;
    private boolean running;

    /** 全局忙状态（列表滚动 / 手指在屏上）：此时极光不重绘 */
    private static volatile boolean globalBusy;

    /**
     * 自调度重绘：每 PAINT_GAP_MS 走一步。
     *
     * 原来用 ValueAnimator：它每帧（~60fps）都会回调一次，再靠时间戳丢掉 3/4 的帧，
     * 白唤醒 CPU 45 次/秒；且 Activity 退到后台、窗口不可见时动画照跑。
     * 现在只有「窗口可见」才排下一次重绘，后台完全静止 —— 观感一致，耗电明显下降。
     */
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (!running) return;
            long now = SystemClock.uptimeMillis();
            if (lastTickAt == 0L) lastTickAt = now;
            long dt = Math.min(now - lastTickAt, 200L);
            lastTickAt = now;
            if (!globalBusy) {
                activeMs += dt;
                phase = (activeMs % CYCLE_MS) / (float) CYCLE_MS;
                invalidate();
            }
            postDelayed(this, PAINT_GAP_MS);
        }
    };

    public static void setGlobalBusy(boolean v) {
        globalBusy = v;
    }

    public Aurora(Context c) {
        super(c);
        setLayerType(LAYER_TYPE_HARDWARE, null);
    }

    @Override
    protected void onSizeChanged(int nw, int nh, int ow, int oh) {
        w = nw;
        h = nh;
        r0 = nh * 0.75f;
        r1 = nh * 0.62f;
        r2 = nh * 0.55f;
    }

    private void startTicking() {
        if (running) return;
        running = true;
        postDelayed(tick, PAINT_GAP_MS);
    }

    private void stopTicking() {
        running = false;
        removeCallbacks(tick);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        startTicking();
    }

    @Override
    protected void onDetachedFromWindow() {
        stopTicking();
        super.onDetachedFromWindow();
    }

    /** 窗口不可见（退到后台 / 被覆盖）时停掉重绘，回来再续上：相位按时间算，接得上 */
    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        if (visibility == VISIBLE) startTicking();
        else stopTicking();
    }

    private void blob(Canvas cv, float cx, float cy, float radius, int color) {
        paint.setShader(new RadialGradient(cx, cy, radius, color, 0x00FFFFFF, Shader.TileMode.CLAMP));
        cv.drawCircle(cx, cy, radius, paint);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawColor(Ui.C_BG);
        if (w == 0 || h == 0) return;
        double t = phase * Math.PI * 2;

        float x0 = (float) (w * 0.22 + Math.sin(t) * w * 0.16);
        float y0 = (float) (h * 0.16 + Math.cos(t * 1.3) * h * 0.06);
        blob(canvas, x0, y0, r0, Ui.AURORA_A);

        float x1 = (float) (w * 0.86 + Math.cos(t * 0.8) * w * 0.14);
        float y1 = (float) (h * 0.42 + Math.sin(t * 1.1) * h * 0.07);
        blob(canvas, x1, y1, r1, Ui.AURORA_B);

        float x2 = (float) (w * 0.35 + Math.sin(t * 1.6 + 1.2) * w * 0.18);
        float y2 = (float) (h * 0.86 + Math.cos(t * 0.9) * h * 0.05);
        blob(canvas, x2, y2, r2, Ui.AURORA_C);
    }
}
