package edu.fpt.sba.scms.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Response payload sau khi lập phiếu hủy kho thành công (UC30 §5.3).
 */
@Schema(name = "StockDisposalSlipResponse", description = "Thông tin phiếu hủy kho và chi tiết các dòng giao dịch tổn thất")
public record StockDisposalSlipResponse(
        @Schema(description = "Mã phiếu hủy kho tự sinh (PHK-YYYYMMDD-XXX)", example = "PHK-20261107-001")
        String slipCode,

        @Schema(description = "Mã chi nhánh hủy hàng", example = "2")
        Integer branchId,

        @Schema(description = "Tên chi nhánh", example = "SCMS Coffee Chi nhánh Quận 1")
        String branchName,

        @Schema(description = "Phân loại nguyên nhân hủy", example = "COOLING_FAILURE")
        String reasonCategory,

        @Schema(description = "Lý do giải trình chi tiết", example = "Sữa tươi Dalat Milk lô 01/11 bị chua do hỏng lốc làm mát tủ bảo quản đêm 06/11")
        String reason,

        @Schema(description = "Đường dẫn ảnh chụp hiện trường / hàng hỏng", example = "uploads/evidence/20261107_spoiled_milk_lot01.jpg")
        String evidenceFileId,

        @Schema(description = "Họ tên người lập và duyệt phiếu hủy (Branch Manager)", example = "Nguyễn Văn Quản Lý")
        String authorizedBy,

        @Schema(description = "Thời điểm lập phiếu hủy (ISO-8601 UTC)")
        Instant disposedAt,

        @Schema(description = "Tổng giá trị thiệt hại tài chính (VND)", example = "720000")
        BigDecimal totalFinancialLoss,

        @Schema(description = "Danh sách chi tiết các mặt hàng đã hủy")
        List<ItemResponse> items
) {

    @Schema(name = "StockDisposalItemResponse", description = "Chi tiết một dòng nguyên vật liệu trong phiếu hủy")
    public record ItemResponse(
            @Schema(description = "Mã giao dịch kho (stock_transaction_id)", example = "110")
            Long transactionId,

            @Schema(description = "Mã nguyên vật liệu", example = "8")
            Long ingredientId,

            @Schema(description = "Tên nguyên vật liệu", example = "Sữa tươi thanh trùng Dalat Milk")
            String ingredientName,

            @Schema(description = "Đơn vị kiện lớn (BR-41)", example = "Thùng 12 hộp")
            String packUnit,

            @Schema(description = "Đơn vị cơ sở định lượng", example = "hộp")
            String baseUnit,

            @Schema(description = "Số lượng kiện lớn đã hủy", example = "2.0")
            BigDecimal quantityPacks,

            @Schema(description = "Số lượng quy đổi sang đơn vị cơ sở đã trừ kho (BR-42)", example = "24.0")
            BigDecimal quantityBaseDeducted,

            @Schema(description = "Giá vốn cho 1 kiện hàng (VND)", example = "360000")
            BigDecimal unitCost,

            @Schema(description = "Thiệt hại tài chính cho dòng này (VND)", example = "720000")
            BigDecimal lineLoss,

            @Schema(description = "Tồn kho còn lại theo đơn vị cơ sở", example = "98.0")
            BigDecimal remainingQtyBase,

            @Schema(description = "Tồn kho còn lại quy ra kiện lớn", example = "8.17")
            BigDecimal remainingPackUnits,

            @Schema(description = "Trạng thái cờ tồn thấp sau khi hủy hàng (BR-45)", example = "false")
            Boolean lowStockFlag
    ) {
    }
}
