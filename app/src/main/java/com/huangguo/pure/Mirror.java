package com.huangguo.pure;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.Request;
import okhttp3.Response;

/**
 * 地址发布页镜像同步。
 *
 * 站点会持续换域名，唯一稳定的入口是 GitHub Pages 上的地址发布页。
 * 发布页的 publish.js 里滚动维护三组常量：
 *   PERMANENT_DOMAINS（主域 / 中转 / 备用主干）
 *   SPEED_LINES（国内直连线路，官方实时轮换）
 *   PUBLISH_MIRRORS（发布页自身的镜像，pages.dev 等）
 *
 * 这里把 url 字段全部抽出来过滤成「内容站」候选，写进 SharedPreferences，
 * Api 下次启动就按新表选线。发布页本身挂了还有 raw.githubusercontent 兜底。
 */
public final class Mirror {

    /** 发布页（GitHub Pages） */
    private static final String[] PUBLISH_PAGES = {
            "https://huangguotv.github.io/publish.js",
            "https://raw.githubusercontent.com/huangguotv/huangguotv.github.io/main/publish.js",
            "https://huangguo88.pages.dev/publish.js",
            "https://huang-guo.pages.dev/publish.js"
    };

    private static final Pattern URL_FIELD = Pattern.compile("url://s*'([^']+)'");
    private static final Pattern HOST_ONLY = Pattern.compile("^(https?://[A-Za-z0-9._-]+)");

    /** 这些域名是发布页 / 社交渠道，不是内容站，不能进选线表 */
    private static final Set<String> EXCLUDE = new HashSet<>(Arrays.asList(
            "huangguotv.github.io", "raw.githubusercontent.com", "github.com",
            "huangguo88.pages.dev", "huang-guo.pages.dev",
            "t.me", "x.com", "twitter.com", "www.tiktok.com", "tiktok.com",
            "www.youtube.com", "youtube.com", "telegram.org"
    ));

    private Mirror() {
    }

    /** 阻塞抓取并解析，返回去重后的内容站地址（失败返回空表） */
    public static List<String> fetch() {
        List<String> out = new ArrayList<>();
        for (String page : PUBLISH_PAGES) {
            String js = read(page);
            if (js == null || js.length() < 200) continue;
            List<String> got = parse(js);
            for (String h : got) if (!out.contains(h)) out.add(h);
            if (out.size() >= 3) break;
        }
        return out;
    }

    /** 从 publish.js 文本里抽出内容站地址 */
    public static List<String> parse(String js) {
        List<String> out = new ArrayList<>();
        Matcher m = URL_FIELD.matcher(js);
        while (m.find()) {
            String url = m.group(1);
            Matcher h = HOST_ONLY.matcher(url);
            if (!h.find()) continue;
            String host = h.group(1).toLowerCase();
            String bare = host.replaceFirst("^https?://", "");
            if (EXCLUDE.contains(bare)) continue;
            if (!out.contains(host)) out.add(host);
        }
        return out;
    }

    private static String read(String url) {
        try {
            Request req = new Request.Builder()
                    .url(url)
                    .header("User-Agent", Api.UA)
                    .header("Accept", "*/*")
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
}
