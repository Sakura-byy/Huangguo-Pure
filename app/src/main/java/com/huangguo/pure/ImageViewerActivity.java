package com.huangguo.pure;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

/**
 * 图片浏览器：全屏黑底 + 双指捏合缩放 + 双击切换 + 单击退出。
 * 长按保存到相册 Pictures/Huangguo/；底部也有显式的「保存图片」胶囊。
 */
public class ImageViewerActivity extends AppCompatActivity {

    private static final String E_URL = "url";
    private static final String E_TITLE = "title";

    public static void open(Context c, String url, String title) {
        if (url == null || url.isEmpty()) {
            Toast.makeText(c, "图片地址为空", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent i = new Intent(c, ImageViewerActivity.class);
        i.putExtra(E_URL, url);
        i.putExtra(E_TITLE, title == null ? "" : title);
        c.startActivity(i);
    }

    private String url = "";
    private String title = "";
    private ZoomView zoom;
    private TextView tip;
    private TextView saveBtn;
    private boolean saved = false;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        url = getIntent().getStringExtra(E_URL);
        title = getIntent().getStringExtra(E_TITLE);
        if (title == null) title = "";

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xF205070C);
        setContentView(root);

        zoom = new ZoomView(this);
        root.addView(zoom, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        tip = Ui.text(this, "加载中…", 12.5f, Ui.C_SUB);
        tip.setGravity(Gravity.CENTER);
        root.addView(tip, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 90), Gravity.CENTER));

        // 顶部标题玻璃条
        TextView head = Ui.text(this, title, 12f, 0xFFC9D1DE);
        head.setSingleLine(true);
        head.setGravity(Gravity.CENTER);
        head.setPadding(Ui.dp(this, 16), Ui.dp(this, 8), Ui.dp(this, 16), Ui.dp(this, 8));
        // 查看器背景恒为深色 → 标题胶囊固定深玻璃 + 浅灰字，不随 App 主题反转
        head.setBackground(new GlassDrawable(Ui.dp(this, 16), 0xF01C2333, 0xE60D121B,
                0xF00A0E15, 0x66FFFFFF));
        FrameLayout.LayoutParams hp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        // 标题原来只离顶 14dp，正好压在前摄挖孔上；让出状态栏高度再留 8dp
        hp.topMargin = Ui.dp(this, 8) + statusBarHeight();
        root.addView(head, hp);

        // 底部保存按钮
        saveBtn = Ui.bold(this, "保存图片", 12.5f, Ui.C_AMBER);
        saveBtn.setGravity(Gravity.CENTER);
        saveBtn.setPadding(Ui.dp(this, 22), Ui.dp(this, 10), Ui.dp(this, 22), Ui.dp(this, 10));
        saveBtn.setBackground(Ui.stroke(0x26F5B942, 0x4DF5B942, Ui.dp(this, 20), 1));
        saveBtn.setClickable(true);
        saveBtn.setOnClickListener(v -> save());
        FrameLayout.LayoutParams sp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        sp.bottomMargin = Ui.dp(this, 26);
        root.addView(saveBtn, sp);

        load();
    }

    /** 状态栏高度：优先取系统资源，取不到用 24dp 兜底 */
    private int statusBarHeight() {
        int id = getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (id > 0) {
            int h = getResources().getDimensionPixelSize(id);
            if (h > 0) return h;
        }
        return Ui.dp(this, 24);
    }

    private void load() {
        new Thread(() -> {
            try {
                final Bitmap bmp = Api.fetchBitmap(url);
                runOnUiThread(() -> {
                    if (bmp == null) {
                        tip.setText("图片加载失败");
                        return;
                    }
                    tip.setVisibility(View.GONE);
                    zoom.setBitmap(bmp);
                });
            } catch (Exception e) {
                runOnUiThread(() -> tip.setText("图片加载失败：" + e.getMessage()));
            }
        }).start();
    }

    private void save() {
        if (saved) return;
        Bitmap bmp = zoom.bitmap();
        if (bmp == null) {
            Toast.makeText(this, "图片还没加载完", Toast.LENGTH_SHORT).show();
            return;
        }
        saved = true;
        saveBtn.setText("正在保存…");
        String name = "hg_" + System.currentTimeMillis() + ".jpg";
        try {
            final String path = MediaSave.saveImage(this, bmp, name);
            runOnUiThread(() -> {
                Toast.makeText(this, "已保存：" + path, Toast.LENGTH_LONG).show();
                saveBtn.setText("已保存 ✓");
            });
        } catch (Exception e) {
            saved = false;
            runOnUiThread(() -> {
                Toast.makeText(this, "保存失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
                saveBtn.setText("保存图片");
            });
        }
    }

    /** 矩阵缩放视图：捏合 + 双击 + 单击退出 + 长按保存 */
    private class ZoomView extends View {

        private final Matrix matrix = new Matrix();
        private final Matrix savedMatrix = new Matrix();
        private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
        private ScaleGestureDetector sgd;
        private Bitmap bmp;

        private float startDist = 0f;
        private float midX, midY;
        private long downAt = 0L;
        private boolean moved = false;
        private boolean multi = false;
        private float lastX, lastY;
        private float minScale = 1f, maxScale = 6f;

        ZoomView(Context c) {
            super(c);
            sgd = new ScaleGestureDetector(c, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                @Override
                public boolean onScale(ScaleGestureDetector d) {
                    float f = d.getScaleFactor();
                    float[] v = new float[9];
                    matrix.getValues(v);
                    float ns = Math.max(minScale, Math.min(maxScale, v[Matrix.MSCALE_X] * f));
                    f = ns / v[Matrix.MSCALE_X];
                    matrix.postScale(f, f, d.getFocusX(), d.getFocusY());
                    clamp();
                    invalidate();
                    return true;
                }
            });
            setClickable(true);
        }

        void setBitmap(Bitmap b) {
            bmp = b;
            reset();
            invalidate();
        }

        Bitmap bitmap() {
            return bmp;
        }

        private void reset() {
            if (bmp == null || getWidth() == 0) return;
            float sw = getWidth() / (float) bmp.getWidth();
            float sh = getHeight() / (float) bmp.getHeight();
            float s = Math.min(sw, sh);
            minScale = s;
            maxScale = s * 8f;
            matrix.reset();
            matrix.postTranslate((getWidth() - bmp.getWidth()) / 2f,
                    (getHeight() - bmp.getHeight()) / 2f);
            matrix.postScale(s, s, getWidth() / 2f, getHeight() / 2f);
            invalidate();
        }

        private void clamp() {
            if (bmp == null) return;
            float[] v = new float[9];
            matrix.getValues(v);
            float s = v[Matrix.MSCALE_X];
            s = Math.max(minScale * 0.5f, Math.min(maxScale, s));
            float[] vx = {0, 0, bmp.getWidth(), bmp.getHeight()};
            // 防止把图甩出屏幕：若中心跑太远则拉回
            float cx = bmp.getWidth() * s / 2f + v[Matrix.MTRANS_X];
            float cy = bmp.getHeight() * s / 2f + v[Matrix.MTRANS_Y];
            float dx = getWidth() / 2f - cx, dy = getHeight() / 2f - cy;
            float m = s * 1.2f;
            if (Math.abs(dx) > bmp.getWidth() * m) dx = Math.signum(dx) * bmp.getWidth() * m;
            if (Math.abs(dy) > bmp.getHeight() * m) dy = Math.signum(dy) * bmp.getHeight() * m;
            matrix.postTranslate(dx * 0.5f, dy * 0.5f);
        }

        @Override
        protected void onSizeChanged(int w, int h, int ow, int oh) {
            super.onSizeChanged(w, h, ow, oh);
            reset();
        }

        @SuppressLint("ClickableViewAccessibility")
        @Override
        public boolean onTouchEvent(MotionEvent e) {
            sgd.onTouchEvent(e);
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downAt = System.currentTimeMillis();
                    moved = false;
                    multi = false;
                    lastX = e.getX();
                    lastY = e.getY();
                    savedMatrix.set(matrix);
                    break;

                case MotionEvent.ACTION_POINTER_DOWN:
                    multi = true;
                    savedMatrix.set(matrix);
                    startDist = dist(e);
                    if (startDist > 0) {
                        midX = (e.getX(0) + e.getX(1)) / 2f;
                        midY = (e.getY(0) + e.getY(1)) / 2f;
                    }
                    break;

                case MotionEvent.ACTION_MOVE: {
                    if (e.getPointerCount() >= 2 && startDist > 0) {
                        float d = dist(e);
                        if (d > 0) {
                            float[] v = new float[9];
                            savedMatrix.getValues(v);
                            float factor = d / startDist;
                            float ns = Math.max(minScale, Math.min(maxScale,
                                    v[Matrix.MSCALE_X] * factor));
                            matrix.set(savedMatrix);
                            float f = ns / v[Matrix.MSCALE_X];
                            matrix.postScale(f, f, midX, midY);
                            clamp();
                            moved = true;
                            invalidate();
                        }
                    } else if (e.getPointerCount() == 1 && !multi) {
                        float dx = e.getX() - lastX;
                        float dy = e.getY() - lastY;
                        lastX = e.getX();
                        lastY = e.getY();
                        if (Math.abs(dx) + Math.abs(dy) > 1.5f) {
                            matrix.postTranslate(dx, dy);
                            clamp();
                            moved = true;
                            invalidate();
                        }
                    }
                    break;
                }

                case MotionEvent.ACTION_POINTER_UP:
                    savedMatrix.set(matrix);
                    break;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL: {
                    long held = System.currentTimeMillis() - downAt;
                    if (!moved && !multi) {
                        if (held >= 550L) {
                            performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
                            save();
                        } else if (held < 350L) {
                            finish();
                        }
                    }
                    break;
                }
            }
            return true;
        }

        private float dist(MotionEvent e) {
            float dx = e.getX(0) - e.getX(1);
            float dy = e.getY(0) - e.getY(1);
            return (float) Math.sqrt(dx * dx + dy * dy);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            if (bmp == null) return;
            canvas.drawBitmap(bmp, matrix, paint);
        }
    }

    /** 跟随系统模式下系统切深浅色：原地重建本页，不回主界面 */
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        boolean dark = Theme.isDark(this);
        if (Theme.mode() == Theme.AUTO && dark != Ui.darkGlass) {
            Ui.retheme(this);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 兜底：配置变化发生在后台时，回到前台再补一次
        if (Ui.darkGlass != Theme.isDark(this)) {
            Ui.retheme(this);
        }
    }
}
