package com.huangguo.pure;

import android.util.Log;

import java.io.InputStream;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import okhttp3.Request;
import okhttp3.Response;

/**
 * 图床加密协议：AES-128-CBC / NoPadding，密钥与 IV 为 UTF-8 16 字节字符串，
 * 与站点 /static/web/js/plugins/crypto-image-protocol.js 公布的一致。
 * 启动时尝试从当前线路重新拉取一次协议（官方轮换密钥时无需重新发版），失败用内置快照。
 */
public final class CryptoImage {

    private static final String PROTOCOL_PATH = "/static/web/js/plugins/crypto-image-protocol.js";
    private static final String DEFAULT_KEY = "f5d965df75336270";
    private static final String DEFAULT_IV = "97b60394abc2fbe1";

    private static volatile String key = DEFAULT_KEY;
    private static volatile String iv = DEFAULT_IV;
    private static volatile boolean synced = false;

    private CryptoImage() {
    }

    public static synchronized void sync() {
        if (synced) return;
        synced = true;
        try {
            Request req = new Request.Builder()
                    .url(Api.baseUrl() + PROTOCOL_PATH)
                    .header("User-Agent", Api.UA)
                    .build();
            try (Response resp = Api.CLIENT.newCall(req).execute()) {
                if (!resp.isSuccessful() || resp.body() == null) return;
                String js = resp.body().string();
                String k = grab(js, "decryptKey");
                String v = grab(js, "decryptIV");
                if (k != null && v != null
                        && k.getBytes("UTF-8").length == 16 && v.getBytes("UTF-8").length == 16) {
                    key = k;
                    iv = v;
                    Log.d("CryptoImage", "protocol synced");
                }
            }
        } catch (Exception e) {
            Log.w("CryptoImage", "protocol fetch failed, use built-in snapshot: " + e);
        }
    }

    private static String grab(String js, String field) {
        Matcher m = Pattern.compile("\"" + field + "\"\\s*:\\s*\"([^\"]+)\"").matcher(js);
        return m.find() ? m.group(1) : null;
    }

    /** 密文直接来自 CDN；已是标准图片头则原样返回 */
    public static byte[] decode(byte[] raw) {
        if (raw == null || raw.length < 32) return raw;
        if (isPlainImage(raw)) return raw;
        try {
            Cipher c = Cipher.getInstance("AES/CBC/NoPadding");
            SecretKeySpec ks = new SecretKeySpec(key.getBytes("UTF-8"), "AES");
            c.init(Cipher.DECRYPT_MODE, ks, new IvParameterSpec(iv.getBytes("UTF-8")));
            return unpad(c.doFinal(raw));
        } catch (Exception e) {
            Log.w("CryptoImage", "decrypt failed: " + e);
            return raw;
        }
    }

    private static boolean isPlainImage(byte[] b) {
        int b0 = b[0] & 0xFF, b1 = b[1] & 0xFF;
        if (b0 == 0xFF && b1 == 0xD8) return true;                       // JPEG
        if (b0 == 0x89 && b1 == 0x50) return true;                       // PNG
        if (b0 == 'G' && b1 == 'I') return true;                         // GIF
        if (b0 == 'R' && b1 == 'I') return true;                         // RIFF/WEBP
        return b0 == '<';                                                // 错误页
    }

    private static byte[] unpad(byte[] b) {
        int n = b.length;
        if (n == 0) return b;
        int pad = b[n - 1] & 0xFF;
        if (pad >= 1 && pad <= 16 && pad <= n) {
            for (int i = n - pad; i < n; i++) {
                if ((b[i] & 0xFF) != pad) return b;
            }
            return Arrays.copyOf(b, n - pad);
        }
        return b;
    }

    static byte[] readAll(InputStream in) throws Exception {
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream(80 * 1024);
        byte[] buf = new byte[16 * 1024];
        int n;
        while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
        return bos.toByteArray();
    }
}
