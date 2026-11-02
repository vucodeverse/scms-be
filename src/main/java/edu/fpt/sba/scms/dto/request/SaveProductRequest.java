package edu.fpt.sba.scms.dto.request;

import edu.fpt.sba.scms.common.enums.ProductSizeName;
import edu.fpt.sba.scms.common.enums.ProductStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Body {@code POST /products} và {@code PUT /products/{id}} (UC10, IMPLEMENTATION.md §10.7). */
@Schema(name = "SaveProductRequest")
public record SaveProductRequest(
        @NotBlank(message = "Tên sản phẩm là bắt buộc.")
        @Size(max = 100, message = "Tên sản phẩm tối đa 100 ký tự.")
        String name,

        @NotNull(message = "Danh mục là bắt buộc.")
        @Min(value = 1, message = "Danh mục không hợp lệ.")
        Integer categoryId,

        @Size(max = 500, message = "Mô tả tối đa 500 ký tự.")
        String description,

        @Size(max = 500, message = "Đường dẫn ảnh tối đa 500 ký tự.")
        String imageUrl,

        @Schema(description = "Size mặc định khi bán nhanh trên POS")
        ProductSizeName defaultSize,

        @NotNull(message = "Trạng thái là bắt buộc.")
        ProductStatus status) {
}
