package com.huangguo.pure;

import android.content.Context;
import android.graphics.Color;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;



/** 我的：本地收藏 + 观看历史；支持下拉刷新 */
public class MinePage extends FrameLayout implements PullRefresh.Refreshable {

    /** 外观行点击后请求宿主弹出玻璃主题菜单 */
    public interface ThemeRequest {
        void onThemeRequest();
    }

    private ThemeRequest themeRequest;

    public void setThemeRequest(ThemeRequest r) {
        themeRequest = r;
    }


    public MinePage(Context c) {
        super(c);
        LinearLayout root = Ui.col(c);
        addView(root, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        root.addView(Chrome.titleBar(c, "我的", "本地 · 不上传"));

        accountRow = buildAccount(c);
        LinearLayout.LayoutParams alp = Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        alp.leftMargin = Ui.dp(c, 16);
        alp.rightMargin = Ui.dp(c, 16);
        alp.bottomMargin = Ui.dp(c, 8);
        root.addView(accountRow, alp);
        paintAccount();

        LinearLayout settingsRow = buildSettingsRow(c);
        LinearLayout.LayoutParams slp = Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.leftMargin = Ui.dp(c, 16);
        slp.rightMargin = Ui.dp(c, 16);
        slp.bottomMargin = Ui.dp(c, 8);
        root.addView(settingsRow, slp);

        LinearLayout aboutRow = buildAboutRow(c);
        LinearLayout.LayoutParams olp = Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        olp.leftMargin = Ui.dp(c, 16);
        olp.rightMargin = Ui.dp(c, 16);
        olp.bottomMargin = Ui.dp(c, 8);
        root.addView(aboutRow, olp);

        // 收藏 / 历史 / 喜欢 / 离线缓存 全部就地切换，不再各开一个页面
        library = new MineLibrary(c);
        root.addView(library, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        paintAccount();
    }

    private MineLibrary library;

    private LinearLayout accountRow;
    private TextView accountAvatar;
    private TextView accountName;
    private TextView accountSub;
    private TextView accountAction;

    /** 账号卡：未登录给「登录 / 注册」，登录后显示昵称与退出 */
    private LinearLayout buildAccount(Context c) {
        LinearLayout wrap = Ui.row(c);
        // 与外观/关于两卡统一内边距与高度
        wrap.setMinimumHeight(Ui.dp(c, 62));
        wrap.setPadding(Ui.dp(c, 12), Ui.dp(c, 10), Ui.dp(c, 14), Ui.dp(c, 10));
        wrap.setBackground(new GlassDrawable(Ui.dp(c, 18)));
        wrap.setGravity(Gravity.CENTER_VERTICAL);

        accountAvatar = Ui.bold(c, "·", 15f, 0xFF1A1206);
        accountAvatar.setGravity(Gravity.CENTER);
        accountAvatar.setBackground(Ui.round(Ui.C_AMBER, Ui.dp(c, 21)));
        wrap.addView(accountAvatar, Ui.lp(Ui.dp(c, 40), Ui.dp(c, 40)));

        LinearLayout texts = Ui.col(c);
        LinearLayout.LayoutParams tp = Ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT);
        tp.weight = 1f;
        tp.leftMargin = Ui.dp(c, 12);
        wrap.addView(texts, tp);

        accountName = Ui.bold(c, "未登录", 13.5f, Ui.C_TEXT);
        texts.addView(accountName);

        accountSub = Ui.text(c, "登录后可发表评论", 10.5f, Ui.C_DIM);
        LinearLayout.LayoutParams sp = Ui.lp(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        sp.topMargin = Ui.dp(c, 4);
        texts.addView(accountSub, sp);

        accountAction = Ui.text(c, "登录 / 注册", 11.5f, Ui.C_AMBER);
        accountAction.setGravity(Gravity.CENTER);
        accountAction.setPadding(Ui.dp(c, 12), Ui.dp(c, 7), Ui.dp(c, 12), Ui.dp(c, 7));
        accountAction.setBackground(Ui.stroke(0x26F5B942, 0x4DF5B942, Ui.dp(c, 14), 1));
        accountAction.setClickable(true);
        accountAction.setOnClickListener(v -> {
            if (Auth.loggedIn()) {
                Auth.logout();
                paintAccount();
                refresh();
            } else {
                AuthDialog.show(getContext(), false, () -> {
                    paintAccount();
                    refresh();
                });
            }
        });
        wrap.addView(accountAction, Ui.lp(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        // 点头像 / 昵称区域进账户设置（登录后才会改资料；未登录先弹登录）
        wrap.setClickable(true);
        wrap.setOnClickListener(v -> {
            if (Auth.loggedIn()) {
                AccountActivity.open(c);
            } else {
                AuthDialog.show(c, false, () -> {
                    paintAccount();
                    refresh();
                });
            }
        });
        return wrap;
    }

    /** 设置入口：外观 / 软件清理 / 检测更新 都收进设置页了 */
    private LinearLayout buildSettingsRow(Context c) {
        LinearLayout wrap = Ui.row(c);
        wrap.setMinimumHeight(Ui.dp(c, 62));
        wrap.setPadding(Ui.dp(c, 12), Ui.dp(c, 10), Ui.dp(c, 14), Ui.dp(c, 10));
        wrap.setBackground(new GlassDrawable(Ui.dp(c, 18)));
        wrap.setGravity(Gravity.CENTER_VERTICAL);
        wrap.setClickable(true);
        wrap.setOnClickListener(v -> SettingsActivity.open(c));

        TextView glyph = Ui.bold(c, "⚙", 16f, Ui.C_AMBER);
        glyph.setGravity(Gravity.CENTER);
        glyph.setBackground(Ui.stroke(0x26F5B942, 0x59F5B942, Ui.dp(c, 20), 1));
        wrap.addView(glyph, Ui.lp(Ui.dp(c, 40), Ui.dp(c, 40)));

        LinearLayout texts = Ui.col(c);
        LinearLayout.LayoutParams tp = Ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT);
        tp.weight = 1f;
        tp.leftMargin = Ui.dp(c, 12);
        wrap.addView(texts, tp);
        texts.addView(Ui.bold(c, "设置", 13.5f, Ui.C_TEXT));
        TextView sub = Ui.text(c, "外观 · 软件清理 · 检测更新", 10.5f, Ui.C_DIM);
        LinearLayout.LayoutParams sp = Ui.lp(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        sp.topMargin = Ui.dp(c, 3);
        texts.addView(sub, sp);

        TextView arrow = Ui.text(c, "›", 15f, Ui.C_DIM);
        LinearLayout.LayoutParams ap = Ui.lp(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        ap.leftMargin = Ui.dp(c, 8);
        wrap.addView(arrow, ap);
        return wrap;
    }

    /** 关于入口行：版本 · GitHub · Telegram 群组 · 开源许可 */
    private LinearLayout buildAboutRow(Context c) {
        LinearLayout wrap = Ui.row(c);
        wrap.setMinimumHeight(Ui.dp(c, 62));
        wrap.setPadding(Ui.dp(c, 12), Ui.dp(c, 10), Ui.dp(c, 14), Ui.dp(c, 10));
        wrap.setBackground(new GlassDrawable(Ui.dp(c, 18)));
        wrap.setGravity(Gravity.CENTER_VERTICAL);
        wrap.setClickable(true);
        wrap.setOnClickListener(v -> AboutActivity.open(c));

        TextView glyph = Ui.bold(c, "ⓘ", 15f, Ui.C_AMBER);
        glyph.setGravity(Gravity.CENTER);
        glyph.setBackground(Ui.stroke(0x26F5B942, 0x59F5B942, Ui.dp(c, 20), 1));
        wrap.addView(glyph, Ui.lp(Ui.dp(c, 40), Ui.dp(c, 40)));

        LinearLayout texts = Ui.col(c);
        LinearLayout.LayoutParams tp = Ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT);
        tp.weight = 1f;
        tp.leftMargin = Ui.dp(c, 12);
        wrap.addView(texts, tp);
        texts.addView(Ui.bold(c, "关于", 13.5f, Ui.C_TEXT));
        TextView sub = Ui.text(c, "版本 · GitHub · Telegram 群组 · 开源许可", 10.5f, Ui.C_DIM);
        LinearLayout.LayoutParams sp = Ui.lp(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        sp.topMargin = Ui.dp(c, 3);
        texts.addView(sub, sp);

        TextView arrow = Ui.text(c, "›", 15f, Ui.C_DIM);
        LinearLayout.LayoutParams ap = Ui.lp(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        ap.leftMargin = Ui.dp(c, 8);
        wrap.addView(arrow, ap);
        return wrap;
    }

    private void paintAccount() {
        if (accountName == null) return;
        if (Auth.loggedIn()) {
            String name = Auth.name().isEmpty() ? "已登录" : Auth.name();
            accountName.setText(name);
            accountSub.setText("账号已连接 · 可发表评论");
            accountAvatar.setText(name.substring(0, 1));
            accountAction.setText("退出登录");
            accountAction.setTextColor(Ui.C_SUB);
            accountAction.setBackground(Ui.subtle(getContext(), Ui.dp(getContext(), 14)));
        } else {
            accountName.setText("未登录");
            accountSub.setText("登录后可发表评论");
            accountAvatar.setText("·");
            accountAction.setText("登录 / 注册");
            accountAction.setTextColor(Ui.C_AMBER);
            accountAction.setBackground(Ui.stroke(0x26F5B942, 0x4DF5B942,
                    Ui.dp(getContext(), 14), 1));
        }
    }

    /** 当前挂载的 Activity（弹确认框需要 Activity 上下文） */
    private android.app.Activity hostActivity() {
        Context c = getContext();
        while (c instanceof android.content.ContextWrapper) {
            if (c instanceof android.app.Activity) return (android.app.Activity) c;
            c = ((android.content.ContextWrapper) c).getBaseContext();
        }
        return null;
    }

    private TextView segment(String text, boolean active) {        TextView t = Ui.text(getContext(), text, 12.5f, active ? 0xFF1A1206 : Ui.C_TEXT);
        t.setGravity(Gravity.CENTER);
        t.setPadding(Ui.dp(getContext(), 16), Ui.dp(getContext(), 7),
                Ui.dp(getContext(), 16), Ui.dp(getContext(), 7));
        t.setBackground(active
                ? Ui.round(Ui.C_AMBER, Ui.dp(getContext(), 16))
                : Ui.subtle(getContext(), Ui.dp(getContext(), 16)));
        return t;
    }

    /** 下拉刷新：重读当前分段的列表（收藏/历史/喜欢/缓存可能在其它页面变了） */
    @Override
    public void onPullRefresh(PullRefresh.Done done) {
        if (library != null) library.refresh();
        postDelayed(done::done, 350);
    }

    public void refresh() {
        // 先把列表画出来，再在后台确认离线缓存的文件还在不在（stat / MediaStore 查询不占主线程）
        if (library != null) library.refresh();
        Store.get(getContext()).verifyCacheFilesAsync(() -> {
            if (library != null) library.refresh();
        });
    }
}
