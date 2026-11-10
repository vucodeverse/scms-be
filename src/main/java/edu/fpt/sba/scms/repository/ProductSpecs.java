package edu.fpt.sba.scms.repository;

import edu.fpt.sba.scms.common.enums.ProductStatus;
import edu.fpt.sba.scms.common.specification.Specifications;
import edu.fpt.sba.scms.common.specification.TextMatch;
import edu.fpt.sba.scms.model.Category;
import edu.fpt.sba.scms.model.Product;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import static edu.fpt.sba.scms.common.specification.Specifications.allOf;
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
                keywordPredicate(keyword),
                equalWhenPresent("categoryId", categoryId),
                equalWhenPresent("status", status));
    }

    private static Specification<Product> keywordPredicate(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        String pattern = Specifications.likePattern(keyword);
        return (root, query, cb) -> {
            Predicate byProductName = TextMatch.of("name").likeIgnoreCase(cb, root, pattern);
            Subquery<Integer> categorySubquery = query.subquery(Integer.class);
            Root<Category> catRoot = categorySubquery.from(Category.class);
            categorySubquery.select(catRoot.get("id"))
                    .where(cb.and(
                            cb.equal(catRoot.get("id"), root.get("categoryId")),
                            TextMatch.of("name").likeIgnoreCase(cb, catRoot, pattern)));
            return cb.or(byProductName, cb.exists(categorySubquery));
        };
    }
}
