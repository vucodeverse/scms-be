package edu.fpt.sba.scms.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

@Schema(name = "SyncConfigRequest", description = "Đồng bộ cấu hình chuỗi (danh mục, sản phẩm, voucher) xuống các chi nhánh (UC14)")
public record SyncConfigRequest(
        @Schema(description = "Đồng bộ danh mục món", example = "true")
        Boolean syncCategories,

        @Schema(description = "Đồng bộ sản phẩm & bảng giá", example = "true")
        Boolean syncProducts,

        @Schema(description = "Đồng bộ chương trình khuyến mãi/voucher", example = "true")
        Boolean syncVouchers,

        @Size(max = 500, message = "Tối đa 500 danh mục mỗi lần đồng bộ")
        @Schema(description = "Danh sách ID danh mục được chọn (null hoặc rỗng = tất cả)", example = "[1, 2]")
        List<@NotNull(message = "ID danh mục không được rỗng") @Min(value = 1, message = "ID danh mục phải lớn hơn 0") Integer> categoryIds,

        @Size(max = 500, message = "Tối đa 500 sản phẩm mỗi lần đồng bộ")
        @Schema(description = "Danh sách ID sản phẩm được chọn (null hoặc rỗng = tất cả)", example = "[1, 2, 3]")
        List<@NotNull(message = "ID sản phẩm không được rỗng") @Min(value = 1, message = "ID sản phẩm phải lớn hơn 0") Integer> productIds,

        @Size(max = 500, message = "Tối đa 500 voucher mỗi lần đồng bộ")
        @Schema(description = "Danh sách ID voucher được chọn (null hoặc rỗng = tất cả)", example = "[1, 2]")
        List<@NotNull(message = "ID voucher không được rỗng") @Min(value = 1, message = "ID voucher phải lớn hơn 0") Integer> voucherIds,

        @Schema(description = "Danh sách ID chi nhánh mục tiêu nhận đồng bộ", example = "[1, 2, 3]")
        @NotEmpty(message = "Danh sách chi nhánh mục tiêu không được để trống")
        @Size(max = 200, message = "Tối đa 200 chi nhánh mỗi lần đồng bộ")
        List<@NotNull(message = "ID chi nhánh không được rỗng") @Min(value = 1, message = "ID chi nhánh phải lớn hơn 0") Integer> targetBranchIds
) {
    public SyncConfigRequest {
        if (syncCategories == null) syncCategories = true;
        if (syncProducts == null) syncProducts = true;
        if (syncVouchers == null) syncVouchers = true;
    }
}
