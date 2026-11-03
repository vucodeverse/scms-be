package edu.fpt.sba.scms.dto.request;

import edu.fpt.sba.scms.common.enums.BranchStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/** Body {@code PATCH /branches/{id}/status} (IMPLEMENTATION.md §10.3). */
@Schema(name = "SaveBranchStatusRequest")
public record SaveBranchStatusRequest(
        @NotNull(message = "Trạng thái là bắt buộc.")
        BranchStatus status) {
}
