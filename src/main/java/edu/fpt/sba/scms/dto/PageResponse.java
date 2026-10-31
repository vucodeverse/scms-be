package edu.fpt.sba.scms.dto;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/** Trang dữ liệu trả FE (IMPLEMENTATION.md §10: page bắt đầu từ 1). */
public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

    public static <T> PageResponse<T> of(Page<T> page) {
        return new PageResponse<>(page.getContent(), page.getNumber() + 1, page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }

    public static <E, T> PageResponse<T> of(Page<E> page, Function<E, T> mapper) {
        return of(page.map(mapper));
    }
}
