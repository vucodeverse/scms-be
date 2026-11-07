package edu.fpt.sba.scms.repository;

import edu.fpt.sba.scms.common.enums.OrderStatus;
import edu.fpt.sba.scms.model.Order;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;

/**
 * Điều kiện lọc danh sách đơn hàng (UC18, §10.11).
 * Nhận tham số thô để tầng repository không phụ thuộc DTO.
 */
public final class OrderSpecs {

    private OrderSpecs() {
    }

    public static Specification<Order> filter(Integer branchId, OrderStatus status,
                                              Instant fromDate, Instant toDate, String keyword) {
        return (root, query, cb) -> {
            var predicates = cb.conjunction();

            if (branchId != null) {
                predicates = cb.and(predicates, cb.equal(root.get("branchId"), branchId));
            }
            if (status != null) {
                predicates = cb.and(predicates, cb.equal(root.get("status"), status));
            }
            if (fromDate != null) {
                predicates = cb.and(predicates, cb.greaterThanOrEqualTo(root.get("createdAt"), fromDate));
            }
            if (toDate != null) {
                predicates = cb.and(predicates, cb.lessThanOrEqualTo(root.get("createdAt"), toDate));
            }
            if (keyword != null && !keyword.isBlank()) {
                String pattern = "%" + keyword.trim().toLowerCase() + "%";
                var receiptMatch = cb.like(cb.lower(root.get("receiptNo")), pattern);
                var noteMatch = cb.like(cb.lower(root.get("note")), pattern);
                predicates = cb.and(predicates, cb.or(receiptMatch, noteMatch));
            }

            return predicates;
        };
    }
}
