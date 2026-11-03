package edu.fpt.sba.scms.dto.response;

import edu.fpt.sba.scms.common.enums.UserStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/** Chi tiết {@code GET /users/{id}} (IMPLEMENTATION.md §10.2.1). */
@Schema(name = "UserDetailDto")
public record UserDetailDto(
        Integer id,
        String fullName,
        String phone,
        String email,
        String position,
        String username,
        String roleCode,
        Integer branchId,
        String branchName,
        UserStatus status,
        boolean hasPin,
        boolean hasCard,
        boolean hasPassword,
        String cardCode,
        Long hourlyRate,
        Instant pinSetAt,
        Instant createdAt,
        Instant updatedAt) {
}
