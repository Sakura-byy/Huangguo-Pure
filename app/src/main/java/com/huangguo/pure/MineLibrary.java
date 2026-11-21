package com.huangguo.pure;

import android.content.Context;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 我的资料库：我的收藏 / 观看历史 / 我的喜欢 / 离线缓存 四个分段共用一套列表。
 *
 * 不再各开一个页面 —— 点 chip 就地换数据源。
 * 长按任意一条进入多选，手指上下划可以连续勾选；多选时在「离线缓存」右边出现「全部删除」。
 */
public class MineLibrary extends FrameLayout {

    private static final int MODE_FAV = 0;
    private static final int MODE_HIS = 1;
    private static final int MODE_LIKE = 2;
    private static final int MODE_CACHE = 3;

    private static final String[] LABELS = {"我的收藏", "观看历史", "我的喜欢", "离线缓存"};

    /** 统一行模型：不管数据来自本地库、云端还是缓存表，都归一成这一种 */
    static class Item {
        /** 业务类型：10 视频收藏 / 11 博客收藏 / 12 专题收藏 / 20 历史 / 30 喜欢 / 40 缓存 */
        int kind;
        long id;
        String title = "";
        String cover = "";
        String meta = "";
        int ep;
        boolean hasNew;
    }

    private final LinearLayout chipBar;
    private final TextView[] chips = new TextView[LABELS.length];
    private final FrameLayout clearAll;
    private final RecyclerView rv;
    private final TextView empty;
    private final Adapter adapter = new Adapter();
    private final List<Item> items = new ArrayList<>();
    private final Set<Integer> selected = new LinkedHashSet<>();

    private int mode = MODE_FAV;
    private SwipeFrame swipeHost;
    private boolean selecting;
    private int lastToggled = -1;

    public MineLibrary(Context c) {
        super(c);
        LinearLayout root = Ui.col(c);
        addView(root, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        chipBar = Ui.row(c);
        chipBar.setPadding(Ui.dp(c, 14), Ui.dp(c, 6), Ui.dp(c, 6), Ui.dp(c, 8));
        // 左边 chips 可横滚，右边删除图标固定在边缘（留 14dp 安全区）
        android.widget.HorizontalScrollView chipScroll = new android.widget.HorizontalScrollView(c);
        chipScroll.setHorizontalScrollBarEnabled(false);
        chipScroll.setClipToPadding(false);
        chipScroll.addView(chipBar);

        LinearLayout chipRow = Ui.row(c);
        LinearLayout.LayoutParams cslp = Ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT);
        cslp.weight = 1f;
        chipRow.addView(chipScroll, cslp);
        root.addView(chipRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        for (int i = 0; i < LABELS.length; i++) {
            final int idx = i;
            TextView t = segment(LABELS[i], i == mode);
            LinearLayout.LayoutParams lp = Ui.lp(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = Ui.dp(c, 6);
            t.setOnClickListener(v -> selectMode(idx));
            chips[i] = t;
            chipBar.addView(t, lp);
        }

        // 批量删除：圆框内的垃圾桶图标（多选时出现在「离线缓存」右边同一行）
        clearAll = new FrameLayout(c);
        // 强调色圆钮：和「立即播放」「确定下载」同一套琥珀
        clearAll.setBackground(Ui.round(Ui.C_AMBER, Ui.dp(c, 17)));
        clearAll.setVisibility(GONE);
        ImageView trash = new ImageView(c);
        trash.setImageResource(R.drawable.ic_trash);
        trash.setColorFilter(Ui.onAmber());
        clearAll.addView(trash, new FrameLayout.LayoutParams(
                Ui.dp(c, 16), Ui.dp(c, 16), Gravity.CENTER));
        clearAll.setClickable(true);
        clearAll.setOnClickListener(v -> confirmClear());
        LinearLayout.LayoutParams calp = Ui.lp(Ui.dp(c, 34), Ui.dp(c, 34));
        calp.leftMargin = Ui.dp(c, 6);
        calp.rightMargin = Ui.dp(c, 14);   // 与屏幕右缘留出安全区
        chipRow.addView(clearAll, calp);

        FrameLayout box = new FrameLayout(c);
        root.addView(box, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        rv = new RecyclerView(c);
        rv.setLayoutManager(new LinearLayoutManager(c));
        rv.setAdapter(adapter);
        rv.setClipToPadding(false);
        rv.setPadding(0, Ui.dp(c, 2), 0, Ui.dp(c, 96));
        rv.setOverScrollMode(OVER_SCROLL_NEVER);

        // 左右滑动快速切分段：我的收藏 → 观看历史 → 我的喜欢 → 离线缓存
        SwipeFrame swipe = new SwipeFrame(c);
        swipeHost = swipe;
        swipe.setOnSwipe(dir -> {
            if (selecting) return;         // 多选中不切页，避免误触
            int next = mode + dir;
            if (next >= 0 && next < LABELS.length) selectMode(next);
        });
        swipe.addView(rv, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        box.addView(swipe, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        empty = Ui.text(c, "", 13f, Ui.C_DIM);
        empty.setGravity(Gravity.CENTER);
        box.addView(empty, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER));

        // 多选时由 RecyclerView 接管触摸：按住不放上下划过哪条就勾哪条
        rv.addOnItemTouchListener(new RecyclerView.SimpleOnItemTouchListener() {
            @Override
            public boolean onInterceptTouchEvent(@NonNull RecyclerView r, @NonNull MotionEvent e) {
                if (!selecting) return false;
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        lastToggled = -1;
                        toggleAt(r, e.getX(), e.getY());
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        toggleAt(r, e.getX(), e.getY());
                        return true;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        lastToggled = -1;
                        return true;
                    default:
                        return false;
                }
            }
        });

        load(MODE_FAV);
    }

    // ------------------------------------------------------------------ 分段

    private TextView segment(String text, boolean active) {
        // 字号与内边距压缩过：4 个分段 + 「全部删除」要在同一行里放得下
        TextView t = Ui.text(getContext(), text, 11.5f, active ? 0xFF1A1206 : Ui.C_TEXT);
        t.setGravity(Gravity.CENTER);
        t.setPadding(Ui.dp(getContext(), 11), Ui.dp(getContext(), 7),
                Ui.dp(getContext(), 11), Ui.dp(getContext(), 7));
        t.setBackground(active
                ? Ui.round(Ui.C_AMBER, Ui.dp(getContext(), 16))
                : Ui.subtle(getContext(), Ui.dp(getContext(), 16)));
        return t;
    }

    private void selectMode(int m) {
        if (m == mode) return;
        exitSelect();
        mode = m;
        paintChips();
        load(m);
    }

    private void paintChips() {
        Context c = getContext();
        for (int i = 0; i < chips.length; i++) {
            boolean on = i == mode;
            chips[i].setTextColor(on ? 0xFF1A1206 : Ui.C_TEXT);
            chips[i].setBackground(on
                    ? Ui.round(Ui.C_AMBER, Ui.dp(c, 16))
                    : Ui.subtle(c, Ui.dp(c, 16)));
        }
    }

    public void refresh() {
        load(mode);
    }

    // ------------------------------------------------------------------ 数据

    private void load(int m) {
        items.clear();
        selected.clear();
        empty.setText("加载中…");
        empty.setVisibility(VISIBLE);
        adapter.notifyDataSetChanged();

        if (m == MODE_FAV) loadFav();
        else if (m == MODE_CACHE) loadCache();
        else if (m == MODE_HIS) loadHistory();
        else loadLikes();
    }

    private void loadFav() {
        for (Object o : Store.get(getContext()).favorites()) {
            Item it = new Item();
            if (o instanceof Models.Video) {
                Models.Video v = (Models.Video) o;
                it.kind = 10;
                it.id = v.id;
                it.title = v.title;
                it.cover = v.cover;
                StringBuilder sb = new StringBuilder();
                if (v.score > 0) sb.append(v.scoreText()).append(" 分");
                if (v.epCount > 0) {
                    if (sb.length() > 0) sb.append("  ·  ");
                    sb.append(v.epText());
                }
                if (v.seenEp > 0 && v.epCount > v.seenEp) {
                    sb.append("  ·  有更新 ").append(v.epCount - v.seenEp).append(" 集");
                    it.hasNew = true;
                }
                it.meta = sb.toString();
                it.ep = v.epCount;
            } else if (o instanceof Models.Post) {
                Models.Post p = (Models.Post) o;
                it.kind = 11;
                it.id = p.id;
                it.title = p.title;
                it.cover = p.cover;
                it.meta = "博客收藏";
            } else if (o instanceof Models.Topic) {
                Models.Topic t = (Models.Topic) o;
                it.kind = 12;
                it.id = t.id;
                it.title = t.title;
                it.cover = t.cover;
                it.meta = "专题收藏";
            } else {
                continue;
            }
            items.add(it);
        }
        finish("还没有收藏，去详情页点 ★");
    }

    private void loadCache() {
        // 文件探活已由 MinePage.refresh() 在后台线程跑完；这里只读记录，先把列表画满
        for (Store.Cached c : Store.get(getContext()).cachedList()) {
            Item it = new Item();
            it.kind = 40;
            it.id = c.id;
            it.title = c.title;
            it.cover = c.cover;
            it.ep = c.count();
            it.meta = "已缓存 " + c.count() + " 集  ·  第 " + c.epsText() + " 集";
            items.add(it);
        }
        finish("还没有离线缓存，去详情页选集下载");
    }

    private void loadHistory() {
        if (!Auth.loggedIn()) {
            localHistory("未登录，显示本机记录");
            return;
        }
        Api.cloudHistory(1, new Api.Cb<Models.HistoryPage>() {
            @Override
            public void ok(Models.HistoryPage hp) {
                for (Models.HistoryItem h : hp.items) {
                    Item it = new Item();
                    it.kind = 20;
                    it.id = h.videoId;
                    it.title = h.title;
                    it.cover = h.cover;
                    it.ep = h.lastEp;
                    StringBuilder sb = new StringBuilder();
                    sb.append("上次看到第 ").append(Math.max(1, h.lastEp)).append(" 集");
                    if (h.totalEp > 0) sb.append(" / 全 ").append(h.totalEp).append(" 集");
                    if (!h.category.isEmpty()) sb.append("  ·  ").append(h.category);
                    if (!h.updatedAt.isEmpty()) sb.append("  ·  ").append(h.updatedAt);
                    it.meta = sb.toString();
                    items.add(it);
                }
                if (items.isEmpty()) {
                    localHistory("账号里还没有历史记录");
                    return;
                }
                finish("还没有观看记录");
            }

            @Override
            public void err(String msg) {
                localHistory("云端历史不可用：" + msg);
            }
        });
    }

    private void localHistory(String why) {
        items.clear();
        for (Store.History h : Store.get(getContext()).history()) {
            if (h.type != Store.T_VIDEO) continue;
            Item it = new Item();
            it.kind = 20;
            it.id = h.id;
            it.title = h.title;
            it.cover = h.cover;
            it.ep = h.ep;
            it.meta = "上次看到第 " + Math.max(1, h.ep) + " 集" + (h.epCount > 0
                    ? " / 全 " + h.epCount + " 集" : "");
            items.add(it);
        }
        finish(why);
    }

    private void loadLikes() {
        if (!Auth.loggedIn()) {
            finish("登录后可以查看账号里的喜欢");
            return;
        }
        Api.me(new Api.Cb<org.json.JSONObject>() {
            @Override
            public void ok(org.json.JSONObject data) {
                final Models.Me me = Models.me(data);
                if (me.likeVideoIds.isEmpty()) {
                    finish("还没有喜欢的剧集，播放页点「喜欢」");
                    return;
                }
                // 用详情把标题 / 封面补齐（最多 20 部，避免一次性打太多接口）
                int n = 0;
                for (final Long vid : me.likeVideoIds) {
                    if (n++ >= 20) break;
                    Api.detail(vid, new Api.Cb<Models.Detail>() {
                        @Override
                        public void ok(Models.Detail d) {
                            Item it = new Item();
                            it.kind = 30;
                            it.id = vid;
                            it.title = d.info.title;
                            it.cover = d.info.cover;
                            it.meta = "已喜欢  ·  " + d.info.epText();
                            it.hasNew = false;
                            items.add(it);
                            adapter.notifyDataSetChanged();
                            empty.setVisibility(items.isEmpty() ? VISIBLE : GONE);
                        }

                        @Override
                        public void err(String msg) {
                        }
                    });
                }
            }

            @Override
            public void err(String msg) {
                finish("喜欢列表拉取失败：" + msg);
            }
        });
    }

    private void finish(String emptyText) {
        adapter.notifyDataSetChanged();
        empty.setText(emptyText);
        empty.setVisibility(items.isEmpty() ? VISIBLE : GONE);
        clearAll.setVisibility(selecting ? VISIBLE : GONE);
    }

    // ------------------------------------------------------------------ 多选

    private void enterSelect(int pos) {
        selecting = true;
        if (swipeHost != null) swipeHost.setSwipeEnabled(false);
        selected.clear();
        selected.add(pos);
        clearAll.setVisibility(VISIBLE);
        adapter.notifyDataSetChanged();
    }

    private void exitSelect() {
        if (!selecting) return;
        selecting = false;
        if (swipeHost != null) swipeHost.setSwipeEnabled(true);
        selected.clear();
        clearAll.setVisibility(GONE);
        adapter.notifyDataSetChanged();
    }

    private void toggleAt(RecyclerView r, float x, float y) {
        View child = r.findChildViewUnder(x, y);
        if (child == null) return;
        int pos = r.getChildAdapterPosition(child);
        if (pos == RecyclerView.NO_POSITION || pos >= items.size()) return;
        if (pos == lastToggled) return;
        lastToggled = pos;
        if (!selected.remove(pos)) selected.add(pos);
        adapter.notifyItemChanged(pos);
    }

    private void confirmClear() {
        if (items.isEmpty()) return;
        final List<Integer> targets = new ArrayList<>();
        if (!selected.isEmpty()) targets.addAll(selected);
        else for (int i = 0; i < items.size(); i++) targets.add(i);
        final int n = targets.size();
        String what = mode == MODE_FAV ? "收藏" : mode == MODE_HIS ? "观看记录"
                : mode == MODE_LIKE ? "喜欢" : "离线缓存记录";
        GlassPanel.confirm(hostActivity(), "删除 " + n + " 条" + what,
                "删除后列表里不再显示。离线缓存只删记录，Download/Huangguo 里的视频文件不会被删。",
                "全部删除", () -> {
                    for (Integer idx : targets) {
                        if (idx < 0 || idx >= items.size()) continue;
                        delete(items.get(idx));
                    }
                    exitSelect();
                    load(mode);
                });
    }

    private void delete(Item it) {
        Store store = Store.get(getContext());
        switch (it.kind) {
            case 10:
                store.removeFavorite(Store.T_VIDEO, it.id);
                Api.collectVideo(it.id, false, new Api.Cb<org.json.JSONObject>() {
                    @Override
                    public void ok(org.json.JSONObject data) {
                    }

                    @Override
                    public void err(String msg) {
                    }
                });
                break;
            case 11:
                store.removeFavorite(Store.T_POST, it.id);
                break;
            case 12:
                store.removeFavorite(Store.T_TOPIC, it.id);
                break;
            case 20:
                store.deleteHistory(Store.T_VIDEO, it.id);
                if (Auth.loggedIn()) {
                    Api.deleteHistory(it.id, new Api.Cb<org.json.JSONObject>() {
                        @Override
                        public void ok(org.json.JSONObject data) {
                        }

                        @Override
                        public void err(String msg) {
                        }
                    });
                }
                break;
            case 30:
                if (Auth.loggedIn()) {
                    Api.likeVideo(it.id, Math.max(1, it.ep), false,
                            new Api.Cb<org.json.JSONObject>() {
                                @Override
                                public void ok(org.json.JSONObject data) {
                                }

                                @Override
                                public void err(String msg) {
                                }
                            });
                }
                break;
            case 40:
                store.removeCached(it.id);
                break;
            default:
                break;
        }
        Sync.markDirty();
    }

    private android.app.Activity hostActivity() {
        Context c = getContext();
        while (c instanceof android.content.ContextWrapper) {
            if (c instanceof android.app.Activity) return (android.app.Activity) c;
            c = ((android.content.ContextWrapper) c).getBaseContext();
        }
        return null;
    }

    // ------------------------------------------------------------------ 列表

    private class Adapter extends RecyclerView.Adapter<Adapter.H> {

        class H extends RecyclerView.ViewHolder {
            final AspectImageView cover;
            final TextView title;
            final TextView meta;
            final TextView mark;

            H(LinearLayout row) {
                super(row);
                LinearLayout left = (LinearLayout) row.getChildAt(0);
                FrameLayout thumb = (FrameLayout) left.getChildAt(0);
                cover = (AspectImageView) thumb.getChildAt(0);
                LinearLayout texts = (LinearLayout) left.getChildAt(1);
                title = (TextView) texts.getChildAt(0);
                meta = (TextView) texts.getChildAt(1);
                mark = (TextView) row.getChildAt(1);
            }
        }

        @NonNull
        @Override
        public H onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            Context c = parent.getContext();
            final LinearLayout row = Ui.row(c);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setBackground(new GlassDrawable(Ui.dp(c, 18)));
            row.setPadding(Ui.dp(c, 10), Ui.dp(c, 10), Ui.dp(c, 12), Ui.dp(c, 10));
            RecyclerView.LayoutParams rlp = new RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(c, 94));
            rlp.leftMargin = Ui.dp(c, 16);
            rlp.rightMargin = Ui.dp(c, 16);
            rlp.topMargin = Ui.dp(c, 8);
            row.setLayoutParams(rlp);

            LinearLayout left = Ui.row(c);
            LinearLayout.LayoutParams lp = Ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.weight = 1f;
            row.addView(left, lp);

            FrameLayout thumb = new FrameLayout(c);
            thumb.setClipToOutline(true);
            thumb.setBackground(new GlassDrawable(Ui.dp(c, 12)));
            left.addView(thumb, Ui.lp(Ui.dp(c, 60), Ui.dp(c, 78)));

            AspectImageView cover = new AspectImageView(c);
            cover.setRatio(1.32f);
            thumb.addView(cover, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

            LinearLayout texts = Ui.col(c);
            LinearLayout.LayoutParams tp = Ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT);
            tp.weight = 1f;
            tp.leftMargin = Ui.dp(c, 12);
            left.addView(texts, tp);

            TextView title = Ui.bold(c, "", 13f, Ui.C_TEXT);
            title.setMaxLines(1);
            title.setEllipsize(TextUtils.TruncateAt.END);
            texts.addView(title);

            TextView meta = Ui.text(c, "", 10.5f, Ui.C_SUB);
            meta.setMaxLines(2);
            LinearLayout.LayoutParams mp = Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            mp.topMargin = Ui.dp(c, 5);
            texts.addView(meta, mp);

            // 右侧勾选圈（多选时显示）
            TextView mark = Ui.text(c, "○", 18f, Ui.C_DIM);
            mark.setGravity(Gravity.CENTER);
            mark.setVisibility(View.GONE);
            row.addView(mark, Ui.lp(Ui.dp(c, 32), Ui.dp(c, 32)));

            row.setClickable(true);
            row.setOnClickListener(v -> {
                int pos = rv.getChildAdapterPosition(row);
                if (pos == RecyclerView.NO_POSITION || pos >= items.size()) return;
                if (selecting) {
                    if (!selected.remove(pos)) selected.add(pos);
                    notifyItemChanged(pos);
                    return;
                }
                Item it = items.get(pos);
                if (it.kind == 11) {
                    PostActivity.open(c, it.id, it.title);
                } else if (it.kind == 12) {
                    TopicActivity.open(c, "", it.title);
                } else {
                    DetailActivity.open(c, it.id);
                }
            });
            row.setOnLongClickListener(v -> {
                int pos = rv.getChildAdapterPosition(row);
                if (pos == RecyclerView.NO_POSITION) return false;
                if (!selecting) enterSelect(pos);
                return true;
            });
            return new H(row);
        }

        @Override
        public void onBindViewHolder(@NonNull H h, int position) {
            Item it = items.get(position);
            h.title.setText(it.title);
            if (it.hasNew) {
                SpannableString sp = new SpannableString(it.meta);
                int idx = it.meta.indexOf("有更新");
                if (idx >= 0) {
                    sp.setSpan(new ForegroundColorSpan(Ui.C_AMBER), idx, it.meta.length(),
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
                h.meta.setText(sp);
            } else {
                h.meta.setText(it.meta);
            }
            ImageLoader.load(h.cover, it.cover);

            if (selecting) {
                h.mark.setVisibility(View.VISIBLE);
                boolean on = selected.contains(position);
                h.mark.setText(on ? "●" : "○");
                h.mark.setTextColor(on ? Ui.C_AMBER : Ui.C_DIM);
            } else {
                h.mark.setVisibility(View.GONE);
            }
        }

        @Override
        public int getItemCount() {
            return items.size();
        }
    }
}
