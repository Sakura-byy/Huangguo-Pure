package com.huangguo.pure;

import android.content.Context;
import android.graphics.Color;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * 搜索页：自带头部（标题 + 关闭），玻璃搜索框 + 热搜 + 结果网格。
 * 头部内置在页面里，不再用浮层关闭按钮压在搜索框上。
 */
public class SearchPage extends FrameLayout {

    private final PagedGrid grid;
    private final LinearLayout hotBar;
    private final EditText input;
    private final TextView hint;
    /** 空搜索状态下的榜单区：搜索页下半屏不再空着 */
    private final LinearLayout rankBox;
    private final ScrollView rankScroll;
    private String keyword = "";
    private Runnable onClose;
    /** 去排行页：由宿主关掉搜索层并切到排行 tab */
    private Runnable onOpenRank;

    public SearchPage(Context c, View backdrop) {
        super(c);
        LinearLayout root = Ui.col(c);
        addView(root, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // ---- 头部：标题 + 关闭 ----
        LinearLayout head = Ui.row(c);
        head.setPadding(Ui.dp(c, 18), Ui.dp(c, 10), Ui.dp(c, 14), Ui.dp(c, 2));
        root.addView(head, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(c, 52)));

        TextView title = Ui.bold(c, "搜索", 20f, Ui.C_TEXT);
        head.addView(title);

        TextView chip = Ui.text(c, "全站检索", 9.5f, Ui.C_AMBER);
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(Ui.dp(c, 7), Ui.dp(c, 3), Ui.dp(c, 7), Ui.dp(c, 3));
        chip.setBackground(Ui.stroke(Ui.accentFill(), Ui.accentStroke(), Ui.dp(c, 8), 1));
        LinearLayout.LayoutParams clp = Ui.lp(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.leftMargin = Ui.dp(c, 9);
        head.addView(chip, clp);

        View gap = new View(c);
        LinearLayout.LayoutParams glp = Ui.lp(0, 1);
        glp.weight = 1f;
        head.addView(gap, glp);

        GlassSurface close = new GlassSurface(c, 18, Ui.darkGlass ? 0x4A0A0E15 : 0x1A1A2332, 8);
        if (backdrop != null) close.bind(backdrop);
        ImageView ci = new ImageView(c);
        ci.setImageResource(R.drawable.ic_close);
        ci.setColorFilter(Ui.C_TEXT);
        close.addView(ci, new FrameLayout.LayoutParams(
                Ui.dp(c, 16), Ui.dp(c, 16), Gravity.CENTER));
        close.setClickable(true);
        close.setOnClickListener(v -> {
            if (onClose != null) onClose.run();
        });
        head.addView(close, Ui.lp(Ui.dp(c, 36), Ui.dp(c, 36)));

        // ---- 搜索框 ----
        LinearLayout box = Ui.row(c);
        box.setBackground(Ui.panelGlass(c, Ui.dp(c, 22)));
        box.setPadding(Ui.dp(c, 14), 0, Ui.dp(c, 6), 0);
        LinearLayout.LayoutParams boxLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(c, 44));
        boxLp.leftMargin = Ui.dp(c, 16);
        boxLp.rightMargin = Ui.dp(c, 16);
        boxLp.topMargin = Ui.dp(c, 6);
        root.addView(box, boxLp);

        ImageView icon = new ImageView(c);
        icon.setImageResource(R.drawable.ic_search);
        icon.setColorFilter(Ui.C_SUB);
        box.addView(icon, Ui.lp(Ui.dp(c, 17), Ui.dp(c, 17)));

        input = new EditText(c);
        input.setHint("搜剧名 / 标签 / 作者");
        input.setHintTextColor(Ui.darkGlass ? 0xFF7F8896 : 0xFF7A879B);
        input.setTextColor(Ui.titleText());
        input.setTextSize(13.5f);
        input.setSingleLine(true);
        input.setBackground(null);
        input.setPadding(Ui.dp(c, 10), 0, Ui.dp(c, 10), 0);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        LinearLayout.LayoutParams ip = Ui.lp(0, ViewGroup.LayoutParams.MATCH_PARENT);
        ip.weight = 1f;
        box.addView(input, ip);

        TextView go = Ui.bold(c, "搜索", 12f, Ui.onAmber());
        go.setGravity(Gravity.CENTER);
        go.setBackground(Ui.round(Ui.C_AMBER, Ui.dp(c, 16)));
        go.setPadding(Ui.dp(c, 16), Ui.dp(c, 7), Ui.dp(c, 16), Ui.dp(c, 7));
        box.addView(go, Ui.lp(ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dp(c, 30)));

        // ---- 热搜 ----
        HorizontalScrollView hs = new HorizontalScrollView(c);
        hs.setHorizontalScrollBarEnabled(false);
        hs.setClipToPadding(false);
        hs.setPadding(Ui.dp(c, 14), Ui.dp(c, 10), Ui.dp(c, 14), Ui.dp(c, 2));
        hotBar = Ui.row(c);
        hs.addView(hotBar);
        root.addView(hs, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        FrameLayout box2 = new FrameLayout(c);
        root.addView(box2, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        grid = new PagedGrid(c, 2, v -> DetailActivity.open(c, v.id));
        grid.setSource((page, cb) -> Api.search(keyword, page, cb));
        // 起手是榜单态：结果网格先藏起来
        grid.setVisibility(View.GONE);
        box2.addView(grid, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // ---- 空白区：引用排行榜（热播榜 TOP10），搜索出结果后自动隐藏 ----
        rankScroll = new ScrollView(c);
        rankScroll.setClipToPadding(false);
        rankScroll.setPadding(0, Ui.dp(c, 4), 0, Ui.dp(c, 96));
        box2.addView(rankScroll, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        rankBox = Ui.col(c);
        rankScroll.addView(rankBox, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        hint = Ui.text(c, "输入关键词开始搜索", 13f, Ui.C_DIM);
        hint.setGravity(Gravity.CENTER);
        hint.setVisibility(GONE);
        box2.addView(hint, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER));

        go.setOnClickListener(v -> doSearch());
        input.setOnEditorActionListener((v, actionId, ev) -> {
            doSearch();
            return true;
        });

        loadHot();
        loadRank();
    }

    /** 搜索页空白区引用「热播榜」：TOP10 列表，点行直接进详情 */
    private void loadRank() {
        Context c = getContext();
        Api.rank(Api.RANK_HOT, new Api.Cb<List<Models.Video>>() {
            @Override
            public void ok(List<Models.Video> list) {
                rankBox.removeAllViews();
                if (list.isEmpty()) return;
                int n = Math.min(10, list.size());

                LinearLayout head = Ui.row(c);
                head.setPadding(Ui.dp(c, 18), Ui.dp(c, 14), Ui.dp(c, 18), Ui.dp(c, 2));
                rankBox.addView(head);

                head.addView(accentBar(c));
                head.addView(Ui.bold(c, "热播榜 · TOP" + n, 14.5f, Ui.C_TEXT));
                View gap = new View(c);
                LinearLayout.LayoutParams glp = Ui.lp(0, 1);
                glp.weight = 1f;
                head.addView(gap, glp);
                TextView all = Ui.text(c, "去排行页 ›", 11f, Ui.C_DIM);
                all.setPadding(Ui.dp(c, 6), Ui.dp(c, 4), 0, Ui.dp(c, 4));
                all.setClickable(true);
                all.setOnClickListener(v -> {
                    if (onOpenRank != null) {
                        onOpenRank.run();       // 宿主负责：先关搜索层再切 tab
                    } else if (c instanceof MainActivity) {
                        ((MainActivity) c).selectPrimary(2);
                    }
                });
                head.addView(all);

                for (int i = 0; i < n; i++) {
                    final Models.Video v = list.get(i);
                    final int rank = i + 1;
                    LinearLayout row = Ui.row(c);
                    row.setPadding(Ui.dp(c, 18), Ui.dp(c, 7), Ui.dp(c, 18), Ui.dp(c, 7));
                    row.setClickable(true);
                    row.setOnClickListener(x -> DetailActivity.open(c, v.id));
                    rankBox.addView(row, Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT));

                    TextView no = Ui.bold(c, String.valueOf(rank), 12.5f,
                            rank <= 3 ? Ui.onAmber() : Ui.C_SUB);
                    no.setGravity(Gravity.CENTER);
                    no.setBackground(rank <= 3
                            ? Ui.round(Ui.C_GOLD, Ui.dp(c, 11))
                            : Ui.subtle(c, Ui.dp(c, 11)));
                    row.addView(no, Ui.lp(Ui.dp(c, 22), Ui.dp(c, 22)));

                    FrameLayout thumb = new FrameLayout(c);
                    thumb.setClipToOutline(true);
                    thumb.setBackground(new GlassDrawable(Ui.dp(c, 8)));
                    AspectImageView cover = new AspectImageView(c);
                    cover.setRatio(1.32f);
                    thumb.addView(cover, new FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                    ImageLoader.load(cover, v.cover);
                    LinearLayout.LayoutParams tlp = Ui.lp(Ui.dp(c, 40), Ui.dp(c, 53));
                    tlp.leftMargin = Ui.dp(c, 10);
                    row.addView(thumb, tlp);

                    LinearLayout texts = Ui.col(c);
                    LinearLayout.LayoutParams xtp = Ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT);
                    xtp.weight = 1f;
                    xtp.leftMargin = Ui.dp(c, 10);
                    row.addView(texts, xtp);
                    TextView title = Ui.bold(c, v.title, 12.5f, Ui.C_TEXT);
                    title.setMaxLines(1);
                    title.setEllipsize(TextUtils.TruncateAt.END);
                    texts.addView(title);
                    StringBuilder sb = new StringBuilder();
                    if (v.score > 0) sb.append(v.scoreText()).append(" 分");
                    if (v.epCount > 0) {
                        if (sb.length() > 0) sb.append("  ·  ");
                        sb.append(v.epText());
                    }
                    if (!v.tags.isEmpty()) {
                        if (sb.length() > 0) sb.append("  ·  ");
                        sb.append(v.tags);
                    }
                    TextView meta = Ui.text(c, sb.toString(), 10f, Ui.C_DIM);
                    meta.setMaxLines(1);
                    meta.setEllipsize(TextUtils.TruncateAt.END);
                    LinearLayout.LayoutParams mp = Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT);
                    mp.topMargin = Ui.dp(c, 3);
                    texts.addView(meta, mp);
                }
            }

            @Override
            public void err(String msg) {
                // 榜单拉不到就保持空白，不挡搜索
            }
        });
    }

    /** 标题前的金色竖条 */
    private View accentBar(Context c) {
        View v = new View(c);
        v.setBackground(Ui.round(Ui.C_GOLD, Ui.dp(c, 2)));
        LinearLayout.LayoutParams lp = Ui.lp(Ui.dp(c, 3), Ui.dp(c, 14));
        lp.rightMargin = Ui.dp(c, 8);
        v.setLayoutParams(lp);
        return v;
    }

    public void setOnClose(Runnable r) {
        onClose = r;
    }

    /**
     * 沉浸式：整页铺到屏幕边缘，状态栏/导航栏的安全区垫在页面内部。
     * 状态栏那一块露出的是外层极光，不再是黑条。
     */
    public void applyInsets(int top, int bottom) {
        setPadding(0, Math.max(0, top), 0, Math.max(0, bottom));
        requestLayout();
    }

    public void setOnOpenRank(Runnable r) {
        onOpenRank = r;
    }

    public void refresh() {
        loadHot();
        loadRank();
    }

    private void loadHot() {
        Api.hotWords(new Api.Cb<List<String>>() {
            @Override
            public void ok(List<String> words) {
                buildHot(words);
            }

            @Override
            public void err(String msg) {
                List<String> fallback = new ArrayList<>();
                fallback.add("短剧");
                fallback.add("漫剧");
                buildHot(fallback);
            }
        });
    }

    private void buildHot(List<String> words) {
        hotBar.removeAllViews();
        Context c = getContext();
        for (String w : words) {
            TextView t = Chrome.chip(c, w, false);
            LinearLayout.LayoutParams lp = Ui.lp(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = Ui.dp(c, 8);
            t.setOnClickListener(v -> {
                input.setText(w);
                input.setSelection(w.length());
                doSearch();
            });
            hotBar.addView(t, lp);
        }
    }

    private void doSearch() {
        String kw = input.getText().toString().trim();
        if (kw.isEmpty()) {
            hint.setVisibility(View.GONE);
            rankScroll.setVisibility(View.VISIBLE);
            grid.setVisibility(View.GONE);
            return;
        }
        keyword = kw;
        hint.setVisibility(View.GONE);
        rankScroll.setVisibility(View.GONE);
        grid.setVisibility(View.VISIBLE);
        InputMethodManager imm = (InputMethodManager) getContext()
                .getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(input.getWindowToken(), 0);
        grid.clear();
        grid.reload();
    }
}
