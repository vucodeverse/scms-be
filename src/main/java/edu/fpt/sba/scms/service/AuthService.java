package edu.fpt.sba.scms.service;

import edu.fpt.sba.scms.dto.request.LoginRequest;
import edu.fpt.sba.scms.dto.request.PinLoginRequest;
import edu.fpt.sba.scms.dto.request.RefreshRequest;
import edu.fpt.sba.scms.dto.response.CashierPinOptionDto;
import edu.fpt.sba.scms.dto.response.CurrentUserResponse;
import edu.fpt.sba.scms.dto.response.LoginResponse;
import edu.fpt.sba.scms.dto.response.TokenResponse;
import edu.fpt.sba.scms.security.principal.UserPrincipal;

import java.util.List;

/** UC01 — Login / Refresh / Logout / Me (M01). */
public interface AuthService {

    /**
     * @param clientIp IP client đã chuẩn hoá — dùng cho rate-limit theo IP (chống DoS dò username và khoá oan
     *                 tài khoản người khác); không dùng để xác thực.
     */
    LoginResponse login(LoginRequest request, String clientIp);

    /** Danh sách Thu ngân đang hoạt động của chi nhánh để mở khóa bằng PIN tại POS / Kiểm két. */
    List<CashierPinOptionDto> listCashierPinOptions(Integer branchId);

    /** Đăng nhập Thu ngân bằng mã PIN 4-6 số tại quầy POS / Kiểm két. */
    LoginResponse pinLogin(PinLoginRequest request, String clientIp);

    /**
     * Làm mới cặp token (rotation). Bị chặn (429 TOO_MANY_REQUESTS + {@code Retry-After}) nếu cùng một người
     * gọi quá nhiều lần trong thời gian ngắn — chống "refresh loop" phía client.
     */
    TokenResponse refresh(RefreshRequest request);

    void logout(UserPrincipal principal);

    CurrentUserResponse me(UserPrincipal principal);
}
