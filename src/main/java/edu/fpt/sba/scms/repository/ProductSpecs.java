package edu.fpt.sba.scms.repository;

import edu.fpt.sba.scms.common.enums.ProductStatus;
import edu.fpt.sba.scms.common.specification.TextMatch;
import edu.fpt.sba.scms.model.Product;
import org.springframework.data.jpa.domain.Specification;
import static edu.fpt.sba.scms.common.specification.Specifications.allOf;
import static edu.fpt.sba.scms.common.specification.Specifications.containsWhenPresent;
import static edu.fpt.sba.scms.common.specification.Specifications.equalWhenPresent;

/**
 * Điều kiện lọc danh sách sản phẩm (UC10, §10.7). Nhận tham số thô để tầng repository không phụ thuộc {@code dto}.
 */
public final class ProductSpecs {

    private ProductSpecs() {
    }

    /** @param keyword tìm theo tên sản phẩm hoặc tên danh mục; null/rỗng = không lọc */
    public static Specification<Product> filter(String keyword, Integer categoryId, ProductStatus status) {
        return allOf(
                containsWhenPresent(keyword, TextMatch.of("name"), TextMatch.joined("category", "name")),
                equalWhenPresent("categoryId", categoryId),
                equalWhenPresent("status", status));
    }
}
