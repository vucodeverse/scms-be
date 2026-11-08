package edu.fpt.sba.scms.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

public record CreateStocktakeSessionRequest(
        @Schema(description = "Loại đợt kiểm kê: DAILY_CLOSING, PERIODIC_WEEKLY, SPOT_AUDIT", example = "DAILY_CLOSING")
        String roundType,

        @Schema(description = "Họ tên nhân viên làm chứng", example = "Nguyễn Văn Pha Chế (Trưởng ca)")
        @Size(max = 100, message = "Tên người làm chứng tối đa 100 ký tự")
        String witnessName,

        @Schema(description = "Ghi chú phiên kiểm kê", example = "Kiểm kê định kỳ chốt sổ ca tối")
        @Size(max = 500, message = "Ghi chú tối đa 500 ký tự")
        String notes
) {
}
