package com.huangguo.pure;

import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.util.List;

/** 剧集详情：封面头图 + 剧情 + 选集 + 固定播放入口 */
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

        // 收藏 / 分享：和 Dock 同源的液态玻璃（采样自己背后那 50dp 的滚动内容）
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
            favorite = Store.get(this).toggleFavorite(detail.info);
            syncFav();
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
        bar.addView(shareWrap, Ui.lp(Ui.dp(this, 50), Ui.dp(this, 50)));

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

        Api.detail(videoId, new Api.Cb<Models.Detail>() {
            @Override
            public void ok(Models.Detail d) {
                detail = d;
                content.removeView(loading);
                render(content);
                bottomBar.setVisibility(View.VISIBLE);
                updatePlayLabel();
                favorite = Store.get(DetailActivity.this).isFavorite(Store.T_VIDEO, videoId);
                syncFav();
            }

            @Override
            public void err(String msg) {
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
        if (v.score > 0) meta.append("★ ").append(v.scoreLabel());
        if (v.epCount > 0) {
            if (meta.length() > 0) meta.append("   ·   ");
            meta.append(v.epText());
        }
        if (v.tags != null && !v.tags.isEmpty()) {
            if (meta.length() > 0) meta.append("   ·   ");
            meta.append(v.tags);
        }
        TextView metaTv = Ui.text(this, meta.toString(), 11.5f, 0xFFDCE3EE);
        LinearLayout.LayoutParams mp = Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        mp.topMargin = Ui.dp(this, 6);
        info.addView(metaTv, mp);

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
        epSec.setPadding(Ui.dp(this, 16), Ui.dp(this, 20), Ui.dp(this, 16), Ui.dp(this, 8));
        content.addView(epSec);
        String epTitle = v.epText().isEmpty() ? ("选集 · " + detail.episodes.size() + " 集")
                : ("选集 · " + v.epText());
        epSec.addView(Ui.bold(this, epTitle, 13.5f, Ui.C_TEXT));

        epGrid = new GridLayout(this);
        epGrid.setColumnCount(5);
        LinearLayout.LayoutParams gp = Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        gp.topMargin = Ui.dp(this, 10);
        epSec.addView(epGrid, gp);
        renderEpisodes();

        renderComments(content);
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

    private void renderEpisodes() {
        epGrid.removeAllViews();
        int col = 5;
        int gap = Ui.dp(this, 8);
        int screen = getResources().getDisplayMetrics().widthPixels;
        int cellW = (screen - Ui.dp(this, 32) - gap * (col - 1)) / col;

        for (int i = 0; i < detail.episodes.size(); i++) {
            final Models.Episode e = detail.episodes.get(i);
            TextView chip = Ui.text(this, String.valueOf(e.ep), 12f,
                    e.ep == selectedEp ? Ui.onAmber() : Ui.C_TEXT);
            chip.setGravity(Gravity.CENTER);
            chip.setPadding(0, Ui.dp(this, 9), 0, Ui.dp(this, 9));
            chip.setBackground(e.ep == selectedEp
                    ? Ui.round(Ui.C_AMBER, Ui.dp(this, 10))
                    : Ui.stroke(Ui.chipFill(), Ui.chipStroke(), Ui.dp(this, 10), 1));
            chip.setOnClickListener(v -> {
                selectedEp = e.ep;
                renderEpisodes();
                updatePlayLabel();
                refreshComments();
            });

            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = cellW;
            lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            lp.setMargins(0, i >= col ? gap : 0, 0, 0);
            lp.columnSpec = GridLayout.spec(i % col, 1f);
            chip.setLayoutParams(lp);
            epGrid.addView(chip);
        }
    }

    private void syncFav() {
        if (favBtn == null) return;
        favBtn.setColorFilter(favorite ? Ui.C_AMBER : 0xFF9AA3B2);
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
            Ui.applyPalette(dark);
            recreate();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 兜底：配置变化发生在后台时，回到前台再补一次
        if (Ui.darkGlass != Theme.isDark(this)) {
            Ui.applyPalette(Theme.isDark(this));
            recreate();
        }
    }
}
