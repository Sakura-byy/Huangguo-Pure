package com.huangguo.pure;

import android.content.Context;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

/**
 * 博客：站内资讯列表，滚动到底自动续页。
 *
 * 顶部分类 chips 与「发现 - 剧集」同一套（Chrome.chip）；
 * 「全部」走 /api/posts，「黑料吃瓜 / 短剧剧评」走站点两个频道的服务端渲染页
 * （/blog/gossip/、/blog/reviews/），解析逻辑在 Models.postPageFromHtml。
 */
public class BlogPage extends FrameLayout implements PullRefresh.Refreshable {

    private final RecyclerView rv;
    private final TextView status;
    private final TextView loadingTip;
    private final LinearLayoutManager lm;
    private final List<Models.Post> data = new ArrayList<>();
    private final Adapter adapter = new Adapter();

    private int page = 0;
    private int pages = 1;
    private boolean busy = false;

    /** 博客频道：空 = 全部（/api/posts），gossip / reviews = 站点分类页 */
    private String channel = "";
    private LinearLayout chipBar;
    private final java.util.List<TextView> chips = new ArrayList<>();
    private TextView countTip;
    private static final String[] CH_NAMES = {"全部", "黑料吃瓜", "短剧剧评"};
    private static final String[] CH_ROUTES = {"", "gossip", "reviews"};

    public BlogPage(Context c) {
        this(c, false);
    }

    /** @param embedded true 时去掉自家标题栏，供「分类」页内嵌使用 */
    public BlogPage(Context c, boolean embedded) {
        super(c);
        LinearLayout root = Ui.col(c);
        addView(root, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        if (!embedded) {
            root.addView(Chrome.titleBar(c, "博客", "站内资讯"));

            countTip = Ui.text(c, "剧集动态 · 片单推荐 · 站内公告", 11.5f, Ui.C_DIM);
            LinearLayout.LayoutParams nlp = Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            nlp.leftMargin = Ui.dp(c, 18);
            nlp.bottomMargin = Ui.dp(c, 6);
            root.addView(countTip, nlp);
        }

        // 分类 chips：等宽铺满整行，和「发现 - 剧集」的短剧/漫剧完全同一套样式
        chipBar = Ui.row(c);
        chipBar.setPadding(Ui.dp(c, 16), Ui.dp(c, 0), Ui.dp(c, 16), Ui.dp(c, 8));
        root.addView(chipBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        for (int i = 0; i < CH_NAMES.length; i++) {
            final int idx = i;
            TextView t = Chrome.chip(c, CH_NAMES[i], CH_ROUTES[i].equals(channel));
            LinearLayout.LayoutParams lp = Ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.weight = 1f;
            if (i < CH_NAMES.length - 1) lp.rightMargin = Ui.dp(c, 8);
            t.setOnClickListener(v -> selectChannel(idx));
            chips.add(t);
            chipBar.addView(t, lp);
        }

        FrameLayout box = new FrameLayout(c);
        root.addView(box, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        lm = new LinearLayoutManager(c);
        rv = new RecyclerView(c);
        rv.setLayoutManager(lm);
        rv.setAdapter(adapter);
        rv.setClipToPadding(false);
        rv.setPadding(0, Ui.dp(c, 2), 0, Ui.dp(c, 24));
        rv.setOverScrollMode(OVER_SCROLL_NEVER);
        box.addView(rv, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        status = Ui.text(c, "加载中…", 13f, Ui.C_SUB);
        status.setGravity(Gravity.CENTER);
        box.addView(status, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER));

        loadingTip = Ui.text(c, "加载中…", 11.5f, Ui.C_DIM);
        loadingTip.setGravity(Gravity.CENTER);
        loadingTip.setVisibility(GONE);
        FrameLayout.LayoutParams llp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(c, 32));
        llp.gravity = Gravity.BOTTOM;
        llp.bottomMargin = Ui.dp(c, 8);
        box.addView(loadingTip, llp);

        rv.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView v, int dx, int dy) {
                if (dy <= 0) return;
                int last = lm.findLastVisibleItemPosition();
                if (last >= adapter.getItemCount() - 3 && !busy && page < pages) load(page + 1);
            }
        });

        load(1, null);
    }

    public void refresh() {
        if (data.isEmpty()) load(1, null);
    }

    @Override
    public void onPullRefresh(PullRefresh.Done done) {
        if (busy) {
            done.done();
            return;
        }
        load(1, done);
    }

    private void load(final int p) {
        load(p, null);
    }

    private void load(final int p, final PullRefresh.Done done) {
        if (busy) {
            if (done != null) done.done();
            return;
        }
        busy = true;
        if (p == 1) {
            status.setVisibility(VISIBLE);
            status.setText("加载中…");
            status.setOnClickListener(null);
        } else {
            loadingTip.setVisibility(VISIBLE);
        }
        if (!channel.isEmpty()) {
            loadChannel(p, done);
            return;
        }
        Api.posts(p, new Api.Cb<Models.PostPage>() {
            @Override
            public void ok(Models.PostPage pg) {
                busy = false;
                loadingTip.setVisibility(GONE);
                page = p;
                pages = Math.max(1, pg.pages);
                if (p == 1) {
                    data.clear();
                    status.setVisibility(pg.items.isEmpty() ? VISIBLE : GONE);
                    if (pg.items.isEmpty()) status.setText("暂无内容");
                }
                updateCount(pg, false);
                int start = data.size();
                data.addAll(pg.items);
                if (start == 0) adapter.notifyDataSetChanged();
                else adapter.notifyItemRangeInserted(start, pg.items.size());
                if (done != null) done.done();
            }

            @Override
            public void err(String msg) {
                busy = false;
                loadingTip.setVisibility(GONE);
                if (data.isEmpty()) {
                    status.setVisibility(VISIBLE);
                    status.setText("加载失败：" + msg + "\n点此重试");
                    status.setOnClickListener(v -> load(1, null));
                }
                if (done != null) done.done();
            }
        });
    }

    /** 频道页：服务端渲染 HTML 解析，一次 12 篇 */
    private void loadChannel(final int p, final PullRefresh.Done done) {
        Api.postsByChannel(channel, p, new Api.Cb<Models.PostPage>() {
            @Override
            public void ok(Models.PostPage pg) {
                busy = false;
                loadingTip.setVisibility(GONE);
                page = p;
                pages = Math.max(1, pg.pages);
                if (p == 1) {
                    data.clear();
                    status.setVisibility(pg.items.isEmpty() ? VISIBLE : GONE);
                    if (pg.items.isEmpty()) status.setText("暂无内容");
                }
                updateCount(pg, true);
                int start = data.size();
                data.addAll(pg.items);
                if (start == 0) adapter.notifyDataSetChanged();
                else adapter.notifyItemRangeInserted(start, pg.items.size());
                if (done != null) done.done();
            }

            @Override
            public void err(String msg) {
                busy = false;
                loadingTip.setVisibility(GONE);
                if (data.isEmpty()) {
                    status.setVisibility(VISIBLE);
                    status.setText("加载失败：" + msg + "\n点此重试");
                    status.setOnClickListener(v -> load(1, null));
                }
                if (done != null) done.done();
            }
        });
    }

    /** 头部计数：全部=接口 total；频道页=HTML 里的「共 N 篇」 */
    private void updateCount(Models.PostPage pg, boolean channelPage) {
        if (countTip == null) return;
        String name = channel.isEmpty() ? "最新文章" : CH_NAMES[indexOfChannel()];
        if (pg.total > 0) {
            countTip.setText(name + " · 共 " + Ui.shortNum(pg.total) + " 篇");
        } else {
            countTip.setText(name + (channelPage ? " · 每日更新" : " · 剧集动态"));
        }
    }

    /**
     * 三级分类左右滑动：在「全部 / 黑料吃瓜 / 短剧剧评」之间切。
     * 到头返回 false，由外层 CategoryPage 决定切二级页面还是切一级页。
     */
    public boolean swipeChannel(int dir) {
        int idx = indexOfChannel();
        int next = idx + dir;
        if (next < 0 || next >= CH_ROUTES.length) return false;
        selectChannel(next);
        return true;
    }

    private int indexOfChannel() {
        for (int i = 0; i < CH_ROUTES.length; i++) {
            if (CH_ROUTES[i].equals(channel)) return i;
        }
        return 0;
    }

    /** 切分类：高亮跟着走，列表从第一页重拉 */
    private void selectChannel(int idx) {
        if (idx < 0 || idx >= CH_ROUTES.length) return;
        if (CH_ROUTES[idx].equals(channel)) return;
        channel = CH_ROUTES[idx];
        Context c = getContext();
        for (int k = 0; k < chips.size(); k++) {
            boolean on = k == idx;
            TextView tv = chips.get(k);
            tv.setTextColor(on ? Ui.onAmber() : Ui.C_TEXT);
            tv.setTypeface(on ? android.graphics.Typeface.DEFAULT_BOLD
                    : android.graphics.Typeface.DEFAULT);
            tv.setBackground(on
                    ? Ui.round(Ui.C_AMBER, Ui.dp(c, 18))
                    : Ui.stroke(0x14FFFFFF, 0x26FFFFFF, Ui.dp(c, 18), 1));
        }
        data.clear();
        adapter.notifyDataSetChanged();
        page = 0;
        pages = 1;
        load(1, null);
    }

    private class Row extends LinearLayout {
        final AspectImageView cover;
        final TextView title;
        final TextView meta;

        Row(Context c) {
            super(c);
            setOrientation(HORIZONTAL);
            setGravity(Gravity.CENTER_VERTICAL);
            setPadding(Ui.dp(c, 16), Ui.dp(c, 9), Ui.dp(c, 16), Ui.dp(c, 9));

            cover = new AspectImageView(c);
            cover.setRatio(0.72f);
            cover.setLayoutParams(Ui.lp(Ui.dp(c, 108), Ui.dp(c, 78)));
            addView(cover);

            LinearLayout texts = Ui.col(c);
            LinearLayout.LayoutParams tp = Ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT);
            tp.weight = 1f;
            tp.leftMargin = Ui.dp(c, 12);
            addView(texts, tp);

            title = Ui.bold(c, "", 13.5f, Ui.C_TEXT);
            title.setMaxLines(2);
            title.setEllipsize(TextUtils.TruncateAt.END);
            texts.addView(title);

            meta = Ui.text(c, "", 10.5f, Ui.C_DIM);
            meta.setMaxLines(1);
            meta.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams mp = Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            mp.topMargin = Ui.dp(c, 6);
            texts.addView(meta, mp);
        }
    }

    private class Adapter extends RecyclerView.Adapter<Adapter.H> {

        class H extends RecyclerView.ViewHolder {
            final Row row;

            H(Row row) {
                super(row);
                this.row = row;
            }
        }

        @NonNull
        @Override
        public H onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            Context c = parent.getContext();
            Row row = new Row(c);
            RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            row.setLayoutParams(lp);
            return new H(row);
        }

        @Override
        public void onBindViewHolder(@NonNull H h, int position) {
            Models.Post p = data.get(position);
            h.row.title.setText(p.title);
            StringBuilder sb = new StringBuilder();
            if (!p.time.isEmpty()) sb.append(p.time);
            if (p.hot > 0) {
                if (sb.length() > 0) sb.append("  ·  ");
                sb.append("热度 ").append(Ui.shortNum(p.hot));
            }
            if (!p.authorName.isEmpty()) {
                if (sb.length() > 0) sb.append("  ·  ");
                sb.append(p.authorName);
            }
            h.row.meta.setText(sb.toString());
            ImageLoader.load(h.row.cover, p.cover);
            h.row.setOnClickListener(v -> PostActivity.open(getContext(), p.id, p.title));
        }

        @Override
        public int getItemCount() {
            return data.size();
        }
    }
}
