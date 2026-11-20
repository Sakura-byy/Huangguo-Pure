package com.huangguo.pure;

import android.content.Context;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

/** 排行榜：热播榜 / 推荐榜 / 飙升榜 三个榜单切换 */
public class RankPage extends FrameLayout implements PullRefresh.Refreshable {

    private static final String[] TYPES = {Api.RANK_HOT, Api.RANK_RECOMMEND, Api.RANK_POTENTIAL};
    private static final String[] NAMES = {"热播榜", "推荐榜", "飙升榜"};
    private static final String[] DESCS = {
            "按站内播放热度排序 · 每日更新 · TOP20",
            "编辑精选与算法推荐加权 · 每日更新 · TOP20",
            "近 24 小时热度增速最快 · 每日更新 · TOP20"
    };

    private final RecyclerView rv;
    private final TextView status;
    private final TextView crumb;
    private final TextView headTitle;
    private final TextView headDesc;
    private final LinearLayout tabBar;
    private final TextView[] tabs = new TextView[3];
    private final View[] bars = new View[3];

    private final List<Models.Video> data = new ArrayList<>();
    private final RankAdapter adapter = new RankAdapter();
    private final List<List<Models.Video>> cache = new ArrayList<>();

    private int current = -1;
    private boolean busy = false;

    public RankPage(Context c) {
        super(c);
        for (int i = 0; i < 3; i++) cache.add(null);

        LinearLayout root = Ui.col(c);
        addView(root, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        root.addView(Chrome.titleBar(c, "排行榜", "每日更新"));

        crumb = Ui.text(c, "", 11.5f, Ui.C_DIM);
        LinearLayout.LayoutParams clp = Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.leftMargin = Ui.dp(c, 18);
        root.addView(crumb, clp);

        LinearLayout head = Ui.row(c);
        LinearLayout.LayoutParams hlp = Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        hlp.leftMargin = Ui.dp(c, 18);
        hlp.rightMargin = Ui.dp(c, 18);
        hlp.topMargin = Ui.dp(c, 12);
        root.addView(head, hlp);

        FrameLayout badge = new FrameLayout(c);
        badge.setBackground(Ui.stroke(0x26F5B942, 0x59F5B942, Ui.dp(c, 13), 1));
        ImageView fire = new ImageView(c);
        fire.setImageResource(R.drawable.ic_fire);
        fire.setColorFilter(Ui.C_AMBER);
        badge.addView(fire, new FrameLayout.LayoutParams(Ui.dp(c, 22), Ui.dp(c, 22), Gravity.CENTER));
        head.addView(badge, Ui.lp(Ui.dp(c, 44), Ui.dp(c, 44)));

        LinearLayout headTexts = Ui.col(c);
        LinearLayout.LayoutParams htlp = Ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT);
        htlp.weight = 1f;
        htlp.leftMargin = Ui.dp(c, 12);
        head.addView(headTexts, htlp);

        headTitle = Ui.bold(c, "热播榜", 23f, Ui.C_TEXT);
        headTexts.addView(headTitle);

        headDesc = Ui.text(c, DESCS[0], 11.5f, Ui.C_SUB);
        LinearLayout.LayoutParams dlp = Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        dlp.topMargin = Ui.dp(c, 3);
        headTexts.addView(headDesc, dlp);

        tabBar = Ui.row(c);
        tabBar.setPadding(Ui.dp(c, 18), Ui.dp(c, 16), Ui.dp(c, 18), 0);
        root.addView(tabBar, Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        for (int i = 0; i < 3; i++) buildTab(i);

        View line = new View(c);
        line.setBackgroundColor(0x14FFFFFF);
        root.addView(line, Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT, 1));

        SwipeFrame box = new SwipeFrame(c);
        box.setOnSwipe(dir -> {
            int next = current + dir;
            if (next < 0 || next >= TYPES.length) {
                // 榜单滑到头 → 切相邻一级页：左→分类，右→我的
                if (c instanceof MainActivity) {
                    ((MainActivity) c).selectPrimary(dir < 0 ? 1 : 3);
                }
                return;
            }
            select(next, true);
        });
        root.addView(box, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        rv = new RecyclerView(c);
        rv.setLayoutManager(new LinearLayoutManager(c));
        rv.setAdapter(adapter);
        rv.setClipToPadding(false);
        rv.setPadding(0, Ui.dp(c, 4), 0, Ui.dp(c, 96));
        rv.setOverScrollMode(OVER_SCROLL_NEVER);
        box.addView(rv, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        status = Ui.text(c, "加载中…", 13f, Ui.C_SUB);
        status.setGravity(Gravity.CENTER);
        box.addView(status, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER));

        select(0, true);
    }

    private void buildTab(final int index) {
        Context c = getContext();
        LinearLayout cell = Ui.col(c);
        cell.setGravity(Gravity.CENTER_HORIZONTAL);
        cell.setClickable(true);
        cell.setPadding(Ui.dp(c, 2), Ui.dp(c, 4), Ui.dp(c, 2), 0);
        // 二级 tab 等宽铺满整行（与分类页统一）
        LinearLayout.LayoutParams lp = Ui.lp(0,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.weight = 1f;
        cell.setLayoutParams(lp);

        TextView label = Ui.text(c, NAMES[index], 14.5f, Ui.C_SUB);
        label.setGravity(Gravity.CENTER);
        cell.addView(label);

        View bar = new View(c);
        bar.setBackground(Ui.round(Ui.C_AMBER, Ui.dp(c, 2)));
        LinearLayout.LayoutParams blp = Ui.lp(Ui.dp(c, 26), Ui.dp(c, 3));
        blp.topMargin = Ui.dp(c, 7);
        blp.gravity = Gravity.CENTER_HORIZONTAL;
        cell.addView(bar, blp);

        bar.setAlpha(0f);
        label.setTextColor(index == 0 ? Ui.C_TEXT : Ui.C_SUB);

        cell.setOnClickListener(v -> select(index, true));

        tabs[index] = label;
        bars[index] = bar;
        tabBar.addView(cell);
    }

    public void refresh() {
        if (current >= 0) load(current, null);
    }

    @Override
    public void onPullRefresh(PullRefresh.Done done) {
        if (current < 0) {
            done.done();
            return;
        }
        load(current, done);
    }

    private void select(int index, boolean force) {
        if (index == current && !force) return;
        current = index;
        for (int i = 0; i < 3; i++) {
            boolean active = i == index;
            tabs[i].setTextColor(active ? Ui.C_TEXT : Ui.C_SUB);
            tabs[i].setTypeface(active ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
            tabs[i].setTextSize(active ? 16f : 15f);
            bars[i].animate().alpha(active ? 1f : 0f).setDuration(180).start();
        }
        crumb.setText("首页 / 排行榜 / " + NAMES[index]);
        headTitle.setText(NAMES[index]);
        headDesc.setText(DESCS[index]);

        List<Models.Video> cached = cache.get(index);
        if (cached != null && !cached.isEmpty() && !force) {
            data.clear();
            data.addAll(cached);
            adapter.notifyDataSetChanged();
            status.setVisibility(GONE);
            rv.scrollToPosition(0);
            return;
        }
        load(index, null);
    }

    private void load(final int index, final PullRefresh.Done done) {
        if (busy) {
            if (done != null) done.done();
            return;
        }
        busy = true;
        status.setVisibility(VISIBLE);
        status.setText("加载中…");
        status.setOnClickListener(null);
        Api.rank(TYPES[index], new Api.Cb<List<Models.Video>>() {
            @Override
            public void ok(List<Models.Video> list) {
                busy = false;
                cache.set(index, list);
                if (index != current) return;
                data.clear();
                data.addAll(list);
                adapter.notifyDataSetChanged();
                status.setVisibility(data.isEmpty() ? VISIBLE : GONE);
                if (data.isEmpty()) status.setText("暂无数据");
                if (done != null) done.done();
            }

            @Override
            public void err(String msg) {
                busy = false;
                if (index != current) return;
                if (data.isEmpty()) {
                    status.setVisibility(VISIBLE);
                    status.setText("加载失败：" + msg + "\n点此重试");
                    status.setOnClickListener(v -> load(index, null));
                }
                if (done != null) done.done();
            }
        });
    }

    private class RankAdapter extends RecyclerView.Adapter<RankAdapter.H> {

        class H extends RecyclerView.ViewHolder {
            final TextView rank;
            final AspectImageView cover;
            final TextView title;
            final TextView sub;
            final TextView metric;

            H(LinearLayout row) {
                super(row);
                rank = (TextView) row.getChildAt(0);
                LinearLayout mid = (LinearLayout) row.getChildAt(1);
                cover = (AspectImageView) mid.getChildAt(0);
                LinearLayout texts = (LinearLayout) mid.getChildAt(1);
                title = (TextView) texts.getChildAt(0);
                sub = (TextView) texts.getChildAt(1);
                metric = (TextView) row.getChildAt(2);
            }
        }

        @NonNull
        @Override
        public H onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            Context c = parent.getContext();
            LinearLayout row = Ui.row(c);
            row.setPadding(Ui.dp(c, 16), Ui.dp(c, 7), Ui.dp(c, 16), Ui.dp(c, 7));

            TextView rank = Ui.bold(c, "", 15f, Ui.C_AMBER);
            rank.setGravity(Gravity.CENTER);
            rank.setLayoutParams(Ui.lp(Ui.dp(c, 30), Ui.dp(c, 30)));
            row.addView(rank);

            LinearLayout mid = Ui.row(c);
            LinearLayout.LayoutParams mp = Ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT);
            mp.weight = 1f;
            row.addView(mid, mp);

            AspectImageView cover = new AspectImageView(c);
            cover.setRatio(1.34f);
            cover.setLayoutParams(Ui.lp(Ui.dp(c, 62), Ui.dp(c, 83)));
            mid.addView(cover);

            LinearLayout texts = Ui.col(c);
            LinearLayout.LayoutParams tp = Ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT);
            tp.weight = 1f;
            tp.leftMargin = Ui.dp(c, 11);
            mid.addView(texts, tp);

            TextView title = Ui.bold(c, "", 13.5f, Ui.C_TEXT);
            title.setMaxLines(2);
            title.setEllipsize(TextUtils.TruncateAt.END);
            texts.addView(title);

            TextView sub = Ui.text(c, "", 11f, Ui.C_SUB);
            sub.setMaxLines(1);
            sub.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams sp = Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            sp.topMargin = Ui.dp(c, 5);
            texts.addView(sub, sp);

            TextView metric = Ui.text(c, "", 11f, Ui.darkGlass ? 0xFFFF8A65 : 0xFFC2410C);
            metric.setGravity(Gravity.END);
            metric.setLayoutParams(Ui.lp(Ui.dp(c, 64), ViewGroup.LayoutParams.WRAP_CONTENT));
            row.addView(metric);

            row.setOnClickListener(v -> {
                int pos = rv.getChildAdapterPosition(row);
                if (pos != RecyclerView.NO_POSITION && pos < data.size()) {
                    DetailActivity.open(c, data.get(pos).id);
                }
            });
            return new H(row);
        }

        @Override
        public void onBindViewHolder(@NonNull H h, int position) {
            Models.Video v = data.get(position);
            h.rank.setText(v.rank > 0 ? String.valueOf(v.rank) : String.valueOf(position + 1));
            h.rank.setTextColor(position < 3 ? Ui.C_AMBER : Ui.C_SUB);
            h.rank.setTypeface(Typeface.DEFAULT_BOLD);
            h.title.setText(v.title);
            StringBuilder sb = new StringBuilder();
            if (v.cateName != null && !v.cateName.isEmpty()) sb.append(v.cateName);
            if (v.score > 0) {
                if (sb.length() > 0) sb.append(" · ");
                sb.append(v.scoreText()).append("分");
            }
            if (v.epCount > 0) {
                if (sb.length() > 0) sb.append(" · ");
                sb.append(v.epText());
            }
            if (v.tags != null && !v.tags.isEmpty() && sb.length() < 24) {
                sb.append(" · ").append(v.tags);
            }
            h.sub.setText(sb.toString());
            long metric = v.metricValue > 0 ? v.metricValue : v.hot;
            h.metric.setText((v.metricLabel.isEmpty() ? "" : v.metricLabel + "\n")
                    + Ui.shortNum(metric));
            h.metric.setMaxLines(2);
            ImageLoader.load(h.cover, v.cover);
        }

        @Override
        public int getItemCount() {
            return data.size();
        }
    }
}
