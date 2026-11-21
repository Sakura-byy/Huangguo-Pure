package com.huangguo.pure;

import android.content.Context;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

/**
 * 顶栏：透明，与页面背景完全同色、无边框不分隔。
 *
 * 顶栏放在页面 LinearLayout 顶部，正文从它下方开始滚，
 * 没有内容会从它底下穿过，所以直接不做任何背景与分隔线，
 * 状态栏区域也和正文背景连成一体。
 *
 * 高度写死成「状态栏 + 56dp」：放进页面的竖向 LinearLayout 时，
 * wrap_content 会被父级量成整屏高，标题就被居中到屏幕中间。
 */
public class GlassHeader extends FrameLayout {

    private final LinearLayout row;

    public GlassHeader(Context c) {
        super(c);

        int top = statusBarHeight(c);
        setPadding(Ui.dp(c, 18), top, Ui.dp(c, 14), 0);

        row = Ui.row(c);
        addView(row, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, Ui.dp(c, 56), Gravity.BOTTOM));
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int h = statusBarHeight(getContext()) + Ui.dp(getContext(), 56);
        super.onMeasure(widthSpec, MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY));
    }

    /** 标题行：调用方往里追加右侧控件（首页的搜索框就挂这儿） */
    public LinearLayout row() {
        return row;
    }

    static int statusBarHeight(Context c) {
        int id = c.getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (id > 0) {
            int h = c.getResources().getDimensionPixelSize(id);
            if (h > 0) return h;
        }
        return Ui.dp(c, 24);
    }
}
