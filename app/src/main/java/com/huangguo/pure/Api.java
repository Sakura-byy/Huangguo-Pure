package com.huangguo.pure;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

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
        java.util.List<String> got = Mirror.fetch();
        if (got.isEmpty()) return false;
        StringBuilder sb = new StringBuilder();
        for (String h : got) {
            if (sb.length() > 0) sb.append(',');
            sb.append(h);
        }
        if (prefs != null) {
            prefs.edit().putString("mirrors", sb.toString())
                    .putLong("mirror_at", System.currentTimeMillis()).apply();
        }
        applyMirrors(sb.toString());
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
        String tk = Auth.token();
        if (tk != null && !tk.isEmpty()) b.header("Authorization", "Bearer " + tk);
        if (body != null) b.post(RequestBody.create(body, JSON_TYPE));
        return b.build();
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

    public static void me(final Cb<JSONObject> cb) {
        get("/api/account/me", new Cb<JSONObject>() {
            @Override
            public void ok(JSONObject data) {
                cb.ok(data);
            }

            @Override
            public void err(String msg) {
                cb.err(msg);
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