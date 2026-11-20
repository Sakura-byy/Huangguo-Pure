package com.huangguo.pure;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.LruCache;
import android.widget.ImageView;

import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.Request;
import okhttp3.Response;

/** 轻量封面加载器：内存 LRU + 四线程 + 采样解码，无第三方图片库 */
public final class ImageLoader {

    /** 解码完成回调，用于按真实宽高调整占位比例 */
    public interface OnSize {
        void onSize(int width, int height);
    }

    private static final LruCache<String, Bitmap> CACHE =
            new LruCache<String, Bitmap>(24 * 1024 * 1024) {
                @Override
                protected int sizeOf(String key, Bitmap value) {
                    return value.getByteCount();
                }
            };

    private static final ExecutorService POOL = Executors.newFixedThreadPool(4);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private ImageLoader() {
    }

    public static void clear() {
        CACHE.evictAll();
    }

    public static void load(final ImageView iv, final String url) {
        load(iv, url, null);
    }

    public static void load(final ImageView iv, final String url, final OnSize cb) {
        if (iv == null) return;
        if (url == null || url.isEmpty()) {
            iv.setImageResource(R.drawable.ph_poster);
            return;
        }
        iv.setTag(url);
        Bitmap hit = CACHE.get(url);
        if (hit != null) {
            iv.setImageBitmap(hit);
            if (cb != null) cb.onSize(hit.getWidth(), hit.getHeight());
            return;
        }
        iv.setImageResource(R.drawable.ph_poster);
        final int target = Math.max(320, iv.getResources().getDisplayMetrics().widthPixels / 2);
        POOL.execute(() -> {
            Bitmap bmp = fetch(url, target);
            if (bmp == null) return;
            CACHE.put(url, bmp);
            MAIN.post(() -> {
                if (url.equals(iv.getTag())) {
                    iv.setImageBitmap(bmp);
                    if (cb != null) cb.onSize(bmp.getWidth(), bmp.getHeight());
                }
            });
        });
    }

    private static Bitmap fetch(String url, int targetW) {
        CryptoImage.sync();
        try {
            Request req = new Request.Builder()
                    .url(url)
                    .header("User-Agent", Api.UA)
                    .header("Referer", Models.HOST + "/")
                    .build();
            try (Response resp = Api.CLIENT.newCall(req).execute()) {
                Log.d("ImgLoad", "code=" + resp.code() + " url=" + url);
                if (!resp.isSuccessful() || resp.body() == null) return null;
                InputStream in = resp.body().byteStream();
                BitmapFactory.Options bounds = new BitmapFactory.Options();
                bounds.inJustDecodeBounds = true;
                byte[] data = CryptoImage.decode(CryptoImage.readAll(in));
                BitmapFactory.decodeByteArray(data, 0, data.length, bounds);
                int sample = 1;
                while (bounds.outWidth / sample > targetW * 2) sample *= 2;
                BitmapFactory.Options opt = new BitmapFactory.Options();
                opt.inSampleSize = sample;
                opt.inPreferredConfig = Bitmap.Config.RGB_565;
                Bitmap out = BitmapFactory.decodeByteArray(data, 0, data.length, opt);
                Log.d("ImgLoad", "decoded=" + (out != null) + " " + bounds.outWidth + "x" + bounds.outHeight
                        + " bytes=" + data.length);
                return out;
            }
        } catch (Exception e) {
            Log.e("ImgLoad", "fail " + url + " :: " + e, e);
            return null;
        }
    }
}
