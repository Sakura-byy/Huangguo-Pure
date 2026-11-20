package com.huangguo.pure;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

/** 通用顶栏 / 小部件 */
public final class Chrome {

    private Chrome() {
    }

    /** 顶栏：只留标题（chip 参数保留兼容旧调用，橙黄横幅已按要求去掉） */
    public static LinearLayout titleBar(Context c, String title, String chip) {
        LinearLayout bar = Ui.row(c);
        bar.setPadding(Ui.dp(c, 18), Ui.dp(c, 8), Ui.dp(c, 18), Ui.dp(c, 8));
        bar.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(c, 54)));

        TextView logo = Ui.bold(c, title, 20f, Ui.C_TEXT);
        bar.addView(logo);
        return bar;
    }

    public static TextView chip(Context c, String text, boolean active) {
        TextView t = Ui.text(c, text, 12.5f, active ? 0xFF1A1206 : Ui.C_TEXT);
        t.setGravity(Gravity.CENTER);
        t.setPadding(Ui.dp(c, 14), Ui.dp(c, 8), Ui.dp(c, 14), Ui.dp(c, 8));
        if (active) {
            t.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            t.setBackground(Ui.round(Ui.C_AMBER, Ui.dp(c, 18)));
        } else {
            t.setBackground(Ui.stroke(0x14FFFFFF, 0x26FFFFFF, Ui.dp(c, 18), 1));
        }
        return t;
    }

    public static View divider(Context c) {
        View v = new View(c);
        v.setBackgroundColor(0x14FFFFFF);
        v.setLayoutParams(Ui.lp(LinearLayout.LayoutParams.MATCH_PARENT, 1));
        return v;
    }
}
