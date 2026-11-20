package com.huangguo.pure;

import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import okhttp3.Request;
import okhttp3.Response;

/**
 * HLS 下载器：master 清单自动选最高 BANDWIDTH 变体 → 拉分片清单 →
 * AES-128 解密 → 边下边写临时 TS（不再整段占内存）→
 * MediaExtractor / MediaMuxer 重封装成 .mp4（H.264/HEVC 视频 + AAC 音频，
 * 直接用任何播放器可播）。重封装失败或编码不受支持时回退保存原始 .ts。
 * 最终经 MediaSave 落到 Download/Huangguo/<剧名>/。
 */
public final class HlsDownloader {

    public interface Cb {
        void onProgress(int done, int total, String note);

        void onDone(String path);

        void onErr(String msg);
    }

    private static final Pattern KEY_LINE = Pattern.compile(
            "EXT-X-KEY:([^\\n]*)");
    private static final Pattern ATTR = Pattern.compile(
            "([A-Z0-9-]+)=(?:\"([^\"]*)\"|([^,\\s]*))");
    private static final Pattern VARIANT = Pattern.compile(
            "#EXT-X-STREAM-INF:([^\\n]*)\\n([^#\\n][^\\n]*)");

    private HlsDownloader() {
    }

    public static void download(final android.content.Context ctx, final String hlsUrl,
                                final String series, final String fileName, final Cb cb) {
        new Thread(() -> {
            try {
                String path = run(ctx, hlsUrl, series, fileName, cb);
                cb.onDone(path);
            } catch (Exception e) {
                cb.onErr(e.getClass().getSimpleName() + ": " + e.getMessage());
            }
        }, "hls-dl").start();
    }

    private static String run(android.content.Context ctx, String url, String series,
                              String fileName, Cb cb) throws Exception {
        // 基础文件名：调用方可能带 .ts / .mp4 后缀，统一剥掉再按产物补
        String base = fileName == null ? "video" : fileName.trim();
        int dot = base.lastIndexOf('.');
        if (dot > 0) base = base.substring(0, dot);
        if (base.isEmpty()) base = "video";

        String text = fetchText(url);
        // master 清单：选最高码率变体再拉一层
        Matcher vm = VARIANT.matcher(text);
        long bestBw = -1;
        String bestUri = null;
        while (vm.find()) {
            String attrs = vm.group(1);
            long bw = 0;
            Matcher am = ATTR.matcher(attrs);
            while (am.find()) {
                if (am.group(1).equals("BANDWIDTH")) {
                    try {
                        bw = Long.parseLong((am.group(2) != null ? am.group(2) : am.group(3)).trim());
                    } catch (Exception ignored) {
                    }
                }
            }
            if (bw > bestBw) {
                bestBw = bw;
                bestUri = vm.group(2).trim();
            }
        }
        if (bestUri != null) {
            URI base0 = new URI(url);
            String child = base0.resolve(bestUri).toString();
            text = fetchText(child);
            url = child;
        }
        if (!text.contains("#EXTINF")) throw new Exception("不是有效的分片清单");

        // 分片 + 解密参数
        List<String> segs = new ArrayList<>();
        String keyUri = null;
        String keyIv = null;
        long mediaSeq = 0;
        Matcher km = KEY_LINE.matcher(text);
        if (km.find()) {
            Matcher am = ATTR.matcher(km.group(1));
            while (am.find()) {
                String k = am.group(1);
                String v = am.group(2) != null ? am.group(2) : am.group(3);
                if (k.equals("URI")) keyUri = v;
                if (k.equals("IV")) keyIv = v;
            }
        }
        Matcher sm = Pattern.compile("#EXT-X-MEDIA-SEQUENCE:(\\d+)").matcher(text);
        if (sm.find()) mediaSeq = Long.parseLong(sm.group(1));

        URI baseUri = new URI(url);
        for (String line : text.split("\\n")) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            segs.add(baseUri.resolve(line).toString());
        }
        if (segs.isEmpty()) throw new Exception("清单里没有分片");

        SecretKeySpec key = null;
        byte[] iv = ivFrom(keyIv, mediaSeq);
        if (keyUri != null) {
            byte[] kb = fetchBytes(baseUri.resolve(keyUri).toString());
            key = new SecretKeySpec(kb, "AES");
        }

        cb.onProgress(0, segs.size(), "开始下载 " + segs.size() + " 个分片");
        File tmpTs = new File(ctx.getCacheDir(),
                "hg_dl_" + System.currentTimeMillis() + ".ts");
        Cipher cipher = null;
        if (key != null) {
            cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, key, new IvParameterSpec(iv));
        }
        try (FileOutputStream out = new FileOutputStream(tmpTs)) {
            for (int i = 0; i < segs.size(); i++) {
                byte[] seg = fetchBytes(segs.get(i));
                if (cipher != null) {
                    try {
                        seg = cipher.doFinal(seg);
                    } catch (Exception de) {
                        // 单片解密失败按原样写入，宁多勿缺
                    }
                }
                out.write(seg, 0, seg.length);
                if (i % 10 == 0 || i == segs.size() - 1) {
                    cb.onProgress(i + 1, segs.size(), null);
                }
            }
        }

        // TS → MP4 重封装：任何播放器直接可播；失败回退原始 TS
        cb.onProgress(segs.size(), segs.size(), "正在封装 MP4…");
        File tmpMp4 = new File(ctx.getCacheDir(),
                "hg_dl_" + System.currentTimeMillis() + ".mp4");
        try {
            if (remuxToMp4(tmpTs.getAbsolutePath(), tmpMp4.getAbsolutePath())) {
                try {
                    return MediaSave.saveDownloadFile(ctx, tmpMp4, series,
                            base + ".mp4", "video/mp4");
                } catch (Exception se) {
                    Log.w("HlsDownloader", "save mp4 failed, fallback ts: " + se);
                }
            }
            return MediaSave.saveDownloadFile(ctx, tmpTs, series,
                    base + ".ts", "video/mp2t");
        } finally {
            tmpTs.delete();
            tmpMp4.delete();
        }
    }

    // ---------------------------------------------------------------- 重封装

    /**
     * TS → MP4：提取视频轨与音频轨重新封装（点播 TS 常见编码：H.264/HEVC + AAC）。
     * 找不到轨、编码不受支持或封装中途异常都返回 false，由调用方回退保存 TS。
     */
    static boolean remuxToMp4(String tsPath, String mp4Path) {
        MediaExtractor ex = new MediaExtractor();
        MediaMuxer mux = null;
        try {
            ex.setDataSource(tsPath);
            int vTrack = -1, aTrack = -1;
            MediaFormat vFmt = null, aFmt = null;
            for (int i = 0; i < ex.getTrackCount(); i++) {
                MediaFormat f = ex.getTrackFormat(i);
                String mime = f.getString(MediaFormat.KEY_MIME);
                if (mime == null) continue;
                if (mime.startsWith("video/") && vTrack < 0) {
                    vTrack = i;
                    vFmt = f;
                } else if (mime.startsWith("audio/") && aTrack < 0) {
                    aTrack = i;
                    aFmt = f;
                }
            }
            if (vTrack < 0 && aTrack < 0) return false;
            // MP4 只装 H.264 / HEVC；其他视频编码保持 TS 兜底
            if (vFmt != null) {
                String m = vFmt.getString(MediaFormat.KEY_MIME);
                if (!"video/avc".equals(m) && !"video/hevc".equals(m)) return false;
            }
            // 音频不是 AAC 就只封装视频轨
            if (aFmt != null && !"audio/mp4a-latm"
                    .equals(aFmt.getString(MediaFormat.KEY_MIME))) {
                aFmt = null;
                aTrack = -1;
            }

            mux = new MediaMuxer(mp4Path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            int vIdx = vTrack >= 0 ? mux.addTrack(vFmt) : -1;
            int aIdx = aTrack >= 0 ? mux.addTrack(aFmt) : -1;
            mux.start();

            if (vTrack >= 0) {
                ex.selectTrack(vTrack);
                pump(ex, mux, vIdx);
            }
            if (aTrack >= 0) {
                if (vTrack >= 0) ex.unselectTrack(vTrack);
                ex.selectTrack(aTrack);
                ex.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
                pump(ex, mux, aIdx);
            }
            mux.stop();
            return true;
        } catch (Throwable t) {
            Log.w("HlsDownloader", "remux failed: " + t);
            return false;
        } finally {
            try {
                ex.release();
            } catch (Exception ignored) {
            }
            if (mux != null) {
                try {
                    mux.release();
                } catch (Exception ignored) {
                }
            }
        }
    }

    /** 把当前选中的轨道逐帧写入 muxer */
    private static void pump(MediaExtractor ex, MediaMuxer mux, int track) {
        ByteBuffer buf = ByteBuffer.allocate(2 << 20);
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        while (true) {
            if (ex.getSampleTrackIndex() < 0) break;
            int size = ex.readSampleData(buf, 0);
            if (size < 0) break;
            info.offset = 0;
            info.size = size;
            info.presentationTimeUs = ex.getSampleTime();
            info.flags = ex.getSampleFlags();
            mux.writeSampleData(track, buf, info);
            if (!ex.advance()) break;
        }
    }

    // ---------------------------------------------------------------- 工具

    private static byte[] ivFrom(String hex, long mediaSeq) {
        if (hex != null && !hex.isEmpty()) {
            String h = hex.startsWith("0x") || hex.startsWith("0X") ? hex.substring(2) : hex;
            byte[] out = new byte[16];
            for (int i = 0; i < 16; i++) {
                int start = i * 2;
                if (start + 2 > h.length()) break;
                out[i] = (byte) Integer.parseInt(h.substring(start, start + 2), 16);
            }
            return out;
        }
        byte[] out = new byte[16];
        for (int i = 15; i >= 0; i--) {
            out[i] = (byte) (mediaSeq & 0xFF);
            mediaSeq >>>= 8;
        }
        return out;
    }

    private static String fetchText(String url) throws Exception {
        return new String(fetchBytes(url), StandardCharsets.UTF_8);
    }

    private static byte[] fetchBytes(String url) throws Exception {
        Request req = new Request.Builder().url(url)
                .header("User-Agent", Api.UA).build();
        try (Response resp = Api.CLIENT.newCall(req).execute()) {
            if (!resp.isSuccessful() || resp.body() == null) {
                throw new Exception("HTTP " + (resp.code()) + " " + url);
            }
            InputStream in = resp.body().byteStream();
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return out.toByteArray();
        }
    }
}
