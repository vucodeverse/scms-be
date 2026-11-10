package edu.fpt.sba.scms.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(name = "SecurityAlertDto", description = "Chi tiết cảnh báo bảo mật hoặc yêu cầu phê duyệt ca tại chi nhánh")
public record SecurityAlertDto(
        Integer id,
        Integer branchId,
        String branchName,
        String alertType,
        String severity,
        String status,
        boolean currentlyLocked,
        Integer targetUserId,
        String targetUserName,
        String targetRole,
        int failureCount,
        Instant lockedUntil,
        long remainingLockSeconds,
        String title,
        String message,
        Integer resolvedBy,
        String resolvedByName,
        Instant resolvedAt,
        String resolveNote,
        Instant createdAt,
        Integer referenceShiftId
) {
}
