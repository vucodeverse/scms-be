package edu.fpt.sba.scms.common.specification;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.Arrays;
import java.util.Collection;
import java.util.Locale;
import java.util.Objects;

/**
 * Ghép điều kiện lọc "khi có mới áp": mỗi điều kiện nhận giá trị có thể null/rỗng để Specification của từng danh
 * sách không phải lặp lại {@code value == null ? null : ...}.
 * <p>
 * Điều kiện trả {@code null} khi không áp dụng; ghép bằng {@link #allOf(Specification[])} để bỏ qua chúng.
 */
public final class Specifications {

    private Specifications() {
    }

    /**
     * Ghép nhiều điều kiện, <b>bỏ qua</b> phần tử null (điều kiện không áp dụng).
     * <p>
     * Không dùng {@code Specification.allOf/where} của framework: Spring Data 4 kiểm tra "tuples must not contain
     * null" và ném lỗi thay vì bỏ qua, khiến filter tuỳ chọn không dùng được.
     */
    @SafeVarargs
    public static <T> Specification<T> allOf(Specification<T>... parts) {
        return (root, query, cb) -> cb.and(Arrays.stream(parts)
                .filter(Objects::nonNull)
                .map(part -> part.toPredicate(root, query, cb))
                .toArray(Predicate[]::new));
    }

    /** Lọc {@code attribute = value} khi value khác null. */
    public static <T, V> Specification<T> equalWhenPresent(String attribute, V value) {
        return value == null ? null : (root, query, cb) -> cb.equal(root.get(attribute), value);
    }

    /**
     * Lọc "chứa, không phân biệt hoa thường" trên một hoặc nhiều cột/đường quan hệ khi từ khoá khác rỗng.
     * Các cột nối bằng OR.
     *
     * @param keyword từ khoá thô của client; null/rỗng → không lọc
     * @param columns cột cần tìm, xem {@link TextMatch#of(String)} / {@link TextMatch#joined(String, String)}
     */
    public static <T> Specification<T> containsWhenPresent(String keyword, TextMatch... columns) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        String pattern = likePattern(keyword);
        return (root, query, cb) -> cb.or(Arrays.stream(columns)
                .map(column -> column.likeIgnoreCase(cb, root, pattern))
                .toArray(Predicate[]::new));
    }

    /** Lọc {@code attribute IN values} khi danh sách không rỗng. */
    public static <T, V> Specification<T> inWhenNotEmpty(String attribute, Collection<V> values) {
        return values == null || values.isEmpty() ? null : (root, query, cb) -> root.get(attribute).in(values);
    }

    /** Mẫu {@code LIKE} đã escape ({@code escape '!'}) và viết thường để khớp {@code lower(...)}. */
    static String likePattern(String keyword) {
        String escaped = keyword.trim()
                .replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_");
        return "%" + escaped.toLowerCase(Locale.ROOT) + "%";
    }
}
