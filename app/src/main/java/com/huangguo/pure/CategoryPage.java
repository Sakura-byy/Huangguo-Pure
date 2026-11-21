package com.huangguo.pure;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.view.animation.OvershootInterpolator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 发现页：顶部分段在「剧集 / 专题 / 博客」之间切换。
 *
 * 站点 /api/site/categories/video 里有两条分类（AI魔改、AI换脸）的列表接口
 * 直接返回 {"data":[],"status":0,"msg":"内容已下线"}，所以按 route 过滤掉，
 * 不再把它们摆进 App。
 */
public class CategoryPage extends FrameLayout {

    private static final Set<String> DEAD_ROUTES = new HashSet<>(
            Arrays.asList("ai-mogai", "ai-huanlian"));

    private static final String[] SEGS = {"剧集", "专题", "博客"};

    private final LinearLayout segBar;
    private final TextView[] segViews = new TextView[SEGS.length];
    private View underline;
    private final View[] views = new View[SEGS.length];
    /** 包了下拉刷新的外层，按段缓存，避免同一页面被重复 addView */
    private final View[] wraps = new View[SEGS.length];

    private final SwipeFrame body;
    private final LinearLayout seriesBox;
    private final LinearLayout chipBar;
    private final PagedGrid grid;
    private final List<TextView> chips = new ArrayList<>();

    private List<Models.Category> cats = new ArrayList<>();
    private int current = -1;
    private String cateRoute = "ai-duanju";

    public CategoryPage(Context c) {
        super(c);
        LinearLayout root = Ui.col(c);
        addView(root, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        root.addView(Chrome.titleBar(c, "发现", "短剧 · 漫剧"));

        FrameLayout segWrap = new FrameLayout(c);
        segWrap.setClipChildren(false);
        LinearLayout.LayoutParams swLp = Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        swLp.bottomMargin = Ui.dp(c, 8);
        root.addView(segWrap, swLp);

        segBar = Ui.row(c);
        segBar.setPadding(Ui.dp(c, 18), Ui.dp(c, 2), Ui.dp(c, 18), Ui.dp(c, 9));
        segWrap.addView(segBar, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        for (int i = 0; i < SEGS.length; i++) buildSeg(i);

        underline = new View(c);
        underline.setBackground(Ui.round(Ui.C_AMBER, Ui.dp(c, 2)));
        underline.setAlpha(0f);
        FrameLayout.LayoutParams ulp = new FrameLayout.LayoutParams(
                Ui.dp(c, 22), Ui.dp(c, 3));
        ulp.gravity = Gravity.BOTTOM | Gravity.START;
        ulp.leftMargin = Ui.dp(c, 18);
        ulp.bottomMargin = Ui.dp(c, 3);
        segWrap.addView(underline, ulp);
        segBar.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) ->
                post(() -> placeUnderline(false)));

        body = new SwipeFrame(c);
        body.setOnSwipe(dir -> {
            // 剧集段带三级 chips（短剧/漫剧…）：先切三级，到边界才交给二级/一级
            if (current == 0 && cats.size() > 1) {
                int idx = chipIndex();
                int nextChip = idx + dir;
                if (nextChip >= 0 && nextChip < cats.size()) {
                    selectChipAt(nextChip);
                    return;
                }
                if (dir > 0 && nextChip >= cats.size()) {
                    select(1, false);   // 三级到头 → 切二级「专题」
                    return;
                }
                if (dir < 0 && nextChip < 0) edgeSwipe(-1); // 三级到头(左) → 切一级「首页」
                return;
            }
            int next = current + dir;
            if (next < 0 || next >= SEGS.length) {
                edgeSwipe(dir);          // 二级到头 → 切一级页面
                return;
            }
            select(next, false);
        });
        root.addView(body, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        // 剧集视图：分类 chips 直接铺满一行（每个等宽），不再横滚
        seriesBox = Ui.col(c);
        chipBar = Ui.row(c);
        chipBar.setPadding(Ui.dp(c, 16), Ui.dp(c, 0), Ui.dp(c, 16), Ui.dp(c, 8));
        seriesBox.addView(chipBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        grid = new PagedGrid(c, 2, v -> DetailActivity.open(c, v.id));
        seriesBox.addView(grid, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        select(0, true);
        loadCategories();
    }

    private void buildSeg(final int index) {
        Context c = getContext();
        final TextView label = Ui.text(c, SEGS[index], 15f, Ui.C_SUB);
        label.setGravity(Gravity.CENTER);
        label.setPadding(Ui.dp(c, 2), Ui.dp(c, 4), Ui.dp(c, 2), Ui.dp(c, 4));
        // 二级 tab 等宽铺满整行（与排行榜页统一）
        LinearLayout.LayoutParams lp = Ui.lp(0,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.weight = 1f;
        label.setLayoutParams(lp);
        label.setOnClickListener(v -> select(index, false));
        segViews[index] = label;
        segBar.addView(label);
    }

    /** 下划线宽度跟着当前标签走，位置在布局完成后按标签实际宽度算 */
    private void placeUnderline(boolean animate) {
        if (current < 0) return;
        TextView t = segViews[current];
        if (t == null || t.getWidth() <= 0) return;
        float target = t.getLeft() + (t.getWidth() - underline.getWidth()) / 2f;
        underline.animate().cancel();
        if (animate) {
            underline.animate().translationX(target).setDuration(220)
                    .setInterpolator(new OvershootInterpolator(1.02f)).start();
        } else {
            underline.setTranslationX(target);
        }
        underline.animate().alpha(1f).setDuration(160).start();
    }

    private void select(int index, boolean force) {
        if (index == current && !force) return;
        current = index;
        for (int i = 0; i < SEGS.length; i++) {
            boolean active = i == index;
            segViews[i].setTextColor(active ? Ui.C_TEXT : Ui.C_SUB);
            segViews[i].setTypeface(active ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
            segViews[i].setTextSize(active ? 16f : 15f);
        }
        post(() -> placeUnderline(true));
        if (views[index] == null) {
            if (index == 0) {
                views[index] = seriesBox;
            } else if (index == 1) {
                views[index] = new TopicPage(getContext(), true);
            } else {
                views[index] = new BlogPage(getContext(), true);
            }
        }
        if (wraps[index] == null) {
            View v = views[index];
            if (v instanceof PullRefresh.Refreshable) {
                PullRefresh pr = new PullRefresh(getContext());
                pr.attach(v, (PullRefresh.Refreshable) v);
                wraps[index] = pr;
            } else {
                wraps[index] = v;
            }
        }
        body.removeAllViews();
        body.addView(wraps[index], new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private void loadCategories() {
        Api.categories(new Api.Cb<List<Models.Category>>() {
            @Override
            public void ok(List<Models.Category> data) {
                List<Models.Category> live = new ArrayList<>();
                for (Models.Category cat : data) {
                    if (cat.route != null && DEAD_ROUTES.contains(cat.route)) continue;
                    if (cat.name != null && cat.name.trim().isEmpty()) continue;
                    live.add(cat);
                }
                if (live.isEmpty()) {
                    live.add(fallback("短剧", "ai-duanju"));
                    live.add(fallback("漫剧", "ai-manju"));
                }
                java.util.Collections.sort(live, (a, b) -> rankOf(a.route) - rankOf(b.route));
                cats = live;
                cateRoute = cats.get(0).route == null || cats.get(0).route.isEmpty()
                        ? "ai-duanju" : cats.get(0).route;
                buildChips();
                loadList();
            }

            @Override
            public void err(String msg) {
                cats = new ArrayList<>();
                cats.add(fallback("短剧", "ai-duanju"));
                cats.add(fallback("漫剧", "ai-manju"));
                cateRoute = "ai-duanju";
                buildChips();
                loadList();
            }
        });
    }

    /** 站内习惯顺序：短剧 → 漫剧 → 其它 */
    private static int rankOf(String route) {
        if ("ai-duanju".equals(route)) return 0;
        if ("ai-manju".equals(route)) return 1;
        return 2;
    }

    private static Models.Category fallback(String name, String route) {
        Models.Category c = new Models.Category();
        c.name = name;
        c.route = route;
        return c;
    }

    private void buildChips() {
        chipBar.removeAllViews();
        chips.clear();
        Context c = getContext();
        for (int i = 0; i < cats.size(); i++) {
            final Models.Category cat = cats.get(i);
            boolean active = cat.route.equals(cateRoute);
            final TextView t = Chrome.chip(c, cat.name, active);
            // 三级 chips 等宽铺满整行
            LinearLayout.LayoutParams lp = Ui.lp(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.weight = 1f;
            lp.rightMargin = Ui.dp(c, 8);
            final int chipIdx = i;
            t.setOnClickListener(v -> selectChipAt(chipIdx));
            chips.add(t);
            chipBar.addView(t, lp);
        }
    }

    /** 当前选中 chip 的下标（按 route 匹配） */
    private int chipIndex() {
        for (int i = 0; i < cats.size(); i++) {
            if (cats.get(i).route != null && cats.get(i).route.equals(cateRoute)) return i;
        }
        return 0;
    }

    /** 选中第 index 个三级 chip：更新高亮 + 重拉列表（点击与左右滑动共用） */
    /** 二/三级都滑到边界后，再滑就切到相邻一级页：左→首页，右→排行 */
    private void edgeSwipe(int dir) {
        if (getContext() instanceof MainActivity) {
            ((MainActivity) getContext()).selectPrimary(dir < 0 ? 0 : 2);
        }
    }

    private void selectChipAt(int index) {
        android.util.Log.d("Category", "selectChipAt(" + index + ") cats=" + cats.size());
        if (index < 0 || index >= cats.size()) return;
        Models.Category cat = cats.get(index);
        if (cat.route == null || cat.route.equals(cateRoute)) return;
        cateRoute = cat.route;
        Context c = getContext();
        for (int k = 0; k < cats.size(); k++) {
            boolean on = cats.get(k).route.equals(cateRoute);
            TextView tv = chips.get(k);
            tv.setTextColor(on ? Ui.onAmber() : Ui.C_TEXT);
            tv.setTypeface(on ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
            tv.setBackground(on
                    ? Ui.round(Ui.C_AMBER, Ui.dp(c, 18))
                    : Ui.stroke(0x14FFFFFF, 0x26FFFFFF, Ui.dp(c, 18), 1));
        }
        loadList();
    }

    private void loadList() {
        final String route = cateRoute;
        grid.clear();
        grid.setStatus("加载中…");
        grid.setSource((page, cb) -> Api.listByRoute(route, page, cb));
        grid.reload();
    }

    public void refresh() {
        if (cats.isEmpty()) loadCategories();
        else if (current == 0) loadList();
    }
}
