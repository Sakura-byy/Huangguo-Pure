package com.huangguo.pure;

import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

/** 专题详情：头图 + 简介 + 该专题下的剧集网格 */
public class TopicActivity extends AppCompatActivity {

    private static final String E_SLUG = "slug";
    private static final String E_TITLE = "title";

    public static void open(Context c, String slug, String title) {
        Intent i = new Intent(c, TopicActivity.class);
        i.putExtra(E_SLUG, slug);
        i.putExtra(E_TITLE, title);
        c.startActivity(i);
    }

    private String slug = "";
    private String title = "";
    private PagedGrid grid;
    private TextView countView;
    private TextView introView;
    private boolean expanded = false;
    private Models.Topic topic;
    private FrameLayout favBtn;
    private boolean fav = false;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        slug = getIntent().getStringExtra(E_SLUG);
        title = getIntent().getStringExtra(E_TITLE);
        if (slug == null) slug = "";
        if (title == null) title = "";

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Ui.C_BG);
        setContentView(root);
        root.addView(new Aurora(this), new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout content = Ui.col(this);
        root.addView(content, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        buildTopBar(content);
        buildHeader(content);

        grid = new PagedGrid(this, 3, v -> DetailActivity.open(this, v.id));
        grid.recycler().setPadding(Ui.dp(this, 9), Ui.dp(this, 4), Ui.dp(this, 9), Ui.dp(this, 24));
        grid.setSource((page, cb) -> Api.topicDetail(slug, page, new Api.Cb<Models.TopicDetail>() {
            @Override
            public void ok(Models.TopicDetail d) {
                Models.Page p = new Models.Page();
                p.items.addAll(d.videos);
                p.page = page;
                p.pages = d.pages;
                cb.ok(p);
            }

            @Override
            public void err(String msg) {
                cb.err(msg);
            }
        }));
        content.addView(grid, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        if (slug.isEmpty()) {
            grid.setStatus("缺少专题参数");
            return;
        }
        grid.reload();
        loadHeader();
    }

    private void buildTopBar(LinearLayout content) {
        LinearLayout bar = Ui.row(this);
        // 返回键/标题要落在状态栏下方，否则沉浸式直接压住状态栏时钟
        bar.setPadding(Ui.dp(this, 14), Ui.dp(this, 10) + GlassHeader.statusBarHeight(this),
                Ui.dp(this, 14), Ui.dp(this, 6));

        FrameLayout backWrap = new FrameLayout(this);
        backWrap.setBackground(new GlassDrawable(Ui.dp(this, 20)));
        backWrap.setClickable(true);
        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.ic_back);
        icon.setColorFilter(Ui.C_TEXT);
        backWrap.addView(icon, new FrameLayout.LayoutParams(
                Ui.dp(this, 18), Ui.dp(this, 18), Gravity.CENTER));
        backWrap.setOnClickListener(v -> finish());
        bar.addView(backWrap, Ui.lp(Ui.dp(this, 38), Ui.dp(this, 38)));

        TextView t = Ui.bold(this, title, 16f, Ui.C_TEXT);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams lp = Ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.weight = 1f;
        lp.leftMargin = Ui.dp(this, 12);
        bar.addView(t, lp);

        TextView tag = Ui.text(this, "专题", 9.5f, Ui.C_AMBER);
        tag.setPadding(Ui.dp(this, 8), Ui.dp(this, 3), Ui.dp(this, 8), Ui.dp(this, 3));
        tag.setBackground(Ui.stroke(Ui.accentFill(), Ui.accentStroke(), Ui.dp(this, 8), 1));
        bar.addView(tag);

        // 更多按钮：下拉玻璃菜单（收藏 / 分享），专题详情加载后按真实 id 判断状态
        favBtn = new FrameLayout(this);
        favBtn.setBackground(new GlassDrawable(Ui.dp(this, 20)));
        favBtn.setClickable(true);
        ImageView more = new ImageView(this);
        more.setImageResource(R.drawable.ic_more);
        more.setColorFilter(Ui.C_TEXT);
        favBtn.addView(more, new FrameLayout.LayoutParams(
                Ui.dp(this, 18), Ui.dp(this, 18), Gravity.CENTER));
        favBtn.setOnClickListener(v -> showMoreMenu());
        LinearLayout.LayoutParams fp = Ui.lp(Ui.dp(this, 38), Ui.dp(this, 38));
        fp.leftMargin = Ui.dp(this, 10);
        bar.addView(favBtn, fp);

        content.addView(bar);
    }

    /** 更多菜单：收藏（随状态切换文案）+ 分享（复制站点链接） */
    private void showMoreMenu() {
        if (topic == null || topic.id <= 0) {
            android.widget.Toast.makeText(this, "专题信息还没加载完",
                    android.widget.Toast.LENGTH_SHORT).show();
            return;
        }
        fav = Store.get(this).isFavorite(Store.T_TOPIC, topic.id);
        GlassMenu.show(favBtn, GlassMenu.items(
                new GlassMenu.Item(fav ? "★" : "☆",
                        fav ? "取消收藏" : "收藏", this::toggleFav),
                new GlassMenu.Item("⤴", "分享",
                        () -> Share.copy(this, Share.topicUrl(slug)))));
    }

    private void toggleFav() {
        boolean now = Store.get(this).toggleItem(Store.T_TOPIC, topic.id,
                title, topic.cover, slug);
        fav = now;
        android.widget.Toast.makeText(this, now ? "已加入收藏" : "已取消收藏",
                android.widget.Toast.LENGTH_SHORT).show();
    }

    private void buildHeader(LinearLayout content) {
        FrameLayout hero = new FrameLayout(this);
        hero.setClipToOutline(true);
        hero.setBackground(new GlassDrawable(Ui.dp(this, 18)));
        LinearLayout.LayoutParams hp = Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 150));
        hp.leftMargin = Ui.dp(this, 14);
        hp.rightMargin = Ui.dp(this, 14);
        content.addView(hero, hp);

        AspectImageView cover = new AspectImageView(this);
        cover.setRatio(0.44f);
        hero.addView(cover, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        coverLoader = cover;

        View scrim = new View(this);
        scrim.setBackground(new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.BOTTOM_TOP,
                new int[]{0xE605070C, 0x9005070C, 0x0005070C}));
        hero.addView(scrim, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout info = Ui.col(this);
        info.setPadding(Ui.dp(this, 14), 0, Ui.dp(this, 14), Ui.dp(this, 12));
        FrameLayout.LayoutParams ip = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ip.gravity = Gravity.BOTTOM;
        hero.addView(info, ip);

        TextView name = Ui.bold(this, title, 17f, 0xFFFFFFFF);
        name.setMaxLines(2);
        name.setEllipsize(TextUtils.TruncateAt.END);
        info.addView(name);

        countView = Ui.text(this, "共 0 部剧集", 11f, Ui.C_AMBER);
        LinearLayout.LayoutParams clp = Ui.lp(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.topMargin = Ui.dp(this, 5);
        info.addView(countView, clp);

        // 简介在封面外的页面正文区，用主题正文色（不是 scrim 上的白色）
        introView = Ui.text(this, "", 11.5f, Ui.bodyText());
        introView.setLineSpacing(Ui.dp(this, 4), 1f);
        introView.setMaxLines(3);
        introView.setEllipsize(TextUtils.TruncateAt.END);
        introView.setPadding(Ui.dp(this, 18), Ui.dp(this, 10), Ui.dp(this, 18), Ui.dp(this, 4));
        introView.setOnClickListener(v -> {
            expanded = !expanded;
            introView.setMaxLines(expanded ? 40 : 3);
        });
        content.addView(introView);
    }

    private AspectImageView coverLoader;

    private void loadHeader() {
        Api.topicDetail(slug, 1, new Api.Cb<Models.TopicDetail>() {
            @Override
            public void ok(Models.TopicDetail d) {
                topic = d.topic;
                if (d.topic.title != null && !d.topic.title.isEmpty()) {
                    title = d.topic.title;
                }
                countView.setText("共 " + Math.max(d.topic.videoCount, d.videos.size()) + " 部剧集");
                introView.setText(d.topic.intro);
                ImageLoader.load(coverLoader, d.topic.cover);
                // 浏览历史 + 收藏状态（用专题真实 id）
                Store.get(TopicActivity.this).recordRead(Store.T_TOPIC, d.topic.id,
                        slug, title, d.topic.cover);
                fav = Store.get(TopicActivity.this).isFavorite(Store.T_TOPIC, d.topic.id);
                paintFav();
            }

            @Override
            public void err(String msg) {
                // 头图失败不影响列表
            }
        });
    }

    private void paintFav() {
        // 更多键恒为 ic_more；收藏状态在更多菜单里体现，不再把更多键换成星形
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
