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
        /** 本地收藏记录：上次看到第几集（>0 且 epCount 更大时列表标「有更新」） */
        public int seenEp;
        /** 本地缓存记录：已缓存集数（详情页选集画已缓存点） */
        public int cachedCount;
        /** 站点发布时间（接口字段 created_at，形如 2026-08-07 19:16:47） */
        public String createdAt = "";

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

    /** 评分：my = 我打的星（0 未评），cnt = 评分人数，score = 均值 */
    public static class Rating {
        public int my;
        public int cnt;
        public double score;

        public String scoreLabel() {
            return score > 0 ? String.format("%.1f 分", score) : "暂无评分";
        }
    }

    public static Rating rating(JSONObject o) {
        Rating r = new Rating();
        if (o == null) return r;
        r.my = o.optInt("my", 0);
        r.cnt = o.optInt("rating_cnt", o.optInt("cnt", 0));
        String sc = o.optString("score", "");
        try {
            r.score = sc.isEmpty() ? o.optDouble("score", 0) : Double.parseDouble(sc);
        } catch (Exception e) {
            r.score = o.optDouble("score", 0);
        }
        return r;
    }

    /** 我的账号：昵称 + 计数 + 互动 id 列表（喜欢/收藏/关注） */
    public static class Me {
        public long id;
        public String nickname = "";
        public String avatar = "";
        public String sign = "";
        public String desc = "";
        public int likeCount;
        public int collectCount;
        public int historyCount;
        public int followingCount;
        public final List<Long> likeVideoIds = new ArrayList<>();
        public final List<Long> favoriteVideoIds = new ArrayList<>();
        public final List<Long> likePostIds = new ArrayList<>();
        public final List<Long> favoritePostIds = new ArrayList<>();
        public final List<String> likeEpisodes = new ArrayList<>();
    }

    public static Me me(JSONObject data) {
        Me m = new Me();
        if (data == null) return m;
        m.id = data.optLong("id", 0);
        m.nickname = data.optString("nickname", data.optString("username", ""));
        m.avatar = abs(data.optString("avatar", ""));
        m.sign = data.optString("sign", "");
        m.desc = data.optString("desc", "");
        JSONObject counts = data.optJSONObject("counts");
        if (counts != null) {
            m.likeCount = counts.optInt("like", 0);
            m.collectCount = counts.optInt("collect", 0);
            m.historyCount = counts.optInt("history", 0);
            m.followingCount = counts.optInt("following", 0);
        }
        JSONObject it = data.optJSONObject("interactions");
        if (it != null) {
            readIds(it.optJSONArray("like_video_ids"), m.likeVideoIds);
            readIds(it.optJSONArray("favorite_video_ids"), m.favoriteVideoIds);
            readIds(it.optJSONArray("like_post_ids"), m.likePostIds);
            readIds(it.optJSONArray("favorite_post_ids"), m.favoritePostIds);
            JSONArray eps = it.optJSONArray("like_video_episodes");
            if (eps != null) {
                for (int i = 0; i < eps.length(); i++) {
                    m.likeEpisodes.add(String.valueOf(eps.opt(i)));
                }
            }
        }
        return m;
    }

    private static void readIds(JSONArray arr, List<Long> out) {
        if (arr == null) return;
        for (int i = 0; i < arr.length(); i++) {
            long v = arr.optLong(i, 0);
            if (v > 0) out.add(v);
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
        /** 原始发布时间（publish_time / created_at，形如 2026-08-14 01:07:59） */
        public String createdAt = "";
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

    /** 云端历史条目（/api/users/me/history 一项） */
    public static class HistoryItem {
        public long videoId;
        public String title = "";
        public String cover = "";
        public int lastEp;
        public int totalEp;
        public String category = "";
        public String updatedAt = "";
        public String targetType = "video";
    }

    public static class HistoryPage {
        public final List<HistoryItem> items = new ArrayList<>();
        public int page = 1;
        public int pages = 1;
        public int total;
    }

    public static HistoryPage historyPage(JSONObject data) {
        HistoryPage hp = new HistoryPage();
        if (data == null) return hp;
        JSONArray arr = data.optJSONArray("items");
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) continue;
                HistoryItem h = new HistoryItem();
                h.videoId = o.optLong("video_id", o.optLong("id", 0));
                h.title = o.optString("title", "");
                h.cover = abs(o.optString("cover", ""));
                h.lastEp = o.optInt("last_ep", 1);
                h.totalEp = o.optInt("total_ep", o.optInt("episode_count", 0));
                h.category = o.optString("category_name", "");
                h.updatedAt = o.optString("updated_at", "");
                h.targetType = o.optString("target_type", "video");
                if (h.videoId > 0) hp.items.add(h);
            }
        }
        JSONObject pg = data.optJSONObject("pagination");
        if (pg != null) {
            hp.page = pg.optInt("page", 1);
            hp.pages = Math.max(1, pg.optInt("pages", 1));
            hp.total = pg.optInt("total", hp.items.size());
        }
        return hp;
    }

    /**
     * 解析博客分类页（服务端渲染）：
     * 卡片是 &lt;a class="hg-cate-feed__card" href="/archives/{id}/"&gt;…
     * 12 张/页，页数取分页链接里的最大值。
     */
    public static PostPage postPageFromHtml(String html, String route, int page) {
        PostPage pg = new PostPage();
        pg.page = page;
        if (html == null) return pg;
        java.util.regex.Matcher tm = java.util.regex.Pattern
                .compile("共\\s*([0-9,]+)\\s*篇").matcher(html);
        if (tm.find()) {
            try {
                pg.total = Integer.parseInt(tm.group(1).replace(",", ""));
            } catch (Exception ignored) {
            }
        }
        java.util.regex.Matcher pm = java.util.regex.Pattern
                .compile("/blog/" + java.util.regex.Pattern.quote(route) + "/(\\d+)/")
                .matcher(html);
        int maxPage = 1;
        while (pm.find()) {
            try {
                maxPage = Math.max(maxPage, Integer.parseInt(pm.group(1)));
            } catch (Exception ignored) {
            }
        }
        pg.pages = maxPage;

        java.util.regex.Matcher cm = java.util.regex.Pattern.compile(
                "<a class=\"hg-cate-feed__card\"[^>]*href=\"/archives/(\\d+)/\"(.*?)</a>",
                java.util.regex.Pattern.DOTALL).matcher(html);
        while (cm.find()) {
            Post p = new Post();
            try {
                p.id = Long.parseLong(cm.group(1));
            } catch (Exception ignored) {
                continue;
            }
            String block = cm.group(2);
            p.cover = abs(opt(block, "data-src=\"([^\"]+)\""));
            p.title = htmlText(opt(block, "hg-cate-feed__title\">(.*?)<"));
            p.authorName = htmlText(opt(block, "hg-cate-feed__author\">(.*?)<"));
            String date = htmlText(opt(block, "hg-cate-feed__date\">(.*?)<"));
            p.createdAt = date;
            p.time = date;
            p.desc = htmlText(opt(block, "hg-cate-feed__intro\">(.*?)<"));
            if (p.title.isEmpty()) continue;
            pg.items.add(p);
        }
        if (pg.total <= 0) pg.total = pg.items.size() * pg.pages;
        return pg;
    }

    private static String opt(String src, String regex) {
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile(regex, java.util.regex.Pattern.DOTALL).matcher(src);
        return m.find() ? m.group(1) : "";
    }

    /** 去标签 + 反转义（分类页里有 &amp; &quot; 之类） */
    private static String htmlText(String s) {
        if (s == null || s.isEmpty()) return "";
        String t = s.replaceAll("<[^>]+>", "");
        t = t.replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'")
                .replace("&lt;", "<").replace("&gt;", ">").replace("&nbsp;", " ");
        return t.trim();
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
        v.createdAt = o.optString("created_at", o.optString("publish_time", ""));
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
        if (t.isEmpty()) t = o.optString("created_at", "");
        p.createdAt = t;
        p.time = shortenTime(t);
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
