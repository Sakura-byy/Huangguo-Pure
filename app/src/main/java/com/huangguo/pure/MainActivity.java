package com.huangguo.pure;

import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Bundle;
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
 * 专题与博客并进「分类」页，Dock 只留 首页 / 分类 / 排行 / 我的。
 */
public class MainActivity extends AppCompatActivity {

    private static final String[] LABELS = {"首页", "分类", "排行", "我的"};
    private static final int[] ICONS = {R.drawable.ic_home, R.drawable.ic_grid,
            R.drawable.ic_rank, R.drawable.ic_user};

    private FrameLayout root;
    private FrameLayout container;
    private FrameLayout pageHost;
    private FrameLayout navWrap;
    private androidx.compose.ui.platform.ComposeView dockView;
    private com.huangguo.pure.glass.DockState dockState;
    private DockBackdrop dockBackdrop;
    private FrameLayout searchFab;
    private FrameLayout searchLayer;
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
        // 采样放到下一帧，别在触摸分发路径里做软件绘制，否则 tap 会被主线程阻塞吃掉
        if (dockBackdrop != null && dockView != null && !backdropPokeScheduled) {
            backdropPokeScheduled = true;
            dockView.postDelayed(() -> {
                backdropPokeScheduled = false;
                if (dockBackdrop != null) dockBackdrop.poke();
            }, 120);
        }
        return super.dispatchTouchEvent(ev);
    }

    private boolean backdropPokeScheduled = false;

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
        pageHost = new FrameLayout(this);
        pageHost.setClipChildren(false);
        container.addView(pageHost, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        buildSearchFab();
        root.addView(searchFab, fabParams(0));

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
                container.setPadding(0, sys.top, 0, 0);
                navWrap.setPadding(0, 0, 0, Math.max(sys.bottom, Ui.dp(MainActivity.this, 8)));
                FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) searchFab.getLayoutParams();
                lp.topMargin = sys.top + Ui.dp(MainActivity.this, 9);
                searchFab.setLayoutParams(lp);
                if (searchLayer != null) searchLayer.setPadding(0, sys.top, 0, 0);
                return insets;
            }
        });

        select(0);

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

    private FrameLayout.LayoutParams fabParams(int topMargin) {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                Ui.dp(this, 40), Ui.dp(this, 40));
        lp.gravity = Gravity.TOP | Gravity.END;
        lp.topMargin = topMargin;
        lp.rightMargin = Ui.dp(this, 14);
        return lp;
    }

    private void buildSearchFab() {
        // 液态玻璃搜索键：浅色白玻璃+墨边，深色深玻璃+白边，避免采样背景把它染深
        searchFab = new FrameLayout(this);
        searchFab.setBackground(Ui.darkGlass
                ? new GlassDrawable(Ui.dp(this, 20), 0x9A1C2333, 0x8A0A0E15,
                        0x900A0E15, 0x66FFFFFF)
                : new GlassDrawable(Ui.dp(this, 20), 0xF5FFFFFF, 0xEFF3F8FD,
                        0xE8EDF4, 0x591A2332));
        ImageView ic = new ImageView(this);
        ic.setImageResource(R.drawable.ic_search);
        ic.setColorFilter(Ui.C_TEXT);
        searchFab.addView(ic, new FrameLayout.LayoutParams(
                Ui.dp(this, 19), Ui.dp(this, 19), Gravity.CENTER));
        searchFab.setClickable(true);
        searchFab.setOnClickListener(v -> openSearch());
    }

    private void buildNav() {
        // 液态玻璃 Dock：Java 壳体（ComposeView）+ 官方 backdrop 库的 Compose 玻璃栏
        dockView = new androidx.compose.ui.platform.ComposeView(this);
        dockView.setBackgroundColor(Color.TRANSPARENT);
        dockState = new com.huangguo.pure.glass.DockState();
        dockBackdrop = new DockBackdrop();
        dockBackdrop.setSource(container);

        FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 64));
        blp.leftMargin = Ui.dp(this, 18);
        blp.rightMargin = Ui.dp(this, 18);
        blp.bottomMargin = Ui.dp(this, 10);
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
        dockState.setAccent(Ui.darkGlass ? 0xFF0091FF : 0xFF0088FF);
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
                    pages[idx] = new HomePage(this, () -> select(1));
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
        if (searchFab != null && !searchOpen) searchFab.setVisibility(View.VISIBLE);
        // 换页后立刻重采一次玻璃背景，否则 Dock 里还留着上一页的折影像
        if (dockBackdrop != null) pageHost.post(() -> dockBackdrop.refresh());
    }

    // ------------------------------------------------------------------ 主题

    private void paintSystemBarIcons() {
        androidx.core.view.WindowInsetsControllerCompat ic =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        // 浅色主题下状态栏底是亮的，图标要变深色才看得见
        ic.setAppearanceLightStatusBars(!Ui.darkGlass);
        ic.setAppearanceLightNavigationBars(!Ui.darkGlass);
    }

    /** 跟随系统模式下，系统切深浅色时重建整个 UI */
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (Theme.mode() == Theme.AUTO && Theme.isDark(this) != Ui.darkGlass) {
            recreate();
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
                    recreate();
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
            searchLayer.setBackgroundColor(0xF505070C);
            searchLayer.addView(new Aurora(this), new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

            SearchPage page = new SearchPage(this, container);
            page.setOnClose(this::closeSearch);
            searchLayer.addView(page, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

            addLayer(searchLayer);
        }
        searchLayer.setVisibility(View.VISIBLE);
        searchLayer.setAlpha(0f);
        searchLayer.setTranslationY(Ui.dp(this, 16));
        searchLayer.animate().alpha(1f).translationY(0f).setDuration(200).start();
        searchFab.setVisibility(View.INVISIBLE);
    }

    private void addLayer(View layer) {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        root.addView(layer, lp);
        // 新加的层不会自动收到一次 insets 分发，先用已缓存的值把安全区垫上，
        // 否则搜索页头部会直接顶到状态栏
        layer.setPadding(0, insetTop, 0, Math.max(insetBottom, Ui.dp(this, 8)));
        ViewCompat.setOnApplyWindowInsetsListener(layer, (v, insets) -> {
            Insets sys = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            insetTop = sys.top;
            insetBottom = sys.bottom;
            v.setPadding(0, sys.top, 0, Math.max(sys.bottom, Ui.dp(this, 8)));
            return insets;
        });
        layer.requestApplyInsets();
    }

    private void closeSearch() {
        if (!searchOpen || searchLayer == null) return;
        searchOpen = false;
        searchLayer.animate().alpha(0f).translationY(Ui.dp(this, 16)).setDuration(170)
                .withEndAction(() -> searchLayer.setVisibility(View.GONE)).start();
        searchFab.setVisibility(View.VISIBLE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        int idx = current;
        if (idx >= 0 && idx < pages.length && pages[idx] instanceof MinePage) {
            ((MinePage) pages[idx]).refresh();
        }
    }
}
