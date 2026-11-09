package edu.fpt.sba.scms.service.support;

import edu.fpt.sba.scms.common.enums.SecurityAlertSeverity;
import edu.fpt.sba.scms.common.enums.SecurityAlertStatus;
import edu.fpt.sba.scms.common.enums.SecurityAlertType;
import edu.fpt.sba.scms.common.exception.PinLockedException;
import edu.fpt.sba.scms.config.properties.AppProperties;
import edu.fpt.sba.scms.model.Branch;
import edu.fpt.sba.scms.model.PinLockState;
import edu.fpt.sba.scms.model.SecurityAlert;
import edu.fpt.sba.scms.repository.BranchRepository;
import edu.fpt.sba.scms.repository.PinLockStateRepository;
import edu.fpt.sba.scms.repository.SecurityAlertRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Bảo vệ PIN Kiosk chấm công: sai quá {@code pinMaxFailures} lần liên tiếp (mặc định 3)
 * trong {@code pinFailureWindowMin} phút -> tạm khóa {@code pinLockMin} phút (mặc định 15)
 * và phát sinh {@link SecurityAlert} để Quản lý chi nhánh / COO theo dõi &amp; mở khóa sớm.
 */
@Slf4j
@Service
public class PinLockoutService {

    public record FailureOutcome(
            boolean locked,
            int failureCount,
            int maxFailures,
            Instant lockedUntil,
            long retryAfterSeconds,
            String message) {
    }

    private final PinLockStateRepository lockStateRepository;
    private final SecurityAlertRepository alertRepository;
    private final BranchRepository branchRepository;
    private final TransactionTemplate requiresNewTx;
    private final int maxFailures;
    private final Duration failureWindow;
    private final Duration lockDuration;
    private final Clock clock;
    private final ConcurrentHashMap<String, Object> keyMutexes = new ConcurrentHashMap<>();

    @Autowired
    public PinLockoutService(
            PinLockStateRepository lockStateRepository,
            SecurityAlertRepository alertRepository,
            BranchRepository branchRepository,
            PlatformTransactionManager txManager,
            AppProperties.Kiosk kioskProps) {
        this(lockStateRepository, alertRepository, branchRepository, txManager, kioskProps, Clock.systemUTC());
    }

    PinLockoutService(
            PinLockStateRepository lockStateRepository,
            SecurityAlertRepository alertRepository,
            BranchRepository branchRepository,
            PlatformTransactionManager txManager,
            AppProperties.Kiosk kioskProps,
            Clock clock) {
        this.lockStateRepository = lockStateRepository;
        this.alertRepository = alertRepository;
        this.branchRepository = branchRepository;
        TransactionTemplate tt = new TransactionTemplate(txManager);
        tt.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.requiresNewTx = tt;
        this.maxFailures = kioskProps != null && kioskProps.pinMaxFailures() != null ? kioskProps.pinMaxFailures() : 3;
        int winMin = kioskProps != null && kioskProps.pinFailureWindowMin() != null ? kioskProps.pinFailureWindowMin() : 15;
        int lockMin = kioskProps != null && kioskProps.pinLockMin() != null ? kioskProps.pinLockMin() : 15;
        this.failureWindow = Duration.ofMinutes(winMin);
        this.lockDuration = Duration.ofMinutes(lockMin);
        this.clock = clock;
    }

    public static String lockKey(Integer branchId, Integer employeeId) {
        return employeeId != null ? "EMP:" + employeeId : "BRANCH:" + branchId;
    }

    /** Kiểm tra khóa tạm PIN trước khi đối chiếu BCrypt; đang khóa -> 401 PIN_LOCKED + Retry-After. */
    public void checkNotLocked(Integer branchId, Integer employeeId, String employeeName) {
        String key = lockKey(branchId, employeeId);
        PinLockState state = lockStateRepository.findByLockKey(key).orElse(null);
        if (state == null) {
            return;
        }
        Instant now = clock.instant();
        if (state.isLockedAt(now)) {
            long retryAfter = Math.max(1, Duration.between(now, state.getLockedUntil()).toSeconds() + 1);
            throw new PinLockedException(retryAfter, buildLockedMessage(employeeName, retryAfter));
        }
    }

    /**
     * Ghi nhận 1 lần nhập sai PIN trong transaction độc lập ({@code REQUIRES_NEW})
     * để không bị rollback khi API chấm công ném {@code 401 INVALID_PIN} hoặc {@code 401 PIN_LOCKED}.
     */
    public FailureOutcome recordFailure(Integer branchId, Integer employeeId, String employeeName, String roleCode) {
        String key = lockKey(branchId, employeeId);
        Object mutex = keyMutexes.computeIfAbsent(key, k -> new Object());
        synchronized (mutex) {
            return requiresNewTx.execute(status -> recordFailureInNewTx(key, branchId, employeeId, employeeName, roleCode));
        }
    }

    private FailureOutcome recordFailureInNewTx(
            String key,
            Integer branchId,
            Integer employeeId,
            String employeeName,
            String roleCode) {
        Instant now = clock.instant();
        PinLockState state = lockStateRepository.findByLockKeyForUpdate(key).orElseGet(() -> PinLockState.builder()
                .lockKey(key)
                .branchId(branchId)
                .employeeId(employeeId)
                .failureCount(0)
                .build());

        if (state.isLockedAt(now)) {
            long retryAfter = Math.max(1, Duration.between(now, state.getLockedUntil()).toSeconds() + 1);
            return new FailureOutcome(
                    true,
                    state.getFailureCount(),
                    maxFailures,
                    state.getLockedUntil(),
                    retryAfter,
                    buildLockedMessage(employeeName, retryAfter));
        }

        boolean startFreshWindow = state.getWindowStartAt() == null
                || state.getWindowStartAt().plus(failureWindow).isBefore(now)
                || state.getLockedUntil() != null;

        int nextCount = startFreshWindow ? 1 : state.getFailureCount() + 1;
        if (startFreshWindow) {
            state.setWindowStartAt(now);
            state.setLockedUntil(null);
        }
        state.setBranchId(branchId);
        state.setEmployeeId(employeeId);
        state.setFailureCount(nextCount);
        state.setLastFailedAt(now);

        if (nextCount >= maxFailures) {
            Instant until = now.plus(lockDuration);
            state.setLockedUntil(until);
            lockStateRepository.save(state);

            createLockAlert(branchId, employeeId, employeeName, roleCode, nextCount, until);

            long retryAfter = Math.max(1, lockDuration.toSeconds());
            String msg = employeeId != null
                    ? String.format("Mã PIN của %s đã bị tạm khóa %d phút do nhập sai %d/%d lần liên tiếp. Đã gửi cảnh báo tới Quản lý chi nhánh & COO.",
                    safeName(employeeName), lockDuration.toMinutes(), nextCount, maxFailures)
                    : String.format("Kiosk chi nhánh đã bị tạm khóa tra cứu PIN %d phút do nhập sai %d/%d lần liên tiếp. Đã gửi cảnh báo tới cấp trên.",
                    lockDuration.toMinutes(), nextCount, maxFailures);
            log.warn("Tạm khóa PIN key={} (branchId={}, employeeId={}) đến {} sau {}/{} lần sai",
                    key, branchId, employeeId, until, nextCount, maxFailures);
            return new FailureOutcome(true, nextCount, maxFailures, until, retryAfter, msg);
        }

        lockStateRepository.save(state);
        int remaining = maxFailures - nextCount;
        String msg = String.format("Mã PIN không đúng (sai %d/%d lần — còn %d lần thử trước khi tạm khóa %d phút).",
                nextCount, maxFailures, remaining, lockDuration.toMinutes());
        return new FailureOutcome(false, nextCount, maxFailures, null, 0, msg);
    }

    private void createLockAlert(
            Integer branchId,
            Integer employeeId,
            String employeeName,
            String roleCode,
            int failureCount,
            Instant until) {
        String branchName = branchRepository.findById(branchId)
                .map(Branch::getName)
                .orElse("Chi nhánh #" + branchId);
        long lockMin = Math.max(1, lockDuration.toMinutes());
        String displayTarget = employeeId != null ? safeName(employeeName) : branchName;
        String title = employeeId != null
                ? "Khóa tạm PIN chấm công · " + displayTarget
                : "Cảnh báo dò PIN Kiosk · " + branchName;
        String message = employeeId != null
                ? String.format("%s nhập sai PIN %d/%d lần liên tiếp tại %s. PIN đã bị tạm khóa %d phút (có thể mở khóa sớm).",
                displayTarget, failureCount, maxFailures, branchName, lockMin)
                : String.format("Kiosk %s có %d/%d lần nhập sai PIN ẩn danh liên tiếp. Đã tạm khóa %d phút.",
                branchName, failureCount, maxFailures, lockMin);

        SecurityAlert alert = SecurityAlert.builder()
                .branchId(branchId)
                .branchName(branchName)
                .alertType(SecurityAlertType.KIOSK_PIN_LOCKED)
                .severity(SecurityAlertSeverity.HIGH)
                .status(SecurityAlertStatus.ACTIVE)
                .targetUserId(employeeId)
                .targetUserName(employeeId != null ? displayTarget : null)
                .targetRole(roleCode)
                .failureCount(failureCount)
                .lockedUntil(until)
                .title(truncate(title, 200))
                .message(truncate(message, 500))
                .build();
        alertRepository.save(alert);
    }

    /** Reset bộ đếm sai khi nhân viên chấm công thành công bằng PIN đúng. */
    @Transactional
    public void onSuccess(Integer branchId, Integer employeeId) {
        String key = lockKey(branchId, employeeId);
        lockStateRepository.findByLockKey(key).ifPresent(state -> {
            if (state.getFailureCount() > 0 || state.getLockedUntil() != null) {
                state.setFailureCount(0);
                state.setWindowStartAt(null);
                state.setLockedUntil(null);
                lockStateRepository.save(state);
            }
        });
    }

    /** Lấy danh sách nhân viên đang bị khóa PIN tại chi nhánh cho màn hình Kiosk/POS. */
    @Transactional(readOnly = true)
    public Map<Integer, Instant> findActiveEmployeeLocks(Integer branchId) {
        Instant now = clock.instant();
        return lockStateRepository.findAllByBranchIdAndLockedUntilAfter(branchId, now).stream()
                .filter(s -> s.getEmployeeId() != null)
                .collect(Collectors.toMap(
                        PinLockState::getEmployeeId,
                        PinLockState::getLockedUntil,
                        (a, b) -> a.isAfter(b) ? a : b));
    }

    /** Lấy tập lockKey đang còn hiệu lực khóa trong phạm vi chi nhánh (hoặc toàn chuỗi nếu branchId == null). */
    @Transactional(readOnly = true)
    public Map<String, Instant> findActiveLockMap(Integer branchId) {
        Instant now = clock.instant();
        List<PinLockState> active = branchId == null
                ? lockStateRepository.findAllByLockedUntilAfter(now)
                : lockStateRepository.findAllByBranchIdAndLockedUntilAfter(branchId, now);
        return active.stream()
                .collect(Collectors.toMap(
                        PinLockState::getLockKey,
                        PinLockState::getLockedUntil,
                        (a, b) -> a.isAfter(b) ? a : b));
    }

    /** Mở khóa PIN sớm (do Quản lý chi nhánh / COO thao tác hoặc khi đặt lại PIN mới). */
    @Transactional
    public void unlockPin(
            Integer branchId,
            Integer targetUserId,
            Integer actorId,
            String actorName,
            String note) {
        Instant now = clock.instant();
        String key = lockKey(branchId, targetUserId);
        lockStateRepository.findByLockKeyForUpdate(key).ifPresent(state -> {
            state.setFailureCount(0);
            state.setWindowStartAt(null);
            state.setLockedUntil(null);
            state.setUnlockedBy(actorId);
            state.setUnlockedAt(now);
            lockStateRepository.save(state);
        });

        List<SecurityAlertStatus> openStatuses = List.of(SecurityAlertStatus.ACTIVE, SecurityAlertStatus.ACKNOWLEDGED);
        List<SecurityAlert> candidates = targetUserId != null
                ? alertRepository.findByTargetUserIdAndAlertTypeAndStatusIn(
                targetUserId, SecurityAlertType.KIOSK_PIN_LOCKED, openStatuses)
                : alertRepository.findByBranchIdAndTargetUserIdIsNullAndAlertTypeAndStatusIn(
                branchId, SecurityAlertType.KIOSK_PIN_LOCKED, openStatuses);

        String resolvedNote = StringUtils.hasText(note) ? truncate(note.trim(), 255) : "Quản lý đã mở khóa PIN sớm";
        for (SecurityAlert alert : candidates) {
            boolean stillActiveOrLocked = alert.getStatus() == SecurityAlertStatus.ACTIVE
                    || (alert.getLockedUntil() != null && alert.getLockedUntil().isAfter(now));
            if (stillActiveOrLocked) {
                alert.setStatus(SecurityAlertStatus.UNLOCKED);
                alert.setResolvedBy(actorId);
                alert.setResolvedByName(actorName);
                alert.setResolvedAt(now);
                alert.setResolveNote(resolvedNote);
                alertRepository.save(alert);
            }
        }
    }

    private String buildLockedMessage(String employeeName, long retryAfterSeconds) {
        long remainingMinutes = Math.max(1, Math.ceilDiv(retryAfterSeconds, 60));
        if (StringUtils.hasText(employeeName)) {
            return String.format(
                    "Mã PIN của %s đang bị tạm khóa do nhập sai quá %d lần. Vui lòng thử lại sau %d phút hoặc liên hệ Quản lý chi nhánh để mở khóa sớm.",
                    employeeName, maxFailures, remainingMinutes);
        }
        return String.format(
                "Chức năng chấm công bằng PIN tại Kiosk đang bị tạm khóa do nhập sai quá %d lần. Vui lòng thử lại sau %d phút hoặc liên hệ Quản lý chi nhánh.",
                maxFailures, remainingMinutes);
    }

    private static String safeName(String name) {
        return StringUtils.hasText(name) ? name.trim() : "Nhân viên";
    }

    private static String truncate(String s, int maxLen) {
        if (s == null || s.length() <= maxLen) {
            return s;
        }
        return s.substring(0, maxLen - 1) + "…";
    }
}
