package edu.fpt.sba.scms.dto.response;

import edu.fpt.sba.scms.common.enums.BranchStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.time.LocalTime;

/** Dòng danh sách {@code GET /branches} (IMPLEMENTATION.md §10.3). */
@Schema(name = "BranchDto")
public record BranchDto(
        Integer id,
        String code,
        String name,
        String address,
        String phone,
        String email,
        String taxCode,
        @Schema(type = "string", example = "07:00") LocalTime openingTime,
        @Schema(type = "string", example = "22:00") LocalTime closingTime,
        Integer tableCount,
        BranchStatus status,
        Integer managerUserId,
        String managerName,
        @Schema(description = "Số nhân sự đang ACTIVE trong chi nhánh") long employeeCount,
        @Schema(description = "Trạng thái máy POS (heartbeat < 2 phút); v1 luôn null khi chưa có heartbeat")
        Boolean posOnline,
        Instant createdAt,
        Instant updatedAt) {
}
