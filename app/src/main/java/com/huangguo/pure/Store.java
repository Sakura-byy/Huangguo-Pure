package com.huangguo.pure;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * 本地库：收藏 + 观看/阅读历史 + 点赞（不上传、不依赖账号）。
 *
 * v3：支持混合类型（0=短剧视频，1=博客文章，2=专题）。
 * 表主键仍是 id，但存的是合成键 key = type*10_000_000 + 真实 id，
 * 避免不同类型的 id 撞车；type/slug 列用于「我的」页展示与跳转。
 * v4：点赞表 likes —— 站点对游客同样是本地点赞，登录后才归并，这里与之对齐。
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

    private static Store instance;

    public static synchronized Store get(Context c) {
        if (instance == null) instance = new Store(c.getApplicationContext());
        return instance;
    }

    private Store(Context c) {
        super(c, "huangguo_local.db", null, 5);
    }

    /** 不同类型的 id 用不同命名空间，避免主键冲突 */
    private static long key(int type, long id) {
        return type * 10000000L + id;
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE fav (id INTEGER PRIMARY KEY, title TEXT, cover TEXT,"
                + " score REAL, ep_count INTEGER, finished INTEGER DEFAULT 0,"
                + " type INTEGER DEFAULT 0, slug TEXT DEFAULT '', updated_at INTEGER)");
        db.execSQL("CREATE TABLE history (id INTEGER PRIMARY KEY, title TEXT, cover TEXT,"
                + " ep INTEGER, position INTEGER, duration INTEGER, ep_count INTEGER,"
                + " type INTEGER DEFAULT 0, slug TEXT DEFAULT '', updated_at INTEGER)");
        // 点赞表：必须在 onCreate 里建 —— 全新安装直接以当前版本创建，不会走 onUpgrade
        db.execSQL("CREATE TABLE IF NOT EXISTS likes ("
                + "k TEXT PRIMARY KEY, updated_at INTEGER)");
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
        cv.put("updated_at", System.currentTimeMillis());
        db.insertWithOnConflict("fav", null, cv, SQLiteDatabase.CONFLICT_REPLACE);
        return true;
    }

    /** 混合收藏列表：Models.Video / Models.Post / Models.Topic */
    public List<Object> favorites() {
        List<Object> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT id,title,cover,score,ep_count,finished,type,slug"
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
                    out.add(v);
                }
            }
        }
        return out;
    }

    public void removeFavorite(int type, long id) {
        getWritableDatabase().delete("fav", "id=?",
                new String[]{String.valueOf(key(type, id))});
    }

    // ---------------------------------------------------------------- 历史

    public void recordHistory(long id, String title, String cover, int ep,
                              long positionMs, long durationMs, int epCount) {
        record(T_VIDEO, id, "", title, cover, ep, positionMs, durationMs, epCount);
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
