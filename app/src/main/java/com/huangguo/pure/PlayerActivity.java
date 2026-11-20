package com.huangguo.pure;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.animation.ValueAnimator;
import android.content.pm.ActivityInfo;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.GestureDetector;
import android.view.HapticFeedbackConstants;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;
import android.view.TextureView;

import java.util.ArrayList;
import java.util.List;

/** 播放器：ExoPlayer(HLS) + Liquid Glass 控制层 + 长按 2 倍速 */
@UnstableApi
public class PlayerActivity extends AppCompatActivity {

    private static final String E_ID = "id";
    private static final String E_TITLE = "title";
    private static final String E_EP = "ep";
    private static final String E_POS = "pos";
    private static final String E_STREAM = "stream";
    private static final String E_COVER = "cover";

    public static void open(Context c, long id, String title, int ep) {
        open(c, id, title, ep, 0L);
    }

    public static void open(Context c, long id, String title, int ep, long startMs) {
        Intent i = new Intent(c, PlayerActivity.class);
        i.putExtra(E_ID, id);
        i.putExtra(E_TITLE, title);
        i.putExtra(E_EP, ep);
        i.putExtra(E_POS, startMs);
        c.startActivity(i);
    }

    /** 直接播一个 m3u8 直链（博客视频用，复用整套手势与 Liquid Glass 控制层） */
    public static void openStream(Context c, String url, String title, String cover) {
        Intent i = new Intent(c, PlayerActivity.class);
        i.putExtra(E_STREAM, url);
        i.putExtra(E_TITLE, title);
        i.putExtra(E_COVER, cover);
        c.startActivity(i);
    }

    /** 直链模式：博客正文里的视频走这里，不查选集、不记录短剧历史 */
    private String streamUrl = "";
    private String streamCover = "";
    /** 当前正在播的 m3u8 直链（下载用） */
    private String currentUrl = "";
    private boolean downloading = false;

    private ExoPlayer player;
    private TextureView videoView;
    /** 视频宽高比（w/h），0 = 未知（铺满） */
    private float videoAspect = 0f;
    /** 顶/底栏液态玻璃：采样视频帧做真实折射 */
    private LiquidGlassDrawable topGlass, bottomGlass;
    private Bitmap pendingTop, pendingBottom;
    private long videoId;
    private String dramaTitle = "";
    private int ep = 1;
    private long startMs = 0;
    private String cover = "";
    private int epCount = 0;

    private FrameLayout overlay;
    private LinearLayout topPanel;
    private LinearLayout bottomPanel;
    private LinearLayout epSheet;
    private FrameLayout centerWrap;
    private ImageView centerBtn;
    private SeekBar seek;
    private TextView timeNow;
    private TextView timeTotal;
    private TextView epLabel;
    private TextView speedBtn;
    private TextView boostTip;
    private FrameLayout orientBtn;
    private GridLayout epGrid;
    private TextView statusTip;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final List<Models.Episode> episodes = new ArrayList<>();
    private GestureDetector gesture;
    private boolean controlsVisible = true;
    private boolean sheetOpen = false;
    private boolean dragging = false;
    private boolean boosting = false;
    private boolean boostLocked = false;
    private float downY = -1f;
    private long downTime = 0L;
    private boolean fingerMoved = false;
    private float speed = 1f;
    /** 本次长按生效的临时倍速：基础 1x → 2x，基础 2x → 1x */
    private float boostSpeed = 2f;
    private ValueAnimator speedAnim;
    /** 长按 360ms 起播 2x，比系统 500ms 更快 */
    private static final int LONG_PRESS_MS = 360;
    /** 上滑锁定 / 下滑恢复的位移阈值 */
    private static final int LOCK_DP = 40;
    private int durationMs = 0;
    private int tick = 0;
    private boolean restoredPosition = false;

    /** 手势模式：0 无 / 1 拖进度 */
    private int gestureMode = 0;
    private static final int G_NONE = 0, G_SEEK = 1;
    private float downX = 0f;
    private long startPos = 0L;
    private int hintHideSeq = 0;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        videoId = getIntent().getLongExtra(E_ID, 0);
        dramaTitle = getIntent().getStringExtra(E_TITLE);
        ep = getIntent().getIntExtra(E_EP, 1);
        startMs = getIntent().getLongExtra(E_POS, 0L);
        streamUrl = getIntent().getStringExtra(E_STREAM);
        if (streamUrl == null) streamUrl = "";
        streamCover = getIntent().getStringExtra(E_COVER);
        if (streamCover == null) streamCover = "";
        if (dramaTitle == null) dramaTitle = "";

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        WindowInsetsControllerCompat ctl =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        ctl.hide(WindowInsetsCompat.Type.systemBars());
        ctl.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF000000);
        setContentView(root);

        androidx.media3.exoplayer.DefaultLoadControl loadControl =
                new androidx.media3.exoplayer.DefaultLoadControl.Builder()
                        .setBufferDurationsMs(15000, 60000, 1200, 2400)
                        .build();
        player = new ExoPlayer.Builder(this).setLoadControl(loadControl).build();
        player.setHandleAudioBecomingNoisy(true);
        videoView = new TextureView(this);   // TextureView 不支持 background，黑底由 root 提供
        root.addView(videoView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        player.setVideoTextureView(videoView);
        // 容器尺寸变化（旋转/分屏）时按当前比例重排
        videoView.addOnLayoutChangeListener((v, l, t, r, bt, ol, ot, or2, obt) -> {
            if (r - l != or2 - ol || bt - t != obt - ot) applyVideoAspect();
        });

        buildOverlay(root);
        installGestures();

        player.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int state) {
                if (state == Player.STATE_READY) {
                    durationMs = (int) Math.max(0, player.getDuration());
                    timeTotal.setText(Ui.hhmmss(durationMs));
                    statusTip.setVisibility(View.GONE);
                    if (!restoredPosition && startMs > 0) {
                        player.seekTo(startMs);
                        restoredPosition = true;
                    }
                } else if (state == Player.STATE_BUFFERING) {
                    statusTip.setVisibility(View.VISIBLE);
                    statusTip.setText("缓冲中…");
                } else if (state == Player.STATE_ENDED) {
                    saveHistory();
                    playNext();
                }
                syncCenter();
            }

            @Override
            public void onVideoSizeChanged(androidx.media3.common.VideoSize vs) {
                if (vs.width > 0 && vs.height > 0) {
                    // 竖屏视频（如手机拍摄）用旋转后比例；9/16 报的是 w=16 h=9 需翻转
                    float w = vs.width, h = vs.height;
                    if (vs.unappliedRotationDegrees == 90 || vs.unappliedRotationDegrees == 270) {
                        float t = w; w = h; h = t;
                    }
                    videoAspect = w / h;
                } else {
                    videoAspect = 0f;
                }
                android.util.Log.d("HGPlayer", "onVideoSizeChanged " + vs.width + "x" + vs.height
                        + " rot=" + vs.unappliedRotationDegrees + " aspect=" + videoAspect);
                applyVideoAspect();
            }

            @Override
            public void onIsPlayingChanged(boolean playing) {
                syncCenter();
                scheduleHide();
            }

            @Override
            public void onPlayerError(@NonNull PlaybackException error) {
                statusTip.setVisibility(View.VISIBLE);
                statusTip.setText("播放出错，点击重试\n" + error.getErrorCodeName());
                statusTip.setOnClickListener(v -> prepare());
            }
        });

        if (streamUrl.isEmpty()) {
            loadEpisodes();
        } else {
            cover = streamCover;
            epLabel.setText("视频");
        }
        prepare();
        startTicker();
        ui.postDelayed(frameSampler, 400L);
    }

    // ------------------------------------------------------------------ 视图

    private void buildOverlay(FrameLayout root) {
        overlay = new FrameLayout(this);
        root.addView(overlay, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        statusTip = Ui.text(this, "加载中…", 13f, Ui.C_TEXT);
        statusTip.setGravity(Gravity.CENTER);
        statusTip.setBackgroundColor(Ui.darkGlass ? 0x33000000 : 0x22FFFFFF);
        overlay.addView(statusTip, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        topPanel = Ui.row(this);
        topGlass = new LiquidGlassDrawable(Ui.dp(this, 22),
                Ui.darkGlass ? 0x66121522 : 0x66FFFFFF, Ui.dp(this, 8), 0f);
        topPanel.setBackground(topGlass);
        topPanel.setPadding(Ui.dp(this, 8), Ui.dp(this, 6), Ui.dp(this, 14), Ui.dp(this, 6));
        FrameLayout.LayoutParams tp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 52));
        tp.gravity = Gravity.TOP;
        tp.leftMargin = Ui.dp(this, 12);
        tp.rightMargin = Ui.dp(this, 12);
        tp.topMargin = Ui.dp(this, 12);
        overlay.addView(topPanel, tp);

        FrameLayout backWrap = new FrameLayout(this);
        backWrap.setClickable(true);
        ImageView back = new ImageView(this);
        back.setImageResource(R.drawable.ic_back);
        backWrap.addView(back, new FrameLayout.LayoutParams(
                Ui.dp(this, 20), Ui.dp(this, 20), Gravity.CENTER));
        backWrap.setOnClickListener(v -> finish());
        topPanel.addView(backWrap, Ui.lp(Ui.dp(this, 36), Ui.dp(this, 36)));

        LinearLayout titleBox = Ui.col(this);
        LinearLayout.LayoutParams tlp = Ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.weight = 1f;
        tlp.leftMargin = Ui.dp(this, 8);
        topPanel.addView(titleBox, tlp);

        TextView titleView = Ui.bold(this, dramaTitle, 13f, Ui.C_TEXT);
        titleView.setSingleLine(true);
        titleView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        titleBox.addView(titleView);

        epLabel = Ui.text(this, "第 " + ep + " 集", 10.5f, Ui.C_AMBER);
        titleBox.addView(epLabel);

        // 中央播放键：整块容器参与隐藏，避免残留圆形底板
        centerWrap = new FrameLayout(this);
        centerWrap.setBackground(Ui.liquidPanel(this, Ui.dp(this, 34)));
        centerWrap.setClickable(true);
        centerBtn = new ImageView(this);
        centerBtn.setImageResource(R.drawable.ic_play);
        // 浅色模式中心玻璃是白色，白色图标看不清 → 用主题文字色
        centerBtn.setColorFilter(Ui.titleText());
        centerWrap.addView(centerBtn, new FrameLayout.LayoutParams(
                Ui.dp(this, 28), Ui.dp(this, 28), Gravity.CENTER));
        centerWrap.setOnClickListener(v -> togglePlay());
        FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(
                Ui.dp(this, 64), Ui.dp(this, 64), Gravity.CENTER);
        overlay.addView(centerWrap, cp);

        boostTip = Ui.text(this, "", 11.5f, Ui.C_TEXT);
        boostTip.setGravity(Gravity.CENTER);
        boostTip.setPadding(Ui.dp(this, 14), Ui.dp(this, 7), Ui.dp(this, 14), Ui.dp(this, 7));
        boostTip.setBackground(Ui.liquidPanel(this, Ui.dp(this, 16)));
        boostTip.setVisibility(View.GONE);
        FrameLayout.LayoutParams btp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        btp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        btp.topMargin = Ui.dp(this, 78);
        overlay.addView(boostTip, btp);

        bottomPanel = Ui.col(this);
        bottomGlass = new LiquidGlassDrawable(Ui.dp(this, 22),
                Ui.darkGlass ? 0x66121522 : 0x66FFFFFF, Ui.dp(this, 8), 0f);
        bottomPanel.setBackground(bottomGlass);
        bottomPanel.setPadding(Ui.dp(this, 14), Ui.dp(this, 8), Ui.dp(this, 14), Ui.dp(this, 10));
        FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bp.gravity = Gravity.BOTTOM;
        bp.leftMargin = Ui.dp(this, 12);
        bp.rightMargin = Ui.dp(this, 12);
        bp.bottomMargin = Ui.dp(this, 16);
        overlay.addView(bottomPanel, bp);

        LinearLayout seekRow = Ui.row(this);
        bottomPanel.addView(seekRow, Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 30)));

        timeNow = Ui.text(this, "00:00", 10.5f, Ui.subText());
        seekRow.addView(timeNow);

        seek = new SeekBar(this);
        seek.setMax(1000);
        seek.setProgressTintList(ColorStateList.valueOf(Ui.C_AMBER));
        seek.setThumbTintList(ColorStateList.valueOf(Ui.darkGlass ? 0xFFFFD37A : 0xFFB8860B));
        seek.setProgressBackgroundTintList(ColorStateList.valueOf(Ui.darkGlass ? 0x33FFFFFF : 0x33223344));
        LinearLayout.LayoutParams sp = Ui.lp(0, Ui.dp(this, 26));
        sp.weight = 1f;
        sp.leftMargin = Ui.dp(this, 8);
        sp.rightMargin = Ui.dp(this, 8);
        seekRow.addView(seek, sp);

        timeTotal = Ui.text(this, "00:00", 10.5f, Ui.subText());
        seekRow.addView(timeTotal);

        LinearLayout toolRow = Ui.row(this);
        LinearLayout.LayoutParams trp = Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        trp.topMargin = Ui.dp(this, 2);
        bottomPanel.addView(toolRow, trp);

        speedBtn = toolChip("1.0x");
        speedBtn.setOnClickListener(v -> toggleSpeedMenu());
        toolRow.addView(speedBtn, chipLp(0));

        TextView epBtn = toolChip("选集");
        epBtn.setOnClickListener(v -> toggleSheet());
        toolRow.addView(epBtn, chipLp(8));

        // 上一集 / 下一集：图标按钮（替代文字胶囊，给右侧全屏键留出空间）
        buildEpisodeStepButton(toolRow, true);
        buildEpisodeStepButton(toolRow, false);

        // 下载按钮：移到「下一集」和「评论」中间，按最高画质存 Download/Huangguo/<剧名>/
        buildDownloadButton(toolRow);

        // 评论按钮：跳详情页对应集数的评论区
        buildCommentEntry(toolRow);

        // 画面设置按钮：全屏键左边（替换原点赞按钮）
        buildDisplayButton(toolRow);

        View filler = new View(this);
        LinearLayout.LayoutParams flp = Ui.lp(0, 1);
        flp.weight = 1f;
        toolRow.addView(filler, flp);

        // 全屏按钮：放到原「长按 2.0x」所在的最右格
        buildOrientButton(toolRow);

        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                if (!fromUser || durationMs <= 0) return;
                timeNow.setText(Ui.hhmmss((long) durationMs * progress / 1000));
            }

            @Override
            public void onStartTrackingTouch(SeekBar bar) {
                dragging = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar bar) {
                dragging = false;
                if (durationMs > 0) player.seekTo((long) durationMs * bar.getProgress() / 1000);
            }
        });

        buildSheet();
        buildSpeedMenu();
    }

    // ------------------------------------------------------------------ 倍速菜单

    private static final float[] SPEEDS = {0.5f, 0.75f, 1f, 1.5f, 2f, 3f};

    private LinearLayout speedMenu;
    private final java.util.List<TextView> speedChips = new java.util.ArrayList<>();
    private boolean speedMenuOpen = false;

    private void buildSpeedMenu() {
        speedMenu = Ui.col(this);
        speedMenu.setBackground(Ui.liquidPanel(this, Ui.dp(this, 18)));
        speedMenu.setPadding(Ui.dp(this, 12), Ui.dp(this, 10), Ui.dp(this, 12), Ui.dp(this, 12));

        TextView title = Ui.text(this, "播放倍速", 10.5f, Ui.C_DIM);
        speedMenu.addView(title);

        LinearLayout row = Ui.row(this);
        LinearLayout.LayoutParams rlp = Ui.lp(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        rlp.topMargin = Ui.dp(this, 8);
        speedMenu.addView(row, rlp);

        for (final float s : SPEEDS) {
            TextView chip = Ui.text(this, fmtSpeed(s), 12f, Ui.C_TEXT);
            chip.setGravity(Gravity.CENTER);
            chip.setPadding(Ui.dp(this, 12), Ui.dp(this, 8), Ui.dp(this, 12), Ui.dp(this, 8));
            chip.setBackground(Ui.stroke(Ui.chipFill(), Ui.chipStroke(), Ui.dp(this, 14), 1));
            chip.setClickable(true);
            chip.setOnClickListener(v -> {
                setSpeed(s);
                closeSpeedMenu();
            });
            LinearLayout.LayoutParams lp = Ui.lp(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = Ui.dp(this, 6);
            row.addView(chip, lp);
            speedChips.add(chip);
        }

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.BOTTOM | Gravity.START;
        lp.leftMargin = Ui.dp(this, 12);
        lp.bottomMargin = Ui.dp(this, 116);
        overlay.addView(speedMenu, lp);
        speedMenu.setVisibility(View.GONE);
        speedMenu.setAlpha(0f);
        paintSpeedChips();
    }

    private void paintSpeedChips() {
        for (int i = 0; i < speedChips.size(); i++) {
            boolean active = Math.abs(SPEEDS[i] - speed) < 0.001f;
            TextView chip = speedChips.get(i);
            chip.setTextColor(active ? Ui.onAmber() : Ui.C_TEXT);
            chip.setBackground(active
                    ? Ui.round(Ui.C_AMBER, Ui.dp(this, 14))
                    : Ui.stroke(Ui.chipFill(), Ui.chipStroke(), Ui.dp(this, 14), 1));
        }
    }

    private void toggleSpeedMenu() {
        if (speedMenuOpen) closeSpeedMenu();
        else openSpeedMenu();
    }

    private void openSpeedMenu() {
        if (speedMenu == null) return;
        speedMenuOpen = true;
        ui.removeCallbacks(hideTask);
        speedMenu.setVisibility(View.VISIBLE);
        speedMenu.setAlpha(0f);
        speedMenu.setTranslationY(Ui.dp(this, 10));
        speedMenu.animate().alpha(1f).translationY(0f).setDuration(160).start();
    }

    private void closeSpeedMenu() {
        if (speedMenu == null || !speedMenuOpen) return;
        speedMenuOpen = false;
        speedMenu.animate().alpha(0f).translationY(Ui.dp(this, 10)).setDuration(140)
                .withEndAction(() -> speedMenu.setVisibility(View.GONE)).start();
        scheduleHide();
    }

    /** 手动设置倍速：优先级高于长按增强与锁定态 */
    private void setSpeed(float s) {
        speed = s;
        boosting = false;
        boostLocked = false;
        animateSpeed(s, 130);
        speedBtn.setText(fmtSpeed(s));
        paintSpeedChips();
        flashTip("倍速 " + fmtSpeed(s), 900);
    }

    /**
     * 长按目标：基础倍速是 1x（或其它）就推到 2x，基础倍速已经是 2x 就降回 1x。
     * 这样「长按」始终是「切到另一个档」，不会出现按下去没反应的情况。
     */
    private float boostTarget() {
        return Math.abs(speed - 2f) < 0.01f ? 1f : 2f;
    }

    // ------------------------------------------------------------------ 画面设置

    /** 显示模式：0 自适应原比例（letterbox）/ 1 铺满全屏 / 2 强制 16:9 / 3 强制 4:3 */
    private int displayMode = 0;
    private static final String[] DISPLAY_MODES = {"自适应", "铺满全屏", "16:9", "4:3"};

    private LinearLayout displayMenu;
    private final java.util.List<TextView> displayChips = new java.util.ArrayList<>();
    private boolean displayMenuOpen = false;

    /** 画面设置按钮：弹出显示比例选项菜单 */
    private void buildDisplayButton(LinearLayout toolRow) {
        TextView btn = toolChip("画面");
        btn.setOnClickListener(v -> {
            if (displayMenuOpen) closeDisplayMenu();
            else openDisplayMenu();
        });
        toolRow.addView(btn, chipLp(8));
        buildDisplayMenu();
    }

    private void buildDisplayMenu() {
        displayMenu = Ui.col(this);
        displayMenu.setBackground(Ui.liquidPanel(this, Ui.dp(this, 18)));
        displayMenu.setPadding(Ui.dp(this, 12), Ui.dp(this, 10), Ui.dp(this, 12), Ui.dp(this, 12));

        TextView title = Ui.text(this, "画面比例", 10.5f, Ui.C_DIM);
        displayMenu.addView(title);

        LinearLayout row = Ui.row(this);
        LinearLayout.LayoutParams rlp = Ui.lp(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        rlp.topMargin = Ui.dp(this, 8);
        displayMenu.addView(row, rlp);

        for (int i = 0; i < DISPLAY_MODES.length; i++) {
            final int mode = i;
            TextView chip = Ui.text(this, DISPLAY_MODES[i], 12f, Ui.C_TEXT);
            chip.setGravity(Gravity.CENTER);
            chip.setPadding(Ui.dp(this, 12), Ui.dp(this, 8), Ui.dp(this, 12), Ui.dp(this, 8));
            chip.setBackground(Ui.stroke(Ui.chipFill(), Ui.chipStroke(), Ui.dp(this, 14), 1));
            chip.setClickable(true);
            chip.setOnClickListener(v -> {
                displayMode = mode;
                paintDisplayChips();
                applyVideoAspect();
                closeDisplayMenu();
                flashTip("画面：" + DISPLAY_MODES[mode], 800);
            });
            LinearLayout.LayoutParams lp = Ui.lp(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = Ui.dp(this, 6);
            row.addView(chip, lp);
            displayChips.add(chip);
        }

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.BOTTOM | Gravity.END;
        lp.rightMargin = Ui.dp(this, 12);
        lp.bottomMargin = Ui.dp(this, 116);
        overlay.addView(displayMenu, lp);
        displayMenu.setVisibility(View.GONE);
        displayMenu.setAlpha(0f);
        paintDisplayChips();
    }

    private void paintDisplayChips() {
        for (int i = 0; i < displayChips.size(); i++) {
            boolean active = displayMode == i;
            TextView chip = displayChips.get(i);
            chip.setTextColor(active ? Ui.onAmber() : Ui.C_TEXT);
            chip.setBackground(active
                    ? Ui.round(Ui.C_AMBER, Ui.dp(this, 14))
                    : Ui.stroke(Ui.chipFill(), Ui.chipStroke(), Ui.dp(this, 14), 1));
        }
    }

    private void openDisplayMenu() {
        closeSpeedMenu();
        displayMenuOpen = true;
        ui.removeCallbacks(hideTask);
        displayMenu.setVisibility(View.VISIBLE);
        displayMenu.setAlpha(0f);
        displayMenu.setTranslationY(Ui.dp(this, 10));
        displayMenu.animate().alpha(1f).translationY(0f).setDuration(160).start();
    }

    private void closeDisplayMenu() {
        if (displayMenu == null || !displayMenuOpen) return;
        displayMenuOpen = false;
        displayMenu.animate().alpha(0f).translationY(Ui.dp(this, 10)).setDuration(140)
                .withEndAction(() -> displayMenu.setVisibility(View.GONE)).start();
        scheduleHide();
    }

    // ------------------------------------------------------------------ 评论入口

    private void buildCommentEntry(LinearLayout toolRow) {
        // 工具条里紧跟「下一集」：跳到详情页对应集数的评论区
        TextView entry = toolChip("评论");
        entry.setOnClickListener(v -> {
            if (videoId > 0) {
                DetailActivity.open(this, videoId, ep);
            } else {
                flashTip("正文视频没有评论区", 1000);
            }
        });
        toolRow.addView(entry, chipLp(8));
    }

    // ------------------------------------------------------------------ 横屏

    /** 工具条下载按钮：状态直接写在按钮上（图标 → 百分比 → 完成勾） */
    private void buildDownloadButton(LinearLayout toolRow) {
        final TextView btn = toolChip("下载");
        btn.setOnClickListener(v -> {
            if (downloading) return;
            if (currentUrl == null || currentUrl.isEmpty()) {
                flashTip("播放地址还没就绪", 1200);
                return;
            }
            downloading = true;
            btn.setText("0%");
            String series = dramaTitle == null || dramaTitle.isEmpty()
                    ? "未知剧集" : dramaTitle;
            String fileName = streamUrl.isEmpty()
                    ? MediaSave.sanitize(series) + "_EP" + String.format("%02d", ep) + ".ts"
                    : MediaSave.sanitize(series) + ".ts";
            HlsDownloader.download(this, currentUrl, series, fileName,
                    new HlsDownloader.Cb() {
                        @Override
                        public void onProgress(int done, int total, String note) {
                            ui.post(() -> {
                                int pct = total > 0 ? done * 100 / total : 0;
                                btn.setText(pct + "%");
                            });
                        }

                        @Override
                        public void onDone(String path) {
                            ui.post(() -> {
                                downloading = false;
                                btn.setText("已存 ✓");
                                flashTip("已保存到 " + path, 2200);
                                ui.postDelayed(() -> btn.setText("下载"), 3500);
                            });
                        }

                        @Override
                        public void onErr(String msg) {
                            ui.post(() -> {
                                downloading = false;
                                btn.setText("下载");
                                flashTip("下载失败：" + msg, 2000);
                            });
                        }
                    });
        });
        toolRow.addView(btn, chipLp(8));
    }

    /** 工具条最右格横屏按钮：点一次切横屏，再点回竖屏（manifest 已配 configChanges，不重建 Activity） */
    private void buildOrientButton(LinearLayout toolRow) {
        FrameLayout wrap = new FrameLayout(this);
        wrap.setBackground(Ui.stroke(Ui.chipFill(), Ui.chipStroke(), Ui.dp(this, 14), 1));
        wrap.setClickable(true);
        ImageView ic = new ImageView(this);
        ic.setImageResource(R.drawable.ic_fullscreen);
        ic.setColorFilter(Ui.C_TEXT);
        wrap.addView(ic, new FrameLayout.LayoutParams(
                Ui.dp(this, 16), Ui.dp(this, 16), Gravity.CENTER));
        wrap.setOnClickListener(v -> toggleOrientation());
        int side = Ui.dp(this, 32);
        toolRow.addView(wrap, Ui.lp(side, side));
        orientBtn = wrap;
        syncOrientIcon();
    }

    private void toggleOrientation() {
        boolean land = getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE;
        if (land) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        } else {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        }
        overlay.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
    }

    private void syncOrientIcon() {
        if (orientBtn == null) return;
        View child = orientBtn.getChildAt(0);
        if (child != null) child.setRotation(
                getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE
                        ? 90f : 0f);
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // 旋转后系统栏可能重新出现，重新压回去
        WindowInsetsControllerCompat ctl =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        ctl.hide(WindowInsetsCompat.Type.systemBars());
        ctl.setSystemBarsBehavior(
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        syncOrientIcon();
        if (overlay != null) overlay.requestLayout();
    }

    private TextView toolChip(String text) {
        TextView t = Ui.text(this, text, 11f, Ui.C_TEXT);
        t.setGravity(Gravity.CENTER);
        t.setPadding(Ui.dp(this, 9), Ui.dp(this, 6), Ui.dp(this, 9), Ui.dp(this, 6));
        t.setBackground(Ui.stroke(Ui.chipFill(), Ui.chipStroke(), Ui.dp(this, 14), 1));
        t.setClickable(true);
        return t;
    }

    private LinearLayout.LayoutParams chipLp(int leftMargin) {
        LinearLayout.LayoutParams lp = Ui.lp(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = Ui.dp(this, leftMargin);
        return lp;
    }

    private void buildSheet() {
        epSheet = Ui.col(this);
        epSheet.setBackground(Ui.liquidPanel(this, Ui.dp(this, 24)));
        epSheet.setPadding(Ui.dp(this, 16), Ui.dp(this, 14), Ui.dp(this, 16), Ui.dp(this, 18));
        int h = (int) (getResources().getDisplayMetrics().heightPixels * 0.52f);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, h);
        lp.gravity = Gravity.BOTTOM;
        lp.leftMargin = Ui.dp(this, 10);
        lp.rightMargin = Ui.dp(this, 10);
        lp.bottomMargin = Ui.dp(this, 10);
        overlay.addView(epSheet, lp);

        LinearLayout head = Ui.row(this);
        head.addView(Ui.bold(this, "选集", 14f, Ui.C_TEXT));
        View f = new View(this);
        LinearLayout.LayoutParams flp = Ui.lp(0, 1);
        flp.weight = 1f;
        head.addView(f, flp);
        TextView close = Ui.text(this, "关闭", 12f, Ui.C_AMBER);
        close.setOnClickListener(v -> toggleSheet());
        head.addView(close);
        epSheet.addView(head);

        ScrollView scroll = new ScrollView(this);
        LinearLayout.LayoutParams slp = Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT, 0);
        slp.weight = 1f;
        slp.topMargin = Ui.dp(this, 10);
        epSheet.addView(scroll, slp);

        epGrid = new GridLayout(this);
        epGrid.setColumnCount(5);
        scroll.addView(epGrid);

        epSheet.setTranslationY(h + Ui.dp(this, 40));
        epSheet.setVisibility(View.GONE);
    }

    // ------------------------------------------------------------------ 手势

    private void installGestures() {
        gesture = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDoubleTap(@NonNull MotionEvent e) {
                // 双击分区：中间=暂停/播放，左=后退10s，右=快进10s；与单击行为共存
                if (gestureMode == G_NONE && player != null) {
                    int w = overlay.getWidth();
                    float x = e.getX();
                    if (w > 0 && x < w / 3f) {
                        jumpBy(-10_000L, "« 后退 10 秒");
                    } else if (w > 0 && x > w * 2f / 3f) {
                        jumpBy(10_000L, "快进 10 秒 »");
                    } else {
                        boolean playing = player.isPlaying();
                        togglePlay();
                        if (!controlsVisible) flashTip(playing ? "⏸ 已暂停" : "▶ 播放中", 800);
                    }
                }
                return true;
            }

            @Override
            public boolean onSingleTapConfirmed(@NonNull MotionEvent e) {
                if (gestureMode != G_NONE || boosting) return true;
                // 单击只切控制栏；左右快进/倒退改由双击触发，防止误触
                toggleControls();
                return true;
            }
        });

        overlay.setOnTouchListener((v, e) -> {
            gesture.onTouchEvent(e);
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downX = e.getX();
                    downY = e.getRawY();
                    downTime = SystemClock.uptimeMillis();
                    fingerMoved = false;
                    gestureMode = G_NONE;
                    if (speedMenuOpen) {
                        closeSpeedMenu();
                        downY = -1f;
                        return true;
                    }
                    startPos = player != null ? player.getCurrentPosition() : 0L;
                    if (!boostLocked && player != null) ui.postDelayed(longPressTask, LONG_PRESS_MS);
                    break;

                case MotionEvent.ACTION_MOVE: {
                    if (downY < 0f) break;
                    float dx = e.getX() - downX;
                    float dy = downY - e.getRawY();      // 正数 = 上滑
                    float adx = Math.abs(dx), ady = Math.abs(dy);
                    int slop = Ui.dp(this, 8);

                    // 长按已触发 2x 后：上滑锁定倍速、下滑恢复倍速。
                    // 与「未长按时的上下滑调亮度/音量」按 boosting 状态共存、互不干扰。
                    if (boosting) {
                        int lock = lockThresholdPx(e);
                        if (!boostLocked && dy >= lock) {
                            lockBoost();
                        } else if (boostLocked && dy <= -lock) {
                            unlockBoost();
                        }
                        break;
                    }

                    if (gestureMode == G_NONE && (adx > slop || ady > slop)) {
                        ui.removeCallbacks(longPressTask);
                        fingerMoved = true;
                        if (adx > ady * 1.2f && durationMs > 0) {
                            gestureMode = G_SEEK;       // 横向主导 → 拖进度
                        }
                    }

                    if (gestureMode == G_SEEK) {
                        // 灵敏度 0.5：整屏宽滑动 = 半段时长，避免轻微横滑就跳很远
                        long delta = (long) (dx / Math.max(1, overlay.getWidth()) * durationMs * 0.5f);
                        long target = Math.max(0, Math.min(durationMs, startPos + delta));
                        seek.setProgress((int) (target * 1000L / durationMs));
                        timeNow.setText(Ui.hhmmss((int) target));
                        flashTip("⏱ " + Ui.hhmmss((int) target) +
                                "  (" + (delta >= 0 ? "+" : "") + (delta / 1000) + "s)", 1200);
                    }
                    break;
                }

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    ui.removeCallbacks(longPressTask);
                    if (gestureMode == G_SEEK && durationMs > 0) {
                        player.seekTo((long) durationMs * seek.getProgress() / 1000L);
                        vibrate();
                    }
                    if (boosting && !boostLocked) stopBoost();
                    gestureMode = G_NONE;
                    downY = -1f;
                    break;

                default:
                    break;
            }
            return true;
        });
    }

    /** 相对当前位置跳转（带画面提示 + 震动） */
    private void jumpBy(long deltaMs, String label) {
        if (player == null) return;
        long target = Math.max(0L, Math.min(player.getDuration(), player.getCurrentPosition() + deltaMs));
        player.seekTo(target);
        vibrate();
        flashTip(label + "  " + Ui.hhmmss((int) target), 900);
    }

    private void vibrate() {
        overlay.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY,
                HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
    }

    /** 自适应比例：按显示模式在黑底上排布画面 */
    private void applyVideoAspect() {
        if (videoView == null || videoView.getParent() == null) return;
        View host = (View) videoView.getParent();
        int pw = host.getWidth(), ph = host.getHeight();
        if (pw <= 0 || ph <= 0) return;   // 未布局，onSizeChanged 后会再来
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) videoView.getLayoutParams();
        float aspect = 0f;   // 0 = 铺满
        if (displayMode == 0) {
            aspect = videoAspect;             // 自适应：用视频原比例
        } else if (displayMode == 2) {
            aspect = 16f / 9f;                // 强制 16:9
        } else if (displayMode == 3) {
            aspect = 4f / 3f;                 // 强制 4:3
        }
        if (aspect <= 0f) {
            lp.width = ViewGroup.LayoutParams.MATCH_PARENT;
            lp.height = ViewGroup.LayoutParams.MATCH_PARENT;
        } else {
            float containerAspect = pw / (float) ph;
            if (aspect > containerAspect) {
                // 目标比例更宽：以宽为准，上下留黑边
                lp.width = ViewGroup.LayoutParams.MATCH_PARENT;
                lp.height = (int) (pw / aspect);
            } else {
                // 目标比例更高：以高为准，左右留黑边
                lp.height = ViewGroup.LayoutParams.MATCH_PARENT;
                lp.width = (int) (ph * aspect);
            }
        }
        lp.gravity = android.view.Gravity.CENTER;
        videoView.setLayoutParams(lp);
        android.util.Log.d("HGPlayer", "applyVideoAspect mode=" + displayMode
                + " host=" + pw + "x" + ph + " videoAspect=" + videoAspect
                + " -> view " + lp.width + "x" + lp.height);
    }

    // ------------------------------------------------ 顶/底栏视频帧采样（真 Liquid Glass）

    /** 每 300ms 从 TextureView 取一帧，裁出面板对应区域喂给液态玻璃 drawable */
    private final Runnable frameSampler = new Runnable() {
        @Override
        public void run() {
            if (videoView != null && videoView.isAvailable()) {
                Bitmap full = videoView.getBitmap(360, 640);
                if (full != null) {
                    applyFrame(full, topGlass, topPanel, true);
                    applyFrame(full, bottomGlass, bottomPanel, false);
                    full.recycle();
                }
            }
            ui.postDelayed(this, 300L);
        }
    };

    /** 全帧按面板在屏幕中的比例裁剪；延迟 400ms 回收上一帧，保证跨过一次绘制 */
    private void applyFrame(Bitmap full, LiquidGlassDrawable glass, View panel, boolean isTop) {
        if (full == null || glass == null || panel == null || panel.getWidth() <= 0) return;
        int[] loc = new int[2];
        panel.getLocationInWindow(loc);
        int[] vloc = new int[2];
        videoView.getLocationInWindow(vloc);
        int px = loc[0] - vloc[0], py = loc[1] - vloc[1];
        float sx = full.getWidth() / (float) Math.max(1, videoView.getWidth());
        float sy = full.getHeight() / (float) Math.max(1, videoView.getHeight());
        int l = Math.max(0, Math.round(px * sx));
        int t = Math.max(0, Math.round(py * sy));
        int rt = Math.min(full.getWidth(), Math.round((px + panel.getWidth()) * sx));
        int bm = Math.min(full.getHeight(), Math.round((py + panel.getHeight()) * sy));
        if (rt - l <= 4 || bm - t <= 4) return;
        try {
            Bitmap crop = Bitmap.createBitmap(full, l, t, rt - l, bm - t);
            Bitmap old = glass.backdrop();
            glass.setBackdrop(crop);
            if (old != null && !old.isRecycled()) {
                if (isTop) { final Bitmap p = pendingTop; pendingTop = old;
                    if (p != null) ui.postDelayed(() -> { if (!p.isRecycled()) p.recycle(); }, 400L);
                } else { final Bitmap p = pendingBottom; pendingBottom = old;
                    if (p != null) ui.postDelayed(() -> { if (!p.isRecycled()) p.recycle(); }, 400L);
                }
            }
        } catch (Throwable ignore) {
        }
    }

    private void recyclePending() {
        if (pendingTop != null && !pendingTop.isRecycled()) pendingTop.recycle();
        if (pendingBottom != null && !pendingBottom.isRecycled()) pendingBottom.recycle();
    }

    /** 快速滑动时阈值降到 62%，慢速拖动保持完整阈值，兼顾手感与误触 */
    private int lockThresholdPx(MotionEvent e) {
        int base = Ui.dp(this, LOCK_DP);
        if (downY < 0f) return base;
        float dt = Math.max(1f, SystemClock.uptimeMillis() - downTime);
        float speedPx = Math.abs(downY - e.getRawY()) / dt * 1000f;
        return speedPx > Ui.dp(this, 900) ? Math.round(base * 0.62f) : base;
    }

    private final Runnable longPressTask = new Runnable() {
        @Override
        public void run() {
            if (!fingerMoved) startBoost();
        }
    };

    /** 长按起播：倍速平滑推到目标档，提示常驻（手指仍按住） */
    private void startBoost() {
        if (boosting || player == null) return;
        boosting = true;
        boostLocked = false;
        boostSpeed = boostTarget();
        animateSpeed(boostSpeed, 130);
        boostTip.animate().cancel();
        ui.removeCallbacks(hideTipTask);
        boostTip.setAlpha(1f);
        boostTip.setVisibility(View.VISIBLE);
        boostTip.setText(fmtSpeed(boostSpeed) + " 播放中 · 上滑锁定");
    }

    /** 上滑越过阈值：锁定当前档位，提示闪现后自动消失 */
    private void lockBoost() {
        if (boostLocked) return;
        boostLocked = true;
        boosting = true;
        animateSpeed(boostSpeed, 90);
        flashTip("已锁定 " + fmtSpeed(boostSpeed) + " · 下滑恢复", 1100);
        overlay.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
    }

    /** 下滑越过阈值：撤销 2x 回到基础倍速 */
    private void unlockBoost() {
        if (!boostLocked) return;
        boostLocked = false;
        boosting = false;
        animateSpeed(speed, 160);
        flashTip("已恢复 " + fmtSpeed(speed), 900);
        overlay.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
    }

    /** 松手且未锁定：回到基础倍速，并在画面中央给一条恢复提示 */
    private void stopBoost() {
        boolean wasBoosted = boosting;
        boosting = false;
        boostLocked = false;
        animateSpeed(speed, 140);
        if (wasBoosted) {
            flashTip("已恢复 " + fmtSpeed(speed), 900);
        } else {
            hideTipNow();
        }
    }

    private void animateSpeed(final float target, int durationMs) {
        if (player == null) return;
        if (speedAnim != null) speedAnim.cancel();
        final float from = player.getPlaybackParameters().speed;
        if (Math.abs(from - target) < 0.001f) {
            player.setPlaybackSpeed(target);
            return;
        }
        speedAnim = ValueAnimator.ofFloat(from, target);
        speedAnim.setDuration(durationMs);
        speedAnim.addUpdateListener(a -> {
            if (player != null) player.setPlaybackSpeed((float) a.getAnimatedValue());
        });
        speedAnim.start();
    }

    private static String fmtSpeed(float s) {
        if (Math.abs(s - Math.round(s)) < 0.001f) return ((int) s) + ".0x";
        return s + "x";
    }

    /** 短暂提示：显示 holdMs 后自动淡出，不留常驻浮层 */
    private void flashTip(String text, long holdMs) {
        ui.removeCallbacks(hideTipTask);
        boostTip.animate().cancel();
        boostTip.setText(text);
        boostTip.setAlpha(1f);
        boostTip.setVisibility(View.VISIBLE);
        ui.postDelayed(hideTipTask, holdMs);
    }

    private void hideTipNow() {
        ui.removeCallbacks(hideTipTask);
        boostTip.animate().cancel();
        boostTip.setAlpha(0f);
        boostTip.setVisibility(View.GONE);
    }

    private final Runnable hideTipTask = new Runnable() {
        @Override
        public void run() {
            boostTip.animate().alpha(0f).setDuration(170)
                    .withEndAction(() -> boostTip.setVisibility(View.GONE)).start();
        }
    };

    // ------------------------------------------------------------------ 数据

    private void loadEpisodes() {
        Api.detail(videoId, new Api.Cb<Models.Detail>() {
            @Override
            public void ok(Models.Detail d) {
                episodes.clear();
                episodes.addAll(d.episodes);
                cover = d.info.cover;
                epCount = d.info.epCount > 0 ? d.info.epCount : d.episodes.size();
                if (dramaTitle.isEmpty()) dramaTitle = d.info.title;
                buildEpisodeGrid();
            }

            @Override
            public void err(String msg) {
                // 选集拉取失败不影响起播
            }
        });
    }

    private void buildEpisodeGrid() {
        epGrid.removeAllViews();
        int col = 5;
        int gap = Ui.dp(this, 8);
        int widthPx = getResources().getDisplayMetrics().widthPixels - Ui.dp(this, 52);
        int cellW = (widthPx - gap * (col - 1)) / col;
        for (int i = 0; i < episodes.size(); i++) {
            final int num = episodes.get(i).ep;
            TextView chip = Ui.text(this, String.valueOf(num), 12f,
                    num == ep ? Ui.onAmber() : Ui.C_TEXT);
            chip.setGravity(Gravity.CENTER);
            chip.setPadding(0, Ui.dp(this, 9), 0, Ui.dp(this, 9));
            chip.setBackground(num == ep
                    ? Ui.round(Ui.C_AMBER, Ui.dp(this, 10))
                    : Ui.stroke(Ui.chipFill(), Ui.chipStroke(), Ui.dp(this, 10), 1));
            chip.setOnClickListener(v -> {
                toggleSheet();
                switchTo(num);
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

    private void prepare() {
        statusTip.setVisibility(View.VISIBLE);
        statusTip.setText("正在获取播放地址…");
        statusTip.setOnClickListener(null);
        final int targetEp = ep;
        restoredPosition = false;
        if (!streamUrl.isEmpty()) {
            currentUrl = streamUrl;
            startStream(streamUrl);
            return;
        }
        Api.playback(videoId, targetEp, new Api.Cb<String>() {
            @Override
            public void ok(String url) {
                currentUrl = url;
                durationMs = 0;
                player.setMediaItem(MediaItem.fromUri(url));
                player.setPlaybackSpeed(boostLocked ? boostSpeed : speed);
                player.prepare();
                player.setPlayWhenReady(true);
                epLabel.setText("第 " + targetEp + " 集");
            }

            @Override
            public void err(String msg) {
                statusTip.setVisibility(View.VISIBLE);
                statusTip.setText("地址获取失败：" + msg + "\n点击重试");
                statusTip.setOnClickListener(v -> prepare());
            }
        });
    }

    private void startStream(String url) {
        currentUrl = url;
        durationMs = 0;
        player.setMediaItem(MediaItem.fromUri(url));
        player.setPlaybackSpeed(boostLocked ? boostSpeed : speed);
        player.prepare();
        player.setPlayWhenReady(true);
    }

    private void switchTo(int target) {
        if (!streamUrl.isEmpty()) return;
        saveHistory();
        ep = target;
        startMs = 0;
        epLabel.setText("第 " + ep + " 集");
        buildEpisodeGrid();
        prepare();
    }

    /** 上一集/下一集图标按钮（32dp，与全屏键同规格） */
    private void buildEpisodeStepButton(LinearLayout toolRow, final boolean prev) {
        FrameLayout wrap = new FrameLayout(this);
        wrap.setBackground(Ui.stroke(Ui.chipFill(), Ui.chipStroke(), Ui.dp(this, 14), 1));
        wrap.setClickable(true);
        ImageView ic = new ImageView(this);
        ic.setImageResource(prev ? R.drawable.ic_skip_prev : R.drawable.ic_skip_next);
        ic.setColorFilter(Ui.C_TEXT);
        wrap.addView(ic, new FrameLayout.LayoutParams(
                Ui.dp(this, 15), Ui.dp(this, 15), Gravity.CENTER));
        wrap.setOnClickListener(v -> {
            if (prev) playPrev();
            else playNext();
        });
        int side = Ui.dp(this, 32);
        LinearLayout.LayoutParams lp = Ui.lp(side, side);
        lp.leftMargin = Ui.dp(this, 6);
        toolRow.addView(wrap, lp);
    }

    private void playPrev() {
        if (!streamUrl.isEmpty()) return;
        if (episodes.isEmpty()) {
            if (ep > 1) switchTo(ep - 1);
            else Toast.makeText(this, "已是第一集", Toast.LENGTH_SHORT).show();
            return;
        }
        int idx = -1;
        for (int i = 0; i < episodes.size(); i++) {
            if (episodes.get(i).ep == ep) {
                idx = i;
                break;
            }
        }
        if (idx > 0) {
            switchTo(episodes.get(idx - 1).ep);
        } else {
            Toast.makeText(this, "已是第一集", Toast.LENGTH_SHORT).show();
        }
    }

    private void playNext() {
        if (!streamUrl.isEmpty()) {
            // 直链模式没有下一集，播完停在最后一帧
            if (player != null) player.pause();
            return;
        }
        if (episodes.isEmpty()) {
            switchTo(ep + 1);
            return;
        }
        int idx = -1;
        for (int i = 0; i < episodes.size(); i++) {
            if (episodes.get(i).ep == ep) {
                idx = i;
                break;
            }
        }
        if (idx >= 0 && idx + 1 < episodes.size()) {
            switchTo(episodes.get(idx + 1).ep);
        } else {
            Toast.makeText(this, "已是最后一集", Toast.LENGTH_SHORT).show();
        }
    }

    // ------------------------------------------------------------- 交互/控制

    private void togglePlay() {
        if (player.isPlaying()) player.pause();
        else player.play();
    }

    private void syncCenter() {
        centerBtn.setImageResource(player.isPlaying() ? R.drawable.ic_pause : R.drawable.ic_play);
    }

    private void setCenter(float alpha, int durationMs) {
        centerWrap.animate().alpha(alpha).setDuration(durationMs)
                .withEndAction(() -> centerWrap.setVisibility(alpha > 0f ? View.VISIBLE : View.INVISIBLE))
                .start();
    }

    private void toggleControls() {
        if (speedMenuOpen) {
            closeSpeedMenu();
            return;
        }
        controlsVisible = !controlsVisible;
        float target = controlsVisible ? 1f : 0f;
        topPanel.animate().alpha(target).setDuration(180).start();
        bottomPanel.animate().alpha(target).setDuration(180).start();
        setCenter(target, 180);
        if (controlsVisible) scheduleHide();
    }

    private void scheduleHide() {
        ui.removeCallbacks(hideTask);
        if (player != null && player.isPlaying() && !sheetOpen) {
            ui.postDelayed(hideTask, 4200);
        }
    }

    private final Runnable hideTask = new Runnable() {
        @Override
        public void run() {
            if (sheetOpen) return;
            if (speedMenuOpen) return;
            controlsVisible = false;
            topPanel.animate().alpha(0f).setDuration(220).start();
            bottomPanel.animate().alpha(0f).setDuration(220).start();
            setCenter(0f, 220);
        }
    };

    private void toggleSheet() {
        sheetOpen = !sheetOpen;
        if (sheetOpen) {
            ui.removeCallbacks(hideTask);
            epSheet.setVisibility(View.VISIBLE);
            epSheet.animate().translationY(0f).setDuration(240).start();
            topPanel.animate().alpha(0f).setDuration(160).start();
            bottomPanel.animate().alpha(0f).setDuration(160).start();
            setCenter(0f, 160);
        } else {
            int h = epSheet.getHeight() == 0
                    ? (int) (getResources().getDisplayMetrics().heightPixels * 0.52f)
                    : epSheet.getHeight();
            epSheet.animate().translationY(h + Ui.dp(this, 40)).setDuration(200)
                    .withEndAction(() -> epSheet.setVisibility(View.GONE)).start();
            controlsVisible = true;
            topPanel.animate().alpha(1f).setDuration(160).start();
            bottomPanel.animate().alpha(1f).setDuration(160).start();
            setCenter(1f, 160);
            scheduleHide();
        }
    }

    private void startTicker() {
        ui.post(new Runnable() {
            @Override
            public void run() {
                if (player != null) {
                    long pos = player.getCurrentPosition();
                    if (durationMs <= 0) durationMs = (int) Math.max(0, player.getDuration());
                    if (!dragging && durationMs > 0) {
                        seek.setProgress((int) (pos * 1000 / durationMs));
                        timeNow.setText(Ui.hhmmss(pos));
                        timeTotal.setText(Ui.hhmmss(durationMs));
                    }
                    long buf = player.getBufferedPercentage();
                    seek.setSecondaryProgress((int) (buf * 10));
                    if (++tick % 6 == 0 && player.isPlaying()) saveHistory();
                }
                ui.postDelayed(this, 500);
            }
        });
    }

    private void saveHistory() {
        if (!streamUrl.isEmpty()) return;
        if (player == null || videoId <= 0) return;
        long pos = player.getCurrentPosition();
        long dur = player.getDuration();
        if (dur <= 0) return;
        if (pos < 3000) return;
        Store.get(this).recordHistory(videoId, dramaTitle, cover, ep, pos, dur, epCount);
    }

    @Override
    public void onBackPressed() {
        if (sheetOpen) {
            toggleSheet();
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected void onPause() {
        super.onPause();
        saveHistory();
        if (player != null) player.pause();
    }

    @Override
    protected void onDestroy() {
        saveHistory();
        ui.removeCallbacksAndMessages(null);
        recyclePending();
        if (topGlass != null) topGlass.setBackdrop(null);
        if (bottomGlass != null) bottomGlass.setBackdrop(null);
        if (speedAnim != null) speedAnim.cancel();
        if (player != null) {
            player.release();
            player = null;
        }
        super.onDestroy();
    }
}
