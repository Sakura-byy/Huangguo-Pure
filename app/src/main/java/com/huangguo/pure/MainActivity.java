package com.huangguo.pure;

import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

/**
 * 主壳：极光背景 + Liquid Glass Dock + 四个页面 + 常驻右上角搜索。
 * 专题与博客并进「发现」页，Dock 只留 首页 / 发现 / 排行 / 我的。
 */
public class MainActivity extends AppCompatActivity {

    private static final String[] LABELS = {"首页", "发现", "排行", "我的"};
    private static final int[] ICONS = {R.drawable.ic_home, R.drawable.ic_discover,
            R.drawable.ic_rank, R.drawable.ic_user};

    private FrameLayout root;
    private FrameLayout container;
    private SwipeFrame pageSwipe;
    private FrameLayout pageHost;
    private FrameLayout navWrap;
    private androidx.compose.ui.platform.ComposeView dockView;
    private com.huangguo.pure.glass.DockState dockState;
    private DockBackdrop dockBackdrop;
    private FrameLayout searchLayer;
    private SearchPage searchPage;
    private boolean searchOpen = false;
    private int insetTop = 0;
    private int insetBottom = 0;

    private final View[] pages = new View[LABELS.length];
    /** 包了下拉刷新的外层（可刷新页面才有），按页缓存，切回来复用同一个实例 */
    private final View[] wrappers = new View[LABELS.length];
    private int current = -1;

    /** 任何触摸都让玻璃采样进全速档：滚动列表时折射逐帧跟手，松手后自动退避 */
    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        // 抬手之后还有惯性滑行，手指已经离开屏幕、不再有 MOVE 事件。
        // 只在触摸期间采样的话，一次大力滑动后玻璃里的背景就定格在抬手那一刻 ——
        // 所以抬手后再续一段「逐帧采样」，覆盖惯性滑行直到画面停住。
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                // 起手点落在 Dock 上：拖动期间背景是静止的，停掉逐帧重采，
                // 把主线程让给 Compose 的跟手动画（视觉上完全一样）
                if (dockBackdrop != null) {
                    dockTouch = inDock(ev);
                    dockBackdrop.setPaused(dockTouch);
                }
                holdAurora(900L);
            case MotionEvent.ACTION_MOVE:
                // 手指按在 Dock 上拖动时背景是静止的、采样已经暂停，
                // 再逐帧排一个 pulse 只会在主线程上加一帧一次的回调，白耗
                if (!dockTouch) kickPulse(120L);
                holdAurora(900L);
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                // 手势结束：先解除暂停并补采一帧，再开收尾的采样窗口
                if (dockTouch) {
                    dockTouch = false;
                    if (dockBackdrop != null) {
                        dockBackdrop.setPaused(false);
                        dockBackdrop.refresh();
                    }
                }
                kickPulse(2200L);
                // 抬手后还有惯性滑行：惯性期间极光继续冻结，停稳再恢复
                holdAurora(1200L);
                break;
            default:
                break;
        }
        return super.dispatchTouchEvent(ev);
    }

    /** 极光恢复重绘的延时任务：滚动 / 惯性期间不重绘那三团全屏柔光 */
    private final Runnable auroraIdle = () -> Aurora.setGlobalBusy(false);

    private void holdAurora(long ms) {
        if (root == null) return;
        Aurora.setGlobalBusy(true);
        root.removeCallbacks(auroraIdle);
        root.postDelayed(auroraIdle, ms);
    }

    /** 本次手势是否按在 Dock 上（拖动期间暂停背景采样） */
    private boolean dockTouch;

    /** 触摸点是否落在 Dock 区域内 */
    private boolean inDock(MotionEvent ev) {
        if (navWrap == null || navWrap.getHeight() <= 0) return false;
        int[] loc = new int[2];
        navWrap.getLocationOnScreen(loc);
        float x = ev.getRawX(), y = ev.getRawY();
        return y >= loc[1] && y <= loc[1] + navWrap.getHeight()
                && x >= loc[0] && x <= loc[0] + navWrap.getWidth();
    }

    /** 逐帧采样窗口的截止时间（uptimeMillis） */
    private long pulseUntil;
    private boolean pulseRunning;

    private final Runnable pulse = new Runnable() {
        @Override
        public void run() {
            if (dockBackdrop == null || dockView == null) {
                pulseRunning = false;
                return;
            }
            dockBackdrop.poke();
            if (SystemClock.uptimeMillis() < pulseUntil) {
                dockView.postOnAnimation(this);
            } else {
                pulseRunning = false;
            }
        }
    };

    /** 打开一段逐帧采样窗口；重复调用只延长截止时间，不会叠出多个循环 */
    private void kickPulse(long ms) {
        pulseUntil = Math.max(pulseUntil, SystemClock.uptimeMillis() + ms);
        if (!pulseRunning && dockView != null) {
            pulseRunning = true;
            dockView.postOnAnimation(pulse);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Api.init(this);
        Api.syncMirrors(null);
        Theme.init(this);
        Ui.applyPalette(Theme.isDark(this));
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);
        paintSystemBarIcons();

        root = new FrameLayout(this);
        root.setBackgroundColor(Ui.C_BG);
        root.setClipChildren(false);
        root.setClipToPadding(false);
        setContentView(root);

        root.addView(new Aurora(this), new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        container = new FrameLayout(this);
        container.setClipChildren(false);
        root.addView(container, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        // container 自己带一层极光，Dock 采样它才有颜色可折射
        container.addView(new Aurora(this), new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        // 一级页容器外面再套一层横滑：首页/发现/排行/我的之间可以直接左右划过去。
        // 起手点落在二级 SwipeFrame 上时让位给子层（发现的 剧集/专题/博客 等），
        // 子层滑到边界自己会回调 selectPrimary。
        pageSwipe = new SwipeFrame(this);
        pageSwipe.deferToInnerSwipe(true);
        pageSwipe.setOnSwipe(dir -> {
            int target = current + dir;
            if (target >= 0 && target < pages.length) select(target);
        });
        container.addView(pageSwipe, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        pageHost = new FrameLayout(this);
        pageHost.setClipChildren(false);
        pageSwipe.addView(pageHost, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        navWrap = new FrameLayout(this);
        navWrap.setClipChildren(false);
        FrameLayout.LayoutParams np = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        np.gravity = Gravity.BOTTOM;
        root.addView(navWrap, np);

        buildNav();

        ViewCompat.setOnApplyWindowInsetsListener(root, new androidx.core.view.OnApplyWindowInsetsListener() {
            @Override
            public WindowInsetsCompat onApplyWindowInsets(View v, @NonNull WindowInsetsCompat insets) {
                Insets sys = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                insetTop = sys.top;
                insetBottom = sys.bottom;
                // 不再给 container 让状态栏：每个页面的顶栏本身覆盖到状态栏顶端，
                // 上面那一块由液态玻璃填满，页面内容从顶栏下沿开始。
                container.setPadding(0, 0, 0, 0);
                navWrap.setPadding(0, 0, 0, Math.max(sys.bottom, Ui.dp(MainActivity.this, 8)));
                // 搜索页是沉浸式的：层铺满整屏，安全区垫在页面里，状态栏那一块由极光填满
                if (searchPage != null) {
                    searchPage.applyInsets(sys.top, Math.max(sys.bottom, Ui.dp(MainActivity.this, 8)));
                }
                return insets;
            }
        });

        // 恢复到离开时的 tab：跟随系统切换深浅色时 recreate 重建，
        // 不带恢复就会落回首页，看起来像「跳回主页」
        int savedTab = 0;
        if (savedInstanceState != null) {
            savedTab = savedInstanceState.getInt("hg.tab", 0);
        }
        if (savedTab < 0 || savedTab >= LABELS.length) savedTab = 0;
        select(savedTab);

        // 首帧后再补采样：页面内容是网络异步回来的，第一次布局时还没有图，
        // 玻璃会记住那张「空底」位图 —— 表现就是刚进 App 时 tab 栏是纯色，划一下才变。
        dockView.post(() -> kickPulse(3000L));

        // 每天第一次启动检查更新（设置里的开关关掉就不跑）
        Update.checkDaily(this);
        // 每天做一次缓存体检：缓存目录只增不减的话，用久了会悄悄吃满几百兆
        dailyCacheUpkeep();
        // 已登录且本地数据有变动：顺手把收藏/历史推给账号（未登录直接返回）
        Sync.pushIfDirty(this);

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (searchOpen) {
                    closeSearch();
                } else if (current != 0) {
                    select(0);
                } else {
                    finish();
                }
            }
        });
    }

    /**
     * 每日一次的缓存维护（对齐 Coil / Glide 的定期维护思路）：
     * 图片磁盘缓存按 LRU 收到上限的 80%，内存缓存直接放掉。
     * 24 小时内只跑一次，放在启动后 1.5 秒的空闲窗口里，不挡首屏。
     */
    private void dailyCacheUpkeep() {
        try {
            final android.content.SharedPreferences sp =
                    getSharedPreferences("hg_client", MODE_PRIVATE);
            long last = sp.getLong("cache_upkeep_at", 0L);
            if (System.currentTimeMillis() - last < 24L * 3600_000L) return;
            sp.edit().putLong("cache_upkeep_at", System.currentTimeMillis()).apply();
            ImageLoader.init(this);
            root.postDelayed(() -> {
                try {
                    ImageLoader.trimDiskNow();
                } catch (Throwable ignored) {
                    // 维护失败不影响使用
                }
            }, 1500L);
        } catch (Throwable ignored) {
            // 读不到偏好就跳过
        }
    }

    private void buildNav() {
        // 液态玻璃 Dock：Java 壳体（ComposeView）+ 官方 backdrop 库的 Compose 玻璃栏
        dockView = new androidx.compose.ui.platform.ComposeView(this);
        dockView.setBackgroundColor(Color.TRANSPARENT);
        dockState = new com.huangguo.pure.glass.DockState();
        dockBackdrop = new DockBackdrop();
        dockBackdrop.setSource(container);

        // view 高度 = 栏体 64dp + 上下各 14dp 溢出余量。
        // 按压时指示器放大到 78dp，上下各越出栏体 7dp；高光、阴影再外扩几像素。
        // 这些越界部分必须落在 view 之内，否则被 Android 的绘制边界裁掉 ——
        // 表现就是「胶囊只往下超出、顶部被切平」。
        int barH = Ui.dp(this, 64);
        int overflow = Ui.dp(this, 14);
        FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, barH + overflow * 2);
        blp.leftMargin = Ui.dp(this, 18);
        blp.rightMargin = Ui.dp(this, 18);
        blp.bottomMargin = 0;
        navWrap.addView(dockView, blp);

        dockBackdrop.setTarget(dockView);
        applyDockTheme();
        java.util.List<Integer> icons = new java.util.ArrayList<>();
        for (int r : ICONS) icons.add(r);
        com.huangguo.pure.glass.GlassBridgeKt.installGlassDock(
                dockView, dockState, dockBackdrop,
                java.util.Arrays.asList(LABELS), icons, this::select);
        dockView.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) ->
                dockBackdrop.refresh());
    }

    /** 主题切换 / 重建时同步 Dock 配色（Compose 状态，主线程写） */
    private void applyDockTheme() {
        if (dockState == null) return;
        dockState.setLight(!Ui.darkGlass);
        dockState.setIconTint(Ui.C_TEXT);
        dockState.setLabelColor(Ui.C_TEXT);
        dockState.setAccent(Ui.accent());
    }

    /** 供子页面在自身二级/三级滑到边界时请求切换一级页 */
    public void selectPrimary(int idx) {
        if (idx < 0 || idx >= pages.length) return;
        select(idx);
    }

    @Override
    protected void onDestroy() {
        if (dockBackdrop != null) {
            dockBackdrop.destroy();
            dockBackdrop = null;
        }
        super.onDestroy();
    }

    private void select(int idx) {
        // Compose 侧选中态与 Java 页面对齐：点击 / 拖动 / 返回键都走这里
        if (dockState != null) dockState.setSelected(idx);
        if (idx == current) return;
        current = idx;
        if (pages[idx] == null) {
            switch (idx) {
                case 0:
                    pages[idx] = new HomePage(this, () -> select(1), this::openSearch);
                    break;
                case 1:
                    pages[idx] = new CategoryPage(this);
                    break;
                case 2:
                    pages[idx] = new RankPage(this);
                    break;
                default:
                    MinePage mine = new MinePage(this);
                    mine.setThemeRequest(this::showThemeMenu);
                    pages[idx] = mine;
                    break;
            }
        }
        if (wrappers[idx] == null) {
            View page = pages[idx];
            if (page instanceof PullRefresh.Refreshable) {
                PullRefresh pr = new PullRefresh(this);
                pr.attach(page, (PullRefresh.Refreshable) page);
                wrappers[idx] = pr;
            } else {
                wrappers[idx] = page;
            }
        }
        pageHost.removeAllViews();
        pageHost.addView(wrappers[idx], new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        // 换页后立刻重采一次玻璃背景，否则 Dock 里还留着上一页的折影像；
        // 再续 1.5s 逐帧采样，等封面 / 列表在这段时间里陆续到位
        if (dockBackdrop != null) {
            pageHost.post(() -> dockBackdrop.refresh());
            kickPulse(1500L);
            holdAurora(1500L);
        }
    }

    // ------------------------------------------------------------------ 主题

    private void paintSystemBarIcons() {
        androidx.core.view.WindowInsetsControllerCompat ic =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        // 浅色主题下状态栏底是亮的，图标要变深色才看得见
        ic.setAppearanceLightStatusBars(!Ui.darkGlass);
        ic.setAppearanceLightNavigationBars(!Ui.darkGlass);
    }

    /** 重建时记住当前 tab，避免跟随系统切换深浅色后落回首页 */
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt("hg.tab", Math.max(0, current));
    }

    /** 跟随系统模式下，系统切深浅色时重建整个 UI */
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (Theme.isDark(this) != Ui.darkGlass) {
            // 先换调色板再重建：只 recreate 不换色，会先渲染出一帧旧配色的花屏
            Ui.retheme(this);
        }
    }

    /** 液态玻璃风格的外观下拉菜单：跟随系统 / 浅色 / 深色 */
    private void showThemeMenu() {
        final FrameLayout layer = new FrameLayout(this);
        layer.setClickable(true);
        layer.setBackgroundColor(Ui.darkGlass ? 0x66000000 : 0x4D101826);
        layer.setOnClickListener(v -> dismissThemeMenu(layer));

        GlassSurface card = new GlassSurface(this, 24, Ui.glassTint(), 9);
        card.bind(container);
        card.setElevation(Ui.dp(this, 12));

        LinearLayout col = Ui.col(this);
        col.setPadding(Ui.dp(this, 8), Ui.dp(this, 6), Ui.dp(this, 8), Ui.dp(this, 8));
        card.addView(col, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // 头部：标题 + 副标
        LinearLayout head = Ui.row(this);
        head.setPadding(Ui.dp(this, 14), Ui.dp(this, 12), Ui.dp(this, 14), Ui.dp(this, 8));
        head.addView(Ui.bold(this, "外观", 15f, Ui.C_TEXT));
        TextView sub = Ui.text(this, "  显示模式", 10.5f, Ui.C_DIM);
        head.addView(sub);
        col.addView(head);

        int[] modes = {Theme.AUTO, Theme.LIGHT, Theme.DARK};
        String[] glyphs = {"◐", "○", "●"};
        for (int i = 0; i < modes.length; i++) {
            final int m = modes[i];
            boolean active = m == Theme.mode();

            LinearLayout row = Ui.row(this);
            row.setPadding(Ui.dp(this, 10), Ui.dp(this, 11), Ui.dp(this, 10), Ui.dp(this, 11));
            row.setBackground(active
                    ? Ui.round(0x1FF5B942, Ui.dp(this, 12))
                    : Ui.round(0x00000000, Ui.dp(this, 12)));

            // 模式符号圆徽
            TextView glyph = Ui.bold(this, glyphs[i], 14f, active ? Ui.C_AMBER : Ui.C_SUB);
            glyph.setGravity(Gravity.CENTER);
            glyph.setBackground(Ui.subtle(this, Ui.dp(this, 16)));
            row.addView(glyph, Ui.lp(Ui.dp(this, 30), Ui.dp(this, 30)));

            TextView name = Ui.text(this, Theme.label(m), 13.5f,
                    active ? Ui.C_AMBER : Ui.C_TEXT);
            LinearLayout.LayoutParams np = Ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT);
            np.weight = 1f;
            np.leftMargin = Ui.dp(this, 12);
            row.addView(name, np);

            if (active) {
                TextView check = Ui.bold(this, "✓", 13.5f, Ui.C_AMBER);
                row.addView(check, Ui.lp(ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT));
            }

            row.setClickable(true);
            row.setOnClickListener(v -> {
                dismissThemeMenu(layer);
                if (m != Theme.mode()) {
                    Theme.setMode(this, m);
                    // 走统一入口：先换调色板再重建，整页重新加载（页面内的列表 / 卡片一起重画）
                    Ui.retheme(this);
                }
            });
            col.addView(row);
        }

        FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(
                Ui.dp(this, 244), ViewGroup.LayoutParams.WRAP_CONTENT);
        cp.gravity = Gravity.CENTER;
        layer.addView(card, cp);

        addLayer(layer);
        card.post(() -> {
            card.setAlpha(0f);
            card.setScaleX(0.90f);
            card.setScaleY(0.90f);
            card.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(190)
                    .setInterpolator(new android.view.animation.OvershootInterpolator(0.9f))
                    .start();
        });
    }

    private void dismissThemeMenu(View layer) {
        layer.animate().alpha(0f).setDuration(130)
                .withEndAction(() -> root.removeView(layer)).start();
    }

    // ------------------------------------------------------------------ 搜索

    private void openSearch() {
        if (searchOpen) return;
        searchOpen = true;
        if (searchLayer == null) {
            searchLayer = new FrameLayout(this);
            // 底色 + 极光铺满整屏（含状态栏那一条），搜索页自己再让出状态栏
            searchLayer.setBackgroundColor(0xF505070C);
            searchLayer.addView(new Aurora(this), new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

            searchPage = new SearchPage(this, container);
            searchPage.setOnClose(this::closeSearch);
            // 搜索页里的「去排行页」：先收起搜索层，再切到排行 tab
            searchPage.setOnOpenRank(() -> {
                closeSearch();
                selectPrimary(2);
            });
            searchLayer.addView(searchPage, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

            // false：不给层本身加内边距，交给 SearchPage，极光才能顶到屏幕最上沿
            addLayer(searchLayer, false);
        }
        searchLayer.setVisibility(View.VISIBLE);
        searchLayer.setAlpha(0f);
        searchLayer.setTranslationY(Ui.dp(this, 16));
        searchLayer.animate().alpha(1f).translationY(0f).setDuration(200).start();
    }

    private void addLayer(View layer) {
        addLayer(layer, true);
    }

    /**
     * @param padSelf true：安全区垫在层自身（弹层内边距）；
     *                false：层铺满整屏，安全区交给层里的内容（搜索页那种沉浸式页面）
     */
    private void addLayer(View layer, final boolean padSelf) {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        root.addView(layer, lp);
        // 新加的层不会自动收到一次 insets 分发，先用已缓存的值把安全区垫上，
        // 否则搜索页头部会直接顶到状态栏
        layer.setPadding(0, padSelf ? insetTop : 0, 0,
                padSelf ? Math.max(insetBottom, Ui.dp(this, 8)) : 0);
        ViewCompat.setOnApplyWindowInsetsListener(layer, (v, insets) -> {
            Insets sys = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            insetTop = sys.top;
            insetBottom = sys.bottom;
            if (padSelf) {
                v.setPadding(0, sys.top, 0, Math.max(sys.bottom, Ui.dp(this, 8)));
            } else {
                v.setPadding(0, 0, 0, 0);
                if (searchPage != null) {
                    searchPage.applyInsets(sys.top, Math.max(sys.bottom, Ui.dp(this, 8)));
                }
            }
            return insets;
        });
        if (!padSelf && searchPage != null) {
            searchPage.applyInsets(insetTop, Math.max(insetBottom, Ui.dp(this, 8)));
        }
        layer.requestApplyInsets();
    }

    private void closeSearch() {
        if (!searchOpen || searchLayer == null) return;
        searchOpen = false;
        searchLayer.animate().alpha(0f).translationY(Ui.dp(this, 16)).setDuration(170)
                .withEndAction(() -> searchLayer.setVisibility(View.GONE)).start();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 设置页改过外观：静态调色板已经换了，但这一屏的控件还是旧色，整页重建一次
        if (Ui.darkGlass != Theme.isDark(this)) {
            Ui.retheme(this);
            return;
        }
        kickPulse(900L);
        int idx = current;
        if (idx >= 0 && idx < pages.length && pages[idx] instanceof MinePage) {
            ((MinePage) pages[idx]).refresh();
        }
    }
}
