package edu.fpt.sba.scms.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

@Schema(name = "CreateOrderLineRequest", description = "Dòng món trong đơn hàng (BR-25)")
public record CreateOrderLineRequest(
        @Schema(description = "ID sản phẩm", example = "1")
        @NotNull(message = "Sản phẩm không được để trống")
        Integer productId,

        @Schema(description = "Size sản phẩm", example = "M")
        String size,

        @Schema(description = "Số lượng", example = "2")
        @NotNull(message = "Số lượng không được để trống")
        @Min(value = 1, message = "Số lượng phải >= 1")
        Integer quantity,

        @Schema(description = "Đơn giá món tại thời điểm bán", example = "35000")
        @NotNull(message = "Đơn giá không được để trống")
        @DecimalMin(value = "0.0", message = "Đơn giá phải >= 0")
        BigDecimal unitPrice,

        @Schema(description = "Mức đường", example = "70%")
        String sugarLevel,

        @Schema(description = "Mức đá", example = "100%")
        String iceLevel,

        @Schema(description = "Ghi chú món", example = "Ít đá")
        String note,

        @Schema(description = "Danh sách ID topping kèm theo", example = "[1, 2]")
        List<Integer> toppingIds
) {
    public CreateOrderLineRequest {
        if (toppingIds == null) {
            toppingIds = List.of();
        }
    }
}
