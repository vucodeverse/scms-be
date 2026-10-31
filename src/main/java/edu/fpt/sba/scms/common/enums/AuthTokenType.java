package edu.fpt.sba.scms.common.enums;

/** Loại bản ghi trong bảng {@code auth_token} (IMPLEMENTATION.md §7.1 #43). */
public enum AuthTokenType {
    /** Refresh token (lưu hash SHA-256). */
    REFRESH,
    /** jti của access token đã đăng xuất — chặn sớm cho đến khi token tự hết hạn. */
    ACCESS_REVOKED
}
