package edu.fpt.sba.scms.common.util;

import java.util.regex.Pattern;

/** Regex dùng chung cho annotation và unit test (IMPLEMENTATION.md §2 — Validation). */
public final class ValidationUtil {

    /** Số điện thoại VN: 0 hoặc +84, đầu 3/5/7/8/9, tổng 10 số. */
    public static final Pattern VN_PHONE = Pattern.compile("^(0|\\+84)(3|5|7|8|9)\\d{8}$");
    /** PIN chấm công: đúng 4 chữ số. */
    public static final Pattern PIN = Pattern.compile("^\\d{4}$");
    /** Mật khẩu 8–72 ký tự, có chữ và số (Q-03 chốt 8). */
    public static final Pattern PASSWORD = Pattern.compile("^(?=.*[A-Za-z])(?=.*\\d).{8,72}$");

    private ValidationUtil() {
    }
}
