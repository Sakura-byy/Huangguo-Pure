package com.huangguo.pure;

import android.content.Context;
import android.graphics.Color;
import android.text.InputFilter;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
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

        /** 拉某条评论的二级回复（点「回复 N」展开时调用） */
        void replies(long commentId, int page, Api.Cb<List<Models.Comment>> cb);

        /** 回复某条评论（点评论行触发，带着父楼 id） */
        void reply(long parentId, String text, Api.Cb<String> cb);
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
    private TextView replyLabel;

    private int page = 1;
    /** 当前回复目标（0 = 发新评论）；点任意评论行设置它 */
    private long replyTo;
    private String replyName = "";
    private LinearLayout replyTipBar;
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

        // 回复态提示条：点评论后出现，右侧 × 取消
        replyTipBar = Ui.row(c);
        replyTipBar.setVisibility(GONE);
        LinearLayout.LayoutParams rtp = Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        rtp.topMargin = Ui.dp(c, 6);
        addView(replyTipBar, rtp);

        replyLabel = Ui.text(c, "", 10.5f, Ui.accentBright());
        LinearLayout.LayoutParams rllp = Ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT);
        rllp.weight = 1f;
        replyTipBar.addView(replyLabel, rllp);

        TextView cancelReply = Ui.text(c, "取消", 10.5f, Ui.C_DIM);
        cancelReply.setPadding(Ui.dp(c, 8), Ui.dp(c, 2), Ui.dp(c, 4), Ui.dp(c, 2));
        cancelReply.setClickable(true);
        cancelReply.setOnClickListener(v -> clearReply());
        replyTipBar.addView(cancelReply);

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
                for (Models.Comment cm : items) list.addView(row(cm));
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
        final long targetId = replyTo;
        Api.Cb<String> cb = new Api.Cb<String>() {
            @Override
            public void ok(String msg) {
                sendBtn.setEnabled(true);
                input.setText("");
                hideIme();
                clearReply();
                Toast.makeText(getContext(), msg == null ? "已提交" : msg,
                        Toast.LENGTH_SHORT).show();
                reload();
            }

            @Override
            public void err(String msg) {
                sendBtn.setEnabled(true);
                Toast.makeText(getContext(), "发表失败：" + msg, Toast.LENGTH_SHORT).show();
            }
        };
        // 回复态挂到父楼下（reply_id），否则发新楼
        if (targetId > 0) source.reply(targetId, text, cb);
        else source.send(text, cb);
    }

    /** 点评论：设为目标楼，输入框聚焦并滚到可视区上方（键盘弹出后仍看得见） */
    private void startReply(Models.Comment cm) {
        if (cm == null || cm.id <= 0) return;
        replyTo = cm.id;
        replyName = cm.nickname;
        replyLabel.setText("正在回复 @" + cm.nickname);
        replyTipBar.setVisibility(VISIBLE);
        input.setHint("回复 @" + cm.nickname + "…");
        input.requestFocus();
        scrollComposerToUpperHalf(0);
        input.postDelayed(() -> {
            try {
                InputMethodManager im = (InputMethodManager)
                        getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                if (im != null) im.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
            } catch (Exception ignored) {
            }
            // 键盘起来后可视高度变了，再校正一次位置
            scrollComposerToUpperHalf(380);
        }, 120L);
    }

    /** 已给内容垫过多少底部空间（只垫一次） */
    private int paddedBottom;

    /**
     * 把输入框滚到可视区上方 22% 处。
     *
     * 不用 scrollTo 算绝对目标（键盘弹出后可视高度会变，容易算过头把输入框顶出屏幕），
     * 直接量「输入框离目标线多少」再 scrollBy，重复调用也自洽。
     */
    private void scrollComposerToUpperHalf(long delay) {
        final android.widget.ScrollView sv = findScrollHost();
        if (sv == null) return;
        final View content = sv.getChildAt(0);
        if (content != null && paddedBottom == 0) {
            paddedBottom = Math.max(Ui.dp(getContext(), 220), sv.getHeight() / 2);
            content.setPadding(content.getPaddingLeft(), content.getPaddingTop(),
                    content.getPaddingRight(), content.getPaddingBottom() + paddedBottom);
        }
        Runnable task = () -> {
            int[] self = new int[2];
            int[] host = new int[2];
            getLocationOnScreen(self);
            sv.getLocationOnScreen(host);
            int inputY = self[1] + Ui.dp(getContext(), 30);
            int desired = host[1] + (int) (sv.getHeight() * 0.22f);
            int delta = inputY - desired;
            if (Math.abs(delta) > Ui.dp(getContext(), 6)) sv.smoothScrollBy(0, delta);
        };
        if (delay <= 0) sv.post(task);
        else sv.postDelayed(task, delay);
    }


    private android.widget.ScrollView findScrollHost() {
        View v = this;
        while (v.getParent() instanceof View) {
            View parent = (View) v.getParent();
            if (parent instanceof android.widget.ScrollView) {
                return (android.widget.ScrollView) parent;
            }
            v = parent;
        }
        return null;
    }

    /** 退出回复态 */
    private void clearReply() {
        replyTo = 0;
        replyName = "";
        replyTipBar.setVisibility(GONE);
        input.setHint("说点什么…");
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

    /**
     * 一条评论：头像 + 内容（可展开二级回复）+ 最右侧点赞列。
     * 点赞列固定 46dp、右缘与输入框「发表」按钮对齐，字号比原来大一档。
     */
    private View row(Models.Comment cm) {
        Context c = getContext();
        LinearLayout row = Ui.row(c);
        row.setGravity(Gravity.TOP);
        LinearLayout.LayoutParams rlp = Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        rlp.topMargin = Ui.dp(c, 12);
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

        // 二级回复容器（默认隐藏，点「回复 N」展开）
        LinearLayout replyBox = Ui.col(c);
        replyBox.setVisibility(GONE);
        LinearLayout.LayoutParams rbp = Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        rbp.topMargin = Ui.dp(c, 6);
        col.addView(replyBox, rbp);

        if (cm.replyCount > 0) {
            final TextView more = Ui.text(c, "展开 " + Ui.shortNum(cm.replyCount) + " 条回复",
                    11f, Ui.accentBright());
            more.setPadding(0, Ui.dp(c, 4), Ui.dp(c, 8), Ui.dp(c, 4));
            more.setClickable(true);
            final boolean[] open = {false};
            final boolean[] loading = {false};
            more.setOnClickListener(v -> {
                if (open[0]) {
                    open[0] = false;
                    replyBox.setVisibility(GONE);
                    more.setText("展开 " + Ui.shortNum(cm.replyCount) + " 条回复");
                    return;
                }
                open[0] = true;
                replyBox.setVisibility(VISIBLE);
                more.setText("收起回复");
                if (loading[0] || replyBox.getChildCount() > 0) return;
                loading[0] = true;
                final TextView tipRow = Ui.text(c, "加载回复中…", 10.5f, Ui.C_DIM);
                replyBox.addView(tipRow);
                source.replies(cm.id, 1, new Api.Cb<List<Models.Comment>>() {
                    @Override
                    public void ok(List<Models.Comment> items) {
                        replyBox.removeView(tipRow);
                        loading[0] = false;
                        if (items.isEmpty()) {
                            replyBox.addView(Ui.text(c, "还没有回复", 10.5f, Ui.C_DIM));
                            return;
                        }
                        for (Models.Comment r : items) replyBox.addView(replyRow(r));
                    }

                    @Override
                    public void err(String msg) {
                        replyBox.removeView(tipRow);
                        loading[0] = false;
                        replyBox.addView(Ui.text(c, "回复加载失败：" + msg, 10.5f, Ui.C_DIM));
                    }
                });
            });
            col.addView(more);
        }

        // 整行可点：点一下就把这条评论设成回复目标（输入框聚焦 + 弹出键盘）
        row.setClickable(true);
        row.setOnClickListener(v -> startReply(cm));

        // 右侧点赞列：与「发表」按钮同一列（右缘对齐），字号大一档
        TextView like = Ui.text(c, likeText(c, cm, isLikedCm(cm)), 12.5f,
                isLikedCm(cm) ? Ui.C_AMBER : Ui.C_DIM);
        like.setGravity(Gravity.CENTER);
        if (likeKind != null && cm.id > 0) {
            like.setClickable(true);
            like.setOnClickListener(v -> {
                boolean now = Store.get(c).toggleLike(likeKind, cm.id);
                like.setText(likeText(c, cm, now));
                like.setTextColor(now ? Ui.C_AMBER : Ui.C_DIM);
                like.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
            });
        }
        LinearLayout.LayoutParams lkp = Ui.lp(Ui.dp(c, 46), ViewGroup.LayoutParams.WRAP_CONTENT);
        lkp.leftMargin = Ui.dp(c, 6);
        row.addView(like, lkp);
        return row;
    }

    private boolean isLikedCm(Models.Comment cm) {
        return likeKind != null && cm.id > 0 && Store.get(getContext()).isLiked(likeKind, cm.id);
    }

    /** 二级回复：缩进一条，头像更小，不带点赞列 */
    private View replyRow(Models.Comment r) {
        Context c = getContext();
        LinearLayout row = Ui.row(c);
        row.setGravity(Gravity.TOP);
        LinearLayout.LayoutParams rlp = Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        rlp.topMargin = Ui.dp(c, 6);
        rlp.leftMargin = Ui.dp(c, 6);
        row.setLayoutParams(rlp);

        TextView avatar = Ui.bold(c, firstChar(r.nickname), 9f, Ui.onAmber());
        avatar.setGravity(Gravity.CENTER);
        int color;
        try {
            color = Color.parseColor(r.avatarColor);
        } catch (Exception e) {
            color = 0xFF6E8494;
        }
        avatar.setBackground(Ui.round(color, Ui.dp(c, 12)));
        row.addView(avatar, Ui.lp(Ui.dp(c, 24), Ui.dp(c, 24)));

        LinearLayout col = Ui.col(c);
        LinearLayout.LayoutParams clp = Ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.weight = 1f;
        clp.leftMargin = Ui.dp(c, 8);
        row.addView(col, clp);

        LinearLayout meta = Ui.row(c);
        meta.addView(Ui.text(c, r.nickname, 10.5f, Ui.subText()));
        if (!r.time.isEmpty()) {
            meta.addView(Ui.text(c, " · " + Models.shortenTime(r.time), 9.5f, Ui.C_DIM));
        }
        col.addView(meta);

        TextView text = Ui.text(c, r.content, 11.5f, Ui.bodyText());
        text.setLineSpacing(Ui.dp(c, 2), 1f);
        col.addView(text);
        return row;
    }

    private static String likeText(Context c, Models.Comment cm, boolean liked) {
        int n = cm.likeCount + (liked ? 1 : 0);
        return "♥ " + Ui.shortNum(n);
    }

    private static String firstChar(String s) {
        if (s == null || s.isEmpty()) return "·";
        return s.substring(0, 1);
    }
}
