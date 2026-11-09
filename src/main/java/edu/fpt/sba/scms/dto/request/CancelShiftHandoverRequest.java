package edu.fpt.sba.scms.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

@Schema(name = "CancelShiftHandoverRequest", description = "Hủy yêu cầu bàn giao két đang chờ nhận")
public record CancelShiftHandoverRequest(
        @Schema(description = "Lý do hủy bàn giao", example = "Người nhận chưa kịp đến, quản lý tạm hủy chờ xử lý")
        @Size(max = 500, message = "Lý do hủy tối đa 500 ký tự")
        String reason
) {
}
