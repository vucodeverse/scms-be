package edu.fpt.sba.scms.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Response payload sau khi nhập kho thành công (UC28 §5.3).
 */
@Schema(name = "StockReceiveSlipResponse", description = "Thông tin phiếu nhập kho và chi tiết các dòng giao dịch")
public record StockReceiveSlipResponse(
        @Schema(description = "Mã phiếu nhập kho tự sinh (PNK-YYYYMMDD-XXX)", example = "PNK-20261107-001")
        String slipCode,

        @Schema(description = "Mã chi nhánh tiếp nhận", example = "2")
        Integer branchId,

        @Schema(description = "Tên chi nhánh", example = "SCMS Coffee Chi nhánh Quận 1")
        String branchName,

        @Schema(description = "Nguồn nhập", example = "SUPPLIER")
        String source,

        @Schema(description = "Tên nhà cung cấp / đối tác chuyển", example = "Công ty Cổ phần Sữa Đà Lạt (Dalat Milk)")
        String supplierName,

        @Schema(description = "Số hóa đơn giao hàng", example = "HD-20261107-99")
        String invoiceRef,

        @Schema(description = "Thời điểm nhận hàng (ISO-8601 UTC)")
        Instant receivedAt,

        @Schema(description = "Họ tên người nhận (Branch Manager)", example = "Nguyễn Văn Quản Lý")
        String receivedBy,

        @Schema(description = "Tổng tiền trước thuế (VND)", example = "7470000")
        BigDecimal subtotalAmount,

        @Schema(description = "Tiền thuế VAT 10% (VND, BR-40)", example = "747000")
        BigDecimal vatAmount,

        @Schema(description = "Tổng tiền phải trả sau thuế (VND)", example = "8217000")
        BigDecimal totalPayable,

        @Schema(description = "Ghi chú lô hàng")
        String notes,

        @Schema(description = "Danh sách chi tiết các mặt hàng đã nhập")
        List<ItemResponse> items
) {

    @Schema(name = "StockReceiveItemResponse", description = "Chi tiết một dòng nguyên vật liệu trong phiếu nhập")
    public record ItemResponse(
            @Schema(description = "Mã giao dịch kho (stock_transaction_id)", example = "101")
            Long transactionId,

            @Schema(description = "Mã nguyên vật liệu", example = "5")
            Long ingredientId,

            @Schema(description = "Mã hiển thị nguyên liệu", example = "ING-005")
            String ingredientCode,

            @Schema(description = "Tên nguyên vật liệu", example = "Hạt Cà phê Robusta Đắk Lắk")
            String ingredientName,

            @Schema(description = "Đơn vị kiện lớn (BR-40)", example = "Bao 5kg")
            String packUnit,

            @Schema(description = "Đơn vị cơ sở định lượng", example = "g")
            String baseUnit,

            @Schema(description = "Hệ số quy đổi kiện -> cơ sở", example = "5000.0")
            BigDecimal unitsPerPack,

            @Schema(description = "Số lượng kiện lớn nhập", example = "5.0")
            BigDecimal cartons,

            @Schema(description = "Số lượng lẻ nhập", example = "0.0")
            BigDecimal looseUnits,

            @Schema(description = "Số lượng quy đổi sang đơn vị cơ sở đã cộng vào kho", example = "25000.0")
            BigDecimal quantityBaseAdded,

            @Schema(description = "Đơn giá mua 1 kiện (VND)", example = "750000")
            BigDecimal purchasePrice,

            @Schema(description = "Thành tiền dòng này (VND)", example = "3750000")
            BigDecimal lineTotal,

            @Schema(description = "Tồn kho mới theo đơn vị cơ sở", example = "42000.0")
            BigDecimal newCurrentQtyBase,

            @Schema(description = "Tồn kho mới quy ra kiện lớn", example = "8.4")
            BigDecimal newCurrentPackUnits,

            @Schema(description = "Trạng thái cờ tồn thấp sau khi nhập hàng (BR-45)", example = "false")
            Boolean lowStockFlag
    ) {
    }
}
