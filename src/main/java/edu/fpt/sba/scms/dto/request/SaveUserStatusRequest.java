package edu.fpt.sba.scms.dto.request;

import edu.fpt.sba.scms.common.enums.UserStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/** Body {@code PATCH /users/{id}/status} (IMPLEMENTATION.md §10.2.1). */
@Schema(name = "SaveUserStatusRequest")
public record SaveUserStatusRequest(
        @NotNull(message = "Trạng thái là bắt buộc.")
        UserStatus status) {
}
