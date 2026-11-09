package edu.fpt.sba.scms.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

@Schema(name = "SyncConfigResponse", description = "Kết quả đồng bộ cấu hình chuỗi xuống chi nhánh (UC14)")
public record SyncConfigResponse(
        @Schema(description = "Trạng thái thành công", example = "true")
        boolean success,

        @Schema(description = "Danh sách ID chi nhánh đã nhận đồng bộ", example = "[1, 2]")
        List<Integer> syncedBranchIds,

        @Schema(description = "Thời điểm thực hiện đồng bộ")
        Instant syncedAt,

        @Schema(description = "Thông báo kết quả")
        String message,

        @Schema(description = "Số lượng món ăn đã đồng bộ")
        Integer syncedProductCount,

        @Schema(description = "Số lượng danh mục đã đồng bộ")
        Integer syncedCategoryCount,

        @Schema(description = "Số lượng voucher đã đồng bộ")
        Integer syncedVoucherCount,

        @Schema(description = "Tên các món ăn đã được đồng bộ")
        List<String> syncedProductNames
) {
    public SyncConfigResponse(boolean success, List<Integer> syncedBranchIds, Instant syncedAt, String message) {
        this(success, syncedBranchIds, syncedAt, message, 0, 0, 0, List.of());
    }
}
