package com.huangguo.pure;

import android.content.ContentValues;
import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

/** 图片 / 视频文件写入系统相册与 Download 目录（Android 10+ 走 MediaStore，无需权限） */
public final class MediaSave {

    private MediaSave() {
    }

    /** 图片 → 相册 Pictures/Huangguo/；返回可读的保存路径描述 */
    public static String saveImage(Context c, Bitmap bmp, String fileName) throws Exception {
        if (Build.VERSION.SDK_INT >= 29) {
            ContentValues cv = new ContentValues();
            cv.put(MediaStore.Images.Media.DISPLAY_NAME, fileName);
            cv.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
            cv.put(MediaStore.Images.Media.RELATIVE_PATH,
                    Environment.DIRECTORY_PICTURES + "/Huangguo");
            Uri uri = c.getContentResolver()
                    .insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv);
            if (uri == null) throw new Exception("相册插入失败");
            try (OutputStream os = c.getContentResolver().openOutputStream(uri)) {
                if (os == null) throw new Exception("输出流打开失败");
                bmp.compress(Bitmap.CompressFormat.JPEG, 92, os);
            }
            return "Pictures/Huangguo/" + fileName;
        }
        File dir = new File(c.getExternalFilesDir(Environment.DIRECTORY_PICTURES),
                "Huangguo");
        if (!dir.exists()) dir.mkdirs();
        File out = new File(dir, fileName);
        try (FileOutputStream os = new FileOutputStream(out)) {
            bmp.compress(Bitmap.CompressFormat.JPEG, 92, os);
        }
        return out.getAbsolutePath();
    }

    /** 视频 / 任意文件 → Download/Huangguo/<series>/；返回可读的保存路径描述 */
    public static String saveDownload(Context c, byte[] data, String series, String fileName)
            throws Exception {
        String rel = "Huangguo/" + sanitize(series);
        if (Build.VERSION.SDK_INT >= 29) {
            ContentValues cv = new ContentValues();
            cv.put(MediaStore.Downloads.DISPLAY_NAME, fileName);
            cv.put(MediaStore.Downloads.MIME_TYPE, "video/mp2t");
            cv.put(MediaStore.Downloads.RELATIVE_PATH,
                    Environment.DIRECTORY_DOWNLOADS + "/" + rel);
            Uri uri = c.getContentResolver()
                    .insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
            if (uri == null) throw new Exception("Download 插入失败");
            try (OutputStream os = c.getContentResolver().openOutputStream(uri)) {
                if (os == null) throw new Exception("输出流打开失败");
                os.write(data);
            }
            return "Download/" + rel + "/" + fileName;
        }
        File dir = new File(c.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), rel);
        if (!dir.exists()) dir.mkdirs();
        File out = new File(dir, fileName);
        try (FileOutputStream os = new FileOutputStream(out)) {
            os.write(data);
        }
        return out.getAbsolutePath();
    }

    /** 大文件流式落盘 → Download/Huangguo/<series>/（不把整个文件读进内存） */
    public static String saveDownloadFile(Context c, File src, String series,
                                          String fileName, String mime) throws Exception {
        String rel = "Huangguo/" + sanitize(series);
        if (Build.VERSION.SDK_INT >= 29) {
            ContentValues cv = new ContentValues();
            cv.put(MediaStore.Downloads.DISPLAY_NAME, fileName);
            cv.put(MediaStore.Downloads.MIME_TYPE, mime);
            cv.put(MediaStore.Downloads.RELATIVE_PATH,
                    Environment.DIRECTORY_DOWNLOADS + "/" + rel);
            Uri uri = c.getContentResolver()
                    .insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
            if (uri == null) throw new Exception("Download 插入失败");
            try (OutputStream os = c.getContentResolver().openOutputStream(uri);
                 InputStream in = new FileInputStream(src)) {
                if (os == null) throw new Exception("输出流打开失败");
                byte[] buf = new byte[64 * 1024];
                int n;
                while ((n = in.read(buf)) > 0) os.write(buf, 0, n);
            }
            return "Download/" + rel + "/" + fileName;
        }
        File dir = new File(c.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), rel);
        if (!dir.exists()) dir.mkdirs();
        File out = new File(dir, fileName);
        try (FileOutputStream os = new FileOutputStream(out);
             InputStream in = new FileInputStream(src)) {
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) os.write(buf, 0, n);
        }
        return out.getAbsolutePath();
    }

    /**
     * 找已下载到 Download/Huangguo/&lt;series&gt;/&lt;fileName&gt; 的文件，返回可直接播的 uri。
     *
     * 下载走 MediaStore（Android 10+），落盘后拿到的是一串相对路径描述，
     * 播放需要 content:// —— 用 RELATIVE_PATH + DISPLAY_NAME 查一次就有。
     */
    public static String findDownloaded(Context c, String series, String fileName) {
        String rel = Environment.DIRECTORY_DOWNLOADS + "/Huangguo/" + sanitize(series) + "/";
        if (Build.VERSION.SDK_INT >= 29) {
            try (android.database.Cursor cur = c.getContentResolver().query(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    new String[]{MediaStore.Downloads._ID},
                    MediaStore.Downloads.RELATIVE_PATH + "=? AND "
                            + MediaStore.Downloads.DISPLAY_NAME + "=?",
                    new String[]{rel, fileName}, null)) {
                if (cur != null && cur.moveToFirst()) {
                    long id = cur.getLong(0);
                    android.net.Uri u = android.content.ContentUris.withAppendedId(
                            MediaStore.Downloads.EXTERNAL_CONTENT_URI, id);
                    return u.toString();
                }
            } catch (Exception ignored) {
                // 查不到就走下面的文件路径探测
            }
        }
        File direct = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOWNLOADS), "Huangguo/" + sanitize(series) + "/" + fileName);
        if (direct.exists()) return direct.getAbsolutePath();
        File appDir = new File(c.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
                "Huangguo/" + sanitize(series) + "/" + fileName);
        if (appDir.exists()) return appDir.getAbsolutePath();
        return null;
    }

    /** 缓存视频的目录描述（设置页展示用） */
    public static String downloadRootLabel() {
        return "Download/Huangguo/<剧名>/第N集.mp4";
    }

    /** 文件名/目录名安全化 */
    public static String sanitize(String s) {
        if (s == null || s.trim().isEmpty()) return "未知剧集";
        String r = s.replaceAll("[\\\\/:*?\"<>|\\s\\.]+", "_").trim();
        if (r.length() > 60) r = r.substring(0, 60);
        return r.isEmpty() ? "未知剧集" : r;
    }
}
