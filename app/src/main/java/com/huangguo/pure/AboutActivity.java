package com.huangguo.pure;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

/**
 * 关于：应用版本 / GitHub 仓库 / Telegram 群组 / 开源许可。
 * GitHub 仓库链接拿到正式地址后改 GITHUB_URL 一行即可。
 */
public class AboutActivity extends AppCompatActivity {

    /** GitHub 仓库主页 */
    public static final String GITHUB_URL = "https://github.com/Zihium/Huangguo-Pure";
    /** Telegram 群组邀请链接 */
    public static final String TG_URL = "https://t.me/+pqd3pxZEkrk1N2Q1";

    private static final String LICENSE_TEXT =
            "本项目基于以下开源项目构建，感谢原作者：\n\n"
                    + "AndroidX / Jetpack\n"
                    + "appcompat 1.7.0 · recyclerview 1.3.2 · core 1.13.1\n"
                    + "Apache License 2.0 · androidx.dev\n\n"
                    + "Media3 / ExoPlayer 1.4.1\n"
                    + "exoplayer · exoplayer-hls · media3-ui\n"
                    + "Apache License 2.0 · developer.android.com/media/media3\n\n"
                    + "OkHttp 4.12.0\n"
                    + "HTTP 客户端 · square.github.io/okhttp\n"
                    + "Apache License 2.0\n\n"
                    + "Liquid Glass 视觉规范\n"
                    + "android-liquid-glass（LiquidBottomTabs 参数体系）\n"
                    + "MIT License";

    public static void open(Context c) {
        c.startActivity(new Intent(c, AboutActivity.class));
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Ui.C_BG);
        setContentView(root);
        root.addView(new Aurora(this), new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout content = Ui.col(this);
        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        sv.addView(content);
        root.addView(sv, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // 顶栏：与设置页同构 —— wrap 高 = 状态栏 + 54dp，标题行 54dp 底部对齐，
        // 状态栏高度只算一次，返回键/标题垂直居中，透明无分隔线
        int barH = GlassHeader.statusBarHeight(this) + dp(54);
        content.setPadding(0, barH, 0, 0);

        LinearLayout bar = Ui.row(this);
        bar.setPadding(dp(14), 0, dp(14), 0);

        FrameLayout backWrap = new FrameLayout(this);
        backWrap.setBackground(new GlassDrawable(dp(20)));
        backWrap.setClickable(true);
        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.ic_back);
        icon.setColorFilter(Ui.C_TEXT);
        backWrap.addView(icon, new FrameLayout.LayoutParams(
                dp(18), dp(18), Gravity.CENTER));
        backWrap.setOnClickListener(v -> finish());
        bar.addView(backWrap, Ui.lp(dp(38), dp(38)));

        TextView title = Ui.bold(this, "关于", 17f, Ui.C_TEXT);
        LinearLayout.LayoutParams tp = Ui.lp(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        tp.leftMargin = dp(12);
        bar.addView(title, tp);

        FrameLayout wrap = new FrameLayout(this);
        wrap.addView(bar, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(54), Gravity.BOTTOM));
        root.addView(wrap, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, barH, Gravity.TOP));

        String ver = "未知";
        try {
            ver = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception ignored) {
        }

        LinearLayout card = Ui.col(this);
        card.setPadding(dp(4), dp(4), dp(4), dp(4));
        card.setBackground(new GlassDrawable(dp(20)));
        LinearLayout.LayoutParams cp = Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        cp.leftMargin = dp(16);
        cp.rightMargin = dp(16);
        cp.topMargin = dp(10);
        content.addView(card, cp);

        card.addView(row("i", "版本", "Huangguo Pure 纯净客户端", "v" + ver, null));
        card.addView(div());
        card.addView(row("◆", "开发者", "设计与开发 · Zihium", "Zihium", null));
        card.addView(div());
        card.addView(row("GH", "GitHub", "源码仓库", "", v -> openUrl(GITHUB_URL)));
        card.addView(div());
        card.addView(row("TG", "Telegram 群组", "更新通知 · 交流反馈", "", v -> openUrl(TG_URL)));
        card.addView(div());
        card.addView(row("§", "开源许可", "本项目使用的开源组件", "", v -> showLicense()));
        card.addView(div());
        card.addView(row("≡", "更新日志", "从 GitHub 仓库实时获取", "",
                v -> Update.showChangelog(this)));
        card.addView(div());
        card.addView(row("⇗", "分享给好友", "微信 · 朋友圈 · QQ", "",
                v -> showShareMenu(v)));

        TextView foot = Ui.text(this, "Huangguo Pure · 非官方第三方客户端", 10.5f, Ui.C_DIM);
        foot.setGravity(Gravity.CENTER);
        foot.setPadding(0, dp(26), 0, dp(26));
        content.addView(foot);
    }

    private int dp(float v) {
        return Ui.dp(this, v);
    }

    private View div() {
        View v = new View(this);
        v.setBackgroundColor(0x14FFFFFF);
        v.setLayoutParams(Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT, 1));
        return v;
    }

    private LinearLayout row(String glyph, String title, String sub, String value,
                             android.view.View.OnClickListener onClick) {
        LinearLayout wrap = Ui.row(this);
        wrap.setPadding(dp(14), dp(12), dp(14), dp(12));
        wrap.setGravity(Gravity.CENTER_VERTICAL);
        if (onClick != null) {
            wrap.setClickable(true);
            wrap.setOnClickListener(onClick);
        }

        TextView g = Ui.bold(this, glyph, 12f, Ui.C_AMBER);
        g.setGravity(Gravity.CENTER);
        g.setBackground(Ui.stroke(0x26F5B942, 0x59F5B942, dp(20), 1));
        wrap.addView(g, Ui.lp(dp(38), dp(38)));

        LinearLayout texts = Ui.col(this);
        LinearLayout.LayoutParams tp = Ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT);
        tp.weight = 1f;
        tp.leftMargin = dp(12);
        wrap.addView(texts, tp);
        texts.addView(Ui.bold(this, title, 13.5f, Ui.C_TEXT));
        TextView s = Ui.text(this, sub, 10.5f, Ui.C_DIM);
        LinearLayout.LayoutParams sp = Ui.lp(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        sp.topMargin = dp(3);
        texts.addView(s, sp);

        if (value != null && !value.isEmpty()) {
            TextView v = Ui.bold(this, value, 11.5f, Ui.C_AMBER);
            v.setGravity(Gravity.CENTER);
            v.setPadding(dp(12), dp(6), dp(12), dp(6));
            v.setBackground(Ui.stroke(0x26F5B942, 0x4DF5B942, dp(14), 1));
            wrap.addView(v, Ui.lp(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
        } else {
            TextView arrow = Ui.text(this, "›", 15f, Ui.C_DIM);
            wrap.addView(arrow, Ui.lp(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        return wrap;
    }

    private void openUrl(String url) {
        if (url == null || url.isEmpty()) {
            Toast.makeText(this, "链接待配置", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception e) {
            Toast.makeText(this, "没有可用的浏览器", Toast.LENGTH_SHORT).show();
        }
    }

    /** 分享文案：一句话简介 + 开源仓库地址 */
    private String shareText() {
        return "推荐一个干净好用的黄果短剧第三方客户端 Huangguo Pure："
                + "无广告、纯本地收藏、液态玻璃界面。开源地址："
                + GITHUB_URL;
    }

    /** 分享入口：液态玻璃菜单选目标 */
    private void showShareMenu(View anchor) {
        GlassMenu.show(anchor, GlassMenu.items(
                new GlassMenu.Item("💬", "微信好友",
                        () -> shareTo("com.tencent.mm", null, "微信")),
                new GlassMenu.Item("◉", "微信朋友圈",
                        () -> shareTo("com.tencent.mm",
                                "com.tencent.mm.ui.tools.ShareToTimeLineUI", "微信朋友圈")),
                new GlassMenu.Item("🐧", "QQ",
                        () -> shareTo("com.tencent.mobileqq", null, "QQ"))));
    }

    /** 定向分享；目标应用不在时回退系统选择器 */
    private void shareTo(String pkg, String cls, String label) {
        try {
            Intent i = new Intent(Intent.ACTION_SEND);
            i.setType("text/plain");
            i.putExtra(Intent.EXTRA_TEXT, shareText());
            if (cls == null) {
                i.setPackage(pkg);
            } else {
                i.setClassName(pkg, cls);
            }
            startActivity(i);
        } catch (Exception e) {
            Toast.makeText(this, "未安装 " + label + "，改用系统分享", Toast.LENGTH_SHORT).show();
            try {
                Intent i = new Intent(Intent.ACTION_SEND);
                i.setType("text/plain");
                i.putExtra(Intent.EXTRA_TEXT, shareText());
                startActivity(Intent.createChooser(i, "分享给好友"));
            } catch (Exception ignored) {
            }
        }
    }

    /** 开源许可：液态玻璃卡片弹层 */
    private void showLicense() {
        Dialog dlg = new Dialog(this);
        FrameLayout box = new FrameLayout(this);
        box.setBackground(new GlassDrawable(dp(22)));
        box.setPadding(dp(18), dp(18), dp(18), dp(12));

        LinearLayout col = Ui.col(this);
        col.addView(Ui.bold(this, "开源许可", 15f, Ui.C_TEXT));

        TextView body = Ui.text(this, LICENSE_TEXT, 11.5f, Ui.C_SUB);
        body.setLineSpacing(dp(3), 1f);
        ScrollView sc = new ScrollView(this);
        sc.addView(body);
        col.addView(sc, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(360)));

        TextView close = Ui.bold(this, "我知道了", 12.5f, Ui.C_AMBER);
        close.setGravity(Gravity.CENTER);
        close.setPadding(0, dp(12), 0, dp(4));
        close.setClickable(true);
        close.setOnClickListener(v -> dlg.dismiss());
        col.addView(close);

        box.addView(col, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        dlg.setContentView(box);
        Window w = dlg.getWindow();
        if (w != null) {
            w.setBackgroundDrawableResource(android.R.color.transparent);
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        dlg.show();
    }

    /** 跟随系统模式下系统切深浅色：原地重建本页，不回主界面 */
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        boolean dark = Theme.isDark(this);
        if (Theme.mode() == Theme.AUTO && dark != Ui.darkGlass) {
            Ui.retheme(this);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 兜底：配置变化发生在后台时，回到前台再补一次
        if (Ui.darkGlass != Theme.isDark(this)) {
            Ui.retheme(this);
        }
    }
}
