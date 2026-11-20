package com.huangguo.pure;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;

/**
 * 主题三态：跟随系统 / 浅色 / 深色。
 * 选择持久化在 hg_client 里；MainActivity 切换后 recreate() 整体重建，
 * 全部 UI 都是代码构建，重建后自然套上新调色板。
 */
public final class Theme {

    public static final int AUTO = 0;
    public static final int LIGHT = 1;
    public static final int DARK = 2;

    private static int mode = AUTO;

    private Theme() {
    }

    public static void init(Context c) {
        SharedPreferences p = c.getApplicationContext()
                .getSharedPreferences("hg_client", Context.MODE_PRIVATE);
        mode = p.getInt("theme_mode", AUTO);
    }

    public static int mode() {
        return mode;
    }

    public static void setMode(Context c, int m) {
        mode = m;
        c.getApplicationContext()
                .getSharedPreferences("hg_client", Context.MODE_PRIVATE)
                .edit().putInt("theme_mode", m).apply();
    }

    /** 解析当前实际生效的明暗（AUTO 按系统 uiMode） */
    public static boolean isDark(Context c) {
        int ui = c.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK;
        return mode == DARK || (mode == AUTO && ui == Configuration.UI_MODE_NIGHT_YES);
    }

    public static String label(int m) {
        if (m == AUTO) return "跟随系统";
        return m == LIGHT ? "浅色" : "深色";
    }
}
