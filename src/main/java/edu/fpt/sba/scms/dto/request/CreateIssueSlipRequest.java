package edu.fpt.sba.scms.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;
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
 * Request payload tạo phiếu xuất kho nội bộ cho quầy Bar (UC29, FEA-02: Two-Party Transfer).
 */
@Schema(name = "CreateIssueSlipRequest", description = "Dữ liệu tạo phiếu xuất kho nguyên vật liệu cho quầy pha chế Bar (FEA-02)")
public record CreateIssueSlipRequest(
        @Schema(description = "Mã chi nhánh xuất kho", example = "4")
        Integer branchId,

        @Schema(description = "Mục đích xuất hàng: BAR_DISPENSING, BRANCH_TRANSFER, TRAINING, STAFF_TRAINING", example = "BAR_DISPENSING")
        @Size(max = 50, message = "Mục đích xuất tối đa 50 ký tự.")
        String purpose,

        @Schema(description = "Quầy pha chế hoặc điểm nhận hàng", example = "QUẦY BAR CHÍNH")
        @JsonAlias({"destination", "destinationBar"})
        @Size(max = 100, message = "Quầy nhận hàng tối đa 100 ký tự.")
        String destination,

        @Schema(description = "Tên nhân viên pha chế nhận hàng bàn giao (Rule 1: Bắt buộc)", example = "Trần Văn Pha Chế")
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

    public CreateIssueSlipRequest(String purpose, String destination, String baristaName, String notes, List<Item> items) {
        this(null, purpose, destination, baristaName, notes, items);
    }

    public String destinationBar() {
        return destination != null && !destination.isBlank() ? destination : "QUẦY BAR CHÍNH";
    }

    @Schema(name = "CreateIssueSlipItem", description = "Chi tiết một mặt hàng trong phiếu xuất")
    public record Item(
            @Schema(description = "Mã nguyên vật liệu", example = "1")
            @NotNull(message = "Mã nguyên vật liệu không được để trống.")
            Long ingredientId,

            @Schema(description = "Số lượng kiện / thùng nguyên xuất đi (>= 0)", example = "1.0")
            @DecimalMin(value = "0.0", message = "Số lượng kiện xuất không được âm.")
            BigDecimal quantityPacks,

            @Schema(description = "Số lượng đơn vị cơ sở / lẻ xuất đi (>= 0)", example = "500.0")
            @DecimalMin(value = "0.0", message = "Số lượng đơn vị lẻ xuất không được âm.")
            BigDecimal looseUnits
    ) {
        public Item(Long ingredientId, BigDecimal quantityPacks) {
            this(ingredientId, quantityPacks, BigDecimal.ZERO);
        }
    }
}
