package com.huangguo.pure;

import android.content.Context;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

/** 可翻页的海报网格容器（下拉刷新由页面顶部按钮触发，避免额外依赖） */
public class PagedGrid extends FrameLayout {

    public interface Source {
        void load(int page, Api.Cb<Models.Page> cb);
    }

    private final RecyclerView rv;
    private final TextView status;
    private final TextView loadingTip;
    private final FeedAdapter adapter;
    private final GridLayoutManager glm;

    private Source source;
    private int page = 0;
    private int pages = 1;
    private boolean busy = false;
    private boolean firstLoaded = false;

    public PagedGrid(Context c, int span, FeedAdapter.OnOpen open) {
        super(c);
        adapter = new FeedAdapter(span, open);
        rv = new RecyclerView(c);
        glm = new GridLayoutManager(c, span);
        glm.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                return adapter.spanOf(position);
            }
        });
        rv.setLayoutManager(glm);
        rv.setAdapter(adapter);
        rv.setClipToPadding(false);
        rv.setPadding(Ui.dp(c, 9), Ui.dp(c, 4), Ui.dp(c, 9), Ui.dp(c, 96));
        rv.setOverScrollMode(OVER_SCROLL_NEVER);
        addView(rv, new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        status = Ui.text(c, "", 13f, Ui.C_SUB);
        status.setGravity(Gravity.CENTER);
        status.setPadding(Ui.dp(c, 24), Ui.dp(c, 24), Ui.dp(c, 24), Ui.dp(c, 24));
        FrameLayout.LayoutParams sp = new FrameLayout.LayoutParams(LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT);
        sp.gravity = Gravity.CENTER;
        addView(status, sp);

        loadingTip = Ui.text(c, "加载中…", 11.5f, Ui.C_DIM);
        loadingTip.setGravity(Gravity.CENTER);
        loadingTip.setVisibility(GONE);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT,
                Ui.dp(c, 34));
        lp.gravity = Gravity.BOTTOM;
        lp.bottomMargin = Ui.dp(c, 84);
        addView(loadingTip, lp);

        rv.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                if (dy <= 0) return;
                int last = glm.findLastVisibleItemPosition();
                if (last >= adapter.getItemCount() - 4 && !busy && page < pages) {
                    load(page + 1);
                }
            }
        });
    }

    public FeedAdapter adapter() {
        return adapter;
    }

    public RecyclerView recycler() {
        return rv;
    }

    public void setSource(Source s) {
        this.source = s;
    }

    public void clear() {
        adapter.clearAll();
        page = 0;
        pages = 1;
        firstLoaded = false;
        status.setVisibility(GONE);
    }

    public void reload() {
        clear();
        loadingTip.setVisibility(GONE);
        status.setText("加载中…");
        status.setVisibility(VISIBLE);
        load(1);
    }

    private void load(final int p) {
        if (source == null || busy) return;
        busy = true;
        loadingTip.setVisibility(p > 1 ? VISIBLE : GONE);
        source.load(p, new Api.Cb<Models.Page>() {
            @Override
            public void ok(Models.Page data) {
                busy = false;
                loadingTip.setVisibility(GONE);
                page = p;
                pages = Math.max(1, data.pages);
                firstLoaded = true;
                adapter.addVideos(data.items);
                if (p == 1) status.setVisibility(adapter.getItemCount() == 0 ? VISIBLE : GONE);
                if (adapter.getItemCount() == 0) status.setText("暂无内容");
                if (p == 1 && data.items.isEmpty()) status.setText("暂无内容，稍后再试");
            }

            @Override
            public void err(String msg) {
                busy = false;
                loadingTip.setVisibility(GONE);
                if (!firstLoaded) {
                    status.setVisibility(VISIBLE);
                    status.setText("加载失败：" + msg + "\n点此重试");
                    status.setOnClickListener(v -> reload());
                }
            }
        });
    }

    /** 首页：先注入头部行，再拉第一页 */
    public void reloadWithHeader(java.util.List<FeedAdapter.Row> header) {
        adapter.clearAll();
        for (FeedAdapter.Row r : header) adapter.addHead(r);
        page = 0;
        pages = 1;
        load(1);
    }

    public void showEmpty(String text) {
        status.setVisibility(VISIBLE);
        status.setText(text);
    }

    /** 让网格左右两侧留白一致 */
    public void setColumnGap(int dp) {
        rv.setPadding(Ui.dp(getContext(), dp), Ui.dp(getContext(), 4),
                Ui.dp(getContext(), dp), Ui.dp(getContext(), 96));
        int span = glm.getSpanCount();
        glm.setSpanCount(span);
        adapter.notifyDataSetChanged();
    }

    public void scrollToTop() {
        rv.scrollToPosition(0);
    }

    public void setStatus(String text) {
        status.setText(text);
        status.setVisibility(VISIBLE);
        status.setOnClickListener(null);
    }

    public void hideStatus() {
        status.setVisibility(GONE);
    }
}
