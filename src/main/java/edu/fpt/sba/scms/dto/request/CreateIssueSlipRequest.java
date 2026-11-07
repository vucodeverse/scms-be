package edu.fpt.sba.scms.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/**
 * Request payload tạo phiếu xuất kho cho quầy Bar (UC29 §5.2).
 */
@Schema(name = "CreateIssueSlipRequest", description = "Dữ liệu tạo phiếu xuất kho nguyên vật liệu cho quầy pha chế Bar")
public record CreateIssueSlipRequest(
        @Schema(description = "Mục đích xuất hàng: BAR_DISPENSING, BRANCH_TRANSFER, TRAINING, STAFF_TRAINING", example = "BAR_DISPENSING")
        @NotBlank(message = "Mục đích xuất không được để trống.")
        @Size(max = 50, message = "Mục đích xuất tối đa 50 ký tự.")
        String purpose,

        @Schema(description = "Quầy pha chế hoặc điểm nhận hàng", example = "Quầy Bar Chính - Tầng 1")
        @NotBlank(message = "Quầy nhận hàng không được để trống.")
        @Size(max = 100, message = "Quầy nhận hàng tối đa 100 ký tự.")
        String destinationBar,

        @Schema(description = "Tên nhân viên pha chế nhận hàng bàn giao", example = "Trần Thị Pha Chế")
        @NotBlank(message = "Tên người nhận không được để trống.")
        @Size(max = 50, message = "Tên người nhận tối đa 50 ký tự.")
        String baristaName,

        @Schema(description = "Ghi chú lý do xuất hoặc thông tin thêm", example = "Xuất phục vụ ca sáng cao điểm cuối tuần")
        @Size(max = 500, message = "Ghi chú tối đa 500 ký tự.")
        String notes,

        @Schema(description = "Danh sách các mặt hàng xuất kho (tối thiểu 1 món)")
        @NotEmpty(message = "Danh sách mặt hàng xuất kho không được để trống.")
        @Valid
        List<Item> items
) {

    @Schema(name = "CreateIssueSlipItem", description = "Chi tiết một mặt hàng trong phiếu xuất")
    public record Item(
            @Schema(description = "Mã nguyên vật liệu", example = "5")
            @NotNull(message = "Mã nguyên vật liệu không được để trống.")
            Long ingredientId,

            @Schema(description = "Số lượng kiện / thùng nguyên xuất đi (> 0)", example = "2.0")
            @NotNull(message = "Số lượng kiện xuất không được để trống.")
            @DecimalMin(value = "0.01", message = "Số lượng kiện xuất phải lớn hơn 0.")
            BigDecimal quantityPacks
    ) {
    }
}
