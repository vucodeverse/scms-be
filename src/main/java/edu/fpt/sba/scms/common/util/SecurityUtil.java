package edu.fpt.sba.scms.common.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Helper hash/so sánh constant-time. Không log dữ liệu đầu vào. */
public final class SecurityUtil {

    private SecurityUtil() {
    }

    /** SHA-256 -> hex thường (64 ký tự). Dùng cho refresh token, pin_lookup_hash. */
    public static String sha256Hex(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    public static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return a == b;
        }
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }

    /** Mask số tài khoản / mã: giữ 4 ký tự cuối (IMPLEMENTATION.md §8.9). */
    public static String maskTail(String value) {
        if (value == null || value.length() <= 4) {
            return "******";
        }
        return "******" + value.substring(value.length() - 4);
    }
}
