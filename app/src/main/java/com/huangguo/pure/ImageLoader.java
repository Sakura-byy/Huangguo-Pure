package com.huangguo.pure;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import okhttp3.Request;
import okhttp3.Response;

/**
 * 轻量封面加载器：内存 LRU + 磁盘 LRU + 四线程 + 采样解码，无第三方图片库。
 *
 * 磁盘缓存放在 cacheDir/img，存的是解密后的原始 JPEG/PNG 字节：
 * 同一张封面重启后不用再走一次网络（含 CryptoImage 解密），列表滚动更稳。
 * 同一 URL 的并发请求在锁上排队，先到的下载、后到的直接命中，不会重复下四份。
 */
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

    /** 每个 URL 一把锁：并发去重用，任务结束即回收 */
    private static final ConcurrentHashMap<String, Object> LOCKS = new ConcurrentHashMap<>();

    /** 磁盘缓存上限与目录 */
    private static final long DISK_MAX = 64L * 1024 * 1024;
    private static volatile File diskDir;
    private static final AtomicInteger diskWrites = new AtomicInteger();

    private ImageLoader() {
    }

    public static void clear() {
        CACHE.evictAll();
        File d = diskDir;
        if (d != null) deleteTree(d);
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
        final Context ctx = iv.getContext().getApplicationContext();
        final int target = Math.max(320, iv.getResources().getDisplayMetrics().widthPixels / 2);
        POOL.execute(() -> {
            Bitmap bmp = obtain(ctx, url, target);
            if (bmp == null) return;
            MAIN.post(() -> {
                if (url.equals(iv.getTag())) {
                    iv.setImageBitmap(bmp);
                    if (cb != null) cb.onSize(bmp.getWidth(), bmp.getHeight());
                }
            });
        });
    }

    /** 预热：只落内存/磁盘，不绑控件 */
    public static void prefetch(final Context c, final String url, final int targetW) {
        if (c == null || url == null || url.isEmpty()) return;
        if (CACHE.get(url) != null) return;
        final Context ctx = c.getApplicationContext();
        POOL.execute(() -> obtain(ctx, url, Math.max(320, targetW)));
    }

    // ------------------------------------------------------------------ 取图

    private static Bitmap obtain(Context ctx, String url, int targetW) {
        Object lock = lockFor(url);
        try {
            synchronized (lock) {
                Bitmap hit = CACHE.get(url);
                if (hit != null) return hit;

                File f = cacheFile(ctx, url, false);
                Bitmap disk = f == null ? null : decodeFile(f, targetW);
                if (disk != null) {
                    CACHE.put(url, disk);
                    if (f != null) f.setLastModified(System.currentTimeMillis());
                    return disk;
                }

                byte[] data = fetchBytes(url);
                if (data == null || data.length == 0) return null;
                Bitmap out = decodeBytes(data, targetW);
                if (out == null) return null;
                CACHE.put(url, out);
                writeDisk(ctx, url, data);
                return out;
            }
        } finally {
            LOCKS.remove(url, lock);
        }
    }

    private static Object lockFor(String url) {
        Object fresh = new Object();
        Object prev = LOCKS.putIfAbsent(url, fresh);
        return prev != null ? prev : fresh;
    }

    private static byte[] fetchBytes(String url) {
        CryptoImage.sync();
        try {
            Request req = new Request.Builder()
                    .url(url)
                    .header("User-Agent", Api.UA)
                    .header("Referer", Models.HOST + "/")
                    .build();
            try (Response resp = Api.CLIENT.newCall(req).execute()) {
                if (!resp.isSuccessful() || resp.body() == null) return null;
                InputStream in = resp.body().byteStream();
                return CryptoImage.decode(CryptoImage.readAll(in));
            }
        } catch (Exception e) {
            return null;
        }
    }

    private static Bitmap decodeBytes(byte[] data, int targetW) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(data, 0, data.length, bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;
        BitmapFactory.Options opt = new BitmapFactory.Options();
        opt.inSampleSize = sampleOf(bounds.outWidth, targetW);
        opt.inPreferredConfig = Bitmap.Config.RGB_565;
        return BitmapFactory.decodeByteArray(data, 0, data.length, opt);
    }

    private static Bitmap decodeFile(File f, int targetW) {
        if (f == null || !f.isFile() || f.length() == 0) return null;
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(f.getAbsolutePath(), bounds);
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;
            BitmapFactory.Options opt = new BitmapFactory.Options();
            opt.inSampleSize = sampleOf(bounds.outWidth, targetW);
            opt.inPreferredConfig = Bitmap.Config.RGB_565;
            return BitmapFactory.decodeFile(f.getAbsolutePath(), opt);
        } catch (Throwable t) {
            return null;
        }
    }

    /** 采样倍数：解出来的宽度不超过目标的两倍，既不糊也不吃内存 */
    private static int sampleOf(int width, int targetW) {
        int sample = 1;
        while (width / sample > targetW * 2) sample *= 2;
        return sample;
    }

    // ------------------------------------------------------------------ 磁盘

    private static File dir(Context ctx) {
        File d = diskDir;
        if (d != null) return d;
        synchronized (ImageLoader.class) {
            if (diskDir == null) {
                File img = new File(ctx.getCacheDir(), "img");
                if (!img.exists()) img.mkdirs();
                diskDir = img;
            }
            return diskDir;
        }
    }

    private static File cacheFile(Context ctx, String url, boolean create) {
        File d = dir(ctx);
        if (d == null) return null;
        if (create && !d.exists()) d.mkdirs();
        return new File(d, hash(url));
    }

    private static void writeDisk(Context ctx, String url, byte[] data) {
        File f = cacheFile(ctx, url, true);
        if (f == null) return;
        File tmp = new File(f.getPath() + ".tmp");
        try (FileOutputStream out = new FileOutputStream(tmp)) {
            out.write(data);
            out.flush();
        } catch (Throwable t) {
            tmp.delete();
            return;
        }
        if (!tmp.renameTo(f)) {
            tmp.delete();
            return;
        }
        // 每写若干张扫一次目录，按最后访问时间淘汰超限部分
        if ((diskWrites.incrementAndGet() & 31) == 0) trim(ctx);
    }

    /** 磁盘 LRU：超限就按 lastModified 从旧到新删 */
    private static void trim(Context ctx) {
        File d = dir(ctx);
        if (d == null) return;
        File[] all = d.listFiles();
        if (all == null || all.length == 0) return;
        long total = 0;
        for (File f : all) total += f.length();
        if (total <= DISK_MAX) return;
        java.util.Arrays.sort(all, (a, b) -> Long.compare(a.lastModified(), b.lastModified()));
        for (File f : all) {
            if (total <= DISK_MAX * 3 / 4) break;
            long len = f.length();
            if (f.delete()) total -= len;
        }
    }

    private static void deleteTree(File f) {
        File[] kids = f.listFiles();
        if (kids != null) {
            for (File k : kids) deleteTree(k);
        }
        f.delete();
    }

    private static String hash(String url) {
        try {
            byte[] md = MessageDigest.getInstance("MD5").digest(url.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder(md.length * 2);
            for (byte b : md) {
                int v = b & 0xFF;
                if (v < 16) sb.append('0');
                sb.append(Integer.toHexString(v));
            }
            return sb.toString();
        } catch (Exception e) {
            return Integer.toHexString(url.hashCode());
        }
    }
}
