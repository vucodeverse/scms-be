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
import edu.fpt.sba.scms.common.enums.Roles;
import edu.fpt.sba.scms.security.principal.UserPrincipal;
import edu.fpt.sba.scms.service.AuthService;
import edu.fpt.sba.scms.service.support.LoginAttemptService;
import edu.fpt.sba.scms.service.support.RefreshRateLimiter;
import edu.fpt.sba.scms.service.TokenService;
import edu.fpt.sba.scms.service.TokenService.IssuedToken;
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

    /**
     * Hash giả để luôn chạy BCrypt kể cả khi username không tồn tại — giữ thời gian phản hồi đồng đều,
     * tránh dò username qua timing (user enumeration). Không dùng để so mật khẩu thật.
     */
    private static final String DUMMY_PASSWORD_HASH =
            "$2a$12$C9Wq3WkO5r0Q9uJ0cU7YDe4mR0K6W8fL7jYQ3w1sF2bG5hN6oP7qK";

    @Override
    @Transactional
    public LoginResponse login(LoginRequest request) {
        String username = request.username().trim();
        loginAttemptService.checkNotLocked(username);

        // Không phân biệt user không tồn tại / sai mật khẩu / tài khoản INACTIVE -> cùng INVALID_CREDENTIALS.
        // Vẫn luôn chạy BCrypt (kể cả khi không có user) để thời gian phản hồi không lộ username tồn tại.
        User user = userRepository.findWithRoleAndBranchByUsername(username).orElse(null);
        String passwordHash = (user == null || user.getPasswordHash() == null)
                ? DUMMY_PASSWORD_HASH
                : user.getPasswordHash();
        boolean passwordOk = passwordEncoder.matches(request.password(), passwordHash);

        if (user == null || !passwordOk || !user.isActive()) {
            loginAttemptService.onFailure(username);
            auditLogService.log(AuditAction.LOGIN_FAILED, "app_user", null, null,
                    Map.of("username", username));
            throw new BadCredentialsException(Messages.MSG09);
        }
        loginAttemptService.onSuccess(username);

        AuthUserDto userDto = toAuthUser(user);
        IssuedToken access =
                tokenService.issueAccessToken(user.getId(), user.getUsername(), user.getFullName(),
                        user.roleCode(), user.getBranchId());

        // Refresh token chỉ cấp khi rememberMe=true (kéo dài phiên qua refresh token rotation).
        // rememberMe=false -> chỉ cấp access token, không có refresh token.
        String refreshToken = null;
        if (Boolean.TRUE.equals(request.rememberMe())) {
            IssuedToken refresh =
                    tokenService.issueRefreshToken(user.getId(), user.getUsername(), user.getFullName(),
                            user.roleCode(), user.getBranchId(), tokenService.refreshTtl());
            tokenService.saveRefreshToken(user.getId(), refresh);
            refreshToken = refresh.token();
        }

        auditLogService.log(AuditAction.LOGIN, "app_user", user.getId(), null,
                java.util.Map.of("role", user.roleCode()));
        log.info("Đăng nhập thành công: userId={}, role={}, roleHint={}", user.getId(), user.roleCode(),
                request.roleHint());

        return new LoginResponse(access.token(), tokenService.accessTtlSeconds(), refreshToken, userDto,
                Roles.landingFor(user.roleCode()));
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
            // Không có token ≠ token hết hạn: FE phải biết cần đăng nhập lại.
            throw new UnauthorizedException(ErrorCode.AUTH_REQUIRED);
        }

        // 1) Token phải còn hạn và đúng chữ ký.
        if (!tokenService.isRefreshToken(raw)) {
            throw new UnauthorizedException(ErrorCode.AUTH_TOKEN_INVALID);
        }
        // 2) Token đã được cấp nhưng đã bị thu hồi -> dùng lại token cũ (replay). Rotation đã phát token mới
        //    cho client; bản cũ chỉ có thể lọt ra khi bị đánh cắp -> huỷ toàn bộ phiên của user đó.
        if (tokenService.isRevokedRefreshToken(raw)) {
            Integer owner = tokenService.refreshTokenOwner(raw);
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
        Integer owner = tokenService.refreshTokenOwner(raw);
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
}
