package edu.fpt.sba.scms.dto.response;

import edu.fpt.sba.scms.common.enums.UserStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/** Dòng danh sách {@code GET /users} (IMPLEMENTATION.md §10.2.1). KHÔNG bao giờ chứa hash/PIN. */
@Schema(name = "UserDto")
public record UserDto(
        Integer id,
        String fullName,
        String phone,
        String email,
        @Schema(description = "Chức danh (position) hiển thị trên POS/Kiosk") String position,
        String username,
        @Schema(example = "COO") String roleCode,
        Integer branchId,
        String branchName,
        UserStatus status,
        boolean hasPin,
        boolean hasCard,
        String cardCode,
        @Schema(description = "Lương/giờ hiện hành (VND); null khi chưa có wage_rate") Long hourlyRate,
        Instant createdAt) {
}
