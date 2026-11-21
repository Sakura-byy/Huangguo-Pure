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

    /**
     * 发布页源，按优先级排：
     *   1) GitHub Pages 主站
     *   2) GitLab 镜像（主站被墙时的第一备份）
     *   3) Cloudflare Pages 两个备用域
     *   4) raw.githubusercontent（最后兜底，JSON/JS 原文直出）
     * 任一源拿到 ≥3 条内容站就停，避免每次都把四个源全打一遍。
     */
    private static final String[] PUBLISH_PAGES = {
            "https://huangguotv.github.io/publish.js",
            "https://gitlab.com/huangguotv/publish/-/raw/main/publish.js",
            "https://gitlab.com/huangguotv/huangguotv/-/raw/main/publish.js",
            "https://huang-guo.pages.dev/publish.js",
            "https://huangguo88.pages.dev/publish.js",
            "https://raw.githubusercontent.com/huangguotv/huangguotv.github.io/main/publish.js"
    };

    /** 测速超时：发布页给的线路大多在海外，4s 足够判断死活 */
    private static final okhttp3.OkHttpClient PROBE =
            new okhttp3.OkHttpClient.Builder()
                    .connectTimeout(4, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(4, java.util.concurrent.TimeUnit.SECONDS)
                    .followRedirects(true)
                    .build();

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

    /** 一条线路：地址 + 延迟（-1 表示不可达） */
    public static class Line {
        public final String url;
        public final int ms;

        public Line(String url, int ms) {
            this.url = url;
            this.ms = ms;
        }

        public String host() {
            return url.replaceFirst("^https?://", "");
        }
    }

    /** 抓发布页 + 并发测速，按延迟升序；不可达的排最后 */
    public static List<Line> fetchRanked() {
        List<String> hosts = fetch();
        List<String> all = new ArrayList<>(hosts);
        for (String b : Api.builtinHosts()) if (!all.contains(b)) all.add(b);
        return rank(probe(all));
    }

    /** 并发测速（最多 6 路） */
    public static List<Line> probe(List<String> hosts) {
        List<Line> out = new java.util.ArrayList<>();
        if (hosts == null || hosts.isEmpty()) return out;
        int n = Math.min(6, hosts.size());
        java.util.concurrent.ExecutorService pool =
                java.util.concurrent.Executors.newFixedThreadPool(n);
        List<java.util.concurrent.Future<Line>> futures = new ArrayList<>();
        for (final String h : hosts) {
            futures.add(pool.submit(() -> new Line(h, ping(h))));
        }
        pool.shutdown();
        for (java.util.concurrent.Future<Line> f : futures) {
            try {
                out.add(f.get(6, java.util.concurrent.TimeUnit.SECONDS));
            } catch (Exception ignored) {
            }
        }
        return out;
    }

    /** 按延迟排序：可达的在前（小的优先），不可达的按原序垫底 */
    public static List<Line> rank(List<Line> lines) {
        List<Line> out = new ArrayList<>();
        if (lines == null) return out;
        List<Line> ok = new ArrayList<>();
        List<Line> bad = new ArrayList<>();
        for (Line l : lines) {
            if (l == null) continue;
            if (l.ms >= 0) ok.add(l);
            else bad.add(l);
        }
        ok.sort((a, b) -> Integer.compare(a.ms, b.ms));
        out.addAll(ok);
        out.addAll(bad);
        return out;
    }

    /** 单条线路往返耗时（ms），失败返回 -1；请求根路径拿首字节时间 */
    public static int ping(String base) {
        if (base == null || base.isEmpty()) return -1;
        String url = base.endsWith("/") ? base : base + "/";
        long t0 = System.currentTimeMillis();
        okhttp3.Response resp = null;
        try {
            Request req = new Request.Builder()
                    .url(url)
                    .header("User-Agent", Api.UA)
                    .header("Accept", "text/html,*/*")
                    .build();
            resp = PROBE.newCall(req).execute();
            if (!resp.isSuccessful()) return -1;
            return (int) Math.max(1L, System.currentTimeMillis() - t0);
        } catch (Exception e) {
            return -1;
        } finally {
            if (resp != null) resp.close();
        }
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
