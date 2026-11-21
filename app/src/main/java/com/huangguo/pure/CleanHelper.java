package com.huangguo.pure;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** 软件清理：缓存 / 观看记录 / 下载视频（点到「我的 → 软件清理」下拉进入） */
public final class CleanHelper {

    private CleanHelper() {
    }

    /** 单个下载项 */
    private static class DlItem {
        final Uri uri;
        final String name;
        final long size;
        /** 所属剧名（MediaStore 的 RELATIVE_PATH 最后一段），一级菜单按它分组 */
        final String series;
        boolean checked;

        DlItem(Uri uri, String name, long size, String series) {
            this.uri = uri;
            this.name = name;
            this.size = size;
            this.series = series;
        }
    }

    /** 清理缓存：弹确认框（提醒会清观看记录、不影响收藏），确认后清 cacheDir + 观看历史 */
    public static void showClearCache(final Context c) {
        LinearLayout box = dialogContent(c);
        TextView title = Ui.bold(c, "清理缓存", 17f, Ui.C_TEXT);
        box.addView(title);

        TextView msg = Ui.text(c,
                "将清理应用观看视频 / 图文产生的缓存，并清空观看历史。\n\n收藏内容不受影响。",
                13f, Ui.C_SUB);
        LinearLayout.LayoutParams mp = Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        mp.topMargin = Ui.dp(c, 14);
        msg.setLineSpacing(Ui.dp(c, 4), 1f);
        box.addView(msg, mp);

        final FrameLayout[] overlayRef = new FrameLayout[1];
        LinearLayout btns = dialogButtons(c, overlayRef,
                new String[]{"取消", "确认清理"},
                new Runnable[]{null, () -> {
                    long freed = clearCacheDir(c);
                    Store.get(c).clearHistory();
                    Toast.makeText(c, "已清理缓存 · 释放 " + Ui.fmtSize(freed), Toast.LENGTH_SHORT).show();
                }});
        box.addView(btns);
        showGlassDialog(c, box, overlayRef);
    }

    /** 递归删除 cacheDir 内容，返回释放字节数 */
    private static long clearCacheDir(Context c) {
        long[] freed = {0L};
        deleteDir(c.getCacheDir(), freed);
        deleteDir(c.getCodeCacheDir(), freed);
        return freed[0];
    }

    private static void deleteDir(File dir, long[] freed) {
        if (dir == null || !dir.exists()) return;
        File[] children = dir.listFiles();
        if (children == null) return;
        for (File f : children) {
            if (f.isDirectory()) {
                deleteDir(f, freed);
            } else {
                long len = f.length();
                if (f.delete()) freed[0] += len;
            }
        }
    }

    /** 清理下载文件：列出 Download/Huangguo 下的视频，勾选后删除 */
    /** 从相对路径 / 绝对路径里取「剧名」这一级目录 */
    private static String seriesOf(String path) {
        if (path == null || path.trim().isEmpty()) return "未分类";
        String p = path.trim();
        while (p.endsWith("/")) p = p.substring(0, p.length() - 1);
        int idx = p.lastIndexOf('/');
        String last = idx >= 0 ? p.substring(idx + 1) : p;
        // RELATIVE_PATH 结尾是目录名；DATA 结尾是文件名，取上一级
        if (last.contains(".")) {
            p = idx >= 0 ? p.substring(0, idx) : p;
            idx = p.lastIndexOf('/');
            last = idx >= 0 ? p.substring(idx + 1) : p;
        }
        return last.isEmpty() ? "未分类" : last;
    }

    /**
     * 删除下载视频：两级菜单。
     * 一级是剧名（带总集数 / 总大小，点一下展开），二级是这一部的每一集。
     */
    public static void showDownloads(final Context c) {
        final List<DlItem> items = queryDownloads(c);

        LinearLayout box = dialogContent(c);
        TextView title = Ui.bold(c, items.isEmpty() ? "暂无下载视频" : "删除下载视频",
                16f, Ui.C_TEXT);
        box.addView(title);

        if (items.isEmpty()) {
            TextView hint = Ui.text(c, "你还没有下载过视频。", 12.5f, Ui.C_DIM);
            LinearLayout.LayoutParams hp = Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            hp.topMargin = Ui.dp(c, 12);
            box.addView(hint, hp);
            final FrameLayout[] ref0 = new FrameLayout[1];
            box.addView(dialogButtons(c, ref0, new String[]{"知道了"}, new Runnable[]{null}));
            showGlassDialog(c, box, ref0);
            return;
        }

        // 按剧名分组，保持查询顺序
        final List<String> groups = new ArrayList<>();
        final List<List<DlItem>> grouped = new ArrayList<>();
        for (DlItem it : items) {
            int g = groups.indexOf(it.series);
            if (g < 0) {
                groups.add(it.series);
                List<DlItem> list = new ArrayList<>();
                list.add(it);
                grouped.add(list);
            } else {
                grouped.get(g).add(it);
            }
        }

        final List<CheckBox> checks = new ArrayList<>();
        ScrollView sv = new ScrollView(c);
        LinearLayout list = Ui.col(c);
        LinearLayout.LayoutParams lp = Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(c, 10);

        for (int g = 0; g < groups.size(); g++) {
            final List<DlItem> eps = grouped.get(g);
            long total = 0;
            for (DlItem it : eps) total += it.size;

            // 一级：剧名 + 集数 + 体积 + 箭头
            LinearLayout head = Ui.row(c);
            head.setPadding(Ui.dp(c, 10), Ui.dp(c, 11), Ui.dp(c, 10), Ui.dp(c, 11));
            head.setBackground(Ui.subtle(c, Ui.dp(c, 12)));
            head.setClickable(true);

            LinearLayout texts = Ui.col(c);
            LinearLayout.LayoutParams tp = Ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT);
            tp.weight = 1f;
            head.addView(texts, tp);

            TextView name = Ui.bold(c, groups.get(g), 13f, Ui.C_TEXT);
            name.setMaxLines(1);
            name.setEllipsize(android.text.TextUtils.TruncateAt.END);
            texts.addView(name);

            TextView sub = Ui.text(c, eps.size() + " 集 · " + Ui.fmtSize(total), 10.5f, Ui.C_DIM);
            LinearLayout.LayoutParams sp = Ui.lp(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            sp.topMargin = Ui.dp(c, 3);
            texts.addView(sub, sp);

            final TextView arrow = Ui.text(c, "›", 15f, Ui.C_DIM);
            arrow.setPadding(Ui.dp(c, 8), 0, 0, 0);
            head.addView(arrow);
            list.addView(head, lp);

            // 二级：每集（默认收起）
            final LinearLayout body = Ui.col(c);
            body.setVisibility(View.GONE);
            for (final DlItem it : eps) {
                CheckBox cb = new CheckBox(c);
                cb.setText(it.name + "  (" + Ui.fmtSize(it.size) + ")");
                cb.setTextColor(Ui.C_TEXT);
                cb.setTextSize(12f);
                cb.setPadding(Ui.dp(c, 20), Ui.dp(c, 7), Ui.dp(c, 4), Ui.dp(c, 7));
                cb.setChecked(false);
                cb.setOnCheckedChangeListener((b, checked) -> it.checked = checked);
                checks.add(cb);
                body.addView(cb, Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT));
            }
            list.addView(body, lp);

            head.setOnClickListener(v -> {
                boolean open = body.getVisibility() != View.VISIBLE;
                body.setVisibility(open ? View.VISIBLE : View.GONE);
                arrow.setText(open ? "⌄" : "›");
            });
        }

        sv.addView(list, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        int listH = Ui.dp(c, Math.min(360, (items.size() + groups.size()) * 44 + 16));
        box.addView(sv, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, listH));

        final FrameLayout[] overlayRef = new FrameLayout[1];
        LinearLayout btns = dialogButtons(c, overlayRef,
                new String[]{"取消", "删除所选"},
                new Runnable[]{null, () -> {
                    int n = 0;
                    for (DlItem it : items) {
                        if (it.checked && deleteUri(c, it.uri)) n++;
                    }
                    Toast.makeText(c, "已删除 " + n + " 个下载文件", Toast.LENGTH_SHORT).show();
                }});
        box.addView(btns);
        showGlassDialog(c, box, overlayRef);
    }

    // -------------------------------------------------- 液态玻璃弹层

    /** 弹层内容容器：去 padding 由卡片统一 */
    private static LinearLayout dialogContent(Context c) {
        LinearLayout box = Ui.col(c);
        box.setPadding(Ui.dp(c, 4), Ui.dp(c, 4), Ui.dp(c, 4), Ui.dp(c, 2));
        return box;
    }

    /** 底部按钮行：玻璃 chip，主按钮用强调色文字 */
    private static LinearLayout dialogButtons(Context c, final FrameLayout[] overlayRef,
                                              String[] labels, Runnable[] actions) {
        LinearLayout row = Ui.row(c);
        LinearLayout.LayoutParams rp = Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        rp.topMargin = Ui.dp(c, 18);
        row.setLayoutParams(rp);
        View spacer = new View(c);
        LinearLayout.LayoutParams sp = Ui.lp(0, 1);
        sp.weight = 1f;
        row.addView(spacer, sp);
        for (int i = 0; i < labels.length; i++) {
            final String label = labels[i];
            final Runnable action = actions[i];
            boolean primary = i == labels.length - 1;
            TextView btn = Ui.bold(c, label, 12.5f,
                    primary ? Ui.accentBright() : Ui.C_SUB);
            btn.setGravity(Gravity.CENTER);
            btn.setPadding(Ui.dp(c, 18), Ui.dp(c, 8), Ui.dp(c, 18), Ui.dp(c, 8));
            btn.setBackground(Ui.stroke(Ui.chipFill(), Ui.chipStroke(), Ui.dp(c, 16), 1));
            btn.setClickable(true);
            btn.setOnClickListener(v -> {
                if (overlayRef[0] != null && overlayRef[0].getParent() instanceof ViewGroup) {
                    ((ViewGroup) overlayRef[0].getParent()).removeView(overlayRef[0]);
                }
                if (action != null) action.run();
            });
            LinearLayout.LayoutParams bp = Ui.lp(ViewGroup.LayoutParams.WRAP_CONTENT,
                    Ui.dp(c, 34));
            if (i > 0) bp.leftMargin = Ui.dp(c, 10);
            row.addView(btn, bp);
        }
        return row;
    }

    /** 居中液态玻璃弹层：暗化 scrim + liquidPanel 卡片，点外部不关闭（显式按钮关闭） */
    private static void showGlassDialog(final Context c, View content,
                                        final FrameLayout[] overlayRef) {
        if (!(c instanceof Activity)) return;
        final Activity act = (Activity) c;
        FrameLayout overlay = new FrameLayout(c);
        overlay.setBackgroundColor(Ui.darkGlass ? 0x8C000000 : 0x66DCE4EF);
        overlay.setClickable(true);
        overlay.setOnClickListener(v -> { /* 点外部不关闭，与原 AlertDialog 一致 */ });

        FrameLayout cardWrap = new FrameLayout(c);
        cardWrap.setBackground(Ui.liquidPanel(c, Ui.dp(c, 20)));
        int pad = Ui.dp(c, 22);
        cardWrap.setPadding(pad, pad, pad, pad);
        cardWrap.addView(content, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(
                Ui.dp(c, 320), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        overlay.addView(cardWrap, clp);
        overlayRef[0] = overlay;
        act.getWindow().getDecorView().post(() -> {
            try {
                act.getWindow().addContentView(overlay,
                        new ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT));
            } catch (Throwable ignore) {
            }
        });
    }

    /** 查询 MediaStore.Downloads 中本应用下载目录（Download/Huangguo%）的视频 */
    private static List<DlItem> queryDownloads(Context c) {
        List<DlItem> out = new ArrayList<>();
        ContentResolver cr = c.getContentResolver();
        Uri uri;
        String[] proj;
        String sel;
        String[] args;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            uri = MediaStore.Downloads.EXTERNAL_CONTENT_URI;
            proj = new String[]{MediaStore.Downloads._ID,
                    MediaStore.Downloads.DISPLAY_NAME,
                    MediaStore.Downloads.SIZE,
                    MediaStore.Downloads.RELATIVE_PATH};
            sel = MediaStore.Downloads.RELATIVE_PATH + " LIKE ?";
            args = new String[]{"Download/Huangguo%"};
        } else {
            uri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
            proj = new String[]{MediaStore.Video.Media._ID,
                    MediaStore.Video.Media.DISPLAY_NAME,
                    MediaStore.Video.Media.SIZE,
                    MediaStore.Video.Media.DATA};
            sel = MediaStore.Video.Media.DATA + " LIKE ?";
            args = new String[]{"%Huangguo%"};
        }
        Cursor cur = null;
        try {
            cur = cr.query(uri, proj, sel, args, MediaStore.MediaColumns.DATE_ADDED + " DESC");
            if (cur == null) return out;
            int iId = cur.getColumnIndex(proj[0]);
            int iName = cur.getColumnIndex(proj[1]);
            int iSize = cur.getColumnIndex(proj[2]);
            int iPath = cur.getColumnIndex(proj[3]);
            while (cur.moveToNext()) {
                long id = iId >= 0 ? cur.getLong(iId) : -1;
                String name = iName >= 0 ? cur.getString(iName) : "下载视频";
                long size = iSize >= 0 ? cur.getLong(iSize) : 0;
                if (id < 0) continue;
                String path = iPath >= 0 ? cur.getString(iPath) : "";
                out.add(new DlItem(
                        Uri.withAppendedPath(uri, String.valueOf(id)),
                        name == null ? "下载视频" : name, size, seriesOf(path)));
            }
        } catch (Throwable ignored) {
        } finally {
            if (cur != null) cur.close();
        }
        return out;
    }

    private static boolean deleteUri(Context c, Uri uri) {
        try {
            return c.getContentResolver().delete(uri, null, null) > 0;
        } catch (Throwable t) {
            return false;
        }
    }
}
