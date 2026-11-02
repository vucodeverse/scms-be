package edu.fpt.sba.scms.dto.request;

import edu.fpt.sba.scms.common.enums.DiscountType;
import edu.fpt.sba.scms.common.enums.RecordStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Body {@code POST /vouchers} và {@code PUT /vouchers/{id}} (UC13, §10.8).
 * <p>
 * Liên field (PERCENT ≤ 100, start ≤ end, sửa khi đã dùng) kiểm ở service để trả một lỗi nghiệp vụ gọn,
 * không phải danh sách lỗi field rời rạc.
 *
 * @param code read-only khi cập nhật: gửi khác mã hiện tại → 422 CROSS_FIELD_INVALID
 */
@Schema(name = "SaveVoucherRequest")
public record SaveVoucherRequest(
        @NotBlank(message = "Mã voucher là bắt buộc.")
        @Pattern(regexp = "^[A-Z0-9_-]{1,15}$",
                message = "Mã voucher gồm chữ in hoa, số, gạch ngang/gạch dưới, tối đa 15 ký tự.")
        @Schema(example = "KHAITRUONG")
        String code,

        @NotNull(message = "Kiểu giảm giá là bắt buộc.")
        DiscountType discountType,

        @NotNull(message = "Giá trị giảm giá là bắt buộc.")
        @DecimalMin(value = "0", inclusive = false, message = "Giá trị giảm giá phải > 0.")
        @Digits(integer = 19, fraction = 0, message = "Giá trị giảm giá phải là số nguyên.")
        BigDecimal discountValue,

        @DecimalMin(value = "0", message = "Đơn tối thiểu không được âm.")
        @Digits(integer = 19, fraction = 0, message = "Đơn tối thiểu phải là số nguyên VND.")
        BigDecimal minOrderAmount,

        @NotNull(message = "Ngày bắt đầu là bắt buộc.")
        Instant startDate,

        @NotNull(message = "Ngày kết thúc là bắt buộc.")
        Instant endDate,

        @Min(value = 1, message = "Giới hạn lượt dùng phải >= 1.")
        @Max(value = 1_000_000, message = "Giới hạn lượt dùng tối đa 1.000.000.")
        Integer usageLimit,

        @NotNull(message = "Trạng thái là bắt buộc.")
        RecordStatus status,

        @Size(max = 500, message = "Một lần tối đa 500 sản phẩm.")
        List<@NotNull @Min(value = 1, message = "productId không hợp lệ.") Integer> productIds,

        @Size(max = 500, message = "Một lần tối đa 500 danh mục.")
        List<@NotNull @Min(value = 1, message = "categoryId không hợp lệ.") Integer> categoryIds) {
}
