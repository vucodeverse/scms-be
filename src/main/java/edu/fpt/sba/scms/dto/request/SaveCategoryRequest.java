package edu.fpt.sba.scms.dto.request;

import edu.fpt.sba.scms.common.enums.RecordStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Body {@code POST /menu-categories} và {@code PUT /menu-categories/{id}} (UC09, IMPLEMENTATION.md §10.7).
 * Mã danh mục do hệ thống sinh (CAT + id) nên không nằm trong request.
 *
 * @param productIds gửi danh sách → thay toàn bộ sản phẩm của danh mục; bỏ field → giữ nguyên
 */
@Schema(name = "SaveCategoryRequest")
public record SaveCategoryRequest(
        @NotBlank(message = "Tên danh mục là bắt buộc.")
        @Size(max = 100, message = "Tên danh mục tối đa 100 ký tự.")
        String name,

        @Size(max = 500, message = "Mô tả tối đa 500 ký tự.")
        String description,

        @NotNull(message = "Thứ tự hiển thị là bắt buộc.")
        @Min(value = 1, message = "Thứ tự hiển thị phải > 0.")
        @Max(value = 9999, message = "Thứ tự hiển thị tối đa 9999.")
        Integer sortOrder,

        @NotNull(message = "Trạng thái là bắt buộc.")
        RecordStatus status,

        @Size(max = 500, message = "Một lần tối đa 500 sản phẩm.")
        List<@NotNull(message = "productId không được null.")
        @Min(value = 1, message = "productId không hợp lệ.") Integer> productIds) {
}
