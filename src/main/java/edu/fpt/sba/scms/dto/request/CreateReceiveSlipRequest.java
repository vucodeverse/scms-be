package edu.fpt.sba.scms.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Request payload tạo phiếu nhập kho nguyên vật liệu (UC28 §5.2).
 */
@Schema(name = "CreateReceiveSlipRequest", description = "Dữ liệu tạo phiếu nhập kho nguyên vật liệu từ NCC hoặc điều chuyển")
public record CreateReceiveSlipRequest(
        @Schema(description = "Nguồn nhập hàng: SUPPLIER, INTERNAL_TRANSFER, SURPLUS", example = "SUPPLIER")
        @NotBlank(message = "Nguồn nhập không được để trống.")
        @Pattern(regexp = "^(SUPPLIER|INTERNAL_TRANSFER|SURPLUS)$", message = "Nguồn nhập phải thuộc: SUPPLIER, INTERNAL_TRANSFER hoặc SURPLUS.")
        String source,

        @Schema(description = "Tên nhà cung cấp hoặc chi nhánh điều chuyển", example = "Công ty Cổ phần Sữa Đà Lạt (Dalat Milk)")
        @NotBlank(message = "Tên nhà cung cấp / đối tác chuyển không được để trống.")
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

    @Schema(name = "CreateReceiveSlipItem", description = "Chi tiết một mặt hàng trong phiếu nhập")
    public record Item(
            @Schema(description = "Mã nguyên vật liệu", example = "5")
            @NotNull(message = "Mã nguyên vật liệu không được để trống.")
            Long ingredientId,

            @Schema(description = "Số lượng kiện / thùng chẵn (BR-40)", example = "5.0")
            @NotNull(message = "Số lượng kiện không được để trống.")
            @DecimalMin(value = "0.0", message = "Số lượng kiện không được âm.")
            BigDecimal cartons,

            @Schema(description = "Số lượng lẻ (hộp, g, ml...)", example = "0.0", defaultValue = "0.0")
            @DecimalMin(value = "0.0", message = "Số lượng lẻ không được âm.")
            BigDecimal looseUnits,

            @Schema(description = "Đơn giá mua cho 1 kiện/thùng (VND)", example = "750000")
            @NotNull(message = "Đơn giá mua không được để trống.")
            @DecimalMin(value = "0.0", message = "Đơn giá mua không được âm.")
            BigDecimal purchasePrice,

            @Schema(description = "Hạn sử dụng của lô hàng (EXP)", example = "2026-12-15")
            LocalDate expiryDate
    ) {
    }
}
