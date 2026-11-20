package com.huangguo.pure;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 博客正文：正文内嵌视频（.post-video-player 占位）+ 图文 + 评论区。
 *
 * 之前「看不到视频」有两个原因：
 *   1) 取址用了 GET /api/posts/{id}/playback，这个接口只认 POST，GET 直接 404；
 *   2) render() 里 body.removeAllViews() 清空了父容器，但 playCard != null 让
 *      mountPlayCard() 提前 return，卡片被移除后再也不会回来。
 * 现在正文每次重画都从头构建（paint 幂等），视频卡按 data-video-index 与取址结果对齐。
 */
public class PostActivity extends AppCompatActivity {

    private static final String E_ID = "id";
    private static final String E_TITLE = "title";

    public static void open(Context c, long id, String title) {
        Intent i = new Intent(c, PostActivity.class);
        i.putExtra(E_ID, id);
        i.putExtra(E_TITLE, title);
        c.startActivity(i);
    }

    /** 正文里的视频挂载点：<div class="post-video-player" data-video-index="0"> */
    private static final Pattern VIDEO_DIV = Pattern.compile(
            "<div[^>]*class=\"[^\"]*post-video-player[^\"]*\"[^>]*>.*?</div>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern VIDEO_INDEX = Pattern.compile(
            "data-video-index\\s*=\\s*[\"']?(\\d+)", Pattern.CASE_INSENSITIVE);

    private long postId;
    private String title = "";
    private String coverUrl = "";

    private LinearLayout headBox;
    private LinearLayout contentBox;
    private LinearLayout commentSec;
    private TextView status;

    private Models.Post post;
    private boolean playbackLoaded = false;
    private boolean commentsBuilt = false;
    private boolean fav = false;
    private FrameLayout favBtn;
    private final Map<Integer, String> videoUrls = new HashMap<>();

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        postId = getIntent().getLongExtra(E_ID, 0);
        title = getIntent().getStringExtra(E_TITLE);
        if (title == null) title = "";

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Ui.C_BG);
        root.setClipChildren(false);
        setContentView(root);
        root.addView(new Aurora(this), new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout page = Ui.col(this);
        root.addView(page, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        buildTopBar(page, root);

        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        LinearLayout.LayoutParams svp = Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT, 0);
        svp.weight = 1f;
        page.addView(sv, svp);

        LinearLayout body = Ui.col(this);
        body.setPadding(Ui.dp(this, 16), 0, Ui.dp(this, 16), Ui.dp(this, 28));
        sv.addView(body, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        headBox = Ui.col(this);
        body.addView(headBox, Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        contentBox = Ui.col(this);
        body.addView(contentBox, Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        status = Ui.text(this, "加载中…", 13f, Ui.C_SUB);
        status.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams stp = Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 90));
        body.addView(status, stp);

        commentSec = Ui.col(this);
        LinearLayout.LayoutParams clp = Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.topMargin = Ui.dp(this, 6);
        body.addView(commentSec, clp);

        if (postId <= 0) {
            status.setText("缺少文章参数");
            return;
        }
        load();
        loadPlayback();
    }

    private void buildTopBar(LinearLayout page, View backdrop) {
        LinearLayout bar = Ui.row(this);
        bar.setPadding(Ui.dp(this, 14), Ui.dp(this, 10), Ui.dp(this, 14), Ui.dp(this, 6));

        // 返回键占位（实际按钮钉在 root 顶层，见下方 pinBackToRoot）
        View backSpacer = new View(this);
        bar.addView(backSpacer, Ui.lp(Ui.dp(this, 38), Ui.dp(this, 38)));
        pinBackToRoot((FrameLayout) backdrop);

        TextView tag = Ui.text(this, "博客", 9.5f, Ui.C_AMBER);
        tag.setGravity(Gravity.CENTER);
        tag.setPadding(Ui.dp(this, 8), Ui.dp(this, 3), Ui.dp(this, 8), Ui.dp(this, 3));
        tag.setBackground(Ui.stroke(Ui.accentFill(), Ui.accentStroke(), Ui.dp(this, 8), 1));
        LinearLayout.LayoutParams tp = Ui.lp(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        tp.leftMargin = Ui.dp(this, 12);
        bar.addView(tag, tp);

        TextView tip = Ui.text(this, "含视频与评论", 10f, Ui.C_DIM);
        LinearLayout.LayoutParams tipLp = Ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT);
        tipLp.weight = 1f;
        tip.setGravity(Gravity.END);
        bar.addView(tip, tipLp);

        // 更多按钮：下拉玻璃菜单（收藏 / 分享）
        favBtn = new FrameLayout(this);
        favBtn.setBackground(new GlassDrawable(Ui.dp(this, 20)));
        favBtn.setClickable(true);
        ImageView star = new ImageView(this);
        star.setTag("star");
        star.setImageResource(R.drawable.ic_more);
        star.setColorFilter(Ui.C_TEXT);
        favBtn.addView(star, new FrameLayout.LayoutParams(
                Ui.dp(this, 18), Ui.dp(this, 18), Gravity.CENTER));
        favBtn.setOnClickListener(v -> showMoreMenu());
        LinearLayout.LayoutParams fp = Ui.lp(Ui.dp(this, 38), Ui.dp(this, 38));
        fp.leftMargin = Ui.dp(this, 10);
        bar.addView(favBtn, fp);
        fav = Store.get(this).isFavorite(Store.T_POST, postId);

        page.addView(bar);
    }

    /** 返回键钉在 root 顶层（Gravity.TOP|START），不随 ScrollView 内容滚动而消失 */
    private void pinBackToRoot(FrameLayout root) {
        FrameLayout back = new FrameLayout(this);
        back.setBackground(Ui.thickGlass(this, Ui.dp(this, 20)));
        back.setClickable(true);
        ImageView ic = new ImageView(this);
        ic.setImageResource(R.drawable.ic_back);
        ic.setColorFilter(Ui.C_TEXT);
        back.addView(ic, new FrameLayout.LayoutParams(
                Ui.dp(this, 18), Ui.dp(this, 18), Gravity.CENTER));
        back.setOnClickListener(v -> finish());
        FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(
                Ui.dp(this, 38), Ui.dp(this, 38), Gravity.TOP | Gravity.START);
        bp.leftMargin = Ui.dp(this, 14);
        bp.topMargin = Ui.dp(this, 10);
        root.addView(back, bp);
    }

    /** 更多菜单：收藏（随状态切换文案）+ 分享（复制站点链接） */
    private void showMoreMenu() {
        fav = Store.get(this).isFavorite(Store.T_POST, postId);
        GlassMenu.show(favBtn, GlassMenu.items(
                new GlassMenu.Item(fav ? "★" : "☆",
                        fav ? "取消收藏" : "收藏", this::toggleFav),
                new GlassMenu.Item("⤴", "分享",
                        () -> Share.copy(this, Share.postUrl(postId)))));
    }

    private void toggleFav() {
        boolean now = Store.get(this).toggleItem(Store.T_POST, postId,
                title, coverUrl, "");
        fav = now;
        android.widget.Toast.makeText(this, now ? "已加入收藏" : "已取消收藏",
                android.widget.Toast.LENGTH_SHORT).show();
    }

    // ------------------------------------------------------------------ 数据

    private void loadPlayback() {
        Api.postPlayback(postId, new Api.Cb<List<Models.PostVideo>>() {
            @Override
            public void ok(List<Models.PostVideo> list) {
                videoUrls.clear();
                for (Models.PostVideo pv : list) videoUrls.put(pv.index, pv.url);
                playbackLoaded = true;
                paint();
            }

            @Override
            public void err(String msg) {
                playbackLoaded = true;
                paint();
            }
        });
    }

    private void paintFav() {
        // 更多键恒为 ic_more；收藏状态在更多菜单里体现，不再把更多键换成星形
    }

    private void load() {
        Api.post(postId, new Api.Cb<Models.Post>() {
            @Override
            public void ok(Models.Post p) {
                post = p;
                if (!p.title.isEmpty()) title = p.title;
                coverUrl = p.cover;
                // 阅读历史：进正文就记一条，标题/封面取正文真实值
                Store.get(PostActivity.this).recordRead(Store.T_POST, postId,
                        "", title, coverUrl);
                paint();
            }

            @Override
            public void err(String msg) {
                status.setText("加载失败：" + msg + "\n点此重试");
                status.setOnClickListener(v -> {
                    status.setText("加载中…");
                    status.setOnClickListener(null);
                    load();
                });
            }
        });
    }

    // ------------------------------------------------------------------ 绘制

    /** 幂等重画：正文每次从 post.content 重新构建，视频卡与评论不会因为重画而消失 */
    private void paint() {
        if (post == null) return;
        status.setVisibility(View.GONE);

        headBox.removeAllViews();
        contentBox.removeAllViews();

        boolean hasVideo = !videoUrls.isEmpty() || countVideoSlots(post.content) > 0;
        if (!post.cover.isEmpty() && !hasVideo) {
            FrameLayout hero = new FrameLayout(this);
            hero.setClipToOutline(true);
            hero.setBackground(new GlassDrawable(Ui.dp(this, 16)));
            AspectImageView cover = new AspectImageView(this);
            cover.setRatio(0.56f);
            hero.addView(cover, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            ImageLoader.load(cover, post.cover);
            LinearLayout.LayoutParams hp = Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            hp.bottomMargin = Ui.dp(this, 14);
            headBox.addView(hero, hp);
        }

        TextView t = Ui.bold(this, title, 19f, Ui.C_TEXT);
        t.setLineSpacing(Ui.dp(this, 5), 1f);
        headBox.addView(t);

        LinearLayout meta = Ui.row(this);
        LinearLayout.LayoutParams mp = Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        mp.topMargin = Ui.dp(this, 10);
        mp.bottomMargin = Ui.dp(this, 12);
        headBox.addView(meta, mp);

        if (!post.authorName.isEmpty()) {
            TextView avatar = Ui.bold(this, firstChar(post.authorName), 10f, Ui.onAmber());
            avatar.setGravity(Gravity.CENTER);
            avatar.setBackground(Ui.round(Ui.C_AMBER, Ui.dp(this, 12)));
            meta.addView(avatar, Ui.lp(Ui.dp(this, 24), Ui.dp(this, 24)));

            TextView name = Ui.text(this, post.authorName, 11.5f, Ui.subText());
            LinearLayout.LayoutParams nlp = Ui.lp(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            nlp.leftMargin = Ui.dp(this, 7);
            meta.addView(name, nlp);
        }

        View spacer = new View(this);
        LinearLayout.LayoutParams slp = Ui.lp(0, 1);
        slp.weight = 1f;
        meta.addView(spacer, slp);

        StringBuilder sb = new StringBuilder();
        if (!post.time.isEmpty()) sb.append(post.time);
        if (post.hot > 0) {
            if (sb.length() > 0) sb.append(" · ");
            sb.append("热度 ").append(Ui.shortNum(post.hot));
        }
        if (post.likeCount > 0) {
            if (sb.length() > 0) sb.append(" · ");
            sb.append("赞 ").append(Ui.shortNum(post.likeCount));
        }
        meta.addView(Ui.text(this, sb.toString(), 10.5f, Ui.C_DIM));

        if (!post.tags.isEmpty()) {
            TextView tags = Ui.text(this, post.tags, 10.5f, Ui.C_AMBER);
            tags.setLineSpacing(Ui.dp(this, 4), 1f);
            LinearLayout.LayoutParams tgp = Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            tgp.bottomMargin = Ui.dp(this, 12);
            headBox.addView(tags, tgp);
        }

        headBox.addView(line(dp(14)));

        renderContent(post.content);

        if (!commentsBuilt) {
            commentsBuilt = true;
            buildComments();
        }
    }

    /** 正文按视频挂载点切段：文字走 HtmlRich，占位点换成视频卡 */
    private void renderContent(String html) {
        String content = html == null ? "" : html;
        int cursor = 0;
        Matcher m = VIDEO_DIV.matcher(content);
        while (m.find()) {
            addText(content.substring(cursor, m.start()));
            int idx = indexOf(m.group());
            contentBox.addView(videoCard(idx));
            cursor = m.end();
        }
        if (cursor < content.length()) addText(content.substring(cursor));

        if (countVideoSlots(content) == 0 && !videoUrls.isEmpty()) {
            // 正文没有挂载点，但接口确实给了视频：按 index 顺序补在正文前
            LinearLayout extra = Ui.col(this);
            contentBox.addView(extra, 0);
            for (Integer idx : new java.util.TreeSet<>(videoUrls.keySet())) {
                extra.addView(videoCard(idx));
            }
        }
    }

    private void addText(String html) {
        if (html == null || html.trim().isEmpty()) return;
        LinearLayout seg = Ui.col(this);
        HtmlRich.fill(seg, html);
        contentBox.addView(seg, Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private int countVideoSlots(String html) {
        if (html == null || html.isEmpty()) return 0;
        Matcher m = VIDEO_DIV.matcher(html);
        int n = 0;
        while (m.find()) n++;
        return n;
    }

    private int indexOf(String divTag) {
        Matcher m = VIDEO_INDEX.matcher(divTag);
        if (m.find()) {
            try {
                return Integer.parseInt(m.group(1));
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        return 0;
    }

    /**
     * 16:9 视频卡。取址没回来时显示「视频加载中」，取址失败显示「视频暂不可用」。
     * 点击直接交给短剧那套播放器（PlayerActivity.openStream），不另起一套。
     */
    private View videoCard(final int index) {
        final String url = videoUrls.get(index);

        FrameLayout card = new FrameLayout(this);
        card.setClipToOutline(true);
        card.setBackground(new GlassDrawable(Ui.dp(this, 16)));

        AspectImageView cover = new AspectImageView(this);
        cover.setRatio(0.5625f);
        card.addView(cover, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        ImageLoader.load(cover, coverUrl);

        View scrim = new View(this);
        scrim.setBackgroundColor(Ui.darkGlass ? 0x4D000000 : 0x1AE8EDF5);
        card.addView(scrim, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        FrameLayout btn = new FrameLayout(this);
        btn.setBackground(Ui.panelGlass(this, Ui.dp(this, 30)));
        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.ic_play);
        icon.setColorFilter(url == null ? Ui.C_DIM : Ui.C_AMBER);
        btn.addView(icon, new FrameLayout.LayoutParams(
                Ui.dp(this, 26), Ui.dp(this, 26), Gravity.CENTER));
        card.addView(btn, new FrameLayout.LayoutParams(
                Ui.dp(this, 60), Ui.dp(this, 60), Gravity.CENTER));

        String label = url != null ? ("播放视频 " + (index + 1) + " · 与短剧共用播放器")
                : (playbackLoaded ? "视频暂不可用" : "视频加载中…");
        TextView tip = Ui.text(this, label, 11f, Ui.bodyText());
        tip.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams tlp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.gravity = Gravity.BOTTOM;
        tlp.bottomMargin = Ui.dp(this, 10);
        card.addView(tip, tlp);

        card.setOnClickListener(v -> {
            if (url == null) {
                Toast.makeText(this, playbackLoaded ? "这条视频暂不可用" : "还在取地址…",
                        Toast.LENGTH_SHORT).show();
                return;
            }
            PlayerActivity.openStream(this, url, title, coverUrl);
        });

        LinearLayout.LayoutParams blp = Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT,
                Math.round(getResources().getDisplayMetrics().widthPixels * 9f / 16f));
        blp.bottomMargin = Ui.dp(this, 14);
        card.setLayoutParams(blp);
        return card;
    }

    private View line(int bottomMargin) {
        View line = new View(this);
        line.setBackgroundColor(Ui.darkGlass ? 0x1AFFFFFF : 0x1A1A2332);
        LinearLayout.LayoutParams lp = Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT, 1);
        lp.bottomMargin = bottomMargin;
        line.setLayoutParams(lp);
        return line;
    }

    private int dp(float v) {
        return Ui.dp(this, v);
    }

    // ------------------------------------------------------------------ 评论

    private void buildComments() {
        View sep = line(dp(18));
        commentSec.addView(sep);

        CommentBox box = new CommentBox(this, "评论", new CommentBox.Source() {
            @Override
            public void load(int page, Api.Cb<List<Models.Comment>> cb) {
                Api.postComments(postId, page, cb);
            }

            @Override
            public void send(String text, Api.Cb<String> cb) {
                Api.postPostComment(postId, text, cb);
            }
        }, () -> AuthDialog.show(this, false, () -> { }), Store.LIKE_PCOMMENT);
        commentSec.addView(box, Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        box.reload();
    }

    private static String firstChar(String s) {
        if (s == null || s.isEmpty()) return "·";
        return s.substring(0, 1);
    }
}
