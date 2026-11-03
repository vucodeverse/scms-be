package edu.fpt.sba.scms.repository;

import edu.fpt.sba.scms.common.enums.BranchStatus;
import edu.fpt.sba.scms.common.specification.TextMatch;
import edu.fpt.sba.scms.model.Branch;
import org.springframework.data.jpa.domain.Specification;

import static edu.fpt.sba.scms.common.specification.Specifications.allOf;
import static edu.fpt.sba.scms.common.specification.Specifications.containsWhenPresent;
import static edu.fpt.sba.scms.common.specification.Specifications.equalWhenPresent;

/** Điều kiện lọc danh sách chi nhánh (IMPLEMENTATION.md §10.3). */
public final class BranchSpecs {

    private BranchSpecs() {
    }

    /**
     * @param keyword tìm trên mã, tên, địa chỉ, số điện thoại
     * @param status  trạng thái; null = không lọc
     */
    public static Specification<Branch> managedList(String keyword, BranchStatus status) {
        return allOf(
                containsWhenPresent(keyword, TextMatch.of("code"), TextMatch.of("name"),
                        TextMatch.of("address"), TextMatch.of("phone")),
                equalWhenPresent("status", status));
    }
}
