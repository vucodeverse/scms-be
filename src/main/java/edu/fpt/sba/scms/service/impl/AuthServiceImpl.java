package edu.fpt.sba.scms.service.impl;

import edu.fpt.sba.scms.common.audit.AuditLogService;
import edu.fpt.sba.scms.common.enums.AuditAction;
import edu.fpt.sba.scms.common.enums.BranchStatus;
import edu.fpt.sba.scms.common.enums.ErrorCode;
import edu.fpt.sba.scms.common.enums.Roles;
import edu.fpt.sba.scms.common.enums.ShiftHandoverStatus;
import edu.fpt.sba.scms.common.enums.ShiftStatus;
import edu.fpt.sba.scms.common.enums.UserStatus;
import edu.fpt.sba.scms.common.exception.ForbiddenException;
import edu.fpt.sba.scms.common.exception.InvalidFieldValueException;
import edu.fpt.sba.scms.common.exception.InvalidPinException;
import edu.fpt.sba.scms.common.exception.NotFoundException;
import edu.fpt.sba.scms.common.exception.PinLockedException;
import edu.fpt.sba.scms.common.exception.UnauthorizedException;
import edu.fpt.sba.scms.common.util.DateTimeUtil;
import edu.fpt.sba.scms.common.util.Messages;
import edu.fpt.sba.scms.domain.shift.ShiftSchedulePolicy;
import edu.fpt.sba.scms.dto.request.LoginRequest;
import edu.fpt.sba.scms.dto.request.PinLoginRequest;
import edu.fpt.sba.scms.dto.request.RefreshRequest;
import edu.fpt.sba.scms.dto.response.AuthUserDto;
import edu.fpt.sba.scms.dto.response.CashierPinOptionDto;
import edu.fpt.sba.scms.dto.response.CurrentUserResponse;
import edu.fpt.sba.scms.dto.response.KioskBranchDto;
import edu.fpt.sba.scms.dto.response.LoginResponse;
import edu.fpt.sba.scms.dto.response.TokenResponse;
import edu.fpt.sba.scms.model.AuthToken;
import edu.fpt.sba.scms.model.Branch;
import edu.fpt.sba.scms.model.Shift;
import edu.fpt.sba.scms.model.ShiftAssignment;
import edu.fpt.sba.scms.model.User;
import edu.fpt.sba.scms.repository.BranchRepository;
import edu.fpt.sba.scms.repository.ShiftAssignmentRepository;
import edu.fpt.sba.scms.repository.ShiftHandoverRepository;
import edu.fpt.sba.scms.repository.ShiftRepository;
import edu.fpt.sba.scms.repository.UserRepository;
import edu.fpt.sba.scms.security.PermissionCatalog;
import edu.fpt.sba.scms.security.jwt.JwtTokenProvider;
import edu.fpt.sba.scms.security.jwt.JwtTokenProvider.IssuedToken;
import edu.fpt.sba.scms.security.principal.UserPrincipal;
import edu.fpt.sba.scms.service.AuthService;
import edu.fpt.sba.scms.service.support.LoginAttemptService;
import edu.fpt.sba.scms.service.support.LoginIpRateLimiter;
import edu.fpt.sba.scms.service.support.PinHasher;
import edu.fpt.sba.scms.service.support.PinLockoutService;
import edu.fpt.sba.scms.service.support.RefreshRateLimiter;
import edu.fpt.sba.scms.service.support.RefreshTokenStore;
import edu.fpt.sba.scms.service.support.UserDirectory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * UC01. Quy tắc: IMPLEMENTATION.md §10.1, §8.4, §8.5.
 * <ul>
 *   <li>5 lần sai trong 15 phút -> khóa 15 phút ({@link LoginAttemptService}); sai -> INVALID_CREDENTIALS 401.</li>
 *   <li>Rate-limit theo IP ({@link LoginIpRateLimiter}) chặn DoS dò username và khoá oan tài khoản người khác.</li>
 *   <li>roleHint chỉ gợi ý; landing tính từ role trong DB.</li>
 *   <li>refresh token lưu SHA-256; refresh xoay vòng token; logout thu hồi refresh token của user + jti access.</li>
 * </ul>
 * Mỗi method public là một use case ngắn: guard (sai thì ném) trước, rồi mới đến phần "happy path".
 */
@Slf4j
@Service
public class AuthServiceImpl implements AuthService {

    /** Lệch đồng hồ giữa các instance khi chặn jti access token — chỉ cần chặn tới khi token tự hết hạn. */
    private static final long CLOCK_SKEW_SECONDS = 60;

    /** Tên entity (bảng) dùng trong audit log. */
    private static final String ENTITY_USER = "app_user";

    private final UserRepository userRepository;
    private final BranchRepository branchRepository;
    private final ShiftRepository shiftRepository;
    private final ShiftAssignmentRepository shiftAssignmentRepository;
    private final ShiftHandoverRepository shiftHandoverRepository;
    private final PasswordEncoder passwordEncoder;
    private final PinHasher pinHasher;
    private final PinLockoutService pinLockoutService;
    private final JwtTokenProvider tokenProvider;
    private final RefreshTokenStore refreshTokenStore;
    private final LoginAttemptService loginAttemptService;
    private final LoginIpRateLimiter loginIpRateLimiter;
    private final RefreshRateLimiter refreshRateLimiter;
    private final AuditLogService auditLogService;

    /**
     * Hash giả để luôn chạy BCrypt kể cả khi username không tồn tại — giữ thời gian phản hồi đồng đều,
     * tránh dò username qua timing (user enumeration). Không dùng để so mật khẩu thật.
     * <p>
     * Sinh lúc khởi tạo bằng chính {@link PasswordEncoder} (không hardcode chuỗi): chuỗi hash BCrypt sai định dạng
     * sẽ khiến {@code matches()} ném IllegalArgumentException thay vì trả false — vừa lỗi 500 vừa làm lộ username.
     */
    private final String dummyPasswordHash;

    public AuthServiceImpl(UserRepository userRepository,
                           BranchRepository branchRepository,
                           ShiftRepository shiftRepository,
                           ShiftAssignmentRepository shiftAssignmentRepository,
                           ShiftHandoverRepository shiftHandoverRepository,
                           PasswordEncoder passwordEncoder,
                           PinHasher pinHasher,
                           PinLockoutService pinLockoutService,
                           JwtTokenProvider tokenProvider,
                           RefreshTokenStore refreshTokenStore,
                           LoginAttemptService loginAttemptService,
                           LoginIpRateLimiter loginIpRateLimiter,
                           RefreshRateLimiter refreshRateLimiter,
                           AuditLogService auditLogService) {
        this.userRepository = userRepository;
        this.branchRepository = branchRepository;
        this.shiftRepository = shiftRepository;
        this.shiftAssignmentRepository = shiftAssignmentRepository;
        this.shiftHandoverRepository = shiftHandoverRepository;
        this.passwordEncoder = passwordEncoder;
        this.pinHasher = pinHasher;
        this.pinLockoutService = pinLockoutService;
        this.tokenProvider = tokenProvider;
        this.refreshTokenStore = refreshTokenStore;
        this.loginAttemptService = loginAttemptService;
        this.loginIpRateLimiter = loginIpRateLimiter;
        this.refreshRateLimiter = refreshRateLimiter;
        this.auditLogService = auditLogService;
        this.dummyPasswordHash = passwordEncoder.encode("scms-dummy-" + UUID.randomUUID());
    }

    @Override
    @Transactional
    public LoginResponse login(LoginRequest request, String clientIp) {
        String username = request.username().trim();
        // Đếm theo IP TRƯỚC khi khoá/theo username: request bị chặn ở đây rẻ nhất (không chạy BCrypt).
        loginIpRateLimiter.onAttempt(clientIp);
        loginAttemptService.checkNotLocked(username);

        User user = authenticate(username, request.password());
        loginAttemptService.onSuccess(username);
        auditLogService.log(AuditAction.LOGIN, ENTITY_USER, user.getId(), null, Map.of("role", user.roleCode()));
        log.info("Đăng nhập thành công: userId={}, role={}, roleHint={}", user.getId(), user.roleCode(),
                request.roleHint());

        String refreshToken = issueRefreshIfRemembered(user, Boolean.TRUE.equals(request.rememberMe()));
        return new LoginResponse(tokenProvider.issueAccessToken(user.getId(), user.getUsername(), user.getFullName(),
                user.roleCode(), user.getBranchId()).token(), tokenProvider.accessTtlSeconds(), refreshToken,
                toAuthUser(user), Roles.landingFor(user.roleCode()));
    }

    @Override
    @Transactional(readOnly = true)
    public List<KioskBranchDto> listPublicBranches(String keyword) {
        String normalized = StringUtils.hasText(keyword) ? keyword.trim().toLowerCase(Locale.ROOT) : null;
        return branchRepository.findAll(Sort.by(Sort.Order.asc("code"))).stream()
                .filter(b -> b.getStatus() != BranchStatus.INACTIVE)
                .filter(b -> normalized == null
                        || (b.getCode() != null && b.getCode().toLowerCase(Locale.ROOT).contains(normalized))
                        || (b.getName() != null && b.getName().toLowerCase(Locale.ROOT).contains(normalized)))
                .map(b -> new KioskBranchDto(b.getId(), b.getCode(), b.getName(), b.getStatus()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CashierPinOptionDto> listCashierPinOptions(Integer branchId) {
        if (branchId == null) {
            throw new InvalidFieldValueException("branchId", "Chi nhánh không được để trống.");
        }
        Branch branch = branchRepository.findById(branchId)
                .orElseThrow(() -> new NotFoundException("chi nhánh"));
        if (branch.getStatus() == BranchStatus.INACTIVE) {
            throw new ForbiddenException(ErrorCode.ACCESS_DENIED, "Chi nhánh đã ngừng hoạt động.");
        }

        List<User> activeUsers = userRepository.findAllByBranchIdAndStatus(branchId, UserStatus.ACTIVE);
        Map<Integer, User> activeById = new LinkedHashMap<>();
        for (User u : activeUsers) {
            activeById.put(u.getId(), u);
        }

        // Chỉ hiển thị nhân sự có vai trò Thu ngân (gốc hoặc kiêm CASHIER trong chính ca đang xét):
        // - Khi có ca ĐANG MỞ chưa bàn giao két: chỉ hiện Thu ngân đang giữ két.
        // - Khi ca ĐANG MỞ đang chờ bàn giao két giữa ca: chỉ hiện người đang giao và người được gán vai trò CASHIER trong ca đó.
        // - Khi chưa có ca ĐANG MỞ: chỉ hiện nhân sự được gán vai trò CASHIER trong ca ĐÃ LÊN LỊCH đã đến khung mở ca
        //   ([plannedStart - 60 phút, plannedEnd + 30 phút]).
        Instant now = Instant.now();
        Set<Integer> cashierUserIds = findEligiblePinCashierIds(branchId, now);
        Map<Integer, Instant> activeLocks = pinLockoutService.findActiveEmployeeLocks(branchId);

        return cashierUserIds.stream()
                .map(activeById::get)
                .filter(Objects::nonNull)
                .filter(u -> u.getPosition() != null && UserDirectory.BRANCH_STAFF_ROLES.contains(u.getPosition()))
                .map(u -> {
                    Instant lockedUntil = activeLocks.get(u.getId());
                    boolean locked = lockedUntil != null && lockedUntil.isAfter(now);
                    return new CashierPinOptionDto(
                            u.getId(),
                            u.getFullName(),
                            u.getBranchId(),
                            StringUtils.hasText(u.getPinHash()),
                            locked,
                            locked ? lockedUntil : null
                    );
                })
                .toList();
    }

    @Override
    @Transactional
    public LoginResponse pinLogin(PinLoginRequest request, String clientIp) {
        loginIpRateLimiter.onAttempt(clientIp);

        Integer branchId = request.branchId();
        Branch branch = branchRepository.findById(branchId)
                .orElseThrow(() -> new NotFoundException("chi nhánh"));
        if (branch.getStatus() == BranchStatus.INACTIVE) {
            throw new ForbiddenException(ErrorCode.ACCESS_DENIED, "Chi nhánh đã ngừng hoạt động.");
        }

        String rawPin = request.pin().trim();
        User user = authenticateCashierByPin(branchId, request.employeeId(), rawPin);

        String subject = StringUtils.hasText(user.getUsername())
                ? user.getUsername()
                : "cashier-" + user.getId();
        String effectiveRole = Roles.CASHIER.code();
        IssuedToken access = tokenProvider.issueAccessToken(
                user.getId(), subject, user.getFullName(), effectiveRole, user.getBranchId());

        auditLogService.log(AuditAction.LOGIN, ENTITY_USER, user.getId(), null,
                Map.of("role", effectiveRole, "method", "PIN", "branchId", branchId));
        log.info("Đăng nhập PIN thu ngân thành công: userId={}, branchId={}", user.getId(), branchId);

        return new LoginResponse(
                access.token(),
                tokenProvider.accessTtlSeconds(),
                null,
                toAuthUser(user, effectiveRole),
                Roles.landingFor(effectiveRole)
        );
    }

    @Override
    @Transactional
    public TokenResponse refresh(RefreshRequest request) {
        String raw = request.token();
        if (raw == null) {
            // Không có token ≠ token hết hạn: FE phải biết cần đăng nhập lại.
            throw new UnauthorizedException(ErrorCode.AUTH_REQUIRED);
        }
        // Đếm TRƯỚC khi kiểm chữ ký để request sai chữ ký spam cũng bị chặn; token rác -> đếm theo IP (không gom
        // một khoá "anonymous" — một kẻ spam sẽ khoá luôn đường refresh của mọi người).
        refreshRateLimiter.onAttempt(RefreshRateLimiter.key(ownerOfIssuedToken(raw), request.clientIp()));

        Instant expiresAt = tokenProvider.parseRefresh(raw).getExpiration().toInstant(); // verify ĐÚNG 1 lần
        return rotate(loadRefreshable(raw), expiresAt);
    }

    @Override
    @Transactional
    public void logout(UserPrincipal principal) {
        refreshTokenStore.revokeAllOfUser(principal.userId());
        refreshTokenStore.revokeAccessJti(principal.userId(), principal.jti(), accessExpiry());
        auditLogService.log(AuditAction.LOGOUT, ENTITY_USER, principal.userId(), null, null);
        log.info("Đăng xuất: userId={}", principal.userId());
    }

    @Override
    @Transactional(readOnly = true)
    public CurrentUserResponse me(UserPrincipal principal) {
        User user = userRepository.findWithRoleAndBranchById(principal.userId())
                .orElseThrow(() -> new UnauthorizedException(ErrorCode.AUTH_REQUIRED));
        String effectiveRole = principal.role() != null ? principal.role() : user.roleCode();
        return new CurrentUserResponse(user.getId(), user.getFullName(), user.getUsername(), effectiveRole,
                user.getBranchId(), branchName(user), PermissionCatalog.of(effectiveRole));
    }

    // ------------------------------------------------------------------ login

    /**
     * Kiểm mật khẩu/trạng thái; mọi lý do thất bại (không tồn tại / sai mật khẩu / INACTIVE) đều ra
     * INVALID_CREDENTIALS — cùng nhau để không lộ username.
     */
    private User authenticate(String username, String password) {
        User user = userRepository.findWithRoleAndBranchByUsername(username).orElse(null);
        // Luôn chạy BCrypt (kể cả khi không có user) để thời gian phản hồi không lộ username tồn tại.
        String passwordHash = user == null || user.getPasswordHash() == null ? dummyPasswordHash
                : user.getPasswordHash();
        boolean passwordOk = passwordEncoder.matches(password, passwordHash);

        if (user != null && passwordOk && user.isActive() && isUserBranchAccessible(user)) {
            return user;
        }
        loginAttemptService.onFailure(username);
        auditLogService.log(AuditAction.LOGIN_FAILED, ENTITY_USER, null, null, Map.of("username", username));
        throw new UnauthorizedException(ErrorCode.INVALID_CREDENTIALS, Messages.MSG09);
    }

    private User authenticateCashierByPin(Integer branchId, Integer employeeId, String rawPin) {
        if (employeeId != null) {
            User user = userRepository.findWithRoleAndBranchById(employeeId)
                    .orElseThrow(() -> new NotFoundException("nhân viên"));

            if (user.getBranchId() == null || !branchId.equals(user.getBranchId())) {
                throw new ForbiddenException(ErrorCode.CROSS_BRANCH_DENIED);
            }
            if (!user.isActive()) {
                throw new UnauthorizedException(ErrorCode.ACCOUNT_INACTIVE);
            }
            if (!isCashierAllowedForPinLogin(user, branchId)) {
                throw new ForbiddenException(ErrorCode.ACCESS_DENIED, buildPinLoginDeniedMessage(user, branchId));
            }
            if (!StringUtils.hasText(user.getPinHash())) {
                throw new InvalidPinException(
                        "Thu ngân chưa được cấp mã PIN. Vui lòng liên hệ Quản lý chi nhánh để cấp PIN.");
            }

            pinLockoutService.checkNotLocked(branchId, user.getId(), user.getFullName());
            if (!pinHasher.matches(rawPin, user.getPinHash())) {
                PinLockoutService.FailureOutcome outcome = pinLockoutService.recordFailure(
                        branchId, user.getId(), user.getFullName(), user.roleCode());
                auditLogService.log(AuditAction.LOGIN_FAILED, ENTITY_USER, user.getId(), null,
                        Map.of("method", "PIN", "branchId", branchId));
                throw toPinFailureException(outcome);
            }

            pinLockoutService.onSuccess(branchId, user.getId());
            return user;
        }

        // Không truyền employeeId: tra cứu theo chi nhánh + SHA-256 lookupHash
        pinLockoutService.checkNotLocked(branchId, null, null);
        String lookupHash = pinHasher.lookupHash(branchId, rawPin);
        User user = userRepository.findFirstByBranchIdAndPinLookupHashAndStatus(branchId, lookupHash, UserStatus.ACTIVE)
                .orElse(null);
        if (user == null) {
            PinLockoutService.FailureOutcome outcome = pinLockoutService.recordFailure(branchId, null, null, null);
            auditLogService.log(AuditAction.LOGIN_FAILED, ENTITY_USER, null, null,
                    Map.of("method", "PIN", "branchId", branchId));
            throw toPinFailureException(outcome);
        }

        pinLockoutService.checkNotLocked(branchId, user.getId(), user.getFullName());
        if (!pinHasher.matches(rawPin, user.getPinHash())) {
            PinLockoutService.FailureOutcome outcome = pinLockoutService.recordFailure(
                    branchId, user.getId(), user.getFullName(), user.roleCode());
            auditLogService.log(AuditAction.LOGIN_FAILED, ENTITY_USER, user.getId(), null,
                    Map.of("method", "PIN", "branchId", branchId));
            throw toPinFailureException(outcome);
        }

        if (!isCashierAllowedForPinLogin(user, branchId)) {
            throw new ForbiddenException(ErrorCode.ACCESS_DENIED, buildPinLoginDeniedMessage(user, branchId));
        }

        pinLockoutService.onSuccess(branchId, user.getId());
        pinLockoutService.onSuccess(branchId, null);
        return user;
    }

    private Set<Integer> findEligiblePinCashierIds(Integer branchId, Instant now) {
        Set<Integer> eligibleIds = new LinkedHashSet<>();

        List<Shift> openShifts = shiftRepository.findByBranchIdAndStatusOrderByOpenedAtDescIdDesc(
                branchId, ShiftStatus.OPEN);
        if (!openShifts.isEmpty()) {
            for (Shift openShift : openShifts) {
                Integer activeCashierId = openShift.getActiveCashierId();
                if (openShift.isEarlyClosePending()) {
                    Integer requesterId = openShift.getEarlyCloseRequestedBy();
                    if (requesterId != null) {
                        eligibleIds.add(requesterId);
                    } else if (activeCashierId != null) {
                        eligibleIds.add(activeCashierId);
                    }
                } else if (!openShift.isHandoverPending()) {
                    // Khi ca đang mở và CHƯA bàn giao két giữa ca, Thu ngân đang giữ két phải đứng quầy tiếp
                    // hoặc thực hiện bàn giao két/đóng ca — không cho Thu ngân khác thế chỗ tại màn PIN.
                    User activeHolder = activeCashierId == null
                            ? null
                            : userRepository.findWithRoleAndBranchById(activeCashierId).orElse(null);
                    if (isActiveBranchStaff(activeHolder, branchId)) {
                        eligibleIds.add(activeHolder.getId());
                    } else {
                        // Ca do Quản lý mở thay (holder không phải staff PIN) hoặc chưa có holder:
                        // chỉ cho phép nhân sự được phân công vai trò Thu ngân trong chính ca đang mở này.
                        collectAssignedCashiersForShift(openShift.getId(), eligibleIds);
                    }
                } else {
                    // Khi ca ĐANG MỞ đang chờ bàn giao két giữa ca, chỉ cho phép đúng những người thuộc ca này:
                    // - người đang bàn giao (để quay lại hủy bàn giao nếu cần),
                    // - nhân sự được phân công vai trò CASHIER trong CHÍNH ca đang mở này (kể cả kiêm nhiệm),
                    // - và người được chỉ định nhận két (nếu pending handover ghi nhận toUserId).
                    if (activeCashierId != null) {
                        eligibleIds.add(activeCashierId);
                    }
                    collectAssignedCashiersForShift(openShift.getId(), eligibleIds);
                    shiftHandoverRepository
                            .findFirstByShiftIdAndStatusOrderByIdDesc(
                                    openShift.getId(), ShiftHandoverStatus.PENDING_RECEIVE)
                            .ifPresent(pending -> {
                                if (pending.getFromUserId() != null) {
                                    eligibleIds.add(pending.getFromUserId());
                                }
                                if (pending.getToUserId() != null) {
                                    eligibleIds.add(pending.getToUserId());
                                }
                            });
                }
            }
            return eligibleIds;
        }

        LocalDate todayVN = DateTimeUtil.todayVN();
        List<Shift> nearbyShifts = shiftRepository
                .findByBranchIdAndShiftDateBetweenOrderByShiftDateAscPlannedStartAsc(
                        branchId, todayVN.minusDays(1), todayVN.plusDays(1));
        for (Shift scheduled : nearbyShifts) {
            if (scheduled.getStatus() == ShiftStatus.SCHEDULED && isWithinPinWindow(scheduled, now)) {
                collectAssignedCashiersForShift(scheduled.getId(), eligibleIds);
            }
        }

        return eligibleIds;
    }

    private void collectAssignedCashiersForShift(Integer shiftId, Set<Integer> cashierUserIds) {
        List<ShiftAssignment> assignments = shiftAssignmentRepository.findByShiftIdWithEmployee(shiftId);
        for (ShiftAssignment sa : assignments) {
            Integer empId = sa.getId() != null ? sa.getId().getEmployeeId() : null;
            if (empId == null) {
                continue;
            }
            String effectiveRole = StringUtils.hasText(sa.getRoleInShift())
                    ? sa.getRoleInShift().trim()
                    : (sa.getEmployee() != null ? sa.getEmployee().roleCode() : null);
            if (Roles.CASHIER.code().equalsIgnoreCase(effectiveRole)) {
                cashierUserIds.add(empId);
            }
        }
    }

    private boolean isCashierAllowedForPinLogin(User user, Integer branchId) {
        if (!isActiveBranchStaff(user, branchId)) {
            return false;
        }
        return findEligiblePinCashierIds(branchId, Instant.now()).contains(user.getId());
    }

    private static boolean isActiveBranchStaff(User user, Integer branchId) {
        return user != null
                && user.isActive()
                && branchId != null
                && branchId.equals(user.getBranchId())
                && user.getPosition() != null
                && UserDirectory.BRANCH_STAFF_ROLES.contains(user.getPosition());
    }

    private String buildPinLoginDeniedMessage(User user, Integer branchId) {
        List<Shift> openShifts = shiftRepository.findByBranchIdAndStatusOrderByOpenedAtDescIdDesc(
                branchId, ShiftStatus.OPEN);
        for (Shift openShift : openShifts) {
            if (openShift.isEarlyClosePending()
                    && user != null
                    && !Objects.equals(openShift.getEarlyCloseRequestedBy(), user.getId())
                    && !Objects.equals(openShift.getActiveCashierId(), user.getId())) {
                return "Ca đang mở hiện đang chờ Quản lý chi nhánh xác nhận đóng ca sớm. Vui lòng chờ Quản lý duyệt trước khi đổi Thu ngân khác.";
            }
            if (!openShift.isHandoverPending()) {
                Integer holderId = openShift.getActiveCashierId();
                User holder = holderId == null
                        ? null
                        : userRepository.findWithRoleAndBranchById(holderId).orElse(null);
                if (isActiveBranchStaff(holder, branchId)
                        && user != null
                        && !Objects.equals(holderId, user.getId())) {
                    return "Ca đang mở hiện chưa bàn giao két giữa ca. Thu ngân đang giữ két phải thực hiện Bàn giao két giữa ca (hoặc Đóng ca) trước khi đổi Thu ngân khác.";
                }
            }
        }
        if (user != null && Roles.CASHIER.code().equals(user.roleCode())) {
            return "Thu ngân chưa được phân công vào ca đang mở hoặc chưa đến khung giờ mở ca (trong 60 phút trước giờ bắt đầu) tại chi nhánh.";
        }
        return "Chỉ Thu ngân hoặc nhân sự được phân công vai trò Thu ngân trong ca hiện tại mới được phép mở khóa POS / kiểm két bằng PIN.";
    }

    private static boolean isWithinPinWindow(Shift s, Instant now) {
        if (s.getPlannedStart() == null || s.getPlannedEnd() == null) {
            return DateTimeUtil.todayVN().equals(s.getShiftDate());
        }
        Instant windowStart = s.getPlannedStart().minus(ShiftSchedulePolicy.EARLY_OPEN_GRACE);
        Instant windowEnd = s.getPlannedEnd().plus(ShiftSchedulePolicy.PAST_EXPIRE_GRACE);
        return !now.isBefore(windowStart) && !now.isAfter(windowEnd);
    }

    private static RuntimeException toPinFailureException(PinLockoutService.FailureOutcome outcome) {
        if (outcome.locked()) {
            return new PinLockedException(outcome.retryAfterSeconds(), outcome.message());
        }
        return new InvalidPinException(outcome.message());
    }

    /** Refresh token chỉ cấp khi rememberMe=true (kéo dài phiên qua refresh token rotation). */
    private String issueRefreshIfRemembered(User user, boolean rememberMe) {
        if (!rememberMe) {
            return null;
        }
        IssuedToken refresh = tokenProvider.issueRefreshToken(user.getId(), user.getUsername(), user.getFullName(),
                user.roleCode(), user.getBranchId(), tokenProvider.refreshTtl());
        refreshTokenStore.saveRefreshToken(user.getId(), refresh.token(), refresh.jti(), refresh.expiresAt());
        return refresh.token();
    }

    // ------------------------------------------------------------------ refresh

    /** Đọc token đã cấp: replay -> huỷ phiên; không dùng được -> 401. */
    private AuthToken loadRefreshable(String raw) {
        AuthToken stored = refreshTokenStore.findAny(raw); // query ĐÚNG 1 lần cho cả kiểm tra lẫn rotation
        rejectReplayed(stored);
        rejectUnusable(stored);
        return stored;
    }

    private Integer ownerOfIssuedToken(String rawRefreshToken) {
        AuthToken stored = refreshTokenStore.findAny(rawRefreshToken);
        return stored == null ? null : stored.getUserId();
    }

    /**
     * Token đã cấp nhưng đã bị thu hồi -> dùng lại token cũ (replay). Rotation đã phát token mới cho client;
     * bản cũ chỉ có thể lọt ra khi bị đánh cắp -> huỷ toàn bộ phiên của user đó.
     */
    private void rejectReplayed(AuthToken stored) {
        if (stored == null || stored.getRevokedAt() == null) {
            return;
        }
        refreshTokenStore.revokeAllOfUser(stored.getUserId());
        auditLogService.log(AuditAction.LOGOUT, ENTITY_USER, stored.getUserId(), null,
                Map.of("reason", "refresh_token_reuse"));
        log.warn("Phát hiện dùng lại refresh token đã thu hồi: userId={} -> huỷ toàn bộ phiên.", stored.getUserId());
        throw new UnauthorizedException(ErrorCode.AUTH_TOKEN_EXPIRED);
    }

    /** Token phải còn trong DB và chưa bị thu hồi (đăng xuất / đổi mật khẩu). */
    private static void rejectUnusable(AuthToken stored) {
        if (stored == null || !stored.isUsable()) {
            throw new UnauthorizedException(ErrorCode.AUTH_TOKEN_EXPIRED);
        }
    }

    private User loadActiveUser(Integer userId) {
        return userRepository.findWithRoleAndBranchById(userId)
                .filter(User::isActive)
                .filter(AuthServiceImpl::isUserBranchAccessible)
                .orElseThrow(() -> new UnauthorizedException(ErrorCode.AUTH_TOKEN_EXPIRED));
    }

    private static boolean isUserBranchAccessible(User user) {
        return user.getPosition() == Roles.COO
                || user.getBranch() == null
                || user.getBranch().getStatus() != BranchStatus.INACTIVE;
    }

    /**
     * Rotation: thu hồi token cũ <b>nguyên tử</b>, phát cặp mới. Hai request đồng thời cùng token: chỉ một bên
     * thu hồi được; bên thua bị từ chối thay vì cũng được cấp token mới (chống race của rotation).
     * Token mới sống đúng bằng phần đời còn lại của token cũ — không kéo dài phiên qua mỗi lần refresh.
     */
    private TokenResponse rotate(AuthToken stored, Instant oldExpiresAt) {
        User user = loadActiveUser(stored.getUserId());
        if (!refreshTokenStore.revokeIfActive(stored)) {
            throw new UnauthorizedException(ErrorCode.AUTH_TOKEN_EXPIRED);
        }
        Duration remaining = Duration.between(Instant.now(), oldExpiresAt);
        if (remaining.isNegative() || remaining.isZero()) {
            throw new UnauthorizedException(ErrorCode.AUTH_TOKEN_EXPIRED);
        }
        IssuedToken access = tokenProvider.issueAccessToken(user.getId(), user.getUsername(), user.getFullName(),
                user.roleCode(), user.getBranchId());
        IssuedToken refresh = tokenProvider.issueRefreshToken(user.getId(), user.getUsername(), user.getFullName(),
                user.roleCode(), user.getBranchId(), remaining);
        refreshTokenStore.saveRefreshToken(user.getId(), refresh.token(), refresh.jti(), refresh.expiresAt());
        return new TokenResponse(access.token(), tokenProvider.accessTtlSeconds(), refresh.token(), toAuthUser(user));
    }

    // ------------------------------------------------------------------ helpers

    private Instant accessExpiry() {
        return Instant.now().plusSeconds(tokenProvider.accessTtlSeconds() + CLOCK_SKEW_SECONDS);
    }

    private static AuthUserDto toAuthUser(User user) {
        return toAuthUser(user, null);
    }

    private static AuthUserDto toAuthUser(User user, String effectiveRole) {
        return new AuthUserDto(user.getId(), user.getFullName(),
                effectiveRole != null ? effectiveRole : user.roleCode(),
                user.getBranchId(),
                branchName(user));
    }

    private static String branchName(User user) {
        return user.getBranch() == null ? null : user.getBranch().getName();
    }
}
