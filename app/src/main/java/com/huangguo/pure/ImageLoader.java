package com.huangguo.pure;

import android.app.ActivityManager;
import android.content.ComponentCallbacks2;
import android.content.Context;
import android.content.res.Configuration;
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
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import okhttp3.Request;
import okhttp3.Response;

/**
 * 轻量封面加载器：内存 LRU + 磁盘 LRU + 四线程 + 采样解码，无第三方图片库。
 *
 * 磁盘缓存放在 cacheDir/img，存的是解密后的原始 JPEG/PNG 字节：
 * 同一张封面重启后不用再走一次网络（含 CryptoImage 解密），列表滚动更稳。
 * 同一 URL 的并发请求在锁上排队，先到的下载、后到的直接命中，不会重复下四份。
 *
 * 缓存策略对齐 Glide / Coil / Fresco 的做法（手写版，不引依赖）：
 *  1. 内存上限按设备的 largeMemoryClass 与 isLowRamDevice 动态算，不写死；
 *  2. 注册 ComponentCallbacks2：系统报内存紧张时先丢内存图，别让 LMK 杀进程；
 *  3. 磁盘 LRU 只在后台单线程里扫，45 秒内不重复扫 —— 扫描/删除不出现在取图路径上；
 *  4. 读命中不写 lastModified：一次读也摸一遍文件元数据就是一次 fsync，
 *     滚动时每张图都摸一遍，掉帧正是这么来的（Glide 同样只在写入时记时间）；
 *  5. 写入先落 .tmp 再 rename，进程被杀不会留半张图；启动时清残留 .tmp。
 */
public final class ImageLoader {

    /** 解码完成回调，用于按真实宽高调整占位比例 */
    public interface OnSize {
        void onSize(int width, int height);
    }

    /** 内存缓存：首次使用时按设备内存创建（24MB 兜底） */
    private static volatile LruCache<String, Bitmap> CACHE;

    private static final ExecutorService POOL = Executors.newFixedThreadPool(4);
    /** 磁盘清理单独一条低优先级线程：扫目录 + 删文件不能占着取图线程 */
    private static final ExecutorService TRIM_POOL = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "img-disk-trim");
        t.setPriority(Thread.MIN_PRIORITY);
        return t;
    });
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    /** 每个 URL 一把锁：并发去重用，任务结束即回收 */
    private static final ConcurrentHashMap<String, Object> LOCKS = new ConcurrentHashMap<>();

    /** 磁盘缓存上限：低内存机 40MB，其它 64MB（封面图几百 KB 一张，够用且不会长期膨胀） */
    private static volatile long diskMaxBytes = 64L * 1024 * 1024;
    private static volatile File diskDir;
    private static final AtomicInteger diskWrites = new AtomicInteger();

    /** 上一次磁盘清理时间（45s 内不重复扫目录） */
    private static final AtomicLong lastTrimAt = new AtomicLong(0L);
    private static final AtomicBoolean trimRunning = new AtomicBoolean(false);
    private static volatile boolean configured;

    private ImageLoader() {
    }

    // ------------------------------------------------------------------ 初始化

    /**
     * 按设备能力定缓存尺寸，并挂上系统内存压力回调。重复调用无副作用。
     * 由 {@link #load} / {@link #prefetch} 首次触发。
     */
    public static void init(Context c) {
        if (c == null || configured) return;
        synchronized (ImageLoader.class) {
            if (configured) return;
            configured = true;
            final Context app = c.getApplicationContext();
            int memCacheMb = 24;
            try {
                ActivityManager am = (ActivityManager) app.getSystemService(Context.ACTIVITY_SERVICE);
                boolean lowRam = am != null && am.isLowRamDevice();
                int largeMb = am == null ? 128 : am.getLargeMemoryClass();
                // 取可用堆的 1/8，夹在 16–48MB：小机器别撑爆，大机器也别囤着不放
                memCacheMb = lowRam ? 12 : Math.max(16, Math.min(48, (int) Math.round(largeMb / 8.0)));
                diskMaxBytes = (lowRam ? 40L : 64L) * 1024 * 1024;
            } catch (Throwable ignored) {
                // 拿不到 ActivityManager 就用默认值
            }
            final int memBytes = memCacheMb * 1024 * 1024;
            CACHE = new LruCache<String, Bitmap>(memBytes) {
                @Override
                protected int sizeOf(String key, Bitmap value) {
                    return value.getByteCount();
                }
            };
            try {
                // 系统内存紧张：先丢图（图随时能重下），别让进程被 LMK 干掉
                app.registerComponentCallbacks(new ComponentCallbacks2() {
                    @Override
                    public void onTrimMemory(int level) {
                        if (level >= TRIM_MEMORY_RUNNING_LOW) cache().evictAll();
                        if (level >= TRIM_MEMORY_COMPLETE) trimSoon(true);
                    }

                    @Override
                    public void onLowMemory() {
                        cache().evictAll();
                        trimSoon(true);
                    }

                    @Override
                    public void onConfigurationChanged(Configuration newConfig) {
                        // 不关心配置变化
                    }
                });
            } catch (Throwable ignored) {
                // 注册失败不影响取图
            }
            // 清掉上次异常退出留下的半张图（.tmp）
            TRIM_POOL.execute(() -> {
                File d = dir(app);
                File[] all = d.listFiles();
                if (all == null) return;
                for (File f : all) {
                    if (f.getName().endsWith(".tmp")) f.delete();
                }
            });
        }
    }

    private static LruCache<String, Bitmap> cache() {
        LruCache<String, Bitmap> c = CACHE;
        if (c == null) {
            synchronized (ImageLoader.class) {
                if (CACHE == null) {
                    CACHE = new LruCache<String, Bitmap>(24 * 1024 * 1024) {
                        @Override
                        protected int sizeOf(String key, Bitmap value) {
                            return value.getByteCount();
                        }
                    };
                }
                c = CACHE;
            }
        }
        return c;
    }

    /** 清空图片缓存（内存 + 磁盘），返回释放的磁盘字节数 */
    public static long clear() {
        cache().evictAll();
        File d = diskDir;
        if (d == null) return 0L;
        long freed = sizeOfTree(d);
        deleteTree(d);
        return freed;
    }

    /** 强制做一次磁盘清理（每日维护 / 系统低内存时调用） */
    public static void trimDiskNow() {
        if (diskDir == null) return;
        trimSoon(true);
    }

    /** 磁盘缓存当前占用（供设置页 / 清理弹窗显示） */
    public static long diskUsage() {
        File d = diskDir;
        return d == null ? 0L : sizeOfTree(d);
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
        init(iv.getContext());
        iv.setTag(url);
        Bitmap hit = cache().get(url);
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
        init(c);
        if (cache().get(url) != null) return;
        final Context ctx = c.getApplicationContext();
        POOL.execute(() -> obtain(ctx, url, Math.max(320, targetW)));
    }

    // ------------------------------------------------------------------ 取图

    private static Bitmap obtain(Context ctx, String url, int targetW) {
        Object lock = lockFor(url);
        try {
            synchronized (lock) {
                Bitmap hit = cache().get(url);
                if (hit != null) return hit;

                File f = cacheFile(ctx, url, false);
                Bitmap disk = f == null ? null : decodeFile(f, targetW);
                if (disk != null) {
                    // 读命中不回写 lastModified：省一次元数据写入（fsync）
                    cache().put(url, disk);
                    return disk;
                }

                byte[] data = fetchBytes(url);
                if (data == null || data.length == 0) return null;
                Bitmap out = decodeBytes(data, targetW);
                if (out == null) return null;
                cache().put(url, out);
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
        // 每写 24 张排一次后台清理（不阻塞取图线程）
        if ((diskWrites.incrementAndGet() & 23) == 0) trimSoon(false);
    }

    /**
     * 后台清理磁盘缓存：超过上限就按写入时间从旧到新删到 80%。
     * 45 秒最多扫一次，且同时只有一个任务 —— 首页快滑几十张图也不会反复扫目录。
     */
    private static void trimSoon(boolean force) {
        long now = System.currentTimeMillis();
        long last = lastTrimAt.get();
        if (!force && now - last < 45_000L) return;
        if (!lastTrimAt.compareAndSet(last, now)) return;
        if (!trimRunning.compareAndSet(false, true)) return;
        TRIM_POOL.execute(() -> {
            try {
                File d = diskDir;
                if (d == null) return;
                File[] all = d.listFiles();
                if (all == null || all.length == 0) return;
                long total = 0L;
                for (File f : all) total += f.length();
                long max = diskMaxBytes;
                if (total <= max) return;
                long target = max * 8 / 10;
                Arrays.sort(all, (a, b) -> Long.compare(a.lastModified(), b.lastModified()));
                for (File f : all) {
                    if (total <= target) break;
                    long len = f.length();
                    if (f.delete()) total -= len;
                }
            } finally {
                trimRunning.set(false);
            }
        });
    }

    private static long sizeOfTree(File f) {
        if (f == null || !f.exists()) return 0L;
        if (f.isFile()) return f.length();
        File[] kids = f.listFiles();
        if (kids == null) return 0L;
        long sum = 0L;
        for (File k : kids) sum += sizeOfTree(k);
        return sum;
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
