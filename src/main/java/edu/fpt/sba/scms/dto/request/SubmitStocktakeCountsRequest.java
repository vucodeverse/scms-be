package edu.fpt.sba.scms.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

public record SubmitStocktakeCountsRequest(
        @Schema(description = "Danh sách số đếm thực tế của các nguyên liệu kiểm kê")
        @NotEmpty(message = "Danh sách số đếm thực tế không được để trống")
        @Valid
        List<CountItem> counts
) {
    public record CountItem(
            @Schema(description = "Mã ID của nguyên vật liệu", example = "5")
            @NotNull(message = "Mã nguyên vật liệu không được để trống")
            Long ingredientId,

            @Schema(description = "Số lượng đếm thực tế ngoài kho (đơn vị cơ sở)", example = "30500.0")
            @NotNull(message = "Số lượng đếm thực tế không được để trống")
            @DecimalMin(value = "0.0", message = "Số lượng đếm thực tế không được âm")
            BigDecimal countedQuantity,

            @Schema(description = "Giải trình nguyên nhân nếu có thừa/thiếu", example = "Hao hụt do rơi vãi khi xay cà phê")
            @Size(max = 255, message = "Ghi chú giải trình tối đa 255 ký tự")
            String notes
    ) {
    }
}
