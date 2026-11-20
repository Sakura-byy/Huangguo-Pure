package com.huangguo.pure;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

/**
 * Liquid Glass 表面：多层半透明渐变 + 顶部高光 + 内描边 + 可移动镜面高光。
 * 纯 Canvas 绘制，无模糊层、无 RenderEffect，RecyclerView 内可安全复用。
 */
public class GlassDrawable extends Drawable {

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint border = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sheen = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint specular = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF r = new RectF();

    private final float radius;
    private final int tintTop;
    private final int tintMid;
    private final int tintBottom;
    private final int borderColor;
    private float glowX = -1f;
    private Shader fillShader;
    private Shader sheenShader;

    public GlassDrawable(float radius) {
        // 单参数构造随主题：浅色模式下白底太透会导致上面的图标/文字看不清，
        // 因此浅色用「高不透明度白底 + 深墨描边」，深色维持原半透明白玻璃。
        if (Ui.darkGlass) {
            this.tintTop = 0x3AFFFFFF;
            this.tintMid = 0x14FFFFFF;
            this.tintBottom = 0x0DFFFFFF;
            this.borderColor = 0x26FFFFFF;
        } else {
            this.tintTop = 0xEAFFFFFF;
            this.tintMid = 0xDCF2F5FA;
            this.tintBottom = 0xCDE4EAF2;
            this.borderColor = 0x3C1A2332;
        }
        this.radius = radius;
        border.setStyle(Paint.Style.STROKE);
        specular.setStyle(Paint.Style.STROKE);
    }

    public GlassDrawable(float radius, int tintTop, int tintMid, int tintBottom, int borderColor) {
        this.radius = radius;
        this.tintTop = tintTop;
        this.tintMid = tintMid;
        this.tintBottom = tintBottom;
        this.borderColor = borderColor;
        border.setStyle(Paint.Style.STROKE);
        specular.setStyle(Paint.Style.STROKE);
    }

    public void setGlow(float x) {
        glowX = x;
        sheenShader = null;
        invalidateSelf();
    }

    @Override
    protected boolean onLevelChange(int level) {
        return false;
    }

    @Override
    public void onBoundsChange(Rect bounds) {
        fillShader = null;
        sheenShader = null;
    }

    @Override
    public void draw(Canvas canvas) {
        Rect b = getBounds();
        if (b.width() <= 0 || b.height() <= 0) return;
        float inset = 1f;
        r.set(b.left + inset, b.top + inset, b.right - inset, b.bottom - inset);
        path.reset();
        path.addRoundRect(r, radius, radius, Path.Direction.CW);

        if (fillShader == null) {
            fillShader = new LinearGradient(r.left, r.top, r.right, r.bottom,
                    new int[]{tintTop, tintMid, tintBottom},
                    new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP);
        }
        fill.setShader(fillShader);
        canvas.drawPath(path, fill);

        // 顶部高光（模拟玻璃边缘折射）
        specular.setStrokeWidth(Math.max(1f, r.height() * 0.012f));
        specular.setShader(new LinearGradient(r.left, r.top, r.right, r.top + r.height() * 0.55f,
                0x66FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
        canvas.drawPath(path, specular);

        // 镜面流光
        if (glowX >= 0) {
            if (sheenShader == null) {
                sheenShader = new RadialGradient(glowX, r.top + r.height() * 0.15f,
                        Math.max(r.width(), r.height()) * 0.75f,
                        new int[]{0x22FFFFFF, 0x00FFFFFF}, null, Shader.TileMode.CLAMP);
            }
            sheen.setShader(sheenShader);
            canvas.drawPath(path, sheen);
        }

        // 描边
        border.setStrokeWidth(1f);
        border.setColor(borderColor);
        canvas.drawPath(path, border);
    }

    @Override
    public void getOutline(Outline outline) {
        Rect b = getBounds();
        outline.setRoundRect(b.left, b.top, b.right, b.bottom, radius);
    }

    @Override
    public void setAlpha(int alpha) {
        fill.setAlpha(alpha);
        invalidateSelf();
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        fill.setColorFilter(colorFilter);
        invalidateSelf();
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
