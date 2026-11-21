package com.huangguo.pure;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

/**
 * 账号态：只保存后端下发的 token 与展示资料。
 * 所有需要登录的接口在 Api.build() 里统一带 Authorization: Bearer &lt;token&gt;。
 */
public final class Auth {

    private static SharedPreferences sp;

    private Auth() {
    }

    public static void init(Context c) {
        if (c == null) return;
        sp = c.getApplicationContext().getSharedPreferences("hg_auth", Context.MODE_PRIVATE);
    }

    public static String token() {
        return sp == null ? "" : sp.getString("token", "");
    }

    public static boolean loggedIn() {
        return !token().isEmpty();
    }

    public static String name() {
        return sp == null ? "" : sp.getString("name", "");
    }

    public static String avatar() {
        return sp == null ? "" : sp.getString("avatar", "");
    }

    public static long uid() {
        return sp == null ? 0L : sp.getLong("uid", 0L);
    }

    /** 登录 / 注册成功后调用；data = {token, user{...}} */
    public static void save(JSONObject data) {
        if (sp == null || data == null) return;
        String tk = data.optString("token", "");
        JSONObject user = data.optJSONObject("user");
        if (user == null) user = data;
        SharedPreferences.Editor e = sp.edit();
        if (!tk.isEmpty()) e.putString("token", tk);
        String name = user.optString("username", user.optString("nickname", ""));
        if (!name.isEmpty()) e.putString("name", name);
        String avatar = Models.abs(user.optString("avatar", ""));
        if (!avatar.isEmpty()) e.putString("avatar", avatar);
        long id = user.optLong("id", user.optLong("uid", 0L));
        if (id > 0) e.putLong("uid", id);
        e.apply();
    }

    public static void logout() {
        if (sp == null) return;
        sp.edit().clear().apply();
    }

    /** 上次成功同步到账号的时间（0 = 从未同步） */
    public static long syncedAt() {
        return sp == null ? 0L : sp.getLong("synced_at", 0L);
    }

    public static void setSyncedAt(Context c, long ms) {
        if (sp == null) return;
        sp.edit().putLong("synced_at", ms).apply();
    }

    /** 用户自定义头像：本地文件绝对路径（空 = 用昵称首字） */
    public static String avatarPath() {
        return sp == null ? "" : sp.getString("avatar_path", "");
    }

    public static void setAvatarPath(Context c, String path) {
        if (sp == null) return;
        sp.edit().putString("avatar_path", path == null ? "" : path).apply();
    }

    public static void setName(Context c, String name) {
        if (sp == null || name == null) return;
        sp.edit().putString("name", name).apply();
    }
}
