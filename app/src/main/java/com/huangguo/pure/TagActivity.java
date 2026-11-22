package com.huangguo.pure;

import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

/** 标签剧集页：#都市 这类标签下的全部剧集，无限翻页 */
public class TagActivity extends AppCompatActivity {

    private static final String E_NAME = "name";
    private static final String E_SLUG = "slug";

    public static void open(Context c, String name, String slug) {
        Intent i = new Intent(c, TagActivity.class);
        i.putExtra(E_NAME, name);
        i.putExtra(E_SLUG, slug);
        c.startActivity(i);
    }

    private String name = "";
    private String slug = "";
    private PagedGrid grid;
    private final Handler ui = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        name = orEmpty(getIntent().getStringExtra(E_NAME));
        slug = orEmpty(getIntent().getStringExtra(E_SLUG));

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Ui.C_BG);
        root.setClipChildren(false);
        setContentView(root);
        root.addView(new Aurora(this), new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout page = Ui.col(this);
        root.addView(page, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        buildTop(page, root);

        TextView head = Ui.bold(this, "#" + name, 26f, Ui.C_TEXT);
        head.setPadding(Ui.dp(this, 18), Ui.dp(this, 2), Ui.dp(this, 18), 0);
        page.addView(head);

        TextView note = Ui.text(this, "该标签下按热度排序，滚动自动续页", 11.5f, Ui.C_DIM);
        note.setPadding(Ui.dp(this, 18), Ui.dp(this, 6), Ui.dp(this, 18), Ui.dp(this, 6));
        page.addView(note);

        grid = new PagedGrid(this, 3, v -> DetailActivity.open(this, v.id));
        grid.recycler().setPadding(Ui.dp(this, 9), Ui.dp(this, 4), Ui.dp(this, 9), Ui.dp(this, 20));
        grid.setSource((p, cb) -> Api.listByTag(slug, name, p, cb));
        page.addView(grid, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        if (slug.isEmpty()) {
            grid.setStatus("缺少标签参数");
            return;
        }
        grid.setStatus("加载中…");
        grid.reload();
    }

    private void buildTop(LinearLayout page, View backdrop) {
        LinearLayout bar = Ui.row(this);
        bar.setPadding(Ui.dp(this, 14), Ui.dp(this, 10) + GlassHeader.statusBarHeight(this),
                Ui.dp(this, 14), Ui.dp(this, 6));

        final FrameLayout back = new FrameLayout(this);
        // tint 随主题：深色给暗底，浅色给亮底，别让返回键在浅色下变成灰黑圆
        GlassSurface gs = new GlassSurface(this, 20,
                Ui.darkGlass ? 0x66121A2A : 0x99FFFFFF, 9);
        gs.bind(backdrop);
        back.addView(gs, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        ImageView ic = new ImageView(this);
        ic.setImageResource(R.drawable.ic_back);
        ic.setColorFilter(Ui.C_TEXT);
        back.addView(ic, new FrameLayout.LayoutParams(
                Ui.dp(this, 18), Ui.dp(this, 18), Gravity.CENTER));
        back.setClickable(true);
        back.setOnClickListener(v -> finish());
        bar.addView(back, Ui.lp(Ui.dp(this, 38), Ui.dp(this, 38)));

        TextView tag = Ui.text(this, "标签", 9.5f, Ui.C_AMBER);
        tag.setGravity(Gravity.CENTER);
        tag.setPadding(Ui.dp(this, 8), Ui.dp(this, 3), Ui.dp(this, 8), Ui.dp(this, 3));
        tag.setBackground(Ui.stroke(0x26F5B942, 0x4DF5B942, Ui.dp(this, 8), 1));
        LinearLayout.LayoutParams tp = Ui.lp(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        tp.leftMargin = Ui.dp(this, 12);
        bar.addView(tag, tp);

        page.addView(bar);
    }

    private static String orEmpty(String s) {
        return s == null ? "" : s;
    }

    @Override
    protected void onDestroy() {
        ui.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    /** 跟随系统模式下系统切深浅色：原地重建本页，不回主界面 */
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // 实际明暗与当前调色板不一致就整页重载（手动切主题 / 跟随系统都走这里）；
        // 单纯旋屏时两者相同，什么都不做。播放器不参与，正在播的视频不重建
        if (Theme.isDark(this) != Ui.darkGlass) {
            Ui.retheme(this);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 兼底：配置变化发生在后台时，回到前台再补一次
        if (Ui.darkGlass != Theme.isDark(this)) {
            Ui.retheme(this);
        }
    }
}
