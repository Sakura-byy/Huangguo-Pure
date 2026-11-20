package com.huangguo.pure;

import android.content.Context;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

/**
 * 下拉刷新：只拦「内容已经到顶再往下拉」那一段手势，其余全部交给子 View。
 *
 * 顶部提示是一颗玻璃胶囊，跟着手指从上方滑下来：
 *   下拉刷新 → 松手刷新 → 正在刷新…
 * 刷新完成（宿主调用 Done.done()）后内容回弹、提示收回去。
 */
public class PullRefresh extends FrameLayout {

    /** 宿主实现：真正去拉数据，结束时调 done.done() */
    public interface Refreshable {
        void onPullRefresh(Done done);
    }

    public interface Done {
        void done();
    }

    private static final int MAX_DP = 88;
    private static final int THRESHOLD_DP = 58;

    private final TextView tip;
    private View content;
    private Refreshable target;

    private float downY;
    private float offset;
    private boolean pulling;
    private boolean refreshing;
    private boolean atTop = true;

    public PullRefresh(Context c) {
        super(c);
        setClipChildren(false);
        setClipToPadding(false);

        tip = Ui.text(c, "下拉刷新", 11.5f, Ui.C_TEXT);
        tip.setGravity(Gravity.CENTER);
        tip.setPadding(Ui.dp(c, 16), Ui.dp(c, 7), Ui.dp(c, 16), Ui.dp(c, 7));
        tip.setBackground(Ui.darkGlass
                ? new GlassDrawable(Ui.dp(c, 16), 0xF01C2333, 0xE60D121B,
                        0xF00A0E15, 0x66FFFFFF)
                : new GlassDrawable(Ui.dp(c, 16), 0xF7FFFFFF, 0xF0F3F8FD,
                        0xE9EDF4, 0x4D1A2332));
        tip.setVisibility(INVISIBLE);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        addView(tip, lp);
    }

    /** content 是页面里那块可滚动的内容；target 提供真正的刷新动作 */
    public PullRefresh attach(View content, Refreshable target) {
        this.content = content;
        this.target = target;
        addView(content, 0, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        bringChildToFront(tip);
        return this;
    }

    private int maxPx() {
        return Ui.dp(getContext(), MAX_DP);
    }

    private int thresholdPx() {
        return Ui.dp(getContext(), THRESHOLD_DP);
    }

    private boolean canPull() {
        return content != null && atTop && !refreshing;
    }

    /**
     * 内容根通常是 LinearLayout（标题栏 + 列表），它自己 canScrollVertically 永远是 false，
     * 所以要往下找真正能滚的那个（RecyclerView / ScrollView）。只在手指按下时算一次。
     */
    private static boolean canScrollUp(View v) {
        if (v == null) return false;
        if (v.canScrollVertically(-1)) return true;
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                if (canScrollUp(g.getChildAt(i))) return true;
            }
        }
        return false;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downY = e.getY();
                pulling = false;
                atTop = !canScrollUp(content);
                return false;
            case MotionEvent.ACTION_MOVE:
                if (!canPull()) return false;
                float dy = e.getY() - downY;
                if (dy > Ui.dp(getContext(), 14)) {
                    pulling = true;
                    return true;
                }
                return false;
            default:
                return false;
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (content == null) return super.onTouchEvent(e);
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downY = e.getY();
                return true;

            case MotionEvent.ACTION_MOVE: {
                float dy = Math.max(0f, e.getY() - downY);
                // 阻尼：越拉越沉，手感接近 iOS
                offset = Math.min(maxPx(), dy * 0.55f);
                applyOffset();
                return true;
            }

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (offset >= thresholdPx()) {
                    startRefresh();
                } else {
                    settleBack();
                }
                pulling = false;
                return true;

            default:
                return super.onTouchEvent(e);
        }
    }

    private void applyOffset() {
        content.setTranslationY(offset);
        tip.setVisibility(VISIBLE);
        int h = Math.max(tip.getHeight(), Ui.dp(getContext(), 30));
        tip.setTranslationY(offset - h - Ui.dp(getContext(), 6));
        tip.setAlpha(Math.min(1f, offset / (float) thresholdPx()));
        tip.setText(offset >= thresholdPx() ? "松手刷新" : "下拉刷新");
    }

    private void startRefresh() {
        refreshing = true;
        offset = thresholdPx();
        content.animate().translationY(offset).setDuration(120).start();
        int h = Math.max(tip.getHeight(), Ui.dp(getContext(), 30));
        tip.setText("正在刷新…");
        tip.setAlpha(1f);
        tip.animate().translationY(offset - h - Ui.dp(getContext(), 6)).setDuration(120).start();

        if (target == null) {
            settleBack();
            return;
        }
        target.onPullRefresh(this::settleBack);
        // 兜底：网络卡住也别让提示一直挂着
        postDelayed(() -> {
            if (refreshing) settleBack();
        }, 8000);
    }

    private void settleBack() {
        refreshing = false;
        offset = 0f;
        if (content != null) {
            content.animate().translationY(0f).setDuration(220).start();
        }
        int h = Math.max(tip.getHeight(), Ui.dp(getContext(), 30));
        tip.animate().translationY(-h - Ui.dp(getContext(), 20)).alpha(0f).setDuration(200)
                .withEndAction(() -> tip.setVisibility(INVISIBLE)).start();
    }
}
