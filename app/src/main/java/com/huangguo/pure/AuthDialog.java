package com.huangguo.pure;

import android.app.Dialog;
import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/**
 * 登录 / 注册面板（Liquid Glass 卡片）。
 * 成功后把 token 存进 Auth，回调宿主刷新界面。
 */
public final class AuthDialog {

    public interface OnDone {
        void onDone();
    }

    private AuthDialog() {
    }

    public static void show(Context ctx, boolean startWithRegister, final OnDone done) {
        final Dialog d = new Dialog(ctx);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(0x00000000));
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        }

        FrameLayout root = new FrameLayout(ctx);
        root.setBackgroundColor(Ui.C_BG);
        root.addView(new Aurora(ctx), new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout card = Ui.col(ctx);
        card.setBackground(new GlassDrawable(Ui.dp(ctx, 22)));
        card.setPadding(Ui.dp(ctx, 18), Ui.dp(ctx, 16), Ui.dp(ctx, 18), Ui.dp(ctx, 18));

        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER);
        clp.leftMargin = Ui.dp(ctx, 22);
        clp.rightMargin = Ui.dp(ctx, 22);
        root.addView(card, clp);

        LinearLayout head = Ui.row(ctx);
        TextView title = Ui.bold(ctx, "登录", 17f, Ui.C_TEXT);
        head.addView(title);
        View fill = new View(ctx);
        LinearLayout.LayoutParams flp = Ui.lp(0, 1);
        flp.weight = 1f;
        head.addView(fill, flp);
        TextView close = Ui.text(ctx, "关闭", 12f, Ui.C_DIM);
        close.setPadding(Ui.dp(ctx, 8), Ui.dp(ctx, 4), 0, Ui.dp(ctx, 4));
        close.setOnClickListener(v -> d.dismiss());
        head.addView(close);
        card.addView(head);

        TextView note = Ui.text(ctx, "账号用于发表评论，与站内数据互通", 11f, Ui.C_DIM);
        LinearLayout.LayoutParams np = Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        np.topMargin = Ui.dp(ctx, 6);
        card.addView(note, np);

        final EditText user = field(ctx, "用户名", InputType.TYPE_CLASS_TEXT);
        final EditText pass = field(ctx, "密码（至少 6 位）",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        final EditText mail = field(ctx, "邮箱（选填）", InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        mail.setVisibility(startWithRegister ? View.VISIBLE : View.GONE);

        LinearLayout.LayoutParams fp = Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        fp.topMargin = Ui.dp(ctx, 10);
        card.addView(user, fp);

        LinearLayout.LayoutParams fp2 = Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        fp2.topMargin = Ui.dp(ctx, 8);
        card.addView(pass, fp2);
        card.addView(mail, fp2);

        final boolean[] registerMode = {startWithRegister};

        LinearLayout tabs = Ui.row(ctx);
        LinearLayout.LayoutParams tlp = Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.topMargin = Ui.dp(ctx, 14);
        card.addView(tabs, tlp);

        final TextView loginTab = tab(ctx, "登录", !registerMode[0]);
        final TextView regTab = tab(ctx, "注册", registerMode[0]);
        LinearLayout.LayoutParams ltp = Ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT);
        ltp.weight = 1f;
        tabs.addView(loginTab, ltp);
        LinearLayout.LayoutParams rtp = Ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT);
        rtp.weight = 1f;
        rtp.leftMargin = Ui.dp(ctx, 8);
        tabs.addView(regTab, rtp);

        final TextView submit = Ui.bold(ctx, registerMode[0] ? "注册并登录" : "登录", 14f, 0xFF1A1206);
        submit.setGravity(Gravity.CENTER);
        submit.setPadding(0, Ui.dp(ctx, 12), 0, Ui.dp(ctx, 12));
        submit.setBackground(Ui.round(Ui.C_AMBER, Ui.dp(ctx, 16)));
        LinearLayout.LayoutParams slp = Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.topMargin = Ui.dp(ctx, 14);
        card.addView(submit, slp);

        loginTab.setOnClickListener(v -> {
            registerMode[0] = false;
            title.setText("登录");
            mail.setVisibility(View.GONE);
            submit.setText("登录");
            paintTabs(ctx, loginTab, regTab, false);
        });
        regTab.setOnClickListener(v -> {
            registerMode[0] = true;
            title.setText("注册");
            mail.setVisibility(View.VISIBLE);
            submit.setText("注册并登录");
            paintTabs(ctx, loginTab, regTab, true);
        });

        submit.setOnClickListener(v -> {
            final String u = user.getText().toString().trim();
            final String p = pass.getText().toString().trim();
            final String m = mail.getText().toString().trim();
            if (u.isEmpty() || p.isEmpty()) {
                Toast.makeText(ctx, "用户名和密码都要填", Toast.LENGTH_SHORT).show();
                return;
            }
            if (p.length() < 6) {
                Toast.makeText(ctx, "密码至少 6 位", Toast.LENGTH_SHORT).show();
                return;
            }
            submit.setEnabled(false);
            Api.Cb<org.json.JSONObject> cb = new Api.Cb<org.json.JSONObject>() {
                @Override
                public void ok(org.json.JSONObject data) {
                    submit.setEnabled(true);
                    Auth.save(data);
                    Toast.makeText(ctx, "登录成功", Toast.LENGTH_SHORT).show();
                    // 登录后立刻把本地收藏与历史同步到账号（失败静默，本地不动）
                    Sync.markDirty();
                    Sync.pushIfDirty(ctx);
                    d.dismiss();
                    if (done != null) done.onDone();
                }

                @Override
                public void err(String msg) {
                    submit.setEnabled(true);
                    Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show();
                }
            };
            if (registerMode[0]) {
                Api.register(u, p, m, cb);
            } else {
                Api.login(u, p, cb);
            }
        });

        d.setContentView(root);
        d.setCanceledOnTouchOutside(true);
        d.show();
    }

    private static void paintTabs(Context c, TextView login, TextView reg, boolean register) {
        login.setTextColor(register ? Ui.C_TEXT : 0xFF1A1206);
        login.setBackground(register
                ? Ui.stroke(0x14FFFFFF, 0x26FFFFFF, Ui.dp(c, 16), 1)
                : Ui.round(Ui.C_AMBER, Ui.dp(c, 16)));
        reg.setTextColor(register ? 0xFF1A1206 : Ui.C_TEXT);
        reg.setBackground(register
                ? Ui.round(Ui.C_AMBER, Ui.dp(c, 16))
                : Ui.stroke(0x14FFFFFF, 0x26FFFFFF, Ui.dp(c, 16), 1));
    }

    private static TextView tab(Context c, String text, boolean active) {
        TextView t = Ui.text(c, text, 12.5f, active ? 0xFF1A1206 : Ui.C_TEXT);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, Ui.dp(c, 8), 0, Ui.dp(c, 8));
        t.setBackground(active
                ? Ui.round(Ui.C_AMBER, Ui.dp(c, 16))
                : Ui.stroke(0x14FFFFFF, 0x26FFFFFF, Ui.dp(c, 16), 1));
        return t;
    }

    private static EditText field(Context c, String hint, int inputType) {
        EditText e = new EditText(c);
        e.setHint(hint);
        e.setHintTextColor(Ui.C_DIM);
        e.setTextColor(Ui.C_TEXT);
        e.setTextSize(13f);
        e.setSingleLine(true);
        e.setInputType(inputType);
        e.setPadding(Ui.dp(c, 12), Ui.dp(c, 10), Ui.dp(c, 12), Ui.dp(c, 10));
        e.setBackground(Ui.stroke(0x14FFFFFF, 0x26FFFFFF, Ui.dp(c, 14), 1));
        return e;
    }
}
