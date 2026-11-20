package com.huangguo.pure;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.view.View;
import android.view.animation.LinearInterpolator;

/** 背景极光：三团缓慢漂移的柔光，给玻璃面板提供背衬层次 */
public class Aurora extends View {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float phase;
    private float r0, r1, r2;
    private int w, h;
    private ValueAnimator animator;
    private long lastPaintAt = 0L;
    /** 全屏 3 团 RadialGradient 每帧重绘 GPU 顶不住：重绘节流到 ~15fps，
     *  26 秒一圈的慢漂在这个帧率下肉眼无差别，GPU 占用降为 1/4 */
    private static final long PAINT_GAP_MS = 66L;

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

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (animator != null) return;
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(26000L);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(a -> {
            long now = android.os.SystemClock.uptimeMillis();
            if (now - lastPaintAt < PAINT_GAP_MS) return;
            lastPaintAt = now;
            phase = (float) a.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    @Override
    protected void onDetachedFromWindow() {
        if (animator != null) {
            animator.cancel();
            animator = null;
        }
        super.onDetachedFromWindow();
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
