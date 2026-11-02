package edu.fpt.sba.scms.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.data.domain.Page;

import java.util.List;

/**
 * Trang dữ liệu trả FE (IMPLEMENTATION.md §10: {@code Paginated<T>}, {@code page} bắt đầu từ 1).
 * Service trả {@link Page} của Spring; controller bọc bằng {@link #of(Page)} để FE không thấy chỉ số trang 0-based.
 */
@Schema(name = "PageResponse", description = "Một trang dữ liệu — page bắt đầu từ 1")
public record PageResponse<T>(
        List<T> content,
        @Schema(example = "1") int page,
        @Schema(example = "10") int size,
        long totalElements,
        int totalPages) {

    public static <T> PageResponse<T> of(Page<T> page) {
        return new PageResponse<>(page.getContent(), page.getNumber() + 1, page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }
}
