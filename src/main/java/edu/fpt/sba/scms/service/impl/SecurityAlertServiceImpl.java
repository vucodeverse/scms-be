package edu.fpt.sba.scms.service.impl;

import edu.fpt.sba.scms.common.audit.AuditLogService;
import edu.fpt.sba.scms.common.enums.AuditAction;
import edu.fpt.sba.scms.common.enums.SecurityAlertStatus;
import edu.fpt.sba.scms.common.enums.SecurityAlertType;
import edu.fpt.sba.scms.common.exception.NotFoundException;
import edu.fpt.sba.scms.dto.response.SecurityAlertDto;
import edu.fpt.sba.scms.dto.response.SecurityAlertSummaryDto;
import edu.fpt.sba.scms.model.SecurityAlert;
import edu.fpt.sba.scms.model.User;
import edu.fpt.sba.scms.repository.SecurityAlertRepository;
import edu.fpt.sba.scms.repository.UserRepository;
import edu.fpt.sba.scms.security.BranchContext;
import edu.fpt.sba.scms.security.principal.UserPrincipal;
import edu.fpt.sba.scms.service.SecurityAlertService;
import edu.fpt.sba.scms.service.support.PinLockoutService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class SecurityAlertServiceImpl implements SecurityAlertService {

    private final SecurityAlertRepository alertRepository;
    private final PinLockoutService pinLockoutService;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;

    @Override
    @Transactional(readOnly = true)
    public SecurityAlertSummaryDto listAlerts(Integer requestedBranchId, boolean activeOnly, int limit) {
        var ctx = BranchContext.current().orElse(null);
        // Quản lý chi nhánh chưa gắn chi nhánh -> trả rỗng an toàn thay vì lỗi banner header.
        if (ctx != null && !ctx.isCoo() && ctx.branchId() == null) {
            return new SecurityAlertSummaryDto(0, 0, List.of());
        }

        Integer scopedBranchId = BranchContext.resolveBranchScope(requestedBranchId);
        int safeLimit = Math.clamp(limit, 1, 50);

        List<SecurityAlert> alerts = alertRepository.findRecentAlerts(
                scopedBranchId, activeOnly, PageRequest.of(0, safeLimit));

        long activeCount = scopedBranchId == null
                ? alertRepository.countByStatus(SecurityAlertStatus.ACTIVE)
                : alertRepository.countByBranchIdAndStatus(scopedBranchId, SecurityAlertStatus.ACTIVE);

        Map<String, Instant> activeLocks = pinLockoutService.findActiveLockMap(scopedBranchId);
        Instant now = Instant.now();

        List<SecurityAlertDto> items = alerts.stream()
                .map(alert -> toDto(alert, activeLocks, now))
                .toList();

        return new SecurityAlertSummaryDto(activeCount, activeLocks.size(), items);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SecurityAlertDto acknowledge(Integer alertId, String note, UserPrincipal actor) {
        SecurityAlert alert = alertRepository.findById(alertId)
                .orElseThrow(() -> new NotFoundException("cảnh báo bảo mật"));
        BranchContext.resolveBranchScope(alert.getBranchId());

        if (alert.getStatus() == SecurityAlertStatus.ACTIVE) {
            alert.setStatus(SecurityAlertStatus.ACKNOWLEDGED);
            alert.setResolvedBy(actor != null ? actor.userId() : null);
            alert.setResolvedByName(resolveActorName(actor));
            alert.setResolvedAt(Instant.now());
            if (StringUtils.hasText(note)) {
                alert.setResolveNote(note.trim());
            }
            alertRepository.save(alert);
        }

        Map<String, Instant> activeLocks = pinLockoutService.findActiveLockMap(alert.getBranchId());
        return toDto(alert, activeLocks, Instant.now());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SecurityAlertDto unlockPin(Integer alertId, String note, UserPrincipal actor) {
        SecurityAlert alert = alertRepository.findById(alertId)
                .orElseThrow(() -> new NotFoundException("cảnh báo bảo mật"));
        BranchContext.resolveBranchScope(alert.getBranchId());

        Integer actorId = actor != null ? actor.userId() : null;
        String actorName = resolveActorName(actor);

        pinLockoutService.unlockPin(alert.getBranchId(), alert.getTargetUserId(), actorId, actorName, note);

        SecurityAlert refreshed = alertRepository.findById(alertId).orElse(alert);
        auditLogService.log(AuditAction.ATTENDANCE_ADJUST, "security_alert", refreshed.getId(), null,
                "Mở khóa PIN sớm cho " + (refreshed.getTargetUserName() != null ? refreshed.getTargetUserName() : "Kiosk chi nhánh #" + refreshed.getBranchId())
                        + " bởi " + actorName);
        log.info("Mở khóa PIN sớm alertId={} branchId={} targetUserId={} actorId={}",
                refreshed.getId(), refreshed.getBranchId(), refreshed.getTargetUserId(), actorId);

        Map<String, Instant> activeLocks = pinLockoutService.findActiveLockMap(refreshed.getBranchId());
        return toDto(refreshed, activeLocks, Instant.now());
    }

    private SecurityAlertDto toDto(SecurityAlert alert, Map<String, Instant> activeLocks, Instant now) {
        boolean currentlyLocked = false;
        long remainingSeconds = 0;
        Instant displayLockedUntil = alert.getLockedUntil();
        if (alert.getAlertType() == SecurityAlertType.KIOSK_PIN_LOCKED) {
            String key = PinLockoutService.lockKey(alert.getBranchId(), alert.getTargetUserId());
            Instant liveLockedUntil = activeLocks.get(key);
            currentlyLocked = liveLockedUntil != null
                    && liveLockedUntil.isAfter(now)
                    && alert.getStatus() != SecurityAlertStatus.UNLOCKED;
            remainingSeconds = currentlyLocked
                    ? Math.max(1, Duration.between(now, liveLockedUntil).toSeconds())
                    : 0;
            displayLockedUntil = currentlyLocked ? liveLockedUntil : alert.getLockedUntil();
        }

        return new SecurityAlertDto(
                alert.getId(),
                alert.getBranchId(),
                alert.getBranchName(),
                alert.getAlertType().name(),
                alert.getSeverity().name(),
                alert.getStatus().name(),
                currentlyLocked,
                alert.getTargetUserId(),
                alert.getTargetUserName(),
                alert.getTargetRole(),
                alert.getFailureCount(),
                displayLockedUntil,
                remainingSeconds,
                alert.getTitle(),
                alert.getMessage(),
                alert.getResolvedBy(),
                alert.getResolvedByName(),
                alert.getResolvedAt(),
                alert.getResolveNote(),
                alert.getCreatedAt(),
                alert.getReferenceShiftId()
        );
    }

    private String resolveActorName(UserPrincipal actor) {
        if (actor == null) {
            return "Hệ thống";
        }
        if (StringUtils.hasText(actor.fullName())) {
            return actor.fullName();
        }
        if (actor.userId() != null) {
            return userRepository.findById(actor.userId())
                    .map(User::getFullName)
                    .orElse(StringUtils.hasText(actor.username()) ? actor.username() : "Quản lý");
        }
        return StringUtils.hasText(actor.username()) ? actor.username() : "Quản lý";
    }
}
