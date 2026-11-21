package com.huangguo.pure;

import android.app.Activity;
import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * 通用液态玻璃弹层：暗化 scrim + liquidPanel 卡片。
 *
 * 设置页的更新提示、关于页的更新日志都走这里，和「软件清理」那套弹层同一观感。
 * 卡片宽度固定 320dp，高度自适应；内容超出时由调用方自己塞 ScrollView。
 */
public final class GlassPanel {

    private GlassPanel() {
    }

    /** 居中玻璃卡片；返回整个 overlay，调用方可自行 removeView 关闭 */
    public static FrameLayout show(Activity act, View content, boolean outsideDismiss) {
        final FrameLayout overlay = new FrameLayout(act);
        overlay.setBackgroundColor(Ui.darkGlass ? 0x8C000000 : 0x66DCE4EF);
        overlay.setClickable(true);

        FrameLayout card = new FrameLayout(act);
        card.setBackground(Ui.liquidPanel(act, Ui.dp(act, 20)));
        int pad = Ui.dp(act, 20);
        card.setPadding(pad, pad, pad, pad);
        card.addView(content, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(
                Ui.dp(act, 320), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        overlay.addView(card, clp);

        if (outsideDismiss) {
            overlay.setOnClickListener(v -> {
                ViewGroup p = (ViewGroup) overlay.getParent();
                if (p != null) p.removeView(overlay);
            });
        }
        act.getWindow().getDecorView().post(() -> {
            try {
                act.getWindow().addContentView(overlay, new ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT));
            } catch (Throwable ignore) {
            }
        });
        return overlay;
    }

    /** 关闭一个 overlay */
    public static void close(FrameLayout overlay) {
        if (overlay == null) return;
        ViewGroup p = (ViewGroup) overlay.getParent();
        if (p != null) p.removeView(overlay);
    }

    /** 弹层里的正文滚动区：固定最大高度，超长内容可滚 */
    public static ScrollView scrollBody(Context c, TextView body, int maxHeightDp) {
        ScrollView sc = new ScrollView(c);
        sc.setVerticalScrollBarEnabled(true);
        body.setLineSpacing(Ui.dp(c, 4), 1f);
        sc.addView(body);
        sc.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(c, maxHeightDp)));
        return sc;
    }

    /** 卡片底部按钮：右对齐，最后一个为强调色主按钮 */
    public static LinearLayout buttons(Context c, final FrameLayout[] overlayRef,
                                       String[] labels, Runnable[] actions) {
        LinearLayout row = Ui.row(c);
        LinearLayout.LayoutParams rp = Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        rp.topMargin = Ui.dp(c, 16);
        row.setLayoutParams(rp);
        View spacer = new View(c);
        LinearLayout.LayoutParams sp = Ui.lp(0, 1);
        sp.weight = 1f;
        row.addView(spacer, sp);
        for (int i = 0; i < labels.length; i++) {
            final Runnable action = actions[i];
            boolean primary = i == labels.length - 1;
            TextView btn = Ui.bold(c, labels[i], 12.5f,
                    primary ? Ui.accentBright() : Ui.C_SUB);
            btn.setGravity(Gravity.CENTER);
            btn.setPadding(Ui.dp(c, 16), Ui.dp(c, 8), Ui.dp(c, 16), Ui.dp(c, 8));
            btn.setBackground(Ui.stroke(Ui.chipFill(), Ui.chipStroke(), Ui.dp(c, 16), 1));
            btn.setClickable(true);
            btn.setOnClickListener(v -> {
                close(overlayRef[0]);
                if (action != null) action.run();
            });
            LinearLayout.LayoutParams bp = Ui.lp(ViewGroup.LayoutParams.WRAP_CONTENT,
                    Ui.dp(c, 34));
            if (i > 0) bp.leftMargin = Ui.dp(c, 10);
            row.addView(btn, bp);
        }
        return row;
    }
}
