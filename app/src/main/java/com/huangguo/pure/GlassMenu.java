package com.huangguo.pure;

import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * 液态玻璃下拉菜单（锚定式）：博客/专题顶栏的「更多」、其它需要小菜单的地方共用。
 * 弹层 = GlassDrawable 卡片 + 行（圆徽 glyph + 文本），点行回调并收起，点外部自动消失。
 */
public final class GlassMenu {

    public static class Item {
        public final String glyph;
        public final String text;
        public final Runnable action;

        public Item(String glyph, String text, Runnable action) {
            this.glyph = glyph;
            this.text = text;
            this.action = action;
        }
    }

    public static void show(View anchor, List<Item> items) {
        if (anchor == null || items == null || items.isEmpty()) return;
        android.content.Context c = anchor.getContext();

        final PopupWindow[] pwRef = new PopupWindow[1];

        // 和 Dock 同源的液态玻璃：GlassSurface 采样弹出位置背后那一小块页面，
        // 做真实折射；PopupWindow 关闭后 view 被移除，onDetachedFromWindow 自动停采样。
        GlassSurface surface = new GlassSurface(c, 18f,
                Ui.darkGlass ? 0x8A121522 : 0xA6FFFFFF, 8f);
        View root = anchor.getRootView();
        if (root != null) surface.bind(root);

        LinearLayout col = Ui.col(c);
        col.setPadding(Ui.dp(c, 6), Ui.dp(c, 8), Ui.dp(c, 6), Ui.dp(c, 8));
        surface.addView(col, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        for (final Item it : items) {
            LinearLayout row = Ui.row(c);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(Ui.dp(c, 12), Ui.dp(c, 11), Ui.dp(c, 12), Ui.dp(c, 11));

            TextView g = Ui.bold(c, it.glyph, 13.5f, Ui.C_AMBER);
            g.setGravity(Gravity.CENTER);
            g.setBackground(Ui.subtle(c, Ui.dp(c, 15)));
            row.addView(g, Ui.lp(Ui.dp(c, 30), Ui.dp(c, 30)));

            TextView t = Ui.text(c, it.text, 13f, Ui.C_TEXT);
            LinearLayout.LayoutParams tp = Ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT);
            tp.weight = 1f;
            tp.leftMargin = Ui.dp(c, 12);
            row.addView(t, tp);

            row.setClickable(true);
            row.setOnClickListener(v -> {
                pwRef[0].dismiss();
                if (it.action != null) it.action.run();
            });
            col.addView(row, Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
        }

        final PopupWindow pw = new PopupWindow(surface, Ui.dp(c, 200),
                ViewGroup.LayoutParams.WRAP_CONTENT, true);
        pwRef[0] = pw;
        pw.setBackgroundDrawable(new ColorDrawable(0));
        pw.setOutsideTouchable(true);
        pw.setElevation(Ui.dp(c, 12));
        pw.showAsDropDown(anchor, -Ui.dp(c, 6), Ui.dp(c, 6), Gravity.END);
    }

    public static List<Item> items(Item... arr) {
        List<Item> out = new ArrayList<>();
        for (Item i : arr) out.add(i);
        return out;
    }
}
