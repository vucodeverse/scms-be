package edu.fpt.sba.scms.repository;

import edu.fpt.sba.scms.common.enums.UserStatus;
import edu.fpt.sba.scms.common.specification.TextMatch;
import edu.fpt.sba.scms.model.User;
import org.springframework.data.jpa.domain.Specification;

import static edu.fpt.sba.scms.common.specification.Specifications.allOf;
import static edu.fpt.sba.scms.common.specification.Specifications.containsWhenPresent;
import static edu.fpt.sba.scms.common.specification.Specifications.equalWhenPresent;

/**
 * Điều kiện lọc danh sách user (IMPLEMENTATION.md §10.2.1). Nhận tham số thô để repository không phụ thuộc dto.
 * <p>
 * Role lọc theo {@code role.code} (quan hệ {@code role}) — không lọc theo {@code position} vì POS đổi position
 * của Cashier/Barista mà role nghiệp vụ vẫn giữ nguyên.
 */
public final class UserSpecs {

    private UserSpecs() {
    }

    /**
     * @param keyword  tìm trên họ tên, username, phone, email
     * @param roleCode mã role cần lọc; null = không lọc
     * @param branchId chi nhánh; null = không lọc (COO). BM luôn truyền branch của mình
     * @param status   trạng thái; null = không lọc
     */
    public static Specification<User> managedList(String keyword, String roleCode, Integer branchId,
                                                  UserStatus status) {
        return managedList(keyword, roleCode, branchId, status, null);
    }

    /**
     * @param keyword    tìm trên họ tên, username, phone, email
     * @param roleCode   mã role cần lọc; null = không lọc
     * @param branchId   chi nhánh; null = không lọc (COO). BM luôn truyền branch của mình
     * @param status     trạng thái; null = không lọc
     * @param unassigned true = {@code branchId IS NULL}; false = {@code branchId IS NOT NULL}; null = không lọc
     */
    public static Specification<User> managedList(String keyword, String roleCode, Integer branchId,
                                                  UserStatus status, Boolean unassigned) {
        return allOf(
                containsWhenPresent(keyword, TextMatch.of("fullName"), TextMatch.of("username"),
                        TextMatch.of("phone"), TextMatch.of("email")),
                // Bỏ join nội bộ của root.get("role.code") (lỗi "query specified join fetching" khi page)
                // -> lấy Path role rồi code: cả hai đều là path ngầm, Hibernate sinh join thường.
                roleCode == null ? null : (root, query, cb) ->
                        cb.equal(root.get("role").get("code"), roleCode),
                equalWhenPresent("branchId", branchId),
                equalWhenPresent("status", status),
                unassignedPredicate(unassigned));
    }

    private static Specification<User> unassignedPredicate(Boolean unassigned) {
        if (unassigned == null) {
            return null;
        }
        return (root, query, cb) -> unassigned
                ? cb.isNull(root.get("branchId"))
                : cb.isNotNull(root.get("branchId"));
    }
}
