package com.huangguo.pure;

import android.content.Context;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** 首页：焦点横滑位 + 精选 + 最新上架；板块标题右侧带「查看全部」 */
public class HomePage extends FrameLayout implements PullRefresh.Refreshable {

    private final PagedGrid grid;
    /** 「查看全部」跳转目标：分类页 */
    private final Runnable goCategory;
    /** 搜索框点击：打开搜索页 */
    private final Runnable goSearch;

    public HomePage(Context c, Runnable goCategory, Runnable goSearch) {
        super(c);
        this.goCategory = goCategory;
        this.goSearch = goSearch;
        LinearLayout root = Ui.col(c);
        addView(root, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        GlassHeader head = Chrome.titleBar(c, "黄果短剧", "纯净 · 无广告");
        root.addView(head);
        head.row().addView(buildSearchBox(c), searchBoxLp(c));

        grid = new PagedGrid(c, 2, v -> DetailActivity.open(c, v.id));
        root.addView(grid, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        load(null);
    }

    /** 顶栏里的搜索框：标题右边一整条，点击即进搜索页 */
    private LinearLayout buildSearchBox(Context c) {
        LinearLayout box = Ui.row(c);
        box.setGravity(Gravity.CENTER_VERTICAL);
        box.setPadding(Ui.dp(c, 12), 0, Ui.dp(c, 12), 0);
        // 顶栏现在是实色底，搜索框要用比底色深一档的填充才看得出边界
        box.setBackground(Ui.stroke(Ui.darkGlass ? 0xFF171D27 : 0xFFEBEEF5,
                Ui.darkGlass ? 0x2EFFFFFF : 0x141A2332, Ui.dp(c, 18), 1));
        box.setClickable(true);
        box.setOnClickListener(v -> {
            if (goSearch != null) goSearch.run();
        });

        ImageView ic = new ImageView(c);
        ic.setImageResource(R.drawable.ic_search);
        ic.setColorFilter(Ui.C_DIM);
        box.addView(ic, Ui.lp(Ui.dp(c, 15), Ui.dp(c, 15)));

        TextView hint = Ui.text(c, "搜索短剧 · 博主", 12f, Ui.C_DIM);
        LinearLayout.LayoutParams hp = Ui.lp(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        hp.leftMargin = Ui.dp(c, 8);
        box.addView(hint, hp);
        return box;
    }

    private LinearLayout.LayoutParams searchBoxLp(Context c) {
        LinearLayout.LayoutParams lp = Ui.lp(0, Ui.dp(c, 36));
        lp.weight = 1f;
        lp.leftMargin = Ui.dp(c, 14);
        lp.rightMargin = Ui.dp(c, 4);
        return lp;
    }

    public void refresh() {
        load(null);
    }

    @Override
    public void onPullRefresh(PullRefresh.Done done) {
        load(done);
    }

    private void load(final PullRefresh.Done done) {
        grid.clear();
        grid.setStatus("加载中…");
        Api.home(new Api.Cb<Models.Home>() {
            @Override
            public void ok(Models.Home home) {
                List<FeedAdapter.Row> head = new ArrayList<>();
                if (!home.banners.isEmpty()) head.add(FeedAdapter.Row.banner(home.banners));
                head.add(FeedAdapter.Row.section("热门标签"));
                head.add(FeedAdapter.Row.tags());
                if (!home.featured.isEmpty()) {
                    head.add(FeedAdapter.Row.section("精选推荐", goCategory));
                    for (Models.Video v : home.featured) head.add(FeedAdapter.Row.of(v));
                }
                if (!home.latest.isEmpty()) head.add(FeedAdapter.Row.section("最新上架", goCategory));
                for (FeedAdapter.Row r : head) grid.adapter().addHead(r);
                grid.adapter().addVideos(home.latest);
                grid.hideStatus();
                if (grid.adapter().getItemCount() == 0) grid.setStatus("暂无内容");
                if (done != null) done.done();
            }

            @Override
            public void err(String msg) {
                grid.setStatus("加载失败：" + msg + "\n点此重试");
                grid.setOnClickListener(v -> load(null));
                if (done != null) done.done();
            }
        });
    }
}
