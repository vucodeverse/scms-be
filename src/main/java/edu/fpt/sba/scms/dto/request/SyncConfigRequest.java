package edu.fpt.sba.scms.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

@Schema(name = "SyncConfigRequest", description = "Đồng bộ cấu hình chuỗi (danh mục, sản phẩm, voucher) xuống các chi nhánh (UC14)")
public record SyncConfigRequest(
        @Schema(description = "Đồng bộ danh mục món", example = "true")
        Boolean syncCategories,

        @Schema(description = "Đồng bộ sản phẩm & bảng giá", example = "true")
        Boolean syncProducts,

        @Schema(description = "Đồng bộ chương trình khuyến mãi/voucher", example = "true")
        Boolean syncVouchers,

        @Schema(description = "Danh sách ID chi nhánh mục tiêu nhận đồng bộ", example = "[1, 2, 3]")
        @NotEmpty(message = "Danh sách chi nhánh mục tiêu không được để trống")
        List<Integer> targetBranchIds
) {
    public SyncConfigRequest {
        if (syncCategories == null) syncCategories = true;
        if (syncProducts == null) syncProducts = true;
        if (syncVouchers == null) syncVouchers = true;
    }
}

