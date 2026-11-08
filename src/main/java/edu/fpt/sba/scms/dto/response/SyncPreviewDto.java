package edu.fpt.sba.scms.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(name = "SyncPreviewDto", description = "Dữ liệu cấu hình chuỗi sẵn sàng phát hành đồng bộ (UC14)")
public record SyncPreviewDto(
        @Schema(description = "Danh sách chi nhánh mục tiêu")
        List<BranchDto> branches,

        @Schema(description = "Danh sách danh mục")
        List<CategoryDto> categories,

        @Schema(description = "Danh sách món ăn & bảng giá theo size")
        List<ProductDto> products,

        @Schema(description = "Danh sách voucher")
        List<VoucherDto> vouchers
) {
}

