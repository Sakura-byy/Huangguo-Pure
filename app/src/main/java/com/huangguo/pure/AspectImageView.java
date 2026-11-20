package com.huangguo.pure;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.ImageView;

/** 固定宽高比的 ImageView（默认 3:4 竖版海报） */
public class AspectImageView extends ImageView {

    private float ratio = 1.3333f; // height / width

    public AspectImageView(Context c) {
        super(c);
        setScaleType(ScaleType.CENTER_CROP);
    }

    public AspectImageView(Context c, AttributeSet a) {
        super(c, a);
        setScaleType(ScaleType.CENTER_CROP);
    }

    public void setRatio(float heightOverWidth) {
        this.ratio = heightOverWidth;
        requestLayout();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        int w = getMeasuredWidth();
        if (w > 0) {
            setMeasuredDimension(w, Math.round(w * ratio));
        }
    }
}
