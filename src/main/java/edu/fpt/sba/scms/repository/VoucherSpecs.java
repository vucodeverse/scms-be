package edu.fpt.sba.scms.repository;

import edu.fpt.sba.scms.common.enums.RecordStatus;
import edu.fpt.sba.scms.common.specification.TextMatch;
import edu.fpt.sba.scms.model.Voucher;
import org.springframework.data.jpa.domain.Specification;
import static edu.fpt.sba.scms.common.specification.Specifications.allOf;

import static edu.fpt.sba.scms.common.specification.Specifications.containsWhenPresent;
import static edu.fpt.sba.scms.common.specification.Specifications.equalWhenPresent;

/**
 * Điều kiện lọc danh sách voucher (UC13, §10.8). Nhận tham số thô để tầng repository không phụ thuộc {@code dto}.
 */
public final class VoucherSpecs {

    private VoucherSpecs() {
    }

    public static Specification<Voucher> filter(String keyword, RecordStatus status) {
        return allOf(
                containsWhenPresent(keyword, TextMatch.of("code")),
                equalWhenPresent("status", status));
    }
}
