package com.huangguo.pure;

import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

/**
 * 账户设置：头像、昵称、云同步状态。
 *
 * 头像只存本地（站点未提供头像上传路由，见 Api.updateProfile 注释）；
 * 昵称改完先写本地，已登录时再尝试提交到账号，失败保持本地。
 */
public class AccountActivity extends AppCompatActivity {

    private static final int REQ_AVATAR = 21;

    public static void open(Context c) {
        c.startActivity(new Intent(c, AccountActivity.class));
    }

    private FrameLayout avatarWrap;
    private TextView avatarText;
    private ImageView avatarImg;
    private EditText nameInput;
    private EditText signInput;
    private String sign = "";
    private TextView syncState;

    @Override
    protected void onCreate(android.os.Bundle b) {
        super.onCreate(b);
        Ui.applyPalette(Theme.isDark(this));

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Ui.C_BG);
        setContentView(root);
        root.addView(new Aurora(this), new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        int barH = GlassHeader.statusBarHeight(this) + Ui.dp(this, 54);

        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        root.addView(sv, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout content = Ui.col(this);
        content.setPadding(0, barH, 0, Ui.dp(this, 30));
        sv.addView(content);

        buildTopBar(root, barH);
        buildAvatarCard(content);
        buildNameCard(content);
        buildSyncCard(content);
    }

    private void buildTopBar(FrameLayout root, int barH) {
        LinearLayout bar = Ui.row(this);
        bar.setPadding(Ui.dp(this, 14), 0, Ui.dp(this, 14), 0);

        FrameLayout back = new FrameLayout(this);
        back.setBackground(new GlassDrawable(Ui.dp(this, 20)));
        back.setClickable(true);
        ImageView ic = new ImageView(this);
        ic.setImageResource(R.drawable.ic_back);
        ic.setColorFilter(Ui.C_TEXT);
        back.addView(ic, new FrameLayout.LayoutParams(
                Ui.dp(this, 18), Ui.dp(this, 18), Gravity.CENTER));
        back.setOnClickListener(v -> finish());
        bar.addView(back, Ui.lp(Ui.dp(this, 38), Ui.dp(this, 38)));

        TextView title = Ui.bold(this, "账户设置", 17f, Ui.C_TEXT);
        LinearLayout.LayoutParams tp = Ui.lp(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        tp.leftMargin = Ui.dp(this, 12);
        bar.addView(title, tp);

        FrameLayout wrap = new FrameLayout(this);
        wrap.addView(bar, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 54), Gravity.BOTTOM));
        root.addView(wrap, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, barH, Gravity.TOP));
    }

    private void buildAvatarCard(LinearLayout content) {
        LinearLayout card = Ui.row(this);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(Ui.dp(this, 14), Ui.dp(this, 14), Ui.dp(this, 14), Ui.dp(this, 14));
        card.setBackground(new GlassDrawable(Ui.dp(this, 20)));
        LinearLayout.LayoutParams cp = Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        cp.leftMargin = Ui.dp(this, 16);
        cp.rightMargin = Ui.dp(this, 16);
        cp.topMargin = Ui.dp(this, 12);
        content.addView(card, cp);

        avatarWrap = new FrameLayout(this);
        avatarWrap.setBackground(Ui.round(Ui.C_AMBER, Ui.dp(this, 30)));
        avatarWrap.setClickable(true);
        avatarText = Ui.bold(this, "·", 24f, Ui.onAmber());
        avatarText.setGravity(Gravity.CENTER);
        avatarWrap.addView(avatarText, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        avatarImg = new ImageView(this);
        avatarImg.setScaleType(ImageView.ScaleType.CENTER_CROP);
        avatarWrap.addView(avatarImg, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        avatarWrap.setOnClickListener(v -> pickAvatar());
        card.addView(avatarWrap, Ui.lp(Ui.dp(this, 60), Ui.dp(this, 60)));

        LinearLayout texts = Ui.col(this);
        LinearLayout.LayoutParams tp = Ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT);
        tp.weight = 1f;
        tp.leftMargin = Ui.dp(this, 14);
        card.addView(texts, tp);
        texts.addView(Ui.bold(this, "头像", 13.5f, Ui.C_TEXT));
        TextView sub = Ui.text(this, "点左侧头像从相册选一张", 10.5f, Ui.C_DIM);
        LinearLayout.LayoutParams sp = Ui.lp(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        sp.topMargin = Ui.dp(this, 4);
        texts.addView(sub, sp);

        paintAvatar();
    }

    private void buildNameCard(LinearLayout content) {
        LinearLayout card = Ui.col(this);
        card.setPadding(Ui.dp(this, 14), Ui.dp(this, 14), Ui.dp(this, 14), Ui.dp(this, 14));
        card.setBackground(new GlassDrawable(Ui.dp(this, 20)));
        LinearLayout.LayoutParams cp = Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        cp.leftMargin = Ui.dp(this, 16);
        cp.rightMargin = Ui.dp(this, 16);
        cp.topMargin = Ui.dp(this, 12);
        content.addView(card, cp);

        card.addView(Ui.bold(this, "用户昵称", 13.5f, Ui.C_TEXT));

        nameInput = new EditText(this);
        nameInput.setText(Auth.name());
        nameInput.setTextSize(14f);
        nameInput.setSingleLine(true);
        nameInput.setTextColor(Ui.C_TEXT);
        nameInput.setHintTextColor(Ui.C_DIM);
        nameInput.setHint("输入昵称");
        nameInput.setPadding(Ui.dp(this, 12), Ui.dp(this, 9), Ui.dp(this, 12), Ui.dp(this, 9));
        nameInput.setBackground(Ui.stroke(Ui.chipFill(), Ui.chipStroke(), Ui.dp(this, 12), 1));
        LinearLayout.LayoutParams np = Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        np.topMargin = Ui.dp(this, 10);
        card.addView(nameInput, np);

        TextView signLabel = Ui.bold(this, "个性签名", 13.5f, Ui.C_TEXT);
        LinearLayout.LayoutParams slp0 = Ui.lp(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        slp0.topMargin = Ui.dp(this, 14);
        card.addView(signLabel, slp0);

        signInput = new EditText(this);
        signInput.setText(sign);
        signInput.setTextSize(14f);
        signInput.setSingleLine(true);
        signInput.setTextColor(Ui.C_TEXT);
        signInput.setHintTextColor(Ui.C_DIM);
        signInput.setHint("一句话介绍自己");
        signInput.setPadding(Ui.dp(this, 12), Ui.dp(this, 9), Ui.dp(this, 12), Ui.dp(this, 9));
        signInput.setBackground(Ui.stroke(Ui.chipFill(), Ui.chipStroke(), Ui.dp(this, 12), 1));
        LinearLayout.LayoutParams slp = Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.topMargin = Ui.dp(this, 8);
        card.addView(signInput, slp);

        TextView save = Ui.bold(this, "保存昵称", 12.5f, Ui.accentBright());
        save.setGravity(Gravity.CENTER);
        save.setPadding(Ui.dp(this, 16), Ui.dp(this, 8), Ui.dp(this, 16), Ui.dp(this, 8));
        save.setBackground(Ui.stroke(Ui.chipFill(), Ui.chipStroke(), Ui.dp(this, 16), 1));
        save.setClickable(true);
        save.setOnClickListener(v -> saveName());
        LinearLayout.LayoutParams savelp = Ui.lp(ViewGroup.LayoutParams.WRAP_CONTENT,
                Ui.dp(this, 34));
        savelp.topMargin = Ui.dp(this, 12);
        card.addView(save, savelp);
    }

    private void buildSyncCard(LinearLayout content) {
        LinearLayout card = Ui.col(this);
        card.setPadding(Ui.dp(this, 14), Ui.dp(this, 14), Ui.dp(this, 14), Ui.dp(this, 14));
        card.setBackground(new GlassDrawable(Ui.dp(this, 20)));
        LinearLayout.LayoutParams cp = Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        cp.leftMargin = Ui.dp(this, 16);
        cp.rightMargin = Ui.dp(this, 16);
        cp.topMargin = Ui.dp(this, 12);
        content.addView(card, cp);

        card.addView(Ui.bold(this, "收藏与历史同步", 13.5f, Ui.C_TEXT));
        syncState = Ui.text(this, "", 11f, Ui.C_SUB);
        syncState.setLineSpacing(Ui.dp(this, 3), 1f);
        LinearLayout.LayoutParams sp = Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        sp.topMargin = Ui.dp(this, 6);
        card.addView(syncState, sp);

        LinearLayout btns = Ui.row(this);
        LinearLayout.LayoutParams bp = Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        bp.topMargin = Ui.dp(this, 12);
        card.addView(btns, bp);

        TextView sync = Ui.bold(this, "立即同步", 12.5f, Ui.accentBright());
        sync.setGravity(Gravity.CENTER);
        sync.setPadding(Ui.dp(this, 16), Ui.dp(this, 8), Ui.dp(this, 16), Ui.dp(this, 8));
        sync.setBackground(Ui.stroke(Ui.chipFill(), Ui.chipStroke(), Ui.dp(this, 16), 1));
        sync.setClickable(true);
        sync.setOnClickListener(v -> doSync());
        btns.addView(sync, Ui.lp(ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dp(this, 34)));

        TextView logout = Ui.bold(this, Auth.loggedIn() ? "退出登录" : "登录 / 注册",
                12.5f, Ui.C_SUB);
        logout.setGravity(Gravity.CENTER);
        logout.setPadding(Ui.dp(this, 16), Ui.dp(this, 8), Ui.dp(this, 16), Ui.dp(this, 8));
        logout.setBackground(Ui.stroke(Ui.chipFill(), Ui.chipStroke(), Ui.dp(this, 16), 1));
        logout.setClickable(true);
        logout.setOnClickListener(v -> {
            if (Auth.loggedIn()) {
                Auth.logout();
                Toast.makeText(this, "已退出登录，数据保留在本地", Toast.LENGTH_SHORT).show();
                recreate();
            } else {
                AuthDialog.show(this, false, this::recreate);
            }
        });
        LinearLayout.LayoutParams lp = Ui.lp(ViewGroup.LayoutParams.WRAP_CONTENT,
                Ui.dp(this, 34));
        lp.leftMargin = Ui.dp(this, 10);
        btns.addView(logout, lp);

        paintSync();
        loadProfile();
    }

    /** 从账号拉一次资料（昵称 / 签名），登录态下以云端为准 */
    private void loadProfile() {
        if (!Auth.loggedIn()) return;
        Api.me(new Api.Cb<org.json.JSONObject>() {
            @Override
            public void ok(org.json.JSONObject data) {
                Models.Me me = Models.me(data);
                if (!me.nickname.isEmpty()) {
                    Auth.setName(AccountActivity.this, me.nickname);
                    if (nameInput != null) nameInput.setText(me.nickname);
                    paintAvatar();
                }
                sign = me.sign == null ? "" : me.sign;
                if (signInput != null) signInput.setText(sign);
            }

            @Override
            public void err(String msg) {
            }
        });
    }

    private void paintAvatar() {
        String path = Auth.avatarPath();
        File f = path == null || path.isEmpty() ? null : new File(path);
        if (f != null && f.exists()) {
            Bitmap bmp = BitmapFactory.decodeFile(f.getAbsolutePath());
            if (bmp != null) {
                avatarImg.setImageBitmap(bmp);
                avatarImg.setVisibility(View.VISIBLE);
                avatarText.setVisibility(View.GONE);
                return;
            }
        }
        avatarImg.setVisibility(View.GONE);
        avatarText.setVisibility(View.VISIBLE);
        String name = Auth.name();
        avatarText.setText(name.isEmpty() ? "·" : name.substring(0, 1));
    }

    private void paintSync() {
        if (syncState == null) return;
        if (Auth.loggedIn()) {
            long at = Auth.syncedAt();
            String when = at <= 0 ? "还未同步过" : android.text.format.DateFormat.format(
                    "MM-dd HH:mm", at).toString();
            syncState.setText("已登录：" + (Auth.name().isEmpty() ? "账号" : Auth.name())
                    + "\n收藏与历史会同步到账号，最近同步 " + when
                    + "\n未登录时数据只保存在本机。");
        } else {
            syncState.setText("当前未登录：收藏、历史、缓存都只保存在本机。\n"
                    + "登录后会自动把本地收藏与历史同步到账号。");
        }
    }

    private void saveName() {
        String name = nameInput.getText().toString().trim();
        sign = signInput == null ? sign : signInput.getText().toString().trim();
        if (name.isEmpty()) {
            Toast.makeText(this, "昵称不能为空", Toast.LENGTH_SHORT).show();
            return;
        }
        Auth.setName(this, name);
        paintAvatar();
        Sync.markDirty();
        Toast.makeText(this, "昵称已保存", Toast.LENGTH_SHORT).show();
        if (Auth.loggedIn()) {
            // PATCH /api/account/profile?token=（实测 200）：昵称 / 头像 / 签名 / 简介
            Api.updateProfile(name, Auth.avatar(), sign, "", new Api.Cb<org.json.JSONObject>() {
                @Override
                public void ok(org.json.JSONObject user) {
                    String n = user.optString("nickname", "");
                    if (!n.isEmpty()) {
                        Auth.setName(AccountActivity.this, n);
                        paintAvatar();
                    }
                    Toast.makeText(AccountActivity.this, "资料已同步到账号",
                            Toast.LENGTH_SHORT).show();
                }

                @Override
                public void err(String msg) {
                    Toast.makeText(AccountActivity.this, "云端保存失败：" + msg,
                            Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    private void pickAvatar() {
        try {
            Intent i = new Intent(Intent.ACTION_GET_CONTENT);
            i.setType("image/*");
            startActivityForResult(Intent.createChooser(i, "选择头像"), REQ_AVATAR);
        } catch (Exception e) {
            Toast.makeText(this, "没有可用的相册", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_AVATAR || resultCode != RESULT_OK || data == null) return;
        final Uri uri = data.getData();
        if (uri == null) return;
        new Thread(() -> {
            try {
                Bitmap bmp = decodeScaled(uri, 512);
                if (bmp == null) throw new Exception("图片读取失败");
                File out = new File(getFilesDir(), "avatar.jpg");
                try (FileOutputStream fos = new FileOutputStream(out)) {
                    bmp.compress(Bitmap.CompressFormat.JPEG, 90, fos);
                }
                Auth.setAvatarPath(this, out.getAbsolutePath());
                runOnUiThread(() -> {
                    paintAvatar();
                    Toast.makeText(this, "头像已更新", Toast.LENGTH_SHORT).show();
                });
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "头像更新失败", Toast.LENGTH_SHORT).show());
            }
        }, "avatar").start();
    }

    private Bitmap decodeScaled(Uri uri, int max) throws Exception {
        BitmapFactory.Options probe = new BitmapFactory.Options();
        probe.inJustDecodeBounds = true;
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            BitmapFactory.decodeStream(in, null, probe);
        }
        int scale = 1;
        while (probe.outWidth / scale > max || probe.outHeight / scale > max) scale *= 2;
        BitmapFactory.Options opt = new BitmapFactory.Options();
        opt.inSampleSize = scale;
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            return BitmapFactory.decodeStream(in, null, opt);
        }
    }

    private void doSync() {
        if (!Auth.loggedIn()) {
            Toast.makeText(this, "先登录再同步", Toast.LENGTH_SHORT).show();
            AuthDialog.show(this, false, this::recreate);
            return;
        }
        Toast.makeText(this, "正在同步…", Toast.LENGTH_SHORT).show();
        Sync.markDirty();
        Sync.pushIfDirty(this);
        getWindow().getDecorView().postDelayed(this::paintSync, 800);
        getWindow().getDecorView().postDelayed(this::paintSync, 2500);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // 实际明暗与当前调色板不一致就整页重载（手动切主题 / 跟随系统都走这里）；
        // 单纯旋屏时两者相同，什么都不做。播放器不参与，正在播的视频不重建
        if (Theme.isDark(this) != Ui.darkGlass) {
            Ui.retheme(this);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (Ui.darkGlass != Theme.isDark(this)) {
            Ui.retheme(this);
        }
    }
}
