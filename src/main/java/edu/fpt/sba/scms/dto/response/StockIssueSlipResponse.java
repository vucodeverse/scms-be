package edu.fpt.sba.scms.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Response payload sau khi xuất kho thành công (UC29 §5.3).
 */
@Schema(name = "StockIssueSlipResponse", description = "Thông tin phiếu xuất kho và chi tiết các dòng giao dịch")
public record StockIssueSlipResponse(
        @Schema(description = "Mã phiếu xuất kho tự sinh (PXK-YYYYMMDD-XXX)", example = "PXK-20261107-002")
        String slipCode,

        @Schema(description = "Mã chi nhánh xuất hàng", example = "2")
        Integer branchId,

        @Schema(description = "Tên chi nhánh", example = "SCMS Coffee Chi nhánh Quận 1")
        String branchName,

        @Schema(description = "Mục đích xuất hàng", example = "BAR_DISPENSING")
        String purpose,

        @Schema(description = "Quầy pha chế đích", example = "Quầy Bar Chính - Tầng 1")
        String destinationBar,

        @Schema(description = "Họ tên Barista nhận hàng", example = "Trần Thị Pha Chế")
        String baristaName,

        @Schema(description = "Họ tên người xuất (Branch Manager)", example = "Nguyễn Văn Quản Lý")
        String issuedBy,

        @Schema(description = "Thời điểm xuất kho (ISO-8601 UTC)")
        Instant issuedAt,

        @Schema(description = "Ghi chú phiếu xuất")
        String notes,

        @Schema(description = "Danh sách chi tiết các mặt hàng đã xuất")
        List<ItemResponse> items
) {

    @Schema(name = "StockIssueItemResponse", description = "Chi tiết một dòng nguyên vật liệu trong phiếu xuất")
    public record ItemResponse(
            @Schema(description = "Mã giao dịch kho (stock_transaction_id)", example = "105")
            Long transactionId,

            @Schema(description = "Mã nguyên vật liệu", example = "5")
            Long ingredientId,

            @Schema(description = "Tên nguyên vật liệu", example = "Hạt Cà phê Robusta Đắk Lắk")
            String ingredientName,

            @Schema(description = "Đơn vị kiện lớn (BR-41)", example = "Bao 5kg")
            String packUnit,

            @Schema(description = "Đơn vị cơ sở định lượng", example = "g")
            String baseUnit,

            @Schema(description = "Số lượng kiện lớn đã xuất", example = "2.0")
            BigDecimal quantityPacks,

            @Schema(description = "Số lượng quy đổi sang đơn vị cơ sở đã trừ kho (BR-42)", example = "10000.0")
            BigDecimal quantityBaseDeducted,

            @Schema(description = "Tồn kho còn lại theo đơn vị cơ sở", example = "32000.0")
            BigDecimal remainingQtyBase,

            @Schema(description = "Tồn kho còn lại quy ra kiện lớn", example = "6.4")
            BigDecimal remainingPackUnits,

            @Schema(description = "Trạng thái cờ tồn thấp sau khi xuất hàng (BR-45)", example = "false")
            Boolean lowStockFlag
    ) {
    }
}
