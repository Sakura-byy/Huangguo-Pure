package com.huangguo.pure;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;

/**
 * 收藏 / 历史 的账号云同步。
 *
 * 策略：本地库始终是工作副本（离线可用），登录后把本地快照推给账号；
 * 服务端同步路由未核实（见 Api.syncLibrary 注释），推送失败就保持本地，
 * 不阻塞任何界面操作、不弹错误。
 *
 * 未登录 → 只写本地；登录 → 本地写完顺手 markDirty，下一次进入前台/登录成功时推一次。
 */
public final class Sync {

    private static volatile boolean dirty = true;

    private Sync() {
    }

    /** 本地数据发生变化：下次有机会就推一次 */
    public static void markDirty() {
        dirty = true;
    }

    public static void pushIfDirty(Context c) {
        if (!dirty) return;
        push(c);
    }

    /**
     * 把本地收藏同步到账号。
     *
     * 站点没有「批量同步」路由（实测 /api/account/sync、/api/favorites 全部 404），
     * 但收藏是视频维度可写的：POST /api/videos/{id}/collect?token=。
     * 所以逐个把本地收藏的剧写进账号；历史站点只记计数、没有列表/写入路由，保持本地。
     */
    public static void push(Context c) {
        if (c == null || !Auth.loggedIn()) return;
        final Context app = c.getApplicationContext();
        final java.util.List<Object> favs = Store.get(app).favorites();
        if (favs.isEmpty()) return;
        dirty = false;
        final int[] left = {0};
        for (Object o : favs) {
            if (!(o instanceof Models.Video)) continue;
            final long vid = ((Models.Video) o).id;
            if (vid <= 0) continue;
            left[0]++;
            Api.collectVideo(vid, true, new Api.Cb<org.json.JSONObject>() {
                @Override
                public void ok(org.json.JSONObject data) {
                    Auth.setSyncedAt(app, System.currentTimeMillis());
                }

                @Override
                public void err(String msg) {
                    dirty = true;
                }
            });
        }
    }

    public static JSONArray favoritesJson(Context c) {
        JSONArray arr = new JSONArray();
        try {
            List<Object> list = Store.get(c).favorites();
            for (Object o : list) {
                JSONObject j = new JSONObject();
                if (o instanceof Models.Video) {
                    Models.Video v = (Models.Video) o;
                    j.put("type", Store.T_VIDEO);
                    j.put("id", v.id);
                    j.put("title", v.title);
                    j.put("cover", v.cover);
                    j.put("score", v.score);
                    j.put("ep_count", v.epCount);
                    j.put("seen_ep", v.seenEp);
                } else if (o instanceof Models.Post) {
                    Models.Post p = (Models.Post) o;
                    j.put("type", Store.T_POST);
                    j.put("id", p.id);
                    j.put("title", p.title);
                    j.put("cover", p.cover);
                } else if (o instanceof Models.Topic) {
                    Models.Topic t = (Models.Topic) o;
                    j.put("type", Store.T_TOPIC);
                    j.put("id", t.id);
                    j.put("slug", t.slug);
                    j.put("title", t.title);
                    j.put("cover", t.cover);
                }
                arr.put(j);
            }
        } catch (Exception ignored) {
        }
        return arr;
    }

    public static JSONArray historyJson(Context c) {
        JSONArray arr = new JSONArray();
        try {
            for (Store.History h : Store.get(c).history()) {
                JSONObject j = new JSONObject();
                j.put("type", h.type);
                j.put("id", h.id);
                j.put("slug", h.slug);
                j.put("title", h.title);
                j.put("cover", h.cover);
                j.put("ep", h.ep);
                j.put("position", h.positionMs);
                j.put("duration", h.durationMs);
                j.put("ep_count", h.epCount);
                j.put("updated_at", h.updatedAt);
                arr.put(j);
            }
        } catch (Exception ignored) {
        }
        return arr;
    }
}
