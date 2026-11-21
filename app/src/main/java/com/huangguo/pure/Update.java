package com.huangguo.pure;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Toast;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.Request;
import okhttp3.Response;

/**
 * 版本检测 / 更新日志，全部走 GitHub 公开接口（无需 token）。
 *
 * - 最新版本：GET /repos/{repo}/releases/latest
 * - 更新日志：GET /repos/{repo}/releases?per_page=10（没有 release 时退回 CHANGELOG.md）
 *
 * 自动检测：设置里开关打开时，每天第一次启动跑一次；检查过就写当天的日期，
 * 一天内不再重复请求，避免每次冷启动都打 GitHub。
 */
public final class Update {

    /** 仓库（与关于页 GITHUB_URL 保持一致） */
    public static final String REPO = "Zihium/Huangguo-Pure";

    private static final String API_LATEST =
            "https://api.github.com/repos/" + REPO + "/releases/latest";
    private static final String API_LIST =
            "https://api.github.com/repos/" + REPO + "/releases?per_page=10";
    private static final String RAW_CHANGELOG =
            "https://raw.githubusercontent.com/" + REPO + "/main/CHANGELOG.md";

    private static final String PREF = "hg_client";
    private static final String K_AUTO = "auto_update";
    private static final String K_DAY = "update_day";

    private static final ExecutorService POOL = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private Update() {
    }

    /** 一次发布 */
    public static class Release {
        public String version = "";
        public String name = "";
        public String notes = "";
        public String pageUrl = "";
        public String apkUrl = "";
        public String date = "";
    }

    public interface Cb {
        /** r 为 null 表示失败或无发布 */
        void onResult(Release r, String err);
    }

    // ------------------------------------------------------------------ 设置

    public static boolean autoEnabled(Context c) {
        return prefs(c).getBoolean(K_AUTO, true);
    }

    public static void setAuto(Context c, boolean on) {
        prefs(c).edit().putBoolean(K_AUTO, on).apply();
    }

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    private static String today() {
        return new SimpleDateFormat("yyyyMMdd", Locale.CHINA).format(new Date());
    }

    /** 本机版本名，如 1.1.0 */
    public static String versionName(Context c) {
        try {
            return c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "0";
        }
    }

    /** a 比 b 新返回正数；按点分段数字比较，非数字段忽略 */
    public static int compare(String a, String b) {
        String[] x = norm(a).split("\\.");
        String[] y = norm(b).split("\\.");
        int n = Math.max(x.length, y.length);
        for (int i = 0; i < n; i++) {
            int xi = i < x.length ? num(x[i]) : 0;
            int yi = i < y.length ? num(y[i]) : 0;
            if (xi != yi) return xi - yi;
        }
        return 0;
    }

    private static String norm(String v) {
        if (v == null) return "0";
        String s = v.trim();
        if (s.startsWith("v") || s.startsWith("V")) s = s.substring(1);
        int cut = s.indexOf('-');
        if (cut > 0) s = s.substring(0, cut);
        return s.isEmpty() ? "0" : s;
    }

    private static int num(String s) {
        try {
            return Integer.parseInt(s.replaceAll("[^0-9]", ""));
        } catch (Exception e) {
            return 0;
        }
    }

    // ------------------------------------------------------------------ 检测

    /** 查最新版本；结果回主线程 */
    public static void check(final Context c, final Cb cb) {
        final String app = c.getApplicationContext() == null
                ? "" : c.getClass().getSimpleName();
        POOL.execute(() -> {
            Release r = null;
            String err = null;
            try {
                String json = get(API_LATEST);
                if (json == null) {
                    err = "无法连接 GitHub";
                } else {
                    JSONObject o = new JSONObject(json);
                    r = parseOne(o);
                    if (r.version.isEmpty()) {
                        r = null;
                        err = "发布页没有版本号";
                    }
                }
            } catch (Exception e) {
                err = "解析失败：" + e.getClass().getSimpleName();
            }
            final Release fr = r;
            final String fe = err;
            MAIN.post(() -> cb.onResult(fr, fe));
        });
    }

    /** 每天首次启动的自动检测（开关关闭时直接跳过） */
    public static void checkDaily(final Activity act) {
        if (act == null) return;
        if (!autoEnabled(act)) return;
        String day = prefs(act).getString(K_DAY, "");
        if (today().equals(day)) return;
        final String stamp = today();
        check(act, (r, err) -> {
            // 不论成功失败都记当天，失败时也不会反复重试打接口
            prefs(act).edit().putString(K_DAY, stamp).apply();
            if (r == null) return;
            if (!act.isFinishing() && compare(r.version, versionName(act)) > 0) {
                showUpdate(act, r);
            }
        });
    }

    /** 设置页里手动检测：给明确反馈 */
    public static void checkManual(final Activity act) {
        check(act, (r, err) -> {
            if (r == null) {
                Toast.makeText(act, err == null ? "检测失败" : err, Toast.LENGTH_SHORT).show();
                return;
            }
            if (compare(r.version, versionName(act)) > 0) {
                showUpdate(act, r);
            } else {
                Toast.makeText(act, "已是最新版本 v" + versionName(act),
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    // ------------------------------------------------------------------ 弹窗

    /** 更新提示：液态玻璃卡片 */
    public static void showUpdate(final Activity act, final Release r) {
        LinearLayout col = Ui.col(act);

        TextView title = Ui.bold(act, "发现新版本", 16f, Ui.C_TEXT);
        col.addView(title);

        TextView sub = Ui.text(act, "v" + r.version
                + (r.date.isEmpty() ? "" : " · " + r.date), 11.5f, Ui.C_AMBER);
        LinearLayout.LayoutParams sp = Ui.lp(LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        sp.topMargin = Ui.dp(act, 6);
        col.addView(sub, sp);

        String notes = r.notes == null ? "" : r.notes.trim();
        if (notes.isEmpty()) notes = "本次更新包含问题修复与体验优化。";
        TextView body = Ui.text(act, notes, 11.5f, Ui.C_SUB);
        LinearLayout.LayoutParams bp = Ui.lp(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        bp.topMargin = Ui.dp(act, 12);
        body.setLayoutParams(bp);
        col.addView(GlassPanel.scrollBody(act, body, 240));

        final FrameLayout[] ref = new FrameLayout[1];
        col.addView(GlassPanel.buttons(act, ref,
                new String[]{"稍后", "立即更新"},
                new Runnable[]{null, () -> downloadAndInstall(act, r)}));

        ref[0] = GlassPanel.show(act, col, true);
    }

    /** 更新日志：从 GitHub 仓库实时拉，拉到之前先显示「加载中」 */
    public static void showChangelog(final Activity act) {
        LinearLayout col = Ui.col(act);
        col.addView(Ui.bold(act, "更新日志", 16f, Ui.C_TEXT));

        final TextView body = Ui.text(act, "加载中…", 11.5f, Ui.C_SUB);
        LinearLayout.LayoutParams bp = Ui.lp(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        bp.topMargin = Ui.dp(act, 12);
        body.setLayoutParams(bp);
        col.addView(GlassPanel.scrollBody(act, body, 320));

        final FrameLayout[] ref = new FrameLayout[1];
        col.addView(GlassPanel.buttons(act, ref,
                new String[]{"关闭"}, new Runnable[]{null}));
        ref[0] = GlassPanel.show(act, col, true);

        POOL.execute(() -> {
            String text = loadChangelog();
            MAIN.post(() -> body.setText(text));
        });
    }

    private static String loadChangelog() {
        try {
            String list = get(API_LIST);
            if (list != null) {
                JSONArray arr = new JSONArray(list);
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject o = arr.optJSONObject(i);
                    if (o == null) continue;
                    String tag = o.optString("tag_name", "");
                    String date = o.optString("published_at", "");
                    if (date.length() >= 10) date = date.substring(0, 10);
                    String notes = o.optString("body", "").trim();
                    sb.append(tag).append(date.isEmpty() ? "" : "  ·  " + date).append('\n');
                    sb.append(notes.isEmpty() ? "• 暂无说明" : notes).append("\n\n");
                }
                if (sb.length() > 0) return sb.toString().trim();
            }
        } catch (Exception ignored) {
        }
        // 仓库还没建 release 时退回仓库里的 CHANGELOG.md
        String md = get(RAW_CHANGELOG);
        if (md != null && !md.trim().isEmpty()) return md.trim();
        return "暂时拉不到更新日志\n\n" + "仓库：github.com/" + REPO;
    }

    private static Release parseOne(JSONObject o) {
        Release r = new Release();
        r.version = norm(o.optString("tag_name", o.optString("name", "")));
        r.name = o.optString("name", "");
        r.notes = o.optString("body", "");
        r.pageUrl = o.optString("html_url", "");
        String date = o.optString("published_at", "");
        if (date.length() >= 10) r.date = date.substring(0, 10);
        JSONArray assets = o.optJSONArray("assets");
        if (assets != null) {
            for (int i = 0; i < assets.length(); i++) {
                JSONObject a = assets.optJSONObject(i);
                if (a == null) continue;
                String u = a.optString("browser_download_url", "");
                if (u.toLowerCase(Locale.ROOT).endsWith(".apk")) {
                    r.apkUrl = u;
                    break;
                }
            }
        }
        return r;
    }

    private static String get(String url) {
        try {
            Request req = new Request.Builder()
                    .url(url)
                    .header("User-Agent", Api.UA)
                    .header("Accept", "application/vnd.github+json")
                    .build();
            Response resp = Api.CLIENT.newCall(req).execute();
            try {
                if (!resp.isSuccessful() || resp.body() == null) return null;
                return resp.body().string();
            } finally {
                resp.close();
            }
        } catch (Exception e) {
            return null;
        }
    }

    // ------------------------------------------------------------------ 应用内更新

    /**
     * 直接下 GitHub release 里的 apk 并调起系统安装器。
     *
     * 不跳浏览器：玻璃进度条显示百分比，下完用 FileProvider 交给 PackageInstaller。
     * 没有「安装未知应用」权限时先带用户去开。
     */
    public static void downloadAndInstall(final Activity act, final Release r) {
        if (act == null || r == null) return;
        if (r.apkUrl == null || r.apkUrl.isEmpty()) {
            Toast.makeText(act, "这个版本没有上传安装包", Toast.LENGTH_SHORT).show();
            return;
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O
                && !act.getPackageManager().canRequestPackageInstalls()) {
            try {
                act.startActivity(new Intent(
                        android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:" + act.getPackageName())));
                Toast.makeText(act, "允许安装未知应用后，再点一次立即更新", Toast.LENGTH_LONG).show();
            } catch (Exception e) {
                Toast.makeText(act, "无法打开安装权限设置", Toast.LENGTH_SHORT).show();
            }
            return;
        }

        final LinearLayout col = Ui.col(act);
        col.addView(Ui.bold(act, "正在下载更新", 15f, Ui.C_TEXT));
        final TextView tip = Ui.text(act, "v" + r.version + " · 准备中…", 11.5f, Ui.C_SUB);
        LinearLayout.LayoutParams tp = Ui.lp(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        tp.topMargin = Ui.dp(act, 8);
        col.addView(tip, tp);

        final android.widget.ProgressBar bar = new android.widget.ProgressBar(act, null,
                android.R.attr.progressBarStyleHorizontal);
        bar.setMax(100);
        LinearLayout.LayoutParams bp = Ui.lp(LinearLayout.LayoutParams.MATCH_PARENT,
                Ui.dp(act, 8));
        bp.topMargin = Ui.dp(act, 12);
        col.addView(bar, bp);

        final FrameLayout[] ref = new FrameLayout[1];
        ref[0] = GlassPanel.show(act, col, false);

        new Thread(() -> {
            java.io.File dir = new java.io.File(act.getCacheDir(), "apk");
            if (!dir.exists()) dir.mkdirs();
            final java.io.File out = new java.io.File(dir, "update-" + r.version + ".apk");
            try {
                Request req = new Request.Builder().url(r.apkUrl)
                        .header("User-Agent", Api.UA).build();
                Response resp = Api.CLIENT.newCall(req).execute();
                if (!resp.isSuccessful() || resp.body() == null) {
                    throw new java.io.IOException("HTTP " + resp.code());
                }
                long total = resp.body().contentLength();
                try (java.io.InputStream in = resp.body().byteStream();
                     java.io.FileOutputStream fos = new java.io.FileOutputStream(out)) {
                    byte[] buf = new byte[64 * 1024];
                    long done = 0;
                    int n;
                    while ((n = in.read(buf)) > 0) {
                        fos.write(buf, 0, n);
                        done += n;
                        final int pct = total > 0 ? (int) (done * 100 / total) : -1;
                        final String size = Ui.fmtSize(done)
                                + (total > 0 ? " / " + Ui.fmtSize(total) : "");
                        MAIN.post(() -> {
                            if (pct >= 0) bar.setProgress(pct);
                            tip.setText("v" + r.version + " · " + size
                                    + (pct >= 0 ? "  " + pct + "%" : ""));
                        });
                    }
                } finally {
                    resp.close();
                }
                MAIN.post(() -> {
                    try {
                        if (ref[0] != null) ref[0].setVisibility(View.GONE);
                        installApk(act, out);
                    } catch (Exception e) {
                        Toast.makeText(act, "安装包启动失败：" + e.getMessage(),
                                Toast.LENGTH_LONG).show();
                    }
                });
            } catch (final Exception e) {
                MAIN.post(() -> {
                    if (ref[0] != null) ref[0].setVisibility(View.GONE);
                    Toast.makeText(act, "下载失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }, "apk-dl").start();
    }

    /** FileProvider 授权后交给系统安装器 */
    private static void installApk(Activity act, java.io.File apk) {
        Uri uri = androidx.core.content.FileProvider.getUriForFile(act,
                act.getPackageName() + ".fileprovider", apk);
        Intent i = new Intent(Intent.ACTION_VIEW);
        i.setDataAndType(uri, "application/vnd.android.package-archive");
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
        act.startActivity(i);
    }

    private static void open(Context c, String url) {
        if (url == null || url.isEmpty()) return;
        try {
            c.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception ignored) {
        }
    }
}
