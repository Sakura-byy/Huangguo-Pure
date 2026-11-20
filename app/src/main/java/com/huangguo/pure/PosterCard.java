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

        badge = Ui.bold(c, "", 10f, Ui.C_AMBER);
        badge.setGravity(Gravity.CENTER);
        badge.setPadding(Ui.dp(c, 7), Ui.dp(c, 3), Ui.dp(c, 7), Ui.dp(c, 3));
        badge.setBackground(Ui.stroke(0x9905070C, 0x59F5B942, Ui.dp(c, 9), 1));
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
    }

    public void bind(Models.Video v) {
        ImageLoader.load(cover, v.cover);
        title.setText(v.title);
        StringBuilder sb = new StringBuilder();
        // 评分只在右上角 badge 显示，这里不再重复（去掉左下角第二个评分）
        if (v.epCount > 0) {
            sb.append(v.epText());
        }
        if (sb.length() == 0 && v.tags != null && !v.tags.isEmpty()) sb.append(v.tags);
        meta.setText(sb.toString());
        if (v.score > 0) {
            badge.setVisibility(VISIBLE);
            badge.setText(v.scoreText());
        } else {
            badge.setVisibility(GONE);
        }
    }
}
