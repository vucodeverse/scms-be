package edu.fpt.sba.scms.dto.response;

import edu.fpt.sba.scms.common.enums.BranchStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.time.LocalTime;
import java.util.List;

/** Chi tiết {@code GET /branches/{id}} — kèm danh sách nhân sự ACTIVE (IMPLEMENTATION.md §10.3). */
@Schema(name = "BranchDetailDto")
public record BranchDetailDto(
        Integer id,
        String code,
        String name,
        String address,
        String phone,
        String email,
        String taxCode,
        LocalTime openingTime,
        LocalTime closingTime,
        Integer tableCount,
        BranchStatus status,
        Integer managerUserId,
        String managerName,
        long employeeCount,
        Boolean posOnline,
        List<UserDto> employees,
        Instant createdAt,
        Instant updatedAt) {
}
