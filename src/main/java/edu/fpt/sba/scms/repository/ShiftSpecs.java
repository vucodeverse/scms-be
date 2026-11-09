package edu.fpt.sba.scms.repository;

import edu.fpt.sba.scms.common.enums.ShiftStatus;
import edu.fpt.sba.scms.model.Shift;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;

/**
 * Điều kiện lọc danh sách ca làm việc theo chi nhánh, trạng thái và khoảng ngày.
 */
public final class ShiftSpecs {

    private ShiftSpecs() {
    }

    public static Specification<Shift> filter(Integer branchId, ShiftStatus status,
                                              LocalDate startDate, LocalDate endDate) {
        return (root, query, cb) -> {
            var predicates = cb.conjunction();

            if (branchId != null) {
                predicates = cb.and(predicates, cb.equal(root.get("branchId"), branchId));
            }
            if (status != null) {
                predicates = cb.and(predicates, cb.equal(root.get("status"), status));
            }
            if (startDate != null) {
                predicates = cb.and(predicates, cb.greaterThanOrEqualTo(root.get("shiftDate"), startDate));
            }
            if (endDate != null) {
                predicates = cb.and(predicates, cb.lessThanOrEqualTo(root.get("shiftDate"), endDate));
            }

            return predicates;
        };
    }
}
