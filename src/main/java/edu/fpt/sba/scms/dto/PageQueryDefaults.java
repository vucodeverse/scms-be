package edu.fpt.sba.scms.dto;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * Giá trị mặc định của phân trang (master data: 10 phần tử/trang).
 * Controller khai báo chúng làm {@code defaultValue} của query param; service dùng {@link #toPageable} để thay
 * null bằng mặc định — nhờ vậy client bỏ trống param vẫn đúng trang mặc định, và OpenAPI hiển thị đúng giá trị.
 */
public final class PageQueryDefaults {

    /** Trang đầu tiên — FE gửi 1, Spring đếm từ 0. */
    public static final String DEFAULT_PAGE = "1";
    public static final String DEFAULT_SIZE = "10";
    /** Chặn client xin trang quá lớn (DoS bằng kích thước trang). */
    public static final int MAX_SIZE = 100;

    private PageQueryDefaults() {
    }

    /**
     * @param page 1-based; null → 1
     * @param size null → 10
     */
    public static Pageable toPageable(Integer page, Integer size, Sort sort) {
        int pageNumber = page == null ? Integer.parseInt(DEFAULT_PAGE) : page;
        int pageSize = size == null ? Integer.parseInt(DEFAULT_SIZE) : size;
        return PageRequest.of(pageNumber - 1, pageSize, sort);
    }
}
