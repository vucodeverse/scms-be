package edu.fpt.sba.scms.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Schema(name = "PendingRequisitionDto", description = "Đơn đề xuất xin cấp hàng từ chi nhánh chờ COO điều phối đa kho")
public record PendingRequisitionDto(
        @Schema(description = "ID đơn đề xuất", example = "108")
        Long requisitionId,

        @Schema(description = "Mã đơn đề xuất (PR-YYYYMMDD-XXX)", example = "PR-20261111-001")
        String requisitionCode,

        @Schema(description = "Mã chi nhánh xin cấp", example = "4")
        Integer branchId,

        @Schema(description = "Tên chi nhánh xin cấp hàng", example = "Chi nhánh Highlands Cầu Giấy")
        String branchName,

        @Schema(description = "Trạng thái đơn (PENDING, APPROVED, COMPLETED)", example = "PENDING")
        String status,

        @Schema(description = "Ghi chú đơn đề xuất", example = "Xin cấp bổ sung nguyên liệu cho tuần mới")
        String notes,

        @Schema(description = "Thời gian tạo đơn")
        Instant createdAt,

        @Schema(description = "Danh sách mặt hàng đề xuất xin cấp")
        List<Item> items
) {
    public record Item(
            @Schema(description = "Mã nguyên vật liệu", example = "1")
            Long ingredientId,

            @Schema(description = "Tên nguyên vật liệu", example = "Hạt Cà phê Robusta")
            String ingredientName,

            @Schema(description = "Đơn vị đóng gói", example = "Bao 5kg")
            String packUnit,

            @Schema(description = "Đơn vị cơ sở", example = "g")
            String baseUnit,

            @Schema(description = "Định lượng cơ sở trong 1 kiện (unitsPerPack)", example = "10000.0000")
            BigDecimal unitsPerPack,

            @Schema(description = "Số lượng kiện / thùng chẵn xin cấp (cartonsRequested)", example = "5")
            Integer cartonsRequested,

            @Schema(description = "Tồn khả dụng tại 3 Hub Kho Tổng Vùng (Hà Nội, Đà Nẵng, TP.HCM)")
            List<AvailableHub> availableHubs,

            @Schema(description = "Tổng số thùng được COO phê duyệt", example = "5")
            Integer totalCartonsApproved,

            @Schema(description = "Chi tiết phân bổ từ các kho nguồn đã duyệt")
            List<Allocation> allocations,

            @Schema(description = "Số thùng đạt chuẩn quán thực nhận vào kho (Tăng tồn kho)", example = "4")
            Integer cartonsReceived,

            @Schema(description = "Số thùng bục vỡ / hư hại phát hiện khi nhận hàng (Không tăng tồn kho)", example = "1")
            Integer cartonsDamaged,

            @Schema(description = "Lý do bục vỡ / hư hại ghi nhận biên bản sự cố", example = "Thùng carton bị rách bục, đổ vỡ trên xe vận chuyển")
            String damageReason,

            @Schema(description = "Link ảnh bằng chứng hiện trường hàng bục vỡ để claim đền bù vận chuyển", example = "https://example.com/damage-01.jpg")
            String evidenceImageUrl
    ) {
    }

    public record AvailableHub(
            @Schema(description = "Mã Hub Kho Tổng (1: DC-HN, 2: DC-DN, 3: DC-HCM)", example = "1")
            Integer warehouseId,

            @Schema(description = "Tên Hub Kho Tổng", example = "Kho Tổng Miền Bắc (Hà Nội)")
            String warehouseName,

            @Schema(description = "Mã code kho", example = "DC-HN")
            String warehouseCode,

            @Schema(description = "Số lượng thùng / kiện khả dụng tại kho", example = "120.00")
            BigDecimal availableCartons
    ) {
    }

    public record Allocation(
            @Schema(description = "Mã kho nguồn xuất hàng", example = "1")
            Integer sourceWarehouseId,

            @Schema(description = "Tên kho nguồn", example = "Kho Tổng Miền Bắc (Hà Nội)")
            String sourceWarehouseName,

            @Schema(description = "Số thùng xuất từ kho này", example = "2")
            Integer cartons
    ) {
    }
}
