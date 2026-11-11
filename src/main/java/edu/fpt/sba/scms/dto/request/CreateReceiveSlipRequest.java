package edu.fpt.sba.scms.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Request payload tạo phiếu nhập kho nguyên vật liệu (UC28 §5.2 & FEA-01 Multi-Hub DC).
 */
@Schema(name = "CreateReceiveSlipRequest", description = "Dữ liệu tạo phiếu nhập kho nguyên vật liệu từ NCC hoặc nhận hàng điều phối đa kho")
public record CreateReceiveSlipRequest(
        @Schema(description = "Mã đơn đề xuất xin cấp hàng (nếu nhận theo điều phối đa kho FEA-01)", example = "108")
        Long requisitionId,

        @Schema(description = "Mã chi nhánh nhận hàng", example = "4")
        Integer branchId,

        @Schema(description = "Nguồn nhập hàng: SUPPLIER, INTERNAL_TRANSFER, SURPLUS", example = "SUPPLIER")
        @Pattern(regexp = "^(SUPPLIER|INTERNAL_TRANSFER|SURPLUS)?$", message = "Nguồn nhập phải thuộc: SUPPLIER, INTERNAL_TRANSFER hoặc SURPLUS.")
        String source,

        @Schema(description = "Tên nhà cung cấp hoặc chi nhánh điều chuyển", example = "Công ty Cổ phần Sữa Đà Lạt (Dalat Milk)")
        @Size(max = 150, message = "Tên nhà cung cấp tối đa 150 ký tự.")
        String supplierName,

        @Schema(description = "Số hóa đơn giao hàng / biên bản bàn giao thực tế", example = "HD-20261107-99")
        @Size(max = 50, message = "Số hóa đơn tối đa 50 ký tự.")
        String invoiceRef,

        @Schema(description = "Ghi chú lô hàng", example = "Nhập lô sữa tươi thanh trùng phục vụ cuối tuần")
        @Size(max = 500, message = "Ghi chú tối đa 500 ký tự.")
        String notes,

        @Schema(description = "Danh sách mặt hàng nhập kho (tối thiểu 1 món)")
        @NotEmpty(message = "Danh sách mặt hàng nhập kho không được để trống.")
        @Valid
        List<Item> items
) {

    // Constructor 5 tham số để tương thích 100% với các test cũ
    public CreateReceiveSlipRequest(String source, String supplierName, String invoiceRef, String notes, List<Item> items) {
        this(null, null, source, supplierName, invoiceRef, notes, items);
    }

    @Schema(name = "CreateReceiveSlipItem", description = "Chi tiết một mặt hàng trong phiếu nhập")
    public record Item(
            @Schema(description = "Mã nguyên vật liệu", example = "5")
            @NotNull(message = "Mã nguyên vật liệu không được để trống.")
            Long ingredientId,

            @Schema(description = "Số lượng kiện / thùng chẵn (BR-40)", example = "5.0")
            @DecimalMin(value = "0.0", message = "Số lượng kiện không được âm.")
            BigDecimal cartons,

            @Schema(description = "Số lượng lẻ (hộp, g, ml...)", example = "0.0", defaultValue = "0.0")
            @DecimalMin(value = "0.0", message = "Số lượng lẻ không được âm.")
            BigDecimal looseUnits,

            @Schema(description = "Đơn giá mua cho 1 kiện/thùng (VND)", example = "750000")
            @DecimalMin(value = "0.0", message = "Đơn giá mua không được âm.")
            BigDecimal purchasePrice,

            @Schema(description = "Hạn sử dụng của lô hàng (EXP)", example = "2026-12-15")
            LocalDate expiryDate,

            // Các trường phục vụ luồng nhận hàng theo điều phối đa kho (FEA-01):
            @Schema(description = "Số thùng đạt chuẩn nghiệm thu", example = "5")
            Integer cartonsAccepted,

            @Schema(description = "Số thùng bục vỡ / hỏng từ chối nhận", example = "0")
            Integer cartonsRejected,

            @Schema(description = "Lý do bục vỡ / hỏng (bắt buộc khi cartonsRejected > 0)", example = "Bao bì rách khi vận chuyển")
            String damageReason,

            @Schema(description = "Link ảnh hiện trường (bắt buộc khi cartonsRejected > 0)", example = "https://img.scms.local/damage-01.jpg")
            String evidenceImageUrl
    ) {
        // Constructor 5 tham số cũ:
        public Item(Long ingredientId, BigDecimal cartons, BigDecimal looseUnits, BigDecimal purchasePrice, LocalDate expiryDate) {
            this(ingredientId, cartons, looseUnits, purchasePrice, expiryDate, null, null, null, null);
        }
    }
}
