package com.huangguo.pure;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.widget.Toast;

/** 分享 = 复制本页在站点上的链接（与网站 SSR 路由一致） */
public final class Share {

    private Share() {
    }

    /** 剧集详情页 */
    public static String videoUrl(long id) {
        return Api.baseUrl() + "/video/" + id + "/";
    }

    /** 博客文章页（站点路由是 /archives/{id}/） */
    public static String postUrl(long id) {
        return Api.baseUrl() + "/archives/" + id + "/";
    }

    /** 专题页 */
    public static String topicUrl(String slug) {
        return Api.baseUrl() + "/topics/" + slug + "/";
    }

    /** 复制链接并提示 */
    public static void copy(Context c, String url) {
        if (url == null || url.isEmpty()) {
            Toast.makeText(c, "链接还没就绪", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            ClipboardManager cm = (ClipboardManager)
                    c.getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm == null) throw new Exception("剪贴板不可用");
            cm.setPrimaryClip(ClipData.newPlainText("link", url));
            Toast.makeText(c, "链接已复制：" + url, Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(c, "复制失败：" + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
}
