package com.huangguo.pure;

import android.content.Context;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

/** 专题：精选合集网格 */
public class TopicPage extends FrameLayout {

    private final RecyclerView rv;
    private final TextView status;
    private final List<Models.Topic> data = new ArrayList<>();
    private final Adapter adapter = new Adapter();

    public TopicPage(Context c) {
        this(c, false);
    }

    /** @param embedded true 时去掉自家标题栏，供「分类」页内嵌使用 */
    public TopicPage(Context c, boolean embedded) {
        super(c);
        LinearLayout root = Ui.col(c);
        addView(root, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        if (!embedded) {
            root.addView(Chrome.titleBar(c, "专题", "精选合集"));

            TextView note = Ui.text(c, "按主题成套的剧集合集，点开即看", 11.5f, Ui.C_DIM);
            LinearLayout.LayoutParams nlp = Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            nlp.leftMargin = Ui.dp(c, 18);
            nlp.bottomMargin = Ui.dp(c, 8);
            root.addView(note, nlp);
        }

        FrameLayout box = new FrameLayout(c);
        root.addView(box, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        rv = new RecyclerView(c);
        rv.setLayoutManager(new GridLayoutManager(c, 2));
        rv.setAdapter(adapter);
        rv.setClipToPadding(false);
        rv.setPadding(Ui.dp(c, 12), Ui.dp(c, 4), Ui.dp(c, 12), Ui.dp(c, 24));
        rv.setOverScrollMode(OVER_SCROLL_NEVER);
        box.addView(rv, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        status = Ui.text(c, "加载中…", 13f, Ui.C_SUB);
        status.setGravity(Gravity.CENTER);
        box.addView(status, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER));

        load();
    }

    public void refresh() {
        if (data.isEmpty()) load();
    }

    private void load() {
        status.setVisibility(VISIBLE);
        status.setText("加载中…");
        status.setOnClickListener(null);
        Api.topics(new Api.Cb<List<Models.Topic>>() {
            @Override
            public void ok(List<Models.Topic> list) {
                data.clear();
                data.addAll(list);
                adapter.notifyDataSetChanged();
                status.setVisibility(data.isEmpty() ? VISIBLE : GONE);
                if (data.isEmpty()) status.setText("暂无专题");
            }

            @Override
            public void err(String msg) {
                status.setVisibility(VISIBLE);
                status.setText("加载失败：" + msg + "\n点此重试");
                status.setOnClickListener(v -> load());
            }
        });
    }

    private class Card extends FrameLayout {
        final AspectImageView cover;
        final TextView title;
        final TextView sub;
        final TextView count;

        Card(Context c) {
            super(c);
            setClipToOutline(true);
            setBackground(new GlassDrawable(Ui.dp(c, 18)));

            cover = new AspectImageView(c);
            cover.setRatio(0.66f);
            addView(cover, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            View scrim = new View(c);
            scrim.setBackground(new android.graphics.drawable.GradientDrawable(
                    android.graphics.drawable.GradientDrawable.Orientation.BOTTOM_TOP,
                    new int[]{0xE605070C, 0x9005070C, 0x0005070C}));
            FrameLayout.LayoutParams sp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(c, 96));
            sp.gravity = Gravity.BOTTOM;
            addView(scrim, sp);

            LinearLayout info = Ui.col(c);
            info.setPadding(Ui.dp(c, 12), 0, Ui.dp(c, 12), Ui.dp(c, 11));
            FrameLayout.LayoutParams ip = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            ip.gravity = Gravity.BOTTOM;
            addView(info, ip);

            title = Ui.bold(c, "", 13.5f, 0xFFFFFFFF);
            title.setMaxLines(2);
            title.setEllipsize(TextUtils.TruncateAt.END);
            info.addView(title);

            sub = Ui.text(c, "", 10.5f, 0xFFDCE3EE);
            sub.setMaxLines(2);
            sub.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams slp = Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            slp.topMargin = Ui.dp(c, 4);
            info.addView(sub, slp);

            count = Ui.text(c, "", 10f, Ui.C_AMBER);
            count.setGravity(Gravity.CENTER);
            count.setPadding(Ui.dp(c, 8), Ui.dp(c, 3), Ui.dp(c, 8), Ui.dp(c, 3));
            count.setBackground(Ui.stroke(Ui.accentFill(), Ui.accentStroke(), Ui.dp(c, 9), 1));
            FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            clp.gravity = Gravity.TOP | Gravity.END;
            clp.topMargin = Ui.dp(c, 9);
            clp.rightMargin = Ui.dp(c, 9);
            addView(count, clp);
        }
    }

    private class Adapter extends RecyclerView.Adapter<Adapter.H> {

        class H extends RecyclerView.ViewHolder {
            final Card card;

            H(Card card) {
                super(card);
                this.card = card;
            }
        }

        @NonNull
        @Override
        public H onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            Context c = parent.getContext();
            Card card = new Card(c);
            RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMargins(Ui.dp(c, 5), Ui.dp(c, 5), Ui.dp(c, 5), Ui.dp(c, 5));
            card.setLayoutParams(lp);
            return new H(card);
        }

        @Override
        public void onBindViewHolder(@NonNull H h, int position) {
            Models.Topic t = data.get(position);
            h.card.title.setText(t.title);
            h.card.sub.setText(t.intro);
            h.card.count.setText(t.videoCount + " 部");
            ImageLoader.load(h.card.cover, t.cover);
            h.card.setOnClickListener(v -> TopicActivity.open(getContext(), t.slug, t.title));
        }

        @Override
        public int getItemCount() {
            return data.size();
        }
    }
}
