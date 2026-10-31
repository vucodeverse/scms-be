package edu.fpt.sba.scms.service.impl;

import edu.fpt.sba.scms.common.enums.AuditAction;
import edu.fpt.sba.scms.common.audit.AuditLogService;
import edu.fpt.sba.scms.common.enums.ErrorCode;
import edu.fpt.sba.scms.common.util.PermissionCatalog;
import edu.fpt.sba.scms.common.exception.UnauthorizedException;
import edu.fpt.sba.scms.common.util.Messages;
import edu.fpt.sba.scms.dto.request.LoginRequest;
import edu.fpt.sba.scms.dto.response.AuthUserDto;
import edu.fpt.sba.scms.dto.response.CurrentUserResponse;
import edu.fpt.sba.scms.dto.response.LoginResponse;
import edu.fpt.sba.scms.dto.response.TokenResponse;
import edu.fpt.sba.scms.model.User;
import edu.fpt.sba.scms.repository.UserRepository;
import edu.fpt.sba.scms.security.Roles;
import edu.fpt.sba.scms.dto.UserPrincipal;
import edu.fpt.sba.scms.service.AuthService;
import edu.fpt.sba.scms.service.support.LoginAttemptService;
import edu.fpt.sba.scms.service.support.RefreshRateLimiter;
import edu.fpt.sba.scms.dto.TokenService;
import edu.fpt.sba.scms.dto.TokenService.IssuedToken;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * UC01. Quy tắc: IMPLEMENTATION.md §10.1, §8.4, §8.5.
 * <ul>
 *   <li>5 lần sai trong 15 phút -> khóa 15 phút ({@link LoginAttemptService}); sai -> INVALID_CREDENTIALS 401.</li>
 *   <li>roleHint chỉ gợi ý; landing tính từ role trong DB.</li>
 *   <li>refresh token lưu SHA-256; refresh xoay vòng token; logout thu hồi refresh token của user + jti access.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final LoginAttemptService loginAttemptService;
    private final RefreshRateLimiter refreshRateLimiter;
    private final AuditLogService auditLogService;

    @Override
    @Transactional
    public LoginResponse login(LoginRequest request) {
        String username = request.username().trim();
        loginAttemptService.checkNotLocked(username);

        // Kiểm tra mật khẩu trực tiếp (không qua AuthenticationManager) để phân biệt
        // user không tồn tại / sai mật khẩu / tài khoản INACTIVE -> cùng INVALID_CREDENTIALS.
        User user = userRepository.findWithRoleAndBranchByUsername(username)
                .filter(u -> u.getPasswordHash() != null)
                .filter(u -> passwordEncoder.matches(request.password(), u.getPasswordHash()))
                .filter(User::isActive)
                .orElseGet(() -> {
                    loginAttemptService.onFailure(username);
                    auditLogService.log(AuditAction.LOGIN_FAILED, "app_user", null, null,
                            Map.of("username", username));
                    throw new BadCredentialsException(Messages.MSG09);
                });
        loginAttemptService.onSuccess(username);

        AuthUserDto userDto = toAuthUser(user);
        IssuedToken access =
                tokenService.issueAccessToken(user.getId(), user.getUsername(), user.getFullName(),
                        user.roleCode(), user.getBranchId());

        // Refresh token cấp theo rememberMe: rememberMe=true -> hạn dài (JWT_REFRESH_TTL_DAYS);
        // false -> hạn ngắn "phiên" (app.jwt.session-refresh-ttl-hours). Cấu trúc token giống hệt,
        // chỉ khác hạn dùng, nên luồng refresh/rotation bên dưới không phải rẽ nhánh.
        Duration refreshTtl = Boolean.TRUE.equals(request.rememberMe())
                ? tokenService.refreshTtl()
                : tokenService.sessionRefreshTtl();
        IssuedToken refresh =
                tokenService.issueRefreshToken(user.getId(), user.getUsername(), user.getFullName(),
                        user.roleCode(), user.getBranchId(), refreshTtl);
        tokenService.saveRefreshToken(user.getId(), refresh);
        String refreshToken = refresh.token();

        auditLogService.log(AuditAction.LOGIN, "app_user", user.getId(), null,
                java.util.Map.of("role", user.roleCode()));
        log.info("Đăng nhập thành công: userId={}, role={}, roleHint={}", user.getId(), user.roleCode(),
                request.roleHint());

        return new LoginResponse(access.token(), tokenService.accessTtlSeconds(), refreshToken, userDto,
                landingFor(user.roleCode()));
    }

    @Override
    @Transactional
    public TokenResponse refresh(String refreshTokenFromBody, String refreshTokenFromCookie) {
        // 0) Chống refresh loop: đếm theo user đọc được từ token (token hỏng -> khoá "anonymous").
        //    Gọi đếm TRƯỚC khi kiểm chữ ký để request sai chữ ký spam cũng bị chặn.
        refreshRateLimiter.onAttempt(refreshRateKey(refreshTokenFromBody, refreshTokenFromCookie));

        String raw = (refreshTokenFromBody != null && !refreshTokenFromBody.isBlank())
                ? refreshTokenFromBody
                : refreshTokenFromCookie;
        if (raw == null || raw.isBlank()) {
            throw new UnauthorizedException(ErrorCode.AUTH_TOKEN_EXPIRED);
        }

        // 1) Token phải còn hạn và đúng chữ ký.
        if (!tokenService.isRefreshToken(raw)) {
            throw new UnauthorizedException(ErrorCode.AUTH_TOKEN_INVALID);
        }
        // 2) Token đã được cấp nhưng đã bị thu hồi -> dùng lại token cũ (replay). Rotation đã phát token mới
        //    cho client; bản cũ chỉ có thể lọt ra khi bị đánh cắp -> huỷ toàn bộ phiên của user đó.
        if (tokenService.isRevokedRefreshToken(raw)) {
            Integer owner = tokenService.revokedRefreshTokenOwner(raw);
            if (owner != null) {
                tokenService.revokeAllRefreshTokens(owner);
                auditLogService.log(AuditAction.LOGOUT, "app_user", owner, null,
                        java.util.Map.of("reason", "refresh_token_reuse"));
                log.warn("Phát hiện dùng lại refresh token đã thu hồi: userId={} -> huỷ toàn bộ phiên.", owner);
            }
            throw new UnauthorizedException(ErrorCode.AUTH_TOKEN_EXPIRED);
        }
        // 3) Token phải còn trong DB và chưa bị thu hồi (đăng xuất / đổi mật khẩu).
        Integer tokenUserId = tokenService.findUsableRefreshToken(raw);
        if (tokenUserId == null) {
            throw new UnauthorizedException(ErrorCode.AUTH_TOKEN_EXPIRED);
        }
        // 4) User phải còn ACTIVE và role hiện hành lấy từ DB (role đổi có hiệu lực sau lần refresh này).
        User user = userRepository.findWithRoleAndBranchById(tokenUserId)
                .filter(User::isActive)
                .orElseThrow(() -> new UnauthorizedException(ErrorCode.AUTH_TOKEN_EXPIRED));

        // 5) Rotation: thu hồi token cũ, phát cặp mới.
        tokenService.revokeRefreshToken(raw);
        IssuedToken access =
                tokenService.issueAccessToken(user.getId(), user.getUsername(), user.getFullName(),
                        user.roleCode(), user.getBranchId());
        // Xoay vòng giữ hạn: token mới sống đúng bằng phần đời còn lại của token cũ (không kéo dài phiên
        // rememberMe/session vô hạn qua mỗi lần refresh).
        IssuedToken refresh =
                tokenService.issueRefreshToken(user.getId(), user.getUsername(), user.getFullName(),
                        user.roleCode(), user.getBranchId(), tokenService.remainingTtl(raw));
        tokenService.saveRefreshToken(user.getId(), refresh);

        return new TokenResponse(access.token(), tokenService.accessTtlSeconds(), refresh.token(),
                toAuthUser(user));
    }

    /** Key đếm refresh: userId đọc từ token nếu được, ngược lại username, cuối cùng là "anonymous". */
    private String refreshRateKey(String fromBody, String fromCookie) {
        String raw = (fromBody != null && !fromBody.isBlank()) ? fromBody : fromCookie;
        if (raw == null || raw.isBlank()) {
            return "anonymous";
        }
        Integer owner = tokenService.revokedRefreshTokenOwner(raw);
        return owner == null ? "anonymous" : "user:" + owner;
    }

    @Override
    @Transactional
    public void logout(UserPrincipal principal) {
        tokenService.revokeAllRefreshTokens(principal.userId());
        tokenService.revokeAccessJti(principal.userId(), principal.jti(), accessExpiry(principal));
        auditLogService.log(AuditAction.LOGOUT, "app_user", principal.userId(), null, null);
        log.info("Đăng xuất: userId={}", principal.userId());
    }

    @Override
    @Transactional(readOnly = true)
    public CurrentUserResponse me(UserPrincipal principal) {
        User user = userRepository.findWithRoleAndBranchById(principal.userId())
                .orElseThrow(() -> new UnauthorizedException(ErrorCode.AUTH_REQUIRED));
        return new CurrentUserResponse(user.getId(), user.getFullName(), user.getUsername(), user.roleCode(),
                user.getBranchId(), branchName(user), PermissionCatalog.of(user.roleCode()));
    }

    private Instant accessExpiry(UserPrincipal principal) {
        // Không cần chính xác: chỉ cần chặn jti cho đến khi token tự hết hạn.
        return Instant.now().plus(java.time.Duration.ofSeconds(tokenService.accessTtlSeconds() + 60));
    }

    private static AuthUserDto toAuthUser(User user) {
        return new AuthUserDto(user.getId(), user.getFullName(), user.roleCode(), user.getBranchId(),
                branchName(user));
    }

    private static String branchName(User user) {
        return user.getBranch() == null ? null : user.getBranch().getName();
    }

    private static String landingFor(String roleCode) {
        if (Roles.COO.equals(roleCode)) {
            return "SCR-10";
        }
        if (Roles.BRANCH_MANAGER.equals(roleCode)) {
            return "SCR-07";
        }
        return "SCR-04";
    }
}
