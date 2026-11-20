package com.huangguo.pure;

import android.content.Context;
import android.text.TextUtils;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 极简 HTML 渲染器：只处理博客正文实际出现的 <p> <br> <img> <h2> <strong>。
 * 不引第三方富文本库，避免 WebView 与额外依赖。
 */
public final class HtmlRich {

    private static final Pattern IMG = Pattern.compile("<img[^>]*>", Pattern.CASE_INSENSITIVE);
    private static final Pattern ATTR_SRC = Pattern.compile(
            "data-src\\s*=\\s*[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);
    private static final Pattern ATTR_SRC2 = Pattern.compile(
            "\\bsrc\\s*=\\s*[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);

    private HtmlRich() {
    }

    public static void fill(LinearLayout parent, String html) {
        if (parent == null) return;
        Context c = parent.getContext();
        if (html == null || html.trim().isEmpty()) {
            TextView empty = Ui.text(c, "正文暂无内容", 13f, Ui.C_DIM);
            parent.addView(empty);
            return;
        }

        Matcher m = IMG.matcher(html);
        int cursor = 0;
        int lastEnd = 0;
        while (m.find()) {
            String text = html.substring(cursor, m.start());
            addBlocks(parent, text);
            cursor = m.end();
            lastEnd = m.end();
            String tag = m.group();
            String url = match(ATTR_SRC, tag);
            if (url == null) url = match(ATTR_SRC2, tag);
            addImage(parent, Models.abs(url == null ? "" : url));
        }
        if (lastEnd < html.length()) addBlocks(parent, html.substring(Math.min(lastEnd, html.length())));
    }

    private static String match(Pattern p, String s) {
        Matcher m = p.matcher(s);
        return m.find() ? m.group(1) : null;
    }

    private static void addBlocks(LinearLayout parent, String raw) {
        if (raw == null || raw.isEmpty()) return;

        String s = raw.replaceAll("(?i)<br\\s*/?>", "\n");
        s = s.replaceAll("(?i)</(p|div|h[1-6]|li|blockquote|section)>", "\n\n");
        s = s.replaceAll("(?i)<(p|div|h[1-6]|li|blockquote|section)[^>]*>", "");
        s = s.replaceAll("<[^>]+>", "");
        s = unescape(s);

        String[] parts = s.split("\n");
        StringBuilder buf = new StringBuilder();
        for (String part : parts) {
            String line = part.replace('\u00A0', ' ').trim();
            if (line.isEmpty()) {
                flush(parent, buf);
            } else {
                if (buf.length() > 0) buf.append('\n');
                buf.append(line);
            }
        }
        flush(parent, buf);
    }

    private static void flush(LinearLayout parent, StringBuilder buf) {
        if (buf.length() == 0) return;
        String text = buf.toString().trim();
        buf.setLength(0);
        if (TextUtils.isEmpty(text)) return;
        Context c = parent.getContext();
        TextView t = Ui.text(c, text, 15f, Ui.bodyText());
        t.setLineSpacing(Ui.dp(c, 7), 1f);
        t.setTextIsSelectable(true);
        LinearLayout.LayoutParams lp = Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = Ui.dp(c, 12);
        parent.addView(t, lp);
    }

    private static void addImage(final LinearLayout parent, String url) {
        if (url == null || url.isEmpty()) return;
        Context c = parent.getContext();
        final AspectImageView iv = new AspectImageView(c);
        iv.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
        iv.setRatio(0.68f);
        iv.setBackgroundColor(0x14FFFFFF);
        LinearLayout.LayoutParams lp = Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = Ui.dp(c, 12);
        parent.addView(iv, lp);
        ImageLoader.load(iv, url, (w, h) -> {
            if (w > 0 && h > 0) iv.setRatio((float) h / (float) w);
        });
        // 点击放大浏览，长按直接存相册
        iv.setClickable(true);
        iv.setOnClickListener(v -> ImageViewerActivity.open(c, url, "博客配图"));
        iv.setOnLongClickListener(v -> {
            android.widget.Toast.makeText(c, "正在保存图片…", android.widget.Toast.LENGTH_SHORT).show();
            new Thread(() -> {
                try {
                    android.graphics.Bitmap bmp = Api.fetchBitmap(url);
                    String path = MediaSave.saveImage(c, bmp,
                            "hg_" + System.currentTimeMillis() + ".jpg");
                    android.os.Handler h2 = new android.os.Handler(android.os.Looper.getMainLooper());
                    h2.post(() -> android.widget.Toast.makeText(
                            c.getApplicationContext(), "已保存：" + path,
                            android.widget.Toast.LENGTH_LONG).show());
                } catch (Exception e) {
                    new android.os.Handler(android.os.Looper.getMainLooper())
                            .post(() -> android.widget.Toast.makeText(
                                    c.getApplicationContext(),
                                    "保存失败：" + e.getMessage(),
                                    android.widget.Toast.LENGTH_LONG).show());
                }
            }).start();
            return true;
        });
    }

    public static String unescape(String s) {
        if (s == null) return "";
        return s.replace("&nbsp;", " ")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&apos;", "'")
                .replace("&amp;", "&");
    }
}
