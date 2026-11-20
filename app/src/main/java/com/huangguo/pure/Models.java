package com.huangguo.pure;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** 数据模型与 JSON 解析（字段对齐 huangguoai.com 接口原始结构） */
public final class Models {

    public static final String HOST = "https://huangguoai.com";
    public static final String IMG = "https://pic.qnlycc.cn";

    private Models() {
    }

    public static class Video {
        public long id;
        public String title = "";
        public String cover = "";
        public double score;
        public int epCount;
        public long hot;
        public String tags = "";
        public String desc = "";
        public String cateName = "";
        public int rank;
        public String metricLabel = "";
        public long metricValue;
        /** 是否完结：false 时显示「更新至 N 集」，true 显示「全 N 集」 */
        public boolean finished;

        public String scoreText() {
            return score > 0 ? String.format("%.1f", score) : "";
        }

        /** 带单位的评分，详情页这类整句展示用 */
        public String scoreLabel() {
            return score > 0 ? String.format("%.1f 分", score) : "";
        }

        /** 网站口径：连载中 = 更新至 N 集，完结 = 全 N 集 */
        public String epText() {
            if (epCount <= 0) return "";
            return finished ? ("全 " + epCount + " 集") : ("更新至 " + epCount + " 集");
        }
    }

    public static class Episode {
        public int ep = 1;
        public String title = "";
        public int duration;
    }

    public static class Detail {
        public Video info = new Video();
        public final List<Episode> episodes = new ArrayList<>();
    }

    public static class Page {
        public final List<Video> items = new ArrayList<>();
        public int page = 1;
        public int pages = 1;
        public int total;
    }

    public static class Home {
        public final List<Video> banners = new ArrayList<>();
        public final List<Video> latest = new ArrayList<>();
        public final List<Video> featured = new ArrayList<>();
    }

    public static class Comment {
        public long id;
        public String nickname = "";
        public String content = "";
        public String time = "";
        public String region = "";
        public String avatarColor = "#6E8494";
        public String avatar = "";
        public int likeCount;
        public int replyCount;
    }

    public static class Category {
        public long id;
        public String name = "";
        public String route = "";
    }

    /** 专题（专辑） */
    public static class Topic {
        public long id;
        public String slug = "";
        public String title = "";
        public String cover = "";
        public String intro = "";
        public int videoCount;
    }

    /** 博客文章 */
    public static class Post {
        public long id;
        public String title = "";
        public String cover = "";
        public String desc = "";
        public String content = "";
        public String time = "";
        public String authorName = "";
        public String authorAvatar = "";
        public long hot;
        public String tags = "";
        public int commentCount;
        public int likeCount;
        public int collectCount;
        public String tagLinks = "";
        /** 帖子自带的视频直链（老接口字段，有就直接用） */
        public String videoUrl = "";
    }

    /** 帖子正文视频：/api/posts/{id}/playback 返回的 videos[] 一项 */
    public static class PostVideo {
        public int index;
        public String url = "";
    }

    public static class PostPage {        public final List<Post> items = new ArrayList<>();
        public int page = 1;
        public int pages = 1;
        public int total;
    }

    public static class TopicDetail {
        public Topic topic = new Topic();
        public final List<Video> videos = new ArrayList<>();
        public final List<Topic> related = new ArrayList<>();
        public int pages = 1;
    }

    /** 相对路径补全为绝对地址 */
    public static String abs(String url) {
        if (url == null) return "";
        if (url.startsWith("http://") || url.startsWith("https://")) return url;
        if (url.startsWith("//")) return "https:" + url;
        if (url.startsWith("/")) return IMG + url;
        return url;
    }

    private static String joinTags(JSONArray arr) {
        if (arr == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < arr.length(); i++) {
            String s = arr.optString(i, "");
            if (s.isEmpty()) continue;
            if (sb.length() > 0) sb.append(" · ");
            sb.append(s);
        }
        return sb.toString();
    }

    public static Video video(JSONObject o) {
        Video v = new Video();
        if (o == null) return v;
        long vid = o.optLong("video_id", 0);
        v.id = vid > 0 ? vid : o.optLong("id", 0);
        v.title = o.optString("title", "");
        String cover = o.optString("cover", "");
        if (cover.isEmpty()) cover = o.optString("image", "");
        v.cover = abs(cover);
        v.score = o.optDouble("score", 0);
        v.epCount = o.optInt("episode_count", o.optInt("total_episodes", o.optInt("episodeCount", 0)));
        v.hot = o.optLong("hot", o.optLong("metric_value", 0));
        v.tags = joinTags(o.optJSONArray("tags"));
        v.desc = o.optString("description", o.optString("desc", ""));
        v.cateName = o.optString("cate_name", "");
        v.rank = o.optInt("rank", 0);
        v.metricLabel = o.optString("metric_label", "");
        v.metricValue = o.optLong("metric_value", 0);
        v.finished = o.optBoolean("is_finished", false);
        return v;
    }

    public static List<Video> videos(JSONArray arr) {
        List<Video> out = new ArrayList<>();
        if (arr == null) return out;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o != null) out.add(video(o));
        }
        return out;
    }

    public static Page page(JSONObject data) {
        Page p = new Page();
        if (data == null) return p;
        JSONArray items = data.optJSONArray("items");
        p.items.addAll(videos(items));
        JSONObject pg = data.optJSONObject("pagination");
        if (pg != null) {
            p.page = pg.optInt("page", 1);
            p.pages = pg.optInt("pages", 1);
            p.total = pg.optInt("total", 0);
        }
        return p;
    }

    public static Home home(JSONObject data) {
        Home h = new Home();
        if (data == null) return h;
        h.banners.addAll(videos(data.optJSONArray("banners")));
        h.latest.addAll(videos(data.optJSONArray("latest")));
        h.featured.addAll(videos(data.optJSONArray("featured")));
        return h;
    }

    public static Detail detail(JSONObject data) {
        Detail d = new Detail();
        d.info = video(data);
        if (data != null) {
            d.info.desc = data.optString("description", "");
            JSONArray eps = data.optJSONArray("episodes");
            if (eps != null) {
                for (int i = 0; i < eps.length(); i++) {
                    JSONObject o = eps.optJSONObject(i);
                    if (o == null) continue;
                    Episode e = new Episode();
                    e.ep = o.optInt("ep_num", i + 1);
                    e.title = o.optString("title", "第" + e.ep + "集");
                    e.duration = o.optInt("duration", 0);
                    d.episodes.add(e);
                }
            }
            if (d.episodes.isEmpty()) {
                int n = Math.max(1, d.info.epCount);
                for (int i = 1; i <= n; i++) {
                    Episode e = new Episode();
                    e.ep = i;
                    e.title = "第" + i + "集";
                    d.episodes.add(e);
                }
            }
        }
        return d;
    }

    public static List<String> words(JSONArray arr) {
        List<String> out = new ArrayList<>();
        if (arr == null) return out;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null) continue;
            String w = o.optString("word", o.optString("name", ""));
            if (!w.isEmpty()) out.add(w);
        }
        return out;
    }

    public static List<Comment> comments(JSONArray arr) {
        List<Comment> out = new ArrayList<>();
        if (arr == null) return out;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null) continue;
            Comment cm = new Comment();
            cm.id = o.optLong("id", 0);
            cm.nickname = o.optString("nickname", o.optString("user_name", "匿名"));
            cm.content = o.optString("content", o.optString("text", ""));
            cm.time = o.optString("time", o.optString("created_at", ""));
            cm.region = o.optString("ip_region", "");
            cm.avatarColor = o.optString("avatar_color", "#6E8494");
            cm.avatar = abs(o.optString("avatar", ""));
            cm.likeCount = o.optInt("like_count", 0);
            cm.replyCount = o.optInt("reply_count", 0);
            out.add(cm);
        }
        return out;
    }

    public static List<Category> categories(JSONArray arr) {
        List<Category> out = new ArrayList<>();
        if (arr == null) return out;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null) continue;
            Category c = new Category();
            c.id = o.optLong("id", 0);
            c.name = o.optString("name", "");
            c.route = o.optString("route", "");
            out.add(c);
        }
        return out;
    }

    // ------------------------------------------------------------ 专题 / 博客

    public static Topic topic(JSONObject o) {
        Topic t = new Topic();
        if (o == null) return t;
        t.id = o.optLong("id", 0);
        t.slug = o.optString("slug", "");
        t.title = o.optString("title", "");
        t.cover = abs(o.optString("cover", ""));
        t.intro = o.optString("intro", "");
        t.videoCount = o.optInt("video_count", 0);
        return t;
    }

    public static List<Topic> topics(JSONArray arr) {
        List<Topic> out = new ArrayList<>();
        if (arr == null) return out;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o != null) out.add(topic(o));
        }
        return out;
    }

    public static TopicDetail topicDetail(JSONObject data) {
        TopicDetail d = new TopicDetail();
        if (data == null) return d;
        d.topic = topic(data.optJSONObject("topic"));
        JSONObject vids = data.optJSONObject("videos");
        if (vids != null) {
            d.videos.addAll(videos(vids.optJSONArray("items")));
            JSONObject pg = vids.optJSONObject("pagination");
            if (pg != null) d.pages = Math.max(1, pg.optInt("pages", 1));
        }
        d.related.addAll(topics(data.optJSONArray("related")));
        return d;
    }

    public static Post post(JSONObject o) {
        Post p = new Post();
        if (o == null) return p;
        p.id = o.optLong("id", 0);
        p.title = o.optString("title", "");
        p.cover = abs(o.optString("cover", ""));
        p.desc = o.optString("desc", "");
        p.content = o.optString("content", "");
        p.videoUrl = abs(o.optString("video_url", ""));
        String t = o.optString("publish_time", "");
        p.time = shortenTime(t.isEmpty() ? o.optString("created_at", "") : t);
        p.hot = o.optLong("hot", 0);
        p.tags = joinTags(o.optJSONArray("tags"));
        JSONObject author = o.optJSONObject("author");
        if (author != null) {
            p.authorName = author.optString("name", "");
            p.authorAvatar = abs(author.optString("avatar", ""));
        }
        JSONObject counts = o.optJSONObject("counts");
        if (counts != null) {
            p.commentCount = counts.optInt("comment", 0);
            p.likeCount = counts.optInt("like", 0);
            p.collectCount = counts.optInt("collect", 0);
            long h = counts.optLong("hot", 0);
            if (h > 0) p.hot = h;
        }
        return p;
    }

    /** "2026-08-14 01:07:59" → "2026-08-14" */
    public static String shortenTime(String s) {
        if (s == null) return "";
        int i = s.indexOf(' ');
        return i > 0 ? s.substring(0, i) : s;
    }

    public static PostPage postPage(JSONObject data) {
        PostPage p = new PostPage();
        if (data == null) return p;
        JSONArray items = data.optJSONArray("items");
        if (items != null) {
            for (int i = 0; i < items.length(); i++) {
                JSONObject o = items.optJSONObject(i);
                if (o != null) p.items.add(post(o));
            }
        }
        JSONObject pg = data.optJSONObject("pagination");
        if (pg != null) {
            p.page = pg.optInt("page", 1);
            p.pages = Math.max(1, pg.optInt("pages", 1));
            p.total = pg.optInt("total", 0);
        }
        return p;
    }
}
