package edu.fpt.sba.scms.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/** Dòng {@code GET /roles} (IMPLEMENTATION.md §10.2) — FE dùng để render dropdown role. */
@Schema(name = "RoleDto")
public record RoleDto(
        Integer roleId,
        String code,
        String name,
        String description,
        @Schema(description = "Số user đang ACTIVE giữ role này") long userCount) {
}
