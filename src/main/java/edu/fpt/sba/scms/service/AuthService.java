package edu.fpt.sba.scms.service;

import edu.fpt.sba.scms.dto.request.LoginRequest;
import edu.fpt.sba.scms.dto.response.CurrentUserResponse;
import edu.fpt.sba.scms.dto.response.LoginResponse;
import edu.fpt.sba.scms.dto.response.TokenResponse;
import edu.fpt.sba.scms.security.principal.UserPrincipal;

/** UC01 — Login / Refresh / Logout / Me (M01). */
public interface AuthService {

    LoginResponse login(LoginRequest request);

    /**
     * Làm mới cặp token (rotation). Bị chặn ({@code ACCOUNT_LOCKED} + {@code Retry-After}) nếu cùng một user
     * gọi quá nhiều lần trong thời gian ngắn — chống "refresh loop" phía client.
     *
     * @param refreshTokenFromBody   token FE gửi trong body (có thể null)
     * @param refreshTokenFromCookie token đọc từ cookie httpOnly (có thể null)
     */
    TokenResponse refresh(String refreshTokenFromBody, String refreshTokenFromCookie);

    void logout(UserPrincipal principal);

    CurrentUserResponse me(UserPrincipal principal);
}
