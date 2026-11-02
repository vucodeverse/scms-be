package edu.fpt.sba.scms.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Body {@code PUT /menu-categories/sequence} (UC09 — "reorder", §10.7). */
@Schema(name = "OrderCategoryRequest")
public record OrderCategoryRequest(
        @NotEmpty(message = "Danh sách sắp xếp không được rỗng.")
        @Size(max = 500, message = "Một lần tối đa 500 danh mục.")
        List<@NotNull @Valid Item> items) {

    @Schema(name = "OrderCategoryItem")
    public record Item(
            @NotNull(message = "categoryId là bắt buộc.")
            @Min(value = 1, message = "categoryId không hợp lệ.")
            Integer categoryId,

            @NotNull(message = "sortOrder là bắt buộc.")
            @Min(value = 1, message = "Thứ tự phải > 0.")
            Integer sortOrder) {
    }
}
