package com.huangguo.pure;

import android.content.Context;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

/**
 * 内容区左右滑动切页（分类的 剧集/专题/博客 + 三级 chips、排行的三个榜单、我的的 收藏/历史）。
 *
 * 与旧版「只观察、不消费」不同，这里用 {@link #onInterceptTouchEvent} 在判定为横滑后
 * **主动拦截**手势：子 View（RecyclerView 行卡片）收到 ACTION_CANCEL，点击不再触发，
 * 从而根治「排行榜里左右滑一下却点进了视频」的误触。
 *
 * 判定规则：
 *   · 横向位移 > 24dp 且 > 纵向位移 × 1.6 → 认定为横滑并拦截，其余情况一律放行
 *   · 起手点落在「本来就能横向滚」的容器里 → 整次手势放行，不抢子容器的横向滚
 *   · 抬手时横向位移 ≥ 64dp 才真正翻页，小于它只算一次「划了一下」，不切页也不点卡片
 */
public class SwipeFrame extends FrameLayout {

    /** dir：+1 = 向左滑（下一页），-1 = 向右滑（上一页） */
    public interface OnSwipe {
        void onSwipe(int dir);
    }

    private static final float MIN_DP = 24f;
    private static final float TRIGGER_DP = 64f;
    private static final float RATIO = 1.6f;

    private OnSwipe swipe;

    /**
     * true 时：起手点落在嵌套的 SwipeFrame 上就把整次手势让给子层，
     * 由子层在自己的边界上回调切一级页。用于「一级页容器」这一层，
     * 否则外层会把分类/排行里二级、三级的横滑全吃掉。
     */
    private boolean deferToInner;

    /** false 时完全不拦截手势（多选拖选阶段用） */
    private boolean enabled = true;

    public SwipeFrame setSwipeEnabled(boolean v) {
        enabled = v;
        return this;
    }

    private float downX, downY;
    private boolean intercepting;
    private boolean blocked;

    public SwipeFrame(Context c) {
        super(c);
    }

    public void setOnSwipe(OnSwipe s) {
        swipe = s;
    }

    public SwipeFrame deferToInnerSwipe(boolean v) {
        deferToInner = v;
        return this;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (!enabled) return false;
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = ev.getX();
                downY = ev.getY();
                intercepting = false;
                blocked = hasHorizontalScroller(this, ev.getX(), ev.getY())
                        || (deferToInner && hasInnerSwipe(this, ev.getX(), ev.getY()));
                break;

            case MotionEvent.ACTION_MOVE: {
                if (intercepting) return true;
                if (blocked) return false;
                float dx = ev.getX() - downX;
                float dy = ev.getY() - downY;
                float min = Ui.dp(getContext(), MIN_DP);
                if (Math.abs(dx) > min && Math.abs(dx) > Math.abs(dy) * RATIO) {
                    // 横滑成立：拦截，子 View 收到 CANCEL，卡片点击被取消
                    intercepting = true;
                    android.util.Log.d("Swipe", "intercept dx=" + dx + " dy=" + dy);
                    return true;
                }
                if (Math.abs(dy) > min) {
                    // 纵向滚动手势成立：本次放弃横滑
                    blocked = true;
                    return false;
                }
                break;
            }

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                intercepting = false;
                blocked = false;
                break;

            default:
                break;
        }
        return false;
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (!intercepting) return super.onTouchEvent(ev);
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_MOVE:
                return true;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                float dx = ev.getX() - downX;
                float trigger = Ui.dp(getContext(), TRIGGER_DP);
                android.util.Log.d("Swipe", "UP dx=" + dx + " trigger=" + trigger);
                if (swipe != null) {
                    if (dx <= -trigger) swipe.onSwipe(1);
                    else if (dx >= trigger) swipe.onSwipe(-1);
                }
                intercepting = false;
                blocked = false;
                return true;
            }

            default:
                return true;
        }
    }

    /** 递归找起手点下方有没有嵌套的 SwipeFrame（二级/三级横滑容器） */
    private boolean hasInnerSwipe(ViewGroup parent, float px, float py) {
        for (int i = parent.getChildCount() - 1; i >= 0; i--) {
            View ch = parent.getChildAt(i);
            if (ch.getVisibility() != VISIBLE) continue;
            float cx = px - ch.getLeft() - ch.getTranslationX();
            float cy = py - ch.getTop() - ch.getTranslationY();
            if (cx < 0f || cy < 0f || cx > ch.getWidth() || cy > ch.getHeight()) continue;
            if (ch instanceof SwipeFrame) return true;
            if (ch instanceof ViewGroup && hasInnerSwipe((ViewGroup) ch, cx, cy)) return true;
        }
        return false;
    }

    /** 递归找起手点下方有没有「能横向滚动」的容器 */
    private boolean hasHorizontalScroller(ViewGroup parent, float px, float py) {
        for (int i = parent.getChildCount() - 1; i >= 0; i--) {
            View ch = parent.getChildAt(i);
            if (ch.getVisibility() != VISIBLE) continue;
            float cx = px - ch.getLeft() - ch.getTranslationX();
            float cy = py - ch.getTop() - ch.getTranslationY();
            if (cx < 0f || cy < 0f || cx > ch.getWidth() || cy > ch.getHeight()) continue;
            if (ch.canScrollHorizontally(-1) || ch.canScrollHorizontally(1)) return true;
            if (ch instanceof ViewGroup && hasHorizontalScroller((ViewGroup) ch, cx, cy)) {
                return true;
            }
        }
        return false;
    }
}
