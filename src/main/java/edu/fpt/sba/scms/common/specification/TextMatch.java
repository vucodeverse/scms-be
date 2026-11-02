package edu.fpt.sba.scms.common.specification;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Root;

/** Một phần tử cột cần tìm "chứa": tên thuộc tính + cách lấy path (path bình thường hoặc theo quan hệ). */
public interface TextMatch {

    /** Ký tự escape dùng thống nhất với {@link Specifications#likePattern(String)}. */
    char ESCAPE = '!';

    /** Thuộc tính của chính entity đang lọc. */
    static TextMatch of(String attribute) {
        return new TextMatch() {
            @Override
            public <T> Expression<String> path(Root<T> root) {
                return root.get(attribute);
            }

            @Override
            public String toString() {
                return attribute;
            }
        };
    }

    /** Thuộc tính của entity liên kết theo đường quan hệ (vd. {@code "category"}, {@code "name"}). */
    static TextMatch joined(String association, String attribute) {
        return new TextMatch() {
            @Override
            public <T> Expression<String> path(Root<T> root) {
                Path<Object> joined = root.get(association);
                return joined.get(attribute);
            }

            @Override
            public String toString() {
                return association + "." + attribute;
            }
        };
    }

    <T> Expression<String> path(Root<T> root);

    /** {@code lower(path) LIKE pattern ESCAPE '!'} — so khớp không phân biệt hoa thường trên mọi DB. */
    default <T> jakarta.persistence.criteria.Predicate likeIgnoreCase(CriteriaBuilder cb, Root<T> root,
                                                                      String lowerCasePattern) {
        Expression<String> text = cb.lower(path(root).as(String.class));
        return cb.like(text, lowerCasePattern, ESCAPE);
    }
}
