package com.huangguo.pure;

import android.content.Context;
import android.graphics.Color;
import android.text.InputFilter;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

/**
 * 评论区（剧集 / 博客共用）。
 *
 * 负责：标题计数、发表框、列表分页、登录拦截。
 * 数据源由宿主传入，所以「按集切换」只需宿主换 Source 里的 ep 再 reload()。
 * likeKind 非空时每行带可点的点赞按钮（本地点亮，与站点游客态对齐）。
 */
public class CommentBox extends LinearLayout {

    public interface Source {
        /** page 从 1 开始 */
        void load(int page, Api.Cb<List<Models.Comment>> cb);

        /** 发表一条评论 */
        void send(String text, Api.Cb<String> cb);
    }

    private static final int MAX_LEN = 200;

    private final Source source;
    private final Runnable onNeedLogin;
    private final String likeKind;
    private final TextView head;
    private final LinearLayout list;
    private final EditText input;
    private final TextView sendBtn;
    private final TextView more;
    private final TextView tip;

    private int page = 1;
    private boolean busy;
    private int total;
    private boolean loadedOnce;

    public CommentBox(Context c, String title, Source src, Runnable onNeedLogin) {
        this(c, title, src, onNeedLogin, null);
    }

    /** likeKind：传 Store.LIKE_VCOMMENT / LIKE_PCOMMENT 开启评论点赞 */
    public CommentBox(Context c, String title, Source src, Runnable onNeedLogin,
                      String likeKind) {
        super(c);
        this.source = src;
        this.onNeedLogin = onNeedLogin;
        this.likeKind = likeKind;
        setOrientation(VERTICAL);

        LinearLayout headRow = Ui.row(c);
        head = Ui.bold(c, title, 14f, Ui.C_TEXT);
        headRow.addView(head);
        View fill = new View(c);
        LinearLayout.LayoutParams flp = Ui.lp(0, 1);
        flp.weight = 1f;
        headRow.addView(fill, flp);
        tip = Ui.text(c, "", 10.5f, Ui.C_DIM);
        headRow.addView(tip);
        addView(headRow);

        LinearLayout inputRow = Ui.row(c);
        LinearLayout.LayoutParams irp = Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        irp.topMargin = Ui.dp(c, 10);
        addView(inputRow, irp);

        input = new EditText(c);
        input.setHint("说点什么…");
        input.setHintTextColor(Ui.C_DIM);
        input.setTextColor(Ui.C_TEXT);
        input.setTextSize(12.5f);
        input.setBackground(Ui.stroke(Ui.chipFill(), Ui.chipStroke(), Ui.dp(c, 14), 1));
        input.setPadding(Ui.dp(c, 12), Ui.dp(c, 8), Ui.dp(c, 12), Ui.dp(c, 8));
        input.setSingleLine(true);
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(MAX_LEN)});
        LinearLayout.LayoutParams ilp = Ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT);
        ilp.weight = 1f;
        inputRow.addView(input, ilp);

        sendBtn = Ui.text(c, "发表", 12f, Ui.onAmber());
        sendBtn.setGravity(Gravity.CENTER);
        sendBtn.setPadding(Ui.dp(c, 14), Ui.dp(c, 8), Ui.dp(c, 14), Ui.dp(c, 8));
        sendBtn.setBackground(Ui.round(Ui.C_AMBER, Ui.dp(c, 14)));
        sendBtn.setClickable(true);
        LinearLayout.LayoutParams slp = Ui.lp(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.leftMargin = Ui.dp(c, 8);
        inputRow.addView(sendBtn, slp);
        sendBtn.setOnClickListener(v -> submit());

        list = Ui.col(c);
        LinearLayout.LayoutParams llp = Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        llp.topMargin = Ui.dp(c, 8);
        addView(list, llp);

        more = Ui.text(c, "加载更多评论", 11.5f, Ui.C_AMBER);
        more.setGravity(Gravity.CENTER);
        more.setPadding(0, Ui.dp(c, 10), 0, Ui.dp(c, 10));
        more.setVisibility(GONE);
        more.setOnClickListener(v -> load(page + 1));
        addView(more);
    }

    public void setTitle(String text) {
        head.setText(text);
    }

    /** 换集 / 首次进入时调用：清空列表重新拉第一页 */
    public void reload() {
        page = 1;
        loadedOnce = false;
        list.removeAllViews();
        more.setVisibility(GONE);
        tip.setText("加载中…");
        load(1);
    }

    private void load(final int p) {
        if (busy) return;
        busy = true;
        source.load(p, new Api.Cb<List<Models.Comment>>() {
            @Override
            public void ok(List<Models.Comment> items) {
                busy = false;
                loadedOnce = true;
                page = p;
                tip.setText("");
                if (p == 1) {
                    total = items.size();
                    if (items.isEmpty()) {
                        TextView empty = Ui.text(getContext(), "还没有评论，来说两句", 11.5f, Ui.C_DIM);
                        empty.setPadding(0, Ui.dp(getContext(), 8), 0, Ui.dp(getContext(), 4));
                        list.addView(empty);
                        more.setVisibility(GONE);
                        return;
                    }
                }
                for (Models.Comment cm : items) list.addView(row(cm, likeKind));
                total = Math.max(total, list.getChildCount());
                head.setText(headText());
                more.setVisibility(items.size() >= 20 ? VISIBLE : GONE);
            }

            @Override
            public void err(String msg) {
                busy = false;
                tip.setText("评论加载失败：" + msg);
                if (p == 1 && list.getChildCount() == 0) {
                    TextView empty = Ui.text(getContext(), "评论暂不可用", 11.5f, Ui.C_DIM);
                    list.addView(empty);
                }
            }
        });
    }

    private String headText() {
        return total > 0 ? "评论 · " + Ui.shortNum(total) : "评论";
    }

    private void submit() {
        final String text = input.getText().toString().trim();
        if (text.isEmpty()) {
            Toast.makeText(getContext(), "先写点内容", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!Auth.loggedIn()) {
            if (onNeedLogin != null) onNeedLogin.run();
            return;
        }
        sendBtn.setEnabled(false);
        source.send(text, new Api.Cb<String>() {
            @Override
            public void ok(String msg) {
                sendBtn.setEnabled(true);
                input.setText("");
                hideIme();
                Toast.makeText(getContext(), msg == null ? "已提交" : msg, Toast.LENGTH_SHORT).show();
                reload();
            }

            @Override
            public void err(String msg) {
                sendBtn.setEnabled(true);
                Toast.makeText(getContext(), "发表失败：" + msg, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void hideIme() {
        try {
            InputMethodManager im = (InputMethodManager)
                    getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            if (im != null) im.hideSoftInputFromWindow(input.getWindowToken(), 0);
        } catch (Exception ignored) {
            // 无输入法服务时忽略
        }
    }

    // ------------------------------------------------------------------ 行

    static View row(Context c, Models.Comment cm, String likeKind) {
        LinearLayout row = Ui.row(c);
        row.setGravity(Gravity.TOP);
        LinearLayout.LayoutParams rlp = Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        rlp.topMargin = Ui.dp(c, 10);
        row.setLayoutParams(rlp);

        TextView avatar = Ui.bold(c, firstChar(cm.nickname), 11f, Ui.onAmber());
        avatar.setGravity(Gravity.CENTER);
        int color;
        try {
            color = Color.parseColor(cm.avatarColor);
        } catch (Exception e) {
            color = 0xFF6E8494;
        }
        avatar.setBackground(Ui.round(color, Ui.dp(c, 15)));
        row.addView(avatar, Ui.lp(Ui.dp(c, 30), Ui.dp(c, 30)));

        LinearLayout col = Ui.col(c);
        LinearLayout.LayoutParams clp = Ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.weight = 1f;
        clp.leftMargin = Ui.dp(c, 10);
        row.addView(col, clp);

        LinearLayout meta = Ui.row(c);
        meta.addView(Ui.text(c, cm.nickname, 11.5f, Ui.subText()));
        StringBuilder sb = new StringBuilder();
        if (!cm.time.isEmpty()) sb.append(" · ").append(Models.shortenTime(cm.time));
        if (!cm.region.isEmpty()) sb.append(" · ").append(cm.region);
        if (sb.length() > 0) meta.addView(Ui.text(c, sb.toString(), 10f, Ui.C_DIM));
        col.addView(meta);

        TextView text = Ui.text(c, cm.content, 12.5f, Ui.bodyText());
        text.setLineSpacing(Ui.dp(c, 3), 1f);
        LinearLayout.LayoutParams tlp = Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.topMargin = Ui.dp(c, 3);
        col.addView(text, tlp);

        // 点赞 + 回复数：点赞可点（本地点亮，♥ 变琥珀色）
        LinearLayout footRow = Ui.row(c);
        LinearLayout.LayoutParams flp = Ui.lp(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        flp.topMargin = Ui.dp(c, 4);
        col.addView(footRow, flp);

        if (likeKind != null && cm.id > 0) {
            boolean liked = Store.get(c).isLiked(likeKind, cm.id);
            TextView like = Ui.text(c, likeText(c, cm, liked), 10.5f,
                    liked ? Ui.C_AMBER : Ui.C_DIM);
            like.setClickable(true);
            like.setPadding(Ui.dp(c, 8), Ui.dp(c, 2), Ui.dp(c, 8), Ui.dp(c, 2));
            like.setOnClickListener(v -> {
                boolean now = Store.get(c).toggleLike(likeKind, cm.id);
                like.setText(likeText(c, cm, now));
                like.setTextColor(now ? Ui.C_AMBER : Ui.C_DIM);
                like.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
            });
            footRow.addView(like);
        }

        if (cm.likeCount > 0 && likeKind == null) {
            TextView like = Ui.text(c, "♥ " + Ui.shortNum(cm.likeCount), 10f, Ui.C_DIM);
            like.setPadding(0, Ui.dp(c, 2), Ui.dp(c, 8), Ui.dp(c, 2));
            footRow.addView(like);
        }
        if (cm.replyCount > 0) {
            TextView reply = Ui.text(c, "回复 " + Ui.shortNum(cm.replyCount), 10f, Ui.C_DIM);
            reply.setPadding(Ui.dp(c, 8), Ui.dp(c, 2), 0, Ui.dp(c, 2));
            footRow.addView(reply);
        }
        return row;
    }

    private static String likeText(Context c, Models.Comment cm, boolean liked) {
        int n = cm.likeCount + (liked ? 1 : 0);
        return "♥ " + Ui.shortNum(n);
    }

    private View row(Models.Comment cm) {
        return row(getContext(), cm, likeKind);
    }

    private View row(Models.Comment cm, String kind) {
        return row(getContext(), cm, kind);
    }

    private static String firstChar(String s) {
        if (s == null || s.isEmpty()) return "·";
        return s.substring(0, 1);
    }
}
