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

/** 博客：站内资讯列表，滚动到底自动续页 */
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

            TextView note = Ui.text(c, "剧集动态 · 片单推荐 · 站内公告", 11.5f, Ui.C_DIM);
            LinearLayout.LayoutParams nlp = Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            nlp.leftMargin = Ui.dp(c, 18);
            nlp.bottomMargin = Ui.dp(c, 6);
            root.addView(note, nlp);
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
