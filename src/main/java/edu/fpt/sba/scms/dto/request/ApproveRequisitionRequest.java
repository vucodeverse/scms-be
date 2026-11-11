package edu.fpt.sba.scms.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

@Schema(name = "ApproveRequisitionRequest", description = "Dữ liệu COO duyệt phân bổ điều phối hàng đa kho tổng")
public record ApproveRequisitionRequest(
        @Schema(description = "Mã đơn đề xuất xin cấp hàng", example = "108")
        @NotNull(message = "Mã đơn đề xuất không được để trống.")
        Long requisitionId,

        @Schema(description = "Danh sách mặt hàng được duyệt phân bổ")
        @NotEmpty(message = "Danh sách mặt hàng phê duyệt không được để trống.")
        @Valid
        List<ApprovedItem> approvedItems
) {
    public record ApprovedItem(
            @Schema(description = "Mã nguyên vật liệu", example = "1")
            @NotNull(message = "Mã nguyên vật liệu không được để trống.")
            Long ingredientId,

            @Schema(description = "Tổng số thùng được duyệt xuất", example = "5")
            @NotNull(message = "Tổng số thùng duyệt không được để trống.")
            @Min(value = 1, message = "Tổng số thùng duyệt phải >= 1.")
            Integer totalCartonsApproved,

            @Schema(description = "Phân bổ từ các kho nguồn (Tùy chọn. Nếu không gửi, hệ thống tự động xuất từ Kho Tổng trung tâm)")
            @Valid
            List<Allocation> allocations
    ) {
    }

    public record Allocation(
            @Schema(description = "Mã kho nguồn xuất hàng (1: DC-HN, 2: DC-DN, 3: DC-HCM)", example = "1")
            @NotNull(message = "Mã kho nguồn không được để trống.")
            Integer sourceWarehouseId,

            @Schema(description = "Số thùng xuất từ kho này (Rule 1: cartons >= 1, nguyên kiện chẵn)", example = "2")
            @NotNull(message = "Số thùng xuất không được để trống.")
            @Min(value = 1, message = "Số thùng xuất từ mỗi kho phải là số nguyên dương >= 1.")
            Integer cartons
    ) {
    }
}
