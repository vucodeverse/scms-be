package edu.fpt.sba.scms.dto.request;

import edu.fpt.sba.scms.common.enums.RecordStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/** Body {@code PATCH /vouchers/{id}/status} (UC13, §10.8). */
@Schema(name = "SaveVoucherStatusRequest")
public record SaveVoucherStatusRequest(
        @NotNull(message = "Trạng thái là bắt buộc.")
        RecordStatus status) {
}
