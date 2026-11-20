package com.huangguo.pure;

import android.content.Context;
import android.graphics.Color;
import android.text.TextUtils;
import android.view.Gravity;
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
import java.util.List;

/** 我的：本地收藏 + 观看历史；支持下拉刷新 */
public class MinePage extends FrameLayout implements PullRefresh.Refreshable {

    private static final int MODE_FAV = 0;
    private static final int MODE_HIS = 1;

    /** 外观行点击后请求宿主弹出玻璃主题菜单 */
    public interface ThemeRequest {
        void onThemeRequest();
    }

    private ThemeRequest themeRequest;

    public void setThemeRequest(ThemeRequest r) {
        themeRequest = r;
    }

    private final RecyclerView rv;
    private final TextView empty;
    private final TextView favTab;
    private final TextView hisTab;
    private final RowAdapter adapter = new RowAdapter();
    private final List<Object> rows = new ArrayList<>();
    private int mode = MODE_FAV;

    public MinePage(Context c) {
        super(c);
        LinearLayout root = Ui.col(c);
        addView(root, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        root.addView(Chrome.titleBar(c, "我的", "本地 · 不上传"));

        accountRow = buildAccount(c);
        LinearLayout.LayoutParams alp = Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        alp.leftMargin = Ui.dp(c, 16);
        alp.rightMargin = Ui.dp(c, 16);
        alp.bottomMargin = Ui.dp(c, 8);
        root.addView(accountRow, alp);
        paintAccount();

        LinearLayout themeRow = buildThemeRow(c);
        LinearLayout.LayoutParams tlp = Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.leftMargin = Ui.dp(c, 16);
        tlp.rightMargin = Ui.dp(c, 16);
        tlp.bottomMargin = Ui.dp(c, 8);
        root.addView(themeRow, tlp);

        LinearLayout aboutRow = buildAboutRow(c);
        LinearLayout.LayoutParams olp = Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        olp.leftMargin = Ui.dp(c, 16);
        olp.rightMargin = Ui.dp(c, 16);
        olp.bottomMargin = Ui.dp(c, 8);
        root.addView(aboutRow, olp);

        LinearLayout cleanRow = buildCleanRow(c);
        LinearLayout.LayoutParams clp = Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.leftMargin = Ui.dp(c, 16);
        clp.rightMargin = Ui.dp(c, 16);
        clp.bottomMargin = Ui.dp(c, 8);
        root.addView(cleanRow, clp);

        LinearLayout seg = Ui.row(c);
        seg.setPadding(Ui.dp(c, 16), Ui.dp(c, 6), Ui.dp(c, 16), Ui.dp(c, 8));
        favTab = segment("收藏", true);
        hisTab = segment("观看历史", false);
        LinearLayout.LayoutParams lp1 = Ui.lp(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp1.rightMargin = Ui.dp(c, 8);
        seg.addView(favTab, lp1);
        seg.addView(hisTab);
        root.addView(seg);

        favTab.setOnClickListener(v -> switchMode(MODE_FAV));
        hisTab.setOnClickListener(v -> switchMode(MODE_HIS));

        SwipeFrame box = new SwipeFrame(c);
        box.setOnSwipe(dir -> switchMode(dir > 0 ? MODE_HIS : MODE_FAV));
        root.addView(box, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        rv = new RecyclerView(c);
        rv.setLayoutManager(new LinearLayoutManager(c));
        rv.setAdapter(adapter);
        rv.setClipToPadding(false);
        rv.setPadding(0, 0, 0, Ui.dp(c, 96));
        rv.setOverScrollMode(OVER_SCROLL_NEVER);
        box.addView(rv, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        empty = Ui.text(c, "", 13f, Ui.C_DIM);
        empty.setGravity(Gravity.CENTER);
        box.addView(empty, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER));

        refresh();
    }

    private LinearLayout accountRow;
    private TextView accountAvatar;
    private TextView accountName;
    private TextView accountSub;
    private TextView accountAction;

    /** 账号卡：未登录给「登录 / 注册」，登录后显示昵称与退出 */
    private LinearLayout buildAccount(Context c) {
        LinearLayout wrap = Ui.row(c);
        // 与外观/关于两卡统一内边距与高度
        wrap.setMinimumHeight(Ui.dp(c, 62));
        wrap.setPadding(Ui.dp(c, 12), Ui.dp(c, 10), Ui.dp(c, 14), Ui.dp(c, 10));
        wrap.setBackground(new GlassDrawable(Ui.dp(c, 18)));
        wrap.setGravity(Gravity.CENTER_VERTICAL);

        accountAvatar = Ui.bold(c, "·", 15f, 0xFF1A1206);
        accountAvatar.setGravity(Gravity.CENTER);
        accountAvatar.setBackground(Ui.round(Ui.C_AMBER, Ui.dp(c, 21)));
        wrap.addView(accountAvatar, Ui.lp(Ui.dp(c, 40), Ui.dp(c, 40)));

        LinearLayout texts = Ui.col(c);
        LinearLayout.LayoutParams tp = Ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT);
        tp.weight = 1f;
        tp.leftMargin = Ui.dp(c, 12);
        wrap.addView(texts, tp);

        accountName = Ui.bold(c, "未登录", 13.5f, Ui.C_TEXT);
        texts.addView(accountName);

        accountSub = Ui.text(c, "登录后可发表评论", 10.5f, Ui.C_DIM);
        LinearLayout.LayoutParams sp = Ui.lp(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        sp.topMargin = Ui.dp(c, 4);
        texts.addView(accountSub, sp);

        accountAction = Ui.text(c, "登录 / 注册", 11.5f, Ui.C_AMBER);
        accountAction.setGravity(Gravity.CENTER);
        accountAction.setPadding(Ui.dp(c, 12), Ui.dp(c, 7), Ui.dp(c, 12), Ui.dp(c, 7));
        accountAction.setBackground(Ui.stroke(0x26F5B942, 0x4DF5B942, Ui.dp(c, 14), 1));
        accountAction.setClickable(true);
        accountAction.setOnClickListener(v -> {
            if (Auth.loggedIn()) {
                Auth.logout();
                paintAccount();
                refresh();
            } else {
                AuthDialog.show(getContext(), false, () -> {
                    paintAccount();
                    refresh();
                });
            }
        });
        wrap.addView(accountAction, Ui.lp(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        return wrap;
    }

    /** 外观设置行：左侧模式圆徽 + 标题副标 + 右侧当前模式胶囊 + 箭头 */
    private LinearLayout buildThemeRow(Context c) {
        LinearLayout wrap = Ui.row(c);
        wrap.setMinimumHeight(Ui.dp(c, 62));
        wrap.setPadding(Ui.dp(c, 12), Ui.dp(c, 10), Ui.dp(c, 14), Ui.dp(c, 10));
        wrap.setBackground(new GlassDrawable(Ui.dp(c, 18)));
        wrap.setGravity(Gravity.CENTER_VERTICAL);
        wrap.setClickable(true);
        wrap.setOnClickListener(v -> {
            if (themeRequest != null) themeRequest.onThemeRequest();
        });

        TextView glyph = Ui.bold(c, "◐", 16f, Ui.C_AMBER);
        glyph.setGravity(Gravity.CENTER);
        glyph.setBackground(Ui.stroke(0x26F5B942, 0x59F5B942, Ui.dp(c, 20), 1));
        wrap.addView(glyph, Ui.lp(Ui.dp(c, 40), Ui.dp(c, 40)));

        LinearLayout texts = Ui.col(c);
        LinearLayout.LayoutParams tp = Ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT);
        tp.weight = 1f;
        tp.leftMargin = Ui.dp(c, 12);
        wrap.addView(texts, tp);
        texts.addView(Ui.bold(c, "外观", 13.5f, Ui.C_TEXT));
        TextView sub = Ui.text(c, "深浅色 · 液态玻璃观感", 10.5f, Ui.C_DIM);
        LinearLayout.LayoutParams sp = Ui.lp(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        sp.topMargin = Ui.dp(c, 3);
        texts.addView(sub, sp);

        TextView value = Ui.bold(c, Theme.label(Theme.mode()), 11.5f, Ui.C_AMBER);
        value.setGravity(Gravity.CENTER);
        value.setPadding(Ui.dp(c, 12), Ui.dp(c, 6), Ui.dp(c, 12), Ui.dp(c, 6));
        value.setBackground(Ui.stroke(0x26F5B942, 0x4DF5B942, Ui.dp(c, 14), 1));
        wrap.addView(value, Ui.lp(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView arrow = Ui.text(c, "›", 15f, Ui.C_DIM);
        LinearLayout.LayoutParams ap = Ui.lp(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        ap.leftMargin = Ui.dp(c, 8);
        wrap.addView(arrow, ap);
        return wrap;
    }

    /** 关于入口行：版本 · GitHub · Telegram 群组 · 开源许可 */
    private LinearLayout buildAboutRow(Context c) {
        LinearLayout wrap = Ui.row(c);
        wrap.setMinimumHeight(Ui.dp(c, 62));
        wrap.setPadding(Ui.dp(c, 12), Ui.dp(c, 10), Ui.dp(c, 14), Ui.dp(c, 10));
        wrap.setBackground(new GlassDrawable(Ui.dp(c, 18)));
        wrap.setGravity(Gravity.CENTER_VERTICAL);
        wrap.setClickable(true);
        wrap.setOnClickListener(v -> AboutActivity.open(c));

        TextView glyph = Ui.bold(c, "ⓘ", 15f, Ui.C_AMBER);
        glyph.setGravity(Gravity.CENTER);
        glyph.setBackground(Ui.stroke(0x26F5B942, 0x59F5B942, Ui.dp(c, 20), 1));
        wrap.addView(glyph, Ui.lp(Ui.dp(c, 40), Ui.dp(c, 40)));

        LinearLayout texts = Ui.col(c);
        LinearLayout.LayoutParams tp = Ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT);
        tp.weight = 1f;
        tp.leftMargin = Ui.dp(c, 12);
        wrap.addView(texts, tp);
        texts.addView(Ui.bold(c, "关于", 13.5f, Ui.C_TEXT));
        TextView sub = Ui.text(c, "版本 · GitHub · Telegram 群组 · 开源许可", 10.5f, Ui.C_DIM);
        LinearLayout.LayoutParams sp = Ui.lp(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        sp.topMargin = Ui.dp(c, 3);
        texts.addView(sub, sp);

        TextView arrow = Ui.text(c, "›", 15f, Ui.C_DIM);
        LinearLayout.LayoutParams ap = Ui.lp(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        ap.leftMargin = Ui.dp(c, 8);
        wrap.addView(arrow, ap);
        return wrap;
    }

    /** 软件清理入口行：点击弹出下拉菜单（清理缓存 / 清理下载文件） */
    private LinearLayout buildCleanRow(final Context c) {
        LinearLayout wrap = Ui.row(c);
        wrap.setMinimumHeight(Ui.dp(c, 62));
        wrap.setPadding(Ui.dp(c, 12), Ui.dp(c, 10), Ui.dp(c, 14), Ui.dp(c, 10));
        wrap.setBackground(new GlassDrawable(Ui.dp(c, 18)));
        wrap.setGravity(Gravity.CENTER_VERTICAL);
        wrap.setClickable(true);

        TextView glyph = Ui.bold(c, "⌫", 15f, Ui.C_AMBER);
        glyph.setGravity(Gravity.CENTER);
        glyph.setBackground(Ui.stroke(0x26F5B942, 0x59F5B942, Ui.dp(c, 20), 1));
        wrap.addView(glyph, Ui.lp(Ui.dp(c, 40), Ui.dp(c, 40)));

        LinearLayout texts = Ui.col(c);
        LinearLayout.LayoutParams tp = Ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT);
        tp.weight = 1f;
        tp.leftMargin = Ui.dp(c, 12);
        wrap.addView(texts, tp);
        texts.addView(Ui.bold(c, "软件清理", 13.5f, Ui.C_TEXT));
        TextView sub = Ui.text(c, "缓存 · 观看记录 · 下载文件", 10.5f, Ui.C_DIM);
        LinearLayout.LayoutParams sp = Ui.lp(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        sp.topMargin = Ui.dp(c, 3);
        texts.addView(sub, sp);

        TextView arrow = Ui.text(c, "›", 15f, Ui.C_DIM);
        LinearLayout.LayoutParams ap = Ui.lp(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        ap.leftMargin = Ui.dp(c, 8);
        wrap.addView(arrow, ap);

        wrap.setOnClickListener(v -> GlassMenu.show(wrap, GlassMenu.items(
                new GlassMenu.Item("↺", "清理缓存", () -> CleanHelper.showClearCache(c)),
                new GlassMenu.Item("⤓", "清理下载文件", () -> CleanHelper.showDownloads(c)))));
        return wrap;
    }

    private void paintAccount() {
        if (accountName == null) return;
        if (Auth.loggedIn()) {
            String name = Auth.name().isEmpty() ? "已登录" : Auth.name();
            accountName.setText(name);
            accountSub.setText("账号已连接 · 可发表评论");
            accountAvatar.setText(name.substring(0, 1));
            accountAction.setText("退出登录");
            accountAction.setTextColor(Ui.C_SUB);
            accountAction.setBackground(Ui.subtle(getContext(), Ui.dp(getContext(), 14)));
        } else {
            accountName.setText("未登录");
            accountSub.setText("登录后可发表评论");
            accountAvatar.setText("·");
            accountAction.setText("登录 / 注册");
            accountAction.setTextColor(Ui.C_AMBER);
            accountAction.setBackground(Ui.stroke(0x26F5B942, 0x4DF5B942,
                    Ui.dp(getContext(), 14), 1));
        }
    }

    private TextView segment(String text, boolean active) {        TextView t = Ui.text(getContext(), text, 12.5f, active ? 0xFF1A1206 : Ui.C_TEXT);
        t.setGravity(Gravity.CENTER);
        t.setPadding(Ui.dp(getContext(), 16), Ui.dp(getContext(), 7),
                Ui.dp(getContext(), 16), Ui.dp(getContext(), 7));
        t.setBackground(active
                ? Ui.round(Ui.C_AMBER, Ui.dp(getContext(), 16))
                : Ui.subtle(getContext(), Ui.dp(getContext(), 16)));
        return t;
    }

    private void switchMode(int m) {
        if (mode == m) return;
        mode = m;
        Context c = getContext();
        favTab.setTextColor(m == MODE_FAV ? 0xFF1A1206 : Ui.C_TEXT);
        favTab.setBackground(m == MODE_FAV
                ? Ui.round(Ui.C_AMBER, Ui.dp(c, 16))
                : Ui.subtle(c, Ui.dp(c, 16)));
        hisTab.setTextColor(m == MODE_HIS ? 0xFF1A1206 : Ui.C_TEXT);
        hisTab.setBackground(m == MODE_HIS
                ? Ui.round(Ui.C_AMBER, Ui.dp(c, 16))
                : Ui.subtle(c, Ui.dp(c, 16)));
        refresh();
    }

    /** 下拉刷新：重读本地库（收藏/历史/点赞可能在其它页面变了） */
    @Override
    public void onPullRefresh(PullRefresh.Done done) {
        refresh();
        postDelayed(done::done, 350);
    }

    public void refresh() {
        Store store = Store.get(getContext());
        rows.clear();
        if (mode == MODE_FAV) {
            rows.addAll(store.favorites());
            empty.setText("还没有收藏，去详情页点★");
        } else {
            rows.addAll(store.history());
            empty.setText("还没有观看记录");
        }
        adapter.notifyDataSetChanged();
        empty.setVisibility(rows.isEmpty() ? VISIBLE : GONE);
    }

    private class RowAdapter extends RecyclerView.Adapter<RowAdapter.H> {

        class H extends RecyclerView.ViewHolder {
            final AspectImageView cover;
            final TextView title;
            final TextView meta;
            final View del;
            final View bar;

            H(LinearLayout row) {
                super(row);
                LinearLayout left = (LinearLayout) row.getChildAt(0);
                FrameLayout thumb = (FrameLayout) left.getChildAt(0);
                cover = (AspectImageView) thumb.getChildAt(0);
                bar = thumb.getChildAt(1);
                LinearLayout texts = (LinearLayout) left.getChildAt(1);
                title = (TextView) texts.getChildAt(0);
                meta = (TextView) texts.getChildAt(1);
                del = row.getChildAt(1);
            }
        }

        @NonNull
        @Override
        public H onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            Context c = parent.getContext();
            LinearLayout row = Ui.row(c);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(Ui.dp(c, 16), Ui.dp(c, 8), Ui.dp(c, 12), Ui.dp(c, 8));
            // 根行宽 MATCH_PARENT：left 的 weight=1f 才能把删除键顶到最右、统一成同一列
            row.setLayoutParams(new RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            LinearLayout left = Ui.row(c);
            LinearLayout.LayoutParams lp = Ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.weight = 1f;
            row.addView(left, lp);

            FrameLayout thumb = new FrameLayout(c);
            thumb.setClipToOutline(true);
            thumb.setBackground(new GlassDrawable(Ui.dp(c, 12)));
            left.addView(thumb, Ui.lp(Ui.dp(c, 62), Ui.dp(c, 82)));

            AspectImageView cover = new AspectImageView(c);
            cover.setRatio(1.32f);
            thumb.addView(cover, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

            View bar = new View(c);
            bar.setBackgroundColor(Ui.C_AMBER);
            FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(c, 2), Gravity.BOTTOM);
            bar.setVisibility(View.GONE);
            thumb.addView(bar, bp);

            LinearLayout texts = Ui.col(c);
            LinearLayout.LayoutParams tp = Ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT);
            tp.weight = 1f;
            tp.leftMargin = Ui.dp(c, 12);
            left.addView(texts, tp);

            TextView title = Ui.bold(c, "", 13.5f, Ui.C_TEXT);
            title.setMaxLines(2);
            title.setEllipsize(TextUtils.TruncateAt.END);
            texts.addView(title);

            TextView meta = Ui.text(c, "", 11f, Ui.C_SUB);
            meta.setMaxLines(1);
            LinearLayout.LayoutParams mp = Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            mp.topMargin = Ui.dp(c, 6);
            texts.addView(meta, mp);

            // 液态玻璃圆形删除按钮：主题感知玻璃底 + 主题色图标，右列固定对齐
            FrameLayout delWrap = new FrameLayout(c);
            delWrap.setBackground(Ui.darkGlass
                    ? new GlassDrawable(Ui.dp(c, 20), 0x4D1C2333, 0x3D0A0E15,
                            0x400A0E15, 0x4DFFFFFF)
                    : new GlassDrawable(Ui.dp(c, 20), 0xF0FFFFFF, 0xE6EFF4FB,
                            0xE0E8EFF7, 0x4D1A2332));
            ImageView del = new ImageView(c);
            del.setImageResource(R.drawable.ic_trash);
            del.setColorFilter(Ui.darkGlass ? 0xFFFFFFFF : 0xFF37455C);
            delWrap.addView(del, new FrameLayout.LayoutParams(
                    Ui.dp(c, 16), Ui.dp(c, 16), Gravity.CENTER));
            row.addView(delWrap, Ui.lp(Ui.dp(c, 34), Ui.dp(c, 34)));

            row.setOnClickListener(v -> {
                int pos = rv.getChildAdapterPosition(row);
                if (pos == RecyclerView.NO_POSITION || pos >= rows.size()) return;
                Object o = rows.get(pos);
                if (o instanceof Models.Video) {
                    Models.Video v2 = (Models.Video) o;
                    DetailActivity.open(c, v2.id);
                } else if (o instanceof Models.Post) {
                    Models.Post p = (Models.Post) o;
                    PostActivity.open(c, p.id, p.title);
                } else if (o instanceof Models.Topic) {
                    Models.Topic t = (Models.Topic) o;
                    TopicActivity.open(c, t.slug, t.title);
                } else {
                    Store.History h = (Store.History) o;
                    if (h.type == Store.T_POST) {
                        PostActivity.open(c, h.id, h.title);
                    } else if (h.type == Store.T_TOPIC) {
                        TopicActivity.open(c, h.slug, h.title);
                    } else {
                        PlayerActivity.open(c, h.id, h.title, h.ep, h.positionMs);
                    }
                }
            });

            delWrap.setOnClickListener(v -> {
                int pos = rv.getChildAdapterPosition(row);
                if (pos == RecyclerView.NO_POSITION || pos >= rows.size()) return;
                Object o = rows.get(pos);
                if (o instanceof Models.Video) {
                    Store.get(c).removeFavorite(Store.T_VIDEO, ((Models.Video) o).id);
                } else if (o instanceof Models.Post) {
                    Store.get(c).removeFavorite(Store.T_POST, ((Models.Post) o).id);
                } else if (o instanceof Models.Topic) {
                    Store.get(c).removeFavorite(Store.T_TOPIC, ((Models.Topic) o).id);
                } else {
                    Store.History h = (Store.History) o;
                    Store.get(c).deleteHistory(h.type, h.id);
                }
                refresh();
            });

            return new H(row);
        }

        @Override
        public void onBindViewHolder(@NonNull H h, int position) {
            // 行宽强制 MATCH_PARENT：否则 RecyclerView item 默认 WRAP_CONTENT，
            // left 的 weight=1f 失效，删除键会跟着标题长度左右漂移、不对齐
            ViewGroup.LayoutParams rlp = h.itemView.getLayoutParams();
            if (rlp == null) {
                rlp = new RecyclerView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                h.itemView.setLayoutParams(rlp);
            } else if (rlp.width != ViewGroup.LayoutParams.MATCH_PARENT) {
                rlp.width = ViewGroup.LayoutParams.MATCH_PARENT;
                h.itemView.setLayoutParams(rlp);
            }
            Object o = rows.get(position);
            if (o instanceof Models.Video) {
                Models.Video v = (Models.Video) o;
                ImageLoader.load(h.cover, v.cover);
                h.title.setText(v.title);
                StringBuilder sb = new StringBuilder();
                if (v.score > 0) sb.append(v.scoreText()).append("分");
                if (v.epCount > 0) {
                    if (sb.length() > 0) sb.append("  ·  ");
                    sb.append(v.epText());
                }
                h.meta.setText(sb.length() == 0 ? "已收藏" : sb.toString());
                h.bar.setVisibility(View.GONE);
            } else if (o instanceof Models.Post) {
                Models.Post p = (Models.Post) o;
                ImageLoader.load(h.cover, p.cover);
                h.title.setText(p.title);
                h.meta.setText("博客文章" + (p.authorName.isEmpty() ? "" : "  ·  " + p.authorName));
                h.bar.setVisibility(View.GONE);
            } else if (o instanceof Models.Topic) {
                Models.Topic t = (Models.Topic) o;
                ImageLoader.load(h.cover, t.cover);
                h.title.setText(t.title);
                h.meta.setText("专题" + (t.videoCount > 0 ? "  ·  共 " + t.videoCount + " 部" : ""));
                h.bar.setVisibility(View.GONE);
            } else {
                Store.History his = (Store.History) o;
                ImageLoader.load(h.cover, his.cover);
                h.title.setText(his.title);
                StringBuilder sb = new StringBuilder();
                if (his.type == Store.T_POST) {
                    sb.append("看过");
                } else if (his.type == Store.T_TOPIC) {
                    sb.append("浏览过专题");
                } else {
                    sb.append("第 ").append(his.ep).append(" 集");
                    if (his.durationMs > 0) {
                        sb.append("  ·  ").append(Ui.hhmmss(his.positionMs))
                                .append(" / ").append(Ui.hhmmss(his.durationMs));
                    } else {
                        sb.append("  ·  已看 ").append(Ui.hhmmss(his.positionMs));
                    }
                }
                h.meta.setText(sb.toString());
                int pct = his.type == Store.T_VIDEO ? his.percent() : 0;
                h.bar.setVisibility(pct > 0 ? View.VISIBLE : View.GONE);
                if (pct > 0) {
                    ViewGroup.LayoutParams blp = h.bar.getLayoutParams();
                    int full = h.cover.getWidth() > 0 ? h.cover.getWidth()
                            : Ui.dp(h.bar.getContext(), 62);
                    blp.width = Math.max(Ui.dp(h.bar.getContext(), 2), full * pct / 100);
                    h.bar.setLayoutParams(blp);
                }
            }
        }

        @Override
        public int getItemCount() {
            return rows.size();
        }
    }
}
