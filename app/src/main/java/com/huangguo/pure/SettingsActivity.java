package com.huangguo.pure;

import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

/**
 * 设置页：外观 / 软件清理 / 检测更新 / 自动检测更新 / 版本。
 *
 * 外观与软件清理从「我的」迁到这儿，我的页只留一个入口。
 * 外观改动只写 Theme 偏好，回主界面时由 MainActivity.onResume 检测到配色不一致再 recreate。
 */
public class SettingsActivity extends AppCompatActivity {

    public static void open(Context c) {
        c.startActivity(new Intent(c, SettingsActivity.class));
    }

    private TextView themeValue;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Ui.applyPalette(Theme.isDark(this));

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Ui.C_BG);
        setContentView(root);
        root.addView(new Aurora(this), new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        int barH = GlassHeader.statusBarHeight(this) + Ui.dp(this, 54);

        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        root.addView(sv, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout content = Ui.col(this);
        content.setPadding(0, barH, 0, Ui.dp(this, 30));
        sv.addView(content);

        buildTopBar(root, barH);

        LinearLayout card = Ui.col(this);
        card.setPadding(Ui.dp(this, 4), Ui.dp(this, 4), Ui.dp(this, 4), Ui.dp(this, 4));
        card.setBackground(new GlassDrawable(Ui.dp(this, 20)));
        LinearLayout.LayoutParams cp = Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        cp.leftMargin = Ui.dp(this, 16);
        cp.rightMargin = Ui.dp(this, 16);
        cp.topMargin = Ui.dp(this, 12);
        content.addView(card, cp);

        card.addView(themeRow());
        card.addView(divider());
        card.addView(cleanRow());
        card.addView(divider());
        card.addView(lineRow());
        card.addView(divider());
        card.addView(updateRow());
        card.addView(divider());
        card.addView(autoRow());

        LinearLayout card2 = Ui.col(this);
        card2.setPadding(Ui.dp(this, 4), Ui.dp(this, 4), Ui.dp(this, 4), Ui.dp(this, 4));
        card2.setBackground(new GlassDrawable(Ui.dp(this, 20)));
        LinearLayout.LayoutParams c2 = Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        c2.leftMargin = Ui.dp(this, 16);
        c2.rightMargin = Ui.dp(this, 16);
        c2.topMargin = Ui.dp(this, 12);
        content.addView(card2, c2);
        card2.addView(versionRow());

        TextView foot = Ui.text(this,
                "Huangguo Pure · 每日自动检测可在上方关闭", 10.5f, Ui.C_DIM);
        foot.setGravity(Gravity.CENTER);
        foot.setPadding(0, Ui.dp(this, 24), 0, Ui.dp(this, 24));
        content.addView(foot);
    }

    // ------------------------------------------------------------------ 顶栏

    private void buildTopBar(FrameLayout root, int barH) {
        LinearLayout bar = Ui.row(this);
        // 透明顶栏：与页面背景同色、无边框；状态栏高度由 wrap 的高度承担，
        // bar 只负责 54dp 的标题行，再 padding 一层状态栏高度就会错位下坠
        bar.setPadding(Ui.dp(this, 14), 0, Ui.dp(this, 14), 0);

        FrameLayout back = new FrameLayout(this);
        back.setBackground(new GlassDrawable(Ui.dp(this, 20)));
        back.setClickable(true);
        ImageView ic = new ImageView(this);
        ic.setImageResource(R.drawable.ic_back);
        ic.setColorFilter(Ui.C_TEXT);
        back.addView(ic, new FrameLayout.LayoutParams(
                Ui.dp(this, 18), Ui.dp(this, 18), Gravity.CENTER));
        back.setOnClickListener(v -> finish());
        bar.addView(back, Ui.lp(Ui.dp(this, 38), Ui.dp(this, 38)));

        TextView title = Ui.bold(this, "设置", 17f, Ui.C_TEXT);
        LinearLayout.LayoutParams tp = Ui.lp(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        tp.leftMargin = Ui.dp(this, 12);
        bar.addView(title, tp);

        // wrap 高 = 状态栏 + 54dp，bar 用 Gravity.BOTTOM 落在底部：
        // 状态栏区域留白但属于背景的一部分，标题行在状态栏正下方居中
        FrameLayout wrap = new FrameLayout(this);
        wrap.addView(bar, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 54), Gravity.BOTTOM));
        root.addView(wrap, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, barH, Gravity.TOP));
    }

    // ------------------------------------------------------------------ 行

    private View divider() {
        View v = new View(this);
        v.setBackgroundColor(Ui.darkGlass ? 0x14FFFFFF : 0x0F1A2332);
        v.setLayoutParams(Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT, 1));
        return v;
    }

    private LinearLayout baseRow() {
        LinearLayout wrap = Ui.row(this);
        wrap.setMinimumHeight(Ui.dp(this, 62));
        wrap.setPadding(Ui.dp(this, 14), Ui.dp(this, 12), Ui.dp(this, 14), Ui.dp(this, 12));
        wrap.setGravity(Gravity.CENTER_VERTICAL);
        return wrap;
    }

    private void addGlyph(LinearLayout wrap, String glyph) {
        TextView g = Ui.bold(this, glyph, 13f, Ui.C_AMBER);
        g.setGravity(Gravity.CENTER);
        g.setBackground(Ui.stroke(0x26F5B942, 0x59F5B942, Ui.dp(this, 20), 1));
        wrap.addView(g, Ui.lp(Ui.dp(this, 38), Ui.dp(this, 38)));
    }

    private LinearLayout texts(LinearLayout wrap, String title, String sub) {
        LinearLayout col = Ui.col(this);
        LinearLayout.LayoutParams tp = Ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT);
        tp.weight = 1f;
        tp.leftMargin = Ui.dp(this, 12);
        wrap.addView(col, tp);
        col.addView(Ui.bold(this, title, 13.5f, Ui.C_TEXT));
        if (sub != null) {
            TextView s = Ui.text(this, sub, 10.5f, Ui.C_DIM);
            LinearLayout.LayoutParams sp = Ui.lp(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            sp.topMargin = Ui.dp(this, 3);
            col.addView(s, sp);
        }
        return col;
    }

    private TextView pill(String text) {
        TextView v = Ui.bold(this, text, 11.5f, Ui.C_AMBER);
        v.setGravity(Gravity.CENTER);
        v.setPadding(Ui.dp(this, 12), Ui.dp(this, 6), Ui.dp(this, 12), Ui.dp(this, 6));
        v.setBackground(Ui.stroke(0x26F5B942, 0x4DF5B942, Ui.dp(this, 14), 1));
        return v;
    }

    private LinearLayout themeRow() {
        final LinearLayout wrap = baseRow();
        addGlyph(wrap, "◐");
        texts(wrap, "外观", "深浅色 · 液态玻璃观感");
        themeValue = pill(Theme.label(Theme.mode()));
        wrap.addView(themeValue);
        TextView arrow = Ui.text(this, "›", 15f, Ui.C_DIM);
        LinearLayout.LayoutParams ap = Ui.lp(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        ap.leftMargin = Ui.dp(this, 8);
        wrap.addView(arrow, ap);
        wrap.setClickable(true);
        wrap.setOnClickListener(v -> GlassMenu.show(wrap, GlassMenu.items(
                new GlassMenu.Item("◐", "跟随系统", () -> applyTheme(Theme.AUTO)),
                new GlassMenu.Item("☀", "浅色", () -> applyTheme(Theme.LIGHT)),
                new GlassMenu.Item("☾", "深色", () -> applyTheme(Theme.DARK)))));
        return wrap;
    }

    private void applyTheme(int mode) {
        if (mode == Theme.mode()) return;
        Theme.setMode(this, mode);
        // 只写偏好；回主界面时 MainActivity 发现配色不一致会自己 recreate
        finish();
    }

    private LinearLayout cleanRow() {
        final LinearLayout wrap = baseRow();
        addGlyph(wrap, "⌫");
        // 副标题直接给出缓存视频落盘位置，找文件不用猜
        texts(wrap, "软件清理", "缓存视频：" + MediaSave.downloadRootLabel());
        TextView arrow = Ui.text(this, "›", 15f, Ui.C_DIM);
        wrap.addView(arrow);
        wrap.setClickable(true);
        wrap.setOnClickListener(v -> GlassMenu.show(wrap, GlassMenu.items(
                new GlassMenu.Item("↺", "清理缓存", () -> CleanHelper.showClearCache(this)),
                new GlassMenu.Item("⤓", "清理下载文件", () -> CleanHelper.showDownloads(this)))));
        return wrap;
    }

    private LinearLayout lineRow() {
        final LinearLayout wrap = baseRow();
        addGlyph(wrap, "⇄");
        texts(wrap, "线路切换", "多线路测速 · 自动挑最快");
        TextView arrow = Ui.text(this, "›", 15f, Ui.C_DIM);
        wrap.addView(arrow);
        wrap.setClickable(true);
        wrap.setOnClickListener(v -> showLines());
        return wrap;
    }

    /** 线路面板：列出发布页给出的所有线路与延迟，点一条立即切换 */
    private void showLines() {
        LinearLayout col = Ui.col(this);
        col.addView(Ui.bold(this, "线路切换", 16f, Ui.C_TEXT));

        TextView sub = Ui.text(this, "从发布页实时测速，数字越小越快", 11f, Ui.C_SUB);
        LinearLayout.LayoutParams sp = Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        sp.topMargin = Ui.dp(this, 6);
        col.addView(sub, sp);

        final LinearLayout list = Ui.col(this);
        ScrollView sc = new ScrollView(this);
        sc.addView(list);
        LinearLayout.LayoutParams slp = Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT,
                Ui.dp(this, 250));
        slp.topMargin = Ui.dp(this, 12);
        col.addView(sc, slp);

        final FrameLayout[] ref = new FrameLayout[1];

        LinearLayout btns = Ui.row(this);
        LinearLayout.LayoutParams bp = Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        bp.topMargin = Ui.dp(this, 16);
        btns.setLayoutParams(bp);

        View spacer = new View(this);
        LinearLayout.LayoutParams vp = Ui.lp(0, 1);
        vp.weight = 1f;
        btns.addView(spacer, vp);

        final TextView rescan = Ui.bold(this, "重新测速", 12.5f, Ui.C_SUB);
        rescan.setGravity(Gravity.CENTER);
        rescan.setPadding(Ui.dp(this, 16), Ui.dp(this, 8), Ui.dp(this, 16), Ui.dp(this, 8));
        rescan.setBackground(Ui.stroke(Ui.chipFill(), Ui.chipStroke(), Ui.dp(this, 16), 1));
        rescan.setClickable(true);
        btns.addView(rescan, Ui.lp(ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dp(this, 34)));

        TextView close = Ui.bold(this, "关闭", 12.5f, Ui.accentBright());
        close.setGravity(Gravity.CENTER);
        close.setPadding(Ui.dp(this, 16), Ui.dp(this, 8), Ui.dp(this, 16), Ui.dp(this, 8));
        close.setBackground(Ui.stroke(Ui.chipFill(), Ui.chipStroke(), Ui.dp(this, 16), 1));
        close.setClickable(true);
        close.setOnClickListener(v -> GlassPanel.close(ref[0]));
        LinearLayout.LayoutParams clp = Ui.lp(ViewGroup.LayoutParams.WRAP_CONTENT,
                Ui.dp(this, 34));
        clp.leftMargin = Ui.dp(this, 10);
        btns.addView(close, clp);

        col.addView(btns);
        ref[0] = GlassPanel.show(this, col, true);

        final Runnable[] scan = new Runnable[1];
        scan[0] = () -> {
            rescan.setEnabled(false);
            rescan.setText("测速中…");
            fillLines(list, ref);
            new Thread(() -> {
                java.util.List<Mirror.Line> got = Mirror.fetchRanked();
                runOnUiThread(() -> {
                    if (got.isEmpty()) {
                        rescan.setText("重新测速");
                        rescan.setEnabled(true);
                        fillLines(list, ref);
                        return;
                    }
                    Api.saveLines(got);
                    rescan.setText("重新测速");
                    rescan.setEnabled(true);
                    fillLines(list, ref);
                });
            }).start();
        };
        rescan.setOnClickListener(v -> scan[0].run());
        fillLines(list, ref);

        // 一行数据都没有就自动测一次
        if (Api.lines().isEmpty()) scan[0].run();
    }

    /** 线路列表：主机名 + 延迟徽标，点一条切过去 */
    private void fillLines(LinearLayout list, FrameLayout[] ref) {
        list.removeAllViews();
        java.util.List<Mirror.Line> ls = Api.lines();
        if (ls.isEmpty()) {
            TextView t = Ui.text(this, "正在从发布页获取线路…", 11.5f, Ui.C_DIM);
            t.setPadding(0, Ui.dp(this, 16), 0, Ui.dp(this, 16));
            list.addView(t);
            return;
        }
        String cur = Api.baseUrl();
        for (final Mirror.Line l : ls) {
            LinearLayout row = Ui.row(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(Ui.dp(this, 10), Ui.dp(this, 10),
                    Ui.dp(this, 10), Ui.dp(this, 10));
            row.setBackground(Ui.stroke(
                    l.url.equals(cur) ? 0x26F5B942 : Ui.chipFill(),
                    l.url.equals(cur) ? 0x4DF5B942 : Ui.chipStroke(),
                    Ui.dp(this, 12), 1));
            LinearLayout.LayoutParams rp = Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            rp.bottomMargin = Ui.dp(this, 8);

            TextView host = Ui.text(this, l.host(), 11.5f,
                    l.url.equals(cur) ? Ui.C_TEXT : Ui.C_SUB);
            LinearLayout.LayoutParams hp = Ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT);
            hp.weight = 1f;
            row.addView(host, hp);

            boolean fast = l.ms >= 0 && l.ms < 300;
            boolean mid = l.ms >= 300 && l.ms < 900;
            int color = l.ms < 0 ? Ui.C_DIM : (fast ? Ui.C_AMBER : (mid ? Ui.C_SUB : Ui.C_DIM));
            TextView ms = Ui.bold(this, l.ms < 0 ? "超时" : (l.ms + " ms"), 11f, color);
            ms.setGravity(Gravity.CENTER);
            ms.setPadding(Ui.dp(this, 10), Ui.dp(this, 4), Ui.dp(this, 10), Ui.dp(this, 4));
            row.addView(ms, Ui.lp(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));

            if (l.url.equals(cur)) {
                TextView now = Ui.bold(this, "当前", 10f, Ui.C_AMBER);
                LinearLayout.LayoutParams np = Ui.lp(ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
                np.leftMargin = Ui.dp(this, 8);
                row.addView(now, np);
            }

            row.setClickable(true);
            row.setOnClickListener(v -> {
                Api.useHost(l.url);
                android.widget.Toast.makeText(this, "已切换到 " + l.host(),
                        android.widget.Toast.LENGTH_SHORT).show();
                fillLines(list, ref);
            });
            list.addView(row, rp);
        }
    }

    private LinearLayout updateRow() {
        final LinearLayout wrap = baseRow();
        addGlyph(wrap, "↑");
        texts(wrap, "检测更新", "从 GitHub 发布页检查新版本");
        TextView arrow = Ui.text(this, "›", 15f, Ui.C_DIM);
        wrap.addView(arrow);
        wrap.setClickable(true);
        wrap.setOnClickListener(v -> Update.checkManual(this));
        return wrap;
    }

    private LinearLayout autoRow() {
        LinearLayout wrap = baseRow();
        addGlyph(wrap, "⏱");
        texts(wrap, "自动更新", "每天首次启动时检查，发现新版弹窗直接下载安装");
        final Switch sw = new Switch(this);
        sw.setChecked(Update.autoEnabled(this));
        sw.setButtonTintList(null);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            sw.setThumbTintList(android.content.res.ColorStateList.valueOf(Ui.C_AMBER));
        }
        sw.setOnCheckedChangeListener((btn, checked) -> Update.setAuto(this, checked));
        wrap.addView(sw, Ui.lp(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        return wrap;
    }

    private LinearLayout versionRow() {
        LinearLayout wrap = baseRow();
        addGlyph(wrap, "i");
        texts(wrap, "版本", "Huangguo Pure 纯净客户端");
        wrap.addView(pill("v" + Update.versionName(this)));
        return wrap;
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
