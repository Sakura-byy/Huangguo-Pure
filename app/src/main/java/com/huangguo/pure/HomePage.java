package com.huangguo.pure;

import android.content.Context;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import java.util.ArrayList;
import java.util.List;

/** 首页：焦点横滑位 + 精选 + 最新上架；板块标题右侧带「查看全部」 */
public class HomePage extends FrameLayout implements PullRefresh.Refreshable {

    private final PagedGrid grid;
    /** 「查看全部」跳转目标：分类页 */
    private final Runnable goCategory;

    public HomePage(Context c, Runnable goCategory) {
        super(c);
        this.goCategory = goCategory;
        LinearLayout root = Ui.col(c);
        addView(root, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        root.addView(Chrome.titleBar(c, "黄果短剧", "纯净 · 无广告"));

        grid = new PagedGrid(c, 2, v -> DetailActivity.open(c, v.id));
        root.addView(grid, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        load(null);
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
