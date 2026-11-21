package com.huangguo.pure;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

/** 尺寸 / 颜色 / 控件工厂 */
public final class Ui {

    /** 主题调色板：非 final，切主题时 applyPalette 整体替换 + Activity recreate 重建 UI */
    public static int C_TEXT = 0xFFF2F4F8;
    public static int C_SUB = 0xFF9AA3B2;
    public static int C_DIM = 0xFF6B7484;
    public static int C_AMBER = 0xFFF5B942;
    public static int C_BG = 0xFF05070C;

    /** 当前是否深色：玻璃 tint / rim / 极光配色随它切换 */
    public static boolean darkGlass = true;
    /** 极光三团的颜色（亮暗两套） */
    public static int AURORA_A = 0x552B4A8F;
    public static int AURORA_B = 0x4A7A3C1E;
    public static int AURORA_C = 0x44204E5A;

    /** 玻璃容器 tint：深色玻璃压暗底、浅色玻璃提亮底 */
    public static int glassTint() {
        return darkGlass ? 0x8A0A0E15 : 0xA9FFFFFF;
    }

    /** 小浮钮 tint */
    public static int fabTint() {
        return darkGlass ? 0x4A0A0E15 : 0x8CFFFFFF;
    }

    /** 厚玻璃：高不透明度底 + 明显 rim，压在亮色内容（如琥珀 CTA）旁也清晰可读 */
    public static GlassDrawable thickGlass(Context c, float radius) {
        return darkGlass
                ? new GlassDrawable(radius, 0xF21C2333, 0xEE121826, 0xE60C111C, 0x73FFFFFF)
                : new GlassDrawable(radius, 0xF7FFFFFF, 0xF0F3F6FC, 0xE8E9EDF4, 0x4D1A2332);
    }

    public static void applyPalette(boolean dark) {
        darkGlass = dark;
        if (dark) {
            C_TEXT = 0xFFF2F4F8;
            C_SUB = 0xFF9AA3B2;
            C_DIM = 0xFF6B7484;
            C_AMBER = 0xFFF5B942;
            C_BG = 0xFF05070C;
            AURORA_A = 0x552B4A8F;
            AURORA_B = 0x4A7A3C1E;
            AURORA_C = 0x44204E5A;
        } else {
            // 浅色模式：文字/图标必须足够深，否则在浅色玻璃和浅色壁纸上完全看不清
            C_TEXT = 0xFF0D1520;   // 主文字：近黑，保证最强对比
            C_SUB = 0xFF37455C;    // 次级文字：深灰蓝（原 0xFF54627A 太淡）
            C_DIM = 0xFF65738A;    // 弱化文字：中深灰（原 0xFF8C99AD 太淡）
            C_AMBER = 0xFFD97706;  // 强调色：高饱和琥珀金，浅底上明显又不发脏棕
            C_BG = 0xFFE8EDF5;     // 页面底色略加深，衬托浅色玻璃
            AURORA_A = 0x3D8FB4E8;
            AURORA_B = 0x36F2C27E;
            AURORA_C = 0x307CCBD9;
        }
    }

    private Ui() {
    }

    public static int dp(Context c, float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                c.getResources().getDisplayMetrics()));
    }

    public static TextView text(Context c, String s, float sp, int color) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setIncludeFontPadding(false);
        return t;
    }

    public static TextView bold(Context c, String s, float sp, int color) {
        TextView t = text(c, s, sp, color);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    public static GradientDrawable round(int color, float radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radius);
        return d;
    }

    /** 判断是否为「白色系低透明度」色（浅色模式下会看不清，需换成深墨色） */
    private static boolean isFaintWhite(int c) {
        int a = (c >>> 24) & 0xFF;
        int r = (c >> 16) & 0xFF, g = (c >> 8) & 0xFF, b = c & 0xFF;
        return a > 0 && a <= 0x4D && r >= 0xE0 && g >= 0xE0 && b >= 0xE0;
    }

    /** 浅色模式下把「白色系低透明度」换成深墨色系，保证边框/底色可见 */
    private static int adaptLight(int c) {
        if (darkGlass || !isFaintWhite(c)) return c;
        int a = (c >>> 24) & 0xFF;
        // 白 20% → 墨 22%；白 15% → 墨 16%；保持透明度量级、反相为深色
        int inkA = Math.min(0xFF, Math.round(a * 1.1f));
        return (inkA << 24) | 0x1A2332;
    }

    public static GradientDrawable stroke(int fill, int strokeColor, float radius, int strokeW) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(adaptLight(fill));
        d.setCornerRadius(radius);
        d.setStroke(strokeW, adaptLight(strokeColor));
        return d;
    }

    /** 随主题的淡描边容器：深色用白描边，浅色用墨色描边 */
    public static GradientDrawable subtle(Context c, float radius) {
        return darkGlass
                ? stroke(0x14FFFFFF, 0x26FFFFFF, radius, 1)
                : stroke(0x140A0E15, 0x26304468, radius, 1);
    }

    public static View spacer(Context c, int w, int h) {
        View v = new View(c);
        v.setLayoutParams(new LinearLayout.LayoutParams(w, h));
        return v;
    }

    public static LinearLayout row(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    public static LinearLayout col(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    public static LinearLayout.LayoutParams lp(int w, int h) {
        return new LinearLayout.LayoutParams(w, h);
    }

    /** 字节数格式化：B / KB / MB / GB */
    public static String fmtSize(long bytes) {
        if (bytes <= 0) return "0 B";
        float v = bytes;
        if (v < 1024f) return Math.round(v) + " B";
        v /= 1024f;
        if (v < 1024f) return String.format("%.1f KB", v);
        v /= 1024f;
        if (v < 1024f) return String.format("%.1f MB", v);
        v /= 1024f;
        return String.format("%.2f GB", v);
    }

    public static String shortNum(long n) {
        if (n >= 100000000L) return String.format("%.1f亿", n / 100000000.0);
        if (n >= 10000L) return String.format("%.1fw", n / 10000.0);
        return String.valueOf(n);
    }

    public static String mmss(int sec) {
        if (sec < 0) sec = 0;
        int m = sec / 60, s = sec % 60;
        return String.format("%02d:%02d", m, s);
    }

    public static String hhmmss(long ms) {
        long total = ms / 1000;
        long h = total / 3600, m = (total % 3600) / 60, s = total % 60;
        if (h > 0) return String.format("%d:%02d:%02d", h, m, s);
        return String.format("%02d:%02d", m, s);
    }

    /** 当前主题的强调色（浅色暗金 / 深色亮琥珀）——Dock 选中图标、chip、CTA 统一用它 */
    public static int accent() {
        return C_AMBER;
    }

    /** 主题感知的「压在琥珀/金色 CTA 上的文字」：深色用近黑，浅色用近白 */
    public static int onAmber() {
        return darkGlass ? 0xFF241A06 : 0xFFFFFFFF;
    }

    /** 主题感知的次要正文（原写死 0xFFC9D1DE / 0xFFB9C2D0 这类浅灰） */
    public static int subText() {
        return darkGlass ? 0xFFC9D1DE : 0xFF37455C;
    }

    /** 主题感知的更弱说明文字（原写死 0xFFAFB8C6 / 0xFFB9C2D0） */
    public static int weakText() {
        return darkGlass ? 0xFFAFB8C6 : 0xFF5A687C;
    }

    /** 主题感知的正文（原写死 0xFFDCE3EE / 0xFFE3E8F0） */
    public static int bodyText() {
        return darkGlass ? 0xFFDCE3EE : 0xFF18202E;
    }

    /** 主题感知的纯标题（原写死 Color.WHITE 的文字） */
    public static int titleText() {
        return darkGlass ? 0xFFFFFFFF : 0xFF0D1520;
    }

    /** 主题感知的 chip/描边底：深色半透明白、浅色半透明墨 */
    public static int chipFill() {
        return darkGlass ? 0x14FFFFFF : 0x120A0E15;
    }

    public static int chipStroke() {
        return darkGlass ? 0x26FFFFFF : 0x33304468;
    }

    /** 主题强调色的半透明描边（chip / 标签） */
    public static int accentFill() {
        return darkGlass ? 0x26F5B942 : 0x2EC28700;
    }

    public static int accentStroke() {
        return darkGlass ? 0x4DF5B942 : 0x66C28700;
    }

    /** 主题感知 thickGlass 用的四色玻璃（播放器面板 / 弹窗菜单） */
    public static GlassDrawable panelGlass(Context c, float radius) {
        return darkGlass
                ? new GlassDrawable(radius, 0xEB1C2333, 0xE00D121B, 0xEB0A0E15, 0x3DFFFFFF)
                : new GlassDrawable(radius, 0xF5FFFFFF, 0xEFF3F8FD, 0xE8EDF4, 0x4D1A2332);
    }

    /** 与 Dock 同款的液态玻璃面板：磨砂底 + 白色液态高光边（无深色硬描边） */
    public static android.graphics.drawable.Drawable liquidPanel(Context c, float radius) {
        GlassDrawable base = darkGlass
                ? new GlassDrawable(radius, 0xE61A212E, 0xDC0D121B, 0xE60A0E15, 0x00FFFFFF)
                : new GlassDrawable(radius, 0xF7FFFFFF, 0xF0F3F8FD, 0xE9EDF4, 0x00FFFFFF);
        GradientDrawable edge = new GradientDrawable();
        edge.setCornerRadius(radius);
        edge.setColor(0x00000000);
        edge.setStroke(Math.round(dp(c, 2.4f)), darkGlass ? 0x66FFFFFF : 0x8CFFFFFF);
        return new android.graphics.drawable.LayerDrawable(
                new android.graphics.drawable.Drawable[]{base, edge});
    }

    /** 顶栏实色底：浅色近白、深色近墨 */
    public static int headerSolid() {
        return darkGlass ? 0xFF0B1018 : 0xFFF7F8FC;
    }

    /** 顶栏底部 1px 分隔线 */
    public static int headerLine() {
        return darkGlass ? 0x1FFFFFFF : 0x141A2332;
    }

    /**
     * 顶栏渐变玻璃膜：顶部实、向下渐隐到全透明，压住滚动内容但不像实心条。
     * 用多段渐变而不是两段，肉眼才不会在过渡处看到一条「截断线」。
     */
    public static android.graphics.drawable.Drawable headerVeil(Context c) {
        android.graphics.drawable.GradientDrawable g =
                new android.graphics.drawable.GradientDrawable(
                        android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
                        darkGlass
                                ? new int[]{0xA60B1018, 0x9A0B1018, 0x7A0B1018, 0x400B1018, 0x000B1018}
                                : new int[]{0xA6FFFFFF, 0x9AFFFFFF, 0x7AFFFFFF, 0x40FFFFFF, 0x00FFFFFF});
        g.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
        return g;
    }

    /** 玩家 seek 进度色 / 按钮高亮 */
    public static int accentBright() {
        return darkGlass ? 0xFFF5B942 : 0xFFB8860B;
    }

    public static int alpha(int color, float a) {
        return Color.argb((int) (a * 255), Color.red(color), Color.green(color), Color.blue(color));
    }
}
