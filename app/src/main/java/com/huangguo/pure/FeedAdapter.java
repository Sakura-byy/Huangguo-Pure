package com.huangguo.pure;

import android.content.Context;
import android.graphics.Color;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

/** 信息流适配器：横滑焦点位 / 分区标题 / 海报卡 三种行 */
public class FeedAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public static final int T_BANNER = 0;
    public static final int T_SECTION = 1;
    public static final int T_VIDEO = 2;
    public static final int T_TAGS = 3;

    public interface OnOpen {
        void onOpen(Models.Video v);
    }

    public static class Row {
        public int type;
        public String text = "";
        public Models.Video video;
        public List<Models.Video> banners = new ArrayList<>();
        /** section 右侧「查看全部」回调；null 不显示按钮 */
        public Runnable more;

        public static Row section(String t) {
            return section(t, null);
        }

        public static Row section(String t, Runnable more) {
            Row r = new Row();
            r.type = T_SECTION;
            r.text = t;
            r.more = more;
            return r;
        }

        public static Row banner(List<Models.Video> list) {
            Row r = new Row();
            r.type = T_BANNER;
            r.banners = list;
            return r;
        }

        public static Row tags() {
            Row r = new Row();
            r.type = T_TAGS;
            return r;
        }

        public static Row of(Models.Video v) {
            Row r = new Row();
            r.type = T_VIDEO;
            r.video = v;
            return r;
        }
    }

    private final List<Row> rows = new ArrayList<>();
    private final OnOpen open;
    private final int span;

    public FeedAdapter(int span, OnOpen open) {
        this.span = span;
        this.open = open;
    }

    public List<Row> rows() {
        return rows;
    }

    public void clearAll() {
        rows.clear();
        notifyDataSetChanged();
    }

    public void addHead(Row r) {
        rows.add(r);
        notifyItemInserted(rows.size() - 1);
    }

    public void addVideos(List<Models.Video> list) {
        if (list.isEmpty()) return;
        int start = rows.size();
        for (Models.Video v : list) rows.add(Row.of(v));
        notifyItemRangeInserted(start, list.size());
    }

    @Override
    public int getItemViewType(int position) {
        return rows.get(position).type;
    }

    public int spanOf(int position) {
        return rows.get(position).type == T_VIDEO ? 1 : span;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        Context c = parent.getContext();
        if (viewType == T_SECTION) {
            LinearLayout wrap = Ui.row(c);
            wrap.setGravity(android.view.Gravity.CENTER_VERTICAL);
            wrap.setPadding(Ui.dp(c, 14), Ui.dp(c, 16), Ui.dp(c, 14), Ui.dp(c, 8));
            TextView bar = new TextView(c);
            bar.setBackgroundColor(Ui.C_AMBER);
            wrap.addView(bar, new LinearLayout.LayoutParams(Ui.dp(c, 3), Ui.dp(c, 14)));
            TextView t = Ui.bold(c, "", 15f, Ui.C_TEXT);
            LinearLayout.LayoutParams tp = Ui.lp(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            tp.leftMargin = Ui.dp(c, 8);
            wrap.addView(t, tp);
            // 右侧「查看全部」胶囊：与网站首页板块头部一致
            TextView moreBtn = Ui.text(c, "查看全部", 11f, Ui.C_AMBER);
            moreBtn.setGravity(android.view.Gravity.CENTER);
            moreBtn.setPadding(Ui.dp(c, 12), Ui.dp(c, 6), Ui.dp(c, 12), Ui.dp(c, 6));
            moreBtn.setBackground(Ui.stroke(0x26F5B942, 0x4DF5B942, Ui.dp(c, 14), 1));
            View filler = new View(c);
            LinearLayout.LayoutParams flp = Ui.lp(0, 1);
            flp.weight = 1f;
            wrap.addView(filler, flp);
            wrap.addView(moreBtn, Ui.lp(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            return new SectionHolder(wrap, t, bar, moreBtn);
        }
        if (viewType == T_TAGS) {
            HorizontalScrollView hs = new HorizontalScrollView(c);
            hs.setHorizontalScrollBarEnabled(false);
            hs.setClipToPadding(false);
            hs.setPadding(Ui.dp(c, 14), Ui.dp(c, 0), Ui.dp(c, 14), Ui.dp(c, 2));
            LinearLayout bar = Ui.row(c);
            hs.addView(bar, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            return new TagsHolder(hs, bar);
        }
        if (viewType == T_BANNER) {
            HorizontalScrollView hs = new HorizontalScrollView(c);
            hs.setHorizontalScrollBarEnabled(false);
            hs.setClipToPadding(false);
            hs.setPadding(Ui.dp(c, 8), Ui.dp(c, 4), Ui.dp(c, 8), Ui.dp(c, 4));
            LinearLayout bar = Ui.row(c);
            hs.addView(bar, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            return new BannerHolder(hs, bar);
        }
        PosterCard card = new PosterCard(c, 16);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(Ui.dp(c, 5), Ui.dp(c, 5), Ui.dp(c, 5), Ui.dp(c, 5));
        card.setLayoutParams(lp);
        return new VideoHolder(card);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Row row = rows.get(position);
        if (holder instanceof SectionHolder) {
            SectionHolder sh = (SectionHolder) holder;
            sh.title.setText(row.text);
            if (row.more != null) {
                sh.moreBtn.setVisibility(View.VISIBLE);
                sh.moreBtn.setOnClickListener(v -> row.more.run());
            } else {
                sh.moreBtn.setVisibility(View.GONE);
                sh.moreBtn.setOnClickListener(null);
            }
        } else if (holder instanceof TagsHolder) {
            ((TagsHolder) holder).bind();
        } else if (holder instanceof BannerHolder) {
            ((BannerHolder) holder).bind(row.banners, open);
        } else if (holder instanceof VideoHolder) {
            VideoHolder h = (VideoHolder) holder;
            h.card.bind(row.video);
            h.card.setOnClickListener(v -> open.onOpen(row.video));
        }
    }

    @Override
    public int getItemCount() {
        return rows.size();
    }

    static class SectionHolder extends RecyclerView.ViewHolder {
        final TextView title;
        final TextView moreBtn;

        SectionHolder(View v, TextView t, View bar, TextView moreBtn) {
            super(v);
            this.title = t;
            this.moreBtn = moreBtn;
        }
    }

    static class VideoHolder extends RecyclerView.ViewHolder {
        final PosterCard card;

        VideoHolder(PosterCard c) {
            super(c);
            this.card = c;
        }
    }

    static class TagsHolder extends RecyclerView.ViewHolder {
        final LinearLayout bar;

        TagsHolder(View v, LinearLayout bar) {
            super(v);
            this.bar = bar;
        }

        void bind() {
            bar.removeAllViews();
            final Context c = bar.getContext();
            for (int i = 0; i < HotTags.COUNT; i++) {
                String name = HotTags.name(i);
                final String slug = HotTags.slug(i);
                final String tagName = name;
                TextView t = Ui.text(c, name, 12.5f, Ui.C_TEXT);
                t.setGravity(Gravity.CENTER);
                t.setPadding(Ui.dp(c, 14), Ui.dp(c, 8), Ui.dp(c, 14), Ui.dp(c, 8));
                t.setBackground(new GlassDrawable(Ui.dp(c, 17)));
                LinearLayout.LayoutParams lp = Ui.lp(ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.rightMargin = Ui.dp(c, 8);
                t.setOnClickListener(v -> TagActivity.open(c, tagName, slug));
                bar.addView(t, lp);
            }
        }
    }

    static class BannerHolder extends RecyclerView.ViewHolder {
        final LinearLayout bar;

        BannerHolder(View v, LinearLayout bar) {
            super(v);
            this.bar = bar;
        }

        void bind(List<Models.Video> list, OnOpen open) {
            bar.removeAllViews();
            Context c = bar.getContext();
            for (Models.Video v : list) {
                FrameLayout card = new FrameLayout(c);
                card.setClipToOutline(true);
                card.setBackground(new GlassDrawable(Ui.dp(c, 18)));

                AspectImageView img = new AspectImageView(c);
                img.setRatio(0.62f);
                card.addView(img, new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                ImageLoader.load(img, v.cover);

                View scrim = new View(c);
                scrim.setBackground(new android.graphics.drawable.GradientDrawable(
                        android.graphics.drawable.GradientDrawable.Orientation.BOTTOM_TOP,
                        new int[]{0xF205070C, 0x8005070C, 0x0005070C}));
                FrameLayout.LayoutParams sp = new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(c, 90));
                sp.gravity = Gravity.BOTTOM;
                card.addView(scrim, sp);

                LinearLayout info = Ui.col(c);
                info.setPadding(Ui.dp(c, 12), 0, Ui.dp(c, 12), Ui.dp(c, 11));
                FrameLayout.LayoutParams ip = new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                ip.gravity = Gravity.BOTTOM;
                card.addView(info, ip);

                String cap = v.title == null ? "" : v.title.trim();
                if (cap.isEmpty()) {
                    cap = v.desc == null ? "热播推荐" : v.desc.replaceAll("\\s+", " ").trim();
                    if (cap.length() > 14) cap = cap.substring(0, 14) + "…";
                    if (cap.isEmpty()) cap = "热播推荐";
                }
                // 压在深色海报 scrim 上，恒用白色不随主题变
                TextView t = Ui.bold(c, cap, 14f, 0xFFFFFFFF);
                t.setMaxLines(1);
                t.setEllipsize(TextUtils.TruncateAt.END);
                info.addView(t);

                TextView sub = Ui.text(c, (v.score > 0 ? v.scoreText() + "分 · " : "")
                        + (v.tags == null || v.tags.isEmpty() ? "热播中" : v.tags), 11f, 0xFFD6DCE6);
                sub.setMaxLines(1);
                sub.setEllipsize(TextUtils.TruncateAt.END);
                LinearLayout.LayoutParams lp = Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.topMargin = Ui.dp(c, 4);
                info.addView(sub, lp);

                LinearLayout.LayoutParams cardLp = Ui.lp(Ui.dp(c, 248), ViewGroup.LayoutParams.WRAP_CONTENT);
                cardLp.rightMargin = Ui.dp(c, 10);
                card.setLayoutParams(cardLp);
                card.setOnClickListener(x -> open.onOpen(v));
                bar.addView(card);
            }
        }
    }
}
