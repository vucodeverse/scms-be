package edu.fpt.sba.scms.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

@Schema(name = "CreateRequisitionRequest", description = "Dữ liệu lập đơn xin cấp hàng từ chi nhánh")
public record CreateRequisitionRequest(
        @Schema(description = "Mã chi nhánh xin cấp hàng (tuỳ chọn, nếu bỏ trống lấy theo BranchContext)", example = "4")
        Integer branchId,

        @Schema(description = "Ghi chú đơn đề xuất", example = "Xin cấp bổ sung nguyên liệu hạt cà phê và sữa")
        @Size(max = 500, message = "Ghi chú tối đa 500 ký tự.")
        String notes,

        @Schema(description = "Danh sách mặt hàng xin cấp (nguyên kiện chẵn cartons >= 1)")
        @NotEmpty(message = "Danh sách mặt hàng xin cấp không được để trống.")
        @Valid
        List<Item> items
) {
    public record Item(
            @Schema(description = "Mã nguyên vật liệu", example = "1")
            @NotNull(message = "Mã nguyên vật liệu không được để trống.")
            Long ingredientId,

            @Schema(description = "Số thùng/bao chẵn cần xin (Rule 1: cartons >= 1, nguyên kiện chẵn)", example = "5")
            @NotNull(message = "Số lượng thùng cần xin không được để trống.")
            @Min(value = 1, message = "Số thùng cần xin phải là số nguyên dương >= 1 (Rule 1: Nguyên kiện chẵn).")
            Integer cartonsRequested
    ) {
    }
}
