package com.huangguo.pure;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 本地库：收藏 + 观看/阅读历史 + 点赞 + 已缓存剧集 + 我的评分。
 *
 * v3：支持混合类型（0=短剧视频，1=博客文章，2=专题）。
 * 表主键仍是 id，但存的是合成键 key = type*10_000_000 + 真实 id，
 * 避免不同类型的 id 撞车；type/slug 列用于「我的」页展示与跳转。
 * v4：点赞表 likes —— 站点对游客同样是本地点赞，登录后才归并，这里与之对齐。
 * v6：缓存表 cached（剧 → 已缓存集数集合，逗号分隔）、评分表 rating，
 *     fav 表加 seen_ep —— 收藏列表里「上次看过之后有没有更新」靠 ep_count 与 seen_ep 比较。
 */
public final class Store extends SQLiteOpenHelper {

    public static final int T_VIDEO = 0;
    public static final int T_POST = 1;
    public static final int T_TOPIC = 2;

    /** 点赞目标 kind：视频（剧+集）用 "video:<vid>"，评论用下面两个常量 */
    public static final String LIKE_VCOMMENT = "vcomment";
    public static final String LIKE_PCOMMENT = "pcomment";

    /** 视频点赞 kind：按「剧+集」隔离，与站点 itemKey(videoId, ep) 对齐 */
    public static String videoLikeKind(long videoId) {
        return "video:" + videoId;
    }

    public static class History {
        public long id;
        public int type = T_VIDEO;
        public String slug = "";
        public String title = "";
        public String cover = "";
        public int ep = 1;
        public long positionMs;
        public long durationMs;
        public long updatedAt;
        public int epCount;

        public int percent() {
            if (durationMs <= 0) return 0;
            return (int) Math.min(100, positionMs * 100 / durationMs);
        }
    }

    /** 一部剧的缓存记录：eps 为已缓存的集数（升序） */
    public static class Cached {
        public long id;
        public String title = "";
        public String cover = "";
        public int epCount;
        public long updatedAt;
        public final Set<Integer> eps = new LinkedHashSet<>();

        public int count() {
            return eps.size();
        }

        /** "1, 2, 3 · 共 3 集" */
        public String epsText() {
            StringBuilder sb = new StringBuilder();
            int i = 0;
            for (Integer e : eps) {
                if (i > 0) sb.append(", ");
                sb.append(e);
                i++;
                if (i >= 8) {
                    sb.append("…");
                    break;
                }
            }
            return sb.toString();
        }
    }

    private static Store instance;

    public static synchronized Store get(Context c) {
        if (instance == null) instance = new Store(c.getApplicationContext());
        return instance;
    }

    private final Context appCtx;

    private Store(Context c) {
        super(c, "huangguo_local.db", null, 8);
        appCtx = c.getApplicationContext();
    }

    /** 校验缓存会 stat 文件、查 MediaStore，全部挪出主线程；单线程保证不会并发跑两遍 */
    private static final java.util.concurrent.ExecutorService IO =
            java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "hg-store-verify");
                t.setPriority(Thread.MIN_PRIORITY);
                return t;
            });
    private static final android.os.Handler MAIN =
            new android.os.Handler(android.os.Looper.getMainLooper());
    private final java.util.concurrent.atomic.AtomicBoolean verifying =
            new java.util.concurrent.atomic.AtomicBoolean(false);

    /**
     * 后台校验缓存并回主线程回调；同一时刻只跑一份，重复调用直接忽略。
     * 界面可以先把列表画出来，几百毫秒后文件真丢了的条目自己消失。
     */
    public void verifyCacheFilesAsync(final Runnable onDone) {
        if (!verifying.compareAndSet(false, true)) return;
        IO.execute(() -> {
            try {
                verifyCacheFiles();
            } catch (Throwable ignored) {
            } finally {
                verifying.set(false);
            }
            if (onDone != null) MAIN.post(onDone);
        });
    }

    /**
     * 校验「离线缓存」是否还在：
     * 每集去本地文件表 / MediaStore 里确认文件存在，缺失的集从记录里剔掉；
     * 整部都没有文件了就删掉这条缓存记录。进入「我的」时自动跑一次。
     */
    public void verifyCacheFiles() {
        for (Cached c : cachedList()) {
            Set<Integer> alive = new LinkedHashSet<>();
            for (Integer ep : c.eps) {
                String uri = localFile(c.id, ep);
                if (uri == null || uri.isEmpty()) {
                    uri = MediaSave.findDownloaded(appCtx, c.title, "第" + ep + "集.mp4");
                }
                if (uri != null && !uri.isEmpty() && fileAlive(uri)) alive.add(ep);
            }
            if (alive.isEmpty()) {
                removeCached(c.id);
                continue;
            }
            if (alive.size() == c.eps.size()) continue;
            // 部分文件被外部删掉了：记录收缩到还剩的集
            ContentValues cv = new ContentValues();
            cv.put("eps", joinEps(alive));
            cv.put("ep_count", Math.max(alive.size(), c.epCount));
            cv.put("updated_at", System.currentTimeMillis());
            try {
                getWritableDatabase().update("cached", cv, "vid=?",
                        new String[]{String.valueOf(c.id)});
            } catch (Exception ignored) {
            }
            for (Integer ep : c.eps) {
                if (!alive.contains(ep)) {
                    try {
                        getWritableDatabase().delete("cached_file", "vid=? AND ep=?",
                                new String[]{String.valueOf(c.id), String.valueOf(ep)});
                    } catch (Exception ignored) {
                    }
                }
            }
        }
    }

    /** 清掉某一集的本地文件记录（文件被外部删了） */
    public void clearLocalFile(long videoId, int ep) {
        try {
            getWritableDatabase().delete("cached_file", "vid=? AND ep=?",
                    new String[]{String.valueOf(videoId), String.valueOf(ep)});
        } catch (Exception ignored) {
        }
    }

    /** 播放前探活：本地缓存文件还在不在 */
    public boolean isFileAlive(String uriOrPath) {
        return fileAlive(uriOrPath);
    }

    /**
     * content:// 走 resolver 探一次，绝对路径走 File.exists。
     *
     * 关键：MediaStore 里文件被外部删掉后，content:// 记录还在、openAssetFileDescriptor
     * 依然能成功打开，但长度是 0 / 读不出字节 —— 只看「fd != null」会把已经没了的
     * 缓存判断成还在，播放器于是指着空文件播，表现为黑屏。这里补长度与首字节校验。
     */
    private boolean fileAlive(String uriOrPath) {
        if (uriOrPath == null || uriOrPath.isEmpty()) return false;
        try {
            if (uriOrPath.startsWith("content://")) {
                android.net.Uri u = android.net.Uri.parse(uriOrPath);
                try (android.content.res.AssetFileDescriptor fd =
                             appCtx.getContentResolver().openAssetFileDescriptor(u, "r")) {
                    if (fd == null) return false;
                    long len = fd.getLength();
                    if (len == 0) return false;
                    if (len > 0) return true;
                    // 长度未知（-1）：实际读一个字节，能读到才算文件真的在
                    try (java.io.InputStream in = fd.createInputStream()) {
                        return in != null && in.read() != -1;
                    }
                }
            }
            java.io.File f = new java.io.File(uriOrPath);
            return f.isFile() && f.length() > 0;
        } catch (Exception e) {
            return false;
        }
    }

    /** 不同类型的 id 用不同命名空间，避免主键冲突 */
    private static long key(int type, long id) {
        return type * 10000000L + id;
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE fav (id INTEGER PRIMARY KEY, title TEXT, cover TEXT,"
                + " score REAL, ep_count INTEGER, finished INTEGER DEFAULT 0,"
                + " type INTEGER DEFAULT 0, slug TEXT DEFAULT '', updated_at INTEGER,"
                + " seen_ep INTEGER DEFAULT 0)");
        db.execSQL("CREATE TABLE history (id INTEGER PRIMARY KEY, title TEXT, cover TEXT,"
                + " ep INTEGER, position INTEGER, duration INTEGER, ep_count INTEGER,"
                + " type INTEGER DEFAULT 0, slug TEXT DEFAULT '', updated_at INTEGER)");
        // 点赞表：必须在 onCreate 里建 —— 全新安装直接以当前版本创建，不会走 onUpgrade
        db.execSQL("CREATE TABLE IF NOT EXISTS likes ("
                + "k TEXT PRIMARY KEY, updated_at INTEGER)");
        db.execSQL("CREATE TABLE IF NOT EXISTS cached ("
                + "vid INTEGER PRIMARY KEY, title TEXT, cover TEXT, eps TEXT,"
                + " ep_count INTEGER DEFAULT 0, updated_at INTEGER)");
        db.execSQL("CREATE TABLE IF NOT EXISTS rating ("
                + "vid INTEGER PRIMARY KEY, stars INTEGER, updated_at INTEGER)");
        // v7：详情快照（离线打开已缓存剧集时不用联网）
        db.execSQL("CREATE TABLE IF NOT EXISTS detail ("
                + "vid INTEGER PRIMARY KEY, json TEXT, updated_at INTEGER)");
        // v8：每集本地文件 uri（离线播放用）
        db.execSQL("CREATE TABLE IF NOT EXISTS cached_file ("
                + "vid INTEGER, ep INTEGER, uri TEXT, updated_at INTEGER,"
                + " PRIMARY KEY(vid, ep))");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // v2：收藏多存一个「是否完结」，避免连载剧在列表里被写成「全 N 集」
        if (oldVersion < 2) {
            try {
                db.execSQL("ALTER TABLE fav ADD COLUMN finished INTEGER DEFAULT 0");
            } catch (Exception ignored) {
            }
        }
        // v3：混合收藏/历史（博客文章、专题）+ 专题 slug
        if (oldVersion < 3) {
            try {
                db.execSQL("ALTER TABLE fav ADD COLUMN type INTEGER DEFAULT 0");
            } catch (Exception ignored) {
            }
            try {
                db.execSQL("ALTER TABLE fav ADD COLUMN slug TEXT DEFAULT ''");
            } catch (Exception ignored) {
            }
            try {
                db.execSQL("ALTER TABLE history ADD COLUMN type INTEGER DEFAULT 0");
            } catch (Exception ignored) {
            }
            try {
                db.execSQL("ALTER TABLE history ADD COLUMN slug TEXT DEFAULT ''");
            } catch (Exception ignored) {
            }
        }
        // 全部用 ALTER 增量迁移，不 drop，收藏与历史数据保留
        // v4/v5：点赞表（IF NOT EXISTS 兼容已损坏的旧库）
        if (oldVersion < 5) {
            try {
                db.execSQL("CREATE TABLE IF NOT EXISTS likes ("
                        + "k TEXT PRIMARY KEY, updated_at INTEGER)");
            } catch (Exception ignored) {
            }
        }
        // v6：缓存记录 + 评分 + 收藏的「上次看到第几集」
        if (oldVersion < 6) {
            try {
                db.execSQL("ALTER TABLE fav ADD COLUMN seen_ep INTEGER DEFAULT 0");
            } catch (Exception ignored) {
            }
            try {
                db.execSQL("CREATE TABLE IF NOT EXISTS cached ("
                        + "vid INTEGER PRIMARY KEY, title TEXT, cover TEXT, eps TEXT,"
                        + " ep_count INTEGER DEFAULT 0, updated_at INTEGER)");
            } catch (Exception ignored) {
            }
            try {
                db.execSQL("CREATE TABLE IF NOT EXISTS rating ("
                        + "vid INTEGER PRIMARY KEY, stars INTEGER, updated_at INTEGER)");
            } catch (Exception ignored) {
            }
        }
        // v8：每集本地文件 uri
        if (oldVersion < 8) {
            try {
                db.execSQL("CREATE TABLE IF NOT EXISTS cached_file ("
                        + "vid INTEGER, ep INTEGER, uri TEXT, updated_at INTEGER,"
                        + " PRIMARY KEY(vid, ep))");
            } catch (Exception ignored) {
            }
        }
        // v7：详情快照表（老库增量建表，不 drop）
        if (oldVersion < 7) {
            try {
                db.execSQL("CREATE TABLE IF NOT EXISTS detail ("
                        + "vid INTEGER PRIMARY KEY, json TEXT, updated_at INTEGER)");
            } catch (Exception ignored) {
            }
        }
    }

    /** 存一份详情 JSON 快照：断网也能打开已缓存的剧集 */
    public void saveDetailSnapshot(long vid, String json) {
        if (vid <= 0 || json == null || json.isEmpty()) return;
        try {
            ContentValues cv = new ContentValues();
            cv.put("vid", vid);
            cv.put("json", json);
            cv.put("updated_at", System.currentTimeMillis());
            getWritableDatabase().insertWithOnConflict("detail", null, cv,
                    SQLiteDatabase.CONFLICT_REPLACE);
        } catch (Exception ignored) {
        }
    }

    /** 读详情快照；没有返回 null */
    public String detailSnapshot(long vid) {
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT json FROM detail WHERE vid=?", new String[]{String.valueOf(vid)})) {
            if (c.moveToFirst()) return c.getString(0);
        } catch (Exception ignored) {
        }
        return null;
    }

    // ---------------------------------------------------------------- 点赞

    private static String likeKey(String kind, long id) {
        return kind + ":" + id;
    }

    /** 返回操作后的点赞状态 */
    public boolean toggleLike(String kind, long id) {
        String k = likeKey(kind, id);
        SQLiteDatabase db = getWritableDatabase();
        if (isLiked(kind, id)) {
            db.delete("likes", "k=?", new String[]{k});
            return false;
        }
        ContentValues cv = new ContentValues();
        cv.put("k", k);
        cv.put("updated_at", System.currentTimeMillis());
        db.insertWithOnConflict("likes", null, cv, SQLiteDatabase.CONFLICT_REPLACE);
        return true;
    }

    public boolean isLiked(String kind, long id) {
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT 1 FROM likes WHERE k=?",
                new String[]{likeKey(kind, id)})) {
            return c.moveToFirst();
        }
    }

    // ---------------------------------------------------------------- 收藏

    public boolean isFavorite(int type, long id) {
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT 1 FROM fav WHERE id=?", new String[]{String.valueOf(key(type, id))})) {
            return c.moveToFirst();
        }
    }

    /** 返回操作后的收藏状态 */
    public boolean toggleFavorite(Models.Video v) {
        SQLiteDatabase db = getWritableDatabase();
        long k = key(T_VIDEO, v.id);
        if (isFavorite(T_VIDEO, v.id)) {
            db.delete("fav", "id=?", new String[]{String.valueOf(k)});
            return false;
        }
        ContentValues cv = new ContentValues();
        cv.put("id", k);
        cv.put("title", v.title);
        cv.put("cover", v.cover);
        cv.put("score", v.score);
        cv.put("ep_count", v.epCount);
        cv.put("finished", v.finished ? 1 : 0);
        cv.put("type", T_VIDEO);
        cv.put("slug", "");
        cv.put("seen_ep", v.epCount);   // 收藏那一刻的集数即「已看过」的基线
        cv.put("updated_at", System.currentTimeMillis());
        db.insertWithOnConflict("fav", null, cv, SQLiteDatabase.CONFLICT_REPLACE);
        return true;
    }

    /** 博客文章 / 专题收藏：返回操作后的收藏状态 */
    public boolean toggleItem(int type, long id, String title, String cover, String slug) {
        SQLiteDatabase db = getWritableDatabase();
        long k = key(type, id);
        if (isFavorite(type, id)) {
            db.delete("fav", "id=?", new String[]{String.valueOf(k)});
            return false;
        }
        ContentValues cv = new ContentValues();
        cv.put("id", k);
        cv.put("title", title);
        cv.put("cover", cover);
        cv.put("score", 0);
        cv.put("ep_count", 0);
        cv.put("finished", 1);
        cv.put("type", type);
        cv.put("slug", slug == null ? "" : slug);
        cv.put("seen_ep", 0);
        cv.put("updated_at", System.currentTimeMillis());
        db.insertWithOnConflict("fav", null, cv, SQLiteDatabase.CONFLICT_REPLACE);
        return true;
    }

    /** 混合收藏列表：Models.Video / Models.Post / Models.Topic */
    public List<Object> favorites() {
        List<Object> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT id,title,cover,score,ep_count,finished,type,slug,seen_ep"
                        + " FROM fav ORDER BY updated_at DESC", null)) {
            while (c.moveToNext()) {
                int type = c.getInt(6);
                String slug = c.getString(7) == null ? "" : c.getString(7);
                long realId = c.getLong(0) - type * 10000000L;
                if (type == T_POST) {
                    Models.Post p = new Models.Post();
                    p.id = realId;
                    p.title = c.getString(1);
                    p.cover = c.getString(2);
                    out.add(p);
                } else if (type == T_TOPIC) {
                    Models.Topic t = new Models.Topic();
                    t.id = realId;
                    t.slug = slug;
                    t.title = c.getString(1);
                    t.cover = c.getString(2);
                    out.add(t);
                } else {
                    Models.Video v = new Models.Video();
                    v.id = realId;
                    v.title = c.getString(1);
                    v.cover = c.getString(2);
                    v.score = c.getDouble(3);
                    v.epCount = c.getInt(4);
                    v.finished = c.getInt(5) == 1;
                    v.seenEp = c.getInt(8);
                    out.add(v);
                }
            }
        }
        return out;
    }

    /** 详情页看到最新集数后：把收藏里的 ep_count 刷新，并推进 seen_ep（看过即不再提示更新） */
    public void refreshFavoriteEpCount(long videoId, int epCount, boolean markSeen) {
        if (epCount <= 0) return;
        ContentValues cv = new ContentValues();
        cv.put("ep_count", epCount);
        if (markSeen) cv.put("seen_ep", epCount);
        try {
            getWritableDatabase().update("fav", cv, "id=?",
                    new String[]{String.valueOf(key(T_VIDEO, videoId))});
        } catch (Exception ignored) {
        }
    }

    public void removeFavorite(int type, long id) {
        getWritableDatabase().delete("fav", "id=?",
                new String[]{String.valueOf(key(type, id))});
    }

    // ---------------------------------------------------------------- 评分

    /** 我的评分（0 = 未评分） */
    public int ratingOf(long videoId) {
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT stars FROM rating WHERE vid=?",
                new String[]{String.valueOf(videoId)})) {
            return c.moveToFirst() ? c.getInt(0) : 0;
        }
    }

    public void setRating(long videoId, int stars) {
        ContentValues cv = new ContentValues();
        cv.put("vid", videoId);
        cv.put("stars", Math.max(0, Math.min(5, stars)));
        cv.put("updated_at", System.currentTimeMillis());
        getWritableDatabase().insertWithOnConflict("rating", null, cv,
                SQLiteDatabase.CONFLICT_REPLACE);
    }

    // ---------------------------------------------------------------- 缓存

    /** 一集缓存完成：并入这部剧的集数集合 */
    public void cacheEpisode(long videoId, String title, String cover, int ep, int epCount) {
        if (videoId <= 0 || ep <= 0) return;
        Set<Integer> eps = cachedEps(videoId);
        eps.add(ep);
        ContentValues cv = new ContentValues();
        cv.put("vid", videoId);
        cv.put("title", title);
        cv.put("cover", cover);
        cv.put("eps", joinEps(eps));
        cv.put("ep_count", epCount);
        cv.put("updated_at", System.currentTimeMillis());
        getWritableDatabase().insertWithOnConflict("cached", null, cv,
                SQLiteDatabase.CONFLICT_REPLACE);
    }

    /**
     * 用本地缓存记录拼一份最小详情（断网兜底）：
     * 有快照走快照，没有快照时至少能打开详情页看到剧名 + 已缓存集数。
     */
    public Models.Detail offlineDetail(long videoId) {
        Set<Integer> eps = cachedEps(videoId);
        if (eps.isEmpty()) return null;
        Models.Detail d = new Models.Detail();
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT title, cover, ep_count FROM cached WHERE vid=?",
                new String[]{String.valueOf(videoId)})) {
            if (c.moveToFirst()) {
                d.info.id = videoId;
                d.info.title = c.getString(0) == null ? "" : c.getString(0);
                d.info.cover = c.getString(1) == null ? "" : c.getString(1);
                d.info.epCount = Math.max(c.getInt(2), eps.size());
            }
        } catch (Exception ignored) {
        }
        if (d.info.title.isEmpty()) d.info.title = "已缓存剧集";
        for (Integer e : eps) {
            Models.Episode ep = new Models.Episode();
            ep.ep = e;
            ep.title = "第" + e + "集";
            d.episodes.add(ep);
        }
        return d;
    }

    /** 记录某集下载后的本地 uri（离线播放直接用它） */
    public void saveLocalFile(long videoId, int ep, String uri) {
        if (videoId <= 0 || ep <= 0 || uri == null || uri.isEmpty()) return;
        try {
            ContentValues cv = new ContentValues();
            cv.put("vid", videoId);
            cv.put("ep", ep);
            cv.put("uri", uri);
            cv.put("updated_at", System.currentTimeMillis());
            getWritableDatabase().insertWithOnConflict("cached_file", null, cv,
                    SQLiteDatabase.CONFLICT_REPLACE);
        } catch (Exception ignored) {
        }
    }

    /** 某集的本地文件 uri；没有返回 null */
    public String localFile(long videoId, int ep) {
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT uri FROM cached_file WHERE vid=? AND ep=?",
                new String[]{String.valueOf(videoId), String.valueOf(ep)})) {
            if (c.moveToFirst()) return c.getString(0);
        } catch (Exception ignored) {
        }
        return null;
    }

    public Set<Integer> cachedEps(long videoId) {
        Set<Integer> out = new LinkedHashSet<>();
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT eps FROM cached WHERE vid=?",
                new String[]{String.valueOf(videoId)})) {
            if (c.moveToFirst()) out.addAll(splitEps(c.getString(0)));
        }
        return out;
    }

    public boolean isEpCached(long videoId, int ep) {
        return cachedEps(videoId).contains(ep);
    }

    public void removeCached(long videoId) {
        getWritableDatabase().delete("cached", "vid=?",
                new String[]{String.valueOf(videoId)});
    }

    /** 已缓存剧集列表（新的在前） */
    public List<Cached> cachedList() {
        List<Cached> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT vid,title,cover,eps,ep_count,updated_at FROM cached"
                        + " ORDER BY updated_at DESC", null)) {
            while (c.moveToNext()) {
                Cached k = new Cached();
                k.id = c.getLong(0);
                k.title = c.getString(1);
                k.cover = c.getString(2);
                k.eps.addAll(splitEps(c.getString(3)));
                k.epCount = c.getInt(4);
                k.updatedAt = c.getLong(5);
                if (!k.eps.isEmpty()) out.add(k);
            }
        }
        return out;
    }

    private static String joinEps(Set<Integer> eps) {
        StringBuilder sb = new StringBuilder();
        int i = 0;
        for (Integer e : eps) {
            if (i++ > 0) sb.append(',');
            sb.append(e);
        }
        return sb.toString();
    }

    private static Set<Integer> splitEps(String s) {
        Set<Integer> out = new LinkedHashSet<>();
        if (s == null || s.isEmpty()) return out;
        for (String part : s.split(",")) {
            try {
                out.add(Integer.parseInt(part.trim()));
            } catch (Exception ignored) {
            }
        }
        return out;
    }

    // ---------------------------------------------------------------- 历史

    public void recordHistory(long id, String title, String cover, int ep,
                              long positionMs, long durationMs, int epCount) {
        record(T_VIDEO, id, "", title, cover, ep, positionMs, durationMs, epCount);
        // 看过第 ep 集：seen_ep 推进到「看过的最大集数」，ep_count 刷新为最新集数
        if (epCount > 0) markWatchedEp(id, ep, epCount);
    }

    /** 记录「看过第几集」：收藏列表的「有更新」角标 = ep_count > seen_ep */
    public void markWatchedEp(long videoId, int ep, int epCount) {
        if (videoId <= 0) return;
        int seen;
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT seen_ep FROM fav WHERE id=?",
                new String[]{String.valueOf(key(T_VIDEO, videoId))})) {
            if (!c.moveToFirst()) return;   // 没收藏就不用维护角标
            seen = c.getInt(0);
        }
        ContentValues cv = new ContentValues();
        cv.put("seen_ep", Math.max(seen, ep));
        if (epCount > 0) cv.put("ep_count", epCount);
        try {
            getWritableDatabase().update("fav", cv, "id=?",
                    new String[]{String.valueOf(key(T_VIDEO, videoId))});
        } catch (Exception ignored) {
        }
    }

    /** 博客 / 专题阅读记录：无进度、无集数 */
    public void recordRead(int type, long id, String slug, String title, String cover) {
        record(type, id, slug, title, cover, 0, 0L, 0L, 0);
    }

    private void record(int type, long id, String slug, String title, String cover,
                        int ep, long positionMs, long durationMs, int epCount) {
        if (id <= 0) return;
        ContentValues cv = new ContentValues();
        cv.put("id", key(type, id));
        cv.put("title", title);
        cv.put("cover", cover);
        cv.put("ep", ep);
        cv.put("position", positionMs);
        cv.put("duration", durationMs);
        cv.put("ep_count", epCount);
        cv.put("type", type);
        cv.put("slug", slug == null ? "" : slug);
        cv.put("updated_at", System.currentTimeMillis());
        getWritableDatabase().insertWithOnConflict("history", null, cv,
                SQLiteDatabase.CONFLICT_REPLACE);
    }

    /** 短剧视频的进度记录（续播用） */
    public History historyOf(long videoId) {
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT id,title,cover,ep,position,duration,ep_count,updated_at,type,slug"
                        + " FROM history WHERE id=?",
                new String[]{String.valueOf(key(T_VIDEO, videoId))})) {
            if (!c.moveToFirst()) return null;
            return read(c, videoId);
        }
    }

    public List<History> history() {
        List<History> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT id,title,cover,ep,position,duration,ep_count,updated_at,type,slug"
                        + " FROM history ORDER BY updated_at DESC LIMIT 200", null)) {
            while (c.moveToNext()) {
                int type = c.getInt(8);
                out.add(read(c, c.getLong(0) - type * 10000000L));
            }
        }
        return out;
    }

    private static History read(Cursor c, long realId) {
        History h = new History();
        h.id = realId;
        h.title = c.getString(1);
        h.cover = c.getString(2);
        h.ep = c.getInt(3);
        h.positionMs = c.getLong(4);
        h.durationMs = c.getLong(5);
        h.epCount = c.getInt(6);
        h.updatedAt = c.getLong(7);
        h.type = c.getInt(8);
        h.slug = c.getString(9) == null ? "" : c.getString(9);
        return h;
    }

    public void deleteHistory(int type, long id) {
        getWritableDatabase().delete("history", "id=?",
                new String[]{String.valueOf(key(type, id))});
    }

    public void clearHistory() {
        getWritableDatabase().delete("history", null, null);
    }
}
