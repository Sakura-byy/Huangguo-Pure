package com.huangguo.pure;

import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 剧集详情：封面头图 + 评分 + 剧情 + 选集（含批量下载）+ 固定播放入口。
 *
 * 下载：底部分享右侧的下载键进入「选集多选」模式——chips 可点选、可手指划过多选，
 * 选集标题右上角出现「全选 / 取消 / 确定下载」；缓存完成的集在 chip 右上角点一颗琥珀圆点。
 */
public class DetailActivity extends AppCompatActivity {

    private static final String EXTRA_ID = "id";
    private static final String EXTRA_EP = "ep";

    public static void open(Context c, long id) {
        open(c, id, 1);
    }

    /** 从播放器跳过来时带上当前集，评论区直接定位到那一集 */
    public static void open(Context c, long id, int ep) {
        Intent i = new Intent(c, DetailActivity.class);
        i.putExtra(EXTRA_ID, id);
        i.putExtra(EXTRA_EP, ep);
        c.startActivity(i);
    }

    private long videoId;
    private Models.Detail detail;
    private int selectedEp = 1;
    private GridLayout epGrid;
    private LinearLayout bottomBar;
    private TextView playLabel;
    private android.widget.ImageView favBtn;
    private boolean favorite = false;

    /** 批量下载：选择模式 / 已勾选集 / 已缓存集 */
    private boolean pickMode = false;
    private final Set<Integer> picked = new LinkedHashSet<>();
    private Set<Integer> cachedEps = new LinkedHashSet<>();
    private boolean downloading = false;
    private int dlDone = 0, dlTotal = 0;
    private TextView epTitleView;
    private LinearLayout epActions;
    private TextView dlStatus;

    @Override
    protected void onCreate(android.os.Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        videoId = getIntent().getLongExtra(EXTRA_ID, 0);
        selectedEp = Math.max(1, getIntent().getIntExtra(EXTRA_EP, 1));

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Ui.C_BG);
        setContentView(root);
        root.addView(new Aurora(this), new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout content = Ui.col(this);
        ScrollView sv = new ScrollView(this);
        scrollSource = sv;
        sv.setFillViewport(true);
        sv.addView(content);
        root.addView(sv, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        buildTopBar(root, content);
        buildBottomBar(root, sv);

        if (videoId <= 0) {
            content.addView(Ui.text(this, "缺少剧集参数", 13f, Ui.C_SUB));
            return;
        }
        load(content);
    }

    /** 返回键：钉在 root 左上角（不随内容滚动） */
    private void buildTopBar(FrameLayout root, LinearLayout content) {
        // 内容让出顶栏高度
        content.setPadding(0, Ui.dp(this, 54), 0, 0);

        FrameLayout backWrap = new FrameLayout(this);
        backWrap.setBackground(Ui.thickGlass(this, Ui.dp(this, 20)));
        backWrap.setClickable(true);
        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.ic_back);
        icon.setColorFilter(Ui.C_TEXT);
        FrameLayout.LayoutParams ip = new FrameLayout.LayoutParams(
                Ui.dp(this, 18), Ui.dp(this, 18), Gravity.CENTER);
        backWrap.addView(icon, ip);
        backWrap.setOnClickListener(v -> finish());
        FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(
                Ui.dp(this, 38), Ui.dp(this, 38), Gravity.TOP | Gravity.START);
        bp.leftMargin = Ui.dp(this, 14);
        // 顶栏返回键要落在状态栏下方，否则沉浸式一划出状态栏就压住按钮
        int sbh;
        int sbId = getResources().getIdentifier("status_bar_height", "dimen", "android");
        sbh = sbId > 0 ? getResources().getDimensionPixelSize(sbId) : Ui.dp(this, 24);
        bp.topMargin = Ui.dp(this, 10) + sbh;
        root.addView(backWrap, bp);
    }

    private void buildBottomBar(FrameLayout root, View source) {
        bottomBar = Ui.col(this);
        bottomBar.setPadding(Ui.dp(this, 14), Ui.dp(this, 10), Ui.dp(this, 14), Ui.dp(this, 16));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM);

        LinearLayout bar = Ui.row(this);

        // 收藏 / 分享 / 下载：和 Dock 同源的液态玻璃（采样自己背后那 50dp 的滚动内容）
        GlassSurface favWrap = new GlassSurface(this, 16f,
                Ui.darkGlass ? 0x8A121522 : 0xA6FFFFFF, 8f);
        if (source != null) favWrap.bind(source);
        favWrap.setClickable(true);
        favBtn = new android.widget.ImageView(this);
        favBtn.setImageResource(R.drawable.ic_star);
        favWrap.addView(favBtn, new FrameLayout.LayoutParams(
                Ui.dp(this, 22), Ui.dp(this, 22), Gravity.CENTER));
        favWrap.setOnClickListener(v -> {
            if (detail == null) return;
            if (!Auth.loggedIn()) {
                AuthDialog.show(this, false, () -> {
                });
                return;
            }
            final boolean target = !favorite;
            favorite = Store.get(this).toggleFavorite(detail.info);
            syncFav();
            // 云端收藏：POST / DELETE /api/videos/{id}/collect?token=
            Api.collectVideo(videoId, target, new Api.Cb<org.json.JSONObject>() {
                @Override
                public void ok(org.json.JSONObject data) {
                    boolean collected = data.optBoolean("collected", target);
                    if (collected != target) {
                        favorite = collected;
                        syncFav();
                    }
                }

                @Override
                public void err(String msg) {
                    android.widget.Toast.makeText(DetailActivity.this,
                            "云端收藏失败：" + msg, android.widget.Toast.LENGTH_SHORT).show();
                }
            });
        });
        bar.addView(favWrap, Ui.lp(Ui.dp(this, 50), Ui.dp(this, 50)));

        // 分享按钮：复制本页在站点上的链接
        GlassSurface shareWrap = new GlassSurface(this, 16f,
                Ui.darkGlass ? 0x8A121522 : 0xA6FFFFFF, 8f);
        if (source != null) shareWrap.bind(source);
        shareWrap.setClickable(true);
        ImageView shareIc = new ImageView(this);
        shareIc.setImageResource(R.drawable.ic_share);
        shareIc.setColorFilter(Ui.C_TEXT);
        shareWrap.addView(shareIc, new FrameLayout.LayoutParams(
                Ui.dp(this, 22), Ui.dp(this, 22), Gravity.CENTER));
        shareWrap.setOnClickListener(v -> Share.copy(this, Share.videoUrl(videoId)));
        LinearLayout.LayoutParams shlp = Ui.lp(Ui.dp(this, 50), Ui.dp(this, 50));
        shlp.leftMargin = Ui.dp(this, 10);
        bar.addView(shareWrap, shlp);

        // 下载按钮：进入选集多选下载
        GlassSurface dlWrap = new GlassSurface(this, 16f,
                Ui.darkGlass ? 0x8A121522 : 0xA6FFFFFF, 8f);
        if (source != null) dlWrap.bind(source);
        dlWrap.setClickable(true);
        ImageView dlIc = new ImageView(this);
        dlIc.setImageResource(R.drawable.ic_download);
        dlIc.setColorFilter(Ui.C_TEXT);
        dlWrap.addView(dlIc, new FrameLayout.LayoutParams(
                Ui.dp(this, 22), Ui.dp(this, 22), Gravity.CENTER));
        dlWrap.setOnClickListener(v -> togglePickMode());
        LinearLayout.LayoutParams dllp = Ui.lp(Ui.dp(this, 50), Ui.dp(this, 50));
        dllp.leftMargin = Ui.dp(this, 10);
        bar.addView(dlWrap, dllp);

        LinearLayout btn = Ui.row(this);
        btn.setGravity(Gravity.CENTER);
        btn.setBackground(Ui.round(Ui.C_AMBER, Ui.dp(this, 16)));
        btn.setClickable(true);
        playLabel = Ui.bold(this, "立即播放", 14f, Ui.onAmber());
        btn.addView(playLabel);
        btn.setOnClickListener(v -> {
            if (detail != null) {
                PlayerActivity.open(this, videoId, detail.info.title, selectedEp);
            }
        });
        LinearLayout.LayoutParams blp = Ui.lp(0, Ui.dp(this, 50));
        blp.weight = 1f;
        blp.leftMargin = Ui.dp(this, 10);
        bar.addView(btn, blp);

        bottomBar.addView(bar, Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 50)));

        bottomBar.setVisibility(View.GONE);
        root.addView(bottomBar, lp);
    }

    private void load(final LinearLayout content) {
        final TextView loading = Ui.text(this, "加载中…", 13f, Ui.C_SUB);
        loading.setGravity(Gravity.CENTER);
        loading.setPadding(0, Ui.dp(this, 40), 0, 0);
        content.addView(loading);

        Api.detailRaw(videoId, new Api.Cb<org.json.JSONObject>() {
            @Override
            public void ok(org.json.JSONObject raw) {
                // 成功一次就落一份快照：已缓存的剧集断网也能打开详情
                Store.get(DetailActivity.this).saveDetailSnapshot(videoId, raw.toString());
                Models.Detail d = Models.detail(raw);
                detail = d;
                content.removeView(loading);
                // 缓存集合必须在 render 之前就位：选集 chips 建的时候就要画缓存勾
                cachedEps = Store.get(DetailActivity.this).cachedEps(videoId);
                render(content);
                checkCacheAlive();
                // 先亮出本地记过的星，云端结果回来再覆盖
                myStars = Store.get(DetailActivity.this).ratingOf(videoId);
                Api.rating(videoId, new Api.Cb<Models.Rating>() {
                    @Override
                    public void ok(Models.Rating r) {
                        if (r.my > 0) {
                            myStars = r.my;
                            Store.get(DetailActivity.this).setRating(videoId, r.my);
                        }
                        ratingCount = r.cnt;
                        if (r.score > 0) detail.info.score = r.score;
                        paintStars(myStars);
                    }

                    @Override
                    public void err(String msg) {
                    }
                });
                bottomBar.setVisibility(View.VISIBLE);
                updatePlayLabel();
                favorite = Store.get(DetailActivity.this).isFavorite(Store.T_VIDEO, videoId);
                syncFav();
                // 详情拿到最新集数：刷新收藏记录里的 ep_count（不改 seen_ep，保留「有更新」提示）
                Store.get(DetailActivity.this).refreshFavoriteEpCount(videoId,
                        d.info.epCount > 0 ? d.info.epCount : d.episodes.size(), false);
            }

            @Override
            public void err(String msg) {
                // 断网 / 站点抽风：有本地快照就离线渲染，不再满屏报错
                String snap = Store.get(DetailActivity.this).detailSnapshot(videoId);
                if (snap == null) {
                    // 没快照但缓存过：用本地缓存记录拼一份最小详情，保证离线能打开
                    Models.Detail offline = Store.get(DetailActivity.this).offlineDetail(videoId);
                    if (offline != null) {
                        detail = offline;
                        content.removeView(loading);
                        cachedEps = Store.get(DetailActivity.this).cachedEps(videoId);
                        render(content);
                        checkCacheAlive();
                        bottomBar.setVisibility(View.VISIBLE);
                        updatePlayLabel();
                        favorite = Store.get(DetailActivity.this)
                                .isFavorite(Store.T_VIDEO, videoId);
                        syncFav();
                        Toast.makeText(DetailActivity.this,
                                "离线模式：仅显示已缓存的集数", Toast.LENGTH_SHORT).show();
                        return;
                    }
                }
                if (snap != null) {
                    try {
                        Models.Detail d = Models.detail(new org.json.JSONObject(snap));
                        detail = d;
                        content.removeView(loading);
                        cachedEps = Store.get(DetailActivity.this).cachedEps(videoId);
                        render(content);
                        checkCacheAlive();
                        bottomBar.setVisibility(View.VISIBLE);
                        updatePlayLabel();
                        favorite = Store.get(DetailActivity.this)
                                .isFavorite(Store.T_VIDEO, videoId);
                        syncFav();
                        Toast.makeText(DetailActivity.this, "离线模式：显示已缓存的详情",
                                Toast.LENGTH_SHORT).show();
                        return;
                    } catch (Exception ignored) {
                        // 快照坏了 → 走原来的报错分支
                    }
                }
                loading.setText("加载失败：" + msg + "\n点此重试");
                loading.setOnClickListener(v -> {
                    content.removeView(loading);
                    load(content);
                });
            }
        });
    }

    private void saveCover(String url, String name) {
        android.widget.Toast.makeText(this, "正在保存封面…", android.widget.Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                android.graphics.Bitmap bmp = Api.fetchBitmap(url);
                String path = MediaSave.saveImage(this,
                        bmp, "hg_" + MediaSave.sanitize(name) + ".jpg");
                runOnUiThread(() -> android.widget.Toast.makeText(this,
                        "已保存：" + path, android.widget.Toast.LENGTH_LONG).show());
            } catch (Exception e) {
                runOnUiThread(() -> android.widget.Toast.makeText(this,
                        "保存失败：" + e.getMessage(), android.widget.Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    private void render(LinearLayout content) {
        Models.Video v = detail.info;

        FrameLayout hero = new FrameLayout(this);
        hero.setClipToOutline(true);
        hero.setBackground(new GlassDrawable(Ui.dp(this, 20)));
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 236));
        hp.leftMargin = Ui.dp(this, 14);
        hp.rightMargin = Ui.dp(this, 14);
        content.addView(hero, hp);

        AspectImageView cover = new AspectImageView(this);
        cover.setRatio(0.75f);
        hero.addView(cover, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        ImageLoader.load(cover, v.cover);
        // 封面可点开大图，长按直接存相册
        cover.setClickable(true);
        cover.setOnClickListener(vv -> ImageViewerActivity.open(this, v.cover, v.title));
        cover.setOnLongClickListener(vv -> {
            saveCover(v.cover, v.title);
            return true;
        });

        // 封面 scrim 恒为深色渐变（压在图片上让白色标题可读，与 App 明暗主题无关）
        View scrim = new View(this);
        scrim.setBackground(new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.BOTTOM_TOP,
                new int[]{0xE605070C, 0x8005070C, 0x0005070C}));
        hero.addView(scrim, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 130), Gravity.BOTTOM));

        LinearLayout info = Ui.col(this);
        info.setPadding(Ui.dp(this, 14), 0, Ui.dp(this, 14), Ui.dp(this, 12));
        hero.addView(info, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM));

        // 标题/元信息压在深色 scrim 上，恒用白色，不随 App 主题变化
        info.addView(Ui.bold(this, v.title, 19f, 0xFFFFFFFF));

        StringBuilder meta = new StringBuilder();
        // 评分不再塞进 meta（大评分块单独一块），这里只留集数 / 标签 / 发布时间
        if (v.epCount > 0) {
            if (meta.length() > 0) meta.append("   ·   ");
            meta.append(v.epText());
        }
        if (v.tags != null && !v.tags.isEmpty()) {
            if (meta.length() > 0) meta.append("   ·   ");
            meta.append(v.tags);
        }
        if (v.createdAt != null && v.createdAt.length() >= 10) {
            meta.append("   ·   发布于 ").append(v.createdAt.substring(0, 10));
        }
        TextView metaTv = Ui.text(this, meta.toString(), 11.5f, 0xFFDCE3EE);
        LinearLayout.LayoutParams mp = Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        mp.topMargin = Ui.dp(this, 6);
        info.addView(metaTv, mp);

        // 封面下的大评分块：左分数 + 右 5 星（可拖动）
        buildRatingBlock(content);

        if (v.desc != null && !v.desc.isEmpty()) {
            LinearLayout sec = Ui.col(this);
            sec.setPadding(Ui.dp(this, 16), Ui.dp(this, 16), Ui.dp(this, 16), 0);
            content.addView(sec);
            sec.addView(Ui.bold(this, "剧情简介", 13.5f, Ui.C_TEXT));

            final TextView desc = Ui.text(this, v.desc, 12.5f, Ui.weakText());
            desc.setLineSpacing(Ui.dp(this, 3), 1f);
            desc.setMaxLines(4);
            desc.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams dp = Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            dp.topMargin = Ui.dp(this, 7);
            sec.addView(desc, dp);

            final TextView more = Ui.text(this, "展开全部", 11.5f, Ui.C_AMBER);
            final boolean[] open = {false};
            more.setOnClickListener(x -> {
                open[0] = !open[0];
                desc.setMaxLines(open[0] ? 200 : 4);
                desc.setEllipsize(open[0] ? null : TextUtils.TruncateAt.END);
                more.setText(open[0] ? "收起" : "展开全部");
            });
            LinearLayout.LayoutParams mm = Ui.lp(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            mm.topMargin = Ui.dp(this, 6);
            sec.addView(more, mm);
        }

        LinearLayout epSec = Ui.col(this);
        epSec.setPadding(Ui.dp(this, 16), Ui.dp(this, 18), Ui.dp(this, 16), Ui.dp(this, 8));
        content.addView(epSec);

        // 标题行：左「选集 · 全 N 集」，右侧是下载模式的操作条（全选 / 取消 / 确定下载）
        LinearLayout titleRow = Ui.row(this);
        epSec.addView(titleRow, Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        epTitleView = Ui.bold(this, epTitleText(), 13.5f, Ui.C_TEXT);
        LinearLayout.LayoutParams tp = Ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT);
        tp.weight = 1f;
        titleRow.addView(epTitleView, tp);

        epActions = Ui.row(this);
        titleRow.addView(epActions);

        epGrid = new GridLayout(this);
        epGrid.setColumnCount(5);
        epGrid.setOnTouchListener((view, ev) -> onGridTouch(ev));
        LinearLayout.LayoutParams gp = Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        gp.topMargin = Ui.dp(this, 10);
        epSec.addView(epGrid, gp);
        renderEpisodes();
        paintEpActions();

        renderComments(content);
    }

    private String epTitleText() {
        String t = detail.info.epText();
        int n = detail.episodes.size();
        if (t.isEmpty()) return "选集 · " + n + " 集";
        return "选集 · " + t;
    }

    /**
     * 评分块（原始样式）：左侧「★ 9.4 分 + 点右侧星星打分」，右侧五颗星 ☆/★。
     * 星星可以横向拖动着打分（拖动只改外观，松手才提交），没有外框、没有玻璃底板。
     */
    private void buildRatingBlock(LinearLayout content) {
        LinearLayout row = Ui.row(this);
        LinearLayout.LayoutParams rlp = Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        rlp.leftMargin = Ui.dp(this, 18);
        rlp.rightMargin = Ui.dp(this, 16);
        rlp.topMargin = Ui.dp(this, 14);
        content.addView(row, rlp);

        LinearLayout left = Ui.col(this);
        LinearLayout.LayoutParams llp = Ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT);
        llp.weight = 1f;
        row.addView(left, llp);

        ratingScoreView = Ui.bold(this, ratingScoreText(), 17f, Ui.C_GOLD);
        left.addView(ratingScoreView);

        ratingSubView = Ui.text(this, ratingSubText(), 10.5f, Ui.C_DIM);
        LinearLayout.LayoutParams sp = Ui.lp(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        sp.topMargin = Ui.dp(this, 3);
        left.addView(ratingSubView, sp);

        // 右侧五颗星：整排吃触摸，按落点 x 定位第几颗，滑过去即连续打分
        LinearLayout stars = Ui.row(this);
        for (int i = 0; i < 5; i++) {
            TextView st = Ui.text(this, "☆", 24f, Ui.C_DIM);
            st.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams starLp = Ui.lp(Ui.dp(this, 30), Ui.dp(this, 38));
            stars.addView(st, starLp);
            starViews[i] = st;
        }
        row.addView(stars, Ui.lp(Ui.dp(this, 5 * 30), Ui.dp(this, 38)));
        paintStars(myStars);

        stars.setOnTouchListener((view, ev) -> {
            switch (ev.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    stars.getParent().requestDisallowInterceptTouchEvent(true);
                    paintStars(starAt(ev.getX(), view.getWidth()), true);
                    return true;
                case MotionEvent.ACTION_MOVE:
                    paintStars(starAt(ev.getX(), view.getWidth()), true);
                    return true;
                case MotionEvent.ACTION_UP: {
                    stars.getParent().requestDisallowInterceptTouchEvent(false);
                    int n = starAt(ev.getX(), view.getWidth());
                    if (n > 0) commitStars(n);
                    else paintStars(myStars);
                    return true;
                }
                case MotionEvent.ACTION_CANCEL:
                    stars.getParent().requestDisallowInterceptTouchEvent(false);
                    paintStars(myStars);
                    return true;
                default:
                    return false;
            }
        });
    }

    /** 落点在第几颗星上（1..5） */
    private int starAt(float x, int width) {
        if (width <= 0) return 0;
        float step = width / 5f;
        int idx = (int) (x / step) + 1;
        return Math.max(1, Math.min(5, idx));
    }

    private void paintStars(int n) {
        paintStars(n, false);
    }

    /**
     * @param preview true 只在拖动中调用，副标题提示「松手提交 N 星」
     */
    private void paintStars(int n, boolean preview) {
        for (int i = 0; i < starViews.length; i++) {
            TextView st = starViews[i];
            if (st == null) continue;
            boolean on = i < n;
            st.setText(on ? "★" : "☆");
            st.setTextColor(on ? Ui.C_GOLD : Ui.C_DIM);
            st.animate().scaleX(on ? 1.08f : 1f).scaleY(on ? 1.08f : 1f)
                    .setDuration(80).start();
        }
        if (ratingScoreView != null) ratingScoreView.setText(ratingScoreText());
        if (ratingSubView != null) {
            ratingSubView.setText(preview && n > 0
                    ? ("松手提交 " + n + " 星") : ratingSubText());
        }
    }

    private String ratingScoreText() {
        double sc = detail == null ? 0 : detail.info.score;
        return "★ " + (sc > 0 ? String.format("%.1f 分", sc) : "暂无评分");
    }

    private String ratingSubText() {
        if (myStars > 0) return "我的评分 " + myStars + " 星";
        return "点右侧星星打分";
    }

    /** 松手提交：本地先记，登录态再推云端 */
    private void commitStars(int stars) {
        myStars = stars;
        Store.get(this).setRating(videoId, stars);
        paintStars(stars);
        if (!Auth.loggedIn()) {
            Toast.makeText(this, "已记下 " + stars + " 星，登录后同步到账号",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        Api.submitRating(videoId, stars, new Api.Cb<Models.Rating>() {
            @Override
            public void ok(Models.Rating r) {
                if (r.my > 0) myStars = r.my;
                ratingCount = r.cnt;
                if (r.score > 0 && detail != null) detail.info.score = r.score;
                paintStars(myStars);
                Toast.makeText(DetailActivity.this,
                        "已提交 " + myStars + " 星 · " + ratingScoreText().replace("★ ", ""),
                        Toast.LENGTH_SHORT).show();
            }

            @Override
            public void err(String msg) {
                Toast.makeText(DetailActivity.this, "云端评分失败：" + msg,
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    private int myStars;
    private int ratingCount;
    /** 大评分块的滚动宿主：液态玻璃要采它背后的内容 */
    private ScrollView scrollSource;
    private TextView ratingScoreView;
    private TextView ratingSubView;
    private final TextView[] starViews = new TextView[5];

    // ------------------------------------------------------------- 批量下载

    private void togglePickMode() {
        if (detail == null) return;
        pickMode = !pickMode;
        picked.clear();
        if (pickMode) {
            // 进入选择模式时，把未缓存的集默认全不选；已缓存集仍显示圆点
            epTitleView.setText("选择要缓存的集数");
        } else {
            epTitleView.setText(epTitleText());
        }
        renderEpisodes();
        paintEpActions();
    }

    /** 选集标题右侧：仅下载模式出现「全选 / 取消 / 确定下载(已选 N)」 */
    private void paintEpActions() {
        if (epActions == null) return;
        epActions.removeAllViews();
        if (!pickMode) return;

        TextView all = miniBtn("全选", Ui.C_SUB, false);
        all.setOnClickListener(v -> {
            boolean full = picked.size() >= detail.episodes.size();
            picked.clear();
            if (!full) {
                for (Models.Episode e : detail.episodes) picked.add(e.ep);
            }
            renderEpisodes();
            paintEpActions();
        });
        epActions.addView(all);

        TextView cancel = miniBtn("取消", Ui.C_SUB, false);
        LinearLayout.LayoutParams cp = Ui.lp(ViewGroup.LayoutParams.WRAP_CONTENT,
                Ui.dp(this, 30));
        cp.leftMargin = Ui.dp(this, 8);
        cancel.setOnClickListener(v -> togglePickMode());
        epActions.addView(cancel, cp);

        TextView ok = miniBtn(picked.isEmpty() ? "确定下载" : "确定下载 " + picked.size(),
                Ui.accentBright(), true);
        LinearLayout.LayoutParams op = Ui.lp(ViewGroup.LayoutParams.WRAP_CONTENT,
                Ui.dp(this, 30));
        op.leftMargin = Ui.dp(this, 8);
        ok.setOnClickListener(v -> startDownload());
        epActions.addView(ok, op);
    }

    private TextView miniBtn(String text, int color, boolean primary) {
        TextView b = Ui.bold(this, text, 11f, color);
        b.setGravity(Gravity.CENTER);
        b.setPadding(Ui.dp(this, 12), Ui.dp(this, 6), Ui.dp(this, 12), Ui.dp(this, 6));
        b.setBackground(primary
                ? Ui.stroke(0x33F5B942, 0x80F5B942, Ui.dp(this, 14), 1)
                : Ui.stroke(Ui.chipFill(), Ui.chipStroke(), Ui.dp(this, 14), 1));
        b.setClickable(true);
        return b;
    }

    private void startDownload() {
        if (downloading) return;
        if (picked.isEmpty()) {
            android.widget.Toast.makeText(this, "先选几集", android.widget.Toast.LENGTH_SHORT).show();
            return;
        }
        final List<Integer> list = new ArrayList<>(picked);
        Collections.sort(list);
        downloading = true;
        dlDone = 0;
        dlTotal = list.size();
        dlStatus = Ui.text(this, "", 11f, Ui.C_AMBER);
        android.widget.Toast.makeText(this, "开始缓存 " + dlTotal + " 集", android.widget.Toast.LENGTH_SHORT).show();
        epTitleView.setText("缓存中 0/" + dlTotal);
        downloadNext(list, 0);
    }

    private void downloadNext(final List<Integer> list, final int idx) {
        if (idx >= list.size()) {
            downloading = false;
            pickMode = false;
            picked.clear();
            epTitleView.setText(epTitleText());
            paintEpActions();
            renderEpisodes();
            android.widget.Toast.makeText(this, "全部缓存完成", android.widget.Toast.LENGTH_SHORT).show();
            return;
        }
        final int epNum = list.get(idx);
        epTitleView.setText("缓存中 " + idx + "/" + dlTotal + "（第 " + epNum + " 集）");
        Api.playback(videoId, epNum, new Api.Cb<String>() {
            @Override
            public void ok(String url) {
                HlsDownloader.download(getApplicationContext(), url, detail.info.title,
                        "第" + epNum + "集", new HlsDownloader.Cb() {
                            @Override
                            public void onProgress(int done, int total, String note) {
                            }

                            @Override
                            public void onDone(String path) {
                                Store.get(DetailActivity.this).cacheEpisode(videoId,
                                        detail.info.title, detail.info.cover, epNum,
                                        detail.info.epCount > 0 ? detail.info.epCount
                                                : detail.episodes.size());
                                // 记下这一集的本地文件：离线播放直接读它，不再走网络
                                String name = path == null ? ""
                                        : path.substring(path.lastIndexOf('/') + 1);
                                String local = MediaSave.findDownloaded(
                                        getApplicationContext(), detail.info.title, name);
                                if (local == null) local = new java.io.File(path == null ? "" : path)
                                        .getAbsolutePath();
                                Store.get(DetailActivity.this).saveLocalFile(videoId, epNum, local);
                                runOnUiThread(() -> {
                                    cachedEps = Store.get(DetailActivity.this).cachedEps(videoId);
                                    paintChip(epNum);
                                    downloadNext(list, idx + 1);
                                });
                            }

                            @Override
                            public void onErr(String msg) {
                                runOnUiThread(() -> {
                                    android.widget.Toast.makeText(DetailActivity.this,
                                            "第 " + epNum + " 集失败：" + msg,
                                            android.widget.Toast.LENGTH_SHORT).show();
                                    downloadNext(list, idx + 1);
                                });
                            }
                        });
            }

            @Override
            public void err(String msg) {
                runOnUiThread(() -> {
                    android.widget.Toast.makeText(DetailActivity.this,
                            "第 " + epNum + " 集地址获取失败", android.widget.Toast.LENGTH_SHORT).show();
                    downloadNext(list, idx + 1);
                });
            }
        });
    }

    private CommentBox commentBox;

    /** 评论区按「影片 + 集数」隔离，切集就整块换数据源 */
    private void renderComments(LinearLayout content) {
        LinearLayout sec = Ui.col(this);
        sec.setPadding(Ui.dp(this, 16), Ui.dp(this, 14), Ui.dp(this, 16), Ui.dp(this, 110));
        content.addView(sec);

        commentBox = new CommentBox(this, commentTitle(), new CommentBox.Source() {
            @Override
            public void load(int page, Api.Cb<List<Models.Comment>> cb) {
                Api.comments(videoId, selectedEp, page, cb);
            }

            @Override
            public void send(String text, Api.Cb<String> cb) {
                Api.postVideoComment(videoId, selectedEp, text, cb);
            }

            @Override
            public void replies(long commentId, int page, Api.Cb<List<Models.Comment>> cb) {
                Api.commentReplies(videoId, commentId, page, cb);
            }

            @Override
            public void reply(long parentId, String text, Api.Cb<String> cb) {
                // 点评论回复：带 reply_id 挂到父楼下
                Api.replyVideoComment(videoId, parentId, text, cb);
            }
        }, () -> AuthDialog.show(this, false, this::refreshComments), Store.LIKE_VCOMMENT);
        sec.addView(commentBox, Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        commentBox.reload();
    }

    private String commentTitle() {
        return "评论 · 第 " + selectedEp + " 集";
    }

    private void refreshComments() {
        if (commentBox == null) return;
        commentBox.setTitle(commentTitle());
        commentBox.reload();
    }

    /** 选集 chips：普通模式点选切集；下载模式点选/划选勾集，已缓存集右上角点琥珀圆点 */
    /**
     * 缓存集数来自本地库记录；文件可能已经被外部删掉。
     * 后台核对一遍（stat / MediaStore 探活），真有集数消失就重画选集网格 ——
     * 这样详情页不会再给已经不存在的缓存画勾。
     */
    private void checkCacheAlive() {
        if (cachedEps == null || cachedEps.isEmpty()) return;
        Store.get(this).verifyCacheFilesAsync(() -> {
            if (isFinishing() || isDestroyed()) return;
            java.util.Set<Integer> fresh = Store.get(this).cachedEps(videoId);
            if (fresh.equals(cachedEps)) return;
            cachedEps = fresh;
            if (epGrid != null) renderEpisodes();
        });
    }

    private void renderEpisodes() {
        epGrid.removeAllViews();
        int col = 5;
        int gap = Ui.dp(this, 8);
        int screen = getResources().getDisplayMetrics().widthPixels;
        int cellW = (screen - Ui.dp(this, 32) - gap * (col - 1)) / col;

        for (int i = 0; i < detail.episodes.size(); i++) {
            final int epNum = detail.episodes.get(i).ep;
            FrameLayout holder = new FrameLayout(this);

            TextView chip = Ui.text(this, String.valueOf(epNum), 12f, chipTextColor(epNum));
            chip.setGravity(Gravity.CENTER);
            chip.setPadding(0, Ui.dp(this, 9), 0, Ui.dp(this, 9));
            chip.setBackground(chipBg(epNum));
            holder.addView(chip, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            chip.setTag(epNum);

            // 已缓存标识：chip 右上角小圆点
            if (cachedEps.contains(epNum)) {
                // 已缓存标识：chip 右上角一个勾（比小圆点在 5 列网格里清楚得多）
                boolean onAmber = (pickMode && picked.contains(epNum)) || (!pickMode && epNum == selectedEp);
                TextView tick = Ui.bold(this, "✓", 9f, onAmber ? Ui.onAmber() : Ui.C_AMBER);
                FrameLayout.LayoutParams dp = new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                        Gravity.TOP | Gravity.END);
                dp.rightMargin = Ui.dp(this, 4);
                dp.topMargin = Ui.dp(this, 1);
                holder.addView(tick, dp);
            }

            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = cellW;
            lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            lp.setMargins(0, i >= col ? gap : 0, 0, 0);
            lp.columnSpec = GridLayout.spec(i % col, 1f);
            holder.setLayoutParams(lp);
            epGrid.addView(holder);
        }
    }

    /** 只重画一集的 chip（拖选时避免整格重建） */
    private void paintChip(int epNum) {
        for (int i = 0; i < epGrid.getChildCount(); i++) {
            FrameLayout holder = (FrameLayout) epGrid.getChildAt(i);
            View chip = holder.getChildAt(0);
            if (chip != null && Integer.valueOf(epNum).equals(chip.getTag())) {
                ((TextView) chip).setTextColor(chipTextColor(epNum));
                chip.setBackground(chipBg(epNum));
                // 缓存勾跟着选中态换对比色（第二个子 View 是缓存勾）
                if (holder.getChildCount() > 1 && holder.getChildAt(1) instanceof TextView) {
                    boolean onAmber = pickMode && picked.contains(epNum);
                    ((TextView) holder.getChildAt(1)).setTextColor(onAmber ? Ui.onAmber() : Ui.C_AMBER);
                }
                return;
            }
        }
    }

    private int chipTextColor(int epNum) {
        if (pickMode) return picked.contains(epNum) ? Ui.onAmber() : Ui.C_TEXT;
        return epNum == selectedEp ? Ui.onAmber() : Ui.C_TEXT;
    }

    private android.graphics.drawable.Drawable chipBg(int epNum) {
        if (pickMode) {
            return picked.contains(epNum)
                    ? Ui.round(Ui.C_AMBER, Ui.dp(this, 10))
                    : Ui.stroke(Ui.chipFill(), Ui.chipStroke(), Ui.dp(this, 10), 1);
        }
        return epNum == selectedEp
                ? Ui.round(Ui.C_AMBER, Ui.dp(this, 10))
                : Ui.stroke(Ui.chipFill(), Ui.chipStroke(), Ui.dp(this, 10), 1);
    }

    // ------------------------------------------------------------- 选集触摸（点选 / 划选）

    private int downEp = -1;
    private boolean gridDragging = false;
    private boolean dragSelect = true;
    private final Set<Integer> gridTouched = new java.util.HashSet<>();
    private float downX, downY;

    /** 点一下切换/切集；按住横向划过多格连续勾选（首次落点决定这次是「勾」还是「取消」） */
    private boolean onGridTouch(MotionEvent ev) {
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                downX = ev.getX();
                downY = ev.getY();
                downEp = epAt(ev.getX(), ev.getY());
                gridDragging = false;
                gridTouched.clear();
                dragSelect = downEp > 0 && !picked.contains(downEp);
                // 先由网格接管：点选要拿到完整 DOWN→UP；确实在竖直滚动时再交还父级
                ViewGroup par = (ViewGroup) epGrid.getParent();
                if (par != null) par.requestDisallowInterceptTouchEvent(true);
                return true;
            }
            case MotionEvent.ACTION_MOVE: {
                if (downEp <= 0) return true;
                if (!gridDragging
                        && (Math.abs(ev.getX() - downX) > 12 || Math.abs(ev.getY() - downY) > 12)) {
                    gridDragging = true;
                    // 只有下载模式才划选；普通模式判定为滚动，交还父级继续滚页面
                    if (!pickMode) {
                        ViewGroup par = (ViewGroup) epGrid.getParent();
                        if (par != null) par.requestDisallowInterceptTouchEvent(false);
                        return false;
                    }
                    if (gridTouched.add(downEp)) applyDragPick(downEp);
                }
                if (gridDragging && pickMode) {
                    int ep = epAt(ev.getX(), ev.getY());
                    if (ep > 0 && gridTouched.add(ep)) applyDragPick(ep);
                }
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                if (!gridDragging && downEp > 0) tapEp(downEp);
                downEp = -1;
                gridDragging = false;
                return true;
            }
            default:
                downEp = -1;
                return true;
        }
    }

    private void applyDragPick(int ep) {
        if (dragSelect) picked.add(ep);
        else picked.remove(ep);
        paintChip(ep);
        paintEpActions();
    }

    private void tapEp(int ep) {
        if (pickMode) {
            if (picked.contains(ep)) picked.remove(ep);
            else picked.add(ep);
            paintChip(ep);
            paintEpActions();
        } else {
            selectedEp = ep;
            renderEpisodes();
            updatePlayLabel();
            refreshComments();
        }
    }

    /** 落点命中的集号（-1 = 空白） */
    private int epAt(float x, float y) {
        for (int i = 0; i < epGrid.getChildCount(); i++) {
            View holder = epGrid.getChildAt(i);
            if (x >= holder.getLeft() && x < holder.getRight()
                    && y >= holder.getTop() && y < holder.getBottom()) {
                if (holder instanceof ViewGroup) {
                    View chip = ((ViewGroup) holder).getChildAt(0);
                    if (chip != null && chip.getTag() != null) {
                        try {
                            return (Integer) chip.getTag();
                        } catch (Exception ignored) {
                        }
                    }
                }
                return -1;
            }
        }
        return -1;
    }

    private void syncFav() {
        if (favBtn == null) return;
        favBtn.setColorFilter(favorite ? Ui.C_GOLD : Ui.C_TEXT);
    }

    private void updatePlayLabel() {
        if (playLabel != null) playLabel.setText("立即播放 第 " + selectedEp + " 集");
    }

    /** 跟随系统模式下系统切深浅色：原地重建本页，不回主界面 */
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        boolean dark = Theme.isDark(this);
        if (Theme.mode() == Theme.AUTO && dark != Ui.darkGlass) {
            Ui.retheme(this);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 兜底：配置变化发生在后台时，回到前台再补一次
        if (Ui.darkGlass != Theme.isDark(this)) {
            Ui.retheme(this);
        }
    }
}
