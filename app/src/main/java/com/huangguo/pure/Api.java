package com.huangguo.pure;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * 接口层：huangguoai 站点矩阵自动选线。
 * 站点被运营商封锁时逐条切换镜像，选定结果写入 SharedPreferences，下次优先直连。
 */
public final class Api {

    public interface Cb<T> {
        void ok(T data);

        void err(String msg);
    }

    /** 排行榜类型：热播榜 / 推荐榜 / 飙升榜 */
    public static final String RANK_HOT = "hot";
    public static final String RANK_RECOMMEND = "recommend";
    public static final String RANK_POTENTIAL = "potential";

    /** 内置线路（发布页抓不到时的兜底；顺序即优先级） */
    private static final String[] BUILTIN = {
            "https://3de302.gdzulcmu.cc",
            "https://0705.fvlelaqt.cc",
            "https://1bb4a2.ipycpknh.cc",
            "https://huangguoai.com",
            "https://huangguoai.ai",
            "https://57c68.zxszoix.cc",
            "https://57c68.zuxszoix.cc",
            "https://fy5x33.ipycpknh.cc",
            "https://hgai24.com"
    };

    /** 运行期线路表 = 发布页同步到的新地址 + 内置兜底 */
    private static final java.util.List<String> HOSTS =
            java.util.Collections.synchronizedList(new java.util.ArrayList<>());

    /** 每次进程只允许触发一次「发布页急救」 */
    private static volatile boolean mirrorTried = false;

    public static final String UA =
            "Mozilla/5.0 (Linux; Android 13; SM-G991B) AppleWebKit/537.36 (KHTML, like Gecko) "
                    + "Chrome/120.0.0.0 Mobile Safari/537.36";

    private static final MediaType JSON_TYPE = MediaType.parse("application/json; charset=utf-8");
    private static final ExecutorService POOL = Executors.newFixedThreadPool(4);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    public static final OkHttpClient CLIENT = new OkHttpClient.Builder()
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(16, TimeUnit.SECONDS)
            .writeTimeout(12, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .followRedirects(true)
            .build();

    private static volatile String host;
    private static SharedPreferences prefs;

    private Api() {
    }

    public static void init(Context c) {
        if (c == null) return;
        Auth.init(c);
        prefs = c.getApplicationContext().getSharedPreferences("hg_client", Context.MODE_PRIVATE);
        String saved = prefs.getString("host", null);
        if (saved != null && !saved.isEmpty()) host = saved;
        applyMirrors(prefs.getString("mirrors", ""));
    }

    /** 内置兜底线路（只读） */
    public static String[] builtinHosts() {
        return BUILTIN.clone();
    }

    /** 已测速的线路表：url|ms 逗号分隔，来自发布页同步或手动测速 */
    public static java.util.List<Mirror.Line> lines() {
        java.util.List<Mirror.Line> out = new java.util.ArrayList<>();
        if (prefs == null) return out;
        String raw = prefs.getString("lines", "");
        if (raw.isEmpty()) return out;
        for (String seg : raw.split(",")) {
            String v = seg.trim();
            int cut = v.lastIndexOf('|');
            if (cut <= 0) continue;
            String url = v.substring(0, cut);
            int ms;
            try {
                ms = Integer.parseInt(v.substring(cut + 1));
            } catch (NumberFormatException e) {
                ms = -1;
            }
            if (url.startsWith("http")) out.add(new Mirror.Line(url, ms));
        }
        return out;
    }

    /** 保存测速结果：线路顺序 + 延迟都落盘，重启后不用重测 */
    public static void saveLines(java.util.List<Mirror.Line> ls) {
        if (prefs == null || ls == null) return;
        StringBuilder order = new StringBuilder();
        StringBuilder detail = new StringBuilder();
        for (Mirror.Line l : ls) {
            if (order.length() > 0) order.append(',');
            order.append(l.url);
            if (detail.length() > 0) detail.append(',');
            detail.append(l.url).append('|').append(l.ms);
        }
        prefs.edit().putString("mirrors", order.toString())
                .putString("lines", detail.toString())
                .apply();
        applyMirrors(order.toString());
    }

    /** 手动切线路：移到表首并立刻生效 */
    public static void useHost(String url) {
        if (url == null || !url.startsWith("http")) return;
        remember(url);
        synchronized (HOSTS) {
            HOSTS.remove(url);
            HOSTS.add(0, url);
            if (prefs != null) {
                StringBuilder sb = new StringBuilder();
                for (String h : HOSTS) {
                    if (sb.length() > 0) sb.append(',');
                    sb.append(h);
                }
                prefs.edit().putString("mirrors", sb.toString()).apply();
            }
        }
    }

    /** 把发布页同步到的地址塞进线路表最前面，再补内置兜底 */
    private static void applyMirrors(String csv) {
        HOSTS.clear();
        if (csv != null && !csv.isEmpty()) {
            for (String s : csv.split(",")) {
                String v = s.trim();
                if (v.startsWith("http") && !HOSTS.contains(v)) HOSTS.add(v);
            }
        }
        for (String s : BUILTIN) if (!HOSTS.contains(s)) HOSTS.add(s);
    }

    /**
     * 去发布页同步最新域名（huangguotv.github.io/publish.js）。
     * 成功后写盘并立即生效，本进程后续请求都会优先走新线路。
     */
    public static void syncMirrors(final Runnable done) {
        // 6 小时内不重复抓发布页
        if (prefs != null) {
            long last = prefs.getLong("mirror_at", 0L);
            if (System.currentTimeMillis() - last < 6L * 3600_000L) {
                if (done != null) MAIN.post(done);
                return;
            }
        }
        POOL.execute(() -> {
            boolean ok = syncMirrorsBlocking();
            if (done != null) MAIN.post(done);
            if (ok) mirrorTried = true;
        });
    }

    private static boolean syncMirrorsBlocking() {
        // 抓发布页 → 并发测速 → 按延迟排序，最快的那条自动排到表首
        java.util.List<Mirror.Line> lines = Mirror.fetchRanked();
        if (lines.isEmpty()) return false;
        saveLines(lines);
        if (prefs != null) {
            prefs.edit().putLong("mirror_at", System.currentTimeMillis()).apply();
        }
        // 没有手动锁定过线路时，直接走最快的
        if (prefs == null || prefs.getString("host", "").isEmpty()) {
            String best = lines.get(0).url;
            if (lines.get(0).ms >= 0) useHost(best);
        }
        return true;
    }

    /** 当前生效线路，供图片等相对路径补全使用 */
    public static String baseUrl() {
        String h = host;
        if (h != null) return h;
        synchronized (HOSTS) {
            return HOSTS.isEmpty() ? "https://huangguoai.com" : HOSTS.get(0);
        }
    }

    private static void remember(String base) {
        if (base.equals(host)) return;
        host = base;
        if (prefs != null) prefs.edit().putString("host", base).apply();
    }

    private static List<String> order() {
        List<String> list = new ArrayList<>();
        String h = host;
        if (h != null) list.add(h);
        synchronized (HOSTS) {
            for (String s : HOSTS) if (!list.contains(s)) list.add(s);
        }
        return list;
    }

    private static Request build(String url, boolean post) {
        return build(url, post ? "{}" : null);
    }

    /** body 为 null 时是 GET；非空时 POST 这段 JSON */
    private static Request build(String url, String body) {
        Request.Builder b = new Request.Builder()
                .url(url)
                .header("User-Agent", UA)
                .header("Accept", "application/json, text/plain, */*")
                .header("Accept-Language", "zh-CN,zh;q=0.9")
                .header("Referer", url);
        if (body != null) b.post(RequestBody.create(body, JSON_TYPE));
        return b.build();
    }

    /**
     * 站点鉴权是 <b>query 参数 ?token=</b>（实测：header Bearer / token / cookie 全部 401，
     * ?token= 返回 200）。所有需要登录的路径都过这一层拼 token。
     */
    static String authPath(String path) {
        String tk = Auth.token();
        if (tk == null || tk.isEmpty()) return path;
        return path + (path.contains("?") ? "&" : "?") + "token=" + tk;
    }

    /** 需要登录的 GET：自动拼 token，未登录直接报错 */
    private static void getAuth(final String path, final Cb<JSONObject> cb) {
        if (!Auth.loggedIn()) {
            fail(cb, "请先登录");
            return;
        }
        get(authPath(path), cb);
    }

    /** 需要登录的 POST */
    private static void postAuth(final String path, final JSONObject body, final Cb<JSONObject> cb) {
        if (!Auth.loggedIn()) {
            fail(cb, "请先登录");
            return;
        }
        postRaw(authPath(path), body, cb);
    }

    /** 需要登录的 DELETE（取消喜欢 / 取消收藏） */
    private static void deleteAuth(final String path, final Cb<JSONObject> cb) {
        if (!Auth.loggedIn()) {
            fail(cb, "请先登录");
            return;
        }
        POOL.execute(() -> {
            try {
                String full = authPath(path);
                JSONObject o = null;
                for (String base : order()) {
                    try (Response resp = CLIENT.newCall(new Request.Builder()
                            .url(base + full)
                            .header("User-Agent", UA)
                            .header("Accept", "application/json, text/plain, */*")
                            .delete().build()).execute()) {
                        if (resp.body() == null) continue;
                        String text = resp.body().string();
                        if (text.isEmpty()) continue;
                        o = new JSONObject(text);
                        remember(base);
                        break;
                    } catch (Exception ignored) {
                    }
                }
                if (o == null) throw new java.io.IOException("所有线路均不可用");
                if (o.optInt("status", 1) == 0) {
                    fail(cb, o.optString("msg", "接口返回异常"));
                    return;
                }
                success(cb, dataOf(o));
            } catch (Exception e) {
                fail(cb, e.getClass().getSimpleName() + ": " + e.getMessage());
            }
        });
    }

    /** 依次尝试各线路，首个返回合法 JSON 的线路胜出 */
    private static JSONObject call(String path, boolean post) throws Exception {
        return call(path, post ? "{}" : null);
    }

    /** body 为 null 走 GET，否则 POST 这段 JSON */
    private static JSONObject call(String path, String body) throws Exception {
        JSONObject r = tryAll(path, body);
        if (r != null) return r;
        // 全部线路失败 → 去地址发布页抓最新域名，换表再打一轮
        if (!mirrorTried) {
            mirrorTried = true;
            if (syncMirrorsBlocking()) {
                r = tryAll(path, body);
                if (r != null) return r;
            }
        }
        throw new IOException("所有线路均不可用");
    }

    /** 顺序打一遍线路表，命中返回 JSON，全挂返回 null */
    private static JSONObject tryAll(String path, String body) {
        for (String base : order()) {
            try (Response resp = CLIENT.newCall(build(base + path, body)).execute()) {
                if (resp.body() == null) continue;
                String text = resp.body().string();
                if (text.isEmpty()) continue;
                JSONObject o = new JSONObject(text);
                if (o.has("data") || o.has("status")) {
                    remember(base);
                    return o;
                }
            } catch (Exception ignored) {
                // 下一条线路
            }
        }
        return null;
    }

    private static void get(final String path, final Cb<JSONObject> cb) {
        POOL.execute(() -> {
            try {
                JSONObject o = call(path, false);
                if (o.optInt("status", 1) == 0) {
                    fail(cb, o.optString("msg", "接口返回异常"));
                    return;
                }
                success(cb, dataOf(o));
            } catch (Exception e) {
                fail(cb, e.getClass().getSimpleName() + ": " + e.getMessage());
            }
        });
    }

    private static void post(final String path, final Cb<JSONObject> cb) {
        POOL.execute(() -> {
            try {
                JSONObject o = call(path, true);
                if (o.optInt("status", 1) == 0) {
                    fail(cb, o.optString("msg", "接口返回异常"));
                    return;
                }
                success(cb, dataOf(o));
            } catch (Exception e) {
                fail(cb, e.getClass().getSimpleName() + ": " + e.getMessage());
            }
        });
    }

    private static JSONObject dataOf(JSONObject o) {
        Object data = o.opt("data");
        return data instanceof JSONObject ? (JSONObject) data : new JSONObject();
    }

    private static <T> void success(final Cb<T> cb, final T data) {
        MAIN.post(() -> cb.ok(data));
    }

    private static <T> void fail(final Cb<T> cb, final String msg) {
        MAIN.post(() -> cb.err(msg));
    }

    // ------------------------------------------------------------------ 首页

    public static void home(final Cb<Models.Home> cb) {
        get("/api/home", new Cb<JSONObject>() {
            @Override
            public void ok(JSONObject data) {
                cb.ok(Models.home(data));
            }

            @Override
            public void err(String msg) {
                cb.err(msg);
            }
        });
    }

    // ------------------------------------------------------------ 分类 / 列表

    public static void categories(final Cb<List<Models.Category>> cb) {
        get("/api/site/categories/video", new Cb<JSONObject>() {
            @Override
            public void ok(JSONObject data) {
                cb.ok(Models.categories(data.optJSONArray("items")));
            }

            @Override
            public void err(String msg) {
                cb.err(msg);
            }
        });
    }

    public static void listByCate(long cateId, int page, final Cb<Models.Page> cb) {
        get("/api/videos?cate_id=" + cateId + "&page=" + page, new Cb<JSONObject>() {
            @Override
            public void ok(JSONObject data) {
                cb.ok(Models.page(data));
            }

            @Override
            public void err(String msg) {
                cb.err(msg);
            }
        });
    }

    public static void listByRoute(String route, int page, final Cb<Models.Page> cb) {
        get("/api/videos/category/" + route + "?page=" + page, new Cb<JSONObject>() {
            @Override
            public void ok(JSONObject data) {
                cb.ok(Models.page(data));
            }

            @Override
            public void err(String msg) {
                cb.err(msg);
            }
        });
    }

    // ------------------------------------------------------------------ 榜单

    /** type: hot / recommend / potential */
    public static void rank(String type, final Cb<List<Models.Video>> cb) {
        get("/api/ranks/" + type, new Cb<JSONObject>() {
            @Override
            public void ok(JSONObject data) {
                cb.ok(Models.videos(data.optJSONArray("items")));
            }

            @Override
            public void err(String msg) {
                cb.err(msg);
            }
        });
    }

    public static void rankHot(final Cb<List<Models.Video>> cb) {
        rank(RANK_HOT, cb);
    }

    // ------------------------------------------------------------------ 专题

    public static void topics(final Cb<List<Models.Topic>> cb) {
        get("/api/topics", new Cb<JSONObject>() {
            @Override
            public void ok(JSONObject data) {
                cb.ok(Models.topics(data.optJSONArray("items")));
            }

            @Override
            public void err(String msg) {
                cb.err(msg);
            }
        });
    }

    public static void topicDetail(String slug, int page, final Cb<Models.TopicDetail> cb) {
        get("/api/topics/" + slug + "?page=" + page, new Cb<JSONObject>() {
            @Override
            public void ok(JSONObject data) {
                cb.ok(Models.topicDetail(data));
            }

            @Override
            public void err(String msg) {
                cb.err(msg);
            }
        });
    }

    // ------------------------------------------------------------------ 博客

    public static void posts(int page, final Cb<Models.PostPage> cb) {
        get("/api/posts?page=" + page, new Cb<JSONObject>() {
            @Override
            public void ok(JSONObject data) {
                cb.ok(Models.postPage(data));
            }

            @Override
            public void err(String msg) {
                cb.err(msg);
            }
        });
    }

    public static void post(long id, final Cb<Models.Post> cb) {
        get("/api/posts/" + id, new Cb<JSONObject>() {
            @Override
            public void ok(JSONObject data) {
                cb.ok(Models.post(data));
            }

            @Override
            public void err(String msg) {
                cb.err(msg);
            }
        });
    }

    /**
     * 博客分类列表：站点两个频道（黑料吃瓜 gossip / 短剧剧评 reviews）是服务端渲染页，
     * 没有对应的 JSON 列表接口（/api/posts/category/{route} 只认 DB 分类，返回「分类不存在」），
     * 所以这里直接抓分类页 HTML 解析卡片。
     */
    public static void postsByChannel(final String route, final int page,
                                      final Cb<Models.PostPage> cb) {
        POOL.execute(() -> {
            try {
                String path = page <= 1
                        ? "/blog/" + route + "/"
                        : "/blog/" + route + "/" + page + "/";
                String html = fetchHtml(path);
                if (html == null) throw new IOException("分类页抓取失败");
                Models.PostPage pg = Models.postPageFromHtml(html, route, page);
                if (pg.items.isEmpty()) throw new IOException("该分类暂无内容");
                success(cb, pg);
            } catch (Exception e) {
                fail(cb, e.getClass().getSimpleName() + ": " + e.getMessage());
            }
        });
    }

    /** 抓一段 HTML（保持 UA / 线路切换；不解析 JSON） */
    private static String fetchHtml(String path) {
        for (String base : order()) {
            try (Response resp = CLIENT.newCall(build(base + path, null)).execute()) {
                if (resp.body() == null) continue;
                String text = resp.body().string();
                if (text != null && text.length() > 1500) {
                    remember(base);
                    return text;
                }
            } catch (Exception ignored) {
                // 下一条线路
            }
        }
        return null;
    }

    /**
     * 云端观看历史：GET /api/users/me/history?token=&page=
     * 站点个人中心「历史记录」就是这条接口（实测返回 video_id / last_ep / total_ep / cover）。
     */
    public static void cloudHistory(int page, final Cb<Models.HistoryPage> cb) {
        getAuth("/api/users/me/history?page=" + Math.max(1, page), new Cb<JSONObject>() {
            @Override
            public void ok(JSONObject data) {
                cb.ok(Models.historyPage(data));
            }

            @Override
            public void err(String msg) {
                cb.err(msg);
            }
        });
    }

    /** 把一条本地进度写进账号：POST /api/users/me/history {video_id,last_ep,total_ep} */
    public static void pushHistory(long videoId, int lastEp, int totalEp) {
        if (!Auth.loggedIn() || videoId <= 0) return;
        JSONObject body = new JSONObject();
        try {
            body.put("video_id", videoId);
            body.put("last_ep", Math.max(1, lastEp));
            if (totalEp > 0) body.put("total_ep", totalEp);
        } catch (Exception ignored) {
        }
        postAuth("/api/users/me/history", body, new Cb<JSONObject>() {
            @Override
            public void ok(JSONObject data) {
            }

            @Override
            public void err(String msg) {
            }
        });
    }

    /** 删一条云端历史：POST /api/users/me/history/delete {video_id} */
    public static void deleteHistory(long videoId, final Cb<JSONObject> cb) {
        JSONObject body = new JSONObject();
        try {
            body.put("video_id", videoId);
        } catch (Exception ignored) {
        }
        postAuth("/api/users/me/history/delete", body, cb);
    }

    // ------------------------------------------------------------------ 搜索

    public static void search(String kw, int page, final Cb<Models.Page> cb) {
        try {
            String q = java.net.URLEncoder.encode(kw, "UTF-8");
            get("/api/search?q=" + q + "&page=" + page, new Cb<JSONObject>() {
                @Override
                public void ok(JSONObject data) {
                    cb.ok(Models.page(data));
                }

                @Override
                public void err(String msg) {
                    cb.err(msg);
                }
            });
        } catch (Exception e) {
            cb.err("关键词编码失败");
        }
    }

    public static void hotWords(final Cb<List<String>> cb) {
        get("/api/search/hot", new Cb<JSONObject>() {
            @Override
            public void ok(JSONObject data) {
                cb.ok(Models.words(data.optJSONArray("items")));
            }

            @Override
            public void err(String msg) {
                cb.err(msg);
            }
        });
    }

    // ------------------------------------------------------------------ 详情

    /** 原始详情 JSON：详情页要把它整段存成本地快照（断网可读） */
    public static void detailRaw(long id, final Cb<JSONObject> cb) {
        get("/api/videos/" + id, cb);
    }

    public static void detail(long id, final Cb<Models.Detail> cb) {
        get("/api/videos/" + id, new Cb<JSONObject>() {
            @Override
            public void ok(JSONObject data) {
                cb.ok(Models.detail(data));
            }

            @Override
            public void err(String msg) {
                cb.err(msg);
            }
        });
    }

    // ------------------------------------------------------------------ 评论

    public static void comments(long id, int page, final Cb<java.util.List<Models.Comment>> cb) {
        get("/api/videos/" + id + "/comments?page=" + page, new Cb<JSONObject>() {
            @Override
            public void ok(JSONObject data) {
                cb.ok(Models.comments(data.optJSONArray("items")));
            }

            @Override
            public void err(String msg) {
                cb.err(msg);
            }
        });
    }

    // ------------------------------------------------------------------ 播放

    public static void playback(long id, final int ep, final Cb<String> cb) {
        post("/api/videos/" + id + "/episodes/" + ep + "/playback", new Cb<JSONObject>() {
            @Override
            public void ok(JSONObject data) {
                JSONObject streams = data.optJSONObject("streams");
                String url = streams != null ? streams.optString("main", "") : "";
                if (url.isEmpty()) url = data.optString("video_url", "");
                if (url.isEmpty()) {
                    cb.err("该集暂无可播放线路");
                } else {
                    cb.ok(url);
                }
            }

            @Override
            public void err(String msg) {
                cb.err(msg);
            }
        });
    }
    // ------------------------------------------------------------------ 标签

    /**
     * 标签剧集列表：/api/videos/tag/{slug} 是站点路由，部分标签在该接口返回空
     * （服务端只做了 SSR），此时第 1 页自动回退到站内搜索同名关键词。
     */
    public static void listByTag(final String slug, final String keyword, final int page,
                                final Cb<Models.Page> cb) {
        get("/api/videos/tag/" + slug + "?page=" + page, new Cb<JSONObject>() {
            @Override
            public void ok(JSONObject data) {
                Models.Page p = Models.page(data);
                if (p.items.isEmpty()) {
                    // 标签接口对很多 slug 每一页都返回空（只有 SSR 有数据），
                    // 任何一页为空都退搜索兜底 —— 且用同一个页码，否则第 2 页起
                    // 拿到 pages=0 的空页，PagedGrid 以为只有 1 页，永远不续
                    search(keyword, page, cb);
                } else {
                    cb.ok(p);
                }
            }

            @Override
            public void err(String msg) {
                search(keyword, page, cb);
            }
        });
    }

    // -------------------------------------------------------------- 博客播放/评论

    /**
     * 帖子正文视频：POST /api/posts/{id}/playback（GET 会 404，这就是之前博客「看不到视频」的原因）。
     * 返回 videos[{index,url}]，index 对应正文里 .post-video-player 的 data-video-index。
     */
    public static void postPlayback(long id, final Cb<List<Models.PostVideo>> cb) {
        post("/api/posts/" + id + "/playback", new Cb<JSONObject>() {
            @Override
            public void ok(JSONObject data) {
                List<Models.PostVideo> out = new ArrayList<>();
                org.json.JSONArray arr = data.optJSONArray("videos");
                if (arr != null) {
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject o = arr.optJSONObject(i);
                        if (o == null) continue;
                        String url = o.optString("url", "");
                        if (url.isEmpty()) continue;
                        Models.PostVideo pv = new Models.PostVideo();
                        pv.index = o.optInt("index", i);
                        pv.url = url;
                        out.add(pv);
                    }
                }
                if (out.isEmpty()) {
                    String single = data.optString("video_url", "");
                    if (!single.isEmpty()) {
                        Models.PostVideo pv = new Models.PostVideo();
                        pv.index = 0;
                        pv.url = single;
                        out.add(pv);
                    }
                }
                cb.ok(out);
            }

            @Override
            public void err(String msg) {
                cb.err(msg);
            }
        });
    }

    public static void postComments(long id, int page, final Cb<java.util.List<Models.Comment>> cb) {
        get("/api/posts/" + id + "/comments?page=" + page, new Cb<JSONObject>() {
            @Override
            public void ok(JSONObject data) {
                cb.ok(Models.comments(data.optJSONArray("items")));
            }

            @Override
            public void err(String msg) {
                cb.err(msg);
            }
        });
    }

    // ------------------------------------------------------------ 每集评论 / 发评论

    /** 剧集评论按「影片 + 集数」隔离：ep > 0 时只取该集 */
    public static void comments(long id, int ep, int page,
                                final Cb<java.util.List<Models.Comment>> cb) {
        String q = ep > 0 ? "?ep=" + ep + "&page=" + page : "?page=" + page;
        get("/api/videos/" + id + "/comments" + q, new Cb<JSONObject>() {
            @Override
            public void ok(JSONObject data) {
                cb.ok(Models.comments(data.optJSONArray("items")));
            }

            @Override
            public void err(String msg) {
                cb.err(msg);
            }
        });
    }

    /** 发一级评论（需登录）。集号只认 query，正文只带 text。 */
    public static void postVideoComment(long id, int ep, String text, final Cb<String> cb) {
        JSONObject body = new JSONObject();
        try {
            body.put("text", text);
        } catch (Exception ignored) {
            // text 一定可序列化
        }
        postRaw("/api/videos/" + id + "/comments?ep=" + Math.max(1, ep), body, new Cb<JSONObject>() {
            @Override
            public void ok(JSONObject data) {
                cb.ok("已提交，审核通过后展示");
            }

            @Override
            public void err(String msg) {
                cb.err(msg);
            }
        });
    }

    /** 发回复（需登录）：reply_id 带上，集号由服务端继承父楼 */
    public static void replyVideoComment(long id, long replyId, String text, final Cb<String> cb) {
        JSONObject body = new JSONObject();
        try {
            body.put("text", text);
            body.put("reply_id", replyId);
        } catch (Exception ignored) {
            // 同上
        }
        postRaw("/api/videos/" + id + "/comments", body, new Cb<JSONObject>() {
            @Override
            public void ok(JSONObject data) {
                cb.ok("已提交，审核通过后展示");
            }

            @Override
            public void err(String msg) {
                cb.err(msg);
            }
        });
    }

    /** 博客留言（需登录）：POST /api/posts/{id}/comments */
    public static void postPostComment(long id, String text, final Cb<String> cb) {
        JSONObject body = new JSONObject();
        try {
            body.put("text", text);
        } catch (Exception ignored) {
            // 同上
        }
        postRaw("/api/posts/" + id + "/comments", body, new Cb<JSONObject>() {
            @Override
            public void ok(JSONObject data) {
                cb.ok("已提交，审核通过后展示");
            }

            @Override
            public void err(String msg) {
                cb.err(msg);
            }
        });
    }

    // ------------------------------------------------------------ 账号

    public static void login(String username, String password, final Cb<JSONObject> cb) {
        JSONObject body = new JSONObject();
        try {
            body.put("username", username);
            body.put("password", password);
        } catch (Exception ignored) {
            // 同上
        }
        postRaw("/api/account/loginByPassword", body, cb);
    }

    public static void register(String username, String password, String email,
                                final Cb<JSONObject> cb) {
        JSONObject body = new JSONObject();
        try {
            body.put("username", username);
            body.put("password", password);
            body.put("confirm_password", password);
            if (email != null && !email.isEmpty()) body.put("email", email);
        } catch (Exception ignored) {
            // 同上
        }
        postRaw("/api/account/registerByPassword", body, cb);
    }

    /** 我的账号（含 counts 与 interactions 互动 id 列表） */
    public static void me(final Cb<JSONObject> cb) {
        getAuth("/api/account/me", cb);
    }

    /**
     * 影片评分：GET /api/videos/{id}/rating?token=
     * 返回 {my, rating_cnt, score}（my = 我打的星，0 = 没打过）
     */
    public static void rating(long videoId, final Cb<Models.Rating> cb) {
        get(authPath("/api/videos/" + videoId + "/rating"), new Cb<JSONObject>() {
            @Override
            public void ok(JSONObject data) {
                cb.ok(Models.rating(data));
            }

            @Override
            public void err(String msg) {
                cb.err(msg);
            }
        });
    }

    /** 提交评分：POST /api/videos/{id}/rating?token= {score}；已评过返回 already=true 与最新均值 */
    public static void submitRating(long videoId, int stars, final Cb<Models.Rating> cb) {
        JSONObject body = new JSONObject();
        try {
            body.put("score", Math.max(1, Math.min(5, stars)));
        } catch (Exception ignored) {
        }
        postAuth("/api/videos/" + videoId + "/rating", body, new Cb<JSONObject>() {
            @Override
            public void ok(JSONObject data) {
                cb.ok(Models.rating(data));
            }

            @Override
            public void err(String msg) {
                cb.err(msg);
            }
        });
    }

    /**
     * 喜欢 / 取消喜欢（按集）：POST|DELETE /api/videos/{id}/like?ep={ep}&token=
     * 返回 {ep, ep_likes, liked, likes}
     */
    public static void likeVideo(long videoId, int ep, boolean on, final Cb<JSONObject> cb) {
        String path = "/api/videos/" + videoId + "/like?ep=" + Math.max(1, ep);
        if (on) {
            postAuth(path, new JSONObject(), cb);
        } else {
            deleteAuth(path, cb);
        }
    }

    /** 收藏 / 取消收藏：POST|DELETE /api/videos/{id}/collect?token= 返回 {collected, collects} */
    public static void collectVideo(long videoId, boolean on, final Cb<JSONObject> cb) {
        String path = "/api/videos/" + videoId + "/collect";
        if (on) {
            postAuth(path, new JSONObject(), cb);
        } else {
            deleteAuth(path, cb);
        }
    }

    /**
     * 修改资料：PATCH /api/account/profile?token=
     * 字段：nickname / avatar / sign / desc（实测 200，返回更新后的 user）
     */
    public static void updateProfile(String nickname, String avatar, String sign, String desc,
                                     final Cb<JSONObject> cb) {
        JSONObject body = new JSONObject();
        try {
            if (nickname != null) body.put("nickname", nickname);
            if (avatar != null) body.put("avatar", avatar);
            if (sign != null) body.put("sign", sign);
            if (desc != null) body.put("desc", desc);
        } catch (Exception ignored) {
        }
        patchAuth("/api/account/profile", body, cb);
    }

    /** 二级评论：GET /api/videos/{id}/comments/{cid}/replies?page=&token= */
    public static void commentReplies(long videoId, long commentId, int page,
                                      final Cb<List<Models.Comment>> cb) {
        get("/api/videos/" + videoId + "/comments/" + commentId + "/replies?page=" + page,
                new Cb<JSONObject>() {
                    @Override
                    public void ok(JSONObject data) {
                        cb.ok(Models.comments(data.optJSONArray("items")));
                    }

                    @Override
                    public void err(String msg) {
                        cb.err(msg);
                    }
                });
    }

    /** 需要登录的 PATCH（资料保存） */
    private static void patchAuth(final String path, final JSONObject body, final Cb<JSONObject> cb) {
        if (!Auth.loggedIn()) {
            fail(cb, "请先登录");
            return;
        }
        POOL.execute(() -> {
            try {
                String full = authPath(path);
                JSONObject o = null;
                for (String base : order()) {
                    try (Response resp = CLIENT.newCall(new Request.Builder()
                            .url(base + full)
                            .header("User-Agent", UA)
                            .header("Accept", "application/json, text/plain, */*")
                            .patch(RequestBody.create(body == null ? "{}" : body.toString(), JSON_TYPE))
                            .build()).execute()) {
                        if (resp.body() == null) continue;
                        String text = resp.body().string();
                        if (text.isEmpty()) continue;
                        o = new JSONObject(text);
                        remember(base);
                        break;
                    } catch (Exception ignored) {
                    }
                }
                if (o == null) throw new java.io.IOException("所有线路均不可用");
                if (o.optInt("status", 1) == 0) {
                    fail(cb, o.optString("msg", "接口返回异常"));
                    return;
                }
                success(cb, dataOf(o));
            } catch (Exception e) {
                fail(cb, e.getClass().getSimpleName() + ": " + e.getMessage());
            }
        });
    }

    /** POST 一段自定义 JSON 正文，返回 data 对象 */
    private static void postRaw(final String path, final JSONObject body, final Cb<JSONObject> cb) {
        POOL.execute(() -> {
            try {
                JSONObject o = call(path, body == null ? "{}" : body.toString());
                int st = o.optInt("status", 1);
                if (st == 0) {
                    fail(cb, o.optString("msg", "接口返回异常"));
                    return;
                }
                success(cb, dataOf(o));
            } catch (Exception e) {
                fail(cb, e.getClass().getSimpleName() + ": " + e.getMessage());
            }
        });
    }

    /**
     * 拉取一张网络图片（浏览大图 / 保存用）。
     * 图床密文必须先过 CryptoImage.decode（AES-128-CBC）再解码，
     * 否则 BitmapFactory 拿到的是密文字节，必然「解码失败」——
     * 与列表加载（ImageLoader.fetch）走同一条解密链路。
     */
    public static android.graphics.Bitmap fetchBitmap(String url) throws Exception {
        CryptoImage.sync();
        okhttp3.Request req = new okhttp3.Request.Builder()
                .url(url)
                .header("User-Agent", UA)
                .header("Referer", Models.HOST + "/")
                .build();
        try (okhttp3.Response resp = CLIENT.newCall(req).execute()) {
            if (resp.body() == null) throw new java.io.IOException("空响应");
            byte[] data = CryptoImage.decode(CryptoImage.readAll(resp.body().byteStream()));
            android.graphics.Bitmap bmp = android.graphics.BitmapFactory
                    .decodeByteArray(data, 0, data.length);
            if (bmp == null) throw new java.io.IOException("解码失败");
            return bmp;
        }
    }
}