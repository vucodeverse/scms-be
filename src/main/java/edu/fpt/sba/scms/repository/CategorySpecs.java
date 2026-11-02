package edu.fpt.sba.scms.repository;

import edu.fpt.sba.scms.common.enums.RecordStatus;
import edu.fpt.sba.scms.common.specification.TextMatch;
import edu.fpt.sba.scms.model.Category;
import org.springframework.data.jpa.domain.Specification;
import static edu.fpt.sba.scms.common.specification.Specifications.allOf;

import static edu.fpt.sba.scms.common.specification.Specifications.containsWhenPresent;
import static edu.fpt.sba.scms.common.specification.Specifications.equalWhenPresent;

/**
 * Điều kiện lọc danh sách danh mục quản lý (UC09, §10.7) — luôn loại danh mục hệ thống UNCAT.
 * Nhận tham số thô (không nhận DTO query) để tầng repository không phụ thuộc {@code dto}.
 */
public final class CategorySpecs {

    private CategorySpecs() {
    }

    /**
     * @param keyword tìm trên mã và tên; null/rỗng = không lọc
     * @param status  trạng thái cần lọc; null = không lọc
     */
    public static Specification<Category> managedList(String keyword, RecordStatus status) {
        return allOf(
                onlyManaged(),
                containsWhenPresent(keyword, TextMatch.of("code"), TextMatch.of("name")),
                equalWhenPresent("status", status));
    }

    private static Specification<Category> onlyManaged() {
        return (root, query, cb) -> cb.notEqual(root.get("code"), Category.UNCATEGORIZED_CODE);
    }
}
