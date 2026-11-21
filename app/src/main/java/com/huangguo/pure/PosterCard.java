package com.huangguo.pure;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/** 竖版海报卡：封面 + 底部渐隐 + 玻璃信息条 */
public class PosterCard extends FrameLayout {

    public final AspectImageView cover;
    public final TextView title;
    public final TextView meta;
    public final TextView badge;
    /** 卡片底部的标签类型文字（异界 奇幻 穿越 / 游戏 恐怖 …），与海报底色拉开对比 */
    public final TextView tagLine;

    public PosterCard(Context c, int radiusDp) {
        super(c);
        int radius = Ui.dp(c, radiusDp);
        setClipToOutline(true);
        setBackground(new GlassDrawable(radius));
        setClickable(true);
        setFocusable(true);

        cover = new AspectImageView(c);
        cover.setRatio(1.32f);
        cover.setImageResource(R.drawable.ph_poster);
        addView(cover, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        View scrim = new View(c);
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP,
                new int[]{0xF205070C, 0xB305070C, 0x0005070C});
        scrim.setBackground(g);
        LayoutParams sp = new LayoutParams(LayoutParams.MATCH_PARENT, Ui.dp(c, 78));
        sp.gravity = Gravity.BOTTOM;
        addView(scrim, sp);

        // 评分角标：底色压成不透明深色遮罩（原来只有 35% 琥珀，压在亮海报上完全看不清）
        badge = Ui.bold(c, "", 10f, Ui.C_GOLD);
        badge.setGravity(Gravity.CENTER);
        badge.setPadding(Ui.dp(c, 7), Ui.dp(c, 3), Ui.dp(c, 7), Ui.dp(c, 3));
        badge.setBackground(Ui.stroke(0xC905070C, 0x59F5B942, Ui.dp(c, 9), 1));
        LayoutParams bp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        bp.gravity = Gravity.TOP | Gravity.END;
        bp.topMargin = Ui.dp(c, 8);
        bp.rightMargin = Ui.dp(c, 8);
        addView(badge, bp);

        LinearLayout info = Ui.col(c);
        info.setPadding(Ui.dp(c, 9), 0, Ui.dp(c, 9), Ui.dp(c, 9));
        LayoutParams ip = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        ip.gravity = Gravity.BOTTOM;
        addView(info, ip);

        // title/meta 压在深色海报 scrim 上，恒用白色不随主题变
        title = Ui.text(c, "", 12.5f, 0xFFFFFFFF);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        title.setMaxLines(2);
        title.setEllipsize(TextUtils.TruncateAt.END);
        info.addView(title);

        meta = Ui.text(c, "", 10.5f, 0xFFD6DCE6);
        meta.setMaxLines(1);
        meta.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams mp = Ui.lp(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        mp.topMargin = Ui.dp(c, 3);
        info.addView(meta, mp);

        // 标签类型：压在底部 scrim 上，恒用浅灰白（不随主题变，海报底是深色）
        tagLine = Ui.text(c, "", 10f, 0xFFAEB9C9);
        tagLine.setMaxLines(1);
        tagLine.setEllipsize(TextUtils.TruncateAt.END);
        tagLine.setIncludeFontPadding(false);
        LinearLayout.LayoutParams tgp = Ui.lp(LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT);
        tgp.topMargin = Ui.dp(c, 3);
        info.addView(tagLine, tgp);
    }

    public void bind(Models.Video v) {
        ImageLoader.load(cover, v.cover);
        title.setText(v.title);
        StringBuilder sb = new StringBuilder();
        // 评分只在右上角 badge 显示，这里不再重复（去掉左下角第二个评分）
        if (v.epCount > 0) sb.append(v.epText());
        meta.setText(sb.toString());

        // 标签类型文字：紧贴标题下方一行（异界 奇幻 穿越 …），没有标签就不占位
        String tags = v.tags == null ? "" : v.tags.trim();
        if (!tags.isEmpty()) {
            tagLine.setVisibility(VISIBLE);
            tagLine.setText(tags.replace(",", "  ").replace("、", "  "));
        } else {
            tagLine.setVisibility(GONE);
        }
        if (v.score > 0) {
            badge.setVisibility(VISIBLE);
            badge.setText(v.scoreText());
        } else {
            badge.setVisibility(GONE);
        }
    }
}
