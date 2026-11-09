package edu.fpt.sba.scms.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Body {@code PUT /branches/{id}/manager} (IMPLEMENTATION.md §10.3).
 * {@code managerUserId = null} gỡ quản lý hiện tại khỏi chi nhánh để trả về danh sách chưa gán.
 */
@Schema(name = "AssignBranchManagerRequest")
public record AssignBranchManagerRequest(
        @Schema(description = "ID tài khoản BRANCH_MANAGER đang ACTIVE và chưa gắn chi nhánh khác; null để gỡ quản lý")
        Integer managerUserId) {
}
